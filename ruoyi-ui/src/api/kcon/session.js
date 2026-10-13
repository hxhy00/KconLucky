import request from '@/utils/request'

// 全部场次（奖品页、中奖记录页的场次下拉数据源，需求 FR-16）
export function listSessions() {
  return request({ url: '/admin/session/list', method: 'get' })
}