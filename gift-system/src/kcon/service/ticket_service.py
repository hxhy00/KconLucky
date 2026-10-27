"""赠票服务：接收人注册、审批、注册码生成。"""
from typing import List, Optional, Tuple
from ..config import AppConfig
from ..domain import codes, rules, validators
from ..domain.enums import ChannelStatus, RecipientStatus, ReferrerStatus, TokenType
from ..infra.repositories import Repositories
class TicketService:
    """赠票服务。"""
    def __init__(self, repos: Repositories, clock, random_source, mailer,
                 qr_generator, config: AppConfig):
        self.repos = repos
        self.clock = clock
        self.random = random_source
        self.mailer = mailer
        self.qr = qr_generator
        self.config = config
    def register(self, referrer_code: str, name: str, company: str,
                 mobile: str, email: str) -> Tuple[bool, str]:
        """接收人注册（5 项提交时校验 + 发 M-04）。"""
        referrer = self.repos.referrer.find_by_code(referrer_code)
        if not referrer:
            return False, "该邀请链接已失效"
        channel = self.repos.channel.find_by_id(referrer["channel_id"])
        if not channel:
            return False, "该邀请链接已失效"
        # 链接可用性
        ok, msg = rules.check_referrer_available(referrer["status"], channel["status"])
        if not ok:
            return False, msg
        # 字段校验。手机号必填：它是主系统参会名单的业务主键，
        # 选填会导致审批通过后导出 CSV 时被「缺手机号」剔除，人在交接环节静默丢失
        for ok, msg in (
            validators.validate_name(name),
            validators.validate_company(company),
            validators.validate_mobile(mobile, required=True),
            validators.validate_email(email),
        ):
            if not ok:
                return False, msg
        email = email.strip().lower()
        # 邮箱冲突（提交时拦截）
        existing = self.repos.recipient.find_by_email(email)
        if existing:
            other = self.repos.referrer.find_by_id(existing["referrer_id"])
            other_name = other["name"] if other else ""
            return rules.check_email_conflict(
                existing["referrer_id"], referrer["id"], existing["status"],
                other_name)
        # 单人上限
        ok, msg = rules.check_referrer_cap(referrer["issued_count"],
                                           channel["referrer_cap"])
        if not ok:
            return False, msg
        # 渠道配额
        used = self.repos.recipient.count_used_by_channel(channel["id"])
        ok, msg = rules.check_channel_quota(used, channel["quota"])
        if not ok:
            return False, msg
        # 创建
        now = self.clock.now_str()
        recipient_id = self.repos.recipient.insert(
            referrer["id"], channel["id"], name.strip(), company.strip(),
            (mobile or "").strip(), email, now)
        # 一次性令牌
        token = self.random.token()
        self.repos.token.insert(token, TokenType.TICKET_APPROVE, recipient_id,
                                referrer["id"],
                                rules.calc_token_expires(self.clock.now()), now)
        # 发 M-04
        self.mailer.send("M-04", referrer["email"],
                         f'【KCon2026赠票申请】「{name}」申请KCon2026大会赠票，请审批！', {
            "recipient_name": name, "company": company,
            "mobile": mobile or "", "email": email, "created_at": now,
            "issued_count": referrer["issued_count"],
            "referrer_cap": channel["referrer_cap"],
            "approve_url": codes.build_approve_url(
                self.config.site_url, "ticket", token),
            "resend_url": f"{self.config.site_url}/resend/{token}",
        })
        return True, "申请已提交，等待推荐人确认"
    def approve(self, token: str, action: str = "approve") -> Tuple[bool, str, Optional[dict]]:
        """推荐人审批赠票（生成注册码 + 发 M-05/M-06）。"""
        record = self.repos.token.find_by_token(token)
        if not record:
            return False, "链接无效", None
        ok, msg = rules.check_token_type(record["type"], TokenType.TICKET_APPROVE)
        if not ok:
            return False, msg, None
        ok, msg = rules.check_token_usable(record["used_at"], record["expires_at"],
                                           self.clock.now())
        if not ok:
            return False, msg, None
        recipient = self.repos.recipient.find_by_id(record["target_id"])
        if not recipient:
            return False, "申请不存在", None
        if recipient["status"] != RecipientStatus.PENDING:
            return False, "该申请已处理", None
        referrer = self.repos.referrer.find_by_id(recipient["referrer_id"])
        channel = self.repos.channel.find_by_id(recipient["channel_id"])
        # 复核上限与配额必须先于令牌消费：失败时令牌保持可用，
        # 推荐人可补充额度后重试；否则令牌被烧、申请永久卡在待审批
        if action == "approve":
            ok, msg = rules.check_referrer_cap(referrer["issued_count"],
                                               channel["referrer_cap"])
            if not ok:
                return False, msg, None
            used = self.repos.recipient.count_used_by_channel(channel["id"])
            ok, msg = rules.check_channel_quota(used, channel["quota"])
            if not ok:
                return False, msg, None
        if self.repos.token.mark_used(token, self.clock.now_str()) != 1:
            return False, "该链接已被使用", None
        if action != "approve":
            # 条件更新：仅 PENDING → REJECTED，rows=0 说明并发下已被处理
            if self.repos.recipient.update_status_from(
                    recipient["id"], RecipientStatus.PENDING,
                    RecipientStatus.REJECTED) != 1:
                return False, "该申请已被处理", None
            return True, f"已拒绝「{recipient['name']}」的赠票申请", recipient
        now = self.clock.now_str()
        # 条件更新：仅 PENDING → APPROVED。并发审批同一申请时只有一方成功，
        # 失败方在此终止，不会双次累加已发数/超发配额
        if self.repos.recipient.update_status_from(
                recipient["id"], RecipientStatus.PENDING,
                RecipientStatus.APPROVED, now) != 1:
            return False, "该申请已被处理", None
        self.repos.referrer.increment_issued(referrer["id"])
        referrer = self.repos.referrer.find_by_id(referrer["id"])
        # 生成注册码
        code = self.random.registration_code()
        code_url = codes.build_code_url(self.config.site_url, code)
        qr_datauri = self.qr.generate_datauri(
            code_url, f"参会人：{recipient['name']}", f"注册码：{code}",
            "现场出示此码签到并领取资料")
        self.repos.code.insert(recipient["id"], code, code_url, code_url, now)
        self.repos.audit.log("referrer", referrer["id"], "approve_recipient",
                             "recipient", recipient["id"], "", "", now)
        # 发 M-05 / M-06
        self.mailer.send("M-05", recipient["email"],
                         '【KCon2026】恭喜获得KCon2026参会资格，期待莅临现场共同研讨'
                         '"AI的安全风险与安全的AI应用"', {
            "recipient_name": recipient["name"], "qr": qr_datauri,
            "code": code, "referrer_name": referrer["name"],
        })
        self.mailer.send("M-06", referrer["email"],
                         f'【KCon2026】您赠与「{recipient["name"]}」的KCon2026门票成功注册！', {
            "referrer_name": referrer["name"], "recipient_name": recipient["name"],
            "company": recipient["company"], "mobile": recipient["mobile"],
            "email": recipient["email"], "code_url": code_url, "qr": qr_datauri,
            "issued_count": referrer["issued_count"],
            "referrer_cap": channel["referrer_cap"],
        })
        return True, f"已通过「{recipient['name']}」的赠票注册", recipient
    def list(self, channel_id: Optional[int] = None,
             referrer_id: Optional[int] = None,
             status: Optional[int] = None) -> List[dict]:
        """接收人列表。"""
        return self.repos.recipient.list(channel_id, referrer_id, status)
    def get_by_code(self, code: str) -> Optional[dict]:
        """按注册码查询。"""
        return self.repos.code.find_by_code(code)