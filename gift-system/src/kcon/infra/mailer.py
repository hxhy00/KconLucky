"""邮件适配器：outbox 模式（测试/Demo）+ SMTP 模式（生产）。"""
import smtplib
import sys
from abc import ABC, abstractmethod
from email.header import Header
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.utils import formataddr, formatdate, make_msgid
from typing import Any, Dict, Optional, Tuple
from . import email_templates
from .repositories import EmailRepo
class Mailer(ABC):
    """邮件发送接口。"""
    @abstractmethod
    def send(self, template: str, to_email: str, subject: str,
             ctx: Dict[str, Any]) -> Tuple[bool, Optional[str]]:
        """发送邮件，返回 (是否成功, 错误消息)。"""
class BaseMailer(Mailer):
    """邮件发送基类：统一渲染与日志。"""
    def __init__(self, email_repo: EmailRepo, site_name: str,
                 theme: str, system_email: str, clock):
        self.email_repo = email_repo
        self.site_name = site_name
        self.theme = theme
        self.system_email = system_email
        self.clock = clock
    def render(self, template: str, ctx: Dict[str, Any]) -> str:
        """渲染邮件 HTML。"""
        return email_templates.render(template, ctx, self.site_name,
                                      self.theme, self.system_email)
    def send(self, template: str, to_email: str, subject: str,
             ctx: Dict[str, Any]) -> Tuple[bool, Optional[str]]:
        """发送邮件（模板渲染 + 投递 + 日志）。

        日志写库失败必须隔离：此时业务主流程往往已提交，若日志异常冒泡
        会把已成功的注册/审批变成 500，且用户重试只会得到「已存在」。
        """
        try:
            html = self.render(template, ctx)
        except Exception as exc:  # 模板缺键 KeyError、类型错误 TypeError 等一并兜住
            self._safe_log(template, to_email, subject, 0,
                           f"渲染失败: {exc}")
            return False, f"渲染失败: {exc}"
        ok, error = self._deliver(to_email, subject, html)
        self._safe_log(template, to_email, subject, 1 if ok else 0, error)
        return ok, error
    def _safe_log(self, template: str, to_email: str, subject: str,
                  status: int, error: Optional[str]) -> None:
        """写发信日志，失败降级为 stderr，绝不向上抛。"""
        try:
            self.email_repo.log(template, to_email, subject, status,
                                error, self.clock.now_str())
        except Exception as exc:  # noqa: BLE001
            print(f"[kcon] email log write failed: tpl={template} to={to_email} "
                  f"status={status}: {exc!r}", file=sys.stderr)
    @abstractmethod
    def _deliver(self, to_email: str, subject: str,
                 html: str) -> Tuple[bool, Optional[str]]:
        """实际投递。"""
class OutboxMailer(BaseMailer):
    """出箱邮件（测试与 Demo 用，不真发信）。"""
    def _deliver(self, to_email: str, subject: str,
                 html: str) -> Tuple[bool, Optional[str]]:
        self.email_repo.save_outbox("", to_email, subject, html, self.clock.now_str())
        return True, None
    def send(self, template: str, to_email: str, subject: str,
             ctx: Dict[str, Any]) -> Tuple[bool, Optional[str]]:
        """发送并保存到出箱（带模板编号）。"""
        try:
            html = self.render(template, ctx)
        except ValueError as exc:
            self.email_repo.log(template, to_email, subject, 0,
                                f"渲染失败: {exc}", self.clock.now_str())
            return False, f"渲染失败: {exc}"
        self.email_repo.save_outbox(template, to_email, subject, html,
                                    self.clock.now_str())
        self.email_repo.log(template, to_email, subject, 1, None,
                            self.clock.now_str())
        return True, None
class SmtpMailer(BaseMailer):
    """SMTP 邮件（生产用）。"""
    def __init__(self, email_repo, site_name, theme, system_email, clock,
                 host: str, port: int, user: str, password: str):
        super().__init__(email_repo, site_name, theme, system_email, clock)
        self.host = host
        self.port = port
        self.user = user
        self.password = password
    def _deliver(self, to_email: str, subject: str,
                 html: str) -> Tuple[bool, Optional[str]]:
        try:
            msg = MIMEMultipart("alternative")
            msg["From"] = formataddr(
                (str(Header(f"{self.site_name}组委会", "utf-8")), self.user))
            msg["To"] = to_email
            msg["Subject"] = Header(subject, "utf-8")
            msg["Date"] = formatdate(localtime=True)
            msg["Message-ID"] = make_msgid(domain=self.host)
            msg.attach(MIMEText(html, "html", "utf-8"))
            with smtplib.SMTP_SSL(self.host, self.port, timeout=15) as server:
                server.login(self.user, self.password)
                server.sendmail(self.user, [to_email], msg.as_string())
            return True, None
        except (smtplib.SMTPException, OSError) as exc:
            return False, str(exc)[:500]
class FakeMailer(Mailer):
    """测试用假邮件器：只记录调用，不落库。"""
    def __init__(self, fail_templates: Optional[set] = None):
        self.sent = []
        self.fail_templates = fail_templates or set()
    def send(self, template: str, to_email: str, subject: str,
             ctx: Dict[str, Any]) -> Tuple[bool, Optional[str]]:
        self.sent.append({"template": template, "to": to_email,
                          "subject": subject, "ctx": ctx})
        if template in self.fail_templates:
            return False, "模拟发送失败"
        return True, None
    def count(self, template: str) -> int:
        """统计某模板发送次数。"""
        return sum(1 for m in self.sent if m["template"] == template)
class AsyncMailer(Mailer):
    """异步邮件包装器：请求路径只入队，单后台线程投递。

    背景：SMTP_SSL 每封最长 15s，同步发送会把耗时叠加到用户请求上，
    高峰期占满 ThreadingHTTPServer 线程。改为入队即返回，worker 消费；
    失败按指数退避重试（retry_limit 次），重试耗尽后写日志标记失败。

    注意：send() 返回值不再是发送结果（恒为 True）；调用方不应依赖它
    判断「是否已投递」。测试可用 inner 拿同步语义（FakeMailer 本就瞬时）。
    """
    def __init__(self, inner: Mailer, retry_limit: int = 3,
                 retry_delay: float = 5.0):
        import atexit
        import queue
        import threading
        self.inner = inner
        self.retry_limit = retry_limit
        self.retry_delay = retry_delay
        self._queue: "queue.Queue" = queue.Queue()
        self._worker = threading.Thread(
            target=self._run, name="kcon-mail-worker", daemon=True)
        self._worker.start()
        atexit.register(self.drain)
    def send(self, template: str, to_email: str, subject: str,
             ctx: Dict[str, Any]) -> Tuple[bool, Optional[str]]:
        self._queue.put((template, to_email, subject, ctx))
        return True, None
    def _run(self) -> None:
        import time
        while True:
            item = self._queue.get()
            if item is None:  # shutdown 哨兵
                break
            template, to_email, subject, ctx = item
            for attempt in range(self.retry_limit + 1):
                ok, error = self.inner.send(template, to_email, subject, ctx)
                if ok:
                    break
                if attempt < self.retry_limit:
                    time.sleep(self.retry_delay * (attempt + 1))
            self._queue.task_done()
    def drain(self, timeout: float = 10.0) -> None:
        """进程退出前把队列里的剩余邮件发完（尽力而为）。"""
        import time
        deadline = time.monotonic() + timeout
        while not self._queue.empty() and time.monotonic() < deadline:
            time.sleep(0.1)