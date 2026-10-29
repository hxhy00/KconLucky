#!/usr/bin/env bash
# 清场：清空名单/签到/中奖/发放数据，保留表结构，重灌端口号池，场次全部归零
# 正式开场前使用（会把演示数据一并清掉）
set -e
cd "$(dirname "$0")/.."

# 不设默认口令：未提供时直接报错退出，避免带着错误口令静默执行
: "${MYSQL_ROOT_PASSWORD:? 需要在环境变量中提供 MYSQL_ROOT_PASSWORD（可在项目根 .env 中配置后 source .env，或 ./scripts/reset.sh 前手动 export）}"

read -r -p "确认清空全部业务数据（名单/签到/中奖/发放记录/核验日志）？[y/N] " ans
[ "$ans" = "y" ] || [ "$ans" = "Y" ] || { echo "已取消"; exit 0; }

# 口令走 MYSQL_PWD 环境变量：命令行 -p 会出现在宿主机 ps 里
docker exec -i -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" kcon-mysql \
  mysql -uroot --default-character-set=utf8mb4 kcon_lucky <<'SQL'
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE kcon_attendee;
TRUNCATE TABLE kcon_checkin;
TRUNCATE TABLE kcon_winner;
TRUNCATE TABLE kcon_verify_log;
TRUNCATE TABLE kcon_benefit_grant;
UPDATE kcon_port_pool SET status=0, phone=NULL, used_time=NULL;
UPDATE kcon_prize SET drawn_count=0;
UPDATE kcon_session SET status=0;
SET FOREIGN_KEY_CHECKS=1;
SELECT '已清场' AS 结果,
       (SELECT COUNT(*) FROM kcon_attendee)  AS 名单数,
       (SELECT COUNT(*) FROM kcon_checkin)   AS 签到数,
       (SELECT COUNT(*) FROM kcon_winner)    AS 中奖数;
SQL

echo "清场完成。端口号池已归零（65535 个号全部释放），场次全部重置为未开始。"
