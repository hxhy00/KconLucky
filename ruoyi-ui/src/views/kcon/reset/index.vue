<template>
  <div class="app-container">
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px;">
      <div slot="title">
        清场仅用于正式开场前重置数据：名单、签到记录、中奖记录、核验日志全部清空，端口号池归零，奖品已抽数归零。
        操作不可恢复，请先导出签到明细备份。
      </div>
    </el-alert>

    <el-card shadow="never" style="max-width: 560px;">
      <div slot="header"><b>数据清场</b></div>
      <p style="color: #606266; line-height: 1.8;">
        请输入「<b style="color: #f56c6c;">清场</b>」二字确认执行：
      </p>
      <el-input v-model="confirmText" placeholder="清场" maxlength="4" style="width: 200px;" />
      <div style="margin-top: 20px;">
        <el-button type="danger" icon="el-icon-delete" :loading="resetting" :disabled="confirmText !== '清场'"
                   v-hasPermi="['kcon:data:reset']"
                   @click="handleReset">执 行 清 场</el-button>
        <el-button v-hasPermi="['kcon:attendee:export']" icon="el-icon-download" @click="handleExport">
          先导出签到明细
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
import { resetData } from "@/api/kcon/data"

export default {
  name: "KconReset",
  data() {
    return {
      confirmText: "",
      resetting: false
    }
  },
  methods: {
    handleReset() {
      if (this.confirmText !== "清场") return
      this.resetting = true
      resetData().then(res => {
        this.$modal.msgSuccess(res.msg || "清场完成")
        this.confirmText = ""
      }).finally(() => { this.resetting = false })
    },
    handleExport() {
      this.download("/admin/data/export", {}, `KCon2026签到明细_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>
