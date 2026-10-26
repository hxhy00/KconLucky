"""集成测试：渠道服务。"""
import pytest
from kcon.domain.enums import ChannelStatus
pytestmark = pytest.mark.integration
class TestCreateChannel:
    def test_create_success(self, container, fake_mailer):
        ok, msg = container.channel.create("渠道A", "张审批", "ap@test.com", 100, 20)
        assert ok is True
        assert "创建成功" in msg
        assert fake_mailer.count("M-01") == 1
        ch = container.repos.channel.find_by_name("渠道A")
        assert ch["quota"] == 100
        assert ch["referrer_cap"] == 20
        assert len(ch["code"]) == 16
    def test_create_invalid_channel_name(self, container):
        ok, msg = container.channel.create("A", "张审批", "ap@test.com", 100, 20)
        assert ok is False
        assert "渠道名称需" in msg
    def test_create_invalid_approver_name(self, container):
        ok, msg = container.channel.create("渠道A", "张", "ap@test.com", 100, 20)
        assert ok is False
        assert "审批员姓名需" in msg
    def test_create_invalid_email(self, container):
        ok, msg = container.channel.create("渠道A", "张审批", "bad", 100, 20)
        assert ok is False
        assert msg == "邮箱格式不正确"
    def test_create_invalid_quota(self, container):
        ok, msg = container.channel.create("渠道A", "张审批", "ap@test.com", 0, 20)
        assert ok is False
        assert "配额需在" in msg
    def test_create_invalid_cap(self, container):
        ok, msg = container.channel.create("渠道A", "张审批", "ap@test.com", 100, 0)
        assert ok is False
        assert "单人上限需在" in msg
    def test_create_invalid_remark(self, container):
        ok, msg = container.channel.create("渠道A", "张审批", "ap@test.com", 100, 20,
                                           "备" * 201)
        assert ok is False
        assert "备注不能超过" in msg
    def test_create_duplicate_name(self, container, channel_factory):
        channel_factory(name="渠道A")
        ok, msg = container.channel.create("渠道A", "李审批", "ap2@test.com", 100, 20)
        assert ok is False
        assert msg == "渠道名称已存在"
    def test_create_duplicate_approver_email(self, container, channel_factory):
        channel_factory(name="渠道A", approver_email="ap@test.com")
        ok, msg = container.channel.create("渠道B", "李审批", "ap@test.com", 100, 20)
        assert ok is False
        assert msg == "该邮箱已被其他渠道使用"
    def test_create_channel_limit(self, container):
        for i in range(20):
            ok, _ = container.channel.create(f"渠道{i}", f"审批{i}",
                                             f"ap{i}@test.com", 10, 5)
            assert ok
        ok, msg = container.channel.create("渠道X", "审批X", "apx@test.com", 10, 5)
        assert ok is False
        assert "渠道数量已达上限（20 个）" == msg
    def test_create_total_quota_limit(self, container):
        # 19 渠道 × 26 张 = 494 张，再申请 7 张将超过 500 上限
        for i in range(19):
            ok, _ = container.channel.create(f"渠道{i}", f"审批{i}",
                                             f"ap{i}@test.com", 26, 5)
            assert ok
        ok, msg = container.channel.create("渠道X", "审批X", "apx@test.com", 7, 5)
        assert ok is False
        assert "总赠票配额将超过上限" in msg
class TestSuspendDelete:
    def test_suspend_success(self, container, channel_factory):
        ch = channel_factory()
        ok, msg = container.channel.suspend(ch["id"])
        assert ok is True
        assert container.repos.channel.find_by_id(ch["id"])["status"] == ChannelStatus.SUSPENDED
    def test_suspend_not_found(self, container):
        ok, msg = container.channel.suspend(999)
        assert ok is False
        assert msg == "渠道不存在"
    def test_suspend_already_suspended(self, container, channel_factory):
        ch = channel_factory()
        container.channel.suspend(ch["id"])
        ok, msg = container.channel.suspend(ch["id"])
        assert ok is False
        assert msg == "渠道已处于停用状态"
    def test_delete_not_found(self, container):
        ok, msg = container.channel.delete(999)
        assert ok is False
        assert msg == "渠道不存在"
    def test_delete_with_data(self, container, referrer_factory):
        referrer_factory()
        ch = container.repos.channel.find_by_name("测试渠道")
        ok, msg = container.channel.delete(ch["id"])
        assert ok is False
        assert "无法物理删除" in msg
    def test_delete_empty_channel(self, container, channel_factory):
        ch = channel_factory()
        ok, msg = container.channel.delete(ch["id"])
        assert ok is True
        assert msg == "渠道已删除"
        assert container.repos.channel.find_by_id(ch["id"])["status"] == ChannelStatus.DELETED
class TestQueries:
    def test_list_all(self, container, channel_factory):
        channel_factory(name="渠道A")
        channel_factory(name="渠道B", approver_email="ap2@test.com")
        assert len(container.channel.list_all()) == 2
    def test_get_by_id(self, container, channel_factory):
        ch = channel_factory()
        assert container.channel.get(ch["id"])["name"] == "测试渠道"
    def test_get_by_code(self, container, channel_factory):
        ch = channel_factory()
        assert container.channel.get_by_code(ch["code"])["id"] == ch["id"]
    def test_get_by_code_suspended(self, container, channel_factory):
        ch = channel_factory()
        container.channel.suspend(ch["id"])
        assert container.channel.get_by_code(ch["code"]) is None