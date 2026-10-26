"""字段校验器：纯函数，无副作用，便于单元测试。"""
import re
from typing import Optional, Tuple
# 正则常量
EMAIL_RE = re.compile(r"^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$")
MOBILE_RE = re.compile(r"^1[3-9]\d{9}$")
# 长度约束常量
NAME_MIN, NAME_MAX = 2, 20
COMPANY_MIN, COMPANY_MAX = 2, 50
DEPARTMENT_MIN, DEPARTMENT_MAX = 2, 30
CHANNEL_NAME_MIN, CHANNEL_NAME_MAX = 2, 50
REMARK_MAX = 200
APPROVER_NAME_MIN, APPROVER_NAME_MAX = 2, 20
QUOTA_MIN, QUOTA_MAX = 1, 500
CAP_MIN, CAP_MAX = 1, 100
def validate_email(email: Optional[str]) -> Tuple[bool, str]:
    """校验邮箱格式。
    Returns:
        (是否通过, 错误消息)
    """
    if not email or not isinstance(email, str):
        return False, "邮箱不能为空"
    if not EMAIL_RE.match(email.strip()):
        return False, "邮箱格式不正确"
    return True, ""
def validate_mobile(mobile: Optional[str], required: bool = True) -> Tuple[bool, str]:
    """校验手机号。
    Args:
        mobile: 手机号
        required: 是否必填。False 时空值视为通过
    """
    if not mobile or not isinstance(mobile, str) or not mobile.strip():
        if required:
            return False, "手机号不能为空"
        return True, ""
    if not MOBILE_RE.match(mobile.strip()):
        return False, "手机号格式不正确"
    return True, ""
def validate_length(value: Optional[str], min_len: int, max_len: int,
                    field_name: str) -> Tuple[bool, str]:
    """通用长度校验。"""
    if not value or not isinstance(value, str):
        return False, f"{field_name}不能为空"
    length = len(value.strip())
    if length < min_len:
        return False, f"{field_name}需 {min_len}～{max_len} 字符"
    if length > max_len:
        return False, f"{field_name}需 {min_len}～{max_len} 字符"
    return True, ""
def validate_name(name: Optional[str]) -> Tuple[bool, str]:
    """校验姓名。"""
    return validate_length(name, NAME_MIN, NAME_MAX, "姓名")
def validate_company(company: Optional[str]) -> Tuple[bool, str]:
    """校验公司/单位。"""
    return validate_length(company, COMPANY_MIN, COMPANY_MAX, "公司")
def validate_department(department: Optional[str]) -> Tuple[bool, str]:
    """校验部门。"""
    return validate_length(department, DEPARTMENT_MIN, DEPARTMENT_MAX, "部门")
def validate_channel_name(name: Optional[str]) -> Tuple[bool, str]:
    """校验渠道名称。"""
    return validate_length(name, CHANNEL_NAME_MIN, CHANNEL_NAME_MAX, "渠道名称")
def validate_approver_name(name: Optional[str]) -> Tuple[bool, str]:
    """校验审批员姓名。"""
    return validate_length(name, APPROVER_NAME_MIN, APPROVER_NAME_MAX, "审批员姓名")
def validate_remark(remark: Optional[str]) -> Tuple[bool, str]:
    """校验备注（可空，最长 200）。"""
    if not remark:
        return True, ""
    if len(remark) > REMARK_MAX:
        return False, f"备注不能超过 {REMARK_MAX} 字符"
    return True, ""
def validate_quota(quota) -> Tuple[bool, str]:
    """校验渠道配额（1～500）。"""
    if quota is None:
        return False, "配额不能为空"
    try:
        value = int(quota)
    except (TypeError, ValueError):
        return False, "配额必须为整数"
    if value < QUOTA_MIN or value > QUOTA_MAX:
        return False, f"配额需在 {QUOTA_MIN}～{QUOTA_MAX} 之间"
    return True, ""
def validate_cap(cap) -> Tuple[bool, str]:
    """校验推荐人单人上限（1～100）。"""
    if cap is None:
        return False, "单人上限不能为空"
    try:
        value = int(cap)
    except (TypeError, ValueError):
        return False, "单人上限必须为整数"
    if value < CAP_MIN or value > CAP_MAX:
        return False, f"单人上限需在 {CAP_MIN}～{CAP_MAX} 之间"
    return True, ""
def validate_verify_code(code: Optional[str], length: int = 6) -> Tuple[bool, str]:
    """校验验证码格式（纯数字，指定长度）。"""
    if not code or not isinstance(code, str):
        return False, "验证码不能为空"
    if not code.strip().isdigit():
        return False, "验证码必须为数字"
    if len(code.strip()) != length:
        return False, f"验证码必须为 {length} 位"
    return True, ""