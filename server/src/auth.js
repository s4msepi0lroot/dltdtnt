// DeltaDotNet - authentication (registration, login, tokens, roles)
import crypto from 'node:crypto'
import { getDb, save } from './store.js'

// The owner account name. Only this account can open the admin panel.
export const OWNER_USERNAME = (process.env.DDN_OWNER || 's4msepi0l').toLowerCase()

const SECRET = process.env.DDN_SECRET || 'change-me-deltadotnet-secret'
const TOKEN_TTL_MS = 1000 * 60 * 60 * 24 * 14 // 14 days

const USERNAME_RE = /^[A-Za-z0-9_.\-]{3,20}$/

function hashPassword (password, salt = crypto.randomBytes(16).toString('hex')) {
  const hash = crypto.scryptSync(password, salt, 64).toString('hex')
  return `${salt}:${hash}`
}

function verifyPassword (password, stored) {
  if (!stored || !stored.includes(':')) return false
  const [salt, hash] = stored.split(':')
  const test = crypto.scryptSync(password, salt, 64).toString('hex')
  const a = Buffer.from(hash, 'hex')
  const b = Buffer.from(test, 'hex')
  return a.length === b.length && crypto.timingSafeEqual(a, b)
}

function b64url (buf) {
  return Buffer.from(buf).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function unb64url (str) {
  return Buffer.from(str.replace(/-/g, '+').replace(/_/g, '/'), 'base64')
}

export function issueToken (userId) {
  const payload = b64url(JSON.stringify({ uid: userId, exp: Date.now() + TOKEN_TTL_MS }))
  const sig = b64url(crypto.createHmac('sha256', SECRET).update(payload).digest())
  return `${payload}.${sig}`
}

export function verifyToken (token) {
  if (typeof token !== 'string' || !token.includes('.')) return null
  const [payload, sig] = token.split('.')
  const expected = b64url(crypto.createHmac('sha256', SECRET).update(payload).digest())
  if (sig.length !== expected.length) return null
  if (!crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expected))) return null
  try {
    const data = JSON.parse(unb64url(payload).toString('utf8'))
    if (!data.exp || data.exp < Date.now()) return null
    const user = getDb().users[data.uid]
    if (!user) return null
    return user
  } catch { return null }
}

export function publicUser (user) {
  if (!user) return null
  return {
    id: user.id,
    username: user.username,
    role: user.role,
    rainbow: !!user.rainbow,
    nameColor: user.nameColor || null,
    badge: user.badge || null,
    createdAt: user.createdAt,
    lastSeen: user.lastSeen || null,
    banned: !!getDb().globalBans[user.id]
  }
}

export function register (username, password) {
  const db = getDb()
  if (!USERNAME_RE.test(username || '')) {
    return { error: 'Username must be 3-20 chars: letters, digits, _ . -' }
  }
  if (typeof password !== 'string' || password.length < 4) {
    return { error: 'Password must be at least 4 characters' }
  }
  const key = username.toLowerCase()
  if (db.usernames[key]) return { error: 'This username is already taken' }

  const id = crypto.randomUUID()
  const user = {
    id,
    username,
    passwordHash: hashPassword(password),
    role: key === OWNER_USERNAME ? 'owner' : 'user',
    rainbow: key === OWNER_USERNAME,
    nameColor: null,
    badge: key === OWNER_USERNAME ? 'OWNER' : null,
    createdAt: Date.now(),
    lastSeen: Date.now()
  }
  db.users[id] = user
  db.usernames[key] = id
  save()
  return { user, token: issueToken(id) }
}

export function login (username, password) {
  const db = getDb()
  const id = db.usernames[(username || '').toLowerCase()]
  const user = id ? db.users[id] : null
  if (!user || !verifyPassword(password, user.passwordHash)) {
    return { error: 'Wrong username or password' }
  }
  if (db.globalBans[user.id]) {
    return { error: 'Account banned: ' + (db.globalBans[user.id].reason || 'no reason') }
  }
  // Owner promotion in case the owner account existed before configuration.
  if (user.username.toLowerCase() === OWNER_USERNAME && user.role !== 'owner') {
    user.role = 'owner'
    user.badge = user.badge || 'OWNER'
  }
  user.lastSeen = Date.now()
  save()
  return { user, token: issueToken(user.id) }
}

export function isOwner (user) {
  return !!user && (user.role === 'owner' || user.username.toLowerCase() === OWNER_USERNAME)
}

export function isStaff (user) {
  return isOwner(user) || (!!user && user.role === 'admin')
}
