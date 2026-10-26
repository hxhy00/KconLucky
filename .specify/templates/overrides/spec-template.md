# Feature Specification: [FEATURE NAME]

**Feature Directory**: `specs/[###-feature-name]`

**Created**: [DATE]

**Status**: Draft

**Input**: User description: "$ARGUMENTS"

> **本项目适配说明（不要删除本段）**
> - 需求编号**必须沿用** `docs/01-需求规格.md` 的 `FR-x` / `AC-x` 编号，不要另起 `FR-001` 序列。
>   若该需求在 `docs/01` 中不存在，编号为 `FR-NEW-<序号>` 并保留 `<!-- 新增需求，待回写 docs/01 -->` 注记。
> - `docs/` 是唯一需求基线，本文件是它的**局部视图**，只写本 feature 涉及的部分。
> - 定稿前先读：`docs/01-需求规格.md`（必读）、`docs/02-数据库设计.md`、`docs/03-接口设计.md`（按需）。

## 背景与问题（必填）

**业务背景**：[谁、在什么场景下、遇到了什么问题？现在是怎么凑合的？来源：`docs/01` §…]

**不做的部分（Out of Scope）**：[本 feature 明确不覆盖的内容；不允许留空，写「无」也要写]

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.

  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - [Brief Title] (Priority: P1)

[Describe this user journey in plain language]

**对应需求**：[FR-x / FR-x.y，来自 `docs/01-需求规格.md`]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently - e.g., "Can be fully tested by [specific action] and delivers [specific value]"]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]
2. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

### User Story 2 - [Brief Title] (Priority: P2)

[Describe this user journey in plain language]

**对应需求**：[FR-x]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

### User Story 3 - [Brief Title] (Priority: P3)

[Describe this user journey in plain language]

**对应需求**：[FR-x]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

[Add more user stories as needed, each with an assigned priority]

### Edge Cases

- What happens when [boundary condition]?
- How does system handle [error scenario]?

## Requirements *(mandatory)*

### Functional Requirements

<!--
  编号规则：沿用 docs/01-需求规格.md 的 FR 编号（如 FR-11、FR-13.3）。
  新需求用 FR-NEW-1、FR-NEW-2 并标注「待回写 docs/01」。
-->

- **FR-x**: System MUST [specific capability]
- **FR-y**: System MUST [specific capability]
- **FR-NEW-1**: System MUST [specific capability] <!-- 新增需求，待回写 docs/01-需求规格.md -->

*标记不明确的需求：*

- **FR-NEW-2**: System MUST authenticate users via [NEEDS CLARIFICATION: auth method not specified - email/password, SSO, OAuth?]

### Mock 清单

<!--
  本项目所有 mock 统一登记在 docs/07-Mock台账.md，此处只做引用。
  无 mock 时写「无」。
-->

- 无 / 见 `docs/07-Mock台账.md` 第 N 条：[一句话说明]

### Key Entities *(include if feature involves data)*

<!--
  基线是 docs/02-数据库设计.md。此处只写本 feature 的增量或变更，
  并明确标注「新增表/新增字段/口径变更」，定稿后回写 docs/02。
-->

- **[Entity 1]**: [What it represents, key attributes without implementation; 标注 新增/变更]
- **[Entity 2]**: [What it represents, relationships to other entities]

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-x**: [Measurable metric]

> 若 `docs/01-需求规格.md` 已有对应 `AC-x`，请直接沿用该编号并引用原文。

## Assumptions

- [Assumption about target users]
- [Assumption about scope boundaries]
- [Dependency on existing system/service]

## 回写清单（实现完成后逐条确认）

<!-- /speckit.implement 结束前必须处理完本节 -->

- [ ] 新增 `FR-NEW-*` 已回写 `docs/01-需求规格.md`
- [ ] 库表变更已回写 `docs/02-数据库设计.md`，并产出幂等迁移脚本
- [ ] 接口变更已回写 `docs/03-接口设计.md`
- [ ] 部署/环境变量变更已回写 `docs/04-部署运行.md`
- [ ] Mock 条目已登记或已清理 `docs/07-Mock台账.md`
