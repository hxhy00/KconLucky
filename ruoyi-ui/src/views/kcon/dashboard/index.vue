<template>
  <div class="app-container">
    <!-- 现场快捷入口：签到台/兑奖台高频只用这几个页面，
         免得每次都在左侧 9 个菜单里找。登录后第一屏就能看到。 -->
    <div class="quick-entry">
      <div
        v-for="q in quickEntries"
        :key="q.path"
        class="quick-item"
        @click="$router.push(q.path)"
      >
        <i :class="q.icon" />
        <div class="quick-text">
          <div class="quick-title">{{ q.title }}</div>
          <div class="quick-desc">{{ q.desc }}</div>
        </div>
      </div>
    </div>

    <!-- 核心指标 -->
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">名单总人数</div>
          <div class="stat-value">{{ stat.total || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">已签到</div>
          <div class="stat-value success">{{ stat.signed || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">未签到</div>
          <div class="stat-value warning">{{ stat.unsigned || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">签到率</div>
          <div class="stat-value primary">{{ stat.signRate || 0 }}%</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 签到进度 -->
    <el-card shadow="never" style="margin-top: 20px;">
      <div slot="header"><span>签到进度</span></div>
      <el-progress
        :text-inside="true"
        :stroke-width="26"
        :percentage="Math.min(stat.signRate || 0, 100)"
        status="success"
      />
    </el-card>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 首次/多次参加 -->
      <el-col :span="8">
        <el-card shadow="never">
          <div slot="header"><span>参会届数分布</span></div>
          <div class="dist-row">
            <span>首次参加</span>
            <b>{{ attendDist.firstTime || 0 }}</b>
          </div>
          <div class="dist-row">
            <span>多次参加</span>
            <b>{{ attendDist.returning || 0 }}</b>
          </div>
          <div class="dist-row">
            <span>未知（未填写）</span>
            <b class="text-muted">{{ attendDist.unknown || 0 }}</b>
          </div>
        </el-card>
      </el-col>

      <!-- 公司 Top -->
      <el-col :span="16">
        <el-card shadow="never">
          <div slot="header">
            <span>公司参会 Top 10</span>
            <el-button style="float: right; padding: 3px 0" type="text" @click="getStat">刷新</el-button>
          </div>
          <el-table :data="stat.topCompanies || []" size="mini" max-height="300">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column label="公司" prop="company" show-overflow-tooltip />
            <el-table-column label="参会人数" prop="total" width="100" align="center" />
            <el-table-column label="已签到" prop="signed" width="100" align="center" />
          </el-table>
          <div v-if="!(stat.topCompanies || []).length" class="empty-tip">
            暂无数据。公司字段为选填，导入后可在名单页补录。
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 签到二维码 -->
    <el-card shadow="never" style="margin-top: 20px;">
      <div slot="header"><span>现场签到二维码</span></div>
      <div class="qr-wrap">
        <img v-if="qrSrc" :src="qrSrc" class="qr-img" alt="签到二维码" />
        <div v-else class="qr-placeholder">二维码加载中…</div>
        <div class="qr-info">
          <p>观众扫描此二维码即可自助签到。</p>
          <p>签到地址：<b>{{ signUrl }}</b></p>
          <p class="text-muted">提示：观众手机能访问上方地址即可扫码。二维码由本机生成，不依赖外部服务。</p>
          <el-button type="primary" size="small" icon="el-icon-download" @click="downloadQr">
            下载二维码
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { dashboard, qrcodeUrl, fetchQrcodeBlob } from "@/api/kcon/attendee"

export default {
  name: "KconDashboard",
  data() {
    return {
      stat: {},
      signUrl: "",
      qrSrc: "",
      qrObjectUrl: "",
      timer: null,
      quickEntries: [
        { title: "兑奖核验", desc: "端口号 + 手机号核验发放", path: "/kcon/verify", icon: "el-icon-search" },
        { title: "抽奖控制台", desc: "切场次 · 抽奖 · 看剩余号数", path: "/kcon/draw", icon: "el-icon-guide" },
        { title: "参会名单", desc: "导入名单 · 打印签到表", path: "/kcon/attendee", icon: "el-icon-user" },
        { title: "中奖记录", desc: "中奖名单与领取状态", path: "/kcon/winner", icon: "el-icon-medal" }
      ]
    }
  },
  computed: {
    attendDist() {
      return this.stat.attendCountDist || {}
    }
  },
  created() {
    this.getStat()
    this.getQr()
    // FR-15：看板每 5 秒自动刷新，签到台挂在副屏上时数字要自己走
    this.timer = setInterval(this.getStat, 5000)
  },
  beforeDestroy() {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
    if (this.qrObjectUrl) {
      URL.revokeObjectURL(this.qrObjectUrl)
      this.qrObjectUrl = ""
    }
  },
  methods: {
    getStat() {
      dashboard().then(res => {
        this.stat = res.data || {}
      })
    },
    getQr() {
      qrcodeUrl().then(res => {
        this.signUrl = res.data
      })
      // 二维码接口在鉴权之后，裸 <img src> 不会带 Token 会拿到 401，
      // 必须走 request 拿 blob 再转成 objectURL（离线可用，图片由后端本地生成）
      fetchQrcodeBlob().then(blob => {
        if (this.qrObjectUrl) {
          URL.revokeObjectURL(this.qrObjectUrl)
        }
        this.qrObjectUrl = URL.createObjectURL(blob)
        this.qrSrc = this.qrObjectUrl
      }).catch(() => {})
    },
    downloadQr() {
      if (!this.qrSrc) {
        this.$modal.msgWarning("二维码尚未加载完成，请稍候")
        return
      }
      const a = document.createElement("a")
      a.download = "KCon2026签到二维码.png"
      a.href = this.qrSrc
      a.click()
    }
  }
}
</script>

<style scoped>
/* 现场快捷入口 */
.quick-entry {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.quick-item {
  flex: 1;
  min-width: 190px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #e8e8ec;
  border-left: 3px solid #d4342a;
  border-radius: 8px;
  cursor: pointer;
  transition: box-shadow .18s, transform .18s, border-color .18s;
}
.quick-item:hover {
  border-color: #d4342a;
  box-shadow: 0 4px 14px rgba(212, 52, 42, .16);
  transform: translateY(-2px);
}
.quick-item > i {
  font-size: 26px;
  color: #d4342a;
  flex-shrink: 0;
}
.quick-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  line-height: 1.4;
}
.quick-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.stat-card {
  text-align: center;
}
.stat-label {
  color: #909399;
  font-size: 14px;
}
.stat-value {
  font-size: 34px;
  font-weight: 700;
  margin-top: 8px;
  color: #303133;
}
.stat-value.success { color: #67C23A; }
.stat-value.warning { color: #E6A23C; }
.stat-value.primary { color: #d4342a; }

.dist-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px dashed #ebeef5;
  font-size: 14px;
}
.dist-row b { font-size: 18px; }

.empty-tip {
  color: #c0c4cc;
  font-size: 13px;
  padding: 12px 0;
  text-align: center;
}

.qr-wrap {
  display: flex;
  align-items: center;
  gap: 30px;
}
.qr-img,
.qr-placeholder {
  width: 240px;
  height: 240px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.qr-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 13px;
}
.qr-info p {
  margin: 6px 0;
  font-size: 14px;
}
</style>
