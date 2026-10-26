"""HTTP 服务器：标准库 http.server，零框架依赖。"""
import json
import os
import re
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, Optional
from ..app import Container
from ..config import load_config
from .handlers import ApiError, Handlers
from .middleware import RateLimiter, SessionStore
from . import router
# 项目根目录：src/kcon/api/server.py → 上溯 3 层到项目根
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent.parent
STATIC_DIR = PROJECT_ROOT / "static"
TEMPLATE_DIR = PROJECT_ROOT / "templates"
MIME_TYPES = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".svg": "image/svg+xml",
    ".ico": "image/x-icon",
}
# 邮件出箱查看路由：/api/mailbox/{id}/view
MAILBOX_VIEW_RE = re.compile(r"^/api/mailbox/(\d+)/view$")
class RequestHandler(BaseHTTPRequestHandler):
    """请求处理器。"""
    server_version = "KConProd/1.0"
    # 由 make_server 注入
    handlers: Handlers = None
    # ---------- 响应工具 ----------
    def _send_json(self, obj: Dict[str, Any], status: int = 200) -> None:
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "X-Session,Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
        self.end_headers()
        self.wfile.write(body)
    def _send_html(self, html: str, status: int = 200) -> None:
        body = html.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def _send_file(self, path: Path) -> None:
        if not path.exists() or not path.is_file():
            self._send_json({"ok": False, "msg": "文件不存在"}, 404)
            return
        data = path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type",
                         MIME_TYPES.get(path.suffix.lower(), "application/octet-stream"))
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(data)
    # ---------- 请求工具 ----------
    def _read_body(self) -> Dict[str, Any]:
        length = int(self.headers.get("Content-Length") or 0)
        if not length:
            return {}
        raw = self.rfile.read(length)
        ctype = self.headers.get("Content-Type", "")
        if "application/json" in ctype:
            try:
                return json.loads(raw.decode("utf-8") or "{}")
            except json.JSONDecodeError:
                return {}
        return {k: v[0] for k, v in urllib.parse.parse_qs(raw.decode("utf-8")).items()}
    def _sid(self) -> Optional[str]:
        return self.headers.get("X-Session") or None
    def _client_ip(self) -> str:
        """真实客户端 IP：优先取 Nginx 设置的 X-Real-IP。

        本服务只在内网监听，公网请求必经前置 Nginx；Nginx 会用
        `$remote_addr` 覆盖 X-Real-IP，因此该头可信。直接取 socket
        对端会得到反代容器 IP，导致全站共享一个限流桶。
        """
        real_ip = (self.headers.get("X-Real-IP") or "").strip()
        if real_ip:
            return real_ip
        return self.client_address[0] if self.client_address else ""
    def log_message(self, fmt: str, *args) -> None:
        """静默日志（生产可接入 logger）。"""
    # ---------- 路由 ----------
    def do_OPTIONS(self) -> None:
        self._send_json({"ok": True})
    def do_GET(self) -> None:
        path = urllib.parse.urlparse(self.path).path
        # 静态资源
        if path.startswith("/static/"):
            rel = path[len("/static/"):]
            self._send_file(STATIC_DIR / rel)
            return
        # 健康检查
        if path == "/healthz":
            self._send_json({"ok": True, "status": "healthy"})
            return
        # 邮件出箱查看（在通用路由前特判）
        m = MAILBOX_VIEW_RE.match(path)
        if m:
            self._mailbox_view(int(m.group(1)))
            return
        # API
        route = router.match_route("GET", path)
        if route:
            self._dispatch(route, {})
            return
        # 页面
        page = router.match_page(path)
        if page:
            self._send_file(TEMPLATE_DIR / page)
            return
        self._send_json({"ok": False, "msg": "接口不存在"}, 404)
    def _mailbox_view(self, mail_id: int) -> None:
        """邮件出箱正文查看：需 admin/approver 会话，返回存储的 HTML。"""
        try:
            sid = self._sid()
            if not sid:
                raise ApiError("未登录或会话已过期", 401)
            s = self.handlers.sessions.get(sid)
            if not s:
                raise ApiError("未登录或会话已过期", 401)
            if s["role"] not in ("admin", "approver"):
                raise ApiError("无权限", 403)
            mail = self.handlers.c.repos.email.get_outbox(mail_id)
            if not mail:
                self._send_json({"ok": False, "msg": "邮件不存在"}, 404)
                return
            # 审批员只能看发给自己的邮件，防止通过猜测 id 读取其他渠道链接
            if s["role"] == "approver":
                own = (s["user"].get("email") or "").lower()
                if (mail["to_email"] or "").lower() != own:
                    self._send_json({"ok": False, "msg": "无权限"}, 403)
                    return
            self._send_html(mail["html"])
        except ApiError as exc:
            self._send_json({"ok": False, "msg": exc.message}, exc.status)
    def do_POST(self) -> None:
        path = urllib.parse.urlparse(self.path).path
        route = router.match_route("POST", path)
        if not route:
            self._send_json({"ok": False, "msg": "接口不存在"}, 404)
            return
        body = self._read_body()
        self._dispatch(route, body)
    def _dispatch(self, route, body: Dict[str, Any]) -> None:
        """分发到处理器（按签名智能匹配参数）。"""
        handler_name, need_body, need_ip = route
        method = getattr(self.handlers, handler_name)
        sid = self._sid()
        try:
            result = self._invoke(method, handler_name, sid, body, need_body, need_ip)
            self._send_json(result)
        except ApiError as exc:
            self._send_json({"ok": False, "msg": exc.message}, exc.status)
        except Exception as exc:  # noqa: BLE001
            self._send_json({"ok": False, "msg": f"服务异常：{exc}"}, 500)

    def _invoke(self, method, handler_name: str, sid, body, need_body: bool,
                need_ip: bool):
        """按处理器签名组装参数。

        处理器签名约定：
          - 需要登录的：第一个参数为 sid
          - 公开接口：第一个参数为 body 或 ip
        """
        # 需要登录的处理器（第一个参数名是 sid）
        import inspect
        params = list(inspect.signature(method).parameters.keys())
        args = []
        if params and params[0] == "sid":
            args.append(sid)
            if need_body:
                args.append(body)
            if need_ip:
                args.append(self._client_ip())
        else:
            # 公开接口
            if need_body:
                args.append(body)
            if need_ip:
                args.append(self._client_ip())
        return method(*args)
def _resolve_container(container: Optional[Container]) -> Container:
    """解析容器：传入则复用，否则新建。"""
    if container is not None:
        return container
    return Container(load_config())


def make_server(host: str = "0.0.0.0", port: int = 8848,
                container: Optional[Container] = None) -> ThreadingHTTPServer:
    """创建 HTTP 服务器。"""
    c = _resolve_container(container)
    sessions = SessionStore(c.clock, c.config.session_ttl_seconds)
    limiter = RateLimiter(c.clock)
    handlers = Handlers(c, sessions, limiter)
    RequestHandler.handlers = handlers
    server = ThreadingHTTPServer((host, port), RequestHandler)
    server.container = c
    server.sessions = sessions
    server.limiter = limiter
    return server
def main() -> None:
    """启动服务。"""
    config = load_config()
    port = int(os.getenv("KCON_PORT", "8848"))
    server = make_server(port=port)
    print(f"KCon2026 赠票系统已启动：http://127.0.0.1:{port}")
    print("管理员邮箱由运维通过 KCON_ADMIN_EMAILS 配置（admin 表为空时作为初始种子）")
    print("按 Ctrl+C 停止")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")
        server.shutdown()
if __name__ == "__main__":  # pragma: no cover
    main()