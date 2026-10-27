"""全局测试夹具。"""
import json
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path
import pytest
# 确保 src 在路径中
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))
from kcon.api.server import make_server
from kcon.app import Container
from kcon.config import AppConfig, md5_email
from kcon.infra.clock import FixedClock
from kcon.infra.external_checkin import NullExternalCheckin
from kcon.infra.mailer import FakeMailer
from kcon.infra.qrcode_gen import FakeQrCodeGenerator
from kcon.infra.random_source import SequenceRandom
@pytest.fixture
def fixed_clock():
    """固定时钟，默认 2026-10-25 10:00:00。"""
    return FixedClock("2026-10-25 10:00:00")
@pytest.fixture
def seq_random():
    """序列随机源，按需构造。"""
    def _make(seq):
        return SequenceRandom(seq)
    return _make
class _DetRandom(SequenceRandom):
    """确定性随机源。"""
    def __init__(self):
        super().__init__([])
        self._counters = {}
    def _next(self, prefix: str, length: int) -> str:
        idx = self._counters.get(prefix, 0) + 1
        self._counters[prefix] = idx
        return f"{prefix}{idx:0{length - len(prefix)}d}"[:length]
    def channel_code(self) -> str:
        return self._next("CH", 16)
    def referrer_code(self) -> str:
        return self._next("RF", 16)
    def token(self) -> str:
        return self._next("TK", 32)
    def registration_code(self) -> str:
        idx = self._counters.get("RC", 0) + 1
        self._counters["RC"] = idx
        return f"RC{idx:02d}".ljust(16, "A")
    def verify_code(self, length: int = 6) -> str:
        idx = self._counters.get("VC", 0) + 1
        self._counters["VC"] = idx
        return f"{idx:06d}"[-length:]
class ApiClient:
    """HTTP 客户端。"""
    def __init__(self, base: str):
        self.base = base
        self.sid = None
    def request(self, method: str, path: str, data=None):
        url = self.base + path
        headers = {"Content-Type": "application/json"}
        if self.sid:
            headers["X-Session"] = self.sid
        body = json.dumps(data).encode() if data is not None else None
        req = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                raw = resp.read()
                ctype = resp.headers.get("Content-Type", "")
                if "application/json" in ctype:
                    return json.loads(raw), resp.status
                return {"_raw": raw, "_len": len(raw)}, resp.status
        except urllib.error.HTTPError as exc:
            try:
                return json.loads(exc.read()), exc.code
            except (json.JSONDecodeError, ValueError):
                return {"ok": False}, exc.code
    def get(self, path: str):
        return self.request("GET", path)
    def post(self, path: str, data=None):
        return self.request("POST", path, data if data is not None else {})
    def login(self, email: str) -> dict:
        """登录并保存 sid。"""
        r, _ = self.post("/api/auth/send-code", {"email": email})
        code = r.get("demo_code")
        r, _ = self.post("/api/auth/login", {"email": email, "code": code})
        if r.get("ok"):
            self.sid = r["sid"]
        return r
@pytest.fixture(scope="function")
def live_server(tmp_path):
    """启动真实 HTTP 服务（集成/系统测试共用）。"""
    demo_admins = {"admin@kcon.com": "系统管理员", "boss@kcon.com": "运营负责人",
                   "ops@kcon.com": "技术负责人"}
    cfg = AppConfig(site_url="http://127.0.0.1:0",
                    db_path=str(tmp_path / "live.db"),
                    admin_whitelist={md5_email(e): n for e, n in demo_admins.items()},
                    admin_emails=demo_admins,
                    demo_code=True)
    container = Container(
        config=cfg,
        clock=FixedClock("2026-10-25 10:00:00"),
        random_source=_DetRandom(),
        mailer=FakeMailer(),
        qr_generator=FakeQrCodeGenerator(),
        external_adapter=NullExternalCheckin(),
    )
    server = make_server(host="127.0.0.1", port=0, container=container)
    port = server.server_address[1]
    cfg.site_url = f"http://127.0.0.1:{port}"
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    time.sleep(0.2)
    yield {
        "base": f"http://127.0.0.1:{port}",
        "container": container,
        "server": server,
        "port": port,
    }
    server.shutdown()
    server.server_close()
@pytest.fixture
def client(live_server):
    """API 客户端。"""
    return ApiClient(live_server["base"])
@pytest.fixture
def admin_client(live_server):
    """已登录的管理员客户端。"""
    c = ApiClient(live_server["base"])
    c.login("admin@kcon.com")
    return c