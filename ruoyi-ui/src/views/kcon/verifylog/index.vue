<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      <div slot="title">
        每次核验与发放都会自动留痕，未通过的尝试同样记录，可据此追溯奖品由谁核验发放。
      </div>
    </el-alert>
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="端口号" prop="portNo">
        <el-input v-model="queryParams.portNo" placeholder="端口号（精确匹配）" clearable
                  style="width: 160px;" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="核验结果" prop="result">
        <el-select v-model="queryParams.result" placeholder="全部" clearable style="width: 160px;">
          <el-option v-for="o in resultOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="操作时间">
        <el-date-picker
          v-model="dateRange"
          style="width: 240px"
          value-format="yyyy-MM-dd HH:mm:ss"
          type="daterange"
          range-separator="-"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          :default-time="['00:00:00', '23:59:59']"
        ></el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="info" plain icon="el-icon-document" size="mini" @click="handleExport"
                   v-hasPermi="['kcon:verify:query']">导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="verifyLogList">
      <el-table-column label="端口号" prop="portNo" width="100" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.portNo" class="port-cell">{{ scope.row.portNo }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="输入手机号" prop="inputPhoneMask" width="140" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.inputPhoneMask">{{ scope.row.inputPhoneMask }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <!-- 端口号 / 手机号 / 操作时间内容定长，保留固定宽度保证对齐；
           核验结果、操作人不设宽度，由 el-table 按剩余空间自动分配，
           宽屏下表格会撑满整行，不再出现右侧大片空白 -->
      <el-table-column label="核验结果" min-width="140" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="resultType(scope.row.result)">{{ resultLabel(scope.row.result) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作人" prop="operator" min-width="140" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.operator">{{ scope.row.operator }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作时间" prop="createTime" min-width="180" align="center" />
    </el-table>

    <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum"
                :limit.sync="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script>
import { listVerifyLog } from "@/api/kcon/verifylog"

// 核验结果枚举：value 与后端 KconVerifyLog 常量一一对应
// label 与后端 KconVerifyServiceImpl#resultText 文案保持一致，改文案需两边同步
const RESULT_OPTIONS = [
  { value: "VALID", label: "有效", type: "success" },
  { value: "NOT_ISSUED", label: "端口号未发放", type: "info" },
  { value: "PHONE_MISMATCH", label: "手机号不匹配", type: "danger" },
  { value: "RECEIVED", label: "已领取", type: "warning" }
]

export default {
  name: "KconVerifyLog",
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      verifyLogList: [],
      dateRange: [],
      resultOptions: RESULT_OPTIONS,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        portNo: null,
        result: null
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listVerifyLog(this.addDateRange(this.queryParams, this.dateRange)).then(res => {
        this.verifyLogList = res.rows
        this.total = res.total
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.dateRange = []
      this.resetForm("queryForm")
      this.handleQuery()
    },
    resultOption(result) {
      return RESULT_OPTIONS.find(o => o.value === result)
    },
    resultLabel(result) {
      const o = this.resultOption(result)
      // 未知取值原样显示，便于发现脏数据
      return o ? o.label : (result || "未知")
    },
    resultType(result) {
      const o = this.resultOption(result)
      return o ? o.type : "info"
    },
    handleExport() {
      this.download('/admin/verifylog/export',
        this.addDateRange({ ...this.queryParams }, this.dateRange),
        `兑奖核验日志_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>

<style scoped>
.port-cell {
  font-weight: 700;
  color: #d4342a;
  font-size: 15px;
}
.text-muted {
  color: #bbb;
}
</style>
