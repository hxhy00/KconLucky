import request from '@/utils/request'

// 名单列表
export function listAttendee(query) {
  return request({ url: '/admin/attendee/list', method: 'get', params: query })
}

// 名单详情
export function getAttendee(id) {
  return request({ url: '/admin/attendee/' + id, method: 'get' })
}

// 新增
export function addAttendee(data) {
  return request({ url: '/admin/attendee', method: 'post', data })
}

// 修改（补录公司/邮箱/参加次数/职务）
export function updateAttendee(data) {
  return request({ url: '/admin/attendee', method: 'put', data })
}

// 删除
export function delAttendee(ids) {
  return request({ url: '/admin/attendee/' + ids, method: 'delete' })
}

// 导入（FormData）
export function importAttendee(formData) {
  return request({
    url: '/admin/attendee/import',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

// 看板统计
export function dashboard() {
  return request({ url: '/admin/attendee/dashboard', method: 'get' })
}

// 签到二维码地址
export function qrcodeUrl() {
  return request({ url: '/admin/data/qrcode', method: 'get' })
}

// 签到二维码图片（后端本地生成 PNG，离线可用）。
// 接口在鉴权之后，裸 <img src> 不会携带 Token，只能用 request 取 blob 再转 objectURL
export function fetchQrcodeBlob() {
  return request({
    url: '/admin/data/qrcode.png',
    method: 'get',
    responseType: 'blob'
  })
}

// 清场
export function resetData() {
  return request({ url: '/admin/data/reset', method: 'post' })
}
