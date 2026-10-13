#!/usr/bin/env bash
# 数据恢复（导入备份 .sql 到当前库）
# 用法：./scripts/restore.sh data/backup/kcon_lucky_20261012_100000.sql
set -e
cd "$(dirname "$0")/.."

FILE="$1"
[ -n "$FILE" ] || { echo "用法：$0 <备份文件.sql>"; exit 1; }
[ -f "$FILE" ] || { echo "文件不存在：$FILE"; exit 1; }

read -r -p "确认用 $FILE 覆盖当前数据？[y/N] " ans
[ "$ans" = "y" ] || [ "$ans" = "Y" ] || { echo "已取消"; exit 0; }

docker exec -i kcon-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD:-root123}" \
  --default-character-set=utf8mb4 < "$FILE"

echo "恢复完成。"
