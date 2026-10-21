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

// 全量重算：把当前所有映射重新应用到存量名单（修复先导入后补映射的错乱数据）
export function resyncChannelMapping() {
  return request({ url: '/admin/channel-mapping/resync', method: 'post' })
}
