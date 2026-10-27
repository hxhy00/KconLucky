"""渠道服务：创建、停用、删除、查询。"""
from typing import List, Optional, Tuple
from ..config import AppConfig
from ..domain import codes, rules, validators
from ..domain.enums import ChannelStatus, ReferrerStatus, TokenType
from ..infra.repositories import Repositories
class ChannelService:
    """渠道服务。"""
    def __init__(self, repos: Repositories, clock, random_source,
                 mailer, config: AppConfig):
        self.repos = repos
        self.clock = clock
        self.random = random_source
        self.mailer = mailer
        self.config = config
    def create(self, name: str, approver_name: str, approver_email: str,
               quota, referrer_cap, remark: str = "") -> Tuple[bool, str]:
        """创建渠道（含审批员账号 + 发 M-01）。"""
        # 字段校验
        for ok, msg in (
            validators.validate_channel_name(name),
            validators.validate_approver_name(approver_name),
            validators.validate_email(approver_email),
            validators.validate_quota(quota),
            validators.validate_cap(referrer_cap),
            validators.validate_remark(remark),
        ):
            if not ok:
                return False, msg
        name = name.strip()
        approver_email = approver_email.strip().lower()
        quota_int = int(quota)
        cap_int = int(referrer_cap)
        # 唯一性
        if self.repos.channel.find_by_name(name):
            return False, "渠道名称已存在"
        if self.repos.approver.email_exists(approver_email):
            return False, "该邮箱已被其他渠道使用"
        # 规模约束
        ok, msg = rules.check_channel_limit(self.repos.channel.count_active())
        if not ok:
            return False, msg
        ok, msg = rules.check_total_ticket_limit(self.repos.channel.sum_quota(), quota_int)
        if not ok:
            return False, msg
        # 创建
        now = self.clock.now_str()
        channel_code = self.random.channel_code()
        channel_id = self.repos.channel.insert(name, channel_code, quota_int,
                                               cap_int, remark or "", now)
        self.repos.approver.insert(channel_id, approver_name.strip(),
                                   approver_email, now)
        self.repos.audit.log("admin", None, "create_channel", "channel",
                             channel_id, f"{name} / {approver_email}", "", now)
        # 发 M-01
        self.mailer.send("M-01", approver_email, "【KCon2026】您的渠道管理账号已开通", {
            "approver_name": approver_name,
            "approver_email": approver_email,
            "channel_name": name,
            "quota": quota_int,
            "referrer_cap": cap_int,
            "register_url": codes.build_channel_register_url(
                self.config.site_url, channel_code),
            "login_url": f"{self.config.site_url}/login",
        })
        return True, f"渠道「{name}」创建成功，已发送开通邮件"
    def suspend(self, channel_id: int) -> Tuple[bool, str]:
        """停用渠道（软删除）。"""
        channel = self.repos.channel.find_by_id(channel_id)
        if not channel:
            return False, "渠道不存在"
        if channel["status"] == ChannelStatus.SUSPENDED:
            return False, "渠道已处于停用状态"
        self.repos.channel.update_status(channel_id, ChannelStatus.SUSPENDED,
                                         self.clock.now_str())
        self.repos.audit.log("admin", None, "suspend_channel", "channel",
                             channel_id, "", "", self.clock.now_str())
        return True, "渠道已停用，链接立即失效"
    def delete(self, channel_id: int) -> Tuple[bool, str]:
        """物理删除渠道（仅限无关联数据）。"""
        channel = self.repos.channel.find_by_id(channel_id)
        if not channel:
            return False, "渠道不存在"
        if self.repos.channel.count_related(channel_id) > 0:
            return False, "该渠道已有数据，无法物理删除，请使用停用"
        self.repos.channel.update_status(channel_id, ChannelStatus.DELETED,
                                         self.clock.now_str())
        self.repos.approver.disable_by_channel(channel_id)
        self.repos.audit.log("admin", None, "delete_channel", "channel",
                             channel_id, "", "", self.clock.now_str())
        return True, "渠道已删除"
    def list_all(self) -> List[dict]:
        """渠道列表。"""
        return self.repos.channel.list_all()
    def get(self, channel_id: int) -> Optional[dict]:
        """按 id 查询渠道。"""
        return self.repos.channel.find_by_id(channel_id)
    def get_by_code(self, channel_code: str) -> Optional[dict]:
        """按渠道码查询（仅启用状态）。"""
        return self.repos.channel.find_by_code(channel_code)
    def replace_approver(self, channel_id: int, new_name: str,
                         new_email: str) -> Tuple[bool, str]:
        """更换渠道审批员：停用原审批员 + 新建 + 作废旧令牌 + 发 M-01。

        换人后，原审批员的未使用审批令牌全部失效，新申请发给新审批员。
        """
        # 字段校验
        ok, msg = validators.validate_approver_name(new_name)
        if not ok:
            return False, msg
        ok, msg = validators.validate_email(new_email)
        if not ok:
            return False, msg
        new_email = new_email.strip().lower()
        channel = self.repos.channel.find_by_id(channel_id)
        if not channel:
            return False, "渠道不存在"
        if channel["status"] != ChannelStatus.ACTIVE:
            return False, "该渠道已停用，无法更换审批员"
        # 新邮箱不能已被其他渠道占用（含已停用行：approver.email 全局唯一，
        # 只查启用行会在「A→B→A」场景误判可插入，触发 UNIQUE 崩溃）
        other = self.repos.approver.email_exists_any(new_email)
        if other and other["channel_id"] != channel_id:
            return False, "该邮箱已被其他渠道使用"
        now = self.clock.now_str()
        # 若新邮箱正是本渠道当前启用审批员，视为无需更换
        current = self.repos.approver.find_by_channel(channel_id)
        if current and current["email"].lower() == new_email:
            return False, "该邮箱已是本渠道审批员"
        # 停用原审批员
        self.repos.approver.disable_by_channel(channel_id)
        # 启用/新建审批员：邮箱在本渠道有历史行（曾被停用）则复用原行，
        # 否则触发 approver.email UNIQUE 约束导致 500，且渠道此刻已无启用审批人
        existing = self.repos.approver.email_exists_any(new_email)
        if existing:
            self.repos.approver.activate(existing["id"], new_name.strip(), now)
        else:
            self.repos.approver.insert(channel_id, new_name.strip(), new_email, now)
        # 先收集本渠道待审批的推荐人（换人后需重新签发令牌给新审批员）
        pending_referrers = [
            r for r in self.repos.referrer.list_by_channel(channel_id)
            if r["status"] == ReferrerStatus.PENDING
        ]
        # 作废本渠道所有未使用的推荐人审批令牌（防旧链接被离职人员使用）
        invalidated = self.repos.token.invalidate_by_owner(
            TokenType.REFERRER_APPROVE, channel_id, now)
        # 为每个待审批推荐人重新签发令牌并发送给新审批员
        reissued = 0
        for ref in pending_referrers:
            new_token = self.random.token()
            self.repos.token.insert(
                new_token, TokenType.REFERRER_APPROVE, ref["id"], channel_id,
                rules.calc_token_expires(self.clock.now()), now)
            self.mailer.send("M-02", new_email,
                             f'【KCon2026】「{ref["name"]}」申请成为赠票推荐人，请审批', {
                "referrer_name": ref["name"], "company": ref["company"],
                "department": ref["department"], "mobile": ref["mobile"],
                "email": ref["email"], "created_at": ref["created_at"],
                "approve_url": codes.build_approve_url(
                    self.config.site_url, "referrer", new_token),
                "login_url": f"{self.config.site_url}/login",
            })
            reissued += 1
        self.repos.audit.log("admin", None, "replace_approver", "channel",
                             channel_id,
                             f"{current['email'] if current else '无'} -> {new_email}；"
                             f"作废旧令牌 {invalidated} 个，重签 {reissued} 个", "", now)
        # 给新审批员发开通通知 M-01
        self.mailer.send("M-01", new_email, "【KCon2026】您的渠道管理账号已开通", {
            "approver_name": new_name,
            "approver_email": new_email,
            "channel_name": channel["name"],
            "quota": channel["quota"],
            "referrer_cap": channel["referrer_cap"],
            "register_url": codes.build_channel_register_url(
                self.config.site_url, channel["code"]),
            "login_url": f"{self.config.site_url}/login",
        })
        return True, (f"审批员已更换为「{new_name}」，"
                      f"作废旧令牌 {invalidated} 个，重签发 {reissued} 个")