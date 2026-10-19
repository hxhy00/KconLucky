// 生产环境走 nginx 同源反代 /prod-api；开发环境走 vite proxy /dev-api
const BASE = import.meta.env.PROD ? '/prod-api' : '/dev-api'

// 观众在弱网（4G / 场馆 WiFi）下扫码，fetch 不设超时会让按钮一直停在「查询中…」，
// 排队直接堵死。这里统一加超时，超时后给出可操作的重试提示。
const DEFAULT_TIMEOUT = 12000

async function request(path, options = {}) {
  const { timeout = DEFAULT_TIMEOUT, ...rest } = options
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeout)
  try {
    const res = await fetch(BASE + path, {
      headers: { 'Content-Type': 'application/json' },
      ...rest,
      signal: controller.signal
    })
    return await res.json()
  } catch (e) {
    const err = new Error(e.name === 'AbortError' ? '请求超时' : '网络异常')
    err.networkError = true
    throw err
  } finally {
    clearTimeout(timer)
  }
}

// 查询参会人（全号或后四位）
export function queryAttendee(keyword) {
  return request('/app/checkin/query', {
    method: 'POST',
    body: JSON.stringify({ keyword: String(keyword).trim() })
  })
}

// 确认签到
// phone 必传（完整 11 位）：公网版用它做二次校验，只凭 attendeeId 无法替他人签到。
// 后四位只用于查询定位，不能作为提交凭证。
export function submitCheckin(attendeeId, phone) {
  return request('/app/checkin/submit', {
    method: 'POST',
    body: JSON.stringify({ attendeeId, phone: String(phone).trim() })
  })
}

// 回查（找回端口号）
export function lookup(keyword) {
  return request('/app/checkin/lookup?keyword=' + encodeURIComponent(keyword))
}
