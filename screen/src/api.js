const BASE = import.meta.env.PROD ? '/prod-api' : '/dev-api'

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })
  return res.json()
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
