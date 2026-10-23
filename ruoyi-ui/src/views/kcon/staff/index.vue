<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      <div slot="title">
        这里只管理「现场工作人员」账号：可做签到、抽奖、兑奖核验等日常操作，
        没有清场、迁移、删名单、名单导入、奖品与渠道映射的权限。
      </div>
    </el-alert>

    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="70px"
             v-show="showSearch">
      <el-form-item label="用户名" prop="userName">
        <el-input v-model="queryParams.userName" placeholder="请输入用户名" clearable
                  @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="手机号" prop="phonenumber">
        <el-input v-model="queryParams.phonenumber" placeholder="请输入手机号" clearable
                  @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
                   v-hasPermi="['kcon:staff:add']">新增工作人员</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="staffList">
      <el-table-column label="用户名" prop="userName" min-width="140" show-overflow-tooltip />
      <el-table-column label="姓名" prop="nickName" min-width="140" show-overflow-tooltip />
      <el-table-column label="手机号" prop="phonenumber" width="140" />
      <el-table-column label="状态" width="100" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.status === '0' ? 'success' : 'info'">
            {{ scope.row.status === '0' ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="160" align="center" />
      <el-table-column label="操作" min-width="220" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-key" @click="handleResetPwd(scope.row)"
                     v-hasPermi="['kcon:staff:resetPwd']">重置密码</el-button>
          <el-button size="mini" type="text" @click="handleToggleStatus(scope.row)"
                     :loading="statusLoadingId === scope.row.userId"
                     v-hasPermi="['kcon:staff:edit']">
            {{ scope.row.status === '0' ? '停用' : '启用' }}
          </el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)"
                     v-hasPermi="['kcon:staff:remove']">删除</el-button>
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

    <!-- 新增 -->
    <el-dialog title="新增工作人员" :visible.sync="open" width="480px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="userName">
          <el-input v-model="form.userName" placeholder="登录名，2-30 位字母/数字/下划线" maxlength="30" />
        </el-form-item>
        <el-form-item label="姓名" prop="nickName">
          <el-input v-model="form.nickName" placeholder="显示在操作日志里" maxlength="30" />
        </el-form-item>
        <el-form-item label="手机号" prop="phonenumber">
          <el-input v-model="form.phonenumber" placeholder="用于账号识别，必填" maxlength="11" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password
                    placeholder="8-20 位，需含字母和数字" maxlength="20" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
        <el-button :disabled="submitting" @click="open = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog title="重置密码" :visible.sync="pwdOpen" width="420px" append-to-body>
      <el-form ref="pwdForm" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="账号">
          <span>{{ pwdForm.userName }}</span>
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="pwdForm.password" type="password" show-password
                    placeholder="8-20 位，需含字母和数字" maxlength="20" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="submitting" @click="submitPwd">确 定</el-button>
        <el-button :disabled="submitting" @click="pwdOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listStaff, addStaff, resetStaffPwd, changeStaffStatus, delStaff } from "@/api/kcon/staff"

export default {
  name: "KconStaff",
  data() {
    return {
      loading: true,
      submitting: false,
      statusLoadingId: null,
      showSearch: true,
      staffList: [],
      total: 0,
      open: false,
      pwdOpen: false,
      queryParams: { pageNum: 1, pageSize: 10, userName: null, phonenumber: null },
      form: {},
      pwdForm: {},
      rules: {
        userName: [
          { required: true, message: "请输入用户名", trigger: "blur" },
          { pattern: /^[A-Za-z0-9_]{2,30}$/, message: "2-30 位字母、数字或下划线", trigger: "blur" }
        ],
        nickName: [
          { required: true, message: "请输入姓名", trigger: "blur" },
          { max: 30, message: "姓名不能超过 30 个字符", trigger: "blur" }
        ],
        phonenumber: [
          { required: true, message: "请输入手机号", trigger: "blur" },
          { pattern: /^1[3-9]\d{9}$/, message: "手机号格式不正确", trigger: "blur" }
        ],
        password: [
          { required: true, message: "请输入初始密码", trigger: "blur" },
          { min: 8, max: 20, message: "密码长度需为 8-20 位", trigger: "blur" },
          { pattern: /^(?=.*[A-Za-z])(?=.*\d)\S+$/, message: "需同时包含字母和数字，且不能有空格", trigger: "blur" }
        ]
      },
      pwdRules: {
        password: [
          { required: true, message: "请输入新密码", trigger: "blur" },
          { min: 8, max: 20, message: "密码长度需为 8-20 位", trigger: "blur" },
          { pattern: /^(?=.*[A-Za-z])(?=.*\d)\S+$/, message: "需同时包含字母和数字，且不能有空格", trigger: "blur" }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listStaff(this.queryParams).then(res => {
        this.staffList = res.rows || []
        this.total = res.total || 0
      }).catch(() => {
        // request.js 已统一提示，这里只兜底避免未捕获的 Promise 拒绝
      }).finally(() => {
        this.loading = false
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
    handleAdd() {
      this.form = { userName: null, nickName: null, phonenumber: null, password: null }
      this.open = true
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        this.submitting = true
        const payload = {
          userName: (this.form.userName || "").trim(),
          nickName: (this.form.nickName || "").trim(),
          phonenumber: (this.form.phonenumber || "").trim(),
          password: this.form.password
        }
        addStaff(payload).then(() => {
          this.$modal.msgSuccess("已创建")
          this.open = false
          this.getList()
        }).catch(() => {}).finally(() => {
          this.submitting = false
        })
      })
    },
    handleResetPwd(row) {
      this.pwdForm = { userId: row.userId, userName: row.userName, password: null }
      this.pwdOpen = true
      this.$nextTick(() => this.$refs.pwdForm && this.$refs.pwdForm.clearValidate())
    },
    submitPwd() {
      this.$refs.pwdForm.validate(valid => {
        if (!valid) return
        this.submitting = true
        resetStaffPwd({ userId: this.pwdForm.userId, password: this.pwdForm.password }).then(() => {
          this.$modal.msgSuccess("密码已重置，该账号需要重新登录")
          this.pwdOpen = false
        }).catch(() => {}).finally(() => {
          this.submitting = false
        })
      })
    },
    handleToggleStatus(row) {
      const next = row.status === "0" ? "1" : "0"
      const action = next === "1" ? "停用" : "启用"
      this.$modal.confirm(`确认${action}账号「${row.userName}」？`).then(() => {
        this.statusLoadingId = row.userId
        return changeStaffStatus({ userId: row.userId, status: next })
      }).then(() => {
        this.$modal.msgSuccess(action + "成功")
        this.getList()
      }).catch(() => {}).finally(() => {
        this.statusLoadingId = null
      })
    },
    handleDelete(row) {
      this.$modal.confirm(`确认删除账号「${row.userName}」？删除后该账号立即无法登录。`)
        .then(() => delStaff(row.userId))
        .then(() => {
          this.$modal.msgSuccess("已删除")
          this.getList()
        }).catch(() => {})
    }
  }
}
</script>
