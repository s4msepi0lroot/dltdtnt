'use strict'
const crypto = require('crypto')

function b64u(buf) {
  return Buffer.from(buf).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function unb64u(str) {
  return Buffer.from(str.replace(/-/g, '+').replace(/_/g, '/'), 'base64')
}

class TokenService {
  constructor(secret, ttlSeconds = 7 * 24 * 3600) {
    this.secret = secret
    this.ttl = ttlSeconds
  }

  sign(login) {
    const payload = { sub: login, iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + this.ttl }
    const body = b64u(JSON.stringify(payload))
    const sig = b64u(crypto.createHmac('sha256', this.secret).update(body).digest())
    return `${body}.${sig}`
  }

  verify(token) {
    if (typeof token !== 'string' || !token.includes('.')) return null
    const [body, sig] = token.split('.')
    const expected = crypto.createHmac('sha256', this.secret).update(body).digest()
    let given
    try {
      given = unb64u(sig)
    } catch (_) {
      return null
    }
    if (given.length !== expected.length || !crypto.timingSafeEqual(given, expected)) return null
    let payload
    try {
      payload = JSON.parse(unb64u(body).toString('utf8'))
    } catch (_) {
      return null
    }
    if (!payload.exp || payload.exp < Math.floor(Date.now() / 1000)) return null
    return payload
  }
}

module.exports = { TokenService }
