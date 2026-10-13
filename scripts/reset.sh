#!/usr/bin/env bash
# 清场：清空名单/签到/中奖数据，保留表结构，重灌端口号池
# 正式开场前使用（会把演示数据一并清掉）
set -e
cd "$(dirname "$0")/.."

read -r -p "确认清空全部业务数据（名单/签到/中奖/核验日志）？[y/N] " ans
[ "$ans" = "y" ] || [ "$ans" = "Y" ] || { echo "已取消"; exit 0; }

docker exec -i kcon-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD:-root123}" kcon_lucky <<'SQL'
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE kcon_attendee;
TRUNCATE TABLE kcon_checkin;
TRUNCATE TABLE kcon_winner;
TRUNCATE TABLE kcon_verify_log;
UPDATE kcon_port_pool SET status=0, phone=NULL, used_time=NULL;
UPDATE kcon_prize SET drawn_count=0;
SET FOREIGN_KEY_CHECKS=1;
SELECT '已清场' AS 结果,
       (SELECT COUNT(*) FROM kcon_attendee)  AS 名单数,
       (SELECT COUNT(*) FROM kcon_checkin)   AS 签到数,
       (SELECT COUNT(*) FROM kcon_winner)    AS 中奖数;
SQL

echo "清场完成。端口号池已归零（65535 个号全部释放）。"
