<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      <div slot="title">
        每个端口号只中一次；已领取的记录不可作废。作废后端口号自动回到抽奖池。
      </div>
    </el-alert>

    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="68px">
      <el-form-item label="场次" prop="sessionCode">
        <el-select v-model="queryParams.sessionCode" placeholder="全部" clearable>
          <el-option v-for="s in sessions" :key="s.code" :label="s.name" :value="s.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable>
          <el-option label="已中奖" :value="0" />
          <el-option label="已领取" :value="1" />
          <el-option label="已作废" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="端口号" prop="portNo">
        <el-input v-model="queryParams.portNo" placeholder="端口号" clearable style="width: 140px;" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar :showSearch="false" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="winnerList">
      <el-table-column label="端口号" prop="portNo" width="100" align="center" />
      <el-table-column label="姓名" prop="name" width="110" />
      <el-table-column label="公司" prop="company" show-overflow-tooltip />
      <el-table-column label="邮箱" prop="email" min-width="150" show-overflow-tooltip>
        <template slot-scope="scope">
          <span v-if="scope.row.email">{{ scope.row.email }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="手机号" prop="phoneMask" width="130" align="center" />
      <el-table-column label="场次" width="150" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.sessionCode === 'AM' ? 'warning' : 'primary'">
            {{ sessionName(scope.row.sessionCode) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="奖项" prop="tierName" width="110" />
      <el-table-column label="状态" width="90" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="statusTagType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="中奖时间" prop="winTime" width="160" align="center" />
      <el-table-column label="领取时间" prop="receiveTime" width="160" align="center" />
      <el-table-column label="操作" width="100" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-close" @click="handleVoid(scope.row)"
                     v-hasPermi="['kcon:winner:void']"
                     :loading="voidingId === scope.row.id"
                     :disabled="scope.row.status !== 0 || voidingId != null">作废</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum"
                :limit.sync="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script>
import { listWinner, voidWinner } from "@/api/kcon/prize"
import { listSessions } from "@/api/kcon/session"

export default {
  name: "KconWinner",
  data() {
    return {
      loading: true,
      winnerList: [],
      total: 0,
      // 正在作废的行 id：请求在途期间该行按钮转圈，避免操作员以为没点上而反复点
      voidingId: null,
      // 请求序号：快速翻页/筛选时，慢返回的旧响应不能盖掉新结果
      listSeq: 0,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        sessionCode: null,
        status: null,
        portNo: null
      },
      sessions: []
    }
  },
  created() {
    this.getSessions()
    this.getList()
  },
  methods: {
    // 场次列表从后端取，避免前端硬编码与库里的场次名不一致（需求 FR-16）
    getSessions() {
      listSessions().then(res => {
        this.sessions = res.data || []
      }).catch(() => { this.sessions = [] })
    },
    sessionName(code) {
      const hit = this.sessions.find(s => s.code === code)
      return hit ? hit.name : (code === 'AM' ? '上午场' : (code === 'PM' ? '下午场' : (code || '—')))
    },
    getList() {
      this.loading = true
      const seq = ++this.listSeq
      listWinner(this.queryParams).then(res => {
        if (seq !== this.listSeq) return
        this.winnerList = res.rows || []
        this.total = res.total
      }).catch(() => {
        // request.js 已统一提示，这里只兜底
      }).finally(() => {
        if (seq === this.listSeq) this.loading = false
      })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    statusText(status) {
      return { 0: "已中奖", 1: "已领取", 2: "已作废" }[status] || "未知"
    },
    statusTagType(status) {
      return { 0: "warning", 1: "success", 2: "info" }[status] || "info"
    },
    handleVoid(row) {
      if (this.voidingId != null) return
      this.$modal.confirm(`确认作废「${row.name}」（端口号 ${row.portNo}）的中奖记录？作废后该端口号回到抽奖池，可被再次抽中。`)
        .then(() => {
          this.voidingId = row.id
          return voidWinner(row.id)
        })
        .then(() => {
          this.getList()
          this.$modal.msgSuccess("作废成功，端口号已回池")
        })
        .catch(() => {})
        .finally(() => { this.voidingId = null })
    }
  }
}
</script>
