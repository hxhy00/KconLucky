"""领域枚举：状态定义与中文映射。"""
from enum import IntEnum
class ReferrerStatus(IntEnum):
    """推荐人状态。"""
    PENDING = 1     # 待审批
    APPROVED = 2    # 已通过
    REJECTED = 3    # 已拒绝
    DISABLED = 4    # 已停用
class RecipientStatus(IntEnum):
    """赠票接收人状态。"""
    PENDING = 1     # 待审批
    APPROVED = 2    # 已通过
    REJECTED = 3    # 已拒绝
    CHECKED_IN = 4  # 已签到
    CANCELLED = 5   # 已作废
class ChannelStatus(IntEnum):
    """渠道状态。"""
    ACTIVE = 1      # 启用
    SUSPENDED = 2   # 暂停
    DELETED = 3     # 已删除
class TokenType(IntEnum):
    """一次性令牌类型。"""
    REFERRER_APPROVE = 1   # 推荐人审批
    TICKET_APPROVE = 2     # 赠票审批
class ExternalSyncStatus(IntEnum):
    """外部签到系统同步状态。"""
    NOT_SYNCED = 0  # 未同步
    SUCCESS = 1     # 成功
    FAILED = 2      # 失败
REFERRER_STATUS_TEXT = {
    ReferrerStatus.PENDING: "待审批",
    ReferrerStatus.APPROVED: "已通过",
    ReferrerStatus.REJECTED: "已拒绝",
    ReferrerStatus.DISABLED: "已停用",
}
RECIPIENT_STATUS_TEXT = {
    RecipientStatus.PENDING: "待审批",
    RecipientStatus.APPROVED: "已通过",
    RecipientStatus.REJECTED: "已拒绝",
    RecipientStatus.CHECKED_IN: "已签到",
    RecipientStatus.CANCELLED: "已作废",
}
CHANNEL_STATUS_TEXT = {
    ChannelStatus.ACTIVE: "启用",
    ChannelStatus.SUSPENDED: "已停用",
    ChannelStatus.DELETED: "已删除",
}
def referrer_status_text(status: int) -> str:
    """推荐人状态转中文，未知状态返回"未知"。"""
    return REFERRER_STATUS_TEXT.get(status, "未知")
def recipient_status_text(status: int) -> str:
    """接收人状态转中文，未知状态返回"未知"。"""
    return RECIPIENT_STATUS_TEXT.get(status, "未知")
def channel_status_text(status: int) -> str:
    """渠道状态转中文，未知状态返回"未知"。"""
    return CHANNEL_STATUS_TEXT.get(status, "未知")