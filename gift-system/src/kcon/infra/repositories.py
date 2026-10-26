"""仓储层：各表的数据访问。"""
from typing import List, Optional
from .database import Database
class AdminRepo:
    """管理员仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def count_active(self) -> int:
        return self.db.scalar("SELECT COUNT(*) FROM admin WHERE status=1") or 0
    def count_all(self) -> int:
        return self.db.scalar("SELECT COUNT(*) FROM admin") or 0
    def find_by_id(self, aid: int) -> Optional[dict]:
        return self.db.query_one("SELECT * FROM admin WHERE id=?", (aid,))
    def find_by_email(self, email: str) -> Optional[dict]:
        return self.db.query_one(
            "SELECT * FROM admin WHERE lower(email)=?", ((email or "").lower(),))
    def find_by_md5(self, email_md5: str) -> Optional[dict]:
        """按 MD5 查启用管理员（登录用）。"""
        return self.db.query_one("SELECT * FROM admin WHERE email_md5=? AND status=1", (email_md5,))
    def email_exists(self, email: str) -> bool:
        return self.db.query_one(
            "SELECT id FROM admin WHERE lower(email)=?", ((email or "").lower(),)) is not None
    def md5_exists(self, email_md5: str) -> bool:
        return self.db.query_one(
            "SELECT id FROM admin WHERE email_md5=?", (email_md5,)) is not None
    def insert(self, email_md5: str, name: str, created_at: str,
               email: str = "") -> int:
        """插入管理员。email 为可选显示字段（旧调用方只传 md5+name 仍兼容）。"""
        return self.db.execute(
            "INSERT INTO admin(email_md5,email,name,status,created_at) VALUES(?,?,?,1,?)",
            (email_md5, email or "", name, created_at))
    def set_status(self, aid: int, status: int) -> int:
        return self.db.execute_rowcount("UPDATE admin SET status=? WHERE id=?", (status, aid))
    def delete(self, aid: int) -> int:
        return self.db.execute_rowcount("DELETE FROM admin WHERE id=?", (aid,))
    def list_all(self) -> List[dict]:
        return self.db.query_all("SELECT * FROM admin ORDER BY id")
class ChannelRepo:
    """渠道仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def count_active(self) -> int:
        return self.db.scalar("SELECT COUNT(*) FROM channel WHERE status<>3") or 0
    def sum_quota(self) -> int:
        return self.db.scalar("SELECT COALESCE(SUM(quota),0) FROM channel WHERE status<>3") or 0
    def find_by_id(self, cid: int) -> Optional[dict]:
        return self.db.query_one("SELECT * FROM channel WHERE id=?", (cid,))
    def find_by_code(self, code: str) -> Optional[dict]:
        return self.db.query_one("SELECT * FROM channel WHERE code=? AND status=1", (code,))
    def find_by_name(self, name: str) -> Optional[dict]:
        return self.db.query_one("SELECT * FROM channel WHERE name=?", (name,))
    def insert(self, name: str, code: str, quota: int, referrer_cap: int,
               remark: str, created_at: str) -> int:
        return self.db.execute(
            "INSERT INTO channel(name,code,quota,referrer_cap,remark,status,created_at,updated_at) "
            "VALUES(?,?,?,?,?,1,?,?)",
            (name, code, quota, referrer_cap, remark, created_at, created_at))
    def update_status(self, cid: int, status: int, updated_at: str) -> int:
        return self.db.execute_rowcount(
            "UPDATE channel SET status=?, updated_at=? WHERE id=?", (status, updated_at, cid))
    def list_all(self) -> List[dict]:
        return self.db.query_all("""
            SELECT c.*, a.name AS approver_name, a.email AS approver_email,
              (SELECT COUNT(*) FROM referrer r WHERE r.channel_id=c.id) AS referrer_count,
              (SELECT COUNT(*) FROM recipient rc WHERE rc.channel_id=c.id) AS recipient_count,
              (SELECT COUNT(*) FROM recipient rc WHERE rc.channel_id=c.id AND rc.status=4) AS checked_in_count,
              (SELECT COUNT(*) FROM recipient rc WHERE rc.channel_id=c.id AND rc.status IN (2,4)) AS used_count
            FROM channel c LEFT JOIN approver a ON a.channel_id=c.id
            WHERE c.status<>3 ORDER BY c.id DESC""")
    def count_related(self, cid: int) -> int:
        """统计关联数据量（推荐人 + 接收人）。"""
        n1 = self.db.scalar("SELECT COUNT(*) FROM referrer WHERE channel_id=?", (cid,)) or 0
        n2 = self.db.scalar("SELECT COUNT(*) FROM recipient WHERE channel_id=?", (cid,)) or 0
        return n1 + n2
class ApproverRepo:
    """审批员仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def find_by_email(self, email: str) -> Optional[dict]:
        return self.db.query_one(
            "SELECT * FROM approver WHERE lower(email)=? AND status=1", (email.lower(),))
    def find_by_channel(self, channel_id: int) -> Optional[dict]:
        return self.db.query_one(
            "SELECT * FROM approver WHERE channel_id=? AND status=1", (channel_id,))
    def email_exists(self, email: str) -> bool:
        return self.db.query_one("SELECT id FROM approver WHERE lower(email)=?", (email.lower(),)) is not None
    def insert(self, channel_id: int, name: str, email: str, created_at: str) -> int:
        return self.db.execute(
            "INSERT INTO approver(channel_id,name,email,status,created_at) VALUES(?,?,?,1,?)",
            (channel_id, name, email, created_at))
    def update_last_login(self, aid: int, at: str) -> int:
        return self.db.execute_rowcount("UPDATE approver SET last_login_at=? WHERE id=?", (at, aid))
    def disable_by_channel(self, channel_id: int) -> int:
        return self.db.execute_rowcount("UPDATE approver SET status=0 WHERE channel_id=?", (channel_id,))
class ReferrerRepo:
    """推荐人仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def find_by_id(self, rid: int) -> Optional[dict]:
        return self.db.query_one("SELECT * FROM referrer WHERE id=?", (rid,))
    def find_by_code(self, code: str) -> Optional[dict]:
        return self.db.query_one("SELECT * FROM referrer WHERE code=?", (code,))
    def find_by_email(self, email: str) -> Optional[dict]:
        return self.db.query_one("SELECT * FROM referrer WHERE lower(email)=?", (email.lower(),))
    def insert(self, channel_id: int, name: str, company: str, department: str,
               mobile: str, email: str, code: str, created_at: str) -> int:
        return self.db.execute(
            "INSERT INTO referrer(channel_id,name,company,department,mobile,email,code,"
            "status,issued_count,created_at) VALUES(?,?,?,?,?,?,?,1,0,?)",
            (channel_id, name, company, department, mobile, email, code, created_at))
    def update_status(self, rid: int, status: int, at: str,
                      approved_by: Optional[int] = None) -> int:
        if status == 2:
            return self.db.execute_rowcount(
                "UPDATE referrer SET status=?, approved_at=?, approved_by=? WHERE id=?",
                (status, at, approved_by, rid))
        return self.db.execute_rowcount(
            "UPDATE referrer SET status=?, rejected_at=? WHERE id=?", (status, at, rid))
    def increment_issued(self, rid: int) -> int:
        return self.db.execute_rowcount(
            "UPDATE referrer SET issued_count=issued_count+1 WHERE id=?", (rid,))
    def update_for_resubmit(self, rid: int, name: str, company: str,
                            department: str, mobile: str, code: str) -> int:
        """被拒后重申：复用原记录，重置为待审批状态。"""
        return self.db.execute_rowcount(
            "UPDATE referrer SET name=?, company=?, department=?, mobile=?, "
            "code=?, status=1, rejected_at=NULL, approved_at=NULL, "
            "approved_by=NULL WHERE id=?",
            (name, company, department, mobile, code, rid))
    def list_by_channel(self, channel_id: Optional[int] = None,
                        status: Optional[int] = None) -> List[dict]:
        sql = """
            SELECT r.*, c.name AS channel_name,
              (SELECT COUNT(*) FROM recipient x WHERE x.referrer_id=r.id) AS invited_count,
              (SELECT COUNT(*) FROM recipient x WHERE x.referrer_id=r.id AND x.status IN (2,4)) AS approved_count,
              (SELECT COUNT(*) FROM recipient x WHERE x.referrer_id=r.id AND x.status=4) AS checked_in_count
            FROM referrer r JOIN channel c ON c.id=r.channel_id WHERE 1=1"""
        params: list = []
        if channel_id is not None:
            sql += " AND r.channel_id=?"
            params.append(channel_id)
        if status is not None:
            sql += " AND r.status=?"
            params.append(status)
        sql += " ORDER BY r.id DESC"
        return self.db.query_all(sql, tuple(params))
class RecipientRepo:
    """接收人仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def find_by_id(self, rid: int) -> Optional[dict]:
        return self.db.query_one("SELECT * FROM recipient WHERE id=?", (rid,))
    def find_by_email(self, email: str) -> Optional[dict]:
        return self.db.query_one("SELECT * FROM recipient WHERE lower(email)=?", (email.lower(),))
    def insert(self, referrer_id: int, channel_id: int, name: str, company: str,
               mobile: str, email: str, created_at: str) -> int:
        return self.db.execute(
            "INSERT INTO recipient(referrer_id,channel_id,name,company,mobile,email,status,created_at) "
            "VALUES(?,?,?,?,?,?,1,?)",
            (referrer_id, channel_id, name, company, mobile, email, created_at))
    def update_status(self, rid: int, status: int, at: Optional[str] = None) -> int:
        if status == 2 and at:
            return self.db.execute_rowcount(
                "UPDATE recipient SET status=?, approved_at=? WHERE id=?", (status, at, rid))
        return self.db.execute_rowcount("UPDATE recipient SET status=? WHERE id=?", (status, rid))
    def count_used_by_channel(self, channel_id: int) -> int:
        return self.db.scalar(
            "SELECT COUNT(*) FROM recipient WHERE channel_id=? AND status IN (2,4)",
            (channel_id,)) or 0
    def list(self, channel_id: Optional[int] = None, referrer_id: Optional[int] = None,
             status: Optional[int] = None) -> List[dict]:
        sql = """
            SELECT rc.*, r.name AS referrer_name, r.department AS referrer_department,
                   r.company AS referrer_company, c.name AS channel_name,
                   g.code AS reg_code, k.checked_at
            FROM recipient rc
            JOIN referrer r ON r.id=rc.referrer_id
            JOIN channel c ON c.id=rc.channel_id
            LEFT JOIN registration_code g ON g.recipient_id=rc.id
            LEFT JOIN checkin k ON k.recipient_id=rc.id
            WHERE 1=1"""
        params: list = []
        if channel_id is not None:
            sql += " AND rc.channel_id=?"
            params.append(channel_id)
        if referrer_id is not None:
            sql += " AND rc.referrer_id=?"
            params.append(referrer_id)
        if status is not None:
            sql += " AND rc.status=?"
            params.append(status)
        sql += " ORDER BY rc.id DESC"
        return self.db.query_all(sql, tuple(params))
class CodeRepo:
    """注册码仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def find_by_code(self, code: str) -> Optional[dict]:
        return self.db.query_one("SELECT * FROM registration_code WHERE code=?", (code,))
    def find_by_recipient(self, recipient_id: int) -> Optional[dict]:
        return self.db.query_one(
            "SELECT * FROM registration_code WHERE recipient_id=?", (recipient_id,))
    def insert(self, recipient_id: int, code: str, qr_content: str,
               qr_url: str, issued_at: str) -> int:
        return self.db.execute(
            "INSERT INTO registration_code(recipient_id,code,qr_content,qr_url,issued_at) "
            "VALUES(?,?,?,?,?)", (recipient_id, code, qr_content, qr_url, issued_at))
class CheckinRepo:
    """签到仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def find_by_recipient(self, recipient_id: int) -> Optional[dict]:
        return self.db.query_one("SELECT * FROM checkin WHERE recipient_id=?", (recipient_id,))
    def insert(self, recipient_id: int, checked_at: str, staff_id: Optional[int],
               device_ip: str) -> int:
        return self.db.execute(
            "INSERT INTO checkin(recipient_id,checked_at,staff_id,device_ip,"
            "material_issued,external_sync_status) VALUES(?,?,?,?,1,0)",
            (recipient_id, checked_at, staff_id, device_ip))
    def update_external(self, recipient_id: int, status: int, at: str,
                        response: str) -> int:
        return self.db.execute_rowcount(
            "UPDATE checkin SET external_sync_status=?, external_sync_at=?, "
            "external_response=? WHERE recipient_id=?",
            (status, at, response, recipient_id))
class StaffRepo:
    """签到工作人员仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def find_by_account(self, account: str) -> Optional[dict]:
        return self.db.query_one(
            "SELECT * FROM checkin_staff WHERE account=? AND status=1", (account,))
    def insert(self, name: str, account: str, password_hash: str,
               created_by: int, created_at: str) -> int:
        return self.db.execute(
            "INSERT INTO checkin_staff(name,account,password_hash,status,created_by,created_at) "
            "VALUES(?,?,?,1,?,?)", (name, account, password_hash, created_by, created_at))
class TokenRepo:
    """令牌仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def find_by_token(self, token: str) -> Optional[dict]:
        return self.db.query_one("SELECT * FROM token WHERE token=?", (token,))
    def find_active(self, token_type: int, target_id: int) -> Optional[dict]:
        return self.db.query_one(
            "SELECT * FROM token WHERE type=? AND target_id=? AND used_at IS NULL "
            "ORDER BY id DESC LIMIT 1", (token_type, target_id))
    def insert(self, token: str, token_type: int, target_id: int, owner_id: int,
               expires_at: str, created_at: str) -> int:
        return self.db.execute(
            "INSERT INTO token(token,type,target_id,owner_id,expires_at,created_at) "
            "VALUES(?,?,?,?,?,?)",
            (token, token_type, target_id, owner_id, expires_at, created_at))
    def mark_used(self, token: str, used_at: str) -> int:
        """标记已使用，返回影响行数（1 表示成功，0 表示已被使用）。"""
        return self.db.execute_rowcount(
            "UPDATE token SET used_at=? WHERE token=? AND used_at IS NULL", (used_at, token))
    def invalidate_by_target(self, token_type: int, target_id: int,
                             used_at: str) -> int:
        """作废某对象的全部未使用令牌（重申场景）。"""
        return self.db.execute_rowcount(
            "UPDATE token SET used_at=? WHERE type=? AND target_id=? "
            "AND used_at IS NULL", (used_at, token_type, target_id))
    def invalidate_by_owner(self, token_type: int, owner_id: int,
                            used_at: str) -> int:
        """作废某归属者（如渠道）的全部未使用令牌（换审批员场景）。"""
        return self.db.execute_rowcount(
            "UPDATE token SET used_at=? WHERE type=? AND owner_id=? "
            "AND used_at IS NULL", (used_at, token_type, owner_id))
class EmailRepo:
    """邮件日志与出箱仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def log(self, template: str, to_email: str, subject: str, status: int,
            error: Optional[str], sent_at: str) -> int:
        return self.db.execute(
            "INSERT INTO email_log(template,to_email,subject,status,error,sent_at) "
            "VALUES(?,?,?,?,?,?)", (template, to_email, subject, status, error, sent_at))
    def save_outbox(self, template: str, to_email: str, subject: str,
                    html: str, created_at: str) -> int:
        return self.db.execute(
            "INSERT INTO outbox(template,to_email,subject,html,created_at) VALUES(?,?,?,?,?)",
            (template, to_email, subject, html, created_at))
    def list_outbox(self, limit: int = 100) -> List[dict]:
        return self.db.query_all("SELECT * FROM outbox ORDER BY id DESC LIMIT ?", (limit,))
    def get_outbox(self, mail_id: int) -> Optional[dict]:
        return self.db.query_one("SELECT * FROM outbox WHERE id=?", (mail_id,))
    def list_logs(self, limit: int = 100) -> List[dict]:
        return self.db.query_all("SELECT * FROM email_log ORDER BY id DESC LIMIT ?", (limit,))
class AuditRepo:
    """审计日志仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def log(self, actor_type: str, actor_id: Optional[int], action: str,
            target_type: Optional[str], target_id: Optional[int],
            detail: str, ip: str, created_at: str) -> int:
        return self.db.execute(
            "INSERT INTO audit_log(actor_type,actor_id,action,target_type,target_id,"
            "detail,ip,created_at) VALUES(?,?,?,?,?,?,?,?)",
            (actor_type, actor_id, action, target_type, target_id, detail, ip, created_at))
    def list_all(self, limit: int = 100) -> List[dict]:
        return self.db.query_all("SELECT * FROM audit_log ORDER BY id DESC LIMIT ?", (limit,))
class VerifyCodeRepo:
    """验证码仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def latest(self, email: str) -> Optional[dict]:
        return self.db.query_one(
            "SELECT * FROM verify_code WHERE email=? ORDER BY id DESC LIMIT 1", (email,))
    def latest_unused(self, email: str) -> Optional[dict]:
        return self.db.query_one(
            "SELECT * FROM verify_code WHERE email=? AND used=0 ORDER BY id DESC LIMIT 1",
            (email,))
    def count_today(self, email: str, day_prefix: str) -> int:
        return self.db.scalar(
            "SELECT COUNT(*) FROM verify_code WHERE email=? AND created_at LIKE ?",
            (email, day_prefix + "%")) or 0
    def insert(self, email: str, code: str, scene: str, expires_at: str,
               created_at: str) -> int:
        return self.db.execute(
            "INSERT INTO verify_code(email,code,scene,expires_at,used,fail_count,created_at) "
            "VALUES(?,?,?,?,0,0,?)", (email, code, scene, expires_at, created_at))
    def mark_used(self, vid: int) -> int:
        return self.db.execute_rowcount("UPDATE verify_code SET used=1 WHERE id=?", (vid,))
    def increment_fail(self, vid: int) -> int:
        return self.db.execute_rowcount(
            "UPDATE verify_code SET fail_count=fail_count+1 WHERE id=?", (vid,))
class M08Repo:
    """M-08 汇总队列仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def enqueue(self, channel_id: int, recipient_id: int, created_at: str) -> int:
        return self.db.execute(
            "INSERT INTO m08_queue(channel_id,recipient_id,created_at,sent) VALUES(?,?,?,0)",
            (channel_id, recipient_id, created_at))
    def pending(self, channel_id: int) -> List[dict]:
        return self.db.query_all(
            "SELECT * FROM m08_queue WHERE channel_id=? AND sent=0 ORDER BY id", (channel_id,))
    def mark_sent(self, ids: List[int]) -> int:
        if not ids:
            return 0
        placeholders = ",".join("?" * len(ids))
        return self.db.execute_rowcount(
            f"UPDATE m08_queue SET sent=1 WHERE id IN ({placeholders})", tuple(ids))
class StatsRepo:
    """统计查询仓储。"""
    def __init__(self, db: Database):
        self.db = db
    def admin_totals(self) -> dict:
        return {
            "channel_count": self.db.scalar("SELECT COUNT(*) FROM channel WHERE status<>3") or 0,
            "referrer_count": self.db.scalar("SELECT COUNT(*) FROM referrer") or 0,
            "invited_count": self.db.scalar("SELECT COUNT(*) FROM recipient") or 0,
            "approved_count": self.db.scalar(
                "SELECT COUNT(*) FROM recipient WHERE status IN (2,4)") or 0,
            "checked_in_count": self.db.scalar(
                "SELECT COUNT(*) FROM recipient WHERE status=4") or 0,
            "quota_total": self.db.scalar(
                "SELECT COALESCE(SUM(quota),0) FROM channel WHERE status<>3") or 0,
        }
class Repositories:
    """仓储集合（依赖注入用）。"""
    def __init__(self, db: Database):
        self.db = db
        self.admin = AdminRepo(db)
        self.channel = ChannelRepo(db)
        self.approver = ApproverRepo(db)
        self.referrer = ReferrerRepo(db)
        self.recipient = RecipientRepo(db)
        self.code = CodeRepo(db)
        self.checkin = CheckinRepo(db)
        self.staff = StaffRepo(db)
        self.token = TokenRepo(db)
        self.email = EmailRepo(db)
        self.audit = AuditRepo(db)
        self.verify_code = VerifyCodeRepo(db)
        self.m08 = M08Repo(db)
        self.stats = StatsRepo(db)