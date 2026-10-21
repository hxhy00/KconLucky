#!/usr/bin/env bash
# =============================================================
# Gitee Tag Push Webhook 部署脚本
#
# 参数：$1 = refs/tags/v1.0.0
#       由 deploy/webhook/hooks.example.json 从 payload.ref 传入
#
# 只接受 v* tag；分支推送一律跳过。
# 迁移只执行 sql/kcon_migrate_v*.sql（幂等），不自动回滚数据库。
# =============================================================
set -euo pipefail

REPO_DIR="${REPO_DIR:-/root/Code/kcon-lucky}"
LOG_FILE="${LOG_FILE:-/var/log/kcon-deploy.log}"
LOCK_FILE="${LOCK_FILE:-/var/lock/kcon-deploy.lock}"
HEALTH_URLS=(
  "http://127.0.0.1/"
  "http://127.0.0.1/admin/"
  "http://127.0.0.1/sign/"
  "http://127.0.0.1/screen/"
  "http://127.0.0.1/prod-api/captchaImage"
)

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }

REF="${1:-}"
if [[ ! "$REF" =~ ^refs/tags/v[0-9] ]]; then
  log "skip: not a v* tag ref: ${REF:-<empty>}"
  exit 0
fi
TAG="${REF#refs/tags/}"

# 防并发：webhook 重试或连续推两个 tag 时，只允许一个部署在跑
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  log "skip: another deploy is running"
  exit 0
fi

exec >>"$LOG_FILE" 2>&1
log "deploy start: $TAG"

cd "$REPO_DIR"

# 记录当前版本，失败时回滚
PREV="$(git describe --tags --exact-match 2>/dev/null || git rev-parse --short HEAD)"
log "previous: $PREV"

git fetch --tags --force
if ! git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  log "error: tag $TAG not found after fetch"
  exit 1
fi

# --force 只覆盖 tracked 文件；.env 是 untracked，不会被删除。
# 禁止在这里使用 git clean -fd，否则服务器本地配置会被清掉。
git checkout --force --detach "$TAG"

# 先让数据库就绪，再执行迁移
docker compose up -d mysql redis

mysql_ready=0
for _ in $(seq 1 45); do
  if docker compose ps mysql 2>/dev/null | grep -q 'healthy'; then
    mysql_ready=1
    break
  fi
  sleep 2
done
if [[ "$mysql_ready" != "1" ]]; then
  log "error: mysql is not healthy, abort before migration"
  exit 1
fi

# 幂等迁移按版本号顺序执行；新增迁移必须保持幂等
shopt -s nullglob
migrations=(sql/kcon_migrate_v*.sql)
shopt -u nullglob
if (( ${#migrations[@]} > 0 )); then
  while IFS= read -r f; do
    log "migrate: $f"
    docker compose exec -T mysql sh -c \
      'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "${MYSQL_DATABASE:-kcon_lucky}"' < "$f"
  done < <(printf '%s\n' "${migrations[@]}" | sort -V)
fi

# 镜像由本地构建后 docker load 导入，服务器不做构建（内存不足）
if ! docker compose up -d; then
  log "error: compose up failed, rolling back to $PREV"
  git checkout --force --detach "$PREV" || true
  docker compose up -d || true
  exit 1
fi

# 健康检查：四端 + 一个后端接口，最多等 60s
healthy=0
for _ in $(seq 1 30); do
  healthy=1
  for url in "${HEALTH_URLS[@]}"; do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$url" || true)"
    if [[ "$code" != "200" ]]; then
      healthy=0
      log "health: $url -> ${code:-no-response}"
      break
    fi
  done
  [[ "$healthy" == "1" ]] && break
  sleep 2
done

if [[ "$healthy" != "1" ]]; then
  log "error: health check failed, rolling back to $PREV"
  git checkout --force --detach "$PREV" || true
  docker compose up -d || true
  exit 1
fi

log "deploy success: $TAG"
