"""全局配置。"""
import hashlib
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List
def _base_dir() -> Path:
    try:
        return Path(__file__).resolve().parent.parent.parent
    except NameError:
        return Path.cwd()
BASE_DIR = _base_dir()
DATA_DIR = BASE_DIR / "data"
# 站点
SITE_NAME = "KCon2026"
SITE_THEME = "奕智"
SYSTEM_EMAIL = "kcon@knownsec.com"
def md5_email(email: str) -> str:
    """邮箱转 MD5（小写去空格）。"""
    return hashlib.md5(email.strip().lower().encode("utf-8")).hexdigest()
def normalize_base_path(value: str) -> str:
    """规范化子路径前缀：无值返回空串，否则形如 "/kconluck2026"（无尾斜杠）。"""
    v = (value or "").strip().strip("/")
    return f"/{v}" if v else ""
def _parse_admin_env(value: str) -> Dict[str, str]:
    """解析 KCON_ADMIN_EMAILS：逗号分隔，支持 email 或 email:name。返回 md5 → name。"""
    whitelist: Dict[str, str] = {}
    for item in value.split(","):
        item = item.strip()
        if not item:
            continue
        if ":" in item:
            email, name = item.split(":", 1)
            email = email.strip()
            name = name.strip() or "管理员"
        else:
            email, name = item, "管理员"
        if email:
            whitelist[md5_email(email)] = name
    return whitelist
def _parse_admin_env_plain(value: str) -> Dict[str, str]:
    """解析 KCON_ADMIN_EMAILS：返回明文 email → name（供种子写入 admin 表）。"""
    plain: Dict[str, str] = {}
    for item in value.split(","):
        item = item.strip()
        if not item:
            continue
        if ":" in item:
            email, name = item.split(":", 1)
            email = email.strip()
            name = name.strip() or "管理员"
        else:
            email, name = item, "管理员"
        if email:
            plain[email.lower()] = name
    return plain
# 管理员白名单：生产由 KCON_ADMIN_EMAILS 提供（docker-compose 已强制必填）。
# 不设内置演示名单：未配置时 admin 表为空即无管理员，不存在可猜测的演示账号。
ADMIN_EMAILS_DEFAULT: Dict[str, str] = {}
ADMIN_WHITELIST: Dict[str, str] = {}
# 验证码参数
CODE_LENGTH = 6
CODE_TTL_SECONDS = 600
CODE_RESEND_INTERVAL = 60
CODE_DAILY_LIMIT = 20
CODE_MAX_FAIL = 5
CODE_LOCK_SECONDS = 1800
# 会话
SESSION_TTL_SECONDS = 7200
# 令牌
TOKEN_TTL_SECONDS = 7 * 24 * 3600
# 业务默认
DEFAULT_REFERRER_CAP = 20
@dataclass
class AppConfig:
    """应用配置（可注入，便于测试）。"""
    site_url: str = "http://127.0.0.1:8848"
    db_path: str = ""
    site_name: str = SITE_NAME
    site_theme: str = SITE_THEME
    system_email: str = SYSTEM_EMAIL
    admin_whitelist: Dict[str, str] = field(default_factory=lambda: dict(ADMIN_WHITELIST))
    # 明文 email → 显示名（供种子管理员写入 admin 表；whitelist 键是 md5 无法反推）
    admin_emails: Dict[str, str] = field(default_factory=lambda: dict(ADMIN_EMAILS_DEFAULT))
    code_length: int = CODE_LENGTH
    code_ttl_seconds: int = CODE_TTL_SECONDS
    code_resend_interval: int = CODE_RESEND_INTERVAL
    code_daily_limit: int = CODE_DAILY_LIMIT
    code_max_fail: int = CODE_MAX_FAIL
    code_lock_seconds: int = CODE_LOCK_SECONDS
    session_ttl_seconds: int = SESSION_TTL_SECONDS
    token_ttl_seconds: int = TOKEN_TTL_SECONDS
    default_referrer_cap: int = DEFAULT_REFERRER_CAP
    # SMTP（v1 未启用真发：仅当 smtp_host 配置时才选择 SmtpMailer）
    smtp_host: str = ""
    smtp_port: int = 465
    smtp_user: str = ""
    smtp_password: str = ""
    # 是否在接口响应里回显验证码：仅本地 Demo/测试开启；生产必须关闭
    demo_code: bool = False
    # 子路径部署前缀（如挂到 knownsec.com/kconluck2026/）。空串表示挂在根路径。
    base_path: str = ""
    def __post_init__(self):
        if not self.db_path:
            self.db_path = str(DATA_DIR / "kcon.db")
    def is_admin_email(self, email: str) -> bool:
        """判断是否为管理员邮箱（保留给测试与种子判定使用，登录已改为查 admin 表）。"""
        return md5_email(email) in self.admin_whitelist
    def admin_name(self, email: str) -> str:
        """获取管理员显示名。"""
        return self.admin_whitelist.get(md5_email(email), "")
def load_config() -> AppConfig:
    """从环境变量加载配置。"""
    port = os.getenv("KCON_PORT", "8848")
    try:
        smtp_port = int(os.getenv("KCON_SMTP_PORT", "465"))
    except ValueError:
        smtp_port = 465
    return AppConfig(
        site_url=os.getenv("KCON_SITE_URL", f"http://127.0.0.1:{port}"),
        db_path=os.getenv("KCON_DB_PATH", str(DATA_DIR / "kcon.db")),
        system_email=os.getenv("KCON_SYSTEM_EMAIL", SYSTEM_EMAIL),
        admin_whitelist=_parse_admin_env(os.getenv("KCON_ADMIN_EMAILS", "")),
        admin_emails=_parse_admin_env_plain(os.getenv("KCON_ADMIN_EMAILS", "")),
        smtp_host=os.getenv("KCON_SMTP_HOST", ""),
        smtp_port=smtp_port,
        smtp_user=os.getenv("KCON_SMTP_USER", ""),
        smtp_password=os.getenv("KCON_SMTP_PASSWORD", ""),
        demo_code=os.getenv("KCON_DEMO_CODE", "0") == "1",
        base_path=normalize_base_path(os.getenv("KCON_BASE_PATH", "")),
    )
