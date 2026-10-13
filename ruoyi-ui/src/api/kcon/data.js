import request from '@/utils/request'

// 数据清场：清空名单/签到/中奖/核验日志，端口号池归零，奖品已抽数归零
export function resetData() {
  return request({ url: '/admin/data/reset', method: 'post' })
}
