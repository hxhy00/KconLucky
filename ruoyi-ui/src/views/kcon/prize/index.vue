<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      <div slot="title">
        奖项按「抽取顺序」从小到大依次开抽；每个档位抽满后自动跳到下一档。
        上午场与下午场各自独立设置。
      </div>
    </el-alert>

    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="68px">
      <el-form-item label="场次" prop="sessionCode">
        <el-select v-model="queryParams.sessionCode" placeholder="全部" clearable>
          <el-option v-for="s in sessions" :key="s.code" :label="s.name" :value="s.code" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
                   v-hasPermi="['kcon:prize:add']">新增奖项</el-button>
      </el-col>
      <right-toolbar :showSearch="true" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="prizeList">
      <el-table-column label="场次" width="120" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.sessionCode === 'AM' ? 'warning' : 'primary'">
            {{ scope.row.sessionCode === 'AM' ? '上午场' : '下午场' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="奖项" prop="tierName" width="140" />
      <el-table-column label="数量" prop="quantity" width="90" align="center" />
      <el-table-column label="已抽出" prop="drawnCount" width="90" align="center" />
      <el-table-column label="抽取顺序" prop="drawOrder" width="100" align="center" />
      <el-table-column label="进度" min-width="200">
        <template slot-scope="scope">
          <el-progress
            :percentage="scope.row.quantity ? Math.round(scope.row.drawnCount * 100 / scope.row.quantity) : 0"
            :status="scope.row.drawnCount >= scope.row.quantity ? 'success' : ''"
          />
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" show-overflow-tooltip />
      <el-table-column label="操作" width="150" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)"
                     v-hasPermi="['kcon:prize:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)"
                     v-hasPermi="['kcon:prize:remove']"
                     :disabled="scope.row.drawnCount > 0">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 后端 list 走 startPage()，默认 pageSize=10；
         此处缺分页组件会导致奖项超过 10 条时永远只看得到前 10 条且无法翻页。 -->
    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog :title="title" :visible.sync="open" width="520px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="场次" prop="sessionCode">
          <el-select v-model="form.sessionCode" placeholder="请选择场次">
            <el-option v-for="s in sessions" :key="s.code" :label="s.name" :value="s.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="奖项名称" prop="tierName">
          <el-input v-model="form.tierName" placeholder="如 特等奖 / 一等奖 / 三等奖" maxlength="32" />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="form.quantity" :min="1" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item label="抽取顺序" prop="drawOrder">
          <el-input-number v-model="form.drawOrder" :min="1" :max="99" controls-position="right" />
          <span class="tip">数字小的先抽</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" maxlength="255" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
        <el-button :disabled="submitting" @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listPrize, addPrize, updatePrize, delPrize } from "@/api/kcon/prize"
import { listSessions } from "@/api/kcon/session"

export default {
  name: "KconPrize",
  data() {
    return {
      loading: true,
      prizeList: [],
      total: 0,
      open: false,
      // 提交中标志：连点两次「确定」会造出两条一模一样的奖项档位，
      // 而后端 POST /admin/prize 没有唯一性校验，慢响应下拦不住
      submitting: false,
      title: "",
      queryParams: { pageNum: 1, pageSize: 10, sessionCode: null },
      sessions: [],
      form: {},
      rules: {
        sessionCode: [{ required: true, message: "请选择场次", trigger: "change" }],
        tierName: [{ required: true, message: "奖项名称不能为空", trigger: "blur" }],
        quantity: [{ required: true, message: "数量不能为空", trigger: "blur" }]
      }
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
    getList() {
      this.loading = true
      listPrize(this.queryParams).then(res => {
        this.prizeList = res.rows
        this.total = res.total
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm("queryForm")
      this.queryParams.pageNum = 1
      this.getList()
    },
    reset() {
      this.form = { id: null, sessionCode: "AM", tierName: null, quantity: 1, drawOrder: 1, remark: null }
      this.resetForm("form")
    },
    cancel() {
      this.open = false
      this.reset()
    },
    handleAdd() {
      this.reset()
      this.title = "新增奖项"
      this.open = true
    },
    handleUpdate(row) {
      this.reset()
      this.form = { ...row }
      this.title = "修改奖项"
      this.open = true
    },
    submitForm() {
      if (this.submitting) return
      this.$refs["form"].validate(valid => {
        if (!valid) return
        const isEdit = this.form.id != null
        this.submitting = true
        const fn = isEdit ? updatePrize : addPrize
        fn(this.form).then(() => {
          this.$modal.msgSuccess(isEdit ? "修改成功" : "新增成功")
          this.open = false
          this.getList()
        }).finally(() => { this.submitting = false })
      })
    },
    handleDelete(row) {
      this.$modal.confirm(`确认删除奖项「${row.tierName}」？`)
        .then(() => delPrize(row.id))
        .then(() => {
          this.getList()
          this.$modal.msgSuccess("删除成功")
        })
        .catch(() => {})
    }
  }
}
</script>

<style scoped>
.tip {
  margin-left: 10px;
  color: #999;
  font-size: 12px;
}
</style>
