<template>
  <div class="app-container">
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px;">
      <div slot="title" style="line-height: 1.8;">
        <b>恢复操作不可逆</b>：从 .sql 备份恢复会用备份内容<b>整体覆盖</b>当前数据库，现场已签到/已中奖的数据将丢失。<br />
        系统会在恢复前自动导出一份当前数据存档，存档失败则中止恢复。<br />
        仅超级管理员可操作，所有导出与恢复都会记入操作日志。
      </div>
    </el-alert>

    <el-row :gutter="20">
      <!-- 导出 -->
      <el-col :span="12">
        <el-card shadow="never">
          <div slot="header"><b>全量数据导出</b></div>
          <p class="text-muted" style="line-height: 1.9;">
            把整个数据库（名单、签到、端口号池、中奖记录、奖品、核验日志）导出为
            <code>.sql</code> 文件，用于换电脑迁移或长期存档。
          </p>
          <el-button
            type="primary"
            icon="el-icon-download"
            :loading="exporting"
            @click="handleExport"
          >导 出 全 量 数 据</el-button>
          <p class="text-muted" style="margin-top: 12px; font-size: 12px;">
            提示：数据量越大导出越慢，请耐心等待，不要刷新页面。
          </p>
        </el-card>
      </el-col>

      <!-- 恢复 -->
      <el-col :span="12">
        <el-card shadow="never">
          <div slot="header"><b>从备份恢复</b></div>
          <el-upload
            ref="upload"
            :auto-upload="false"
            :limit="1"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
            accept=".sql"
            drag
            action="#"
          >
            <i class="el-icon-upload"></i>
            <div class="el-upload__text">将 <b>.sql</b> 备份拖到此处，或<em>点击选择</em></div>
            <div class="el-upload__tip" slot="tip">上限 20MB，建议先用「导出」得到备份样例</div>
          </el-upload>

          <el-button
            type="danger"
            icon="el-icon-refresh-left"
            :loading="restoring"
            :disabled="!restoreFile"
            style="margin-top: 14px;"
            @click="handleRestore"
          >恢 复 数 据</el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
export default {
  name: "KconMigrate",
  data() {
    return {
      exporting: false,
      restoring: false,
      restoreFile: null
    }
  },
  methods: {
    handleExport() {
      this.$confirm(
        "将导出全量数据库为 .sql 文件，数据量大时可能需要较长时间。确认导出？",
        "全量导出",
        { type: "warning", confirmButtonText: "开始导出", cancelButtonText: "取消" }
      ).then(() => {
        this.exporting = true
        // 走原生 location 下载，避免 axios 对大 blob 的内存占用；
        // 但接口需登录态，所以用带 token 的表单提交方式触发浏览器下载
        const token = this.$store.getters.token
        const form = document.createElement("form")
        form.method = "get"
        form.action = process.env.VUE_APP_BASE_API + "/admin/migrate/export"
        const hidden = document.createElement("input")
        hidden.type = "hidden"
        hidden.name = "Authorization"
        hidden.value = "Bearer " + token
        form.appendChild(hidden)
        document.body.appendChild(form)
        form.submit()
        document.body.removeChild(form)
        this.exporting = false
        this.$modal.msgSuccess("已开始下载，请查看浏览器下载列表")
      }).catch(() => {})
    },
    onFileChange(file) {
      const raw = file.raw
      if (!raw.name.toLowerCase().endsWith(".sql")) {
        this.$modal.msgError("只支持 .sql 文件")
        this.restoreFile = null
        return
      }
      this.restoreFile = raw
    },
    onFileRemove() {
      this.restoreFile = null
    },
    handleRestore() {
      if (!this.restoreFile) {
        this.$modal.msgWarning("请先选择要恢复的 .sql 文件")
        return
      }
      this.$prompt(
        "此操作会用备份内容整体覆盖当前数据库，现场已签到与已中奖的数据将丢失。\n" +
        "系统会先自动存档当前数据。请输入「确认恢复」四个字以继续。",
        "危险操作确认",
        {
          type: "error",
          confirmButtonText: "我已了解风险",
          cancelButtonText: "取消",
          inputPlaceholder: "确认恢复",
          inputValidator: (v) => (v === "确认恢复" ? true : "请输入「确认恢复」四个字")
        }
      ).then(() => {
        const formData = new FormData()
        formData.append("file", this.restoreFile)
        this.restoring = true
        import("@/utils/request").then(({ default: request }) => {
          request({
            url: "/admin/migrate/restore",
            method: "post",
            data: formData,
            headers: { "Content-Type": "multipart/form-data" },
            timeout: 600000
          }).then(res => {
            this.$modal.msgSuccess(res.msg || "恢复完成")
            if (this.$refs.upload) this.$refs.upload.clearFiles()
            this.restoreFile = null
          }).catch(() => {}).finally(() => { this.restoring = false })
        })
      }).catch(() => {})
    }
  }
}
</script>