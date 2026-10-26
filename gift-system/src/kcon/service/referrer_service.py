"""推荐人服务：注册、审批、查询。"""
from typing import List, Optional, Tuple
from ..config import AppConfig
from ..domain import codes, rules, validators
from ..domain.enums import ReferrerStatus, TokenType
from ..infra.repositories import Repositories
class ReferrerService:
    """推荐人服务。"""
    def __init__(self, repos: Repositories, clock, random_source, mailer,
                 qr_generator, config: AppConfig):
        self.repos = repos
        self.clock = clock
        self.random = random_source
        self.mailer = mailer
        self.qr = qr_generator
        self.config = config
    def register(self, channel_code: str, name: str, company: str,
                 department: str, mobile: str, email: str) -> Tuple[bool, str]:
        """推荐人注册（提交申请 + 发 M-02）。"""
        channel = self.repos.channel.find_by_code(channel_code)
        if not channel:
            return False, "该注册链接已失效"
        # 字段校验
        for ok, msg in (
            validators.validate_name(name),
            validators.validate_company(company),
            validators.validate_department(department),
            validators.validate_mobile(mobile, required=True),
            validators.validate_email(email),
        ):
            if not ok:
                return False, msg
        email = email.strip().lower()
        now = self.clock.now_str()
        # 邮箱唯一 + 被拒重申
        existing = self.repos.referrer.find_by_email(email)
        resubmit = False
        if existing:
            if existing["status"] == ReferrerStatus.REJECTED:
                ok, msg = rules.check_reject_retry(existing["rejected_at"],
                                                   self.clock.now())
                if not ok:
                    return False, msg
                resubmit = True
            else:
                return False, "该邮箱已注册为推荐人"
        # 创建或重申
        referrer_code = self.random.referrer_code()
        if resubmit:
            # 复用原记录，重置状态；作废旧令牌
            referrer_id = existing["id"]
            self.repos.referrer.update_for_resubmit(
                referrer_id, name.strip(), company.strip(), department.strip(),
                mobile.strip(), referrer_code)
            self.repos.token.invalidate_by_target(TokenType.REFERRER_APPROVE,
                                                  referrer_id, now)
        else:
            referrer_id = self.repos.referrer.insert(
                channel["id"], name.strip(), company.strip(), department.strip(),
                mobile.strip(), email, referrer_code, now)
        # 一次性审批令牌
        token = self.random.token()
        self.repos.token.insert(token, TokenType.REFERRER_APPROVE, referrer_id,
                                channel["id"],
                                rules.calc_token_expires(self.clock.now()), now)
        # 发 M-02
        approver = self.repos.approver.find_by_channel(channel["id"])
        if approver:
            self.mailer.send("M-02", approver["email"],
                             f'【KCon2026】「{name}」申请成为赠票推荐人，请审批', {
                "referrer_name": name, "company": company,
                "department": department, "mobile": mobile, "email": email,
                "created_at": now,
                "approve_url": codes.build_approve_url(
                    self.config.site_url, "referrer", token),
                "login_url": f"{self.config.site_url}/login",
            })
        return True, "申请已提交，请等待渠道审批员审批"
    def approve(self, token: str, action: str = "approve",
                operator_id: Optional[int] = None) -> Tuple[bool, str, Optional[dict]]:
        """审批推荐人（邮件链接或系统按钮）。"""
        record = self.repos.token.find_by_token(token)
        if not record:
            return False, "链接无效", None
        ok, msg = rules.check_token_type(record["type"], TokenType.REFERRER_APPROVE)
        if not ok:
            return False, msg, None
        ok, msg = rules.check_token_usable(record["used_at"], record["expires_at"],
                                           self.clock.now())
        if not ok:
            return False, msg, None
        referrer = self.repos.referrer.find_by_id(record["target_id"])
        if not referrer:
            return False, "推荐人不存在", None
        if referrer["status"] != ReferrerStatus.PENDING:
            return False, "该申请已处理", None
        # 原子标记令牌已用
        if self.repos.token.mark_used(token, self.clock.now_str()) != 1:
            return False, "该链接已被使用", None
        now = self.clock.now_str()
        channel = self.repos.channel.find_by_id(referrer["channel_id"])
        if action == "approve":
            self.repos.referrer.update_status(referrer["id"], ReferrerStatus.APPROVED,
                                              now, operator_id)
            self.repos.audit.log("approver", record["owner_id"], "approve_referrer",
                                 "referrer", referrer["id"], "", "", now)
            # 发 M-03
            referral_url = codes.build_referral_url(self.config.site_url,
                                                    referrer["code"])
            qr_datauri = self.qr.generate_datauri(
                referral_url, f"推荐人：{referrer['name']}",
                f"渠道：{channel['name']}", "扫码注册 KCon2026 赠票")
            self.mailer.send("M-03", referrer["email"], "请查收KCon专属赠票链接", {
                "referrer_name": referrer["name"], "referral_url": referral_url,
                "qr": qr_datauri, "channel_name": channel["name"],
                "referrer_cap": channel["referrer_cap"],
                "issued_count": referrer["issued_count"],
            })
            return True, f"已通过「{referrer['name']}」的推荐人注册", referrer
        self.repos.referrer.update_status(referrer["id"], ReferrerStatus.REJECTED, now)
        self.repos.audit.log("approver", record["owner_id"], "reject_referrer",
                             "referrer", referrer["id"], "", "", now)
        approver = self.repos.approver.find_by_channel(referrer["channel_id"])
        self.mailer.send("M-03R", referrer["email"], "【KCon2026】您的推荐人申请未通过", {
            "referrer_name": referrer["name"], "channel_name": channel["name"],
            "rejected_at": now,
            "approver_email": approver["email"] if approver else "",
        })
        return True, f"已拒绝「{referrer['name']}」的推荐人申请", referrer
    def list_by_channel(self, channel_id: Optional[int] = None,
                        status: Optional[int] = None) -> List[dict]:
        """推荐人列表。"""
        return self.repos.referrer.list_by_channel(channel_id, status)
    def get(self, referrer_id: int) -> Optional[dict]:
        """按 id 查询。"""
        return self.repos.referrer.find_by_id(referrer_id)
    def get_by_code(self, referrer_code: str) -> Optional[dict]:
        """按推荐人码查询。"""
        return self.repos.referrer.find_by_code(referrer_code)
    def approve_by_id(self, referrer_id: int, action: str,
                      channel_id: int) -> Tuple[bool, str]:
        """系统内审批（校验归属渠道，防越权）。"""
        referrer = self.repos.referrer.find_by_id(referrer_id)
        if not referrer:
            return False, "推荐人不存在"
        if referrer["channel_id"] != channel_id:
            return False, "无权操作该推荐人"
        token_record = self.repos.token.find_active(TokenType.REFERRER_APPROVE,
                                                    referrer_id)
        if not token_record:
            return False, "该申请已处理或令牌已失效"
        ok, msg, _ = self.approve(token_record["token"], action)
        return ok, msg