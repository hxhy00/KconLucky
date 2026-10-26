"""集成测试：邮件出箱 API + 导出脚本。"""
import csv
import importlib.util
import sqlite3
import sys
from pathlib import Path
import pytest
from kcon.api.handlers import ApiError, Handlers
from kcon.api.middleware import RateLimiter, SessionStore
pytestmark = pytest.mark.integration
SCRIPT_PATH = Path(__file__).resolve().parent.parent.parent / "scripts" / "export_recipients.py"
def _load_export_module():
    spec = importlib.util.spec_from_file_location("export_recipients", SCRIPT_PATH)
    mod = importlib.util.module_from_spec(spec)
    sys.modules["export_recipients"] = mod
    spec.loader.exec_module(mod)
    return mod
class TestMailboxApi:
    def _handlers(self, container):
        return Handlers(container, SessionStore(container.clock, 7200),
                        RateLimiter(container.clock))
    def _sid(self, container, email="admin@kcon.com"):
        _, _, code = container.auth.send_verify_code(email)
        ok, msg, role, user = container.auth.login(email, code)
        assert ok, msg
        sid = f"sid-{email}"
        container._test_sessions = (role, user)
        return role, user, sid
    def test_mailbox_list_admin(self, container):
        h = self._handlers(container)
        role, user, sid = self._sid(container)
        h.sessions.create(sid, role, user)
        # 造一封出箱邮件
        container.repos.email.save_outbox("M-01", "a@b.com", "开通通知",
                                          "<html>ok</html>", "2026-10-25 10:00:00")
        r = h.mailbox_list(sid)
        assert r["ok"] is True
        assert len(r["data"]) == 1
        keys = set(r["data"][0].keys())
        assert keys == {"id", "template", "to_email", "subject", "created_at"}
        assert "html" not in keys
    def test_mailbox_list_requires_auth(self, container):
        h = self._handlers(container)
        with pytest.raises(ApiError) as exc:
            h.mailbox_list(None)
        assert exc.value.status == 401
    def test_mailbox_list_forbidden_role(self, container):
        h = self._handlers(container)
        h.sessions.create("sid-staff", "staff", {"id": 1})
        with pytest.raises(ApiError) as exc:
            h.mailbox_list("sid-staff")
        assert exc.value.status == 403
    def test_mailbox_list_approver(self, container, channel_factory):
        h = self._handlers(container)
        ch = channel_factory(approver_email="ap@test.com")
        role, user, sid = self._sid(container, "ap@test.com")
        h.sessions.create(sid, role, user)
        r = h.mailbox_list(sid)
        assert r["ok"] is True

    def test_mailbox_list_approver_only_sees_own_mail(self, container, channel_factory):
        """审批员不得通过出箱看到其他渠道的审批链接与注册码。"""
        h = self._handlers(container)
        channel_factory(approver_email="ap@test.com")
        role, user, sid = self._sid(container, "ap@test.com")
        h.sessions.create(sid, role, user)
        container.repos.email.save_outbox("M-01", "ap@test.com", "自己的",
                                          "<html>mine</html>", "2026-10-25 10:00:00")
        container.repos.email.save_outbox("M-05", "other@test.com", "别人的",
                                          "<html>other</html>", "2026-10-25 10:00:00")
        r = h.mailbox_list(sid)
        assert [x["to_email"] for x in r["data"]] == ["ap@test.com"]
class TestMailboxViewRoute:
    """通过真实 HTTP 服务覆盖 /api/mailbox/{id}/view 特判路由。"""
    def test_view_via_http(self, live_server, admin_client):
        c = live_server["container"]
        c.repos.email.save_outbox("M-01", "a@b.com", "开通通知",
                                  "<html><body>hello-mail</body></html>",
                                  "2026-10-25 10:00:00")
        mid = c.repos.email.list_outbox()[0]["id"]
        raw, status = admin_client.request("GET", f"/api/mailbox/{mid}/view")
        assert status == 200
        assert b"hello-mail" in raw["_raw"]
    def test_view_without_session_401(self, live_server, client):
        c = live_server["container"]
        c.repos.email.save_outbox("M-01", "a@b.com", "s", "<html>x</html>",
                                  "2026-10-25 10:00:00")
        mid = c.repos.email.list_outbox()[0]["id"]
        _, status = client.request("GET", f"/api/mailbox/{mid}/view")
        assert status == 401
    def test_view_not_found_404(self, live_server, admin_client):
        _, status = admin_client.request("GET", "/api/mailbox/999/view")
        assert status == 404

    def test_view_other_channel_mail_403(self, live_server, client):
        """审批员猜到别人的邮件 id 也不能读取正文。"""
        c = live_server["container"]
        ok, msg = c.channel.create("渠道A", "审批员", "ap@test.com", 100, 20)
        assert ok, msg
        c.repos.email.save_outbox("M-05", "other@test.com", "别人的",
                                  "<html>secret-code</html>",
                                  "2026-10-25 10:00:00")
        mid = c.repos.email.list_outbox()[0]["id"]
        _, _, code = c.auth.send_verify_code("ap@test.com")
        ok, _, _, user = c.auth.login("ap@test.com", code)
        assert ok
        sid = "sid-ap-view"
        live_server["server"].sessions.create(sid, "approver", user)
        client.sid = sid
        _, status = client.request("GET", f"/api/mailbox/{mid}/view")
        assert status == 403
class TestExportRecipients:
    def _make_db(self, tmp_path):
        """构造含已审批接收人的临时库（复用 SCHEMA）。"""
        from kcon.infra.database import Database
        db_path = str(tmp_path / "export.db")
        db = Database(db_path)
        db.init_schema()
        conn = sqlite3.connect(db_path)
        conn.execute(
            "INSERT INTO channel(name,code,quota,referrer_cap,remark,status,"
            "created_at,updated_at) VALUES('渠道A','CHA',100,20,'',1,"
            "'2026-10-25 10:00:00','2026-10-25 10:00:00')")
        conn.execute(
            "INSERT INTO referrer(channel_id,name,company,department,mobile,"
            "email,code,status,created_at) VALUES(1,'李推荐','公司','部门',"
            "'13800138000','ref@test.com','RF1',2,'2026-10-25 10:00:00')")
        rows = [
            # (name, mobile, email, approved_at, created_at)
            ("王一", "13900000001", "a1@test.com", "2026-10-25 11:00:00",
             "2026-10-25 10:30:00"),
            ("王一dup", "13900000001", "dup@test.com", "2026-10-25 12:00:00",
             "2026-10-25 10:40:00"),  # 重复手机号，approved_at 更新 → 应保留此条
            ("王二", "", "b@test.com", "2026-10-25 11:00:00",
             "2026-10-25 10:30:00"),  # 缺手机号
            ("王三", "13900000003", "", "2026-10-25 11:00:00",
             "2026-10-25 10:30:00"),  # 空邮箱仍可导出
            ("王四", " 13900000004 ", "d@test.com", None, "2026-10-25 10:50:00"),
            ("王五", "13900000005", "e@test.com", "2026-10-25 11:30:00",
             "2026-10-25 10:30:00"),
        ]
        for i, (name, mobile, email, approved_at, created_at) in enumerate(rows):
            # status=5 的行不应导出（只导 status=2）
            status = 5 if name == "王五" else 2
            conn.execute(
                "INSERT INTO recipient(referrer_id,channel_id,name,company,"
                "mobile,email,status,approved_at,created_at) "
                "VALUES(1,1,?,?,?,?,?,?,?)",
                (name, "单位", mobile.strip(), email, status, approved_at, created_at))
        conn.commit()
        conn.close()
        return db_path
    def test_export_dedupe_and_missing_phone(self, tmp_path):
        mod = _load_export_module()
        db_path = self._make_db(tmp_path)
        out = tmp_path / "out.csv"
        conflicts = tmp_path / "conflicts.csv"
        summary = mod.export(db_path, str(out), str(conflicts))
        # 审批通过总数：6 - 1(status=5) = 5
        assert summary["approved_total"] == 5
        # 导出：5 - 缺手机号 1 - 重复 1 = 3
        assert summary["exported"] == 3
        assert summary["duplicate_conflicts"] == 1
        assert summary["missing_phone"] == 1
        assert summary["unique_phones"] == 3
        with open(out, encoding="utf-8-sig") as f:
            rows = list(csv.reader(f))
        assert rows[0] == ["姓名", "手机号", "渠道来源", "公司", "职位", "邮箱"]
        data = {r[1]: r for r in rows[1:]}
        assert "13900000001" in data
        # 保留 approved_at 最新的那条（王一dup，12:00）
        assert data["13900000001"][0] == "王一dup"
        # 空邮箱导出为空字符串
        assert data["13900000003"][5] == ""
        # 手机号 strip
        assert data["13900000004"][0] == "王四"
        assert data["13900000004"][1] == "13900000004"
        # 职位列恒为空
        for r in rows[1:]:
            assert r[4] == ""
        # 缺手机号的行不导出
        assert "" not in data
        with open(conflicts, encoding="utf-8-sig") as f:
            crows = list(csv.reader(f))
        assert crows[0] == ["手机号", "姓名", "邮箱", "渠道来源", "推荐人", "状态", "原因"]
        reasons = [r[-1] for r in crows[1:]]
        assert reasons.count("重复手机号") == 1
        assert reasons.count("缺少手机号") == 1
        # 重复冲突行是被淘汰的那条（王一）
        dup_row = next(r for r in crows[1:] if r[-1] == "重复手机号")
        assert dup_row[1] == "王一"
    def test_export_stdout(self, tmp_path, capsys):
        mod = _load_export_module()
        db_path = self._make_db(tmp_path)
        mod.export(db_path, str(tmp_path / "out.csv"),
                   str(tmp_path / "conf.csv"), stdout=True)
        captured = capsys.readouterr()
        assert "姓名,手机号,渠道来源" in captured.out
    def test_main_summary(self, tmp_path, capsys):
        mod = _load_export_module()
        db_path = self._make_db(tmp_path)
        sys.argv = ["export_recipients.py", "--db", db_path,
                    "--out", str(tmp_path / "o.csv"),
                    "--conflicts", str(tmp_path / "c.csv")]
        mod.main()
        out_text = capsys.readouterr().out
        assert "导出：3 条" in out_text
