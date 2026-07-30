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
 */
const crypto = require('crypto')

// Без похожих символов (0/O, 1/I) — код часто диктуют голосом.
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'

/** Все возможные роли по порядку слотов. */
const ROLES = ['P1', 'P2', 'P3', 'P4']

const MIN_PLAYERS = 2
const MAX_PLAYERS = 4

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

class Lobby {
  constructor(code, host, name, maxPlayers) {
    this.code = code
    this.host = host
    this.name = name
    this.maxPlayers = maxPlayers
    // Слоты гостей: индекс 0 → P2, 1 → P3, 2 → P4.
    this.slots = new Array(MAX_PLAYERS - 1).fill(null)
    this.running = false
    this.createdAt = Date.now()
    this.frames = 0
    this.bytes = 0
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

  /** Публичное представление для отправки клиентам. */
  toPublic() {
    const players = [{ login: this.host.ctx.login, role: 'P1', host: true }]
    for (let i = 0; i < this.maxPlayers - 1; i++) {
      const conn = this.slots[i]
      if (conn) players.push({ login: conn.ctx.login, role: ROLES[i + 1], host: false })
    }
    return {
      code: this.code,
      name: this.name,
      host: this.host.ctx.login,
      maxPlayers: this.maxPlayers,
      playerCount: this.playerCount,
      running: this.running,
      createdAt: this.createdAt,
      players,
    }
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

  create(hostConn, name, maxPlayers) {
    let code = randomCode()
    while (this.lobbies.has(code)) code = randomCode()
    const lobby = new Lobby(code, hostConn, name, maxPlayers)
    this.lobbies.set(code, lobby)
    return lobby
  }

  get(code) {
    if (!code) return null
    return this.lobbies.get(String(code).toUpperCase()) || null
  }

  /** Список лобби для окна поиска игры. */
  list() {
    return [...this.lobbies.values()].map((l) => l.toPublic())
  }

  remove(code) {
    this.lobbies.delete(code)
  }

  /**
   * Отцепляет соединение от его лобби и рассылает уведомления.
   * Если ушёл хост — лобби закрывается для всех.
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

module.exports = { Lobby, LobbyManager, randomCode, normalizeMaxPlayers, ROLES, MIN_PLAYERS, MAX_PLAYERS }
