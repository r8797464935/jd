'use strict';

const path = require('path');
const crypto = require('crypto');
const express = require('express');
const bcrypt = require('bcryptjs');
const db = require('./db');

const app = express();
const PORT = process.env.PORT || 3000;

// Every account on this platform lives under this domain. Real Gmail accounts
// can't be created programmatically, so JMail issues its own addresses.
const MAIL_DOMAIN = process.env.JMAIL_DOMAIN || 'jmail.local';

app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.join(__dirname, 'public')));

// ----- prepared statements -------------------------------------------------
const stmts = {
  findUserByUsername: db.prepare('SELECT * FROM users WHERE username = ?'),
  findUserByEmail: db.prepare('SELECT * FROM users WHERE email = ?'),
  findUserById: db.prepare('SELECT * FROM users WHERE id = ?'),
  insertUser: db.prepare(
    'INSERT INTO users (username, email, display_name, password_hash) VALUES (?, ?, ?, ?)'
  ),
  insertSession: db.prepare('INSERT INTO sessions (token, user_id) VALUES (?, ?)'),
  findSession: db.prepare('SELECT * FROM sessions WHERE token = ?'),
  deleteSession: db.prepare('DELETE FROM sessions WHERE token = ?'),
  insertMessage: db.prepare(
    `INSERT INTO messages (from_email, from_name, to_email, subject, body)
     VALUES (@from_email, @from_name, @to_email, @subject, @body)`
  ),
  insertMailbox: db.prepare(
    `INSERT INTO mailbox (user_id, message_id, folder, is_read, is_starred)
     VALUES (@user_id, @message_id, @folder, @is_read, @is_starred)`
  ),
  listFolder: db.prepare(
    `SELECT mb.id AS mailbox_id, mb.folder, mb.is_read, mb.is_starred,
            m.id AS message_id, m.from_email, m.from_name, m.to_email,
            m.subject, m.body, m.created_at
       FROM mailbox mb
       JOIN messages m ON m.id = mb.message_id
      WHERE mb.user_id = ? AND mb.folder = ?
      ORDER BY m.created_at DESC, m.id DESC`
  ),
  listStarred: db.prepare(
    `SELECT mb.id AS mailbox_id, mb.folder, mb.is_read, mb.is_starred,
            m.id AS message_id, m.from_email, m.from_name, m.to_email,
            m.subject, m.body, m.created_at
       FROM mailbox mb
       JOIN messages m ON m.id = mb.message_id
      WHERE mb.user_id = ? AND mb.is_starred = 1 AND mb.folder != 'trash'
      ORDER BY m.created_at DESC, m.id DESC`
  ),
  getMailbox: db.prepare('SELECT * FROM mailbox WHERE id = ? AND user_id = ?'),
  markRead: db.prepare('UPDATE mailbox SET is_read = ? WHERE id = ? AND user_id = ?'),
  setStar: db.prepare('UPDATE mailbox SET is_starred = ? WHERE id = ? AND user_id = ?'),
  setFolder: db.prepare('UPDATE mailbox SET folder = ? WHERE id = ? AND user_id = ?'),
  deleteMailbox: db.prepare('DELETE FROM mailbox WHERE id = ? AND user_id = ?'),
  unreadCount: db.prepare(
    `SELECT COUNT(*) AS n FROM mailbox
      WHERE user_id = ? AND folder = 'inbox' AND is_read = 0`
  ),
};

// ----- helpers -------------------------------------------------------------
function publicUser(u) {
  return { id: u.id, username: u.username, email: u.email, displayName: u.display_name };
}

function rowToMail(r) {
  return {
    mailboxId: r.mailbox_id,
    messageId: r.message_id,
    folder: r.folder,
    isRead: !!r.is_read,
    isStarred: !!r.is_starred,
    from: { email: r.from_email, name: r.from_name },
    to: r.to_email,
    subject: r.subject,
    body: r.body,
    date: r.created_at,
  };
}

function auth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) return res.status(401).json({ error: 'Not authenticated' });
  const session = stmts.findSession.get(token);
  if (!session) return res.status(401).json({ error: 'Invalid session' });
  const user = stmts.findUserById.get(session.user_id);
  if (!user) return res.status(401).json({ error: 'Invalid session' });
  req.user = user;
  req.token = token;
  next();
}

const USERNAME_RE = /^[a-z0-9](?:[a-z0-9._]{1,28}[a-z0-9])$/;

// ----- auth routes ---------------------------------------------------------
app.post('/api/register', (req, res) => {
  let { username, displayName, password } = req.body || {};
  username = String(username || '').trim().toLowerCase();
  displayName = String(displayName || '').trim();
  password = String(password || '');

  if (!USERNAME_RE.test(username)) {
    return res.status(400).json({
      error:
        'Username must be 3-30 chars: lowercase letters, numbers, dots or underscores (not at the ends).',
    });
  }
  if (password.length < 6) {
    return res.status(400).json({ error: 'Password must be at least 6 characters.' });
  }
  if (!displayName) displayName = username;

  const email = `${username}@${MAIL_DOMAIN}`;
  if (stmts.findUserByUsername.get(username) || stmts.findUserByEmail.get(email)) {
    return res.status(409).json({ error: 'That username is already taken.' });
  }

  const hash = bcrypt.hashSync(password, 10);
  const info = stmts.insertUser.run(username, email, displayName, hash);
  const user = stmts.findUserById.get(info.lastInsertRowid);

  // Welcome email so a brand-new inbox isn't empty.
  deliver({
    from_email: 'team@' + MAIL_DOMAIN,
    from_name: 'The JMail Team',
    to_email: email,
    subject: 'Welcome to JMail ✉️',
    body:
      `Hi ${displayName},\n\n` +
      `Welcome to JMail! Your address is ${email}.\n\n` +
      `Invite a friend to register, then send each other a message to see ` +
      `delivery in action. Use Compose to write a new email.\n\n` +
      `Happy emailing,\nThe JMail Team`,
  });

  const token = createSession(user.id);
  res.status(201).json({ token, user: publicUser(user) });
});

app.post('/api/login', (req, res) => {
  let { username, password } = req.body || {};
  username = String(username || '').trim().toLowerCase();
  password = String(password || '');

  // Allow login with either the username or the full email address.
  const user =
    stmts.findUserByUsername.get(username) || stmts.findUserByEmail.get(username);
  if (!user || !bcrypt.compareSync(password, user.password_hash)) {
    return res.status(401).json({ error: 'Invalid username or password.' });
  }
  const token = createSession(user.id);
  res.json({ token, user: publicUser(user) });
});

app.post('/api/logout', auth, (req, res) => {
  stmts.deleteSession.run(req.token);
  res.json({ ok: true });
});

app.get('/api/me', auth, (req, res) => {
  res.json({ user: publicUser(req.user) });
});

// ----- mail routes ---------------------------------------------------------
app.get('/api/folders/:folder', auth, (req, res) => {
  const folder = req.params.folder;
  let rows;
  if (folder === 'starred') {
    rows = stmts.listStarred.all(req.user.id);
  } else if (['inbox', 'sent', 'drafts', 'trash'].includes(folder)) {
    rows = stmts.listFolder.all(req.user.id, folder);
  } else {
    return res.status(404).json({ error: 'Unknown folder' });
  }
  res.json({
    folder,
    unread: stmts.unreadCount.get(req.user.id).n,
    messages: rows.map(rowToMail),
  });
});

app.post('/api/messages', auth, (req, res) => {
  let { to, subject, body, draft } = req.body || {};
  to = String(to || '').trim().toLowerCase();
  subject = String(subject || '').trim();
  body = String(body || '');
  const isDraft = !!draft;

  if (!isDraft) {
    if (!to) return res.status(400).json({ error: 'Recipient is required.' });
    // Be forgiving: allow bare username, append the domain automatically.
    if (!to.includes('@')) to = `${to}@${MAIL_DOMAIN}`;
    const recipient = stmts.findUserByEmail.get(to);
    if (!recipient) {
      return res.status(404).json({
        error: `No JMail user found at ${to}. They need to register first.`,
      });
    }
  }

  const msg = {
    from_email: req.user.email,
    from_name: req.user.display_name,
    to_email: to,
    subject,
    body,
  };

  if (isDraft) {
    const info = stmts.insertMessage.run(msg);
    stmts.insertMailbox.run({
      user_id: req.user.id,
      message_id: info.lastInsertRowid,
      folder: 'drafts',
      is_read: 1,
      is_starred: 0,
    });
    return res.status(201).json({ ok: true, folder: 'drafts' });
  }

  deliver(msg, req.user.id);
  res.status(201).json({ ok: true, folder: 'sent' });
});

app.patch('/api/mailbox/:id', auth, (req, res) => {
  const mb = stmts.getMailbox.get(req.params.id, req.user.id);
  if (!mb) return res.status(404).json({ error: 'Message not found' });
  const { isRead, isStarred, folder } = req.body || {};
  if (typeof isRead === 'boolean') stmts.markRead.run(isRead ? 1 : 0, mb.id, req.user.id);
  if (typeof isStarred === 'boolean') stmts.setStar.run(isStarred ? 1 : 0, mb.id, req.user.id);
  if (folder && ['inbox', 'sent', 'drafts', 'trash'].includes(folder)) {
    stmts.setFolder.run(folder, mb.id, req.user.id);
  }
  res.json({ ok: true });
});

// Move to trash, or permanently delete when already in trash.
app.delete('/api/mailbox/:id', auth, (req, res) => {
  const mb = stmts.getMailbox.get(req.params.id, req.user.id);
  if (!mb) return res.status(404).json({ error: 'Message not found' });
  if (mb.folder === 'trash') {
    stmts.deleteMailbox.run(mb.id, req.user.id);
    res.json({ ok: true, deleted: true });
  } else {
    stmts.setFolder.run('trash', mb.id, req.user.id);
    res.json({ ok: true, trashed: true });
  }
});

// ----- core delivery logic -------------------------------------------------
function createSession(userId) {
  const token = crypto.randomBytes(24).toString('hex');
  stmts.insertSession.run(token, userId);
  return token;
}

// Insert the message once and fan it out into the sender's Sent folder and the
// recipient's Inbox. senderId is omitted for system-generated mail.
const deliver = db.transaction((msg, senderId) => {
  const info = stmts.insertMessage.run(msg);
  const messageId = info.lastInsertRowid;

  if (senderId) {
    stmts.insertMailbox.run({
      user_id: senderId,
      message_id: messageId,
      folder: 'sent',
      is_read: 1,
      is_starred: 0,
    });
  }

  const recipient = stmts.findUserByEmail.get(msg.to_email);
  if (recipient) {
    stmts.insertMailbox.run({
      user_id: recipient.id,
      message_id: messageId,
      folder: 'inbox',
      is_read: 0,
      is_starred: 0,
    });
  }
  return messageId;
});

// SPA fallback
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`JMail running at http://localhost:${PORT}  (domain: @${MAIL_DOMAIN})`);
});
