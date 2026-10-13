#!/usr/bin/env bash
# 全量数据备份（导出 .sql，可用于换机迁移）
set -e
cd "$(dirname "$0")/.."

mkdir -p data/backup
STAMP=$(date +%Y%m%d_%H%M%S)
OUT="data/backup/kcon_lucky_${STAMP}.sql"

docker exec kcon-mysql mysqldump \
  -uroot -p"${MYSQL_ROOT_PASSWORD:-root123}" \
  --single-transaction --routines --triggers \
  --default-character-set=utf8mb4 \
  kcon_lucky > "$OUT"

echo "备份完成：$OUT ($(du -h "$OUT" | cut -f1))"
