<template>
  <div class="app-container" v-loading="initLoading">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      <div slot="title">
        本页用于管理端接管抽奖操作（切换场次 / 抽奖 / 作废）。大屏
        <b>/screen/</b> 仍在同步刷新同一份数据，两边看到的是同一个抽奖池。
      </div>
    </el-alert>

    <!-- 状态获取失败：给出重试入口，不让页面只剩一片空白 -->
    <el-alert v-if="loadError" type="error" :closable="false" show-icon style="margin-bottom: 16px;">
      <div slot="title">{{ loadError }}</div>
      <el-button size="mini" type="primary" icon="el-icon-refresh" @click="reload">重新加载</el-button>
    </el-alert>

    <!-- FR-19：当前场次可抽号数 / 今日已签到数 / 已中奖数 -->
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">可抽号数</div>
          <div class="stat-value" :class="poolEmpty ? 'danger' : 'primary'">{{ poolCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">今日已签到</div>
          <div class="stat-value success">{{ checkedInToday }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">已中奖</div>
          <div class="stat-value warning">{{ winnerCount }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 场次 + 抽奖操作 -->
    <el-card shadow="never" style="margin-top: 20px;">
      <div slot="header">
        <span>当前场次：<b>{{ sessionName || '未设置' }}</b></span>
      </div>

      <!-- FR-20：无人可抽时醒目提示 -->
      <div aria-live="polite">
        <el-alert v-if="poolEmpty" type="warning" :closable="false" show-icon
                  title="请先让观众签到" description="当前场次抽奖池为空，签到后端口号会自动进入奖池。"
                  style="margin-bottom: 16px;" />
        <el-alert v-else-if="tierAllDrawn" type="success" :closable="false" show-icon
                  title="本场次奖项已全部抽完" style="margin-bottom: 16px;" />
      </div>

      <!-- 当前应抽档位 -->
      <div class="current-tier">
        <span class="current-tier-label">当前应抽档位</span>
        <template v-if="currentTier">
          <b class="current-tier-name">{{ currentTier.tierName }}</b>
          <el-tag size="medium" type="warning" class="current-tier-count">
            ({{ currentTier.drawnCount || 0 }} / {{ currentTier.quantity }})
          </el-tag>
        </template>
        <span v-else class="text-muted">本场次奖项已抽完</span>
      </div>

      <!-- 场次切换：场次列表来自后端 sessions，前端不硬编码 AM/PM -->
      <div class="session-row">
        <span class="session-label">切换场次</span>
        <el-button-group v-if="sessions.length">
          <el-button v-for="s in sessions" :key="s.code" size="small"
                     :type="s.code === status.sessionCode ? 'primary' : 'default'"
                     :disabled="switching"
                     :aria-pressed="String(s.code === status.sessionCode)"
                     @click="handleSwitchSession(s.code)">{{ s.name }}</el-button>
        </el-button-group>
        <span v-else class="text-muted">未配置场次</span>
      </div>

      <!-- 操作按钮 -->
      <div class="action-row">
        <el-button type="primary" size="medium" icon="el-icon-present"
                   :loading="drawing" :disabled="drawDisabled" v-hasPermi="['kcon:draw:operate']"
                   @click="handleDraw">抽一档</el-button>
        <el-button type="danger" size="medium" icon="el-icon-close"
                   :loading="voiding" :disabled="!lastWinner" v-hasPermi="['kcon:draw:operate']"
                   @click="handleVoid">撤回最近一档</el-button>
      </div>
    </el-card>

    <!-- 上一次中奖 + 各档位奖池进度 -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="10">
        <el-card shadow="never" class="last-winner-card">
          <div slot="header"><span>最近一位中奖</span></div>
          <template v-if="lastWinner">
            <div class="winner-port">{{ lastWinner.portNo }}</div>
            <div class="winner-name">{{ lastWinner.name }}</div>
            <p class="winner-meta">{{ lastWinner.company || '—' }}</p>
            <p class="winner-meta">
              <el-tag size="mini" type="warning">{{ lastWinner.tierName }}</el-tag>
              <span class="text-muted win-time">{{ lastWinner.winTime }}</span>
            </p>
            <div class="text-muted void-hint">点「撤回最近一档」会把这一档整批撤回，端口号全部回池。</div>
          </template>
          <div v-else class="empty-tip">暂无中奖记录。抽出的号码会实时显示在这里。</div>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card shadow="never">
          <div slot="header"><span>各档位奖池进度</span></div>
          <el-table :data="tiers" size="mini">
            <div slot="empty" class="empty-tip">
              当前场次未配置奖项档位，请先到「奖品设置」添加后再抽奖。
            </div>
            <el-table-column label="奖项" prop="tierName" min-width="110" />
            <el-table-column label="已抽 / 总量" width="110" align="center">
              <template slot-scope="scope">{{ rowDrawn(scope.row) }} / {{ rowTotal(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="剩余" width="80" align="center">
              <template slot-scope="scope">{{ rowRemain(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template slot-scope="scope">
                <el-tag size="mini" :type="rowStatusType(scope.row)">{{ rowStatusText(scope.row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度" min-width="160">
              <template slot-scope="scope">
                <el-progress :percentage="rowPercent(scope.row)" :stroke-width="12" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 大屏设置：赞助商名称等展示内容，运营可直接改，不用改代码发版 -->
    <el-card shadow="never" style="margin-top: 20px;">
      <div slot="header">
        <span>抽奖大屏设置</span>
        <el-button type="text" icon="el-icon-refresh"
                   style="float: right; padding: 3px 0"
                   @click="loadSetting">重新读取</el-button>
      </div>
      <el-form label-width="110px" style="max-width: 720px;">
        <el-form-item label="赞助商（底部展示）">
          <el-input v-model="settingForm.sponsors" type="textarea" :rows="2"
                    placeholder="多个用逗号分隔，例如：某某科技, 某某网络。留空则大屏不显示赞助商条" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="small" icon="el-icon-check"
                     :loading="savingSetting" @click="saveSetting">保存</el-button>
          <span class="text-muted" style="margin-left: 10px; font-size: 12px;">
            大屏每 3 秒自动刷新，保存后约 3 秒生效
          </span>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon>
        <div slot="title" style="line-height: 1.7;">
          大屏地址：<b>{{ screenUrl }}</b><br />
          在这台笔记本上按 F11 全屏打开即可投屏；快捷键（空格抽奖 / 退格作废 / 1、2 切场次）见部署文档。
        </div>
      </el-alert>
    </el-card>

    <!-- 本次抽出：一次点击会抽出整档的人（如三等奖一次 3 位），整批列出来方便核对 -->
    <el-card v-if="lastBatch.length" shadow="never" style="margin-top: 20px;">
      <div slot="header">
        <span>本次抽出「{{ lastBatchTier }}」共 {{ lastBatch.length }} 人</span>
        <el-button type="text" style="float: right; padding: 3px 0"
                   @click="lastBatch = []">清除</el-button>
      </div>
      <el-table :data="lastBatch" size="mini">
        <el-table-column label="端口号" prop="portNo" width="110" align="center">
          <template slot-scope="scope">
            <b>{{ scope.row.portNo }}</b>
          </template>
        </el-table-column>
        <el-table-column label="姓名" prop="name" width="150" />
        <el-table-column label="公司" prop="company" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { getDrawStatus, doDraw, voidDrawBatch, switchDrawSession, getDrawSetting, saveDrawSetting } from "@/api/kcon/draw"

// 轮询间隔：与「现场看板」一致，签到台/控制台挂在副屏上时数字自己走
const POLL_INTERVAL = 5000

export default {
  name: "KconDrawConsole",
  data() {
    return {
      initLoading: true,
      loadError: "",
      // /admin/draw/status 返回体：sessionCode / sessionName / checkedInToday /
      // poolCount / winnerCount / sessions / currentTier / tiers / lastWinner
      status: {},
      drawing: false,
      voiding: false,
      switching: false,
      timer: null,
      polling: false,
      // 请求序号：用于丢弃过期响应，避免慢返回的轮询结果盖掉刚操作完的刷新结果
      seq: 0,
      appliedSeq: 0,
      // 大屏设置（赞助商等）
      settingForm: { sponsors: "" },
      savingSetting: false,
      // 本次抽出的整批（一次点击会抽出整档的人，如三等奖一次出 3 位）
      lastBatch: [],
      lastBatchTier: ""
    }
  },
  computed: {
    sessions() {
      return this.status.sessions || []
    },
    tiers() {
      return this.status.tiers || []
    },
    currentTier() {
      return this.status.currentTier || null
    },
    /** 大屏访问地址：与后端 qrcode 接口同源，按当前页面 host 推导 */
    screenUrl() {
      const host = window.location.origin.replace(/\/admin\/?$/, "")
      return `${host}/screen/`
    },
    lastWinner() {
      return this.status.lastWinner || null
    },
    sessionName() {
      return this.status.sessionName || ""
    },
    poolCount() {
      return this.status.poolCount || 0
    },
    checkedInToday() {
      return this.status.checkedInToday || 0
    },
    winnerCount() {
      return this.status.winnerCount || 0
    },
    // FR-20：奖池为空
    poolEmpty() {
      return this.poolCount <= 0
    },
    // 后端在无档位可抽时不会返回 currentTier 字段
    tierAllDrawn() {
      return !this.currentTier
    },
    operating() {
      return this.drawing || this.voiding || this.switching
    },
    drawDisabled() {
      return this.poolEmpty || this.tierAllDrawn || this.operating
    }
  },
  created() {
    this.getStatus()
    this.loadSetting()
    this.timer = setInterval(this.getStatus, POLL_INTERVAL)
  },
  beforeDestroy() {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  },
  methods: {
    // ---------- 大屏设置 ----------
    loadSetting() {
      getDrawSetting().then(res => {
        const list = res.data?.sponsors || res.sponsors || []
        this.settingForm.sponsors = Array.isArray(list) ? list.join(", ") : String(list || "")
      }).catch(() => {})
    },
    saveSetting() {
      this.savingSetting = true
      saveDrawSetting({ sponsors: this.settingForm.sponsors })
        .then(() => {
          this.$modal.msgSuccess("已保存，大屏约 3 秒后自动生效")
          this.fetchStatus()
        })
        .catch(() => {})
        .finally(() => { this.savingSetting = false })
    },
    // 轮询入口：上一次请求未回、或正在操作时跳过本轮，
    // 避免与操作返回的状态互相覆盖（过期响应由 applyStatus 的序号判断丢弃）
    getStatus() {
      if (this.polling || this.operating) return
      this.fetchStatus()
    },
    reload() {
      this.loadError = ""
      this.initLoading = true
      this.fetchStatus()
    },
    // 真正的请求入口，getStatus / reload / 操作失败兜底都走这里
    fetchStatus() {
      this.polling = true
      const seq = ++this.seq
      return getDrawStatus().then(res => {
        this.applyStatus(res.data, seq)
        this.loadError = ""
      }).catch(() => {
        // 错误提示由 request.js 统一弹出，这里只记录状态并保留上一次的有效数据
        this.loadError = "抽奖池状态获取失败，请确认后端服务与网络后重试。"
      }).finally(() => {
        this.polling = false
        this.initLoading = false
      })
    },
    applyStatus(data, seq) {
      if (!data) return
      if (seq !== undefined) {
        if (seq < this.appliedSeq) return
        this.appliedSeq = seq
      }
      this.status = data
    },
    // ---------------- 操作 ----------------
    handleDraw() {
      if (this.poolEmpty) {
        this.$modal.msgWarning("请先让观众签到")
        return
      }
      if (this.tierAllDrawn) {
        this.$modal.msgWarning("该场次奖项已抽完")
        return
      }
      const tierName = this.currentTier.tierName
      const remain = this.rowRemain(this.currentTier)
      this.$modal.confirm(
        `将一次抽出「${tierName}」剩余的全部 ${remain} 位（当前场次可抽号数 ${this.poolCount}，` +
        `不够时只抽得出来的那些）。抽出后大屏会逐个揭晓。确认继续？`
      )
        .then(() => this.submitDraw())
        .catch(() => {})
    },
    submitDraw() {
      this.drawing = true
      return doDraw({
        sessionCode: this.status.sessionCode,
        prizeId: this.currentTier.id
      }).then(res => {
        const data = res.data || {}
        const ws = data.winners || []
        // 一次点击抽出整档的人：把整批列出来，方便操作员逐位核对
        this.lastBatch = ws
        this.lastBatchTier = data.tierName || ""
        // 抽奖接口现在会带回最新状态，这里能真正乐观更新。
        // 旧版响应里没有 status，applyStatus 拿到 undefined 等于什么都没做，
        // 结果就是「点了抽奖要等下一轮 5 秒轮询，数字和进度条才动」。
        this.applyStatus(data.status, ++this.seq)
        if (ws.length) {
          this.$modal.msgSuccess(
            `「${data.tierName}」一次抽出 ${ws.length} 人，大屏正在逐个揭晓`
          )
        }
        if (data.shortfall > 0) {
          this.$modal.msgWarning(
            `可抽号数不足，还差 ${data.shortfall} 位未能抽出，请让更多观众签到后再抽`
          )
        }
      }).catch(() => {
        // 抽奖失败（如池被抽空）后状态可能已变，兜底刷新一次
        this.fetchStatus()
      }).finally(() => {
        this.drawing = false
      })
    },
    handleVoid() {
      const w = this.lastWinner
      if (!w) {
        this.$modal.msgWarning("当前场次没有可撤回的中奖记录")
        return
      }
      // 一次点击抽出的是整档（可能多人），撤回也按整档：把这一档最后一次抽出的全部人作废。
      // 若该档已有人领奖，后端会拒绝整档撤回并报错，避免静默漏撤。
      const tier = w.tierName || "该档位"
      this.$modal.confirm(
        `确认撤回「${tier}」最近抽出的整档中奖记录？撤回后这些端口号全部回到抽奖池，可被再次抽中。`
      )
        .then(() => this.submitVoid())
        .catch(() => {})
    },
    submitVoid() {
      this.voiding = true
      // 按当前场次撤回最近一档；sessionCode 为空时后端按全场最近一档处理
      return voidDrawBatch({ sessionCode: this.status.sessionCode }).then(res => {
        const data = res.data || {}
        this.applyStatus(data, ++this.seq)
        const n = data.voidedCount
        this.$modal.msgSuccess(n ? `已撤回 ${n} 人，端口号已回池` : "撤回成功，端口号已回池")
      }).catch(() => {
        this.fetchStatus()
      }).finally(() => {
        this.voiding = false
      })
    },
    handleSwitchSession(code) {
      if (!code || code === this.status.sessionCode) return
      const target = this.sessions.find(s => s.code === code)
      const name = target ? target.name : code
      this.$modal.confirm(`确认把当前场次切换为「${name}」？切换后抽奖池按新场次重新计算，大屏会同步切换。`)
        .then(() => this.submitSwitchSession(code))
        .catch(() => {})
    },
    submitSwitchSession(code) {
      this.switching = true
      return switchDrawSession({ sessionCode: code }).then(res => {
        this.applyStatus(res.data, ++this.seq)
        this.$modal.msgSuccess("已切换到「" + this.sessionName + "」")
      }).catch(() => {
        this.fetchStatus()
      }).finally(() => {
        this.switching = false
      })
    },
    // ---------------- 档位行计算 ----------------
    rowDrawn(row) {
      return row.drawnCount || 0
    },
    rowTotal(row) {
      return row.quantity || 0
    },
    rowRemain(row) {
      return Math.max(this.rowTotal(row) - this.rowDrawn(row), 0)
    },
    rowPercent(row) {
      const total = this.rowTotal(row)
      return total > 0 ? Math.min(Math.round(this.rowDrawn(row) / total * 100), 100) : 0
    },
    rowFinished(row) {
      return this.rowTotal(row) > 0 && this.rowDrawn(row) >= this.rowTotal(row)
    },
    rowStatusText(row) {
      return this.rowFinished(row) ? "已抽完" : "进行中"
    },
    rowStatusType(row) {
      return this.rowFinished(row) ? "info" : "warning"
    }
  }
}
</script>

<style scoped>
/* 统计卡片：与「现场看板」dashboard/index.vue 保持同一套写法 */
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
.stat-value.danger { color: #F56C6C; }

.current-tier {
  display: flex;
  align-items: center;
  font-size: 15px;
}
.current-tier-label {
  color: #909399;
  margin-right: 12px;
}
.current-tier-name {
  font-size: 20px;
  color: #303133;
}
.current-tier-count {
  margin-left: 10px;
}

.session-row {
  display: flex;
  align-items: center;
  margin-top: 16px;
}
.session-label {
  color: #909399;
  margin-right: 12px;
}

.action-row {
  margin-top: 20px;
}
.action-row .el-button + .el-button {
  margin-left: 12px;
}

.last-winner-card {
  min-height: 260px;
}
.winner-port {
  font-size: 40px;
  font-weight: 700;
  color: #d4342a;
  line-height: 1.2;
}
.winner-name {
  font-size: 18px;
  font-weight: 600;
  margin-top: 6px;
  color: #303133;
}
.winner-meta {
  margin: 6px 0;
  font-size: 14px;
  color: #606266;
}
.win-time {
  margin-left: 8px;
  font-size: 13px;
}
.void-hint {
  margin-top: 12px;
  font-size: 13px;
}

.empty-tip {
  color: #c0c4cc;
  font-size: 13px;
  padding: 12px 0;
  text-align: center;
}
</style>