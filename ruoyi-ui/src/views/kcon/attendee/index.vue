<template>
  <div class="app-container">
    <!-- 查询 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="姓名" prop="name">
        <el-input v-model="queryParams.name" placeholder="姓名" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="queryParams.phone" placeholder="手机号（支持模糊）" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="渠道标识" prop="channel">
        <el-input v-model="queryParams.channel" placeholder="如 S / T / C" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="票种" prop="ticketType">
        <el-input v-model="queryParams.ticketType" placeholder="如 早鸟票" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="公司" prop="company">
        <el-input v-model="queryParams.company" placeholder="公司" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="职务/年级" prop="position">
        <el-input v-model="queryParams.position" placeholder="职务/年级" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="签到状态" prop="signed">
        <el-select v-model="queryParams.signed" placeholder="全部" clearable>
          <el-option label="已签到" :value="1" />
          <el-option label="未签到" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具条 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
                   v-hasPermi="['kcon:attendee:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single"
                   @click="handleUpdate" v-hasPermi="['kcon:attendee:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple"
                   @click="handleDelete" v-hasPermi="['kcon:attendee:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-upload2" size="mini" @click="openImport"
                   v-hasPermi="['kcon:attendee:import']">导入名单</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="el-icon-download" size="mini" @click="handleSignSheet"
                   v-hasPermi="['kcon:attendee:export']">签到表</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="el-icon-document" size="mini" @click="handleExport"
                   v-hasPermi="['kcon:attendee:export']">导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="attendeeList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="姓名" prop="name" width="100" />
      <el-table-column label="手机号" prop="phoneMask" width="130" />
      <el-table-column label="渠道" prop="channel" width="70" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" v-if="scope.row.channel">{{ scope.row.channel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="票种" prop="ticketType" width="110" />
      <el-table-column label="公司" prop="company" show-overflow-tooltip />
      <el-table-column label="邮箱" prop="email" min-width="160" show-overflow-tooltip>
        <template slot-scope="scope">
          <span v-if="scope.row.email">{{ scope.row.email }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="职务/年级" prop="position" width="110" />
      <el-table-column label="参加次数" prop="attendCount" width="90" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.attendCount === 1">首次</span>
          <span v-else-if="scope.row.attendCount > 1">第 {{ scope.row.attendCount }} 次</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="端口号" prop="portNo" width="90" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.portNo" class="port-cell">{{ scope.row.portNo }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="签到" width="80" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.signed === 1 ? 'success' : 'info'">
            {{ scope.row.signed === 1 ? '已签到' : '未签到' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签到时间" prop="checkinTime" width="160" />
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)"
                     v-hasPermi="['kcon:attendee:edit']">补录</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)"
                     v-hasPermi="['kcon:attendee:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum"
                :limit.sync="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/补录 -->
    <el-dialog :title="title" :visible.sync="open" width="560px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="姓名" maxlength="64" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="渠道标识" prop="channel">
          <el-input v-model="form.channel" placeholder="单字母，如 S/T/C/H/D/G/V" maxlength="8" />
        </el-form-item>
        <el-form-item label="票种" prop="ticketType">
          <el-input v-model="form.ticketType" placeholder="如 散客票/早鸟票/合作渠道票" maxlength="32" />
        </el-form-item>
        <el-form-item label="公司" prop="company">
          <el-input v-model="form.company" placeholder="选填，可后补" maxlength="128" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="选填" maxlength="128" />
        </el-form-item>
        <el-form-item label="第几次参加" prop="attendCount">
          <el-input-number v-model="form.attendCount" :min="1" :max="99" controls-position="right" />
          <span class="tip">留空表示未知</span>
        </el-form-item>
        <el-form-item label="职务/年级" prop="position">
          <el-input v-model="form.position" placeholder="选填，如 CTO / 大二" maxlength="64" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 导入 -->
    <el-dialog title="导入参会名单" :visible.sync="importOpen" width="600px" append-to-body>
      <el-alert type="info" :closable="false" show-icon>
        <div slot="title" style="line-height: 1.8;">
          支持 .xlsx / .xls / .csv。<b>按表头名识别列，顺序不限</b>。<br />
          <b>必填 4 列</b>：姓名、手机号、渠道标识、票种<br />
          <b>选填 4 列</b>：公司、邮箱、第几次参加KCon、职务/年级（可导入后在管理端补录）<br />
          多余的列（购票时间、金额等）会自动忽略。<br />
          <b>第一次整理名单？点下方「下载模板」按表头填好再导入，不会出错。</b>
        </div>
      </el-alert>

      <el-form label-width="100px" style="margin-top: 16px;">
        <el-form-item label="导入方式">
          <el-radio-group v-model="importMode">
            <el-radio label="append">追加（按手机号去重更新）</el-radio>
            <el-radio label="replace">替换（先清空再导入）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload
            ref="upload"
            :auto-upload="false"
            :limit="1"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
            accept=".xlsx,.xls,.csv"
            drag
            action="#"
          >
            <i class="el-icon-upload"></i>
            <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
            <div class="el-upload__tip" slot="tip">一次只能上传一个文件</div>
          </el-upload>
        </el-form-item>
      </el-form>

      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
        <el-button icon="el-icon-download" @click="handleTemplate" v-hasPermi="['kcon:attendee:import']">下载模板</el-button>
        <el-button @click="importOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listAttendee, getAttendee, addAttendee, updateAttendee, delAttendee,
  importAttendee
} from "@/api/kcon/attendee"

export default {
  name: "KconAttendee",
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      attendeeList: [],
      title: "",
      open: false,
      importOpen: false,
      importing: false,
      importMode: "append",
      importFile: null,
      timer: null,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        name: null,
        phone: null,
        channel: null,
        ticketType: null,
        company: null,
        position: null,
        signed: null
      },
      form: {},
      rules: {
        name: [{ required: true, message: "姓名不能为空", trigger: "blur" }],
        phone: [
          { required: true, message: "手机号不能为空", trigger: "blur" },
          { pattern: /^1[3-9]\d{9}$/, message: "手机号格式不正确", trigger: "blur" }
        ],
        channel: [{ required: true, message: "渠道标识不能为空", trigger: "blur" }],
        ticketType: [{ required: true, message: "票种不能为空", trigger: "blur" }]
      }
    }
  },
  created() {
    this.getList()
    // 签到期间名单页的「已签到 / 端口号 / 签到时间」会持续变化，
    // 只在打开时拉一次数据会一直停在旧值，所以加自动刷新。
    // 10 秒一次，比看板的 5 秒保守，避免频繁请求。
    this.timer = setInterval(this.getList, 10000)
  },
  beforeDestroy() {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  },
  methods: {
    getList() {
      this.loading = true
      listAttendee(this.queryParams).then(res => {
        this.attendeeList = res.rows
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
      this.queryParams.signed = null
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    reset() {
      this.form = {
        id: null, name: null, phone: null, channel: null,
        ticketType: null, company: null, email: null,
        attendCount: null, position: null
      }
      this.resetForm("form")
    },
    cancel() {
      this.open = false
      this.reset()
    },
    handleAdd() {
      this.reset()
      this.title = "新增参会人"
      this.open = true
    },
    handleUpdate(row) {
      this.reset()
      const id = row.id || this.ids[0]
      getAttendee(id).then(res => {
        this.form = res.data
        this.title = "补录 / 修改信息"
        this.open = true
      })
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (!valid) return
        if (this.form.id != null) {
          updateAttendee(this.form).then(() => {
            this.$modal.msgSuccess("修改成功")
            this.open = false
            this.getList()
          })
        } else {
          addAttendee(this.form).then(() => {
            this.$modal.msgSuccess("新增成功")
            this.open = false
            this.getList()
          })
        }
      })
    },
    handleDelete(row) {
      const ids = row.id || this.ids
      this.$modal.confirm('确认删除选中的 ' + (row.id ? 1 : ids.length) + ' 条名单？')
        .then(() => delAttendee(ids))
        .then(() => {
          this.getList()
          this.$modal.msgSuccess("删除成功")
        })
        .catch(() => {})
    },
    handleExport() {
      this.download('/admin/attendee/export', { ...this.queryParams }, `参会名单_${new Date().getTime()}.xlsx`)
    },
    handleSignSheet() {
      this.download('/admin/attendee/signsheet', {}, `统一签到表_${new Date().getTime()}.xlsx`)
    },
    // 下载导入模板：票务/市场第一次整理名单时按模板填，避免表头拼错
    handleTemplate() {
      this.download('/admin/attendee/template', {}, `参会名单导入模板_${new Date().getTime()}.xlsx`)
    },

    // ---------- 导入 ----------
    openImport() {
      this.importFile = null
      this.importMode = "append"
      this.importOpen = true
      this.$nextTick(() => {
        if (this.$refs.upload) this.$refs.upload.clearFiles()
      })
    },
    onFileChange(file) {
      this.importFile = file.raw
    },
    onFileRemove() {
      this.importFile = null
    },
    submitImport() {
      if (!this.importFile) {
        this.$modal.msgWarning("请先选择文件")
        return
      }
      const fd = new FormData()
      fd.append("file", this.importFile)
      fd.append("mode", this.importMode)

      this.importing = true
      importAttendee(fd).then(res => {
        this.importing = false
        this.importOpen = false
        const s = res.data || {}
        this.$alert(
          `新增 ${s.inserted || 0} 条，更新 ${s.updated || 0} 条，跳过 ${s.skipped || 0} 条。<br/>
           当前有效名单共 <b>${s.total || 0}</b> 人。`,
          "导入完成",
          { dangerouslyUseHTMLString: true }
        )
        this.handleQuery()
      }).catch(() => {
        this.importing = false
      })
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
.tip {
  margin-left: 10px;
  color: #999;
  font-size: 12px;
}
</style>
