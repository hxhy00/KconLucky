# Feature Specification: 去除 RuoYi/若依 品牌残留

**Feature Directory**: `specs/001-remove-ruoyi-branding`

**Created**: 2026-10-15

**Status**: Draft

**Input**: User description: "前后端代码都已写完，不改业务逻辑，只做表层品牌替换：清掉用户能看到的 RuoYi/若依 字样与视觉痕迹（title、logo、版权、主题色、登录页、Swagger、启动打印、演示菜单），并 grep 清一遍 ruoyi 残留。"

> **本项目适配说明（不要删除本段）**
> - 需求编号**必须沿用** `docs/01-需求规格.md` 的 `FR-x` / `AC-x` 编号，不要另起 `FR-001` 序列。
>   若该需求在 `docs/01` 中不存在，编号为 `FR-NEW-<序号>` 并保留 `<!-- 新增需求，待回写 docs/01 -->` 注记。
> - `docs/` 是唯一需求基线，本文件是它的**局部视图**，只写本 feature 涉及的部分。
> - 定稿前先读：`docs/01-需求规格.md`（必读）、`docs/02-数据库设计.md`、`docs/03-接口设计.md`（按需）。

## 背景与问题（必填）

**业务背景**：

本系统基于 RuoYi-Vue 3.9.2 框架二次开发，业务代码（`com.ruoyi.kcon`、`views/kcon/`）已完成并部署到公网（`123.56.72.135`）。开发过程中已做过**一轮**品牌改造，但**只覆盖了一部分**，导致当前处于「改了一半」的不一致状态：

| 已改造 | 仍是若依 |
|---|---|
| 浏览器标题 `KCon 2026 签到抽奖管理端` | 开发/测试环境 `VUE_APP_TITLE = 若依管理系统` |
| 生产环境 `VUE_APP_TITLE = KCon 2026` | 页面版权 `Copyright © 2018-2026 RuoYi` |
| 主题色 `#d4342a`（KCon 红） | Swagger 标题「若依管理系统_接口文档」 |
| 侧边栏/登录页 logo 换成 `logo-mark.png` | 启动横幅打印「若依启动成功」 |
| | 加载页背景色 `#7171C6`（若依紫） |
| | SQL 里「若依官网」菜单与若依宣传公告 |

问题影响：

1. **对外形象**：公网访客、评委或合作方看到「若依管理系统」「Copyright © RuoYi」，直接暴露「这是个套模板的项目」，与 KCon 大会的品牌调性冲突。
2. **开发/测试环境穿帮**：开发与测试环境的 `VUE_APP_TITLE` 未同步，本地演示时标题栏显示「若依管理系统」，比生产环境更容易穿帮。
3. **源码可检索性**：`grep ruoyi` 仍能命中大量配置与注释，交付或开源时需要清理。

来源：`docs/01-需求规格.md` §13「变更单 · 2026-10-15（公网部署后首批）」的同一批次后续项。

**不做的部分（Out of Scope）**：

本 feature **明确不做**以下高成本、高风险的改动（用户已确认「主要用户看不到就不折腾」）：

1. **不改 Java 包名 `com.ruoyi.*`**。全仓约 620+ 个文件含该包名，改动会牵连所有 `import`、Maven 模块坐标、MyBatis `typeAliasesPackage`、日志包名，风险极高且用户不可见。
2. **不改目录名 `ruoyi/`、`ruoyi-ui/`、`ruoyi-admin/` 等**。牵连 `docker-compose.yml`、构建脚本、部署文档路径，属重构范畴。
3. **不改前端文件名** `src/utils/ruoyi.js`、`src/assets/styles/ruoyi.scss`（被多处 `import` 引用，改名收益低于风险）。
4. **不改任何业务逻辑**：签到、抽奖、兑奖核验、数据迁移等功能代码一行不动。
5. **不改 `application.yml` 的 `ruoyi:` 配置前缀**与 `RuoYiConfig` 的 `@ConfigurationProperties(prefix = "ruoyi")`——二者强绑定，改动有启动失败风险，且用户不可见。
6. **不改 `@author ruoyi` 注释**（≥264 文件）——纯注释、用户不可见，批量替换的收益不足以抵消 264 文件的 diff 噪音。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 访客不再看到若依品牌 (Priority: P1)

作为大会访客或评委，我打开管理端/签到端时，页面上不应出现任何「若依」「RuoYi」字样或若依特有的视觉特征，让系统看起来是 KCon 自有产品。

**对应需求**：`FR-51`、`FR-52`、`FR-53`（见下方 Functional Requirements）

**Why this priority**：这是本次改造的核心价值。前一轮已完成大部分，剩余残留若不清，公网形象仍会穿帮。P1 的全部项都是「改一行配置」级别的低成本高收益改动。

**Independent Test**：打开管理端登录页与内页，检查浏览器标签标题、页面版权文字、加载动画配色；打开 Swagger 页面检查标题与描述。全部无「若依」字样且配色为 KCon 红即为通过。

**Acceptance Scenarios**:

1. **Given** 以任何环境（development / staging / production）启动前端，**When** 打开浏览器访问管理端，**Then** 浏览器标签标题显示 `KCon 2026 签到抽奖管理端`，不出现「若依管理系统」。
2. **Given** 打开管理端任一页面，**When** 查看页面底部（若版权区可见），**Then** 显示 KCon 版权文字，不出现 `Copyright © RuoYi`。
3. **Given** 前端正在加载资源，**When** 观察加载动画背景，**Then** 背景色为 KCon 品牌色系（红/黑），不是若依紫 `#7171C6`。
4. **Given** 打开 Swagger/Knife4j 接口文档页，**When** 查看文档标题与描述，**Then** 显示 KCon 相关标题，不出现「若依管理系统_接口文档」与若依默认描述文字。

---

### User Story 2 - 启动与数据库层面无若依痕迹 (Priority: P2)

作为运维/开发者，我启动服务时控制台不打印「若依启动成功」；初始化或查看数据库时，不出现「若依官网」菜单与若依宣传公告。

**对应需求**：`FR-54`、`FR-55`、`FR-56`

**Why this priority**：这些痕迹只在控制台或数据库中出现，普通访客看不到，但交付演示时会穿帮，属「顺手清掉」级别。

**Independent Test**：启动后端观察控制台输出；检查菜单初始化 SQL 与公告 SQL 中是否还有「若依官网」「若依」字样。

**Acceptance Scenarios**:

1. **Given** 后端服务启动，**When** 观察控制台输出，**Then** 不出现「若依启动成功」字样。
2. **Given** 一个全新初始化的数据库（按 `sql/00-ry-base.sql` 建立），**When** 登录管理端查看菜单与公告，**Then** 无「若依官网」菜单项，公告中无若依宣传内容。
3. **Given** 管理端接口文档配置，**When** 查看 Swagger 分组名，**Then** 不再使用无关的默认分组名「测试模块」。

---

### User Story 3 - 可检索的源码残留清理完成 (Priority: P3)

作为交付方/开源维护者，在交付或开源前运行 `grep -ri "ruoyi\|若依"` 时，命中结果应只剩「已明确保留」的部分（包名、目录名、注释），其余配置与文案残留应已清除。

**对应需求**：`FR-57`

**Why this priority**：用户不可见，但对交付质量有影响。P3 只要求「清单化 + 清理可控项」，不追求零命中。

**Independent Test**：运行全局 grep，对照保留清单核对，不在保留清单内的命中均已处理。

**Acceptance Scenarios**:

1. **Given** 完成本 feature 全部改动，**When** 在 `ruoyi-ui/` 与 `ruoyi/` 下运行 `grep -ri "ruoyi\|若依"`，**Then** 命中项全部可归入「已保留清单」（包名 / 目录名 / `@author ruoyi` 注释 / 依赖声明），无遗留的**用户可见文案**类命中。

---

### Edge Cases

- **环境变量兜底值**：`vue.config.js` 中有 `process.env.VUE_APP_TITLE || '若依管理系统'` 的兜底值。若某环境未设置 `VUE_APP_TITLE`，标题会回落到「若依管理系统」。本 feature 要求兜底值也一并改为 KCon，避免漏网。
- **已部署实例的存量数据**：SQL 演示菜单/公告的修改**只影响新装库**。已在运行的公网实例需另行执行迁移脚本或手工清理（本 feature 的验收针对「新装库」与「已部署库」分别定义）。
- **版权区默认是否可见**：`settings.js` 中 `footerVisible: false`（默认隐藏）。改动仍必须做，因为用户可手动开启，且隐藏不等于不存在。
- **不引入新的品牌不一致**：改 `.env.development` / `.env.staging` 的 TITLE 时，三个环境（dev/staging/prod）的标题文案必须统一，不能改出一个新前缀。

## Requirements *(mandatory)*

### Functional Requirements

> 编号承接 `docs/01-需求规格.md` §13 变更单（上一项为 FR-50），本次为同一批次续接。

**用户可见层（P1）**

- **FR-51**: 前端**所有环境**的系统标题必须为 KCon 品牌文案。`ruoyi-ui/.env.development`、`.env.staging`、`.env.production` 的 `VUE_APP_TITLE` 三处一致；`vue.config.js` 中的兜底默认值也必须改为 KCon 文案，不得保留「若依管理系统」。 <!-- 新增需求，待回写 docs/01-需求规格.md -->
- **FR-52**: 页面版权文字（`settings.js` 的 `footerContent`）必须改为 KCon 版权表述，不得出现 `RuoYi` 字样。
- **FR-53**: 接口文档（Swagger/Knife4j）的标题、描述与分组名必须改为 KCon 相关表述，不得出现「若依管理系统」与无关的默认描述。 <!-- 新增需求，待回写 docs/01-需求规格.md -->
- **FR-54**: 前端资源加载页背景色必须改为 KCon 品牌色系，不得保留若依默认紫 `#7171C6`。 <!-- 新增需求，待回写 docs/01-需求规格.md -->

**启动与数据层（P2）**

- **FR-55**: 后端启动横幅必须移除「若依启动成功」等若依专属字样。 <!-- 新增需求，待回写 docs/01-需求规格.md -->
- **FR-56**: 菜单与公告初始化 SQL 必须移除「若依官网」菜单项与若依宣传公告内容（含指向 ruoyi.vip / gitee 若依仓库的链接）。 <!-- 新增需求，待回写 docs/01-需求规格.md -->

**源码可检索性（P3）**

- **FR-57**: 必须产出一份「品牌残留保留清单」，说明哪些 `ruoyi` 命中是**有意保留**（包名、目录名、`@author ruoyi` 注释、`pom.xml` 坐标、依赖声明），并清理清单之外的所有用户可见文案残留。 <!-- 新增需求，待回写 docs/01-需求规格.md -->

**约束性需求**

- **FR-58**: 本 feature 的所有改动**不得触碰业务逻辑**。签到、抽奖、兑奖核验、数据迁移、权限校验等功能的代码与 SQL 逻辑一行不改；仅允许修改展示层文案、样式变量、静态资源、配置值与初始化数据。 <!-- 新增需求，待回写 docs/01-需求规格.md -->
- **FR-59**: 改动前必须提交 Git 基线；`application.yml` 的 `ruoyi:` 前缀与 `RuoYiConfig` 注解**不得改动**（强绑定，改动会导致启动失败）。 <!-- 新增需求，待回写 docs/01-需求规格.md -->

### Mock 清单

- 无。本 feature 是文案与配置替换，不引入任何 mock 数据。

### Key Entities *(include if feature involves data)*

<!-- 基线是 docs/02-数据库设计.md。此处只写本 feature 的增量或变更，并明确标注「新增表/新增字段/口径变更」，定稿后回写 docs/02。-->

- **无库表结构变更**。仅在初始化数据层面调整：`sys_menu` 表移除「若依官网」菜单行；`sys_notice` 表移除若依宣传公告行。**属初始化数据变更，不属结构变更**，因此 `docs/02-数据库设计.md` 无需修改，但 `sql/00-ry-base.sql` 等初始化脚本需同步。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-1**: 三个前端环境（development / staging / production）启动后，浏览器标签标题均显示 KCon 品牌文案，命中「若依」的次数为 **0**。
- **SC-2**: 管理端可见区域（登录页、内页、页脚、加载页、接口文档页）中，人工排查「若依」或「RuoYi」字样，命中次数为 **0**。
- **SC-3**: 后端控制台启动输出中，命中「若依」的次数为 **0**。
- **SC-4**: 按 `sql/00-ry-base.sql` 新建的数据库，菜单表中含「若依」字样的记录数为 **0**，公告表中含「若依」字样的记录数为 **0**。
- **SC-5**: 在 `ruoyi/` 与 `ruoyi-ui/` 下运行 `grep -ri "ruoyi\|若依"`，所有命中项均可归入「品牌残留保留清单」，无未解释的用户可见文案命中。
- **SC-6**: 本 feature 的改动不导致任何既有功能回归——`docs/05-功能缺口.md` 中记录的 15 项已修复缺口在改动后仍全部通过（回归验证）。

> 若 `docs/01-需求规格.md` 已有对应 `AC-x`，请直接沿用该编号并引用原文。

## Assumptions

- **前一轮改造已完成**：浏览器标题（`index.html`）、生产环境 `VUE_APP_TITLE`、主题色 `#d4342a`、logo `logo-mark.png` 已改造完成，本 feature 只处理**残留项**，不重复改造已验证完成的部分。
- **目标用户**：大会访客（签到端）、管理端操作员、评委/合作方（看管理端与接口文档）。
- **范围边界**：以「用户肉眼可见」为第一优先；源码层只清理低成本项，包名与目录名重命名明确排除。
- **依赖**：改动集中在 `ruoyi-ui/src/settings.js`、`ruoyi-ui/.env.*`、`ruoyi-ui/public/index.html`、`ruoyi-ui/vue.config.js`、`ruoyi/ruoyi-admin/.../SwaggerConfig.java`、`ruoyi/ruoyi-admin/.../banner.txt`、`ruoyi/ruoyi-admin/.../RuoYiApplication.java`（仅改打印文案）、`sql/00-ry-base.sql`。
- **logo.png 待确认**：调研发现 `ruoyi-ui/src/assets/logo/logo.png` 未被代码引用（引用的是 `logo-mark.png`）。实现阶段需目视确认其是否为若依原版图，若是则删除或替换（属 FR-51 的附带项）。
- **公网存量库**：已运行实例的菜单/公告清理不在本 spec 的自动化范围内，实现阶段需产出人工执行说明。

## 回写清单（实现完成后逐条确认）

<!-- /speckit.implement 结束前必须处理完本节 -->

- [ ] 新增 `FR-NEW-*` 已回写 `docs/01-需求规格.md`（本 feature 的 FR-51 ~ FR-59 作为 §13 批次续接）
- [ ] 库表变更已回写 `docs/02-数据库设计.md`，并产出幂等迁移脚本（**本 feature 无结构变更**，仅初始化数据；无需迁移脚本，但需说明）
- [ ] 接口变更已回写 `docs/03-接口设计.md`（**本 feature 无接口变更**，Swagger 标题改动不属接口契约变更）
- [ ] 部署/环境变量变更已回写 `docs/04-部署运行.md`（`.env.development` / `.env.staging` 的 TITLE 改动需说明）
- [ ] Mock 条目已登记或已清理 `docs/06-Mock台账.md`（**本 feature 无 mock**）
- [ ] 「品牌残留保留清单」已产出（FR-57），可随 `docs/05-功能缺口.md` 或本文档附录归置
- [ ] 回归验证：`docs/05-功能缺口.md` 的 15 项缺口复测通过（SC-6）
