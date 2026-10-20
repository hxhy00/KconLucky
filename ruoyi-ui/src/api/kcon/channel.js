import request from '@/utils/request'

export function listChannelMapping(query) {
  return request({ url: '/admin/channel-mapping/list', method: 'get', params: query })
}

export function addChannelMapping(data) {
  return request({ url: '/admin/channel-mapping', method: 'post', data })
}

export function updateChannelMapping(data) {
  return request({ url: '/admin/channel-mapping', method: 'put', data })
}

export function delChannelMapping(id) {
  return request({ url: '/admin/channel-mapping/' + id, method: 'delete' })
}
