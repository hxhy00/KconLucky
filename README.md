# KCon 2026 签到 + 抽奖系统

KCon 2026 大会现场的**自助签到**与**抽奖大屏**系统。参会人扫码输入手机号自助签到，系统即分配一个 1–65535 的唯一随机端口号，作为抽奖与兑奖的唯一凭证；现场大屏滚动抽号，管理端负责名单导入、抽奖控制、兑奖核验与数据统计。

一套镜像 + 一份编排，可**完全离线**在会场单机运行。

---

## 核心特性

| 能力 | 说明 |
|---|---|
| 自助签到 | 参会人手机扫码 → 输入完整 11 位手机号 → 确认身份 → 端口号显示在**手机屏幕上** |
| 唯一端口号 | 从 65535 号池中随机分配，作为抽奖与兑奖的唯一凭证 |
| 抽奖大屏 | 深色大屏，滚动抽号，分上午 / 下午场；一号只中一次，支持作废重抽 |
| 管理端 | 名单导入、签到监控、抽奖控制台、兑奖核验、中奖与核验日志、奖品配置、数据清场与迁移 |
| 渠道映射 | 按票务渠道标识映射签到入口，支持变更后重算存量名单 |
| 离线部署 | Docker Compose 一键编排，现场不联网、不构建、不拉镜像 |
| Tag 自动部署 | 推送 `v*` tag 触发 CD，健康检查失败自动回滚上一版本 |

---

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 4.0.3 · RuoYi-Vue 3.9.2（业务代码位于 `com.ruoyi.kcon`） |
| 管理端 | Vue 2.6 · Element UI · vue-cli（`ruoyi-ui/`） |
| 签到 H5 | Vue 3.4 · Vite 5（`checkin-h5/`） |
| 抽奖大屏 | Vue 3.4 · Vite 5（`screen/`） |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 7 |
| 编排 | Docker Compose · Nginx 1.27 |

---

## 系统架构

```
                     ┌──────────────────────────────────────────┐
   观众手机  ──────► │  nginx-web 容器（前端总入口 :80）         │
   （自助扫码）      │   /            → 入口分流页（选入口）     │
                     │   /admin/      → 管理端（静态）           │
                     │   /screen/     → 抽奖大屏（静态）         │
                     │   /sign/       → 签到 H5（静态）          │
                     │   /prod-api/   → 反向代理到 backend:8080  │
                     └───────────────┬──────────────────────────┘
                                     │ 容器内网络
                                     ▼
   笔记本 HDMI ────► ┌──────────────────────────┐     ┌────────────────────────┐
   投大屏            │  backend 容器（:8080）    │────►│  mysql 容器（:3306）    │
                     │  RuoYi-Vue 后端 jar       │JDBC │  redis 容器（:6379）    │
                     └──────────────────────────┘     └────────────────────────┘
```

三端前端共用同一后端域名与端口，走同源 `/prod-api/` 反代，**不存在跨域问题**。

| 端 | 地址 | 使用者 |
|---|---|---|
| 入口分流页 | `http://<IP>/` | 认不准 URL 时开这个 |
| 管理端 | `http://<IP>/admin/` | 工作人员 |
| 抽奖大屏 | `http://<IP>/screen/` | 投屏机（F11 全屏） |
| 签到 H5 | `http://<IP>/sign/` | 参会人扫码进入 |

---

## 快速开始

### 前置

- Docker Desktop（含 Compose v2）
- 若需本地改代码：JDK 17、Maven 3.9、Node 18+（推荐用 nvm 管理）

### 1. 准备配置

```bash
cp .env.example .env
# 必须生成 JWT 密钥，否则后端拒绝启动
openssl rand -base64 48
# 将输出填入 .env 的 TOKEN_SECRET
```

`.env` 关键项：

| 变量 | 作用 |
|---|---|
| `WEB_PORT` | 三端统一入口端口，默认 80 |
| `LAN_IP` | 现场局域网 IP 或域名，用于生成签到二维码 |
| `TOKEN_SECRET` | JWT 签名密钥，**必填**，≥32 字节，不达标拒绝启动 |
| `MYSQL_ROOT_PASSWORD` | 数据库口令 |
| `MYSQL_DEBUG_PORT` | 数据库调试端口，仅绑定 `127.0.0.1` |

### 2. 一键启动

```bash
docker compose up -d --build      # 会前联网机器，构建并启动
docker compose ps                 # 四个容器应全部 Up，mysql/redis 为 healthy
```

首次启动时 `sql/` 下的初始化脚本会自动执行（仅当数据卷为空时）。

### 3. 访问

浏览器打开 `http://<LAN_IP>/`，按分流页选择入口。默认管理端账号见 `docs/04-部署运行.md` 第 7 节。

---

## 目录结构

```
KconLucky/
├── docker-compose.yml          # 一键编排
├── .env.example                # 配置模板
├── docker/
│   ├── backend/Dockerfile      # 后端多阶段构建
│   └── web/                    # 三端统一构建 + nginx 配置 + 入口分流页
├── ruoyi/                      # 后端源码（业务在 com.ruoyi.kcon 包）
├── ruoyi-ui/                   # 管理端源码
├── checkin-h5/                 # 签到 H5 源码
├── screen/                     # 抽奖大屏源码
├── sql/                        # 初始化脚本与幂等迁移脚本
├── scripts/                    # 备份 / 恢复 / 清场 / 部署脚本
├── deploy/                     # WebHook 接收器安装
├── docs/                       # 需求、数据库、接口、部署、缺口、Mock 台账
├── specs/                      # 按序号的特性规格
└── CONTRIBUTING.md             # 分支模型与贡献规范
```

---

## 本地开发

三端前端各自独立开发，后端通过 `ruoyi/` 启动。

```bash
# 后端（默认 8080）
cd ruoyi && mvn clean package -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin.jar

# 管理端（vue-cli）
cd ruoyi-ui && npm install && npm run dev

# 签到 H5（Vite）
cd checkin-h5 && npm install && npm run dev

# 抽奖大屏（Vite）
cd screen && npm install && npm run dev
```

前端依赖使用 npmmirror 源，后端 Maven 使用阿里云源（见 `docker/maven-settings.xml`）。

---

## 部署

- **会前（联网机器）**：`docker compose build` → `docker save` 导出镜像 → 打包项目目录
- **现场（离线笔记本）**：`docker load` 导入镜像 → `docker compose up -d`（不 build、不拉取）
- **公网服务器**：推送 `v*` tag 触发 CD 自动部署，失败自动回滚

完整步骤、现场网络要求、迁移与故障排查见 [`docs/04-部署运行.md`](docs/04-部署运行.md)。

---

## 分支模型

采用**标准 Git Flow**：`main` 只存放可发布版本，合入来源仅限 `release/*` 与 `hotfix/*`；`develop` 为集成分支；`feature/*` 从 `develop` 切出并 `--no-ff` 合回。

完整约束、`main` 分支保护配置清单与发布流程见 [`CONTRIBUTING.md`](CONTRIBUTING.md)。

---

## 文档索引

| 文档 | 内容 |
|---|---|
| [`docs/01-需求规格.md`](docs/01-需求规格.md) | 需求基线与验收标准 |
| [`docs/02-数据库设计.md`](docs/02-数据库设计.md) | 表结构与字段说明 |
| [`docs/03-接口设计.md`](docs/03-接口设计.md) | 接口契约 |
| [`docs/04-部署运行.md`](docs/04-部署运行.md) | 部署、发布、迁移、故障排查、安全基线 |
| [`docs/05-功能缺口.md`](docs/05-功能缺口.md) | 已知功能缺口 |
| [`docs/06-Mock台账.md`](docs/06-Mock台账.md) | 未验证 Mock 记录 |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | 分支模型、提交规范、发布流程 |
| `specs/` | 按序号的特性规格（需求 + 计划 + 任务） |

---

## 许可

内部项目，未公开授权。
