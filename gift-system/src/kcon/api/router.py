"""路由表：路径 → 处理器方法映射。"""
from typing import Callable, Dict, List, Optional, Tuple
# 路由定义：(方法, 路径, 处理器方法名, 是否需要 body, 是否传 ip)
ROUTES: List[Tuple[str, str, str, bool, bool]] = [
    # 认证
    ("POST", "/api/auth/send-code", "send_code", True, True),
    ("POST", "/api/auth/login", "login", True, False),
    ("POST", "/api/auth/logout", "logout", False, False),
    ("GET", "/api/me", "me", False, False),
    # 管理员
    ("GET", "/api/admin/stats", "admin_stats", False, False),
    ("GET", "/api/admin/channels", "admin_channels", False, False),
    ("POST", "/api/admin/channels", "admin_create_channel", True, False),
    ("POST", "/api/admin/channel/suspend", "admin_suspend_channel", True, False),
    ("POST", "/api/admin/channel/update", "admin_update_channel", True, False),
    ("POST", "/api/admin/channel/delete", "admin_delete_channel", True, False),
    # 注：v1 不创建签到员账号（现场签到由下游 KconLucky 系统负责），
    # /api/admin/staff 不对外暴露，处理器仅保留给历史测试。
    ("POST", "/api/admin/approver/replace", "admin_replace_approver", True, False),
    # 管理员账户管理
    ("GET", "/api/admin/admins", "admin_list_admins", False, False),
    ("POST", "/api/admin/admins", "admin_create_admin", True, False),
    ("POST", "/api/admin/admins/disable", "admin_disable_admin", True, False),
    ("POST", "/api/admin/admins/delete", "admin_delete_admin", True, False),
    # 邮件出箱
    ("GET", "/api/mailbox", "mailbox_list", False, False),
    # 审批员
    ("GET", "/api/approver/overview", "approver_overview", False, False),
    ("GET", "/api/approver/referrers", "approver_referrers", False, False),
    ("GET", "/api/approver/recipients", "approver_recipients", False, False),
    ("POST", "/api/approver/referrer/approve", "approver_approve", True, False),
    # 公开注册
    ("POST", "/api/referrer/register", "referrer_register", True, True),
    ("POST", "/api/ticket/register", "ticket_register", True, True),
    # 一次性审批
    ("POST", "/api/approve/info", "approve_info", True, False),
    ("POST", "/api/approve/do", "approve_do", True, False),
    ("POST", "/api/approve/resend", "approve_resend", True, False),
    # 签到
    ("POST", "/api/checkin/lookup", "checkin_lookup", True, True),
    ("POST", "/api/checkin/confirm", "checkin_confirm", True, True),
    ("POST", "/api/checkin/staff/login", "checkin_staff_login", True, False),
    ("POST", "/api/checkin/retry-sync", "checkin_retry_sync", True, False),
    # 扫码落地页
    ("POST", "/api/code/info", "code_info", True, False),
]
# 快速查找表
ROUTE_MAP: Dict[Tuple[str, str], Tuple[str, bool, bool]] = {
    (method, path): (handler, need_body, need_ip)
    for method, path, handler, need_body, need_ip in ROUTES
}
# 页面路由（静态 HTML）
# 注：/checkin 页面已移除（现场签到由下游 KconLucky 系统负责），
# 签到相关 API 保留但不在界面暴露入口。
PAGES: Dict[str, str] = {
    "/": "index.html",
    "/login": "login.html",
    # 对外路径：避免与下游 KconLucky 的 /admin/ 冲突
    "/gift-admin": "admin.html",
    "/gift-approver": "approver.html",
    # 本地开发兼容路径（对外由 Nginx 只暴露 /gift-admin 与 /gift-approver）
    "/admin": "admin.html",
    "/approver": "approver.html",
    "/mailbox": "mailbox.html",
}
# 前缀路由（动态页面）
PREFIX_PAGES: List[Tuple[str, str]] = [
    ("/r/", "referrer_register.html"),
    ("/t/", "ticket_register.html"),
    ("/c/", "code_landing.html"),
    ("/approve/", "approve.html"),
]
def match_route(method: str, path: str) -> Optional[Tuple[str, bool, bool]]:
    """匹配 API 路由。"""
    return ROUTE_MAP.get((method, path))
def match_page(path: str) -> Optional[str]:
    """匹配页面路由。"""
    if path in PAGES:
        return PAGES[path]
    for prefix, page in PREFIX_PAGES:
        if path.startswith(prefix):
            return page
    return None