"""集成测试：更换渠道审批员（含旧令牌作废）。"""
import pytest
from kcon.domain.enums import TokenType
pytestmark = pytest.mark.integration
class TestReplaceApprover:
    def _make_channel(self, container):
        ok, msg = container.channel.create("换人渠道", "张原审", "ap_old@test.com", 100, 20)
        assert ok
        return container.repos.channel.find_by_name("换人渠道")
    def test_replace_success(self, container, fake_mailer):
        ch = self._make_channel(container)
        ok, msg = container.channel.replace_approver(ch["id"], "李接替", "ap_new@test.com")
        assert ok is True
        assert "已更换" in msg
        # 新审批员生效，旧的停用
        cur = container.repos.approver.find_by_channel(ch["id"])
        assert cur["email"] == "ap_new@test.com"
        old = container.db.query_one(
            "SELECT status FROM approver WHERE email='ap_old@test.com'")
        assert old["status"] == 0
        # 给新审批员发了 M-01
        assert fake_mailer.count("M-01") == 2  # 建渠道1次 + 换人1次
    def test_replace_invalidates_old_token(self, container):
        """换人后，原审批员未使用的推荐人审批令牌应失效。"""
        ch = self._make_channel(container)
        # 制造一个待审批推荐人 + 有效令牌
        container.referrer.register(ch["code"], "王推荐", "公司", "部门",
                                    "13800000001", "ref_dup@test.com")
        ref = container.repos.referrer.find_by_email("ref_dup@test.com")
        tk = container.repos.token.find_active(TokenType.REFERRER_APPROVE, ref["id"])
        assert tk is not None
        # 换人
        ok, msg = container.channel.replace_approver(ch["id"], "李接替", "ap_new@test.com")
        assert ok
        assert "作废旧令牌 1 个" in msg
        # 旧令牌失效
        after = container.repos.token.find_by_token(tk["token"])
        assert after["used_at"] is not None
        # 用旧令牌再审批失败
        ok2, msg2, _ = container.referrer.approve(tk["token"], "approve")
        assert ok2 is False
        assert msg2 == "该链接已被使用"
    def test_replace_invalid_name(self, container):
        ch = self._make_channel(container)
        ok, msg = container.channel.replace_approver(ch["id"], "李", "x@test.com")
        assert ok is False
        assert "审批员姓名需" in msg
    def test_replace_invalid_email(self, container):
        ch = self._make_channel(container)
        ok, msg = container.channel.replace_approver(ch["id"], "李接替", "bad")
        assert ok is False
        assert msg == "邮箱格式不正确"
    def test_replace_channel_not_found(self, container):
        ok, msg = container.channel.replace_approver(999, "李接替", "x@test.com")
        assert ok is False
        assert msg == "渠道不存在"
    def test_replace_channel_suspended(self, container):
        ch = self._make_channel(container)
        container.channel.suspend(ch["id"])
        ok, msg = container.channel.replace_approver(ch["id"], "李接替", "x@test.com")
        assert ok is False
        assert "无法更换审批员" in msg
    def test_replace_email_used_by_other(self, container):
        ch = self._make_channel(container)
        ok, _ = container.channel.create("另一渠道", "赵审批", "other@test.com", 50, 10)
        assert ok
        ok, msg = container.channel.replace_approver(ch["id"], "李接替", "other@test.com")
        assert ok is False
        assert msg == "该邮箱已被其他渠道使用"
    def test_replace_same_current_approver(self, container):
        """新邮箱正是本渠道当前审批员 → 拒绝。"""
        ch = self._make_channel(container)
        ok, msg = container.channel.replace_approver(ch["id"], "张原审", "ap_old@test.com")
        assert ok is False
        assert msg == "该邮箱已是本渠道审批员"
    def test_replace_no_current_approver(self, container):
        """渠道无启用审批员时也能换人（current 为 None 分支）。"""
        ch = self._make_channel(container)
        container.repos.approver.disable_by_channel(ch["id"])
        ok, msg = container.channel.replace_approver(ch["id"], "李接替", "ap_new@test.com")
        assert ok is True
        assert "已更换" in msg
