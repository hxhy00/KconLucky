"""单元测试：字段校验器（覆盖全部分支）。"""
import pytest
from kcon.domain import validators as v
class TestValidateEmail:
    """邮箱校验。"""
    @pytest.mark.unit
    def test_valid_email(self):
        assert v.validate_email("a@b.com") == (True, "")
    @pytest.mark.unit
    def test_valid_email_with_plus(self):
        assert v.validate_email("a.b+c@d-e.com") == (True, "")
    @pytest.mark.unit
    def test_none_email(self):
        assert v.validate_email(None) == (False, "邮箱不能为空")
    @pytest.mark.unit
    def test_empty_email(self):
        assert v.validate_email("") == (False, "邮箱不能为空")
    @pytest.mark.unit
    def test_non_string_email(self):
        assert v.validate_email(123) == (False, "邮箱不能为空")
    @pytest.mark.unit
    def test_invalid_format_no_at(self):
        assert v.validate_email("abc.com") == (False, "邮箱格式不正确")
    @pytest.mark.unit
    def test_invalid_format_no_domain(self):
        assert v.validate_email("a@") == (False, "邮箱格式不正确")
    @pytest.mark.unit
    def test_email_with_spaces_trimmed(self):
        assert v.validate_email("  a@b.com  ") == (True, "")
class TestValidateMobile:
    """手机号校验。"""
    @pytest.mark.unit
    def test_valid_mobile(self):
        assert v.validate_mobile("13800138000") == (True, "")
    @pytest.mark.unit
    def test_valid_mobile_19x(self):
        assert v.validate_mobile("19912345678") == (True, "")
    @pytest.mark.unit
    def test_none_required(self):
        assert v.validate_mobile(None, required=True) == (False, "手机号不能为空")
    @pytest.mark.unit
    def test_empty_required(self):
        assert v.validate_mobile("", required=True) == (False, "手机号不能为空")
    @pytest.mark.unit
    def test_blank_required(self):
        assert v.validate_mobile("   ", required=True) == (False, "手机号不能为空")
    @pytest.mark.unit
    def test_none_optional(self):
        assert v.validate_mobile(None, required=False) == (True, "")
    @pytest.mark.unit
    def test_empty_optional(self):
        assert v.validate_mobile("", required=False) == (True, "")
    @pytest.mark.unit
    def test_non_string_optional(self):
        assert v.validate_mobile(123, required=False) == (True, "")
    @pytest.mark.unit
    def test_invalid_mobile_short(self):
        assert v.validate_mobile("1380013800") == (False, "手机号格式不正确")
    @pytest.mark.unit
    def test_invalid_mobile_prefix(self):
        assert v.validate_mobile("12800138000") == (False, "手机号格式不正确")
    @pytest.mark.unit
    def test_invalid_mobile_letters(self):
        assert v.validate_mobile("1380013800a") == (False, "手机号格式不正确")
class TestValidateLength:
    """通用长度校验。"""
    @pytest.mark.unit
    def test_valid_length(self):
        assert v.validate_length("abc", 2, 10, "字段") == (True, "")
    @pytest.mark.unit
    def test_none_value(self):
        assert v.validate_length(None, 2, 10, "字段") == (False, "字段不能为空")
    @pytest.mark.unit
    def test_empty_value(self):
        assert v.validate_length("", 2, 10, "字段") == (False, "字段不能为空")
    @pytest.mark.unit
    def test_non_string_value(self):
        assert v.validate_length(123, 2, 10, "字段") == (False, "字段不能为空")
    @pytest.mark.unit
    def test_too_short(self):
        assert v.validate_length("a", 2, 10, "字段") == (False, "字段需 2～10 字符")
    @pytest.mark.unit
    def test_too_long(self):
        assert v.validate_length("a" * 11, 2, 10, "字段") == (False, "字段需 2～10 字符")
    @pytest.mark.unit
    def test_exact_min(self):
        assert v.validate_length("ab", 2, 10, "字段") == (True, "")
    @pytest.mark.unit
    def test_exact_max(self):
        assert v.validate_length("a" * 10, 2, 10, "字段") == (True, "")
    @pytest.mark.unit
    def test_strips_whitespace(self):
        assert v.validate_length("  ab  ", 2, 10, "字段") == (True, "")
class TestNamedValidators:
    """具名校验器。"""
    @pytest.mark.unit
    def test_validate_name_ok(self):
        assert v.validate_name("张三") == (True, "")
    @pytest.mark.unit
    def test_validate_name_too_short(self):
        assert v.validate_name("张") == (False, "姓名需 2～20 字符")
    @pytest.mark.unit
    def test_validate_company_ok(self):
        assert v.validate_company("某公司") == (True, "")
    @pytest.mark.unit
    def test_validate_company_too_long(self):
        assert v.validate_company("公" * 51) == (False, "公司需 2～50 字符")
    @pytest.mark.unit
    def test_validate_department_ok(self):
        assert v.validate_department("安全部") == (True, "")
    @pytest.mark.unit
    def test_validate_department_too_long(self):
        assert v.validate_department("部" * 31) == (False, "部门需 2～30 字符")
    @pytest.mark.unit
    def test_validate_channel_name_ok(self):
        assert v.validate_channel_name("渠道A") == (True, "")
    @pytest.mark.unit
    def test_validate_channel_name_too_long(self):
        assert v.validate_channel_name("渠" * 51) == (False, "渠道名称需 2～50 字符")
    @pytest.mark.unit
    def test_validate_approver_name_ok(self):
        assert v.validate_approver_name("李四") == (True, "")
    @pytest.mark.unit
    def test_validate_approver_name_too_short(self):
        assert v.validate_approver_name("李") == (False, "审批员姓名需 2～20 字符")
class TestValidateRemark:
    """备注校验。"""
    @pytest.mark.unit
    def test_none_remark(self):
        assert v.validate_remark(None) == (True, "")
    @pytest.mark.unit
    def test_empty_remark(self):
        assert v.validate_remark("") == (True, "")
    @pytest.mark.unit
    def test_valid_remark(self):
        assert v.validate_remark("备注内容") == (True, "")
    @pytest.mark.unit
    def test_too_long_remark(self):
        assert v.validate_remark("备" * 201) == (False, "备注不能超过 200 字符")
    @pytest.mark.unit
    def test_exact_200_remark(self):
        assert v.validate_remark("备" * 200) == (True, "")
class TestValidateQuota:
    """配额校验。"""
    @pytest.mark.unit
    def test_valid_quota(self):
        assert v.validate_quota(100) == (True, "")
    @pytest.mark.unit
    def test_valid_quota_string(self):
        assert v.validate_quota("100") == (True, "")
    @pytest.mark.unit
    def test_none_quota(self):
        assert v.validate_quota(None) == (False, "配额不能为空")
    @pytest.mark.unit
    def test_non_numeric_quota(self):
        assert v.validate_quota("abc") == (False, "配额必须为整数")
    @pytest.mark.unit
    def test_quota_zero(self):
        assert v.validate_quota(0) == (False, "配额需在 1～500 之间")
    @pytest.mark.unit
    def test_quota_too_large(self):
        assert v.validate_quota(501) == (False, "配额需在 1～500 之间")
    @pytest.mark.unit
    def test_quota_min_boundary(self):
        assert v.validate_quota(1) == (True, "")
    @pytest.mark.unit
    def test_quota_max_boundary(self):
        assert v.validate_quota(500) == (True, "")
class TestValidateCap:
    """单人上限校验。"""
    @pytest.mark.unit
    def test_valid_cap(self):
        assert v.validate_cap(20) == (True, "")
    @pytest.mark.unit
    def test_none_cap(self):
        assert v.validate_cap(None) == (False, "单人上限不能为空")
    @pytest.mark.unit
    def test_non_numeric_cap(self):
        assert v.validate_cap("abc") == (False, "单人上限必须为整数")
    @pytest.mark.unit
    def test_cap_zero(self):
        assert v.validate_cap(0) == (False, "单人上限需在 1～100 之间")
    @pytest.mark.unit
    def test_cap_too_large(self):
        assert v.validate_cap(101) == (False, "单人上限需在 1～100 之间")
    @pytest.mark.unit
    def test_cap_boundaries(self):
        assert v.validate_cap(1) == (True, "")
        assert v.validate_cap(100) == (True, "")
class TestValidateVerifyCode:
    """验证码格式校验。"""
    @pytest.mark.unit
    def test_valid_code(self):
        assert v.validate_verify_code("123456") == (True, "")
    @pytest.mark.unit
    def test_none_code(self):
        assert v.validate_verify_code(None) == (False, "验证码不能为空")
    @pytest.mark.unit
    def test_empty_code(self):
        assert v.validate_verify_code("") == (False, "验证码不能为空")
    @pytest.mark.unit
    def test_non_string_code(self):
        assert v.validate_verify_code(123456) == (False, "验证码不能为空")
    @pytest.mark.unit
    def test_non_digit_code(self):
        assert v.validate_verify_code("12345a") == (False, "验证码必须为数字")
    @pytest.mark.unit
    def test_wrong_length(self):
        assert v.validate_verify_code("12345") == (False, "验证码必须为 6 位")
    @pytest.mark.unit
    def test_custom_length(self):
        assert v.validate_verify_code("1234", length=4) == (True, "")