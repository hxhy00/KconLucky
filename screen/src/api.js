const BASE = import.meta.env.PROD ? '/prod-api' : '/dev-api'

// 现场网络抖动很常见：fetch 默认不超时，请求会一直挂着，
// 大屏停在「抽奖中…」，操作员无从判断后端到底有没有落库。
const DEFAULT_TIMEOUT = 10000

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
    // 统一成带标识的错误，调用方据此区分「后端返回的业务结果」与「结果未知」
    const err = new Error(e.name === 'AbortError' ? '请求超时' : '网络异常')
    err.networkError = true
    throw err
  } finally {
    clearTimeout(timer)
  }
}

// 当前场次 + 统计
export function fetchStatus() {
  return request('/app/screen/status')
}

// 切换场次
export function switchSession(sessionCode) {
  return request('/app/screen/session', {
    method: 'POST',
    body: JSON.stringify({ sessionCode })
  })
}

// 抽一个
export function draw(sessionCode, prizeId) {
  return request('/app/screen/draw', {
    method: 'POST',
    body: JSON.stringify({ sessionCode, prizeId })
  })
}

// 作废重抽
export function voidWinner(winnerId) {
  return request('/app/screen/void', {
    method: 'POST',
    body: JSON.stringify({ winnerId })
  })
}
