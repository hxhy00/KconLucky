import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

// 页面逻辑与接口解耦：整体替换 api，专注验证步骤流转与错误分支
vi.mock('../api', () => ({
  queryAttendee: vi.fn(),
  submitCheckin: vi.fn(),
  lookup: vi.fn()
}))

import App from '../App.vue'
import { lookup, queryAttendee, submitCheckin } from '../api'

const PHONE = '13800000001'
const OTHER_PHONE = '13900000002'

const ok = data => ({ code: 200, data })

const attendee = () => ({
  id: 7,
  name: '张三',
  phoneMask: '138****0001',
  company: 'ACME',
  position: '工程师'
})

const checkinResult = () => ({
  signed: true,
  portNo: 4321,
  checkinTime: '2026-10-30 10:00:00',
  repeat: false,
  name: '张三',
  ticketType: '午餐'
})

let wrapper = null

/** 按按钮文案取元素：页面同一时刻只渲染一个主操作按钮 */
function button(text) {
  const el = wrapper.findAll('button').find(b => b.text().includes(text))
  if (!el) throw new Error(`未找到按钮：${text}`)
  return el
}

async function setPhone(value) {
  await wrapper.find('#phone').setValue(value)
}

async function click(text) {
  await button(text).trigger('click')
  await flushPromises()
}

async function queryToConfirm() {
  queryAttendee.mockResolvedValue(ok({ signed: false, attendee: attendee() }))
  await setPhone(PHONE)
  await click('查询参会信息')
}

beforeEach(() => {
  queryAttendee.mockReset()
  submitCheckin.mockReset()
  lookup.mockReset()
  // jsdom 未实现滚动 API，页面会调用，直接打桩
  window.scrollTo = vi.fn()
  Element.prototype.scrollIntoView = vi.fn()
})

afterEach(() => {
  if (wrapper) wrapper.unmount()
  wrapper = null
})

describe('手机号输入', () => {
  it('位数不足时给出还差几位，并禁用查询按钮', async () => {
    wrapper = mount(App)
    await setPhone('138')

    expect(wrapper.text()).toContain('还需输入 8 位')
    expect(button('查询参会信息').attributes('disabled')).toBeDefined()
  })

  it('粘贴带分隔符或全角数字时自动归一化为 11 位半角', async () => {
    wrapper = mount(App)
    await setPhone('１３８-００００ ０００１')

    expect(wrapper.find('#phone').element.value).toBe(PHONE)
    expect(wrapper.text()).toContain('手机号已完整，可以查询')
  })

  it('满 11 位但号段非法时提示格式错误且不发请求', async () => {
    wrapper = mount(App)
    await setPhone('12345678901')
    await click('查询参会信息')

    expect(wrapper.text()).toContain('手机号格式不正确，请检查后重试')
    expect(queryAttendee).not.toHaveBeenCalled()
  })
})

describe('查询参会人', () => {
  it('查询成功进入确认页，只展示掩码手机号', async () => {
    wrapper = mount(App)
    await queryToConfirm()

    expect(queryAttendee).toHaveBeenCalledWith(PHONE)
    expect(wrapper.text()).toContain('核对参会信息')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('138****0001')
    expect(wrapper.text()).toContain('ACME')
    expect(wrapper.text()).not.toContain(PHONE)
  })

  it('业务失败展示后端文案并停留在输入页', async () => {
    wrapper = mount(App)
    queryAttendee.mockResolvedValue({ code: 500, msg: '查无此人，请联系现场工作人员' })
    await setPhone(PHONE)
    await click('查询参会信息')

    expect(wrapper.text()).toContain('查无此人，请联系现场工作人员')
    expect(wrapper.find('#phone').exists()).toBe(true)
  })

  it('响应缺少 attendee 时给出兜底提示', async () => {
    wrapper = mount(App)
    queryAttendee.mockResolvedValue(ok({ signed: false }))
    await setPhone(PHONE)
    await click('查询参会信息')

    expect(wrapper.text()).toContain('查无此人，请联系现场工作人员')
  })

  it('网络异常直接展示接口层的可读文案', async () => {
    wrapper = mount(App)
    const err = new Error('请求超时')
    err.networkError = true
    queryAttendee.mockRejectedValue(err)
    await setPhone(PHONE)
    await click('查询参会信息')

    expect(wrapper.text()).toContain('请求超时')
  })

  it('已签到用户改走只读回查，不调用签到提交接口', async () => {
    wrapper = mount(App)
    queryAttendee.mockResolvedValue(ok({ signed: true, attendee: attendee() }))
    lookup.mockResolvedValue(ok({
      signed: true, portNo: 4321, name: '张三', ticketType: '午餐',
      checkinTime: '2026-10-30 10:00:00'
    }))

    await setPhone(PHONE)
    await click('查询参会信息')

    expect(lookup).toHaveBeenCalledWith(PHONE)
    expect(submitCheckin).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('已完成签到')
    expect(wrapper.text()).toContain('4321')
  })

  it('已签到但回查不到记录时展示错误，不进入成功页', async () => {
    wrapper = mount(App)
    queryAttendee.mockResolvedValue(ok({ signed: true, attendee: attendee() }))
    lookup.mockResolvedValue(ok({ signed: false, msg: '该手机号尚未签到' }))

    await setPhone(PHONE)
    await click('查询参会信息')

    expect(wrapper.text()).toContain('该手机号尚未签到')
    expect(wrapper.text()).not.toContain('已完成签到')
  })

  it('请求途中改手机号时，旧结果作废不展示上一位的信息', async () => {
    wrapper = mount(App)
    let resolveQuery
    queryAttendee.mockImplementation(() => new Promise(resolve => { resolveQuery = resolve }))

    await setPhone(PHONE)
    await button('查询参会信息').trigger('click')
    await setPhone(OTHER_PHONE)

    resolveQuery(ok({ signed: false, attendee: attendee() }))
    await flushPromises()

    expect(wrapper.text()).not.toContain('张三')
    expect(wrapper.find('#phone').exists()).toBe(true)
  })
})

describe('确认签到', () => {
  it('提交成功进入凭证页，带上 attendeeId 与完整手机号', async () => {
    wrapper = mount(App)
    await queryToConfirm()
    submitCheckin.mockResolvedValue(ok(checkinResult()))

    await click('确认签到')

    expect(submitCheckin).toHaveBeenCalledWith(7, PHONE)
    expect(wrapper.text()).toContain('签到成功')
    expect(wrapper.text()).toContain('4321')
    expect(wrapper.text()).toContain('午餐')
    expect(wrapper.text()).toContain('请截屏保存')
  })

  it('重复签到时展示「已完成签到」与原端口号', async () => {
    wrapper = mount(App)
    await queryToConfirm()
    submitCheckin.mockResolvedValue(ok({ ...checkinResult(), repeat: true }))

    await click('确认签到')

    expect(wrapper.text()).toContain('已完成签到')
    expect(wrapper.text()).toContain('4321')
  })

  it('code=200 但缺少端口号时提示读取失败，不切成功页（避免白屏）', async () => {
    wrapper = mount(App)
    await queryToConfirm()
    submitCheckin.mockResolvedValue(ok({ signed: true, name: '张三' }))

    await click('确认签到')

    expect(wrapper.text()).toContain('签到信息读取失败，请联系现场工作人员')
    expect(wrapper.text()).toContain('核对参会信息')
  })

  it('业务失败展示后端文案', async () => {
    wrapper = mount(App)
    await queryToConfirm()
    submitCheckin.mockResolvedValue({ code: 500, msg: '签到处理中，请稍后重试' })

    await click('确认签到')

    expect(wrapper.text()).toContain('签到处理中，请稍后重试')
  })

  it('网络异常展示可重试文案，停留在确认页', async () => {
    wrapper = mount(App)
    await queryToConfirm()
    submitCheckin.mockRejectedValue(new Error('网络异常'))

    await click('确认签到')

    expect(wrapper.text()).toContain('网络异常')
    expect(wrapper.text()).toContain('核对参会信息')
  })

  it('提交在途时按钮进入 loading，不重复提交', async () => {
    wrapper = mount(App)
    await queryToConfirm()
    submitCheckin.mockImplementation(() => new Promise(() => {}))

    await button('确认签到').trigger('click')

    expect(button('签到中…').attributes('disabled')).toBeDefined()
    await button('签到中…').trigger('click')
    expect(submitCheckin).toHaveBeenCalledTimes(1)
  })

  it('「信息有误，重新输入」回到输入页并清空上一次输入', async () => {
    wrapper = mount(App)
    await queryToConfirm()

    await click('信息有误，重新输入')

    expect(wrapper.find('#phone').element.value).toBe('')
    expect(wrapper.text()).not.toContain('张三')
  })
})

describe('找回端口号', () => {
  it('完整手机号回查成功时展示端口号与签到时间', async () => {
    wrapper = mount(App)
    lookup.mockResolvedValue(ok({
      signed: true, portNo: 4321, name: '张三', ticketType: '午餐',
      checkinTime: '2026-10-30 10:00:00'
    }))

    await setPhone(PHONE)
    await click('找回端口号')

    expect(lookup).toHaveBeenCalledWith(PHONE)
    expect(wrapper.text()).toContain('签到凭证')
    expect(wrapper.text()).toContain('4321')
    expect(wrapper.text()).toContain('2026-10-30 10:00:00')
  })

  it('未签到时展示后端提示，不展示凭证卡片', async () => {
    wrapper = mount(App)
    lookup.mockResolvedValue(ok({ signed: false, msg: '该手机号尚未签到' }))

    await setPhone(PHONE)
    await click('找回端口号')

    expect(wrapper.text()).toContain('该手机号尚未签到')
    expect(wrapper.text()).not.toContain('签到凭证')
  })

  it('网络异常时展示接口层文案', async () => {
    wrapper = mount(App)
    lookup.mockRejectedValue(new Error('服务异常（HTTP 502）'))

    await setPhone(PHONE)
    await click('找回端口号')

    expect(wrapper.text()).toContain('服务异常（HTTP 502）')
  })

  it('号码不足 11 位时按钮不可用', async () => {
    wrapper = mount(App)
    await setPhone('1380000')

    expect(button('找回端口号').attributes('disabled')).toBeDefined()
  })

  it('改输入清除上一次找回结果，避免显示旧号码的端口号', async () => {
    wrapper = mount(App)
    lookup.mockResolvedValue(ok({
      signed: true, portNo: 4321, name: '张三', checkinTime: '2026-10-30 10:00:00'
    }))

    await setPhone(PHONE)
    await click('找回端口号')
    expect(wrapper.text()).toContain('4321')

    await setPhone(OTHER_PHONE)
    await flushPromises()

    expect(wrapper.text()).not.toContain('4321')
    expect(wrapper.text()).not.toContain('签到凭证')
  })
})
