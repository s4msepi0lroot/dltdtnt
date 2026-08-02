'use strict';
// DeltaDotNet - in-memory lobby registry.
const crypto = require('crypto');
const db = require('./db');

/**
 * Lobby shape:
 * {
 *   id, name, hostLogin, visibility: 'open'|'closed',
 *   password: string|null,          // for closed lobbies (password mode)
 *   allowList: string[],            // for closed lobbies (login whitelist mode)
 *   maxPlayers: number,             // 2..8
 *   state: 'waiting'|'playing',
 *   createdAt, bans: {login: {reason, at}},
 *   members: Map<loginLower, member>
 * }
 * member = { login, display, slot, isHost, connId, ready }
 */

const lobbies = new Map();

function newId() {
  return crypto.randomBytes(4).toString('hex').toUpperCase();
}

function create(host, opts) {
  const s = db.get();
  if (lobbies.size >= (s.settings.maxLobbies || 200)) {
    throw new Error('Server lobby limit reached');
  }
  const hardCap = s.settings.maxPlayersHardCap || 8;
  let maxPlayers = parseInt(opts.maxPlayers, 10);
  if (!Number.isFinite(maxPlayers)) maxPlayers = 2;
  maxPlayers = Math.min(hardCap, Math.max(2, maxPlayers));

  const visibility = opts.visibility === 'closed' ? 'closed' : 'open';
  const id = newId();
  const lobby = {
    id,
    name: (opts.name || (host.display + "'s lobby")).slice(0, 40),
    hostLogin: host.login,
    visibility,
    password: visibility === 'closed' && opts.password ? String(opts.password) : null,
    allowList: Array.isArray(opts.allowList) ? opts.allowList.map(x => String(x).toLowerCase()).slice(0, 32) : [],
    maxPlayers,
    state: 'waiting',
    createdAt: Date.now(),
    bans: {},
    members: new Map()
  };
  lobbies.set(id, lobby);
  return lobby;
}

function get(id) { return lobbies.get(String(id || '').toUpperCase()); }
function remove(id) { return lobbies.delete(String(id || '').toUpperCase()); }
function all() { return Array.from(lobbies.values()); }

function freeSlot(lobby) {
  const used = new Set(Array.from(lobby.members.values()).map(m => m.slot));
  for (let i = 1; i <= lobby.maxPlayers; i++) if (!used.has(i)) return i;
  return null;
}

function publicLobby(lobby) {
  return {
    id: lobby.id,
    name: lobby.name,
    host: lobby.hostLogin,
    visibility: lobby.visibility,
    locked: lobby.visibility === 'closed',
    hasPassword: !!lobby.password,
    whitelisted: lobby.allowList.length > 0,
    players: lobby.members.size,
    maxPlayers: lobby.maxPlayers,
    state: lobby.state,
    createdAt: lobby.createdAt
  };
}

function fullLobby(lobby, usersDb) {
  return Object.assign(publicLobby(lobby), {
    bans: Object.keys(lobby.bans),
    allowList: lobby.allowList,
    members: Array.from(lobby.members.values()).map(m => {
      const u = usersDb ? usersDb[m.login.toLowerCase()] : null;
      return {
        login: m.login,
        display: m.display,
        slot: m.slot,
        isHost: m.isHost,
        ready: !!m.ready,
        rainbow: u ? !!u.rainbow : false,
        nameColor: u ? (u.nameColor || null) : null,
        badge: u ? (u.badge || null) : null,
        rank: u ? u.rank : 'player'
      };
    }).sort((a, b) => a.slot - b.slot)
  });
}

module.exports = { create, get, remove, all, freeSlot, publicLobby, fullLobby, lobbies, newId };
