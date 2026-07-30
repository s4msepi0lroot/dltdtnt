'use strict'
/**
 * CoopStream Relay Server
 * -----------------------
 * Лёгкий сервер-ретранслятор (без P2P): авторизация, лобби,
 * пересылка видеокадров (host -> guest) и ввода с клавиатуры (guest -> host).
 *
 * Зависимости: нет (только стандартная библиотека Node.js >= 18).
 *
 * Переменные окружения:
 *   PORT           - порт (по умолчанию 8080)
 *   AUTH_SECRET    - секрет для подписи токенов (обязательно в продакшене)
 *   DATA_FILE      - файл базы пользователей (по умолчанию ./data/users.json)
 *   ALLOW_REGISTER - '0' чтобы запретить регистрацию новых пользователей
 *   MAX_FRAME_KB   - максимальный размер кадра в КБ (по умолчанию 2048)
 */
const http = require('http')
const path = require('path')
const { attach } = require('./ws')
const { UserStore } = require('./store')
const { TokenService } = require('./auth')
const { LobbyManager } = require('./lobby')

const PORT = Number(process.env.PORT || 8080)
const AUTH_SECRET = process.env.AUTH_SECRET || 'dev-insecure-secret-change-me'
const DATA_FILE = process.env.DATA_FILE || path.join(process.cwd(), 'data', 'users.json')
const ALLOW_REGISTER = process.env.ALLOW_REGISTER !== '0'
const MAX_FRAME_KB = Number(process.env.MAX_FRAME_KB || 2048)

const store = new UserStore(DATA_FILE)
const tokens = new TokenService(AUTH_SECRET)
const lobbies = new LobbyManager()

if (AUTH_SECRET === 'dev-insecure-secret-change-me') {
  console.warn('[warn] AUTH_SECRET не задан — используется небезопасное значение по умолчанию')
}

// ---------------------------------------------------------------- HTTP часть
const server = http.createServer((req, res) => {
  if (req.url === '/health' || req.url === '/') {
    res.writeHead(200, { 'content-type': 'application/json' })
    res.end(
      JSON.stringify({
        ok: true,
        service: 'coopstream-relay',
        uptime: Math.round(process.uptime()),
        lobbies: lobbies.lobbies.size,
        users: store.users.size,
        allowRegister: ALLOW_REGISTER,
      })
    )
    return
  }
  res.writeHead(404, { 'content-type': 'text/plain' })
  res.end('not found')
})

// ------------------------------------------------------------ Вспомогательное
function fail(conn, code, message, extra = {}) {
  conn.sendJson({ t: 'error', code, message, ...extra })
}

function requireAuth(conn) {
  if (!conn.ctx.login) {
    fail(conn, 'unauthorized', 'Сначала выполните вход')
    return false
  }
  return true
}

// Клавиши, разрешённые каждой роли. Сервер фильтрует ввод ещё до клиента.
const ALLOWED_KEYS = {
  P1: new Set(['W', 'A', 'S', 'D', 'Z', 'X', 'P', 'C', 'LCtrl', 'RCtrl', 'LShift', 'RShift']),
  P2: new Set(['Up', 'Down', 'Left', 'Right', 'Enter', 'NumEnter', 'C', 'LCtrl', 'RCtrl', 'LShift', 'RShift']),
}

// ------------------------------------------------------------- WebSocket часть
attach(server, {
  path: '/ws',
  maxPayload: MAX_FRAME_KB * 1024,
  onConnection(conn) {
    conn.ctx = { login: null, lobbyCode: null, role: null }
    conn.sendJson({ t: 'hello', server: 'coopstream-relay', version: 1, allowRegister: ALLOW_REGISTER })

    conn.on('message', (msg) => {
      if (msg.type === 'binary') return onBinary(conn, msg.data)
      let m
      try {
        m = JSON.parse(msg.data)
      } catch (_) {
        return fail(conn, 'bad_json', 'Некорректный JSON')
      }
      try {
        onJson(conn, m)
      } catch (err) {
        console.error('handler error', err)
        fail(conn, 'internal', 'Внутренняя ошибка сервера')
      }
    })

    conn.on('close', () => {
      lobbies.detach(conn)
      if (conn.ctx.login) console.log(`[ws] disconnect ${conn.ctx.login}`)
    })

    conn.on('error', () => {})
  },
})

/** Обработка управляющих (JSON) сообщений. */
function onJson(conn, m) {
  switch (m.t) {
    // ---------------------------------------------------------- авторизация
    case 'register': {
      if (!ALLOW_REGISTER) return fail(conn, 'register_disabled', 'Регистрация отключена')
      const login = String(m.login || '').trim()
      const password = String(m.password || '')
      if (!/^[A-Za-z0-9_.-]{3,24}$/.test(login))
        return fail(conn, 'bad_login', 'Логин: 3-24 символа, латиница/цифры/._-')
      if (password.length < 6) return fail(conn, 'bad_password', 'Пароль: минимум 6 символов')
      try {
        store.create(login, password)
      } catch (err) {
        return fail(conn, 'user_exists', 'Такой логин уже занят')
      }
      conn.ctx.login = login
      console.log(`[auth] register ${login}`)
      return conn.sendJson({ t: 'auth_ok', login, token: tokens.sign(login) })
    }

    case 'login': {
      const user = store.verify(String(m.login || ''), String(m.password || ''))
      if (!user) return fail(conn, 'bad_credentials', 'Неверный логин или пароль')
      conn.ctx.login = user.login
      console.log(`[auth] login ${user.login}`)
      return conn.sendJson({ t: 'auth_ok', login: user.login, token: tokens.sign(user.login) })
    }

    case 'auth_token': {
      const payload = tokens.verify(String(m.token || ''))
      if (!payload) return fail(conn, 'bad_token', 'Токен недействителен, войдите заново')
      if (!store.has(payload.sub)) return fail(conn, 'bad_token', 'Пользователь удалён')
      conn.ctx.login = payload.sub
      return conn.sendJson({ t: 'auth_ok', login: payload.sub, token: String(m.token) })
    }

    // --------------------------------------------------------------- лобби
    case 'list_lobbies': {
      if (!requireAuth(conn)) return
      return conn.sendJson({ t: 'lobby_list', lobbies: lobbies.list() })
    }

    case 'create_lobby': {
      if (!requireAuth(conn)) return
      if (conn.ctx.lobbyCode) lobbies.detach(conn)
      const lobby = lobbies.create(conn, String(m.name || `${conn.ctx.login}'s game`).slice(0, 40), m.hostRole)
      conn.ctx.lobbyCode = lobby.code
      conn.ctx.role = lobby.hostRole
      conn.ctx.isHost = true
      console.log(`[lobby] created ${lobby.code} by ${conn.ctx.login}`)
      return conn.sendJson({ t: 'lobby_created', lobby: lobby.toPublic(), you: 'host', role: lobby.hostRole })
    }

    case 'join_lobby': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(m.code)
      if (!lobby) return fail(conn, 'no_lobby', 'Лобби с таким кодом не найдено')
      if (lobby.isFull) return fail(conn, 'lobby_full', 'В лобби уже два игрока')
      if (lobby.host === conn) return fail(conn, 'self_join', 'Вы уже хост этого лобби')
      if (conn.ctx.lobbyCode) lobbies.detach(conn)
      lobby.guest = conn
      conn.ctx.lobbyCode = lobby.code
      conn.ctx.role = lobby.guestRole
      conn.ctx.isHost = false
      console.log(`[lobby] ${conn.ctx.login} joined ${lobby.code}`)
      conn.sendJson({ t: 'lobby_joined', lobby: lobby.toPublic(), you: 'guest', role: lobby.guestRole })
      lobby.host.sendJson({ t: 'peer_joined', lobby: lobby.toPublic(), login: conn.ctx.login, role: lobby.guestRole })
      return
    }

    case 'leave_lobby': {
      if (!requireAuth(conn)) return
      lobbies.detach(conn)
      conn.ctx.lobbyCode = null
      conn.ctx.role = null
      return conn.sendJson({ t: 'lobby_left' })
    }

    case 'start': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(conn.ctx.lobbyCode)
      if (!lobby || lobby.host !== conn) return fail(conn, 'not_host', 'Запускать игру может только хост')
      if (!lobby.guest) return fail(conn, 'no_guest', 'Второй игрок ещё не подключился')
      lobby.started = true
      console.log(`[lobby] start ${lobby.code}`)
      lobby.broadcast({ t: 'started', lobby: lobby.toPublic() })
      return
    }

    case 'stop': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(conn.ctx.lobbyCode)
      if (!lobby || lobby.host !== conn) return fail(conn, 'not_host', 'Остановить игру может только хост')
      lobby.started = false
      lobby.broadcast({ t: 'stopped' })
      return
    }

    // ----------------------------------------------------------------- ввод
    case 'input': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(conn.ctx.lobbyCode)
      if (!lobby || !lobby.started) return
      const target = lobby.other(conn)
      if (!target) return
      // Ввод имеет смысл только от гостя к хосту (игра запущена на хосте).
      if (conn !== lobby.guest) return
      const key = String(m.key || '')
      if (!ALLOWED_KEYS[lobby.guestRole].has(key)) {
        return fail(conn, 'key_not_allowed', `Клавиша ${key} не разрешена для роли ${lobby.guestRole}`)
      }
      lobby.stats.inputs++
      target.sendJson({ t: 'input', key, down: !!m.down, role: lobby.guestRole, ts: m.ts || Date.now() })
      return
    }

    case 'release_all': {
      const lobby = lobbies.get(conn.ctx.lobbyCode)
      if (!lobby) return
      const target = lobby.other(conn)
      target?.sendJson({ t: 'release_all', role: conn.ctx.role })
      return
    }

    case 'chat': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(conn.ctx.lobbyCode)
      if (!lobby) return
      lobby.broadcast({ t: 'chat', from: conn.ctx.login, text: String(m.text || '').slice(0, 500) })
      return
    }

    case 'ping':
      return conn.sendJson({ t: 'pong', ts: m.ts || Date.now() })

    case 'stats': {
      const lobby = lobbies.get(conn.ctx.lobbyCode)
      return conn.sendJson({ t: 'stats', stats: lobby ? lobby.stats : null })
    }

    default:
      return fail(conn, 'unknown_type', `Неизвестный тип сообщения: ${m.t}`)
  }
}

/**
 * Бинарные сообщения = видеокадры от хоста. Сервер просто пересылает их гостю.
 * Формат пакета описан в docs/PROTOCOL.md.
 */
function onBinary(conn, data) {
  const lobby = lobbies.get(conn.ctx.lobbyCode)
  if (!lobby || !lobby.started) return
  if (conn !== lobby.host) return // транслирует только хост
  const guest = lobby.guest
  if (!guest || guest.closed) return
  // Backpressure: если гость не успевает — дропаем кадр, а не копим задержку.
  if (guest.bufferedAmount > 4 * 1024 * 1024) return
  lobby.stats.frames++
  lobby.stats.bytes += data.length
  guest.sendBinary(data)
}

server.listen(PORT, () => {
  console.log(`[coopstream] relay слушает :${PORT} (ws путь /ws, здоровье /health)`)
})

module.exports = { server, store, tokens, lobbies, ALLOWED_KEYS }
