# TC-U 单元测试用例集

| 项 | 内容 |
|---|---|
| 用例集编号 | TC-U |
| 用例总数 | 210 |
| 说明 | 验证领域层纯函数与工具函数的正确性，无外部依赖 |
| 归档位置 | `tests/unit/` |
| 执行命令 | `.venv/bin/pytest tests/unit/ -v` |

---

## 用例清单

| 用例编号 | 所属文件 | 测试类 | 用例方法 | 验证点 | 代码行 |
|---|---|---|---|---|---|
| TC-U-001 | test_domain_rest.py | TestReferrerTransitions | `test_pending_to_approved` | pending to approved | 16 |
| TC-U-002 | test_domain_rest.py | TestReferrerTransitions | `test_pending_to_rejected` | pending to rejected | 20 |
| TC-U-003 | test_domain_rest.py | TestReferrerTransitions | `test_pending_to_disabled_illegal` | pending to disabled illegal | 24 |
| TC-U-004 | test_domain_rest.py | TestReferrerTransitions | `test_approved_to_disabled` | approved to disabled | 29 |
| TC-U-005 | test_domain_rest.py | TestReferrerTransitions | `test_approved_to_pending_illegal` | approved to pending illegal | 33 |
| TC-U-006 | test_domain_rest.py | TestReferrerTransitions | `test_rejected_to_pending` | rejected to pending | 37 |
| TC-U-007 | test_domain_rest.py | TestReferrerTransitions | `test_disabled_to_anything_illegal` | disabled to anything illegal | 41 |
| TC-U-008 | test_domain_rest.py | TestReferrerTransitions | `test_unknown_from_status` | unknown from status | 45 |
| TC-U-009 | test_domain_rest.py | TestRecipientTransitions | `test_pending_to_approved` | pending to approved | 51 |
| TC-U-010 | test_domain_rest.py | TestRecipientTransitions | `test_pending_to_rejected` | pending to rejected | 55 |
| TC-U-011 | test_domain_rest.py | TestRecipientTransitions | `test_approved_to_checked_in` | approved to checked in | 59 |
| TC-U-012 | test_domain_rest.py | TestRecipientTransitions | `test_approved_to_cancelled` | approved to cancelled | 63 |
| TC-U-013 | test_domain_rest.py | TestRecipientTransitions | `test_checked_in_to_anything_illegal` | checked in to anything illegal | 67 |
| TC-U-014 | test_domain_rest.py | TestRecipientTransitions | `test_rejected_terminal` | rejected terminal | 71 |
| TC-U-015 | test_domain_rest.py | TestRecipientTransitions | `test_cancelled_terminal` | cancelled terminal | 75 |
| TC-U-016 | test_domain_rest.py | TestRecipientTransitions | `test_unknown_status` | unknown status | 79 |
| TC-U-017 | test_domain_rest.py | TestChannelTransitions | `test_active_to_suspended` | active to suspended | 83 |
| TC-U-018 | test_domain_rest.py | TestChannelTransitions | `test_active_to_deleted` | active to deleted | 87 |
| TC-U-019 | test_domain_rest.py | TestChannelTransitions | `test_suspended_to_active` | suspended to active | 91 |
| TC-U-020 | test_domain_rest.py | TestChannelTransitions | `test_suspended_to_deleted` | suspended to deleted | 95 |
| TC-U-021 | test_domain_rest.py | TestChannelTransitions | `test_deleted_terminal` | deleted terminal | 99 |
| TC-U-022 | test_domain_rest.py | TestChannelTransitions | `test_unknown_status` | unknown status | 103 |
| TC-U-023 | test_domain_rest.py | TestCodes | `test_valid_channel_code` | valid channel code | 108 |
| TC-U-024 | test_domain_rest.py | TestCodes | `test_channel_code_wrong_length` | channel code wrong length | 111 |
| TC-U-025 | test_domain_rest.py | TestCodes | `test_channel_code_none` | channel code none | 114 |
| TC-U-026 | test_domain_rest.py | TestCodes | `test_channel_code_non_string` | channel code non string | 117 |
| TC-U-027 | test_domain_rest.py | TestCodes | `test_channel_code_invalid_chars` | channel code invalid chars | 120 |
| TC-U-028 | test_domain_rest.py | TestCodes | `test_valid_referrer_code` | valid referrer code | 123 |
| TC-U-029 | test_domain_rest.py | TestCodes | `test_valid_token` | valid token | 126 |
| TC-U-030 | test_domain_rest.py | TestCodes | `test_token_wrong_length` | token wrong length | 129 |
| TC-U-031 | test_domain_rest.py | TestCodes | `test_valid_registration_code` | valid registration code | 132 |
| TC-U-032 | test_domain_rest.py | TestCodes | `test_registration_code_with_excluded_chars` | registration code with excluded chars | 135 |
| TC-U-033 | test_domain_rest.py | TestCodes | `test_registration_code_wrong_length` | registration code wrong length | 142 |
| TC-U-034 | test_domain_rest.py | TestCodes | `test_registration_code_none` | registration code none | 145 |
| TC-U-035 | test_domain_rest.py | TestCodes | `test_normalize_registration_code` | normalize registration code | 148 |
| TC-U-036 | test_domain_rest.py | TestCodes | `test_normalize_none` | normalize none | 151 |
| TC-U-037 | test_domain_rest.py | TestCodes | `test_normalize_non_string` | normalize non string | 154 |
| TC-U-038 | test_domain_rest.py | TestCodes | `test_build_referral_url` | build referral url | 157 |
| TC-U-039 | test_domain_rest.py | TestCodes | `test_build_referral_url_trailing_slash` | build referral url trailing slash | 160 |
| TC-U-040 | test_domain_rest.py | TestCodes | `test_build_channel_register_url` | build channel register url | 163 |
| TC-U-041 | test_domain_rest.py | TestCodes | `test_build_code_url` | build code url | 166 |
| TC-U-042 | test_domain_rest.py | TestCodes | `test_build_approve_url_referrer` | build approve url referrer | 169 |
| TC-U-043 | test_domain_rest.py | TestCodes | `test_build_approve_url_ticket` | build approve url ticket | 173 |
| TC-U-044 | test_domain_rest.py | TestEnums | `test_referrer_status_text` | referrer status text | 179 |
| TC-U-045 | test_domain_rest.py | TestEnums | `test_referrer_status_unknown` | referrer status unknown | 185 |
| TC-U-046 | test_domain_rest.py | TestEnums | `test_recipient_status_text` | recipient status text | 188 |
| TC-U-047 | test_domain_rest.py | TestEnums | `test_recipient_status_unknown` | recipient status unknown | 195 |
| TC-U-048 | test_domain_rest.py | TestEnums | `test_channel_status_text` | channel status text | 198 |
| TC-U-049 | test_domain_rest.py | TestEnums | `test_channel_status_unknown` | channel status unknown | 203 |
| TC-U-050 | test_domain_rest.py | TestEnums | `test_enum_values` | enum values | 206 |
| TC-U-051 | test_domain_rest.py | TestModels | `test_channel_defaults` | channel defaults | 215 |
| TC-U-052 | test_domain_rest.py | TestModels | `test_channel_frozen` | channel frozen | 220 |
| TC-U-053 | test_domain_rest.py | TestModels | `test_referrer_defaults` | referrer defaults | 225 |
| TC-U-054 | test_domain_rest.py | TestModels | `test_recipient_defaults` | recipient defaults | 232 |
| TC-U-055 | test_domain_rest.py | TestModels | `test_registration_code_defaults` | registration code defaults | 238 |
| TC-U-056 | test_domain_rest.py | TestModels | `test_checkin_defaults` | checkin defaults | 244 |
| TC-U-057 | test_domain_rest.py | TestModels | `test_token_defaults` | token defaults | 251 |
| TC-U-058 | test_domain_rest.py | TestModels | `test_admin_stats_defaults` | admin stats defaults | 255 |
| TC-U-059 | test_domain_rest.py | TestModels | `test_channel_stats_defaults` | channel stats defaults | 260 |
| TC-U-060 | test_domain_rest.py | TestModels | `test_checkin_lookup_result_mutable` | checkin lookup result mutable | 265 |
| TC-U-061 | test_domain_rest.py | TestClock | `test_system_clock_now` | system clock now | 274 |
| TC-U-062 | test_domain_rest.py | TestClock | `test_system_clock_now_str` | system clock now str | 278 |
| TC-U-063 | test_domain_rest.py | TestClock | `test_fixed_clock` | fixed clock | 282 |
| TC-U-064 | test_domain_rest.py | TestClock | `test_fixed_clock_advance` | fixed clock advance | 286 |
| TC-U-065 | test_domain_rest.py | TestClock | `test_fixed_clock_set` | fixed clock set | 291 |
| TC-U-066 | test_domain_rest.py | TestClock | `test_expires_at` | expires at | 296 |
| TC-U-067 | test_domain_rest.py | TestClock | `test_expires_at_negative` | expires at negative | 300 |
| TC-U-068 | test_domain_rest.py | TestSecureRandom | `test_choice_in_charset` | choice in charset | 306 |
| TC-U-069 | test_domain_rest.py | TestSecureRandom | `test_channel_code_length` | channel code length | 310 |
| TC-U-070 | test_domain_rest.py | TestSecureRandom | `test_referrer_code_length` | referrer code length | 313 |
| TC-U-071 | test_domain_rest.py | TestSecureRandom | `test_token_length` | token length | 316 |
| TC-U-072 | test_domain_rest.py | TestSecureRandom | `test_registration_code_length` | registration code length | 319 |
| TC-U-073 | test_domain_rest.py | TestSecureRandom | `test_registration_code_charset` | registration code charset | 322 |
| TC-U-074 | test_domain_rest.py | TestSecureRandom | `test_verify_code_digits` | verify code digits | 326 |
| TC-U-075 | test_domain_rest.py | TestSecureRandom | `test_verify_code_custom_length` | verify code custom length | 331 |
| TC-U-076 | test_domain_rest.py | TestSecureRandom | `test_random_string` | random string | 334 |
| TC-U-077 | test_domain_rest.py | TestSequenceRandom | `test_choice_sequence` | choice sequence | 338 |
| TC-U-078 | test_domain_rest.py | TestSequenceRandom | `test_choice_wraps_around` | choice wraps around | 344 |
| TC-U-079 | test_domain_rest.py | TestSequenceRandom | `test_random_string_exact_length` | random string exact length | 350 |
| TC-U-080 | test_domain_rest.py | TestSequenceRandom | `test_random_string_padding` | random string padding | 354 |
| TC-U-081 | test_domain_rest.py | TestSequenceRandom | `test_random_string_wraps` | random string wraps | 358 |
| TC-U-082 | test_rules.py | TestAdminLimit | `test_under_limit` | under limit | 8 |
| TC-U-083 | test_rules.py | TestAdminLimit | `test_at_limit` | at limit | 12 |
| TC-U-084 | test_rules.py | TestAdminLimit | `test_over_limit` | over limit | 15 |
| TC-U-085 | test_rules.py | TestChannelLimit | `test_under_limit` | under limit | 19 |
| TC-U-086 | test_rules.py | TestChannelLimit | `test_at_limit` | at limit | 22 |
| TC-U-087 | test_rules.py | TestTotalTicketLimit | `test_within_limit` | within limit | 26 |
| TC-U-088 | test_rules.py | TestTotalTicketLimit | `test_exceed_limit` | exceed limit | 30 |
| TC-U-089 | test_rules.py | TestReferrerCap | `test_under_cap` | under cap | 38 |
| TC-U-090 | test_rules.py | TestReferrerCap | `test_at_cap` | at cap | 41 |
| TC-U-091 | test_rules.py | TestReferrerCap | `test_over_cap` | over cap | 46 |
| TC-U-092 | test_rules.py | TestChannelQuota | `test_under_quota` | under quota | 50 |
| TC-U-093 | test_rules.py | TestChannelQuota | `test_at_quota` | at quota | 53 |
| TC-U-094 | test_rules.py | TestReferrerAvailable | `test_available` | available | 57 |
| TC-U-095 | test_rules.py | TestReferrerAvailable | `test_referrer_pending` | referrer pending | 61 |
| TC-U-096 | test_rules.py | TestReferrerAvailable | `test_referrer_rejected` | referrer rejected | 65 |
| TC-U-097 | test_rules.py | TestReferrerAvailable | `test_channel_suspended` | channel suspended | 69 |
| TC-U-098 | test_rules.py | TestReferrerAvailable | `test_both_invalid` | both invalid | 73 |
| TC-U-099 | test_rules.py | TestEmailConflict | `test_no_conflict` | no conflict | 79 |
| TC-U-100 | test_rules.py | TestEmailConflict | `test_same_referrer_conflict` | same referrer conflict | 82 |
| TC-U-101 | test_rules.py | TestEmailConflict | `test_same_referrer_pending_status` | same referrer pending status | 87 |
| TC-U-102 | test_rules.py | TestEmailConflict | `test_other_referrer_conflict` | other referrer conflict | 91 |
| TC-U-103 | test_rules.py | TestRejectRetry | `test_not_rejected` | not rejected | 97 |
| TC-U-104 | test_rules.py | TestRejectRetry | `test_within_7_days` | within 7 days | 101 |
| TC-U-105 | test_rules.py | TestRejectRetry | `test_after_7_days` | after 7 days | 107 |
| TC-U-106 | test_rules.py | TestRejectRetry | `test_exactly_7_days` | exactly 7 days | 111 |
| TC-U-107 | test_rules.py | TestRejectRetry | `test_invalid_time_format` | invalid time format | 115 |
| TC-U-108 | test_rules.py | TestTokenUsable | `test_usable` | usable | 120 |
| TC-U-109 | test_rules.py | TestTokenUsable | `test_used` | used | 124 |
| TC-U-110 | test_rules.py | TestTokenUsable | `test_expired` | expired | 129 |
| TC-U-111 | test_rules.py | TestTokenUsable | `test_exactly_expired` | exactly expired | 133 |
| TC-U-112 | test_rules.py | TestTokenUsable | `test_invalid_expire_format` | invalid expire format | 137 |
| TC-U-113 | test_rules.py | TestTokenOwner | `test_owner_match` | owner match | 142 |
| TC-U-114 | test_rules.py | TestTokenOwner | `test_owner_mismatch` | owner mismatch | 145 |
| TC-U-115 | test_rules.py | TestTokenType | `test_type_match` | type match | 149 |
| TC-U-116 | test_rules.py | TestTokenType | `test_type_mismatch` | type mismatch | 153 |
| TC-U-117 | test_rules.py | TestCalcTokenExpires | `test_expires_7_days_later` | expires 7 days later | 158 |
| TC-U-118 | test_rules.py | TestRecipientCheckinable | `test_approved_can_checkin` | approved can checkin | 163 |
| TC-U-119 | test_rules.py | TestRecipientCheckinable | `test_pending_cannot` | pending cannot | 166 |
| TC-U-120 | test_rules.py | TestRecipientCheckinable | `test_rejected_cannot` | rejected cannot | 170 |
| TC-U-121 | test_rules.py | TestRecipientCheckinable | `test_cancelled_cannot` | cancelled cannot | 174 |
| TC-U-122 | test_rules.py | TestRecipientCheckinable | `test_checked_in_cannot` | checked in cannot | 178 |
| TC-U-123 | test_rules.py | TestDuplicateCheckin | `test_not_checked_in` | not checked in | 183 |
| TC-U-124 | test_rules.py | TestDuplicateCheckin | `test_already_checked_in` | already checked in | 186 |
| TC-U-125 | test_rules.py | TestDuplicateCheckin | `test_checked_in_without_time` | checked in without time | 192 |
| TC-U-126 | test_rules.py | TestShouldFlushM08 | `test_empty_queue` | empty queue | 198 |
| TC-U-127 | test_rules.py | TestShouldFlushM08 | `test_reach_batch_size` | reach batch size | 202 |
| TC-U-128 | test_rules.py | TestShouldFlushM08 | `test_below_batch_within_window` | below batch within window | 206 |
| TC-U-129 | test_rules.py | TestShouldFlushM08 | `test_below_batch_over_window` | below batch over window | 210 |
| TC-U-130 | test_rules.py | TestShouldFlushM08 | `test_exactly_30_minutes` | exactly 30 minutes | 214 |
| TC-U-131 | test_rules.py | TestShouldFlushM08 | `test_no_first_time` | no first time | 218 |
| TC-U-132 | test_rules.py | TestShouldFlushM08 | `test_invalid_time_format` | invalid time format | 222 |
| TC-U-133 | test_rules.py | TestCalcCheckinRate | `test_normal` | normal | 227 |
| TC-U-134 | test_rules.py | TestCalcCheckinRate | `test_zero_approved` | zero approved | 230 |
| TC-U-135 | test_rules.py | TestCalcCheckinRate | `test_negative_approved` | negative approved | 233 |
| TC-U-136 | test_rules.py | TestCalcCheckinRate | `test_rounding` | rounding | 236 |
| TC-U-137 | test_rules.py | TestCalcCheckinRate | `test_full` | full | 239 |
| TC-U-138 | test_rules.py | TestMaskEmail | `test_normal` | normal | 243 |
| TC-U-139 | test_rules.py | TestMaskEmail | `test_short_name` | short name | 246 |
| TC-U-140 | test_rules.py | TestMaskEmail | `test_single_char` | single char | 249 |
| TC-U-141 | test_rules.py | TestMaskEmail | `test_none` | none | 252 |
| TC-U-142 | test_rules.py | TestMaskEmail | `test_no_at` | no at | 255 |
| TC-U-143 | test_rules.py | TestMaskMobile | `test_normal` | normal | 259 |
| TC-U-144 | test_rules.py | TestMaskMobile | `test_short` | short | 262 |
| TC-U-145 | test_rules.py | TestMaskMobile | `test_none` | none | 265 |
| TC-U-146 | test_rules.py | TestMaskMobile | `test_empty` | empty | 268 |
| TC-U-147 | test_validators.py | TestValidateEmail | `test_valid_email` | 邮箱校验。 | 7 |
| TC-U-148 | test_validators.py | TestValidateEmail | `test_valid_email_with_plus` | 邮箱校验。 | 10 |
| TC-U-149 | test_validators.py | TestValidateEmail | `test_none_email` | 邮箱校验。 | 13 |
| TC-U-150 | test_validators.py | TestValidateEmail | `test_empty_email` | 邮箱校验。 | 16 |
| TC-U-151 | test_validators.py | TestValidateEmail | `test_non_string_email` | 邮箱校验。 | 19 |
| TC-U-152 | test_validators.py | TestValidateEmail | `test_invalid_format_no_at` | 邮箱校验。 | 22 |
| TC-U-153 | test_validators.py | TestValidateEmail | `test_invalid_format_no_domain` | 邮箱校验。 | 25 |
| TC-U-154 | test_validators.py | TestValidateEmail | `test_email_with_spaces_trimmed` | 邮箱校验。 | 28 |
| TC-U-155 | test_validators.py | TestValidateMobile | `test_valid_mobile` | 手机号校验。 | 33 |
| TC-U-156 | test_validators.py | TestValidateMobile | `test_valid_mobile_19x` | 手机号校验。 | 36 |
| TC-U-157 | test_validators.py | TestValidateMobile | `test_none_required` | 手机号校验。 | 39 |
| TC-U-158 | test_validators.py | TestValidateMobile | `test_empty_required` | 手机号校验。 | 42 |
| TC-U-159 | test_validators.py | TestValidateMobile | `test_blank_required` | 手机号校验。 | 45 |
| TC-U-160 | test_validators.py | TestValidateMobile | `test_none_optional` | 手机号校验。 | 48 |
| TC-U-161 | test_validators.py | TestValidateMobile | `test_empty_optional` | 手机号校验。 | 51 |
| TC-U-162 | test_validators.py | TestValidateMobile | `test_non_string_optional` | 手机号校验。 | 54 |
| TC-U-163 | test_validators.py | TestValidateMobile | `test_invalid_mobile_short` | 手机号校验。 | 57 |
| TC-U-164 | test_validators.py | TestValidateMobile | `test_invalid_mobile_prefix` | 手机号校验。 | 60 |
| TC-U-165 | test_validators.py | TestValidateMobile | `test_invalid_mobile_letters` | 手机号校验。 | 63 |
| TC-U-166 | test_validators.py | TestValidateLength | `test_valid_length` | 通用长度校验。 | 68 |
| TC-U-167 | test_validators.py | TestValidateLength | `test_none_value` | 通用长度校验。 | 71 |
| TC-U-168 | test_validators.py | TestValidateLength | `test_empty_value` | 通用长度校验。 | 74 |
| TC-U-169 | test_validators.py | TestValidateLength | `test_non_string_value` | 通用长度校验。 | 77 |
| TC-U-170 | test_validators.py | TestValidateLength | `test_too_short` | 通用长度校验。 | 80 |
| TC-U-171 | test_validators.py | TestValidateLength | `test_too_long` | 通用长度校验。 | 83 |
| TC-U-172 | test_validators.py | TestValidateLength | `test_exact_min` | 通用长度校验。 | 86 |
| TC-U-173 | test_validators.py | TestValidateLength | `test_exact_max` | 通用长度校验。 | 89 |
| TC-U-174 | test_validators.py | TestValidateLength | `test_strips_whitespace` | 通用长度校验。 | 92 |
| TC-U-175 | test_validators.py | TestNamedValidators | `test_validate_name_ok` | 具名校验器。 | 97 |
| TC-U-176 | test_validators.py | TestNamedValidators | `test_validate_name_too_short` | 具名校验器。 | 100 |
| TC-U-177 | test_validators.py | TestNamedValidators | `test_validate_company_ok` | 具名校验器。 | 103 |
| TC-U-178 | test_validators.py | TestNamedValidators | `test_validate_company_too_long` | 具名校验器。 | 106 |
| TC-U-179 | test_validators.py | TestNamedValidators | `test_validate_department_ok` | 具名校验器。 | 109 |
| TC-U-180 | test_validators.py | TestNamedValidators | `test_validate_department_too_long` | 具名校验器。 | 112 |
| TC-U-181 | test_validators.py | TestNamedValidators | `test_validate_channel_name_ok` | 具名校验器。 | 115 |
| TC-U-182 | test_validators.py | TestNamedValidators | `test_validate_channel_name_too_long` | 具名校验器。 | 118 |
| TC-U-183 | test_validators.py | TestNamedValidators | `test_validate_approver_name_ok` | 具名校验器。 | 121 |
| TC-U-184 | test_validators.py | TestNamedValidators | `test_validate_approver_name_too_short` | 具名校验器。 | 124 |
| TC-U-185 | test_validators.py | TestValidateRemark | `test_none_remark` | 备注校验。 | 129 |
| TC-U-186 | test_validators.py | TestValidateRemark | `test_empty_remark` | 备注校验。 | 132 |
| TC-U-187 | test_validators.py | TestValidateRemark | `test_valid_remark` | 备注校验。 | 135 |
| TC-U-188 | test_validators.py | TestValidateRemark | `test_too_long_remark` | 备注校验。 | 138 |
| TC-U-189 | test_validators.py | TestValidateRemark | `test_exact_200_remark` | 备注校验。 | 141 |
| TC-U-190 | test_validators.py | TestValidateQuota | `test_valid_quota` | 配额校验。 | 146 |
| TC-U-191 | test_validators.py | TestValidateQuota | `test_valid_quota_string` | 配额校验。 | 149 |
| TC-U-192 | test_validators.py | TestValidateQuota | `test_none_quota` | 配额校验。 | 152 |
| TC-U-193 | test_validators.py | TestValidateQuota | `test_non_numeric_quota` | 配额校验。 | 155 |
| TC-U-194 | test_validators.py | TestValidateQuota | `test_quota_zero` | 配额校验。 | 158 |
| TC-U-195 | test_validators.py | TestValidateQuota | `test_quota_too_large` | 配额校验。 | 161 |
| TC-U-196 | test_validators.py | TestValidateQuota | `test_quota_min_boundary` | 配额校验。 | 164 |
| TC-U-197 | test_validators.py | TestValidateQuota | `test_quota_max_boundary` | 配额校验。 | 167 |
| TC-U-198 | test_validators.py | TestValidateCap | `test_valid_cap` | 单人上限校验。 | 172 |
| TC-U-199 | test_validators.py | TestValidateCap | `test_none_cap` | 单人上限校验。 | 175 |
| TC-U-200 | test_validators.py | TestValidateCap | `test_non_numeric_cap` | 单人上限校验。 | 178 |
| TC-U-201 | test_validators.py | TestValidateCap | `test_cap_zero` | 单人上限校验。 | 181 |
| TC-U-202 | test_validators.py | TestValidateCap | `test_cap_too_large` | 单人上限校验。 | 184 |
| TC-U-203 | test_validators.py | TestValidateCap | `test_cap_boundaries` | 单人上限校验。 | 187 |
| TC-U-204 | test_validators.py | TestValidateVerifyCode | `test_valid_code` | 验证码格式校验。 | 193 |
| TC-U-205 | test_validators.py | TestValidateVerifyCode | `test_none_code` | 验证码格式校验。 | 196 |
| TC-U-206 | test_validators.py | TestValidateVerifyCode | `test_empty_code` | 验证码格式校验。 | 199 |
| TC-U-207 | test_validators.py | TestValidateVerifyCode | `test_non_string_code` | 验证码格式校验。 | 202 |
| TC-U-208 | test_validators.py | TestValidateVerifyCode | `test_non_digit_code` | 验证码格式校验。 | 205 |
| TC-U-209 | test_validators.py | TestValidateVerifyCode | `test_wrong_length` | 验证码格式校验。 | 208 |
| TC-U-210 | test_validators.py | TestValidateVerifyCode | `test_custom_length` | 验证码格式校验。 | 211 |

---

## 归档说明

1. **编号规则**：`TC-U-NNN`，按文件名字母序 + 代码行号顺序编号；
2. **代码位置**：用例代码位于 `tests/unit/` 目录，与本文档一一对应；
3. **执行方式**：`pytest tests/unit/ -v` 可查看每个用例的执行结果；
4. **变更管理**：新增/删除用例需同步更新本文档编号；
5. **追溯关系**：见 `RTM-001-需求追溯矩阵.md`。

**统计**：共 210 个用例。