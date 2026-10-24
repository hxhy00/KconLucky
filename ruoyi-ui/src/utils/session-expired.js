/**
 * 会话过期状态（模块级单例）
 *
 * 401 弹窗被用户「取消」后置位：之后所有请求直接快速失败，不再反复重开弹窗。
 * 此前轮询页面（看板 5s / 抽奖台 5s / 名单 10s）会每轮触发一次弹窗，
 * 用户点几次「取消」就弹几次，把现场操作界面彻底挡住。
 *
 * 登录成功后调用 resetSessionExpired() 复位。
 */
export const SESSION_EXPIRED_MESSAGE = '登录已过期，请重新登录'

let expired = false

export function markSessionExpired() {
  expired = true
}

export function resetSessionExpired() {
  expired = false
}

export function isSessionExpired() {
  return expired
}
