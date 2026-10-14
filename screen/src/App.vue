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

      <!-- S3 定格（中奖信息） -->
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

        <div class="text-lg text-slate-400 mb-4 tracking-[0.3em]">恭 喜 中 奖</div>
        <!-- 庆祝动效②：中奖号码入场缩放 0.6 → 1（带轻微过冲） -->
        <div class="rand-num pop-in text-gold glow-gold">{{ winner.portNo }}</div>
        <div class="mt-10 text-center fade-up">
          <div class="text-6xl font-black">{{ winner.name }}</div>
          <div class="mt-4 text-2xl text-slate-300">
            <span v-if="winner.company">{{ winner.company }}</span>
            <span v-if="winner.company && winner.position" class="mx-3 text-slate-600">·</span>
            <span v-if="winner.position">{{ winner.position }}</span>
          </div>
          <div class="mt-7 inline-block px-8 py-3 rounded-2xl
                      bg-gold/15 text-gold border border-gold/40 text-3xl font-bold">
            {{ winner.tierName }}
          </div>
        </div>
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
const phase = ref('idle')      // idle | rolling | result | empty
const winner = ref(null)
const displayNo = ref('00000')

let timer = null
let ticker = null
let lastWinnerId = null

// 增量轮询：把上次收到的版本号带回去，未变化则协议上直接跳过
let currentVersion = null
// 已经「看到」过的最近中奖 id：用来识别管理端抽奖控制台抽出的新中奖
let knownWinnerId = null
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

async function loadStatus() {
  if (statusInflight) return
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

/** 把服务端快照映射到本地 phase，并接管「管理端抽出的中奖」 */
function syncFromServer(d) {
  const lw = d.lastWinner

  if (!firstLoadDone) {
    // 刷新后不该凭空弹出上一轮的结果：只记住已有中奖，不展示
    firstLoadDone = true
    if (lw && lw.winnerId) knownWinnerId = lw.winnerId
    if (phase.value === 'idle' && poolEmpty.value) phase.value = 'empty'
    return
  }

  if (lw && lw.winnerId) {
    if (lw.winnerId > knownWinnerId) {
      // 出现新的中奖：可能是在管理端「抽奖控制台」抽出的。
      // 以前大屏永远不会切到结果页，于是管理端抽了一个、大屏还停在上一轮，
      // 而两边文案都写着「两边看到的是同一个抽奖池」。
      knownWinnerId = lw.winnerId
      if (phase.value !== 'rolling') {
        showWinner(lw)
        return
      }
    } else if (lw.winnerId < knownWinnerId) {
      // 最新一条在别处被作废了：如果大屏正定格在这条上，回到待机
      knownWinnerId = lw.winnerId
      if (phase.value === 'result') {
        winner.value = null
        lastWinnerId = null
        phase.value = 'idle'
      }
    }
  } else if (knownWinnerId != null) {
    knownWinnerId = null
    if (phase.value === 'result') {
      winner.value = null
      lastWinnerId = null
      phase.value = 'idle'
    }
  }

  if (phase.value === 'idle' && poolEmpty.value) phase.value = 'empty'
  else if (phase.value === 'empty' && !poolEmpty.value) phase.value = 'idle'
}

/** 进入中奖定格页。大屏自己抽中与管理端代抽共用同一条路径 */
function showWinner(w) {
  winner.value = w
  lastWinnerId = w.winnerId
  displayNo.value = String(w.portNo).padStart(5, '0')
  // 先备好纸屑再切到定格页，避免纸屑晚一拍出现
  makeConfetti()
  phase.value = 'result'
}

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
  if (phase.value === 'rolling') return
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
  const MIN_MS = 1600
  const start = Date.now()

  try {
    const tierId = status.value.currentTier?.id
    const res = await draw(status.value.sessionCode, tierId)

    const elapsed = Date.now() - start
    if (elapsed < MIN_MS) await new Promise(r => setTimeout(r, MIN_MS - elapsed))
    stopRolling()

    if (res.code === 200) {
      // 自己抽出的也要记为「已看到」，否则下一轮轮询会把它当成新中奖再播一次
      knownWinnerId = res.data.winnerId
      showWinner(res.data)
      await loadStatus()
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
    // 否则操作员以为没抽到，再按一次空格就会再抽一个，静默产生两条中奖记录。
    await showTip('抽奖请求未收到响应，结果未知。请先刷新页面确认是否已产生中奖，再决定是否重抽。')
    await loadStatus()
  }
}

async function doVoid() {
  if (!lastWinnerId) return
  if (!(await showConfirm('确认作废该中奖记录？号码将回到池中。'))) return
  try {
    const res = await voidWinner(lastWinnerId)
    if (res.code === 200) {
      lastWinnerId = null
      winner.value = null
      phase.value = 'idle'
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
    winner.value = null
    lastWinnerId = null
    phase.value = 'idle'
    // 场次变了，强制下次拉全量状态
    currentVersion = null
    await loadStatus()
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
