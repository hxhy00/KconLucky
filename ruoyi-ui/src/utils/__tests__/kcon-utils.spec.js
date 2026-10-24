import { beforeEach, describe, expect, it } from 'vitest'
import { buildResyncReportText, fileToken, isValidPreview } from '@/utils/kcon'
import {
  SESSION_EXPIRED_MESSAGE, isSessionExpired, markSessionExpired, resetSessionExpired
} from '@/utils/session-expired'

describe('重算结果文案', () => {
  it('未受影响时只汇报同步人数', () => {
    expect(buildResyncReportText({ synced: 12, locked: [] })).toBe('已同步 12 人。')
    expect(buildResyncReportText()).toBe('已同步 0 人。')
  })

  it('锁定人员按纯文本逐行列出，不自行拼接标签', () => {
    const text = buildResyncReportText({
      synced: 3,
      locked: [
        { attendeeId: 7, name: '<img src=x onerror=alert(1)>', channel: '<b>VIP</b>', signed: 1, won: 0 },
        { attendeeId: 8, name: '李四', channel: '官网', signed: 0, won: 1 }
      ]
    })
    // 不可信内容原样保留、由调用方以纯文本渲染；报告本身不得生成 <br/> 等标签
    expect(text).not.toContain('<br')
    expect(text).not.toContain('<span')
    expect(text).toContain('第7号 <img src=x onerror=alert(1)>（<b>VIP</b>，已签到）')
    expect(text).toContain('第8号 李四（官网，已中奖）')
    expect(text.split('\n')).toHaveLength(4)
  })

  it('超过 20 人时截断并给出剩余人数', () => {
    const locked = Array.from({ length: 23 }, (_, i) => ({
      attendeeId: i + 1, name: `观众${i + 1}`, channel: '官网', signed: 1, won: 0
    }))
    const lines = buildResyncReportText({ synced: 0, locked }).split('\n')
    // 首行 + 提示行 + 20 条明细 + 截断行
    expect(lines).toHaveLength(23)
    expect(lines[22]).toBe('… 另有 3 人')
  })
})

describe('导入预检工具', () => {
  it('文件令牌随大小 / 修改时间 / 文件名变化', () => {
    const a = { size: 100, lastModified: 1, name: 'a.xlsx' }
    expect(fileToken(a)).toBe('100-1-a.xlsx')
    expect(fileToken({ ...a, lastModified: 2 })).not.toBe(fileToken(a))
    expect(fileToken({ ...a, size: 101 })).not.toBe(fileToken(a))
    expect(fileToken(null)).toBe('')
  })

  it('预检结果必须是完整结构', () => {
    expect(isValidPreview({ level: 'OK', totalRows: 0 })).toBe(true)
    expect(isValidPreview({ level: 'WARN', totalRows: 3 })).toBe(true)
    expect(isValidPreview({ level: 'BLOCK', totalRows: 5 })).toBe(true)

    expect(isValidPreview(null)).toBe(false)
    expect(isValidPreview({})).toBe(false)
    expect(isValidPreview({ level: 'ok', totalRows: 1 })).toBe(false)
    expect(isValidPreview({ level: 'OK', totalRows: '1' })).toBe(false)
  })
})

describe('会话过期状态', () => {
  beforeEach(() => resetSessionExpired())

  it('默认未过期，置位与复位成对生效', () => {
    expect(isSessionExpired()).toBe(false)
    markSessionExpired()
    expect(isSessionExpired()).toBe(true)
    resetSessionExpired()
    expect(isSessionExpired()).toBe(false)
    expect(SESSION_EXPIRED_MESSAGE).toBe('登录已过期，请重新登录')
  })
})
