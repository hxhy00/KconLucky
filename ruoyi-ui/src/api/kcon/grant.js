import request from '@/utils/request'

// 权益发放日志（仅 BENEFIT，类型由服务端固定）
export function listBenefitGrant(query) {
  return request({ url: '/admin/benefit/list', method: 'get', params: query })
}

// 奖品发放日志（仅 PRIZE，类型由服务端固定）
export function listPrizeGrant(query) {
  return request({ url: '/admin/prize-grant/list', method: 'get', params: query })
}
