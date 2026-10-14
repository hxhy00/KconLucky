<template>
  <div class="min-h-full flex flex-col">
    <!-- 顶部标题 -->
    <header class="px-5 pt-6 pb-4 text-center">
      <!-- 图形与文字分离：图形用透明 PNG（深色底不再出现白方块），文字用真实文本 -->
      <div class="flex items-center justify-center gap-2">
        <img :src="logo" class="h-9" alt="KCon" />
        <span class="text-lg font-bold tracking-wide">KCon 2026</span>
      </div>
      <h1 class="text-xl font-semibold mt-2.5">现场自助签到</h1>
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

        <!-- 找回端口号：放在签到页，而不是「签到完成页」——
             截图丢了的人根本走不到完成页，入口放那儿等于不存在。
             复用上面同一个输入框，走只读的 /app/checkin/lookup，不会产生新签到记录。 -->
        <div class="mt-6 pt-5 border-t border-white/5">
          <button
            class="w-full h-12 rounded-xl border border-slate-700 text-slate-300 text-sm
                   active:scale-[.99] transition disabled:opacity-40"
            :disabled="!keyword || loading"
            @click="doLookup"
          >
            {{ loading ? '查询中...' : '截图丢了？找回我的端口号' }}
          </button>
          <p class="mt-2 text-center text-slate-500 text-xs">
            已签到过的，输入手机号或后四位也能查回
          </p>

          <!-- 找回结果 -->
          <div v-if="lookupResult" class="mt-4 p-4 rounded-xl bg-brand/10 border border-brand/30 text-center">
            <div class="text-brand text-xs tracking-widest">你的端口号</div>
            <div class="text-4xl font-black text-brand mt-1 tabular-nums">{{ lookupResult.portNo }}</div>
            <p class="text-slate-400 text-xs mt-2">{{ lookupResult.name }} · {{ lookupResult.checkinTime }}</p>
          </div>

          <!-- 后四位撞号：列出候选让人自己认领。
               以前这种情况一律报「查无此人」，当事人会以为自己没签到过而白跑一趟。 -->
          <div v-if="lookupCandidates.length" class="mt-4">
            <p class="text-slate-400 text-sm text-center mb-3">
              后四位「{{ keyword.slice(-4) }}」匹配到 {{ lookupCandidates.length }} 位，请点击您的名字
            </p>
            <button
              v-for="c in lookupCandidates"
              :key="c.id"
              class="w-full mb-2 px-4 py-3 rounded-xl bg-slate-800/80 border border-slate-700
                     text-left active:scale-[.99] transition"
              @click="pickCandidate(c)"
            >
              <div class="flex items-center justify-between gap-2">
                <span class="font-semibold">{{ c.name }}</span>
                <span class="text-xs shrink-0" :class="c.signed ? 'text-emerald-300' : 'text-slate-500'">
                  {{ c.signed ? '已签到' : '未签到' }}
                </span>
              </div>
              <div class="text-slate-400 text-xs mt-1">
                {{ c.phoneMask }}<template v-if="c.company"> · {{ c.company }}</template>
              </div>
            </button>
          </div>

          <p v-if="lookupError" class="mt-3 text-center text-rose-400 text-sm">{{ lookupError }}</p>
        </div>
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
      </section>
    </main>

    <footer class="pb-6 text-center text-slate-600 text-xs">
      KCon 2026 · 中关村展示中心
    </footer>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { queryAttendee, submitCheckin, lookup } from './api'
import logo from './assets/kcon-mark.png'

const step = ref('input')        // input | candidates | confirm | success
const keyword = ref('')
const candidates = ref([])
const person = ref(null)
const result = ref(null)
const lookupResult = ref(null)
const lookupError = ref('')
const lookupCandidates = ref([])
const loading = ref(false)
const error = ref('')

// 输入一变就清掉上一次的找回结果与候选，避免「改了手机号、下面还显示旧端口号」
watch(keyword, () => {
  lookupResult.value = null
  lookupError.value = ''
  lookupCandidates.value = []
})

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

// 找回端口号：截图丢了的人还在签到台时，直接用输入框里的手机号/后四位回查原端口号。
// 走只读的 /app/checkin/lookup——不写签到记录，重复点击也安全。
async function doLookup() {
  error.value = ''
  lookupError.value = ''
  lookupResult.value = null
  lookupCandidates.value = []
  const kw = keyword.value.trim()
  if (!kw) return
  loading.value = true
  try {
    const res = await lookup(kw)
    if (res.code !== 200) {
      lookupError.value = res.msg || '查询失败，请稍后重试'
      return
    }
    const d = res.data || {}
    // 以下三种情况后端都返回 code=200，真正的提示都在 data 里——
    // 读 res.msg 只会拿到 RuoYi 固定的「操作成功」，观众会以为查到了却没看到号码。
    if (d.ambiguous && Array.isArray(d.candidates) && d.candidates.length) {
      lookupCandidates.value = d.candidates
      return
    }
    if (!d.signed || !d.portNo) {
      lookupError.value = d.msg || '未查到签到记录，请联系现场工作人员'
      return
    }
    lookupResult.value = d
  } catch (e) {
    lookupError.value = '查询失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

// 认领候选项：端口号已随候选一起返回，直接展示，不再发请求
function pickCandidate(c) {
  lookupError.value = ''
  if (c.signed && c.portNo) {
    lookupResult.value = { portNo: c.portNo, name: c.name, checkinTime: c.checkinTime }
    lookupCandidates.value = []
    return
  }
  lookupError.value = `${c.name} 尚未签到，请点「查 询」完成签到`
}

async function doQuery() {
  error.value = ''
  lookupResult.value = null
  lookupError.value = ''
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
