import request from '@/utils/request'

// 工作人员账号（kcon_staff）：仅超管可用的简化管理接口
export function listStaff(query) {
  return request({ url: '/admin/staff/list', method: 'get', params: query })
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
