'use strict';
// DeltaDotNet - WebSocket hub: lobby control, input relay, video frame relay.
const { WebSocketServer } = require('ws');
const url = require('url');
const db = require('./db');
const auth = require('./auth');
const L = require('./lobbies');

const MAX_FRAME_BYTES = 4 * 1024 * 1024; // 4 MB safety cap per video frame

// connId -> conn
const conns = new Map();
let connSeq = 1;

function sendJson(ws, obj) {
  if (ws.readyState === ws.OPEN) {
    try { ws.send(JSON.stringify(obj)); } catch (_) {}
  }
}

function err(ws, code, message) {
  sendJson(ws, { t: 'error', code, message });
}

function connsInLobby(lobbyId) {
  return Array.from(conns.values()).filter(c => c.lobbyId === lobbyId);
}

function broadcastLobby(lobby, obj, exceptConnId) {
  const payload = JSON.stringify(obj);
  for (const c of connsInLobby(lobby.id)) {
    if (exceptConnId && c.id === exceptConnId) continue;
    if (c.ws.readyState === c.ws.OPEN) { try { c.ws.send(payload); } catch (_) {} }
  }
}

function pushLobbyState(lobby) {
  const s = db.get();
  broadcastLobby(lobby, { t: 'lobby.state', lobby: L.fullLobby(lobby, s.users) });
}

function leaveLobby(conn, reason) {
  const lobby = L.get(conn.lobbyId);
  conn.lobbyId = null;
  if (!lobby) return;
  lobby.members.delete(conn.login.toLowerCase());
  if (lobby.hostLogin.toLowerCase() === conn.login.toLowerCase()) {
    // host left -> close the lobby for everyone
    for (const c of connsInLobby(lobby.id)) {
      c.lobbyId = null;
      sendJson(c.ws, { t: 'lobby.closed', id: lobby.id, reason: reason || 'Host left the lobby' });
    }
    L.remove(lobby.id);
    return;
  }
  pushLobbyState(lobby);
}

function attach(server) {
  const wss = new WebSocketServer({ server, path: '/ws', maxPayload: MAX_FRAME_BYTES });

  wss.on('connection', (ws, req) => {
    const q = url.parse(req.url, true).query;
    const user = auth.userFromToken(q.token);
    if (!user) {
      sendJson(ws, { t: 'error', code: 'auth', message: 'Invalid or expired token' });
      ws.close(4001, 'unauthorized');
      return;
    }

    const conn = {
      id: connSeq++,
      ws,
      login: user.login,
      lobbyId: null,
      alive: true,
      lastFrameAt: 0
    };
    conns.set(conn.id, conn);

    const s = db.get();
    sendJson(ws, {
      t: 'hello',
      you: auth.publicUser(user),
      motd: s.settings.motd,
      serverTime: Date.now()
    });

    ws.on('pong', () => { conn.alive = true; });

    ws.on('message', (data, isBinary) => {
      if (isBinary) { handleBinary(conn, data); return; }
      let msg;
      try { msg = JSON.parse(data.toString()); } catch (_) { return err(ws, 'json', 'Malformed JSON'); }
      try { handleJson(conn, msg); }
      catch (e) { err(ws, 'server', e.message || 'Unexpected error'); }
    });

    ws.on('close', () => {
      leaveLobby(conn);
      conns.delete(conn.id);
    });
  });

  // keep-alive
  const interval = setInterval(() => {
    for (const c of conns.values()) {
      if (!c.alive) { try { c.ws.terminate(); } catch (_) {} continue; }
      c.alive = false;
      try { c.ws.ping(); } catch (_) {}
    }
  }, 20000);
  wss.on('close', () => clearInterval(interval));

  return wss;
}

// ---------------------------------------------------------------- binary
// Binary layout (host -> server -> guests):
//   byte 0      : 0x01 = JPEG video frame
//   bytes 1..8  : uint64 LE timestamp (ms)
//   bytes 9..   : JPEG payload
function handleBinary(conn, buf) {
  if (!conn.lobbyId) return;
  const lobby = L.get(conn.lobbyId);
  if (!lobby) return;
  if (lobby.hostLogin.toLowerCase() !== conn.login.toLowerCase()) return; // only host streams
  if (!buf || buf.length < 9) return;
  if (buf[0] !== 0x01) return;
  for (const c of connsInLobby(lobby.id)) {
    if (c.id === conn.id) continue;
    if (c.ws.readyState === c.ws.OPEN && c.ws.bufferedAmount < 8 * 1024 * 1024) {
      try { c.ws.send(buf, { binary: true }); } catch (_) {}
    }
  }
}

// ---------------------------------------------------------------- json
function handleJson(conn, msg) {
  const ws = conn.ws;
  const s = db.get();
  const me = s.users[conn.login.toLowerCase()];
  if (!me) { ws.close(4001, 'gone'); return; }

  switch (msg.t) {
    case 'ping':
      return sendJson(ws, { t: 'pong', time: Date.now(), echo: msg.echo || null });

    case 'lobby.list': {
      const list = L.all()
        .filter(l => l.visibility === 'open' || msg.includeClosed)
        .map(L.publicLobby)
        .sort((a, b) => b.createdAt - a.createdAt);
      return sendJson(ws, { t: 'lobby.list', lobbies: list });
    }

    case 'lobby.create': {
      if (conn.lobbyId) leaveLobby(conn);
      const lobby = L.create(me, msg);
      lobby.members.set(me.login.toLowerCase(), {
        login: me.login, display: me.display, slot: 1, isHost: true, ready: true
      });
      conn.lobbyId = lobby.id;
      sendJson(ws, { t: 'lobby.joined', lobby: L.fullLobby(lobby, s.users), slot: 1, isHost: true });
      return pushLobbyState(lobby);
    }

    case 'lobby.join': {
      const lobby = L.get(msg.id);
      if (!lobby) return err(ws, 'nolobby', 'Lobby not found');
      const key = me.login.toLowerCase();
      if (lobby.bans[key]) return err(ws, 'banned', 'You are banned from this lobby');
      if (lobby.members.size >= lobby.maxPlayers) return err(ws, 'full', 'Lobby is full');
      if (lobby.visibility === 'closed') {
        const byList = lobby.allowList.length > 0 && lobby.allowList.includes(key);
        const byPass = lobby.password && String(msg.password || '') === lobby.password;
        if (!byList && !byPass) return err(ws, 'denied', 'Wrong password / you are not on the guest list');
      }
      if (conn.lobbyId && conn.lobbyId !== lobby.id) leaveLobby(conn);
      const slot = L.freeSlot(lobby);
      if (!slot) return err(ws, 'full', 'No free player slot');
      lobby.members.set(key, { login: me.login, display: me.display, slot, isHost: false, ready: false });
      conn.lobbyId = lobby.id;
      sendJson(ws, { t: 'lobby.joined', lobby: L.fullLobby(lobby, s.users), slot, isHost: false });
      broadcastLobby(lobby, { t: 'lobby.chat', system: true, text: me.display + ' joined (slot ' + slot + ')' });
      return pushLobbyState(lobby);
    }

    case 'lobby.leave':
      leaveLobby(conn);
      return sendJson(ws, { t: 'lobby.left' });

    case 'lobby.close': {
      const lobby = requireHost(conn, ws); if (!lobby) return;
      for (const c of connsInLobby(lobby.id)) {
        c.lobbyId = null;
        sendJson(c.ws, { t: 'lobby.closed', id: lobby.id, reason: 'Lobby closed by host' });
      }
      L.remove(lobby.id);
      return;
    }

    case 'lobby.update': {
      const lobby = requireHost(conn, ws); if (!lobby) return;
      if (typeof msg.name === 'string') lobby.name = msg.name.slice(0, 40);
      if (msg.visibility === 'open' || msg.visibility === 'closed') lobby.visibility = msg.visibility;
      if (typeof msg.password === 'string') lobby.password = msg.password || null;
      if (Array.isArray(msg.allowList)) lobby.allowList = msg.allowList.map(x => String(x).toLowerCase()).slice(0, 32);
      if (msg.maxPlayers) {
        const cap = s.settings.maxPlayersHardCap || 8;
        const n = Math.min(cap, Math.max(2, parseInt(msg.maxPlayers, 10) || 2));
        if (n >= lobby.members.size) lobby.maxPlayers = n;
        else err(ws, 'toosmall', 'There are more players in the lobby than the new limit');
      }
      return pushLobbyState(lobby);
    }

    case 'lobby.setSlot': {
      const lobby = requireHost(conn, ws); if (!lobby) return;
      const target = lobby.members.get(String(msg.login || '').toLowerCase());
      const slot = parseInt(msg.slot, 10);
      if (!target) return err(ws, 'nouser', 'Player is not in this lobby');
      if (!(slot >= 1 && slot <= lobby.maxPlayers)) return err(ws, 'badslot', 'Bad slot number');
      for (const m of lobby.members.values()) if (m.slot === slot) m.slot = target.slot;
      target.slot = slot;
      return pushLobbyState(lobby);
    }

    case 'lobby.kick': {
      const lobby = requireHost(conn, ws); if (!lobby) return;
      return kickOrBan(lobby, msg.login, false, msg.reason);
    }

    case 'lobby.ban': {
      const lobby = requireHost(conn, ws); if (!lobby) return;
      return kickOrBan(lobby, msg.login, true, msg.reason);
    }

    case 'lobby.unban': {
      const lobby = requireHost(conn, ws); if (!lobby) return;
      delete lobby.bans[String(msg.login || '').toLowerCase()];
      return pushLobbyState(lobby);
    }

    case 'lobby.start': {
      const lobby = requireHost(conn, ws); if (!lobby) return;
      lobby.state = 'playing';
      broadcastLobby(lobby, { t: 'game.started', lobby: L.fullLobby(lobby, s.users) });
      return pushLobbyState(lobby);
    }

    case 'lobby.stop': {
      const lobby = requireHost(conn, ws); if (!lobby) return;
      lobby.state = 'waiting';
      broadcastLobby(lobby, { t: 'game.stopped' });
      return pushLobbyState(lobby);
    }

    case 'lobby.chat': {
      const lobby = L.get(conn.lobbyId);
      if (!lobby) return;
      const text = String(msg.text || '').slice(0, 300);
      if (!text) return;
      return broadcastLobby(lobby, {
        t: 'lobby.chat', from: me.display, rainbow: !!me.rainbow,
        nameColor: me.nameColor || null, text, at: Date.now()
      });
    }

    // guest -> host input
    case 'input': {
      const lobby = L.get(conn.lobbyId);
      if (!lobby) return;
      const member = lobby.members.get(me.login.toLowerCase());
      if (!member) return;
      if (member.isHost) return; // host uses its own keyboard directly
      const hostConn = connsInLobby(lobby.id).find(c => c.login.toLowerCase() === lobby.hostLogin.toLowerCase());
      if (!hostConn) return;
      return sendJson(hostConn.ws, {
        t: 'input',
        slot: member.slot,
        login: me.login,
        action: String(msg.action || '').slice(0, 24),
        down: !!msg.down,
        seq: msg.seq || 0
      });
    }

    // ------------------------------------------------------------ admin
    case 'admin.users': {
      if (!requireAdmin(conn, ws)) return;
      const users = Object.values(s.users).map(auth.publicUser)
        .sort((a, b) => (b.lastSeen || 0) - (a.lastSeen || 0));
      const online = new Set(Array.from(conns.values()).map(c => c.login.toLowerCase()));
      users.forEach(u => { u.online = online.has(u.login.toLowerCase()); });
      return sendJson(ws, { t: 'admin.users', users, bans: s.globalBans });
    }

    case 'admin.setRainbow': {
      if (!requireAdmin(conn, ws)) return;
      const u = s.users[String(msg.login || '').toLowerCase()];
      if (!u) return err(ws, 'nouser', 'User not found');
      u.rainbow = !!msg.value;
      db.save();
      notifyUser(u.login, { t: 'profile.updated', profile: auth.publicUser(u) });
      return sendJson(ws, { t: 'admin.ok', action: 'setRainbow', login: u.login, value: u.rainbow });
    }

    case 'admin.setNameColor': {
      if (!requireAdmin(conn, ws)) return;
      const u = s.users[String(msg.login || '').toLowerCase()];
      if (!u) return err(ws, 'nouser', 'User not found');
      u.nameColor = msg.color ? String(msg.color).slice(0, 9) : null;
      db.save();
      notifyUser(u.login, { t: 'profile.updated', profile: auth.publicUser(u) });
      return sendJson(ws, { t: 'admin.ok', action: 'setNameColor', login: u.login });
    }

    case 'admin.setBadge': {
      if (!requireAdmin(conn, ws)) return;
      const u = s.users[String(msg.login || '').toLowerCase()];
      if (!u) return err(ws, 'nouser', 'User not found');
      u.badge = msg.badge ? String(msg.badge).slice(0, 12) : null;
      db.save();
      notifyUser(u.login, { t: 'profile.updated', profile: auth.publicUser(u) });
      return sendJson(ws, { t: 'admin.ok', action: 'setBadge', login: u.login });
    }

    case 'admin.setRank': {
      if (!requireAdmin(conn, ws)) return;
      const u = s.users[String(msg.login || '').toLowerCase()];
      if (!u) return err(ws, 'nouser', 'User not found');
      if (u.login.toLowerCase() === auth.ADMIN_USERNAME) return err(ws, 'owner', 'Owner rank cannot be changed');
      u.rank = ['player', 'vip', 'moderator', 'admin'].includes(msg.rank) ? msg.rank : 'player';
      db.save();
      notifyUser(u.login, { t: 'profile.updated', profile: auth.publicUser(u) });
      return sendJson(ws, { t: 'admin.ok', action: 'setRank', login: u.login, rank: u.rank });
    }

    case 'admin.resetPassword': {
      if (!requireAdmin(conn, ws)) return;
      const u = s.users[String(msg.login || '').toLowerCase()];
      if (!u) return err(ws, 'nouser', 'User not found');
      const np = String(msg.password || '');
      if (np.length < 4) return err(ws, 'weak', 'Password must be at least 4 characters');
      const h = auth.hashPassword(np);
      u.salt = h.salt; u.hash = h.hash;
      db.save();
      return sendJson(ws, { t: 'admin.ok', action: 'resetPassword', login: u.login });
    }

    case 'admin.globalBan': {
      if (!requireAdmin(conn, ws)) return;
      const key = String(msg.login || '').toLowerCase();
      if (key === auth.ADMIN_USERNAME) return err(ws, 'owner', 'You cannot ban the owner');
      if (msg.value === false) { delete s.globalBans[key]; }
      else { s.globalBans[key] = { reason: String(msg.reason || 'no reason'), at: Date.now(), by: me.login }; }
      db.save();
      if (msg.value !== false) {
        for (const c of conns.values()) {
          if (c.login.toLowerCase() === key) {
            sendJson(c.ws, { t: 'kicked', reason: 'Banned by admin: ' + s.globalBans[key].reason });
            try { c.ws.close(4003, 'banned'); } catch (_) {}
          }
        }
      }
      return sendJson(ws, { t: 'admin.ok', action: 'globalBan', login: key });
    }

    case 'admin.deleteUser': {
      if (!requireAdmin(conn, ws)) return;
      const key = String(msg.login || '').toLowerCase();
      if (key === auth.ADMIN_USERNAME) return err(ws, 'owner', 'You cannot delete the owner');
      delete s.users[key];
      db.save();
      return sendJson(ws, { t: 'admin.ok', action: 'deleteUser', login: key });
    }

    case 'admin.lobbies': {
      if (!requireAdmin(conn, ws)) return;
      return sendJson(ws, { t: 'admin.lobbies', lobbies: L.all().map(l => L.fullLobby(l, s.users)) });
    }

    case 'admin.killLobby': {
      if (!requireAdmin(conn, ws)) return;
      const lobby = L.get(msg.id);
      if (!lobby) return err(ws, 'nolobby', 'Lobby not found');
      for (const c of connsInLobby(lobby.id)) {
        c.lobbyId = null;
        sendJson(c.ws, { t: 'lobby.closed', id: lobby.id, reason: 'Lobby closed by administrator' });
      }
      L.remove(lobby.id);
      return sendJson(ws, { t: 'admin.ok', action: 'killLobby', id: msg.id });
    }

    case 'admin.broadcast': {
      if (!requireAdmin(conn, ws)) return;
      const text = String(msg.text || '').slice(0, 300);
      for (const c of conns.values()) sendJson(c.ws, { t: 'announce', text, from: me.display });
      return sendJson(ws, { t: 'admin.ok', action: 'broadcast' });
    }

    case 'admin.setMotd': {
      if (!requireAdmin(conn, ws)) return;
      s.settings.motd = String(msg.text || '').slice(0, 300);
      db.save();
      return sendJson(ws, { t: 'admin.ok', action: 'setMotd' });
    }

    case 'admin.setMaintenance': {
      if (!requireAdmin(conn, ws)) return;
      s.settings.maintenance = !!msg.value;
      db.save();
      return sendJson(ws, { t: 'admin.ok', action: 'setMaintenance', value: s.settings.maintenance });
    }

    case 'admin.stats': {
      if (!requireAdmin(conn, ws)) return;
      return sendJson(ws, {
        t: 'admin.stats',
        users: Object.keys(s.users).length,
        online: conns.size,
        lobbies: L.all().length,
        playing: L.all().filter(l => l.state === 'playing').length,
        uptimeSec: Math.round(process.uptime()),
        memoryMb: Math.round(process.memoryUsage().rss / 1048576),
        maintenance: !!s.settings.maintenance,
        motd: s.settings.motd
      });
    }

    default:
      return err(ws, 'unknown', 'Unknown message type: ' + msg.t);
  }
}

function kickOrBan(lobby, loginRaw, ban, reason) {
  const key = String(loginRaw || '').toLowerCase();
  if (key === lobby.hostLogin.toLowerCase()) return;
  if (ban) lobby.bans[key] = { reason: String(reason || 'no reason'), at: Date.now() };
  lobby.members.delete(key);
  for (const c of connsInLobby(lobby.id)) {
    if (c.login.toLowerCase() === key) {
      c.lobbyId = null;
      sendJson(c.ws, { t: 'kicked', reason: (ban ? 'Banned from the lobby: ' : 'Kicked from the lobby: ') + (reason || 'no reason') });
    }
  }
  pushLobbyState(lobby);
}

function notifyUser(login, obj) {
  const key = String(login).toLowerCase();
  for (const c of conns.values()) if (c.login.toLowerCase() === key) sendJson(c.ws, obj);
}

function requireHost(conn, ws) {
  const lobby = L.get(conn.lobbyId);
  if (!lobby) { err(ws, 'nolobby', 'You are not in a lobby'); return null; }
  if (lobby.hostLogin.toLowerCase() !== conn.login.toLowerCase()) { err(ws, 'nothost', 'Only the host can do that'); return null; }
  return lobby;
}

function requireAdmin(conn, ws) {
  const s = db.get();
  const u = s.users[conn.login.toLowerCase()];
  if (!auth.isAdmin(u)) { err(ws, 'noadmin', 'Admin panel is not available for your account'); return false; }
  return true;
}

module.exports = { attach, conns };
