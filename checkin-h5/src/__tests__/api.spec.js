import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { queryAttendee, submitCheckin, lookup } from '../api'

/** 断言最近一次 fetch 调用的 URL 与选项 */
function lastFetch() {
  const calls = global.fetch.mock.calls
  return calls[calls.length - 1]
}

beforeEach(() => {
  global.fetch = vi.fn(() => Promise.resolve({
    ok: true,
    status: 200,
    headers: { get: (name) => (name.toLowerCase() === 'content-type' ? 'application/json;charset=UTF-8' : null) },
    json: () => Promise.resolve({ code: 200, data: { signed: true } })
  }))
})

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('签到端请求封装', () => {
  it('查询参会人：POST JSON，手机号两端空格被 trim', async () => {
    const data = await queryAttendee(' 13800000001 ')
    expect(data).toEqual({ code: 200, data: { signed: true } })

    const [url, options] = lastFetch()
    expect(url).toBe('/dev-api/app/checkin/query')
    expect(options.method).toBe('POST')
    expect(options.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(JSON.parse(options.body)).toEqual({ keyword: '13800000001' })
    expect(options.signal).toBeInstanceOf(AbortSignal)
  })

  it('确认签到：attendeeId 保持数字，phone 被 trim', async () => {
    await submitCheckin(7, ' 13800000001 ')
    const [url, options] = lastFetch()
    expect(url).toBe('/dev-api/app/checkin/submit')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({ attendeeId: 7, phone: '13800000001' })
  })

  it('找回端口号：POST JSON，手机号放请求体不做 URL 编码', async () => {
    await lookup(' 张 三&1 ')
    const [url, options] = lastFetch()
    expect(url).toBe('/dev-api/app/checkin/lookup')
    expect(options.method).toBe('POST')
    expect(options.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(JSON.parse(options.body)).toEqual({ keyword: '张 三&1' })
  })

  it('网络异常统一转成可提示的错误', async () => {
    global.fetch = vi.fn(() => Promise.reject(new Error('boom')))
    await expect(queryAttendee('13800000001')).rejects.toMatchObject({
      message: '网络异常',
      networkError: true
    })
  })

  it('非 2xx 响应按 HTTP 状态码提示，而不是笼统的网络异常', async () => {
    global.fetch = vi.fn(() => Promise.resolve({
      ok: false,
      status: 502,
      headers: { get: () => 'text/html' },
      json: () => Promise.resolve({})
    }))
    await expect(queryAttendee('13800000001')).rejects.toMatchObject({
      message: '服务异常（HTTP 502）',
      networkError: true
    })
  })

  it('429 限流给出可操作提示，且不归为网络异常', async () => {
    global.fetch = vi.fn(() => Promise.resolve({
      ok: false,
      status: 429,
      headers: { get: () => 'application/json' },
      json: () => Promise.resolve({})
    }))
    await expect(queryAttendee('13800000001')).rejects.toMatchObject({
      message: '操作过于频繁，请稍后再试'
    })
    await expect(queryAttendee('13800000001')).rejects.not.toMatchObject({
      networkError: true
    })
  })

  it('响应不是 JSON（如网关错误页）时给出服务异常提示', async () => {
    global.fetch = vi.fn(() => Promise.resolve({
      ok: true,
      status: 200,
      headers: { get: () => 'text/html; charset=utf-8' },
      json: () => { throw new SyntaxError('Unexpected token <') }
    }))
    await expect(lookup('13800000001')).rejects.toMatchObject({
      message: '服务异常，请稍后重试',
      networkError: true
    })
  })

  it('超过 12 秒未返回则中断并提示超时', async () => {
    vi.useFakeTimers()
    global.fetch = vi.fn((url, options) => new Promise((resolve, reject) => {
      options.signal.addEventListener('abort', () => {
        const err = new Error('aborted')
        err.name = 'AbortError'
        reject(err)
      })
    }))

    const pending = queryAttendee('13800000001')
    const assertion = expect(pending).rejects.toMatchObject({
      message: '请求超时',
      networkError: true
    })
    await vi.advanceTimersByTimeAsync(12001)
    await assertion
  })
})
