import { beforeEach, describe, expect, it, vi } from 'vitest'

// 指令只依赖 store.getters，整体替换避免拉起 Vuex 单例
vi.mock('@/store', () => ({
  default: { getters: { permissions: [], roles: [] } }
}))

import store from '@/store'
import hasPermi from '../hasPermi'
import hasRole from '../hasRole'

/** 构造带父节点的元素，便于断言「元素被移除」 */
function element() {
  const removeChild = vi.fn()
  const el = { parentNode: { removeChild } }
  return { el, removeChild }
}

beforeEach(() => {
  store.getters.permissions = []
  store.getters.roles = []
})

describe('v-hasPermi 操作权限指令', () => {
  it('拥有任一权限时保留元素', () => {
    store.getters.permissions = ['kcon:draw:draw']
    const { el, removeChild } = element()

    hasPermi.inserted(el, { value: ['kcon:draw:draw'] })

    expect(removeChild).not.toHaveBeenCalled()
  })

  it('通配权限 *:*:* 视为拥有全部权限', () => {
    store.getters.permissions = ['*:*:*']
    const { el, removeChild } = element()

    hasPermi.inserted(el, { value: ['kcon:data:reset'] })

    expect(removeChild).not.toHaveBeenCalled()
  })

  it('缺少权限时从 DOM 移除元素', () => {
    store.getters.permissions = ['kcon:attendee:list']
    const { el, removeChild } = element()

    hasPermi.inserted(el, { value: ['kcon:draw:draw'] })

    expect(removeChild).toHaveBeenCalledWith(el)
  })

  it('未挂载父节点时不抛错（指令可能被单独调用）', () => {
    store.getters.permissions = []

    expect(() => hasPermi.inserted({ parentNode: null }, { value: ['kcon:draw:draw'] })).not.toThrow()
  })

  it('标签值缺失或不是非空数组时直接报错，避免误放行', () => {
    const { el } = element()

    expect(() => hasPermi.inserted(el, { value: [] })).toThrow('请设置操作权限标签值')
    expect(() => hasPermi.inserted(el, { value: 'kcon:draw:draw' })).toThrow('请设置操作权限标签值')
  })
})

describe('v-hasRole 角色指令', () => {
  it('匹配任一角色时保留元素', () => {
    store.getters.roles = ['kcon_draw']
    const { el, removeChild } = element()

    hasRole.inserted(el, { value: ['kcon_draw', 'kcon_verify'] })

    expect(removeChild).not.toHaveBeenCalled()
  })

  it('内置 admin 视为拥有全部角色', () => {
    store.getters.roles = ['admin']
    const { el, removeChild } = element()

    hasRole.inserted(el, { value: ['super_only'] })

    expect(removeChild).not.toHaveBeenCalled()
  })

  it('角色不匹配时移除元素', () => {
    store.getters.roles = ['kcon_checkin']
    const { el, removeChild } = element()

    hasRole.inserted(el, { value: ['admin'] })

    expect(removeChild).toHaveBeenCalledWith(el)
  })

  it('标签值缺失或不是非空数组时直接报错', () => {
    const { el } = element()

    expect(() => hasRole.inserted(el, { value: null })).toThrow('请设置角色权限标签值')
  })
})
