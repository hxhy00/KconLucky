#!/usr/bin/env bash
# =============================================================
# 服务器一次性安装脚本：webhook 接收器 + systemd
#
# 用法（在服务器仓库根目录执行）：
#   sudo bash deploy/install-server.sh
#
# 可用环境变量：
#   WEBHOOK_SECRET   自定义 Gitee WebHook 密码；不传则随机生成
# =============================================================
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "请用 root 执行：sudo bash deploy/install-server.sh" >&2
  exit 1
fi

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOOKS_DIR="/etc/kcon"
HOOKS_FILE="$HOOKS_DIR/hooks.json"
UNIT_FILE="/etc/systemd/system/kcon-webhook.service"

echo "仓库目录：$REPO_DIR"

# ---------- 1. 安装 webhook 二进制 ----------
if ! command -v webhook >/dev/null 2>&1; then
  case "$(uname -m)" in
    x86_64)  ARCH="amd64" ;;
    aarch64|arm64) ARCH="arm64" ;;
    *) echo "不支持的架构：$(uname -m)" >&2; exit 1 ;;
  esac

  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT
  URL="https://github.com/adnanh/webhook/releases/download/2.8.2/webhook-linux-${ARCH}.tar.gz"

  echo "下载 webhook：$URL"
  if ! curl -fsSL "$URL" -o "$TMP_DIR/webhook.tar.gz"; then
    echo "下载失败。请手动下载 webhook-linux-${ARCH}.tar.gz 并安装到 /usr/local/bin/webhook。" >&2
    exit 1
  fi

  tar -xzf "$TMP_DIR/webhook.tar.gz" -C "$TMP_DIR"
  BIN="$(find "$TMP_DIR" -type f -name webhook | head -n 1)"
  if [[ -z "$BIN" ]]; then
    echo "压缩包里没有找到 webhook 二进制" >&2
    exit 1
  fi
  install -m 755 "$BIN" /usr/local/bin/webhook
  echo "已安装：/usr/local/bin/webhook"
else
  echo "webhook 已存在：$(command -v webhook)"
fi

# ---------- 2. 生成 hooks.json ----------
install -d -m 700 "$HOOKS_DIR"

if [[ -f "$HOOKS_FILE" ]]; then
  echo "保留现有配置：$HOOKS_FILE（未覆盖）"
else
  SECRET="${WEBHOOK_SECRET:-$(openssl rand -hex 32)}"
  sed -e "s#<WEBHOOK_SECRET>#${SECRET}#g" \
      -e "s#/root/Code/kcon-lucky#${REPO_DIR}#g" \
      "$REPO_DIR/deploy/webhook/hooks.example.json" > "$HOOKS_FILE"
  chmod 600 "$HOOKS_FILE"
  echo "已生成：$HOOKS_FILE"
  echo
  echo "Gitee WebHook 密码（请立即保存）："
  echo "  $SECRET"
  echo
fi

# ---------- 3. systemd ----------
install -m 644 "$REPO_DIR/deploy/webhook/kcon-webhook.service" "$UNIT_FILE"
systemctl daemon-reload
systemctl enable kcon-webhook
systemctl restart kcon-webhook
sleep 1
systemctl --no-pager --lines=5 status kcon-webhook || true

# ---------- 4. 自检 ----------
# 用非 tag 的 ref 触发，脚本会直接跳过，不会真的部署
TOKEN="$(sed -n 's/.*"value": "\(.*\)".*/\1/p' "$HOOKS_FILE" | head -n 1)"
CODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST \
  "http://127.0.0.1:9000/hooks/kcon-deploy" \
  -H "X-Gitee-Token: ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"ref":"refs/heads/main"}' || true)"

if [[ "$CODE" == "200" ]]; then
  echo "自检通过：接收器返回 200"
else
  echo "自检失败：返回 $CODE，请查看 journalctl -u kcon-webhook -n 50" >&2
  exit 1
fi

cat <<EOF

安装完成。下一步在 Gitee 仓库设置 WebHook：
  URL：      http://<服务器公网IP>/hooks/deploy
  密码：     ${TOKEN}
  事件：     Tag Push（只推 v* 标签）
  备注：     9000 端口不要加到云安全组，只有本机 nginx 容器通过 host-gateway 访问。

部署日志：
  tail -f /var/log/kcon-deploy.log
  journalctl -u kcon-webhook -f
EOF
