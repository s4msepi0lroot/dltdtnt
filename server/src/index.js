'use strict';
// DeltaDotNet server entry point.
const http = require('http');
const express = require('express');
const db = require('./db');
const auth = require('./auth');
const hub = require('./hub');
const L = require('./lobbies');

const PORT = parseInt(process.env.PORT || '8080', 10);
const HOST = process.env.HOST || '0.0.0.0';

db.load();

const app = express();
app.use(express.json({ limit: '256kb' }));
app.disable('x-powered-by');

app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

function bearer(req) {
  const h = req.headers.authorization || '';
  if (h.toLowerCase().startsWith('bearer ')) return h.slice(7).trim();
  return req.query.token || null;
}

app.get('/api/health', (req, res) => {
  const s = db.get();
  res.json({
    ok: true,
    name: 'DeltaDotNet',
    version: require('../package.json').version,
    protocol: 1,
    users: Object.keys(s.users).length,
    lobbies: L.all().length,
    online: hub.conns.size,
    maintenance: !!s.settings.maintenance,
    motd: s.settings.motd
  });
});

app.post('/api/register', (req, res) => {
  try {
    const s = db.get();
    if (s.settings.maintenance) return res.status(503).json({ error: 'Server is in maintenance mode' });
    const user = auth.register(String(req.body.login || ''), String(req.body.password || ''));
    res.json({ token: auth.makeToken(user), profile: auth.publicUser(user) });
  } catch (e) {
    res.status(e.status || 500).json({ error: e.message });
  }
});

app.post('/api/login', (req, res) => {
  try {
    const s = db.get();
    const user = auth.login(String(req.body.login || ''), String(req.body.password || ''));
    if (s.settings.maintenance && !auth.isAdmin(user)) {
      return res.status(503).json({ error: 'Server is in maintenance mode' });
    }
    res.json({ token: auth.makeToken(user), profile: auth.publicUser(user) });
  } catch (e) {
    res.status(e.status || 500).json({ error: e.message });
  }
});

app.get('/api/me', (req, res) => {
  const user = auth.userFromToken(bearer(req));
  if (!user) return res.status(401).json({ error: 'Invalid token' });
  res.json({ profile: auth.publicUser(user) });
});

app.post('/api/password', (req, res) => {
  const user = auth.userFromToken(bearer(req));
  if (!user) return res.status(401).json({ error: 'Invalid token' });
  const oldP = String(req.body.oldPassword || '');
  const newP = String(req.body.newPassword || '');
  if (!auth.verifyPassword(oldP, user.salt, user.hash)) return res.status(403).json({ error: 'Current password is wrong' });
  if (newP.length < 4) return res.status(400).json({ error: 'Password must be at least 4 characters' });
  const h = auth.hashPassword(newP);
  user.salt = h.salt; user.hash = h.hash;
  db.save();
  res.json({ ok: true });
});

app.get('/api/lobbies', (req, res) => {
  res.json({ lobbies: L.all().filter(l => l.visibility === 'open').map(L.publicLobby) });
});

const server = http.createServer(app);
hub.attach(server);

server.listen(PORT, HOST, () => {
  console.log('[DeltaDotNet] server listening on http://' + HOST + ':' + PORT);
  console.log('[DeltaDotNet] websocket endpoint: ws://' + HOST + ':' + PORT + '/ws?token=...');
  console.log('[DeltaDotNet] admin account: ' + auth.ADMIN_USERNAME);
});
