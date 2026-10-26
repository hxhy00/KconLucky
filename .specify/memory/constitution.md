# KConLucky 项目章程（Constitution）

**版本**：1.2.0
**批准日期**：2026-10-15
**最近修订**：2026-10-21（第六条收紧为标准 Git Flow：明确 `main` 合入唯一来源为 `release/*` 与 `hotfix/*`，`develop` / `feature` 不得直合 `main`；补充 `release` 冻结期只收 bugfix、`hotfix` 双向合回、`main` 分支保护必配；细则迁至 `CONTRIBUTING.md`）

> 本章程是 Spec Kit 工作流的最高约束。`/speckit.plan` 的 Constitution Check 按此逐条判定，
> 与章程 MUST 条款冲突的方案一律判定为 CRITICAL，必须修改方案而非稀释章程。
> 章程与 `docs/` 既有规约冲突时，以本章程为准。

---

## 一、文档契约（Documentation Contract）

**唯一需求基线是 `docs/`，Spec Kit 产物是它的派生与细化视图。**

| 文档 | 角色 | 谁维护 |
|---|---|---|
| `docs/01-需求规格.md` | **唯一需求基线**，FR / AC 编号的权威来源 | 人 |
| `docs/02-数据库设计.md` | 库表结构、字段口径、迁移历史的权威来源 | 人 |
| `docs/03-接口设计.md` | HTTP 接口契约（路径 / 入参 / 出参）的权威来源 | 人 |
| `docs/04-部署运行.md` | 部署、运行、环境变量的权威来源 | 人 |
| `docs/05-功能缺口.md` | 已修复缺口与回归检查清单 | 人 |
| `specs/<NNN>-<slug>/` | 单个 feature 的规格、计划、任务，按 Spec Kit 流程生成 | 人 + AI |

**MUST**：

1. 新增 feature 必须先读相关 `docs/` 文件，不得凭记忆或推测编写规格。
2. `spec.md` 的**功能需求编号必须沿用 `docs/01-需求规格.md` 的 FR 编号**（如 `FR-11`），不得另起 `FR-001` 序列；仅当该需求在 `docs/01` 中不存在时才新建编号，并在 spec 中显式标注「新增需求，待回写 docs/01」。
3. 修改了 `docs/` 中已有语义（字段口径、接口契约、状态机、FR 定义）时，**必须在同一次任务内回写 `docs/` 对应文件**，不得只改 `specs/`。
4. `/speckit.implement` 完成后，对照 `docs/01-需求规格.md` 的 AC 逐条打勾；未通过的不算完成。
5. 「不做清单」（Out of Scope）必须显式写入 `spec.md`，不允许留空。

---

## 二、Mock 管理（Mock Registry）

**任何未经真实接口/数据验证的人造内容都算 mock，必须登记、必须标记、必须能清理。**

**MUST**：

1. 每一条 mock 在 `docs/07-Mock台账.md` 中登记，至少包含：mock 位置（文件 + 符号名）、mock 的内容形态、为什么先 mock、替换为真实实现的前置条件、责任人/关联需求。
2. 代码内使用统一前缀 `MOCK:` 注释，并指向台账条目编号。
3. 真实实现就绪后，**同步删除**台账条目与代码标记，不留「已废弃的 mock 说明」。
4. 禁止把 mock 冒充真实实现交付；禁止 mock 数据进入生产默认配置；禁止只写 `TODO` 而不写清前置条件与口径。
5. `/speckit.analyze` 发现未登记的 mock（代码含硬编码假数据、未验证字段枚举、占位 stub 且台账无记录）时，一律判定为 **HIGH**。

---

## 三、数据与迁移（Data & Migration）

**MUST**：

1. 所有 DDL 变更同时更新 `sql/kcon_lucky.sql`（新装基线）**并**新增幂等的 `sql/kcon_migrate_v<N>.sql`（已部署库）。二者缺一不可。
2. 迁移脚本必须幂等：可重复执行不报错、不产生重复数据。
3. 数据库结构变更必须同步更新 `docs/02-数据库设计.md`。
4. 删除已签到人员的名单记录、替换模式导入等破坏性操作，必须先校验关联签到/中奖记录并拒绝执行，不得静默成功。
5. 「当日」口径统一为 `checkin_date = curdate()`，不允许同一概念在不同 SQL 里各写一套。

---

## 四、控制权唯一（Single Source of Authority）

**MUST**：

1. 权限校验只在服务端做，前端隐藏菜单/按钮不算鉴权；管理端与签到端不得存在绕过鉴权的写操作后门。
2. 签到端（H5）只允许只读查询与「签到」这一个写操作；其余写操作仅管理端具备，且必须带权限点（`kcon:*:*`）。
3. 端口号分配必须在一个事务内完成唯一性校验与落库，禁止「先查后插」两步分离。
4. 抽奖池在点击抽奖按钮的瞬间锁定；已中奖号码自动移出，不可重复中奖。

---

## 五、依赖与工具链

**MUST**：

1. 包管理器一律优先国内镜像，禁止默认走境外源；国内源失败必须先报告具体错误再考虑切换，不允许静默换源。
2. Python 依赖统一用 `uv` 管理，虚拟环境固定在项目根 `.venv`，锁定文件 `uv.lock` 必须提交；执行脚本用 `uv run`。
3. Node 依赖遵循 `package.json` 的 `engines` 与 `.nvmrc`；不擅自升级 lockfile 中的无关依赖。
4. **未经许可不得创建软链接**（`ln -s` / junction / 硬链接）。若方案只能靠软链接实现，先停下说明并请求许可。
5. 不新增冗余文档：已有文件能承载的内容不新开文件；方案变更时替换而非堆叠版本（历史由 git 承担）。

---

## 六、Git 与提交

**MUST**：

1. 本项目采用**标准 Git Flow**，不因项目规模降级为 GitHub Flow 或简化分支：
   - `main`：只存放已发布版本，**禁止直接 commit、禁止直接 push**。合入 `main` 的**唯一来源**是 `release/*` 与 `hotfix/*`；`develop` 与 `feature/*` **均不得直接合入 `main`**。
   - `develop`：日常集成分支，由 `main` 的初始 commit 切出，长期存在，不得删除。
   - `feature/<seq>-<kebab>`：从 `develop` 切出，完成后以 `--no-ff` 合回 `develop`，完成后删除。
   - `release/<版本>`：从 `develop` 切出并携带版本号。冻结期内**只接受 bugfix，不接受新功能**；修复必须同时合回 `develop`；合入 `main` 时打 tag，发版后删除。
   - `hotfix/<seq>-<kebab>`：从 `main` 切出，**双向**合回 `main` 与 `develop`，并打 tag。
   - **`main` 分支保护必配**：仓库平台须开启禁止直接 push、禁止直接 commit、限定合并来源为 `release/*` 与 `hotfix/*`。规则文本与平台配置**二者缺一不可**。
   - **分支命名**遵循 `.specify/branch-convention.yml`：`{type}/{seq}-{kebab}`，其中 `{type}` 取 `feature`/`bugfix`/`hotfix`/`refactor`/`docs`；`main` 与 `develop` 是基线分支，不参与命名校验。
     例：`feature/007-public-net-hardening`。
   - `specs/` 目录保持**扁平** `{seq}-{kebab}`（如 `specs/001-remove-ruoyi-branding/`），**不随分支加前缀**；分支与目录的 `{seq}-{kebab}` 部分必须同源。
   - 完整约束、分支保护检查清单与发布 / 热修命令见 `CONTRIBUTING.md`。
2. Commit message **遵循 Conventional Commits 格式**（`<type>(<scope>): <subject>`）。
   注意：项目当前**没有** commit 钩子或 lint 工具强制校验，属团队约定而非工具强制，因此提交前须自行核对格式。
3. 可自行 `commit`，**不准直接 `push`**。推送按 **spec 粒度**：一个 spec 完成（代码 + 文档 + 验证通过）后合并到 `develop`，由 agent 说明分支与范围并给出命令，由用户手动执行推送。
4. 禁止 `git commit --amend`、`git reset --hard`、`git push --force`，除非用户明确要求。

---

## 七、语言与表达

**MUST**：

1. 与人交流、写文档一律用简体中文；代码、变量名、函数名、commit message 保持英文。
2. 首次出现英文专业术语时给出中文解释，之后可沿用英文原词；术语在全项目内保持统一。
3. 代码注释不得留下已过时的描述；代码改完必须检查 `README`、`docs/`、注释是否需要同步更新，需要更新的在同一次任务内改完。

---

## 治理（Governance）

- **效力**：本章程约束所有 Spec Kit 工作流产物（`spec.md`、`plan.md`、`tasks.md`、实现代码）。`/speckit.analyze` 与 `/speckit.converge` 检测到 MUST 条款被违反时，一律判定为 CRITICAL。
- **修订流程**：修订通过 `/speckit.constitution` 提出；修改必须在本文件内留下版本号与日期变更，并说明理由。
- **版本规则**：MAJOR = 删除或重定义原则；MINOR = 新增原则或实质扩展；PATCH = 措辞澄清。语义化版本 `MAJOR.MINOR.PATCH`。
- **合规审查**：`/speckit.plan` 阶段的 Constitution Check 是强制关卡，未通过不得进入实现；`/speckit.implement` 完成后由 `/speckit.analyze` 复核。
- **冲突裁决**：章程 > `docs/` 现有规约 > `specs/` 内约定 > 代码现状。
