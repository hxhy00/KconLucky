# Spec Kit 在本项目中的使用说明

本目录是 [GitHub Spec Kit](https://github.com/github/spec-kit)（spec-driven development 工具链）
在本项目中的**项目级配置**。本文件说明三件事：它是什么、与 `docs/` 如何分工、以及如何在新项目里复用。

---

## 一、工具链位置（全局装，与项目无关）

`specify` CLI 通过 `uv tool` **全局安装**，任何目录都能调用，不需要每个项目单独装：

```bash
which specify        # → ~/.local/bin/specify
specify --version    # → specify 1.2.0
```

升级 CLI（只影响 CLI 本身，不会动项目内已生成的资产）：

```bash
specify self check
specify self upgrade
```

> **重要**：CLI 是全局的，但 `.specify/`（本目录）和 `.codebuddy/commands/` 是**每个项目各一份**。
> 这是 Spec Kit 的设计——它靠向上查找 `.specify/` 来判定"当前是不是一个 Spec Kit 项目"，
> 缺了它所有命令都会报 `Not a Spec Kit project`。因此不存在"全局 `.specify/`"这种用法。

---

## 二、与 `docs/` 的分工（核心适配点）

本项目**唯一的需求基线是 `docs/`**，Spec Kit 产物是它的派生视图。两者的职责边界：

| 路径 | 角色 | 权威性 |
|---|---|---|
| `docs/01-需求规格.md` | 需求基线，`FR-x` / `AC-x` 编号的权威来源 | 唯一基线 |
| `docs/02-数据库设计.md` | 库表结构、字段口径、迁移历史 | 权威 |
| `docs/03-接口设计.md` | HTTP 接口契约（路径 / 入参 / 出参） | 权威 |
| `docs/04-部署运行.md` | 部署、运行、环境变量 | 权威 |
| `docs/05-功能缺口.md` | 已修复缺口与回归检查清单 | 权威 |
| `docs/06-Mock台账.md` | Mock 登记表 | 权威 |
| `specs/<NNN>-<slug>/` | **单个 feature** 的 spec / plan / tasks | 派生，按需回写 `docs/` |

**冲突裁决顺序**：`.specify/memory/constitution.md`（章程） > `docs/` 现有规约 > `specs/` 内约定 > 代码现状。

### 适配是怎么实现的

Spec Kit 的模板解析优先级（`resolve_template`）是：

```
1. .specify/templates/overrides/          ← 本项目在此注入适配
2. .specify/presets/<id>/templates/
3. .specify/extensions/<id>/templates/
4. .specify/templates/                    ← core 默认模板
```

本项目在**第 1 级**放置了三个 overrides，让 Spec Kit 生成的产物自动对齐 `docs/` 规约：

| Override 文件 | 作用 |
|---|---|
| `templates/overrides/spec-template.md` | 要求 `spec.md` 沿用 `docs/01` 的 `FR-x` 编号，不另起 `FR-001` 序列；强制填写 Out of Scope |
| `templates/overrides/plan-template.md` | 锁定既定技术栈；Constitution Check 逐条对齐章程七大章节；要求回写 `docs/02` / `docs/03` |
| `templates/overrides/tasks-template.md` | 任务粒度与回写 `docs/` 的检查项对齐 |

> 这三个文件与 `.specify/memory/constitution.md` 是本项目对 Spec Kit 的**全部定制**，改动它们即可调整工作流。

---

## 三、版本控制策略

**`.specify/` 入版本库**（团队共享），机器本地状态由 `.specify/.gitignore` 自动排除：

| 入库（团队共享） | 忽略（机器本地） |
|---|---|
| `memory/constitution.md`（章程） | `feature.json`（当前 feature 指针，每次切换 feature 都重写） |
| `templates/`（含 `overrides/`） | `integrations/packages/`（可执行适配器包） |
| `scripts/bash/` | `extensions/*/local-config.yml` |
| `workflows/`、`integration*.json`、`init-options.json` | — |

**`.codebuddy/` 不入版本库**（已在根 `.gitignore` 忽略）。
spec-kit 生成的 slash 命令落在 `.codebuddy/commands/`，属于本机 IDE 数据，团队成员各自生成即可（见下）。

---

## 四、团队协作：克隆后怎么用

新克隆仓库的成员只需两步（`.codebuddy/commands/` 不会随仓库带过来）：

```bash
cd <项目根>
export PATH="$HOME/.local/bin:$PATH"
specify init --here --integration codebuddy    # 就地生成 .codebuddy/commands/，已存在的 .specify/ 不受影响
```

随后在 CodeBuddy 中即可使用 slash 命令：

| 命令 | 作用 |
|---|---|
| `/speckit.constitution` | 查看 / 修订项目章程 |
| `/speckit.specify` | 从自然语言描述创建 `specs/<NNN>-<slug>/spec.md` |
| `/speckit.plan` | 生成 `plan.md` 及设计产物（含 Constitution Check 关卡） |
| `/speckit.tasks` | 生成 `tasks.md` |
| `/speckit.implement` | 执行任务 |
| `/speckit.analyze` | 跨产物一致性检查（章程 MUST 违反判 CRITICAL） |
| `/speckit.clarify` / `/speckit.checklist` | 可选：澄清需求 / 生成检查清单 |

---

## 五、在新项目里复用本项目的适配

若新项目也要同一套"`docs/` 为基线 + 章程约束"的工作流，按下面顺序做：

```bash
# 1. 初始化（生成 .specify/ 骨架与本机 .codebuddy/commands/）
cd <新项目根>
specify init --here --integration codebuddy

# 2. 用官方 preset 机制复用本项目的定制模板与章程
#    以本项目作为 preset 源目录（--dev 从本地目录安装，属官方支持）
specify preset add --dev <本项目根>/.specify-preset
```

`--dev` 指向的目录需是标准 preset 结构（`preset.yml` + `templates/`）。
本项目已将其打包在 `.specify-preset/`，可直接复用；自定义调整只改那里，不动 `.specify/templates/overrides/`。

> 未使用软链接，也无需修改 CLI 全局状态；`--dev` 与 `preset add` 均为 `specify` 官方命令。
