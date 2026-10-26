# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

> **本项目适配说明（不要删除本段）**
> - 本项目的技术栈是**既定的**（见下方 Technical Context），不要提出更换技术栈的方案。
> - `docs/` 是权威基线：数据模型基线在 `docs/02-数据库设计.md`，接口基线在 `docs/03-接口设计.md`，
>   部署基线在 `docs/04-部署运行.md`。本计划只写增量，定稿后回写。
> - 第 6 阶段（架构设计）**必须先找成熟方案再与用户确认**，确认后才进入实现。

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  以下为本项目的既定技术栈，默认无需修改。只有确需引入新模式时才改动，并说明理由。
-->

**Language/Version**: Java 8 / Spring Boot 2.5（RuoYi-Vue 框架底座）；前端 Vue 2 + Element UI（`ruoyi-ui/`）

**Primary Dependencies**: RuoYi-Vue（校验、权限、字典、代码生成）、MyBatis、Druid、Redis、TailwindCSS（签到 H5 与抽奖大屏）

**Storage**: MySQL 8（库表基线见 `docs/02-数据库设计.md`）、Redis（缓存/会话）

**Testing**: [按需：当前以人工实测为主，见 `docs/05-功能缺口.md` 的实测记录格式]

**Target Platform**: 单机 Docker Compose 本地部署（第一期，数据不出本机）；公网版为第二期。

**Project Type**: Web application（三端共用一套 SpringBoot API）
- `ruoyi/` SpringBoot 后端
- `ruoyi-ui/` 管理端（Vue 2 + Element UI）
- `checkin-h5/` 签到端 H5
- `screen/` 抽奖大屏端

**Performance Goals**: [domain-specific, e.g. 现场并发签到 500 人/分钟 or NEEDS CLARIFICATION]

**Constraints**: [e.g. 单机部署、无外网依赖、现场断网可用]

**Scale/Scope**: [e.g. 参会人 ~4000、端口号池 1–65535]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

按 `.specify/memory/constitution.md` 逐条判定，至少覆盖：

- [ ] **一、文档契约**：需求编号是否沿用 `docs/01` 的 FR 编号？Out of Scope 是否已写？
- [ ] **二、Mock 管理**：所有 mock 是否登记到 `docs/07-Mock台账.md`？代码是否带 `MOCK:` 标记？
- [ ] **三、数据与迁移**：DDL 是否同时更新 `sql/kcon_lucky.sql` 与幂等迁移脚本？是否同步 `docs/02`？
- [ ] **四、控制权唯一**：写操作是否只在服务端做鉴权？签到端是否只有「签到」一个写操作？
- [ ] **五、依赖与工具链**：是否使用国内源？是否避免软链接？是否未新增冗余文档？
- [ ] **六、Git 与提交**：是否按 Conventional Commits 写 commit？是否只 commit 不 push？
- [ ] **七、语言与表达**：文档是否简体中文？术语是否统一？

[Gates determined based on constitution file]

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

<!-- 本项目实际结构（按 feature 删减未涉及部分） -->

```text
ruoyi/                                  # SpringBoot 后端
├── ruoyi-admin/                        # 启动模块 + 控制器
│   └── src/main/java/com/ruoyi/web/controller/kcon/
├── ruoyi-system/                       # 业务服务与 Mapper
│   └── src/main/java/com/ruoyi/system/{domain,service,mapper}/…
└── sql/                                # 建表与迁移脚本（或根 sql/）

ruoyi-ui/src/views/                     # 管理端页面
├── kcon/                               # 签到抽奖业务页
└── admin/                              # 管理端路由页

checkin-h5/                             # 签到端 H5（Tailwind）
screen/                                 # 抽奖大屏端（Tailwind + 动效）
sql/                                    # kcon_lucky.sql（新装）+ kcon_migrate_v*.sql（迁移）
docs/                                   # 需求/库表/接口/部署/缺口 基线
```

**Structure Decision**: [说明本 feature 落在哪些具体目录/文件上]

## 数据库与接口变更

| 类型 | 变更内容 | 回写目标 |
|---|---|---|
| 新增表 / 新增字段 / 索引 | [具体] | `docs/02-数据库设计.md` + `sql/kcon_lucky.sql` + `sql/kcon_migrate_vN.sql` |
| 新增 / 修改接口 | [具体：方法 路径 入参 出参] | `docs/03-接口设计.md` |
| 新增权限点 | [具体：`kcon:xx:yy`] | `docs/01` 或菜单初始化 SQL |

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 新增微服务] | [current need] | [why 单体不足] |
