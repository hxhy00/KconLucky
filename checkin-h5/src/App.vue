<template>
  <div class="min-h-[100dvh] flex flex-col">
    <!-- 步骤切换不会移动焦点，用 aria-live 区域让读屏用户知道当前进展 -->
    <div class="sr-only" aria-live="polite">{{ liveMessage }}</div>
    <!-- 顶部品牌线：细窄的一条，替代大面积红色渐变 -->
    <div class="h-[3px] bg-brand"></div>

    <header class="px-5 pt-7">
      <div class="mx-auto w-full max-w-md">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2.5">
            <img :src="logo" class="h-7 w-7 object-contain" alt="KCon" />
            <span class="text-sm font-semibold tracking-wide text-slate-200">KCon 2026</span>
          </div>
          <span class="text-xs text-slate-400">现场签到</span>
        </div>

        <div class="mt-7">
          <h1 class="text-xl font-semibold text-slate-100">观众签到</h1>
          <p class="mt-1.5 text-sm text-slate-400">请使用报名时填写的手机号完成签到</p>
        </div>
        <div class="mt-5 h-px bg-white/10"></div>
      </div>
    </header>

    <main class="flex-1 px-5 pt-6 pb-10">
      <div class="mx-auto w-full max-w-md">
        <!-- P1 输入手机号 -->
        <section v-if="step === 'input'">
          <label for="phone" class="block text-xs font-medium text-slate-400 mb-2">手机号</label>
          <div class="relative">
            <input
              id="phone"
              ref="phoneEl"
              v-model="keyword"
              type="tel"
              inputmode="numeric"
              autocomplete="tel"
              autocapitalize="off"
              spellcheck="false"
              placeholder="请输入报名手机号"
              aria-describedby="phone-status"
              class="w-full h-14 pl-4 pr-11 rounded-lg bg-[#0E0E11] border text-lg
                     tracking-[0.08em] text-slate-100 placeholder:text-slate-400
                     outline-none transition-colors tabular-nums"
              :class="inputInvalid
                ? 'border-rose-500/60 focus:border-rose-400'
                : 'border-white/10 focus:border-brand'"
              @input="onKeywordInput"
              @compositionstart="onCompositionStart"
              @compositionend="onCompositionEnd"
              @keyup.enter="onEnter"
            />
            <button
              v-if="keyword"
              type="button"
              class="absolute right-1 top-1/2 -translate-y-1/2 w-9 h-9 rounded-md
                     text-slate-400 text-base active:bg-white/5"
              aria-label="清空手机号"
              @click="clearKeyword"
            >✕</button>
          </div>

          <!-- 提示与报错共用一行：位置固定，不会因为出现红字把按钮往下顶 -->
          <p
            id="phone-status"
            class="mt-2 min-h-[18px] text-xs"
            :class="error ? 'text-rose-400' : 'text-slate-400'"
            :role="error ? 'alert' : null"
            :aria-live="error ? 'assertive' : 'off'"
          >{{ error || phoneHint }}</p>

          <button
            class="w-full h-12 mt-4 rounded-lg bg-[#C1332A] text-white text-base font-medium
                   active:bg-[#A82B23] transition-colors disabled:opacity-40"
            :disabled="!canSubmit || loading"
            @click="doQuery"
          >
            {{ loading ? '查询中…' : '查询参会信息' }}
          </button>

          <p class="mt-3 text-xs text-slate-400">
            签到成功后将生成抽奖端口号，请提前准备截屏
          </p>

          <!-- 签到与找回都只认完整 11 位手机号；尾号空间只有 10^4，
               免登录接口上可被枚举，会连带削弱兑奖核验「本人知道完整号码」这层防冒领判定。 -->
          <div class="mt-8 pt-4 border-t border-white/10">
            <div class="flex items-center justify-between gap-4">
              <span class="text-xs text-slate-400">已签到但没保存端口号？</span>
              <button
                type="button"
                class="text-sm text-slate-300 underline underline-offset-4 decoration-slate-600
                       disabled:opacity-40 disabled:no-underline"
                :disabled="!canLookup || loading"
                @click="doLookup"
              >找回端口号</button>
            </div>

            <!-- 找回结果 -->
            <div
              v-if="lookupResult"
              ref="lookupEl"
              class="mt-4 rounded-lg border border-white/10 bg-[#141418] overflow-hidden"
            >
              <div class="flex items-center justify-between px-4 py-2.5 border-b border-white/10">
                <span class="text-xs text-slate-400">签到凭证</span>
                <span class="text-xs text-amber-300">请截屏保存</span>
              </div>
              <div class="px-4 py-5 text-center">
                <div class="text-xs text-slate-400">抽奖端口号</div>
                <div class="mt-2 text-5xl font-bold text-brand tabular-nums">{{ lookupResult.portNo }}</div>
              </div>
              <dl class="px-4 py-2 text-sm border-t border-white/10">
                <div class="flex justify-between py-1.5">
                  <dt class="text-slate-400">姓名</dt>
                  <dd class="text-slate-200">{{ lookupResult.name }}</dd>
                </div>
                <div v-if="lookupResult.ticketType" class="flex justify-between gap-4 py-1.5">
                  <dt class="text-slate-400 shrink-0">权益类型</dt>
                  <dd class="text-right text-slate-200">{{ lookupResult.ticketType }}</dd>
                </div>
                <div class="flex justify-between py-1.5">
                  <dt class="text-slate-400">签到时间</dt>
                  <dd class="text-slate-300 tabular-nums">{{ lookupResult.checkinTime }}</dd>
                </div>
              </dl>
              <!-- spec 006：与签到成功页同口径——不暴露抽奖资格，找回结果一律不区分 -->
            </div>

            <!-- 这里曾列出「后四位撞号」的候选让人自己认领。找回只认完整 11 位后，
                 后端不再返回 ambiguous / candidates，该分支已无来源。 -->
            <p
              v-if="lookupError"
              class="mt-3 text-sm text-rose-400"
              role="alert"
              aria-live="assertive"
            >{{ lookupError }}</p>
          </div>

          <p class="mt-8 text-xs text-slate-400">
            如遇问题，请向现场工作人员出示本页面。
          </p>
        </section>

        <!-- P2 身份确认 -->
        <section v-else-if="step === 'confirm'">
          <div class="flex items-baseline justify-between">
            <h2 class="text-lg font-semibold text-slate-100">核对参会信息</h2>
            <span class="text-xs text-slate-400">步骤 2 / 2</span>
          </div>
          <p class="mt-1 text-sm text-slate-400">请确认以下信息为本人，再完成签到</p>

          <div class="mt-5 rounded-lg border border-white/10 bg-[#141418] overflow-hidden">
            <div class="px-5 py-4 border-b border-white/10">
              <div class="text-xl font-semibold text-slate-100">{{ person.name }}</div>
              <!-- 只展示一次手机号：此前卡片里是掩码、下面又放一个只读完整号码，
                   观众会疑惑「到底要核对哪一个」 -->
              <div class="mt-1 text-sm text-slate-400 tabular-nums">{{ person.phoneMask }}</div>
            </div>
            <dl class="px-5 py-1 text-sm divide-y divide-white/5">
              <div v-if="person.company" class="flex justify-between gap-4 py-3">
                <dt class="text-slate-400 shrink-0">公司</dt>
                <dd class="text-right text-slate-200">{{ person.company }}</dd>
              </div>
              <div v-if="person.position" class="flex justify-between gap-4 py-3">
                <dt class="text-slate-400 shrink-0">职务 / 年级</dt>
                <dd class="text-right text-slate-200">{{ person.position }}</dd>
              </div>
            </dl>
          </div>

          <button
            class="w-full h-12 mt-5 rounded-lg bg-[#C1332A] text-white text-base font-medium
                   active:bg-[#A82B23] transition-colors disabled:opacity-40"
            :disabled="loading || !canConfirm"
            @click="doCheckin"
          >
            {{ loading ? '签到中…' : '确认签到' }}
          </button>
          <button
            class="w-full h-11 mt-3 rounded-lg border border-white/10 text-sm text-slate-300
                   active:bg-white/5 transition-colors"
            :disabled="loading"
            @click="reset"
          >信息有误，重新输入</button>

          <p
            v-if="error"
            class="mt-4 text-sm text-rose-400"
            role="alert"
            aria-live="assertive"
          >{{ error }}</p>
        </section>

        <!-- P4/P5 签到成功（含重复签到） -->
        <section v-else-if="step === 'success'">
          <div class="flex items-center gap-2">
            <span class="w-1.5 h-1.5 rounded-full"
                  :class="result.repeat ? 'bg-amber-400' : 'bg-emerald-400'"></span>
            <span class="text-sm font-medium"
                  :class="result.repeat ? 'text-amber-300' : 'text-emerald-300'">
              {{ result.repeat ? '已完成签到' : '签到成功' }}
            </span>
          </div>
          <p class="mt-3 text-sm text-slate-400">
            {{ result.name }}，您好{{ result.repeat ? '。以下是您首次签到获得的端口号' : '' }}
          </p>

          <!-- 签到凭证：视觉上明确告诉观众截哪一块 -->
          <div class="mt-4 rounded-lg border border-white/10 bg-[#141418] overflow-hidden">
            <div class="flex items-center justify-between px-4 py-2.5 border-b border-white/10">
              <span class="text-xs tracking-wide text-slate-400">KCon 2026 · 签到凭证</span>
              <span class="text-xs text-amber-300">请截屏保存</span>
            </div>

            <div class="px-4 py-6 text-center">
              <div class="text-xs text-slate-400">抽奖端口号</div>
              <div class="port-no mt-2">{{ result.portNo }}</div>
            </div>

            <dl class="px-4 py-2 text-sm border-t border-white/10 divide-y divide-white/5">
              <div class="flex justify-between py-1.5">
                <dt class="text-slate-400">姓名</dt>
                <dd class="text-slate-200">{{ result.name }}</dd>
              </div>
              <div v-if="result.ticketType" class="flex justify-between gap-4 py-1.5">
                <dt class="text-slate-400 shrink-0">权益类型</dt>
                <dd class="text-right text-slate-200">{{ result.ticketType }}</dd>
              </div>
              <div class="flex justify-between py-1.5">
                <dt class="text-slate-400">签到时间</dt>
                <dd class="text-slate-300 tabular-nums">{{ result.checkinTime }}</dd>
              </div>
            </dl>
          </div>

          <!-- 核心引导：端口号 = 抽奖凭证 + 兑奖凭证（spec 005）。
               注意：非买票者（spec 006）在这里看到的内容与买票者**完全一致**，
               不提示「您不参与抽奖」是刻意设计——筛选规则不对外暴露。 -->
          <ol class="mt-6 space-y-3 text-sm text-slate-300">
            <li class="flex gap-3">
              <span class="font-num text-xs text-slate-400 pt-0.5">01</span>
              <span>截屏保存本页，端口号是抽奖与兑奖的唯一凭证</span>
            </li>
            <li class="flex gap-3">
              <span class="font-num text-xs text-slate-400 pt-0.5">02</span>
              <span>凭端口号参与现场抽奖</span>
            </li>
            <li class="flex gap-3">
              <span class="font-num text-xs text-slate-400 pt-0.5">03</span>
              <span>中奖后凭端口号到礼品区兑换奖品</span>
            </li>
          </ol>

          <div class="mt-6 border-l-2 border-brand bg-brand/5 px-4 py-3 text-sm text-slate-300">
            请向工作人员出示本凭证，领取胸牌。
          </div>

          <!-- 成功页需要明确出口：共用设备时，下一位观众不必靠下拉刷新或后退才能重新开始。
               按钮做成次要样式，避免和「请截屏保存」抢注意力。 -->
          <button
            class="mt-8 w-full h-11 rounded-lg border border-white/10 text-sm text-slate-300
                   active:bg-white/5 transition-colors"
            @click="reset"
          >完成，返回签到首页</button>
        </section>
      </div>
    </main>

    <footer class="px-5 pb-[max(1.5rem,env(safe-area-inset-bottom))]">
      <div class="mx-auto w-full max-w-md flex items-center justify-between gap-4
                  border-t border-white/5 pt-4 text-xs text-slate-400">
        <span>KCon 2026</span>
        <span>中关村展示中心</span>
      </div>
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
// 读屏播报文本：步骤切换 / 找回成功时更新
const liveMessage = ref('')
// 找回请求序号：输入变化后，在途的旧结果必须作废
let lookupSeq = 0

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
  return '手机号已完整，可以查询'
})

// 输入一变就清掉上一次的找回结果，避免「改了手机号、下面还显示旧端口号」；
// 同时让在途的找回/查询请求作废，防止旧号码的结果晚到后覆盖新状态。
// loading 一并复位：在途请求已被作废，其 finally 会因序号不符跳过复位，
// 不在这里解锁的话按钮会一直禁用到请求超时
watch(keyword, () => {
  lookupSeq++
  loading.value = false
  lookupResult.value = null
  lookupError.value = ''
  error.value = ''
})

// 步骤切换的读屏播报
watch(step, (val) => {
  if (val === 'confirm') liveMessage.value = '已进入确认页'
  else if (val === 'success') liveMessage.value = '签到成功'
})

onMounted(() => {
  // 部分浏览器（尤其 iOS）不允许无手势聚焦，失败也不影响手动点击
  phoneEl.value && phoneEl.value.focus()
})

// 输入即归一化：全角转半角、去掉分隔符、截断到 11 位。
// IME 组合输入期间不改写：全角输入法下改写 value 会吞字/光标跳动。
// 不用事件属性判断（input 事件的 keyCode 恒为 undefined、部分安卓 IME
// 组合期间 isComposing=false），用 compositionstart/end 显式加锁
let composing = false

function onCompositionStart() {
  composing = true
}

function onCompositionEnd(e) {
  composing = false
  // compositionend 后部分浏览器不再补发 input 事件：这里手动归一化一次
  const digits = digitsOnly(e.target.value)
  keyword.value = digits
  if (e.target.value !== digits) {
    e.target.value = digits
  }
}

function onKeywordInput(e) {
  if (composing) return
  const digits = digitsOnly(e.target.value)
  keyword.value = digits
  if (e.target.value !== digits) {
    e.target.value = digits
  }
}

// IME 确认候选词的 Enter 也会触发 keyup：组合期间不查询
function onEnter(e) {
  if (composing || e.isComposing) return
  doQuery()
}

function clearKeyword() {
  keyword.value = ''
  phoneEl.value && phoneEl.value.focus()
}

// 切换步骤后回到页首：成功页与确认页都从顶部开始读，避免沿用上一页的滚动位置
function scrollTop() {
  nextTick(() => window.scrollTo(0, 0))
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
    window.scrollTo(0, 0)
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
  // 记录本次请求序号：输入在请求途中变化时，结果不再可信
  const seq = ++lookupSeq
  loading.value = true
  try {
    const res = await lookup(keywordDigits.value)
    if (seq !== lookupSeq) return
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
    liveMessage.value = '查询成功'
    // 结果在页面下方，小屏上不滚过去观众会以为没查到
    nextTick(() => {
      lookupEl.value && lookupEl.value.scrollIntoView({ behavior: 'smooth', block: 'center' })
    })
  } catch (e) {
    if (seq !== lookupSeq) return
    // api.js 已备好面向用户的文案（请求超时/服务异常 HTTP 502），直接展示，
    // 不再统一改写成「网络异常」——现场才能区分超时与网关故障
    lookupError.value = e.message || '查询失败，请检查网络后重试'
  } finally {
    // 只有最新一次请求才有资格复位 loading；被作废的旧请求由 watch 复位
    if (seq === lookupSeq) loading.value = false
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
  // 请求序号：与 doLookup 同一机制。输入在请求途中变化时 watch 会自增 lookupSeq，
  // 旧响应回来后序号不符即丢弃——否则共用签到设备上，旧号码的结果晚到
  // 会把上一个人的姓名与端口号展示出来（隐私泄露）
  const seq = ++lookupSeq
  loading.value = true
  try {
    const res = await queryAttendee(kw)
    if (seq !== lookupSeq) return
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
      // 已签到：走只读的 lookup 回查原端口号，不再调用 submit——
      // submit 会写签到记录，已签到的人点「查询」不该产生写操作
      await showExistingCheckin(kw, seq)
      return
    }
    person.value = data.attendee
    step.value = 'confirm'
    scrollTop()
  } catch (e) {
    if (seq !== lookupSeq) return
    error.value = e.message || '网络异常，请重试或联系现场工作人员'
  } finally {
    if (seq === lookupSeq) loading.value = false
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
  // 与 doQuery 同一序号机制：提交在途时输入若变化（watch 自增 lookupSeq），
  // 旧提交的结果作废，不再把旧号码的成功页盖回来
  const seq = ++lookupSeq
  loading.value = true
  try {
    // 一并提交完整手机号：后端据此做二次校验，
    // 防止有人只拿 attendeeId 替他人签到（公网版加固）
    const res = await submitCheckin(id, phone)
    if (seq !== lookupSeq) return
    if (res.code === 200 && res.data && res.data.portNo) {
      result.value = res.data
      step.value = 'success'
      scrollTop()
    } else if (res.code === 200) {
      // code=200 但 data 缺 portNo：直接切成功页会因访问 result.portNo 抛错白屏
      error.value = '签到信息读取失败，请联系现场工作人员'
    } else {
      error.value = res.msg || '签到失败，请联系现场工作人员'
    }
  } catch (e) {
    if (seq !== lookupSeq) return
    error.value = e.message || '网络异常，请重试'
  } finally {
    if (seq === lookupSeq) loading.value = false
  }
}

// 已签到用户：只读回查原端口号，复用「签到成功」页展示。
// lookup 不返回 repeat，这里补成 true —— 语义就是「已完成签到」。
// seq：沿用 doQuery 的请求序号，输入在回查途中变化时结果作废
async function showExistingCheckin(phone, seq) {
  error.value = ''
  try {
    const res = await lookup(phone)
    if (seq !== lookupSeq) return
    const d = res.data || {}
    if (res.code !== 200 || !d.signed || !d.portNo) {
      error.value = d.msg || res.msg || '签到信息读取失败，请联系现场工作人员'
      return
    }
    result.value = {
      portNo: d.portNo,
      name: d.name,
      ticketType: d.ticketType,
      checkinTime: d.checkinTime,
      repeat: true
    }
    step.value = 'success'
    scrollTop()
  } catch (e) {
    if (seq !== lookupSeq) return
    error.value = e.message || '网络异常，请重试'
  }
}
</script>

<style>
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: none; }
}
</style>
