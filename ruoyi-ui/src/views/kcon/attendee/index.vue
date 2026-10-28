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
      <el-form-item label="渠道来源" prop="channel">
        <el-select v-model="queryParams.channel" placeholder="全部渠道" clearable filterable
                   style="width: 180px;">
          <el-option v-for="c in channelOptions" :key="c.channelSource" :label="c.channelSource" :value="c.channelSource" />
        </el-select>
      </el-form-item>
      <el-form-item label="权益类型" prop="ticketType">
        <el-input v-model="queryParams.ticketType" placeholder="如 含自助午餐" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="公司" prop="company">
        <el-input v-model="queryParams.company" placeholder="公司" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="职位" prop="position">
        <el-input v-model="queryParams.position" placeholder="职位" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="签到状态" prop="signed">
        <el-select v-model="queryParams.signed" placeholder="全部" clearable>
          <el-option label="已签到" :value="1" />
          <el-option label="未签到" :value="0" />
        </el-select>
      </el-form-item>
      <!-- spec 006：按「参与抽奖」筛选 -->
      <el-form-item label="参与抽奖" prop="drawEligible">
        <el-select v-model="queryParams.drawEligible" placeholder="全部" clearable>
          <el-option label="参与" :value="1" />
          <el-option label="不参与" :value="0" />
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

    <!-- 自动刷新失败时的常驻提示：以前只有一次性红条，页面数字静止却看不出「数据已经多久没更新了」 -->
    <el-alert v-if="listError" type="warning" :closable="false" show-icon
              style="margin-bottom: 8px;" :title="listError" />

    <!-- 列表 -->
    <el-table v-loading="loading" :data="attendeeList" row-key="id"
              :row-class-name="rowClassName" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" :reserve-selection="true" />
      <el-table-column label="姓名" prop="name" width="100" />
      <el-table-column label="手机号" prop="phoneMask" width="130" />
      <el-table-column label="渠道来源" prop="channel" width="140" show-overflow-tooltip />
      <el-table-column label="权益类型" prop="ticketType" width="190" show-overflow-tooltip>
        <template slot-scope="scope">
          <span>{{ scope.row.ticketType }}</span>
          <!-- 手工改过权益类型的人：后续映射重算/导入都不会覆盖，单独标记出来 -->
          <el-tooltip v-if="scope.row.ticketTypeManual === 1" content="人工修改，映射重算与导入均不覆盖" placement="top">
            <el-tag size="mini" type="warning" style="margin-left: 4px;">手工</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <!-- spec 006：参与抽奖，名单页可展示 -->
      <el-table-column label="参与抽奖" prop="drawEligible" width="90" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.drawEligible === 1 ? 'success' : 'info'">
            {{ scope.row.drawEligible === 1 ? '参与' : '不参与' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="公司" prop="company" show-overflow-tooltip />
      <el-table-column label="邮箱" prop="email" min-width="160" show-overflow-tooltip>
        <template slot-scope="scope">
          <span v-if="scope.row.email">{{ scope.row.email }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="职位" prop="position" width="110" />
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
      <el-table-column label="权益发放" width="100" align="center">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.benefitGranted === 1" size="mini" type="success">已发放</el-tag>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="200" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-present"
                     :disabled="scope.row.benefitGranted === 1 || scope.row.signed !== 1"
                     @click="handleGrant(scope.row)"
                     v-hasPermi="['kcon:benefit:grant']">发放</el-button>
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
        <!-- 渠道来源只能从映射表里选：手输会有错字、全半角括号差异，导致映射匹配不上 -->
        <el-form-item label="渠道来源" prop="channel">
          <el-select v-model="form.channel" placeholder="请选择渠道来源" filterable
                     style="width: 100%"
                     @change="onChannelChange">
            <el-option v-for="m in channelOptions" :key="m.id" :label="m.channelSource" :value="m.channelSource" />
          </el-select>
        </el-form-item>
        <el-form-item label="权益类型" prop="ticketType">
          <el-input v-model="form.ticketType" placeholder="如 含自助午餐、伴手礼 / 无" maxlength="128" />
          <div class="tip" style="line-height: 1.6;">
            <template v-if="form.ticketTypeManual === 1">
              <el-tag size="mini" type="warning">人工修改</el-tag>
              该值不会被映射重算或导入覆盖。
              <el-button type="text" size="mini" @click="handleResetTicketType" style="padding: 0 2px;">
                恢复为跟随映射
              </el-button>
            </template>
            <template v-else>
              默认由渠道来源映射带出；手工改过后将一直保留，不再随映射变化。
            </template>
          </div>
        </el-form-item>
        <!-- spec 006：参与抽奖，补录时可手工改单条；与导入列同口径 -->
        <el-form-item label="参与抽奖" prop="drawEligible">
          <el-switch v-model="form.drawEligible" :active-value="1" :inactive-value="0"
                     active-text="参与" inactive-text="不参与" />
          <span class="tip">不参与者照常签到领胸牌，仅不进抽奖池</span>
        </el-form-item>
        <el-form-item label="公司" prop="company">
          <el-input v-model="form.company" placeholder="选填，可后补" maxlength="128" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="选填" maxlength="128" />
        </el-form-item>
        <el-form-item label="职位" prop="position">
          <el-input v-model="form.position" placeholder="选填，如 CTO / 大二" maxlength="64" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
        <el-button :disabled="submitting" @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 导入 -->
    <el-dialog title="导入参会名单" :visible.sync="importOpen" width="680px" append-to-body @closed="resetPreview">
      <el-alert type="info" :closable="false" show-icon>
        <div slot="title" style="line-height: 1.8;">
          支持 .xlsx / .xls / .csv。<b>按表头名识别列，顺序不限</b>。<br />
          <b>必填 3 列</b>：姓名、手机号、渠道来源<br />
          <b>选填 3 列</b>：公司、职位、邮箱<br />
          权益类型与参与抽奖<b>不用填</b>：由渠道来源在「设置 → 渠道来源映射」中决定，导入时自动带出。<br />
          <b style="color:#d4342a">渠道来源必须已在映射表中配置</b>，未配置的渠道不允许导入，需先去补映射。<br />
          <b style="color:#d4342a">仅支持上方 6 列表头</b>：旧模板（渠道标识 / 票种 / 是否参与抽奖 / 职务年级）已不再支持，请用下方「下载模板」重新整理。<br />
          多余的列（购票时间、金额等）会自动忽略。<br />
          <b>第一次整理名单？点下方「下载模板」按表头填好再导入，不会出错。</b>
        </div>
      </el-alert>

      <el-form label-width="100px" style="margin-top: 16px;">
        <el-form-item label="导入方式">
          <el-radio-group v-model="importMode" :disabled="previewing || importing">
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

      <!-- 预检报告：解析后立刻展示，确认无误再落库 -->
      <div v-if="preview" class="preview-box">
        <div class="preview-head" :class="'lv-' + (preview.level || 'OK').toLowerCase()">
          <i :class="previewIcon"></i>
          <span>{{ previewTitle }}</span>
        </div>

        <div class="preview-stat">
          共 <b>{{ preview.totalRows || 0 }}</b> 行数据：
          新增 <b>{{ preview.willInsert || 0 }}</b>，
          更新 <b>{{ preview.willUpdate || 0 }}</b>，
          跳过 <b>{{ preview.willSkip || 0 }}</b>
        </div>

        <div v-if="preview.blockers && preview.blockers.length" class="preview-list">
          <div class="preview-list-title">阻塞问题（必须补全映射后才能导入，没有强制导入）</div>
          <div v-for="(b, i) in preview.blockers" :key="'b' + i" class="preview-item err">
            第 {{ b.row }} 行：{{ b.reason }}
          </div>
        </div>

        <div v-if="preview.warnings && preview.warnings.length" class="preview-list">
          <div class="preview-list-title">提示</div>
          <div v-for="(w, i) in preview.warnings" :key="'w' + i" class="preview-item warn">
            第 {{ w.row }} 行：{{ w.reason }}
          </div>
        </div>

        <div v-if="preview.channelStats && preview.channelStats.length" class="preview-list">
          <div class="preview-list-title">渠道 → 权益 / 抽奖资格</div>
          <el-table :data="preview.channelStats" size="mini" border max-height="220">
            <el-table-column prop="channel" label="渠道来源" min-width="140" show-overflow-tooltip />
            <el-table-column prop="count" label="人数" width="70" align="center" />
            <el-table-column label="权益类型" min-width="150" show-overflow-tooltip>
              <template slot-scope="scope">{{ scope.row.benefitType || '—' }}</template>
            </el-table-column>
            <el-table-column label="参与抽奖" width="90" align="center">
              <template slot-scope="scope">
                <span :class="scope.row.drawEligible === 1 ? 'ok-text' : 'no-text'">
                  {{ scope.row.drawEligible === 1 ? '是' : '否' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="来源" width="80" align="center">
              <template slot-scope="scope">
                <el-tag v-if="scope.row.mapped" type="success" size="mini">映射</el-tag>
                <el-tag v-else type="danger" size="mini">未配</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="preview.skipDetails && preview.skipDetails.length" class="preview-list">
          <div class="preview-list-title">将跳过的行</div>
          <div v-for="(s, i) in preview.skipDetails" :key="'s' + i" class="preview-item err">
            第 {{ s.row }} 行：{{ s.reason }}
          </div>
        </div>
      </div>

      <div slot="footer" class="dialog-footer">
        <el-button :loading="previewing" :disabled="importing" @click="runPreview">
          {{ preview ? '重新预检' : '预检' }}
        </el-button>
        <el-button
          type="primary"
          :loading="importing"
          :disabled="!preview || previewing || preview.level === 'BLOCK'"
          @click="submitImport"
        >确认导入</el-button>
        <el-button icon="el-icon-download" :disabled="importing" @click="handleTemplate" v-hasPermi="['kcon:attendee:import']">下载模板</el-button>
        <el-button :disabled="importing" @click="importOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listAttendee, getAttendee, addAttendee, updateAttendee, delAttendee,
  importAttendee, previewImport, resetAttendeeTicketType, grantBenefit
} from "@/api/kcon/attendee"
import { optionselectChannelMapping } from "@/api/kcon/channel"
import { isSessionExpired } from "@/utils/session-expired"
import { fileToken, isValidPreview } from "@/utils/kcon"

// 静默增量轮询间隔
const POLL_INTERVAL = 10000

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
      previewing: false,
      importMode: "append",
      importFile: null,
      // 预检结果为 null 时不允许导入：必须先看到解析报告再落库
      preview: null,
      // 列表请求序号：丢弃过期响应，避免慢返回的旧结果盖掉新结果
      listSeq: 0,
      // 预检请求序号：文件变更后，在途的旧文件预检响应必须作废
      previewSeq: 0,
      // 渠道下拉选项（来源：渠道来源映射表）
      channelOptions: [],
      timer: null,
      // polling 防请求叠加；listError 是常驻的自动刷新失败提示
      polling: false,
      listError: "",
      submitting: false,
      // 轮询发现变化的行 id，短暂高亮
      highlightIds: [],
      highlightTimer: null,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        name: null,
        phone: null,
        channel: null,
        ticketType: null,
        company: null,
        position: null,
        signed: null,
        drawEligible: null
      },
      form: {},
      rules: {
        name: [{ required: true, message: "姓名不能为空", trigger: "blur" }],
        phone: [
          { required: true, message: "手机号不能为空", trigger: "blur" },
          { pattern: /^1[3-9]\d{9}$/, message: "手机号格式不正确", trigger: "blur" }
        ],
        channel: [{ required: true, message: "渠道来源不能为空", trigger: "blur" }],
        ticketType: [{ required: true, message: "权益类型不能为空", trigger: "blur" }]
      }
    }
  },
  computed: {
    previewTitle() {
      if (!this.preview) return ""
      const lv = this.preview.level
      if (lv === "BLOCK") {
        return `预检未通过：${(this.preview.blockers || []).length} 处阻塞问题，补全后才能导入`
      }
      if (lv === "WARN") {
        return "预检通过，但有需要确认的提示"
      }
      return "预检通过，可以导入"
    },
    previewIcon() {
      if (!this.preview) return "el-icon-info"
      const lv = this.preview.level
      if (lv === "BLOCK") return "el-icon-error"
      if (lv === "WARN") return "el-icon-warning"
      return "el-icon-success"
    }
  },
  created() {
    this.getList()
    this.loadChannelOptions()
    // 签到期间名单页的「已签到 / 端口号 / 签到时间」会持续变化，需要自动刷新。
    // 但刷新方式不能是「整页重拉」：以前轮询直接复用 getList()，每 10 秒把表格
    // 刷成灰色遮罩并整体替换数组——勾选被清空、行序跳动，操作员边看观众边操作时
    // 经常刚好在勾选后被刷掉。
    // 现在改为静默增量刷新：不置 loading，结果按 id 就地合并，
    // 只有内容真变了的那一行才更新并短暂高亮。
    this.timer = setInterval(this.refreshQuietly, POLL_INTERVAL)
  },
  beforeDestroy() {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
    if (this.highlightTimer) {
      clearTimeout(this.highlightTimer)
      this.highlightTimer = null
    }
  },
  methods: {
    // 显式刷新（首次加载 / 搜索 / 分页 / 增删改后对账）：带 loading，让人知道自己在等
    getList() {
      this.loading = true
      return this.fetchList().finally(() => { this.loading = false })
    },
    // 轮询刷新：静默。弹窗开着时不打扰，上一轮没回来就跳过本轮避免请求叠加
    refreshQuietly() {
      if (this.loading || this.polling || this.open || this.importOpen) return
      this.polling = true
      this.fetchList().finally(() => { this.polling = false })
    },
    fetchList() {
      const seq = ++this.listSeq
      return listAttendee(this.queryParams).then(res => {
        // 已有更新的请求发出（搜索 / 分页 / 轮询）：丢弃本轮过期响应
        if (seq !== this.listSeq) return
        this.mergeRows(res.rows || [])
        this.total = res.total
        this.listError = ""
      }).catch(() => {
        if (seq !== this.listSeq) return
        // 会话已过期时请求会快速失败：静默跳过，避免常驻提示一直闪
        if (isSessionExpired()) return
        // 不再依赖 request.js 的一次性红条（轮询失败每 10 秒一条会刷屏），
        // 改为页面内常驻提示，让「数据已经多久没更新」这件事可见
        this.listError = "名单自动刷新失败，正在重试…（当前显示的可能是旧数据）"
      })
    },
    // 按 id 就地合并，而不是整体替换数组：行序不变、勾选不丢
    mergeRows(rows) {
      const prevById = new Map(this.attendeeList.map(r => [r.id, r]))
      const changed = []
      this.attendeeList = rows.map(r => {
        const prev = prevById.get(r.id)
        if (!prev) {
          changed.push(r.id)
          return r
        }
        // 内容没变就复用原对象，避免整表重渲染
        if (this.rowFingerprint(prev) === this.rowFingerprint(r)) return prev
        changed.push(r.id)
        return r
      })
      if (changed.length) this.flashChanged(changed)
    },
    // 只比对会因「别人签到」或「本页编辑」而变化的字段
    rowFingerprint(r) {
      return [r.name, r.phone, r.channel, r.ticketType, r.ticketTypeManual, r.drawEligible,
              r.company, r.email, r.position, r.signed, r.portNo, r.checkinTime, r.benefitGranted].join("|")
    },
    // 变化的行短暂高亮，让操作员注意到「刚才这一行更新了」，而不是数字悄悄变
    flashChanged(ids) {
      this.highlightIds = (ids || []).filter(id => id != null)
      if (!this.highlightIds.length) return
      if (this.highlightTimer) clearTimeout(this.highlightTimer)
      this.highlightTimer = setTimeout(() => { this.highlightIds = [] }, 3000)
    },
    rowClassName({ row }) {
      return this.highlightIds.indexOf(row.id) >= 0 ? "row-changed" : ""
    },
    // 这行是否满足当前筛选条件（乐观插入前判断，避免插进一条会被筛掉的行）
    matchesQuery(row) {
      const q = this.queryParams
      const like = (v, kw) => !kw || String(v == null ? "" : v).indexOf(kw) >= 0
      if (!like(row.name, q.name) || !like(row.phone, q.phone)) return false
      if (!like(row.company, q.company) || !like(row.position, q.position)) return false
      if (q.channel && row.channel !== q.channel) return false
      if (q.ticketType && row.ticketType !== q.ticketType) return false
      if (q.signed !== null && q.signed !== undefined && Number(q.signed) !== Number(row.signed || 0)) return false
      if (q.drawEligible !== null && q.drawEligible !== undefined &&
          Number(q.drawEligible) !== Number(row.drawEligible || 0)) return false
      return true
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
        // 默认不参与抽奖：与文档口径一致，选中渠道后由映射覆盖
        ticketType: null, drawEligible: 0, company: null, email: null,
        position: null
      }
      this.resetForm("form")
    },
    cancel() {
      this.open = false
      this.reset()
    },
    // 渠道下拉数据源：映射表即白名单，只允许从中选择；optionselect 不分页，避免写死 pageSize 截断
    loadChannelOptions() {
      optionselectChannelMapping().then(res => {
        this.channelOptions = res.data || []
      }).catch(() => {
        this.channelOptions = []
      })
    },
    // 选中渠道后自动带出权益与资格，与导入口径保持一致，避免手填出矛盾组合
    onChannelChange(channel) {
      const m = this.channelOptions.find(x => x.channelSource === channel)
      if (!m) return
      this.form.ticketType = m.benefitType
      this.$set(this.form, "drawEligible", m.drawEligible)
    },
    handleAdd() {
      this.reset()
      this.title = "新增参会人"
      this.open = true
      this.loadChannelOptions()
    },
    handleUpdate(row) {
      this.reset()
      this.loadChannelOptions()
      const id = row.id || this.ids[0]
      getAttendee(id).then(res => {
        this.form = res.data
        this.title = "补录 / 修改信息"
        this.open = true
      }).catch(() => {
        this.$modal.msgError("获取参会人信息失败，请重试")
      })
    },
    // 把权益类型恢复为「跟随渠道来源映射」：清掉人工标记并按映射回填
    handleResetTicketType() {
      this.$modal.confirm("将按渠道来源映射重新回填该人的权益类型，人工填写的内容会丢失，确定？").then(() => {
        return resetAttendeeTicketType(this.form.id)
      }).then(() => {
        this.$modal.msgSuccess("已恢复为跟随映射")
        // 重新拉一次，拿到映射回填后的真实值与更新后的版本号，
        // 否则直接用旧版本号提交会被乐观锁拒绝
        return getAttendee(this.form.id)
      }).then(res => {
        this.form = res.data
        const i = this.attendeeList.findIndex(r => r.id === res.data.id)
        if (i >= 0) {
          this.$set(this.attendeeList, i, { ...this.attendeeList[i], ...res.data })
          this.flashChanged([res.data.id])
        }
      }).catch(() => {})
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (!valid) return
        const isEdit = this.form.id != null
        // 带上读取时的版本号做乐观锁：若编辑期间被别人改过，后端会拒绝而不是静默覆盖
        const payload = { ...this.form }
        this.submitting = true
        const req = isEdit ? updateAttendee(payload) : addAttendee(payload)
        req.then(res => {
          this.open = false
          this.$modal.msgSuccess(isEdit ? "修改成功" : "新增成功")
          if (isEdit) {
            // 乐观更新：立刻把改动写回本地那一行，不用等下一轮轮询
            const i = this.attendeeList.findIndex(r => r.id === payload.id)
            if (i >= 0) {
              this.$set(this.attendeeList, i, { ...this.attendeeList[i], ...payload })
              this.flashChanged([payload.id])
            }
          } else {
            const created = res.data || {}
            const row = { ...payload, ...created, signed: 0, portNo: null, checkinTime: null }
            // 只有符合当前筛选条件才乐观插入，否则交给随后的静默对账决定
            if (created.id != null && this.matchesQuery(row)) {
              this.attendeeList.unshift(row)
              this.total += 1
              this.flashChanged([created.id])
            }
          }
          // 静默对账：取回服务端写入的 update_time（版本号）与签到状态
          this.refreshQuietly()
        }).catch(() => {
          this.$modal.msgError(isEdit ? "修改失败，请重试" : "新增失败，请重试")
        }).finally(() => { this.submitting = false })
      })
    },
    handleDelete(row) {
      const ids = row.id || this.ids
      const idList = Array.isArray(ids) ? ids : [ids]
      this.$modal.confirm('确认删除选中的 ' + (row.id ? 1 : ids.length) + ' 条名单？')
        .then(() => delAttendee(ids))
        .then(() => {
          // 乐观更新：先从本地列表移除，界面立刻有反馈
          this.attendeeList = this.attendeeList.filter(r => idList.indexOf(r.id) < 0)
          this.total = Math.max(0, this.total - idList.length)
          this.ids = []
          this.single = true
          this.multiple = true
          this.$modal.msgSuccess("删除成功")
          this.refreshQuietly()
        })
        .catch(() => {})
    },
    // 权益发放：需已签到、一人一条、发放后不可撤销（按钮已对未签到/已发放置灰）
    handleGrant(row) {
      // 纯文本：$modal.confirm 不渲染 HTML，姓名/权益类型也不能拼标签
      const tip = `确认为「${row.name}」发放权益？` +
        `权益内容：${row.ticketType || '（未配置）'}；` +
        `端口号：${row.portNo}。` +
        `发放后不可撤销；同一人重复发放会被系统拦截。`
      this.$modal.confirm(tip).then(() => {
        return grantBenefit(row.id)
      }).then(() => {
        this.$modal.msgSuccess("发放成功")
        this.refreshQuietly()
      }).catch(() => {})
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
      this.resetPreview()
      this.$nextTick(() => {
        if (this.$refs.upload) this.$refs.upload.clearFiles()
      })
    },
    // 文件或模式一变，旧报告立即作废：否则会拿上一次的结论给这一次背书
    resetPreview() {
      this.preview = null
      // 序号递增让在途的旧预检响应作废（换文件 / 移除文件 / 关闭弹窗都会走到这里）
      this.previewSeq++
      this.previewing = false
    },
    onFileChange(file) {
      this.importFile = file.raw
      this.resetPreview()
      // 选完文件自动预检：让操作员第一眼就看到问题，而不是点导入才知道
      if (this.importFile) this.runPreview()
    },
    onFileRemove() {
      this.importFile = null
      this.resetPreview()
    },
    // 预检：只解析不落库
    runPreview() {
      if (!this.importFile) {
        this.$modal.msgWarning("请先选择文件")
        return
      }
      // 在途时重复点击不再发请求，避免同一文件被解析多次
      if (this.previewing) return
      const token = fileToken(this.importFile)
      const seq = ++this.previewSeq
      const fd = new FormData()
      fd.append("file", this.importFile)
      fd.append("mode", this.importMode)

      this.previewing = true
      previewImport(fd).then(res => {
        // 文件已变更：这是一份旧文件的报告，直接丢弃
        if (seq !== this.previewSeq) return
        const data = res.data
        // 空对象/缺字段曾被当成「预检通过 0 行」而放行，必须先校验结构
        if (!isValidPreview(data)) {
          this.preview = null
          this.$modal.msgWarning("预检结果异常，请重试")
          return
        }
        // 记住这份报告对应的文件，提交前再核对一次
        this.preview = { ...data, fileToken: token }
      }).catch(() => {
        if (seq !== this.previewSeq) return
        this.preview = null
      }).finally(() => {
        if (seq === this.previewSeq) this.previewing = false
      })
    },
    submitImport() {
      if (!this.importFile) {
        this.$modal.msgWarning("请先选择文件")
        return
      }
      if (!this.preview) {
        this.$modal.msgWarning("请先预检，确认解析结果无误")
        return
      }
      // 预检后又换了文件：旧报告不能给新文件背书
      if (this.preview.fileToken !== fileToken(this.importFile)) {
        this.$modal.msgError("文件已变更，请重新预检")
        return
      }
      if (!isValidPreview(this.preview)) {
        this.$modal.msgWarning("预检结果异常，请重试")
        return
      }
      // 未配置映射的渠道直接拒绝，不再提供强制导入：
      // 放行只会得到一份「看起来成功」的残缺名单，事后极难发现
      if (this.preview.level === "BLOCK") {
        this.$modal.msgError("存在阻塞问题，请先在「设置 → 渠道来源映射」补全对应渠道后再导入")
        return
      }
      this.doImport()
    },
    doImport() {
      const fd = new FormData()
      fd.append("file", this.importFile)
      fd.append("mode", this.importMode)

      this.importing = true
      importAttendee(fd).then(res => {
        this.importing = false
        this.importOpen = false
        const s = res.data || {}
        const mappingTip = s.skipped > 0
          ? `有 ${s.skipped} 行未导入，详见跳过明细。`
          : ""
        this.$alert(
          `新增 ${s.inserted || 0} 条，更新 ${s.updated || 0} 条，跳过 ${s.skipped || 0} 条。` +
            `当前有效名单共 ${s.total || 0} 人。${mappingTip}`,
          "导入完成"
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
/* 轮询发现该行数据变化时短暂染色，让操作员注意到「刚才这一行更新了」。
   加在 td 上：Element 的表格背景色画在 td，只染 tr 是看不见的 */
.app-container ::v-deep .el-table .row-changed td {
  animation: kconRowFlash 3s ease-out;
}
@keyframes kconRowFlash {
  0%   { background-color: #fdf1c8; }
  100% { background-color: transparent; }
}
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

/* ---------- 导入预检报告 ---------- */
.preview-box {
  margin-top: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px 14px;
  max-height: 340px;
  overflow-y: auto;
  background: #fafafa;
}
.preview-head {
  display: flex;
  align-items: center;
  font-weight: 600;
  margin-bottom: 8px;
}
.preview-head i {
  margin-right: 6px;
  font-size: 16px;
}
.preview-head.lv-block { color: #d4342a; }
.preview-head.lv-warn { color: #e6a23c; }
.preview-head.lv-ok { color: #67c23a; }
.preview-stat {
  color: #606266;
  font-size: 13px;
  margin-bottom: 10px;
}
.preview-list {
  margin-top: 10px;
}
.preview-list-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.preview-item {
  font-size: 12px;
  line-height: 1.7;
  padding-left: 8px;
  border-left: 2px solid transparent;
}
.preview-item.err {
  color: #d4342a;
  border-left-color: #d4342a;
}
.preview-item.warn {
  color: #e6a23c;
  border-left-color: #e6a23c;
}
.ok-text { color: #67c23a; font-weight: 600; }
.no-text { color: #909399; }
</style>
