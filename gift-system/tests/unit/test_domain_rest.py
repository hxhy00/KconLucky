"""单元测试：状态机、编码、模型、枚举、时钟、随机源。"""
from datetime import datetime
import pytest
from kcon.domain import codes, state_machine as sm
from kcon.domain.enums import (ChannelStatus, ExternalSyncStatus, RecipientStatus,
                               ReferrerStatus, TokenType, channel_status_text,
                               recipient_status_text, referrer_status_text)
from kcon.domain.models import (AdminStats, Channel, ChannelStats, Checkin,
                                CheckinLookupResult, Recipient, Referrer,
                                RegistrationCode, Token)
from kcon.infra.clock import FixedClock, SystemClock
from kcon.infra.random_source import SecureRandom, SequenceRandom
# ==================== 状态机 ====================
class TestReferrerTransitions:
    @pytest.mark.unit
    def test_pending_to_approved(self):
        assert sm.can_transit_referrer(
            ReferrerStatus.PENDING, ReferrerStatus.APPROVED) == (True, "")
    @pytest.mark.unit
    def test_pending_to_rejected(self):
        assert sm.can_transit_referrer(
            ReferrerStatus.PENDING, ReferrerStatus.REJECTED) == (True, "")
    @pytest.mark.unit
    def test_pending_to_disabled_illegal(self):
        ok, msg = sm.can_transit_referrer(ReferrerStatus.PENDING, ReferrerStatus.DISABLED)
        assert not ok
        assert "不允许从状态" in msg
    @pytest.mark.unit
    def test_approved_to_disabled(self):
        assert sm.can_transit_referrer(
            ReferrerStatus.APPROVED, ReferrerStatus.DISABLED) == (True, "")
    @pytest.mark.unit
    def test_approved_to_pending_illegal(self):
        assert sm.can_transit_referrer(
            ReferrerStatus.APPROVED, ReferrerStatus.PENDING)[0] is False
    @pytest.mark.unit
    def test_rejected_to_pending(self):
        assert sm.can_transit_referrer(
            ReferrerStatus.REJECTED, ReferrerStatus.PENDING) == (True, "")
    @pytest.mark.unit
    def test_disabled_to_anything_illegal(self):
        assert sm.can_transit_referrer(
            ReferrerStatus.DISABLED, ReferrerStatus.APPROVED)[0] is False
    @pytest.mark.unit
    def test_unknown_from_status(self):
        ok, msg = sm.can_transit_referrer(99, ReferrerStatus.APPROVED)
        assert not ok
        assert "未知的起始状态：99" in msg
class TestRecipientTransitions:
    @pytest.mark.unit
    def test_pending_to_approved(self):
        assert sm.can_transit_recipient(
            RecipientStatus.PENDING, RecipientStatus.APPROVED) == (True, "")
    @pytest.mark.unit
    def test_pending_to_rejected(self):
        assert sm.can_transit_recipient(
            RecipientStatus.PENDING, RecipientStatus.REJECTED) == (True, "")
    @pytest.mark.unit
    def test_approved_to_checked_in(self):
        assert sm.can_transit_recipient(
            RecipientStatus.APPROVED, RecipientStatus.CHECKED_IN) == (True, "")
    @pytest.mark.unit
    def test_approved_to_cancelled(self):
        assert sm.can_transit_recipient(
            RecipientStatus.APPROVED, RecipientStatus.CANCELLED) == (True, "")
    @pytest.mark.unit
    def test_checked_in_to_anything_illegal(self):
        assert sm.can_transit_recipient(
            RecipientStatus.CHECKED_IN, RecipientStatus.APPROVED)[0] is False
    @pytest.mark.unit
    def test_rejected_terminal(self):
        assert sm.can_transit_recipient(
            RecipientStatus.REJECTED, RecipientStatus.APPROVED)[0] is False
    @pytest.mark.unit
    def test_cancelled_terminal(self):
        assert sm.can_transit_recipient(
            RecipientStatus.CANCELLED, RecipientStatus.APPROVED)[0] is False
    @pytest.mark.unit
    def test_unknown_status(self):
        assert sm.can_transit_recipient(99, RecipientStatus.APPROVED)[0] is False
class TestChannelTransitions:
    @pytest.mark.unit
    def test_active_to_suspended(self):
        assert sm.can_transit_channel(
            ChannelStatus.ACTIVE, ChannelStatus.SUSPENDED) == (True, "")
    @pytest.mark.unit
    def test_active_to_deleted(self):
        assert sm.can_transit_channel(
            ChannelStatus.ACTIVE, ChannelStatus.DELETED) == (True, "")
    @pytest.mark.unit
    def test_suspended_to_active(self):
        assert sm.can_transit_channel(
            ChannelStatus.SUSPENDED, ChannelStatus.ACTIVE) == (True, "")
    @pytest.mark.unit
    def test_suspended_to_deleted(self):
        assert sm.can_transit_channel(
            ChannelStatus.SUSPENDED, ChannelStatus.DELETED) == (True, "")
    @pytest.mark.unit
    def test_deleted_terminal(self):
        assert sm.can_transit_channel(
            ChannelStatus.DELETED, ChannelStatus.ACTIVE)[0] is False
    @pytest.mark.unit
    def test_unknown_status(self):
        assert sm.can_transit_channel(99, ChannelStatus.ACTIVE)[0] is False
# ==================== 编码 ====================
class TestCodes:
    @pytest.mark.unit
    def test_valid_channel_code(self):
        assert codes.is_valid_channel_code("AbCd1234567890Xy") is True
    @pytest.mark.unit
    def test_channel_code_wrong_length(self):
        assert codes.is_valid_channel_code("abc") is False
    @pytest.mark.unit
    def test_channel_code_none(self):
        assert codes.is_valid_channel_code(None) is False
    @pytest.mark.unit
    def test_channel_code_non_string(self):
        assert codes.is_valid_channel_code(123) is False
    @pytest.mark.unit
    def test_channel_code_invalid_chars(self):
        assert codes.is_valid_channel_code("AbCd1234567890!y") is False
    @pytest.mark.unit
    def test_valid_referrer_code(self):
        assert codes.is_valid_referrer_code("AbCd1234567890Xy") is True
    @pytest.mark.unit
    def test_valid_token(self):
        assert codes.is_valid_token("A" * 32) is True
    @pytest.mark.unit
    def test_token_wrong_length(self):
        assert codes.is_valid_token("A" * 31) is False
    @pytest.mark.unit
    def test_valid_registration_code(self):
        assert codes.is_valid_registration_code("ABCDEFGHJKLMNPQR") is True
    @pytest.mark.unit
    def test_registration_code_with_excluded_chars(self):
        # 含 0/O/1/I 应不合法
        assert codes.is_valid_registration_code("ABCDEFGHJKLMN0QR") is False
        assert codes.is_valid_registration_code("ABCDEFGHJKLMNOQR") is False
        assert codes.is_valid_registration_code("ABCDEFGHJKLMN1QR") is False
        assert codes.is_valid_registration_code("ABCDEFGHJKLMNIQR") is False
    @pytest.mark.unit
    def test_registration_code_wrong_length(self):
        assert codes.is_valid_registration_code("ABC") is False
    @pytest.mark.unit
    def test_registration_code_none(self):
        assert codes.is_valid_registration_code(None) is False
    @pytest.mark.unit
    def test_normalize_registration_code(self):
        assert codes.normalize_registration_code("  abc123  ") == "ABC123"
    @pytest.mark.unit
    def test_normalize_none(self):
        assert codes.normalize_registration_code(None) == ""
    @pytest.mark.unit
    def test_normalize_non_string(self):
        assert codes.normalize_registration_code(123) == ""
    @pytest.mark.unit
    def test_build_referral_url(self):
        assert codes.build_referral_url("http://x.com", "abc") == "http://x.com/t/abc"
    @pytest.mark.unit
    def test_build_referral_url_trailing_slash(self):
        assert codes.build_referral_url("http://x.com/", "abc") == "http://x.com/t/abc"
    @pytest.mark.unit
    def test_build_channel_register_url(self):
        assert codes.build_channel_register_url("http://x.com", "c1") == "http://x.com/r/c1"
    @pytest.mark.unit
    def test_build_code_url(self):
        assert codes.build_code_url("http://x.com", "CODE") == "http://x.com/c/CODE"
    @pytest.mark.unit
    def test_build_approve_url_referrer(self):
        assert codes.build_approve_url(
            "http://x.com", "referrer", "tk") == "http://x.com/approve/referrer/tk"
    @pytest.mark.unit
    def test_build_approve_url_ticket(self):
        assert codes.build_approve_url(
            "http://x.com", "ticket", "tk") == "http://x.com/approve/ticket/tk"
# ==================== 枚举 ====================
class TestEnums:
    @pytest.mark.unit
    def test_referrer_status_text(self):
        assert referrer_status_text(ReferrerStatus.PENDING) == "待审批"
        assert referrer_status_text(ReferrerStatus.APPROVED) == "已通过"
        assert referrer_status_text(ReferrerStatus.REJECTED) == "已拒绝"
        assert referrer_status_text(ReferrerStatus.DISABLED) == "已停用"
    @pytest.mark.unit
    def test_referrer_status_unknown(self):
        assert referrer_status_text(99) == "未知"
    @pytest.mark.unit
    def test_recipient_status_text(self):
        assert recipient_status_text(RecipientStatus.PENDING) == "待审批"
        assert recipient_status_text(RecipientStatus.APPROVED) == "已通过"
        assert recipient_status_text(RecipientStatus.REJECTED) == "已拒绝"
        assert recipient_status_text(RecipientStatus.CHECKED_IN) == "已签到"
        assert recipient_status_text(RecipientStatus.CANCELLED) == "已作废"
    @pytest.mark.unit
    def test_recipient_status_unknown(self):
        assert recipient_status_text(99) == "未知"
    @pytest.mark.unit
    def test_channel_status_text(self):
        assert channel_status_text(ChannelStatus.ACTIVE) == "启用"
        assert channel_status_text(ChannelStatus.SUSPENDED) == "已停用"
        assert channel_status_text(ChannelStatus.DELETED) == "已删除"
    @pytest.mark.unit
    def test_channel_status_unknown(self):
        assert channel_status_text(99) == "未知"
    @pytest.mark.unit
    def test_enum_values(self):
        assert TokenType.REFERRER_APPROVE == 1
        assert TokenType.TICKET_APPROVE == 2
        assert ExternalSyncStatus.NOT_SYNCED == 0
        assert ExternalSyncStatus.SUCCESS == 1
        assert ExternalSyncStatus.FAILED == 2
# ==================== 模型 ====================
class TestModels:
    @pytest.mark.unit
    def test_channel_defaults(self):
        c = Channel(id=1, name="n", code="c", quota=10, referrer_cap=5, status=1)
        assert c.remark == ""
        assert c.created_at == ""
    @pytest.mark.unit
    def test_channel_frozen(self):
        c = Channel(id=1, name="n", code="c", quota=10, referrer_cap=5, status=1)
        with pytest.raises(Exception):
            c.name = "x"
    @pytest.mark.unit
    def test_referrer_defaults(self):
        r = Referrer(id=1, channel_id=1, name="n", company="c", department="d",
                     mobile="m", email="e", code="cd", status=1)
        assert r.issued_count == 0
        assert r.approved_at is None
        assert r.rejected_at is None
    @pytest.mark.unit
    def test_recipient_defaults(self):
        rc = Recipient(id=1, referrer_id=1, channel_id=1, name="n",
                       company="c", email="e", status=1)
        assert rc.mobile == ""
        assert rc.approved_at is None
    @pytest.mark.unit
    def test_registration_code_defaults(self):
        g = RegistrationCode(id=1, recipient_id=1, code="C")
        assert g.qr_content == ""
        assert g.qr_url == ""
        assert g.issued_at == ""
    @pytest.mark.unit
    def test_checkin_defaults(self):
        ck = Checkin(id=1, recipient_id=1, checked_at="t")
        assert ck.staff_id is None
        assert ck.device_ip == ""
        assert ck.material_issued == 1
        assert ck.external_sync_status == 0
    @pytest.mark.unit
    def test_token_defaults(self):
        t = Token(id=1, token="tk", type=1, target_id=1, owner_id=1, expires_at="e")
        assert t.used_at is None
    @pytest.mark.unit
    def test_admin_stats_defaults(self):
        s = AdminStats()
        assert s.channel_count == 0
        assert s.checkin_rate == 0.0
    @pytest.mark.unit
    def test_channel_stats_defaults(self):
        s = ChannelStats()
        assert s.channel_name == ""
        assert s.quota == 0
    @pytest.mark.unit
    def test_checkin_lookup_result_mutable(self):
        res = CheckinLookupResult(recipient_id=1, name="n", company="c",
                                  code="cd", status=2, status_text="已通过")
        res.checked_at = "2026-10-25 10:00:00"
        assert res.checked_at == "2026-10-25 10:00:00"
        assert res.referrer_name == ""
# ==================== 时钟 ====================
class TestClock:
    @pytest.mark.unit
    def test_system_clock_now(self):
        c = SystemClock()
        assert isinstance(c.now(), datetime)
    @pytest.mark.unit
    def test_system_clock_now_str(self):
        c = SystemClock()
        assert len(c.now_str()) == 19
    @pytest.mark.unit
    def test_fixed_clock(self):
        c = FixedClock("2026-10-25 10:00:00")
        assert c.now_str() == "2026-10-25 10:00:00"
    @pytest.mark.unit
    def test_fixed_clock_advance(self):
        c = FixedClock("2026-10-25 10:00:00")
        c.advance(60)
        assert c.now_str() == "2026-10-25 10:01:00"
    @pytest.mark.unit
    def test_fixed_clock_set(self):
        c = FixedClock("2026-10-25 10:00:00")
        c.set("2026-11-01 00:00:00")
        assert c.now_str() == "2026-11-01 00:00:00"
    @pytest.mark.unit
    def test_expires_at(self):
        c = FixedClock("2026-10-25 10:00:00")
        assert c.expires_at(600) == "2026-10-25 10:10:00"
    @pytest.mark.unit
    def test_expires_at_negative(self):
        c = FixedClock("2026-10-25 10:00:00")
        assert c.expires_at(-60) == "2026-10-25 09:59:00"
# ==================== 随机源 ====================
class TestSecureRandom:
    @pytest.mark.unit
    def test_choice_in_charset(self):
        r = SecureRandom()
        assert r.choice("abc") in "abc"
    @pytest.mark.unit
    def test_channel_code_length(self):
        assert len(SecureRandom().channel_code()) == 16
    @pytest.mark.unit
    def test_referrer_code_length(self):
        assert len(SecureRandom().referrer_code()) == 16
    @pytest.mark.unit
    def test_token_length(self):
        assert len(SecureRandom().token()) == 32
    @pytest.mark.unit
    def test_registration_code_length(self):
        assert len(SecureRandom().registration_code()) == 16
    @pytest.mark.unit
    def test_registration_code_charset(self):
        code = SecureRandom().registration_code()
        assert codes.is_valid_registration_code(code)
    @pytest.mark.unit
    def test_verify_code_digits(self):
        code = SecureRandom().verify_code()
        assert len(code) == 6
        assert code.isdigit()
    @pytest.mark.unit
    def test_verify_code_custom_length(self):
        assert len(SecureRandom().verify_code(4)) == 4
    @pytest.mark.unit
    def test_random_string(self):
        assert len(SecureRandom().random_string(10)) == 10
class TestSequenceRandom:
    @pytest.mark.unit
    def test_choice_sequence(self):
        r = SequenceRandom(["a", "b", "c"])
        assert r.choice("xyz") == "a"
        assert r.choice("xyz") == "b"
        assert r.choice("xyz") == "c"
    @pytest.mark.unit
    def test_choice_wraps_around(self):
        r = SequenceRandom(["a", "b"])
        r.choice("x")
        r.choice("x")
        assert r.choice("x") == "a"
    @pytest.mark.unit
    def test_random_string_exact_length(self):
        r = SequenceRandom(["ABCDEFGHIJ"])
        assert r.random_string(10) == "ABCDEFGHIJ"
    @pytest.mark.unit
    def test_random_string_padding(self):
        r = SequenceRandom(["AB"])
        assert r.random_string(4) == "ABAB"
    @pytest.mark.unit
    def test_random_string_wraps(self):
        r = SequenceRandom(["AB"])
        r.random_string(2)
        assert r.random_string(2) == "AB"