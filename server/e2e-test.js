'use strict'
/**
 * Сквозной (end-to-end) тест relay-сервера DeltaDotNet.
 *
 * Запускает сервер в отдельном процессе и проверяет весь сценарий:
 *   0. корректность WebSocket-рукопожатия (Sec-WebSocket-Accept)
 *   1. авторизация (регистрация, ошибки, вход по токену)
 *   2. лобби на 3 игроков (создание, роли P1/P2/P3, заполненное лобби)
 *   3. старт игры и рассылка видеокадра всем гостям
 *   4. логические действия ввода и их валидация
 *   5. корректная обработка обрыва связи
 *
 * Запуск: node server/e2e-test.js   (или npm test в папке server)
 */
const net = require('net')
const os = require('os')
const path = require('path')
const crypto = require('crypto')
const { spawn } = require('child_process')
const { encodeFrame, OP } = require('./src/ws')

const PORT = Number(process.env.TEST_PORT || 18081)
const TIMEOUT = 4000
const WS_GUID = '258EAFA5-E914-47DA-95CA-5AB0DC85B11F'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function assert(cond, message) {
  if (!cond) throw new Error('ASSERT: ' + message)
}

/**
 * Минимальный WebSocket-клиент на сыром сокете.
 * Входящие сообщения кладёт в очереди, чтобы ничего не терялось между ожиданиями.
 * Также проверяет заголовок Sec-WebSocket-Accept — точно так же, как это делает
 * ClientWebSocket в .NET.
 */
class TestClient {
  constructor(name) {
    this.name = name
    this.jsonInbox = []
    this.binaryInbox = []
    this.buffer = Buffer.alloc(0)
    this.handshaked = false
    this.closed = false
    this.acceptHeader = null
    this.expectedAccept = null
  }

  connect(port) {
    return new Promise((resolve, reject) => {
      const key = crypto.randomBytes(16).toString('base64')
      this.expectedAccept = crypto.createHash('sha1').update(key + WS_GUID).digest('base64')
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
      const m = head.match(/Sec-WebSocket-Accept:\s*(\S+)/i)
      this.acceptHeader = m ? m[1] : null
      this.buffer = this.buffer.subarray(idx + 4)
      this.handshaked = true
      onHandshake()
    }

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

async function run() {
  const dataFile = path.join(os.tmpdir(), `deltadotnet-test-${Date.now()}.json`)

  const child = spawn(process.execPath, [path.join(__dirname, 'src', 'index.js')], {
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
  const p2 = new TestClient('p2')
  const p3 = new TestClient('p3')
  const extra = new TestClient('extra')

  try {
    // -------------------------------------------------- 0. рукопожатие
    await host.connect(PORT)
    await p2.connect(PORT)
    await p3.connect(PORT)
    await extra.connect(PORT)
    assert(
      host.acceptHeader === host.expectedAccept,
      `Sec-WebSocket-Accept неверен: ожидался ${host.expectedAccept}, получен ${host.acceptHeader}`
    )
    assert(p2.acceptHeader === p2.expectedAccept, 'Sec-WebSocket-Accept неверен у второго клиента')
    console.log('  [ok] 0. рукопожатие WebSocket')

    const hello = await host.waitJson('hello')
    assert(hello.version === 2, 'версия протокола 2')
    assert(hello.maxPlayers === 4, 'сервер сообщает максимум 4 игрока')
    assert(Array.isArray(hello.actions) && hello.actions.length === 9, '9 логических действий')
    await p2.waitJson('hello')
    await p3.waitJson('hello')
    await extra.waitJson('hello')

    // ------------------------------------------------- 1. авторизация
    host.sendJson({ t: 'create_lobby', name: 'no auth' })
    assert((await host.waitJson('error')).code === 'unauthorized', 'действие без входа отклоняется')

    host.sendJson({ t: 'register', login: 'player_one', password: 'secret123' })
    const hostAuth = await host.waitJson('auth_ok')
    assert(hostAuth.login === 'player_one', 'логин хоста')
    assert(typeof hostAuth.token === 'string' && hostAuth.token.length > 10, 'выдан токен')

    host.sendJson({ t: 'register', login: 'pl', password: 'secret123' })
    assert((await host.waitJson('error')).code === 'bad_login', 'короткий логин отклоняется')

    host.sendJson({ t: 'register', login: 'player_one', password: 'secret123' })
    assert((await host.waitJson('error')).code === 'user_exists', 'дубль логина отклоняется')

    p2.sendJson({ t: 'register', login: 'player_two', password: 'secret456' })
    await p2.waitJson('auth_ok')
    p3.sendJson({ t: 'register', login: 'player_three', password: 'secret789' })
    await p3.waitJson('auth_ok')
    extra.sendJson({ t: 'register', login: 'player_four', password: 'secret000' })
    await extra.waitJson('auth_ok')

    p2.sendJson({ t: 'login', login: 'player_two', password: 'wrong' })
    assert((await p2.waitJson('error')).code === 'bad_credentials', 'неверный пароль')

    const tokenClient = new TestClient('token')
    await tokenClient.connect(PORT)
    await tokenClient.waitJson('hello')
    tokenClient.sendJson({ t: 'auth_token', token: hostAuth.token })
    assert((await tokenClient.waitJson('auth_ok')).login === 'player_one', 'вход по токену')
    tokenClient.sendJson({ t: 'auth_token', token: 'garbage.token' })
    assert((await tokenClient.waitJson('error')).code === 'bad_token', 'битый токен отклоняется')
    tokenClient.destroy()
    console.log('  [ok] 1. авторизация')

    // ------------------------------------------- 2. лобби на 3 игроков
    host.sendJson({ t: 'create_lobby', name: 'Слишком много', maxPlayers: 9 })
    assert((await host.waitJson('error')).code === 'bad_max_players', 'больше 4 игроков нельзя')

    host.sendJson({ t: 'create_lobby', name: 'Тестовая игра', maxPlayers: 3 })
    const created = await host.waitJson('lobby_created')
    const code = created.lobby.code
    assert(/^[A-Z2-9]{6}$/.test(code), 'код лобби из 6 символов, получено ' + code)
    assert(created.role === 'P1', 'хост получает роль P1')
    assert(created.lobby.maxPlayers === 3, 'в лобби 3 места')
    assert(created.lobby.playerCount === 1, 'пока только хост')

    p2.sendJson({ t: 'list_lobbies' })
    const list = await p2.waitJson('lobby_list')
    const entry = list.lobbies.find((l) => l.code === code)
    assert(entry, 'созданное лобби видно в списке')
    assert(entry.maxPlayers === 3 && entry.playerCount === 1, 'в списке видно 1/3')

    p2.sendJson({ t: 'join_lobby', code: 'ZZZZZZ' })
    assert((await p2.waitJson('error')).code === 'no_lobby', 'несуществующий код')

    host.sendJson({ t: 'start' })
    assert((await host.waitJson('error')).code === 'no_guest', 'старт в одиночку невозможен')

    p2.sendJson({ t: 'join_lobby', code })
    assert((await p2.waitJson('lobby_joined')).role === 'P2', 'первый гость — P2')
    assert((await host.waitJson('peer_joined')).role === 'P2', 'хост узнал о P2')

    p3.sendJson({ t: 'join_lobby', code })
    const joined3 = await p3.waitJson('lobby_joined')
    assert(joined3.role === 'P3', 'второй гость — P3')
    assert(joined3.lobby.playerCount === 3, 'в лобби трое')
    await host.waitJson('peer_joined')
    await p2.waitJson('peer_joined')

    extra.sendJson({ t: 'join_lobby', code })
    assert((await extra.waitJson('error')).code === 'lobby_full', 'четвёртый не влезает в лобби на 3')
    console.log('  [ok] 2. лобби на 3 игроков')

    // ------------------------------------------- 3. старт и видеокадр
    p2.sendJson({ t: 'start' })
    assert((await p2.waitJson('error')).code === 'not_host', 'запускает только хост')

    host.sendJson({ t: 'start' })
    await host.waitJson('started')
    await p2.waitJson('started')
    await p3.waitJson('started')

    const frame = Buffer.alloc(17 + 1000)
    frame[0] = 0x01
    frame.writeUInt32LE(42, 1)
    frame.writeUInt16LE(1280, 5)
    frame.writeUInt16LE(720, 7)
    frame.writeBigInt64LE(BigInt(Date.now()), 9)
    frame.fill(0xab, 17)
    host.sendBinary(frame)

    const got2 = await p2.waitBinary()
    const got3 = await p3.waitBinary()
    assert(got2.length === frame.length && got2.readUInt32LE(1) === 42, 'P2 получил кадр целиком')
    assert(got3.length === frame.length && got3.readUInt16LE(5) === 1280, 'P3 получил тот же кадр')

    host.sendJson({ t: 'stats' })
    const stats = (await host.waitJson('stats')).stats
    assert(stats.frames === 1 && stats.players === 3 && stats.maxPlayers === 3, 'статистика лобби')
    console.log('  [ok] 3. старт и трансляция видео всем гостям')

    // ------------------------------------------------ 4. действия ввода
    p2.sendJson({ t: 'input', action: 'Left', down: true })
    const in2 = await host.waitJson('input')
    assert(in2.role === 'P2' && in2.action === 'Left' && in2.down === true, 'действие P2 дошло до хоста')
    assert(in2.login === 'player_two', 'в событии есть логин игрока')

    p3.sendJson({ t: 'input', action: 'Confirm', down: true })
    const in3 = await host.waitJson('input')
    assert(in3.role === 'P3' && in3.action === 'Confirm', 'действие P3 пришло с его ролью')

    p2.sendJson({ t: 'input', action: 'Left', down: false })
    assert((await host.waitJson('input')).down === false, 'отпускание клавиши пересылается')

    p2.sendJson({ t: 'input', action: 'SelfDestruct', down: true })
    assert((await p2.waitJson('error')).code === 'bad_action', 'неизвестное действие отклоняется')

    host.sendJson({ t: 'input', action: 'Up', down: true })
    assert((await host.waitJson('error')).code === 'not_guest', 'ввод хоста не пересылается')

    p3.sendJson({ t: 'release_all' })
    assert((await host.waitJson('release_all')).role === 'P3', 'сброс клавиш приходит с ролью')
    console.log('  [ok] 4. логические действия и их валидация')

    // --------------------------------------------------- 5. обрыв связи
    p3.destroy()
    const left = await host.waitJson('peer_left')
    assert(left.role === 'P3', 'хост узнал об уходе P3')
    assert(left.lobby.playerCount === 2, 'осталось двое')

    // Новый игрок занимает освободившийся слот P3.
    extra.sendJson({ t: 'join_lobby', code })
    assert((await extra.waitJson('lobby_joined')).role === 'P3', 'свободный слот переиспользуется')
    await host.waitJson('peer_joined')

    // Уход хоста закрывает лобби для всех.
    host.destroy()
    assert((await p2.waitJson('lobby_closed')).reason, 'P2 узнал о закрытии лобби')
    await extra.waitJson('lobby_closed')
    console.log('  [ok] 5. обрыв связи и закрытие лобби')

    console.log('\nВСЕ ТЕСТЫ ПРОШЛИ')
  } finally {
    host.destroy()
    p2.destroy()
    p3.destroy()
    extra.destroy()
    child.kill()
  }
}

run().catch((err) => {
  console.error('\nТЕСТ ПРОВАЛЕН: ' + err.message)
  process.exit(1)
})
