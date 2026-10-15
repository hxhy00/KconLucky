# Spec Kit ↔ 本项目规约体系 适配说明

本文件说明 Spec Kit（`.specify/` + `specs/` + `.codebuddy/commands/`）如何与本项目既有的
`docs/01~05` 规约体系协同工作。**冲突的裁决顺序以 `.specify/memory/constitution.md` 为准。**

---

## 一、两套体系的角色分工（不重叠、不互斥）

| 体系 | 位置 | 回答什么问题 | 变化频率 |
|---|---|---|---|
| 项目规约（长期基线） | `docs/01~05` | 系统**整体**长什么样：需求基线、库表、接口、部署、缺口 | 低（人工维护） |
| Spec Kit（单 feature） | `specs/<NNN>-<slug>/` | **这一次**要做什么：验收场景、技术方案、任务拆解 | 高（每 feature 一套） |

关键判断：`docs/` 是**全局且唯一的基线**，`specs/` 是**局部且派生的细化**。
禁止在 `specs/` 里重新定义与 `docs/` 冲突的口径。

## 二、编号映射（最重要的约定）

`docs/01-需求规格.md` 使用 `FR-1`、`FR-13.3`、`AC-11`、`P5` 形式编号。

- `spec.md` 的功能需求**必须沿用 `docs/01` 的 FR 编号**，不要另起 `FR-001` 序列。
- 新增需求（`docs/01` 中不存在）：在 spec 中编号为 `FR-NEW-<序号>`，并在该行标注
  `<!-- 新增需求，待回写 docs/01-需求规格.md -->`，实现完成后必须回写。
- `plan.md` 的 Phase 与 `tasks.md` 的任务描述中，凡涉及具体能力，附上 `FR-x` 引用，
  便于 `/speckit.analyze` 的覆盖度检查与 `docs/01` 双向对照。

## 三、产物归属映射（避免重复维护）

| `specs/` 产物 | 与 `docs/` 的关系 |
|---|---|
| `spec.md` | 需求的**局部视图**，来源是 `docs/01`；只写本 feature 涉及的部分 |
| `plan.md` → `data-model.md` | 库表增量设计；**基线仍是 `docs/02-数据库设计.md`**，定稿后回写 |
| `plan.md` → `contracts/` | 接口契约增量；**基线仍是 `docs/03-接口设计.md`**，定稿后回写 |
| `plan.md` → `quickstart.md` | 本 feature 的验证步骤；**部署全局说明仍在 `docs/04`** |
| `tasks.md` | 本 feature 的任务清单；**已修复缺口的记录仍归 `docs/05-功能缺口.md`** |
| `research.md` | 技术选型与踩坑记录，属 feature 级，不回写 |

**回写规则**：凡是改变了 `docs/` 中已存在的语义（字段、接口、口径、FR 定义、部署步骤），
在 `/speckit.implement` 完成时必须在同一次任务内回写对应 `docs/` 文件。

## 四、Mock 归口

`docs/06-Mock台账.md` 是**唯一**的 mock 登记处，`specs/` 内不另开 mock 清单。
spec/plan/tasks 中出现的 mock，写成「见 `docs/06-Mock台账.md` 第 N 条」并同步登记。

## 五、已知的机制差异与处理方式

1. **spec 目录名固定 `specs/<NNN>-<slug>/`**
   Spec Kit 的脚本（`.specify/scripts/bash/create-new-feature.sh`）硬编码 `SPECS_DIR="$REPO_ROOT/specs"`，
   无法通过配置改成 `docs/`。**处理方式：接受这个差异**，`docs/` 保持为长期基线，
   feature 产物放 `specs/`；两者通过第二节的编号映射建立联系。

2. **`.specify/feature.json` 是机器本地状态**
   它记录「当前 feature 目录」，每次切换 feature 都会重写，因此已在 `.specify/.gitignore` 中排除，
   不进版本库。多人协作时各自 `cd` 到项目根跑 `/speckit.*` 即可自动定位。

3. **git 分支与 feature 目录解耦（本项目有意为之）**
   本项目**未**安装 `git` 扩展，所以 `/speckit.specify` 不会自动创建分支，
   也不会依赖分支名去定位 spec 目录。本项目实际为 **`main` 单分支流**（见章程第六节），
   不按分支区分 feature。
   这样做的理由：Spec Kit 的 `NNN-<slug>` 目录命名已能唯一标识 feature，
   再为每个 feature 建分支会产生大量无意义分支。

4. **本项目项目规约目录是 `docs/` 而不是 `specs/`**
   本项目的 `specs/` 在初始化 Spec Kit 之前不存在，因此不存在命名冲突。
   若将来需要，`docs/` 与 `specs/` 的职责边界按第一节执行。

## 六、日常使用流程（八阶段流程的 Spec Kit 落地）

对应 `产品开发流程` 规则中的八个阶段：

| 阶段 | 落地方式 |
|---|---|
| 1 问题定义 · 2 概念模型 | 人工，写入 `docs/01`；必要时用 `/speckit.clarify` 追问 |
| 3 用户旅程 | `/speckit.specify` → `spec.md` 的 User Scenarios（带 P1/P2/P3 优先级） |
| 4 需求规格 | `spec.md` 的 Functional Requirements（沿用 `docs/01` 的 FR 编号）+ Success Criteria + Out of Scope |
| 5 交互设计 | `plan.md` 的 Phase 1 产物（`contracts/` 中的 UI 契约） |
| 6 架构设计 | `/speckit.plan` → `plan.md` + `research.md` + `data-model.md`；选型需先与用户确认 |
| 7 实现 | `/speckit.tasks` → `/speckit.implement`（`/speckit.analyze` 做一致性检查） |
| 8 验收 | 对照 `docs/01` 的 AC 逐条打勾；本 feature 的复盘结论写回 `docs/05-功能缺口.md` |

## 七、命令速查

| 命令 | 用途 | 何时用 |
|---|---|---|
| `/speckit.constitution` | 修订项目章程 | 原则变化时 |
| `/speckit.specify` | 生成 `specs/<NNN>-<slug>/spec.md` | 新 feature 开工 |
| `/speckit.clarify` | 追问规格歧义，答案写回 spec | plan 之前，可选 |
| `/speckit.plan` | 生成 `plan.md` + `research.md` + `data-model.md` + `contracts/` + `quickstart.md` | spec 定稿后 |
| `/speckit.tasks` | 生成 `tasks.md` | plan 之后 |
| `/speckit.analyze` | 跨产物一致性检查（只读） | tasks 之后、implement 之前 |
| `/speckit.checklist` | 生成需求质量检查清单 | 可选 |
| `/speckit.implement` | 按 tasks.md 执行实现 | 检查通过后 |
| `/speckit.converge` | 评估代码与规格的差距，补任务 | implement 之后 |

**优先级提示**：`docs/01-需求规格.md` 是唯一需求基线；`specs/` 中的 `spec.md` 必须与它一致，
冲突时改 `spec.md`，不改 `docs/01`（除非需求本身确实变更，那属于需求变更流程）。
