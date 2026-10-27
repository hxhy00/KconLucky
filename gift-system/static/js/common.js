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
/* HTML 转义：所有服务端数据（用户可输入的姓名/公司/部门等）插入 innerHTML 前必须过 esc()，
   防止存储型 XSS —— 公开注册接口无需登录，company 可携带 <img onerror=...> 之类载荷 */
function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}
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
  });
  // msg 可能携带服务端拼入的用户输入（如「该邮箱已被其他渠道使用」含用户数据），用 textContent 防注入
  t.textContent = msg;
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
async function openMail(id) {
  const mask = $('#mailModal');
  if (!mask) return;
  const frame = $('#mailFrame');
  frame.srcdoc = '<p style="font-family:sans-serif;color:#888;padding:24px;">加载中…</p>';
  mask.classList.add('show');
  // iframe 导航带不了 X-Session 自定义头（会 401），改 fetch 取回 HTML 后用 srcdoc 展示；
  // srcdoc 文档为独立源且继承父页 CSP，邮件内脚本不会拿到本站会话
  try {
    const r = await fetch(KCON_BASE + '/api/mailbox/' + encodeURIComponent(id) + '/view', {
      headers: { 'X-Session': API.sid }
    });
    if (!r.ok) { frame.srcdoc = '<p style="font-family:sans-serif;color:#e33;padding:24px;">无法加载邮件（' + r.status + '）</p>'; return; }
    frame.srcdoc = await r.text();
  } catch (e) {
    frame.srcdoc = '<p style="font-family:sans-serif;color:#e33;padding:24px;">网络异常，请稍后重试</p>';
  }
}
function closeMail() {
  const mask = $('#mailModal');
  if (mask) mask.classList.remove('show');
}