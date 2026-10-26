"""领域模型：数据类定义（不可变值对象优先）。"""
from dataclasses import dataclass, field
from typing import Optional
@dataclass(frozen=True)
class Channel:
    """渠道。"""
    id: int
    name: str
    code: str
    quota: int
    referrer_cap: int
    status: int
    remark: str = ""
    created_at: str = ""
@dataclass(frozen=True)
class Approver:
    """渠道审批员。"""
    id: int
    channel_id: int
    name: str
    email: str
    status: int = 1
@dataclass(frozen=True)
class Referrer:
    """推荐人。"""
    id: int
    channel_id: int
    name: str
    company: str
    department: str
    mobile: str
    email: str
    code: str
    status: int
    issued_count: int = 0
    approved_at: Optional[str] = None
    rejected_at: Optional[str] = None
    created_at: str = ""
@dataclass(frozen=True)
class Recipient:
    """赠票接收人。"""
    id: int
    referrer_id: int
    channel_id: int
    name: str
    company: str
    email: str
    status: int
    mobile: str = ""
    approved_at: Optional[str] = None
    created_at: str = ""
@dataclass(frozen=True)
class RegistrationCode:
    """注册码。"""
    id: int
    recipient_id: int
    code: str
    qr_content: str = ""
    qr_url: str = ""
    issued_at: str = ""
@dataclass(frozen=True)
class Checkin:
    """签到记录。"""
    id: int
    recipient_id: int
    checked_at: str
    staff_id: Optional[int] = None
    device_ip: str = ""
    material_issued: int = 1
    external_sync_status: int = 0
@dataclass(frozen=True)
class Token:
    """一次性令牌。"""
    id: int
    token: str
    type: int
    target_id: int
    owner_id: int
    expires_at: str
    used_at: Optional[str] = None
@dataclass(frozen=True)
class AdminStats:
    """管理员统计。"""
    channel_count: int = 0
    referrer_count: int = 0
    invited_count: int = 0
    approved_count: int = 0
    checked_in_count: int = 0
    quota_total: int = 0
    quota_used: int = 0
    quota_remaining: int = 0
    checkin_rate: float = 0.0
@dataclass(frozen=True)
class ChannelStats:
    """渠道统计。"""
    channel_name: str = ""
    quota: int = 0
    referrer_cap: int = 0
    referrer_count: int = 0
    invited_count: int = 0
    approved_count: int = 0
    checked_in_count: int = 0
    quota_used: int = 0
    quota_remaining: int = 0
    checkin_rate: float = 0.0
@dataclass
class CheckinLookupResult:
    """签到查询结果（可变，便于组装）。"""
    recipient_id: int
    name: str
    company: str
    code: str
    status: int
    status_text: str
    referrer_name: str = ""
    channel_name: str = ""
    checked_at: Optional[str] = None