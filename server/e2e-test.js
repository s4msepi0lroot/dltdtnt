/**
 * Сквозной тест релей-сервера DeltaDotNet (протокол v3).
 *
 * Запуск:  node server/e2e-test.js
 *
 * Тест поднимает настоящий сервер на свободном порту, ходит в него своим
 * мини-клиентом WebSocket (без внешних зависимостей) и проверяет всю цепочку:
 * рукопожатие, авторизацию, лобби, приватность, кик/бан, закрытие лобби
 * и админ-команды.
 */

const http = require('http')
const net = require('net')
const os = require('os')
const path = require('path')
const fs = require('fs')
const crypto = require('crypto')

const PORT = Number(process.env.TEST_PORT || 18081)
const TIMEOUT = 4000
const WS_GUID = '258EAFA5-E914-47DA-95CA-5AB0DC85B11F'

const dataFile = path.join(os.tmpdir(), 'deltadotnet-test-' + Date.now() + '.json')
process.env.PORT = String(PORT)
process.env.AUTH_SECRET = 'test-secret'
process.env.DATA_FILE = dataFile
process.env.ADMIN_LOGIN = 's4msepi0l'

const { server } = require('./src/index')

// ---------------------------------------------------------------- мини-клиент

/**
 * Крошечный WebSocket-клиент: только то, что нужно тесту.
 * Входящие сообщения складываются в очередь, чтобы не было гонок.
 */
class TestClient {
	constructor(name) {
		this.name = name
		this.inbox = []
		this.binaries = []
		this.waiters = []
		this.closed = false
		this.buffer = Buffer.alloc(0)
	}

	connect() {
		return new Promise((resolve, reject) => {
			const key = crypto.randomBytes(16).toString('base64')
			this.socket = net.connect(PORT, '127.0.0.1', () => {
				this.socket.write(
					'GET /ws HTTP/1.1\r\n' +
						'Host: 127.0.0.1:' + PORT + '\r\n' +
						'Upgrade: websocket\r\n' +
						'Connection: Upgrade\r\n' +
						'Sec-WebSocket-Key: ' + key + '\r\n' +
						'Sec-WebSocket-Version: 13\r\n\r\n',
				)
			})

			let handshakeDone = false
			this.socket.on('data', (chunk) => {
				this.buffer = Buffer.concat([this.buffer, chunk])

				if (!handshakeDone) {
					const end = this.buffer.indexOf('\r\n\r\n')
					if (end < 0) return
					const head = this.buffer.subarray(0, end).toString('utf8')
					this.buffer = this.buffer.subarray(end + 4)
					handshakeDone = true

					const expected = crypto.createHash('sha1').update(key + WS_GUID).digest('base64')
					const match = /sec-websocket-accept:\s*(\S+)/i.exec(head)
					if (!head.startsWith('HTTP/1.1 101')) return reject(new Error('нет 101: ' + head.split('\r\n')[0]))
					if (!match || match[1] !== expected) return reject(new Error('неверный Sec-WebSocket-Accept'))
					resolve()
				}

				this.drainFrames()
			})

			const markClosed = () => {
				this.closed = true
				this.push({ t: '__closed__' })
			}
			this.socket.on('close', markClosed)
			this.socket.on('end', markClosed)
			this.socket.on('error', reject)
		})
	}

	/** Разбирает все целые кадры, которые уже пришли в буфер. */
	drainFrames() {
		for (;;) {
			if (this.buffer.length < 2) return
			const opcode = this.buffer[0] & 0x0f
			const masked = (this.buffer[1] & 0x80) !== 0
			let length = this.buffer[1] & 0x7f
			let offset = 2

			if (length === 126) {
				if (this.buffer.length < offset + 2) return
				length = this.buffer.readUInt16BE(offset)
				offset += 2
			} else if (length === 127) {
				if (this.buffer.length < offset + 8) return
				length = Number(this.buffer.readBigUInt64BE(offset))
				offset += 8
			}
			if (masked) offset += 4
			if (this.buffer.length < offset + length) return

			const payload = this.buffer.subarray(offset, offset + length)
			this.buffer = this.buffer.subarray(offset + length)

			if (opcode === 0x1) {
				try {
					this.push(JSON.parse(payload.toString('utf8')))
				} catch {
					/* мусор игнорируем */
				}
			} else if (opcode === 0x2) {
				this.binaries.push(Buffer.from(payload))
				this.push({ t: '__binary__', size: payload.length })
			} else if (opcode === 0x8) {
				this.closed = true
				this.push({ t: '__closed__' })
			} else if (opcode === 0x9) {
				this.sendFrame(0xa, payload)
			}
		}
	}

	push(message) {
		this.inbox.push(message)
	}

	sendFrame(opcode, payload) {
		const mask = crypto.randomBytes(4)
		const masked = Buffer.from(payload)
		for (let i = 0; i < masked.length; i++) masked[i] ^= mask[i % 4]

		let header
		if (masked.length < 126) {
			header = Buffer.from([0x80 | opcode, 0x80 | masked.length])
		} else if (masked.length < 65536) {
			header = Buffer.alloc(4)
			header[0] = 0x80 | opcode
			header[1] = 0x80 | 126
			header.writeUInt16BE(masked.length, 2)
		} else {
			header = Buffer.alloc(10)
			header[0] = 0x80 | opcode
			header[1] = 0x80 | 127
			header.writeBigUInt64BE(BigInt(masked.length), 2)
		}
		this.socket.write(Buffer.concat([header, mask, masked]))
	}

	send(message) {
		this.sendFrame(0x1, Buffer.from(JSON.stringify(message), 'utf8'))
	}

	sendBinary(buffer) {
		this.sendFrame(0x2, buffer)
	}

	/**
	 * Ждёт сообщение указанного типа, вынимая его из очереди.
	 * Важно: после завершения опрос обязательно останавливается, иначе старое
	 * ожидание продолжает работать и выдёргивает чужие сообщения из очереди.
	 */
	wait(type, timeout = TIMEOUT) {
		const deadline = Date.now() + timeout
		return new Promise((resolve, reject) => {
			let done = false
			const check = () => {
				if (done) return
				const index = this.inbox.findIndex((m) => m.t === type)
				if (index >= 0) {
					done = true
					return resolve(this.inbox.splice(index, 1)[0])
				}
				if (Date.now() > deadline) {
					done = true
					return reject(new Error(
						`${this.name}: не дождались "${type}"; в очереди [${this.inbox.map((m) => m.t).join(', ')}]`,
					))
				}
				setTimeout(check, 20)
			}
			check()
		})
	}

	close() {
		try {
			this.socket.destroy()
		} catch {
			/* уже закрыт */
		}
	}
}

// -------------------------------------------------------------------- хелперы

function assert(condition, message) {
	if (!condition) throw new Error(message)
}

function ok(step) {
	console.log('[ok] ' + step)
}

async function makeUser(name, login, password) {
	const client = new TestClient(name)
	await client.connect()
	await client.wait('hello')
	client.send({ t: 'register', login, password })
	const auth = await client.wait('auth_ok')
	return { client, auth }
}

/** Минимальный кадр видео: 17 байт заголовка плюс пара байт "картинки". */
function fakeFrame(sequence) {
	const frame = Buffer.alloc(21)
	frame[0] = 0x01
	frame.writeUInt32LE(sequence, 1)
	frame.writeUInt16LE(320, 5)
	frame.writeUInt16LE(240, 7)
	frame.writeBigInt64LE(BigInt(Date.now()), 9)
	return frame
}

// ---------------------------------------------------------------------- тесты

async function main() {
	// 0. Рукопожатие и версия протокола
	const host = new TestClient('host')
	await host.connect()
	const hello = await host.wait('hello')
	assert(hello.version === 3, 'ожидалась версия протокола 3, пришла ' + hello.version)
	assert(Array.isArray(hello.joinModes) && hello.joinModes.includes('whitelist'), 'нет списка режимов входа')
	ok('0. рукопожатие WebSocket и протокол v3')

	// 1. Авторизация и признак администратора
	host.send({ t: 'register', login: 'player_one', password: 'secret123' })
	const hostAuth = await host.wait('auth_ok')
	assert(hostAuth.login === 'player_one', 'неверный логин в auth_ok')
	assert(hostAuth.isAdmin === false, 'обычный игрок не должен быть админом')

	const two = await makeUser('guest2', 'player_two', 'secret456')
	const three = await makeUser('guest3', 'player_three', 'secret789')
	ok('1. авторизация')

	// 2. Открытое лобби на 3 игроков
	host.send({ t: 'create_lobby', name: 'Тестовая игра', maxPlayers: 3 })
	const created = await host.wait('lobby_created')
	const code = created.lobby.code
	assert(created.lobby.maxPlayers === 3, 'лобби должно быть на 3 игроков')
	assert(created.lobby.visibility === 'public', 'по умолчанию лобби открытое')

	two.client.send({ t: 'join_lobby', code })
	const joined2 = await two.client.wait('lobby_joined')
	assert(joined2.role === 'P2', 'второй игрок должен получить роль P2')
	await host.wait('peer_joined')

	three.client.send({ t: 'join_lobby', code })
	const joined3 = await three.client.wait('lobby_joined')
	assert(joined3.role === 'P3', 'третий игрок должен получить роль P3')
	await host.wait('peer_joined')
	ok('2. лобби на 3 игроков')

	// 3. Старт, видео и действия
	host.send({ t: 'start' })
	await host.wait('started')
	await two.client.wait('started')
	await three.client.wait('started')

	host.sendBinary(fakeFrame(1))
	await two.client.wait('__binary__')
	await three.client.wait('__binary__')

	two.client.send({ t: 'input', action: 'Confirm', down: true })
	const input = await host.wait('input')
	assert(input.role === 'P2' && input.action === 'Confirm' && input.down === true, 'действие пришло искажённым')

	two.client.send({ t: 'input', action: 'NoSuchAction', down: true })
	const badAction = await two.client.wait('error')
	assert(badAction.code === 'bad_action', 'неизвестное действие должно отклоняться')
	ok('3. трансляция и ввод')

	// 4. Кик игрока хостом
	host.send({ t: 'kick', login: 'player_three', reason: 'тест' })
	const kicked = await three.client.wait('kicked')
	assert(kicked.scope === 'lobby' && kicked.banned !== true, 'кик не должен быть баном')
	await host.wait('lobby_state')
	ok('4. кик игрока')

	// 5. Бан в лобби: повторный вход запрещён
	host.send({ t: 'ban', login: 'player_two', reason: 'тест бана' })
	const banned = await two.client.wait('kicked')
	assert(banned.banned === true, 'бан должен помечаться флагом banned')
	await host.wait('lobby_state')

	two.client.send({ t: 'join_lobby', code })
	const banError = await two.client.wait('error')
	assert(banError.code === 'lobby_banned', 'забаненный не должен заходить, пришло ' + banError.code)

	host.send({ t: 'unban', login: 'player_two' })
	await host.wait('lobby_state')
	two.client.send({ t: 'join_lobby', code })
	await two.client.wait('lobby_joined')
	await host.wait('peer_joined')
	ok('5. бан и разбан в лобби')

	// 6. Закрытое лобби: пароль и список логинов
	host.send({ t: 'lobby_settings', visibility: 'private', joinMode: 'password', password: 'hunter22' })
	await host.wait('lobby_state')

	const listCheck = new TestClient('lurker')
	await listCheck.connect()
	await listCheck.wait('hello')
	listCheck.send({ t: 'register', login: 'player_four', password: 'secret000' })
	await listCheck.wait('auth_ok')
	listCheck.send({ t: 'list_lobbies' })
	const list = await listCheck.wait('lobby_list')
	assert(!list.lobbies.some((l) => l.code === code), 'закрытое лобби не должно быть в общем списке')

	listCheck.send({ t: 'join_lobby', code, password: 'неправильный' })
	const wrongPassword = await listCheck.wait('error')
	assert(wrongPassword.code === 'bad_lobby_password', 'нужна ошибка bad_lobby_password, пришло ' + wrongPassword.code)

	listCheck.send({ t: 'join_lobby', code, password: 'hunter22' })
	await listCheck.wait('lobby_joined')
	await host.wait('peer_joined')

	host.send({ t: 'kick', login: 'player_four' })
	await listCheck.wait('kicked')
	await host.wait('lobby_state')

	host.send({ t: 'lobby_settings', visibility: 'private', joinMode: 'whitelist', allowList: ['player_two'] })
	await host.wait('lobby_state')
	listCheck.send({ t: 'join_lobby', code })
	const notInvited = await listCheck.wait('error')
	assert(notInvited.code === 'not_invited', 'нужна ошибка not_invited, пришло ' + notInvited.code)
	ok('6. закрытое лобби по паролю и по списку логинов')

	// 7. Закрытие лобби хостом
	host.send({ t: 'close_lobby' })
	const closedForGuest = await two.client.wait('lobby_closed')
	assert(closedForGuest.code === code, 'гостю должно прийти lobby_closed с кодом лобби')
	await host.wait('lobby_closed')

	two.client.send({ t: 'join_lobby', code })
	const gone = await two.client.wait('error')
	assert(gone.code === 'no_lobby', 'после закрытия лобби должно исчезнуть')
	ok('7. закрытие лобби хостом')

	// 8. Админка: только для s4msepi0l
	host.send({ t: 'admin_users' })
	const forbidden = await host.wait('error')
	assert(forbidden.code === 'forbidden', 'обычному игроку админка запрещена, пришло ' + forbidden.code)

	const admin = await makeUser('admin', 's4msepi0l', 'adminpass1')
	assert(admin.auth.isAdmin === true, 'учётка s4msepi0l должна быть админом')

	admin.client.send({ t: 'admin_users' })
	const users = await admin.client.wait('admin_users')
	assert(users.users.some((u) => u.login === 'player_one'), 'в списке должны быть все игроки')

	// Переливающийся ник выдаётся и тут же доезжает до самого игрока.
	admin.client.send({ t: 'admin_set_cosmetic', login: 'player_one', rainbow: true, tag: 'VIP' })
	const updated = await admin.client.wait('admin_user')
	assert(updated.user.cosmetic.rainbow === true, 'радуга не выдалась')
	const profile = await host.wait('profile')
	assert(profile.user.cosmetic.rainbow === true && profile.user.cosmetic.tag === 'VIP', 'игрок не получил свои украшения')

	admin.client.send({ t: 'admin_broadcast', text: 'Сервер перезапустится' })
	await admin.client.wait('admin_broadcast_ok')
	const announce = await host.wait('announce')
	assert(announce.text === 'Сервер перезапустится', 'объявление не дошло')

	admin.client.send({ t: 'admin_stats' })
	const stats = await admin.client.wait('admin_stats')
	assert(typeof stats.stats.online === 'number', 'в статистике нет поля online')
	ok('8. админ-панель и переливающиеся ники')

	// 9. Админ банит на всём сервере и закрывает чужое лобби
	two.client.send({ t: 'create_lobby', name: 'Лобби гостя', maxPlayers: 2 })
	const guestLobby = await two.client.wait('lobby_created')
	admin.client.send({ t: 'admin_close_lobby', code: guestLobby.lobby.code })
	await admin.client.wait('admin_lobby_closed')
	await two.client.wait('lobby_closed')

	admin.client.send({ t: 'admin_ban', login: 'player_three', reason: 'правила' })
	await admin.client.wait('admin_user')
	const serverKick = await three.client.wait('kicked')
	assert(serverKick.scope === 'server', 'бан на сервере должен отключать игрока')

	const rejected = new TestClient('banned')
	await rejected.connect()
	await rejected.wait('hello')
	rejected.send({ t: 'login', login: 'player_three', password: 'secret789' })
	const bannedLogin = await rejected.wait('error')
	assert(bannedLogin.code === 'banned', 'забаненный не должен входить, пришло ' + bannedLogin.code)
	ok('9. бан на сервере и закрытие чужого лобби')

	// 10. Защита владельца админки
	admin.client.send({ t: 'admin_set_role', login: 's4msepi0l', role: 'user' })
	const demote = await admin.client.wait('error')
	assert(demote.code === 'cannot_demote_owner', 'владельца админки нельзя разжаловать, пришло ' + demote.code)
	ok('10. учётку владельца нельзя сломать')

	for (const c of [host, two.client, three.client, listCheck, admin.client, rejected]) c.close()
	await new Promise((resolve) => setTimeout(resolve, 100))
	console.log('\nВСЕ ТЕСТЫ ПРОШЛИ')
}

main()
	.then(() => {
		server.close()
		fs.rmSync(dataFile, { force: true })
		process.exit(0)
	})
	.catch((error) => {
		console.error('\nТЕСТ ПРОВАЛЕН: ' + error.message)
		server.close()
		fs.rmSync(dataFile, { force: true })
		process.exit(1)
	})
