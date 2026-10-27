# 贡献指南

本项目采用**轻量分支模型**：以 `main` 为唯一基线，`develop` 已废弃（2026-10-26 删除），不再遵循严格 Git Flow。
本文约束**分支与提交**；分支命名细则见 `.specify/branch-convention.yml`，章程见 `.specify/memory/constitution.md`。

---

## 1. 分支模型

| 分支 | 职责 | 从哪切出 | 合入谁 | 生命周期 |
|---|---|---|---|---|
| `main` | 可上线的唯一基线 | — | — | 永久存在 |
| `feature/<seq>-<主题>` | 功能 / 重构 / 文档开发 | `main` | `main` | 完成后删除 |
| `hotfix/<seq>-<主题>` | 线上修复 | `main` | `main` | 修复后删除 |
| `bugfix/<seq>-<主题>` | 测试问题修复（可与 hotfix 混用） | `main` | `main` | 修复后删除 |

### 1.1 硬性约束

1. `main` **禁止直接 commit、禁止直接 push**（平台保护开启）。
2. 所有工作分支从 `main` 切出，完成后合回 `main` 并删除。
3. 功能边界保留：合回用 `--no-ff`，保留合并提交。
4. 不设 `develop` 集成分支、不设 `release/*` 冻结分支；发布直接以 `main` 为准打 tag（见第 3 节）。

### 1.2 完整流向

```
main ──► feature/020-xxx ──(完成, --no-ff)──► main ──(tag v1.1.0)──► 上线
     └─► hotfix/021-fix-xxx ──(修复, --no-ff)──► main ──(tag v1.1.1)──► 上线
```

---

## 2. 分支保护（仓库平台必配）

规则文本与平台配置**二者缺一不可**。必须在 Git 平台（Gitee / GitHub / GitLab）开启 `main` 保护：

| 保护项 | 要求 |
|---|---|
| 禁止直接 push | 开启，包括管理员 |
| 禁止直接 commit / force push | 开启 |
| 删除与强推 | 禁止删除 `main`；禁止 force push |

> 配置后自检：在本地直接 `git push origin main` 应被拒绝；若成功推送，说明保护未生效。

---

## 3. 发布流程

```bash
# 1. 从 main 切工作分支
git checkout main && git pull
git checkout -b hotfix/021-fix-xxx

# 2. 修复并提交
git commit -am "fix(xxx): ..."

# 3. 本地构建与自动化测试通过后合回 main
cd ruoyi && mvn -B test && cd ../ruoyi-ui && npm test
git checkout main
git merge --no-ff hotfix/021-fix-xxx
git tag -a v1.1.1 -m "v1.1.1"

# 4. 推送（由用户手动执行）
git push origin main --tags

# 5. 删除分支
git branch -d hotfix/021-fix-xxx
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

- `subject` 用英文；说明正文可中文。
- 禁止 `--amend`、`reset --hard`、`push --force`，除非明确要求。
- 推送按 **spec 粒度**：一个 spec（`specs/{seq}-{主题}/`）完成、验证通过后再合并推送。

---

## 5. 开发流程

1. 需求落 `docs/01-需求规格.md`，接口契约落 `docs/03-接口设计.md`；有规格需求时先建 `specs/{seq}-{主题}/`。
2. 从 `main` 切出工作分支，分支与 `specs/` 目录的 `{seq}-{主题}` **必须同源**。
3. 本地构建与自动化测试通过后提交（`cd ruoyi && mvn -B test`、`ruoyi-ui` / `checkin-h5` 的 `npm test`），`--no-ff` 合回 `main`。
4. 涉 DDL 的改动，同时更新新装基线脚本与幂等迁移脚本（`sql/kcon_migrate_v*.sql`），否则新装环境与现有环境行为不一致。

---

## 6. 禁止事项

- 直接向 `main` 推送或提交
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
