/**
 * 部署基路径工具
 *
 * 背景：管理端挂在 nginx 的 /admin/ 子路径下（见 vue.config.js 的 publicPath）。
 * vue-router 的 base、以及 location.href 这类整页跳转都依赖同一个基路径，
 * 集中在这里避免多处手拼导致少斜杠（如 VUE_APP_BASE_PATH 写成 /admin 时拼出 /adminindex）。
 */

// 基路径统一补齐尾部斜杠，'/' 与 '/admin' 都能得到 '/' 与 '/admin/'
export function basePath() {
  const base = process.env.VUE_APP_BASE_PATH || '/'
  return base.endsWith('/') ? base : base + '/'
}

/**
 * 管理端落地页的完整 URL（登录、退出登录、会话过期时跳转用）
 * 注意：location.href 不会经过 vue-router，必须自己拼 base
 */
export function adminIndexUrl() {
  return basePath() + 'index'
}