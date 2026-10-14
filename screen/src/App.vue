<template>
  <div class="h-full flex flex-col select-none pattern-grid">
    <!-- 顶部：场次 + 统计 -->
    <header class="flex items-center justify-between px-10 py-6">
      <div class="flex items-center gap-4">
        <!-- 图形与文字分离：图形用透明 PNG（深色底不再出现白方块），文字用真实文本 -->
        <div class="flex items-center gap-2">
          <img :src="logo" class="h-10" alt="KCon" />
          <span class="text-xl font-black tracking-wide text-slate-200">KCon 2026</span>
        </div>
        <!-- 场次切换：点击即刻切换，当前场次高亮。
             原先只能按键盘 1/2，页面上毫无提示，操作员不易发现 -->
        <div v-if="sessions.length" class="flex items-center gap-2">
          <button
            v-for="s in sessions"
            :key="s.code"
            class="px-4 py-1.5 rounded-full text-sm border transition active:scale-95"
            :class="s.code === status.sessionCode
              ? 'bg-brand/15 text-brand border-brand/40'
              : 'bg-white/5 text-slate-400 border-white/10 hover:text-slate-200'"
            @click="setSession(s.code)"
          >{{ s.name }}</button>
        </div>
        <span v-else class="px-4 py-1.5 rounded-full text-sm bg-brand/10 text-brand border border-brand/30">
          {{ status.sessionName || '—' }}
        </span>
      </div>
      <div class="flex items-center gap-10 text-sm">
        <div class="text-center">
          <div class="text-2xl font-bold tabular-nums">{{ status.checkedInToday ?? 0 }}</div>
          <div class="text-slate-400 mt-0.5">今日已签到</div>
        </div>
        <div class="text-center">
          <div class="text-2xl font-bold tabular-nums text-brand">{{ status.poolCount ?? 0 }}</div>
          <div class="text-slate-400 mt-0.5">可抽号数</div>
        </div>
        <div class="text-center">
          <div class="text-2xl font-bold tabular-nums text-gold">{{ status.winnerCount ?? 0 }}</div>
          <div class="text-slate-400 mt-0.5">已中奖</div>
        </div>
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

        <div v-if="revealSettled" class="mt-10 text-center fade-up">
          <div class="text-6xl font-black">{{ currentReveal.name }}</div>
          <div class="mt-4 text-2xl text-slate-300">{{ currentReveal.company || '' }}</div>
        </div>
        <div v-else class="mt-10 text-2xl text-slate-700 tracking-[0.3em]">滚 动 中</div>
      </template>

      <!-- 汇总：这一档抽出的全部人，主持人照着一屏念完 -->
      <template v-else-if="phase === 'result'">
        <!-- 庆祝动效①：全屏金红纸屑（纯 CSS，无外部依赖） -->
        <div class="confetti-layer" aria-hidden="true">
          <span
            v-for="c in confetti"
            :key="c.id"
            class="confetti"
            :style="c.style"
          ></span>
        </div>

        <div class="text-lg text-slate-400 tracking-[0.3em]">恭 喜 中 奖</div>
        <div class="text-4xl font-black text-gold mt-2 mb-8">{{ resultTier }}</div>

        <div class="winner-grid">
          <div v-for="w in resultList" :key="w.winnerId" class="winner-cell">
            <div class="winner-port">{{ w.portNo }}</div>
            <div class="winner-name">{{ w.name }}</div>
            <div class="winner-company">{{ w.company || '' }}</div>
            <!-- 有人不在现场时要能单独撤掉他：撤掉后该位从汇总页消失、名额退回本档，
                 点「抽下一档」即可把缺的那位补上 -->
            <button class="winner-void-btn" @click="doVoid(w)">撤 回</button>
          </div>
        </div>

        <div v-if="shortfall > 0" class="mt-8 text-xl text-amber-300">
          当前可抽号数不足，本次只抽出 {{ resultList.length }} 位，还差 {{ shortfall }} 位
          （可让更多观众签到后继续抽）
        </div>

        <button class="mt-10 px-14 py-4 rounded-2xl text-2xl font-black tracking-[0.3em]
                       bg-brand text-white hover:brightness-110 glow active:scale-95 transition"
                @click="doDraw">抽 下 一 档</button>
        <div class="mt-3 text-sm text-slate-500">空格键同样可以开始下一次抽奖</div>
      </template>

      <!-- S1/S2 待机 & 滚动 -->
      <template v-else>
        <div class="text-lg text-slate-400 mb-6 tracking-[0.3em]">
          {{ phase === 'rolling' ? '抽 奖 中' : '准 备 抽 奖' }}
        </div>
        <div class="rand-num glow" :class="phase === 'rolling' ? 'text-brand' : 'text-slate-700'">
          {{ displayNo }}
        </div>
        <div v-if="status.currentTier" class="mt-8 text-xl text-slate-400">
          当前奖项：
          <span class="text-gold font-bold">{{ status.currentTier.tierName }}</span>
          <span class="text-slate-500 ml-2">
            还剩 {{ remainingOfCurrent }} 份
          </span>
        </div>

        <!-- 可见的抽奖按钮：大屏虽支持空格快捷键，但投屏机上鼠标点按钮更直观。
             一次点击 = 一次抽奖，端口号滚动效果由 doDraw 内部的 startRolling 驱动 -->
        <button
          v-if="phase === 'idle'"
          class="mt-12 px-16 py-4 rounded-2xl text-2xl font-black tracking-[0.3em] transition active:scale-95"
          :class="poolEmpty
            ? 'bg-white/5 text-slate-600 cursor-not-allowed'
            : 'bg-brand text-white hover:brightness-110 glow'"
          :disabled="poolEmpty"
          @click="doDraw"
        >开始抽奖</button>
        <div v-else class="mt-12 text-sm text-slate-500 tracking-[0.3em]">
          抽 奖 进 行 中 ， 请 稍 候
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
          <div class="h-2 rounded-full bg-white/8 overflow-hidden">
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

    <!-- 自绘提示 / 确认层：替代原生 alert/confirm（投屏上会带出域名与系统灰框） -->
    <div v-if="dialog.show" class="dialog-mask">
      <div class="dialog-box">
        <div class="dialog-text">{{ dialog.text }}</div>
        <div class="flex gap-4 mt-8">
          <button v-if="dialog.confirm" class="dialog-btn dialog-btn-ghost"
                  @click="resolveDialog(false)">取 消</button>
          <button class="dialog-btn" @click="resolveDialog(true)">
            {{ dialog.confirm ? '确 定' : '知道了' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { fetchStatus, draw, voidWinner, switchSession } from './api'
import logo from './assets/kcon-mark.png'

const status = ref({})
const phase = ref('idle')      // idle | rolling | revealing | result | empty
const displayNo = ref('00000')

// 一次点击抽出整档的人，所以「揭晓」是一段过程而不是一帧：
//   revealList    本次要揭晓的整批
//   revealIndex   揭晓到第几个（0 基）
//   revealSettled 当前号码是否已定格——定格前不显示姓名，避免提前剧透
const revealList = ref([])
const revealIndex = ref(0)
const revealSettled = ref(false)
// 汇总页：整批中奖者 + 档位名 + 池子不够时少抽的人数
const resultList = ref([])
const resultTier = ref('')
const shortfall = ref(0)
// 揭晓动画进行中：轮询发现的「新中奖」其实正是本次抽出的同一批，不要来打断
let revealing = false

let timer = null
let ticker = null
let lastWinnerId = null

// 增量轮询：把上次收到的版本号带回去，未变化则协议上直接跳过
let currentVersion = null
// 已经「看到」过的最近中奖 id 与批次：用来识别管理端抽奖控制台代抽的新中奖
let knownWinnerId = null
let knownBatchKey = null
let failStreak = 0        // 连续失败次数（单次抖动不闪遮罩）
let firstLoadDone = false // 首次加载只记录不展示，避免刷新后凭空弹出上一轮结果
const offline = ref(false)

// 自绘提示 / 确认层，替代原生 alert / confirm
// （投屏上弹系统原生弹窗会带出域名和灰色系统样式，观众也会看到）
const dialog = ref({ show: false, text: '', confirm: false })
let dialogResolver = null

function resolveDialog(ok) {
  dialog.value = { show: false, text: '', confirm: false }
  const fn = dialogResolver
  dialogResolver = null
  if (fn) fn(ok)
}
function showTip(text) {
  return new Promise(resolve => {
    dialogResolver = resolve
    dialog.value = { show: true, text, confirm: false }
  })
}
function showConfirm(text) {
  return new Promise(resolve => {
    dialogResolver = resolve
    dialog.value = { show: true, text, confirm: true }
  })
}

const poolEmpty = computed(() => (status.value.poolCount ?? 0) <= 0)

/** 可选场次列表（后端 screenStatus 一并返回），用于顶部可见的场次切换 */
const sessions = computed(() => status.value.sessions || [])

/** 中奖庆祝纸屑：每次中奖重新生成，保证每次的落点与飘向都不一样 */
const confetti = ref([])

function makeConfetti() {
  const colors = ['#FFD166', '#E23B2E', '#F0B429', '#C4291F', '#FFE9A8']
  const list = []
  for (let i = 0; i < 90; i++) {
    const size = 6 + Math.random() * 8
    list.push({
      id: i,
      style: {
        left: (Math.random() * 100).toFixed(2) + '%',
        width: size.toFixed(1) + 'px',
        height: (size * 1.7).toFixed(1) + 'px',
        background: colors[i % colors.length],
        animationDelay: (Math.random() * 0.7).toFixed(2) + 's',
        animationDuration: (2.1 + Math.random() * 1.7).toFixed(2) + 's',
        '--drift': ((Math.random() * 2 - 1) * 130).toFixed(0) + 'px'
      }
    })
  }
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

// 轮询在途标记：单次请求超过 3s 时不再叠加新请求，弱网下避免请求雪崩
let statusInflight = false
// 是否成功取到过一次状态：用于区分「真的没号可抽」与「后端连不上」
let statusLoaded = false

async function loadStatus(force = false) {
  // force：切换场次等场景必须立即拿到新场次的快照。
  // 此时若正好有一个 3 秒轮询在飞行中，普通调用会被早退掉，
  // 而那次请求带回的还是旧场次的 version 与数据，会把它覆盖回 currentVersion。
  if (statusInflight && !force) return
  statusInflight = true
  try {
    const res = await fetchStatus(currentVersion)
    if (res.code === 200) {
      statusLoaded = true
      failStreak = 0
      offline.value = false
      const d = res.data || {}
      // 增量：后端说没变化就什么都不做。
      // 大屏在投屏，整份重渲染会打断中奖动画，也让画面看起来在闪。
      if (!d.unchanged) {
        currentVersion = d.version || null
        status.value = d
        syncFromServer(d)
      }
    }
  } catch (e) {
    // 单次抖动不闪遮罩；连续失败才判定为断开，恢复后自动消失
    failStreak += 1
    if (failStreak >= 3) offline.value = true
  } finally {
    statusInflight = false
  }
}

/** batchKey 前缀（档位id@中奖时间）相同 = 同一次抽奖，末段是人数 */
function sameDraw(a, b) {
  const head = k => String(k || '').split('@').slice(0, 2).join('@')
  return head(a) === head(b)
}

/** 把服务端快照映射到本地 phase，并接管「管理端抽出的整批中奖」 */
function syncFromServer(d) {
  const lw = d.lastWinner
  const batch = d.lastBatch

  if (!firstLoadDone) {
    // 刷新后不该凭空弹出上一轮的结果：只记住，不展示
    firstLoadDone = true
    if (lw && lw.winnerId) knownWinnerId = lw.winnerId
    if (batch && batch.batchKey) knownBatchKey = batch.batchKey
    if (phase.value === 'idle' && poolEmpty.value) phase.value = 'empty'
    return
  }

  if (batch && batch.batchKey && batch.batchKey !== knownBatchKey) {
    const prevKey = knownBatchKey
    knownBatchKey = batch.batchKey
    if (lw && lw.winnerId) knownWinnerId = lw.winnerId

    if (prevKey && sameDraw(prevKey, batch.batchKey)) {
      // 同一次抽奖、只有人数变了 = 批次里有人被作废（可能在管理端操作）：
      // 汇总页就地同步，不重播揭晓动画
      if (phase.value === 'result') {
        resultList.value = (batch.winners || []).map(w => ({ ...w }))
      }
    } else if (!revealing && phase.value !== 'rolling') {
      // 新的整批：可能是管理端「抽奖控制台」代抽的。
      // 以前大屏永远不会切到结果页，于是管理端抽了、大屏还停在上一轮，
      // 而两边文案都写着「两边看到的是同一个抽奖池」。
      revealAll(batch.winners || [], batch.tierName, 0)
      return
    }
  } else if (!batch && knownBatchKey != null) {
    // 整批都被作废了
    knownBatchKey = null
    knownWinnerId = null
    if (phase.value === 'result') {
      resultList.value = []
      lastWinnerId = null
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
 * 依次揭晓整批中奖者，全部揭晓完停在汇总页。
 * 大屏自己抽中与管理端「抽奖控制台」代抽共用这条路径。
 */
async function revealAll(winners, tierName, gap) {
  if (!winners || !winners.length) {
    phase.value = 'idle'
    return
  }
  revealing = true
  revealList.value = winners
  resultList.value = winners.map(w => ({ ...w }))
  resultTier.value = tierName || ''
  shortfall.value = gap || 0
  lastWinnerId = winners[winners.length - 1].winnerId

  for (let i = 0; i < winners.length; i++) {
    revealIndex.value = i
    revealSettled.value = false
    phase.value = 'revealing'
    await sleep(100)
    // 多人时每个滚短一点，单人时滚久一点，整体节奏都在舞台上可控
    await rollTo(winners[i].portNo, winners.length > 1 ? 1100 : 1600)
    revealSettled.value = true
    await sleep(winners.length > 1 ? 1900 : 2400)
  }

  // 先备好纸屑再切汇总页，避免纸屑晚一拍出现
  makeConfetti()
  phase.value = 'result'
  revealing = false
}

/** 当前正在揭晓的那一位 */
const currentReveal = computed(() => revealList.value[revealIndex.value] || {})

function randomNo() {
  return String(Math.floor(Math.random() * 65535) + 1).padStart(5, '0')
}

function startRolling() {
  ticker = setInterval(() => { displayNo.value = randomNo() }, 45)
}

function stopRolling() {
  clearInterval(ticker)
  ticker = null
}

async function doDraw() {
  // 揭晓过程中不接受新的抽奖：整批还没播完就抽下一档，画面会乱
  if (phase.value === 'rolling' || phase.value === 'revealing' || revealing) return
  if (poolEmpty.value) {
    // 区分「确实没号可抽」与「后端连不上导致 status 为空」：
    // 后者若显示成「请先让观众签到」，等于把故障误报成业务提示
    if (!statusLoaded) {
      await showTip('无法连接服务，请检查大屏与后端之间的网络后重试')
      return
    }
    phase.value = 'empty'
    return
  }

  phase.value = 'rolling'
  startRolling()
  const MIN_MS = 1400
  const start = Date.now()

  try {
    const tierId = status.value.currentTier?.id
    const res = await draw(status.value.sessionCode, tierId)

    const elapsed = Date.now() - start
    if (elapsed < MIN_MS) await sleep(MIN_MS - elapsed)
    stopRolling()

    if (res.code === 200) {
      const d = res.data || {}
      const ws = d.winners || []
      // 整批都算「已看到」：否则紧接着的 3 秒轮询会把它当成新中奖，把揭晓重播一遍
      if (ws.length) knownWinnerId = ws[ws.length - 1].winnerId
      knownBatchKey = d.batchKey || null
      // 抽奖接口现在会带回最新状态，直接用，省掉一次请求
      if (d.status) {
        status.value = d.status
        currentVersion = d.status.version || null
      }
      await revealAll(ws, d.tierName, d.shortfall)
    } else if (res.code === 4001) {
      phase.value = 'empty'
    } else {
      phase.value = 'idle'
      await showTip(res.msg || '抽奖失败')
    }
  } catch (e) {
    stopRolling()
    phase.value = 'idle'
    // 超时/断网时后端可能已经落库并写入中奖记录，绝不能静默回到待抽状态：
    // 否则操作员以为没抽到，再按一次空格就会再抽一整批，静默多出好几个人。
    await showTip('抽奖请求未收到响应，结果未知。请先刷新页面确认是否已产生中奖，再决定是否重抽。')
    await loadStatus()
  }
}

/**
 * 作废某位中奖者。传 winner 表示从汇总页撤掉指定的那一位，
 * 不传则撤掉最近一位（空格/退格快捷键走这条）。
 */
async function doVoid(target) {
  const id = (target && target.winnerId) || lastWinnerId
  if (!id) return
  const who = target && target.name ? `「${target.name}」` : ''
  if (!(await showConfirm(`确认作废${who}的中奖记录？号码将回到池中，可被再次抽中。`))) return
  try {
    const res = await voidWinner(id)
    if (res.code === 200) {
      if (target) {
        // 汇总页就地移除该位，不打断画面。
        // 与服务端随后的批次同步结果保持一致（否则本地标「已作废」、同步后又整个消失，两种表现打架）。
        // 空出的名额已退回本档，点「抽下一档」即可补抽。
        resultList.value = resultList.value.filter(w => w.winnerId !== id)
        if (lastWinnerId === id) lastWinnerId = null
      } else {
        lastWinnerId = null
        phase.value = 'idle'
      }
      await loadStatus()
    } else {
      await showTip(res.msg || '作废失败')
    }
  } catch (e) {
    // 以前这里是空的 catch：超时/断网时画面原样不动，操作员以为已经作废，
    // 实际记录还在，下一个奖池里也没有这个号。
    await showTip('作废请求未收到响应，结果未知。请刷新大屏确认后再决定是否重试。')
    await loadStatus()
  }
}

async function setSession(code) {
  try {
    await switchSession(code)
    // 换场次 = 大屏重新认识世界：旧场次的版本号、「已看到的中奖」等本地记忆全部失效。
    // 尤其 knownBatchKey：不重置的话，新场次首个批次的 batchKey 若与旧值同前缀，
    // 会被误判成「同一批里有人被作废」，只更新 resultList 而漏掉整批揭晓动画。
    lastWinnerId = null
    currentVersion = null
    knownWinnerId = null
    knownBatchKey = null
    phase.value = 'idle'
    await loadStatus(true)
  } catch (e) {
    // 以前同样是空 catch：点了没反应、场次高亮也不变，操作员会反复点
    await showTip('切换场次失败，请检查网络后重试。当前场次未改变。')
    await loadStatus()
  }
}

function onKey(e) {
  if (e.code === 'Space') { e.preventDefault(); doDraw() }
  else if (e.code === 'Backspace') { e.preventDefault(); doVoid() }
  else if (e.key === '1') setSession('AM')
  else if (e.key === '2') setSession('PM')
}

onMounted(() => {
  loadStatus()
  timer = setInterval(loadStatus, 3000)
  window.addEventListener('keydown', onKey)
})

onUnmounted(() => {
  clearInterval(timer)
  stopRolling()
  window.removeEventListener('keydown', onKey)
})
</script>
