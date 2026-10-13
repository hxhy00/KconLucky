import request from '@/utils/request'

// 兑奖核验日志列表（分页）
// 时间范围由调用方用 addDateRange 挂到 params.beginTime / params.endTime
export function listVerifyLog(query) {
  return request({ url: '/admin/verifylog/list', method: 'get', params: query })
}
