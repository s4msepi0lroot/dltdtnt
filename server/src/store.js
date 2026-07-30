'use strict'
/**
 * Простое хранилище пользователей в JSON-файле.
 * Пароли хранятся как scrypt-хеш с индивидуальной солью (никогда в открытом виде).
 */
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

const SCRYPT_KEYLEN = 32

class UserStore {
  /** @param {string} filePath путь к JSON-файлу базы */
  constructor(filePath) {
    this.filePath = filePath
    this.users = new Map()
    this._load()
  }

  _load() {
    try {
      const raw = fs.readFileSync(this.filePath, 'utf8')
      const parsed = JSON.parse(raw)
      for (const u of parsed.users || []) this.users.set(u.login.toLowerCase(), u)
    } catch (err) {
      if (err.code !== 'ENOENT') throw err
      this.users = new Map()
    }
  }

  _save() {
    const dir = path.dirname(this.filePath)
    fs.mkdirSync(dir, { recursive: true })
    const tmp = `${this.filePath}.tmp`
    fs.writeFileSync(tmp, JSON.stringify({ users: [...this.users.values()] }, null, 2))
    fs.renameSync(tmp, this.filePath)
  }

  static hashPassword(password, salt = crypto.randomBytes(16).toString('hex')) {
    const hash = crypto.scryptSync(password, salt, SCRYPT_KEYLEN).toString('hex')
    return { salt, hash }
  }

  has(login) {
    return this.users.has(String(login).toLowerCase())
  }

  /** Создаёт пользователя. Бросает Error('user_exists'), если логин занят. */
  create(login, password) {
    const key = String(login).toLowerCase()
    if (this.users.has(key)) throw new Error('user_exists')
    const { salt, hash } = UserStore.hashPassword(password)
    const user = { login: String(login), salt, hash, createdAt: new Date().toISOString() }
    this.users.set(key, user)
    this._save()
    return { login: user.login }
  }

  /** Проверяет пару логин/пароль. */
  verify(login, password) {
    const user = this.users.get(String(login).toLowerCase())
    if (!user) return null
    const candidate = crypto.scryptSync(password, user.salt, SCRYPT_KEYLEN)
    const known = Buffer.from(user.hash, 'hex')
    if (candidate.length !== known.length) return null
    if (!crypto.timingSafeEqual(candidate, known)) return null
    return { login: user.login }
  }
}

module.exports = { UserStore }
