import request from '@/utils/request'

// 数据清场：清空名单/签到/中奖/核验日志，端口号池归零，奖品已抽数归零
// password 为当前登录人本人的账号密码，服务端做 BCrypt 二次校验（2026-10-21 起）
export function resetData(password) {
  return request({ url: '/admin/data/reset', method: 'post', data: { password } })
}
