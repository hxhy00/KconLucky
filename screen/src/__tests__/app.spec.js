import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

// 大屏只依赖 fetchStatus 轮询，整体替换 api 模块即可完全控制服务端快照
vi.mock('../api', () => ({ fetchStatus: vi.fn() }))

import App from '../App.vue'
import { fetchStatus } from '../api'

/** 一次完整的 /app/screen/status 快照（字段与后端 screenStatus 对齐） */
function statusData(overrides = {}) {
  return {
    sessionCode: 'AM',
    sessionName: '上午场',
    poolCount: 5,
    tiers: [{ id: 3, tierName: '三等奖', quantity: 5, drawnCount: 0 }],
    totalQuantity: 5,
    totalDrawn: 0,
    currentTier: { id: 3, tierName: '三等奖', quantity: 5, drawnCount: 0 },
    sponsors: [],
    version: 'AM|v1',
    ...overrides
  }
}

function lastBatch(batchKey, winners, tierName = '三等奖', shortfall = 0) {
  return { batchKey, winners, tierName, shortfall }
}

const ok = data => ({ code: 200, data })
const winner = (winnerId, portNo, name = '张三') => ({ winnerId, portNo, name })

let wrapper = null

/** 推进轮询与揭晓动画的假时钟，并冲掉随之产生的微任务 */
const advance = ms => vi.advanceTimersByTimeAsync(ms)

async function mountApp(initial = statusData()) {
  fetchStatus.mockResolvedValueOnce(ok(initial))
  wrapper = mount(App)
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.useFakeTimers()
  fetchStatus.mockReset()
})

afterEach(() => {
  if (wrapper) wrapper.unmount()
  wrapper = null
  vi.useRealTimers()
})

describe('首次加载', () => {
  it('历史批次只记录不展示，刷新后不会凭空重播上一轮揭晓', async () => {
    const data = statusData({
      lastBatch: lastBatch('3@2026-10-23 10:00:00@2', [winner(11, 4321), winner(12, 5678)])
    })
    const w = await mountApp(data)

    expect(w.text()).toContain('准 备 抽 奖')
    expect(w.text()).not.toContain('4321')
    expect(w.text()).not.toContain('恭 喜 中 奖')

    // 下一轮仍是同一批次：继续待机，不补播
    fetchStatus.mockResolvedValue(ok(data))
    await advance(1500)
    expect(w.text()).toContain('准 备 抽 奖')
  })

  it('抽奖池为空时直接进入空池页', async () => {
    const w = await mountApp(statusData({ poolCount: 0, currentTier: null, tiers: [] }))

    expect(w.text()).toContain('请先让观众签到')
    expect(w.text()).toContain('当前抽奖池为空')
  })

  it('空池补充签到后回到待机页', async () => {
    await mountApp(statusData({ poolCount: 0, currentTier: null, tiers: [] }))

    fetchStatus.mockResolvedValue(ok(statusData({ poolCount: 3 })))
    await advance(1500)

    expect(wrapper.text()).toContain('准 备 抽 奖')
    expect(wrapper.text()).not.toContain('请先让观众签到')
  })
})

describe('轮询发现新批次', () => {
  it('逐个揭晓后停在汇总页，只展示端口号不展示姓名', async () => {
    const w = await mountApp(statusData())

    fetchStatus.mockResolvedValue(ok(statusData({
      poolCount: 4,
      lastBatch: lastBatch('3@2026-10-23 10:00:00@1', [winner(11, 4321)])
    })))
    await advance(1500)

    // 进入揭晓阶段：号码在滚动，尚未定格
    expect(w.text()).toContain('恭 喜 中 奖')
    expect(w.text()).toContain('滚 动 中')

    await advance(5000)

    expect(w.text()).toContain('三等奖')
    expect(w.text()).toContain('4321')
    // 观众隐私：姓名不上大屏
    expect(w.text()).not.toContain('张三')
    expect(w.text()).not.toContain('滚 动 中')
  })

  it('池子不足时在汇总页提示还差几位', async () => {
    const w = await mountApp(statusData())

    fetchStatus.mockResolvedValue(ok(statusData({
      lastBatch: lastBatch('3@2026-10-23 10:00:00@1', [winner(11, 4321)], '三等奖', 2)
    })))
    await advance(1500)
    await advance(5000)

    expect(w.text()).toContain('本次只抽出 1 位')
    expect(w.text()).toContain('还差 2 位')
  })

  it('同一次抽奖人数减少（作废）时汇总页就地同步，不重播动画', async () => {
    const w = await mountApp(statusData())

    fetchStatus.mockResolvedValue(ok(statusData({
      lastBatch: lastBatch('3@2026-10-23 10:00:00@2', [winner(11, 4321), winner(12, 5678)])
    })))
    await advance(1500)
    await advance(8000)
    expect(w.text()).toContain('4321')
    expect(w.text()).toContain('5678')

    // 同一批被作废一人：batchKey 前缀不变、人数变成 1、shortfall=1
    fetchStatus.mockResolvedValue(ok(statusData({
      lastBatch: lastBatch('3@2026-10-23 10:00:00@1', [winner(11, 4321)], '三等奖', 1)
    })))
    await advance(1500)

    expect(w.text()).toContain('4321')
    expect(w.text()).not.toContain('5678')
    expect(w.text()).toContain('还差 1 位')
    expect(w.text()).not.toContain('滚 动 中')
  })

  it('lastBatch 退化成更早批次时不重播（撤回最近一档的兜底）', async () => {
    const w = await mountApp(statusData({
      lastBatch: lastBatch('3@2026-10-23 12:00:00@1', [winner(11, 4321)])
    }))

    fetchStatus.mockResolvedValue(ok(statusData({
      lastBatch: lastBatch('3@2026-10-23 10:00:00@2', [winner(21, 1111), winner(22, 2222)])
    })))
    await advance(1500)

    expect(w.text()).toContain('准 备 抽 奖')
    expect(w.text()).not.toContain('1111')
    expect(w.text()).not.toContain('滚 动 中')
  })

  it('整批被作废后清空汇总页并回到待机', async () => {
    const w = await mountApp(statusData())

    fetchStatus.mockResolvedValue(ok(statusData({
      lastBatch: lastBatch('3@2026-10-23 10:00:00@1', [winner(11, 4321)])
    })))
    await advance(1500)
    await advance(5000)
    expect(w.text()).toContain('4321')

    fetchStatus.mockResolvedValue(ok(statusData()))
    await advance(1500)

    expect(w.text()).not.toContain('4321')
    expect(w.text()).toContain('准 备 抽 奖')
  })
})

describe('切换场次', () => {
  it('切到已有结果的场次时直接展示其名单，不重播上一场动画', async () => {
    const w = await mountApp(statusData())

    fetchStatus.mockResolvedValue(ok(statusData({
      lastBatch: lastBatch('3@2026-10-23 10:00:00@1', [winner(11, 4321)])
    })))
    await advance(1500)
    await advance(5000)
    expect(w.text()).toContain('4321')

    fetchStatus.mockResolvedValue(ok(statusData({
      sessionCode: 'PM',
      sessionName: '下午场',
      lastBatch: lastBatch('4@2026-10-23 15:00:00@1', [winner(31, 9999)])
    })))
    await advance(1500)

    expect(w.text()).toContain('下午场')
    expect(w.text()).toContain('9999')
    expect(w.text()).not.toContain('4321')
    expect(w.text()).not.toContain('滚 动 中')
  })

  it('切到尚无结果的场次时清空上一场名单并回到待机', async () => {
    const w = await mountApp(statusData())

    fetchStatus.mockResolvedValue(ok(statusData({
      lastBatch: lastBatch('3@2026-10-23 10:00:00@1', [winner(11, 4321)])
    })))
    await advance(1500)
    await advance(5000)
    expect(w.text()).toContain('4321')

    fetchStatus.mockResolvedValue(ok(statusData({ sessionCode: 'PM', sessionName: '下午场' })))
    await advance(1500)

    expect(w.text()).toContain('下午场')
    expect(w.text()).not.toContain('4321')
    expect(w.text()).toContain('准 备 抽 奖')
  })
})

describe('轮询容错', () => {
  it('连续三次失败才亮离线遮罩，恢复后自动消失', async () => {
    fetchStatus.mockRejectedValue(new Error('boom'))
    wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).not.toContain('与后端失去连接')

    await advance(1500)
    await advance(6000)

    expect(wrapper.text()).toContain('与后端失去连接')

    fetchStatus.mockResolvedValue(ok(statusData()))
    await advance(12000)

    expect(wrapper.text()).not.toContain('与后端失去连接')
    expect(wrapper.text()).toContain('准 备 抽 奖')
  })

  it('限流 429 不判定为断连', async () => {
    await mountApp(statusData())

    fetchStatus.mockResolvedValue({ code: 429, msg: '操作过于频繁' })
    await advance(1500)
    await advance(3000)
    await advance(6000)

    expect(wrapper.text()).not.toContain('与后端失去连接')
  })

  it('响应畸形（无 code）与业务失败同口径计入失败连击', async () => {
    await mountApp(statusData())

    fetchStatus.mockResolvedValue({})
    await advance(1500)
    await advance(3000)
    await advance(6000)

    expect(wrapper.text()).toContain('与后端失去连接')
  })

  it('增量轮询带上上次 version，unchanged 时整份快照不重渲染', async () => {
    const w = await mountApp(statusData({ version: 'AM|v7' }))
    expect(fetchStatus).toHaveBeenLastCalledWith(null)

    fetchStatus.mockResolvedValue(ok({ unchanged: true, version: 'AM|v7' }))
    await advance(1500)

    expect(fetchStatus).toHaveBeenLastCalledWith('AM|v7')
    expect(w.text()).toContain('准 备 抽 奖')
  })

  it('请求在途时不叠加新轮询', async () => {
    fetchStatus.mockImplementation(() => new Promise(() => {}))
    wrapper = mount(App)
    await flushPromises()

    await advance(10000)

    expect(fetchStatus).toHaveBeenCalledTimes(1)
  })
})

describe('奖项进度展示', () => {
  it('展示各档位已抽份数、当前档位剩余份数与百分比', async () => {
    const w = await mountApp(statusData({
      tiers: [
        { id: 3, tierName: '三等奖', quantity: 3, drawnCount: 1 },
        { id: 4, tierName: '一等奖', quantity: 4, drawnCount: 2 }
      ],
      totalQuantity: 7,
      totalDrawn: 3,
      currentTier: { id: 4, tierName: '一等奖', quantity: 4, drawnCount: 2 }
    }))

    expect(w.text()).toContain('共 7 份')
    expect(w.text()).toContain('已抽出 3 份')
    expect(w.text()).toContain('1 / 3')
    expect(w.text()).toContain('2 / 4')
    expect(w.text()).toContain('还剩 2 份')

    const bars = w.findAll('.h-2 > div')
    expect(bars.map(b => b.attributes('style'))).toEqual([
      expect.stringContaining('width: 33%'),
      expect.stringContaining('width: 50%')
    ])
  })

  it('份数为 0 时不出现除零导致的 NaN 宽度', async () => {
    const w = await mountApp(statusData({
      tiers: [{ id: 3, tierName: '三等奖', quantity: 0, drawnCount: 0 }],
      totalQuantity: 0,
      totalDrawn: 0,
      currentTier: null
    }))

    expect(w.find('.h-2 > div').attributes('style')).toContain('width: 0%')
    expect(w.text()).not.toContain('NaN')
  })
})
