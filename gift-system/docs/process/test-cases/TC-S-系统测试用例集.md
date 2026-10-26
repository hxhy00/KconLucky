# TC-S 系统测试用例集

| 项 | 内容 |
|---|---|
| 用例集编号 | TC-S |
| 用例总数 | 16 |
| 说明 | 验证端到端业务流程，使用真实 HTTP 服务 |
| 归档位置 | `tests/system/` |
| 执行命令 | `.venv/bin/pytest tests/system/ -v` |

---

## 用例清单

| 用例编号 | 所属文件 | 测试类 | 用例方法 | 验证点 | 代码行 |
|---|---|---|---|---|---|
| TC-S-001 | test_e2e.py | TestS1FullFlow | `test_full_flow` | S1：完整主流程。 | 32 |
| TC-S-002 | test_e2e.py | TestS2AdminLimit | `test_admin_limit` | S2：管理员数量上限。 | 92 |
| TC-S-003 | test_e2e.py | TestS3ChannelLimit | `test_channel_limit` | S3：渠道数量上限 20。 | 103 |
| TC-S-004 | test_e2e.py | TestS4ReferrerCap | `test_referrer_cap` | S4：单人上限拦截。 | 119 |
| TC-S-005 | test_e2e.py | TestS5ChannelQuota | `test_channel_quota` | S5：渠道配额拦截。 | 145 |
| TC-S-006 | test_e2e.py | TestS6EmailDuplicate | `test_email_duplicate` | S6：邮箱重复提交时拦截。 | 169 |
| TC-S-007 | test_e2e.py | TestS7TokenReuse | `test_token_reuse` | S7：一次性链接复用。 | 189 |
| TC-S-008 | test_e2e.py | TestS8RejectRetry | `test_reject_retry` | S8：被拒 7 天重申。 | 206 |
| TC-S-009 | test_e2e.py | TestS9DuplicateCheckin | `test_duplicate_checkin` | S9：重复签到。 | 225 |
| TC-S-010 | test_e2e.py | TestS10CrossChannel | `test_cross_channel` | S10：跨渠道越权。 | 252 |
| TC-S-011 | test_e2e.py | TestS11Unauthorized | `test_unauthorized` | S11：未登录访问。 | 271 |
| TC-S-012 | test_e2e.py | TestS12LandingPageTier | `test_landing_page_tier` | S12：落地页权限分级。 | 279 |
| TC-S-013 | test_e2e.py | TestS13M08Batch | `test_m08_batch` | S13：M-08 满 10 人汇总。 | 310 |
| TC-S-014 | test_e2e.py | TestS14ExternalFailure | `test_external_failure` | S14：外部接口失败不阻断。 | 338 |
| TC-S-015 | test_e2e.py | TestS15FullScale | `test_full_scale` | S15：20 渠道 500 票全流程压测。 | 366 |
| TC-S-016 | test_replace_approver_e2e.py | TestS16ReplaceApproverE2E | `test_replace_approver_e2e` | S16：更换审批员后，旧令牌失效、新申请发给新审批员。 | 7 |

---

## 归档说明

1. **编号规则**：`TC-S-NNN`，按文件名字母序 + 代码行号顺序编号；
2. **代码位置**：用例代码位于 `tests/system/` 目录，与本文档一一对应；
3. **执行方式**：`pytest tests/system/ -v` 可查看每个用例的执行结果；
4. **变更管理**：新增/删除用例需同步更新本文档编号；
5. **追溯关系**：见 `RTM-001-需求追溯矩阵.md`。

**统计**：共 16 个用例。