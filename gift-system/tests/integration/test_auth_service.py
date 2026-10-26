"""集成测试：认证服务。"""
import pytest
from kcon.domain.enums import ReferrerStatus
pytestmark = pytest.mark.integration
class TestSendVerifyCode:
    def test_send_code_success(self, container):
        ok, msg, code = container.auth.send_verify_code("admin@kcon.com")
        assert ok is True
        assert msg == "验证码已发送"
        assert len(code) == 6
    def test_send_code_invalid_email(self, container):
        ok, msg, code = container.auth.send_verify_code("bad-email")
        assert ok is False
        assert msg == "邮箱格式不正确"
        assert code is None
    def test_send_code_resend_too_soon(self, container):
        container.auth.send_verify_code("admin@kcon.com")
        ok, msg, code = container.auth.send_verify_code("admin@kcon.com")
        assert ok is False
        assert "秒后再试" in msg
    def test_send_code_after_interval(self, container):
        container.auth.send_verify_code("admin@kcon.com")
        container.clock.advance(61)
        ok, msg, code = container.auth.send_verify_code("admin@kcon.com")
        assert ok is True
    def test_send_code_daily_limit(self, container):
        for _ in range(20):
            container.auth.send_verify_code("admin@kcon.com")
            container.clock.advance(61)
        ok, msg, code = container.auth.send_verify_code("admin@kcon.com")
        assert ok is False
        assert msg == "今日验证码发送次数已达上限"
    def test_send_code_invalid_created_at(self, container):
        container.auth.send_verify_code("admin@kcon.com")
        # 篡改创建时间为非法格式
        container.db.execute(
            "UPDATE verify_code SET created_at='bad' WHERE email='admin@kcon.com'")
        ok, msg, code = container.auth.send_verify_code("admin@kcon.com")
        assert ok is True
class TestLogin:
    def test_login_admin_success(self, container):
        _, _, code = container.auth.send_verify_code("admin@kcon.com")
        ok, msg, role, user = container.auth.login("admin@kcon.com", code)
        assert ok is True
        assert role == "admin"
        assert user["name"] == "系统管理员"
    def test_login_invalid_email(self, container):
        ok, msg, role, user = container.auth.login("bad", "123456")
        assert ok is False
        assert msg == "邮箱格式不正确"
        assert role is None
    def test_login_invalid_code_format(self, container):
        ok, msg, role, user = container.auth.login("admin@kcon.com", "abc")
        assert ok is False
        assert msg == "验证码必须为数字"
    def test_login_no_code_requested(self, container):
        ok, msg, role, user = container.auth.login("admin@kcon.com", "123456")
        assert ok is False
        assert msg == "请先获取验证码"
    def test_login_expired_code(self, container):
        _, _, code = container.auth.send_verify_code("admin@kcon.com")
        container.clock.advance(601)
        ok, msg, role, user = container.auth.login("admin@kcon.com", code)
        assert ok is False
        assert msg == "验证码已过期，请重新获取"
    def test_login_invalid_expires_format(self, container):
        _, _, code = container.auth.send_verify_code("admin@kcon.com")
        container.db.execute(
            "UPDATE verify_code SET expires_at='bad' WHERE email='admin@kcon.com'")
        ok, msg, role, user = container.auth.login("admin@kcon.com", code)
        assert ok is False
        assert msg == "验证码已过期，请重新获取"
    def test_login_too_many_fails(self, container):
        _, _, code = container.auth.send_verify_code("admin@kcon.com")
        container.db.execute(
            "UPDATE verify_code SET fail_count=5 WHERE email='admin@kcon.com'")
        ok, msg, role, user = container.auth.login("admin@kcon.com", code)
        assert ok is False
        assert msg == "验证码错误次数过多，请重新获取"
    def test_login_wrong_code(self, container):
        _, _, code = container.auth.send_verify_code("admin@kcon.com")
        wrong = "000000" if code != "000000" else "111111"
        ok, msg, role, user = container.auth.login("admin@kcon.com", wrong)
        assert ok is False
        assert "验证码错误，还可尝试 4 次" == msg
    def test_login_wrong_code_remaining_zero(self, container):
        _, _, code = container.auth.send_verify_code("admin@kcon.com")
        container.db.execute(
            "UPDATE verify_code SET fail_count=4 WHERE email='admin@kcon.com'")
        wrong = "000000" if code != "000000" else "111111"
        ok, msg, role, user = container.auth.login("admin@kcon.com", wrong)
        assert ok is False
        assert "还可尝试 0 次" in msg
    def test_login_approver_success(self, container, channel_factory):
        channel_factory(approver_email="ap@test.com")
        _, _, code = container.auth.send_verify_code("ap@test.com")
        ok, msg, role, user = container.auth.login("ap@test.com", code)
        assert ok is True
        assert role == "approver"
        assert user["email"] == "ap@test.com"
    def test_login_no_permission(self, container):
        _, _, code = container.auth.send_verify_code("nobody@test.com")
        ok, msg, role, user = container.auth.login("nobody@test.com", code)
        assert ok is False
        assert msg == "该邮箱无系统权限"
class TestStaffLogin:
    def test_staff_login_not_found(self, container):
        ok, msg, staff = container.auth.staff_login("nobody", "hash")
        assert ok is False
        assert msg == "账号或密码错误"
    def test_staff_login_wrong_password(self, container):
        container.repos.staff.insert("签到员", "staff1", "correct_hash", 1,
                                     container.clock.now_str())
        ok, msg, staff = container.auth.staff_login("staff1", "wrong_hash")
        assert ok is False
        assert msg == "账号或密码错误"
    def test_staff_login_success(self, container):
        container.repos.staff.insert("签到员", "staff1", "correct_hash", 1,
                                     container.clock.now_str())
        ok, msg, staff = container.auth.staff_login("staff1", "correct_hash")
        assert ok is True
        assert staff["account"] == "staff1"