"""集成测试：基础设施层（邮件、二维码、数据库、仓储、配置）。"""
import pytest
from kcon.config import AppConfig, md5_email, load_config
from kcon.infra import email_templates
from kcon.infra.clock import FixedClock
from kcon.infra.database import Database
from kcon.infra.external_checkin import FailingExternalCheckin, NullExternalCheckin
from kcon.infra.mailer import FakeMailer, OutboxMailer, SmtpMailer
from kcon.infra.qrcode_gen import FakeQrCodeGenerator, NullQrCodeGenerator
from kcon.infra.random_source import SecureRandom, SequenceRandom
from kcon.infra.repositories import Repositories
pytestmark = pytest.mark.integration
# ==================== 配置 ====================
class TestConfig:
    def test_md5_email_lowercase(self):
        assert md5_email("ADMIN@KCON.COM") == md5_email("admin@kcon.com")
    def test_md5_email_strip(self):
        assert md5_email("  admin@kcon.com  ") == md5_email("admin@kcon.com")
    def test_is_admin_email_false_by_default(self):
        """未配置 KCON_ADMIN_EMAILS 时不应有内置演示管理员。"""
        cfg = AppConfig()
        assert cfg.is_admin_email("admin@kcon.com") is False
        assert cfg.admin_emails == {}
    def test_is_admin_email_with_env_injection(self):
        import os
        os.environ["KCON_ADMIN_EMAILS"] = "admin@kcon.com:系统管理员"
        try:
            cfg = load_config()
            assert cfg.is_admin_email("admin@kcon.com") is True
            assert cfg.admin_name("admin@kcon.com") == "系统管理员"
        finally:
            del os.environ["KCON_ADMIN_EMAILS"]
    def test_admin_name_unknown(self):
        cfg = AppConfig()
        assert cfg.admin_name("nobody@test.com") == ""
    def test_load_config_default(self):
        cfg = load_config()
        assert cfg.site_url.startswith("http://")
        assert cfg.db_path.endswith(".db")
    def test_config_default_db_path(self):
        cfg = AppConfig(db_path="")
        assert cfg.db_path != ""
# ==================== 邮件模板 ====================
class TestEmailTemplates:
    def _ctx_m01(self):
        return {"approver_name": "张审批", "approver_email": "ap@test.com",
                "channel_name": "渠道A", "quota": 100, "referrer_cap": 20,
                "register_url": "http://x/r/abc", "login_url": "http://x/login"}
    def _ctx_m02(self):
        return {"referrer_name": "李推荐", "company": "公司", "department": "部门",
                "mobile": "13800138000", "email": "r@test.com",
                "created_at": "2026-10-25 10:00:00",
                "approve_url": "http://x/approve/referrer/tk",
                "login_url": "http://x/login"}
    def _ctx_m03(self):
        return {"referrer_name": "李推荐", "referral_url": "http://x/t/abc",
                "qr": "data:image/png;base64,AAA", "channel_name": "渠道A",
                "referrer_cap": 20, "issued_count": 0}
    def _ctx_m03r(self):
        return {"referrer_name": "李推荐", "channel_name": "渠道A",
                "rejected_at": "2026-10-25 10:00:00", "approver_email": "ap@test.com"}
    def _ctx_m04(self):
        return {"recipient_name": "王参会", "company": "单位", "mobile": "",
                "email": "rc@test.com", "created_at": "2026-10-25 10:00:00",
                "issued_count": 1, "referrer_cap": 20,
                "approve_url": "http://x/approve/ticket/tk",
                "resend_url": "http://x/resend/tk"}
    def _ctx_m05(self):
        return {"recipient_name": "王参会", "qr": "data:image/png;base64,AAA",
                "code": "CODE123", "referrer_name": "李推荐"}
    def _ctx_m06(self):
        return {"referrer_name": "李推荐", "recipient_name": "王参会",
                "company": "单位", "mobile": "", "email": "rc@test.com",
                "code_url": "http://x/c/CODE", "qr": "data:image/png;base64,AAA",
                "issued_count": 1, "referrer_cap": 20}
    def _ctx_m07(self):
        return {"referrer_name": "李推荐", "recipient_name": "王参会",
                "company": "单位", "mobile": "", "email": "rc@test.com",
                "checked_at": "2026-10-25 10:00:00"}
    def _ctx_m08(self):
        return {"approver_name": "张审批", "referrer_company": "公司",
                "referrer_department": "部门", "referrer_name": "李推荐",
                "recipient_company": "单位", "recipient_name": "王参会",
                "period_start": "10:00", "period_end": "10:30", "period_count": 1,
                "new_checkins": [{"referrer_name": "李推荐",
                                  "referrer_department": "部门",
                                  "recipient_name": "王参会",
                                  "recipient_company": "单位",
                                  "checked_at": "10:10"}],
                "referrers": [{"name": "李推荐", "department": "部门",
                               "invited": 1, "checked_in": 1}]}
    def _ctx_m09(self):
        return {"admin_name": "管理员", "channel_name": "渠道A",
                "date": "2026-10-25", "quota": 100, "used": 1, "remaining": 99,
                "referrer_count": 1, "invited_count": 1, "approved_count": 1,
                "checked_in_count": 1, "checkin_rate": 100.0}
    @pytest.mark.parametrize("template,ctx_fn", [
        ("M-01", "_ctx_m01"), ("M-02", "_ctx_m02"), ("M-03", "_ctx_m03"),
        ("M-03R", "_ctx_m03r"), ("M-04", "_ctx_m04"), ("M-05", "_ctx_m05"),
        ("M-06", "_ctx_m06"), ("M-07", "_ctx_m07"), ("M-08", "_ctx_m08"),
        ("M-09", "_ctx_m09"),
    ])
    def test_render_all_templates(self, template, ctx_fn):
        ctx = getattr(self, ctx_fn)()
        html = email_templates.render(template, ctx, "KCon2026", "奕智",
                                      "kcon@knownsec.com")
        assert "KCon" in html
        assert "#00e5ff" in html
        assert "#ff2d95" in html
        assert "kcon@knownsec.com" in html
    def test_render_code_template(self):
        html = email_templates.render("CODE", {"code": "123456"}, "KCon2026",
                                      "奕智", "kcon@knownsec.com")
        assert "123456" in html
    def test_render_unknown_template(self):
        with pytest.raises(ValueError, match="未知邮件模板"):
            email_templates.render("UNKNOWN", {}, "a", "b", "c")
    def test_m04_mobile_placeholder(self):
        ctx = self._ctx_m04()
        ctx["mobile"] = ""
        html = email_templates.render("M-04", ctx, "a", "b", "c")
        assert "--" in html
    def test_m06_mobile_placeholder(self):
        ctx = self._ctx_m06()
        ctx["mobile"] = ""
        html = email_templates.render("M-06", ctx, "a", "b", "c")
        assert "--" in html
    def test_m07_mobile_placeholder(self):
        ctx = self._ctx_m07()
        ctx["mobile"] = ""
        html = email_templates.render("M-07", ctx, "a", "b", "c")
        assert "--" in html
    def test_btn_secondary_color(self):
        html = email_templates.btn("按钮", "http://x", "#ff2d95")
        assert "#ffffff" in html
    def test_qr_block_no_caption(self):
        html = email_templates.qr_block("data:image/png;base64,AAA")
        assert "img" in html
    def test_priority_tag(self):
        html = email_templates.priority_tag()
        assert "优先级：高" in html
# ==================== 邮件适配器 ====================
class TestMailer:
    def test_outbox_mailer_saves(self, tmp_path):
        db = Database(str(tmp_path / "t.db"))
        db.init_schema()
        repos = Repositories(db)
        clock = FixedClock()
        mailer = OutboxMailer(repos.email, "KCon2026", "奕智", "k@k.com", clock)
        ok, err = mailer.send("CODE", "a@b.com", "主题", {"code": "123456"})
        assert ok is True
        assert err is None
        mails = repos.email.list_outbox()
        assert len(mails) == 1
        assert mails[0]["template"] == "CODE"
        logs = repos.email.list_logs()
        assert len(logs) == 1
        assert logs[0]["status"] == 1
    def test_outbox_mailer_unknown_template(self, tmp_path):
        db = Database(str(tmp_path / "t.db"))
        db.init_schema()
        repos = Repositories(db)
        clock = FixedClock()
        mailer = OutboxMailer(repos.email, "KCon2026", "奕智", "k@k.com", clock)
        ok, err = mailer.send("UNKNOWN", "a@b.com", "主题", {})
        assert ok is False
        assert "渲染失败" in err
        logs = repos.email.list_logs()
        assert logs[0]["status"] == 0
    def test_smtp_mailer_failure(self, tmp_path):
        db = Database(str(tmp_path / "t.db"))
        db.init_schema()
        repos = Repositories(db)
        clock = FixedClock()
        mailer = SmtpMailer(repos.email, "KCon2026", "奕智", "k@k.com", clock,
                            host="invalid.host.local", port=1, user="u", password="p")
        ok, err = mailer.send("CODE", "a@b.com", "主题", {"code": "123456"})
        assert ok is False
        assert err is not None
    def test_outbox_deliver_direct(self, tmp_path):
        db = Database(str(tmp_path / "t.db"))
        db.init_schema()
        repos = Repositories(db)
        clock = FixedClock()
        mailer = OutboxMailer(repos.email, "KCon2026", "奕智", "k@k.com", clock)
        ok, err = mailer._deliver("a@b.com", "主题", "<html></html>")
        assert ok is True
    def test_fake_mailer_count(self):
        m = FakeMailer()
        m.send("M-01", "a@b.com", "s", {})
        m.send("M-01", "a@b.com", "s", {})
        m.send("M-02", "a@b.com", "s", {})
        assert m.count("M-01") == 2
        assert m.count("M-02") == 1
        assert m.count("M-03") == 0
    def test_fake_mailer_fail(self):
        m = FakeMailer(fail_templates={"M-01"})
        ok, err = m.send("M-01", "a@b.com", "s", {})
        assert ok is False
        assert err == "模拟发送失败"
# ==================== 二维码 ====================
class TestQrCode:
    def test_null_generate_png_empty(self):
        gen = NullQrCodeGenerator()
        assert gen.generate_png("http://x/t/abc", "标题") == b""
    def test_null_generate_datauri_empty(self):
        gen = NullQrCodeGenerator()
        assert gen.generate_datauri("http://x/t/abc", "标题") == ""
    def test_fake_qr_records_calls(self):
        gen = FakeQrCodeGenerator()
        gen.generate_png("content", "title", "sub", "foot")
        assert len(gen.calls) == 1
        assert gen.calls[0]["content"] == "content"
    def test_fake_qr_datauri(self):
        gen = FakeQrCodeGenerator()
        uri = gen.generate_datauri("content", "title")
        assert uri.startswith("data:image/png;base64,")
# ==================== 外部签到 ====================
class TestExternalCheckin:
    def test_null_adapter_success(self):
        adapter = NullExternalCheckin()
        ok, err = adapter.push_checkin({"name": "x"}, "2026-10-25")
        assert ok is True
        assert err is None
        assert len(adapter.calls) == 1
    def test_failing_adapter(self):
        adapter = FailingExternalCheckin()
        ok, err = adapter.push_checkin({"name": "x"}, "2026-10-25")
        assert ok is False
        assert err == "外部系统不可用"
        assert len(adapter.calls) == 1
# ==================== 数据库与仓储 ====================
class TestDatabase:
    def test_init_schema_idempotent(self, tmp_path):
        db = Database(str(tmp_path / "t.db"))
        db.init_schema()
        db.init_schema()
        tables = db.query_all(
            "SELECT name FROM sqlite_master WHERE type='table'")
        names = {t["name"] for t in tables}
        assert "channel" in names
        assert "referrer" in names
    def test_query_one_none(self, tmp_path):
        db = Database(str(tmp_path / "t.db"))
        db.init_schema()
        assert db.query_one("SELECT * FROM channel WHERE id=?", (999,)) is None
    def test_scalar_none(self, tmp_path):
        db = Database(str(tmp_path / "t.db"))
        db.init_schema()
        assert db.scalar("SELECT id FROM channel WHERE id=?", (999,)) is None
    def test_transaction_rollback(self, tmp_path):
        db = Database(str(tmp_path / "t.db"))
        db.init_schema()
        with pytest.raises(RuntimeError):
            with db.transaction() as conn:
                conn.execute(
                    "INSERT INTO channel(name,code,quota,referrer_cap,status,"
                    "created_at,updated_at) VALUES('x','c',1,1,1,'t','t')")
                raise RuntimeError("boom")
        assert db.scalar("SELECT COUNT(*) FROM channel") == 0
    def test_execute_rowcount(self, tmp_path):
        db = Database(str(tmp_path / "t.db"))
        db.init_schema()
        n = db.execute_rowcount("UPDATE channel SET status=2 WHERE id=?", (999,))
        assert n == 0
class TestRepositories:
    def _repos(self, tmp_path):
        db = Database(str(tmp_path / "t.db"))
        db.init_schema()
        return Repositories(db)
    def test_admin_crud(self, tmp_path):
        repos = self._repos(tmp_path)
        assert repos.admin.count_active() == 0
        aid = repos.admin.insert("md5", "管理员", "2026-10-25")
        assert aid > 0
        assert repos.admin.count_active() == 1
        assert repos.admin.find_by_md5("md5")["name"] == "管理员"
        assert len(repos.admin.list_all()) == 1
    def test_channel_crud(self, tmp_path):
        repos = self._repos(tmp_path)
        cid = repos.channel.insert("渠道A", "code1", 100, 20, "", "2026-10-25")
        assert repos.channel.count_active() == 1
        assert repos.channel.sum_quota() == 100
        assert repos.channel.find_by_id(cid)["name"] == "渠道A"
        assert repos.channel.find_by_code("code1")["id"] == cid
        assert repos.channel.find_by_name("渠道A")["id"] == cid
        assert repos.channel.count_related(cid) == 0
        assert repos.channel.update_status(cid, 2, "2026-10-25") == 1
        assert repos.channel.find_by_code("code1") is None
    def test_approver_crud(self, tmp_path):
        repos = self._repos(tmp_path)
        cid = repos.channel.insert("渠道A", "code1", 100, 20, "", "2026-10-25")
        aid = repos.approver.insert(cid, "张审批", "ap@test.com", "2026-10-25")
        assert repos.approver.find_by_email("ap@test.com")["id"] == aid
        assert repos.approver.find_by_channel(cid)["id"] == aid
        assert repos.approver.email_exists("ap@test.com") is True
        assert repos.approver.email_exists("no@test.com") is False
        assert repos.approver.update_last_login(aid, "2026-10-25") == 1
        assert repos.approver.disable_by_channel(cid) == 1
        assert repos.approver.find_by_email("ap@test.com") is None
    def test_referrer_crud(self, tmp_path):
        repos = self._repos(tmp_path)
        cid = repos.channel.insert("渠道A", "code1", 100, 20, "", "2026-10-25")
        rid = repos.referrer.insert(cid, "李推荐", "公司", "部门", "13800138000",
                                    "r@test.com", "rcode", "2026-10-25")
        assert repos.referrer.find_by_id(rid)["name"] == "李推荐"
        assert repos.referrer.find_by_code("rcode")["id"] == rid
        assert repos.referrer.find_by_email("r@test.com")["id"] == rid
        assert repos.referrer.update_status(rid, 2, "2026-10-25", 1) == 1
        assert repos.referrer.find_by_id(rid)["status"] == 2
        assert repos.referrer.update_status(rid, 3, "2026-10-25") == 1
        assert repos.referrer.increment_issued(rid) == 1
        assert repos.referrer.find_by_id(rid)["issued_count"] == 1
        assert len(repos.referrer.list_by_channel(cid)) == 1
        assert len(repos.referrer.list_by_channel(None, 3)) == 1
    def test_referrer_resubmit(self, tmp_path):
        repos = self._repos(tmp_path)
        cid = repos.channel.insert("渠道A", "code1", 100, 20, "", "2026-10-25")
        rid = repos.referrer.insert(cid, "李推荐", "公司", "部门", "13800138000",
                                    "r@test.com", "rcode", "2026-10-25")
        repos.referrer.update_status(rid, 3, "2026-10-25")
        n = repos.referrer.update_for_resubmit(rid, "李推荐2", "公司2", "部门2",
                                               "13800138001", "rcode2")
        assert n == 1
        updated = repos.referrer.find_by_id(rid)
        assert updated["status"] == 1
        assert updated["name"] == "李推荐2"
        assert updated["rejected_at"] is None
    def test_recipient_crud(self, tmp_path):
        repos = self._repos(tmp_path)
        cid = repos.channel.insert("渠道A", "code1", 100, 20, "", "2026-10-25")
        rid = repos.referrer.insert(cid, "李推荐", "公司", "部门", "13800138000",
                                    "r@test.com", "rcode", "2026-10-25")
        rcid = repos.recipient.insert(rid, cid, "王参会", "单位", "", "rc@test.com",
                                      "2026-10-25")
        assert repos.recipient.find_by_id(rcid)["name"] == "王参会"
        assert repos.recipient.find_by_email("rc@test.com")["id"] == rcid
        assert repos.recipient.count_used_by_channel(cid) == 0
        assert repos.recipient.update_status(rcid, 2, "2026-10-25") == 1
        assert repos.recipient.count_used_by_channel(cid) == 1
        assert repos.recipient.update_status(rcid, 3) == 1
        assert len(repos.recipient.list()) == 1
        assert len(repos.recipient.list(channel_id=cid)) == 1
        assert len(repos.recipient.list(referrer_id=rid)) == 1
        assert len(repos.recipient.list(status=3)) == 1
    def test_code_crud(self, tmp_path):
        repos = self._repos(tmp_path)
        cid = repos.channel.insert("渠道A", "code1", 100, 20, "", "2026-10-25")
        rid = repos.referrer.insert(cid, "李推荐", "公司", "部门", "13800138000",
                                    "r@test.com", "rcode", "2026-10-25")
        rcid = repos.recipient.insert(rid, cid, "王参会", "单位", "", "rc@test.com",
                                      "2026-10-25")
        repos.code.insert(rcid, "CODE123", "http://x/c/CODE123", "", "2026-10-25")
        assert repos.code.find_by_code("CODE123")["recipient_id"] == rcid
        assert repos.code.find_by_recipient(rcid)["code"] == "CODE123"
    def test_checkin_crud(self, tmp_path):
        repos = self._repos(tmp_path)
        cid = repos.channel.insert("渠道A", "code1", 100, 20, "", "2026-10-25")
        rid = repos.referrer.insert(cid, "李推荐", "公司", "部门", "13800138000",
                                    "r@test.com", "rcode", "2026-10-25")
        rcid = repos.recipient.insert(rid, cid, "王参会", "单位", "", "rc@test.com",
                                      "2026-10-25")
        repos.checkin.insert(rcid, "2026-10-25", 1, "127.0.0.1")
        assert repos.checkin.find_by_recipient(rcid)["checked_at"] == "2026-10-25"
        assert repos.checkin.update_external(rcid, 1, "2026-10-25", "ok") == 1
    def test_staff_crud(self, tmp_path):
        repos = self._repos(tmp_path)
        repos.staff.insert("签到员", "staff1", "hash", 1, "2026-10-25")
        assert repos.staff.find_by_account("staff1")["name"] == "签到员"
        assert repos.staff.find_by_account("nobody") is None
    def test_token_crud(self, tmp_path):
        repos = self._repos(tmp_path)
        repos.token.insert("tk1", 1, 1, 1, "2026-11-01", "2026-10-25")
        assert repos.token.find_by_token("tk1")["type"] == 1
        assert repos.token.find_active(1, 1)["token"] == "tk1"
        assert repos.token.mark_used("tk1", "2026-10-25") == 1
        assert repos.token.mark_used("tk1", "2026-10-25") == 0
        assert repos.token.find_active(1, 1) is None
    def test_token_invalidate(self, tmp_path):
        repos = self._repos(tmp_path)
        repos.token.insert("tk1", 1, 1, 1, "2026-11-01", "2026-10-25")
        repos.token.insert("tk2", 1, 1, 1, "2026-11-01", "2026-10-25")
        assert repos.token.invalidate_by_target(1, 1, "2026-10-25") == 2
        assert repos.token.find_active(1, 1) is None
    def test_email_crud(self, tmp_path):
        repos = self._repos(tmp_path)
        repos.email.log("M-01", "a@b.com", "主题", 1, None, "2026-10-25")
        repos.email.save_outbox("M-01", "a@b.com", "主题", "<html></html>",
                                "2026-10-25")
        assert len(repos.email.list_logs()) == 1
        assert len(repos.email.list_outbox()) == 1
        mid = repos.email.list_outbox()[0]["id"]
        assert repos.email.get_outbox(mid)["template"] == "M-01"
        assert repos.email.get_outbox(999) is None
    def test_audit_crud(self, tmp_path):
        repos = self._repos(tmp_path)
        repos.audit.log("admin", 1, "create_channel", "channel", 1, "detail",
                        "127.0.0.1", "2026-10-25")
        assert len(repos.audit.list_all()) == 1
    def test_verify_code_crud(self, tmp_path):
        repos = self._repos(tmp_path)
        vid = repos.verify_code.insert("a@b.com", "123456", "login",
                                       "2026-10-25 10:10:00", "2026-10-25 10:00:00")
        assert repos.verify_code.latest("a@b.com")["id"] == vid
        assert repos.verify_code.latest_unused("a@b.com")["id"] == vid
        assert repos.verify_code.count_today("a@b.com", "2026-10-25") == 1
        assert repos.verify_code.increment_fail(vid) == 1
        assert repos.verify_code.mark_used(vid) == 1
        assert repos.verify_code.latest_unused("a@b.com") is None
    def test_m08_crud(self, tmp_path):
        repos = self._repos(tmp_path)
        repos.m08.enqueue(1, 1, "2026-10-25")
        repos.m08.enqueue(1, 2, "2026-10-25")
        pending = repos.m08.pending(1)
        assert len(pending) == 2
        assert repos.m08.mark_sent([p["id"] for p in pending]) == 2
        assert repos.m08.pending(1) == []
        assert repos.m08.mark_sent([]) == 0
    def test_stats_repo(self, tmp_path):
        repos = self._repos(tmp_path)
        totals = repos.stats.admin_totals()
        assert totals["channel_count"] == 0
        assert totals["quota_total"] == 0
# ==================== 随机源 ====================
class TestRandomSourceExtra:
    def test_secure_random_all_methods(self):
        r = SecureRandom()
        assert len(r.channel_code()) == 16
        assert len(r.referrer_code()) == 16
        assert len(r.token()) == 32
        assert len(r.registration_code()) == 16
        assert len(r.verify_code()) == 6
        assert len(r.random_string(5)) == 5
    def test_sequence_random_choice_empty_then_wrap(self):
        r = SequenceRandom(["a"])
        assert r.choice("x") == "a"
        assert r.choice("x") == "a"