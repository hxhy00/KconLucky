<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      <div slot="title">
        每在名单页点一次「发放」都会留痕。同一人的权益重复发放会被系统拦截，可据此追溯发放人与发放时间。
        奖品发放记录不在此页，见「日志 → 奖品发放」。
      </div>
    </el-alert>
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="端口号" prop="portNo">
        <el-input v-model="queryParams.portNo" placeholder="端口号（精确匹配）" clearable
                  style="width: 160px;" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="姓名" prop="name">
        <el-input v-model="queryParams.name" placeholder="姓名（支持模糊）" clearable
                  style="width: 140px;" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="发放时间">
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
                   v-hasPermi="['kcon:benefit:export']">导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="grantList">
      <el-table-column label="端口号" prop="portNo" width="100" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.portNo" class="port-cell">{{ scope.row.portNo }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="姓名" prop="name" min-width="100" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.name">{{ scope.row.name }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="手机号" prop="phoneMask" width="130" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.phoneMask">{{ scope.row.phoneMask }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="权益类型" prop="item" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作人" prop="operator" min-width="120" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.operator">{{ scope.row.operator }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="发放时间" prop="createTime" min-width="170" align="center" />
    </el-table>

    <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum"
                :limit.sync="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script>
import { listBenefitGrant } from "@/api/kcon/grant"

export default {
  name: "KconBenefitGrant",
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      grantList: [],
      // 请求序号：快速翻页/搜索时，慢返回的旧响应不能盖掉新结果
      listSeq: 0,
      dateRange: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        portNo: null,
        name: null
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      const seq = ++this.listSeq
      listBenefitGrant(this.addDateRange(this.queryParams, this.dateRange)).then(res => {
        if (seq !== this.listSeq) return
        this.grantList = res.rows || []
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
      this.dateRange = []
      this.resetForm("queryForm")
      this.handleQuery()
    },
    handleExport() {
      this.download('/admin/benefit/export',
        this.addDateRange({ ...this.queryParams }, this.dateRange),
        `权益发放日志_${new Date().getTime()}.xlsx`)
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
