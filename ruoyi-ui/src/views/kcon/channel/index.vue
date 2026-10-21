<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      <div slot="title">
        导入名单时按「渠道来源」匹配本表，命中后用这里的「权益类型 / 参与抽奖」覆盖行内值。
        未配置的渠道来源会回退到行内值，并在导入结果里单独提示。
      </div>
    </el-alert>

    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
      <el-form-item label="渠道来源" prop="channelSource">
        <el-input v-model="queryParams.channelSource" placeholder="请输入渠道来源" clearable
                  @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="参与抽奖" prop="drawEligible">
        <el-select v-model="queryParams.drawEligible" placeholder="全部" clearable>
          <el-option label="是" :value="1" />
          <el-option label="否" :value="0" />
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
                   v-hasPermi="['kcon:channel:add']">新增映射</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-refresh" size="mini" :loading="resyncing"
                   @click="handleResync"
                   v-hasPermi="['kcon:channel:edit']">重算存量名单</el-button>
      </el-col>
      <right-toolbar :showSearch="true" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="mappingList">
      <el-table-column label="渠道来源" prop="channelSource" min-width="160" show-overflow-tooltip />
      <el-table-column label="权益类型" prop="benefitType" min-width="200" show-overflow-tooltip>
        <template slot-scope="scope">
          <span v-if="scope.row.benefitType">{{ scope.row.benefitType }}</span>
          <span v-else class="empty">无</span>
        </template>
      </el-table-column>
      <el-table-column label="参与抽奖" width="110" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.drawEligible === 1 ? 'success' : 'info'">
            {{ scope.row.drawEligible === 1 ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sort" width="90" align="center" />
      <el-table-column label="操作" width="150" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)"
                     v-hasPermi="['kcon:channel:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)"
                     v-hasPermi="['kcon:channel:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog :title="title" :visible.sync="open" width="560px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="渠道来源" prop="channelSource">
          <el-input v-model="form.channelSource" placeholder="与导入表「渠道来源」列完全一致" maxlength="64" />
        </el-form-item>
        <el-form-item label="权益类型" prop="benefitType">
          <el-input v-model="form.benefitType" type="textarea" :rows="2" maxlength="128"
                    placeholder="留空表示无权益" />
        </el-form-item>
        <el-form-item label="参与抽奖" prop="drawEligible">
          <el-radio-group v-model="form.drawEligible">
            <el-radio :label="1">是</el-radio>
            <el-radio :label="0">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="999" controls-position="right" />
          <span class="tip">数字小的排在前面</span>
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
import {
  listChannelMapping, addChannelMapping, updateChannelMapping, delChannelMapping,
  resyncChannelMapping
} from "@/api/kcon/channel"

export default {
  name: "KconChannelMapping",
  data() {
    return {
      loading: true,
      mappingList: [],
      total: 0,
      open: false,
      submitting: false,
      resyncing: false,
      title: "",
      queryParams: { pageNum: 1, pageSize: 10, channelSource: null, drawEligible: null },
      form: {},
      rules: {
        channelSource: [{ required: true, message: "渠道来源不能为空", trigger: "blur" }],
        drawEligible: [{ required: true, message: "请选择是否参与抽奖", trigger: "change" }]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listChannelMapping(this.queryParams).then(res => {
        this.mappingList = res.rows || []
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
      // 先清校验再换表单对象：resetFields 会把字段写回旧模型，
      // 若放在赋值之后会把刚灌入的行数据覆盖成首次挂载时的默认值
      this.resetForm("form")
      this.form = { id: null, channelSource: null, benefitType: null, drawEligible: 0, sort: 0 }
    },
    cancel() {
      this.open = false
      this.reset()
    },
    handleAdd() {
      this.reset()
      this.title = "新增映射"
      this.open = true
    },
    handleUpdate(row) {
      this.resetForm("form")
      this.form = { ...row }
      this.title = "修改映射"
      this.open = true
    },
    submitForm() {
      if (this.submitting) return
      this.$refs["form"].validate(valid => {
        if (!valid) return
        const isEdit = this.form.id != null
        this.submitting = true
        const fn = isEdit ? updateChannelMapping : addChannelMapping
        fn(this.form).then(() => {
          this.$modal.msgSuccess(isEdit ? "修改成功" : "新增成功")
          this.open = false
          this.getList()
        }).finally(() => { this.submitting = false })
      })
    },
    handleDelete(row) {
      this.$modal.confirm(`确认删除渠道来源「${row.channelSource}」的映射？删除后导入将回退到行内值。`)
        .then(() => delChannelMapping(row.id))
        .then(() => {
          this.getList()
          this.$modal.msgSuccess("删除成功")
        })
        .catch(() => {})
    },

    // 全量重算：把当前映射重新应用到存量名单，修复「先导入后补映射」留下的资格错乱。
    // 已签到/已中奖的人不会改，只列出来让人工核对——静默改动会造成「已中奖但资格变否」。
    handleResync() {
      this.$modal.confirm(
        "将把当前所有渠道映射重新应用到已有名单（已签到/已中奖的人员不受影响）。确定重算？"
      ).then(() => {
        this.resyncing = true
        return resyncChannelMapping().then(res => {
          const d = (res && res.data) || {}
          const locked = d.locked || []
          let html = `已同步 <b>${d.synced || 0}</b> 人。`
          if (locked.length) {
            html += `<br/>以下 <b>${locked.length}</b> 人已签到或已中奖，为避免状态矛盾未做修改，请人工核对：<br/>`
            html += locked.slice(0, 20).map(p => {
              const why = p.signed === 1 ? "已签到" : ""
              const won = p.won === 1 ? (why ? "、已中奖" : "已中奖") : ""
              return `第${p.attendeeId}号 ${p.name}（${p.channel}，${why}${won}）`
            }).join("<br/>")
            if (locked.length > 20) html += `<br/>… 另有 ${locked.length - 20} 人`
          }
          this.$alert(html, "重算完成", { dangerouslyUseHTMLString: true })
        })
      }).catch(() => {}).finally(() => { this.resyncing = false })
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
.empty {
  color: #bbb;
}
</style>
