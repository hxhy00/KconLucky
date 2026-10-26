"""系统测试：15 个端到端场景 + 20 渠道 500 票压测。"""
import time
import pytest
from ..conftest import ApiClient
pytestmark = pytest.mark.system
def create_channel(client, name="测试渠道", email="ap@test.com",
                   quota=100, cap=20):
    """创建渠道并返回渠道信息。"""
    r, _ = client.post("/api/admin/channels", {
        "name": name, "approver_name": "张审批",
        "approver_email": email, "quota": quota, "referrer_cap": cap,
    })
    assert r["ok"], r["msg"]
    r, _ = client.get("/api/admin/channels")
    return next(c for c in r["data"] if c["name"] == name)
def register_referrer(client, channel_code, email="ref@test.com", name="李推荐"):
    """注册推荐人。"""
    r, _ = client.post("/api/referrer/register", {
        "channel_code": channel_code, "name": name, "company": "测试公司",
        "department": "安全部", "mobile": "13800138000", "email": email,
    })
    return r
def register_recipient(client, referrer_code, email="rc@test.com", name="王参会"):
    """注册接收人。"""
    r, _ = client.post("/api/ticket/register", {
        "referrer_code": referrer_code, "name": name, "company": "参会单位",
        "mobile": "13900139000", "email": email,
    })
    return r
class TestS1FullFlow:
    """S1：完整主流程。"""
    def test_full_flow(self, live_server):
        c = ApiClient(live_server["base"])
        container = live_server["container"]
        # 1. 管理员登录
        r = c.login("admin@kcon.com")
        assert r["ok"] and r["role"] == "admin"
        # 2. 建渠道
        ch = create_channel(c)
        assert ch["quota"] == 100
        # 3. 推荐人注册
        r = register_referrer(c, ch["code"])
        assert r["ok"], r["msg"]
        # 4. 审批员登录并审批
        ap = ApiClient(live_server["base"])
        r = ap.login("ap@test.com")
        assert r["ok"] and r["role"] == "approver"
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        r, _ = ap.post("/api/approver/referrer/approve",
                       {"id": ref["id"], "action": "approve"})
        assert r["ok"], r["msg"]
        # 5. 获取推荐人码
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        assert ref["status"] == 2
        # 6. 接收人注册
        r = register_recipient(c, ref["code"])
        assert r["ok"], r["msg"]
        # 7. 推荐人审批赠票（用令牌）
        rc = container.repos.recipient.find_by_email("rc@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        r, _ = c.post("/api/approve/info", {"token": token["token"]})
        assert r["ok"] and r["type"] == "ticket"
        r, _ = c.post("/api/approve/do",
                      {"token": token["token"], "action": "approve"})
        assert r["ok"], r["msg"]
        # 8. 注册码已生成（v1 上游不做签到，现场签到由下游 KconLucky 负责）
        code = container.repos.code.find_by_recipient(rc["id"])
        assert code and code["code"]
        # 9. 上游签到页不对外暴露
        r, status = c.request("GET", "/checkin")
        assert status == 404
        # 10. 统计：1 人已审批、0 人签到
        r, _ = c.get("/api/admin/stats")
        assert r["ok"]
        assert r["total"]["approved_count"] == 1
        assert r["total"]["checked_in_count"] == 0
        assert r["total"]["checkin_rate"] == 0.0
class TestS2AdminLimit:
    """S2：管理员数量上限。"""
    def test_admin_limit(self, live_server):
        c = ApiClient(live_server["base"])
        # 3 个管理员白名单都可登录
        for email in ("admin@kcon.com", "boss@kcon.com", "ops@kcon.com"):
            r = c.login(email)
            assert r["ok"]
        # 非白名单不可登录
        r = c.login("nobody@test.com")
        assert not r["ok"]
class TestS3ChannelLimit:
    """S3：渠道数量上限 20。"""
    def test_channel_limit(self, admin_client):
        for i in range(20):
            r, _ = admin_client.post("/api/admin/channels", {
                "name": f"渠道{i}", "approver_name": f"审批{i}",
                "approver_email": f"ap{i}@test.com", "quota": 20,
                "referrer_cap": 5,
            })
            assert r["ok"], r["msg"]
        r, _ = admin_client.post("/api/admin/channels", {
            "name": "渠道X", "approver_name": "审批X",
            "approver_email": "apx@test.com", "quota": 10, "referrer_cap": 5,
        })
        assert not r["ok"]
        assert "渠道数量已达上限" in r["msg"]
class TestS4ReferrerCap:
    """S4：单人上限拦截。"""
    def test_referrer_cap(self, live_server):
        c = ApiClient(live_server["base"])
        c.login("admin@kcon.com")
        ch = create_channel(c, quota=100, cap=1)
        register_referrer(c, ch["code"])
        ap = ApiClient(live_server["base"])
        ap.login("ap@test.com")
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        ap.post("/api/approver/referrer/approve", {"id": ref["id"],
                                                   "action": "approve"})
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        # 第 1 张成功
        r = register_recipient(c, ref["code"], email="r1@test.com")
        assert r["ok"]
        container = live_server["container"]
        rc = container.repos.recipient.find_by_email("r1@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        c.post("/api/approve/do", {"token": token["token"], "action": "approve"})
        # 第 2 张被拦截
        r = register_recipient(c, ref["code"], email="r2@test.com")
        assert not r["ok"]
        assert "赠票已发完" in r["msg"]
class TestS5ChannelQuota:
    """S5：渠道配额拦截。"""
    def test_channel_quota(self, live_server):
        c = ApiClient(live_server["base"])
        c.login("admin@kcon.com")
        ch = create_channel(c, quota=1, cap=20)
        register_referrer(c, ch["code"])
        ap = ApiClient(live_server["base"])
        ap.login("ap@test.com")
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        ap.post("/api/approver/referrer/approve", {"id": ref["id"],
                                                   "action": "approve"})
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        r = register_recipient(c, ref["code"], email="r1@test.com")
        assert r["ok"]
        container = live_server["container"]
        rc = container.repos.recipient.find_by_email("r1@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        c.post("/api/approve/do", {"token": token["token"], "action": "approve"})
        r = register_recipient(c, ref["code"], email="r2@test.com")
        assert not r["ok"]
        assert "本渠道赠票已发完" in r["msg"]
class TestS6EmailDuplicate:
    """S6：邮箱重复提交时拦截。"""
    def test_email_duplicate(self, live_server):
        c = ApiClient(live_server["base"])
        c.login("admin@kcon.com")
        ch = create_channel(c)
        register_referrer(c, ch["code"])
        ap = ApiClient(live_server["base"])
        ap.login("ap@test.com")
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        ap.post("/api/approver/referrer/approve", {"id": ref["id"],
                                                   "action": "approve"})
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        r = register_recipient(c, ref["code"], email="dup@test.com")
        assert r["ok"]
        r = register_recipient(c, ref["code"], email="dup@test.com")
        assert not r["ok"]
        assert "您已为该邮箱提交过申请" in r["msg"]
class TestS7TokenReuse:
    """S7：一次性链接复用。"""
    def test_token_reuse(self, live_server):
        c = ApiClient(live_server["base"])
        c.login("admin@kcon.com")
        ch = create_channel(c)
        register_referrer(c, ch["code"])
        container = live_server["container"]
        ref = container.repos.referrer.find_by_email("ref@test.com")
        token = container.repos.token.find_active(1, ref["id"])
        r, _ = c.post("/api/approve/do",
                      {"token": token["token"], "action": "approve"})
        assert r["ok"]
        r, _ = c.post("/api/approve/do",
                      {"token": token["token"], "action": "approve"})
        assert not r["ok"]
        assert r["msg"] == "该链接已被使用"
class TestS8RejectRetry:
    """S8：被拒 7 天重申。"""
    def test_reject_retry(self, live_server):
        c = ApiClient(live_server["base"])
        c.login("admin@kcon.com")
        ch = create_channel(c)
        register_referrer(c, ch["code"])
        container = live_server["container"]
        ref = container.repos.referrer.find_by_email("ref@test.com")
        token = container.repos.token.find_active(1, ref["id"])
        c.post("/api/approve/do", {"token": token["token"], "action": "reject"})
        # 7 天内重申失败
        r = register_referrer(c, ch["code"])
        assert not r["ok"]
        assert "已被拒绝" in r["msg"]
        # 7 天后重申成功
        container.clock.advance(8 * 24 * 3600)
        r = register_referrer(c, ch["code"])
        assert r["ok"], r["msg"]
class TestS9DuplicateCheckin:
    """S9：重复签到。"""
    def test_duplicate_checkin(self, live_server):
        c = ApiClient(live_server["base"])
        c.login("admin@kcon.com")
        ch = create_channel(c)
        register_referrer(c, ch["code"])
        ap = ApiClient(live_server["base"])
        ap.login("ap@test.com")
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        ap.post("/api/approver/referrer/approve", {"id": ref["id"],
                                                   "action": "approve"})
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        register_recipient(c, ref["code"])
        container = live_server["container"]
        rc = container.repos.recipient.find_by_email("rc@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        c.post("/api/approve/do", {"token": token["token"], "action": "approve"})
        code = container.repos.code.find_by_recipient(rc["id"])
        # 管理员代签到
        r, _ = c.post("/api/checkin/confirm", {"code": code["code"]})
        assert r["ok"]
        r, _ = c.post("/api/checkin/confirm", {"code": code["code"]})
        assert not r["ok"]
        assert "完成签到" in r["msg"]
class TestS10CrossChannel:
    """S10：跨渠道越权。"""
    def test_cross_channel(self, live_server):
        c = ApiClient(live_server["base"])
        c.login("admin@kcon.com")
        create_channel(c, name="渠道A", email="ap1@test.com")
        create_channel(c, name="渠道B", email="ap2@test.com")
        r, _ = c.get("/api/admin/channels")
        ch_b = next(x for x in r["data"] if x["name"] == "渠道B")
        register_referrer(c, ch_b["code"], email="refb@test.com")
        # 审批员 A 尝试审批渠道 B 的推荐人
        ap1 = ApiClient(live_server["base"])
        ap1.login("ap1@test.com")
        container = live_server["container"]
        ref = container.repos.referrer.find_by_email("refb@test.com")
        r, _ = ap1.post("/api/approver/referrer/approve",
                        {"id": ref["id"], "action": "approve"})
        assert not r["ok"]
        assert r["msg"] == "无权操作该推荐人"
class TestS11Unauthorized:
    """S11：未登录访问。"""
    def test_unauthorized(self, client):
        r, status = client.get("/api/admin/stats")
        assert status == 401
        assert not r["ok"]
        r, status = client.get("/api/approver/overview")
        assert status == 401
class TestS12LandingPageTier:
    """S12：落地页权限分级。"""
    def test_landing_page_tier(self, live_server):
        c = ApiClient(live_server["base"])
        c.login("admin@kcon.com")
        ch = create_channel(c)
        register_referrer(c, ch["code"])
        ap = ApiClient(live_server["base"])
        ap.login("ap@test.com")
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        ap.post("/api/approver/referrer/approve", {"id": ref["id"],
                                                   "action": "approve"})
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        register_recipient(c, ref["code"])
        container = live_server["container"]
        rc = container.repos.recipient.find_by_email("rc@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        c.post("/api/approve/do", {"token": token["token"], "action": "approve"})
        code = container.repos.code.find_by_recipient(rc["id"])
        # 未登录：无个人信息
        anon = ApiClient(live_server["base"])
        r, _ = anon.post("/api/code/info", {"code": code["code"]})
        assert r["ok"]
        assert "name" not in r["data"]
        assert "company" not in r["data"]
        # 已登录管理员：有个人信息
        r, _ = c.post("/api/code/info", {"code": code["code"]})
        assert r["ok"]
        assert r["data"]["name"] == "王参会"
class TestS13M08Batch:
    """S13：M-08 满 10 人汇总。"""
    def test_m08_batch(self, live_server):
        c = ApiClient(live_server["base"])
        c.login("admin@kcon.com")
        ch = create_channel(c, quota=100, cap=20)
        container = live_server["container"]
        mailer = container.mailer
        # 造 10 个签到
        for i in range(10):
            register_referrer(c, ch["code"], email=f"ref{i}@test.com",
                              name=f"推荐{i}")
            ref = container.repos.referrer.find_by_email(f"ref{i}@test.com")
            token = container.repos.token.find_active(1, ref["id"])
            c.post("/api/approve/do", {"token": token["token"],
                                       "action": "approve"})
            ref = container.repos.referrer.find_by_id(ref["id"])
            register_recipient(c, ref["code"], email=f"rc{i}@test.com",
                               name=f"参会{i}")
            rc = container.repos.recipient.find_by_email(f"rc{i}@test.com")
            token2 = container.repos.token.find_active(2, rc["id"])
            c.post("/api/approve/do", {"token": token2["token"],
                                       "action": "approve"})
            rc = container.repos.recipient.find_by_id(rc["id"])
            code = container.repos.code.find_by_recipient(rc["id"])
            c.post("/api/checkin/confirm", {"code": code["code"]})
        # 第 10 个触发 M-08
        assert mailer.count("M-08") == 1
class TestS14ExternalFailure:
    """S14：外部接口失败不阻断。"""
    def test_external_failure(self, live_server):
        from kcon.infra.external_checkin import FailingExternalCheckin
        container = live_server["container"]
        container.checkin.external = FailingExternalCheckin()
        c = ApiClient(live_server["base"])
        c.login("admin@kcon.com")
        ch = create_channel(c)
        register_referrer(c, ch["code"])
        ap = ApiClient(live_server["base"])
        ap.login("ap@test.com")
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        ap.post("/api/approver/referrer/approve", {"id": ref["id"],
                                                   "action": "approve"})
        r, _ = ap.get("/api/approver/referrers")
        ref = r["data"][0]
        register_recipient(c, ref["code"])
        rc = container.repos.recipient.find_by_email("rc@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        c.post("/api/approve/do", {"token": token["token"], "action": "approve"})
        code = container.repos.code.find_by_recipient(rc["id"])
        # 签到仍成功
        r, _ = c.post("/api/checkin/confirm", {"code": code["code"]})
        assert r["ok"]
        checkin = container.repos.checkin.find_by_recipient(rc["id"])
        assert checkin["external_sync_status"] == 2
class TestS15FullScale:
    """S15：20 渠道 500 票全流程压测。"""
    def test_full_scale(self, live_server):
        start = time.time()
        c = ApiClient(live_server["base"])
        c.login("admin@kcon.com")
        container = live_server["container"]
        total_tickets = 0
        total_checkins = 0
        # 20 渠道 × 25 票 = 500 票
        for i in range(20):
            r, _ = c.post("/api/admin/channels", {
                "name": f"压测渠道{i}", "approver_name": f"审批{i}",
                "approver_email": f"scale{i}@test.com", "quota": 25,
                "referrer_cap": 5,
            })
            assert r["ok"], r["msg"]
            ch = container.repos.channel.find_by_name(f"压测渠道{i}")
            # 每渠道 5 推荐人 × 5 票
            for j in range(5):
                register_referrer(c, ch["code"],
                                  email=f"sr{i}_{j}@test.com",
                                  name=f"推荐{i}{j}")
                ref = container.repos.referrer.find_by_email(f"sr{i}_{j}@test.com")
                token = container.repos.token.find_active(1, ref["id"])
                c.post("/api/approve/do", {"token": token["token"],
                                           "action": "approve"})
                ref = container.repos.referrer.find_by_id(ref["id"])
                for k in range(5):
                    r = register_recipient(c, ref["code"],
                                           email=f"sc{i}_{j}_{k}@test.com",
                                           name=f"参会{i}{j}{k}")
                    assert r["ok"], r["msg"]
                    rc = container.repos.recipient.find_by_email(
                        f"sc{i}_{j}_{k}@test.com")
                    token2 = container.repos.token.find_active(2, rc["id"])
                    c.post("/api/approve/do", {"token": token2["token"],
                                               "action": "approve"})
                    total_tickets += 1
                    rc = container.repos.recipient.find_by_id(rc["id"])
                    code = container.repos.code.find_by_recipient(rc["id"])
                    # 每推荐人前 2 张签到（5 推荐人 × 2 = 10，20 渠道 = 200）
                    # 加上每渠道第 1 个推荐人的第 3 张（+20）= 220，再补 30 张
                    # 简化：k < 2 签到（20 × 5 × 2 = 200），再对 i < 10 且 k == 2 签到（+50）= 250
                    should_checkin = (k < 2) or (k == 2 and i < 10)
                    if should_checkin:
                        r, _ = c.post("/api/checkin/confirm",
                                      {"code": code["code"]})
                        assert r["ok"], r["msg"]
                        total_checkins += 1
        elapsed = time.time() - start
        # 断言
        r, _ = c.get("/api/admin/stats")
        assert r["ok"]
        assert r["total"]["channel_count"] == 20
        assert r["total"]["approved_count"] == 500
        assert r["total"]["checked_in_count"] == 250
        assert total_tickets == 500
        assert total_checkins == 250
        # 性能断言：500 票全流程 < 60 秒
        assert elapsed < 60, f"耗时 {elapsed:.1f}s 超过 60s"
        print(f"\n[压测结果] 20 渠道 500 票 250 签到，耗时 {elapsed:.2f}s")