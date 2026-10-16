import request from '@/utils/request'

// 抽奖池实时状态：当前场次 / 三项统计 / 当前应抽档位 / 档位进度 / 上一次中奖
export function getDrawStatus() {
  return request({ url: '/admin/draw/status', method: 'get' })
}

// 抽奖 { sessionCode, prizeId }，prizeId 为空时后端自动取当前应抽档位
// 返回：中奖结果 + status（刷新后的抽奖池状态）
export function doDraw(data) {
  return request({ url: '/admin/draw/draw', method: 'post', data })
}

// 撤回最近一档 { sessionCode }：把该场次最后一次抽出的整批全部作废，号码回池
// 返回：刷新后的抽奖池状态 + voidedCount（实际撤回人数）
export function voidDrawBatch(data) {
  return request({ url: '/admin/draw/void-batch', method: 'post', data })
}

// 切换场次 { sessionCode }
export function switchDrawSession(data) {
  return request({ url: '/admin/draw/session', method: 'post', data })
}

// 大屏设置：读取赞助商等展示内容
export function getDrawSetting() {
  return request({ url: '/admin/draw/setting', method: 'get' })
}

// 保存大屏设置 { sponsors }：逗号分隔的赞助商名称
export function saveDrawSetting(data) {
  return request({ url: '/admin/draw/setting', method: 'post', data })
}