<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      <div slot="title">
        这里管理本系统的全部账号，身份决定权限：<b>管理员</b>管全局（含清场、迁移、名单导入、奖品与渠道映射），
        <b>签到台</b>发权益、<b>抽奖台</b>抽奖、<b>兑奖台</b>发奖品；一人可兼多身份（多选）。
        管理员身份只有内置超管可以授予或收回。
      </div>
    </el-alert>

    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="70px" v-show="showSearch">
      <el-form-item label="用户名" prop="userName">
        <el-input v-model="queryParams.userName" placeholder="请输入用户名" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="手机号" prop="phonenumber">
        <el-input v-model="queryParams.phonenumber" placeholder="请输入手机号" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
          v-hasPermi="['kcon:staff:add']">新增账号</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="staffList">
      <el-table-column label="姓名" prop="nickName" min-width="120" show-overflow-tooltip />
      <el-table-column label="手机号（登录账号）" prop="phonenumber" min-width="150" />
      <el-table-column label="身份" prop="roleNames" min-width="160">
        <template slot-scope="scope">
          <span v-if="scope.row.roleNames">{{ scope.row.roleNames }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.status === '0' ? 'success' : 'info'">
            {{ scope.row.status === '0' ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="160" align="center" />
      <el-table-column label="操作" min-width="260" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleEdit(scope.row)"
            v-hasPermi="['kcon:staff:edit']">编辑</el-button>
          <el-button size="mini" type="text" icon="el-icon-key" @click="handleResetPwd(scope.row)"
            v-hasPermi="['kcon:staff:resetPwd']">重置密码</el-button>
          <el-button size="mini" type="text" @click="handleToggleStatus(scope.row)"
            :loading="statusLoadingId === scope.row.userId" v-hasPermi="['kcon:staff:edit']">
            {{ scope.row.status === '0' ? '停用' : '启用' }}
          </el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)"
            v-hasPermi="['kcon:staff:remove']"
            :loading="deletingId === scope.row.userId"
            :disabled="deletingId != null">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize"
      @pagination="getList" />

    <!-- 新增 / 编辑 -->
    <el-dialog :title="dialogTitle" :visible.sync="open" width="480px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="姓名" prop="nickName">
          <el-input v-model="form.nickName" placeholder="显示在操作日志里" maxlength="30" />
        </el-form-item>
        <el-form-item label="手机号" prop="phonenumber">
          <el-input v-model="form.phonenumber" placeholder="登录账号，11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="身份" prop="roleKeys">
          <el-select v-model="form.roleKeys" multiple placeholder="选择身份（可多选，一人可兼多身份）" style="width: 100%;">
            <el-option v-for="r in roleOptions" :key="r.roleKey" :label="r.roleName" :value="r.roleKey" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isEdit" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="8-20 位，需含字母和数字" maxlength="20" />
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
          <span>{{ pwdForm.nickName }}</span>
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="pwdForm.password" type="password" show-password placeholder="8-20 位，需含字母和数字"
            maxlength="20" />
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
import { listStaff, listStaffRoles, addStaff, updateStaff, resetStaffPwd, changeStaffStatus, delStaff } from "@/api/kcon/staff"

export default {
  name: "KconStaff",
  data() {
    return {
      loading: true,
      submitting: false,
      statusLoadingId: null,
      // 正在删除的账号 id：请求在途期间禁用删除，避免连点造成重复请求
      deletingId: null,
      showSearch: true,
      staffList: [],
      total: 0,
      open: false,
      // 编辑态：复用同一个弹窗，编辑时不显示「初始密码」并改为 PUT
      isEdit: false,
      dialogTitle: "新增账号",
      pwdOpen: false,
      roleOptions: [],
      queryParams: { pageNum: 1, pageSize: 10, userName: null, phonenumber: null },
      form: {},
      pwdForm: {},
      rules: {
        roleKeys: [
          { required: true, type: "array", min: 1, message: "请至少选择一个岗位", trigger: "change" }
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
    this.loadRoles()
  },
  methods: {
    // 岗位下拉数据源（后端白名单，只有签到台/抽奖台/兑奖台三个）
    loadRoles() {
      listStaffRoles().then(res => {
        this.roleOptions = res.data || []
      }).catch(() => {
        this.roleOptions = []
      })
    },
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
      this.isEdit = false
      this.dialogTitle = "新增账号"
      this.rules.password[0].required = true
      this.form = { userId: null, nickName: null, phonenumber: null, roleKeys: [], password: null }
      this.loadRoles()
      this.open = true
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    handleEdit(row) {
      this.isEdit = true
      this.dialogTitle = "编辑账号"
      // 编辑不涉及密码（走「重置密码」），把必填关掉避免误拦
      this.rules.password[0].required = false
      this.form = {
        userId: row.userId,
        nickName: row.nickName,
        phonenumber: row.phonenumber,
        roleKeys: (row.roleKeys || []).slice(),
        password: null
      }
      this.loadRoles()
      this.open = true
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        this.submitting = true
        const payload = {
          nickName: (this.form.nickName || "").trim(),
          phonenumber: (this.form.phonenumber || "").trim(),
          roleKeys: this.form.roleKeys || []
        }
        const request = this.isEdit
          ? updateStaff({ ...payload, userId: this.form.userId })
          : addStaff({ ...payload, password: this.form.password })
        request.then(() => {
          this.$modal.msgSuccess(this.isEdit ? "已保存" : "已创建")
          this.open = false
          this.getList()
        }).catch(() => { }).finally(() => {
          this.submitting = false
        })
      })
    },
    handleResetPwd(row) {
      this.pwdForm = { userId: row.userId, nickName: row.nickName, password: null }
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
        }).catch(() => { }).finally(() => {
          this.submitting = false
        })
      })
    },
    handleToggleStatus(row) {
      const next = row.status === "0" ? "1" : "0"
      const action = next === "1" ? "停用" : "启用"
      this.$modal.confirm(`确认${action}账号「${row.nickName}」？`).then(() => {
        this.statusLoadingId = row.userId
        return changeStaffStatus({ userId: row.userId, status: next })
      }).then(() => {
        this.$modal.msgSuccess(action + "成功")
        this.getList()
      }).catch(() => { }).finally(() => {
        this.statusLoadingId = null
      })
    },
    handleDelete(row) {
      if (this.deletingId != null) return
      this.$modal.confirm(`确认删除账号「${row.nickName}」？删除后该账号立即无法登录。`)
        .then(() => {
          this.deletingId = row.userId
          return delStaff(row.userId)
        })
        .then(() => {
          this.$modal.msgSuccess("已删除")
          this.getList()
        }).catch(() => { })
        .finally(() => { this.deletingId = null })
    }
  }
}
</script>

<style scoped>
.text-muted {
  color: #bbb;
}
</style>
