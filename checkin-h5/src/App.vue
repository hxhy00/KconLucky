<template>
  <div class="min-h-full flex flex-col">
    <!-- 顶部标题 -->
    <header class="px-5 pt-6 pb-4 text-center">
      <img :src="logo" class="h-8 mx-auto" alt="KCon 2026" />
      <h1 class="text-xl font-semibold mt-2">现场自助签到</h1>
    </header>

    <main class="flex-1 px-5 pb-8 flex flex-col justify-center">
      <!-- P1 输入手机号 -->
      <section v-if="step === 'input'" class="animate-[fadeIn_.2s_ease]">
        <p class="text-slate-400 text-sm text-center mb-6">
          请输入报名时的手机号<br />
          或用手机号后四位快速签到
        </p>

        <input
          v-model="keyword"
          type="tel"
          inputmode="numeric"
          maxlength="11"
          placeholder="手机号 / 后四位"
          class="w-full h-14 px-4 rounded-xl bg-slate-800/80 border border-slate-700
                 text-center text-2xl tracking-widest outline-none
                 focus:border-brand focus:ring-2 focus:ring-brand/30 transition"
          @keyup.enter="doQuery"
        />

        <button
          class="w-full h-14 mt-4 rounded-xl bg-brand text-slate-900 font-bold text-lg
                 active:scale-[.99] transition disabled:opacity-40"
          :disabled="!keyword || loading"
          @click="doQuery"
        >
          {{ loading ? '查询中...' : '查 询' }}
        </button>

        <p v-if="error" class="mt-4 text-center text-rose-400 text-sm">{{ error }}</p>
      </section>

      <!-- P3 候选选择 -->
      <section v-else-if="step === 'candidates'">
        <p class="text-slate-400 text-sm text-center mb-4">
          找到 {{ candidates.length }} 位，请点击您的名字
        </p>
        <button
          v-for="c in candidates"
          :key="c.id"
          class="w-full text-left p-4 mb-3 rounded-xl bg-slate-800/80 border border-slate-700
                 active:border-brand transition"
          @click="pick(c)"
        >
          <div class="flex items-center justify-between">
            <span class="text-lg font-semibold">{{ c.name }}</span>
            <span v-if="c.channel"
                  class="px-2 py-0.5 rounded text-xs bg-brand/15 text-brand border border-brand/30">
              {{ c.channel }}
            </span>
          </div>
          <div class="text-slate-400 text-sm mt-1">{{ c.phoneMask }}</div>
          <div class="text-slate-400 text-sm" v-if="c.company">{{ c.company }}</div>
          <div class="text-slate-500 text-xs mt-1" v-if="c.position">{{ c.position }}</div>
        </button>
        <button class="w-full h-12 mt-2 rounded-xl border border-slate-700 text-slate-400"
                @click="reset">重新输入</button>
      </section>

      <!-- P2 身份确认 -->
      <section v-else-if="step === 'confirm'">
        <div class="p-5 rounded-2xl bg-slate-800/80 border border-slate-700">
          <div class="text-center text-3xl font-bold">{{ person.name }}</div>
          <div class="text-center text-slate-400 mt-2">{{ person.phoneMask }}</div>
          <dl class="mt-5 space-y-2 text-sm">
            <div v-if="person.channel" class="flex justify-between">
              <dt class="text-slate-500">票源渠道</dt><dd>{{ person.channel }}</dd>
            </div>
            <div v-if="person.company" class="flex justify-between">
              <dt class="text-slate-500">公司</dt><dd>{{ person.company }}</dd>
            </div>
            <div v-if="person.position" class="flex justify-between">
              <dt class="text-slate-500">职务/年级</dt><dd>{{ person.position }}</dd>
            </div>
            <div class="flex justify-between">
              <dt class="text-slate-500">参加次数</dt>
              <dd>{{ attendText(person.attendCount) }}</dd>
            </div>
          </dl>
        </div>

        <button
          class="w-full h-14 mt-5 rounded-xl bg-brand text-slate-900 font-bold text-lg
                 active:scale-[.99] transition disabled:opacity-40"
          :disabled="loading"
          @click="doCheckin"
        >
          {{ loading ? '签到中...' : '确认签到' }}
        </button>
        <button class="w-full h-12 mt-3 rounded-xl border border-slate-700 text-slate-400"
                @click="reset">不是我，重新输入</button>
        <p v-if="error" class="mt-4 text-center text-rose-400 text-sm">{{ error }}</p>
      </section>

      <!-- P4/P5 签到成功（含重复签到） -->
      <section v-else-if="step === 'success'" class="text-center">
        <div v-if="result.repeat"
             class="inline-block px-3 py-1 rounded-full text-xs
                    bg-amber-400/15 text-amber-300 border border-amber-400/30">
          您已签到，以下为原端口号
        </div>
        <div v-else
             class="inline-block px-3 py-1 rounded-full text-xs
                    bg-emerald-400/15 text-emerald-300 border border-emerald-400/30">
          ✓ 签到成功
        </div>

        <p class="mt-7 text-slate-400 text-sm">{{ result.name }}，您好</p>
        <p class="mt-3 text-slate-300 text-sm">您的抽奖端口号为</p>

        <div class="port-no text-brand my-6 tabular-nums">{{ result.portNo }}</div>

        <p class="text-slate-300 text-sm leading-relaxed">
          请您截屏保存，用于兑换抽奖礼品<br />
          <span class="text-amber-300">奖品很丰厚哦 🎁</span>
        </p>

        <div class="mt-7 p-4 rounded-xl bg-amber-400/10 border border-amber-400/30 text-amber-200 text-sm">
          请向工作人员出示此截图领取胸牌
        </div>

        <p class="mt-4 text-slate-500 text-xs">签到时间：{{ result.checkinTime }}</p>

        <!-- 需求 P5：截图丢了可凭手机号找回原端口号 -->
        <div class="mt-5">
          <button
            class="w-full py-2.5 rounded-xl border border-slate-600 text-slate-300 text-sm
                   active:scale-[.99] transition"
            @click="lookupOpen = !lookupOpen"
          >
            {{ lookupOpen ? '收起' : '截图丢了？找回我的端口号' }}
          </button>

          <div v-if="lookupOpen" class="mt-3">
            <input
              v-model="lookupPhone"
              type="tel"
              inputmode="numeric"
              maxlength="11"
              placeholder="签到时填过的手机号"
              class="w-full h-11 px-4 rounded-xl bg-slate-800/80 border border-slate-700
                     text-center text-base tracking-widest outline-none
                     focus:border-brand focus:ring-2 focus:ring-brand/30 transition"
            />
            <button
              class="w-full h-11 mt-2 rounded-xl bg-brand text-slate-900 font-bold
                     active:scale-[.99] transition disabled:opacity-40"
              :disabled="!lookupPhone || loading"
              @click="doLookup"
            >
              {{ loading ? '查询中...' : '查 回' }}
            </button>
          </div>
        </div>

        <div v-if="lookupResult" class="mt-3 p-4 rounded-xl bg-brand/10 border border-brand/30 text-center">
          <div class="text-brand text-xs tracking-widest">你的端口号</div>
          <div class="text-4xl font-black text-brand mt-1 tabular-nums">{{ lookupResult.portNo }}</div>
          <p class="text-slate-400 text-xs mt-2">{{ lookupResult.name }} · {{ lookupResult.checkinTime }}</p>
        </div>
        <p v-if="lookupError" class="mt-3 text-center text-rose-400 text-sm">{{ lookupError }}</p>
      </section>
    </main>

    <footer class="pb-6 text-center text-slate-600 text-xs">
      KCon 2026 · 中关村展示中心
    </footer>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { queryAttendee, submitCheckin, lookup } from './api'
import logo from './assets/kcon-logo-dark.png'

const step = ref('input')        // input | candidates | confirm | success
const keyword = ref('')
const candidates = ref([])
const person = ref(null)
const result = ref(null)
const lookupResult = ref(null)
const lookupError = ref('')
const lookupOpen = ref(false)
const lookupPhone = ref('')
const loading = ref(false)
const error = ref('')

function attendText(n) {
  if (!n) return '—'
  return n === 1 ? '首次参加' : `第 ${n} 次参加`
}

function reset() {
  step.value = 'input'
  candidates.value = []
  person.value = null
  error.value = ''
}

// 找回端口号（需求 P5）：截图丢了、或只记得后四位且重号时，
// 用签到时填过的手机号回查原端口号
async function doLookup() {
  lookupError.value = ''
  lookupResult.value = null
  const kw = lookupPhone.value.trim()
  if (!kw) return
  loading.value = true
  try {
    const res = await lookup(kw)
    if (res.code !== 200 || !res.data || !res.data.signed || !res.data.portNo) {
      // 后端在「查无此人」时仍返回 code=200，msg 是 RuoYi 固定的「操作成功」，
      // 真正的提示放在 data.msg 里。直接读 res.msg 会显示成「操作成功」，
      // 观众会以为查到了、却没看到端口号。
      lookupError.value = (res.data && res.data.msg)
        || (res.code !== 200 ? res.msg : '')
        || '未查到签到记录，请联系现场工作人员'
      return
    }
    lookupResult.value = res.data
  } catch (e) {
    lookupError.value = '查询失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

async function doQuery() {
  error.value = ''
  const kw = keyword.value.trim()
  if (!kw) return
  loading.value = true
  try {
    const res = await queryAttendee(kw)
    if (res.code !== 200) {
      error.value = res.msg || '查无此人，请联系现场工作人员'
      return
    }
    const data = res.data
    if (data.signed && data.attendee) {
      // 已签到直接展示原端口号
      await doCheckinById(data.attendee.id)
      return
    }
    if (data.matchType === 'single') {
      person.value = data.attendee
      step.value = 'confirm'
    } else if (data.matchType === 'multiple') {
      candidates.value = data.candidates || []
      step.value = 'candidates'
    }
  } catch (e) {
    error.value = '网络异常，请重试或联系现场工作人员'
  } finally {
    loading.value = false
  }
}

function pick(c) {
  person.value = c
  step.value = 'confirm'
}

async function doCheckin() {
  if (!person.value) return
  await doCheckinById(person.value.id)
}

async function doCheckinById(id) {
  error.value = ''
  loading.value = true
  try {
    const res = await submitCheckin(id)
    if (res.code === 200) {
      result.value = res.data
      step.value = 'success'
    } else {
      error.value = res.msg || '签到失败，请联系现场工作人员'
    }
  } catch (e) {
    error.value = '网络异常，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style>
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: none; }
}
</style>
