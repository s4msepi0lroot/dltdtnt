'use strict'
/**
 * Лобби DeltaDotNet.
 *
 * В лобби от 2 до 4 игроков. Хост всегда занимает роль P1: именно на его
 * машине запущена игра, он транслирует картинку и принимает чужой ввод.
 * Остальные садятся в свободные слоты P2, P3, P4.
 *
 * Слоты фиксированные: если P3 отвалился, P4 остаётся P4, а новый игрок
 * займёт освободившийся P3. Иначе у людей посреди игры менялось бы
 * управление.
 *
 * Доступ в лобби бывает трёх видов (joinMode):
 *   'open'      - пускаем всех, кто знает код
 *   'password'  - нужен пароль лобби
 *   'whitelist' - пускаем только логины из списка допуска
 *
 * Отдельно есть visibility: публичные лобби видны в общем списке,
 * закрытые — только по прямому коду.
 */
const crypto = require('crypto')

// Без похожих символов (0/O, 1/I) - код часто диктуют голосом.
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'

/** Все возможные роли по порядку слотов. */
const ROLES = ['P1', 'P2', 'P3', 'P4']

const MIN_PLAYERS = 2
const MAX_PLAYERS = 4

/** Допустимые режимы входа. */
const JOIN_MODES = ['open', 'password', 'whitelist']

function randomCode(length = 6) {
  const bytes = crypto.randomBytes(length)
  let out = ''
  for (let i = 0; i < length; i++) out += CODE_ALPHABET[bytes[i] % CODE_ALPHABET.length]
  return out
}

/** Приводит запрошенное число игроков к допустимому диапазону или возвращает null. */
function normalizeMaxPlayers(value) {
  const n = Number(value)
  if (!Number.isInteger(n) || n < MIN_PLAYERS || n > MAX_PLAYERS) return null
  return n
}

/** Нормализует режим входа или возвращает null. */
function normalizeJoinMode(value) {
  const s = String(value || 'open').toLowerCase()
  return JOIN_MODES.includes(s) ? s : null
}

class Lobby {
  constructor(code, host, name, maxPlayers, options = {}) {
    this.code = code
    this.host = host
    this.name = name
    this.maxPlayers = maxPlayers
    // Слоты гостей: индекс 0 -> P2, 1 -> P3, 2 -> P4.
    this.slots = new Array(MAX_PLAYERS - 1).fill(null)
    this.running = false
    this.createdAt = Date.now()
    this.frames = 0
    this.bytes = 0

    // Доступ
    this.visibility = options.visibility === 'private' ? 'private' : 'public'
    this.joinMode = normalizeJoinMode(options.joinMode) || 'open'
    this.password = options.password ? String(options.password) : null
    // Список допуска и бан-лист хранятся в нижнем регистре.
    this.allowList = new Set((options.allowList || []).map((l) => String(l).toLowerCase()))
    this.bans = new Map() // login(lower) -> { login, reason, at }
  }

  /** Сколько игроков сейчас в лобби, включая хоста. */
  get playerCount() {
    return 1 + this.slots.filter(Boolean).length
  }

  get isFull() {
    return this.playerCount >= this.maxPlayers
  }

  /** Все гости (без пустых слотов). */
  get guests() {
    return this.slots.filter(Boolean)
  }

  /** Все участники, включая хоста. */
  get everyone() {
    return [this.host, ...this.guests]
  }

  /** Роль соединения или null, если оно не в этом лобби. */
  roleOf(conn) {
    if (conn === this.host) return 'P1'
    const index = this.slots.indexOf(conn)
    return index < 0 ? null : ROLES[index + 1]
  }

  /** Соединение гостя по логину (нужно для кика и бана). */
  findByLogin(login) {
    const key = String(login || '').toLowerCase()
    return this.everyone.find((c) => String(c.ctx.login || '').toLowerCase() === key) || null
  }

  isBanned(login) {
    return this.bans.has(String(login || '').toLowerCase())
  }

  /**
   * Может ли игрок войти. Возвращает код ошибки или null, если всё хорошо.
   */
  checkJoin(login, password) {
    if (this.isBanned(login)) return 'lobby_banned'
    if (this.isFull) return 'lobby_full'
    if (this.joinMode === 'password') {
      if (String(password || '') !== String(this.password || '')) return 'bad_lobby_password'
    }
    if (this.joinMode === 'whitelist') {
      if (!this.allowList.has(String(login || '').toLowerCase())) return 'not_invited'
    }
    return null
  }

  /**
   * Сажает игрока в первый свободный слот в пределах maxPlayers.
   * Возвращает роль или null, если мест нет.
   */
  addGuest(conn) {
    for (let i = 0; i < this.maxPlayers - 1; i++) {
      if (!this.slots[i]) {
        this.slots[i] = conn
        return ROLES[i + 1]
      }
    }
    return null
  }

  /** Убирает гостя из слота. Возвращает освобождённую роль или null. */
  removeGuest(conn) {
    const index = this.slots.indexOf(conn)
    if (index < 0) return null
    this.slots[index] = null
    return ROLES[index + 1]
  }

  /** Добавляет логин в бан-лист лобби. */
  ban(login, reason) {
    this.bans.set(String(login).toLowerCase(), {
      login: String(login),
      reason: reason ? String(reason).slice(0, 120) : null,
      at: Date.now(),
    })
  }

  /** Снимает бан лобби. */
  unban(login) {
    return this.bans.delete(String(login).toLowerCase())
  }

  /** Список забаненных в этом лобби. */
  banList() {
    return [...this.bans.values()]
  }

  /**
   * Публичное представление для отправки клиентам.
   * @param {boolean} full включать ли данные для хоста (списки допуска и банов)
   */
  toPublic(full = false) {
    const describe = (conn, role, host) => ({
      login: conn.ctx.login,
      role,
      host,
      // Украшения ника едут вместе с игроком, чтобы клиент сразу знал, как рисовать.
      cosmetic: conn.ctx.cosmetic || null,
      admin: conn.ctx.role === 'admin',
    })

    const players = [describe(this.host, 'P1', true)]
    for (let i = 0; i < this.maxPlayers - 1; i++) {
      const conn = this.slots[i]
      if (conn) players.push(describe(conn, ROLES[i + 1], false))
    }

    const view = {
      code: this.code,
      name: this.name,
      host: this.host.ctx.login,
      maxPlayers: this.maxPlayers,
      playerCount: this.playerCount,
      running: this.running,
      createdAt: this.createdAt,
      visibility: this.visibility,
      joinMode: this.joinMode,
      hasPassword: this.joinMode === 'password',
      players,
    }

    if (full) {
      view.allowList = [...this.allowList]
      view.bans = this.banList()
    }
    return view
  }

  /** Шлёт JSON всем участникам, кроме указанного соединения. */
  broadcast(message, except = null) {
    for (const conn of this.everyone) {
      if (conn !== except) conn.sendJson(message)
    }
  }
}

class LobbyManager {
  constructor() {
    this.lobbies = new Map()
  }

  create(hostConn, name, maxPlayers, options = {}) {
    let code = randomCode()
    while (this.lobbies.has(code)) code = randomCode()
    const lobby = new Lobby(code, hostConn, name, maxPlayers, options)
    this.lobbies.set(code, lobby)
    return lobby
  }

  get(code) {
    if (!code) return null
    return this.lobbies.get(String(code).toUpperCase()) || null
  }

  /**
   * Список лобби для окна поиска игры.
   * Закрытые лобби в общий список не попадают - в них входят только по коду.
   * Админу видны все лобби без исключений.
   */
  list({ includePrivate = false } = {}) {
    return [...this.lobbies.values()]
      .filter((l) => includePrivate || l.visibility === 'public')
      .map((l) => l.toPublic())
  }

  remove(code) {
    this.lobbies.delete(code)
  }

  /**
   * Закрывает лобби целиком: всем участникам приходит lobby_closed,
   * контексты очищаются, запись удаляется из списка.
   */
  close(lobby, reason) {
    if (!lobby) return
    for (const conn of lobby.everyone) {
      conn.ctx.lobbyCode = null
      conn.ctx.role = null
      conn.ctx.isHost = false
      conn.sendJson({ t: 'lobby_closed', code: lobby.code, reason })
    }
    this.remove(lobby.code)
  }

  /**
   * Отцепляет соединение от его лобби и рассылает уведомления.
   * Если ушёл хост - лобби закрывается для всех.
   */
  detach(conn) {
    const lobby = this.get(conn.ctx && conn.ctx.lobbyCode)
    if (!lobby) return

    if (lobby.host === conn) {
      for (const guest of lobby.guests) {
        guest.ctx.lobbyCode = null
        guest.ctx.role = null
        guest.sendJson({ t: 'lobby_closed', code: lobby.code, reason: 'хост вышел из игры' })
      }
      this.remove(lobby.code)
    } else {
      const role = lobby.removeGuest(conn)
      if (role) {
        lobby.broadcast({
          t: 'peer_left',
          login: conn.ctx.login,
          role,
          lobby: lobby.toPublic(),
        })
      }
    }

    conn.ctx.lobbyCode = null
    conn.ctx.role = null
    conn.ctx.isHost = false
  }
}

module.exports = {
  Lobby,
  LobbyManager,
  randomCode,
  normalizeMaxPlayers,
  normalizeJoinMode,
  ROLES,
  MIN_PLAYERS,
  MAX_PLAYERS,
  JOIN_MODES,
}
