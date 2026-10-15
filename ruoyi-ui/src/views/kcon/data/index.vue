<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ---------------- 数据清场 ---------------- -->
      <el-tab-pane label="数据清场" name="reset">
        <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px;">
          <div slot="title" style="line-height: 1.8;">
            清场仅用于正式开场前重置数据：名单、签到记录、中奖记录、核验日志全部清空，端口号池归零，奖品已抽数归零。<br />
            操作不可恢复，请先导出签到明细备份。
          </div>
        </el-alert>

        <el-card shadow="never" style="max-width: 560px;">
          <div slot="header"><b>清空现场数据</b></div>
          <p style="color: #606266; line-height: 1.8;">
            请输入「<b style="color: #f56c6c;">清场</b>」二字确认执行：
          </p>
          <el-input v-model="confirmText" placeholder="清场" maxlength="4" style="width: 200px;" />
          <div style="margin-top: 20px;">
            <el-button type="danger" icon="el-icon-delete" :loading="resetting" :disabled="confirmText !== '清场'"
                       v-hasPermi="['kcon:data:reset']"
                       @click="handleReset">执 行 清 场</el-button>
            <el-button v-hasPermi="['kcon:attendee:export']" icon="el-icon-download" @click="handleExportSignSheet">
              先导出签到明细
            </el-button>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- ---------------- 数据迁移 ---------------- -->
      <el-tab-pane label="数据迁移" name="migrate">
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
                v-hasRole="['admin']"
                @click="handleExportAll"
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
                :on-exceed="onFileExceed"
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
                v-hasRole="['admin']"
                @click="handleRestore"
              >恢 复 数 据</el-button>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import { resetData } from "@/api/kcon/data"

export default {
  name: "KconData",
  data() {
    return {
      activeTab: "reset",
      // 清场
      confirmText: "",
      resetting: false,
      // 迁移
      exporting: false,
      restoring: false,
      restoreFile: null
    }
  },
  methods: {
    /* ==================== 数据清场 ==================== */
    handleReset() {
      if (this.confirmText !== "清场") return
      this.$confirm(
        "将清空名单、签到、中奖、核验日志，并把端口号池与奖品已抽数归零。确认执行？",
        "数据清场",
        { type: "warning", confirmButtonText: "确认清场", cancelButtonText: "取消" }
      ).then(() => {
        this.resetting = true
        // 必须挂 catch：请求失败时拦截器已弹错，但这里若不兜底，
        // resetData() 的 reject 会成为 unhandled rejection 打到控制台；
        // 且失败时不能清空 confirmText，否则用户要在输入框重敲「清场」。
        resetData().then(res => {
          this.$modal.msgSuccess(res.msg || "清场完成")
          this.confirmText = ""
        }).catch(() => {
          this.$modal.msgError("清场失败，数据未变更，请重试。")
        }).finally(() => { this.resetting = false })
      }).catch(() => {})
    },
    // 清场前先导出一份签到明细，避免误清后无法追溯
    handleExportSignSheet() {
      this.download("/admin/data/export", {}, `KCon2026签到明细_${new Date().getTime()}.xlsx`)
    },

    /* ==================== 数据迁移 ==================== */
    handleExportAll() {
      this.$confirm(
        "将导出全量数据库为 .sql 文件，数据量大时可能需要较长时间。确认导出？",
        "全量导出",
        { type: "warning", confirmButtonText: "开始导出", cancelButtonText: "取消" }
      ).then(() => {
        // 必须走项目统一的 download()（utils/request：POST + blob + Authorization 请求头）。
        // 旧实现用原生 <form method="get"> 把 Token 放进隐藏域：
        //   1) 后端只从请求头读取 Token → 必然 401，导出根本下不来；
        //   2) Token 会出现在 URL 里，被浏览器历史记录与 nginx access log 留存。
        this.exporting = true
        this.download(
          "/admin/migrate/export",
          {},
          `kcon_lucky_${new Date().getTime()}.sql`
        ).finally(() => {
          this.exporting = false
        })
      }).catch(() => {})
    },
    onFileChange(file) {
      const raw = file.raw
      if (!raw.name.toLowerCase().endsWith(".sql")) {
        this.$modal.msgError("只支持 .sql 文件")
        this.restoreFile = null
        // 必须把文件从 upload 的列表里踢掉：limit=1 且没配 on-exceed 时，
        // 这个文件会一直占着唯一名额，用户再选文件会被 limit 静默拦下、
        // 「恢复数据」按钮永久 disabled，只能刷新页面。accept=".sql" 拦不住拖拽。
        this.$nextTick(() => {
          if (this.$refs.upload) this.$refs.upload.clearFiles()
        })
        return
      }
      this.restoreFile = raw
    },
    onFileRemove() {
      this.restoreFile = null
    },
    // limit=1 且未配 on-exceed 时，超出会被 element静默丢弃、界面毫无反应，
    // 用户会以为点击失效。补一句提示把原因说清。
    onFileExceed() {
      this.$modal.msgWarning("每次恢复只能选择一个文件，请先移除已选文件")
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
          }).catch(() => {
            this.$modal.msgError("恢复失败，当前数据未被覆盖，请检查文件后重试。")
          }).finally(() => { this.restoring = false })
        }).catch(() => {
          // 动态 import 失败（网络中断导致 chunk 拉不下来）时，
          // request 根本没发出去，必须自己收尾 restoring，否则按钮永久锁死无提示。
          this.restoring = false
          this.$modal.msgError("上传模块加载失败，请刷新页面后重试。")
        })
      }).catch(() => {})
    }
  }
}
</script>
