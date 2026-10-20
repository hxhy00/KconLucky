<template>
  <div class="min-h-[100dvh] flex flex-col">
    <!-- 顶部标题 -->
    <header class="px-5 pt-6 pb-3 text-center">
      <!-- 图形与文字分离：图形用透明 PNG（深色底不再出现白方块），文字用真实文本 -->
      <div class="flex items-center justify-center gap-2">
        <img :src="logo" class="h-8" alt="KCon" />
        <span class="text-base font-bold tracking-wide">KCon 2026</span>
      </div>
      <h1 class="text-2xl font-semibold mt-3">现场自助签到</h1>
      <p class="mt-1.5 text-sm text-slate-400">用报名手机号签到，领取现场抽奖端口号</p>
    </header>

    <main class="flex-1 px-5 pb-8 pt-1">
      <!-- P1 输入手机号 -->
      <section v-if="step === 'input'" class="animate-[fadeIn_.2s_ease]">
        <!-- 三步预期：让第一次扫码的人知道接下来会发生什么 -->
        <ol class="mb-5 grid grid-cols-3 gap-2 text-center text-[11px] text-slate-400">
          <li class="rounded-lg bg-slate-800/60 py-2"><span class="text-brand font-bold mr-1">1</span>输入手机号</li>
          <li class="rounded-lg bg-slate-800/60 py-2"><span class="text-brand font-bold mr-1">2</span>核对姓名</li>
          <li class="rounded-lg bg-slate-800/60 py-2"><span class="text-brand font-bold mr-1">3</span>保存端口号</li>
        </ol>

        <label for="phone" class="block text-sm text-slate-300 mb-2">报名手机号</label>
        <div class="relative">
          <input
            id="phone"
            ref="phoneEl"
            v-model="keyword"
            type="tel"
            inputmode="numeric"
            autocomplete="tel"
            maxlength="11"
            placeholder="请输入 11 位手机号"
            aria-describedby="phone-status"
            class="w-full h-14 pl-4 pr-12 rounded-xl bg-slate-800/80 border
                   text-center text-2xl tracking-widest outline-none transition"
            :class="inputInvalid
              ? 'border-rose-500/70 focus:border-rose-400 focus:ring-2 focus:ring-rose-500/20'
              : 'border-slate-700 focus:border-brand focus:ring-2 focus:ring-brand/30'"
            @input="onKeywordInput"
            @keyup.enter="doQuery"
          />
          <button
            v-if="keyword"
            type="button"
            class="absolute right-1.5 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full
                   text-slate-400 text-lg active:bg-slate-700/60"
            aria-label="清空手机号"
            @click="clearKeyword"
          >✕</button>
        </div>

        <!-- 提示与报错共用一行：位置固定，不会因为出现红字把按钮往下顶 -->
        <p
          id="phone-status"
          class="mt-2 min-h-[20px] text-center text-xs"
          :class="error ? 'text-rose-400' : 'text-slate-400'"
          :role="error ? 'alert' : null"
          :aria-live="error ? 'assertive' : 'off'"
        >{{ error || phoneHint }}</p>

        <button
          class="w-full h-14 mt-3 rounded-xl bg-brand text-slate-900 font-bold text-lg
                 active:scale-[.99] transition disabled:opacity-40"
          :disabled="!canSubmit || loading"
          @click="doQuery"
        >
          {{ loading ? '查询中…' : '查 询' }}
        </button>

        <!-- 签到与找回都只认完整 11 位手机号；尾号空间只有 10^4，
             免登录接口上可被枚举，会连带削弱兑奖核验「本人知道完整号码」这层防冒领判定。 -->
        <div class="mt-7 pt-5 border-t border-white/5">
          <button
            type="button"
            class="w-full h-12 rounded-xl border border-slate-700 text-slate-300 text-sm
                   active:scale-[.99] transition disabled:opacity-40"
            :disabled="!canLookup || loading"
            @click="doLookup"
          >
            {{ loading ? '查询中…' : '截图丢了？找回端口号' }}
          </button>
          <p v-if="!canLookup && !lookupResult" class="mt-2 text-center text-xs text-slate-500">
            输入完整 11 位手机号后可找回
          </p>

          <!-- 找回结果 -->
          <div
            v-if="lookupResult"
            ref="lookupEl"
            class="mt-4 p-4 rounded-xl bg-brand/10 border border-brand/30 text-center"
          >
            <div class="text-brand text-xs tracking-widest">你的端口号</div>
            <div class="port-no my-2 tabular-nums">{{ lookupResult.portNo }}</div>
            <p class="text-amber-300 text-sm font-medium">请截屏保存</p>
            <p class="text-slate-400 text-xs mt-2">
              {{ lookupResult.name }} · {{ lookupResult.checkinTime }}
            </p>
            <p v-if="lookupResult.ticketType" class="text-slate-300 text-sm mt-2">
              权益类型：{{ lookupResult.ticketType }}
            </p>
            <!-- spec 006：与签到成功页同口径——不暴露抽奖资格，找回结果一律不区分 -->
          </div>

          <!-- 这里曾列出「后四位撞号」的候选让人自己认领。找回只认完整 11 位后，
               后端不再返回 ambiguous / candidates，该分支已无来源。 -->
          <p
            v-if="lookupError"
            class="mt-3 text-center text-rose-400 text-sm"
            role="alert"
            aria-live="assertive"
          >{{ lookupError }}</p>
        </div>

        <p class="mt-6 text-center text-xs text-slate-500">
          遇到问题？请向现场工作人员出示本页面
        </p>
      </section>

      <!-- P2 身份确认 -->
      <section v-else-if="step === 'confirm'" class="animate-[fadeIn_.2s_ease]">
        <p class="text-center text-sm text-slate-400 mb-4">请确认是您本人</p>

        <div class="p-5 rounded-2xl bg-slate-800/80 border border-slate-700">
          <div class="text-center text-3xl font-bold">{{ person.name }}</div>
          <!-- 只展示一次手机号：此前卡片里是掩码、下面又放一个只读完整号码，
               观众会疑惑「到底要核对哪一个」 -->
          <div class="text-center text-slate-400 mt-2 tabular-nums">{{ person.phoneMask }}</div>
          <dl class="mt-5 space-y-2.5 text-sm">
            <div v-if="person.company" class="flex justify-between gap-4">
              <dt class="text-slate-500 shrink-0">公司</dt><dd class="text-right">{{ person.company }}</dd>
            </div>
            <div v-if="person.position" class="flex justify-between gap-4">
              <dt class="text-slate-500 shrink-0">职务/年级</dt><dd class="text-right">{{ person.position }}</dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-slate-500 shrink-0">参加次数</dt>
              <dd class="text-right">{{ attendText(person.attendCount) }}</dd>
            </div>
          </dl>
        </div>

        <p class="mt-4 text-center text-xs text-slate-400">
          签到后会生成专属端口号，请提前准备好截屏
        </p>

        <button
          class="w-full h-14 mt-4 rounded-xl bg-brand text-slate-900 font-bold text-lg
                 active:scale-[.99] transition disabled:opacity-40"
          :disabled="loading || !canConfirm"
          @click="doCheckin"
        >
          {{ loading ? '签到中…' : '确认签到，获取端口号' }}
        </button>
        <button
          class="w-full h-12 mt-3 rounded-xl border border-slate-700 text-slate-400"
          :disabled="loading"
          @click="reset"
        >不是我，重新输入</button>
        <p
          v-if="error"
          class="mt-4 text-center text-rose-400 text-sm"
          role="alert"
          aria-live="assertive"
        >{{ error }}</p>
      </section>

      <!-- P4/P5 签到成功（含重复签到） -->
      <section v-else-if="step === 'success'" class="text-center animate-[fadeIn_.2s_ease]">
        <div v-if="result.repeat"
             class="inline-block px-3 py-1 rounded-full text-xs
                    bg-amber-400/15 text-amber-300 border border-amber-400/30">
          您已签过到，无需重复操作
        </div>
        <div v-else
             class="inline-block px-3 py-1 rounded-full text-xs
                    bg-emerald-400/15 text-emerald-300 border border-emerald-400/30">
          ✓ 签到成功
        </div>

        <p class="mt-5 text-slate-300">{{ result.name }}，您好</p>

        <!-- 把端口号收进「待截屏」卡片：视觉上明确告诉观众截哪一块 -->
        <div class="mt-4 rounded-2xl border border-brand/40 bg-brand/[0.07] px-4 py-5">
          <p class="text-sm text-slate-300">您的抽奖端口号</p>
          <div class="port-no my-3 tabular-nums">{{ result.portNo }}</div>
          <p class="text-sm font-medium text-amber-300">请立即截屏保存</p>
        </div>

        <!-- 核心引导：端口号 = 抽奖凭证 + 兑奖凭证（spec 005）。
             注意：非买票者（spec 006）在这里看到的内容与买票者**完全一致**，
             不提示「您不参与抽奖」是刻意设计——筛选规则不对外暴露。 -->
        <div class="mt-5 space-y-2.5 text-left text-sm text-slate-300">
          <p class="flex items-start gap-2">
            <span class="mt-0.5 w-5 h-5 shrink-0 rounded-full bg-brand/15 text-brand text-xs font-bold flex items-center justify-center">1</span>
            <span>截屏保存本页，端口号是抽奖与兑奖的唯一凭证</span>
          </p>
          <p class="flex items-start gap-2">
            <span class="mt-0.5 w-5 h-5 shrink-0 rounded-full bg-brand/15 text-brand text-xs font-bold flex items-center justify-center">2</span>
            <span>凭端口号参与现场抽奖</span>
          </p>
          <p class="flex items-start gap-2">
            <span class="mt-0.5 w-5 h-5 shrink-0 rounded-full bg-brand/15 text-brand text-xs font-bold flex items-center justify-center">3</span>
            <span>中奖后凭端口号到礼品区兑换奖品</span>
          </p>
        </div>

        <div class="mt-5 p-4 rounded-xl bg-slate-800/60 border border-slate-700 text-slate-300 text-sm">
          请向工作人员出示此截图领取胸牌
        </div>

        <p v-if="result.ticketType" class="mt-4 text-slate-300 text-sm">
          权益类型：{{ result.ticketType }}
        </p>
        <p class="mt-3 text-slate-500 text-xs">签到时间：{{ result.checkinTime }}</p>

        <!-- 成功页需要明确出口：共用设备时，下一位观众不必靠下拉刷新或后退才能重新开始。
             按钮做成次要样式，避免和「请立即截屏」抢注意力。 -->
        <button
          class="mt-6 w-full h-12 rounded-xl border border-slate-700 text-slate-300 text-sm
                 active:scale-[.99] transition"
          @click="reset"
        >完成，返回签到首页</button>
      </section>
    </main>

    <footer class="pb-[max(1.5rem,env(safe-area-inset-bottom))] text-center text-slate-600 text-xs">
      KCon 2026 · 中关村展示中心
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { queryAttendee, submitCheckin, lookup } from './api'
import logo from './assets/kcon-mark.png'

const step = ref('input')        // input | confirm | success
const keyword = ref('')
const person = ref(null)
const result = ref(null)
const phoneInput = ref('')       // 查询到的完整手机号，提交签到用
const lookupResult = ref(null)
const lookupError = ref('')
const loading = ref(false)
const error = ref('')
const phoneEl = ref(null)
const lookupEl = ref(null)

// 全角数字转半角：现场有人用全角输入法（如 １３８...），\d 只认半角，
// 不转换会被误判为「位数不足」（2026-10-15 测试报告问题 8）
function normalizeDigits(s) {
  return String(s == null ? '' : s)
    .replace(/[\uFF10-\uFF19]/g, c => String.fromCharCode(c.charCodeAt(0) - 0xFEE0))
}

// 粘贴常带空格、短横线、括号（138-1234-5678），统一去掉，只留最多 11 位数字
function digitsOnly(s) {
  return normalizeDigits(s).replace(/\D/g, '').slice(0, 11)
}

const keywordDigits = computed(() => digitsOnly(keyword.value))
const canSubmit = computed(() => keywordDigits.value.length === 11)
// 找回与签到同口径：必须完整 11 位且是合法号段
const canLookup = computed(() => /^1[3-9]\d{9}$/.test(keywordDigits.value))
const canConfirm = computed(() => /^1[3-9]\d{9}$/.test(digitsOnly(phoneInput.value)))
// 满 11 位但号段不对时才标红，避免刚输入 1 位就红着脸
const inputInvalid = computed(() =>
  keywordDigits.value.length === 11 && !/^1[3-9]/.test(keywordDigits.value)
)

const phoneHint = computed(() => {
  const n = keywordDigits.value.length
  if (n === 0) return '请输入报名时填写的 11 位手机号'
  if (n < 11) return `还需输入 ${11 - n} 位`
  if (!/^1[3-9]/.test(keywordDigits.value)) return '手机号格式好像不对，请检查'
  return '手机号已完整，点击查询'
})

// 输入一变就清掉上一次的找回结果，避免「改了手机号、下面还显示旧端口号」
watch(keyword, () => {
  lookupResult.value = null
  lookupError.value = ''
  error.value = ''
})

onMounted(() => {
  // 部分浏览器（尤其 iOS）不允许无手势聚焦，失败也不影响手动点击
  phoneEl.value && phoneEl.value.focus()
})

// 输入即归一化：全角转半角、去掉分隔符、截断到 11 位
function onKeywordInput(e) {
  const digits = digitsOnly(e.target.value)
  keyword.value = digits
  if (e.target.value !== digits) {
    e.target.value = digits
  }
}

function clearKeyword() {
  keyword.value = ''
  phoneEl.value && phoneEl.value.focus()
}

function attendText(n) {
  if (!n) return '—'
  return n === 1 ? '首次参加' : `第 ${n} 次参加`
}

function reset() {
  step.value = 'input'
  person.value = null
  result.value = null
  error.value = ''
  // 输入也一并清空：不然下一位看到的还是上一个人的手机号
  keyword.value = ''
  phoneInput.value = ''
  lookupResult.value = null
  lookupError.value = ''
  nextTick(() => {
    phoneEl.value && phoneEl.value.focus()
  })
}

// 找回端口号：截图丢了的人还在签到台时，用输入框里的完整手机号回查原端口号。
// 走只读的 /app/checkin/lookup——不写签到记录，重复点击也安全。
// 只认完整 11 位：尾号可枚举，会让兑奖核验的防冒领判定失去意义（详见后端实现）。
async function doLookup() {
  if (loading.value) return
  error.value = ''
  lookupError.value = ''
  lookupResult.value = null
  if (!canLookup.value) {
    lookupError.value = '请输入完整 11 位手机号'
    return
  }
  loading.value = true
  try {
    const res = await lookup(keywordDigits.value)
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
    // 结果在页面下方，小屏上不滚过去观众会以为没查到
    nextTick(() => {
      lookupEl.value && lookupEl.value.scrollIntoView({ behavior: 'smooth', block: 'center' })
    })
  } catch (e) {
    lookupError.value = '查询失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

async function doQuery() {
  if (loading.value) return
  error.value = ''
  lookupResult.value = null
  lookupError.value = ''
  const kw = keywordDigits.value
  if (kw.length !== 11) {
    error.value = '请输入完整 11 位手机号'
    return
  }
  if (!/^1[3-9]\d{9}$/.test(kw)) {
    error.value = '手机号格式不正确，请检查后重试'
    return
  }
  // 收起键盘：确认页信息较多，键盘会挡住「确认签到」按钮
  phoneEl.value && phoneEl.value.blur()
  phoneInput.value = kw
  loading.value = true
  try {
    const res = await queryAttendee(kw)
    if (res.code !== 200) {
      error.value = res.msg || '查无此人，请联系现场工作人员'
      return
    }
    const data = res.data
    if (!data || !data.attendee) {
      error.value = '查无此人，请联系现场工作人员'
      return
    }
    if (data.signed && data.attendee) {
      // 已签到：完整手机号已在手，直接回查原端口号，不再进确认页
      await doCheckinById(data.attendee.id, kw)
      return
    }
    person.value = data.attendee
    step.value = 'confirm'
  } catch (e) {
    error.value = '网络异常，请重试或联系现场工作人员'
  } finally {
    loading.value = false
  }
}

async function doCheckin() {
  if (loading.value || !person.value) return
  const phone = digitsOnly(phoneInput.value)
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    error.value = '请输入完整 11 位手机号'
    return
  }
  await doCheckinById(person.value.id, phone)
}

async function doCheckinById(id, phone) {
  error.value = ''
  loading.value = true
  try {
    // 一并提交完整手机号：后端据此做二次校验，
    // 防止有人只拿 attendeeId 替他人签到（公网版加固）
    const res = await submitCheckin(id, phone)
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
