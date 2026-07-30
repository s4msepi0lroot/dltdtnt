'use strict'
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

const SCRYPT_KEYLEN = 32

function emptyCosmetic() {
  return { rainbow: false, color: null, tag: null }
}

function normalizeCosmetic(value) {
  const c = emptyCosmetic()
  if (!value || typeof value !== 'object') return c
  c.rainbow = !!value.rainbow
  if (typeof value.color === 'string' && /^#[0-9a-fA-F]{6}$/.test(value.color)) {
    c.color = value.color.toUpperCase()
  }
  if (typeof value.tag === 'string' && value.tag.trim()) {
    c.tag = value.tag.trim().slice(0, 16)
  }
  return c
}

class UserStore {
  constructor(filePath, adminLogin = '') {
    this.filePath = filePath
    this.adminLogin = String(adminLogin || '').toLowerCase()
    this.users = new Map()
    this._load()
  }

  _load() {
    try {
      const raw = fs.readFileSync(this.filePath, 'utf8')
      const parsed = JSON.parse(raw)
      for (const u of parsed.users || []) {
        u.role = u.role || 'user'
        u.banned = !!u.banned
        u.banReason = u.banReason || null
        u.cosmetic = normalizeCosmetic(u.cosmetic)
        u.lastSeen = u.lastSeen || null
        this.users.set(u.login.toLowerCase(), u)
      }
    } catch (err) {
      if (err.code !== 'ENOENT') throw err
      this.users = new Map()
    }
    this._applyAdmin()
  }

  _applyAdmin() {
    if (!this.adminLogin) return
    const user = this.users.get(this.adminLogin)
    if (user && (user.role !== 'admin' || user.banned)) {
      user.role = 'admin'
      user.banned = false
      user.banReason = null
      this._save()
    }
  }

  _save() {
    const dir = path.dirname(this.filePath)
    fs.mkdirSync(dir, { recursive: true })
    const tmp = this.filePath + '.tmp'
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

  raw(login) {
    return this.users.get(String(login).toLowerCase()) || null
  }

  publicUser(login) {
    const u = this.raw(login)
    if (!u) return null
    return {
      login: u.login,
      role: u.role,
      banned: u.banned,
      banReason: u.banReason,
      cosmetic: u.cosmetic,
      createdAt: u.createdAt,
      lastSeen: u.lastSeen,
    }
  }

  isAdmin(login) {
    const u = this.raw(login)
    return !!u && u.role === 'admin'
  }

  create(login, password) {
    const key = String(login).toLowerCase()
    if (this.users.has(key)) throw new Error('user_exists')
    const { salt, hash } = UserStore.hashPassword(password)
    const user = {
      login: String(login),
      salt,
      hash,
      role: key === this.adminLogin ? 'admin' : 'user',
      banned: false,
      banReason: null,
      cosmetic: emptyCosmetic(),
      createdAt: new Date().toISOString(),
      lastSeen: null,
    }
    this.users.set(key, user)
    this._save()
    return this.publicUser(user.login)
  }

  verify(login, password) {
    const user = this.raw(login)
    if (!user) return null
    const candidate = crypto.scryptSync(password, user.salt, SCRYPT_KEYLEN)
    const known = Buffer.from(user.hash, 'hex')
    if (candidate.length !== known.length) return null
    if (!crypto.timingSafeEqual(candidate, known)) return null
    return this.publicUser(user.login)
  }

  touch(login) {
    const user = this.raw(login)
    if (!user) return
    user.lastSeen = new Date().toISOString()
    this._save()
  }

  setBanned(login, banned, reason = null) {
    const user = this.raw(login)
    if (!user) return null
    if (user.role === 'admin' && banned) throw new Error('cannot_ban_admin')
    user.banned = !!banned
    user.banReason = banned ? (reason ? String(reason).slice(0, 120) : 'без указания причины') : null
    this._save()
    return this.publicUser(user.login)
  }

  setRole(login, role) {
    const user = this.raw(login)
    if (!user) return null
    if (role !== 'admin' && role !== 'user') throw new Error('bad_role')
    if (user.login.toLowerCase() === this.adminLogin && role !== 'admin') {
      throw new Error('cannot_demote_owner')
    }
    user.role = role
    this._save()
    return this.publicUser(user.login)
  }

  setCosmetic(login, cosmetic) {
    const user = this.raw(login)
    if (!user) return null
    user.cosmetic = normalizeCosmetic(cosmetic)
    this._save()
    return this.publicUser(user.login)
  }

  setPassword(login, password) {
    const user = this.raw(login)
    if (!user) return null
    const { salt, hash } = UserStore.hashPassword(password)
    user.salt = salt
    user.hash = hash
    this._save()
    return this.publicUser(user.login)
  }

  remove(login) {
    const key = String(login).toLowerCase()
    const user = this.users.get(key)
    if (!user) return false
    if (key === this.adminLogin) throw new Error('cannot_delete_owner')
    this.users.delete(key)
    this._save()
    return true
  }

  list() {
    return [...this.users.keys()]
      .map((k) => this.publicUser(k))
      .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
  }
}

module.exports = { UserStore, emptyCosmetic, normalizeCosmetic }
