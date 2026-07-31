// DeltaDotNet - realtime layer: lobby rooms, video relay (binary), input relay (JSON)
import { WebSocketServer } from 'ws'
import { verifyToken, isOwner, publicUser } from './auth.js'
import { getDb, save } from './store.js'
import {
  getLobby, deleteLobby, detail, canJoin, addMember,
  removeMember, banMember, allLobbies
} from './lobbies.js'

// Binary frame layout sent by the host:
//   byte 0      : payload type (1 = JPEG video frame)
//   bytes 1..4  : uint32 BE sequence number
//   bytes 5..8  : uint32 BE frame width
//   bytes 9..12 : uint32 BE frame height
//   bytes 13..  : JPEG data
const PAYLOAD_VIDEO = 1

const sockets = new Set() // every authenticated socket

export function attachWebSocket (server) {
  const wss = new WebSocketServer({ server, path: '/ws', maxPayload: 8 * 1024 * 1024 })

  wss.on('connection', (ws) => {
    ws.ddn = { user: null, lobbyId: null, alive: true }
    ws.on('pong', () => { ws.ddn.alive = true })

    ws.on('message', (data, isBinary) => {
      try {
        if (isBinary) handleBinary(ws, data)
        else handleText(ws, data.toString('utf8'))
      } catch (err) {
        console.error('[ws] message error:', err.message)
      }
    })

    ws.on('close', () => {
      sockets.delete(ws)
      handleLeave(ws, 'disconnected')
    })
  })

  const heartbeat = setInterval(() => {
    for (const ws of sockets) {
      if (!ws.ddn.alive) { ws.terminate(); continue }
      ws.ddn.alive = false
      try { ws.ping() } catch {}
    }
  }, 20000)
  wss.on('close', () => clearInterval(heartbeat))

  return wss
}

function send (ws, obj) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(obj))
}

function fail (ws, message) {
  send(ws, { t: 'error', message })
}

function socketsInLobby (lobbyId) {
  return [...sockets].filter(s => s.ddn.lobbyId === lobbyId)
}

function broadcastLobby (lobby, obj, exceptWs = null) {
  for (const s of socketsInLobby(lobby.id)) {
    if (s !== exceptWs) send(s, obj)
  }
}

function pushLobbyState (lobby) {
  broadcastLobby(lobby, { t: 'lobby', lobby: detail(lobby) })
}

function handleText (ws, raw) {
  const msg = JSON.parse(raw)
  const state = ws.ddn

  if (msg.t === 'auth') {
    const user = verifyToken(msg.token)
    if (!user) return fail(ws, 'Invalid or expired token')
    if (getDb().globalBans[user.id]) return fail(ws, 'Account banned')
    state.user = user
    user.lastSeen = Date.now(); save()
    sockets.add(ws)
    return send(ws, { t: 'authed', user: publicUser(user), motd: getDb().motd })
  }

  if (!state.user) return fail(ws, 'Not authenticated')
  const user = state.user

  switch (msg.t) {
    case 'join': {
      const lobby = getLobby(msg.lobbyId)
      const reason = canJoin(lobby, user, msg.password)
      if (reason) return fail(ws, reason)
      const member = addMember(lobby, user)
      state.lobbyId = lobby.id
      send(ws, { t: 'joined', lobby: detail(lobby), slot: member.slot, isHost: lobby.hostId === user.id })
      pushLobbyState(lobby)
      break
    }

    case 'leave': {
      handleLeave(ws, 'left')
      send(ws, { t: 'left' })
      break
    }

    case 'ready': {
      const lobby = getLobby(state.lobbyId)
      if (!lobby) return fail(ws, 'You are not in a lobby')
      const member = lobby.members.get(user.id)
      if (member) member.ready = !!msg.ready
      pushLobbyState(lobby)
      break
    }

    case 'quality': {
      const lobby = getLobby(state.lobbyId)
      if (!lobby) return fail(ws, 'You are not in a lobby')
      if (lobby.hostId !== user.id) return fail(ws, 'Only the host can change quality')
      lobby.quality = {
        fps: Math.min(60, Math.max(5, Number(msg.fps) || lobby.quality.fps)),
        scale: Math.min(100, Math.max(25, Number(msg.scale) || lobby.quality.scale)),
        jpegQuality: Math.min(95, Math.max(20, Number(msg.jpegQuality) || lobby.quality.jpegQuality))
      }
      pushLobbyState(lobby)
      break
    }

    case 'start': {
      const lobby = getLobby(state.lobbyId)
      if (!lobby) return fail(ws, 'You are not in a lobby')
      if (lobby.hostId !== user.id) return fail(ws, 'Only the host can start the game')
      lobby.state = 'playing'
      broadcastLobby(lobby, { t: 'started', quality: lobby.quality, lobby: detail(lobby) })
      break
    }

    case 'stop': {
      const lobby = getLobby(state.lobbyId)
      if (!lobby || lobby.hostId !== user.id) return fail(ws, 'Only the host can stop the game')
      lobby.state = 'lobby'
      broadcastLobby(lobby, { t: 'stopped' })
      pushLobbyState(lobby)
      break
    }

    // Guest -> server -> host. The host injects the key into the game.
    case 'input': {
      const lobby = getLobby(state.lobbyId)
      if (!lobby || lobby.state !== 'playing') return
      const member = lobby.members.get(user.id)
      if (!member) return
      const hostSocket = socketsInLobby(lobby.id).find(s => s.ddn.user.id === lobby.hostId)
      if (!hostSocket) return
      send(hostSocket, {
        t: 'input',
        slot: member.slot,
        userId: user.id,
        action: String(msg.action || '').slice(0, 32),
        down: !!msg.down
      })
      break
    }

    case 'chat': {
      const lobby = getLobby(state.lobbyId)
      if (!lobby) return
      broadcastLobby(lobby, {
        t: 'chat',
        from: user.username,
        rainbow: !!user.rainbow,
        nameColor: user.nameColor || null,
        text: String(msg.text || '').slice(0, 300),
        at: Date.now()
      })
      break
    }

    case 'kick':
    case 'ban': {
      const lobby = getLobby(state.lobbyId)
      if (!lobby) return fail(ws, 'You are not in a lobby')
      if (lobby.hostId !== user.id && !isOwner(user)) return fail(ws, 'Only the host can do that')
      const targetId = msg.userId
      if (targetId === lobby.hostId) return fail(ws, 'The host cannot be removed')
      const target = lobby.members.get(targetId)
      const targetName = target ? target.user.username : 'player'
      if (msg.t === 'ban') banMember(lobby, targetId, targetName, msg.reason)
      else removeMember(lobby, targetId)
      for (const s of socketsInLobby(lobby.id)) {
        if (s.ddn.user.id === targetId) {
          send(s, { t: 'kicked', banned: msg.t === 'ban', reason: msg.reason || null })
          s.ddn.lobbyId = null
        }
      }
      pushLobbyState(lobby)
      break
    }

    case 'unban': {
      const lobby = getLobby(state.lobbyId)
      if (!lobby || (lobby.hostId !== user.id && !isOwner(user))) return fail(ws, 'Only the host can do that')
      lobby.bans.delete(msg.userId)
      pushLobbyState(lobby)
      break
    }

    // Host closes (deletes) the lobby; everyone returns to the lobby browser.
    case 'close': {
      const lobby = getLobby(state.lobbyId)
      if (!lobby) return fail(ws, 'You are not in a lobby')
      if (lobby.hostId !== user.id && !isOwner(user)) return fail(ws, 'Only the host can close the lobby')
      closeLobby(lobby, 'The host closed the lobby')
      break
    }

    case 'ping':
      send(ws, { t: 'pong', at: Date.now() })
      break

    default:
      fail(ws, 'Unknown message type: ' + msg.t)
  }
}

function handleBinary (ws, buffer) {
  const state = ws.ddn
  if (!state.user || !state.lobbyId) return
  const lobby = getLobby(state.lobbyId)
  if (!lobby || lobby.hostId !== state.user.id) return // only the host streams
  if (buffer[0] !== PAYLOAD_VIDEO) return
  for (const s of socketsInLobby(lobby.id)) {
    if (s === ws) continue
    if (s.readyState === s.OPEN && s.bufferedAmount < 4 * 1024 * 1024) {
      s.send(buffer, { binary: true })
    }
  }
}

function handleLeave (ws, reason) {
  const state = ws.ddn
  if (!state || !state.lobbyId) return
  const lobby = getLobby(state.lobbyId)
  state.lobbyId = null
  if (!lobby || !state.user) return
  if (lobby.hostId === state.user.id && reason === 'left') {
    // Host leaving without closing keeps the lobby alive for a rejoin,
    // but the game is stopped.
    lobby.state = 'lobby'
  }
  removeMember(lobby, state.user.id)
  if (lobby.members.size === 0) {
    deleteLobby(lobby.id)
    return
  }
  pushLobbyState(lobby)
}

export function closeLobby (lobby, message) {
  for (const s of socketsInLobby(lobby.id)) {
    send(s, { t: 'lobbyClosed', message })
    s.ddn.lobbyId = null
  }
  deleteLobby(lobby.id)
}

export function adminBroadcast (text) {
  for (const s of sockets) send(s, { t: 'announce', text })
}

export function disconnectUser (userId, message) {
  for (const s of sockets) {
    if (s.ddn.user && s.ddn.user.id === userId) {
      send(s, { t: 'forceLogout', message })
      setTimeout(() => { try { s.close() } catch {} }, 200)
    }
  }
}

export function onlineCount () {
  return sockets.size
}

export function onlineUsers () {
  return [...sockets]
    .filter(s => s.ddn.user)
    .map(s => ({ ...publicUser(s.ddn.user), lobbyId: s.ddn.lobbyId }))
}

export function serverStats () {
  return {
    online: onlineCount(),
    lobbies: allLobbies().length,
    playing: allLobbies().filter(l => l.state === 'playing').length,
    users: Object.keys(getDb().users).length,
    uptimeSec: Math.round(process.uptime())
  }
}
