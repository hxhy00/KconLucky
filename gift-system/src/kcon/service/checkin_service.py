"""签到服务：查询、签到、外部同步、M-08 汇总。"""
from typing import Dict, List, Optional, Tuple
from ..config import AppConfig
from ..domain import codes, rules
from ..domain.enums import ExternalSyncStatus, RecipientStatus
from ..domain.models import CheckinLookupResult
from ..infra.repositories import Repositories
class CheckinService:
    """签到服务。"""
    def __init__(self, repos: Repositories, clock, mailer,
                 external_adapter, config: AppConfig):
        self.repos = repos
        self.clock = clock
        self.mailer = mailer
        self.external = external_adapter
        self.config = config
    def lookup(self, code: str) -> Tuple[bool, str, Optional[CheckinLookupResult]]:
        """查询注册码（签到前核验）。"""
        normalized = codes.normalize_registration_code(code)
        if not normalized:
            return False, "请输入注册码", None
        record = self.repos.code.find_by_code(normalized)
        if not record:
            return False, "注册码无效，请核对", None
        recipient = self.repos.recipient.find_by_id(record["recipient_id"])
        if not recipient:
            return False, "注册码无效，请核对", None
        # 已签到状态放行（由 confirm 判断重复），其余非法状态拦截
        if recipient["status"] != RecipientStatus.CHECKED_IN:
            ok, msg = rules.check_recipient_checkinable(recipient["status"])
            if not ok:
                return False, msg, None
        referrer = self.repos.referrer.find_by_id(recipient["referrer_id"])
        channel = self.repos.channel.find_by_id(recipient["channel_id"])
        checkin = self.repos.checkin.find_by_recipient(recipient["id"])
        from ..domain.enums import recipient_status_text
        result = CheckinLookupResult(
            recipient_id=recipient["id"], name=recipient["name"],
            company=recipient["company"], code=record["code"],
            status=recipient["status"],
            status_text=recipient_status_text(recipient["status"]),
            referrer_name=referrer["name"] if referrer else "",
            channel_name=channel["name"] if channel else "",
            checked_at=checkin["checked_at"] if checkin else None)
        return True, "查询成功", result
    def confirm(self, code: str, staff_id: Optional[int] = None,
                device_ip: str = "") -> Tuple[bool, str, Optional[CheckinLookupResult]]:
        """确认签到（发材料 + 异步外部同步 + M-07/M-08）。"""
        ok, msg, result = self.lookup(code)
        if not ok:
            return False, msg, None
        # 重复签到（返回 result 供前端展示已签到信息）
        ok, msg = rules.check_duplicate_checkin(result.status, result.checked_at)
        if not ok:
            return False, msg, result
        now = self.clock.now_str()
        self.repos.checkin.insert(result.recipient_id, now, staff_id, device_ip)
        self.repos.recipient.update_status(result.recipient_id,
                                           RecipientStatus.CHECKED_IN)
        # 异步外部同步（失败不阻断）
        self._sync_external(result.recipient_id, now)
        recipient = self.repos.recipient.find_by_id(result.recipient_id)
        referrer = self.repos.referrer.find_by_id(recipient["referrer_id"])
        channel = self.repos.channel.find_by_id(recipient["channel_id"])
        # M-07 实时
        self.mailer.send("M-07", referrer["email"],
                         f'【KCon2026】您邀请的参会人「{recipient["name"]}」已到场签到！', {
            "referrer_name": referrer["name"], "recipient_name": recipient["name"],
            "company": recipient["company"], "mobile": recipient["mobile"],
            "email": recipient["email"], "checked_at": now,
        })
        # M-08 入队 + 尝试汇总
        self.repos.m08.enqueue(channel["id"], recipient["id"], now)
        self.try_flush_m08(channel["id"])
        self.repos.audit.log("staff", staff_id, "checkin", "recipient",
                             recipient["id"], "", device_ip, now)
        result.status = RecipientStatus.CHECKED_IN
        result.checked_at = now
        return True, f'签到成功，请发放参会材料（{recipient["name"]} / {recipient["company"]}）', result
    def _sync_external(self, recipient_id: int, now: str) -> None:
        """调用外部签到系统（失败仅记录）。"""
        recipient = self.repos.recipient.find_by_id(recipient_id)
        referrer = self.repos.referrer.find_by_id(recipient["referrer_id"])
        channel = self.repos.channel.find_by_id(recipient["channel_id"])
        info = {
            "name": recipient["name"], "company": recipient["company"],
            "email": recipient["email"], "mobile": recipient["mobile"],
            "channel_name": channel["name"] if channel else "",
            "referrer_name": referrer["name"] if referrer else "",
        }
        ok, error = self.external.push_checkin(info, now)
        self.repos.checkin.update_external(
            recipient_id,
            ExternalSyncStatus.SUCCESS if ok else ExternalSyncStatus.FAILED,
            now, error or "")
    def try_flush_m08(self, channel_id: int, force: bool = False) -> bool:
        """尝试发送 M-08 汇总（满 10 人或超 30 分钟）。"""
        pending = self.repos.m08.pending(channel_id)
        if not pending:
            return False
        first_created = pending[0]["created_at"] if pending else None
        should = force or rules.should_flush_m08(len(pending), first_created,
                                                  self.clock.now())
        if not should:
            return False
        channel = self.repos.channel.find_by_id(channel_id)
        approver = self.repos.approver.find_by_channel(channel_id)
        if not approver:
            return False
        new_checkins: List[Dict] = []
        for item in pending:
            recipient = self.repos.recipient.find_by_id(item["recipient_id"])
            referrer = self.repos.referrer.find_by_id(recipient["referrer_id"])
            checkin = self.repos.checkin.find_by_recipient(recipient["id"])
            new_checkins.append({
                "referrer_name": referrer["name"],
                "referrer_department": referrer["department"],
                "recipient_name": recipient["name"],
                "recipient_company": recipient["company"],
                "checked_at": checkin["checked_at"] if checkin else "",
            })
        referrers = []
        for ref in self.repos.referrer.list_by_channel(channel_id):
            referrers.append({
                "name": ref["name"], "department": ref["department"],
                "invited": ref["approved_count"], "checked_in": ref["checked_in_count"],
            })
        last = new_checkins[-1]
        self.mailer.send("M-08", approver["email"],
                         f'【KCon2026】「{last["referrer_name"]}」邀请的'
                         f'「{last["recipient_name"]}」已到场签到，请关注', {
            "approver_name": approver["name"],
            "referrer_company": "", "referrer_department": last["referrer_department"],
            "referrer_name": last["referrer_name"],
            "recipient_company": last["recipient_company"],
            "recipient_name": last["recipient_name"],
            "period_start": pending[0]["created_at"],
            "period_end": pending[-1]["created_at"],
            "period_count": len(new_checkins),
            "new_checkins": new_checkins, "referrers": referrers,
        })
        self.repos.m08.mark_sent([item["id"] for item in pending])
        return True
    def code_info(self, code: str, authenticated: bool = False) -> Tuple[bool, str, Optional[dict]]:
        """扫码落地页信息（分级展示）。"""
        normalized = codes.normalize_registration_code(code)
        if not normalized:
            return False, "注册码无效", None
        record = self.repos.code.find_by_code(normalized)
        if not record:
            return False, "注册码无效", None
        recipient = self.repos.recipient.find_by_id(record["recipient_id"])
        if not recipient:
            return False, "注册码无效", None
        checkin = self.repos.checkin.find_by_recipient(recipient["id"])
        from ..domain.enums import recipient_status_text
        data = {
            "valid": True,
            "checked_in": recipient["status"] == RecipientStatus.CHECKED_IN,
            "checked_at": checkin["checked_at"] if checkin else None,
            "status_text": recipient_status_text(recipient["status"]),
        }
        # 分级：仅已登录（签到员/管理员）展示个人信息
        if authenticated:
            data.update({"name": recipient["name"], "company": recipient["company"],
                         "code": record["code"]})
        return True, "查询成功", data