"""编码生成规则：定义字符集与格式校验（纯函数）。"""
import re
import string
from typing import Tuple
# 字符集
BASE62 = string.ascii_letters + string.digits
# 注册码字符集：去掉易混的 0/O/1/I
REGISTRATION_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
# 长度
CHANNEL_CODE_LEN = 16
REFERRER_CODE_LEN = 16
REGISTRATION_CODE_LEN = 16
TOKEN_LEN = 32
# 正则
BASE62_RE = re.compile(r"^[A-Za-z0-9]+$")
REGISTRATION_RE = re.compile(r"^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]+$")
def is_valid_base62(code: str, length: int) -> bool:
    """校验是否为指定长度的 base62 字符串。"""
    if not code or not isinstance(code, str):
        return False
    if len(code) != length:
        return False
    return bool(BASE62_RE.match(code))
def is_valid_channel_code(code: str) -> bool:
    """校验渠道码格式（16 位 base62）。"""
    return is_valid_base62(code, CHANNEL_CODE_LEN)
def is_valid_referrer_code(code: str) -> bool:
    """校验推荐人码格式（16 位 base62）。"""
    return is_valid_base62(code, REFERRER_CODE_LEN)
def is_valid_token(token: str) -> bool:
    """校验令牌格式（32 位 base62）。"""
    return is_valid_base62(token, TOKEN_LEN)
def is_valid_registration_code(code: str) -> bool:
    """校验注册码格式（16 位，去易混字符集）。"""
    if not code or not isinstance(code, str):
        return False
    if len(code) != REGISTRATION_CODE_LEN:
        return False
    return bool(REGISTRATION_RE.match(code))
def normalize_registration_code(code: str) -> str:
    """规范化注册码输入（去空格、转大写）。"""
    if not code or not isinstance(code, str):
        return ""
    return code.strip().upper()
def build_referral_url(base_url: str, referrer_code: str) -> str:
    """构造推荐人赠票链接。"""
    return f"{base_url.rstrip('/')}/t/{referrer_code}"
def build_channel_register_url(base_url: str, channel_code: str) -> str:
    """构造推荐人注册链接。"""
    return f"{base_url.rstrip('/')}/r/{channel_code}"
def build_code_url(base_url: str, code: str) -> str:
    """构造注册码落地页链接。"""
    return f"{base_url.rstrip('/')}/c/{code}"
def build_approve_url(base_url: str, token_type: str, token: str) -> str:
    """构造一次性审批链接。
    Args:
        token_type: "referrer" 或 "ticket"
    """
    return f"{base_url.rstrip('/')}/approve/{token_type}/{token}"