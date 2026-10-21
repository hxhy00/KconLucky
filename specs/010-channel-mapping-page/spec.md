# Feature Specification: 渠道来源映射独立页面

**Feature Branch**: `feature/016-channel-mapping-page`

**Created**: 2026-10-19

**Status**: 已实现；迁移 v6 已在本地库执行，列表/新增/重复校验/修改/删除与导入映射优先级均已实测

**Input**: 用户反馈：用字典备注承载「是否参与抽奖」过于隐晦；字典管理的操作路径太长。要求改为独立表，并像「奖品设置」一样用单个 tab 直接增删改。

## 背景

spec 009 第一版复用了 RuoYi 字典：
- `dict_label` = 渠道来源
- `dict_value` = 权益类型
- `remark` = 是/否

问题：

1. 字典模型只有 label/value 两个语义字段，第三个值只能塞进「备注」，无法表达强类型；
2. 编辑路径是「系统管理 → 字典管理 → 选择 dict_type → 字典数据」，现场人员难找；
3. 无法在 KCon 菜单内直接看到完整映射。

本规格改为独立表 + 独立页面，替代字典方案。

## 数据模型

表 `kcon_channel_mapping`：

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `channel_source` | VARCHAR(64) | 渠道来源，唯一 |
| `benefit_type` | VARCHAR(128) | 权益类型 |
| `draw_eligible` | TINYINT | 1=参与抽奖 0=不参与 |
| `sort` | INT | 列表排序 |
| `create_by` / `create_time` / `update_by` / `update_time` | | RuoYi 标准字段 |

初始数据沿用 spec 009 的 7 条映射。

## Requirements

### Functional Requirements

- **FR-P1**: 系统 MUST 使用独立表 `kcon_channel_mapping` 存储渠道来源、权益类型、参与抽奖。
- **FR-P2**: `channel_source` MUST 唯一；新增/修改重复时明确报错。
- **FR-P3**: 管理端 MUST 在「设置 → 渠道来源映射」提供单页 CRUD，交互与「奖品设置」一致。
- **FR-P4**: 列表 MUST 展示渠道来源、权益类型、参与抽奖、排序，并支持按渠道来源搜索。
- **FR-P5**: 新增/修改弹窗 MUST 直接编辑三个字段：渠道来源、权益类型、参与抽奖。
- **FR-P6**: 导入 MUST 从 `kcon_channel_mapping` 读取映射，不再读取字典。
- **FR-P7**: 命中映射时映射优先；未命中时回退行内值；都没有时参与抽奖=否，并在导入结果提示。
- **FR-P8**: 迁移 v6 MUST 创建新表、把已有字典数据迁入新表、删除旧字典类型与数据，并把 3009 菜单改为新页面。
- **FR-P9**: 新装库的 `kcon_lucky.sql` 与 `kcon_menu.sql` MUST 直接包含新表、初始数据、新菜单与按钮权限。
- **FR-P10**: 旧 9 列导入文件与旧表头 MUST 继续可用。

### 增量需求（2026-10-21 修订）

现场发现两个真实缺陷，故对 FR-P7 与 SC-P3 做如下修订：

- **FR-P11（导入预检）**: 导入 MUST 支持「先解析后落库」的预检接口
  `POST /admin/attendee/import/preview`，返回阻塞问题、提示、将跳过行、渠道→权益/资格分布。
  预检与正式导入 MUST 共用同一套解析逻辑，避免结论不一致。
- **FR-P12（严格模式）**: 导入默认 `strict=true`，存在「渠道来源未配置映射」时 MUST 整批拒绝并提示去补映射；
  传 `strict=false` 才允许强制导入。
- **FR-P13（映射重算）**: 映射新增/修改后 MUST 自动重算该渠道存量名单的权益类型与抽奖资格；
  另提供 `POST /admin/channel-mapping/resync` 做全量重算。
- **FR-P14（重算锁定）**: 已签到或已有未作废中奖记录的人员 MUST NOT 被自动改动，
  MUST 输出清单供人工核对，避免形成「已中奖但资格变为否」的矛盾状态。

## Success Criteria

- **SC-P1**: 从 KCon 侧边栏「设置 → 渠道来源映射」一次点击进入，页面直接列出全部映射。
- **SC-P2**: 新增一条「测试渠道 → 测试权益 → 参与」后，下一次导入立即生效。
- ~~**SC-P3**: 修改映射不影响已有名单。~~
  → 已被 FR-P13 取代：修改映射 MUST 重算存量名单（锁定人员除外）。
  原口径导致「先导入、后补映射」的渠道人员永远保留导入时的资格，
  现场出现「配了嘉宾不参与抽奖，结果还是被抽中」。
- **SC-P4**: 重复渠道来源被拒绝并给出明确提示。
- **SC-P5**: 旧字典 `kcon_channel_mapping` 在迁移后不再存在，避免两套事实源。
- **SC-P6**: 文件含未配置映射的渠道时，预检 MUST 报 BLOCK 且直接导入 MUST 被拒，不产生任何落库。
- **SC-P7**: 修改映射后，未签到的存量人员资格 MUST 立即跟随变更；已签到/已中奖人员出现在「未修改清单」中。

## Out of Scope

- ~~映射修改后自动刷新历史名单。~~ → 已纳入 FR-P13。
- 映射审批流或变更历史。
- 按渠道来源关键字模糊匹配。
