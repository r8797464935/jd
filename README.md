# JMail — a Gmail-like email app

A self-contained webmail application styled after Gmail. You can **create an
account**, **sign in**, and **send / receive email** with other users on the
platform — all from a familiar inbox UI.

> **Note:** This is a self-hosted demo. It does **not** create real Google /
> Gmail accounts (Google does not allow that programmatically) and it does not
> talk to the public internet email system (SMTP). Every account lives inside
> this app under the `@jmail.local` domain, and mail is delivered between
> registered JMail users.

## Features

- **Create account** — pick a username, get `username@jmail.local`. Passwords
  are hashed with bcrypt.
- **Sign in / out** — token-based sessions, remembered across reloads.
- **Compose & send** — write to any other registered user (by username or full
  address). A new account even gets a welcome email.
- **Receive** — incoming mail lands in the recipient's Inbox with unread counts.
- **Folders** — Inbox, Starred, Sent, Drafts, Trash.
- **Read & manage** — open messages, reply, star, save drafts, delete (move to
  Trash, then delete forever), search across your mail.

## Tech stack

- **Backend:** Node.js + Express, SQLite (`better-sqlite3`), bcrypt.
- **Frontend:** vanilla HTML/CSS/JS single-page app (no build step).

## Run it

```bash
npm install
npm start
# open http://localhost:3000
```

Then **Create account** for two different users (e.g. `ada` and `grace`) in two
browser windows and email each other to see delivery in action.

### Configuration

| Variable        | Default       | Description                          |
| --------------- | ------------- | ------------------------------------ |
| `PORT`          | `3000`        | HTTP port                            |
| `JMAIL_DOMAIN`  | `jmail.local` | Domain for issued addresses          |
| `JMAIL_DB`      | `./jmail.db`  | SQLite database file path            |

## API overview

| Method & path              | Description                          |
| -------------------------- | ------------------------------------ |
| `POST /api/register`       | Create account, returns a token      |
| `POST /api/login`          | Sign in, returns a token             |
| `POST /api/logout`         | Invalidate the current session       |
| `GET  /api/me`             | Current user                         |
| `GET  /api/folders/:name`  | List a folder (inbox/starred/sent/…) |
| `POST /api/messages`       | Send a message (or save a draft)     |
| `PATCH /api/mailbox/:id`   | Mark read/unread, star, move folder  |
| `DELETE /api/mailbox/:id`  | Trash, or delete forever if trashed  |

All mail routes require an `Authorization: Bearer <token>` header.
