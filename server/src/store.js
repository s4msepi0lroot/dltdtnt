// DeltaDotNet - persistent JSON storage (users + bans + settings)
import fs from 'node:fs'
import path from 'node:path'

const DATA_DIR = process.env.DDN_DATA_DIR || path.resolve(process.cwd(), 'data')
const DATA_FILE = path.join(DATA_DIR, 'db.json')

const DEFAULT_DB = {
  users: {},        // id -> user
  usernames: {},    // lowercase username -> id
  globalBans: {},   // userId -> { reason, at, by }
  motd: 'Welcome to DeltaDotNet!',
  version: 1
}

let db = null
let saveTimer = null

export function loadDb () {
  if (db) return db
  try {
    fs.mkdirSync(DATA_DIR, { recursive: true })
    if (fs.existsSync(DATA_FILE)) {
      db = { ...DEFAULT_DB, ...JSON.parse(fs.readFileSync(DATA_FILE, 'utf8')) }
    } else {
      db = structuredClone(DEFAULT_DB)
      saveNow()
    }
  } catch (err) {
    console.error('[store] failed to load db, starting empty:', err.message)
    db = structuredClone(DEFAULT_DB)
  }
  return db
}

export function save () {
  if (saveTimer) return
  saveTimer = setTimeout(() => { saveTimer = null; saveNow() }, 250)
}

export function saveNow () {
  if (!db) return
  try {
    fs.mkdirSync(DATA_DIR, { recursive: true })
    fs.writeFileSync(DATA_FILE + '.tmp', JSON.stringify(db, null, 2))
    fs.renameSync(DATA_FILE + '.tmp', DATA_FILE)
  } catch (err) {
    console.error('[store] save failed:', err.message)
  }
}

process.on('SIGINT', () => { saveNow(); process.exit(0) })
process.on('SIGTERM', () => { saveNow(); process.exit(0) })

export function getDb () { return loadDb() }
