// DeltaDotNet - in-memory lobby registry (open/closed lobbies, slots, kick/ban)
import crypto from 'node:crypto'

/**
 * Lobby shape:
 * {
 *   id, name, hostId, hostName,
 *   visibility: 'open' | 'closed',
 *   accessMode: 'none' | 'password' | 'whitelist',
 *   passwordHash, whitelist: [usernameLower],
 *   maxPlayers: 2..4,
 *   state: 'lobby' | 'playing',
 *   members: Map(userId -> { user, slot, ready, connected }),
 *   bans: Map(userId -> { username, reason, at }),
 *   quality: { fps, scale, jpegQuality },
 *   createdAt
 * }
 */
const lobbies = new Map()

function hash (text) {
  return crypto.createHash('sha256').update(String(text)).digest('hex')
}

export function createLobby (host, opts = {}) {
  const id = crypto.randomBytes(3).toString('hex').toUpperCase() // short join code
  const maxPlayers = Math.min(4, Math.max(2, Number(opts.maxPlayers) || 2))
  const visibility = opts.visibility === 'closed' ? 'closed' : 'open'
  let accessMode = opts.accessMode || 'none'
  if (visibility === 'open') accessMode = 'none'
  if (!['none', 'password', 'whitelist'].includes(accessMode)) accessMode = 'none'

  const lobby = {
    id,
    name: (opts.name || `${host.username}'s lobby`).slice(0, 40),
    hostId: host.id,
    hostName: host.username,
    visibility,
    accessMode,
    passwordHash: accessMode === 'password' && opts.password ? hash(opts.password) : null,
    whitelist: Array.isArray(opts.whitelist)
      ? opts.whitelist.map(u => String(u).toLowerCase()).slice(0, 32)
      : [],
    maxPlayers,
    state: 'lobby',
    members: new Map(),
    bans: new Map(),
    quality: {
      fps: clamp(opts.quality?.fps, 5, 60, 30),
      scale: clamp(opts.quality?.scale, 25, 100, 75),
      jpegQuality: clamp(opts.quality?.jpegQuality, 20, 95, 60)
    },
    createdAt: Date.now()
  }
  lobbies.set(id, lobby)
  return lobby
}

function clamp (value, min, max, fallback) {
  const n = Number(value)
  if (!Number.isFinite(n)) return fallback
  return Math.min(max, Math.max(min, Math.round(n)))
}

export function getLobby (id) {
  return lobbies.get(String(id || '').toUpperCase()) || null
}

export function deleteLobby (id) {
  return lobbies.delete(String(id || '').toUpperCase())
}

export function allLobbies () {
  return [...lobbies.values()]
}

export function listPublicLobbies () {
  return allLobbies().map(summary)
}

export function summary (lobby) {
  return {
    id: lobby.id,
    name: lobby.name,
    hostName: lobby.hostName,
    visibility: lobby.visibility,
    accessMode: lobby.accessMode,
    maxPlayers: lobby.maxPlayers,
    players: lobby.members.size,
    state: lobby.state,
    createdAt: lobby.createdAt
  }
}

export function detail (lobby) {
  return {
    ...summary(lobby),
    hostId: lobby.hostId,
    quality: lobby.quality,
    whitelist: lobby.whitelist,
    members: [...lobby.members.values()].map(m => ({
      id: m.user.id,
      username: m.user.username,
      rainbow: !!m.user.rainbow,
      nameColor: m.user.nameColor || null,
      badge: m.user.badge || null,
      slot: m.slot,
      ready: !!m.ready,
      isHost: m.user.id === lobby.hostId
    })),
    bans: [...lobby.bans.entries()].map(([id, b]) => ({ id, ...b }))
  }
}

export function canJoin (lobby, user, password) {
  if (!lobby) return 'Lobby not found'
  if (lobby.bans.has(user.id)) return 'You are banned from this lobby'
  if (lobby.members.has(user.id)) return null // rejoin allowed
  if (lobby.members.size >= lobby.maxPlayers) return 'Lobby is full'
  if (lobby.state === 'playing') return 'Game already started'
  if (lobby.visibility === 'closed') {
    if (lobby.accessMode === 'password') {
      if (!password || hash(password) !== lobby.passwordHash) return 'Wrong lobby password'
    } else if (lobby.accessMode === 'whitelist') {
      if (!lobby.whitelist.includes(user.username.toLowerCase())) {
        return 'Your login is not on the allow list'
      }
    }
  }
  return null
}

export function addMember (lobby, user) {
  const existing = lobby.members.get(user.id)
  if (existing) { existing.connected = true; return existing }
  const used = new Set([...lobby.members.values()].map(m => m.slot))
  let slot = 0
  while (used.has(slot)) slot++
  const member = { user, slot, ready: false, connected: true }
  lobby.members.set(user.id, member)
  return member
}

export function removeMember (lobby, userId) {
  lobby.members.delete(userId)
}

export function banMember (lobby, userId, username, reason) {
  lobby.bans.set(userId, { username, reason: reason || 'No reason given', at: Date.now() })
  lobby.members.delete(userId)
}

export function unbanMember (lobby, userId) {
  lobby.bans.delete(userId)
}
