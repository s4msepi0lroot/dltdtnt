// DeltaDotNet - HTTP API + WebSocket relay server entry point
import http from 'node:http'
import { URL } from 'node:url'
import { loadDb, getDb, save } from './store.js'
import { register, login, verifyToken, publicUser, isOwner, isStaff } from './auth.js'
import {
  createLobby, getLobby, listPublicLobbies, detail, allLobbies
} from './lobbies.js'
import {
  attachWebSocket, closeLobby, adminBroadcast, disconnectUser,
  serverStats, onlineUsers
} from './ws.js'

const PORT = Number(process.env.PORT || 8080)
const HOST = process.env.HOST || '0.0.0.0'

loadDb()

function json (res, code, body) {
  const data = JSON.stringify(body)
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS'
  })
  res.end(data)
}

function readBody (req) {
  return new Promise((resolve) => {
    let raw = ''
    req.on('data', chunk => {
      raw += chunk
      if (raw.length > 1_000_000) req.destroy()
    })
    req.on('end', () => {
      try { resolve(raw ? JSON.parse(raw) : {}) } catch { resolve({}) }
    })
  })
}

function authUser (req) {
  const header = req.headers.authorization || ''
  const token = header.startsWith('Bearer ') ? header.slice(7) : null
  return token ? verifyToken(token) : null
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://' + (req.headers.host || 'localhost'))
  const path = url.pathname.replace(/\/+$/, '') || '/'
  const method = req.method.toUpperCase()

  if (method === 'OPTIONS') return json(res, 204, {})

  try {
    // ---------- public ----------
    if (path === '/' || path === '/health') {
      return json(res, 200, { ok: true, name: 'DeltaDotNet', version: '1.0.0', ...serverStats() })
    }

    if (path === '/api/auth/register' && method === 'POST') {
      const body = await readBody(req)
      const result = register(body.username, body.password)
      if (result.error) return json(res, 400, { error: result.error })
      return json(res, 200, { token: result.token, user: publicUser(result.user) })
    }

    if (path === '/api/auth/login' && method === 'POST') {
      const body = await readBody(req)
      const result = login(body.username, body.password)
      if (result.error) return json(res, 401, { error: result.error })
      return json(res, 200, { token: result.token, user: publicUser(result.user) })
    }

    // ---------- authenticated ----------
    const user = authUser(req)
    if (path.startsWith('/api/') && !user) return json(res, 401, { error: 'Not authenticated' })
    if (user && getDb().globalBans[user.id]) return json(res, 403, { error: 'Account banned' })

    if (path === '/api/me' && method === 'GET') {
      return json(res, 200, { user: publicUser(user), motd: getDb().motd })
    }

    if (path === '/api/lobbies' && method === 'GET') {
      const all = listPublicLobbies()
      // Closed lobbies stay visible so they can be joined by code/password,
      // but they are flagged so the client can show a lock icon.
      return json(res, 200, { lobbies: all })
    }

    if (path === '/api/lobbies' && method === 'POST') {
      const body = await readBody(req)
      const lobby = createLobby(user, body)
      return json(res, 200, { lobby: detail(lobby) })
    }

    const lobbyMatch = path.match(/^\/api\/lobbies\/([A-Za-z0-9]+)$/)
    if (lobbyMatch) {
      const lobby = getLobby(lobbyMatch[1])
      if (!lobby) return json(res, 404, { error: 'Lobby not found' })
      if (method === 'GET') return json(res, 200, { lobby: detail(lobby) })
      if (method === 'DELETE') {
        if (lobby.hostId !== user.id && !isStaff(user)) {
          return json(res, 403, { error: 'Only the host can close this lobby' })
        }
        closeLobby(lobby, 'The lobby was closed')
        return json(res, 200, { ok: true })
      }
    }

    // ---------- admin (owner only, account: s4msepi0l) ----------
    if (path.startsWith('/api/admin')) {
      if (!isOwner(user)) return json(res, 403, { error: 'Admin panel is restricted to the owner account' })
      const db = getDb()

      if (path === '/api/admin/stats' && method === 'GET') {
        return json(res, 200, { stats: serverStats(), online: onlineUsers() })
      }

      if (path === '/api/admin/users' && method === 'GET') {
        const q = (url.searchParams.get('q') || '').toLowerCase()
        const users = Object.values(db.users)
          .filter(u => !q || u.username.toLowerCase().includes(q))
          .sort((a, b) => a.username.localeCompare(b.username))
          .map(publicUser)
        return json(res, 200, { users })
      }

      const userMatch = path.match(/^\/api\/admin\/users\/([^/]+)$/)
      if (userMatch && method === 'PATCH') {
        const target = db.users[userMatch[1]]
        if (!target) return json(res, 404, { error: 'User not found' })
        const body = await readBody(req)
        if (typeof body.rainbow === 'boolean') target.rainbow = body.rainbow
        if ('nameColor' in body) target.nameColor = body.nameColor || null
        if ('badge' in body) target.badge = body.badge ? String(body.badge).slice(0, 12) : null
        if (body.role && ['user', 'admin'].includes(body.role) && target.role !== 'owner') {
          target.role = body.role
        }
        if (typeof body.username === 'string' && body.username !== target.username) {
          const key = body.username.toLowerCase()
          if (!db.usernames[key]) {
            delete db.usernames[target.username.toLowerCase()]
            target.username = body.username
            db.usernames[key] = target.id
          }
        }
        if (typeof body.banned === 'boolean') {
          if (body.banned) {
            db.globalBans[target.id] = { reason: body.reason || 'No reason given', at: Date.now(), by: user.username }
            disconnectUser(target.id, 'You were banned: ' + (body.reason || 'no reason'))
          } else {
            delete db.globalBans[target.id]
          }
        }
        save()
        return json(res, 200, { user: publicUser(target) })
      }

      if (userMatch && method === 'DELETE') {
        const target = db.users[userMatch[1]]
        if (!target) return json(res, 404, { error: 'User not found' })
        if (isOwner(target)) return json(res, 400, { error: 'The owner account cannot be deleted' })
        delete db.usernames[target.username.toLowerCase()]
        delete db.users[target.id]
        delete db.globalBans[target.id]
        disconnectUser(target.id, 'Your account was deleted')
        save()
        return json(res, 200, { ok: true })
      }

      if (path === '/api/admin/lobbies' && method === 'GET') {
        return json(res, 200, { lobbies: allLobbies().map(detail) })
      }

      const adminLobbyMatch = path.match(/^\/api\/admin\/lobbies\/([A-Za-z0-9]+)$/)
      if (adminLobbyMatch && method === 'DELETE') {
        const lobby = getLobby(adminLobbyMatch[1])
        if (!lobby) return json(res, 404, { error: 'Lobby not found' })
        closeLobby(lobby, 'A moderator closed this lobby')
        return json(res, 200, { ok: true })
      }

      if (path === '/api/admin/broadcast' && method === 'POST') {
        const body = await readBody(req)
        adminBroadcast(String(body.text || '').slice(0, 300))
        return json(res, 200, { ok: true })
      }

      if (path === '/api/admin/motd' && method === 'POST') {
        const body = await readBody(req)
        db.motd = String(body.motd || '').slice(0, 300)
        save()
        return json(res, 200, { motd: db.motd })
      }
    }

    return json(res, 404, { error: 'Not found' })
  } catch (err) {
    console.error('[http] error:', err)
    return json(res, 500, { error: 'Internal server error' })
  }
})

attachWebSocket(server)

server.listen(PORT, HOST, () => {
  console.log('DeltaDotNet server listening on http://' + HOST + ':' + PORT + ' (ws path /ws)')
})
