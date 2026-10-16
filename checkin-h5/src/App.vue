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
             复用上面同一个输入框，走只读的 /app/checkin/lookup，不会产生新签到记录。
             注意：找回只认完整 11 位手机号，不支持后四位——尾号只有 10^4 种，
             免登录接口上可被枚举，会连带削弱兑奖核验「本人知道完整号码」这层防冒领判定。 -->
        <div class="mt-6 pt-5 border-t border-white/5">
          <button
            class="w-full h-12 rounded-xl border border-slate-700 text-slate-300 text-sm
                   active:scale-[.99] transition disabled:opacity-40"
            :disabled="!canLookup || loading"
            @click="doLookup"
          >
            {{ loading ? '查询中...' : '截图丢了？找回我的端口号' }}
          </button>
          <p class="mt-2 text-center text-slate-500 text-xs">
            需输入完整 11 位手机号，后四位不支持找回
          </p>

          <!-- 找回结果 -->
          <div v-if="lookupResult" class="mt-4 p-4 rounded-xl bg-brand/10 border border-brand/30 text-center">
            <div class="text-brand text-xs tracking-widest">你的端口号</div>
            <div class="text-4xl font-black text-brand mt-1 tabular-nums">{{ lookupResult.portNo }}</div>
            <p class="text-slate-400 text-xs mt-2">{{ lookupResult.name }} · {{ lookupResult.checkinTime }}</p>
            <!-- spec 006：与签到成功页同口径——不暴露抽奖资格，找回结果一律不区分 -->
          </div>

          <!-- 这里曾列出「后四位撞号」的候选让人自己认领。找回只认完整 11 位后，
               后端不再返回 ambiguous / candidates，该分支已无来源。 -->
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

        <!-- 核心引导：端口号 = 抽奖凭证 + 兑奖凭证（spec 005）。
             胸牌提示降为次要信息，避免抢走「号码是抽奖用的」这个主认知。

             注意：非买票者（spec 006）在这里看到的内容与买票者**完全一致**。
             不提示「您不参与抽奖」是刻意设计——筛选规则不对外暴露，
             否则等于告诉对方「你的票是白来的」，既引发现场争执，也把口径送了出去。
             判定结果只留在服务端，供抽奖池与统计使用。 -->
        <p class="text-slate-300 text-sm leading-relaxed">
          请截屏保存此号码，凭它参与现场抽奖<br />
          中奖后凭此号码到礼品区兑换奖品
        </p>

        <div class="mt-7 p-4 rounded-xl bg-slate-800/60 border border-slate-700 text-slate-400 text-sm">
          请向工作人员出示此截图领取胸牌
        </div>

        <p class="mt-4 text-slate-500 text-xs">签到时间：{{ result.checkinTime }}</p>

        <!-- 这里曾有一个「不是我，重新输入」按钮。签到成功是终态，不该再给额外操作：
             它暗示「还能改」，弱化了「请截图保存」这个真正的核心动作。
             注意：当前 success 页没有任何自动返回机制，下一位观众需要手动下拉刷新
             或后退才能回到输入页（见 README「已知问题」）。 -->
      </section>
    </main>

    <footer class="pb-6 text-center text-slate-600 text-xs">
      KCon 2026 · 中关村展示中心
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { queryAttendee, submitCheckin, lookup } from './api'
import logo from './assets/kcon-mark.png'

const step = ref('input')        // input | candidates | confirm | success
const keyword = ref('')
const candidates = ref([])
const person = ref(null)
const result = ref(null)
const lookupResult = ref(null)
const lookupError = ref('')
const loading = ref(false)
const error = ref('')

// 全角数字转半角：现场有人用全角输入法（如 １３８...），\d 只认半角，
// 不转换会被误判为「位数不足」（2026-10-15 测试报告问题 8）
function normalizeDigits(s) {
  return s.trim().replace(/[\uFF10-\uFF19]/g, c => String.fromCharCode(c.charCodeAt(0) - 0xFEE0))
}

// 找回端口号只认完整 11 位手机号：后端已砍掉尾号分支，前端提前禁用按钮，
// 避免观众输了 4 位后拿到一句「不支持」却不知道要找什么。
const canLookup = computed(() => /^\d{11}$/.test(normalizeDigits(keyword.value)))

// 输入一变就清掉上一次的找回结果，避免「改了手机号、下面还显示旧端口号」
watch(keyword, () => {
  lookupResult.value = null
  lookupError.value = ''
})

function attendText(n) {
  if (!n) return '—'
  return n === 1 ? '首次参加' : `第 ${n} 次参加`
}

function reset() {
  step.value = 'input'
  candidates.value = []
  person.value = null
  result.value = null
  error.value = ''
  // 输入也一并清空：不然下一位看到的还是上一个人的手机号
  keyword.value = ''
  lookupResult.value = null
  lookupError.value = ''
}

// 找回端口号：截图丢了的人还在签到台时，用输入框里的完整手机号回查原端口号。
// 走只读的 /app/checkin/lookup——不写签到记录，重复点击也安全。
// 只认完整 11 位：尾号可枚举，会让兑奖核验的防冒领判定失去意义（详见后端实现）。
async function doLookup() {
  error.value = ''
  lookupError.value = ''
  lookupResult.value = null
  const kw = normalizeDigits(keyword.value)
  if (!kw) return
  if (!canLookup.value) {
    lookupError.value = '请输入完整 11 位手机号（后四位不支持找回，防止号码被冒领）'
    return
  }
  loading.value = true
  try {
    const res = await lookup(kw)
    if (res.code !== 200) {
      lookupError.value = res.msg || '查询失败，请稍后重试'
      return
    }
    const d = res.data || {}
    // 后端这里固定返回 code=200，真正的提示都在 data.msg 里——
    // 读 res.msg 只会拿到 RuoYi 固定的「操作成功」，观众会以为查到了却没看到号码。
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

async function doQuery() {
  error.value = ''
  lookupResult.value = null
  lookupError.value = ''
  const kw = normalizeDigits(keyword.value)
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
