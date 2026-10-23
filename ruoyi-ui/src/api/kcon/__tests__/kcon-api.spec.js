import { beforeEach, describe, expect, it, vi } from 'vitest'

// 统一拦截 request，只验证各 API 封装的 URL / method / 参数拼装
vi.mock('@/utils/request', () => ({
  default: vi.fn(() => Promise.resolve({ code: 200 }))
}))

import request from '@/utils/request'
import {
  listAttendee, getAttendee, addAttendee, updateAttendee, resetAttendeeTicketType,
  grantBenefit, delAttendee, importAttendee, previewImport, dashboard, qrcodeUrl, fetchQrcodeBlob
} from '@/api/kcon/attendee'
import {
  listChannelMapping, optionselectChannelMapping, addChannelMapping,
  updateChannelMapping, delChannelMapping, resyncChannelMapping
} from '@/api/kcon/channel'
import { resetData } from '@/api/kcon/data'
import {
  getDrawStatus, doDraw, voidDrawBatch, switchDrawSession, getDrawSetting, saveDrawSetting
} from '@/api/kcon/draw'
import { listBenefitGrant, listPrizeGrant } from '@/api/kcon/grant'
import {
  listPrize, addPrize, updatePrize, delPrize, listWinner,
  verify, receivePrize, voidWinner
} from '@/api/kcon/prize'
import { listSessions } from '@/api/kcon/session'
import {
  listStaff, listStaffRoles, addStaff, resetStaffPwd, changeStaffStatus, delStaff
} from '@/api/kcon/staff'

/** 取最近一次 request 调用的参数 */
function lastCall() {
  return request.mock.calls[request.mock.calls.length - 1][0]
}

beforeEach(() => {
  request.mockClear()
})

describe('参会名单 API', () => {
  it('列表按 query 透传分页与筛选', () => {
    const query = { pageNum: 2, pageSize: 20, name: '张' }
    listAttendee(query)
    expect(lastCall()).toEqual({ url: '/admin/attendee/list', method: 'get', params: query })
  })

  it('详情拼接到路径', () => {
    getAttendee(7)
    expect(lastCall()).toEqual({ url: '/admin/attendee/7', method: 'get' })
  })

  it('新增与修改共用集合路径，方法不同', () => {
    addAttendee({ name: '张三' })
    expect(lastCall()).toEqual({ url: '/admin/attendee', method: 'post', data: { name: '张三' } })
    updateAttendee({ id: 7, name: '张三' })
    expect(lastCall()).toEqual({ url: '/admin/attendee', method: 'put', data: { id: 7, name: '张三' } })
  })

  it('恢复权益类型走 ticket-type/reset', () => {
    resetAttendeeTicketType(7)
    expect(lastCall()).toEqual({ url: '/admin/attendee/7/ticket-type/reset', method: 'put' })
  })

  it('权益发放走 /admin/benefit/grant/{id}', () => {
    grantBenefit(7)
    expect(lastCall()).toEqual({ url: '/admin/benefit/grant/7', method: 'post' })
  })

  it('删除支持批量 id 字符串', () => {
    delAttendee('1,2,3')
    expect(lastCall()).toEqual({ url: '/admin/attendee/1,2,3', method: 'delete' })
  })

  it('导入与预检使用 multipart 且超时放宽到 120s', () => {
    const form = new FormData()
    importAttendee(form)
    expect(lastCall()).toEqual({
      url: '/admin/attendee/import',
      method: 'post',
      data: form,
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
    previewImport(form)
    expect(lastCall()).toEqual({
      url: '/admin/attendee/import/preview',
      method: 'post',
      data: form,
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
  })

  it('看板与二维码地址', () => {
    dashboard()
    expect(lastCall()).toEqual({ url: '/admin/attendee/dashboard', method: 'get' })
    qrcodeUrl()
    expect(lastCall()).toEqual({ url: '/admin/data/qrcode', method: 'get' })
  })

  it('二维码图片必须按 blob 取，才能带鉴权头', () => {
    fetchQrcodeBlob()
    expect(lastCall()).toEqual({
      url: '/admin/data/qrcode.png',
      method: 'get',
      responseType: 'blob'
    })
  })
})

describe('渠道来源 API', () => {
  it('列表与下拉', () => {
    listChannelMapping({ channelSource: 'S' })
    expect(lastCall()).toEqual({
      url: '/admin/channel-mapping/list',
      method: 'get',
      params: { channelSource: 'S' }
    })
    optionselectChannelMapping()
    expect(lastCall()).toEqual({ url: '/admin/channel-mapping/optionselect', method: 'get' })
  })

  it('CRUD 与重算', () => {
    addChannelMapping({ channelSource: 'S' })
    expect(lastCall()).toEqual({
      url: '/admin/channel-mapping',
      method: 'post',
      data: { channelSource: 'S' }
    })
    updateChannelMapping({ id: 1 })
    expect(lastCall()).toEqual({ url: '/admin/channel-mapping', method: 'put', data: { id: 1 } })
    delChannelMapping(1)
    expect(lastCall()).toEqual({ url: '/admin/channel-mapping/1', method: 'delete' })
    resyncChannelMapping()
    expect(lastCall()).toEqual({ url: '/admin/channel-mapping/resync', method: 'post' })
  })
})

describe('数据清场 API', () => {
  it('密码包在 data.password 里，不拼 query', () => {
    resetData('secret')
    expect(lastCall()).toEqual({
      url: '/admin/data/reset',
      method: 'post',
      data: { password: 'secret' }
    })
  })
})

describe('抽奖控制台 API', () => {
  it('状态 / 抽奖 / 切场次', () => {
    getDrawStatus()
    expect(lastCall()).toEqual({ url: '/admin/draw/status', method: 'get' })
    doDraw({ sessionCode: 'AM' })
    expect(lastCall()).toEqual({ url: '/admin/draw/draw', method: 'post', data: { sessionCode: 'AM' } })
    switchDrawSession({ sessionCode: 'PM' })
    expect(lastCall()).toEqual({ url: '/admin/draw/session', method: 'post', data: { sessionCode: 'PM' } })
    voidDrawBatch({ sessionCode: 'AM', tierId: 1 })
    expect(lastCall()).toEqual({
      url: '/admin/draw/void-batch',
      method: 'post',
      data: { sessionCode: 'AM', tierId: 1 }
    })
  })

  it('大屏设置读写', () => {
    getDrawSetting()
    expect(lastCall()).toEqual({ url: '/admin/draw/setting', method: 'get' })
    saveDrawSetting({ sponsors: 'A,B' })
    expect(lastCall()).toEqual({
      url: '/admin/draw/setting',
      method: 'post',
      data: { sponsors: 'A,B' }
    })
  })
})

describe('发放日志 API', () => {
  it('权益与奖品是两个独立端点', () => {
    listBenefitGrant({ pageNum: 1 })
    expect(lastCall()).toEqual({
      url: '/admin/benefit/list',
      method: 'get',
      params: { pageNum: 1 }
    })
    listPrizeGrant({ pageNum: 1 })
    expect(lastCall()).toEqual({
      url: '/admin/prize-grant/list',
      method: 'get',
      params: { pageNum: 1 }
    })
  })
})

describe('奖品与核验 API', () => {
  it('奖品 CRUD', () => {
    listPrize({ sessionCode: 'AM' })
    expect(lastCall()).toEqual({
      url: '/admin/prize/list',
      method: 'get',
      params: { sessionCode: 'AM' }
    })
    addPrize({ tierName: '一等奖' })
    expect(lastCall()).toEqual({ url: '/admin/prize', method: 'post', data: { tierName: '一等奖' } })
    updatePrize({ id: 1 })
    expect(lastCall()).toEqual({ url: '/admin/prize', method: 'put', data: { id: 1 } })
    delPrize(1)
    expect(lastCall()).toEqual({ url: '/admin/prize/1', method: 'delete' })
  })

  it('中奖列表与核验发放', () => {
    listWinner({ status: 0 })
    expect(lastCall()).toEqual({ url: '/admin/verify/winners', method: 'get', params: { status: 0 } })
    verify({ portNo: 1, phone: '13800000000' })
    expect(lastCall()).toEqual({
      url: '/admin/verify',
      method: 'post',
      data: { portNo: 1, phone: '13800000000' }
    })
    receivePrize({ portNo: 1, phone: '13800000000' })
    expect(lastCall()).toEqual({
      url: '/admin/verify/receive',
      method: 'post',
      data: { portNo: 1, phone: '13800000000' }
    })
  })

  it('作废中奖拼接到路径', () => {
    voidWinner(9)
    expect(lastCall()).toEqual({ url: '/admin/verify/void/9', method: 'post' })
  })
})

describe('场次与工作人员 API', () => {
  it('场次列表', () => {
    listSessions()
    expect(lastCall()).toEqual({ url: '/admin/session/list', method: 'get' })
  })

  it('工作人员列表 / 岗位 / 建号', () => {
    listStaff({ pageNum: 1 })
    expect(lastCall()).toEqual({ url: '/admin/staff/list', method: 'get', params: { pageNum: 1 } })
    listStaffRoles()
    expect(lastCall()).toEqual({ url: '/admin/staff/roles', method: 'get' })
    addStaff({ name: '张三' })
    expect(lastCall()).toEqual({ url: '/admin/staff', method: 'post', data: { name: '张三' } })
  })

  it('重置密码 / 启停 / 删除', () => {
    resetStaffPwd({ userId: 1, password: 'x' })
    expect(lastCall()).toEqual({
      url: '/admin/staff/resetPwd',
      method: 'put',
      data: { userId: 1, password: 'x' }
    })
    changeStaffStatus({ userId: 1, status: '0' })
    expect(lastCall()).toEqual({
      url: '/admin/staff/changeStatus',
      method: 'put',
      data: { userId: 1, status: '0' }
    })
    delStaff('1,2')
    expect(lastCall()).toEqual({ url: '/admin/staff/1,2', method: 'delete' })
  })
})
