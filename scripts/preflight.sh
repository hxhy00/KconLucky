#!/usr/bin/env bash
# 活动前自检（只读，不改任何配置）
# 用法：在服务器项目根目录执行  bash scripts/preflight.sh
# 退出码：0 = 无阻断项；1 = 存在 FAIL，必须先处理

set -uo pipefail
cd "$(dirname "$0")/.."

FAIL=0
WARN=0
pass() { printf "  [PASS] %s\n" "$1"; }
warn() { printf "  [WARN] %s\n" "$1"; WARN=$((WARN + 1)); }
fail() { printf "  [FAIL] %s\n" "$1"; FAIL=$((FAIL + 1)); }
env_get() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2-; }

[ -f .env ] || { echo "缺少 .env，无法自检"; exit 1; }

echo "== 1. 环境变量 =="
SECRET=$(env_get TOKEN_SECRET)
[ ${#SECRET} -ge 32 ] && pass "TOKEN_SECRET 长度 ${#SECRET}" || fail "TOKEN_SECRET 过短（需 ≥32）"

DBPW=$(env_get MYSQL_ROOT_PASSWORD)
case "$DBPW" in
  "" | 123456 | password | admin123) fail "MYSQL_ROOT_PASSWORD 为空或弱口令" ;;
  *) [ ${#DBPW} -ge 16 ] && pass "MYSQL_ROOT_PASSWORD 长度 ${#DBPW}" || warn "MYSQL_ROOT_PASSWORD < 16 位" ;;
esac

DEMO=$(env_get KCON_DEMO_CODE)
[ -z "$DEMO" ] && pass "KCON_DEMO_CODE 未配置（赠票侧车已移除）" || warn "KCON_DEMO_CODE 仍在 .env 中，赠票侧车已移除，可删除该项"

LAN=$(env_get LAN_IP)
case "$LAN" in
  "" | 127.0.0.1 | 192.168.1.100) fail "LAN_IP=${LAN}，签到二维码会指向错误地址" ;;
  *) pass "LAN_IP=${LAN}" ;;
esac

PREFIX=$(env_get KCON_BASE_PATH)
PREFIX=${PREFIX%/}

echo "== 2. 容器状态 =="
for c in kcon-web kcon-backend kcon-mysql kcon-redis; do
  st=$(docker inspect -f '{{.State.Status}}' "$c" 2>/dev/null || echo missing)
  [ "$st" = "running" ] && pass "$c running" || fail "$c=$st"
done

echo "== 3. 入口连通性（本机回环） =="
for p in "${PREFIX}/" "${PREFIX}/admin/" "${PREFIX}/sign/" "${PREFIX}/screen/"; do
  code=$(curl -sL -o /dev/null -w '%{http_code}' -m 5 "http://127.0.0.1${p}" 2>/dev/null || echo 000)
  [ "$code" = "200" ] && pass "${p} → 200" || fail "${p} → ${code}"
done

echo "== 4. 测试残留 =="
n=$(docker exec kcon-redis redis-cli --scan --pattern 'login_tokens:*' 2>/dev/null | wc -l | tr -d ' ')
[ "${n:-0}" = "0" ] && pass "无残留登录令牌" || warn "Redis 有 ${n} 个 login_tokens 键，建议清理"

db=$(docker compose exec -T mysql mysql -uroot -p"$DBPW" kcon_lucky -N \
  -e "SELECT COUNT(*) FROM sys_user WHERE user_name LIKE '1990000000%';" 2>/dev/null | tr -d ' \r')
if [ "${db:-x}" = "0" ]; then
  pass "无渗透测试账号残留"
else
  fail "仍有 ${db:-?} 个渗透测试账号（1990000000x），活动前必须删除"
fi

echo "== 5. 资源 =="
mem=$(free -m | awk '/Mem:/{print $7}')
[ "${mem:-0}" -ge 200 ] && pass "可用内存 ${mem}MB" || warn "可用内存仅 ${mem}MB"
use=$(df -h / | awk 'NR==2{gsub("%","",$5);print $5}')
[ "${use:-100}" -le 85 ] && pass "根分区占用 ${use}%" || warn "根分区占用 ${use}%"

echo
echo "结果：FAIL=${FAIL}  WARN=${WARN}"
[ "$FAIL" -eq 0 ] || exit 1
