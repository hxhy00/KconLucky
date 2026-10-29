#!/usr/bin/env bash
# 活动前自检（只读，不改任何配置）
# 用法：在服务器项目根目录执行  bash scripts/preflight.sh
# 退出码：0 = 无阻断项；1 = 存在 FAIL，必须先处理

set -uo pipefail

# 定位 .env：脚本可能在 scripts/ 下（发行版 /opt/kcon/scripts/preflight.sh，
# .env 在上层），也可能和 .env 同目录。两处都找，先同目录后上级，
# 避免按固定层级 cd 到 /opt 后找不到 .env 直接退出
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
if [ -f "$SCRIPT_DIR/.env" ]; then
  cd "$SCRIPT_DIR"
elif [ -f "$SCRIPT_DIR/../.env" ]; then
  cd "$SCRIPT_DIR/.."
fi

FAIL=0
WARN=0
pass() { printf "  [PASS] %s\n" "$1"; }
warn() { printf "  [WARN] %s\n" "$1"; WARN=$((WARN + 1)); }
fail() { printf "  [FAIL] %s\n" "$1"; FAIL=$((FAIL + 1)); }
# 取值必须去掉行内注释与首尾空白：.env 里 "WEB_PORT=80  # 说明" 这种写法，
# 直接 cut 会把注释一并读进值，拼出非法 URL 导致连通性检查全部误报
env_get() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2- | sed -e 's/[[:space:]]*#.*$//' -e 's/[[:space:]]*$//'; }

[ -f .env ] || { echo "缺少 .env，无法自检"; exit 1; }

echo "== 1. 环境变量 =="
SECRET=$(env_get TOKEN_SECRET)
case "$SECRET" in
  "" | 用_openssl_rand_生成后填入) fail "TOKEN_SECRET 未配置（仍是占位符或为空）" ;;
  *) [ ${#SECRET} -ge 32 ] && pass "TOKEN_SECRET 长度 ${#SECRET}" || fail "TOKEN_SECRET 过短（需 ≥32）" ;;
esac

DBPW=$(env_get MYSQL_ROOT_PASSWORD)
case "$DBPW" in
  "" | 123456 | password | admin123 | root123 | changeme | 改成你自己的强口令)
    fail "MYSQL_ROOT_PASSWORD 为空、弱口令或仍是模板占位符" ;;
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
PREFIX=${PREFIX:-/kconluck2026}
# WEB_PORT 支持两种写法："8081" 或 "127.0.0.1:8081"（只绑本机回环），
# 后者直接当端口用会拼出 http://127.0.0.1:127.0.0.1:8081 这样的非法 URL
PORT=$(env_get WEB_PORT)
PORT=${PORT:-80}
case "$PORT" in
  *:*) PORT=${PORT##*:} ;;
esac

echo "== 2. 容器状态 =="
for c in kcon-web kcon-backend kcon-mysql kcon-redis; do
  # docker inspect 找不到容器时 stdout 可能只是一个换行，直接判空不可靠，统一去空白
  st=$(docker inspect -f '{{.State.Status}}' "$c" 2>/dev/null | tr -d '[:space:]')
  [ "$st" = "running" ] && pass "$c running" || fail "$c=${st:-missing}"
done

echo "== 3. 入口连通性（本机回环） =="
for p in "${PREFIX}/" "${PREFIX}/admin/" "${PREFIX}/sign/" "${PREFIX}/screen/"; do
  # 连接失败时 curl 仍会经 -w 输出 000 且退出码非 0，这里只取输出，
  # 不要用 "|| echo 000" 兜底——那会把结果拼成 000000
  code=$(curl -sL -o /dev/null -w '%{http_code}' -m 5 "http://127.0.0.1:${PORT}${p}" 2>/dev/null)
  code=$(printf '%s' "$code" | tr -dc '0-9')
  [ "$code" = "200" ] && pass "${p} → 200" || fail "${p} → ${code:-000}"
done

echo "== 4. 测试残留 =="
n=$(docker exec kcon-redis redis-cli --scan --pattern 'login_tokens:*' 2>/dev/null | wc -l | tr -d ' ')
[ "${n:-0}" = "0" ] && pass "无残留登录令牌" || warn "Redis 有 ${n} 个 login_tokens 键，建议清理"

# 统一用 docker exec（compose exec 要求 cwd 有 docker-compose.yml，发行版目录没有）
db=$(docker exec -e MYSQL_PWD="$DBPW" kcon-mysql mysql -uroot kcon_lucky -N \
  -e "SELECT COUNT(*) FROM sys_user WHERE user_name LIKE '1990000000%';" 2>/dev/null | tr -d ' \r')
if [ -z "$db" ]; then
  fail "无法检查渗透测试账号（mysql 容器未运行或查询失败）"
elif [ "$db" = "0" ]; then
  pass "无渗透测试账号残留"
else
  fail "仍有 ${db} 个渗透测试账号（1990000000x），活动前必须删除"
fi

# 初始口令是公开的（写在仓库模板与文档里，raw.githubusercontent.com 可下载），
# 未修改 = 任何人可登录超管清场/导出数据，必须作为阻断项
pwd_unset=$(docker exec -e MYSQL_PWD="$DBPW" kcon-mysql mysql -uroot kcon_lucky -N \
  -e "SELECT pwd_update_date IS NULL FROM sys_user WHERE user_name='13000000000';" 2>/dev/null | tr -d ' \r')
if [ -z "$pwd_unset" ]; then
  fail "无法检查超管口令（mysql 容器未运行或账号 13000000000 不存在）"
elif [ "$pwd_unset" = "0" ]; then
  pass "超管初始口令已修改"
else
  fail "超管初始口令未修改（13000000000 的口令是公开的），登录后必须立即修改"
fi

echo "== 5. 资源 =="
mem=$(free -m | awk '/Mem:/{print $7}')
[ "${mem:-0}" -ge 200 ] && pass "可用内存 ${mem}MB" || warn "可用内存仅 ${mem}MB"
use=$(df -h / | awk 'NR==2{gsub("%","",$5);print $5}')
[ "${use:-100}" -le 85 ] && pass "根分区占用 ${use}%" || warn "根分区占用 ${use}%"

echo
echo "结果：FAIL=${FAIL}  WARN=${WARN}"
[ "$FAIL" -eq 0 ] || exit 1
