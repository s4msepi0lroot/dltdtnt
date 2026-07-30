'use strict'
/**
 * Менеджер лобби.
 * Лобби = хост (тот, кто запускает игру и транслирует экран) + один гость (зритель/второй игрок).
 */
const crypto = require('crypto')

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789' // без похожих символов 0/O, 1/I

function randomCode(len = 6) {
  const bytes = crypto.randomBytes(len)
  let out = ''
  for (let i = 0; i < len; i++) out += CODE_ALPHABET[bytes[i] % CODE_ALPHABET.length]
  return out
}

class Lobby {
  constructor(code, name, hostConn) {
    this.code = code
    this.name = name || `Lobby ${code}`
    this.host = hostConn
    this.guest = null
    this.started = false
    this.createdAt = Date.now()
    /** Кто из игроков хост: 'P1' или 'P2'. Гостю достаётся вторая роль. */
    this.hostRole = 'P1'
    this.stats = { frames: 0, bytes: 0, inputs: 0 }
  }

  get guestRole() {
    return this.hostRole === 'P1' ? 'P2' : 'P1'
  }

  get isFull() {
    return this.guest !== null
  }

  toPublic() {
    return {
      code: this.code,
      name: this.name,
      host: this.host?.ctx?.login || null,
      guest: this.guest?.ctx?.login || null,
      started: this.started,
      hostRole: this.hostRole,
      guestRole: this.guestRole,
      players: this.guest ? 2 : 1,
    }
  }

  other(conn) {
    if (conn === this.host) return this.guest
    if (conn === this.guest) return this.host
    return null
  }

  broadcast(obj) {
    this.host?.sendJson(obj)
    this.guest?.sendJson(obj)
  }
}

class LobbyManager {
  constructor() {
    /** @type {Map<string, Lobby>} */
    this.lobbies = new Map()
  }

  create(hostConn, name, hostRole = 'P1') {
    let code
    do {
      code = randomCode()
    } while (this.lobbies.has(code))
    const lobby = new Lobby(code, name, hostConn)
    lobby.hostRole = hostRole === 'P2' ? 'P2' : 'P1'
    this.lobbies.set(code, lobby)
    return lobby
  }

  get(code) {
    return this.lobbies.get(String(code || '').toUpperCase()) || null
  }

  list() {
    return [...this.lobbies.values()].filter((l) => !l.started || !l.isFull).map((l) => l.toPublic())
  }

  remove(code) {
    this.lobbies.delete(code)
  }

  /** Удаляет соединение из всех лобби. Возвращает затронутое лобби (если было). */
  detach(conn) {
    const code = conn.ctx.lobbyCode
    if (!code) return null
    const lobby = this.lobbies.get(code)
    if (!lobby) return null
    if (lobby.host === conn) {
      // Хост ушёл — лобби закрывается.
      lobby.guest?.sendJson({ t: 'lobby_closed', reason: 'host_left' })
      if (lobby.guest) lobby.guest.ctx.lobbyCode = null
      this.lobbies.delete(code)
      return { lobby, closed: true }
    }
    if (lobby.guest === conn) {
      lobby.guest = null
      lobby.started = false
      lobby.host?.sendJson({ t: 'peer_left', role: lobby.guestRole })
      return { lobby, closed: false }
    }
    return null
  }
}

module.exports = { LobbyManager, Lobby, randomCode }
