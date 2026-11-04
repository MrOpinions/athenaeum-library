/* Athenaeum Library — single-page UI. No framework, no build step.
   Every dynamic value goes through the `h` template tag, which HTML-escapes it. */
(() => {
'use strict';

const CUR = '₹';
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];

// ---------------------------------------------------------------- safe templating
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
class Safe { constructor(s) { this.s = s; } toString() { return this.s; } }
const raw = s => new Safe(s);
const part = v => v instanceof Safe ? v.s : Array.isArray(v) ? v.map(part).join('') : (v === false || v == null) ? '' : esc(v);
const h = (strs, ...vals) => new Safe(strs.reduce((a, s, i) => a + s + (i < vals.length ? part(vals[i]) : ''), ''));

// ---------------------------------------------------------------- small utilities
const ls = {
  get(k) { try { return localStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch { /* storage may be blocked */ } },
};
const money = n => CUR + Number(n || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const initials = n => (n || '?').split(/\s+/).filter(Boolean).map(w => w[0]).slice(0, 2).join('').toUpperCase();
const D = iso => iso ? new Date(iso.length === 10 ? iso + 'T00:00:00' : iso) : null;
const fmtDate = iso => iso ? D(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
const fmtDT = iso => iso ? D(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—';
const day0 = d => { const x = new Date(d); x.setHours(0, 0, 0, 0); return x; };
const daysTo = iso => Math.round((day0(D(iso)) - day0(new Date())) / 864e5);
const ago = ms => {
  const m = Math.round((Date.now() - ms) / 60000);
  return m < 1 ? 'just now' : m < 60 ? m + 'm ago' : m < 1440 ? Math.round(m / 60) + 'h ago' : Math.round(m / 1440) + 'd ago';
};
const shade = (hex, amt) => {
  if (!/^#[0-9a-f]{6}$/i.test(hex || '')) hex = '#8b7d6b';
  const n = parseInt(hex.slice(1), 16), c = v => Math.max(0, Math.min(255, v));
  return '#' + [(n >> 16) + amt, ((n >> 8) & 255) + amt, (n & 255) + amt].map(v => c(v).toString(16).padStart(2, '0')).join('');
};
const greeting = () => { const hr = new Date().getHours(); return hr < 5 ? 'Late night reading' : hr < 12 ? 'Good morning' : hr < 18 ? 'Good afternoon' : 'Good evening'; };
const qs = o => { const p = new URLSearchParams(); Object.entries(o).forEach(([k, v]) => { if (v !== '' && v != null) p.set(k, v); }); const s = p.toString(); return s ? '?' + s : ''; };

const ICONS = {
  home: 'M3 11l9-8 9 8v10a1 1 0 0 1-1 1h-5v-7h-6v7H4a1 1 0 0 1-1-1z',
  layers: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  book: 'M4 4h11a4 4 0 0 1 4 4v12H8a4 4 0 0 1-4-4zM8 4v12',
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM21 21l-4.3-4.3',
  clock: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 7v5l3 2',
  bookmark: 'M6 3h12v18l-6-4-6 4z',
  coins: 'M3 7h18v12H3zM3 7l3-4h12l3 4M16 13h2',
  heart: 'M12 21s-8-5.2-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 10c0 5.8-8 11-8 11z',
  gift: 'M4 13h4l1 3h6l1-3h4M4 13V6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v7M4 13v5a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-5',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0',
  users: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 21a7 7 0 0 1 14 0M17 3.5a4 4 0 0 1 0 7.5M22 21a6 6 0 0 0-4-5.6',
  scan: 'M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M7 12h10',
  shield: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6zM9 12l2 2 4-4',
  sun: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
  moon: 'M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z',
  bell: 'M6 8a6 6 0 0 1 12 0c0 7 3 8 3 8H3s3-1 3-8M10 20a2 2 0 0 0 4 0',
  logout: 'M9 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h4M16 8l4 4-4 4M20 12H9',
  plus: 'M12 5v14M5 12h14',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  x: 'M6 6l12 12M18 6L6 18',
  alert: 'M12 3l10 18H2zM12 10v5M12 18h.01',
  download: 'M12 4v11M7 11l5 5 5-5M5 20h14',
  star: 'M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z',
  refresh: 'M20 11a8 8 0 0 0-14.5-4M4 4v4h4M4 13a8 8 0 0 0 14.5 4M20 20v-4h-4',
  menu: 'M4 7h16M4 12h16M4 17h16',
  trend: 'M3 17l6-6 4 4 8-8M15 7h6v6',
  edit: 'M4 20h4l11-11-4-4L4 16zM13 7l4 4',
};
const ic = (n, cls = '') => raw(`<svg class="i ${cls}" viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONS[n] || ''}"/></svg>`);
const pill = (t, k = '') => h`<span class="pill ${k}">${t}</span>`;
const statusPill = s => pill(String(s).replace('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase()),
  { AVAILABLE: 'ok', ACTIVE: 'ok', PAID: 'ok', READY: 'ok', FULFILLED: 'ok', APPROVED: 'ok', PURCHASED: 'ok', ISSUED: 'info', WAITING: 'info', PENDING: 'info',
    UNPAID: 'bad', LOST: 'bad', SUSPENDED: 'bad', REJECTED: 'bad', DAMAGED: 'warn', ON_HOLD: 'warn', WITHDRAWN: '', EXPIRED: '', CANCELLED: '', WAIVED: '' }[s] || '');

function stars(avg, count) {
  if (avg == null) return h`<span class="stars"><small>No ratings yet</small></span>`;
  const full = Math.round(avg);
  return h`<span class="stars">${[1, 2, 3, 4, 5].map(i => raw(`<svg class="i fill ${i <= full ? '' : 'off'}" viewBox="0 0 24 24"><path d="${ICONS.star}"/></svg>`))}<small>${avg.toFixed(1)}${count != null ? ` (${count})` : ''}</small></span>`;
}

function cover(b, cls = '') {
  const c = /^#[0-9a-f]{6}$/i.test(b.color || '') ? b.color : '#8b7d6b';
  const first = (b.authors || '').split(',')[0];
  return h`<div class="cover ${cls}" style="--c1:${c};--c2:${shade(c, -46)}"><i class="cv-spine"></i><span class="cv-cat">${b.category || ''}</span><b class="cv-title">${b.title}</b><span class="cv-auth">${first}</span></div>`;
}

function bookCard(b) {
  const avail = b.available_copies > 0 ? pill(`${b.available_copies} in`, 'ok') : pill(b.total_copies ? 'All out' : 'Unavailable', 'bad');
  return h`<a class="bk" href="#/book/${b.id}">${cover(b)}<div class="meta"><b>${b.title}</b><small>${b.authors}</small><div class="row">${stars(b.avg_rating)}${avail}</div></div></a>`;
}

function dueChip(iso, returned) {
  if (returned) return pill('Returned ' + fmtDate(returned));
  const d = daysTo(iso);
  if (d < 0) return pill(`${-d}d overdue`, 'bad');
  if (d === 0) return pill('Due today', 'warn');
  if (d <= 3) return pill(`Due in ${d}d`, 'warn');
  return pill('Due ' + fmtDate(iso), 'ok');
}

const empty = (icon, title, sub, action) => h`<div class="empty">${ic(icon)}<h3>${title}</h3><p>${sub}</p>${action || ''}</div>`;

// ---------------------------------------------------------------- charts (inline SVG)
function monthSeries(rows, n = 6) {
  const map = Object.fromEntries((rows || []).map(r => [r.m, Number(r.n)]));
  const out = [], now = new Date();
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const key = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0');
    out.push({ label: d.toLocaleDateString(undefined, { month: 'short' }), value: map[key] || 0 });
  }
  return out;
}

function areaChart(data, fmt = v => v) {
  const W = 620, H = 230, L = 34, B = 28, T = 14, R = 10;
  const max = Math.max(1, ...data.map(d => d.value)), niceMax = Math.ceil(max * 1.15);
  const x = i => L + (W - L - R) * (data.length === 1 ? .5 : i / (data.length - 1));
  const y = v => T + (H - T - B) * (1 - v / niceMax);
  const line = data.map((d, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(d.value).toFixed(1)}`).join('');
  const area = `${line}L${x(data.length - 1)},${H - B}L${x(0)},${H - B}Z`;
  const grid = [0, .5, 1].map(t => { const v = Math.round(niceMax * t); return `<line class="grid-l" x1="${L}" x2="${W - R}" y1="${y(v)}" y2="${y(v)}"/><text x="${L - 8}" y="${y(v) + 4}" text-anchor="end">${v}</text>`; }).join('');
  return raw(`<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Loans per month"><defs><linearGradient id="ag" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--accent)" stop-opacity=".45"/><stop offset="1" stop-color="var(--accent)" stop-opacity="0"/></linearGradient></defs>${grid}
    <path d="${area}" fill="url(#ag)"/><path d="${line}" fill="none" stroke="var(--accent)" stroke-width="2.6" stroke-linejoin="round" stroke-linecap="round"/>
    ${data.map((d, i) => `<circle cx="${x(i)}" cy="${y(d.value)}" r="4.5" fill="var(--surface)" stroke="var(--accent)" stroke-width="2.4"><title>${esc(d.label)}: ${esc(fmt(d.value))}</title></circle><text x="${x(i)}" y="${H - 8}" text-anchor="middle">${esc(d.label)}</text>`).join('')}</svg>`);
}

function barChart(data, fmt = v => v) {
  const W = 360, H = 190, B = 26, T = 10, gap = 10;
  const max = Math.max(1, ...data.map(d => d.value));
  const bw = (W - gap * (data.length + 1)) / data.length;
  return raw(`<svg viewBox="0 0 ${W} ${H}" role="img">${data.map((d, i) => {
    const bh = Math.max(2, (H - B - T) * d.value / max), x = gap + i * (bw + gap);
    return `<rect class="bar" x="${x}" y="${H - B - bh}" width="${bw}" height="${bh}" rx="5" style="animation-delay:${i * 60}ms"><title>${esc(d.label)}: ${esc(fmt(d.value))}</title></rect><text x="${x + bw / 2}" y="${H - 8}" text-anchor="middle">${esc(d.label)}</text>`;
  }).join('')}</svg>`);
}

function donut(data) {
  const total = data.reduce((a, d) => a + d.value, 0) || 1, r = 52, C = 2 * Math.PI * r;
  let off = 0;
  const segs = data.map(d => { const len = C * d.value / total, s = `<circle r="${r}" cx="70" cy="70" fill="none" stroke="${esc(d.color)}" stroke-width="22" stroke-dasharray="${len.toFixed(2)} ${(C - len).toFixed(2)}" stroke-dashoffset="${(-off).toFixed(2)}"><title>${esc(d.label)}: ${d.value}</title></circle>`; off += len; return s; }).join('');
  return h`<div class="donut-wrap"><svg viewBox="0 0 140 140" style="transform:rotate(-90deg)">${raw(segs)}</svg><div class="legend">${data.slice(0, 7).map(d => h`<div><i class="dot" style="background:${d.color}"></i><span>${d.label}</span><span>${Math.round(100 * d.value / total)}%</span></div>`)}</div></div>`;
}

const hbars = (data) => { const max = Math.max(1, ...data.map(d => d.value)); return h`${data.map(d => h`<div class="hbar"><span title="${d.label}">${d.label}</span><div><i style="width:${(100 * d.value / max).toFixed(1)}%"></i></div><b>${d.value}</b></div>`)}`; };

// Code 39 barcode (the standard 1D library-card symbology)
const C39 = { '0': 'nnnwwnwnn', '1': 'wnnwnnnnw', '2': 'nnwwnnnnw', '3': 'wnwwnnnnn', '4': 'nnnwwnnnw', '5': 'wnnwwnnnn', '6': 'nnwwwnnnn', '7': 'nnnwnnwnw', '8': 'wnnwnnwnn', '9': 'nnwwnnwnn',
  A: 'wnnnnwnnw', B: 'nnwnnwnnw', C: 'wnwnnwnnn', D: 'nnnnwwnnw', E: 'wnnnwwnnn', F: 'nnwnwwnnn', G: 'nnnnnwwnw', H: 'wnnnnwwnn', I: 'nnwnnwwnn', J: 'nnnnwwwnn', K: 'wnnnnnnww', L: 'nnwnnnnww',
  M: 'wnwnnnnwn', N: 'nnnnwnnww', O: 'wnnnwnnwn', P: 'nnwnwnnwn', Q: 'nnnnnnwww', R: 'wnnnnnwwn', S: 'nnwnnnwwn', T: 'nnnnwnwwn', U: 'wwnnnnnnw', V: 'nwwnnnnnw', W: 'wwwnnnnnn', X: 'nwnnwnnnw',
  Y: 'wwnnwnnnn', Z: 'nwwnwnnnn', '*': 'nwnnwnwnn' };
function barcode(text) {
  let x = 0, out = '';
  for (const ch of '*' + String(text).toUpperCase() + '*') {
    const p = C39[ch]; if (!p) continue;
    for (let i = 0; i < 9; i++) { const w = p[i] === 'w' ? 3 : 1; if (i % 2 === 0) out += `<rect x="${x}" y="0" width="${w}" height="40"/>`; x += w; }
    x += 1;
  }
  return raw(`<svg viewBox="0 0 ${x} 40" preserveAspectRatio="none" fill="#1c1405" aria-label="Card barcode">${out}</svg>`);
}

// ---------------------------------------------------------------- state, api, feedback
const S = { token: ls.get('lms_token'), user: null, cats: [], deskLog: [], notifOpen: false };
const staff = () => S.user && S.user.role !== 'MEMBER';
let R = { name: 'home', id: null, q: new URLSearchParams() };

async function api(path, opts = {}) {
  const res = await fetch(path, {
    method: opts.method || 'GET',
    headers: { ...(opts.body ? { 'Content-Type': 'application/json' } : {}), ...(S.token ? { Authorization: 'Bearer ' + S.token } : {}) },
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  const json = (res.headers.get('Content-Type') || '').includes('json');
  const data = json ? await res.json() : await res.blob();
  if (res.status === 401 && S.token) { signOut(); throw new Error('Session expired — please sign in again'); }
  if (!res.ok) throw new Error((json && data.error) || 'Something went wrong');
  return data;
}
const post = (p, body) => api(p, { method: 'POST', body: body || {} });

function toast(msg, kind = 'ok') {
  const t = document.createElement('div');
  t.className = 'toast ' + kind;
  t.innerHTML = String(h`${ic(kind === 'ok' ? 'check' : 'alert')}<span>${msg}</span>`);
  $('#toasts').append(t);
  setTimeout(() => t.remove(), kind === 'ok' ? 3800 : 6000);
}
const guard = async fn => { try { return await fn(); } catch (e) { toast(e.message, 'bad'); } };

const modal = $('#modal');
function openModal(content, wide) { modal.className = wide ? 'wide' : ''; modal.innerHTML = String(h`<div class="modal-in">${content}</div>`); if (!modal.open) modal.showModal(); }
const closeModal = () => { if (modal.open) modal.close(); };
modal.addEventListener('click', e => { if (e.target === modal) closeModal(); });

function applyTheme(t) {
  const theme = t || ls.get('lms_theme') || 'dark';
  document.documentElement.dataset.theme = theme;
}
function signOut() {
  if (S.token) fetch('/api/auth/logout', { method: 'POST', headers: { Authorization: 'Bearer ' + S.token } }).catch(() => {});
  S.token = null; S.user = null; ls.set('lms_token', null); location.hash = '#/'; render();
}

// ---------------------------------------------------------------- form helpers
const fld = (label, name, o = {}) => h`<div><label for="f_${name}">${label}</label>${o.type === 'textarea'
  ? h`<textarea id="f_${name}" name="${name}" ${o.req ? 'required' : ''} placeholder="${o.ph || ''}">${o.value ?? ''}</textarea>`
  : h`<input id="f_${name}" name="${name}" type="${o.type || 'text'}" value="${o.value ?? ''}" ${o.req ? 'required' : ''} placeholder="${o.ph || ''}" ${o.min != null ? raw(`min="${o.min}"`) : ''} ${o.ro ? 'readonly' : ''} autocomplete="${o.ac || 'off'}">`}</div>`;
const sel = (label, name, opts, value) => h`<div><label for="f_${name}">${label}</label><select id="f_${name}" name="${name}">${opts.map(([v, t]) => h`<option value="${v}" ${String(v) === String(value) ? 'selected' : ''}>${t}</option>`)}</select></div>`;
const catOpts = () => S.cats.map(c => [c.id, c.name]);

// ---------------------------------------------------------------- pagination & params
function pager(total, page, size) {
  const pages = Math.ceil(total / size);
  if (pages <= 1) return '';
  const link = p => '#/' + R.name + qs({ ...Object.fromEntries(R.q), page: p });
  return h`<div class="pager">${page > 1 ? h`<a class="btn sm" href="${link(page - 1)}">‹ Prev</a>` : ''}<span>Page ${page} of ${pages} · ${total} results</span>${page < pages ? h`<a class="btn sm" href="${link(page + 1)}">Next ›</a>` : ''}</div>`;
}
function setParam(k, v) {
  const q = new URLSearchParams(R.q);
  v === '' || v == null ? q.delete(k) : q.set(k, v);
  if (k !== 'page') q.delete('page');
  location.hash = '#/' + R.name + (q.toString() ? '?' + q : '');
}

// ================================================================ VIEWS
const VIEWS = {};

// ---------------- auth
async function authView(mode = 'login') {
  let health = {};
  try { health = await fetch('/api/health').then(r => r.json()); } catch { /* server still starting */ }
  const spines = ['#c8694d', '#4f86c6', '#d9a84c', '#8a68c2', '#6fa35a', '#c25b84', '#3f9c96', '#b08d4a', '#4577c4', '#cc8f5e', '#6b7f8c', '#98763f'];
  const hs = [120, 170, 140, 190, 110, 165, 150, 180, 125, 175, 135, 160];
  $('#app').innerHTML = String(h`<div class="auth">
    <section class="auth-art">
      <div class="brand"><span class="logo">${ic('book')}</span><div><b>Athenaeum</b><small>Library system</small></div></div>
      <div><blockquote>A library is a quiet kind of power.</blockquote><cite>Borrow, reserve, renew — and let the desk keep the books honest.</cite></div>
      <div class="spines">${spines.map((c, i) => h`<i style="--c:${c};height:${hs[i]}px;animation-delay:${i * 70}ms"></i>`)}</div>
    </section>
    <section class="auth-form"><div class="box">
      <p class="eyebrow">Welcome</p><h1 style="margin-bottom:18px">${mode === 'login' ? 'Sign in' : 'Join the library'}</h1>
      <div class="tabs"><button data-act="authTab" data-mode="login" class="${mode === 'login' ? 'on' : ''}">Sign in</button><button data-act="authTab" data-mode="register" class="${mode === 'register' ? 'on' : ''}">Create account</button></div>
      ${mode === 'login' ? h`<form class="form" data-form="login">
          ${fld('Email', 'email', { type: 'email', req: true, ac: 'username', ph: 'you@example.com' })}
          ${fld('Password', 'password', { type: 'password', req: true, ac: 'current-password' })}
          <button class="btn primary" type="submit">Sign in</button></form>
        ${health.demo ? h`<div class="banner info" style="margin-top:18px;flex-wrap:wrap"><span class="muted">Demo accounts:</span>
          ${[['Admin', 'admin@library.test'], ['Librarian', 'librarian@library.test'], ['Member', 'aarav.menon@students.test']].map(([t, e]) => h`<button class="chip" data-act="demo" data-email="${e}" data-pw="${health.demo_password}">${t}</button>`)}</div>` : ''}`
        : h`<form class="form" data-form="register">
          ${fld('Full name', 'name', { req: true, ac: 'name' })}
          ${fld('Email', 'email', { type: 'email', req: true, ac: 'email' })}
          ${fld('Password (8+ characters)', 'password', { type: 'password', req: true, ac: 'new-password' })}
          <button class="btn primary" type="submit">Create my library card</button></form>`}
    </div></section></div>`);
}

async function finishLogin(res) {
  S.token = res.token; ls.set('lms_token', res.token); S.user = res.user;
  S.cats = await api('/api/categories');
  const same = location.hash === '#/home';
  location.hash = '#/home';
  shell();
  if (same) render(); // otherwise the hashchange event renders
}

// ---------------- home
VIEWS.home = () => staff() ? homeStaff() : homeMember();

async function homeMember() {
  const [sum, loans] = await Promise.all([api('/api/me/summary'), api('/api/loans?status=active&size=50')]);
  const s = sum.stats, first = S.user.name.split(' ')[0];
  const tiles = [
    ['On loan', s.active, `of ${S.user.max_loans} allowed`, '', 'book'],
    ['Overdue', s.overdue, s.overdue ? 'return these soon' : 'all on time', s.overdue ? 'bad' : 'ok', 'alert'],
    ['Holds', s.waiting + s.ready, s.ready ? `${s.ready} ready to collect` : 'in the queue', s.ready ? 'ok' : '', 'bookmark'],
    ['Fines due', money(s.unpaid), s.unpaid ? 'settle at the desk or online' : 'nothing owed', s.unpaid ? 'warn' : '', 'coins'],
    ['Books read', s.read_count, 'returned so far', '', 'trend'],
  ];
  return h`
  <section class="hero"><div>
      <p class="eyebrow">${greeting()}</p><h1>${first}, what will you read next?</h1>
      <p class="lead">Your card is valid until ${fmtDate(S.user.expires_on)}. Reserve anything that's out, renew from here, and we'll nudge you before the due date.</p>
      <div class="row"><a class="btn primary" href="#/catalog">${ic('search')} Browse the catalog</a><a class="btn" href="#/profile">${ic('user')} My library card</a></div></div>
    <div class="stack-covers">${(sum.trending.slice(0, 3)).map(b => cover(b))}</div></section>
  <div class="stats">${tiles.map(([l, v, sub, k, i]) => h`<div class="stat ${k}"><div class="lbl">${ic(i)} ${l}</div><div class="val">${v}</div><div class="sub">${sub}</div></div>`)}</div>
  <section class="sec"><div class="page-head" style="margin-bottom:12px"><h2 style="margin:0">Currently borrowed</h2><a class="btn sm" href="#/loans">All loans</a></div>
    ${loans.items.length ? loanTable(loans.items, false) : h`<div class="card">${empty('book', 'Nothing on loan', 'Pick something from the catalog and collect it at the desk.')}</div>`}</section>
  <section class="sec"><h2>Recommended for you</h2><div class="shelf row-scroll">${sum.recommended.map(bookCard)}</div></section>
  <section class="sec"><h2>Trending this month</h2><div class="shelf row-scroll">${sum.trending.map(bookCard)}</div></section>`;
}

async function homeStaff() {
  const d = await api('/api/dashboard'), k = d.kpi;
  const tile = (l, v, sub, kind, i) => h`<div class="stat ${kind}"><div class="lbl">${ic(i)} ${l}</div><div class="val" data-count="${v}">${v}</div><div class="sub">${sub}</div></div>`;
  const wk = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((l, i) => ({ label: l, value: Number((d.by_weekday.find(r => r.d === i + 1) || {}).n || 0) }));
  return h`
  <div class="page-head"><div><p class="eyebrow">${greeting()}, ${S.user.name.split(' ')[0]}</p><h1>Library dashboard</h1></div>
    <div class="row"><a class="btn primary" href="#/desk">${ic('scan')} Open circulation desk</a><a class="btn" href="#/catalog">${ic('plus')} Catalog</a></div></div>
  <div class="stats k4" style="margin-top:0">
    ${tile('Titles', k.books, `${k.copies} circulating copies`, '', 'layers')}
    ${tile('On the shelf', k.available, `${Math.round(100 * k.available / Math.max(1, k.copies))}% of copies in`, 'ok', 'book')}
    ${tile('Active loans', k.active_loans, `${k.issued_today} out · ${k.returned_today} back today`, '', 'clock')}
    ${tile('Overdue', k.overdue, k.overdue ? 'follow up with members' : 'none — well done', k.overdue ? 'bad' : 'ok', 'alert')}
    ${tile('Members', k.members, 'active accounts', '', 'users')}
    ${tile('Holds queue', k.holds, `${k.pending_requests} purchase requests pending`, k.holds ? 'warn' : '', 'bookmark')}
    <div class="stat warn"><div class="lbl">${ic('coins')} Unpaid fines</div><div class="val">${money(k.unpaid_fines)}</div><div class="sub">across all members</div></div>
    <div class="stat ok"><div class="lbl">${ic('trend')} Collected, 30 days</div><div class="val">${money(k.collected_30d)}</div><div class="sub">cash + UPI</div></div></div>
  <div class="charts">
    <div class="card chart c-8"><div class="row between"><h3>Loans per month</h3><span class="faint">last 6 months</span></div>${areaChart(monthSeries(d.monthly))}</div>
    <div class="card chart c-4"><h3 style="margin-bottom:14px">What gets borrowed</h3>${donut(d.by_category.map(c => ({ label: c.name, value: Number(c.n), color: c.color })))}</div>
    <div class="card chart c-4"><h3>Busiest days</h3>${barChart(wk)}</div>
    <div class="card c-4"><h3 style="margin-bottom:8px">Most borrowed</h3>${hbars(d.top_books.map(b => ({ label: b.title, value: Number(b.n) })))}</div>
    <div class="card chart c-4"><h3>Fines collected</h3>${barChart(monthSeries(d.fines_monthly), v => money(v))}</div>
    <div class="card tight c-8"><div class="row between" style="padding:18px 20px 8px"><h3>Overdue right now</h3><a class="btn sm" href="#/loans?status=overdue">View all</a></div>
      ${d.overdue.length ? h`<div class="scroll-x"><table><thead><tr><th>Book</th><th>Member</th><th>Late</th><th class="num">Accrued</th></tr></thead><tbody>
        ${d.overdue.map(o => h`<tr><td><a href="#/book/${o.book_id}"><b>${o.title}</b></a><br><small class="faint scan">${o.barcode}</small></td><td>${o.member}<br><small class="faint">${o.card_no}</small></td><td>${pill(o.days_overdue + ' days', 'bad')}</td><td class="num">${money(o.accrued)}</td></tr>`)}</tbody></table></div>`
        : empty('check', 'Nothing overdue', 'Every borrowed book is within its due date.')}</div>
    <div class="card c-4"><div class="row between"><h3>Recent activity</h3><a class="btn sm" href="#/audit">Audit</a></div><div class="feed">
      ${d.activity.map(a => h`<div><time>${ago(a.ts_ms)}</time><span><b>${a.action.replace('_', ' ').toLowerCase()}</b> · <span class="muted">${a.detail}</span></span></div>`)}</div></div>
  </div>`;
}

// ---------------- catalog
VIEWS.catalog = async () => {
  const q = R.q, page = Number(q.get('page') || 1), size = 12;
  const params = { q: q.get('q') || '', category: q.get('category') || '', available: q.get('available') || '', sort: q.get('sort') || 'title', page, size };
  const res = await api('/api/books' + qs(params));
  const chip = (label, on, onclick, color) => h`<a class="chip ${on ? 'on' : ''}" href="${onclick}">${color ? h`<i class="dot" style="background:${color}"></i>` : ''}${label}</a>`;
  const base = extra => '#/catalog' + qs({ ...Object.fromEntries(q), page: '', ...extra });
  return h`
  <div class="page-head"><div><p class="eyebrow">Discover</p><h1>The catalog</h1></div>
    ${staff() ? h`<button class="btn primary" data-act="bookForm">${ic('plus')} Add a book</button>` : ''}</div>
  <div class="filters">
    <form data-form="catSearch" style="flex:1;min-width:240px;max-width:460px"><input name="q" value="${params.q}" placeholder="Title, author, ISBN or publisher" aria-label="Search the catalog"></form>
    <select data-param="sort" style="width:auto" aria-label="Sort">${[['title', 'Title A–Z'], ['rating', 'Top rated'], ['popular', 'Most borrowed'], ['newest', 'Newly added'], ['year', 'Publication year']].map(([v, t]) => h`<option value="${v}" ${params.sort === v ? 'selected' : ''}>${t}</option>`)}</select>
    ${chip('Available now', params.available === '1', base({ available: params.available === '1' ? '' : '1' }))}
  </div>
  <div class="filters">${chip('All', !params.category, base({ category: '' }))}${S.cats.map(c => chip(`${c.name} · ${c.books}`, String(c.id) === params.category, base({ category: c.id }), c.color))}</div>
  ${res.items.length ? h`<div class="shelf">${res.items.map(bookCard)}</div>${pager(res.total, res.page, res.size)}`
    : h`<div class="card">${empty('search', 'No books match', 'Try a different spelling, or clear a filter.', h`<a class="btn" href="#/catalog">Clear filters</a>`)}</div>`}`;
};

// ---------------- book detail
VIEWS.book = async id => {
  const b = await api('/api/books/' + id);
  const loggedIn = !!S.user;
  const avail = b.available_copies > 0;
  let status = '';
  if (b.my_loan) status = h`<div class="banner info">${ic('clock')}<div><b>You have this on loan.</b> ${dueChip(b.my_loan.due_at)}</div><button class="btn sm" style="margin-left:auto" data-act="renew" data-id="${b.my_loan.id}">Renew</button></div>`;
  else if (b.my_hold && b.my_hold.status === 'READY') status = h`<div class="banner ok">${ic('check')}<div><b>Your hold is ready.</b> Collect it at the desk before ${fmtDT(b.my_hold.expires_at)}.</div></div>`;
  else if (b.my_hold) status = h`<div class="banner info">${ic('bookmark')}<div><b>You're #${b.my_hold.position} in the queue.</b> We'll notify you when it's ready.</div><button class="btn sm danger" style="margin-left:auto" data-act="cancelHold" data-id="${b.my_hold.id}">Leave queue</button></div>`;
  const dist = [5, 4, 3, 2, 1].map(n => ({ n, c: b.reviews.filter(r => r.rating === n).length }));
  return h`
  <div class="detail">
    <div class="side-col">${cover(b, 'lg')}
      ${loggedIn && !b.my_loan && !b.my_hold && !avail && b.total_copies ? h`<button class="btn primary" data-act="reserve" data-id="${b.id}">${ic('bookmark')} Join the waiting list</button>` : ''}
      ${loggedIn ? h`<button class="btn" data-act="fav" data-id="${b.id}">${ic('heart', b.favorite ? 'fill' : '')} ${b.favorite ? 'On your reading list' : 'Add to reading list'}</button>` : h`<a class="btn primary" href="#/">Sign in to borrow</a>`}
      ${staff() ? h`<button class="btn" data-act="bookForm" data-id="${b.id}">${ic('edit')} Edit details</button>` : ''}</div>
    <div>
      <a class="muted" href="#/catalog?category=${b.category_id}">‹ ${b.category}</a>
      <h1 style="margin:8px 0 6px">${b.title}</h1><p class="muted" style="font-size:17px">by ${b.authors}</p>
      <div class="row" style="margin:6px 0 4px">${stars(b.avg_rating, b.review_count)}<span class="faint">· borrowed ${b.times_borrowed} times</span></div>
      ${status}
      <div class="banner ${avail ? 'ok' : 'warn'}">${ic(avail ? 'check' : 'clock')}<div>${avail ? h`<b>${b.available_copies} of ${b.total_copies} copies on the shelf.</b> ${b.shelves.length ? 'Find it at ' + b.shelves.map(s => s.shelf).join(', ') + ', then borrow at the desk.' : ''}` : h`<b>All ${b.total_copies} copies are out.</b> ${b.waiting ? `${b.waiting} waiting.` : 'Be the first in the queue.'}`}</div></div>
      <p style="font-size:16px;line-height:1.7;color:var(--muted)">${b.description || 'No description yet.'}</p>
      <div class="meta-grid">
        <div><small>ISBN</small><b class="scan">${b.isbn}</b></div><div><small>Publisher</small><b>${b.publisher || '—'}</b></div>
        <div><small>Published</small><b>${b.published_year || '—'}</b></div><div><small>Pages</small><b>${b.pages || '—'}</b></div><div><small>Language</small><b>${b.language}</b></div></div>
      ${staff() ? h`<section class="sec"><div class="row between"><h2 style="margin:0">Copies</h2><button class="btn sm" data-act="copiesForm" data-id="${b.id}">${ic('plus')} Add copies</button></div>
        <div class="card tight scroll-x" style="margin-top:12px"><table><thead><tr><th>Barcode</th><th>Shelf</th><th>Status</th><th>Borrower</th><th>Due</th><th></th></tr></thead><tbody>
        ${b.copies.map(c => h`<tr><td class="scan">${c.barcode}</td><td>${c.shelf || '—'}</td><td>${statusPill(c.status)}</td><td>${c.borrower || '—'}</td><td>${c.due_at ? dueChip(c.due_at) : '—'}</td>
          <td class="num">${c.status === 'ISSUED' ? h`<a class="btn sm" href="#/desk?return=${c.barcode}">Check in</a>` : c.status === 'ON_HOLD' ? '' : h`<select class="sm" data-copy="${c.id}" style="width:auto;padding:4px 8px" aria-label="Change status">${['AVAILABLE', 'DAMAGED', 'WITHDRAWN', 'LOST'].map(s => h`<option ${s === c.status ? 'selected' : ''}>${s}</option>`)}</select>`}</td></tr>`)}
        </tbody></table></div></section>` : ''}
      <section class="sec"><h2>Reviews</h2>
        <div class="grid2"><div class="card"><div class="row" style="gap:18px"><div class="stat" style="border:0;padding:0;background:none"><div class="val" style="font-size:48px;margin:0">${b.avg_rating == null ? '–' : b.avg_rating.toFixed(1)}</div></div>
            <div style="flex:1"><div class="dist">${dist.map(d => h`<div>${d.n}<span class="track"><i style="width:${b.reviews.length ? 100 * d.c / b.reviews.length : 0}%"></i></span>${d.c}</div>`)}</div></div></div></div>
          <div class="card">${b.can_review ? h`<form class="form" data-form="review" data-id="${b.id}"><div><label>Your rating</label><div class="rate-in">${[5, 4, 3, 2, 1].map(n => h`<input type="radio" id="rt${n}" name="rating" value="${n}" required><label for="rt${n}" title="${n} stars">★</label>`)}</div></div>
            <div><label for="f_body">Your thoughts</label><textarea id="f_body" name="body" maxlength="1000" placeholder="What did you think?"></textarea></div><button class="btn primary" type="submit">Post review</button></form>`
            : h`<p class="muted" style="margin:0">${loggedIn ? 'Reviews open once you have borrowed this book — so every review comes from a real reader.' : 'Sign in to read and write reviews.'}</p>`}</div></div>
        <div style="margin-top:10px">${b.reviews.length ? b.reviews.map(r => h`<div class="review"><div class="row"><span class="avatar sm">${initials(r.name)}</span><b>${r.name}</b>${stars(r.rating)}<span class="faint">${fmtDate(r.created_at)}</span>
          ${S.user && (staff() || r.user_id === S.user.id) ? h`<button class="btn sm ghost danger" style="margin-left:auto" data-act="delReview" data-id="${r.id}">Delete</button>` : ''}</div>${r.body ? h`<p class="muted" style="margin:8px 0 0 38px">${r.body}</p>` : ''}</div>`)
          : h`<p class="muted">No reviews yet.</p>`}</div></section>
      ${b.related.length ? h`<section class="sec"><h2>More in ${b.category}</h2><div class="shelf row-scroll">${b.related.map(bookCard)}</div></section>` : ''}
    </div></div>`;
};

// ---------------- loans
function loanTable(items, showMember) {
  return h`<div class="card tight scroll-x"><table><thead><tr><th>Book</th>${showMember ? h`<th>Member</th>` : ''}<th>Issued</th><th>Due</th><th>Renewals</th><th></th></tr></thead><tbody>
    ${items.map(l => h`<tr><td><div class="t-title">${cover({ color: l.color, title: '' }, 'xs')}<div><a href="#/book/${l.book_id}"><b>${l.title}</b></a><small class="scan">${l.barcode}</small></div></div></td>
      ${showMember ? h`<td>${l.member}<br><small class="faint scan">${l.card_no}</small></td>` : ''}<td>${fmtDate(l.issued_at)}</td><td>${dueChip(l.due_at, l.returned_at)}</td><td>${l.renewals}</td>
      <td class="num">${!l.returned_at ? h`${l.renewable ? h`<button class="btn sm" data-act="renew" data-id="${l.id}">${ic('refresh')} Renew</button>` : ''}${staff() ? h` <a class="btn sm" href="#/desk?return=${l.barcode}">Check in</a>` : ''}` : ''}</td></tr>`)}
    </tbody></table></div>`;
}

VIEWS.loans = async () => {
  const q = R.q, status = q.get('status') || 'active', page = Number(q.get('page') || 1);
  const res = await api('/api/loans' + qs({ status, page, size: 15, q: q.get('q') || '', user_id: q.get('user_id') || '' }));
  const tabs = [['active', 'On loan'], ['overdue', 'Overdue'], ['returned', 'History'], ['all', 'Everything']];
  return h`
  <div class="page-head"><div><p class="eyebrow">${staff() ? 'Circulation' : 'My library'}</p><h1>${staff() ? 'Loans' : 'My loans'}</h1></div>
    ${staff() ? h`<a class="btn primary" href="#/desk">${ic('scan')} Circulation desk</a>` : ''}</div>
  <div class="filters">${tabs.map(([v, t]) => h`<a class="chip ${status === v ? 'on' : ''}" href="#/loans${qs({ status: v, user_id: q.get('user_id') || '' })}">${t}</a>`)}
    ${staff() ? h`<form data-form="loanSearch" style="margin-left:auto;min-width:240px"><input name="q" value="${q.get('q') || ''}" placeholder="Search title, member, card, barcode"></form>` : ''}</div>
  ${res.items.length ? h`${loanTable(res.items, staff())}${pager(res.total, res.page, res.size)}` : h`<div class="card">${empty('clock', 'No loans here', status === 'overdue' ? 'Nothing is overdue.' : 'When books are borrowed they will show up in this list.')}</div>`}`;
};

// ---------------- holds
VIEWS.holds = async () => {
  const items = await api('/api/reservations');
  return h`
  <div class="page-head"><div><p class="eyebrow">${staff() ? 'Circulation' : 'My library'}</p><h1>${staff() ? 'Holds queue' : 'My holds'}</h1></div></div>
  ${items.length ? h`<div class="card tight scroll-x"><table><thead><tr><th>Book</th>${staff() ? h`<th>Member</th>` : ''}<th>Status</th><th>Position</th><th>Details</th><th></th></tr></thead><tbody>
    ${items.map(r => h`<tr><td><div class="t-title">${cover({ color: r.color, title: '' }, 'xs')}<div><a href="#/book/${r.book_id}"><b>${r.title}</b></a><small>placed ${fmtDate(r.created_at)}</small></div></div></td>
      ${staff() ? h`<td>${r.member}<br><small class="faint scan">${r.card_no}</small></td>` : ''}<td>${statusPill(r.status)}</td>
      <td>${r.status === 'WAITING' ? '#' + r.position : '—'}</td>
      <td>${r.status === 'READY' ? h`Collect from desk${r.shelf ? ' (shelf ' + r.shelf + ')' : ''} by ${fmtDT(r.expires_at)}` : raw('<span class="muted">Waiting for a returned copy</span>')}</td>
      <td class="num">${r.status === 'READY' && staff() ? h`<a class="btn sm primary" href="#/desk?card=${r.card_no}&barcode=${r.barcode}">Issue</a> ` : ''}<button class="btn sm danger" data-act="cancelHold" data-id="${r.id}">Cancel</button></td></tr>`)}
    </tbody></table></div>` : h`<div class="card">${empty('bookmark', 'No holds', 'When a title is out, join its waiting list from the book page. First come, first served.', h`<a class="btn" href="#/catalog">Browse the catalog</a>`)}</div>`}`;
};

// ---------------- fines
VIEWS.fines = async () => {
  const status = R.q.get('status') || 'UNPAID';
  const items = await api('/api/fines' + qs({ status }));
  const total = items.filter(f => f.status === 'UNPAID').reduce((a, f) => a + f.amount, 0);
  return h`
  <div class="page-head"><div><p class="eyebrow">${staff() ? 'Circulation' : 'My library'}</p><h1>${staff() ? 'Fines' : 'My fines'}</h1></div>
    ${!staff() && total > 0 ? h`<button class="btn primary" data-act="payAll">${ic('coins')} Pay ${money(total)}</button>` : ''}</div>
  <div class="filters">${[['UNPAID', 'Unpaid'], ['PAID', 'Paid'], ['WAIVED', 'Waived'], ['all', 'All']].map(([v, t]) => h`<a class="chip ${status === v ? 'on' : ''}" href="#/fines?status=${v}">${t}</a>`)}
    ${status === 'UNPAID' ? h`<span class="pill warn" style="margin-left:auto">Outstanding ${money(total)}</span>` : ''}</div>
  ${!staff() && total >= 100 ? h`<div class="banner bad">${ic('alert')}<div><b>Borrowing is paused.</b> Unpaid fines of ${money(total)} are above the ${money(100)} limit.</div></div>` : ''}
  ${items.length ? h`<div class="card tight scroll-x"><table><thead><tr><th>Book</th>${staff() ? h`<th>Member</th>` : ''}<th>Reason</th><th>Date</th><th>Status</th><th class="num">Amount</th><th></th></tr></thead><tbody>
    ${items.map(f => h`<tr><td><b>${f.title}</b></td>${staff() ? h`<td>${f.member}</td>` : ''}<td>${pill(f.reason.toLowerCase(), f.reason === 'OVERDUE' ? 'warn' : 'bad')}</td><td>${fmtDate(f.created_at)}</td><td>${statusPill(f.status)}${f.method && f.status !== 'UNPAID' ? h` <small class="faint">${f.method}</small>` : ''}</td><td class="num"><b>${money(f.amount)}</b></td>
      <td class="num">${f.status === 'UNPAID' ? h`<button class="btn sm" data-act="payFine" data-id="${f.id}">Pay</button>${staff() ? h` <button class="btn sm ghost" data-act="waive" data-id="${f.id}">Waive</button>` : ''}` : ''}</td></tr>`)}
    </tbody></table></div><p class="faint" style="margin-top:12px">Payments here are simulated for the demo — no real money moves.</p>`
    : h`<div class="card">${empty('coins', 'No fines', status === 'UNPAID' ? 'Nothing is owed. Return books on time and it stays that way.' : 'Nothing to show for this filter.')}</div>`}`;
};

// ---------------- reading list
VIEWS.favorites = async () => {
  const items = await api('/api/me/favorites');
  return h`<div class="page-head"><div><p class="eyebrow">My library</p><h1>Reading list</h1></div></div>
  ${items.length ? h`<div class="shelf">${items.map(bookCard)}</div>` : h`<div class="card">${empty('heart', 'Your reading list is empty', 'Tap the heart on any book to save it for later.', h`<a class="btn" href="#/catalog">Find something</a>`)}</div>`}`;
};

// ---------------- requests
VIEWS.requests = async () => {
  const items = await api('/api/requests');
  return h`<div class="page-head"><div><p class="eyebrow">${staff() ? 'Acquisitions' : 'My library'}</p><h1>${staff() ? 'Purchase requests' : 'Request a book'}</h1></div></div>
  <div class="${staff() ? '' : 'grid2'}" style="align-items:start">
    ${!staff() ? h`<form class="card form" data-form="request"><h3>Suggest a title</h3><p class="muted">Not in the catalog? Tell the librarians what to buy next.</p>
      ${fld('Title', 'title', { req: true })}${fld('Author', 'author')}${fld('Why this book?', 'note', { type: 'textarea' })}<button class="btn primary" type="submit">Send request</button></form>` : ''}
    <div>${items.length ? h`<div class="card tight scroll-x"><table><thead><tr><th>Title</th>${staff() ? h`<th>Requested by</th>` : ''}<th>Status</th><th></th></tr></thead><tbody>
      ${items.map(r => h`<tr><td><b>${r.title}</b><br><small class="faint">${r.author || ''}${r.note ? ' · ' + r.note : ''}</small></td>${staff() ? h`<td>${r.member}</td>` : ''}<td>${statusPill(r.status)}<br><small class="faint">${fmtDate(r.created_at)}</small></td>
        <td class="num">${staff() ? [['APPROVED', 'Approve'], ['PURCHASED', 'Bought'], ['REJECTED', 'Decline']].filter(([s]) => s !== r.status).map(([s, t]) => h`<button class="btn sm ${s === 'REJECTED' ? 'danger' : ''}" data-act="reqStatus" data-id="${r.id}" data-status="${s}">${t}</button> `) : ''}</td></tr>`)}
      </tbody></table></div>` : h`<div class="card">${empty('gift', 'No requests yet', 'Suggestions appear here with their status.')}</div>`}</div></div>`;
};

// ---------------- profile
VIEWS.profile = async () => {
  const u = S.user = await api('/api/me');
  return h`<div class="page-head"><div><p class="eyebrow">Account</p><h1>My library card</h1></div></div>
  <div class="grid2" style="align-items:start">
    <div class="stack"><div class="libcard"><div class="top2"><b style="font:600 15px var(--serif);letter-spacing:.06em">ATHENAEUM LIBRARY</b><span class="pill" style="background:rgba(28,20,5,.12);color:#1c1405;border-color:rgba(28,20,5,.25)">${u.plan}</span></div>
        <h3>${u.name}</h3><div class="no">${u.card_no}</div><div class="bars">${barcode(u.card_no)}</div>
        <div class="foot2"><span>Member since ${fmtDate(u.joined_on)}</span><span>Valid until ${fmtDate(u.expires_on)}</span></div></div>
      <div class="card"><h3 style="margin-bottom:10px">Your plan: ${u.plan}</h3><div class="meta-grid" style="margin:0">
        <div><small>Max loans</small><b>${u.max_loans} books</b></div><div><small>Loan period</small><b>${u.loan_days} days</b></div><div><small>Renewals</small><b>${u.max_renewals} each</b></div><div><small>Late fee</small><b>${money(u.fine_per_day)}/day</b></div></div></div></div>
    <div class="stack"><form class="card form" data-form="profile"><h3>Details</h3>${fld('Name', 'name', { value: u.name, req: true })}${fld('Email (ask the desk to change it)', 'email', { value: u.email, ro: true })}${fld('Phone', 'phone', { value: u.phone })}<button class="btn primary" type="submit">Save</button></form>
      <form class="card form" data-form="password"><h3>Change password</h3>${fld('Current password', 'old_password', { type: 'password', req: true, ac: 'current-password' })}${fld('New password (8+ characters)', 'new_password', { type: 'password', req: true, ac: 'new-password' })}<button class="btn" type="submit">Update password</button></form></div></div>`;
};

// ---------------- circulation desk (staff)
VIEWS.desk = async () => {
  const holds = await api('/api/reservations');
  const ready = holds.filter(r => r.status === 'READY');
  const pre = { barcode: R.q.get('barcode') || '', ret: R.q.get('return') || '', card: R.q.get('card') || '' };
  return {
    html: h`<div class="page-head"><div><p class="eyebrow">Circulation</p><h1>Desk</h1></div><p class="muted" style="margin:0">Scan or type barcodes — press Enter to confirm.</p></div>
    <div class="desk">
      <div class="card"><h2>${ic('book')} Check out</h2>
        <form class="form" data-form="issue" autocomplete="off">
          <div><label for="dMember">Member (name, card number or email)</label><input id="dMember" data-input="memberSearch" placeholder="Start typing…" value="${pre.card}"><div id="dResults"></div><div id="dChosen"></div></div>
          <input type="hidden" name="card_no" id="dCard" value="${pre.card}">
          <div><label for="dBarcode">Book barcode</label><input id="dBarcode" class="scan" name="barcode" data-input="copyPeek" placeholder="BK00012-01" value="${pre.barcode}" required><div id="dCopy" class="muted" style="margin-top:6px;min-height:22px"></div></div>
          <button class="btn primary" type="submit">${ic('check')} Issue book</button></form><div id="outResult"></div></div>
      <div class="card"><h2>${ic('refresh')} Check in</h2>
        <form class="form" data-form="return" autocomplete="off">
          <div><label for="rBarcode">Book barcode</label><input id="rBarcode" class="scan" name="barcode" data-input="copyPeek" placeholder="BK00012-01" value="${pre.ret}" required><div id="rCopy" class="muted" style="margin-top:6px;min-height:22px"></div></div>
          ${sel('Condition', 'condition', [['OK', 'Good condition'], ['DAMAGED', 'Damaged (fine applies)'], ['LOST', 'Lost / not returned (fine applies)']], 'OK')}
          <button class="btn primary" type="submit">${ic('check')} Check in</button></form><div id="inResult"></div></div></div>
    <div class="grid2 sec">
      <div class="card"><div class="row between"><h3>Ready for pickup</h3>${pill(ready.length, ready.length ? 'ok' : '')}</div>
        ${ready.length ? h`<div class="feed" style="margin-top:8px">${ready.map(r => h`<div style="align-items:center"><span style="flex:1"><b>${r.title}</b><br><small class="muted">${r.member} · shelf ${r.shelf || '?'} · until ${fmtDT(r.expires_at)}</small></span><button class="btn sm primary" data-act="fillIssue" data-card="${r.card_no}" data-barcode="${r.barcode}">Issue</button></div>`)}</div>` : raw('<p class="muted" style="margin:10px 0 0">No holds are waiting to be collected.</p>')}</div>
      <div class="card"><h3>This session</h3><div id="deskLog" class="feed" style="margin-top:8px">${deskLogHtml()}</div></div></div>`,
    after() {
      if (pre.ret) peekCopy($('#rBarcode'));
      if (pre.card) pickByCard(pre.card);
      if (pre.barcode) peekCopy($('#dBarcode'));
      (pre.ret ? $('#rBarcode') : pre.card ? $('#dBarcode') : $('#dMember')).focus();
    },
  };
};
function deskLogHtml() {
  return S.deskLog.length ? h`${S.deskLog.map(l => h`<div><time>${l.t}</time><span>${l.msg}</span></div>`)}` : raw('<p class="muted" style="margin:0">Actions you take appear here.</p>');
}
let peekTimer, memberTimer;
async function peekCopy(input) {
  const box = input.id === 'rBarcode' ? $('#rCopy') : $('#dCopy'), code = input.value.trim();
  if (!code) { box.textContent = ''; return; }
  try {
    const c = await api('/api/copies/lookup/' + encodeURIComponent(code));
    box.innerHTML = String(h`<b>${c.title}</b> · ${c.authors} · ${statusPill(c.status)}${c.borrower ? h` with ${c.borrower}` : ''}${c.held_for ? h` held for ${c.held_for}` : ''}`);
  } catch (e) { box.innerHTML = String(h`<span style="color:var(--bad)">${e.message}</span>`); }
}
function chooseMember(m) {
  $('#dCard').value = m.card_no; $('#dMember').value = m.name; $('#dResults').innerHTML = '';
  $('#dChosen').innerHTML = String(h`<div class="chosen"><span class="avatar">${initials(m.name)}</span><div style="flex:1"><b>${m.name}</b> ${statusPill(m.status)}<br><small class="muted">${m.card_no} · ${m.plan} · ${m.active_loans} on loan${m.unpaid > 0 ? ' · owes ' + money(m.unpaid) : ''}</small></div><button type="button" class="btn sm ghost" data-act="clearMember">Change</button></div>`);
  $('#dBarcode').focus();
}
async function pickByCard(card) { try { const r = await api('/api/members' + qs({ q: card })); const m = r.find(x => x.card_no === card) || r[0]; if (m) chooseMember(m); } catch { /* ignore */ } }

// ---------------- members (staff)
VIEWS.members = async () => {
  const q = R.q.get('q') || '';
  const list = await api('/api/members' + qs({ q }));
  return {
    html: h`<div class="page-head"><div><p class="eyebrow">Administration</p><h1>Members</h1></div><button class="btn primary" data-act="memberForm">${ic('plus')} Add member</button></div>
    <div class="filters"><form data-form="memberSearch" style="flex:1;max-width:460px"><input name="q" value="${q}" placeholder="Search name, email or card number"></form></div>
    <div class="card tight scroll-x"><table><thead><tr><th>Member</th><th>Plan</th><th>Status</th><th>Expires</th><th class="num">On loan</th><th class="num">Owes</th></tr></thead><tbody>
      ${list.map(m => h`<tr class="click" data-act="openMember" data-id="${m.id}"><td><div class="t-title"><span class="avatar sm">${initials(m.name)}</span><div><b>${m.name}</b> ${m.role !== 'MEMBER' ? pill(m.role.toLowerCase(), 'gold') : ''}<small>${m.email} · <span class="scan">${m.card_no}</span></small></div></div></td>
        <td>${m.plan}</td><td>${statusPill(m.status)}</td><td>${daysTo(m.expires_on) < 0 ? pill('Expired ' + fmtDate(m.expires_on), 'bad') : fmtDate(m.expires_on)}</td><td class="num">${m.active_loans}</td><td class="num">${m.unpaid > 0 ? h`<b style="color:var(--warn)">${money(m.unpaid)}</b>` : '—'}</td></tr>`)}
    </tbody></table>${list.length ? '' : empty('users', 'No members found', 'Try another search.')}</div>`,
  };
};

async function memberModal(id) {
  const [m, plans] = await Promise.all([api('/api/members/' + id), api('/api/plans')]);
  const admin = S.user.role === 'ADMIN';
  openModal(h`<div class="row between"><div class="row"><span class="avatar">${initials(m.name)}</span><div><h2 style="margin:0">${m.name}</h2><span class="muted">${m.email} · <span class="scan">${m.card_no}</span></span></div></div><button class="btn icon ghost" data-act="close" aria-label="Close">${ic('x')}</button></div>
    <div class="row" style="margin-bottom:14px">${statusPill(m.status)}${pill(m.plan, 'gold')}${pill(m.role.toLowerCase())}${daysTo(m.expires_on) < 0 ? pill('Membership expired', 'bad') : pill('Valid to ' + fmtDate(m.expires_on))}${m.unpaid_fines > 0 ? pill('Owes ' + money(m.unpaid_fines), 'warn') : ''}</div>
    <form class="form" data-form="memberEdit" data-id="${m.id}"><div class="grid2">${fld('Name', 'name', { value: m.name })}${sel('Plan', 'plan_id', plans.map(p => [p.id, `${p.name} — ${p.max_loans} books, ${p.loan_days} days`]), m.plan_id)}</div>
      <div class="grid3">${sel('Status', 'status', [['ACTIVE', 'Active'], ['SUSPENDED', 'Suspended']], m.status)}${admin ? sel('Role', 'role', [['MEMBER', 'Member'], ['LIBRARIAN', 'Librarian'], ['ADMIN', 'Admin']], m.role) : ''}${sel('Extend membership', 'extend_months', [['', 'No change'], ['6', '+ 6 months'], ['12', '+ 1 year'], ['24', '+ 2 years']], '')}</div>
      <div class="row"><button class="btn primary" type="submit">Save changes</button>${admin ? h`<button class="btn danger" type="button" data-act="resetPw" data-id="${m.id}">Reset password</button>` : ''}<a class="btn ghost" href="#/loans?user_id=${m.id}&status=all" data-act="close">All loans</a></div></form>
    <h3 style="margin:22px 0 8px">Recent loans</h3>${m.loans.length ? h`<div class="card tight scroll-x"><table><tbody>${m.loans.slice(0, 6).map(l => h`<tr><td><b>${l.title}</b></td><td>${dueChip(l.due_at, l.returned_at)}</td></tr>`)}</tbody></table></div>` : raw('<p class="muted">No loans yet.</p>')}
    ${m.fines.length ? h`<h3 style="margin:18px 0 8px">Fines</h3><div class="card tight scroll-x"><table><tbody>${m.fines.slice(0, 5).map(f => h`<tr><td>${f.title}</td><td>${statusPill(f.status)}</td><td class="num">${money(f.amount)}</td></tr>`)}</tbody></table></div>` : ''}`, true);
}

// ---------------- audit (staff)
VIEWS.audit = async () => {
  const rows = await api('/api/audit?limit=120');
  return h`<div class="page-head"><div><p class="eyebrow">Administration</p><h1>Audit trail &amp; exports</h1></div></div>
  <div class="grid2" style="align-items:start">
    <div class="card"><h3>Integrity check</h3><p class="muted">Every action is chained: each entry stores a SHA-256 hash of the previous one. If anyone edits or deletes a past row, the chain breaks at that exact point.</p>
      <div id="verifyOut" class="banner" style="margin-top:12px">${ic('shield')}<span class="muted">Not checked yet.</span></div><button class="btn primary" data-act="verify">${ic('shield')} Verify the chain</button></div>
    <div class="card"><h3>Export data</h3><p class="muted">CSV files open in Excel or Sheets. Cells starting with = + - @ are neutralised so a spreadsheet can't run them as formulas.</p>
      <div class="row">${['loans', 'fines', 'books', 'members'].map(w => h`<button class="btn" data-act="export" data-what="${w}">${ic('download')} ${w}</button>`)}</div></div></div>
  <div class="card tight scroll-x sec"><table><thead><tr><th>#</th><th>When</th><th>Who</th><th>Action</th><th>Detail</th><th>Hash</th></tr></thead><tbody>
    ${rows.map(a => h`<tr><td class="faint">${a.id}</td><td>${fmtDT(new Date(a.ts_ms).toISOString())}</td><td>${a.actor || 'system'}</td><td>${pill(a.action.replace('_', ' ').toLowerCase(), 'gold')}</td><td>${a.detail}</td><td class="scan faint">${a.hash.slice(0, 10)}…</td></tr>`)}</tbody></table></div>`;
};

// ================================================================ shell, router
const NAV = [
  { h: 'Discover' },
  { r: 'home', t: u => u ? 'Dashboard' : 'Home', i: 'home' }, { r: 'catalog', t: () => 'Catalog', i: 'layers' },
  { h: 'Library' },
  { r: 'loans', t: s => s ? 'Loans' : 'My loans', i: 'clock' }, { r: 'holds', t: s => s ? 'Holds queue' : 'My holds', i: 'bookmark' },
  { r: 'fines', t: s => s ? 'Fines' : 'My fines', i: 'coins' }, { r: 'favorites', t: () => 'Reading list', i: 'heart' },
  { r: 'requests', t: s => s ? 'Purchase requests' : 'Request a book', i: 'gift' },
  { h: 'Staff', staff: true },
  { r: 'desk', t: () => 'Circulation desk', i: 'scan', staff: true }, { r: 'members', t: () => 'Members', i: 'users', staff: true }, { r: 'audit', t: () => 'Audit & exports', i: 'shield', staff: true },
];

function shell() {
  const u = S.user, st = staff();
  $('#app').innerHTML = String(h`<div class="shell">
    <aside class="side" id="side"><div class="brand"><span class="logo">${ic('book')}</span><div><b>Athenaeum</b><small>Library system</small></div></div>
      <nav class="nav">${NAV.filter(n => !n.staff || st).map(n => n.h ? h`<div class="nav-h">${n.h}</div>` : h`<a href="#/${n.r}" data-nav="${n.r}">${ic(n.i)}<span>${n.t(st)}</span></a>`)}
        <div class="nav-h">Account</div><a href="#/profile" data-nav="profile">${ic('user')}<span>My library card</span></a></nav>
      <div class="side-foot"><div class="me"><span class="avatar">${initials(u.name)}</span><div><b>${u.name}</b><small>${u.role.toLowerCase()} · ${u.plan}</small></div></div>
        <div class="foot-actions"><button class="btn sm" data-act="theme" style="flex:1">${ic('sun')} Theme</button><button class="btn sm" data-act="logout" style="flex:1">${ic('logout')} Sign out</button></div></div></aside>
    <div class="main"><header class="top"><button class="btn icon menu-btn" data-act="menu" aria-label="Menu">${ic('menu')}</button>
        <div class="search">${ic('search')}<input id="gsearch" placeholder="Search books, authors, ISBN…" aria-label="Search"><kbd>/</kbd></div><span class="grow"></span>
        <div style="position:relative"><button class="btn icon" data-act="notif" aria-label="Notifications">${ic('bell')}<span class="badge" id="badge" hidden></span></button><div id="notifPanel" class="panel" hidden></div></div></header>
      <main id="view"></main></div></div>`);
  refreshBadge();
}

async function refreshBadge() {
  if (!S.user) return;
  try { const n = await api('/api/notifications'); S.notif = n; const b = $('#badge'); if (b) { b.hidden = !n.unread; b.textContent = n.unread; } } catch { /* ignore */ }
}

const kindIcon = { LOAN: 'book', RETURN: 'check', DUE: 'clock', OVERDUE: 'alert', HOLD: 'bookmark', FINE: 'coins', REQUEST: 'gift' };
async function toggleNotif() {
  const p = $('#notifPanel');
  if (!p.hidden) { p.hidden = true; return; }
  await refreshBadge();
  const items = (S.notif && S.notif.items) || [];
  p.innerHTML = String(h`<header><b>Notifications</b>${items.some(n => !n.read_at) ? h`<button class="btn sm ghost" data-act="readAll">Mark all read</button>` : ''}</header>
    ${items.length ? items.map(n => h`<div class="note ${n.read_at ? '' : 'unread'}">${ic(kindIcon[n.kind] || 'bell')}<div>${n.message}<time>${fmtDT(n.created_at)}</time></div></div>`) : raw('<div class="empty" style="padding:30px">You\'re all caught up.</div>')}`);
  p.hidden = false;
}

function parseRoute() {
  const m = (location.hash || '#/').slice(2).split('?');
  const [name, id] = (m[0] || '').split('/');
  return { name: name || 'home', id, q: new URLSearchParams(m[1] || '') };
}

let renderId = 0;
async function render() {
  if (!S.user) { return authView(R.authMode || 'login'); }
  if (!$('#view')) shell();
  R = parseRoute();
  if (!VIEWS[R.name] && !['members', 'desk', 'audit'].includes(R.name)) R.name = 'home';
  if (['desk', 'members', 'audit'].includes(R.name) && !staff()) R.name = 'home';
  $$('.nav a').forEach(a => a.classList.toggle('on', a.dataset.nav === R.name || (R.name === 'book' && a.dataset.nav === 'catalog')));
  $('#side').classList.remove('open');
  const view = $('#view'), my = ++renderId;
  view.classList.remove('fade');
  view.innerHTML = '<div class="skeleton"></div>';
  try {
    const out = await VIEWS[R.name](R.id);
    if (my !== renderId) return;
    view.innerHTML = String(out.html || out);
    view.classList.add('fade');
    if (out.after) out.after();
    animateCounts();
    window.scrollTo(0, 0);
  } catch (e) {
    if (my !== renderId) return;
    view.innerHTML = String(h`<div class="card">${empty('alert', 'Could not load this page', e.message, h`<button class="btn" data-act="reload">Try again</button>`)}</div>`);
  }
}

function animateCounts() {
  $$('[data-count]').forEach(el => {
    const to = Number(el.dataset.count); if (!Number.isFinite(to) || to < 2) return;
    const t0 = performance.now();
    const step = t => { const p = Math.min(1, (t - t0) / 700); el.textContent = Math.round(to * (1 - Math.pow(1 - p, 3))); if (p < 1) requestAnimationFrame(step); };
    requestAnimationFrame(step);
  });
}

// ================================================================ actions (click delegation)
const ACTIONS = {
  close: () => closeModal(),
  reload: () => render(),
  menu: () => $('#side').classList.toggle('open'),
  theme: () => { const t = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'; ls.set('lms_theme', t); applyTheme(t); },
  logout: () => signOut(),
  notif: () => toggleNotif(),
  readAll: () => guard(async () => { await post('/api/notifications/read-all'); await refreshBadge(); $('#notifPanel').hidden = true; toast('All caught up'); }),
  authTab: el => { R.authMode = el.dataset.mode; authView(R.authMode); },
  demo: el => guard(async () => finishLogin(await post('/api/auth/login', { email: el.dataset.email, password: el.dataset.pw }))),
  fav: el => guard(async () => { const r = await post(`/api/books/${el.dataset.id}/favorite`); toast(r.favorite ? 'Added to your reading list' : 'Removed from your reading list'); render(); }),
  reserve: el => guard(async () => { const r = await post(`/api/books/${el.dataset.id}/reserve`); toast(`You're #${r.position} in the queue`); render(); }),
  cancelHold: el => guard(async () => { if (!confirm('Cancel this hold?')) return; await api('/api/reservations/' + el.dataset.id, { method: 'DELETE' }); toast('Hold cancelled'); render(); }),
  renew: el => guard(async () => { const r = await post(`/api/loans/${el.dataset.id}/renew`); toast('Renewed — now due ' + fmtDate(r.due_at)); render(); }),
  payFine: el => guard(async () => { const r = await post(`/api/fines/${el.dataset.id}/pay`, { method: staff() ? 'CASH' : 'UPI' }); toast(`Paid ${money(r.paid)}`); render(); refreshBadge(); }),
  payAll: () => guard(async () => { if (!confirm('Pay all outstanding fines? (Demo payment)')) return; const r = await post('/api/fines/pay-all', { method: 'UPI' }); toast(`Paid ${money(r.paid)} — thank you`); render(); }),
  waive: el => guard(async () => { if (!confirm('Waive this fine?')) return; await post(`/api/fines/${el.dataset.id}/waive`); toast('Fine waived'); render(); }),
  delReview: el => guard(async () => { if (!confirm('Delete this review?')) return; await api('/api/reviews/' + el.dataset.id, { method: 'DELETE' }); toast('Review deleted'); render(); }),
  reqStatus: el => guard(async () => { await api('/api/requests/' + el.dataset.id, { method: 'PUT', body: { status: el.dataset.status } }); toast('Request updated'); render(); }),
  openMember: el => guard(() => memberModal(el.dataset.id)),
  resetPw: el => guard(async () => { if (!confirm('Reset this member\'s password? They will be signed out.')) return; const r = await post(`/api/members/${el.dataset.id}/reset-password`); openModal(h`<h2>Temporary password</h2><p class="muted">Shown once. Give it to the member and ask them to change it.</p><div class="card scan" style="font-size:22px;text-align:center;letter-spacing:.12em">${r.temp_password}</div><div class="row" style="margin-top:16px"><button class="btn primary" data-act="close">Done</button></div>`); }),
  clearMember: () => { $('#dCard').value = ''; $('#dMember').value = ''; $('#dChosen').innerHTML = ''; $('#dMember').focus(); },
  fillIssue: el => { $('#dCard').value = el.dataset.card; $('#dBarcode').value = el.dataset.barcode; pickByCard(el.dataset.card); peekCopy($('#dBarcode')); $('#dBarcode').scrollIntoView({ block: 'center' }); },
  pickMember: el => chooseMember(JSON.parse(el.dataset.m)),
  verify: () => guard(async () => {
    const r = await api('/api/audit/verify'), box = $('#verifyOut');
    box.className = 'banner ' + (r.ok ? 'ok' : 'bad');
    box.innerHTML = String(r.ok ? h`${ic('check')}<span><b>Chain intact.</b> ${r.checked} entries verified end to end.</span>` : h`${ic('alert')}<span><b>Tampering detected</b> at entry #${r.brokenAt} (after ${r.checked} good entries).</span>`);
  }),
  export: el => guard(async () => {
    const blob = await api('/api/export/' + el.dataset.what), a = document.createElement('a');
    a.href = URL.createObjectURL(blob); a.download = el.dataset.what + '.csv'; a.click(); URL.revokeObjectURL(a.href); toast('Exported ' + el.dataset.what + '.csv');
  }),
  memberForm: () => openModal(h`<div class="row between"><h2 style="margin:0">Add a member</h2><button class="btn icon ghost" data-act="close" aria-label="Close">${ic('x')}</button></div>
    <form class="form" data-form="memberAdd">${fld('Full name', 'name', { req: true })}${fld('Email', 'email', { type: 'email', req: true })}${fld('Phone', 'phone')}
      <p class="muted" style="margin:0">A temporary password is generated and shown once after saving.</p><button class="btn primary" type="submit">Create member</button></form>`),
  bookForm: el => guard(async () => {
    const b = el.dataset.id ? await api('/api/books/' + el.dataset.id) : {};
    openModal(h`<div class="row between"><h2 style="margin:0">${b.id ? 'Edit book' : 'Add a book'}</h2><button class="btn icon ghost" data-act="close" aria-label="Close">${ic('x')}</button></div>
      <form class="form" data-form="book" data-id="${b.id || ''}">${fld('Title', 'title', { value: b.title, req: true })}
        <div class="grid2">${fld('Authors (comma separated)', 'authors', { value: b.authors, req: true })}${fld('ISBN (10 or 13 digits)', 'isbn', { value: b.isbn, req: true })}</div>
        <div class="grid3">${sel('Category', 'category_id', catOpts(), b.category_id)}${fld('Publisher', 'publisher', { value: b.publisher })}${fld('Year', 'published_year', { type: 'number', value: b.published_year, min: 1000 })}</div>
        <div class="grid3">${fld('Pages', 'pages', { type: 'number', value: b.pages, min: 1 })}${fld('Language', 'language', { value: b.language || 'English' })}${b.id ? '' : fld('Copies', 'copies', { type: 'number', value: 1, min: 0 })}</div>
        ${b.id ? '' : h`<div class="grid2">${fld('Shelf', 'shelf', { ph: 'e.g. C-4' })}${fld('Price per copy', 'price', { type: 'number', value: 0, min: 0 })}</div>`}
        ${fld('Description', 'description', { type: 'textarea', value: b.description })}<button class="btn primary" type="submit">${b.id ? 'Save changes' : 'Add to catalog'}</button></form>`, true);
  }),
  copiesForm: el => openModal(h`<div class="row between"><h2 style="margin:0">Add copies</h2><button class="btn icon ghost" data-act="close" aria-label="Close">${ic('x')}</button></div>
    <form class="form" data-form="copies" data-id="${el.dataset.id}"><div class="grid3">${fld('How many', 'count', { type: 'number', value: 1, min: 1 })}${fld('Shelf', 'shelf')}${fld('Price each', 'price', { type: 'number', value: 0, min: 0 })}</div><button class="btn primary" type="submit">Add copies</button></form>`),
};

document.addEventListener('click', e => {
  const el = e.target.closest('[data-act]');
  if (el && ACTIONS[el.dataset.act]) {
    if (el.tagName === 'A' && el.dataset.act !== 'close') e.preventDefault();
    if (el.tagName === 'A' && el.dataset.act === 'close') closeModal();
    else ACTIONS[el.dataset.act](el, e);
    return;
  }
  const p = $('#notifPanel');
  if (p && !p.hidden && !e.target.closest('#notifPanel')) p.hidden = true;
});

// ================================================================ forms
const FORMS = {
  login: async (f, v) => finishLogin(await post('/api/auth/login', v)),
  register: async (f, v) => finishLogin(await post('/api/auth/register', v)),
  catSearch: (f, v) => setParam('q', v.q.trim()),
  loanSearch: (f, v) => setParam('q', v.q.trim()),
  memberSearch: (f, v) => setParam('q', v.q.trim()),
  review: async (f, v) => { await post(`/api/books/${f.dataset.id}/reviews`, { rating: Number(v.rating), body: v.body }); toast('Thanks for your review'); render(); },
  request: async (f, v) => { await post('/api/requests', v); toast('Request sent to the librarians'); render(); },
  profile: async (f, v) => { S.user = { ...S.user, ...(await api('/api/me', { method: 'PUT', body: { name: v.name, phone: v.phone } })) }; toast('Profile saved'); shell(); render(); },
  password: async (f, v) => { await post('/api/me/password', v); f.reset(); toast('Password updated'); },
  book: async (f, v) => {
    const id = f.dataset.id;
    const r = await api(id ? '/api/books/' + id : '/api/books', { method: id ? 'PUT' : 'POST', body: v });
    closeModal(); toast(id ? 'Book updated' : 'Book added'); location.hash = '#/book/' + r.id; render();
  },
  copies: async (f, v) => { await post(`/api/books/${f.dataset.id}/copies`, v); closeModal(); toast('Copies added'); render(); },
  memberAdd: async (f, v) => {
    const r = await post('/api/members', v);
    openModal(h`<h2>Member created</h2><p class="muted">${r.profile.name} — card <b class="scan">${r.profile.card_no}</b></p>
      <p class="muted">Temporary password (shown once):</p><div class="card scan" style="font-size:22px;text-align:center;letter-spacing:.12em">${r.temp_password}</div>
      <div class="row" style="margin-top:16px"><button class="btn primary" data-act="close">Done</button></div>`);
    render();
  },
  memberEdit: async (f, v) => {
    const body = { name: v.name, plan_id: Number(v.plan_id), status: v.status };
    if (v.role) body.role = v.role;
    if (v.extend_months) body.extend_months = Number(v.extend_months);
    await api('/api/members/' + f.dataset.id, { method: 'PUT', body }); closeModal(); toast('Member updated'); render();
  },
  issue: async (f, v) => {
    if (!v.card_no) throw new Error('Pick a member first');
    const r = await post('/api/loans/issue', { card_no: v.card_no, barcode: v.barcode.trim() });
    $('#outResult').innerHTML = String(h`<div class="result ok">${ic('check')} <b>${r.title}</b> issued to ${r.member}.<br><span class="muted">Due ${fmtDate(r.due_at)}</span></div>`);
    S.deskLog.unshift({ t: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }), msg: `Issued ${r.title} → ${r.member}` }); $('#deskLog').innerHTML = String(deskLogHtml());
    $('#dBarcode').value = ''; $('#dCopy').textContent = ''; $('#dBarcode').focus(); refreshBadge();
  },
  return: async (f, v) => {
    const r = await post('/api/loans/return', { barcode: v.barcode.trim(), condition: v.condition });
    $('#inResult').innerHTML = String(h`<div class="result ${r.fine ? 'bad' : 'ok'}">${ic(r.fine ? 'alert' : 'check')} <b>${r.title}</b> checked in from ${r.member}.${r.fine ? h`<br>Fine added: <b>${money(r.fine)}</b>` : h`<br><span class="muted">No fine — returned on time.</span>`}</div>`);
    S.deskLog.unshift({ t: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }), msg: `Returned ${r.title} ← ${r.member}${r.fine ? ' (fine ' + money(r.fine) + ')' : ''}` }); $('#deskLog').innerHTML = String(deskLogHtml());
    $('#rBarcode').value = ''; $('#rCopy').textContent = ''; $('#rBarcode').focus();
  },
};

document.addEventListener('submit', e => {
  const f = e.target.closest('form[data-form]');
  if (!f || !FORMS[f.dataset.form]) return;
  e.preventDefault();
  const btn = f.querySelector('[type=submit]'); if (btn) btn.disabled = true;
  guard(() => FORMS[f.dataset.form](f, Object.fromEntries(new FormData(f)))).finally(() => { if (btn) btn.disabled = false; });
});

document.addEventListener('change', e => {
  const t = e.target;
  if (t.dataset.param) setParam(t.dataset.param, t.value);
  if (t.dataset.copy) guard(async () => { await api('/api/copies/' + t.dataset.copy, { method: 'PUT', body: { status: t.value } }); toast('Copy marked ' + t.value.toLowerCase()); render(); });
});

document.addEventListener('input', e => {
  const t = e.target;
  if (t.dataset.input === 'copyPeek') { clearTimeout(peekTimer); peekTimer = setTimeout(() => peekCopy(t), 250); }
  if (t.dataset.input === 'memberSearch') {
    clearTimeout(memberTimer);
    $('#dCard').value = ''; $('#dChosen').innerHTML = '';
    memberTimer = setTimeout(async () => {
      const q = t.value.trim(), box = $('#dResults');
      if (q.length < 2) { box.innerHTML = ''; return; }
      try {
        const r = (await api('/api/members' + qs({ q }))).slice(0, 8);
        box.innerHTML = r.length ? String(h`<div class="pick-list">${r.map(m => h`<button type="button" data-act="pickMember" data-m="${JSON.stringify(m)}"><span class="avatar sm">${initials(m.name)}</span><span style="flex:1"><b>${m.name}</b><br><small class="muted scan">${m.card_no}</small></span>${statusPill(m.status)}</button>`)}</div>`) : '<p class="muted" style="margin:8px 0 0">No match.</p>';
      } catch { /* ignore */ }
    }, 220);
  }
});

document.addEventListener('keydown', e => {
  if (e.target.id === 'gsearch' && e.key === 'Enter') { location.hash = '#/catalog' + qs({ q: e.target.value.trim() }); e.target.blur(); }
  else if (e.key === '/' && !/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName) && $('#gsearch')) { e.preventDefault(); $('#gsearch').focus(); }
  else if (e.key === 'Escape') { const p = $('#notifPanel'); if (p) p.hidden = true; }
});

window.addEventListener('hashchange', render);
setInterval(refreshBadge, 60000);

// ================================================================ boot
(async function boot() {
  applyTheme();
  if (S.token) { try { S.user = await api('/api/me'); } catch { S.token = null; ls.set('lms_token', null); } }
  try { S.cats = await api('/api/categories'); } catch { S.cats = []; }
  if (S.user) shell();
  render();
})();
})();
