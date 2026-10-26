"""业务规则：纯函数，不依赖 I/O，便于 100% 覆盖。"""
from datetime import datetime, timedelta
from typing import Optional, Tuple
from .enums import ChannelStatus, RecipientStatus, ReferrerStatus, TokenType
# 常量
MAX_ADMINS = 3
MAX_CHANNELS = 20
TOTAL_TICKET_LIMIT = 500
TOKEN_TTL_DAYS = 7
REJECT_RETRY_DAYS = 7
M08_BATCH_SIZE = 10
M08_WINDOW_MINUTES = 30
TIME_FMT = "%Y-%m-%d %H:%M:%S"
def check_admin_limit(current_count: int) -> Tuple[bool, str]:
    """校验管理员数量上限（≤3）。"""
    if current_count >= MAX_ADMINS:
        return False, f"管理员数量已达上限（{MAX_ADMINS} 个）"
    return True, ""
def check_channel_limit(current_count: int) -> Tuple[bool, str]:
    """校验渠道数量上限（≤20）。"""
    if current_count >= MAX_CHANNELS:
        return False, f"渠道数量已达上限（{MAX_CHANNELS} 个）"
    return True, ""
def check_total_ticket_limit(current_total: int, add_quota: int) -> Tuple[bool, str]:
    """校验总赠票配额上限（≤500）。"""
    if current_total + add_quota > TOTAL_TICKET_LIMIT:
        return False, (
            f"总赠票配额将超过上限（{TOTAL_TICKET_LIMIT} 张），"
            f"当前已分配 {current_total} 张，本次申请 {add_quota} 张"
        )
    return True, ""
def check_referrer_cap(issued_count: int, cap: int) -> Tuple[bool, str]:
    """校验推荐人单人上限。"""
    if issued_count >= cap:
        return False, f"该邀请链接赠票已发完（上限 {cap} 张），请联系推荐人"
    return True, ""
def check_channel_quota(used_count: int, quota: int) -> Tuple[bool, str]:
    """校验渠道配额。"""
    if used_count >= quota:
        return False, "本渠道赠票已发完，请联系渠道负责人"
    return True, ""
def check_referrer_available(status: int, channel_status: int) -> Tuple[bool, str]:
    """校验推荐人链接是否可用（推荐人已通过 + 渠道启用）。"""
    if status != ReferrerStatus.APPROVED:
        return False, "该邀请链接已失效"
    if channel_status != ChannelStatus.ACTIVE:
        return False, "该渠道已停用"
    return True, ""
def check_email_conflict(existing_referrer_id: Optional[int],
                         current_referrer_id: int,
                         existing_status: int,
                         existing_referrer_name: str = "") -> Tuple[bool, str]:
    """校验邮箱冲突（提交时拦截）。
    Args:
        existing_referrer_id: 已存在记录所属推荐人 id，None 表示无冲突
        current_referrer_id: 当前推荐人 id
        existing_status: 已存在记录状态
        existing_referrer_name: 已存在记录所属推荐人姓名
    """
    if existing_referrer_id is None:
        return True, ""
    from .enums import recipient_status_text
    if existing_referrer_id == current_referrer_id:
        return False, (
            f"您已为该邮箱提交过申请，当前状态：{recipient_status_text(existing_status)}"
        )
    return False, f"该邮箱已由「{existing_referrer_name}」推荐注册，请勿重复提交"
def check_reject_retry(rejected_at: Optional[str], now: datetime) -> Tuple[bool, str]:
    """校验被拒后 7 天重申规则。
    Args:
        rejected_at: 被拒时间字符串，None 表示未被拒
        now: 当前时间
    """
    if not rejected_at:
        return True, ""
    try:
        rejected_dt = datetime.strptime(rejected_at, TIME_FMT)
    except (ValueError, TypeError):
        return True, ""
    retry_at = rejected_dt + timedelta(days=REJECT_RETRY_DAYS)
    if now < retry_at:
        return False, f"您的申请已被拒绝，请于 {retry_at.strftime('%m月%d日')} 后再试"
    return True, ""
def check_token_usable(used_at: Optional[str], expires_at: str,
                       now: datetime) -> Tuple[bool, str]:
    """校验一次性令牌是否可用。"""
    if used_at:
        return False, "该链接已被使用"
    try:
        expires_dt = datetime.strptime(expires_at, TIME_FMT)
    except (ValueError, TypeError):
        return False, "链接已过期"
    if now >= expires_dt:
        return False, "链接已过期"
    return True, ""
def check_token_owner(token_owner_id: int, actor_id: int) -> Tuple[bool, str]:
    """校验令牌归属（防越权）。"""
    if token_owner_id != actor_id:
        return False, "无权操作该申请"
    return True, ""
def check_token_type(token_type: int, expected: TokenType) -> Tuple[bool, str]:
    """校验令牌类型。"""
    if token_type != expected:
        return False, "链接类型不匹配"
    return True, ""
def calc_token_expires(now: datetime) -> str:
    """计算令牌过期时间（7 天后）。"""
    return (now + timedelta(days=TOKEN_TTL_DAYS)).strftime(TIME_FMT)
def check_recipient_checkinable(status: int) -> Tuple[bool, str]:
    """校验接收人是否可签到。"""
    if status == RecipientStatus.PENDING:
        return False, "该赠票尚未生效，请联系推荐人"
    if status == RecipientStatus.REJECTED:
        return False, "该赠票已被拒绝"
    if status == RecipientStatus.CANCELLED:
        return False, "该赠票已作废"
    if status == RecipientStatus.CHECKED_IN:
        return False, "该参会人已完成签到"
    return True, ""
def check_duplicate_checkin(status: int, checked_at: Optional[str]) -> Tuple[bool, str]:
    """校验重复签到，返回友好提示。"""
    if status == RecipientStatus.CHECKED_IN:
        time_text = checked_at or ""
        return False, f"该参会人已于 {time_text} 完成签到"
    return True, ""
def should_flush_m08(pending_count: int, first_created_at: Optional[str],
                     now: datetime) -> bool:
    """判断是否应触发 M-08 汇总发送。
    规则：满 10 人 或 距首条超过 30 分钟。
    """
    if pending_count <= 0:
        return False
    if pending_count >= M08_BATCH_SIZE:
        return True
    if not first_created_at:
        return True
    try:
        first_dt = datetime.strptime(first_created_at, TIME_FMT)
    except (ValueError, TypeError):
        return True
    elapsed_minutes = (now - first_dt).total_seconds() / 60
    return elapsed_minutes >= M08_WINDOW_MINUTES
def calc_checkin_rate(checked_in: int, approved: int) -> float:
    """计算签到率（百分比，保留 1 位小数）。approved 为 0 时返回 0.0。"""
    if approved <= 0:
        return 0.0
    return round(checked_in / approved * 100, 1)
def mask_email(email: Optional[str]) -> str:
    """邮箱脱敏：a***z@domain.com。"""
    if not email or "@" not in email:
        return email or ""
    name, domain = email.split("@", 1)
    if len(name) <= 2:
        return name[0] + "*@" + domain
    return name[0] + "*" * (len(name) - 2) + name[-1] + "@" + domain
def mask_mobile(mobile: Optional[str]) -> str:
    """手机号脱敏：138****8000。"""
    if not mobile or len(mobile) < 7:
        return mobile or ""
    return mobile[:3] + "****" + mobile[-4:]