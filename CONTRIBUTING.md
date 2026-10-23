# 贡献指南

本项目采用**标准 Git Flow**，不因项目规模降级为 GitHub Flow 或简化分支模型。
本文约束**分支与提交**；分支命名细则见 `.specify/branch-convention.yml`，章程见 `.specify/memory/constitution.md`。

---

## 1. 分支模型

| 分支 | 职责 | 从哪切出 | 合入谁 | 生命周期 |
|---|---|---|---|---|
| `main` | 只存放**可发布版本**，每个提交都应可上线 | — | — | 永久存在 |
| `develop` | 集成分支，日常开发成果汇入此处 | `main` 的初始 commit | — | 永久存在 |
| `feature/<seq>-<主题>` | 功能 / 缺陷 / 重构 / 文档开发 | `develop` | `develop` | 完成后删除 |
| `release/<版本>` | 发布冻结与回归 | `develop` | `main` + `develop` | 发版后删除 |
| `hotfix/<seq>-<主题>` | 线上紧急修复 | `main` | `main` + `develop` | 修复后删除 |

### 1.1 硬性约束

1. `main` **禁止直接 commit、禁止直接 push**。
2. 合入 `main` 的**唯一来源**是 `release/*` 与 `hotfix/*`。
   `develop` 与 `feature/*` **均不得直接合入 `main`**——即使只改一行文档。
3. 所有 `feature/*` 必须从 `develop` 切出，完成后以 `--no-ff` 合回 `develop`，保留特性边界。
4. `release/*` 冻结期内**只接受 bugfix，不接受新功能**。在此修的每个 bug 必须同时合回 `develop`，否则下个版本缺陷复活。
5. `hotfix/*` 修复完成后必须**双向合回** `main` 与 `develop`，漏掉 `develop` 会造成「修复在下一个版本丢失」。
6. 新仓库以 `main` 的初始 commit 为起点切出 `develop`，此后 `develop` 长期存在，不得删除。

### 1.2 完整流向

```
feature/a ──┐
feature/b ──┴──► develop ──► release/1.1 ──► main ──(tag v1.1.0)──► 上线
                   ▲            │
                   └── bugfix 回合 ┘

main ──► hotfix/紧急修复 ──┬──► main ──(tag v1.1.1)──► 上线
                          └──► develop
```

---

## 2. 分支保护（仓库平台必配）

规则文本与平台配置**二者缺一不可**。仅靠文档约定不算数，必须在 Git 平台（Gitee / GitHub / GitLab）开启 `main` 保护：

| 保护项 | 要求 |
|---|---|
| 禁止直接 push | 开启，包括管理员 |
| 禁止直接 commit / force push | 开启 |
| 合并来源限制 | 仅允许 `release/*` 与 `hotfix/*` |
| 合并方式 | 保留合并提交（`--no-ff`），保留发布点 |
| 删除与强推 | 禁止删除 `main`、`develop`；禁止 force push |

`develop` 建议同样开启「禁止 force push」，但不限制直接提交（集成分支允许小修直接落）。

> 配置后自检：在本地直接 `git push origin main` 应被拒绝；若成功推送，说明保护未生效。

---

## 3. 发布流程

### 3.1 常规发布

```bash
# 1. 从 develop 切 release 分支
git checkout develop && git pull
git checkout -b release/1.1.0

# 2. 冻结回归，只修 bug（修完需回合 develop）
git commit -am "fix(release): ..."

# 3. 合入 main 并打 tag
git checkout main
git merge --no-ff release/1.1.0
git tag -a v1.1.0 -m "v1.1.0"

# 4. 回合 develop
git checkout develop
git merge --no-ff release/1.1.0

# 5. 推送（由用户手动执行）
git push origin main develop --tags

# 6. 删除 release 分支
git branch -d release/1.1.0
```

### 3.2 线上紧急修复

```bash
git checkout main && git pull
git checkout -b hotfix/020-fix-xxx

# 修复并提交
git commit -am "fix(xxx): ..."

# 双向合回
git checkout main && git merge --no-ff hotfix/020-fix-xxx
git tag -a v1.1.1 -m "v1.1.1"
git checkout develop && git merge --no-ff hotfix/020-fix-xxx

git push origin main develop --tags   # 手动执行
git branch -d hotfix/020-fix-xxx
```

> 2026-10-22 起已停用 CD：推送 tag 不再触发自动部署，发布按 `docs/04-部署运行.md` 第 7.4 节手动执行。

---

## 4. 提交规范

遵循 Conventional Commits：`<type>(<scope>): <subject>`

| type | 用途 |
|---|---|
| `feat` | 新功能 |
| `fix` | 缺陷修复 |
| `docs` | 文档 |
| `refactor` | 重构（不改行为） |
| `chore` | 构建、依赖、脚本 |
| `test` | 测试 |
| `release` | 发布相关提交 |

- `subject` 用英文；说明正文可中文。
- 禁止 `--amend`、`reset --hard`、`push --force`，除非明确要求。
- 推送按 **spec 粒度**：一个 spec（`specs/{seq}-{主题}/`）完成、验证通过后再合并推送。

---

## 5. 开发流程

1. 需求落 `docs/01-需求规格.md`，接口契约落 `docs/03-接口设计.md`；有规格需求时先建 `specs/{seq}-{主题}/`。
2. 从 `develop` 切出 `feature/{seq}-{主题}`，分支与 `specs/` 目录的 `{seq}-{主题}` **必须同源**。
3. 本地构建与自动化测试通过后提交（`cd ruoyi && mvn -B test`、`ruoyi-ui` / `checkin-h5` 的 `npm test`），`--no-ff` 合回 `develop`。
4. 涉 DDL 的改动，同时更新新装基线脚本与幂等迁移脚本（`sql/kcon_migrate_v*.sql`），否则新装环境与现有环境行为不一致。
5. 发布走 `release/*`，见第 3 节。

---

## 6. 禁止事项

- 直接向 `main` 推送或提交
- 从 `feature/*` 直接合入 `main`
- `release/*` 中夹带新功能
- `hotfix/*` 只合 `main` 不回 `develop`
- 未经允许 `--amend`、`reset --hard`、`push --force`
- 把密钥、令牌、真实个人信息写进代码、文档或日志

---

## 7. 相关文档

| 文档 | 内容 |
|---|---|
| `README.md` | 项目概览、快速启动 |
| `.specify/branch-convention.yml` | 分支命名规约（类型前缀、序号、长度） |
| `.specify/memory/constitution.md` | 项目章程 |
| `docs/01-需求规格.md` | 需求与验收标准 |
| `docs/03-接口设计.md` | 接口契约 |
| `docs/04-部署运行.md` | 部署、发布、故障排查 |
