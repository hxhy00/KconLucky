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

# 不设默认口令：未提供时直接报错退出
: "${MYSQL_ROOT_PASSWORD:? 需要在环境变量中提供 MYSQL_ROOT_PASSWORD（可在项目根 .env 中配置后 source .env，或执行前手动 export）}"

# 必须指定库名：备份产物不含 USE/CREATE DATABASE（mysqldump 未带 --databases），
# 缺了会报 ERROR 1046 No database selected，恢复必然失败。
# 库名与基线 SQL 一致固定为 kcon_lucky（SQL 里 CREATE DATABASE 写死同名）。
# 口令走 MYSQL_PWD 环境变量：命令行 -p 会出现在宿主机 ps 里
docker exec -i -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" kcon-mysql \
  mysql -uroot --default-character-set=utf8mb4 kcon_lucky < "$FILE"

echo "恢复完成。"
