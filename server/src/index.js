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
 * а хост обратно превращает действие в ту клавишу, которую ждёт игра.
 *
 * Зависимости: нет (только стандартная библиотека Node.js >= 18).
 *
 * Переменные окружения:
 *   PORT           - порт (по умолчанию 8080)
 *   AUTH_SECRET    - секрет для подписи токенов (обязательно в продакшене)
 *   DATA_FILE      - файл базы пользователей (по умолчанию ./data/users.json)
 *   ALLOW_REGISTER - '0' чтобы запретить регистрацию новых пользователей
 *   MAX_FRAME_KB   - максимальный размер кадра в КБ (по умолчанию 2048)
 *   ADMIN_LOGIN    - логин владельца сервера (по умолчанию s4msepi0l)
 */
const http = require('http')
const path = require('path')
const { attach } = require('./ws')
const { UserStore } = require('./store')
const { TokenService } = require('./auth')
const {
  LobbyManager,
  normalizeMaxPlayers,
  normalizeJoinMode,
  MIN_PLAYERS,
  MAX_PLAYERS,
  JOIN_MODES,
} = require('./lobby')

const PORT = Number(process.env.PORT || 8080)
const AUTH_SECRET = process.env.AUTH_SECRET || 'dev-insecure-secret-change-me'
const DATA_FILE = process.env.DATA_FILE || path.join(process.cwd(), 'data', 'users.json')
const ALLOW_REGISTER = process.env.ALLOW_REGISTER !== '0'
const MAX_FRAME_KB = Number(process.env.MAX_FRAME_KB || 2048)
const ADMIN_LOGIN = process.env.ADMIN_LOGIN || 's4msepi0l'
const MAX_BUFFERED = 4 * 1024 * 1024
const STARTED_AT = Date.now()

const store = new UserStore(DATA_FILE, ADMIN_LOGIN)
const tokens = new TokenService(AUTH_SECRET)
const lobbies = new LobbyManager()

/** Все живые соединения - нужно админке для списка онлайна и рассылки. */
const connections = new Set()

if (AUTH_SECRET === 'dev-insecure-secret-change-me') {
  console.warn('[warn] AUTH_SECRET не задан - используется небезопасное значение по умолчанию')
}

/**
 * Список логических действий. Это единственное, что сервер валидирует
 * в сообщениях ввода. Какая физическая клавиша соответствует действию -
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
        online: connections.size,
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

/** Проверка прав админа для всех admin_* команд. */
function requireAdmin(conn) {
  if (!requireAuth(conn)) return false
  if (conn.ctx.role !== 'admin') {
    fail(conn, 'forbidden', 'Команда доступна только администратору')
    return false
  }
  return true
}

/** Возвращает лобби текущего соединения при условии, что оно - хост. */
function hostLobby(conn, errorMessage) {
  const lobby = lobbies.get(conn.ctx.lobbyCode)
  if (!lobby || lobby.host !== conn) {
    fail(conn, 'not_host', errorMessage)
    return null
  }
  return lobby
}

/** Соединения конкретного пользователя (один логин может быть открыт дважды). */
function connectionsOf(login) {
  const key = String(login || '').toLowerCase()
  return [...connections].filter((c) => String(c.ctx.login || '').toLowerCase() === key)
}

/** Отправляет хосту обновлённое состояние лобби со списками допуска и банов. */
function sendHostView(lobby) {
  lobby.host.sendJson({ t: 'lobby_state', lobby: lobby.toPublic(true) })
}

/** Обновляет косметику на всех соединениях игрока и сообщает ему об этом. */
function pushCosmetic(login) {
  const user = store.publicUser(login)
  if (!user) return
  for (const conn of connectionsOf(login)) {
    conn.ctx.cosmetic = user.cosmetic
    conn.ctx.role = user.role
    conn.sendJson({ t: 'profile', user })
  }
  // Если игрок сейчас в лобби - обновляем список игроков у всех участников.
  for (const conn of connectionsOf(login)) {
    const lobby = lobbies.get(conn.ctx.lobbyCode)
    if (lobby) lobby.broadcast({ t: 'lobby_state', lobby: lobby.toPublic() })
  }
}

/** Отключает все соединения пользователя (после бана или удаления). */
function disconnectUser(login, reason) {
  for (const conn of connectionsOf(login)) {
    conn.sendJson({ t: 'kicked', scope: 'server', reason })
    lobbies.detach(conn)
    conn.close(reason)
  }
}

// ------------------------------------------------------------- WebSocket часть
attach(server, {
  path: '/ws',
  maxPayload: MAX_FRAME_KB * 1024,
  onConnection(conn) {
    conn.ctx = { login: null, lobbyCode: null, role: null, isHost: false, cosmetic: null }
    connections.add(conn)

    conn.sendJson({
      t: 'hello',
      server: 'deltadotnet-relay',
      version: 3,
      allowRegister: ALLOW_REGISTER,
      minPlayers: MIN_PLAYERS,
      maxPlayers: MAX_PLAYERS,
      actions: [...ACTIONS],
      joinModes: JOIN_MODES,
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
      connections.delete(conn)
      lobbies.detach(conn)
      if (conn.ctx.login) console.log('[ws] disconnect ' + conn.ctx.login)
    })

    conn.on('error', () => {})
  },
})

/** Общий ответ при успешном входе: логин, токен, роль и украшения. */
function completeAuth(conn, user, token) {
  if (user.banned) {
    fail(conn, 'banned', 'Вы забанены на сервере: ' + (user.banReason || 'без указания причины'))
    return
  }
  conn.ctx.login = user.login
  conn.ctx.role = user.role
  conn.ctx.cosmetic = user.cosmetic
  store.touch(user.login)
  conn.sendJson({
    t: 'auth_ok',
    login: user.login,
    token,
    role: user.role,
    isAdmin: user.role === 'admin',
    cosmetic: user.cosmetic,
  })
}

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
      let user
      try {
        user = store.create(login, password)
      } catch (err) {
        return fail(conn, 'user_exists', 'Такой логин уже занят')
      }
      console.log('[auth] register ' + login + (user.role === 'admin' ? ' (admin)' : ''))
      return completeAuth(conn, user, tokens.sign(user.login))
    }

    case 'login': {
      const user = store.verify(String(m.login || ''), String(m.password || ''))
      if (!user) return fail(conn, 'bad_credentials', 'Неверный логин или пароль')
      console.log('[auth] login ' + user.login)
      return completeAuth(conn, user, tokens.sign(user.login))
    }

    case 'auth_token': {
      const payload = tokens.verify(String(m.token || ''))
      if (!payload) return fail(conn, 'bad_token', 'Токен недействителен, войдите заново')
      const user = store.publicUser(payload.sub)
      if (!user) return fail(conn, 'bad_token', 'Пользователь удалён')
      return completeAuth(conn, user, String(m.token))
    }

    case 'whoami': {
      if (!requireAuth(conn)) return
      return conn.sendJson({ t: 'profile', user: store.publicUser(conn.ctx.login) })
    }

    // --------------------------------------------------------------- лобби
    case 'list_lobbies': {
      if (!requireAuth(conn)) return
      const includePrivate = conn.ctx.role === 'admin'
      return conn.sendJson({ t: 'lobby_list', lobbies: lobbies.list({ includePrivate }) })
    }

    case 'create_lobby': {
      if (!requireAuth(conn)) return
      const maxPlayers = normalizeMaxPlayers(m.maxPlayers === undefined ? 2 : m.maxPlayers)
      if (maxPlayers === null)
        return fail(conn, 'bad_max_players', 'Игроков может быть от ' + MIN_PLAYERS + ' до ' + MAX_PLAYERS)

      const joinMode = normalizeJoinMode(m.joinMode)
      if (joinMode === null) return fail(conn, 'bad_join_mode', 'Неизвестный режим входа')

      const password = String(m.password || '')
      if (joinMode === 'password' && password.length < 1)
        return fail(conn, 'bad_lobby_password', 'Для закрытого лобби нужен пароль')

      if (conn.ctx.lobbyCode) lobbies.detach(conn)
      const name = String(m.name || 'Игра ' + conn.ctx.login).slice(0, 40)
      const lobby = lobbies.create(conn, name, maxPlayers, {
        visibility: m.visibility === 'private' ? 'private' : 'public',
        joinMode,
        password: password.slice(0, 60),
        allowList: Array.isArray(m.allowList) ? m.allowList.slice(0, 20) : [],
      })
      conn.ctx.lobbyCode = lobby.code
      conn.ctx.role2 = 'P1'
      conn.ctx.isHost = true
      console.log(
        '[lobby] created ' + lobby.code + ' by ' + conn.ctx.login +
        ' (' + maxPlayers + ' игроков, ' + lobby.visibility + '/' + lobby.joinMode + ')'
      )
      return conn.sendJson({
        t: 'lobby_created',
        lobby: lobby.toPublic(true),
        you: 'host',
        role: 'P1',
      })
    }

    case 'join_lobby': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(m.code)
      if (!lobby) return fail(conn, 'no_lobby', 'Лобби с таким кодом не найдено')
      if (lobby.host === conn) return fail(conn, 'self_join', 'Вы уже хост этого лобби')

      const problem = lobby.checkJoin(conn.ctx.login, m.password)
      if (problem === 'lobby_banned')
        return fail(conn, 'lobby_banned', 'Вас забанили в этом лобби')
      if (problem === 'lobby_full') return fail(conn, 'lobby_full', 'В лобби уже нет свободных мест')
      if (problem === 'bad_lobby_password')
        return fail(conn, 'bad_lobby_password', 'Неверный пароль лобби')
      if (problem === 'not_invited')
        return fail(conn, 'not_invited', 'Вашего логина нет в списке допуска')

      if (conn.ctx.lobbyCode) lobbies.detach(conn)

      const role = lobby.addGuest(conn)
      if (!role) return fail(conn, 'lobby_full', 'В лобби уже нет свободных мест')
      conn.ctx.lobbyCode = lobby.code
      conn.ctx.role2 = role
      conn.ctx.isHost = false
      console.log('[lobby] ' + conn.ctx.login + ' joined ' + lobby.code + ' as ' + role)

      conn.sendJson({ t: 'lobby_joined', lobby: lobby.toPublic(), you: 'guest', role })
      lobby.broadcast(
        { t: 'peer_joined', lobby: lobby.toPublic(), login: conn.ctx.login, role },
        conn
      )
      sendHostView(lobby)
      return
    }

    case 'leave_lobby': {
      if (!requireAuth(conn)) return
      lobbies.detach(conn)
      return conn.sendJson({ t: 'lobby_left' })
    }

    /** Хост удаляет лобби целиком и возвращается в главное меню. */
    case 'close_lobby': {
      if (!requireAuth(conn)) return
      const lobby = hostLobby(conn, 'Закрыть лобби может только хост')
      if (!lobby) return
      console.log('[lobby] closed ' + lobby.code + ' by host')
      lobbies.close(lobby, 'хост закрыл лобби')
      return
    }

    /** Изменение настроек доступа уже созданного лобби. */
    case 'lobby_settings': {
      if (!requireAuth(conn)) return
      const lobby = hostLobby(conn, 'Менять настройки может только хост')
      if (!lobby) return

      if (m.visibility !== undefined) {
        lobby.visibility = m.visibility === 'private' ? 'private' : 'public'
      }
      if (m.joinMode !== undefined) {
        const mode = normalizeJoinMode(m.joinMode)
        if (mode === null) return fail(conn, 'bad_join_mode', 'Неизвестный режим входа')
        lobby.joinMode = mode
      }
      if (m.password !== undefined) {
        lobby.password = String(m.password || '').slice(0, 60)
      }
      if (lobby.joinMode === 'password' && !lobby.password) {
        return fail(conn, 'bad_lobby_password', 'Для входа по паролю нужно задать пароль')
      }
      if (Array.isArray(m.allowList)) {
        lobby.allowList = new Set(m.allowList.slice(0, 20).map((l) => String(l).toLowerCase()))
      }
      sendHostView(lobby)
      lobby.broadcast({ t: 'lobby_state', lobby: lobby.toPublic() }, lobby.host)
      return
    }

    /** Выгнать игрока из лобби (без бана, может вернуться). */
    case 'kick': {
      if (!requireAuth(conn)) return
      const lobby = hostLobby(conn, 'Выгонять игроков может только хост')
      if (!lobby) return
      const target = lobby.findByLogin(m.login)
      if (!target || target === lobby.host)
        return fail(conn, 'no_player', 'Такого игрока нет в лобби')

      const reason = String(m.reason || 'хост выгнал вас из лобби').slice(0, 120)
      target.sendJson({ t: 'kicked', scope: 'lobby', code: lobby.code, reason })
      lobbies.detach(target)
      console.log('[lobby] kick ' + m.login + ' from ' + lobby.code)
      sendHostView(lobby)
      return
    }

    /** Забанить игрока в этом лобби: выгоняем и больше не пускаем. */
    case 'ban': {
      if (!requireAuth(conn)) return
      const lobby = hostLobby(conn, 'Банить игроков может только хост')
      if (!lobby) return
      const login = String(m.login || '').trim()
      if (!login) return fail(conn, 'no_player', 'Не указан логин')
      if (login.toLowerCase() === String(conn.ctx.login).toLowerCase())
        return fail(conn, 'no_player', 'Себя забанить нельзя')

      const reason = String(m.reason || '').slice(0, 120)
      lobby.ban(login, reason)
      const target = lobby.findByLogin(login)
      if (target && target !== lobby.host) {
        target.sendJson({
          t: 'kicked',
          scope: 'lobby',
          code: lobby.code,
          banned: true,
          reason: reason || 'хост забанил вас в этом лобби',
        })
        lobbies.detach(target)
      }
      console.log('[lobby] ban ' + login + ' in ' + lobby.code)
      sendHostView(lobby)
      return
    }

    case 'unban': {
      if (!requireAuth(conn)) return
      const lobby = hostLobby(conn, 'Снимать бан может только хост')
      if (!lobby) return
      lobby.unban(String(m.login || ''))
      sendHostView(lobby)
      return
    }

    case 'start': {
      if (!requireAuth(conn)) return
      const lobby = hostLobby(conn, 'Запускать игру может только хост')
      if (!lobby) return
      if (lobby.playerCount < 2) return fail(conn, 'no_guest', 'Никто ещё не подключился')
      lobby.running = true
      console.log('[lobby] start ' + lobby.code)
      lobby.broadcast({ t: 'started', lobby: lobby.toPublic() })
      return
    }

    case 'stop': {
      if (!requireAuth(conn)) return
      const lobby = hostLobby(conn, 'Остановить игру может только хост')
      if (!lobby) return
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
        role: lobby.roleOf(conn),
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
      lobby.host.sendJson({ t: 'release_all', role: lobby.roleOf(conn), login: conn.ctx.login })
      return
    }

    // ------------------------------------------------------------- прочее
    case 'chat': {
      if (!requireAuth(conn)) return
      const lobby = lobbies.get(conn.ctx.lobbyCode)
      if (!lobby) return fail(conn, 'no_lobby', 'Вы не в лобби')
      const text = String(m.text || '').slice(0, 300)
      if (!text) return
      lobby.broadcast({
        t: 'chat',
        from: conn.ctx.login,
        role: lobby.roleOf(conn),
        cosmetic: conn.ctx.cosmetic || null,
        text,
      })
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

    // ------------------------------------------------------------- админка
    // Все команды ниже доступны только учётной записи с ролью admin.

    case 'admin_users': {
      if (!requireAdmin(conn)) return
      const online = new Set([...connections].map((c) => String(c.ctx.login || '').toLowerCase()))
      const users = store.list().map((u) => ({ ...u, online: online.has(u.login.toLowerCase()) }))
      return conn.sendJson({ t: 'admin_users', users })
    }

    case 'admin_lobbies': {
      if (!requireAdmin(conn)) return
      return conn.sendJson({
        t: 'admin_lobbies',
        lobbies: [...lobbies.lobbies.values()].map((l) => l.toPublic(true)),
      })
    }

    case 'admin_stats': {
      if (!requireAdmin(conn)) return
      let frames = 0
      let bytes = 0
      for (const l of lobbies.lobbies.values()) {
        frames += l.frames
        bytes += l.bytes
      }
      return conn.sendJson({
        t: 'admin_stats',
        stats: {
          uptimeSec: Math.round((Date.now() - STARTED_AT) / 1000),
          users: store.users.size,
          online: connections.size,
          lobbies: lobbies.lobbies.size,
          frames,
          bytes,
          allowRegister: ALLOW_REGISTER,
          adminLogin: ADMIN_LOGIN,
        },
      })
    }

    /** Выдача переливающегося ника, своего цвета или тега. */
    case 'admin_set_cosmetic': {
      if (!requireAdmin(conn)) return
      const login = String(m.login || '')
      if (!store.has(login)) return fail(conn, 'no_user', 'Пользователь не найден')
      const user = store.setCosmetic(login, {
        rainbow: !!m.rainbow,
        color: m.color,
        tag: m.tag,
      })
      console.log('[admin] cosmetic ' + login + ' rainbow=' + !!m.rainbow)
      pushCosmetic(login)
      return conn.sendJson({ t: 'admin_user', user })
    }

    case 'admin_set_role': {
      if (!requireAdmin(conn)) return
      const login = String(m.login || '')
      if (!store.has(login)) return fail(conn, 'no_user', 'Пользователь не найден')
      let user
      try {
        user = store.setRole(login, String(m.role || 'user'))
      } catch (err) {
        return fail(conn, err.message, 'Нельзя изменить роль этого пользователя')
      }
      pushCosmetic(login)
      return conn.sendJson({ t: 'admin_user', user })
    }

    case 'admin_ban': {
      if (!requireAdmin(conn)) return
      const login = String(m.login || '')
      if (!store.has(login)) return fail(conn, 'no_user', 'Пользователь не найден')
      let user
      try {
        user = store.setBanned(login, true, m.reason)
      } catch (err) {
        return fail(conn, 'cannot_ban_admin', 'Администратора забанить нельзя')
      }
      console.log('[admin] ban ' + login)
      disconnectUser(login, 'вы забанены на сервере')
      return conn.sendJson({ t: 'admin_user', user })
    }

    case 'admin_unban': {
      if (!requireAdmin(conn)) return
      const login = String(m.login || '')
      if (!store.has(login)) return fail(conn, 'no_user', 'Пользователь не найден')
      const user = store.setBanned(login, false)
      return conn.sendJson({ t: 'admin_user', user })
    }

    case 'admin_set_password': {
      if (!requireAdmin(conn)) return
      const login = String(m.login || '')
      const password = String(m.password || '')
      if (!store.has(login)) return fail(conn, 'no_user', 'Пользователь не найден')
      if (password.length < 6) return fail(conn, 'bad_password', 'Пароль: минимум 6 символов')
      const user = store.setPassword(login, password)
      return conn.sendJson({ t: 'admin_user', user })
    }

    case 'admin_delete_user': {
      if (!requireAdmin(conn)) return
      const login = String(m.login || '')
      try {
        if (!store.remove(login)) return fail(conn, 'no_user', 'Пользователь не найден')
      } catch (err) {
        return fail(conn, 'cannot_delete_owner', 'Владельца сервера удалить нельзя')
      }
      disconnectUser(login, 'ваша учётная запись удалена')
      return conn.sendJson({ t: 'admin_user_deleted', login })
    }

    /** Закрыть любое лобби на сервере по коду. */
    case 'admin_close_lobby': {
      if (!requireAdmin(conn)) return
      const lobby = lobbies.get(m.code)
      if (!lobby) return fail(conn, 'no_lobby', 'Лобби не найдено')
      lobbies.close(lobby, 'лобби закрыто администратором')
      console.log('[admin] close lobby ' + m.code)
      return conn.sendJson({ t: 'admin_lobby_closed', code: String(m.code).toUpperCase() })
    }

    /** Сообщение всем онлайн-игрокам. */
    case 'admin_broadcast': {
      if (!requireAdmin(conn)) return
      const text = String(m.text || '').slice(0, 300)
      if (!text) return fail(conn, 'bad_text', 'Пустое сообщение')
      let sent = 0
      for (const c of connections) {
        if (!c.ctx.login) continue
        c.sendJson({ t: 'announce', from: conn.ctx.login, text })
        sent++
      }
      return conn.sendJson({ t: 'admin_broadcast_ok', sent })
    }

    default:
      return fail(conn, 'unknown_type', 'Неизвестный тип сообщения: ' + m.t)
  }
}

/**
 * Бинарные сообщения - это видеокадры. Шлёт их только хост, получают
 * все остальные. Если конкретный зритель не успевает вычитывать - кадр для
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
  console.log('[deltadotnet] relay слушает порт ' + PORT)
  console.log('[deltadotnet] health: http://127.0.0.1:' + PORT + '/health')
  console.log('[deltadotnet] администратор: ' + ADMIN_LOGIN)
})

module.exports = { server, store, tokens, lobbies, connections, ACTIONS }
