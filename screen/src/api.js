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

// 当前场次 + 统计。
// 带上上次收到的 version 走增量轮询：后端发现无变化时只回一个极小的响应，
// 大屏据此跳过整份重渲染（投屏中重渲染会打断中奖动画）。
export function fetchStatus(version) {
  const q = version ? '?version=' + encodeURIComponent(version) : ''
  return request('/app/screen/status' + q)
}

// 注意：大屏只负责展示，不提供抽奖 / 作废 / 切场次接口（全部只读）。
// 抽奖与切场次由管理端「抽奖控制台」发起，大屏靠 fetchStatus 轮询被动感知新批次与场次变化。
// 这里曾导出的 draw() / voidWinner() / switchSession() 已随对应后端端点一起删除。
