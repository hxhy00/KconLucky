"""中间件：会话管理、鉴权、限流。"""
import time
from typing import Dict, Optional, Tuple
class SessionStore:
    """会话存储（内存，生产可换 Redis）。"""
    def __init__(self, clock, ttl_seconds: int = 7200):
        self.clock = clock
        self.ttl = ttl_seconds
        self._sessions: Dict[str, dict] = {}
    def create(self, sid: str, role: str, user: dict) -> None:
        """创建会话。"""
        self._sessions[sid] = {
            "role": role, "user": user,
            "expire_at": time.time() + self.ttl,
        }
    def get(self, sid: str) -> Optional[dict]:
        """获取会话，过期返回 None。"""
        s = self._sessions.get(sid)
        if not s:
            return None
        if s["expire_at"] < time.time():
            self._sessions.pop(sid, None)
            return None
        return s
    def destroy(self, sid: str) -> None:
        """销毁会话。"""
        self._sessions.pop(sid, None)
    def clear(self) -> None:
        """清空（测试用）。"""
        self._sessions.clear()
class RateLimiter:
    """简单滑动窗口限流（内存）。"""
    def __init__(self, clock):
        self.clock = clock
        self._records: Dict[str, list] = {}
    def check(self, key: str, limit: int, window_seconds: int) -> Tuple[bool, str]:
        """检查是否超限。返回 (是否允许, 错误消息)。"""
        now = time.time()
        records = self._records.setdefault(key, [])
        # 清理过期记录
        cutoff = now - window_seconds
        self._records[key] = [t for t in records if t > cutoff]
        records = self._records[key]
        if len(records) >= limit:
            return False, "请求过于频繁，请稍后再试"
        records.append(now)
        return True, ""
    def clear(self) -> None:
        """清空（测试用）。"""
        self._records.clear()
class AuthContext:
    """鉴权上下文。"""
    def __init__(self, role: str, user: dict, sid: str):
        self.role = role
        self.user = user
        self.sid = sid
    @property
    def is_admin(self) -> bool:
        return self.role == "admin"
    @property
    def is_approver(self) -> bool:
        return self.role == "approver"
    @property
    def is_staff(self) -> bool:
        return self.role == "staff"
    def channel_id(self) -> Optional[int]:
        """审批员所属渠道 id。"""
        return self.user.get("channel_id") if self.is_approver else None