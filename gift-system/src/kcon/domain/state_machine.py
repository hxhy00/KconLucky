"""状态机：定义合法状态流转，非法流转返回错误。"""
from typing import Dict, FrozenSet, Tuple
from .enums import ChannelStatus, RecipientStatus, ReferrerStatus
# 推荐人合法流转
REFERRER_TRANSITIONS: Dict[int, FrozenSet[int]] = {
    ReferrerStatus.PENDING: frozenset({ReferrerStatus.APPROVED, ReferrerStatus.REJECTED}),
    ReferrerStatus.APPROVED: frozenset({ReferrerStatus.DISABLED}),
    ReferrerStatus.REJECTED: frozenset({ReferrerStatus.PENDING}),
    ReferrerStatus.DISABLED: frozenset(),
}
# 接收人合法流转
RECIPIENT_TRANSITIONS: Dict[int, FrozenSet[int]] = {
    RecipientStatus.PENDING: frozenset({RecipientStatus.APPROVED, RecipientStatus.REJECTED}),
    RecipientStatus.APPROVED: frozenset({RecipientStatus.CHECKED_IN, RecipientStatus.CANCELLED}),
    RecipientStatus.REJECTED: frozenset(),
    RecipientStatus.CHECKED_IN: frozenset(),
    RecipientStatus.CANCELLED: frozenset(),
}
# 渠道合法流转
CHANNEL_TRANSITIONS: Dict[int, FrozenSet[int]] = {
    ChannelStatus.ACTIVE: frozenset({ChannelStatus.SUSPENDED, ChannelStatus.DELETED}),
    ChannelStatus.SUSPENDED: frozenset({ChannelStatus.ACTIVE, ChannelStatus.DELETED}),
    ChannelStatus.DELETED: frozenset(),
}
def can_transit(transitions: Dict[int, FrozenSet[int]],
                from_status: int, to_status: int) -> Tuple[bool, str]:
    """通用状态流转校验。"""
    allowed = transitions.get(from_status)
    if allowed is None:
        return False, f"未知的起始状态：{from_status}"
    if to_status not in allowed:
        return False, f"不允许从状态 {from_status} 流转到 {to_status}"
    return True, ""
def can_transit_referrer(from_status: int, to_status: int) -> Tuple[bool, str]:
    """校验推荐人状态流转。"""
    return can_transit(REFERRER_TRANSITIONS, from_status, to_status)
def can_transit_recipient(from_status: int, to_status: int) -> Tuple[bool, str]:
    """校验接收人状态流转。"""
    return can_transit(RECIPIENT_TRANSITIONS, from_status, to_status)
def can_transit_channel(from_status: int, to_status: int) -> Tuple[bool, str]:
    """校验渠道状态流转。"""
    return can_transit(CHANNEL_TRANSITIONS, from_status, to_status)