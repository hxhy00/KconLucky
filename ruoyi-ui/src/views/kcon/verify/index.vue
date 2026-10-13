<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      <div slot="title">
        双重校验防冒领：端口号必须中奖且未领取，手机号必须与持号人一致（支持全号或后四位）。
      </div>
    </el-alert>

    <el-card shadow="never" style="max-width: 560px;">
      <div slot="header"><b>兑奖核验</b></div>
      <el-form ref="verifyForm" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="端口号" prop="portNo">
          <el-input v-model="form.portNo" placeholder="凭条上的端口号" maxlength="10"
                    @keyup.enter.native="handleVerify" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="全号或后四位" maxlength="11"
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
          <p><b>{{ verifyData.name }}</b>（{{ verifyData.phoneMask }}）</p>
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
        <div slot="extra" v-if="verifyData.holderMask">
          <p class="verify-info">持号人手机号：{{ verifyData.holderMask }}</p>
        </div>
        <!-- 需求 FR-24.2：明确告知领取时间与领取人 -->
        <div slot="extra" v-if="verifyResult === 'RECEIVED' && verifyData.receiveTime">
          <p class="verify-info">领取时间：{{ verifyData.receiveTime }}</p>
          <p class="verify-info" v-if="verifyData.receiveOperator">领取操作人：{{ verifyData.receiveOperator }}</p>
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
      sessions: [],
      form: { portNo: null, phone: null },
      // 核验通过时的输入快照：发放一律以快照为准，避免与输入框当前值不一致
      verifiedBody: null,
      rules: {
        portNo: [{ required: true, message: "请输入端口号", trigger: "blur" }],
        phone: [{ required: true, message: "请输入手机号", trigger: "blur" }]
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
    // 作废上一次核验结果（输入改动、核验失败时调用）
    resetVerify() {
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
      this.$refs["verifyForm"].validate(valid => {
        if (!valid) return
        this.verifying = true
        const body = this.buildBody()
        verify(body).then(res => {
          this.verifyData = res.data
          this.verifyResult = res.data.result
          // 记录本次核验对应的输入，发放时只认这份快照
          this.verifiedBody = body
        }).catch(() => {
          this.resetVerify()
        }).finally(() => { this.verifying = false })
      })
    },
    handleReceive() {
      if (this.verifyResult !== "VALID") return
      // 用核验时的快照提交，而不是输入框当前值，避免「弹窗显示 A、实际发放 B」
      const body = this.verifiedBody || this.buildBody()
      this.receiving = true
      this.$modal.confirm(`确认向「${this.verifyData.name}」（端口号 ${body.portNo}）发放「${this.verifyData.tierName}」？发放后不可撤销。`)
        .then(() => receivePrize(body))
        .then(() => {
          this.$modal.msgSuccess("发放成功")
          // 发放后重新核验，刷新状态为已领取
          return verify(body)
        })
        .then(res => {
          this.verifyData = res.data
          this.verifyResult = res.data.result
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
</style>
