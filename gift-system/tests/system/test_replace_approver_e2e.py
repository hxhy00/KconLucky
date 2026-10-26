"""系统测试：换审批员端到端（TC-S-016）。"""
import pytest
from ..conftest import ApiClient
pytestmark = pytest.mark.system
class TestS16ReplaceApproverE2E:
    """S16：更换审批员后，旧令牌失效、新申请发给新审批员。"""
    def test_replace_approver_e2e(self, live_server):
        c = ApiClient(live_server["base"])
        container = live_server["container"]
        # 1. 管理员建渠道
        r = c.login("admin@kcon.com")
        assert r["ok"]
        r, _ = c.post("/api/admin/channels", {
            "name": "换人渠道", "approver_name": "张原审",
            "approver_email": "ap_old@test.com", "quota": 100, "referrer_cap": 20})
        assert r["ok"], r["msg"]
        ch = container.repos.channel.find_by_name("换人渠道")
        # 2. 推荐人注册（产生一个待审批令牌）
        r, _ = c.post("/api/referrer/register", {
            "channel_code": ch["code"], "name": "王推荐", "company": "公司",
            "department": "部门", "mobile": "13800000001", "email": "ref_x@test.com"})
        assert r["ok"], r["msg"]
        ref = container.repos.referrer.find_by_email("ref_x@test.com")
        tk = container.repos.token.find_active(1, ref["id"])
        assert tk is not None
        old_token = tk["token"]
        # 3. 管理员换审批员
        r, _ = c.post("/api/admin/approver/replace", {
            "channel_id": ch["id"], "approver_name": "李接替",
            "approver_email": "ap_new@test.com"})
        assert r["ok"], r["msg"]
        assert "作废旧令牌" in r["msg"]
        # 4. 旧审批员的令牌已失效，用旧链接审批失败
        r, _ = c.post("/api/approve/do", {"token": old_token, "action": "approve"})
        assert r["ok"] is False
        assert r["msg"] == "该链接已被使用"
        # 5. 新审批员登录，看到待审批列表并重新审批成功
        new_ap = ApiClient(live_server["base"])
        r = new_ap.login("ap_new@test.com")
        assert r["ok"] and r["role"] == "approver"
        r, _ = new_ap.get("/api/approver/referrers")
        pend = [x for x in r["data"] if x["status"] == 1]
        assert len(pend) == 1
        r, _ = new_ap.post("/api/approver/referrer/approve",
                           {"id": pend[0]["id"], "action": "approve"})
        assert r["ok"], r["msg"]
        # 6. 验证邮件收件人已改道到新审批员
        mails = [m for m in container.mailer.sent if m["template"] == "M-02"]
        assert mails[-1]["to"] == "ap_new@test.com"