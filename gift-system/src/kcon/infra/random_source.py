"""随机源抽象：便于测试确定性。"""
import secrets
from abc import ABC, abstractmethod
from typing import List
from ..domain.codes import BASE62, REGISTRATION_CHARSET
class RandomSource(ABC):
    """随机源接口。"""
    @abstractmethod
    def choice(self, charset: str) -> str:
        """从字符集中选一个字符。"""
    def random_string(self, length: int, charset: str = BASE62) -> str:
        """生成指定长度的随机串。"""
        return "".join(self.choice(charset) for _ in range(length))
    def channel_code(self) -> str:
        """生成渠道码（16 位 base62）。"""
        return self.random_string(16, BASE62)
    def referrer_code(self) -> str:
        """生成推荐人码（16 位 base62）。"""
        return self.random_string(16, BASE62)
    def token(self) -> str:
        """生成令牌（32 位 base62）。"""
        return self.random_string(32, BASE62)
    def registration_code(self) -> str:
        """生成注册码（16 位去易混字符集）。"""
        return self.random_string(16, REGISTRATION_CHARSET)
    def verify_code(self, length: int = 6) -> str:
        """生成数字验证码。"""
        return self.random_string(length, "0123456789")
class SecureRandom(RandomSource):
    """密码学安全随机源（生产用）。"""
    def choice(self, charset: str) -> str:
        return secrets.choice(charset)
class SequenceRandom(RandomSource):
    """序列随机源（测试用），按预设序列返回。"""
    def __init__(self, sequence: List[str]):
        self._sequence = list(sequence)
        self._index = 0
    def choice(self, charset: str) -> str:
        if self._index >= len(self._sequence):
            # 序列耗尽后循环，保证测试可重复
            self._index = 0
        value = self._sequence[self._index]
        self._index += 1
        return value
    def random_string(self, length: int, charset: str = BASE62) -> str:
        """直接返回序列中的完整字符串（若长度匹配）。"""
        if self._index >= len(self._sequence):
            self._index = 0
        value = self._sequence[self._index]
        self._index += 1
        if len(value) == length:
            return value
        # 长度不匹配时用字符填充
        return (value * length)[:length]