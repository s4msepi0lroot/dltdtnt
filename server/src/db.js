'use strict';
// DeltaDotNet - simple JSON file storage (no external DB required).
const fs = require('fs');
const path = require('path');

const DATA_DIR = process.env.DDN_DATA_DIR || path.join(__dirname, '..', 'data');
const FILE = path.join(DATA_DIR, 'db.json');

const DEFAULT_STATE = {
  version: 1,
  users: {},        // lowercased login -> user record
  globalBans: {},   // lowercased login -> { reason, at, by }
  settings: {
    motd: 'Welcome to DeltaDotNet!',
    maintenance: false,
    maxLobbies: 200,
    maxPlayersHardCap: 8
  }
};

let state = null;
let saveTimer = null;

function ensureDir() {
  if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
}

function load() {
  ensureDir();
  if (fs.existsSync(FILE)) {
    try {
      state = JSON.parse(fs.readFileSync(FILE, 'utf8'));
    } catch (e) {
      console.error('[db] corrupted db.json, starting fresh:', e.message);
      state = JSON.parse(JSON.stringify(DEFAULT_STATE));
    }
  } else {
    state = JSON.parse(JSON.stringify(DEFAULT_STATE));
  }
  // migrate missing keys
  for (const k of Object.keys(DEFAULT_STATE)) {
    if (state[k] === undefined) state[k] = JSON.parse(JSON.stringify(DEFAULT_STATE[k]));
  }
  return state;
}

function get() {
  if (!state) load();
  return state;
}

function saveNow() {
  if (!state) return;
  ensureDir();
  const tmp = FILE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(state, null, 2), 'utf8');
  fs.renameSync(tmp, FILE);
}

// debounced save so we do not hammer the disk
function save() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try { saveNow(); } catch (e) { console.error('[db] save failed:', e.message); }
  }, 400);
}

process.on('exit', () => { try { saveNow(); } catch (_) {} });

module.exports = { get, save, saveNow, load, FILE, DATA_DIR };
