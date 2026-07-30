'use strict'
/**
 * Сквозной (end-to-end) тест relay-сервера.
 *
 * Запускает сервер в отдельном процессе и проверяет весь сценарий:
 *   1. авторизация (регистрация, ошибки, вход по токену)
 *   2. лобби (создание, список, подключение)
 *   3. старт игры и пересылка видеокадра
 *   4. ввод с клавиатуры и белый список клавиш
 *   5. корректная обработка обрыва связи
 *
 * Запуск: node server/test/e2e.js   (или npm test в папке server)
 */
const net = require('net')
const os = require('os')
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')
const { spawn } = require('child_process')
const { encodeFrame, OP } = require('../src/ws')

const PORT = Number(process.env.TEST_PORT || 18081)
const TIMEOUT = 4000

// --------------------------------------------------------------- утилиты
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function assert(cond, message) {
  if (!cond) throw new Error('ASSERT: ' + message)
}

/**
 * Минимальный WebSocket-клиент на сыром сокете.
 * Входящие сообщения кладёт в очереди, чтобы ничего не терялось
 * между ожиданиями (гонка была реальной проблемой при отладке).
 */
class TestClient {
  constructor(name) {
    this.name = name
    this.jsonInbox = []
    this.binaryInbox = []
    this.buffer = Buffer.alloc(0)
    this.handshaked = false
    this.closed = false
  }

  connect(port) {
    return new Promise((resolve, reject) => {
      const key = crypto.randomBytes(16).toString('base64')
      this.socket = net.connect(port, '127.0.0.1', () => {
        this.socket.write(
          'GET /ws HTTP/1.1\r\n' +
            `Host: 127.0.0.1:${port}\r\n` +
            'Upgrade: websocket\r\n' +
            'Connection: Upgrade\r\n' +
            `Sec-WebSocket-Key: ${key}\r\n` +
            'Sec-WebSocket-Version: 13\r\n\r\n'
        )
      })
      this.socket.setNoDelay(true)
      this.socket.on('data', (chunk) => this._onData(chunk, resolve, reject))
      this.socket.on('error', reject)
      this.socket.on('close', () => {
        this.closed = true
      })
    })
  }

  _onData(chunk, onHandshake, onError) {
    this.buffer = Buffer.concat([this.buffer, chunk])

    if (!this.handshaked) {
      const idx = this.buffer.indexOf('\r\n\r\n')
      if (idx < 0) return
      const head = this.buffer.subarray(0, idx).toString('utf8')
      if (!head.includes('101')) {
        onError(new Error('handshake failed: ' + head))
        return
      }
      this.buffer = this.buffer.subarray(idx + 4)
      this.handshaked = true
      onHandshake()
    }

    // Разбираем столько кадров, сколько накопилось в буфере.
    for (;;) {
      const buf = this.buffer
      if (buf.length < 2) return
      const opcode = buf[0] & 0x0f
      const masked = (buf[1] & 0x80) !== 0
      let len = buf[1] & 0x7f
      let off = 2
      if (len === 126) {
        if (buf.length < 4) return
        len = buf.readUInt16BE(2)
        off = 4
      } else if (len === 127) {
        if (buf.length < 10) return
        len = Number(buf.readBigUInt64BE(2))
        off = 10
      }
      let mask = null
      if (masked) {
        if (buf.length < off + 4) return
        mask = buf.subarray(off, off + 4)
        off += 4
      }
      if (buf.length < off + len) return

      const payload = Buffer.from(buf.subarray(off, off + len))
      if (mask) for (let i = 0; i < len; i++) payload[i] ^= mask[i % 4]
      this.buffer = buf.subarray(off + len)

      if (opcode === OP.TEXT) {
        try {
          this.jsonInbox.push(JSON.parse(payload.toString('utf8')))
        } catch (_) {}
      } else if (opcode === OP.BINARY) {
        this.binaryInbox.push(payload)
      } else if (opcode === OP.PING) {
        this._write(encodeFrame(OP.PONG, payload, { mask: true }))
      } else if (opcode === OP.CLOSE) {
        this.closed = true
      }
    }
  }

  _write(buf) {
    try {
      this.socket.write(buf)
    } catch (_) {}
  }

  sendJson(obj) {
    this._write(encodeFrame(OP.TEXT, Buffer.from(JSON.stringify(obj), 'utf8'), { mask: true }))
  }

  sendBinary(buf) {
    this._write(encodeFrame(OP.BINARY, buf, { mask: true }))
  }

  /** Ждёт первое JSON-сообщение, подходящее под предикат, и убирает его из очереди. */
  async waitJson(pred, label) {
    const deadline = Date.now() + TIMEOUT
    const test = typeof pred === 'string' ? (m) => m.t === pred : pred
    while (Date.now() < deadline) {
      const i = this.jsonInbox.findIndex(test)
      if (i >= 0) return this.jsonInbox.splice(i, 1)[0]
      await sleep(5)
    }
    throw new Error(
      `timeout waiting for ${label || pred}; в очереди [${this.jsonInbox.map((m) => m.t).join(', ')}]`
    )
  }

  async waitBinary() {
    const deadline = Date.now() + TIMEOUT
    while (Date.now() < deadline) {
      if (this.binaryInbox.length) return this.binaryInbox.shift()
      await sleep(5)
    }
    throw new Error('timeout waiting for binary frame')
  }

  destroy() {
    try {
      this.socket.destroy()
    } catch (_) {}
  }
}

// ------------------------------------------------------------------- тесты
async function run() {
  const dataFile = path.join(os.tmpdir(), `coopstream-test-${Date.now()}.json`)

  const child = spawn(process.execPath, [path.join(__dirname, '..', 'src', 'index.js')], {
    env: {
      ...process.env,
      PORT: String(PORT),
      AUTH_SECRET: 'test-secret',
      DATA_FILE: dataFile,
      ALLOW_REGISTER: '1',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  child.stdout.on('data', () => {})
  child.stderr.on('data', (d) => process.stderr.write('[server] ' + d))

  // Ждём, пока порт откроется.
  for (let i = 0; i < 100; i++) {
    const ok = await new Promise((resolve) => {
      const s = net.connect(PORT, '127.0.0.1')
      s.on('connect', () => {
        s.destroy()
        resolve(true)
      })
      s.on('error', () => resolve(false))
    })
    if (ok) break
    await sleep(50)
  }

  const host = new TestClient('host')
  const guest = new TestClient('guest')

  try {
    // ------------------------------------------------- 1. авторизация
    await host.connect(PORT)
    await guest.connect(PORT)
    await host.waitJson('hello')
    await guest.waitJson('hello')

    host.sendJson({ t: 'create_lobby', name: 'no auth' })
    const denied = await host.waitJson('error')
    assert(denied.code === 'unauthorized', 'действие без входа должно отклоняться')

    host.sendJson({ t: 'register', login: 'player_one', password: 'secret123' })
    const hostAuth = await host.waitJson('auth_ok')
    assert(hostAuth.login === 'player_one', 'логин хоста')
    assert(typeof hostAuth.token === 'string' && hostAuth.token.length > 10, 'выдан токен')

    host.sendJson({ t: 'register', login: 'pl', password: 'secret123' })
    assert((await host.waitJson('error')).code === 'bad_login', 'короткий логин отклоняется')

    host.sendJson({ t: 'register', login: 'player_one', password: 'secret123' })
    assert((await host.waitJson('error')).code === 'user_exists', 'дубль логина отклоняется')

    guest.sendJson({ t: 'register', login: 'player_two', password: 'secret456' })
    await guest.waitJson('auth_ok')

    guest.sendJson({ t: 'login', login: 'player_two', password: 'wrong' })
    assert((await guest.waitJson('error')).code === 'bad_credentials', 'неверный пароль')

    // вход по токену новым соединением
    const tokenClient = new TestClient('token')
    await tokenClient.connect(PORT)
    await tokenClient.waitJson('hello')
    tokenClient.sendJson({ t: 'auth_token', token: hostAuth.token })
    assert((await tokenClient.waitJson('auth_ok')).login === 'player_one', 'вход по токену')
    tokenClient.sendJson({ t: 'auth_token', token: 'garbage.token' })
    assert((await tokenClient.waitJson('error')).code === 'bad_token', 'битый токен отклоняется')
    tokenClient.destroy()
    console.log('  [ok] 1. авторизация')

    // ------------------------------------------------------- 2. лобби
    host.sendJson({ t: 'create_lobby', name: 'Тестовая игра', hostRole: 'P1' })
    const created = await host.waitJson('lobby_created')
    const code = created.lobby.code
    assert(/^[A-Z2-9]{6}$/.test(code), 'код лобби из 6 символов, получено ' + code)
    assert(created.role === 'P1', 'хост получает роль P1')

    guest.sendJson({ t: 'list_lobbies' })
    const list = await guest.waitJson('lobby_list')
    assert(
      list.lobbies.some((l) => l.code === code),
      'созданное лобби видно в списке'
    )

    guest.sendJson({ t: 'join_lobby', code: 'ZZZZZZ' })
    assert((await guest.waitJson('error')).code === 'no_lobby', 'несуществующий код')

    host.sendJson({ t: 'start' })
    assert((await host.waitJson('error')).code === 'no_guest', 'старт без второго игрока невозможен')

    guest.sendJson({ t: 'join_lobby', code })
    const joined = await guest.waitJson('lobby_joined')
    assert(joined.role === 'P2', 'гость получает роль P2')
    const peer = await host.waitJson('peer_joined')
    assert(peer.login === 'player_two', 'хост узнаёт о втором игроке')
    console.log('  [ok] 2. лобби')

    // ------------------------------------------- 3. старт и видеокадр
    guest.sendJson({ t: 'start' })
    assert((await guest.waitJson('error')).code === 'not_host', 'запускает только хост')

    host.sendJson({ t: 'start' })
    await host.waitJson('started')
    await guest.waitJson('started')

    // Заголовок 17 байт + «картинка» 1000 байт.
    const frame = Buffer.alloc(17 + 1000)
    frame[0] = 0x01
    frame.writeUInt32LE(42, 1)
    frame.writeUInt16LE(1280, 5)
    frame.writeUInt16LE(720, 7)
    frame.writeBigInt64LE(BigInt(Date.now()), 9)
    for (let i = 17; i < frame.length; i++) frame[i] = i & 0xff
    host.sendBinary(frame)

    const received = await guest.waitBinary()
    assert(received.length === frame.length, 'длина кадра совпадает')
    assert(received.equals(frame), 'кадр дошёл байт-в-байт')
    assert(received.readUInt32LE(1) === 42, 'номер кадра сохранён')

    // Кадры в обратную сторону сервер не пересылает.
    guest.sendBinary(frame)
    await sleep(150)
    assert(host.binaryInbox.length === 0, 'гость не может транслировать видео')
    console.log('  [ok] 3. старт и трансляция видео')

    // ------------------------------------------------------- 4. ввод
    guest.sendJson({ t: 'input', key: 'Left', down: true })
    const input = await host.waitJson('input')
    assert(input.key === 'Left' && input.down === true, 'нажатие Left дошло до хоста')
    assert(input.role === 'P2', 'роль указана в событии')

    guest.sendJson({ t: 'input', key: 'Left', down: false })
    assert((await host.waitJson('input')).down === false, 'отпускание Left дошло')

    for (const key of ['Up', 'Down', 'Right', 'Enter', 'C', 'LShift', 'RShift', 'LCtrl', 'RCtrl']) {
      guest.sendJson({ t: 'input', key, down: true })
      assert((await host.waitJson('input')).key === key, `клавиша ${key} разрешена для P2`)
    }

    for (const key of ['W', 'A', 'S', 'D', 'Z', 'X', 'P']) {
      guest.sendJson({ t: 'input', key, down: true })
      const err = await guest.waitJson('error')
      assert(err.code === 'key_not_allowed', `клавиша ${key} запрещена для P2`)
    }
    assert(host.jsonInbox.filter((m) => m.t === 'input').length === 0, 'запрещённые клавиши не утекают к хосту')

    guest.sendJson({ t: 'release_all' })
    await host.waitJson('release_all')

    guest.sendJson({ t: 'chat', text: 'готов' })
    const chatToHost = await host.waitJson('chat')
    const chatToGuest = await guest.waitJson('chat')
    assert(chatToHost.text === 'готов' && chatToGuest.text === 'готов', 'чат шлётся обоим')

    host.sendJson({ t: 'stats' })
    const stats = await host.waitJson('stats')
    assert(stats.stats && stats.stats.frames === 1, 'статистика считает кадры')

    host.sendJson({ t: 'nonsense' })
    assert((await host.waitJson('error')).code === 'unknown_type', 'неизвестный тип сообщения')
    console.log('  [ok] 4. ввод и белый список клавиш')

    // ------------------------------------------------- 5. отключение
    guest.destroy()
    const left = await host.waitJson('peer_left')
    assert(left.t === 'peer_left', 'хост узнаёт об отключении гостя')
    console.log('  [ok] 5. обрыв связи')

    console.log('\nВСЕ ТЕСТЫ ПРОШЛИ')
  } finally {
    host.destroy()
    guest.destroy()
    child.kill('SIGKILL')
    try {
      fs.unlinkSync(dataFile)
    } catch (_) {}
  }
}

run().catch((err) => {
  console.error('\nТЕСТ ПРОВАЛЕН:', err.message)
  process.exit(1)
})
