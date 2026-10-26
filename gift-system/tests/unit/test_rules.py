"""单元测试：业务规则（覆盖全部分支与条件）。"""
from datetime import datetime
import pytest
from kcon.domain import rules as r
from kcon.domain.enums import ChannelStatus, RecipientStatus, ReferrerStatus, TokenType
class TestAdminLimit:
    @pytest.mark.unit
    def test_under_limit(self):
        assert r.check_admin_limit(0) == (True, "")
        assert r.check_admin_limit(2) == (True, "")
    @pytest.mark.unit
    def test_at_limit(self):
        assert r.check_admin_limit(3) == (False, "管理员数量已达上限（3 个）")
    @pytest.mark.unit
    def test_over_limit(self):
        assert r.check_admin_limit(5) == (False, "管理员数量已达上限（3 个）")
class TestChannelLimit:
    @pytest.mark.unit
    def test_under_limit(self):
        assert r.check_channel_limit(19) == (True, "")
    @pytest.mark.unit
    def test_at_limit(self):
        assert r.check_channel_limit(20) == (False, "渠道数量已达上限（20 个）")
class TestTotalTicketLimit:
    @pytest.mark.unit
    def test_within_limit(self):
        assert r.check_total_ticket_limit(0, 500) == (True, "")
        assert r.check_total_ticket_limit(400, 100) == (True, "")
    @pytest.mark.unit
    def test_exceed_limit(self):
        ok, msg = r.check_total_ticket_limit(400, 101)
        assert not ok
        assert "总赠票配额将超过上限（500 张）" in msg
        assert "当前已分配 400 张" in msg
        assert "本次申请 101 张" in msg
class TestReferrerCap:
    @pytest.mark.unit
    def test_under_cap(self):
        assert r.check_referrer_cap(19, 20) == (True, "")
    @pytest.mark.unit
    def test_at_cap(self):
        ok, msg = r.check_referrer_cap(20, 20)
        assert not ok
        assert "上限 20 张" in msg
    @pytest.mark.unit
    def test_over_cap(self):
        assert r.check_referrer_cap(25, 20)[0] is False
class TestChannelQuota:
    @pytest.mark.unit
    def test_under_quota(self):
        assert r.check_channel_quota(99, 100) == (True, "")
    @pytest.mark.unit
    def test_at_quota(self):
        assert r.check_channel_quota(100, 100) == (False, "本渠道赠票已发完，请联系渠道负责人")
class TestReferrerAvailable:
    @pytest.mark.unit
    def test_available(self):
        assert r.check_referrer_available(
            ReferrerStatus.APPROVED, ChannelStatus.ACTIVE) == (True, "")
    @pytest.mark.unit
    def test_referrer_pending(self):
        assert r.check_referrer_available(
            ReferrerStatus.PENDING, ChannelStatus.ACTIVE) == (False, "该邀请链接已失效")
    @pytest.mark.unit
    def test_referrer_rejected(self):
        assert r.check_referrer_available(
            ReferrerStatus.REJECTED, ChannelStatus.ACTIVE)[0] is False
    @pytest.mark.unit
    def test_channel_suspended(self):
        assert r.check_referrer_available(
            ReferrerStatus.APPROVED, ChannelStatus.SUSPENDED) == (False, "该渠道已停用")
    @pytest.mark.unit
    def test_both_invalid(self):
        # 条件覆盖：referrer 假 + channel 假（短路，只报第一个）
        assert r.check_referrer_available(
            ReferrerStatus.PENDING, ChannelStatus.SUSPENDED)[0] is False
class TestEmailConflict:
    @pytest.mark.unit
    def test_no_conflict(self):
        assert r.check_email_conflict(None, 1, 1) == (True, "")
    @pytest.mark.unit
    def test_same_referrer_conflict(self):
        ok, msg = r.check_email_conflict(1, 1, RecipientStatus.APPROVED)
        assert not ok
        assert "您已为该邮箱提交过申请，当前状态：已通过" == msg
    @pytest.mark.unit
    def test_same_referrer_pending_status(self):
        ok, msg = r.check_email_conflict(1, 1, RecipientStatus.PENDING)
        assert "当前状态：待审批" in msg
    @pytest.mark.unit
    def test_other_referrer_conflict(self):
        ok, msg = r.check_email_conflict(2, 1, RecipientStatus.APPROVED, "张三")
        assert not ok
        assert msg == "该邮箱已由「张三」推荐注册，请勿重复提交"
class TestRejectRetry:
    @pytest.mark.unit
    def test_not_rejected(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.check_reject_retry(None, now) == (True, "")
    @pytest.mark.unit
    def test_within_7_days(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        ok, msg = r.check_reject_retry("2026-10-20 10:00:00", now)
        assert not ok
        assert "请于 10月27日 后再试" in msg
    @pytest.mark.unit
    def test_after_7_days(self):
        now = datetime(2026, 10, 28, 10, 0, 0)
        assert r.check_reject_retry("2026-10-20 10:00:00", now) == (True, "")
    @pytest.mark.unit
    def test_exactly_7_days(self):
        now = datetime(2026, 10, 27, 10, 0, 0)
        assert r.check_reject_retry("2026-10-20 10:00:00", now) == (True, "")
    @pytest.mark.unit
    def test_invalid_time_format(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.check_reject_retry("bad-time", now) == (True, "")
class TestTokenUsable:
    @pytest.mark.unit
    def test_usable(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.check_token_usable(None, "2026-11-01 10:00:00", now) == (True, "")
    @pytest.mark.unit
    def test_used(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.check_token_usable(
            "2026-10-25 09:00:00", "2026-11-01 10:00:00", now) == (False, "该链接已被使用")
    @pytest.mark.unit
    def test_expired(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.check_token_usable(None, "2026-10-24 10:00:00", now) == (False, "链接已过期")
    @pytest.mark.unit
    def test_exactly_expired(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.check_token_usable(None, "2026-10-25 10:00:00", now) == (False, "链接已过期")
    @pytest.mark.unit
    def test_invalid_expire_format(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.check_token_usable(None, "bad", now) == (False, "链接已过期")
class TestTokenOwner:
    @pytest.mark.unit
    def test_owner_match(self):
        assert r.check_token_owner(1, 1) == (True, "")
    @pytest.mark.unit
    def test_owner_mismatch(self):
        assert r.check_token_owner(1, 2) == (False, "无权操作该申请")
class TestTokenType:
    @pytest.mark.unit
    def test_type_match(self):
        assert r.check_token_type(
            TokenType.REFERRER_APPROVE, TokenType.REFERRER_APPROVE) == (True, "")
    @pytest.mark.unit
    def test_type_mismatch(self):
        assert r.check_token_type(
            TokenType.TICKET_APPROVE, TokenType.REFERRER_APPROVE) == (False, "链接类型不匹配")
class TestCalcTokenExpires:
    @pytest.mark.unit
    def test_expires_7_days_later(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.calc_token_expires(now) == "2026-11-01 10:00:00"
class TestRecipientCheckinable:
    @pytest.mark.unit
    def test_approved_can_checkin(self):
        assert r.check_recipient_checkinable(RecipientStatus.APPROVED) == (True, "")
    @pytest.mark.unit
    def test_pending_cannot(self):
        assert r.check_recipient_checkinable(
            RecipientStatus.PENDING) == (False, "该赠票尚未生效，请联系推荐人")
    @pytest.mark.unit
    def test_rejected_cannot(self):
        assert r.check_recipient_checkinable(
            RecipientStatus.REJECTED) == (False, "该赠票已被拒绝")
    @pytest.mark.unit
    def test_cancelled_cannot(self):
        assert r.check_recipient_checkinable(
            RecipientStatus.CANCELLED) == (False, "该赠票已作废")
    @pytest.mark.unit
    def test_checked_in_cannot(self):
        assert r.check_recipient_checkinable(
            RecipientStatus.CHECKED_IN) == (False, "该参会人已完成签到")
class TestDuplicateCheckin:
    @pytest.mark.unit
    def test_not_checked_in(self):
        assert r.check_duplicate_checkin(RecipientStatus.APPROVED, None) == (True, "")
    @pytest.mark.unit
    def test_already_checked_in(self):
        ok, msg = r.check_duplicate_checkin(
            RecipientStatus.CHECKED_IN, "2026-10-25 10:30:00")
        assert not ok
        assert "已于 2026-10-25 10:30:00 完成签到" in msg
    @pytest.mark.unit
    def test_checked_in_without_time(self):
        ok, msg = r.check_duplicate_checkin(RecipientStatus.CHECKED_IN, None)
        assert not ok
        assert "已于  完成签到" in msg
class TestShouldFlushM08:
    @pytest.mark.unit
    def test_empty_queue(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.should_flush_m08(0, None, now) is False
    @pytest.mark.unit
    def test_reach_batch_size(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.should_flush_m08(10, "2026-10-25 09:59:00", now) is True
    @pytest.mark.unit
    def test_below_batch_within_window(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.should_flush_m08(5, "2026-10-25 09:45:00", now) is False
    @pytest.mark.unit
    def test_below_batch_over_window(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.should_flush_m08(5, "2026-10-25 09:25:00", now) is True
    @pytest.mark.unit
    def test_exactly_30_minutes(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.should_flush_m08(5, "2026-10-25 09:30:00", now) is True
    @pytest.mark.unit
    def test_no_first_time(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.should_flush_m08(5, None, now) is True
    @pytest.mark.unit
    def test_invalid_time_format(self):
        now = datetime(2026, 10, 25, 10, 0, 0)
        assert r.should_flush_m08(5, "bad", now) is True
class TestCalcCheckinRate:
    @pytest.mark.unit
    def test_normal(self):
        assert r.calc_checkin_rate(5, 10) == 50.0
    @pytest.mark.unit
    def test_zero_approved(self):
        assert r.calc_checkin_rate(0, 0) == 0.0
    @pytest.mark.unit
    def test_negative_approved(self):
        assert r.calc_checkin_rate(5, -1) == 0.0
    @pytest.mark.unit
    def test_rounding(self):
        assert r.calc_checkin_rate(1, 3) == 33.3
    @pytest.mark.unit
    def test_full(self):
        assert r.calc_checkin_rate(10, 10) == 100.0
class TestMaskEmail:
    @pytest.mark.unit
    def test_normal(self):
        assert r.mask_email("abcdef@x.com") == "a****f@x.com"
    @pytest.mark.unit
    def test_short_name(self):
        assert r.mask_email("ab@x.com") == "a*@x.com"
    @pytest.mark.unit
    def test_single_char(self):
        assert r.mask_email("a@x.com") == "a*@x.com"
    @pytest.mark.unit
    def test_none(self):
        assert r.mask_email(None) == ""
    @pytest.mark.unit
    def test_no_at(self):
        assert r.mask_email("invalid") == "invalid"
class TestMaskMobile:
    @pytest.mark.unit
    def test_normal(self):
        assert r.mask_mobile("13800138000") == "138****8000"
    @pytest.mark.unit
    def test_short(self):
        assert r.mask_mobile("123") == "123"
    @pytest.mark.unit
    def test_none(self):
        assert r.mask_mobile(None) == ""
    @pytest.mark.unit
    def test_empty(self):
        assert r.mask_mobile("") == ""