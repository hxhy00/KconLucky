"""外部签到系统适配器（本期空实现）。"""
from abc import ABC, abstractmethod
from typing import Any, Dict, Optional, Tuple
class ExternalCheckinAdapter(ABC):
    """外部签到系统接口。"""
    @abstractmethod
    def push_checkin(self, recipient_info: Dict[str, Any],
                     checkin_time: str) -> Tuple[bool, Optional[str]]:
        """推送签到记录，返回 (是否成功, 错误消息)。"""
class NullExternalCheckin(ExternalCheckinAdapter):
    """空实现（本期留空，仅记录调用）。"""
    def __init__(self):
        self.calls = []
    def push_checkin(self, recipient_info: Dict[str, Any],
                     checkin_time: str) -> Tuple[bool, Optional[str]]:
        self.calls.append({"info": recipient_info, "time": checkin_time})
        return True, None
class FailingExternalCheckin(ExternalCheckinAdapter):
    """测试用：总是失败。"""
    def __init__(self):
        self.calls = []
    def push_checkin(self, recipient_info: Dict[str, Any],
                     checkin_time: str) -> Tuple[bool, Optional[str]]:
        self.calls.append({"info": recipient_info, "time": checkin_time})
        return False, "外部系统不可用"