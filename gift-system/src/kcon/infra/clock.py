"""时钟抽象：便于测试固定时间。"""
from abc import ABC, abstractmethod
from datetime import datetime, timedelta
TIME_FMT = "%Y-%m-%d %H:%M:%S"
class Clock(ABC):
    """时钟接口。"""
    @abstractmethod
    def now(self) -> datetime:
        """返回当前时间。"""
    def now_str(self) -> str:
        """返回当前时间字符串。"""
        return self.now().strftime(TIME_FMT)
    def expires_at(self, seconds: int) -> str:
        """返回 N 秒后的时间字符串。"""
        return (self.now() + timedelta(seconds=seconds)).strftime(TIME_FMT)
class SystemClock(Clock):
    """系统时钟（生产用）。"""
    def now(self) -> datetime:
        return datetime.now()
class FixedClock(Clock):
    """固定时钟（测试用），可手动推进。"""
    def __init__(self, fixed_time: str = "2026-10-25 10:00:00"):
        self._now = datetime.strptime(fixed_time, TIME_FMT)
    def now(self) -> datetime:
        return self._now
    def advance(self, seconds: int) -> None:
        """推进时间。"""
        self._now = self._now + timedelta(seconds=seconds)
    def set(self, fixed_time: str) -> None:
        """设置时间。"""
        self._now = datetime.strptime(fixed_time, TIME_FMT)