import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * request.js 的拦截器是管理端所有请求的公共入口：
 * 鉴权头、GET 参数拼装、防重复提交、401/403/业务码、网络错误文案都在这里。
 * 这里用假 axios 实例捕获拦截器，再直接调用拦截器函数验证行为，无需真实网络。
 */
const mocks = vi.hoisted(() => {
  const handlers = { request: [], response: [] }
  const instance = {
    interceptors: {
      request: { use: fn => handlers.request.push(fn) },
      response: { use: (ok, err) => handlers.response.push([ok, err]) }
    },
    post: vi.fn()
  }
  return { handlers, instance }
})

const ui = vi.hoisted(() => {
  const message = vi.fn()
  message.error = vi.fn()
  return {
    message,
    confirm: vi.fn(),
    notificationError: vi.fn(),
    loadingService: vi.fn(() => ({ close: vi.fn() }))
  }
})

vi.mock('axios', () => ({
  default: {
    defaults: { headers: {} },
    create: () => mocks.instance
  }
}))

vi.mock('element-ui', () => ({
  Message: ui.message,
  MessageBox: { confirm: ui.confirm },
  Notification: { error: ui.notificationError },
  Loading: { service: ui.loadingService }
}))

vi.mock('@/store', () => ({
  default: { dispatch: vi.fn(() => Promise.resolve()) }
}))

vi.mock('@/utils/auth', () => ({ getToken: vi.fn() }))
vi.mock('@/utils/path', () => ({ adminIndexUrl: vi.fn(() => '/admin/index') }))
vi.mock('@/plugins/cache', () => ({
  default: { session: { getJSON: vi.fn(), setJSON: vi.fn() } }
}))
vi.mock('file-saver', () => ({ saveAs: vi.fn() }))

import service, { download, isRelogin } from '@/utils/request'
import store from '@/store'
import cache from '@/plugins/cache'
import { getToken } from '@/utils/auth'
import { saveAs } from 'file-saver'
import { SESSION_EXPIRED_MESSAGE, isSessionExpired, resetSessionExpired } from '@/utils/session-expired'

const onRequest = () => mocks.handlers.request[0]
const onResponse = () => mocks.handlers.response[0][0]
const onResponseError = () => mocks.handlers.response[0][1]

/** 等一轮宏任务 + 微任务，让 401 弹窗的 then/catch 回调执行完 */
const tick = () => new Promise(resolve => setTimeout(resolve, 0))

beforeEach(() => {
  getToken.mockReset().mockReturnValue('tok-123')
  cache.session.getJSON.mockReset().mockReturnValue(null)
  cache.session.setJSON.mockReset()
  ui.message.mockReset()
  ui.message.error.mockReset()
  ui.confirm.mockReset().mockRejectedValue(new Error('cancel'))
  ui.notificationError.mockReset()
  ui.loadingService.mockReset().mockReturnValue({ close: vi.fn() })
  mocks.instance.post.mockReset()
  store.dispatch.mockReset().mockResolvedValue()
  saveAs.mockReset()
  isRelogin.show = false
  resetSessionExpired()
  vi.stubGlobal('location', { href: '' })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('请求拦截器', () => {
  it('默认给请求带上 Bearer Token', () => {
    const config = onRequest()({ headers: {}, method: 'get', url: '/admin/attendee/list' })

    expect(config.headers.Authorization).toBe('Bearer tok-123')
  })

  it('标记 isToken:false 的请求（登录、二维码等）不带 Token', () => {
    const config = onRequest()({ headers: { isToken: false }, method: 'post', url: '/login' })

    expect(config.headers.Authorization).toBeUndefined()
  })

  it('GET 参数拼进 URL，并清空 params 避免 axios 重复拼接', () => {
    const config = onRequest()({
      headers: {},
      method: 'get',
      url: '/admin/attendee/list',
      params: { pageNum: 1, name: '张 三' }
    })

    expect(config.url).toBe('/admin/attendee/list?pageNum=1&name=%E5%BC%A0%20%E4%B8%89')
    expect(config.params).toEqual({})
  })

  it('同一 POST 在 1 秒内重复提交时直接拒绝，不发起第二次请求', async () => {
    const data = { name: '张三' }
    cache.session.getJSON.mockReturnValue({
      url: '/admin/attendee',
      data: JSON.stringify(data),
      time: Date.now()
    })

    await expect(onRequest()({ headers: {}, method: 'post', url: '/admin/attendee', data }))
      .rejects.toThrow('操作过于频繁，请稍后重试')
    expect(cache.session.setJSON).not.toHaveBeenCalled()
  })

  it('超过防重窗口或内容不同则放行并刷新缓存', () => {
    cache.session.getJSON.mockReturnValue({
      url: '/admin/attendee',
      data: JSON.stringify({ name: '张三' }),
      time: Date.now() - 5000
    })

    expect(() => onRequest()({ headers: {}, method: 'post', url: '/admin/attendee', data: { name: '张三' } }))
      .not.toThrow()
    expect(cache.session.setJSON).toHaveBeenCalledTimes(1)
  })

  it('未命中缓存时写入本次请求指纹', () => {
    const config = onRequest()({ headers: {}, method: 'post', url: '/admin/attendee', data: { name: '张三' } })

    expect(config.url).toBe('/admin/attendee')
    expect(cache.session.setJSON).toHaveBeenCalledWith('sessionObj', expect.objectContaining({
      url: '/admin/attendee',
      data: JSON.stringify({ name: '张三' })
    }))
  })

  it('GET 请求不做防重复提交校验', () => {
    cache.session.getJSON.mockReturnValue({
      url: '/admin/attendee/list',
      data: '{}',
      time: Date.now()
    })

    expect(() => onRequest()({ headers: {}, method: 'get', url: '/admin/attendee/list' })).not.toThrow()
    expect(cache.session.setJSON).not.toHaveBeenCalled()
  })
})

describe('响应拦截器', () => {
  it('code=200 时直接返回响应体', () => {
    const data = { code: 200, rows: [{ id: 1 }] }
    expect(onResponse()({ data, request: {} })).toEqual(data)
  })

  it('blob/arraybuffer 响应原样返回，不解析业务码', () => {
    expect(onResponse()({ data: 'binary', request: { responseType: 'blob' } })).toBe('binary')
    expect(onResponse()({ data: 'buffer', request: { responseType: 'arraybuffer' } })).toBe('buffer')
  })

  it('code=500 弹出后端文案并 reject', async () => {
    await expect(onResponse()({ data: { code: 500, msg: '签到处理中，请稍后重试' }, request: {} }))
      .rejects.toThrow('签到处理中，请稍后重试')
    expect(ui.message).toHaveBeenCalledWith({ message: '签到处理中，请稍后重试', type: 'error' })
  })

  it('code=601 用警告样式提示并 reject 统一标记', async () => {
    await expect(onResponse()({ data: { code: 601, msg: '请先选择场次' }, request: {} })).rejects.toBe('error')
    expect(ui.message).toHaveBeenCalledWith({ message: '请先选择场次', type: 'warning' })
  })

  it('其他业务码用 Notification 提示，403 使用统一权限文案', async () => {
    await expect(onResponse()({ data: { code: 403, msg: '后端文案' }, request: {} })).rejects.toBe('error')
    expect(ui.notificationError).toHaveBeenCalledWith({ title: '当前操作没有权限' })
  })

  it('401 且用户选择取消：置位过期状态，后续请求不再重复弹窗', async () => {
    await expect(onResponse()({ data: { code: 401 }, request: {} })).rejects.toBe(SESSION_EXPIRED_MESSAGE)
    await tick()

    expect(ui.confirm).toHaveBeenCalledTimes(1)
    expect(isSessionExpired()).toBe(true)

    await expect(onResponse()({ data: { code: 401 }, request: {} })).rejects.toBe(SESSION_EXPIRED_MESSAGE)
    await tick()
    expect(ui.confirm).toHaveBeenCalledTimes(1)
  })

  it('401 且用户选择重新登录：退出并跳转登录页', async () => {
    ui.confirm.mockResolvedValue()

    await expect(onResponse()({ data: { code: 401 }, request: {} })).rejects.toBe(SESSION_EXPIRED_MESSAGE)
    await tick()

    expect(store.dispatch).toHaveBeenCalledWith('LogOut')
    expect(globalThis.location.href).toBe('/admin/index')
    expect(isSessionExpired()).toBe(false)
  })

  it('网络错误统一转成可读文案', async () => {
    const err = new Error('Network Error')

    await expect(onResponseError()(err)).rejects.toBe(err)
    expect(ui.message).toHaveBeenCalledWith({ message: '后端接口连接异常', type: 'error', duration: 5000 })
  })

  it('请求超时文案不暴露底层异常', async () => {
    await expect(onResponseError()(new Error('timeout of 10000ms exceeded'))).rejects.toBeInstanceOf(Error)
    expect(ui.message).toHaveBeenCalledWith({ message: '系统接口请求超时', type: 'error', duration: 5000 })
  })

  it('HTTP 状态码异常带上状态码提示', async () => {
    await expect(onResponseError()(new Error('Request failed with status code 502'))).rejects.toBeInstanceOf(Error)
    expect(ui.message).toHaveBeenCalledWith({ message: '系统接口502异常', type: 'error', duration: 5000 })
  })

  it('同一错误在 15 秒窗口内只弹一次（避免轮询刷屏）', async () => {
    const err = new Error('Request failed with status code 503')

    await expect(onResponseError()(err)).rejects.toBe(err)
    await expect(onResponseError()(err)).rejects.toBe(err)

    expect(ui.message).toHaveBeenCalledTimes(1)
  })
})

describe('通用下载', () => {
  it('blob 有效时保存文件并关闭 loading', async () => {
    const close = vi.fn()
    ui.loadingService.mockReturnValue({ close })
    const blob = new Blob(['x'], { type: 'application/octet-stream' })
    mocks.instance.post.mockResolvedValue(blob)

    await download('/admin/data/export', {}, '签到明细.xlsx')

    expect(mocks.instance.post).toHaveBeenCalledWith('/admin/data/export', {}, expect.objectContaining({
      responseType: 'blob'
    }))
    expect(saveAs).toHaveBeenCalledWith(expect.any(Blob), '签到明细.xlsx')
    expect(close).toHaveBeenCalled()
  })

  it('后端返回 JSON 错误体时提示业务文案，不保存文件', async () => {
    const close = vi.fn()
    ui.loadingService.mockReturnValue({ close })
    mocks.instance.post.mockResolvedValue({
      type: 'application/json',
      text: () => Promise.resolve(JSON.stringify({ code: 500, msg: '导出失败' }))
    })

    await download('/admin/data/export', {}, '签到明细.xlsx')

    expect(saveAs).not.toHaveBeenCalled()
    expect(ui.message.error).toHaveBeenCalledWith('导出失败')
    expect(close).toHaveBeenCalled()
  })

  it('请求失败时给出统一下载失败提示并关闭 loading', async () => {
    const close = vi.fn()
    ui.loadingService.mockReturnValue({ close })
    mocks.instance.post.mockRejectedValue(new Error('Network Error'))

    await download('/admin/data/export', {}, '签到明细.xlsx')

    expect(ui.message.error).toHaveBeenCalledWith('下载文件出现错误，请联系管理员！')
    expect(close).toHaveBeenCalled()
  })
})
