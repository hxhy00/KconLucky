// 生产环境走 nginx 同源反代（默认 /prod-api，挂子路径时为 /kconluck2026/prod-api）；
// 开发环境走 vite proxy /dev-api
const BASE = import.meta.env.VITE_API_BASE ||
  (import.meta.env.PROD ? '/prod-api' : '/dev-api')

// 观众在弱网（4G / 场馆 WiFi）下扫码，fetch 不设超时会让按钮一直停在「查询中…」，
// 排队直接堵死。这里统一加超时，超时后给出可操作的重试提示。
const DEFAULT_TIMEOUT = 12000

async function request(path, options = {}) {
  const { timeout = DEFAULT_TIMEOUT, headers, ...rest } = options
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeout)
  try {
    const res = await fetch(BASE + path, {
      ...rest,
      // 调用方传的 headers 与默认合并而非整体覆盖；
      // GET 请求不带 Content-Type（无请求体，带了会多一次 CORS 预检）
      headers: { ...(rest.body ? { 'Content-Type': 'application/json' } : {}), ...headers },
      cache: 'no-store',
      signal: controller.signal
    })
    // fetch 只在网络层失败时 reject：网关 502、鉴权失败等仍会正常返回。
    // 不查状态码就会把 HTML 错误页交给 res.json()，最终被归成「网络异常」，
    // 掩盖真实原因，也无法给出可操作的提示。
    if (!res.ok) {
      // 429 是后端限流（连续点击 / 公网刷接口），不是网络故障：
      // 归为 networkError 只会让观众看到笼统的「服务异常」。这里给出可操作文案，
      // 且不打 networkError 标记（下方 catch 据此保留原文案）
      const limited = res.status === 429
      const err = new Error(limited ? '操作过于频繁，请稍后再试' : `服务异常（HTTP ${res.status}）`)
      if (limited) err.rateLimited = true
      else err.networkError = true
      throw err
    }
    const contentType = (res.headers && res.headers.get && res.headers.get('content-type')) || ''
    if (!contentType.includes('application/json')) {
      const err = new Error('服务异常，请稍后重试')
      err.networkError = true
      throw err
    }
    return await res.json()
  } catch (e) {
    // 上面主动抛出的错误保留原文案（限流同样不是网络异常），其余统一归为网络/超时异常
    if (e && (e.networkError || e.rateLimited)) throw e
    const err = new Error(e.name === 'AbortError' ? '请求超时' : '网络异常')
    err.networkError = true
    throw err
  } finally {
    clearTimeout(timer)
  }
}

// 查询参会人（完整 11 位手机号）
export function queryAttendee(keyword) {
  return request('/app/checkin/query', {
    method: 'POST',
    body: JSON.stringify({ keyword: String(keyword).trim() })
  })
}

// 确认签到
// phone 必传（完整 11 位）：公网版用它做二次校验，只凭 attendeeId 无法替他人签到。
export function submitCheckin(attendeeId, phone) {
  return request('/app/checkin/submit', {
    method: 'POST',
    body: JSON.stringify({ attendeeId, phone: String(phone).trim() })
  })
}

// 回查（找回端口号）
// 手机号放请求体而非 URL：nginx 的 log_format 会记录完整 $request，
// GET 查询串会把观众手机号明文写进访问日志（还会留在浏览器历史里），POST body 则不会。
export function lookup(keyword) {
  return request('/app/checkin/lookup', {
    method: 'POST',
    body: JSON.stringify({ keyword: String(keyword).trim() })
  })
}
