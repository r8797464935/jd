'use strict';

// ----------------------------- state ---------------------------------------
const state = {
  token: localStorage.getItem('jmail_token') || null,
  user: null,
  folder: 'inbox',
  messages: [],
  search: '',
  current: null, // mailboxId being read
};

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

// ----------------------------- API -----------------------------------------
async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  const res = await fetch(path, { ...options, headers });
  let data = {};
  try { data = await res.json(); } catch (_) {}
  if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
  return data;
}

// ----------------------------- helpers -------------------------------------
function initials(name) {
  const parts = (name || '?').trim().split(/\s+/);
  return ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase() || '?';
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])
  );
}

function formatDate(iso) {
  // SQLite returns UTC "YYYY-MM-DD HH:MM:SS"
  const d = new Date(iso.replace(' ', 'T') + 'Z');
  const now = new Date();
  const sameDay = d.toDateString() === now.toDateString();
  if (sameDay) {
    return d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  }
  const sameYear = d.getFullYear() === now.getFullYear();
  return d.toLocaleDateString([], { month: 'short', day: 'numeric', ...(sameYear ? {} : { year: 'numeric' }) });
}

function snippet(body) {
  return body.replace(/\s+/g, ' ').slice(0, 100);
}

let toastTimer;
function toast(msg) {
  const el = $('#toast');
  el.textContent = msg;
  el.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add('hidden'), 3000);
}

const FOLDER_TITLES = {
  inbox: 'Inbox', starred: 'Starred', sent: 'Sent', drafts: 'Drafts', trash: 'Trash',
};

// ----------------------------- auth UI -------------------------------------
function showAuth() {
  $('#auth').classList.remove('hidden');
  $('#app').classList.add('hidden');
}

function showApp() {
  $('#auth').classList.add('hidden');
  $('#app').classList.remove('hidden');
  const u = state.user;
  $('#user-email').textContent = u.email;
  $('#avatar-btn').textContent = initials(u.displayName);
  $('#menu-name').textContent = u.displayName;
  $('#menu-email').textContent = u.email;
  loadFolder('inbox');
}

$$('[data-show]').forEach((btn) => {
  btn.addEventListener('click', () => {
    const target = btn.dataset.show;
    $('#login-form').classList.toggle('hidden', target !== 'login');
    $('#register-form').classList.toggle('hidden', target !== 'register');
  });
});

$('#login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('#login-error').textContent = '';
  try {
    const data = await api('/api/login', {
      method: 'POST',
      body: JSON.stringify({
        username: $('#login-username').value,
        password: $('#login-password').value,
      }),
    });
    setSession(data);
  } catch (err) {
    $('#login-error').textContent = err.message;
  }
});

$('#register-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('#register-error').textContent = '';
  try {
    const data = await api('/api/register', {
      method: 'POST',
      body: JSON.stringify({
        username: $('#reg-username').value,
        displayName: $('#reg-name').value,
        password: $('#reg-password').value,
      }),
    });
    setSession(data);
    toast(`Account created: ${data.user.email}`);
  } catch (err) {
    $('#register-error').textContent = err.message;
  }
});

function setSession(data) {
  state.token = data.token;
  state.user = data.user;
  localStorage.setItem('jmail_token', data.token);
  showApp();
}

$('#logout-btn').addEventListener('click', async () => {
  try { await api('/api/logout', { method: 'POST' }); } catch (_) {}
  state.token = null;
  state.user = null;
  localStorage.removeItem('jmail_token');
  $('#account-menu').classList.add('hidden');
  showAuth();
});

$('#avatar-btn').addEventListener('click', (e) => {
  e.stopPropagation();
  $('#account-menu').classList.toggle('hidden');
});
document.addEventListener('click', () => $('#account-menu').classList.add('hidden'));
$('#account-menu').addEventListener('click', (e) => e.stopPropagation());

// ----------------------------- folders / list ------------------------------
$$('.folder').forEach((el) => {
  el.addEventListener('click', () => loadFolder(el.dataset.folder));
});
$('#refresh-btn').addEventListener('click', () => loadFolder(state.folder));

async function loadFolder(folder) {
  state.folder = folder;
  state.search = '';
  $('#search-input').value = '';
  closeReader();
  $$('.folder').forEach((el) => el.classList.toggle('active', el.dataset.folder === folder));
  $('#folder-title').textContent = FOLDER_TITLES[folder] || folder;
  try {
    const data = await api(`/api/folders/${folder}`);
    state.messages = data.messages;
    updateUnread(data.unread);
    renderList();
  } catch (err) {
    toast(err.message);
  }
}

function updateUnread(n) {
  const el = $('#count-inbox');
  el.textContent = n > 0 ? n : '';
}

function visibleMessages() {
  if (!state.search) return state.messages;
  const q = state.search.toLowerCase();
  return state.messages.filter((m) =>
    [m.subject, m.body, m.from.name, m.from.email, m.to].join(' ').toLowerCase().includes(q)
  );
}

function renderList() {
  const list = $('#mail-list');
  const msgs = visibleMessages();
  const empty = $('#empty-state');

  if (msgs.length === 0) {
    list.innerHTML = '';
    empty.classList.remove('hidden');
    empty.textContent = state.search
      ? 'No messages match your search.'
      : `Nothing in ${FOLDER_TITLES[state.folder] || state.folder}.`;
    return;
  }
  empty.classList.add('hidden');

  const showRecipient = state.folder === 'sent' || state.folder === 'drafts';
  list.innerHTML = msgs
    .map((m) => {
      const who = showRecipient ? `To: ${m.to}` : m.from.name;
      return `
      <li class="mail-row ${m.isRead ? '' : 'unread'}" data-id="${m.mailboxId}">
        <span class="star ${m.isStarred ? 'on' : ''}" data-star="${m.mailboxId}" title="Star">${m.isStarred ? '★' : '☆'}</span>
        <span class="mail-who">${escapeHtml(who)}</span>
        <span class="mail-main">
          <span class="mail-subject">${escapeHtml(m.subject || '(no subject)')}</span>
          <span class="mail-snippet"> — ${escapeHtml(snippet(m.body))}</span>
        </span>
        <button class="row-trash" data-trash="${m.mailboxId}" title="Delete">🗑</button>
        <span class="mail-date">${formatDate(m.date)}</span>
      </li>`;
    })
    .join('');
}

$('#mail-list').addEventListener('click', (e) => {
  const star = e.target.closest('[data-star]');
  if (star) { toggleStar(Number(star.dataset.star)); return; }
  const trash = e.target.closest('[data-trash]');
  if (trash) { trashMessage(Number(trash.dataset.trash)); return; }
  const row = e.target.closest('.mail-row');
  if (row) openMessage(Number(row.dataset.id));
});

$('#search-input').addEventListener('input', (e) => {
  state.search = e.target.value;
  renderList();
});

// ----------------------------- read / actions ------------------------------
function findMsg(id) {
  return state.messages.find((m) => m.mailboxId === id);
}

async function openMessage(id) {
  const m = findMsg(id);
  if (!m) return;
  state.current = id;

  if (state.folder === 'drafts') {
    openCompose({ to: m.to, subject: m.subject, body: m.body });
    return;
  }

  if (!m.isRead) {
    m.isRead = true;
    try {
      await api(`/api/mailbox/${id}`, { method: 'PATCH', body: JSON.stringify({ isRead: true }) });
      const data = await api(`/api/folders/inbox`).catch(() => null);
      if (data) updateUnread(data.unread);
    } catch (_) {}
    renderList();
  }

  $('#reader-subject').textContent = m.subject || '(no subject)';
  $('#reader-avatar').textContent = initials(m.from.name);
  $('#reader-from-name').textContent = m.from.name;
  $('#reader-from-email').textContent = `<${m.from.email}>`;
  $('#reader-to').textContent = `to ${m.to}`;
  $('#reader-date').textContent = formatDate(m.date);
  $('#reader-text').textContent = m.body;
  $('#reader-star').classList.toggle('on', m.isStarred);
  $('#reader-star').textContent = m.isStarred ? '★' : '☆';
  $('#reader').classList.remove('hidden');
}

function closeReader() {
  state.current = null;
  $('#reader').classList.add('hidden');
}

$('#reader-back').addEventListener('click', closeReader);
$('#reader-trash').addEventListener('click', () => {
  if (state.current != null) { trashMessage(state.current); closeReader(); }
});
$('#reader-star').addEventListener('click', () => {
  if (state.current != null) toggleStar(state.current);
});
$('#reader-reply').addEventListener('click', () => {
  const m = findMsg(state.current);
  if (!m) return;
  openCompose({
    to: m.from.email,
    subject: m.subject.startsWith('Re:') ? m.subject : `Re: ${m.subject}`,
    body: `\n\n----\nOn ${formatDate(m.date)}, ${m.from.name} <${m.from.email}> wrote:\n> ${m.body.replace(/\n/g, '\n> ')}`,
  });
});

async function toggleStar(id) {
  const m = findMsg(id);
  if (!m) return;
  m.isStarred = !m.isStarred;
  renderList();
  if (state.current === id) {
    $('#reader-star').classList.toggle('on', m.isStarred);
    $('#reader-star').textContent = m.isStarred ? '★' : '☆';
  }
  try {
    await api(`/api/mailbox/${id}`, { method: 'PATCH', body: JSON.stringify({ isStarred: m.isStarred }) });
  } catch (err) { toast(err.message); }
  if (state.folder === 'starred' && !m.isStarred) loadFolder('starred');
}

async function trashMessage(id) {
  try {
    const data = await api(`/api/mailbox/${id}`, { method: 'DELETE' });
    toast(data.deleted ? 'Deleted forever' : 'Moved to Trash');
    loadFolder(state.folder);
  } catch (err) { toast(err.message); }
}

// ----------------------------- compose -------------------------------------
function openCompose(prefill = {}) {
  $('#compose-error').textContent = '';
  $('#compose-to').value = prefill.to || '';
  $('#compose-subject').value = prefill.subject || '';
  $('#compose-body').value = prefill.body || '';
  $('#compose').classList.remove('hidden');
  $('#compose-to').focus();
}

$('#compose-btn').addEventListener('click', () => openCompose());
$('#compose-close').addEventListener('click', () => $('#compose').classList.add('hidden'));

$('#compose-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('#compose-error').textContent = '';
  try {
    await api('/api/messages', {
      method: 'POST',
      body: JSON.stringify({
        to: $('#compose-to').value,
        subject: $('#compose-subject').value,
        body: $('#compose-body').value,
      }),
    });
    $('#compose').classList.add('hidden');
    toast('Message sent');
    if (state.folder === 'sent') loadFolder('sent');
  } catch (err) {
    $('#compose-error').textContent = err.message;
  }
});

$('#save-draft').addEventListener('click', async () => {
  try {
    await api('/api/messages', {
      method: 'POST',
      body: JSON.stringify({
        to: $('#compose-to').value,
        subject: $('#compose-subject').value,
        body: $('#compose-body').value,
        draft: true,
      }),
    });
    $('#compose').classList.add('hidden');
    toast('Draft saved');
    if (state.folder === 'drafts') loadFolder('drafts');
  } catch (err) {
    $('#compose-error').textContent = err.message;
  }
});

// ----------------------------- misc ----------------------------------------
$('#menu-toggle').addEventListener('click', () => $('#sidebar').classList.toggle('collapsed'));

// ----------------------------- boot ----------------------------------------
(async function boot() {
  if (state.token) {
    try {
      const data = await api('/api/me');
      state.user = data.user;
      showApp();
      return;
    } catch (_) {
      localStorage.removeItem('jmail_token');
      state.token = null;
    }
  }
  showAuth();
})();
