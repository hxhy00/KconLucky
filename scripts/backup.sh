#!/usr/bin/env bash
# 全量数据备份（导出 .sql，可用于换机迁移）
set -e
cd "$(dirname "$0")/.."

mkdir -p data/backup
STAMP=$(date +%Y%m%d_%H%M%S)
OUT="data/backup/kcon_lucky_${STAMP}.sql"

# 不设默认口令：未提供时直接报错退出
: "${MYSQL_ROOT_PASSWORD:? 需要在环境变量中提供 MYSQL_ROOT_PASSWORD（可在项目根 .env 中配置后 source .env，或执行前手动 export）}"

# 口令走 MYSQL_PWD 环境变量：命令行 -p 会出现在宿主机 ps 里
docker exec -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" kcon-mysql mysqldump \
  -uroot \
  --single-transaction --routines --triggers \
  --default-character-set=utf8mb4 \
  kcon_lucky > "$OUT"

echo "备份完成：$OUT ($(du -h "$OUT" | cut -f1))"
