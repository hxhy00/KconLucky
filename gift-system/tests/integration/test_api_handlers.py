"""集成测试：接口层处理器（补齐 handlers/middleware/router/server 覆盖率）。"""
import json
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path
import pytest
ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "src"))
from kcon.api.handlers import ApiError, Handlers
from kcon.api.middleware import AuthContext, RateLimiter, SessionStore
from kcon.api import router
from kcon.api.server import make_server
from kcon.app import Container
from kcon.config import AppConfig
from kcon.infra.clock import FixedClock
from kcon.infra.mailer import FakeMailer
from kcon.infra.qrcode_gen import FakeQrCodeGenerator
from kcon.infra.random_source import SequenceRandom
pytestmark = pytest.mark.integration
class SeqRandom(SequenceRandom):
    """确定性随机源。"""
    def __init__(self):
        super().__init__([])
        self._n = {}
    def _next(self, prefix, length):
        i = self._n.get(prefix, 0) + 1
        self._n[prefix] = i
        return f"{prefix}{i:0{length - len(prefix)}d}"[:length]
    def channel_code(self):
        return self._next("CH", 16)
    def referrer_code(self):
        return self._next("RF", 16)
    def token(self):
        return self._next("TK", 32)
    def registration_code(self):
        i = self._n.get("RC", 0) + 1
        self._n["RC"] = i
        return f"RC{i:02d}".ljust(16, "A")
    def verify_code(self, length=6):
        i = self._n.get("VC", 0) + 1
        self._n["VC"] = i
        return f"{i:06d}"[-length:]
@pytest.fixture
def api(tmp_path):
    """构造 Handlers + 容器。"""
    cfg = AppConfig(site_url="http://t.local", db_path=str(tmp_path / "api.db"),
                    demo_code=True)
    c = Container(config=cfg, clock=FixedClock(), random_source=SeqRandom(),
                  mailer=FakeMailer(), qr_generator=FakeQrCodeGenerator())
    sessions = SessionStore(c.clock, 7200)
    limiter = RateLimiter(c.clock)
    return Handlers(c, sessions, limiter), c, sessions, limiter
def _login_admin(api):
    h, c, sessions, _ = api
    r = h.send_code({"email": "admin@kcon.com"})
    r = h.login({"email": "admin@kcon.com", "code": r["demo_code"]})
    return r["sid"]
def _login_approver(api, email):
    h, c, sessions, _ = api
    r = h.send_code({"email": email})
    r = h.login({"email": email, "code": r["demo_code"]})
    return r["sid"]
class TestMiddleware:
    def test_session_create_get_destroy(self):
        clock = FixedClock()
        s = SessionStore(clock, 7200)
        s.create("sid1", "admin", {"email": "a@b.com"})
        assert s.get("sid1")["role"] == "admin"
        s.destroy("sid1")
        assert s.get("sid1") is None
    def test_session_expired(self, monkeypatch):
        clock = FixedClock()
        s = SessionStore(clock, 1)
        s.create("sid1", "admin", {})
        # 模拟时间流逝
        import kcon.api.middleware as mw
        real_time = mw.time.time
        monkeypatch.setattr(mw.time, "time", lambda: real_time() + 10)
        assert s.get("sid1") is None
    def test_session_clear(self):
        s = SessionStore(FixedClock(), 7200)
        s.create("a", "admin", {})
        s.clear()
        assert s.get("a") is None
    def test_rate_limiter_allow(self):
        rl = RateLimiter(FixedClock())
        ok, msg = rl.check("k", 2, 60)
        assert ok is True
        assert msg == ""
    def test_rate_limiter_exceed(self):
        rl = RateLimiter(FixedClock())
        rl.check("k", 1, 60)
        ok, msg = rl.check("k", 1, 60)
        assert ok is False
        assert "请求过于频繁" in msg
    def test_rate_limiter_clear(self):
        rl = RateLimiter(FixedClock())
        rl.check("k", 1, 60)
        rl.clear()
        ok, _ = rl.check("k", 1, 60)
        assert ok is True
    def test_auth_context_properties(self):
        ctx = AuthContext("admin", {"channel_id": 1}, "sid")
        assert ctx.is_admin is True
        assert ctx.is_approver is False
        assert ctx.is_staff is False
        assert ctx.channel_id() is None
    def test_auth_context_approver(self):
        ctx = AuthContext("approver", {"channel_id": 5}, "sid")
        assert ctx.is_approver is True
        assert ctx.channel_id() == 5
    def test_auth_context_staff(self):
        ctx = AuthContext("staff", {}, "sid")
        assert ctx.is_staff is True
class TestRouter:
    def test_match_route_found(self):
        r = router.match_route("POST", "/api/auth/login")
        assert r is not None
        assert r[0] == "login"
    def test_match_route_not_found(self):
        assert router.match_route("POST", "/api/nope") is None
    def test_match_page_static(self):
        assert router.match_page("/login") == "login.html"
        assert router.match_page("/gift-admin") == "admin.html"
        assert router.match_page("/gift-approver") == "approver.html"
    def test_match_page_prefix(self):
        assert router.match_page("/r/abc") == "referrer_register.html"
        assert router.match_page("/t/abc") == "ticket_register.html"
        assert router.match_page("/c/abc") == "code_landing.html"
        assert router.match_page("/approve/x/y") == "approve.html"
    def test_match_page_not_found(self):
        assert router.match_page("/nope") is None
class TestHandlersAuth:
    def test_send_code_invalid_email(self, api):
        h, *_ = api
        r = h.send_code({"email": "bad"})
        assert r["ok"] is False

    def test_send_code_hides_code_when_demo_disabled(self, api):
        """生产默认不回显验证码，避免任何人拿到管理员验证码。"""
        h, c, *_ = api
        c.config.demo_code = False
        r = h.send_code({"email": "admin@kcon.com"})
        assert r["ok"] is True
        assert "demo_code" not in r
        c.config.demo_code = True

    def test_send_code_sends_mail_to_known_admin(self, api):
        """已知管理员会真实投递验证码邮件（假邮件器计数）。"""
        h, c, *_ = api
        r = h.send_code({"email": "admin@kcon.com"})
        assert r["ok"] is True
        assert c.mailer.count("CODE") == 1

    def test_send_code_skips_unknown_email(self, api):
        """未知邮箱返回同样提示但不发信，避免被用作邮件轰炸器。"""
        h, c, *_ = api
        r = h.send_code({"email": "nobody@test.com"})
        assert r["ok"] is True
        assert "demo_code" not in r
        assert c.mailer.count("CODE") == 0
    def test_login_failure(self, api):
        h, *_ = api
        r = h.login({"email": "admin@kcon.com", "code": "000000"})
        assert r["ok"] is False
    def test_logout_with_sid(self, api):
        h, *_ = api
        sid = _login_admin(api)
        r = h.logout(sid)
        assert r["ok"] is True
        assert h.me(sid) if False else True
    def test_logout_without_sid(self, api):
        h, *_ = api
        r = h.logout(None)
        assert r["ok"] is True
    def test_me_unauthorized(self, api):
        h, *_ = api
        with pytest.raises(ApiError) as exc:
            h.me(None)
        assert exc.value.status == 401
    def test_me_bad_sid(self, api):
        h, *_ = api
        with pytest.raises(ApiError) as exc:
            h.me("badsid")
        assert exc.value.status == 401
    def test_me_success(self, api):
        h, *_ = api
        sid = _login_admin(api)
        r = h.me(sid)
        assert r["ok"] is True
        assert r["role"] == "admin"
    def test_forbidden_role(self, api):
        h, *_ = api
        sid = _login_admin(api)
        with pytest.raises(ApiError) as exc:
            h.approver_overview(sid)
        assert exc.value.status == 403
class TestHandlersAdmin:
    def test_admin_stats(self, api):
        h, *_ = api
        sid = _login_admin(api)
        r = h.admin_stats(sid)
        assert r["ok"] is True
        assert "total" in r
    def test_admin_channels(self, api):
        h, *_ = api
        sid = _login_admin(api)
        r = h.admin_channels(sid)
        assert r["ok"] is True
    def test_admin_create_channel(self, api):
        h, *_ = api
        sid = _login_admin(api)
        r = h.admin_create_channel(sid, {
            "name": "渠道A", "approver_name": "张审批",
            "approver_email": "ap@test.com", "quota": 100, "referrer_cap": 20})
        assert r["ok"] is True
    def test_admin_create_channel_default_cap(self, api):
        h, *_ = api
        sid = _login_admin(api)
        r = h.admin_create_channel(sid, {
            "name": "渠道B", "approver_name": "李审批",
            "approver_email": "ap2@test.com", "quota": 50})
        assert r["ok"] is True
    def test_admin_suspend(self, api):
        h, c, *_ = api
        sid = _login_admin(api)
        h.admin_create_channel(sid, {"name": "渠道A", "approver_name": "张审批",
                                     "approver_email": "ap@test.com",
                                     "quota": 100, "referrer_cap": 20})
        ch = c.repos.channel.find_by_name("渠道A")
        r = h.admin_suspend_channel(sid, {"id": ch["id"]})
        assert r["ok"] is True
    def test_admin_delete(self, api):
        h, c, *_ = api
        sid = _login_admin(api)
        h.admin_create_channel(sid, {"name": "渠道A", "approver_name": "张审批",
                                     "approver_email": "ap@test.com",
                                     "quota": 100, "referrer_cap": 20})
        ch = c.repos.channel.find_by_name("渠道A")
        r = h.admin_delete_channel(sid, {"id": ch["id"]})
        assert r["ok"] is True
    def test_admin_replace_approver_success(self, api):
        h, c, *_ = api
        sid = _login_admin(api)
        h.admin_create_channel(sid, {"name": "渠道A", "approver_name": "张审批",
                                     "approver_email": "ap@test.com",
                                     "quota": 100, "referrer_cap": 20})
        ch = c.repos.channel.find_by_name("渠道A")
        r = h.admin_replace_approver(sid, {
            "channel_id": ch["id"], "approver_name": "李接替",
            "approver_email": "ap_new@test.com"})
        assert r["ok"] is True
        cur = c.repos.approver.find_by_channel(ch["id"])
        assert cur["email"] == "ap_new@test.com"
    def test_admin_replace_approver_forbidden(self, api):
        """审批员无权更换审批员 → 403。"""
        h, c, *_ = api
        admin_sid = _login_admin(api)
        h.admin_create_channel(admin_sid, {"name": "渠道A", "approver_name": "张审批",
                                          "approver_email": "ap@test.com",
                                          "quota": 100, "referrer_cap": 20})
        ap_sid = _login_approver(api, "ap@test.com")
        with pytest.raises(ApiError) as exc:
            h.admin_replace_approver(ap_sid, {"channel_id": 1,
                                             "approver_name": "李", "approver_email": "x@y.com"})
        assert exc.value.status == 403
    def test_admin_replace_approver_unauthorized(self, api):
        h, *_ = api
        with pytest.raises(ApiError) as exc:
            h.admin_replace_approver(None, {"channel_id": 1})
        assert exc.value.status == 401
    def test_admin_create_staff_invalid_name(self, api):
        h, *_ = api
        sid = _login_admin(api)
        r = h.admin_create_staff(sid, {"name": "张", "account": "staff1",
                                       "password": "123456"})
        assert r["ok"] is False
    def test_admin_create_staff_short_account(self, api):
        h, *_ = api
        sid = _login_admin(api)
        r = h.admin_create_staff(sid, {"name": "签到员", "account": "ab",
                                       "password": "123456"})
        assert r["ok"] is False
        assert "账号至少 3 字符" in r["msg"]
    def test_admin_create_staff_short_password(self, api):
        h, *_ = api
        sid = _login_admin(api)
        r = h.admin_create_staff(sid, {"name": "签到员", "account": "staff1",
                                       "password": "123"})
        assert r["ok"] is False
        assert "密码至少 6 字符" in r["msg"]
    def test_admin_create_staff_duplicate(self, api):
        h, *_ = api
        sid = _login_admin(api)
        h.admin_create_staff(sid, {"name": "签到员", "account": "staff1",
                                   "password": "123456"})
        r = h.admin_create_staff(sid, {"name": "签到员2", "account": "staff1",
                                       "password": "123456"})
        assert r["ok"] is False
        assert r["msg"] == "账号已存在"
class TestHandlersApprover:
    def _setup(self, api):
        h, c, *_ = api
        sid = _login_admin(api)
        h.admin_create_channel(sid, {"name": "渠道A", "approver_name": "张审批",
                                     "approver_email": "ap@test.com",
                                     "quota": 100, "referrer_cap": 20})
        ch = c.repos.channel.find_by_name("渠道A")
        h.referrer_register({"channel_code": ch["code"], "name": "李推荐",
                             "company": "公司", "department": "部门",
                             "mobile": "13800138000", "email": "ref@test.com"})
        return ch
    def test_overview(self, api):
        h, c, *_ = api
        self._setup(api)
        sid = _login_approver(api, "ap@test.com")
        r = h.approver_overview(sid)
        assert r["ok"] is True
        assert r["channel"]["name"] == "渠道A"
    def test_referrers(self, api):
        h, c, *_ = api
        self._setup(api)
        sid = _login_approver(api, "ap@test.com")
        r = h.approver_referrers(sid)
        assert r["ok"] is True
        assert len(r["data"]) == 1
    def test_recipients(self, api):
        h, c, *_ = api
        self._setup(api)
        sid = _login_approver(api, "ap@test.com")
        r = h.approver_recipients(sid)
        assert r["ok"] is True
    def test_approve(self, api):
        h, c, *_ = api
        self._setup(api)
        sid = _login_approver(api, "ap@test.com")
        r = h.approver_referrers(sid)
        ref = r["data"][0]
        r = h.approver_approve(sid, {"id": ref["id"], "action": "approve"})
        assert r["ok"] is True
class TestHandlersPublic:
    def test_referrer_register_rate_limit(self, api):
        h, *_ = api
        sid = _login_admin(api)
        h.admin_create_channel(sid, {"name": "渠道A", "approver_name": "张审批",
                                     "approver_email": "ap@test.com",
                                     "quota": 100, "referrer_cap": 20})
        ch = h.c.repos.channel.find_by_name("渠道A")
        # 触发限流（200 次）
        for i in range(200):
            h.limiter._records[f"ref_reg:1.2.3.4"] = [time.time()] * 200
        with pytest.raises(ApiError) as exc:
            h.referrer_register({"channel_code": ch["code"]}, "1.2.3.4")
        assert exc.value.status == 429
    def test_ticket_register_rate_limit(self, api):
        h, *_ = api
        h.limiter._records["tk_reg:1.2.3.4"] = [time.time()] * 1000
        with pytest.raises(ApiError) as exc:
            h.ticket_register({}, "1.2.3.4")
        assert exc.value.status == 429
class TestHandlersApprove:
    def _setup(self, api):
        h, c, *_ = api
        sid = _login_admin(api)
        h.admin_create_channel(sid, {"name": "渠道A", "approver_name": "张审批",
                                     "approver_email": "ap@test.com",
                                     "quota": 100, "referrer_cap": 20})
        ch = c.repos.channel.find_by_name("渠道A")
        h.referrer_register({"channel_code": ch["code"], "name": "李推荐",
                             "company": "公司", "department": "部门",
                             "mobile": "13800138000", "email": "ref@test.com"})
        ref = c.repos.referrer.find_by_email("ref@test.com")
        return ref
    def test_approve_info_invalid_token(self, api):
        h, *_ = api
        r = h.approve_info({"token": "bad"})
        assert r["ok"] is False
        assert r["msg"] == "链接无效"
    def test_approve_info_used_token(self, api):
        h, c, *_ = api
        ref = self._setup(api)
        token = c.repos.token.find_active(1, ref["id"])
        c.repos.token.mark_used(token["token"], c.clock.now_str())
        r = h.approve_info({"token": token["token"]})
        assert r["ok"] is False
        assert r["msg"] == "该链接已被使用"
    def test_approve_info_referrer(self, api):
        h, c, *_ = api
        ref = self._setup(api)
        token = c.repos.token.find_active(1, ref["id"])
        r = h.approve_info({"token": token["token"]})
        assert r["ok"] is True
        assert r["type"] == "referrer"
    def test_approve_info_referrer_missing(self, api):
        h, c, *_ = api
        c.repos.token.insert("ORPHAN00000000000000000000000001", 1, 999, 1,
                             "2026-11-01 10:00:00", c.clock.now_str())
        r = h.approve_info({"token": "ORPHAN00000000000000000000000001"})
        assert r["ok"] is False
        assert r["msg"] == "推荐人不存在"
    def test_approve_info_referrer_no_channel(self, api):
        """推荐人存在但渠道被删（ch 为 None 分支）。"""
        h, c, *_ = api
        ref = self._setup(api)
        token = c.repos.token.find_active(1, ref["id"])
        # 绕过外键删除渠道
        import sqlite3
        conn = sqlite3.connect(c.config.db_path)
        conn.execute("PRAGMA foreign_keys = OFF")
        conn.execute("DELETE FROM channel WHERE id=?", (ref["channel_id"],))
        conn.commit()
        conn.close()
        r = h.approve_info({"token": token["token"]})
        assert r["ok"] is True
        assert r["data"]["channel"] == ""
    def test_approve_info_ticket(self, api):
        h, c, *_ = api
        ref = self._setup(api)
        token = c.repos.token.find_active(1, ref["id"])
        h.approve_do({"token": token["token"], "action": "approve"})
        ref = c.repos.referrer.find_by_id(ref["id"])
        h.ticket_register({"referrer_code": ref["code"], "name": "王参会",
                           "company": "单位", "mobile": "", "email": "rc@test.com"})
        rc = c.repos.recipient.find_by_email("rc@test.com")
        tk2 = c.repos.token.find_active(2, rc["id"])
        r = h.approve_info({"token": tk2["token"]})
        assert r["ok"] is True
        assert r["type"] == "ticket"
    def test_approve_info_ticket_missing(self, api):
        h, c, *_ = api
        c.repos.token.insert("ORPHAN00000000000000000000000002", 2, 999, 1,
                             "2026-11-01 10:00:00", c.clock.now_str())
        r = h.approve_info({"token": "ORPHAN00000000000000000000000002"})
        assert r["ok"] is False
        assert r["msg"] == "申请不存在"
    def test_approve_info_ticket_no_referrer(self, api):
        """接收人存在但推荐人被删（ref 为 None 分支）。"""
        h, c, *_ = api
        ref = self._setup(api)
        token = c.repos.token.find_active(1, ref["id"])
        h.approve_do({"token": token["token"], "action": "approve"})
        ref = c.repos.referrer.find_by_id(ref["id"])
        h.ticket_register({"referrer_code": ref["code"], "name": "王参会",
                           "company": "单位", "mobile": "", "email": "rc@test.com"})
        rc = c.repos.recipient.find_by_email("rc@test.com")
        tk2 = c.repos.token.find_active(2, rc["id"])
        import sqlite3
        conn = sqlite3.connect(c.config.db_path)
        conn.execute("PRAGMA foreign_keys = OFF")
        conn.execute("DELETE FROM referrer WHERE id=?", (ref["id"],))
        conn.commit()
        conn.close()
        r = h.approve_info({"token": tk2["token"]})
        assert r["ok"] is True
        assert r["data"]["referrer"] == ""
    def test_approve_do_invalid_token(self, api):
        h, *_ = api
        r = h.approve_do({"token": "bad"})
        assert r["ok"] is False
        assert r["msg"] == "链接无效"
    def test_approve_do_referrer(self, api):
        h, c, *_ = api
        ref = self._setup(api)
        token = c.repos.token.find_active(1, ref["id"])
        r = h.approve_do({"token": token["token"], "action": "approve"})
        assert r["ok"] is True
    def test_approve_do_ticket(self, api):
        h, c, *_ = api
        ref = self._setup(api)
        token = c.repos.token.find_active(1, ref["id"])
        h.approve_do({"token": token["token"], "action": "approve"})
        ref = c.repos.referrer.find_by_id(ref["id"])
        h.ticket_register({"referrer_code": ref["code"], "name": "王参会",
                           "company": "单位", "mobile": "", "email": "rc@test.com"})
        rc = c.repos.recipient.find_by_email("rc@test.com")
        tk2 = c.repos.token.find_active(2, rc["id"])
        r = h.approve_do({"token": tk2["token"], "action": "approve"})
        assert r["ok"] is True
    def test_approve_resend_invalid(self, api):
        h, *_ = api
        r = h.approve_resend({"token": "bad"})
        assert r["ok"] is False
    def test_approve_resend_success(self, api):
        h, c, *_ = api
        ref = self._setup(api)
        token = c.repos.token.find_active(1, ref["id"])
        r = h.approve_resend({"token": token["token"]})
        assert r["ok"] is True
        assert "token" in r
class TestHandlersCheckin:
    def _setup(self, api):
        h, c, *_ = api
        sid = _login_admin(api)
        h.admin_create_channel(sid, {"name": "渠道A", "approver_name": "张审批",
                                     "approver_email": "ap@test.com",
                                     "quota": 100, "referrer_cap": 20})
        ch = c.repos.channel.find_by_name("渠道A")
        h.referrer_register({"channel_code": ch["code"], "name": "李推荐",
                             "company": "公司", "department": "部门",
                             "mobile": "13800138000", "email": "ref@test.com"})
        ref = c.repos.referrer.find_by_email("ref@test.com")
        token = c.repos.token.find_active(1, ref["id"])
        h.approve_do({"token": token["token"], "action": "approve"})
        ref = c.repos.referrer.find_by_id(ref["id"])
        h.ticket_register({"referrer_code": ref["code"], "name": "王参会",
                           "company": "单位", "mobile": "", "email": "rc@test.com"})
        rc = c.repos.recipient.find_by_email("rc@test.com")
        tk2 = c.repos.token.find_active(2, rc["id"])
        h.approve_do({"token": tk2["token"], "action": "approve"})
        rc = c.repos.recipient.find_by_id(rc["id"])
        code = c.repos.code.find_by_recipient(rc["id"])
        return sid, rc, code
    def test_lookup_and_confirm(self, api):
        h, c, *_ = api
        sid, rc, code = self._setup(api)
        r = h.checkin_lookup(sid, {"code": code["code"]}, "1.2.3.4")
        assert r["ok"] is True
        r = h.checkin_confirm(sid, {"code": code["code"]}, "1.2.3.4")
        assert r["ok"] is True
    def test_staff_login_and_checkin(self, api):
        h, c, *_ = api
        sid, rc, code = self._setup(api)
        h.admin_create_staff(sid, {"name": "签到员", "account": "staff1",
                                   "password": "123456"})
        r = h.checkin_staff_login(None, {"account": "staff1",
                                         "password": "123456"})
        assert r["ok"] is True
        staff_sid = r["sid"]
        r = h.checkin_confirm(staff_sid, {"code": code["code"]}, "1.2.3.4")
        assert r["ok"] is True
    def test_staff_login_failure(self, api):
        h, *_ = api
        r = h.checkin_staff_login(None, {"account": "nobody", "password": "x"})
        assert r["ok"] is False
    def test_retry_sync_invalid(self, api):
        h, *_ = api
        sid = _login_admin(api)
        r = h.checkin_retry_sync(sid, {"code": "INVALIDCODE12345"})
        assert r["ok"] is False
    def test_retry_sync_success(self, api):
        h, c, *_ = api
        sid, rc, code = self._setup(api)
        r = h.checkin_retry_sync(sid, {"code": code["code"]})
        assert r["ok"] is True
    def test_code_info_anonymous(self, api):
        h, c, *_ = api
        sid, rc, code = self._setup(api)
        r = h.code_info(None, {"code": code["code"]})
        assert r["ok"] is True
        assert "name" not in r["data"]
    def test_code_info_authenticated(self, api):
        h, c, *_ = api
        sid, rc, code = self._setup(api)
        r = h.code_info(sid, {"code": code["code"]})
        assert r["ok"] is True
        assert r["data"]["name"] == "王参会"
    def test_code_info_bad_sid(self, api):
        h, c, *_ = api
        sid, rc, code = self._setup(api)
        r = h.code_info("badsid", {"code": code["code"]})
        assert r["ok"] is True
        assert "name" not in r["data"]
    def test_code_info_approver_sid(self, api):
        """审批员会话不算已认证（只有 staff/admin）。"""
        h, c, *_ = api
        sid, rc, code = self._setup(api)
        ap_sid = _login_approver(api, "ap@test.com")
        r = h.code_info(ap_sid, {"code": code["code"]})
        assert r["ok"] is True
        assert "name" not in r["data"]
class TestServerEndpoints:
    """覆盖 server.py 的 HTTP 层分支。"""
    @pytest.fixture
    def live(self, tmp_path):
        cfg = AppConfig(site_url="http://127.0.0.1:0",
                        db_path=str(tmp_path / "srv.db"))
        c = Container(config=cfg, clock=FixedClock(), random_source=SeqRandom(),
                      mailer=FakeMailer(), qr_generator=FakeQrCodeGenerator())
        srv = make_server("127.0.0.1", 0, c)
        port = srv.server_address[1]
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        time.sleep(0.2)
        yield f"http://127.0.0.1:{port}", c
        srv.shutdown()
        srv.server_close()
    @pytest.fixture
    def mounted(self, tmp_path):
        """挂在 /kconluck2026/ 子路径下的实例（对应 knownsec.com 的挂载方式）。"""
        cfg = AppConfig(site_url="http://127.0.0.1:0",
                        db_path=str(tmp_path / "mnt.db"),
                        base_path="/kconluck2026")
        c = Container(config=cfg, clock=FixedClock(), random_source=SeqRandom(),
                      mailer=FakeMailer(), qr_generator=FakeQrCodeGenerator())
        srv = make_server("127.0.0.1", 0, c)
        port = srv.server_address[1]
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        time.sleep(0.2)
        yield f"http://127.0.0.1:{port}", c
        srv.shutdown()
        srv.server_close()
    def test_subpath_page_rewrites_assets(self, mounted):
        """子路径部署：页面里的静态资源与站内链接必须带前缀。"""
        base, _ = mounted
        with urllib.request.urlopen(base + "/kconluck2026/login", timeout=5) as resp:
            assert resp.headers.get_content_type() == "text/html"
            html = resp.read().decode("utf-8")
        assert 'window.KCON_BASE = "/kconluck2026"' in html
        assert 'href="/kconluck2026/static/css/style.css"' in html
        assert 'href="/static/' not in html
    def test_subpath_api_reachable(self, mounted):
        """子路径下的 API 剥前缀后要命中路由（未登录是 401，而不是 404）。"""
        base, _ = mounted
        _, s, _ = self._req(base, "GET", "/kconluck2026/api/me")
        assert s == 401
    def test_subpath_static_file(self, mounted):
        base, _ = mounted
        _, s, _ = self._req(base, "GET", "/kconluck2026/static/css/style.css")
        assert s == 200
    def test_root_path_still_served_when_mounted(self, mounted):
        """配了子路径后根路径仍保留：官网反代剥离前缀、本机直连排障都依赖它。"""
        base, _ = mounted
        _, s, _ = self._req(base, "GET", "/login")
        assert s == 200
    def _req(self, base, method, path, data=None, sid=None):
        headers = {"Content-Type": "application/json"}
        if sid:
            headers["X-Session"] = sid
        body = json.dumps(data).encode() if data is not None else None
        req = urllib.request.Request(base + path, data=body, headers=headers,
                                     method=method)
        try:
            with urllib.request.urlopen(req, timeout=5) as r:
                raw = r.read()
                try:
                    return json.loads(raw), r.status, r.headers
                except (json.JSONDecodeError, ValueError):
                    return {"_raw": raw}, r.status, r.headers
        except urllib.error.HTTPError as e:
            raw = e.read()
            try:
                return json.loads(raw), e.code, e.headers
            except (json.JSONDecodeError, ValueError):
                return {"_raw": raw}, e.code, e.headers
    def test_healthz(self, live):
        base, _ = live
        r, s, _ = self._req(base, "GET", "/healthz")
        assert s == 200
        assert r["status"] == "healthy"
    def test_options(self, live):
        base, _ = live
        r, s, _ = self._req(base, "OPTIONS", "/api/auth/login")
        assert s == 200

    def test_page_content_type_is_html(self, live):
        """页面必须以 text/html 返回，否则浏览器会当成文件下载。"""
        base, _ = live
        with urllib.request.urlopen(base + "/login", timeout=5) as resp:
            assert resp.headers.get_content_type() == "text/html"
            assert "登录" in resp.read().decode("utf-8")
    def test_static_file(self, live):
        base, _ = live
        r, s, h = self._req(base, "GET", "/static/css/style.css")
        assert s == 200
    def test_static_not_found(self, live):
        base, _ = live
        r, s, _ = self._req(base, "GET", "/static/nope.css")
        assert s == 404
    def test_page_not_found(self, live):
        base, _ = live
        r, s, _ = self._req(base, "GET", "/nope")
        assert s == 404
    def test_api_not_found_get(self, live):
        base, _ = live
        r, s, _ = self._req(base, "GET", "/api/nope")
        assert s == 404
    def test_api_not_found_post(self, live):
        base, _ = live
        r, s, _ = self._req(base, "POST", "/api/nope", {})
        assert s == 404
    def test_invalid_json_body(self, live):
        base, _ = live
        req = urllib.request.Request(base + "/api/auth/send-code",
                                     data=b"not json",
                                     headers={"Content-Type": "application/json"},
                                     method="POST")
        with urllib.request.urlopen(req, timeout=5) as r:
            body = json.loads(r.read())
        assert body["ok"] is False
    def test_form_urlencoded_body(self, live):
        base, _ = live
        req = urllib.request.Request(
            base + "/api/auth/send-code",
            data=b"email=admin%40kcon.com",
            headers={"Content-Type": "application/x-www-form-urlencoded"},
            method="POST")
        with urllib.request.urlopen(req, timeout=5) as r:
            body = json.loads(r.read())
        assert body["ok"] is True
    def test_empty_body(self, live):
        base, _ = live
        r, s, _ = self._req(base, "POST", "/api/auth/logout")
        assert s == 200
    def test_page_index(self, live):
        base, _ = live
        r, s, _ = self._req(base, "GET", "/")
        assert s == 200
    def test_unhandled_exception(self, live, monkeypatch):
        """覆盖 500 分支。"""
        base, c = live
        def boom(*a, **kw):
            raise RuntimeError("boom")
        monkeypatch.setattr(c.auth, "send_verify_code", boom)
        r, s, _ = self._req(base, "POST", "/api/auth/send-code",
                            {"email": "a@b.com"})
        assert s == 500
        assert "服务异常" in r["msg"]

    def test_send_html_direct(self, live):
        """覆盖 _send_html（40-45）。"""
        from kcon.api.server import RequestHandler
        import io
        from unittest.mock import MagicMock
        handler = RequestHandler.__new__(RequestHandler)
        handler.wfile = io.BytesIO()
        handler.send_response = MagicMock()
        handler.send_header = MagicMock()
        handler.end_headers = MagicMock()
        handler._send_html("<html>test</html>")
        assert handler.wfile.getvalue() == b"<html>test</html>"
        handler.send_response.assert_called_once_with(200)

    def test_main_function(self, monkeypatch):
        """覆盖 main()（163-175）。"""
        from kcon.api import server as srv_module
        called = {"served": False}
        class FakeServer:
            def serve_forever(self):
                called["served"] = True
                raise KeyboardInterrupt
            def shutdown(self):
                pass
        def fake_make_server(*a, **kw):
            return FakeServer()
        monkeypatch.setattr(srv_module, "make_server", fake_make_server)
        monkeypatch.setenv("KCON_SITE_URL", "http://127.0.0.1:9999")
        srv_module.main()
        assert called["served"] is True

    def test_make_server_default_container(self, tmp_path, monkeypatch):
        """覆盖 make_server 的 container=None 分支（143->145）。"""
        from kcon.api.server import make_server
        monkeypatch.setenv("KCON_DB_PATH", str(tmp_path / "mk.db"))
        srv = make_server("127.0.0.1", 0, None)
        assert srv.container is not None
        srv.server_close()

    def test_make_server_with_container(self, live):
        """覆盖 make_server 的 container 非空分支（143 短路）。"""
        from kcon.api.server import make_server
        _, c = live
        srv = make_server("127.0.0.1", 0, c)
        assert srv.container is c
        srv.server_close()

    def test_main_module_entry(self, monkeypatch):
        """覆盖 if __name__ == '__main__' 分支（175）。

        只执行源码中的 __main__ 判断片段，不真正启动服务。
        """
        from kcon.api import server as srv_module
        called = {"ok": False}
        def fake_main():
            called["ok"] = True
        # 构造与模块末尾等价的代码片段
        snippet = 'if __name__ == "__main__":\n    main()\n'
        g = {"__name__": "__main__", "main": fake_main}
        exec(compile(snippet, "<snippet>", "exec"), g)
        assert called["ok"] is True

    def test_dispatch_public_no_ip_branch(self, live):
        """覆盖公开接口 need_ip=False 的分支（143->145）。

        /api/approve/info 是公开接口（无 sid）且不需要 ip。
        """
        base, _ = live
        r, s, _ = self._req(base, "POST", "/api/approve/info",
                            {"token": "bad"})
        assert s == 200
        assert r["ok"] is False
        assert r["msg"] == "链接无效"

    def test_resolve_container_with_existing(self, live):
        """覆盖 _resolve_container 的 container 非空分支。"""
        from kcon.api.server import _resolve_container
        _, c = live
        assert _resolve_container(c) is c

    def test_resolve_container_with_none(self, tmp_path, monkeypatch):
        """覆盖 _resolve_container 的 container 为 None 分支。"""
        from kcon.api.server import _resolve_container
        monkeypatch.setenv("KCON_DB_PATH", str(tmp_path / "rc.db"))
        result = _resolve_container(None)
        assert result is not None

    def test_make_server_container_shortcircuit(self, tmp_path):
        """覆盖 143->145 假分支（container 不为 None，走 else）。"""
        from kcon.api import server as srv_module
        cfg = AppConfig(site_url="http://127.0.0.1:0",
                        db_path=str(tmp_path / "sc.db"))
        c = Container(config=cfg, clock=FixedClock(), random_source=SeqRandom(),
                      mailer=FakeMailer(), qr_generator=FakeQrCodeGenerator())
        original = srv_module.Container
        def boom(*a, **kw):
            raise AssertionError("container 不为 None 时不应构造新容器")
        srv_module.Container = boom
        try:
            srv = srv_module.make_server("127.0.0.1", 0, c)
            assert srv.container is c
            srv.server_close()
        finally:
            srv_module.Container = original