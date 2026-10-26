# TC-I 集成测试用例集

| 项 | 内容 |
|---|---|
| 用例集编号 | TC-I |
| 用例总数 | 295 |
| 说明 | 验证服务层与基础设施层的协作，使用真实 SQLite，Mock 外部依赖 |
| 归档位置 | `tests/integration/` |
| 执行命令 | `.venv/bin/pytest tests/integration/ -v` |

---

## 用例清单

| 用例编号 | 所属文件 | 测试类 | 用例方法 | 验证点 | 代码行 |
|---|---|---|---|---|---|
| TC-I-001 | test_api_handlers.py | TestMiddleware | `test_session_create_get_destroy` | session create get destroy | 66 |
| TC-I-002 | test_api_handlers.py | TestMiddleware | `test_session_expired` | session expired | 73 |
| TC-I-003 | test_api_handlers.py | TestMiddleware | `test_session_clear` | session clear | 82 |
| TC-I-004 | test_api_handlers.py | TestMiddleware | `test_rate_limiter_allow` | rate limiter allow | 87 |
| TC-I-005 | test_api_handlers.py | TestMiddleware | `test_rate_limiter_exceed` | rate limiter exceed | 92 |
| TC-I-006 | test_api_handlers.py | TestMiddleware | `test_rate_limiter_clear` | rate limiter clear | 98 |
| TC-I-007 | test_api_handlers.py | TestMiddleware | `test_auth_context_properties` | auth context properties | 104 |
| TC-I-008 | test_api_handlers.py | TestMiddleware | `test_auth_context_approver` | auth context approver | 110 |
| TC-I-009 | test_api_handlers.py | TestMiddleware | `test_auth_context_staff` | auth context staff | 114 |
| TC-I-010 | test_api_handlers.py | TestRouter | `test_match_route_found` | match route found | 118 |
| TC-I-011 | test_api_handlers.py | TestRouter | `test_match_route_not_found` | match route not found | 122 |
| TC-I-012 | test_api_handlers.py | TestRouter | `test_match_page_static` | match page static | 124 |
| TC-I-013 | test_api_handlers.py | TestRouter | `test_match_page_prefix` | match page prefix | 126 |
| TC-I-014 | test_api_handlers.py | TestRouter | `test_match_page_not_found` | match page not found | 131 |
| TC-I-015 | test_api_handlers.py | TestHandlersAuth | `test_send_code_invalid_email` | send code invalid email | 134 |
| TC-I-016 | test_api_handlers.py | TestHandlersAuth | `test_login_failure` | login failure | 138 |
| TC-I-017 | test_api_handlers.py | TestHandlersAuth | `test_logout_with_sid` | logout with sid | 142 |
| TC-I-018 | test_api_handlers.py | TestHandlersAuth | `test_logout_without_sid` | logout without sid | 148 |
| TC-I-019 | test_api_handlers.py | TestHandlersAuth | `test_me_unauthorized` | me unauthorized | 152 |
| TC-I-020 | test_api_handlers.py | TestHandlersAuth | `test_me_bad_sid` | me bad sid | 157 |
| TC-I-021 | test_api_handlers.py | TestHandlersAuth | `test_me_success` | me success | 162 |
| TC-I-022 | test_api_handlers.py | TestHandlersAuth | `test_forbidden_role` | forbidden role | 168 |
| TC-I-023 | test_api_handlers.py | TestHandlersAdmin | `test_admin_stats` | admin stats | 175 |
| TC-I-024 | test_api_handlers.py | TestHandlersAdmin | `test_admin_channels` | admin channels | 181 |
| TC-I-025 | test_api_handlers.py | TestHandlersAdmin | `test_admin_create_channel` | admin create channel | 186 |
| TC-I-026 | test_api_handlers.py | TestHandlersAdmin | `test_admin_create_channel_default_cap` | admin create channel default cap | 193 |
| TC-I-027 | test_api_handlers.py | TestHandlersAdmin | `test_admin_suspend` | admin suspend | 200 |
| TC-I-028 | test_api_handlers.py | TestHandlersAdmin | `test_admin_delete` | admin delete | 209 |
| TC-I-029 | test_api_handlers.py | TestHandlersAdmin | `test_admin_replace_approver_success` | admin replace approver success | 218 |
| TC-I-030 | test_api_handlers.py | TestHandlersAdmin | `test_admin_replace_approver_forbidden` | 审批员无权更换审批员 → 403。 | 231 |
| TC-I-031 | test_api_handlers.py | TestHandlersAdmin | `test_admin_replace_approver_unauthorized` | admin replace approver unauthorized | 243 |
| TC-I-032 | test_api_handlers.py | TestHandlersAdmin | `test_admin_create_staff_invalid_name` | admin create staff invalid name | 248 |
| TC-I-033 | test_api_handlers.py | TestHandlersAdmin | `test_admin_create_staff_short_account` | admin create staff short account | 254 |
| TC-I-034 | test_api_handlers.py | TestHandlersAdmin | `test_admin_create_staff_short_password` | admin create staff short password | 261 |
| TC-I-035 | test_api_handlers.py | TestHandlersAdmin | `test_admin_create_staff_duplicate` | admin create staff duplicate | 268 |
| TC-I-036 | test_api_handlers.py | TestHandlersApprover | `test_overview` | overview | 289 |
| TC-I-037 | test_api_handlers.py | TestHandlersApprover | `test_referrers` | referrers | 296 |
| TC-I-038 | test_api_handlers.py | TestHandlersApprover | `test_recipients` | recipients | 303 |
| TC-I-039 | test_api_handlers.py | TestHandlersApprover | `test_approve` | approve | 309 |
| TC-I-040 | test_api_handlers.py | TestHandlersPublic | `test_referrer_register_rate_limit` | referrer register rate limit | 318 |
| TC-I-041 | test_api_handlers.py | TestHandlersPublic | `test_ticket_register_rate_limit` | ticket register rate limit | 331 |
| TC-I-042 | test_api_handlers.py | TestHandlersApprove | `test_approve_info_invalid_token` | approve info invalid token | 350 |
| TC-I-043 | test_api_handlers.py | TestHandlersApprove | `test_approve_info_used_token` | approve info used token | 355 |
| TC-I-044 | test_api_handlers.py | TestHandlersApprove | `test_approve_info_referrer` | approve info referrer | 363 |
| TC-I-045 | test_api_handlers.py | TestHandlersApprove | `test_approve_info_referrer_missing` | approve info referrer missing | 370 |
| TC-I-046 | test_api_handlers.py | TestHandlersApprove | `test_approve_info_referrer_no_channel` | 推荐人存在但渠道被删（ch 为 None 分支）。 | 377 |
| TC-I-047 | test_api_handlers.py | TestHandlersApprove | `test_approve_info_ticket` | approve info ticket | 392 |
| TC-I-048 | test_api_handlers.py | TestHandlersApprove | `test_approve_info_ticket_missing` | approve info ticket missing | 405 |
| TC-I-049 | test_api_handlers.py | TestHandlersApprove | `test_approve_info_ticket_no_referrer` | 接收人存在但推荐人被删（ref 为 None 分支）。 | 412 |
| TC-I-050 | test_api_handlers.py | TestHandlersApprove | `test_approve_do_invalid_token` | approve do invalid token | 432 |
| TC-I-051 | test_api_handlers.py | TestHandlersApprove | `test_approve_do_referrer` | approve do referrer | 437 |
| TC-I-052 | test_api_handlers.py | TestHandlersApprove | `test_approve_do_ticket` | approve do ticket | 443 |
| TC-I-053 | test_api_handlers.py | TestHandlersApprove | `test_approve_resend_invalid` | approve resend invalid | 455 |
| TC-I-054 | test_api_handlers.py | TestHandlersApprove | `test_approve_resend_success` | approve resend success | 459 |
| TC-I-055 | test_api_handlers.py | TestHandlersCheckin | `test_lookup_and_confirm` | lookup and confirm | 489 |
| TC-I-056 | test_api_handlers.py | TestHandlersCheckin | `test_staff_login_and_checkin` | staff login and checkin | 496 |
| TC-I-057 | test_api_handlers.py | TestHandlersCheckin | `test_staff_login_failure` | staff login failure | 507 |
| TC-I-058 | test_api_handlers.py | TestHandlersCheckin | `test_retry_sync_invalid` | retry sync invalid | 511 |
| TC-I-059 | test_api_handlers.py | TestHandlersCheckin | `test_retry_sync_success` | retry sync success | 516 |
| TC-I-060 | test_api_handlers.py | TestHandlersCheckin | `test_code_info_anonymous` | code info anonymous | 521 |
| TC-I-061 | test_api_handlers.py | TestHandlersCheckin | `test_code_info_authenticated` | code info authenticated | 527 |
| TC-I-062 | test_api_handlers.py | TestHandlersCheckin | `test_code_info_bad_sid` | code info bad sid | 533 |
| TC-I-063 | test_api_handlers.py | TestHandlersCheckin | `test_code_info_approver_sid` | 审批员会话不算已认证（只有 staff/admin）。 | 539 |
| TC-I-064 | test_api_handlers.py | TestServerEndpoints | `test_healthz` | 覆盖 server.py 的 HTTP 层分支。 | 582 |
| TC-I-065 | test_api_handlers.py | TestServerEndpoints | `test_options` | 覆盖 server.py 的 HTTP 层分支。 | 587 |
| TC-I-066 | test_api_handlers.py | TestServerEndpoints | `test_static_file` | 覆盖 server.py 的 HTTP 层分支。 | 591 |
| TC-I-067 | test_api_handlers.py | TestServerEndpoints | `test_static_not_found` | 覆盖 server.py 的 HTTP 层分支。 | 595 |
| TC-I-068 | test_api_handlers.py | TestServerEndpoints | `test_page_not_found` | 覆盖 server.py 的 HTTP 层分支。 | 599 |
| TC-I-069 | test_api_handlers.py | TestServerEndpoints | `test_api_not_found_get` | 覆盖 server.py 的 HTTP 层分支。 | 603 |
| TC-I-070 | test_api_handlers.py | TestServerEndpoints | `test_api_not_found_post` | 覆盖 server.py 的 HTTP 层分支。 | 607 |
| TC-I-071 | test_api_handlers.py | TestServerEndpoints | `test_invalid_json_body` | 覆盖 server.py 的 HTTP 层分支。 | 611 |
| TC-I-072 | test_api_handlers.py | TestServerEndpoints | `test_form_urlencoded_body` | 覆盖 server.py 的 HTTP 层分支。 | 620 |
| TC-I-073 | test_api_handlers.py | TestServerEndpoints | `test_empty_body` | 覆盖 server.py 的 HTTP 层分支。 | 630 |
| TC-I-074 | test_api_handlers.py | TestServerEndpoints | `test_page_index` | 覆盖 server.py 的 HTTP 层分支。 | 634 |
| TC-I-075 | test_api_handlers.py | TestServerEndpoints | `test_unhandled_exception` | 覆盖 500 分支。 | 638 |
| TC-I-076 | test_api_handlers.py | TestServerEndpoints | `test_send_html_direct` | 覆盖 _send_html（40-45）。 | 649 |
| TC-I-077 | test_api_handlers.py | TestServerEndpoints | `test_main_function` | 覆盖 main()（163-175）。 | 663 |
| TC-I-078 | test_api_handlers.py | TestServerEndpoints | `test_make_server_default_container` | 覆盖 make_server 的 container=None 分支（143->145）。 | 680 |
| TC-I-079 | test_api_handlers.py | TestServerEndpoints | `test_make_server_with_container` | 覆盖 make_server 的 container 非空分支（143 短路）。 | 688 |
| TC-I-080 | test_api_handlers.py | TestServerEndpoints | `test_main_module_entry` | 覆盖 if __name__ == '__main__' 分支（175）。 | 696 |
| TC-I-081 | test_api_handlers.py | TestServerEndpoints | `test_dispatch_public_no_ip_branch` | 覆盖公开接口 need_ip=False 的分支（143->145）。 | 711 |
| TC-I-082 | test_api_handlers.py | TestServerEndpoints | `test_resolve_container_with_existing` | 覆盖 _resolve_container 的 container 非空分支。 | 723 |
| TC-I-083 | test_api_handlers.py | TestServerEndpoints | `test_resolve_container_with_none` | 覆盖 _resolve_container 的 container 为 None 分支。 | 729 |
| TC-I-084 | test_api_handlers.py | TestServerEndpoints | `test_make_server_container_shortcircuit` | 覆盖 143->145 假分支（container 不为 None，走 else）。 | 736 |
| TC-I-085 | test_auth_service.py | TestSendVerifyCode | `test_send_code_success` | send code success | 6 |
| TC-I-086 | test_auth_service.py | TestSendVerifyCode | `test_send_code_invalid_email` | send code invalid email | 11 |
| TC-I-087 | test_auth_service.py | TestSendVerifyCode | `test_send_code_resend_too_soon` | send code resend too soon | 16 |
| TC-I-088 | test_auth_service.py | TestSendVerifyCode | `test_send_code_after_interval` | send code after interval | 21 |
| TC-I-089 | test_auth_service.py | TestSendVerifyCode | `test_send_code_daily_limit` | send code daily limit | 26 |
| TC-I-090 | test_auth_service.py | TestSendVerifyCode | `test_send_code_invalid_created_at` | send code invalid created at | 33 |
| TC-I-091 | test_auth_service.py | TestLogin | `test_login_admin_success` | login admin success | 41 |
| TC-I-092 | test_auth_service.py | TestLogin | `test_login_invalid_email` | login invalid email | 47 |
| TC-I-093 | test_auth_service.py | TestLogin | `test_login_invalid_code_format` | login invalid code format | 52 |
| TC-I-094 | test_auth_service.py | TestLogin | `test_login_no_code_requested` | login no code requested | 56 |
| TC-I-095 | test_auth_service.py | TestLogin | `test_login_expired_code` | login expired code | 60 |
| TC-I-096 | test_auth_service.py | TestLogin | `test_login_invalid_expires_format` | login invalid expires format | 66 |
| TC-I-097 | test_auth_service.py | TestLogin | `test_login_too_many_fails` | login too many fails | 73 |
| TC-I-098 | test_auth_service.py | TestLogin | `test_login_wrong_code` | login wrong code | 80 |
| TC-I-099 | test_auth_service.py | TestLogin | `test_login_wrong_code_remaining_zero` | login wrong code remaining zero | 86 |
| TC-I-100 | test_auth_service.py | TestLogin | `test_login_approver_success` | login approver success | 94 |
| TC-I-101 | test_auth_service.py | TestLogin | `test_login_no_permission` | login no permission | 101 |
| TC-I-102 | test_auth_service.py | TestStaffLogin | `test_staff_login_not_found` | staff login not found | 107 |
| TC-I-103 | test_auth_service.py | TestStaffLogin | `test_staff_login_wrong_password` | staff login wrong password | 111 |
| TC-I-104 | test_auth_service.py | TestStaffLogin | `test_staff_login_success` | staff login success | 117 |
| TC-I-105 | test_channel_service.py | TestCreateChannel | `test_create_success` | create success | 6 |
| TC-I-106 | test_channel_service.py | TestCreateChannel | `test_create_invalid_channel_name` | create invalid channel name | 15 |
| TC-I-107 | test_channel_service.py | TestCreateChannel | `test_create_invalid_approver_name` | create invalid approver name | 19 |
| TC-I-108 | test_channel_service.py | TestCreateChannel | `test_create_invalid_email` | create invalid email | 23 |
| TC-I-109 | test_channel_service.py | TestCreateChannel | `test_create_invalid_quota` | create invalid quota | 27 |
| TC-I-110 | test_channel_service.py | TestCreateChannel | `test_create_invalid_cap` | create invalid cap | 31 |
| TC-I-111 | test_channel_service.py | TestCreateChannel | `test_create_invalid_remark` | create invalid remark | 35 |
| TC-I-112 | test_channel_service.py | TestCreateChannel | `test_create_duplicate_name` | create duplicate name | 40 |
| TC-I-113 | test_channel_service.py | TestCreateChannel | `test_create_duplicate_approver_email` | create duplicate approver email | 45 |
| TC-I-114 | test_channel_service.py | TestCreateChannel | `test_create_channel_limit` | create channel limit | 50 |
| TC-I-115 | test_channel_service.py | TestCreateChannel | `test_create_total_quota_limit` | create total quota limit | 58 |
| TC-I-116 | test_channel_service.py | TestSuspendDelete | `test_suspend_success` | suspend success | 68 |
| TC-I-117 | test_channel_service.py | TestSuspendDelete | `test_suspend_not_found` | suspend not found | 73 |
| TC-I-118 | test_channel_service.py | TestSuspendDelete | `test_suspend_already_suspended` | suspend already suspended | 77 |
| TC-I-119 | test_channel_service.py | TestSuspendDelete | `test_delete_not_found` | delete not found | 83 |
| TC-I-120 | test_channel_service.py | TestSuspendDelete | `test_delete_with_data` | delete with data | 87 |
| TC-I-121 | test_channel_service.py | TestSuspendDelete | `test_delete_empty_channel` | delete empty channel | 93 |
| TC-I-122 | test_channel_service.py | TestQueries | `test_list_all` | list all | 100 |
| TC-I-123 | test_channel_service.py | TestQueries | `test_get_by_id` | get by id | 104 |
| TC-I-124 | test_channel_service.py | TestQueries | `test_get_by_code` | get by code | 107 |
| TC-I-125 | test_channel_service.py | TestQueries | `test_get_by_code_suspended` | get by code suspended | 110 |
| TC-I-126 | test_checkin_stats_service.py | TestCheckinLookup | `test_lookup_success` | lookup success | 6 |
| TC-I-127 | test_checkin_stats_service.py | TestCheckinLookup | `test_lookup_empty_code` | lookup empty code | 13 |
| TC-I-128 | test_checkin_stats_service.py | TestCheckinLookup | `test_lookup_invalid_code` | lookup invalid code | 17 |
| TC-I-129 | test_checkin_stats_service.py | TestCheckinLookup | `test_lookup_lowercase_normalized` | lookup lowercase normalized | 21 |
| TC-I-130 | test_checkin_stats_service.py | TestCheckinLookup | `test_lookup_pending_recipient` | lookup pending recipient | 26 |
| TC-I-131 | test_checkin_stats_service.py | TestCheckinLookup | `test_lookup_rejected_recipient` | lookup rejected recipient | 36 |
| TC-I-132 | test_checkin_stats_service.py | TestCheckinLookup | `test_lookup_cancelled_recipient` | lookup cancelled recipient | 46 |
| TC-I-133 | test_checkin_stats_service.py | TestCheckinLookup | `test_lookup_already_checked_in` | 已签到状态 lookup 放行（由 confirm 判断重复）。 | 56 |
| TC-I-134 | test_checkin_stats_service.py | TestCheckinConfirm | `test_confirm_success` | confirm success | 66 |
| TC-I-135 | test_checkin_stats_service.py | TestCheckinConfirm | `test_confirm_duplicate` | confirm duplicate | 81 |
| TC-I-136 | test_checkin_stats_service.py | TestCheckinConfirm | `test_confirm_invalid_code` | confirm invalid code | 88 |
| TC-I-137 | test_checkin_stats_service.py | TestCheckinConfirm | `test_confirm_external_failure_not_blocking` | confirm external failure not blocking | 92 |
| TC-I-138 | test_checkin_stats_service.py | TestM08Flush | `test_flush_when_batch_full` | flush when batch full | 103 |
| TC-I-139 | test_checkin_stats_service.py | TestM08Flush | `test_flush_when_window_expired` | flush when window expired | 124 |
| TC-I-140 | test_checkin_stats_service.py | TestM08Flush | `test_flush_empty_queue` | flush empty queue | 135 |
| TC-I-141 | test_checkin_stats_service.py | TestM08Flush | `test_flush_no_approver` | flush no approver | 138 |
| TC-I-142 | test_checkin_stats_service.py | TestM08Flush | `test_flush_force` | flush force | 143 |
| TC-I-143 | test_checkin_stats_service.py | TestCodeInfo | `test_code_info_unauthenticated` | code info unauthenticated | 151 |
| TC-I-144 | test_checkin_stats_service.py | TestCodeInfo | `test_code_info_authenticated` | code info authenticated | 160 |
| TC-I-145 | test_checkin_stats_service.py | TestCodeInfo | `test_code_info_invalid` | code info invalid | 167 |
| TC-I-146 | test_checkin_stats_service.py | TestCodeInfo | `test_code_info_empty` | code info empty | 171 |
| TC-I-147 | test_checkin_stats_service.py | TestCodeInfo | `test_code_info_checked_in` | code info checked in | 175 |
| TC-I-148 | test_checkin_stats_service.py | TestStats | `test_admin_stats_empty` | admin stats empty | 184 |
| TC-I-149 | test_checkin_stats_service.py | TestStats | `test_admin_stats_with_data` | admin stats with data | 189 |
| TC-I-150 | test_checkin_stats_service.py | TestStats | `test_channel_stats_empty` | channel stats empty | 203 |
| TC-I-151 | test_checkin_stats_service.py | TestStats | `test_channel_stats_not_found` | channel stats not found | 209 |
| TC-I-152 | test_checkin_stats_service.py | TestStats | `test_channel_stats_with_data` | channel stats with data | 213 |
| TC-I-153 | test_coverage_gaps.py | TestAppContainer | `test_container_with_defaults` | container with defaults | 21 |
| TC-I-154 | test_coverage_gaps.py | TestAppContainer | `test_container_with_db_path_override` | container with db path override | 28 |
| TC-I-155 | test_coverage_gaps.py | TestAppContainer | `test_container_without_init_schema` | container without init schema | 33 |
| TC-I-156 | test_coverage_gaps.py | TestConfigBaseDir | `test_base_dir_fallback` | base dir fallback | 39 |
| TC-I-157 | test_coverage_gaps.py | TestMailerGaps | `test_base_mailer_send_success` | base mailer send success | 56 |
| TC-I-158 | test_coverage_gaps.py | TestMailerGaps | `test_base_mailer_send_render_error` | base mailer send render error | 63 |
| TC-I-159 | test_coverage_gaps.py | TestMailerGaps | `test_smtp_mailer_success_mock` | smtp mailer success mock | 68 |
| TC-I-160 | test_coverage_gaps.py | TestQrCodeGaps | `test_load_font_fallback` | load font fallback | 87 |
| TC-I-161 | test_coverage_gaps.py | TestQrCodeGaps | `test_load_font_truetype_error` | load font truetype error | 92 |
| TC-I-162 | test_coverage_gaps.py | TestCheckinGaps | `test_lookup_code_recipient_missing` | lookup code recipient missing | 101 |
| TC-I-163 | test_coverage_gaps.py | TestCheckinGaps | `test_confirm_duplicate_returns_result` | confirm duplicate returns result | 108 |
| TC-I-164 | test_coverage_gaps.py | TestCheckinGaps | `test_code_info_recipient_missing` | code info recipient missing | 116 |
| TC-I-165 | test_coverage_gaps.py | TestReferrerGaps | `test_approve_referrer_missing` | approve referrer missing | 124 |
| TC-I-166 | test_coverage_gaps.py | TestReferrerGaps | `test_approve_referrer_mark_used_fails` | approve referrer mark used fails | 132 |
| TC-I-167 | test_coverage_gaps.py | TestReferrerGaps | `test_register_referrer_no_approver` | register referrer no approver | 139 |
| TC-I-168 | test_coverage_gaps.py | TestReferrerGaps | `test_approve_referrer_mark_used_race` | 覆盖 referrer_service.py:99 —— mark_used 返回 0 的分支。 | 146 |
| TC-I-169 | test_coverage_gaps.py | TestTicketGaps | `test_register_recipient_channel_missing` | register recipient channel missing | 160 |
| TC-I-170 | test_coverage_gaps.py | TestTicketGaps | `test_approve_recipient_missing` | approve recipient missing | 168 |
| TC-I-171 | test_coverage_gaps.py | TestTicketGaps | `test_approve_recipient_mark_used_fails` | approve recipient mark used fails | 175 |
| TC-I-172 | test_coverage_gaps.py | TestTicketGaps | `test_approve_recipient_mark_used_race` | 覆盖 ticket_service.py:101 —— mark_used 返回 0 的分支。 | 184 |
| TC-I-173 | test_coverage_gaps.py | TestTicketGaps | `test_register_duplicate_email_no_other_referrer` | 覆盖 ticket_service.py:47->50 —— 邮箱冲突但原推荐人查不到。 | 200 |
| TC-I-174 | test_infra.py | TestConfig | `test_md5_email_lowercase` | md5 email lowercase | 15 |
| TC-I-175 | test_infra.py | TestConfig | `test_md5_email_strip` | md5 email strip | 17 |
| TC-I-176 | test_infra.py | TestConfig | `test_is_admin_email_true` | is admin email true | 19 |
| TC-I-177 | test_infra.py | TestConfig | `test_is_admin_email_false` | is admin email false | 22 |
| TC-I-178 | test_infra.py | TestConfig | `test_admin_name` | admin name | 25 |
| TC-I-179 | test_infra.py | TestConfig | `test_admin_name_unknown` | admin name unknown | 28 |
| TC-I-180 | test_infra.py | TestConfig | `test_load_config_default` | load config default | 31 |
| TC-I-181 | test_infra.py | TestConfig | `test_config_default_db_path` | config default db path | 35 |
| TC-I-182 | test_infra.py | TestEmailTemplates | `test_render_all_templates[0]` | render all templates[0] | 98 |
| TC-I-183 | test_infra.py | TestEmailTemplates | `test_render_all_templates[1]` | render all templates[1] | 98 |
| TC-I-184 | test_infra.py | TestEmailTemplates | `test_render_all_templates[2]` | render all templates[2] | 98 |
| TC-I-185 | test_infra.py | TestEmailTemplates | `test_render_all_templates[3]` | render all templates[3] | 98 |
| TC-I-186 | test_infra.py | TestEmailTemplates | `test_render_all_templates[4]` | render all templates[4] | 98 |
| TC-I-187 | test_infra.py | TestEmailTemplates | `test_render_all_templates[5]` | render all templates[5] | 98 |
| TC-I-188 | test_infra.py | TestEmailTemplates | `test_render_all_templates[6]` | render all templates[6] | 98 |
| TC-I-189 | test_infra.py | TestEmailTemplates | `test_render_all_templates[7]` | render all templates[7] | 98 |
| TC-I-190 | test_infra.py | TestEmailTemplates | `test_render_all_templates[8]` | render all templates[8] | 98 |
| TC-I-191 | test_infra.py | TestEmailTemplates | `test_render_all_templates[9]` | render all templates[9] | 98 |
| TC-I-192 | test_infra.py | TestEmailTemplates | `test_render_code_template` | render code template | 106 |
| TC-I-193 | test_infra.py | TestEmailTemplates | `test_render_unknown_template` | render unknown template | 110 |
| TC-I-194 | test_infra.py | TestEmailTemplates | `test_m04_mobile_placeholder` | m04 mobile placeholder | 113 |
| TC-I-195 | test_infra.py | TestEmailTemplates | `test_m06_mobile_placeholder` | m06 mobile placeholder | 118 |
| TC-I-196 | test_infra.py | TestEmailTemplates | `test_m07_mobile_placeholder` | m07 mobile placeholder | 123 |
| TC-I-197 | test_infra.py | TestEmailTemplates | `test_btn_secondary_color` | btn secondary color | 128 |
| TC-I-198 | test_infra.py | TestEmailTemplates | `test_qr_block_no_caption` | qr block no caption | 131 |
| TC-I-199 | test_infra.py | TestEmailTemplates | `test_priority_tag` | priority tag | 134 |
| TC-I-200 | test_infra.py | TestMailer | `test_outbox_mailer_saves` | outbox mailer saves | 139 |
| TC-I-201 | test_infra.py | TestMailer | `test_outbox_mailer_unknown_template` | outbox mailer unknown template | 154 |
| TC-I-202 | test_infra.py | TestMailer | `test_smtp_mailer_failure` | smtp mailer failure | 165 |
| TC-I-203 | test_infra.py | TestMailer | `test_outbox_deliver_direct` | outbox deliver direct | 175 |
| TC-I-204 | test_infra.py | TestMailer | `test_fake_mailer_count` | fake mailer count | 183 |
| TC-I-205 | test_infra.py | TestMailer | `test_fake_mailer_fail` | fake mailer fail | 191 |
| TC-I-206 | test_infra.py | TestQrCode | `test_pillow_generate_png` | pillow generate png | 198 |
| TC-I-207 | test_infra.py | TestQrCode | `test_pillow_generate_png_no_subtitle` | pillow generate png no subtitle | 204 |
| TC-I-208 | test_infra.py | TestQrCode | `test_pillow_generate_datauri` | pillow generate datauri | 208 |
| TC-I-209 | test_infra.py | TestQrCode | `test_fake_qr_records_calls` | fake qr records calls | 212 |
| TC-I-210 | test_infra.py | TestQrCode | `test_fake_qr_datauri` | fake qr datauri | 217 |
| TC-I-211 | test_infra.py | TestExternalCheckin | `test_null_adapter_success` | null adapter success | 223 |
| TC-I-212 | test_infra.py | TestExternalCheckin | `test_failing_adapter` | failing adapter | 229 |
| TC-I-213 | test_infra.py | TestDatabase | `test_init_schema_idempotent` | init schema idempotent | 237 |
| TC-I-214 | test_infra.py | TestDatabase | `test_query_one_none` | query one none | 246 |
| TC-I-215 | test_infra.py | TestDatabase | `test_scalar_none` | scalar none | 250 |
| TC-I-216 | test_infra.py | TestDatabase | `test_transaction_rollback` | transaction rollback | 254 |
| TC-I-217 | test_infra.py | TestDatabase | `test_execute_rowcount` | execute rowcount | 264 |
| TC-I-218 | test_infra.py | TestRepositories | `test_admin_crud` | admin crud | 274 |
| TC-I-219 | test_infra.py | TestRepositories | `test_channel_crud` | channel crud | 282 |
| TC-I-220 | test_infra.py | TestRepositories | `test_approver_crud` | approver crud | 293 |
| TC-I-221 | test_infra.py | TestRepositories | `test_referrer_crud` | referrer crud | 304 |
| TC-I-222 | test_infra.py | TestRepositories | `test_referrer_resubmit` | referrer resubmit | 319 |
| TC-I-223 | test_infra.py | TestRepositories | `test_recipient_crud` | recipient crud | 332 |
| TC-I-224 | test_infra.py | TestRepositories | `test_code_crud` | code crud | 349 |
| TC-I-225 | test_infra.py | TestRepositories | `test_checkin_crud` | checkin crud | 359 |
| TC-I-226 | test_infra.py | TestRepositories | `test_staff_crud` | staff crud | 369 |
| TC-I-227 | test_infra.py | TestRepositories | `test_token_crud` | token crud | 374 |
| TC-I-228 | test_infra.py | TestRepositories | `test_token_invalidate` | token invalidate | 382 |
| TC-I-229 | test_infra.py | TestRepositories | `test_email_crud` | email crud | 388 |
| TC-I-230 | test_infra.py | TestRepositories | `test_audit_crud` | audit crud | 398 |
| TC-I-231 | test_infra.py | TestRepositories | `test_verify_code_crud` | verify code crud | 403 |
| TC-I-232 | test_infra.py | TestRepositories | `test_m08_crud` | m08 crud | 413 |
| TC-I-233 | test_infra.py | TestRepositories | `test_stats_repo` | stats repo | 422 |
| TC-I-234 | test_infra.py | TestRandomSourceExtra | `test_secure_random_all_methods` | secure random all methods | 429 |
| TC-I-235 | test_infra.py | TestRandomSourceExtra | `test_sequence_random_choice_empty_then_wrap` | sequence random choice empty then wrap | 437 |
| TC-I-236 | test_referrer_ticket_service.py | TestReferrerRegister | `test_register_success` | register success | 6 |
| TC-I-237 | test_referrer_ticket_service.py | TestReferrerRegister | `test_register_invalid_channel` | register invalid channel | 16 |
| TC-I-238 | test_referrer_ticket_service.py | TestReferrerRegister | `test_register_invalid_name` | register invalid name | 21 |
| TC-I-239 | test_referrer_ticket_service.py | TestReferrerRegister | `test_register_invalid_company` | register invalid company | 27 |
| TC-I-240 | test_referrer_ticket_service.py | TestReferrerRegister | `test_register_invalid_department` | register invalid department | 33 |
| TC-I-241 | test_referrer_ticket_service.py | TestReferrerRegister | `test_register_invalid_mobile` | register invalid mobile | 39 |
| TC-I-242 | test_referrer_ticket_service.py | TestReferrerRegister | `test_register_invalid_email` | register invalid email | 45 |
| TC-I-243 | test_referrer_ticket_service.py | TestReferrerRegister | `test_register_duplicate_email` | register duplicate email | 51 |
| TC-I-244 | test_referrer_ticket_service.py | TestReferrerRegister | `test_register_rejected_within_7_days` | register rejected within 7 days | 58 |
| TC-I-245 | test_referrer_ticket_service.py | TestReferrerRegister | `test_register_rejected_after_7_days` | register rejected after 7 days | 67 |
| TC-I-246 | test_referrer_ticket_service.py | TestReferrerApprove | `test_approve_success` | approve success | 82 |
| TC-I-247 | test_referrer_ticket_service.py | TestReferrerApprove | `test_approve_invalid_token` | approve invalid token | 90 |
| TC-I-248 | test_referrer_ticket_service.py | TestReferrerApprove | `test_approve_wrong_type` | approve wrong type | 94 |
| TC-I-249 | test_referrer_ticket_service.py | TestReferrerApprove | `test_approve_used_token` | approve used token | 103 |
| TC-I-250 | test_referrer_ticket_service.py | TestReferrerApprove | `test_approve_expired_token` | approve expired token | 110 |
| TC-I-251 | test_referrer_ticket_service.py | TestReferrerApprove | `test_approve_already_processed` | approve already processed | 117 |
| TC-I-252 | test_referrer_ticket_service.py | TestReferrerApprove | `test_reject_success` | reject success | 124 |
| TC-I-253 | test_referrer_ticket_service.py | TestReferrerApprove | `test_approve_by_id_success` | approve by id success | 132 |
| TC-I-254 | test_referrer_ticket_service.py | TestReferrerApprove | `test_approve_by_id_not_found` | approve by id not found | 137 |
| TC-I-255 | test_referrer_ticket_service.py | TestReferrerApprove | `test_approve_by_id_wrong_channel` | approve by id wrong channel | 141 |
| TC-I-256 | test_referrer_ticket_service.py | TestReferrerApprove | `test_approve_by_id_no_active_token` | approve by id no active token | 146 |
| TC-I-257 | test_referrer_ticket_service.py | TestReferrerQueries | `test_list_by_channel` | list by channel | 153 |
| TC-I-258 | test_referrer_ticket_service.py | TestReferrerQueries | `test_list_by_status` | list by status | 157 |
| TC-I-259 | test_referrer_ticket_service.py | TestReferrerQueries | `test_get` | get | 160 |
| TC-I-260 | test_referrer_ticket_service.py | TestReferrerQueries | `test_get_by_code` | get by code | 163 |
| TC-I-261 | test_referrer_ticket_service.py | TestTicketRegister | `test_register_success` | register success | 167 |
| TC-I-262 | test_referrer_ticket_service.py | TestTicketRegister | `test_register_invalid_referrer` | register invalid referrer | 174 |
| TC-I-263 | test_referrer_ticket_service.py | TestTicketRegister | `test_register_referrer_not_approved` | register referrer not approved | 179 |
| TC-I-264 | test_referrer_ticket_service.py | TestTicketRegister | `test_register_channel_suspended` | register channel suspended | 185 |
| TC-I-265 | test_referrer_ticket_service.py | TestTicketRegister | `test_register_invalid_name` | register invalid name | 192 |
| TC-I-266 | test_referrer_ticket_service.py | TestTicketRegister | `test_register_invalid_company` | register invalid company | 198 |
| TC-I-267 | test_referrer_ticket_service.py | TestTicketRegister | `test_register_invalid_mobile` | register invalid mobile | 204 |
| TC-I-268 | test_referrer_ticket_service.py | TestTicketRegister | `test_register_invalid_email` | register invalid email | 210 |
| TC-I-269 | test_referrer_ticket_service.py | TestTicketRegister | `test_register_duplicate_other_referrer` | register duplicate other referrer | 216 |
| TC-I-270 | test_referrer_ticket_service.py | TestTicketRegister | `test_register_duplicate_same_referrer` | register duplicate same referrer | 232 |
| TC-I-271 | test_referrer_ticket_service.py | TestTicketRegister | `test_register_referrer_cap_reached` | register referrer cap reached | 239 |
| TC-I-272 | test_referrer_ticket_service.py | TestTicketRegister | `test_register_channel_quota_reached` | register channel quota reached | 248 |
| TC-I-273 | test_referrer_ticket_service.py | TestTicketApprove | `test_approve_success` | approve success | 262 |
| TC-I-274 | test_referrer_ticket_service.py | TestTicketApprove | `test_approve_invalid_token` | approve invalid token | 279 |
| TC-I-275 | test_referrer_ticket_service.py | TestTicketApprove | `test_approve_wrong_type` | approve wrong type | 283 |
| TC-I-276 | test_referrer_ticket_service.py | TestTicketApprove | `test_approve_used_token` | approve used token | 289 |
| TC-I-277 | test_referrer_ticket_service.py | TestTicketApprove | `test_approve_expired_token` | approve expired token | 297 |
| TC-I-278 | test_referrer_ticket_service.py | TestTicketApprove | `test_approve_already_processed` | approve already processed | 306 |
| TC-I-279 | test_referrer_ticket_service.py | TestTicketApprove | `test_reject_success` | reject success | 315 |
| TC-I-280 | test_referrer_ticket_service.py | TestTicketApprove | `test_approve_cap_reached_at_approve_time` | approve cap reached at approve time | 324 |
| TC-I-281 | test_referrer_ticket_service.py | TestTicketApprove | `test_approve_quota_reached_at_approve_time` | approve quota reached at approve time | 335 |
| TC-I-282 | test_referrer_ticket_service.py | TestTicketQueries | `test_list` | list | 351 |
| TC-I-283 | test_referrer_ticket_service.py | TestTicketQueries | `test_list_by_channel` | list by channel | 354 |
| TC-I-284 | test_referrer_ticket_service.py | TestTicketQueries | `test_list_by_referrer` | list by referrer | 357 |
| TC-I-285 | test_referrer_ticket_service.py | TestTicketQueries | `test_list_by_status` | list by status | 360 |
| TC-I-286 | test_referrer_ticket_service.py | TestTicketQueries | `test_get_by_code` | get by code | 363 |
| TC-I-287 | test_replace_approver.py | TestReplaceApprover | `test_replace_success` | replace success | 10 |
| TC-I-288 | test_replace_approver.py | TestReplaceApprover | `test_replace_invalidates_old_token` | 换人后，原审批员未使用的推荐人审批令牌应失效。 | 23 |
| TC-I-289 | test_replace_approver.py | TestReplaceApprover | `test_replace_invalid_name` | replace invalid name | 43 |
| TC-I-290 | test_replace_approver.py | TestReplaceApprover | `test_replace_invalid_email` | replace invalid email | 48 |
| TC-I-291 | test_replace_approver.py | TestReplaceApprover | `test_replace_channel_not_found` | replace channel not found | 53 |
| TC-I-292 | test_replace_approver.py | TestReplaceApprover | `test_replace_channel_suspended` | replace channel suspended | 57 |
| TC-I-293 | test_replace_approver.py | TestReplaceApprover | `test_replace_email_used_by_other` | replace email used by other | 63 |
| TC-I-294 | test_replace_approver.py | TestReplaceApprover | `test_replace_same_current_approver` | 新邮箱正是本渠道当前审批员 → 拒绝。 | 70 |
| TC-I-295 | test_replace_approver.py | TestReplaceApprover | `test_replace_no_current_approver` | 渠道无启用审批员时也能换人（current 为 None 分支）。 | 76 |

---

## 归档说明

1. **编号规则**：`TC-I-NNN`，按文件名字母序 + 代码行号顺序编号；
2. **代码位置**：用例代码位于 `tests/integration/` 目录，与本文档一一对应；
3. **执行方式**：`pytest tests/integration/ -v` 可查看每个用例的执行结果；
4. **变更管理**：新增/删除用例需同步更新本文档编号；
5. **追溯关系**：见 `RTM-001-需求追溯矩阵.md`。

**统计**：共 295 个用例。