"""应用装配：依赖注入容器。"""
from typing import Optional
from .config import AppConfig, load_config, md5_email
from .infra.clock import Clock, SystemClock
from .infra.database import Database
from .infra.external_checkin import ExternalCheckinAdapter, NullExternalCheckin
from .infra.mailer import AsyncMailer, Mailer, OutboxMailer, SmtpMailer
from .infra.qrcode_gen import PillowQrCodeGenerator, QrCodeGenerator
from .infra.random_source import RandomSource, SecureRandom
from .infra.repositories import Repositories
from .service.auth_service import AuthService
from .service.channel_service import ChannelService
from .service.checkin_service import CheckinService
from .service.referrer_service import ReferrerService
from .service.stats_service import StatsService
from .service.ticket_service import TicketService
def _seed_admins(repos: Repositories, config: AppConfig) -> None:
    """admin 表为空时，从部署配置（config.admin_emails 明文映射）播种初始管理员。幂等。"""
    if repos.admin.count_all() > 0:
        return
    now = repos.db.scalar("SELECT datetime('now','localtime')") or ""
    for email, name in config.admin_emails.items():
        repos.admin.insert(md5_email(email), name, now, email=email.lower())
class Container:
    """依赖注入容器：装配全部服务。"""
    def __init__(self, config: Optional[AppConfig] = None,
                 clock: Optional[Clock] = None,
                 random_source: Optional[RandomSource] = None,
                 mailer: Optional[Mailer] = None,
                 qr_generator: Optional[QrCodeGenerator] = None,
                 external_adapter: Optional[ExternalCheckinAdapter] = None,
                 db_path: Optional[str] = None,
                 init_schema: bool = True):
        self.config = config or load_config()
        if db_path:
            self.config.db_path = db_path
        self.clock = clock or SystemClock()
        self.random = random_source or SecureRandom()
        self.db = Database(self.config.db_path)
        if init_schema:
            self.db.init_schema()
        self.repos = Repositories(self.db)
        if init_schema:
            _seed_admins(self.repos, self.config)
        self.qr = qr_generator or PillowQrCodeGenerator()
        self.external = external_adapter or NullExternalCheckin()
        if mailer is not None:
            self.mailer = mailer
        elif self.config.smtp_host:
            # 生产：SMTP 真发（企业邮箱），包一层异步——请求路径只入队，
            # 后台线程投递 + 失败重试，避免 SMTP 慢阻塞用户请求线程
            self.mailer = AsyncMailer(SmtpMailer(
                self.repos.email, self.config.site_name,
                self.config.site_theme, self.config.system_email, self.clock,
                host=self.config.smtp_host, port=self.config.smtp_port,
                user=self.config.smtp_user, password=self.config.smtp_password))
        else:
            # 默认：出箱模式（邮件落库，可在 /mailbox 页面查看）
            self.mailer = OutboxMailer(
                self.repos.email, self.config.site_name,
                self.config.site_theme, self.config.system_email, self.clock)
        # 服务层
        self.auth = AuthService(self.repos, self.clock, self.random, self.config)
        self.channel = ChannelService(self.repos, self.clock, self.random,
                                      self.mailer, self.config)
        self.referrer = ReferrerService(self.repos, self.clock, self.random,
                                        self.mailer, self.qr, self.config)
        self.ticket = TicketService(self.repos, self.clock, self.random,
                                    self.mailer, self.qr, self.config)
        self.checkin = CheckinService(self.repos, self.clock, self.mailer,
                                      self.external, self.config)
        self.stats = StatsService(self.repos)
