<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      <div slot="title">
        双重校验防冒领：端口号必须中奖且未领取，手机号必须与持号人完全一致（需完整 11 位）。
      </div>
    </el-alert>

    <el-card shadow="never" style="max-width: 560px;">
      <div slot="header"><b>兑奖核验</b></div>
      <el-form ref="verifyForm" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="端口号" prop="portNo">
          <el-input ref="portNoInput" v-model="form.portNo" placeholder="凭条上的端口号" maxlength="10"
                    @keyup.enter.native="handleVerify" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="完整 11 位手机号" maxlength="11"
                    @keyup.enter.native="handleVerify" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" :loading="verifying" @click="handleVerify">核 验</el-button>
          <el-button type="success" icon="el-icon-check" :loading="receiving" @click="handleReceive"
                     v-hasPermi="['kcon:verify:receive']"
                     :disabled="verifyResult !== 'VALID'">确认发放</el-button>
        </el-form-item>
      </el-form>

      <el-divider v-if="verifyData" />

      <el-result v-if="verifyData" icon="success" title="核验通过" :sub-title="verifyData.msg"
                 v-show="verifyResult === 'VALID'">
        <div slot="extra" class="verify-info">
          <!-- 刻意不显示手机号掩码：核验台是公开工位，掩码（138****0001）配上端口号，
               就是一条「本人可自助核验」的旁路——围观者可看到尾号再去签到页找回端口号。
               身份已由「本人报出的完整 11 位手机号与持号人一致」证明，无需再回显。 -->
          <p><b>{{ verifyData.name }}</b></p>
          <p>{{ verifyData.company }} {{ verifyData.position }}</p>
          <!-- 需求 FR-23：邮箱用于二次核对身份 -->
          <p v-if="verifyData.email">邮箱：{{ verifyData.email }}</p>
          <p><el-tag size="small">{{ verifyData.tierName }}</el-tag>
            <el-tag size="small" type="warning" style="margin-left: 8px;">{{ sessionName(verifyData.sessionCode) }}</el-tag>
          </p>
        </div>
      </el-result>

      <el-result v-if="verifyData && verifyResult !== 'VALID'" icon="warning"
                 :title="resultTitle(verifyResult)" :sub-title="verifyData.msg">
        <!-- 失败/已领取时也不再回显持号人手机号掩码：
             这里回显等于把真号码的尾号告诉了一个已经报错号码的人，
             他据此再猜一次即可通过核验——回显本身就是一条自证旁路。
             已在领取时间/操作人上保留可追溯信息，足够兑奖台判断。 -->
        <div slot="extra" v-if="verifyResult === 'RECEIVED' && verifyData.receiveTime">
          <p class="verify-info">领取时间：{{ verifyData.receiveTime }}</p>
          <p class="verify-info" v-if="verifyData.receiveOperator">领取操作人：{{ verifyData.receiveOperator }}</p>
        </div>
        <!-- spec 006 FR-A9：未中奖者常以「我怎么没被抽中」为由纠缠。
             这里预先给出统一话术方向，避免工作人员临场自创解释——
             任何关于「抽取范围」的解释都会泄露筛选口径。

             必须用 issued 区分：NOT_ISSUED 同时覆盖「已签到未中奖」（issued=true）
             与「号码压根没发出去 = 伪造截图」（issued=false）。对伪造截图的人讲
             「运气问题」等于承认这个号是本场的，反而坐实了假截图的可信度。 -->
        <div slot="extra" v-if="verifyResult === 'NOT_ISSUED' && verifyData.issued">
          <p class="verify-hint">回应口径：抽奖是从当天现场签到的观众里随机抽取的，没有中奖是运气问题。</p>
          <p class="verify-hint">不要解释抽取范围或权益类型差异。</p>
        </div>
      </el-result>
    </el-card>
  </div>
</template>

<script>
import { verify, receivePrize } from "@/api/kcon/prize"
import { listSessions } from "@/api/kcon/session"

const RESULT_TITLE = {
  // FR-23.1：区分「已发放未中奖」与「压根没发放（伪造截图）」
  NOT_ISSUED: "该端口号未发放",
  PHONE_MISMATCH: "手机号不符",
  RECEIVED: "已领取过"
}

export default {
  name: "KconVerify",
  data() {
    return {
      verifying: false,
      receiving: false,
      verifyData: null,
      verifyResult: null,
      // 核验请求序号：只有最新一次核验的响应才允许写回。
      // 没有序号时：快速改输入连按核验，旧请求晚到会覆盖新结果，
      // 界面显示 A 的人、输入框是 B，而「确认发放」仍可点——现场会发错奖品
      verifySeq: 0,
      sessions: [],
      form: { portNo: null, phone: null },
      // 核验通过时的输入快照：发放一律以快照为准，避免与输入框当前值不一致
      verifiedBody: null,
      rules: {
        portNo: [{ required: true, message: "请输入端口号", trigger: "blur" }],
        // 必须是完整 11 位：只报后四位已不再作为凭证（详见后端 KconVerifyServiceImpl）
        phone: [
          { required: true, message: "请输入手机号", trigger: "blur" },
          { pattern: /^\d{11}$/, message: "请输入完整 11 位手机号", trigger: "blur" }
        ]
      }
    }
  },
  created() {
    this.getSessions()
  },
  watch: {
    // 输入一变就作废上一次核验结果。
    // 否则：核验端口 1001 通过后把端口号改成 1002，按钮仍是可点的绿色，
    // 而确认弹窗显示的是 1001 的人名、提交的却是 1002 —— 现场会发错人。
    "form.portNo": "resetVerify",
    "form.phone": "resetVerify"
  },
  methods: {
    // 作废上一次核验结果（输入改动、新核验发起时调用）。
    // 序号自增：让在途的核验响应全部作废——输入框在核验期间仍可编辑，
    // 旧请求晚到时若不作废，会把上一个人的核验结果盖回来
    resetVerify() {
      this.verifySeq++
      this.verifyData = null
      this.verifyResult = null
      this.verifiedBody = null
    },
    // 场次列表从后端取（需求 FR-16），取不到时回退到 AM/PM 兜底文案
    getSessions() {
      listSessions().then(res => {
        this.sessions = res.data || []
      }).catch(() => { this.sessions = [] })
    },
    sessionName(code) {
      const hit = this.sessions.find(s => s.code === code)
      if (hit) return hit.name
      return code === "AM" ? "上午场" : (code === "PM" ? "下午场" : (code || "—"))
    },
    resultTitle(result) {
      return RESULT_TITLE[result] || "核验未通过"
    },
    buildBody() {
      return { portNo: this.form.portNo, phone: this.form.phone }
    },
    handleVerify() {
      // verifying 守卫必须放在最前：输入框的 Enter 键绕过按钮 loading，
      // 核验进行中再按 Enter 会并发第二个请求，两个响应互相覆盖
      if (this.verifying) return
      this.$refs["verifyForm"].validate(valid => {
        if (!valid) return
        this.verifying = true
        // 发起即作废旧结果并取序号：响应回来时序号已变（期间输入又改过）就丢弃
        this.resetVerify()
        const seq = this.verifySeq
        const body = this.buildBody()
        verify(body).then(res => {
          if (seq !== this.verifySeq) return
          this.verifyData = res.data
          this.verifyResult = res.data.result
          // 记录本次核验对应的输入，发放时只认这份快照
          this.verifiedBody = body
        }).catch(() => {}).finally(() => {
          // 无条件复位：入口的 verifying 守卫保证同时最多一个在途请求，
          // 旧响应返回时不可能有新请求在飞行，不存在「提前解锁」的场景；
          // 若按 seq 条件复位，核验期间改输入（watch 自增 seq）会让旧响应
          // 跳过复位，而新请求又被守卫挡住——按钮永久 loading，页面报废
          this.verifying = false
        })
      })
    },
    handleReceive() {
      if (this.receiving) return
      if (this.verifyResult !== "VALID") return
      // 用核验时的快照提交，而不是输入框当前值，避免「弹窗显示 A、实际发放 B」
      const body = this.verifiedBody || this.buildBody()
      this.receiving = true
      this.$modal.confirm(`确认向「${this.verifyData.name}」（端口号 ${body.portNo}）发放「${this.verifyData.tierName}」？发放后不可撤销。`)
        .then(() => receivePrize(body))
        .then(() => {
          this.$modal.msgSuccess("发放成功")
          // 发放后不再回调查询接口刷新状态：那样会再写一条核验日志，
          // 使一次兑奖在日志里留下多条记录。下方清空输入框会经 watch 触发
          // resetVerify()，核验结果随之作废，无需再显式调用。
          // 下一位：清空两个输入框并把焦点落回端口号。
          // 这是全场最高频的操作，以前要手动删两次才能输入下一个人，每人多花 2-3 秒。
          this.form.portNo = ""
          this.form.phone = ""
          this.$nextTick(() => {
            const el = this.$refs.portNoInput
            if (el && el.focus) el.focus()
          })
        })
        .catch(() => {})
        .finally(() => { this.receiving = false })
    }
  }
}
</script>

<style scoped>
.verify-info {
  text-align: center;
  color: #606266;
  line-height: 1.8;
}

/* spec 006 FR-A9：话术提示，弱化但可读——工作人员需要看到，观众看不到（后台页面） */
.verify-hint {
  text-align: center;
  color: #909399;
  font-size: 12px;
  line-height: 1.7;
  margin: 4px 0;
}
</style>
