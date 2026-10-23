import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { queryAttendee, submitCheckin, lookup } from '../api'

/** 断言最近一次 fetch 调用的 URL 与选项 */
function lastFetch() {
  const calls = global.fetch.mock.calls
  return calls[calls.length - 1]
}

beforeEach(() => {
  global.fetch = vi.fn(() => Promise.resolve({
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

  it('找回端口号：GET，keyword 做 URL 编码', async () => {
    await lookup('张 三&1')
    const [url, options] = lastFetch()
    expect(url).toBe('/dev-api/app/checkin/lookup?keyword=' + encodeURIComponent('张 三&1'))
    expect(options.method).toBeUndefined()
    expect(options.body).toBeUndefined()
  })

  it('网络异常统一转成可提示的错误', async () => {
    global.fetch = vi.fn(() => Promise.reject(new Error('boom')))
    await expect(queryAttendee('13800000001')).rejects.toMatchObject({
      message: '网络异常',
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
