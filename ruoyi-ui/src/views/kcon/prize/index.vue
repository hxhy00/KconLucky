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
            {{ sessionName(scope.row.sessionCode) }}
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
                     :loading="deletingId === scope.row.id"
                     :disabled="scope.row.drawnCount > 0 || deletingId != null">删除</el-button>
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
          <!-- 不用自由文本：大屏纸屑特效按档位名关键字判定等级（特等/一等 → 最高档，
               二等 → 次档，其余 → 兜底），现场手打「一等」「第1名」等变体会漏匹配，
               导致抽一等奖却放最朴素的动效。改为下拉只允许选固定档位名 -->
          <el-select v-model="form.tierName" placeholder="请选择奖项档位">
            <el-option v-for="t in tierOptions" :key="t" :label="t" :value="t" />
          </el-select>
          <span class="tip">名字决定大屏庆祝特效的等级</span>
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

// 可选档位名：与 screen 大屏的纸屑特效关键字严格对应
// （screen/src/App.vue 的 makeConfetti：含「特等/一等」→ 最高档，含「二等」→ 次档，其余 → 兜底）。
// 用下拉替代自由文本，避免现场打出「一等」「第1名」等漏匹配的变体
const TIER_OPTIONS = ["特等奖", "一等奖", "二等奖", "三等奖"]

export default {
  name: "KconPrize",
  data() {
    return {
      loading: true,
      prizeList: [],
      total: 0,
      open: false,
      // 提交中标志：连点两次「确定」会重复提交。
      // 后端确有同场次同名的唯一性校验（Controller 预检 + validatePrize），
      // 重复请求会被拒；但被拒也得让用户看到一次报错，不如前端直接锁住按钮
      submitting: false,
      // 正在删除的行 id：请求在途期间禁用删除，避免连点造成重复请求
      deletingId: null,
      title: "",
      queryParams: { pageNum: 1, pageSize: 10, sessionCode: null },
      sessions: [],
      form: {},
      rules: {
        sessionCode: [{ required: true, message: "请选择场次", trigger: "change" }],
        tierName: [{ required: true, message: "请选择奖项档位", trigger: "change" }],
        quantity: [{ required: true, message: "数量不能为空", trigger: "blur" }]
      }
    }
  },
  computed: {
    /**
     * 档位下拉选项。固定四档之外，把当前 form 里的历史档位名（老数据可自由输入过
     * 「优胜奖」等）自动补进选项，避免编辑时下拉显示空白、把奖项名提交成空。
     * 用 computed 派生而非在 handleUpdate/reset 里手动增删，彻底免掉
     * 「忘记复原导致临时选项污染下一次新增」的隐患。
     */
    tierOptions() {
      const cur = this.form && this.form.tierName
      return cur && !TIER_OPTIONS.includes(cur) ? TIER_OPTIONS.concat([cur]) : TIER_OPTIONS
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
      return hit ? hit.name : (code || '—')
    },
    getList() {
      this.loading = true
      listPrize(this.queryParams).then(res => {
        // rows 兜底：响应缺字段时 prizeList 会变成 undefined，
        // 后续 submitForm 的去重判断 some() 会直接抛错
        this.prizeList = res.rows || []
        this.total = res.total || 0
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
        // 同场次同名档位提前拦：档位收敛为固定四档后，重复建「一等奖」比自由文本时更容易发生。
        // 注意这只是「当前页已加载数据」的快速提示，不是唯一防线——
        // 真正的唯一性由后端 validatePrize / countBySessionAndTier 强校验（新增与修改都过），
        // 所以此处漏判也不会写进脏数据，后端会拒绝
        const dup = this.prizeList.some(r =>
          r.sessionCode === this.form.sessionCode &&
          r.tierName === this.form.tierName &&
          r.id !== this.form.id
        )
        if (dup) {
          this.$modal.msgError(`该场次下已存在「${this.form.tierName}」，请勿重复添加`)
          return
        }
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
      if (this.deletingId != null) return
      this.$modal.confirm(`确认删除奖项「${row.tierName}」？`)
        .then(() => {
          this.deletingId = row.id
          return delPrize(row.id)
        })
        .then(() => {
          this.getList()
          this.$modal.msgSuccess("删除成功")
        })
        .catch(() => {})
        .finally(() => { this.deletingId = null })
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
