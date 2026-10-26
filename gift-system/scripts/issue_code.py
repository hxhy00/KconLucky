#!/usr/bin/env python3
"""本地签发一次性登录验证码（v1 未接 SMTP 时使用）。

用法（在服务器项目目录执行）：
    python3 scripts/issue_code.py --email ops@example.com
    python3 scripts/issue_code.py --email ops@example.com --db /app/data/kcon.db

说明：
- 验证码 10 分钟有效、一次性，直接写入数据库；
- 仅限运维在服务器本地执行，不要暴露成 HTTP 接口；
- 生产环境禁止开启 KCON_DEMO_CODE=1（那会把验证码回显给任意请求者）。
"""
import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

from kcon.app import Container  # noqa: E402
from kcon.config import AppConfig  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description="签发一次性登录验证码")
    parser.add_argument("--email", required=True, help="管理员邮箱")
    parser.add_argument("--db", default=str(ROOT / "data" / "kcon.db"),
                        help="SQLite 数据库路径")
    args = parser.parse_args()

    config = AppConfig(site_url="http://127.0.0.1:8848", db_path=args.db)
    container = Container(config=config)
    ok, msg, code = container.auth.send_verify_code(args.email.strip().lower())
    if not ok:
        print(f"签发失败：{msg}", file=sys.stderr)
        sys.exit(1)
    print(f"验证码：{code}（10 分钟内有效，一次性使用）")


if __name__ == "__main__":
    main()
