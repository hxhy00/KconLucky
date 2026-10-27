"""集成测试：签到服务 + 统计服务。"""
import pytest
from kcon.domain.enums import ExternalSyncStatus, RecipientStatus
pytestmark = pytest.mark.integration
class TestCheckinLookup:
    def test_lookup_success(self, container, recipient_factory):
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        ok, msg, result = container.checkin.lookup(code["code"])
        assert ok is True
        assert result.name == "王参会"
        assert result.status == RecipientStatus.APPROVED
    def test_lookup_empty_code(self, container):
        ok, msg, result = container.checkin.lookup("")
        assert ok is False
        assert msg == "请输入注册码"
    def test_lookup_invalid_code(self, container):
        ok, msg, result = container.checkin.lookup("INVALIDCODE12345")
        assert ok is False
        assert msg == "注册码无效，请核对"
    def test_lookup_lowercase_normalized(self, container, recipient_factory):
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        ok, msg, result = container.checkin.lookup(code["code"].lower())
        assert ok is True
    def test_lookup_pending_recipient(self, container, referrer_factory):
        ref = referrer_factory()
        container.ticket.register(ref["code"], "待审", "单位", "13900139000", "p@test.com")
        rc = container.repos.recipient.find_by_email("p@test.com")
        # 手动造一个注册码（模拟异常数据）
        container.repos.code.insert(rc["id"], "PENDINGCODE12345", "", "",
                                    container.clock.now_str())
        ok, msg, result = container.checkin.lookup("PENDINGCODE12345")
        assert ok is False
        assert msg == "该赠票尚未生效，请联系推荐人"
    def test_lookup_rejected_recipient(self, container, referrer_factory):
        ref = referrer_factory()
        container.ticket.register(ref["code"], "被拒", "单位", "13900139000", "r@test.com")
        rc = container.repos.recipient.find_by_email("r@test.com")
        container.repos.code.insert(rc["id"], "REJECTEDCODE1234", "", "",
                                    container.clock.now_str())
        container.db.execute("UPDATE recipient SET status=3 WHERE id=?", (rc["id"],))
        ok, msg, result = container.checkin.lookup("REJECTEDCODE1234")
        assert ok is False
        assert msg == "该赠票已被拒绝"
    def test_lookup_cancelled_recipient(self, container, referrer_factory):
        ref = referrer_factory()
        container.ticket.register(ref["code"], "作废", "单位", "13900139000", "c@test.com")
        rc = container.repos.recipient.find_by_email("c@test.com")
        container.repos.code.insert(rc["id"], "CANCELLEDCODE123", "", "",
                                    container.clock.now_str())
        container.db.execute("UPDATE recipient SET status=5 WHERE id=?", (rc["id"],))
        ok, msg, result = container.checkin.lookup("CANCELLEDCODE123")
        assert ok is False
        assert msg == "该赠票已作废"
    def test_lookup_already_checked_in(self, container, recipient_factory):
        """已签到状态 lookup 放行（由 confirm 判断重复）。"""
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        container.checkin.confirm(code["code"])
        ok, msg, result = container.checkin.lookup(code["code"])
        assert ok is True
        assert result.status == RecipientStatus.CHECKED_IN
        assert result.checked_at is not None
class TestCheckinConfirm:
    def test_confirm_success(self, container, recipient_factory, fake_mailer,
                             fake_external):
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        ok, msg, result = container.checkin.confirm(code["code"], staff_id=1,
                                                    device_ip="127.0.0.1")
        assert ok is True
        assert "签到成功，请发放参会材料" in msg
        assert fake_mailer.count("M-07") == 1
        assert len(fake_external.calls) == 1
        updated = container.repos.recipient.find_by_id(rc["id"])
        assert updated["status"] == RecipientStatus.CHECKED_IN
        checkin = container.repos.checkin.find_by_recipient(rc["id"])
        assert checkin["material_issued"] == 1
        assert checkin["external_sync_status"] == ExternalSyncStatus.SUCCESS
    def test_confirm_duplicate(self, container, recipient_factory):
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        container.checkin.confirm(code["code"])
        ok, msg, result = container.checkin.confirm(code["code"])
        assert ok is False
        assert "完成签到" in msg
    def test_confirm_invalid_code(self, container):
        ok, msg, result = container.checkin.confirm("INVALIDCODE12345")
        assert ok is False
        assert msg == "注册码无效，请核对"
    def test_confirm_external_failure_not_blocking(self, container, recipient_factory):
        from kcon.infra.external_checkin import FailingExternalCheckin
        container.checkin.external = FailingExternalCheckin()
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        ok, msg, result = container.checkin.confirm(code["code"])
        assert ok is True
        checkin = container.repos.checkin.find_by_recipient(rc["id"])
        assert checkin["external_sync_status"] == ExternalSyncStatus.FAILED
        assert checkin["external_response"] == "外部系统不可用"
class TestM08Flush:
    def test_flush_when_batch_full(self, container, channel_factory, fake_mailer):
        ch = channel_factory(cap=20)
        # 造 10 个已签到记录
        for i in range(10):
            ok, msg = container.referrer.register(ch["code"], f"推荐{i}", "公司",
                                                  "部门", f"1380013800{i}",
                                                  f"ref{i}@test.com")
            ref = container.repos.referrer.find_by_email(f"ref{i}@test.com")
            token = container.repos.token.find_active(1, ref["id"])
            container.referrer.approve(token["token"], "approve")
            ref = container.repos.referrer.find_by_id(ref["id"])
            container.ticket.register(ref["code"], f"参会{i}", "单位", "13900139000",
                                      f"rc{i}@test.com")
            rc = container.repos.recipient.find_by_email(f"rc{i}@test.com")
            token2 = container.repos.token.find_active(2, rc["id"])
            container.ticket.approve(token2["token"], "approve")
            rc = container.repos.recipient.find_by_id(rc["id"])
            code = container.repos.code.find_by_recipient(rc["id"])
            container.checkin.confirm(code["code"])
        # 第 10 个触发汇总
        assert fake_mailer.count("M-08") == 1
    def test_flush_when_window_expired(self, container, recipient_factory, fake_mailer):
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        container.checkin.confirm(code["code"])
        # 未满 10 人，未到 30 分钟
        assert fake_mailer.count("M-08") == 0
        container.clock.advance(31 * 60)
        # 手动触发
        flushed = container.checkin.try_flush_m08(rc["channel_id"])
        assert flushed is True
        assert fake_mailer.count("M-08") == 1
    def test_flush_empty_queue(self, container, channel_factory):
        ch = channel_factory()
        assert container.checkin.try_flush_m08(ch["id"]) is False
    def test_flush_no_approver(self, container, channel_factory):
        ch = channel_factory()
        container.repos.approver.disable_by_channel(ch["id"])
        container.repos.m08.enqueue(ch["id"], 1, container.clock.now_str())
        assert container.checkin.try_flush_m08(ch["id"], force=True) is False
    def test_flush_force(self, container, recipient_factory, fake_mailer):
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        container.checkin.confirm(code["code"])
        flushed = container.checkin.try_flush_m08(rc["channel_id"], force=True)
        assert flushed is True
        assert fake_mailer.count("M-08") == 1
class TestCodeInfo:
    def test_code_info_unauthenticated(self, container, recipient_factory):
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        ok, msg, data = container.checkin.code_info(code["code"], authenticated=False)
        assert ok is True
        assert data["valid"] is True
        assert data["checked_in"] is False
        assert "name" not in data
        assert "company" not in data
    def test_code_info_authenticated(self, container, recipient_factory):
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        ok, msg, data = container.checkin.code_info(code["code"], authenticated=True)
        assert ok is True
        assert data["name"] == "王参会"
        assert data["company"] == "参会单位"
    def test_code_info_invalid(self, container):
        ok, msg, data = container.checkin.code_info("INVALIDCODE12345")
        assert ok is False
        assert msg == "注册码无效"
    def test_code_info_empty(self, container):
        ok, msg, data = container.checkin.code_info("")
        assert ok is False
        assert msg == "注册码无效"
    def test_code_info_checked_in(self, container, recipient_factory):
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        container.checkin.confirm(code["code"])
        ok, msg, data = container.checkin.code_info(code["code"])
        assert ok is True
        assert data["checked_in"] is True
        assert data["checked_at"] is not None
class TestStats:
    def test_admin_stats_empty(self, container):
        stats, channels = container.stats.admin_stats()
        assert stats.channel_count == 0
        assert stats.checkin_rate == 0.0
        assert channels == []
    def test_admin_stats_with_data(self, container, recipient_factory):
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        container.checkin.confirm(code["code"])
        stats, channels = container.stats.admin_stats()
        assert stats.channel_count == 1
        assert stats.referrer_count == 1
        assert stats.invited_count == 1
        assert stats.approved_count == 1
        assert stats.checked_in_count == 1
        assert stats.checkin_rate == 100.0
        assert stats.quota_total == 100
        assert stats.quota_used == 1
        assert stats.quota_remaining == 99
    def test_channel_stats_empty(self, container, channel_factory):
        ch = channel_factory()
        stats, referrers = container.stats.channel_stats(ch["id"])
        assert stats.channel_name == "测试渠道"
        assert stats.referrer_count == 0
        assert stats.checkin_rate == 0.0
    def test_channel_stats_not_found(self, container):
        stats, referrers = container.stats.channel_stats(999)
        assert stats.channel_name == ""
        assert stats.quota == 0
    def test_channel_stats_with_data(self, container, recipient_factory):
        rc = recipient_factory()
        code = container.repos.code.find_by_recipient(rc["id"])
        container.checkin.confirm(code["code"])
        stats, referrers = container.stats.channel_stats(rc["channel_id"])
        assert stats.referrer_count == 1
        assert stats.invited_count == 1
        assert stats.approved_count == 1
        assert stats.checked_in_count == 1
        assert stats.checkin_rate == 100.0
        assert len(referrers) == 1