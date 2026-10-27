# KCon2026 赠票系统（生产级）
> 渠道 → 推荐人 → 赠票接收人 三级分发 · 实名注册 · 审批与邮件出箱
> 现场签到、抽奖、兑奖由下游 KconLucky 系统负责（见 `specs/011-gift-ticket-handoff`）
> 规模基线：**20 渠道 / 500 票**
---
## 一、项目状态
| 项 | 状态 |
|---|---|
| 需求文档 | ✅ V3.0 定稿 |
| 架构设计 | ✅ 已评审 |
| 数据库设计 | ✅ 已评审 |
| 接口设计 | ✅ 24 个接口 |
| 代码实现 | ✅ 完成（v1 边界版） |
| 测试 | ✅ **552 个全通过**（单元 + 集成 + 系统） |
---
## 二、快速开始
```bash
cd gift-system
# 1. 创建虚拟环境
python3 -m venv .venv
source .venv/bin/activate
# 2. 安装依赖
pip install -r requirements.txt
# 3. 启动服务（可选环境变量，见 docs/engineering/06-部署文档.md）
python run.py
```
访问 **http://127.0.0.1:8848**

**管理员账号**：通过环境变量 `KCON_ADMIN_EMAILS` 配置（admin 表为空时作为初始种子写入；docker-compose 已强制必填）。未配置时系统没有任何内置账号。管理员可在后台「管理员账户」卡片中增停删（最多 3 个启用）。

**邮件发送**：默认 `OutboxMailer`（邮件落库到 `/mailbox`）；配置 `KCON_SMTP_*` 后自动切换 `SmtpMailer` 真发。当前生产已接入企业微信邮（发件箱 `kcon_master@knownsec.com`），凭据只写在服务器 `.env`。验证码只发给已存在的管理员/审批员，未知邮箱不发信。

**核心功能**：
- 三级分发：渠道 → 推荐人 → 赠票接收人，实名注册 + 一次性审批链接
- 管理员账户管理：列表 / 新增 / 停用 / 删除（≤3 个启用，保护最后一个与本人）
- 邮件出箱（`/mailbox`）：系统发出的全部邮件可查看，SMTP 未接通时运营据此复制审批链接
- 名单导出：`scripts/export_recipients.py` 导出已审批接收人 CSV，交付下游 KconLucky 系统
- 现场签到不在本系统暴露入口（签到由下游 KconLucky 系统负责，签到 API 保留）
---
## 三、项目结构
```
kcon-prod/
├── src/kcon/
│   ├── app.py                  # 依赖注入容器
│   ├── config.py               # 配置（环境变量 + 管理员种子）
│   ├── domain/                 # 领域层（纯函数，无 I/O）
│   │   ├── enums.py            # 状态枚举
│   │   ├── validators.py       # 字段校验器
│   │   ├── rules.py            # 业务规则
│   │   ├── state_machine.py    # 状态机
│   │   ├── codes.py            # 编码规则
│   │   └── models.py           # 数据模型
│   ├── infra/                  # 基础设施层
│   │   ├── clock.py            # 时钟抽象
│   │   ├── random_source.py    # 随机源抽象
│   │   ├── database.py         # SQLite 封装
│   │   ├── repositories.py     # 14 个仓储
│   │   ├── mailer.py           # 邮件适配器（Outbox/SMTP/Fake）
│   │   ├── qrcode_gen.py       # 二维码生成
│   │   ├── external_checkin.py # 外部签到适配器
│   │   └── email_templates.py  # 10 封邮件模板
│   ├── service/                # 服务层（业务编排）
│   │   ├── auth_service.py
│   │   ├── channel_service.py
│   │   ├── referrer_service.py
│   │   ├── ticket_service.py
│   │   ├── checkin_service.py
│   │   └── stats_service.py
│   └── api/                    # 接口层
│       ├── middleware.py       # 会话/限流/鉴权
│       ├── handlers.py         # 24 个接口处理器
│       ├── router.py           # 路由表
│       └── server.py           # HTTP 服务器
├── tests/
│   ├── unit/                   # 单元测试（210 个）
│   ├── integration/            # 集成测试（283 个）
│   └── system/                 # 系统测试（15 个）
├── docs/
│   ├── engineering/            # 工程文档（6 份）
│   │   ├── 01-需求规格说明书.md
│   │   ├── 02-系统架构设计.md
│   │   ├── 03-数据库设计.md
│   │   ├── 04-接口设计.md
│   │   ├── 05-测试计划.md
│   │   └── 06-部署文档.md
│   └── process/                # 过程文档（4 份）
│       ├── 00-过程文档规范.md
│       ├── 测试执行报告.md
│       ├── 条件覆盖矩阵.md
│       ├── 代码评审记录.md
│       └── 缺陷记录.md
├── reports/                    # 覆盖率报告
│   ├── htmlcov/                # HTML 报告
│   └── coverage.xml            # XML 报告
├── scripts/                    # 辅助脚本
├── run.py                      # 启动入口
├── pytest.ini                  # pytest 配置
└── requirements.txt
```
---
## 四、测试
### 4.1 全量测试 + 覆盖率
```bash
.venv/bin/pytest tests/ -q \
  --cov=src/kcon --cov-branch \
  --cov-report=term-missing \
  --cov-report=html:reports/htmlcov \
  --cov-report=xml:reports/coverage.xml
```
### 4.2 分层执行
```bash
.venv/bin/pytest tests/unit/ -q          # 单元（210 个，0.2 秒）
.venv/bin/pytest tests/integration/ -q   # 集成（283 个，约 37 秒）
.venv/bin/pytest tests/system/ -q        # 系统（15 个，约 26 秒）
```
### 4.3 查看覆盖率报告
```bash
open reports/htmlcov/index.html
```
---
## 五、核心业务规则
| # | 规则 | 实现位置 |
|---|---|---|
| 1 | 管理员 ≤ 3 个，账号存 `admin` 表（首次启动按 `KCON_ADMIN_EMAILS` 播种，后台可增停删） | `app.py` / `handlers.py` |
| 2 | 渠道 ≤ 20 个，总配额 ≤ 500 张 | `rules.check_channel_limit` / `check_total_ticket_limit` |
| 3 | 验证码 6 位 / 10 分钟 / 60 秒间隔 / 单日 20 次 / 错 5 次锁 30 分钟 | `auth_service` |
| 4 | 推荐人单人上限（默认 20，可配 1～100） | `rules.check_referrer_cap` |
| 5 | 邮箱全局唯一，**提交时拦截** | `rules.check_email_conflict` |
| 6 | 一次性令牌 7 天有效，用后失效，绑定归属 | `rules.check_token_usable` / `check_token_owner` |
| 7 | 被拒 7 天后可重申（复用原记录） | `referrer_service.register` |
| 8 | 签到 = 扫码 + 发材料，`material_issued` 留痕 | `checkin_service.confirm` |
| 9 | 重复签到拦截，不重复发邮件 | `rules.check_duplicate_checkin` |
| 10 | M-07 实时发，M-08 汇总发（30 分钟/满 10 人） | `rules.should_flush_m08` |
| 11 | 外部签到异步调用，失败不阻断 | `checkin_service._sync_external` |
| 12 | 扫码落地页分级（未登录不展示个人信息） | `checkin_service.code_info` |
| 13 | 签到员不可见手机邮箱 | `handlers.checkin_lookup` |
| 14 | 跨渠道越权拦截 | `referrer_service.approve_by_id` |
---
## 六、10 封邮件模板
| 编号 | 收件人 | 场景 |
|---|---|---|
| M-01 | 审批员 | 渠道开通通知 |
| M-02 | 审批员 | 推荐人申请审批 |
| M-03 | 推荐人 | 专属赠票链接 + 二维码 |
| M-03R | 推荐人 | 申请被拒通知 |
| M-04 | 推荐人 | 赠票申请审批（优先级高） |
| M-05 | 接收人 | 注册成功 + 注册码二维码 |
| M-06 | 推荐人 | 赠票注册成功 |
| M-07 | 推荐人 | 参会人已签到（实时） |
| M-08 | 审批员 | 签到汇总（30 分钟/满 10 人） |
| M-09 | 管理员 | 数据日报 |
**视觉规范**：深色底 `#0b0e14` + 荧光青 `#00e5ff` + 品红 `#ff2d95`，表格布局 + 内联样式，兼容 QQ/163/Gmail/Outlook。
---
## 七、文档索引
### 工程文档
| 文档 | 说明 |
|---|---|
| [需求规格说明书](docs/engineering/01-需求规格说明书.md) | 角色权限、业务流程、规模基线、可测试性要求 |
| [系统架构设计](docs/engineering/02-系统架构设计.md) | 分层架构、依赖注入、技术决策、部署架构 |
| [数据库设计](docs/engineering/03-数据库设计.md) | 14 张表、索引、容量估算、关键 SQL |
| [接口设计](docs/engineering/04-接口设计.md) | 24 个接口、错误码字典、限流策略 |
| [测试计划](docs/engineering/05-测试计划.md) | 三层测试、覆盖率达成策略 |
| [部署文档](docs/engineering/06-部署文档.md) | 环境要求、systemd、Nginx、备份、运维手册 |
### 过程文档
| 文档 | 说明 |
|---|---|
| [过程文档规范](docs/process/00-过程文档规范.md) | 过程文档清单与模板 |
| [测试执行报告](docs/process/测试执行报告.md) | 508 个测试结果、覆盖率、压测数据 |
| [条件覆盖矩阵](docs/process/条件覆盖矩阵.md) | 18 处复合条件、58 个原子条件逐条审计 |
| [代码评审记录](docs/process/代码评审记录.md) | 24 个源文件评审、安全问题专项 |
| [缺陷记录](docs/process/缺陷记录.md) | 5 个缺陷的根因与修复 |
---
## 八、技术栈
| 层 | 技术 | 说明 |
|---|---|---|
| 语言 | Python 3.12 | 标准库为主，零框架 |
| 数据库 | SQLite 3.50（WAL） | 单机足够，零运维 |
| HTTP | 标准库 `http.server` | 无框架依赖 |
| 二维码 | qrcode 8.2 + Pillow 13.0 | 带 KCon 主视觉边框 |
| 测试 | pytest 9.1.1 + coverage 7.17 | 三层测试 + 覆盖率门禁 |
---
## 九、已知限制
| 项 | 说明 | 建议 |
|---|---|---|
| 真实邮件发送 | v1 延期，默认出箱落库（`/mailbox` 可查看） | 二期接入企业邮箱（配置 `KCON_SMTP_*` 即可启用） |
| 现场签到 | 本系统不提供签到页面入口，由下游 KconLucky 系统负责 | 签到 API 保留供集成测试与下游对接 |
| CSRF | 当前未启用 | 生产环境加 CSRF Token |
| 会话存储 | 内存字典 | 多实例部署改 Redis |
| 签到员密码 | SHA-256 | 生产建议 bcrypt |
| 离线签到 | 未实现 | 二期加本地缓存 |
| 外部签到系统 | 空实现 | 待甲方提供接口后对接 |
| 分支覆盖率 | 99.7%（1 个工具局限） | 已记录，不影响功能 |
---
## 十、许可
内部项目，仅供 KCon2026 使用。