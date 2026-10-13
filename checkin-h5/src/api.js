// 生产环境走 nginx 同源反代 /prod-api；开发环境走 vite proxy /dev-api
const BASE = import.meta.env.PROD ? '/prod-api' : '/dev-api'

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })
  return res.json()
}

// 查询参会人（全号或后四位）
export function queryAttendee(keyword) {
  return request('/app/checkin/query', {
    method: 'POST',
    body: JSON.stringify({ keyword: String(keyword).trim(), type: 'phone' })
  })
}

// 确认签到
export function submitCheckin(attendeeId) {
  return request('/app/checkin/submit', {
    method: 'POST',
    body: JSON.stringify({ attendeeId })
  })
}

// 回查（找回端口号）
export function lookup(keyword) {
  return request('/app/checkin/lookup?keyword=' + encodeURIComponent(keyword))
}
