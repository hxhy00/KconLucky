/**
 * KCon 管理端通用纯函数
 *
 * 抽出来单独放这里，便于在 Vitest（node 环境，不挂载组件）里直接单测。
 */

/**
 * 「重算存量名单」结果 → 纯文本报告。
 *
 * 必须保持纯文本：姓名、渠道来自 Excel 导入，属于不可信输入，
 * 一旦拼进 HTML 再交给 dangerouslyUseHTMLString，就是存储型 XSS。
 * 多行展示靠调用方的 white-space: pre-line。
 */
export function buildResyncReportText(data) {
  const d = data || {}
  const locked = Array.isArray(d.locked) ? d.locked : []
  const lines = [`已同步 ${d.synced || 0} 人。`]
  if (locked.length) {
    lines.push(`以下 ${locked.length} 人已签到或已中奖，为避免状态矛盾未做修改，请人工核对：`)
    locked.slice(0, 20).forEach(p => {
      const reasons = []
      if (p.signed === 1) reasons.push('已签到')
      if (p.won === 1) reasons.push('已中奖')
      lines.push(`第${p.attendeeId}号 ${p.name}（${p.channel}，${reasons.join('、')}）`)
    })
    if (locked.length > 20) lines.push(`… 另有 ${locked.length - 20} 人`)
  }
  return lines.join('\n')
}

/**
 * 导入预检的文件令牌：用于确认「预检时的文件」与「现在要提交的文件」是同一个。
 * 同名文件重新选择时 lastModified 会变，能识别出来。
 */
export function fileToken(file) {
  if (!file) return ''
  return `${file.size || 0}-${file.lastModified || 0}-${file.name || ''}`
}

const PREVIEW_LEVELS = ['OK', 'WARN', 'BLOCK']

/**
 * 校验预检结果结构是否完整。
 * 曾经把 `res.data || {}` 直接当成成功结果：空对象 = level 未定义、
 * totalRows 未定义，界面显示成「OK / 0 行」，点导入后才发现不对劲。
 */
export function isValidPreview(data) {
  if (!data || typeof data !== 'object') return false
  if (!PREVIEW_LEVELS.includes(data.level)) return false
  return typeof data.totalRows === 'number'
}
