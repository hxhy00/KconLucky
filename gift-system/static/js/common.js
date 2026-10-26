/* ===== KCon2026 Demo 公共工具 ===== */
/* 子路径部署前缀：由页面注入 window.KCON_BASE（如 "/kconluck2026"），根路径部署时为空 */
const KCON_BASE = window.KCON_BASE || '';
const LOGIN_URL = KCON_BASE + '/login';
const API = {
  sid: localStorage.getItem('kcon_sid') || '',
  async req(path, data, method) {
    const opt = { method: method || (data ? 'POST' : 'GET'), headers: {} };
    if (this.sid) opt.headers['X-Session'] = this.sid;
    if (data) {
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(data);
    }
    const r = await fetch(KCON_BASE + path, opt);
    if (r.status === 401) { location.href = LOGIN_URL; throw new Error('未登录'); }
    return await r.json();
  },
  get(p) { return this.req(p, null, 'GET'); },
  post(p, d) { return this.req(p, d, 'POST'); },
  setSid(s) { this.sid = s; localStorage.setItem('kcon_sid', s); },
  clear() { this.sid = ''; localStorage.removeItem('kcon_sid'); },
};
function $(sel, root) { return (root || document).querySelector(sel); }
function $$(sel, root) { return Array.from((root || document).querySelectorAll(sel)); }
function el(tag, attrs, html) {
  const e = document.createElement(tag);
  if (attrs) for (const k in attrs) {
    if (k === 'class') e.className = attrs[k];
    else if (k === 'style') e.style.cssText = attrs[k];
    else e.setAttribute(k, attrs[k]);
  }
  if (html !== undefined) e.innerHTML = html;
  return e;
}
function toast(msg, type) {
  const box = $('#toast') || (() => {
    const b = el('div', { id: 'toast', style: 'position:fixed;top:24px;left:50%;transform:translateX(-50%);z-index:999;display:flex;flex-direction:column;gap:10px;align-items:center;' });
    document.body.appendChild(b);
    return b;
  })();
  const colors = { ok: '#22c55e', err: '#ef4444', info: '#00e5ff' };
  const t = el('div', {
    style: `padding:12px 22px;border-radius:8px;font-size:13px;font-weight:600;color:#fff;
            background:${colors[type] || colors.info};box-shadow:0 8px 24px rgba(0,0,0,.5);
            opacity:0;transform:translateY(-10px);transition:.25s;`
  }, msg);
  box.appendChild(t);
  requestAnimationFrame(() => { t.style.opacity = '1'; t.style.transform = 'translateY(0)'; });
  setTimeout(() => {
    t.style.opacity = '0'; t.style.transform = 'translateY(-10px)';
    setTimeout(() => t.remove(), 300);
  }, 2600);
}
function copyText(text) {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => toast('已复制到剪贴板', 'ok'));
  } else {
    const ta = el('textarea'); ta.value = text; document.body.appendChild(ta);
    ta.select(); document.execCommand('copy'); ta.remove();
    toast('已复制到剪贴板', 'ok');
  }
}
function fmtTime(s) {
  if (!s) return '--';
  return s.replace(/^\d{4}-/, '').slice(0, 11);
}
const STATUS = {
  referrer: { 1: ['待审批', 'wait'], 2: ['已通过', 'ok'], 3: ['已拒绝', 'no'], 4: ['已停用', 'dim'] },
  recipient: { 1: ['待审批', 'wait'], 2: ['已通过', 'ok'], 3: ['已拒绝', 'no'], 4: ['已签到', 'info'], 5: ['已作废', 'dim'] },
  channel: { 1: ['启用', 'ok'], 2: ['已停用', 'dim'], 3: ['已删除', 'no'] },
};
function badge(kind, status) {
  const m = (STATUS[kind] || {})[status] || ['未知', 'dim'];
  return `<span class="badge ${m[1]}">${m[0]}</span>`;
}
function logout() {
  API.post('/api/auth/logout', {}).finally(() => { API.clear(); location.href = LOGIN_URL; });
}
function requireRole(role) {
  API.get('/api/me').then(r => {
    if (!r.ok || r.role !== role) { location.href = LOGIN_URL; }
  }).catch(() => { location.href = LOGIN_URL; });
}
function openMail(id) {
  const mask = $('#mailModal');
  if (!mask) return;
  $('#mailFrame').src = `${KCON_BASE}/api/mailbox/${id}/view`;
  mask.classList.add('show');
}
function closeMail() {
  const mask = $('#mailModal');
  if (mask) mask.classList.remove('show');
}