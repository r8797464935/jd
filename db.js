'use strict';

const path = require('path');
const Database = require('better-sqlite3');

const DB_PATH = process.env.JMAIL_DB || path.join(__dirname, 'jmail.db');
const db = new Database(DB_PATH);

db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

db.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT NOT NULL UNIQUE,
    email         TEXT NOT NULL UNIQUE,
    display_name  TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS sessions (
    token      TEXT PRIMARY KEY,
    user_id    INTEGER NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
  );

  -- A message is stored once; each recipient/sender gets a "mail" row that
  -- represents the message in one of their folders (inbox/sent/etc).
  CREATE TABLE IF NOT EXISTS messages (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    from_email  TEXT NOT NULL,
    from_name   TEXT NOT NULL,
    to_email    TEXT NOT NULL,
    subject     TEXT NOT NULL DEFAULT '',
    body        TEXT NOT NULL DEFAULT '',
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS mailbox (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL,
    message_id INTEGER NOT NULL,
    folder     TEXT NOT NULL,            -- inbox | sent | drafts | trash
    is_read    INTEGER NOT NULL DEFAULT 0,
    is_starred INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
  );

  CREATE INDEX IF NOT EXISTS idx_mailbox_user_folder ON mailbox(user_id, folder);
  CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
`);

module.exports = db;
