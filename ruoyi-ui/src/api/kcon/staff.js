import request from '@/utils/request'

// 工作人员账号（按岗位：签到台/抽奖台/兑奖台）：仅超管可用的简化管理接口
export function listStaff(query) {
  return request({ url: '/admin/staff/list', method: 'get', params: query })
}

// 可选岗位列表（建号页下拉数据源）
export function listStaffRoles() {
  return request({ url: '/admin/staff/roles', method: 'get' })
}

export function addStaff(data) {
  return request({ url: '/admin/staff', method: 'post', data })
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
