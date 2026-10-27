import request from '@/utils/request'

// 账号管理（身份：管理员/签到台/抽奖台/兑奖台）：仅超管可用的简化管理接口
export function listStaff(query) {
  return request({ url: '/admin/staff/list', method: 'get', params: query })
}

// 可选身份列表（建号/编辑页下拉数据源）
export function listStaffRoles() {
  return request({ url: '/admin/staff/roles', method: 'get' })
}

export function addStaff(data) {
  return request({ url: '/admin/staff', method: 'post', data })
}

export function updateStaff(data) {
  return request({ url: '/admin/staff', method: 'put', data })
}

export function resetStaffPwd(data) {
  return request({ url: '/admin/staff/resetPwd', method: 'put', data })
}

export function changeStaffStatus(data) {
  return request({ url: '/admin/staff/changeStatus', method: 'put', data })
}

export function delStaff(userIds) {
  return request({ url: '/admin/staff/' + userIds, method: 'delete' })
}
