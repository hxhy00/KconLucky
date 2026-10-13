#!/usr/bin/env bash
# 一键启动（首次或日常）
set -e
cd "$(dirname "$0")/.."

[ -f .env ] || { echo "缺少 .env，请先从 .env.example 复制并修改"; exit 1; }

echo "==> 构建并启动容器..."
docker compose up -d --build

echo "==> 等待后端就绪..."
for i in $(seq 1 60); do
  if docker exec kcon-backend curl -sf http://127.0.0.1:8080/ >/dev/null 2>&1 || \
     docker logs kcon-backend 2>&1 | grep -q "Started RuoYiApplication"; then
    break
  fi
  sleep 3
done

LAN_IP=$(grep -E '^LAN_IP=' .env | cut -d= -f2)
PORT=$(grep -E '^WEB_PORT=' .env | cut -d= -f2)
echo ""
echo "============================================"
echo " 启动完成"
echo " 管理端：   http://${LAN_IP}:${PORT}/admin/"
echo " 签到 H5：  http://${LAN_IP}:${PORT}/sign/"
echo " 抽奖大屏： http://${LAN_IP}:${PORT}/screen/"
echo " 默认账号： admin / admin123"
echo "============================================"
docker compose ps
