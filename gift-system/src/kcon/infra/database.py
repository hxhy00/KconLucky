"""数据库：连接管理、建表、事务。"""
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Any, List, Optional
SCHEMA = """
CREATE TABLE IF NOT EXISTS admin (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email_md5 TEXT NOT NULL UNIQUE,
    email TEXT NOT NULL DEFAULT '',
    name TEXT NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS channel (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    code TEXT NOT NULL UNIQUE,
    quota INTEGER NOT NULL CHECK(quota BETWEEN 1 AND 500),
    referrer_cap INTEGER NOT NULL DEFAULT 20 CHECK(referrer_cap BETWEEN 1 AND 100),
    remark TEXT DEFAULT '',
    status INTEGER NOT NULL DEFAULT 1,
    created_by INTEGER,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS approver (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id INTEGER NOT NULL REFERENCES channel(id),
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    status INTEGER NOT NULL DEFAULT 1,
    last_login_at TEXT,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS referrer (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id INTEGER NOT NULL REFERENCES channel(id),
    name TEXT NOT NULL,
    company TEXT NOT NULL,
    department TEXT NOT NULL,
    mobile TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    code TEXT NOT NULL UNIQUE,
    status INTEGER NOT NULL DEFAULT 1,
    issued_count INTEGER NOT NULL DEFAULT 0,
    approved_by INTEGER,
    approved_at TEXT,
    rejected_at TEXT,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS recipient (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    referrer_id INTEGER NOT NULL REFERENCES referrer(id),
    channel_id INTEGER NOT NULL REFERENCES channel(id),
    name TEXT NOT NULL,
    company TEXT NOT NULL,
    mobile TEXT DEFAULT '',
    email TEXT NOT NULL UNIQUE,
    status INTEGER NOT NULL DEFAULT 1,
    approved_at TEXT,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS registration_code (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recipient_id INTEGER NOT NULL UNIQUE REFERENCES recipient(id),
    code TEXT NOT NULL UNIQUE,
    qr_url TEXT DEFAULT '',
    qr_content TEXT DEFAULT '',
    issued_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS checkin (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recipient_id INTEGER NOT NULL UNIQUE REFERENCES recipient(id),
    checked_at TEXT NOT NULL,
    staff_id INTEGER,
    device_ip TEXT DEFAULT '',
    material_issued INTEGER NOT NULL DEFAULT 1,
    external_sync_status INTEGER NOT NULL DEFAULT 0,
    external_sync_at TEXT,
    external_response TEXT
);
CREATE TABLE IF NOT EXISTS checkin_staff (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    account TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    created_by INTEGER,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS token (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    token TEXT NOT NULL UNIQUE,
    type INTEGER NOT NULL,
    target_id INTEGER NOT NULL,
    owner_id INTEGER NOT NULL,
    expires_at TEXT NOT NULL,
    used_at TEXT,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS email_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    template TEXT NOT NULL,
    to_email TEXT NOT NULL,
    subject TEXT NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    error TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    sent_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    actor_type TEXT NOT NULL,
    actor_id INTEGER,
    action TEXT NOT NULL,
    target_type TEXT,
    target_id INTEGER,
    detail TEXT,
    ip TEXT DEFAULT '',
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS outbox (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    template TEXT NOT NULL,
    to_email TEXT NOT NULL,
    subject TEXT NOT NULL,
    html TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS verify_code (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT NOT NULL,
    code TEXT NOT NULL,
    scene TEXT NOT NULL DEFAULT 'login',
    expires_at TEXT NOT NULL,
    used INTEGER NOT NULL DEFAULT 0,
    fail_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS m08_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id INTEGER NOT NULL,
    recipient_id INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    sent INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_channel_status ON channel(status);
CREATE INDEX IF NOT EXISTS idx_approver_channel ON approver(channel_id);
CREATE INDEX IF NOT EXISTS idx_referrer_channel ON referrer(channel_id);
CREATE INDEX IF NOT EXISTS idx_referrer_status ON referrer(status);
CREATE INDEX IF NOT EXISTS idx_recipient_referrer ON recipient(referrer_id);
CREATE INDEX IF NOT EXISTS idx_recipient_channel ON recipient(channel_id);
CREATE INDEX IF NOT EXISTS idx_recipient_status ON recipient(status);
CREATE INDEX IF NOT EXISTS idx_token_type_target ON token(type, target_id);
CREATE INDEX IF NOT EXISTS idx_token_expires ON token(expires_at);
CREATE INDEX IF NOT EXISTS idx_email_template ON email_log(template);
CREATE INDEX IF NOT EXISTS idx_email_status ON email_log(status);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_log(actor_type, actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_vc_email ON verify_code(email, created_at);
CREATE INDEX IF NOT EXISTS idx_m08_channel ON m08_queue(channel_id, sent);
CREATE INDEX IF NOT EXISTS idx_checkin_checked ON checkin(checked_at);
"""
class Database:
    """数据库封装。"""
    def __init__(self, db_path: str):
        self.db_path = db_path
        Path(db_path).parent.mkdir(parents=True, exist_ok=True)
    def connect(self) -> sqlite3.Connection:
        """建立连接（WAL 模式）。"""
        conn = sqlite3.connect(self.db_path, timeout=15.0)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode = WAL")
        conn.execute("PRAGMA synchronous = NORMAL")
        conn.execute("PRAGMA busy_timeout = 5000")
        conn.execute("PRAGMA foreign_keys = ON")
        return conn
    @contextmanager
    def transaction(self):
        """事务上下文（自动提交/回滚）。"""
        conn = self.connect()
        try:
            conn.execute("BEGIN IMMEDIATE")
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()
    @contextmanager
    def reader(self):
        """只读上下文。"""
        conn = self.connect()
        try:
            yield conn
        finally:
            conn.close()
    def init_schema(self) -> None:
        """初始化表结构（含幂等迁移）。"""
        with self.transaction() as conn:
            conn.executescript(SCHEMA)
            self._migrate(conn)
    @staticmethod
    def _migrate(conn: sqlite3.Connection) -> None:
        """幂等迁移：旧库 admin 表补充 email 列与唯一索引。"""
        cols = [r["name"] for r in conn.execute("PRAGMA table_info(admin)").fetchall()]
        if "email" not in cols:
            conn.execute("ALTER TABLE admin ADD COLUMN email TEXT NOT NULL DEFAULT ''")
        # 部分唯一索引：仅对非空 email 生效，避免历史空值冲突
        conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_admin_email "
                     "ON admin(email) WHERE email <> ''")
    def query_all(self, sql: str, params: tuple = ()) -> List[dict]:
        """查询多行。"""
        with self.reader() as conn:
            return [dict(r) for r in conn.execute(sql, params).fetchall()]
    def query_one(self, sql: str, params: tuple = ()) -> Optional[dict]:
        """查询单行。"""
        with self.reader() as conn:
            row = conn.execute(sql, params).fetchone()
            return dict(row) if row else None
    def execute(self, sql: str, params: tuple = ()) -> int:
        """执行写操作，返回 lastrowid。"""
        with self.transaction() as conn:
            cur = conn.execute(sql, params)
            return cur.lastrowid or 0
    def execute_rowcount(self, sql: str, params: tuple = ()) -> int:
        """执行写操作，返回影响行数。"""
        with self.transaction() as conn:
            cur = conn.execute(sql, params)
            return cur.rowcount
    def scalar(self, sql: str, params: tuple = ()) -> Any:
        """查询单值。"""
        with self.reader() as conn:
            row = conn.execute(sql, params).fetchone()
            return row[0] if row else None