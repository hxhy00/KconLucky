<template>
  <div class="h-full flex flex-col select-none pattern-grid">
    <!-- 顶部：场次 + 统计 -->
    <header class="flex items-center justify-between px-10 py-6">
      <div class="flex items-center gap-4">
        <img :src="logo" class="h-9" alt="KCon 2026" />
        <span class="px-4 py-1.5 rounded-full text-sm bg-brand/10 text-brand border border-brand/30">
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
        <div class="text-lg text-slate-400 mb-4 tracking-[0.3em]">恭 喜 中 奖</div>
        <div class="rand-num text-gold glow-gold">{{ winner.portNo }}</div>
        <div class="mt-10 text-center">
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
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { fetchStatus, draw, voidWinner, switchSession } from './api'
import logo from './assets/kcon-logo-dark.png'

const status = ref({})
const phase = ref('idle')      // idle | rolling | result | empty
const winner = ref(null)
const displayNo = ref('00000')

let timer = null
let ticker = null
let lastWinnerId = null

const poolEmpty = computed(() => (status.value.poolCount ?? 0) <= 0)

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

async function loadStatus() {
  try {
    const res = await fetchStatus()
    if (res.code === 200) {
      status.value = res.data
      if (phase.value === 'idle' && poolEmpty.value && status.value.checkedInToday >= 0) {
        // 仅在有明确池数据且池空时提示
        if (poolEmpty.value) phase.value = 'empty'
      } else if (phase.value === 'empty' && !poolEmpty.value) {
        phase.value = 'idle'
      }
    }
  } catch (e) { /* 静默重试 */ }
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
  if (poolEmpty.value) { phase.value = 'empty'; return }

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
      winner.value = res.data
      lastWinnerId = res.data.winnerId
      displayNo.value = String(res.data.portNo).padStart(5, '0')
      phase.value = 'result'
      await loadStatus()
    } else if (res.code === 4001) {
      phase.value = 'empty'
    } else {
      phase.value = 'idle'
      alert(res.msg || '抽奖失败')
    }
  } catch (e) {
    stopRolling()
    phase.value = 'idle'
  }
}

async function doVoid() {
  if (!lastWinnerId) return
  if (!confirm('确认作废该中奖记录？号码将回到池中。')) return
  try {
    const res = await voidWinner(lastWinnerId)
    if (res.code === 200) {
      lastWinnerId = null
      winner.value = null
      phase.value = 'idle'
      await loadStatus()
    } else {
      alert(res.msg || '作废失败')
    }
  } catch (e) { /* ignore */ }
}

async function setSession(code) {
  try {
    await switchSession(code)
    winner.value = null
    lastWinnerId = null
    phase.value = 'idle'
    await loadStatus()
  } catch (e) { /* ignore */ }
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
