<template>
  <div class="h-full flex flex-col select-none pattern-grid">
    <!-- 顶部：场次。统计（今日已签到/可抽号数/已中奖）已按需求整体移除：
         不让全场观众实时盯运营数字，数据只在管理端控制台可见 -->
    <header class="flex items-center justify-between px-10 py-6">
      <div class="flex items-center gap-4">
        <!-- 图形与文字分离：图形用透明 PNG（深色底不再出现白方块），文字用真实文本 -->
        <div class="flex items-center gap-2">
          <img :src="logo" class="h-10" alt="KCon" />
          <span class="text-xl font-black tracking-wide text-slate-200">KCon 2026</span>
        </div>
        <!-- 场次只读展示：大屏不提供切换入口，切场次由管理端操作 -->
        <span class="px-4 py-1.5 rounded-full text-sm bg-brand/10 text-brand border border-brand/30">
          {{ status.sessionName || '—' }}
        </span>
      </div>
    </header>

    <!-- 主体 -->
    <main class="flex-1 flex flex-col items-center justify-center px-10 relative">
      <!-- S4 空池 -->
      <template v-if="phase === 'empty'">
        <div class="text-6xl font-black text-slate-600">请先让观众签到</div>
        <div class="mt-6 text-slate-500">当前抽奖池为空</div>
      </template>

      <!-- 揭晓中：一个人一个人的端口号滚出来 -->
      <template v-else-if="phase === 'revealing'">
        <div class="text-lg text-slate-400 mb-2 tracking-[0.3em]">
          {{ revealList.length > 1 ? `第 ${revealIndex + 1} / ${revealList.length} 位` : '恭 喜 中 奖' }}
        </div>
        <div class="text-2xl text-slate-500 tracking-[0.3em] mb-6">{{ resultTier }}</div>

        <!-- 未定格时只滚号码；定格后号码有入场缩放，姓名才出现，避免提前剧透 -->
        <div class="rand-num" :class="revealSettled ? 'pop-in text-gold glow-gold' : 'text-brand'">
          {{ displayNo }}
        </div>

        <!-- 未定格时提示在滚动；定格后不再额外渲染（姓名已按需求不上大屏，号码本身即结果） -->
        <div v-if="!revealSettled" class="mt-10 text-2xl text-slate-700 tracking-[0.3em]">滚 动 中</div>
      </template>

      <!-- 汇总：这一档抽出的全部人，主持人照着一屏念完 -->
      <template v-else-if="phase === 'result'">
        <!-- 庆祝动效①：全屏纸屑（纯 CSS，无外部依赖），颜色/形状/下落方式按奖项档位差异化 -->
        <div class="confetti-layer" aria-hidden="true">
          <span
            v-for="c in confetti"
            :key="confettiSeq + '-' + c.id"
            class="confetti"
            :class="c.shape"
            :style="c.style"
          ></span>
        </div>

        <div class="text-lg text-slate-400 tracking-[0.3em]">恭 喜 中 奖</div>
        <div class="text-4xl font-black text-gold mt-2 mb-8">{{ resultTier }}</div>

        <div class="winner-grid">
          <div v-for="w in resultList" :key="w.winnerId" class="winner-cell">
            <!-- 只展示端口号：姓名不上大屏（观众隐私）。去掉姓名后单列号码加大保持可读 -->
            <div class="winner-port text-5xl md:text-6xl">{{ w.portNo }}</div>
          </div>
        </div>

        <div v-if="shortfall > 0" class="mt-8 text-xl text-amber-300">
          当前可抽号数不足，本次只抽出 {{ resultList.length }} 位，还差 {{ shortfall }} 位
          （请在抽奖控制台让更多观众签到后继续抽）
        </div>

      </template>

      <!-- 待机：大屏只展示，等待管理端「抽奖控制台」发起。
           rolling 阶段已不可达（不再有本地 doDraw），统一显示「准备抽奖」+ 灰色占位号 -->
      <template v-else>
        <div class="text-lg text-slate-400 mb-6 tracking-[0.3em]">准 备 抽 奖</div>
        <div class="rand-num glow text-slate-700">
          {{ displayNo }}
        </div>
        <div v-if="status.currentTier" class="mt-8 text-xl text-slate-400">
          当前奖项：
          <span class="text-gold font-bold">{{ status.currentTier.tierName }}</span>
          <span class="text-slate-500 ml-2">
            还剩 {{ remainingOfCurrent }} 份
          </span>
        </div>

        <!-- 大屏只展示，不再提供「开始抽奖」按钮。抽奖由管理端「抽奖控制台」发起，
             大屏靠轮询被动感知新批次并自动播放滚动揭晓动画 -->
        <div class="mt-12 text-sm text-slate-500 tracking-[0.3em]">
          {{ poolEmpty ? '当 前 无 可 抽 号 数 ， 请 在 控 制 台 确 认' : '等 待 抽 奖 控 制 台 发 起 …' }}
        </div>
      </template>
    </main>

    <!-- 奖项进度：让全场一眼看出「每个奖还剩几份」，主持人不用口头解说 -->
    <section class="px-10 pb-5" v-if="tiers.length">
      <div class="flex items-end justify-between gap-4 mb-3">
        <span class="text-sm text-slate-500 tracking-[0.25em]">奖 品 进 度</span>
        <span class="text-sm text-slate-500 tabular-nums">
          共 {{ totalQuantity }} 份 · 已抽出 {{ totalDrawn }} 份
        </span>
      </div>
      <div class="flex gap-4">
        <div v-for="t in tiers" :key="t.id" class="flex-1">
          <div class="flex items-baseline justify-between mb-2">
            <span class="text-lg font-bold" :class="isCurrentTier(t) ? 'text-gold' : 'text-slate-300'">
              {{ t.tierName }}
            </span>
            <span class="text-sm tabular-nums text-slate-400">{{ t.drawnCount }} / {{ t.quantity }}</span>
          </div>
          <div class="h-2 rounded-full bg-white/[0.08] overflow-hidden">
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="isCurrentTier(t) ? 'bg-gold' : 'bg-brand'"
              :style="{ width: tierPercent(t) + '%' }"
            ></div>
          </div>
        </div>
      </div>
    </section>

    <!-- 赞助商：黑客大会惯例，也让投屏画面完整。未配置时不占位 -->
    <footer class="px-10 py-4 border-t border-white/5" v-if="sponsors.length">
      <div class="flex items-center justify-center gap-10 flex-wrap">
        <span class="text-xs text-slate-600 tracking-[0.2em]">支 持 单 位</span>
        <span v-for="(s, i) in sponsors" :key="i"
              class="text-xl font-bold text-slate-400 tracking-wide">
          {{ s }}
        </span>
      </div>
    </footer>

    <!-- 与后端失去连接：连续失败才显示。以前断网大屏毫无提示，
         头部数字停在旧值，看起来像「还没人来签到」而不是「大屏连不上」 -->
    <div v-if="offline" class="offline-mask">
      <div class="text-4xl font-black text-rose-300">与后端失去连接</div>
      <div class="mt-5 text-lg text-slate-300">大屏正在自动重试，请检查大屏与后端之间的网络</div>
      <div class="mt-2 text-sm text-slate-500">画面上的数字可能已经过期</div>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
// 大屏纯只读：fetchStatus 轮询被动感知场次 / 批次变化。
// 抽奖、作废、切场次均已收归管理端，这里不再导入 draw / voidWinner / switchSession。
import { fetchStatus } from './api'
import logo from './assets/kcon-mark.png'

const status = ref({})
// idle 待机 | revealing 逐个揭晓中 | result 汇总页 | empty 池空。
// 不再有 rolling：大屏不主动抽奖，端口号滚动只发生在被动揭晓路径里。
const phase = ref('idle')
const displayNo = ref('00000')

// 一次点击抽出整档的人，所以「揭晓」是一段过程而不是一帧：
//   revealList    本次要揭晓的整批
//   revealIndex   揭晓到第几个（0 基）
//   revealSettled 当前号码是否已定格——定格前号码无入场动画，避免提前剧透
const revealList = ref([])
const revealIndex = ref(0)
const revealSettled = ref(false)
// 汇总页：整批中奖者 + 档位名 + 池子不够时少抽的人数
const resultList = ref([])
const resultTier = ref('')
const shortfall = ref(0)
// 揭晓动画进行中：轮询发现的「新中奖」其实正是本次抽出的同一批，不要来打断
let revealing = false
// 组件已卸载：卸载后揭晓循环不再新建滚动定时器——
// 卸载发生在 await sleep 期间时，之后的 rollTo 会新建 interval 且再无人清理
let disposed = false

// 端口号滚动定时器句柄（rollTo 启动 / stopRolling 清理）。
// 注意：必须声明——<script setup> 编译为 ESM 严格模式，给未声明变量赋值会抛
// ReferenceError，rollTo 一旦抛错整批揭晓动画就会被 revealAll 的 finally 跳过，
// 现场表现为「看不到滚动和纸屑，直接出结果名单」
let ticker = null

// 增量轮询：把上次收到的版本号带回去，未变化则协议上直接跳过
let currentVersion = null
// 已经「看到」过的最近批次：用来识别管理端抽奖控制台代抽的新中奖
let knownBatchKey = null
// 已见批次的最新中奖时间（batchKey 第二段）水位：管理端作废最近一档后
// lastBatch 会退化成更早批次，没有水位的话退化批次会被误当成新抽奖，
// 大屏把上一轮的中奖动画完整重播一遍
let knownBatchTime = null
// 揭晓动画进行中时轮询送来的新批次：先挂起，等当前批次播完由 revealAll 的
// finally 补播。不挂起的话该批次会被 `!revealing` 静默丢弃，现场就是「少播一次特效」。
// 用队列而非单槽：主持人手快连抽两档、或单档人数多导致揭晓耗时长时，
// 单槽会被后一批覆盖，前一批动画直接丢失
let pendingBatches = []
// 队列上限：单批揭晓要数秒到数十秒，若主持人狂点抽奖、或后端异常重复下发不同 batchKey，
// 无上限会让大屏连续播动画停不下来、主持人无法插话。超出时丢最旧的并留证据
const MAX_PENDING_BATCHES = 5
let failStreak = 0        // 连续失败次数（单次抖动不闪遮罩）
let firstLoadDone = false // 首次加载只记录不展示，避免刷新后凭空弹出上一轮结果
const offline = ref(false)
// 连续失败后的轮询间隔退避：1.5s → 3s → 6s → 12s（封顶）。
// 后端短时不可达时降低请求频率，避免 1.5s 一拍的请求全部超时堆积，
// 也避免恢复瞬间的请求风暴；成功一次即复位
let pollInterval = 1500
const POLL_MAX_INTERVAL = 12000
// 轮询句柄改用递归 setTimeout：固定 setInterval 在退避时无法改间隔
let pollTimer = null

const poolEmpty = computed(() => (status.value.poolCount ?? 0) <= 0)

/** 中奖庆祝纸屑：当前这一批要渲染的纸屑元素列表（makeConfetti 每次重填） */
const confetti = ref([])
/** 第几次生成纸屑：拼进 v-for 的 key，强制重建 DOM。
 *  否则连续两档同为最高档（一等奖→特等奖，纸屑数都是 150、key 都是 0..149）时，
 *  Vue 复用旧节点，CSS 动画不会重播，表现为「第二次抽最高档没有特效」——
 *  纸屑动画 iteration-count:1 + fill-mode:forwards，播完即停在透明/屏外状态 */
const confettiSeq = ref(0)

/** 中奖庆祝纸屑：每次中奖重新生成，保证每次的落点与飘向都不一样。
 *  按奖项等级差异化（完整版）：档位越高，纸屑越密、配色越华丽、
 *  形状越多（disc 圆片 / strip 长条彩带 / star 星形）、下落方式越花（直落翻飞 / sway 横向摇摆）。
 *  奖项名可配置，按关键字匹配：含「特等/一等」最高、含「二等」次之、其余按最低档处理 */
const CONFETTI_PRESETS = {
  // 一等奖 / 特等奖：金红最密最久，长条 + 星形 + 摇摆全开
  high: {
    count: 150, durMin: 2.6, durMax: 4.4,
    colors: ['#FFD166', '#E23B2E', '#F0B429', '#C4291F', '#FFE9A8', '#FF9F1C'],
    shapes: ['strip sway', 'star', 'disc sway', 'strip']
  },
  // 二等奖：金白配色，中等密度
  mid: {
    count: 100, durMin: 2.2, durMax: 3.6,
    colors: ['#FFD166', '#FFE9A8', '#FFFFFF', '#F0B429'],
    shapes: ['strip sway', 'disc', 'star']
  },
  // 三等奖及未识别档位：单色少量，直落
  low: {
    count: 60, durMin: 2.1, durMax: 3.2,
    colors: ['#FFD166', '#FFE9A8'],
    shapes: ['disc']
  }
}

function makeConfetti(tierName) {
  const n = String(tierName || '')
  const p = n.includes('特等') || n.includes('一等') ? CONFETTI_PRESETS.high
        : n.includes('二等') ? CONFETTI_PRESETS.mid
        : CONFETTI_PRESETS.low
  const list = []
  for (let i = 0; i < p.count; i++) {
    const shape = p.shapes[i % p.shapes.length]
    const size = 6 + Math.random() * 8
    const style = {
      left: (Math.random() * 100).toFixed(2) + '%',
      width: size.toFixed(1) + 'px',
      height: (size * 1.7).toFixed(1) + 'px',
      background: p.colors[i % p.colors.length],
      animationDelay: (Math.random() * 0.7).toFixed(2) + 's',
      animationDuration: (p.durMin + Math.random() * (p.durMax - p.durMin)).toFixed(2) + 's',
      '--drift': ((Math.random() * 2 - 1) * 130).toFixed(0) + 'px'
    }
    if (shape.includes('sway')) {
      style['--sway'] = ((Math.random() * 2 - 1) * 110).toFixed(0) + 'px'
    }
    if (shape.includes('star')) {
      // 星形等宽等高，尺寸略大才看得清轮廓；颜色固定金色，与奖项色统一
      style.width = (12 + Math.random() * 8).toFixed(1) + 'px'
      style.height = style.width
      style.background = '#FFD166'
    }
    list.push({ id: i, shape, style })
  }
  // 序号自增：作为 v-for 的 key 前缀，保证每次生成都是全新节点，动画必然重播
  confettiSeq.value++
  confetti.value = list
}

/** 本场次全部档位（后端已按场次过滤，避免两场奖品混在一张进度条上） */
const tiers = computed(() => status.value.tiers || [])
/** 赞助商名称列表，为空则不渲染底部条 */
const sponsors = computed(() => status.value.sponsors || [])
const totalQuantity = computed(() => status.value.totalQuantity ?? 0)
const totalDrawn = computed(() => status.value.totalDrawn ?? 0)

/** 某档位已抽百分比（防止 quantity 为 0 时除零） */
function tierPercent(t) {
  const q = t?.quantity ?? 0
  if (!q) return 0
  return Math.min(100, Math.round(((t.drawnCount ?? 0) / q) * 100))
}

/** 是否为当前应抽档位：用于把进度条高亮成金色 */
function isCurrentTier(t) {
  return !!status.value.currentTier && status.value.currentTier.id === t?.id
}

/** 当前奖项还剩多少份 */
const remainingOfCurrent = computed(() => {
  const c = status.value.currentTier
  if (!c) return 0
  return Math.max(0, (c.quantity ?? 0) - (c.drawnCount ?? 0))
})

// 轮询在途标记：弱网下单次请求可能超过轮询间隔（api.js 超时 10s），
// 在途时不叠加新请求，避免请求雪崩
let statusInflight = false

async function loadStatus() {
  if (statusInflight) return
  statusInflight = true
  // 只把网络请求包进 try：syncFromServer 等后续处理若抛编程异常，
  // 以前会被当成「网络失败」吞掉——不落日志、误亮「失去连接」遮罩，
  // 而网络其实正常（正是掩盖 ticker 根因的同一类缺陷）
  let res = null
  let failed = false
  try {
    res = await fetchStatus(currentVersion)
  } catch (e) {
    failed = true
    // 单次抖动不闪遮罩；连续失败才判定为断开，恢复后自动消失
    failStreak += 1
    if (failStreak >= 3) offline.value = true
    // 失败退避：指数加倍轮询间隔，恢复后复位（成功时在下面复位）
    pollInterval = Math.min(pollInterval * 2, POLL_MAX_INTERVAL)
  } finally {
    statusInflight = false
  }
  if (failed) return
  try {
    if (res.code === 200) {
      failStreak = 0
      offline.value = false
      pollInterval = 1500
      const d = res.data || {}
      // 增量：后端说没变化就什么都不做。
      // 大屏在投屏，整份重渲染会打断中奖动画，也让画面看起来在闪。
      if (!d.unchanged) {
        currentVersion = d.version || null
        status.value = d
        syncFromServer(d)
      }
    } else if (res.code !== undefined) {
      // 业务失败（不是网络异常）：以前这里什么都不做，画面停在旧数据上，
      // 操作员看到的场次/奖池数全是过期的，却没有任何提示。
      // 计入失败连击，连续业务失败同样亮离线遮罩。
      // 限流（429）本质是服务端自保而非「连接断了」，不计入断连判定，
      // 且顺带退避一轮，避免限流期间继续高频打
      if (res.code === 429) {
        pollInterval = Math.min(pollInterval * 2, POLL_MAX_INTERVAL)
      } else {
        failStreak += 1
        if (failStreak >= 3) offline.value = true
      }
    } else {
      // 响应畸形（无 code 字段）：与业务失败同口径计入失败连击，
      // 静默忽略的话画面停在旧数据上却毫无迹象
      failStreak += 1
      if (failStreak >= 3) offline.value = true
    }
  } catch (e) {
    // 编程异常（如状态映射出错）：留下可观测证据，不吞掉也不当成网络失败；
    // 捕获而非放任抛出——否则 scheduleNext 的 await 链会断，轮询直接停摆
    console.error('[loadStatus] 状态处理异常', e)
  }
}

/** batchKey 前缀（档位id@中奖时间）相同 = 同一次抽奖，末段是人数 */
function sameDraw(a, b) {
  const head = k => String(k || '').split('@').slice(0, 2).join('@')
  return head(a) === head(b)
}

/** batchKey 第二段（yyyy-MM-dd HH:mm:ss）：字典序即时间序，可直接比较 */
function batchTimeOf(key) {
  return String(key || '').split('@')[1] || null
}

/** 把服务端快照映射到本地 phase，并接管「管理端抽出的整批中奖」 */
function syncFromServer(d) {
  const batch = d.lastBatch

  if (!firstLoadDone) {
    // 刷新后不该凭空弹出上一轮的结果：只记住，不展示
    firstLoadDone = true
    if (batch && batch.batchKey) {
      knownBatchKey = batch.batchKey
      knownBatchTime = batchTimeOf(batch.batchKey)
    }
    if (phase.value === 'idle' && poolEmpty.value) phase.value = 'empty'
    return
  }

  if (batch && batch.batchKey && batch.batchKey !== knownBatchKey) {
    const prevKey = knownBatchKey

    if (prevKey && sameDraw(prevKey, batch.batchKey)) {
      // 同一次抽奖、只有人数变了 = 批次里有人被作废（可能在管理端操作）：
      // 汇总页就地同步，不重播揭晓动画。shortfall 一并同步：
      // 后端按作废后的 drawnCount 重算缺口，不同步的话「还差 N 位」停留旧值
      knownBatchKey = batch.batchKey
      if (phase.value === 'result') {
        resultList.value = (batch.winners || []).map(w => ({ ...w }))
        shortfall.value = batch.shortfall ?? 0
      }
    } else {
      // 撤回导致的退化批次：管理端作废最近一档后，lastBatch 会回退到
      // 更早的批次（时间早于已见水位），这不是新抽奖——不拦截的话
      // 大屏会把上一轮的中奖动画完整重播一遍。
      // 用严格小于：主持人同一秒内连抽两档时，下一档时间等于水位，
      // 是新批次不是退化，不能吞
      const bt = batchTimeOf(batch.batchKey)
      if (knownBatchTime && bt && bt < knownBatchTime) {
        knownBatchKey = batch.batchKey
        return
      }
      if (bt) knownBatchTime = bt
      if (!revealing) {
        // 新的整批：抽奖全程由管理端「抽奖控制台」发起，大屏检测到新 batchKey
        // 就播放揭晓动画。revealing 为真时说明正是本次揭晓触发的同步，不要重播。
        // 记账在开播前：配合 revealAll 的 try/finally，即使本批动画中途异常，
        // revealing 也会复位，下一批照常播放，不会出现「卡死后全批失效」
        knownBatchKey = batch.batchKey
        // gap：池子不足时本批少抽的人数（后端 lastBatch.shortfall），
        // 汇总页据此提示「还差 N 位」；后端不下发时按 0 处理
        revealAll(batch.winners || [], batch.tierName, batch.shortfall ?? 0)
        return
      } else {
        // 揭晓进行中收到新批次：入队待补播。不在此记账 knownBatchKey，
        // 让这批在 finally 补播时仍走「新批次」分支，避免被当成已消费而漏播
        const entry = {
          batchKey: batch.batchKey,
          winners: batch.winners || [],
          tierName: batch.tierName,
          gap: batch.shortfall ?? 0
        }
        // 同一次抽奖（batchKey 同前缀）已在队列里时只替换、不追加：
        // 揭晓期间同批有人被作废会带来同一批的新快照（人数变化），
        // 若直接 push，补播时会把这一批连播两遍（后一遍人数少），
        // 现场表现为同一档特效莫名重复。替换时保留原位置，维持先后顺序
        const dupIdx = pendingBatches.findIndex(p => sameDraw(p.batchKey, entry.batchKey))
        if (dupIdx >= 0) {
          pendingBatches[dupIdx] = entry
          return
        }
        if (pendingBatches.length >= MAX_PENDING_BATCHES) {
          // 队列已满：丢最旧的。现场宁可漏播最早那一批，也不能让大屏无限连播
          const dropped = pendingBatches.shift()
          console.warn('[syncFromServer] 待播批次积压超上限，丢弃最旧一批', dropped && dropped.batchKey)
        }
        pendingBatches.push(entry)
        return
      }
    }
  } else if (!batch && knownBatchKey != null) {
    // 整批都被作废了。knownBatchTime 保留：它是「已见最新中奖时间」水位，
    // 清空的话继续撤回更早批次时，lastBatch 的退化批次会被误当成新抽奖
    knownBatchKey = null
    // 队列里可能还压着这一批（或更早批次）的待播快照，源头已作废：
    // 不清掉的话 finally 补播仍会把它们播出来，等于播了已被撤回的奖
    if (pendingBatches.length) pendingBatches.length = 0
    if (phase.value === 'result') {
      resultList.value = []
      phase.value = 'idle'
    }
  }

  if (phase.value === 'idle' && poolEmpty.value) phase.value = 'empty'
  else if (phase.value === 'empty' && !poolEmpty.value) phase.value = 'idle'
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/** 让端口号滚动一段时间后定格到目标值（舞台效果：一个人一个人的端口号在滚） */
function rollTo(target, ms) {
  const settledNo = String(target).padStart(5, '0')
  return new Promise(resolve => {
    stopRolling()
    const t0 = Date.now()
    ticker = setInterval(() => {
      if (Date.now() - t0 >= ms) {
        stopRolling()
        displayNo.value = settledNo
        resolve()
      } else {
        displayNo.value = randomNo()
      }
    }, 45)
  })
}

/**
 * 收尾校正汇总页名单：揭晓期间若当前批在管理端被作废（人数变化），
 * syncFromServer 因 phase='revealing' 不会刷新 resultList，汇总页会停在旧名单。
 * 这里按最新 status.lastBatch 校正一次，仅当它确实是当前批（batchKey 前缀一致）时生效。
 */
function syncResultIfBatchChanged() {
  const batch = status.value.lastBatch
  if (!batch || !batch.batchKey || batch.batchKey !== knownBatchKey) return
  resultList.value = (batch.winners || []).map(w => ({ ...w }))
  // 缺口一并校正：作废后后端按最新 drawnCount 重算，不同步会停留旧值
  shortfall.value = batch.shortfall ?? 0
}

/**
 * 播放一整批的揭晓动画（纯播放，不碰待播队列）。
 * 队列消费统一由 revealAll 的迭代 drain 负责，避免「补播里再调 revealAll」
 * 形成递归调用栈。
 */
async function playReveal(winners, tierName, gap) {
  if (!winners || !winners.length) {
    phase.value = 'idle'
    return
  }
  revealList.value = winners
  resultList.value = winners.map(w => ({ ...w }))
  resultTier.value = tierName || ''
  shortfall.value = gap || 0

  for (let i = 0; i < winners.length; i++) {
    // 卸载后停止揭晓：组件已销毁，继续跑只会新建无人清理的滚动定时器
    if (disposed) return
    revealIndex.value = i
    revealSettled.value = false
    phase.value = 'revealing'
    await sleep(100)
    if (disposed) return
    // 多人时每个滚短一点，单人时滚久一点，整体节奏都在舞台上可控
    await rollTo(winners[i].portNo, winners.length > 1 ? 1100 : 1600)
    revealSettled.value = true
    await sleep(winners.length > 1 ? 1900 : 2400)
  }

  // 先备好纸屑再切汇总页，避免纸屑晚一拍出现；纸屑样式按本档奖项等级差异化
  makeConfetti(tierName)
  phase.value = 'result'
  // 收尾校正名单：揭晓耗时数秒，期间该批若有人在管理端被作废，
  // syncFromServer 会因 phase 处于 revealing 而不刷新 resultList，
  // 导致汇总页停在作废前的旧名单。这里按最新 status 再校一次
  syncResultIfBatchChanged()
}

/**
 * 依次揭晓整批中奖者，全部揭晓完停在汇总页。
 * 大屏自己抽中与管理端「抽奖控制台」代抽共用这条路径。
 *
 * 队列消费是「迭代式 drain」而非递归：批次播完统一在同一层 while 里取下一批，
 * 每次继续播放都重新套 try/catch/finally，单批异常不会中断后续批次。
 */
async function revealAll(winners, tierName, gap) {
  if (!winners || !winners.length) {
    phase.value = 'idle'
    return
  }
  let next = { winners, tierName, gap }
  while (next) {
    // 揭晓主体必须裹在 try/finally 里：揭晓期间纸屑生成等任何异常如果让
    // revealing 卡在 true，syncFromServer 的 `!revealing` 会恒为假，
    // 之后所有新批次都不再播揭晓动画（现场表现就是「后面抽的奖全没特效」，
    // 只能刷新大屏恢复）。finally 保证标志位一定复位，异常不扩散到后续批次
    revealing = true
    try {
      await playReveal(next.winners, next.tierName, next.gap)
    } catch (e) {
      // 揭晓中途异常（如纸屑生成失败）：留下可观测证据，不要把现场问题变成「静默少一次特效」
      console.error('[revealAll] 揭晓过程异常，本批动画可能未完整播放', e)
    } finally {
      // 兜底 phase：异常若发生在 makeConfetti 与 phase='result' 之间，
      // 画面会永久卡在「滚动中」定格态，这里强制落到汇总页。
      // 同时清空纸屑——本批没成功生成，不清的话会拿上一批的旧纸屑配新名单
      if (phase.value === 'revealing') {
        phase.value = 'result'
        confetti.value = []
      }
      revealing = false
    }
    // 揭晓期间若轮询送来过新批次（被 revealing 挡下没播），这里依次补播，
    // 否则那些动画会被永久静默丢弃。
    // 补播前先记账 batchKey：否则补播进行中轮询又会把同一批重新入队，
    // 造成同一批动画反复重播、大屏卡在该批次上出不去。
    // 同一层 while 取下一批，不递归调用 revealAll，队列再长调用栈也不增长
    next = null
    if (pendingBatches.length) {
      const p = pendingBatches.shift()
      knownBatchKey = p.batchKey
      next = { winners: p.winners, tierName: p.tierName, gap: p.gap }
    }
  }
}

function randomNo() {
  return String(Math.floor(Math.random() * 65535) + 1).padStart(5, '0')
}

// startRolling 已随 doDraw 一起删除：大屏不再主动发起滚动，
// 端口号滚动只由被动揭晓路径 revealAll → rollTo 驱动。

function stopRolling() {
  clearInterval(ticker)
  ticker = null
}

// 注意：大屏不再主动抽奖 / 作废。
// 抽奖由管理端「抽奖控制台」发起并落库，大屏靠 loadStatus 轮询被动感知新批次，
// 在 syncFromServer 里检测到新 batchKey 时调用 revealAll 播放揭晓动画。
// 这里曾有的 doDraw() / doVoid() 已随对应后端端点一起删除。

// 切场次已收归管理端：大屏不提供按钮也不响应键盘，切场次后由轮询被动同步。

onMounted(() => {
  loadStatus()
  // 管理端「抽奖控制台」点抽奖后，大屏靠这条轮询感知新批次并开始滚动揭晓。
  // 间隔越短延迟越低、请求量越大；现场内网下 1.5s 完全扛得住，主持人手感更跟手。
  // 用递归 setTimeout 而非 setInterval：连续失败时按 pollInterval 退避
  // （1.5s → 3s → 6s → 12s 封顶），成功即复位；fixed interval 做不到这点
  const scheduleNext = () => {
    // 卸载后不再续：请求在途时卸载组件，await 返回后若无此检查
    // 仍会重新 setTimeout，形成永不停止的轮询（HMR 下叠加多套）
    if (disposed) return
    pollTimer = setTimeout(async () => {
      await loadStatus()
      scheduleNext()
    }, pollInterval)
  }
  scheduleNext()
})

onUnmounted(() => {
  disposed = true
  clearTimeout(pollTimer)
  stopRolling()
})
</script>
