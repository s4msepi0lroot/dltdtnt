'use strict'
/**
 * Минимальная реализация WebSocket-сервера (RFC 6455) без внешних зависимостей.
 * Поддерживает: handshake, text/binary кадры, фрагментацию, ping/pong, close.
 *
 * Экспортирует attach(httpServer, { onConnection, maxPayload }).
 */
const crypto = require('crypto')
const { EventEmitter } = require('events')

const GUID = '258EAFA5-E914-47DA-95CA-5AB0DC85B11F'

const OP = {
  CONT: 0x0,
  TEXT: 0x1,
  BINARY: 0x2,
  CLOSE: 0x8,
  PING: 0x9,
  PONG: 0xa,
}

/** Собирает кадр WebSocket. Сервер отправляет данные без маски. */
function encodeFrame(opcode, payload, { mask = false, fin = true } = {}) {
  const data = Buffer.isBuffer(payload) ? payload : Buffer.from(String(payload), 'utf8')
  const len = data.length
  let header
  if (len < 126) {
    header = Buffer.alloc(2)
    header[1] = len
  } else if (len < 65536) {
    header = Buffer.alloc(4)
    header[1] = 126
    header.writeUInt16BE(len, 2)
  } else {
    header = Buffer.alloc(10)
    header[1] = 127
    header.writeBigUInt64BE(BigInt(len), 2)
  }
  header[0] = (fin ? 0x80 : 0x00) | (opcode & 0x0f)
  if (!mask) return Buffer.concat([header, data])

  header[1] |= 0x80
  const key = crypto.randomBytes(4)
  const masked = Buffer.allocUnsafe(len)
  for (let i = 0; i < len; i++) masked[i] = data[i] ^ key[i % 4]
  return Buffer.concat([header, key, masked])
}

/**
 * Одно WebSocket-соединение.
 * События: 'message' ({ type: 'text'|'binary', data }), 'close', 'error'.
 */
class WsConnection extends EventEmitter {
  constructor(socket, opts = {}) {
    super()
    this.socket = socket
    this.maxPayload = opts.maxPayload || 8 * 1024 * 1024
    this.closed = false
    this.buffer = Buffer.alloc(0)
    this.fragments = []
    this.fragmentOpcode = null
    this.isAlive = true
    /** Произвольные данные приложения (пользователь, лобби и т.п.). */
    this.ctx = {}

    socket.on('data', (chunk) => this._onData(chunk))
    socket.on('close', () => this._finish())
    // Некоторые клиенты рвут соединение так, что 'close' приходит с задержкой.
    socket.on('end', () => {
      this._finish()
      try {
        socket.destroy()
      } catch (_) {}
    })
    socket.on('error', (err) => {
      this.emit('error', err)
      this._finish()
    })
  }

  get remoteAddress() {
    return this.socket.remoteAddress
  }

  sendText(str) {
    this._send(OP.TEXT, Buffer.from(str, 'utf8'))
  }

  sendJson(obj) {
    this.sendText(JSON.stringify(obj))
  }

  sendBinary(buf) {
    this._send(OP.BINARY, buf)
  }

  ping() {
    this._send(OP.PING, Buffer.alloc(0))
  }

  /** Размер данных, ожидающих отправки в сокете (для backpressure). */
  get bufferedAmount() {
    return this.socket.writableLength || 0
  }

  close(code = 1000, reason = '') {
    if (this.closed) return
    const payload = Buffer.alloc(2 + Buffer.byteLength(reason))
    payload.writeUInt16BE(code, 0)
    payload.write(reason, 2)
    try {
      this.socket.write(encodeFrame(OP.CLOSE, payload))
    } catch (_) {}
    this._finish()
    try {
      this.socket.end()
    } catch (_) {}
  }

  _send(opcode, payload) {
    if (this.closed) return
    try {
      this.socket.write(encodeFrame(opcode, payload))
    } catch (err) {
      this.emit('error', err)
      this._finish()
    }
  }

  _finish() {
    if (this.closed) return
    this.closed = true
    this.emit('close')
  }

  _onData(chunk) {
    this.buffer = this.buffer.length ? Buffer.concat([this.buffer, chunk]) : chunk
    // eslint-disable-next-line no-constant-condition
    while (true) {
      const frame = this._readFrame()
      if (!frame) break
      this._handleFrame(frame)
      if (this.closed) break
    }
  }

  /** Пытается вычитать один кадр из накопленного буфера. */
  _readFrame() {
    const buf = this.buffer
    if (buf.length < 2) return null
    const fin = (buf[0] & 0x80) !== 0
    const opcode = buf[0] & 0x0f
    const masked = (buf[1] & 0x80) !== 0
    let len = buf[1] & 0x7f
    let offset = 2

    if (len === 126) {
      if (buf.length < offset + 2) return null
      len = buf.readUInt16BE(offset)
      offset += 2
    } else if (len === 127) {
      if (buf.length < offset + 8) return null
      const big = buf.readBigUInt64BE(offset)
      if (big > BigInt(this.maxPayload)) {
        this.close(1009, 'payload too large')
        return null
      }
      len = Number(big)
      offset += 8
    }
    if (len > this.maxPayload) {
      this.close(1009, 'payload too large')
      return null
    }

    let key = null
    if (masked) {
      if (buf.length < offset + 4) return null
      key = buf.subarray(offset, offset + 4)
      offset += 4
    }
    if (buf.length < offset + len) return null

    const payload = Buffer.from(buf.subarray(offset, offset + len))
    if (masked) for (let i = 0; i < len; i++) payload[i] ^= key[i % 4]
    this.buffer = buf.subarray(offset + len)
    return { fin, opcode, payload }
  }

  _handleFrame(frame) {
    const { fin, opcode, payload } = frame
    switch (opcode) {
      case OP.PING:
        this._send(OP.PONG, payload)
        return
      case OP.PONG:
        this.isAlive = true
        return
      case OP.CLOSE:
        this.close(1000, '')
        return
      case OP.TEXT:
      case OP.BINARY:
        if (fin) {
          this._emitMessage(opcode, payload)
        } else {
          this.fragmentOpcode = opcode
          this.fragments = [payload]
        }
        return
      case OP.CONT: {
        this.fragments.push(payload)
        if (!fin) return
        const full = Buffer.concat(this.fragments)
        const op = this.fragmentOpcode
        this.fragments = []
        this.fragmentOpcode = null
        this._emitMessage(op, full)
        return
      }
      default:
        this.close(1002, 'bad opcode')
    }
  }

  _emitMessage(opcode, payload) {
    this.isAlive = true
    if (opcode === OP.TEXT) {
      this.emit('message', { type: 'text', data: payload.toString('utf8') })
    } else {
      this.emit('message', { type: 'binary', data: payload })
    }
  }
}

/**
 * Подключает WebSocket-обработчик к обычному http.Server.
 * @param {import('http').Server} server
 * @param {{ path?: string, onConnection: (conn: WsConnection, req) => void, maxPayload?: number, heartbeatMs?: number }} opts
 */
function attach(server, opts) {
  const path = opts.path || '/ws'
  const connections = new Set()

  server.on('upgrade', (req, socket) => {
    const url = req.url.split('?')[0]
    const key = req.headers['sec-websocket-key']
    if (url !== path || !key || (req.headers.upgrade || '').toLowerCase() !== 'websocket') {
      socket.write('HTTP/1.1 400 Bad Request\r\n\r\n')
      socket.destroy()
      return
    }
    const accept = crypto.createHash('sha1').update(key + GUID).digest('base64')
    socket.write(
      'HTTP/1.1 101 Switching Protocols\r\n' +
        'Upgrade: websocket\r\n' +
        'Connection: Upgrade\r\n' +
        `Sec-WebSocket-Accept: ${accept}\r\n\r\n`
    )
    socket.setNoDelay(true)
    const conn = new WsConnection(socket, { maxPayload: opts.maxPayload })
    connections.add(conn)
    conn.on('close', () => connections.delete(conn))
    opts.onConnection(conn, req)
  })

  // Heartbeat: отключаем «мёртвые» соединения.
  const interval = setInterval(() => {
    for (const conn of connections) {
      if (!conn.isAlive) {
        conn.close(1001, 'timeout')
        continue
      }
      conn.isAlive = false
      conn.ping()
    }
  }, opts.heartbeatMs || 20000)
  interval.unref?.()

  return { connections }
}

module.exports = { attach, encodeFrame, WsConnection, OP }
