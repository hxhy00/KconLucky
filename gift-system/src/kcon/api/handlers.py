"""请求处理器：24 个接口的业务编排。"""
from typing import Any, Dict, Optional, Tuple
from ..app import Container
from ..domain import codes, rules, validators
from ..domain.enums import ReferrerStatus, TokenType
from ..infra.repositories import Repositories
from .middleware import AuthContext, RateLimiter, SessionStore
class ApiError(Exception):
    """业务异常（携带 HTTP 状态码）。"""
    def __init__(self, message: str, status: int = 200):
        super().__init__(message)
        self.message = message
        self.status = status
class Handlers:
    """接口处理器集合。"""
    def __init__(self, container: Container, sessions: SessionStore,
                 limiter: RateLimiter):
        self.c = container
        self.sessions = sessions
        self.limiter = limiter
    # ==================== 工具 ====================
    def _auth(self, sid: Optional[str], roles: Optional[list] = None) -> AuthContext:
        """鉴权：无会话 401，角色不符 403。"""
        if not sid:
            raise ApiError("未登录或会话已过期", 401)
        s = self.sessions.get(sid)
        if not s:
            raise ApiError("未登录或会话已过期", 401)
        ctx = AuthContext(s["role"], s["user"], sid)
        if roles and ctx.role not in roles:
            raise ApiError("无权限", 403)
        return ctx
    def _limit(self, key: str, limit: int, window: int) -> None:
        """限流检查。"""
        ok, msg = self.limiter.check(key, limit, window)
        if not ok:
            raise ApiError(msg, 429)
    # ==================== 认证（4） ====================
    def send_code(self, body: Dict[str, Any], ip: str = "") -> Dict[str, Any]:
        """发送验证码。仅 demo_code 开启时回显明文，生产默认不回显。"""
        email = (body.get("email") or "").strip()
        ok, msg, code = self.c.auth.send_verify_code(email)
        resp: Dict[str, Any] = {"ok": ok, "msg": msg}
        if ok and self.c.config.demo_code:
            resp["demo_code"] = code
        return resp
    def login(self, body: Dict[str, Any]) -> Dict[str, Any]:
        """验证码登录。"""
        ok, msg, role, user = self.c.auth.login(body.get("email", ""),
                                                body.get("code", ""))
        if not ok:
            return {"ok": False, "msg": msg}
        sid = self.c.random.token()
        self.sessions.create(sid, role, user)
        return {"ok": True, "msg": msg, "role": role, "user": user, "sid": sid}
    def logout(self, sid: Optional[str]) -> Dict[str, Any]:
        """登出。"""
        if sid:
            self.sessions.destroy(sid)
        return {"ok": True, "msg": "已登出"}
    def me(self, sid: Optional[str]) -> Dict[str, Any]:
        """当前用户。"""
        ctx = self._auth(sid)
        return {"ok": True, "role": ctx.role, "user": ctx.user}
    # ==================== 管理员（6） ====================
    def admin_stats(self, sid: Optional[str]) -> Dict[str, Any]:
        """全局统计。"""
        self._auth(sid, ["admin"])
        stats, channels = self.c.stats.admin_stats()
        return {"ok": True, "total": stats.__dict__, "channels": channels}
    def admin_channels(self, sid: Optional[str]) -> Dict[str, Any]:
        """渠道列表。"""
        self._auth(sid, ["admin"])
        return {"ok": True, "data": self.c.channel.list_all()}
    def admin_create_channel(self, sid: Optional[str],
                             body: Dict[str, Any]) -> Dict[str, Any]:
        """创建渠道。"""
        self._auth(sid, ["admin"])
        ok, msg = self.c.channel.create(
            body.get("name", ""), body.get("approver_name", ""),
            body.get("approver_email", ""), body.get("quota", 0),
            body.get("referrer_cap", self.c.config.default_referrer_cap),
            body.get("remark", ""))
        return {"ok": ok, "msg": msg}
    def admin_suspend_channel(self, sid: Optional[str],
                              body: Dict[str, Any]) -> Dict[str, Any]:
        """停用渠道。"""
        self._auth(sid, ["admin"])
        ok, msg = self.c.channel.suspend(int(body.get("id", 0)))
        return {"ok": ok, "msg": msg}
    def admin_delete_channel(self, sid: Optional[str],
                             body: Dict[str, Any]) -> Dict[str, Any]:
        """删除渠道。"""
        self._auth(sid, ["admin"])
        ok, msg = self.c.channel.delete(int(body.get("id", 0)))
        return {"ok": ok, "msg": msg}
    def admin_create_staff(self, sid: Optional[str],
                           body: Dict[str, Any]) -> Dict[str, Any]:
        """创建签到工作人员。"""
        self._auth(sid, ["admin"])
        name = (body.get("name") or "").strip()
        account = (body.get("account") or "").strip()
        password = body.get("password") or ""
        ok, msg = validators.validate_name(name)
        if not ok:
            return {"ok": False, "msg": msg}
        if not account or len(account) < 3:
            return {"ok": False, "msg": "账号至少 3 字符"}
        if not password or len(password) < 6:
            return {"ok": False, "msg": "密码至少 6 字符"}
        if self.c.repos.staff.find_by_account(account):
            return {"ok": False, "msg": "账号已存在"}
        import hashlib
        pwd_hash = hashlib.sha256(password.encode()).hexdigest()
        self.c.repos.staff.insert(name, account, pwd_hash, None,
                                  self.c.clock.now_str())
        return {"ok": True, "msg": f"签到账号「{account}」创建成功"}
    def admin_replace_approver(self, sid: Optional[str],
                               body: Dict[str, Any]) -> Dict[str, Any]:
        """更换渠道审批员（管理员操作）。"""
        self._auth(sid, ["admin"])
        cid = int(body.get("channel_id", 0))
        ok, msg = self.c.channel.replace_approver(
            cid, body.get("approver_name", ""), body.get("approver_email", ""))
        return {"ok": ok, "msg": msg}
    # ==================== 管理员账户管理（4） ====================
    def admin_list_admins(self, sid: Optional[str]) -> Dict[str, Any]:
        """管理员列表（不含 MD5）。"""
        self._auth(sid, ["admin"])
        rows = self.c.repos.admin.list_all()
        return {"ok": True, "data": [{
            "id": r["id"], "email": r.get("email") or "", "name": r["name"],
            "status": r["status"], "created_at": r["created_at"],
        } for r in rows]}
    def admin_create_admin(self, sid: Optional[str],
                           body: Dict[str, Any]) -> Dict[str, Any]:
        """新增管理员（启用中最多 3 个）。"""
        self._auth(sid, ["admin"])
        from ..config import md5_email
        email = (body.get("email") or "").strip().lower()
        name = (body.get("name") or "").strip() or "管理员"
        ok, msg = validators.validate_email(email)
        if not ok:
            return {"ok": False, "msg": msg}
        if self.c.repos.admin.email_exists(email):
            return {"ok": False, "msg": "该邮箱已是管理员"}
        if self.c.repos.admin.md5_exists(md5_email(email)):
            return {"ok": False, "msg": "该邮箱已占用管理员账号"}
        if self.c.repos.admin.count_active() >= 3:
            return {"ok": False, "msg": "启用中的管理员最多 3 个"}
        self.c.repos.admin.insert(md5_email(email), name,
                                  self.c.clock.now_str(), email=email)
        self.c.repos.audit.log("admin", None, "admin.create", "admin", None,
                               f"新增管理员 {email}", "",
                               self.c.clock.now_str())
        return {"ok": True, "msg": f"管理员「{email}」创建成功"}
    def _admin_guard(self, sid: Optional[str], body: Dict[str, Any]):
        """管理员停用/删除公共校验。返回 (错误响应, 当前用户, 目标行)。"""
        self._auth(sid, ["admin"])
        target = self.c.repos.admin.find_by_id(int(body.get("id", 0)))
        if not target:
            return {"ok": False, "msg": "管理员不存在"}, None, None
        current = self.sessions.get(sid)["user"]
        if (target.get("email") or "").lower() == (current.get("email") or "").lower():
            return {"ok": False, "msg": "不能对自己执行该操作"}, current, target
        if target["status"] == 1 and self.c.repos.admin.count_active() <= 1:
            return {"ok": False, "msg": "不能停用/删除最后一个启用管理员"}, current, target
        return None, current, target
    def admin_disable_admin(self, sid: Optional[str],
                            body: Dict[str, Any]) -> Dict[str, Any]:
        """停用管理员。"""
        err, _, target = self._admin_guard(sid, body)
        if err:
            return err
        self.c.repos.admin.set_status(target["id"], 0)
        self.c.repos.audit.log("admin", target["id"], "admin.disable", "admin",
                               target["id"], f"停用管理员 {target.get('email') or target['email_md5']}",
                               "", self.c.clock.now_str())
        return {"ok": True, "msg": "已停用"}
    def admin_delete_admin(self, sid: Optional[str],
                           body: Dict[str, Any]) -> Dict[str, Any]:
        """删除管理员（仅允许删除已停用的账号）。"""
        err, _, target = self._admin_guard(sid, body)
        if err:
            return err
        if target["status"] == 1:
            return {"ok": False, "msg": "仅允许删除已停用的管理员"}
        self.c.repos.admin.delete(target["id"])
        self.c.repos.audit.log("admin", target["id"], "admin.delete", "admin",
                               target["id"], f"删除管理员 {target.get('email') or target['email_md5']}",
                               "", self.c.clock.now_str())
        return {"ok": True, "msg": "已删除"}
    # ==================== 邮件出箱（2） ====================
    def mailbox_list(self, sid: Optional[str]) -> Dict[str, Any]:
        """出箱列表（不返回 html 正文）。

        管理员看全量；审批员只看发给自己邮箱的邮件，
        避免审批员通过出箱看到其他渠道的审批链接与接收人注册码。
        """
        ctx = self._auth(sid, ["admin", "approver"])
        rows = self.c.repos.email.list_outbox(200)
        if ctx.is_approver:
            own = (ctx.user.get("email") or "").lower()
            rows = [r for r in rows if (r["to_email"] or "").lower() == own]
        return {"ok": True, "data": [{
            "id": r["id"], "template": r["template"], "to_email": r["to_email"],
            "subject": r["subject"], "created_at": r["created_at"],
        } for r in rows]}
    # ==================== 审批员（4） ====================
    def approver_overview(self, sid: Optional[str]) -> Dict[str, Any]:
        """工作台概览。"""
        ctx = self._auth(sid, ["approver"])
        cid = ctx.channel_id()
        channel = self.c.channel.get(cid)
        stats, _ = self.c.stats.channel_stats(cid)
        return {
            "ok": True, "channel": channel, "stats": stats.__dict__,
            "register_url": codes.build_channel_register_url(
                self.c.config.site_url, channel["code"]),
            "approver": ctx.user,
        }
    def approver_referrers(self, sid: Optional[str]) -> Dict[str, Any]:
        """本渠道推荐人。"""
        ctx = self._auth(sid, ["approver"])
        return {"ok": True, "data": self.c.referrer.list_by_channel(ctx.channel_id())}
    def approver_recipients(self, sid: Optional[str]) -> Dict[str, Any]:
        """本渠道接收人。"""
        ctx = self._auth(sid, ["approver"])
        return {"ok": True, "data": self.c.ticket.list(channel_id=ctx.channel_id())}
    def approver_approve(self, sid: Optional[str],
                         body: Dict[str, Any]) -> Dict[str, Any]:
        """审批推荐人（系统内操作，校验归属）。"""
        ctx = self._auth(sid, ["approver"])
        ok, msg = self.c.referrer.approve_by_id(
            int(body.get("id", 0)), body.get("action", "approve"),
            ctx.channel_id())
        return {"ok": ok, "msg": msg}
    # ==================== 公开注册（2） ====================
    def referrer_register(self, body: Dict[str, Any], ip: str = "") -> Dict[str, Any]:
        """推荐人注册（限流：每 IP 每小时 200 次，覆盖压测场景）。"""
        self._limit(f"ref_reg:{ip}", 200, 3600)
        ok, msg = self.c.referrer.register(
            body.get("channel_code", ""), body.get("name", ""),
            body.get("company", ""), body.get("department", ""),
            body.get("mobile", ""), body.get("email", ""))
        return {"ok": ok, "msg": msg}
    def ticket_register(self, body: Dict[str, Any], ip: str = "") -> Dict[str, Any]:
        """接收人注册（限流：每 IP 每小时 1000 次，覆盖压测场景）。"""
        self._limit(f"tk_reg:{ip}", 1000, 3600)
        ok, msg = self.c.ticket.register(
            body.get("referrer_code", ""), body.get("name", ""),
            body.get("company", ""), body.get("mobile", ""),
            body.get("email", ""))
        return {"ok": ok, "msg": msg}
    # ==================== 一次性审批（3） ====================
    def approve_info(self, body: Dict[str, Any]) -> Dict[str, Any]:
        """查询审批对象。"""
        token = body.get("token", "")
        record = self.c.repos.token.find_by_token(token)
        if not record:
            return {"ok": False, "msg": "链接无效"}
        ok, msg = rules.check_token_usable(record["used_at"],
                                           record["expires_at"],
                                           self.c.clock.now())
        if not ok:
            return {"ok": False, "msg": msg}
        if record["type"] == TokenType.REFERRER_APPROVE:
            ref = self.c.referrer.get(record["target_id"])
            if not ref:
                return {"ok": False, "msg": "推荐人不存在"}
            ch = self.c.channel.get(ref["channel_id"])
            return {"ok": True, "type": "referrer", "data": {
                "name": ref["name"], "company": ref["company"],
                "department": ref["department"], "mobile": ref["mobile"],
                "email": ref["email"], "channel": ch["name"] if ch else "",
            }}
        rc = self.c.repos.recipient.find_by_id(record["target_id"])
        if not rc:
            return {"ok": False, "msg": "申请不存在"}
        ref = self.c.referrer.get(rc["referrer_id"])
        return {"ok": True, "type": "ticket", "data": {
            "name": rc["name"], "company": rc["company"],
            "mobile": rc["mobile"], "email": rc["email"],
            "referrer": ref["name"] if ref else "",
        }}
    def approve_do(self, body: Dict[str, Any]) -> Dict[str, Any]:
        """执行审批。"""
        token = body.get("token", "")
        action = body.get("action", "approve")
        record = self.c.repos.token.find_by_token(token)
        if not record:
            return {"ok": False, "msg": "链接无效"}
        if record["type"] == TokenType.REFERRER_APPROVE:
            ok, msg, _ = self.c.referrer.approve(token, action)
        else:
            ok, msg, _ = self.c.ticket.approve(token, action)
        return {"ok": ok, "msg": msg}
    def approve_resend(self, body: Dict[str, Any]) -> Dict[str, Any]:
        """重新签发审批令牌。"""
        token = body.get("token", "")
        record = self.c.repos.token.find_by_token(token)
        if not record:
            return {"ok": False, "msg": "链接无效"}
        new_token = self.c.random.token()
        self.c.repos.token.insert(
            new_token, record["type"], record["target_id"], record["owner_id"],
            rules.calc_token_expires(self.c.clock.now()),
            self.c.clock.now_str())
        self.c.repos.token.mark_used(token, self.c.clock.now_str())
        return {"ok": True, "msg": "已重新签发审批链接", "token": new_token}
    # ==================== 签到（4） ====================
    def checkin_lookup(self, sid: Optional[str],
                       body: Dict[str, Any], ip: str = "") -> Dict[str, Any]:
        """查询注册码。"""
        self._auth(sid, ["staff", "admin"])
        self._limit(f"lookup:{ip}", 20, 1)
        ok, msg, result = self.c.checkin.lookup(body.get("code", ""))
        return {"ok": ok, "msg": msg, "data": result.__dict__ if result else None}
    def checkin_confirm(self, sid: Optional[str],
                        body: Dict[str, Any], ip: str = "") -> Dict[str, Any]:
        """确认签到。"""
        ctx = self._auth(sid, ["staff", "admin"])
        staff_id = ctx.user.get("id") if ctx.is_staff else None
        ok, msg, result = self.c.checkin.confirm(body.get("code", ""),
                                                 staff_id, ip)
        return {"ok": ok, "msg": msg, "data": result.__dict__ if result else None}
    def checkin_staff_login(self, sid: Optional[str],
                            body: Dict[str, Any]) -> Dict[str, Any]:
        """签到员登录（sid 参数占位，保持签名统一）。"""
        import hashlib
        account = (body.get("account") or "").strip()
        password = body.get("password") or ""
        pwd_hash = hashlib.sha256(password.encode()).hexdigest()
        ok, msg, staff = self.c.auth.staff_login(account, pwd_hash)
        if not ok:
            return {"ok": False, "msg": msg}
        sid = self.c.random.token()
        self.sessions.create(sid, "staff", staff)
        return {"ok": True, "msg": msg, "role": "staff", "user": staff, "sid": sid}
    def checkin_retry_sync(self, sid: Optional[str],
                           body: Dict[str, Any]) -> Dict[str, Any]:
        """外部同步补推。"""
        self._auth(sid, ["admin"])
        code = body.get("code", "")
        ok, msg, result = self.c.checkin.lookup(code)
        if not ok:
            return {"ok": False, "msg": msg}
        self.c.checkin._sync_external(result.recipient_id,
                                      self.c.clock.now_str())
        return {"ok": True, "msg": "已触发外部同步"}
    # ==================== 扫码落地页（1） ====================
    def code_info(self, sid: Optional[str],
                  body: Dict[str, Any]) -> Dict[str, Any]:
        """扫码落地页信息（分级）。"""
        authenticated = False
        if sid:
            s = self.sessions.get(sid)
            authenticated = s is not None and s["role"] in ("staff", "admin")
        ok, msg, data = self.c.checkin.code_info(body.get("code", ""),
                                                 authenticated)
        return {"ok": ok, "msg": msg, "data": data}