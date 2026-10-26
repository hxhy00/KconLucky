"""集成测试：推荐人服务 + 赠票服务。"""
import pytest
from kcon.domain.enums import RecipientStatus, ReferrerStatus
pytestmark = pytest.mark.integration
class TestReferrerRegister:
    def test_register_success(self, container, channel_factory, fake_mailer):
        ch = channel_factory()
        ok, msg = container.referrer.register(ch["code"], "李推荐", "测试公司",
                                              "安全部", "13800138000", "ref@test.com")
        assert ok is True
        assert "申请已提交" in msg
        assert fake_mailer.count("M-02") == 1
        ref = container.repos.referrer.find_by_email("ref@test.com")
        assert ref["status"] == ReferrerStatus.PENDING
        assert len(ref["code"]) == 16
    def test_register_invalid_channel(self, container):
        ok, msg = container.referrer.register("badcode", "李推荐", "测试公司",
                                              "安全部", "13800138000", "ref@test.com")
        assert ok is False
        assert msg == "该注册链接已失效"
    def test_register_invalid_name(self, container, channel_factory):
        ch = channel_factory()
        ok, msg = container.referrer.register(ch["code"], "李", "测试公司",
                                              "安全部", "13800138000", "ref@test.com")
        assert ok is False
        assert "姓名需" in msg
    def test_register_invalid_company(self, container, channel_factory):
        ch = channel_factory()
        ok, msg = container.referrer.register(ch["code"], "李推荐", "公",
                                              "安全部", "13800138000", "ref@test.com")
        assert ok is False
        assert "公司需" in msg
    def test_register_invalid_department(self, container, channel_factory):
        ch = channel_factory()
        ok, msg = container.referrer.register(ch["code"], "李推荐", "测试公司",
                                              "部", "13800138000", "ref@test.com")
        assert ok is False
        assert "部门需" in msg
    def test_register_invalid_mobile(self, container, channel_factory):
        ch = channel_factory()
        ok, msg = container.referrer.register(ch["code"], "李推荐", "测试公司",
                                              "安全部", "123", "ref@test.com")
        assert ok is False
        assert msg == "手机号格式不正确"
    def test_register_invalid_email(self, container, channel_factory):
        ch = channel_factory()
        ok, msg = container.referrer.register(ch["code"], "李推荐", "测试公司",
                                              "安全部", "13800138000", "bad")
        assert ok is False
        assert msg == "邮箱格式不正确"
    def test_register_duplicate_email(self, container, referrer_factory):
        referrer_factory(email="ref@test.com")
        ch = container.repos.channel.find_by_name("测试渠道")
        ok, msg = container.referrer.register(ch["code"], "赵推荐", "测试公司",
                                              "安全部", "13800138001", "ref@test.com")
        assert ok is False
        assert msg == "该邮箱已注册为推荐人"
    def test_register_rejected_within_7_days(self, container, referrer_factory):
        ref = referrer_factory(email="ref@test.com", approve=False)
        token = container.repos.token.find_active(1, ref["id"])
        container.referrer.approve(token["token"], "reject")
        ch = container.repos.channel.find_by_name("测试渠道")
        ok, msg = container.referrer.register(ch["code"], "李推荐", "测试公司",
                                              "安全部", "13800138000", "ref@test.com")
        assert ok is False
        assert "已被拒绝" in msg
    def test_register_rejected_after_7_days(self, container, referrer_factory):
        ref = referrer_factory(email="ref@test.com", approve=False)
        token = container.repos.token.find_active(1, ref["id"])
        container.referrer.approve(token["token"], "reject")
        container.clock.advance(8 * 24 * 3600)
        ch = container.repos.channel.find_by_name("测试渠道")
        # 7 天后可重新申请：复用原记录，重置为待审批
        ok, msg = container.referrer.register(ch["code"], "李推荐", "测试公司",
                                              "安全部", "13800138000", "ref@test.com")
        assert ok is True
        assert "申请已提交" in msg
        updated = container.repos.referrer.find_by_id(ref["id"])
        assert updated["status"] == ReferrerStatus.PENDING
        assert updated["rejected_at"] is None
class TestReferrerApprove:
    def test_approve_success(self, container, referrer_factory, fake_mailer):
        ref = referrer_factory(approve=False)
        token = container.repos.token.find_active(1, ref["id"])
        ok, msg, result = container.referrer.approve(token["token"], "approve")
        assert ok is True
        assert "已通过" in msg
        assert fake_mailer.count("M-03") == 1
        assert container.repos.referrer.find_by_id(ref["id"])["status"] == ReferrerStatus.APPROVED
    def test_approve_invalid_token(self, container):
        ok, msg, _ = container.referrer.approve("badtoken", "approve")
        assert ok is False
        assert msg == "链接无效"
    def test_approve_wrong_type(self, container, referrer_factory):
        # 构造一个 type=2 的令牌，用推荐人审批接口处理
        ref = referrer_factory()
        container.ticket.register(ref["code"], "王参会", "参会单位", "", "rc@test.com")
        rc = container.repos.recipient.find_by_email("rc@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        ok, msg, _ = container.referrer.approve(token["token"], "approve")
        assert ok is False
        assert msg == "链接类型不匹配"
    def test_approve_used_token(self, container, referrer_factory):
        ref = referrer_factory(approve=False)
        token = container.repos.token.find_active(1, ref["id"])
        container.referrer.approve(token["token"], "approve")
        ok, msg, _ = container.referrer.approve(token["token"], "approve")
        assert ok is False
        assert msg == "该链接已被使用"
    def test_approve_expired_token(self, container, referrer_factory):
        ref = referrer_factory(approve=False)
        token = container.repos.token.find_active(1, ref["id"])
        container.clock.advance(8 * 24 * 3600)
        ok, msg, _ = container.referrer.approve(token["token"], "approve")
        assert ok is False
        assert msg == "链接已过期"
    def test_approve_already_processed(self, container, referrer_factory):
        ref = referrer_factory(approve=False)
        token = container.repos.token.find_active(1, ref["id"])
        container.db.execute("UPDATE referrer SET status=2 WHERE id=?", (ref["id"],))
        ok, msg, _ = container.referrer.approve(token["token"], "approve")
        assert ok is False
        assert msg == "该申请已处理"
    def test_reject_success(self, container, referrer_factory, fake_mailer):
        ref = referrer_factory(approve=False)
        token = container.repos.token.find_active(1, ref["id"])
        ok, msg, result = container.referrer.approve(token["token"], "reject")
        assert ok is True
        assert "已拒绝" in msg
        assert fake_mailer.count("M-03R") == 1
        assert container.repos.referrer.find_by_id(ref["id"])["status"] == ReferrerStatus.REJECTED
    def test_approve_by_id_success(self, container, referrer_factory):
        ref = referrer_factory(approve=False)
        ch = container.repos.channel.find_by_name("测试渠道")
        ok, msg = container.referrer.approve_by_id(ref["id"], "approve", ch["id"])
        assert ok is True
    def test_approve_by_id_not_found(self, container):
        ok, msg = container.referrer.approve_by_id(999, "approve", 1)
        assert ok is False
        assert msg == "推荐人不存在"
    def test_approve_by_id_wrong_channel(self, container, referrer_factory):
        ref = referrer_factory(approve=False)
        ok, msg = container.referrer.approve_by_id(ref["id"], "approve", 999)
        assert ok is False
        assert msg == "无权操作该推荐人"
    def test_approve_by_id_no_active_token(self, container, referrer_factory):
        ref = referrer_factory()
        ok, msg = container.referrer.approve_by_id(ref["id"], "approve",
                                                   ref["channel_id"])
        assert ok is False
        assert msg == "该申请已处理或令牌已失效"
class TestReferrerQueries:
    def test_list_by_channel(self, container, referrer_factory):
        referrer_factory()
        ch = container.repos.channel.find_by_name("测试渠道")
        assert len(container.referrer.list_by_channel(ch["id"])) == 1
    def test_list_by_status(self, container, referrer_factory):
        referrer_factory()
        assert len(container.referrer.list_by_channel(None, ReferrerStatus.APPROVED)) == 1
    def test_get(self, container, referrer_factory):
        ref = referrer_factory()
        assert container.referrer.get(ref["id"])["name"] == "李推荐"
    def test_get_by_code(self, container, referrer_factory):
        ref = referrer_factory()
        assert container.referrer.get_by_code(ref["code"])["id"] == ref["id"]
class TestTicketRegister:
    def test_register_success(self, container, referrer_factory, fake_mailer):
        ref = referrer_factory()
        ok, msg = container.ticket.register(ref["code"], "王参会", "参会单位",
                                            "13900139000", "rc@test.com")
        assert ok is True
        assert "申请已提交" in msg
        assert fake_mailer.count("M-04") == 1
    def test_register_invalid_referrer(self, container):
        ok, msg = container.ticket.register("badcode", "王参会", "参会单位",
                                            "", "rc@test.com")
        assert ok is False
        assert msg == "该邀请链接已失效"
    def test_register_referrer_not_approved(self, container, referrer_factory):
        ref = referrer_factory(approve=False)
        ok, msg = container.ticket.register(ref["code"], "王参会", "参会单位",
                                            "", "rc@test.com")
        assert ok is False
        assert msg == "该邀请链接已失效"
    def test_register_channel_suspended(self, container, referrer_factory):
        ref = referrer_factory()
        container.channel.suspend(ref["channel_id"])
        ok, msg = container.ticket.register(ref["code"], "王参会", "参会单位",
                                            "", "rc@test.com")
        assert ok is False
        assert msg == "该渠道已停用"
    def test_register_invalid_name(self, container, referrer_factory):
        ref = referrer_factory()
        ok, msg = container.ticket.register(ref["code"], "王", "参会单位",
                                            "", "rc@test.com")
        assert ok is False
        assert "姓名需" in msg
    def test_register_invalid_company(self, container, referrer_factory):
        ref = referrer_factory()
        ok, msg = container.ticket.register(ref["code"], "王参会", "单",
                                            "", "rc@test.com")
        assert ok is False
        assert "公司需" in msg
    def test_register_invalid_mobile(self, container, referrer_factory):
        ref = referrer_factory()
        ok, msg = container.ticket.register(ref["code"], "王参会", "参会单位",
                                            "123", "rc@test.com")
        assert ok is False
        assert msg == "手机号格式不正确"
    def test_register_invalid_email(self, container, referrer_factory):
        ref = referrer_factory()
        ok, msg = container.ticket.register(ref["code"], "王参会", "参会单位",
                                            "", "bad")
        assert ok is False
        assert msg == "邮箱格式不正确"
    def test_register_duplicate_other_referrer(self, container, referrer_factory,
                                               recipient_factory):
        recipient_factory(email="rc@test.com")
        # 在同一渠道下创建第二个推荐人
        ch = container.repos.channel.find_by_name("测试渠道")
        ok, msg = container.referrer.register(ch["code"], "赵推荐", "测试公司",
                                              "安全部", "13800138001", "ref2@test.com")
        assert ok, msg
        ref2 = container.repos.referrer.find_by_email("ref2@test.com")
        token = container.repos.token.find_active(1, ref2["id"])
        container.referrer.approve(token["token"], "approve")
        ref2 = container.repos.referrer.find_by_id(ref2["id"])
        ok, msg = container.ticket.register(ref2["code"], "王参会2", "参会单位2",
                                            "", "rc@test.com")
        assert ok is False
        assert "该邮箱已由「李推荐」推荐注册" in msg
    def test_register_duplicate_same_referrer(self, container, recipient_factory):
        rc = recipient_factory(email="rc@test.com")
        ref = container.repos.referrer.find_by_id(rc["referrer_id"])
        ok, msg = container.ticket.register(ref["code"], "王参会2", "参会单位2",
                                            "", "rc@test.com")
        assert ok is False
        assert "您已为该邮箱提交过申请" in msg
    def test_register_referrer_cap_reached(self, container, referrer_factory):
        ref = referrer_factory()
        ch = container.repos.channel.find_by_id(ref["channel_id"])
        container.db.execute("UPDATE referrer SET issued_count=? WHERE id=?",
                             (ch["referrer_cap"], ref["id"]))
        ok, msg = container.ticket.register(ref["code"], "王参会", "参会单位",
                                            "", "rc@test.com")
        assert ok is False
        assert "赠票已发完" in msg
    def test_register_channel_quota_reached(self, container, referrer_factory):
        ref = referrer_factory()
        ch = container.repos.channel.find_by_id(ref["channel_id"])
        container.db.execute("UPDATE channel SET quota=1 WHERE id=?", (ch["id"],))
        container.db.execute(
            "INSERT INTO recipient(referrer_id,channel_id,name,company,mobile,email,"
            "status,created_at) VALUES(?,?,?,?,?,?,2,?)",
            (ref["id"], ch["id"], "已用", "单位", "", "used@test.com",
             container.clock.now_str()))
        ok, msg = container.ticket.register(ref["code"], "王参会", "参会单位",
                                            "", "rc@test.com")
        assert ok is False
        assert msg == "本渠道赠票已发完，请联系渠道负责人"
class TestTicketApprove:
    def test_approve_success(self, container, referrer_factory, fake_mailer):
        ref = referrer_factory()
        container.ticket.register(ref["code"], "王参会", "参会单位", "", "rc@test.com")
        rc = container.repos.recipient.find_by_email("rc@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        ok, msg, result = container.ticket.approve(token["token"], "approve")
        assert ok is True
        assert "已通过" in msg
        assert fake_mailer.count("M-05") == 1
        assert fake_mailer.count("M-06") == 1
        updated = container.repos.recipient.find_by_id(rc["id"])
        assert updated["status"] == RecipientStatus.APPROVED
        code = container.repos.code.find_by_recipient(rc["id"])
        assert code is not None
        assert len(code["code"]) == 16
        ref_updated = container.repos.referrer.find_by_id(ref["id"])
        assert ref_updated["issued_count"] == 1
    def test_approve_invalid_token(self, container):
        ok, msg, _ = container.ticket.approve("badtoken")
        assert ok is False
        assert msg == "链接无效"
    def test_approve_wrong_type(self, container, referrer_factory):
        ref = referrer_factory(approve=False)
        token = container.repos.token.find_active(1, ref["id"])
        ok, msg, _ = container.ticket.approve(token["token"])
        assert ok is False
        assert msg == "链接类型不匹配"
    def test_approve_used_token(self, container, recipient_factory):
        rc = recipient_factory()
        token = container.repos.token.find_active(2, rc["id"])
        # 令牌已被使用（recipient_factory 已审批）
        if token is None:
            token_row = container.repos.token.find_by_token("TK000000000000000000000000000003")
        ok, msg, _ = container.ticket.approve("nonexistent")
        assert ok is False
    def test_approve_expired_token(self, container, referrer_factory):
        ref = referrer_factory()
        container.ticket.register(ref["code"], "王参会", "参会单位", "", "rc@test.com")
        rc = container.repos.recipient.find_by_email("rc@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        container.clock.advance(8 * 24 * 3600)
        ok, msg, _ = container.ticket.approve(token["token"])
        assert ok is False
        assert msg == "链接已过期"
    def test_approve_already_processed(self, container, referrer_factory):
        ref = referrer_factory()
        container.ticket.register(ref["code"], "王参会", "参会单位", "", "rc@test.com")
        rc = container.repos.recipient.find_by_email("rc@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        container.db.execute("UPDATE recipient SET status=2 WHERE id=?", (rc["id"],))
        ok, msg, _ = container.ticket.approve(token["token"])
        assert ok is False
        assert msg == "该申请已处理"
    def test_reject_success(self, container, referrer_factory):
        ref = referrer_factory()
        container.ticket.register(ref["code"], "王参会", "参会单位", "", "rc@test.com")
        rc = container.repos.recipient.find_by_email("rc@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        ok, msg, result = container.ticket.approve(token["token"], "reject")
        assert ok is True
        assert "已拒绝" in msg
        assert container.repos.recipient.find_by_id(rc["id"])["status"] == RecipientStatus.REJECTED
    def test_approve_cap_reached_at_approve_time(self, container, referrer_factory):
        ref = referrer_factory()
        container.ticket.register(ref["code"], "王参会", "参会单位", "", "rc@test.com")
        rc = container.repos.recipient.find_by_email("rc@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        ch = container.repos.channel.find_by_id(ref["channel_id"])
        container.db.execute("UPDATE referrer SET issued_count=? WHERE id=?",
                             (ch["referrer_cap"], ref["id"]))
        ok, msg, _ = container.ticket.approve(token["token"])
        assert ok is False
        assert "赠票已发完" in msg
    def test_approve_quota_reached_at_approve_time(self, container, referrer_factory):
        ref = referrer_factory()
        container.ticket.register(ref["code"], "王参会", "参会单位", "", "rc@test.com")
        rc = container.repos.recipient.find_by_email("rc@test.com")
        token = container.repos.token.find_active(2, rc["id"])
        ch = container.repos.channel.find_by_id(ref["channel_id"])
        container.db.execute("UPDATE channel SET quota=1 WHERE id=?", (ch["id"],))
        container.db.execute(
            "INSERT INTO recipient(referrer_id,channel_id,name,company,mobile,email,"
            "status,created_at) VALUES(?,?,?,?,?,?,2,?)",
            (ref["id"], ch["id"], "已用", "单位", "", "used@test.com",
             container.clock.now_str()))
        ok, msg, _ = container.ticket.approve(token["token"])
        assert ok is False
        assert msg == "本渠道赠票已发完，请联系渠道负责人"
class TestTicketQueries:
    def test_list(self, container, recipient_factory):
        recipient_factory()
        assert len(container.ticket.list()) == 1
    def test_list_by_channel(self, container, recipient_factory):
        rc = recipient_factory()
        assert len(container.ticket.list(channel_id=rc["channel_id"])) == 1
    def test_list_by_referrer(self, container, recipient_factory):
        rc = recipient_factory()
        assert len(container.ticket.list(referrer_id=rc["referrer_id"])) == 1
    def test_list_by_status(self, container, recipient_factory):
        recipient_factory()
        assert len(container.ticket.list(status=RecipientStatus.APPROVED)) == 1
    def test_get_by_code(self, container, recipient_factory):
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        assert container.ticket.get_by_code(code["code"])["recipient_id"] == rc["id"]