"""补齐覆盖率缺口：覆盖剩余语句与分支。"""
import sqlite3
import pytest
from kcon.app import Container
from kcon.config import AppConfig
from kcon.infra.clock import FixedClock
from kcon.infra.database import Database
from kcon.infra.mailer import OutboxMailer
from kcon.infra.repositories import Repositories
pytestmark = pytest.mark.integration

def raw_execute(db_path, sql, params=()):
    """绕过外键直接执行 SQL（用于构造异常数据）。"""
    conn = sqlite3.connect(db_path)
    conn.execute("PRAGMA foreign_keys = OFF")
    conn.execute(sql, params)
    conn.commit()
    conn.close()

class TestAppContainer:
    def test_container_with_defaults(self, tmp_path, monkeypatch):
        monkeypatch.setenv("KCON_DB_PATH", str(tmp_path / "default.db"))
        c = Container(init_schema=True)
        assert c.config is not None and c.clock is not None
        assert c.random is not None and c.mailer is not None
        assert c.qr is not None and c.external is not None

    def test_container_with_db_path_override(self, tmp_path):
        cfg = AppConfig(db_path=str(tmp_path / "orig.db"))
        c = Container(config=cfg, db_path=str(tmp_path / "override.db"))
        assert c.config.db_path == str(tmp_path / "override.db")

    def test_container_without_init_schema(self, tmp_path):
        cfg = AppConfig(db_path=str(tmp_path / "noinit.db"))
        c = Container(config=cfg, init_schema=False)
        assert c.repos is not None

class TestConfigBaseDir:
    def test_base_dir_fallback(self):
        from kcon import config as cfg_module
        real_file = cfg_module.__file__
        try:
            del cfg_module.__file__
            assert cfg_module._base_dir() is not None
        finally:
            cfg_module.__file__ = real_file

class TestMailerGaps:
    def _mailer(self, tmp_path):
        db = Database(str(tmp_path / "t.db"))
        db.init_schema()
        repos = Repositories(db)
        return repos, OutboxMailer(repos.email, "KCon2026", "奕智", "k@k.com",
                                   FixedClock())

    def test_base_mailer_send_success(self, tmp_path):
        repos, mailer = self._mailer(tmp_path)
        ok, err = super(OutboxMailer, mailer).send(
            "CODE", "a@b.com", "主题", {"code": "123456"})
        assert ok is True and err is None
        assert len(repos.email.list_logs()) == 1

    def test_base_mailer_send_render_error(self, tmp_path):
        repos, mailer = self._mailer(tmp_path)
        ok, err = super(OutboxMailer, mailer).send("UNKNOWN", "a@b.com", "主题", {})
        assert ok is False and "渲染失败" in err

    def test_smtp_mailer_success_mock(self, tmp_path, monkeypatch):
        repos, _ = self._mailer(tmp_path)
        from kcon.infra import mailer as mailer_module

        class FakeSMTP:
            def __init__(self, *a, **kw): pass
            def __enter__(self): return self
            def __exit__(self, *a): return False
            def login(self, u, p): pass
            def sendmail(self, f, t, m): pass

        monkeypatch.setattr(mailer_module.smtplib, "SMTP_SSL", FakeSMTP)
        smtp = mailer_module.SmtpMailer(repos.email, "KCon2026", "奕智", "k@k.com",
                                        FixedClock(), host="h", port=465,
                                        user="u", password="p")
        ok, err = smtp.send("CODE", "a@b.com", "主题", {"code": "123456"})
        assert ok is True and err is None

class TestCheckinGaps:
    def test_lookup_code_recipient_missing(self, container):
        raw_execute(container.config.db_path,
                    "INSERT INTO registration_code(recipient_id,code,qr_url,qr_content,"
                    "issued_at) VALUES(999,'ORPHANCODE123456','','','2026-10-25')")
        ok, msg, _ = container.checkin.lookup("ORPHANCODE123456")
        assert ok is False and msg == "注册码无效，请核对"

    def test_confirm_duplicate_returns_result(self, container, recipient_factory):
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        container.checkin.confirm(code["code"])
        ok, msg, result = container.checkin.confirm(code["code"])
        assert ok is False and result is not None
        assert result.name == "王参会"

    def test_code_info_recipient_missing(self, container):
        raw_execute(container.config.db_path,
                    "INSERT INTO registration_code(recipient_id,code,qr_url,qr_content,"
                    "issued_at) VALUES(998,'ORPHANCODE654321','','','2026-10-25')")
        ok, msg, _ = container.checkin.code_info("ORPHANCODE654321")
        assert ok is False and msg == "注册码无效"

class TestReferrerGaps:
    def test_approve_referrer_missing(self, container):
        container.repos.token.insert("ORPHANTOKEN000000000000000000001", 1, 999, 1,
                                     "2026-11-01 10:00:00",
                                     container.clock.now_str())
        ok, msg, _ = container.referrer.approve(
            "ORPHANTOKEN000000000000000000001", "approve")
        assert ok is False and msg == "推荐人不存在"

class TestChannelUpdate:
    def test_update_ok(self, container, channel_factory):
        ch = channel_factory()
        ok, msg = container.channel.update(ch["id"], "新名称", 200, 30, "备注x")
        assert ok is True, msg
        c = container.repos.channel.find_by_id(ch["id"])
        assert c["name"] == "新名称" and c["quota"] == 200 and c["referrer_cap"] == 30
        assert c["remark"] == "备注x"
    def test_update_missing(self, container):
        ok, msg = container.channel.update(999, "名", 100, 20, "")
        assert ok is False and msg == "渠道不存在"
    def test_update_duplicate_name(self, container, channel_factory):
        ch1 = channel_factory(approver_email="a1@test.com")
        ch2 = channel_factory(name="另一渠道", approver_email="a2@test.com")
        ok, msg = container.channel.update(ch2["id"], ch1["name"], 100, 20, "")
        assert ok is False and msg == "渠道名称已存在"
    def test_update_quota_below_used(self, container, channel_factory,
                                     referrer_factory, recipient_factory):
        ch = channel_factory(approver_email="a3@test.com")
        ref = referrer_factory(channel=ch)
        rc = recipient_factory(referrer=ref)  # 已审批 → used=1
        ok, msg = container.channel.update(ch["id"], ch["name"], 0, 20, "")
        assert ok is False and "配额" in msg
    def test_update_suspended_rejected(self, container, channel_factory):
        ch = channel_factory(approver_email="a4@test.com")
        container.channel.suspend(ch["id"])
        ok, msg = container.channel.update(ch["id"], "改名", 100, 20, "")
        assert ok is False and "仅启用中" in msg
    def test_update_invalid_fields(self, container, channel_factory):
        ch = channel_factory(approver_email="a5@test.com")
        ok, msg = container.channel.update(ch["id"], "", 100, 20, "")
        assert ok is False
        ok, msg = container.channel.update(ch["id"], "有效名称", 99999, 20, "")
        assert ok is False and "配额" in msg

    def test_approve_referrer_mark_used_fails(self, container, referrer_factory):
        ref = referrer_factory(approve=False)
        token = container.repos.token.find_active(1, ref["id"])
        container.repos.token.mark_used(token["token"], container.clock.now_str())
        ok, msg, _ = container.referrer.approve(token["token"], "approve")
        assert ok is False and msg == "该链接已被使用"

    def test_register_referrer_no_approver(self, container, channel_factory):
        ch = channel_factory()
        container.repos.approver.disable_by_channel(ch["id"])
        ok, msg = container.referrer.register(ch["code"], "李推荐", "公司",
                                              "部门", "13800138000", "r@test.com")
        assert ok is True

    def test_approve_referrer_mark_used_race(self, container, referrer_factory):
        """覆盖 referrer_service.py:99 —— mark_used 返回 0 的分支。"""
        ref = referrer_factory(approve=False)
        token = container.repos.token.find_active(1, ref["id"])
        original = container.repos.token.mark_used
        container.repos.token.mark_used = lambda t, u: 0
        try:
            ok, msg, _ = container.referrer.approve(token["token"], "approve")
        finally:
            container.repos.token.mark_used = original
        assert ok is False
        assert msg == "该链接已被使用"

class TestTicketGaps:
    def test_register_recipient_channel_missing(self, container, referrer_factory):
        ref = referrer_factory()
        raw_execute(container.config.db_path,
                    "DELETE FROM channel WHERE id=?", (ref["channel_id"],))
        ok, msg = container.ticket.register(ref["code"], "王参会", "单位", "13900139000",
                                            "rc@test.com")
        assert ok is False and msg == "该邀请链接已失效"

    def test_approve_recipient_missing(self, container):
        container.repos.token.insert("ORPHANTOKEN000000000000000000002", 2, 999, 1,
                                     "2026-11-01 10:00:00",
                                     container.clock.now_str())
        ok, msg, _ = container.ticket.approve("ORPHANTOKEN000000000000000000002")
        assert ok is False and msg == "申请不存在"

    def test_approve_recipient_mark_used_fails(self, container, referrer_factory):
        ref = referrer_factory()
        container.ticket.register(ref["code"], "王参会", "单位", "13900139000", "rc@test.com")
        rc = container.repos.recipient.find_by_email("rc@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        container.repos.token.mark_used(token["token"], container.clock.now_str())
        ok, msg, _ = container.ticket.approve(token["token"])
        assert ok is False and msg == "该链接已被使用"

    def test_approve_recipient_mark_used_race(self, container, referrer_factory):
        """覆盖 ticket_service.py:101 —— mark_used 返回 0 的分支。"""
        ref = referrer_factory()
        container.ticket.register(ref["code"], "王参会", "单位", "13900139000", "rc@test.com")
        rc = container.repos.recipient.find_by_email("rc@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        # 直接调用 approve，但在中途用 monkeypatch 让 mark_used 返回 0
        original = container.repos.token.mark_used
        container.repos.token.mark_used = lambda t, u: 0
        try:
            ok, msg, _ = container.ticket.approve(token["token"])
        finally:
            container.repos.token.mark_used = original
        assert ok is False
        assert msg == "该链接已被使用"

    def test_register_duplicate_email_no_other_referrer(self, container,
                                                        recipient_factory):
        """覆盖 ticket_service.py:47->50 —— 邮箱冲突但原推荐人查不到。"""
        rc = recipient_factory()
        ref = container.repos.referrer.find_by_id(rc["referrer_id"])
        # 先建第二个推荐人（复用同一渠道）
        ch = container.repos.channel.find_by_name("测试渠道")
        container.referrer.register(ch["code"], "赵推荐", "公司", "部门",
                                    "13800138001", "ref2@test.com")
        ref2 = container.repos.referrer.find_by_email("ref2@test.com")
        token = container.repos.token.find_active(1, ref2["id"])
        container.referrer.approve(token["token"], "approve")
        ref2 = container.repos.referrer.find_by_id(ref2["id"])
        # 删除原推荐人（模拟数据异常），使 find_by_id 返回 None
        raw_execute(container.config.db_path,
                    "DELETE FROM referrer WHERE id=?", (ref["id"],))
        ok, msg = container.ticket.register(ref2["code"], "王参会2", "单位2", "13900139000",
                                            "rc@test.com")
        assert ok is False
        assert msg == (False, "该邮箱已由「」推荐注册，请勿重复提交")[1]
