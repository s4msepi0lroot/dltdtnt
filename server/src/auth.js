'use strict';
// DeltaDotNet - authentication: scrypt password hashing + HMAC signed tokens.
const crypto = require('crypto');
const db = require('./db');

const SECRET = process.env.DDN_SECRET || 'change-me-deltadotnet-secret';
const TOKEN_TTL_MS = 1000 * 60 * 60 * 24 * 14; // 14 days
const ADMIN_USERNAME = (process.env.DDN_ADMIN_USERNAME || 's4msepi0l').toLowerCase();

const NAME_RE = /^[a-zA-Z0-9_.\-]{3,20}$/;

function hashPassword(password, salt) {
  salt = salt || crypto.randomBytes(16).toString('hex');
  const hash = crypto.scryptSync(password, salt, 64).toString('hex');
  return { salt, hash };
}

function verifyPassword(password, salt, hash) {
  const test = crypto.scryptSync(password, salt, 64).toString('hex');
  const a = Buffer.from(test, 'hex');
  const b = Buffer.from(hash, 'hex');
  if (a.length !== b.length) return false;
  return crypto.timingSafeEqual(a, b);
}

function b64u(buf) {
  return Buffer.from(buf).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function b64uDecode(str) {
  str = str.replace(/-/g, '+').replace(/_/g, '/');
  return Buffer.from(str, 'base64').toString('utf8');
}

function sign(payloadObj) {
  const payload = b64u(JSON.stringify(payloadObj));
  const sig = b64u(crypto.createHmac('sha256', SECRET).update(payload).digest());
  return payload + '.' + sig;
}

function verifyToken(token) {
  if (!token || typeof token !== 'string' || token.indexOf('.') < 0) return null;
  const [payload, sig] = token.split('.');
  const expected = b64u(crypto.createHmac('sha256', SECRET).update(payload).digest());
  if (sig.length !== expected.length) return null;
  if (!crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expected))) return null;
  let obj;
  try { obj = JSON.parse(b64uDecode(payload)); } catch (_) { return null; }
  if (!obj.exp || Date.now() > obj.exp) return null;
  return obj;
}

function publicUser(u) {
  if (!u) return null;
  return {
    login: u.login,
    display: u.display,
    rank: u.rank,
    rainbow: !!u.rainbow,
    nameColor: u.nameColor || null,
    badge: u.badge || null,
    isAdmin: u.login.toLowerCase() === ADMIN_USERNAME || u.rank === 'admin',
    createdAt: u.createdAt,
    lastSeen: u.lastSeen || null
  };
}

function register(login, password) {
  const s = db.get();
  if (!NAME_RE.test(login)) throw httpError(400, 'Login must be 3-20 chars: a-z, 0-9, _ . -');
  if (typeof password !== 'string' || password.length < 4) throw httpError(400, 'Password must be at least 4 characters');
  const key = login.toLowerCase();
  if (s.users[key]) throw httpError(409, 'This login is already taken');
  const { salt, hash } = hashPassword(password);
  const user = {
    login,
    display: login,
    salt, hash,
    rank: key === ADMIN_USERNAME ? 'admin' : 'player',
    rainbow: key === ADMIN_USERNAME,
    nameColor: null,
    badge: key === ADMIN_USERNAME ? 'ADMIN' : null,
    createdAt: Date.now(),
    lastSeen: Date.now(),
    banned: false
  };
  s.users[key] = user;
  db.save();
  return user;
}

function login(loginName, password) {
  const s = db.get();
  const key = String(loginName || '').toLowerCase();
  const user = s.users[key];
  if (!user) throw httpError(401, 'Wrong login or password');
  if (!verifyPassword(password, user.salt, user.hash)) throw httpError(401, 'Wrong login or password');
  if (s.globalBans[key]) throw httpError(403, 'You are banned: ' + (s.globalBans[key].reason || 'no reason'));
  // keep the owner account always admin
  if (key === ADMIN_USERNAME && user.rank !== 'admin') { user.rank = 'admin'; }
  user.lastSeen = Date.now();
  db.save();
  return user;
}

function makeToken(user) {
  return sign({ sub: user.login.toLowerCase(), login: user.login, exp: Date.now() + TOKEN_TTL_MS });
}

function userFromToken(token) {
  const payload = verifyToken(token);
  if (!payload) return null;
  const s = db.get();
  const u = s.users[payload.sub];
  if (!u) return null;
  if (s.globalBans[payload.sub]) return null;
  return u;
}

function isAdmin(user) {
  if (!user) return false;
  return user.login.toLowerCase() === ADMIN_USERNAME || user.rank === 'admin';
}

function httpError(status, message) {
  const e = new Error(message);
  e.status = status;
  return e;
}

module.exports = {
  register, login, makeToken, userFromToken, isAdmin, publicUser,
  hashPassword, verifyPassword, httpError, ADMIN_USERNAME, NAME_RE
};
