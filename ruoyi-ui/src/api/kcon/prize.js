import request from '@/utils/request'

export function listPrize(query) {
  return request({ url: '/admin/prize/list', method: 'get', params: query })
}

export function addPrize(data) {
  return request({ url: '/admin/prize', method: 'post', data })
}

export function updatePrize(data) {
  return request({ url: '/admin/prize', method: 'put', data })
}

export function delPrize(id) {
  return request({ url: '/admin/prize/' + id, method: 'delete' })
}

// 中奖记录
export function listWinner(query) {
  return request({ url: '/admin/verify/winners', method: 'get', params: query })
}

// 核验
export function verify(data) {
  return request({ url: '/admin/verify', method: 'post', data })
}

// 确认发放
export function receivePrize(data) {
  return request({ url: '/admin/verify/receive', method: 'post', data })
}

// 中奖作废（端口号回池）
export function voidWinner(winnerId) {
  return request({ url: '/admin/verify/void/' + winnerId, method: 'post' })
}
