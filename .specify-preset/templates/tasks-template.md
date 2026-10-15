# Tasks: [FEATURE NAME]

**Input**: Design documents from `/specs/[###-feature-name]/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The examples below include test tasks. Tests are OPTIONAL - only include them if explicitly requested in the feature specification.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

> **本项目适配说明（不要删除本段）**
> - 描述中写明**真实文件路径**（`ruoyi/…`、`ruoyi-ui/…`、`checkin-h5/…`、`screen/…`、`sql/…`），不要用 `src/`、`tests/` 这类通用占位路径。
> - 每条任务尽量带上 `FR-x` 引用，便于 `/speckit.analyze` 做需求覆盖度检查。
> - 涉及库表变更的任务必须**同时**包含：改 `sql/kcon_lucky.sql` + 新增幂等迁移脚本 + 回写 `docs/02`。
> - 涉及接口变更的任务必须包含回写 `docs/03`；涉及部署/环境变量必须回写 `docs/04`。
> - Mock 任务必须包含「登记到 `docs/06-Mock台账.md`」或「清理台账条目」。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

本项目为多端 Web 应用，使用以下真实路径：

- **后端**：`ruoyi/ruoyi-admin/src/main/java/com/ruoyi/web/controller/kcon/`、`ruoyi/ruoyi-system/src/main/java/com/ruoyi/system/{domain,service,service/impl,mapper}/`
- **管理端**：`ruoyi-ui/src/views/kcon/`、`ruoyi-ui/src/api/kcon/`
- **签到端 H5**：`checkin-h5/`
- **抽奖大屏**：`screen/`
- **数据库脚本**：`sql/kcon_lucky.sql`（新装基线）、`sql/kcon_migrate_v<N>.sql`（幂等迁移）
- **文档基线**：`docs/01-需求规格.md` … `docs/05-功能缺口.md`、`docs/06-Mock台账.md`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 [FR-x] [具体任务：在哪个模块/目录做什么]
- [ ] T002 [P] [FR-x] [具体任务]

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T003 [FR-x] 数据库变更：更新 `sql/kcon_lucky.sql` 并新增 `sql/kcon_migrate_v<N>.sql`（幂等），同时回写 `docs/02-数据库设计.md`
- [ ] T004 [P] [FR-x] 权限点与菜单初始化 SQL，并在 `docs/` 中登记权限点
- [ ] T005 [FR-x] 后端 SERVICE/Mapper 基础设施

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - [Title] (Priority: P1) 🎯 MVP

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Implementation for User Story 1

- [ ] T006 [P] [US1] [FR-x] 后端：`ruoyi/ruoyi-system/src/main/java/com/ruoyi/system/domain/…`（实体）
- [ ] T007 [P] [US1] [FR-x] 后端：`ruoyi/ruoyi-system/src/main/java/com/ruoyi/system/mapper/…`（Mapper + XML）
- [ ] T008 [US1] [FR-x] 后端：`ruoyi/ruoyi-system/src/main/java/com/ruoyi/system/service/impl/…`（依赖 T006、T007）
- [ ] T009 [US1] [FR-x] 接口：`ruoyi/ruoyi-admin/src/main/java/com/ruoyi/web/controller/kcon/…`，并回写 `docs/03-接口设计.md`
- [ ] T010 [P] [US1] [FR-x] 前端：`ruoyi-ui/src/api/kcon/…` + `ruoyi-ui/src/views/kcon/…`
- [ ] T011 [US1] [FR-x] Mock 处理：登记到 `docs/06-Mock台账.md` 或删除代码中 `MOCK:` 标记与台账条目

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Implementation for User Story 2

- [ ] T012 [P] [US2] [FR-x] …
- [ ] T013 [US2] [FR-x] …

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

[Add more user story phases as needed, following the same pattern]

---

## Phase N: 回写与验收（强制收尾）

**Purpose**: 满足章程「文档契约」与「代码与文档并行」要求

- [ ] TXXX [P] 回写 `docs/01-需求规格.md`（新增 `FR-NEW-*`）
- [ ] TXXX [P] 回写 `docs/02-数据库设计.md`（库表与字段变更）
- [ ] TXXX [P] 回写 `docs/03-接口设计.md`（新增/变更接口）
- [ ] TXXX [P] 回写 `docs/04-部署运行.md`（部署与环境变量变更）
- [ ] TXXX [P] 回写 `docs/05-功能缺口.md`（本 feature 的实测记录）
- [ ] TXXX [P] 清理 `docs/06-Mock台账.md` 中已被真实实现替换的条目
- [ ] TXXX 对照 `docs/01-需求规格.md` 的 AC 逐条打勾，形成验收单
- [ ] TXXX 按 Conventional Commits 提交到 `main`，只 commit 不 push

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
- **回写与验收 (Final Phase)**: Depends on all desired user stories being complete

### Within Each User Story

- 数据库变更 → 实体 → Mapper → Service → Controller → 前端
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- 后端与前端任务通常可 [P] 并行
- 不同 user story 可由不同人并行推进

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 每条任务都应能追溯到 spec.md 的 `FR-x`
- Commit after each task or logical group
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
