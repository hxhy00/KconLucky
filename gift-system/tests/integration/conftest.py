"""集成测试夹具：真实 SQLite + 假邮件器 + 固定时钟。"""
import sys
from pathlib import Path
import pytest
ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "src"))
from kcon.app import Container
from kcon.config import AppConfig, md5_email
from kcon.infra.clock import FixedClock
from kcon.infra.external_checkin import NullExternalCheckin
from kcon.infra.mailer import FakeMailer
from kcon.infra.qrcode_gen import FakeQrCodeGenerator
from kcon.infra.random_source import SequenceRandom
class DeterministicRandom(SequenceRandom):
    """确定性随机源：按计数器生成可预测的编码。"""
    def __init__(self):
        super().__init__([])
        self._counters = {}
    def _next(self, prefix: str, length: int, charset: str) -> str:
        idx = self._counters.get(prefix, 0) + 1
        self._counters[prefix] = idx
        base = f"{prefix}{idx:0{length - len(prefix)}d}"
        return base[:length]
    def channel_code(self) -> str:
        return self._next("CH", 16, "")
    def referrer_code(self) -> str:
        return self._next("RF", 16, "")
    def token(self) -> str:
        return self._next("TK", 32, "")
    def registration_code(self) -> str:
        idx = self._counters.get("RC", 0) + 1
        self._counters["RC"] = idx
        # 使用合法字符集（去 0/O/1/I）
        return f"RC{idx:02d}".ljust(16, "A")
    def verify_code(self, length: int = 6) -> str:
        idx = self._counters.get("VC", 0) + 1
        self._counters["VC"] = idx
        return f"{idx:06d}"[-length:]
@pytest.fixture
def app_config(tmp_path):
    """测试配置（显式提供管理员名单，模拟生产由 KCON_ADMIN_EMAILS 注入）。"""
    return AppConfig(
        site_url="http://test.local",
        db_path=str(tmp_path / "test.db"),
        admin_whitelist={md5_email("admin@kcon.com"): "系统管理员"},
        admin_emails={"admin@kcon.com": "系统管理员"},
        demo_code=True,
    )
@pytest.fixture
def fake_mailer():
    """假邮件器。"""
    return FakeMailer()
@pytest.fixture
def fake_qr():
    """假二维码生成器。"""
    return FakeQrCodeGenerator()
@pytest.fixture
def fake_external():
    """假外部签到适配器。"""
    return NullExternalCheckin()
@pytest.fixture
def container(app_config, fake_mailer, fake_qr, fake_external):
    """完整容器（确定性依赖）。"""
    return Container(
        config=app_config,
        clock=FixedClock("2026-10-25 10:00:00"),
        random_source=DeterministicRandom(),
        mailer=fake_mailer,
        qr_generator=fake_qr,
        external_adapter=fake_external,
        init_schema=True,
    )
@pytest.fixture
def channel_factory(container):
    """创建渠道的快捷方法。"""
    def _create(name="测试渠道", approver_name="张审批",
                approver_email="approver@test.com", quota=100, cap=20):
        ok, msg = container.channel.create(name, approver_name, approver_email,
                                           quota, cap)
        assert ok, msg
        return container.repos.channel.find_by_name(name)
    return _create
@pytest.fixture
def referrer_factory(container, channel_factory):
    """创建并审批推荐人的快捷方法。"""
    def _create(channel=None, name="李推荐", email="ref@test.com",
                approve=True):
        ch = channel or channel_factory()
        ok, msg = container.referrer.register(
            ch["code"], name, "测试公司", "安全部", "13800138000", email)
        assert ok, msg
        ref = container.repos.referrer.find_by_email(email)
        if approve:
            token = container.repos.token.find_active(1, ref["id"])
            ok, msg, _ = container.referrer.approve(token["token"], "approve")
            assert ok, msg
            ref = container.repos.referrer.find_by_id(ref["id"])
        return ref
    return _create
@pytest.fixture
def recipient_factory(container, referrer_factory):
    """创建并审批接收人的快捷方法。"""
    def _create(referrer=None, name="王参会", email="rc@test.com", approve=True):
        ref = referrer or referrer_factory()
        ok, msg = container.ticket.register(
            ref["code"], name, "参会单位", "13900139000", email)
        assert ok, msg
        rc = container.repos.recipient.find_by_email(email)
        if approve:
            token = container.repos.token.find_active(2, rc["id"])
            ok, msg, _ = container.ticket.approve(token["token"], "approve")
            assert ok, msg
            rc = container.repos.recipient.find_by_id(rc["id"])
        return rc
    return _create