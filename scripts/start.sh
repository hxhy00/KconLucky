#!/usr/bin/env bash
# 一键启动（首次或日常）
set -e
cd "$(dirname "$0")/.."

[ -f .env ] || { echo "缺少 .env，请先从 .env.example 复制并修改"; exit 1; }

echo "==> 构建并启动容器..."
docker compose up -d --build

echo "==> 等待后端就绪..."
# 以日志里的启动标志为准（curl / 依赖根路径状态码，不可靠）；
# 超时必须失败退出：否则脚本照常打印「启动完成」，误导读到的人
READY=0
for i in $(seq 1 60); do
  if docker logs kcon-backend 2>&1 | grep -q "Started RuoYiApplication"; then
    READY=1
    break
  fi
  sleep 3
done
if [ "$READY" != "1" ]; then
  echo "后端 3 分钟内未就绪，请查看日志排查：docker logs kcon-backend"
  exit 1
fi

# 取值必须去掉行内注释与首尾空白（同 preflight.sh 的 env_get）
env_get() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2- | sed -e 's/[[:space:]]*#.*$//' -e 's/[[:space:]]*$//'; }
LAN_IP=$(env_get LAN_IP)
PORT=$(env_get WEB_PORT)
PORT=${PORT:-80}
# WEB_PORT 支持 "8081" 或 "127.0.0.1:8081"（只绑本机）两种写法，取端口部分
case "$PORT" in *:*) PORT=${PORT##*:} ;; esac
PREFIX=$(env_get KCON_BASE_PATH)
PREFIX=${PREFIX%/}
# 未配置时实际前缀是镜像内置的 /kconluck2026
PREFIX=${PREFIX:-/kconluck2026}
# 80 端口不显示 :80
[ "$PORT" = "80" ] && PORT_PART="" || PORT_PART=":${PORT}"
echo ""
echo "============================================"
echo " 启动完成"
echo " 入口页：   http://${LAN_IP}${PORT_PART}${PREFIX}/"
echo " 管理端：   http://${LAN_IP}${PORT_PART}${PREFIX}/admin/"
echo " 签到 H5：  http://${LAN_IP}${PORT_PART}${PREFIX}/sign/"
echo " 抽奖大屏： http://${LAN_IP}${PORT_PART}${PREFIX}/screen/"
echo " 账号：     见 docs/04-部署运行.md（首次登录后请立即修改初始口令）"
echo "============================================"
docker compose ps
