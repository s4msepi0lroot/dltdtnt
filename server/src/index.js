'use strict'
/**
 * DeltaDotNet Relay Server
 * ------------------------
 * Лёгкий сервер-ретранслятор (без P2P) для совместной игры в Deltarune:
 * авторизация, лобби на 2-4 игроков, пересылка видеокадров (хост -> всем)
 * и действий управления (гости -> хосту).
 *
 * ВАЖНО про управление: сервер ничего не знает про конкретные клавиши.
 * Клиент превращает нажатую клавишу в логическое действие (Up, Confirm, ...),
 * а хост обратно превращает действие в ту клавишу, которую ждёт игра для
 * этого игрока. Поэтому каждый участник может назначить себе любые удобные
 * кнопки, никак не мешая остальным.
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
const { LobbyManager, normalizeMaxPlayers, MIN_PLAYERS, MAX_PLAYERS } = require('./lobby')

const PORT = Number(process.env.PORT || 8080)
const AUTH_SECRET = process.env.AUTH_SECRET || 'dev-insecure-secret-change-me'
const DATA_FILE = process.env.DATA_FILE || path.join(process.cwd(), 'data', 'users.json')
const ALLOW_REGISTER = process.env.ALLOW_REGISTER !== '0'
const MAX_FRAME_KB = Number(process.env.MAX_FRAME_KB || 2048)
const MAX_BUFFERED = 4 * 1024 * 1024

const store = new UserStore(DATA_FILE)
const tokens = new TokenService(AUTH_SECRET)
const lobbies = new LobbyManager()

if (AUTH_SECRET === 'dev-insecure-secret-change-me') {
  console.warn('[warn] AUTH_SECRET не задан — используется небезопасное значение по умолчанию')
}

/**
 * Список логических действий. Это единственное, что сервер валидирует
 * в сообщениях ввода. Какая физическая клавиша соответствует действию —
 * личное дело каждого клиента.
 */
const ACTIONS = new Set([
  'Up',
  'Down',
  'Left',
  'Right',
  'Confirm',
  'Cancel',
  'Menu',
  'Extra1',
  'Extra2',
])

// ---------------------------------------------------------------- HTTP часть
const server = http.createServer((req, res) => {
  if (req.url === '/health' || req.url === '/') {
    res.writeHead(200, { 'content-type': 'application/json' })
    res.end(
      JSON.stringify({
        ok: true,
        service: 'deltadotnet-relay',
        uptime: Math.round(process.uptime()),
        lobbies: lobbies.lobbies.size,
        users: store.users.size,
        allowRegister: ALLOW_REGISTER,
        maxPlayers: MAX_PLAYERS,
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

// ------------------------------------------------------------- WebSocket часть
attach(server, {
  path: '/ws',
  maxPayload: MAX_FRAME_KB * 1024,
  onConnection(conn) {
    conn.ctx = { login: null, lobbyCode: null, role: null, isHost: false }
    conn.sendJson({
      t: 'hello',
      server: 'deltadotnet-relay',
      version: 2,
      allowRegister: ALLOW_REGISTER,
      minPlayers: MIN_PLAYERS,
      maxPlayers: MAX_PLAYERS,
      actions: [...ACTIONS],
    })

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
      const maxPlayers = normalizeMaxPlayers(m.maxPlayers === undefined ? 2 : m.maxPlayers)
      if (maxPlayers === null)
        return fail(conn, 'bad_max_players', `Игроков может быть от ${MIN_PLAYERS} до ${MAX_PLAYERS}`)
      if (conn.ctx.lobbyCode) lobbies.detach(conn)
      const name = String(m.name || `Игра ${conn.ctx.login}`).slice(0, 40)
      const lobby = lobbies.create(conn, name, maxPlayers)
      conn.ctx.lobbyCode = lobby.code
      conn.ctx.role = 'P1'
      conn.ctx.isHost = true
      console.log(`[lobby] created ${lobby.code} by ${conn.ctx.login} (${maxPlayers} игроков)`)
      return conn.sendJson({ t: 'lobby_created', lobby: lobby.toPublic(), you: 'host', role: 'P1' })
    }

    case 'join_lobby': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(m.code)
      if (!lobby) return fail(conn, 'no_lobby', 'Лобби с таким кодом не найдено')
      if (lobby.host === conn) return fail(conn, 'self_join', 'Вы уже хост этого лобби')
      if (lobby.isFull) return fail(conn, 'lobby_full', 'В лобби уже нет свободных мест')
      if (conn.ctx.lobbyCode) lobbies.detach(conn)

      const role = lobby.addGuest(conn)
      if (!role) return fail(conn, 'lobby_full', 'В лобби уже нет свободных мест')
      conn.ctx.lobbyCode = lobby.code
      conn.ctx.role = role
      conn.ctx.isHost = false
      console.log(`[lobby] ${conn.ctx.login} joined ${lobby.code} as ${role}`)

      conn.sendJson({ t: 'lobby_joined', lobby: lobby.toPublic(), you: 'guest', role })
      lobby.broadcast(
        { t: 'peer_joined', lobby: lobby.toPublic(), login: conn.ctx.login, role },
        conn
      )
      return
    }

    case 'leave_lobby': {
      if (!requireAuth(conn)) return
      lobbies.detach(conn)
      return conn.sendJson({ t: 'lobby_left' })
    }

    case 'start': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(conn.ctx.lobbyCode)
      if (!lobby || lobby.host !== conn) return fail(conn, 'not_host', 'Запускать игру может только хост')
      if (lobby.playerCount < 2) return fail(conn, 'no_guest', 'Никто ещё не подключился')
      lobby.running = true
      console.log(`[lobby] start ${lobby.code}`)
      lobby.broadcast({ t: 'started', lobby: lobby.toPublic() })
      return
    }

    case 'stop': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(conn.ctx.lobbyCode)
      if (!lobby || lobby.host !== conn) return fail(conn, 'not_host', 'Остановить игру может только хост')
      lobby.running = false
      lobby.broadcast({ t: 'stopped' })
      return
    }

    // ----------------------------------------------------------------- ввод
    case 'input': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(conn.ctx.lobbyCode)
      if (!lobby) return fail(conn, 'no_lobby', 'Вы не в лобби')
      if (lobby.host === conn) return fail(conn, 'not_guest', 'Хост играет напрямую, его ввод не пересылается')

      const action = String(m.action || '')
      if (!ACTIONS.has(action)) return fail(conn, 'bad_action', 'Неизвестное действие: ' + action)

      lobby.host.sendJson({
        t: 'input',
        role: conn.ctx.role,
        login: conn.ctx.login,
        action,
        down: !!m.down,
      })
      return
    }

    case 'release_all': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(conn.ctx.lobbyCode)
      if (!lobby || lobby.host === conn) return
      lobby.host.sendJson({ t: 'release_all', role: conn.ctx.role, login: conn.ctx.login })
      return
    }

    // ------------------------------------------------------------- прочее
    case 'chat': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(conn.ctx.lobbyCode)
      if (!lobby) return fail(conn, 'no_lobby', 'Вы не в лобби')
      const text = String(m.text || '').slice(0, 300)
      if (!text) return
      lobby.broadcast({ t: 'chat', from: conn.ctx.login, role: conn.ctx.role, text })
      return
    }

    case 'ping':
      return conn.sendJson({ t: 'pong', time: Date.now() })

    case 'stats': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(conn.ctx.lobbyCode)
      if (!lobby) return fail(conn, 'no_lobby', 'Вы не в лобби')
      return conn.sendJson({
        t: 'stats',
        stats: {
          frames: lobby.frames,
          bytes: lobby.bytes,
          players: lobby.playerCount,
          maxPlayers: lobby.maxPlayers,
          running: lobby.running,
        },
      })
    }

    default:
      return fail(conn, 'unknown_type', 'Неизвестный тип сообщения: ' + m.t)
  }
}

/**
 * Бинарные сообщения — это видеокадры. Шлёт их только хост, получают
 * все остальные. Если конкретный зритель не успевает вычитывать — кадр для
 * него пропускается, чтобы не растить задержку у остальных.
 */
function onBinary(conn, data) {
  const lobby = lobbies.get(conn.ctx.lobbyCode)
  if (!lobby || lobby.host !== conn) return

  lobby.frames++
  lobby.bytes += data.length

  for (const guest of lobby.guests) {
    if (guest.bufferedAmount > MAX_BUFFERED) continue
    guest.sendBinary(data)
  }
}

server.listen(PORT, () => {
  console.log(`[deltadotnet] relay слушает порт ${PORT}`)
  console.log(`[deltadotnet] health: http://127.0.0.1:${PORT}/health`)
})

module.exports = { server, store, tokens, lobbies, ACTIONS }
