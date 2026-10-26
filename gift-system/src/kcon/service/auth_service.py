"""认证服务：验证码发送、登录校验、会话管理。"""
from typing import Dict, Optional, Tuple
from ..config import AppConfig, md5_email
from ..domain import rules, validators
from ..domain.enums import ReferrerStatus
from ..infra.repositories import Repositories
class AuthService:
    """认证服务。"""
    def __init__(self, repos: Repositories, clock, random_source, config: AppConfig):
        self.repos = repos
        self.clock = clock
        self.random = random_source
        self.config = config
    def send_verify_code(self, email: str, scene: str = "login") -> Tuple[bool, str, Optional[str]]:
        """发送验证码。
        Returns:
            (是否成功, 消息, 验证码明文[仅 Demo 返回])
        """
        ok, msg = validators.validate_email(email)
        if not ok:
            return False, msg, None
        email = email.strip().lower()
        # 频率限制
        last = self.repos.verify_code.latest(email)
        if last:
            from datetime import datetime
            try:
                last_dt = datetime.strptime(last["created_at"], "%Y-%m-%d %H:%M:%S")
                elapsed = (self.clock.now() - last_dt).total_seconds()
            except (ValueError, TypeError):
                elapsed = self.config.code_resend_interval
            if elapsed < self.config.code_resend_interval:
                remain = int(self.config.code_resend_interval - elapsed)
                return False, f"请 {remain} 秒后再试", None
        # 单日上限
        day_prefix = self.clock.now_str()[:10]
        today_count = self.repos.verify_code.count_today(email, day_prefix)
        if today_count >= self.config.code_daily_limit:
            return False, "今日验证码发送次数已达上限", None
        # 生成并保存
        code = self.random.verify_code(self.config.code_length)
        expires_at = self.clock.expires_at(self.config.code_ttl_seconds)
        self.repos.verify_code.insert(email, code, scene, expires_at,
                                      self.clock.now_str())
        return True, "验证码已发送", code
    def login(self, email: str, code: str) -> Tuple[bool, str, Optional[str], Optional[dict]]:
        """验证码登录。
        Returns:
            (是否成功, 消息, 角色[admin/approver], 用户信息)
        """
        ok, msg = validators.validate_email(email)
        if not ok:
            return False, msg, None, None
        ok, msg = validators.validate_verify_code(code, self.config.code_length)
        if not ok:
            return False, msg, None, None
        email = email.strip().lower()
        record = self.repos.verify_code.latest_unused(email)
        if not record:
            return False, "请先获取验证码", None, None
        # 过期校验
        from datetime import datetime
        try:
            expires_dt = datetime.strptime(record["expires_at"], "%Y-%m-%d %H:%M:%S")
        except (ValueError, TypeError):
            return False, "验证码已过期，请重新获取", None, None
        if self.clock.now() >= expires_dt:
            return False, "验证码已过期，请重新获取", None, None
        # 失败次数
        if record["fail_count"] >= self.config.code_max_fail:
            return False, "验证码错误次数过多，请重新获取", None, None
        # 校验码值
        if record["code"] != code.strip():
            self.repos.verify_code.increment_fail(record["id"])
            remain = self.config.code_max_fail - record["fail_count"] - 1
            return False, f"验证码错误，还可尝试 {max(remain, 0)} 次", None, None
        self.repos.verify_code.mark_used(record["id"])
        # 判定角色：管理员从 admin 表查（MD5 匹配 + 启用状态），不依赖配置白名单
        admin = self.repos.admin.find_by_md5(md5_email(email))
        if admin:
            return True, "登录成功", "admin", {
                "id": admin["id"], "email": admin["email"] or email, "name": admin["name"]}
        approver = self.repos.approver.find_by_email(email)
        if approver:
            self.repos.approver.update_last_login(approver["id"], self.clock.now_str())
            return True, "登录成功", "approver", approver
        return False, "该邮箱无系统权限", None, None
    def staff_login(self, account: str, password_hash: str) -> Tuple[bool, str, Optional[dict]]:
        """签到工作人员登录（密码哈希由调用方校验）。"""
        staff = self.repos.staff.find_by_account(account)
        if not staff:
            return False, "账号或密码错误", None
        if staff["password_hash"] != password_hash:
            return False, "账号或密码错误", None
        return True, "登录成功", staff