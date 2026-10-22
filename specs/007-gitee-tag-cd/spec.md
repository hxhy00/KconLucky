# Feature Specification: Gitee Tag 自动部署（CD）

> **已废弃（2026-10-22）**：本特性已从仓库移除。Gitee 的 Tag Push WebHook 已撤销，
> `deploy/`、`scripts/deploy.sh`、nginx 的 `/hooks/deploy` 反代与 compose 的
> `extra_hosts` 均已删除，发布改为手动执行（见 `docs/04-部署运行.md` 第 7.4 节）。
> 本文档仅作历史归档，不再代表当前实现。

**Feature Branch**: `docs/007-gitee-tag-cd`（实现分支 `feature/011-tag-cd`）

**Created**: 2026-10-19

**Status**: 已废弃（原状态：已实现；2026-10-22 停用 CD 并移除代码）

**Input**: 用户要求：本地打 tag 并 push 后，服务器能够及时响应并更新。方案选定为 Gitee WebHook + 服务器脚本，只认 `v*`，自动执行幂等数据库迁移。

## 背景与目标

当前发布依赖人工 SSH 到服务器执行 `git pull` + `docker compose up -d --build`。目标是：

- 正常发布不需要登录服务器；
- 只对 `v*` 发布 tag 生效，分支推送不触发；
- 部署失败时自动回到上一版本；
- 数据库迁移在部署前自动执行，且可重复执行。

## User Scenarios & Testing

### User Story 1 - 推送 v* tag 自动发布（Priority: P1）

运维在 `main` 上打完发布 tag 并 push，Gitee 触发 WebHook，服务器自动检出该 tag、执行迁移、重建并启动容器。

**Why this priority**: 这是 CD 的核心价值，替代手工 SSH 发布。

**Independent Test**: 推送一个测试 tag，不登录服务器，四个入口恢复 200，部署日志出现 `deploy success: <tag>`。

**Acceptance Scenarios**:

1. **Given** 服务器已安装接收器且 Gitee WebHook 已配置，**When** push `refs/tags/v1.0.0`，**Then** 服务器检出 `v1.0.0` 并完成 `docker compose up -d --build`。
2. **Given** push 的是 `refs/heads/develop`，**When** WebHook 到达，**Then** 部署脚本记录 `skip: not a v* tag ref` 并退出 0，不改变运行版本。

---

### User Story 2 - 部署失败自动回滚（Priority: P2）

构建失败或健康检查不通过时，脚本自动检出上一个 tag/提交并重建，保证旧版本继续服务。

**Why this priority**: 现场活动不能因为一次坏发布长时间不可用。

**Independent Test**: 人为让健康检查失败，确认脚本回到 `previous` 并重建，四个入口恢复 200。

**Acceptance Scenarios**:

1. **Given** 当前运行 `v1.0.0`，**When** 部署 `v1.0.1` 时 `docker compose up` 失败，**Then** 脚本检出 `v1.0.0` 并重新构建。
2. **Given** 新版本容器已启动但 `/admin/` 返回非 200，**When** 健康检查超时，**Then** 脚本回滚并记录失败原因。

---

### User Story 3 - 运维可观测（Priority: P3）

运维可以查看 WebHook 服务状态、部署日志和容器状态，必要时手动回滚。

**Why this priority**: 自动部署必须可追踪，否则故障时无法定位。

**Independent Test**: `journalctl -u kcon-webhook` 能看到请求，`/var/log/kcon-deploy.log` 能看到每次部署的开始、迁移、结果。

**Acceptance Scenarios**:

1. **Given** 一次成功部署，**When** 查看日志，**Then** 能看到 tag、previous、迁移文件、`deploy success`。
2. **Given** 需要手动回滚，**When** 在服务器检出旧 tag 并重建，**Then** 服务恢复到旧版本。

---

### Edge Cases

- Gitee 重复投递同一个 tag：`flock` 保证只有一个部署在执行，其余直接跳过。
- 连续推送两个 tag：后一个在锁释放后执行，不会并行构建。
- tag 在 fetch 后仍不存在（例如删除 tag 的事件）：脚本报错退出，不部署。
- 服务器存在 tracked 本地改动：`git checkout --force` 会覆盖；`.env` 是 untracked，不会被删除。
- 迁移脚本非幂等：脚本会失败；新增迁移必须保持幂等。
- 迁移成功但新版本启动失败：代码/容器会回滚，数据库迁移不会自动回滚，需要人工处理。
- 服务器无法访问 GitHub：安装脚本会失败，需要手动放置 `webhook` 二进制。
- 云安全组误开放 9000：接收器会暴露；必须只允许本机 web 容器通过 host-gateway 访问。

## Requirements

### Functional Requirements

- **FR-C1**: 系统 MUST 只接受 `refs/tags/v*` 触发部署，分支推送直接跳过。
- **FR-C2**: 接收器 MUST 校验 Gitee `X-Gitee-Token`，不匹配的请求不得执行部署脚本。
- **FR-C3**: 部署 MUST 检出触发 tag 对应的提交，而不是拉取分支最新代码。
- **FR-C4**: 部署 MUST 保留服务器本地 `.env`，不得执行 `git clean -fd`。
- **FR-C5**: 部署 MUST 使用 `flock` 串行化，重复或并发 WebHook 不得并行构建。
- **FR-C6**: 部署 MUST 在启动新版本前，按版本号顺序执行 `sql/kcon_migrate_v*.sql` 幂等迁移。
- **FR-C7**: 部署 MUST 使用 `docker compose up -d --build` 更新 backend 与 web，mysql/redis 复用现有容器。
- **FR-C8**: 部署 MUST 在完成后检查 `/`、`/admin/`、`/sign/`、`/screen/`、`/prod-api/captchaImage` 均返回 200。
- **FR-C9**: 构建失败或健康检查失败时，部署 MUST 自动检出上一个 tag/提交并重建。
- **FR-C10**: 部署过程 MUST 写入 `/var/log/kcon-deploy.log`，接收器日志由 systemd journal 保存。
- **FR-C11**: Web 容器 MUST 只暴露 `POST /hooks/deploy` 一个入口，并反代到宿主机接收器；9000 端口不得加入云安全组。
- **FR-C12**: 安装脚本 MUST 幂等：已安装二进制、已存在 `hooks.json` 时不重复覆盖配置。
- **FR-C13**: 发布 tag MUST 由用户在 `main` 上创建并 push；agent 不直接 push。
- **FR-C14**: WebHook 密码 MUST 随机生成并只保存在服务器 `/etc/kcon/hooks.json`（权限 600），不得提交到仓库。

### Key Entities

- **Release Tag**: `v*` 形式的 Git tag，代表一次可部署版本。
- **Deploy Run**: 一次 WebHook 触发的部署，包含 tag、previous、迁移、构建、健康检查结果。
- **Webhook Config**: `/etc/kcon/hooks.json`，包含执行命令与 `X-Gitee-Token` 校验规则。
- **Deploy Log**: `/var/log/kcon-deploy.log`，记录每次部署的完整过程。

## Success Criteria

- **SC-C1**: 正常发布只需 push 一次 tag，不需要 SSH 登录服务器。
- **SC-C2**: 部署失败时，四个入口仍由上一版本提供服务。
- **SC-C3**: 重复或并发 WebHook 不产生并行构建。
- **SC-C4**: 仓库中不出现 WebHook 密码；9000 端口不对公网开放。
- **SC-C5**: 迁移脚本可重复执行，已存在的列/索引不会导致部署失败。

## Assumptions

- 服务器使用 Docker Engine 20.10+、Compose v2、systemd。
- 服务器已存在 `curl`、`openssl`，并能访问 GitHub 或可手动安装 `webhook` 二进制。
- 服务器仓库目录为 `/root/Code/kcon-lucky`，`.env` 已存在且未纳入版本控制。
- Gitee WebHook 使用密码模式（`X-Gitee-Token` 直接携带密码），不是签名模式。
- 发布 tag 采用 annotated tag，格式为 `v<major>.<minor>.<patch>`。
- 新增迁移脚本保持幂等，破坏性 DDL 发布前先备份。

## Out of Scope

- CI 构建镜像、镜像仓库、服务器只 pull。
- 数据库迁移的自动回滚。
- HTTPS/域名接入（后续与域名一起处理）。
- 多服务器、蓝绿发布、灰度发布。
- Gitee Go 流水线方案。

## Implementation Notes

已落地文件：

- `scripts/deploy.sh`：tag 校验、`flock`、检出、迁移、构建、健康检查、回滚。
- `deploy/webhook/hooks.example.json`：WebHook 定义与 token 校验。
- `deploy/webhook/kcon-webhook.service`：systemd 服务。
- `deploy/install-server.sh`：服务器一次性安装脚本。
- `docker/web/nginx.conf`：`POST /hooks/deploy` 反代。
- `docker-compose.yml`：web 服务增加 `host.docker.internal:host-gateway`。
- `docs/04-部署运行.md` 7.4 节：安装、Gitee 配置、发布、回滚。

仓库侧验证：

- `bash -n scripts/deploy.sh deploy/install-server.sh`：通过。
- `python3 -m json.tool deploy/webhook/hooks.example.json`：通过。
- `docker compose config`：通过。
- `nginx -t`（容器内）：通过。

待执行：

1. 恢复服务器 SSH 访问（当前 `kex_exchange_identification: Connection closed by remote host`）。
2. 在服务器 `git checkout develop && git pull --ff-only` 后执行 `sudo bash deploy/install-server.sh`。
3. 在 Gitee 仓库配置 WebHook：URL `/hooks/deploy`、密码、Tag Push。
4. 推送测试 tag，确认端到端部署与回滚。

## Open Questions

- 服务器 SSH 当前不可达：需确认 IP、云安全组 22 端口、fail2ban 或 sshd 状态。
- 域名与 HTTPS 的时间点；接入后 `/hooks/deploy` 应一并走 HTTPS。
