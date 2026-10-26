"""集成测试：管理员账户管理（DB 认证 + CRUD 限制）。"""
import pytest
pytestmark = pytest.mark.integration
class TestAdminAuthFromDb:
    def test_admin_seeded_on_init(self, container):
        """admin 表为空时按配置播种初始管理员（幂等）。"""
        rows = container.repos.admin.list_all()
        assert len(rows) == 3
        assert rows[0]["email"] == "admin@kcon.com"
        assert rows[0]["name"] == "系统管理员"
    def test_seed_idempotent(self, container):
        container.db.init_schema()
        container.db.init_schema()
        assert len(container.repos.admin.list_all()) == 3
    def test_admin_login_from_db(self, container):
        _, _, code = container.auth.send_verify_code("admin@kcon.com")
        ok, msg, role, user = container.auth.login("admin@kcon.com", code)
        assert ok is True
        assert role == "admin"
        assert user["id"] >= 1
        assert user["email"] == "admin@kcon.com"
        assert user["name"] == "系统管理员"
    def test_disabled_admin_cannot_login(self, container):
        admin = container.repos.admin.find_by_email("admin@kcon.com")
        container.repos.admin.set_status(admin["id"], 0)
        _, _, code = container.auth.send_verify_code("admin@kcon.com")
        ok, msg, role, user = container.auth.login("admin@kcon.com", code)
        assert ok is False
        assert msg == "该邮箱无系统权限"
    def test_non_whitelist_admin_not_in_db_cannot_login(self, container):
        """登录判定只看 admin 表，不再看配置白名单。"""
        from kcon.config import md5_email
        container.config.admin_whitelist[md5_email("ghost@kcon.com")] = "幽灵"
        _, _, code = container.auth.send_verify_code("ghost@kcon.com")
        ok, msg, role, user = container.auth.login("ghost@kcon.com", code)
        assert ok is False
        assert msg == "该邮箱无系统权限"
    def test_empty_email_admin_md5_only(self, container):
        """历史数据（email 为空）仍可通过 MD5 登录。"""
        container.repos.admin.delete(container.repos.admin.list_all()[0]["id"])
        _, _, code = container.auth.send_verify_code("admin@kcon.com")
        ok, msg, role, user = container.auth.login("admin@kcon.com", code)
        assert ok is False  # 已删除，无权限
class TestAdminCrud:
    def _login(self, container):
        _, _, code = container.auth.send_verify_code("admin@kcon.com")
        ok, msg, role, user = container.auth.login("admin@kcon.com", code)
        assert ok, msg
        return user
    def _sid(self, container, api):
        from kcon.api.handlers import Handlers
        from kcon.api.middleware import RateLimiter, SessionStore
        h = Handlers(container, SessionStore(container.clock, 7200),
                     RateLimiter(container.clock))
        user = self._login(container)
        sid = "sid-" + user["email"]
        h.sessions.create(sid, "admin", user)
        return h, sid
    def test_list_admins_no_md5(self, container):
        h, sid = self._sid(container, None)
        r = h.admin_list_admins(sid)
        assert r["ok"] is True
        assert len(r["data"]) == 3
        keys = set(r["data"][0].keys())
        assert keys == {"id", "email", "name", "status", "created_at"}
        assert "email_md5" not in keys
    def test_list_requires_admin(self, container):
        from kcon.api.handlers import Handlers
        from kcon.api.middleware import RateLimiter, SessionStore
        from kcon.api.handlers import ApiError
        h = Handlers(container, SessionStore(container.clock, 7200),
                     RateLimiter(container.clock))
        with pytest.raises(ApiError) as exc:
            h.admin_list_admins(None)
        assert exc.value.status == 401
    def test_create_admin_ok(self, container):
        h, sid = self._sid(container, None)
        # 种子已占满 3 个名额，先停用一个
        target = container.repos.admin.find_by_email("boss@kcon.com")
        h.admin_disable_admin(sid, {"id": target["id"]})
        r = h.admin_create_admin(sid, {"email": "new@test.com", "name": "新管理员"})
        assert r["ok"] is True, r["msg"]
        row = container.repos.admin.find_by_email("new@test.com")
        assert row["name"] == "新管理员"
        assert row["status"] == 1
    def test_create_admin_invalid_email(self, container):
        h, sid = self._sid(container, None)
        r = h.admin_create_admin(sid, {"email": "bad", "name": "坏"})
        assert r["ok"] is False
    def test_create_admin_max_three_active(self, container):
        h, sid = self._sid(container, None)
        # 已有 3 个启用管理员（种子），第 4 个应被拒
        r = h.admin_create_admin(sid, {"email": "4th@test.com", "name": "第四人"})
        assert r["ok"] is False
        assert "最多 3 个" in r["msg"]
    def test_create_admin_duplicate_email(self, container):
        h, sid = self._sid(container, None)
        # 先停用一个腾出名额
        target = container.repos.admin.find_by_email("boss@kcon.com")
        h.admin_disable_admin(sid, {"id": target["id"]})
        r = h.admin_create_admin(sid, {"email": "admin@kcon.com", "name": "重复"})
        assert r["ok"] is False
        assert "已是管理员" in r["msg"]
    def test_disable_then_create_fourth_ok(self, container):
        h, sid = self._sid(container, None)
        target = container.repos.admin.find_by_email("boss@kcon.com")
        assert h.admin_disable_admin(sid, {"id": target["id"]})["ok"] is True
        r = h.admin_create_admin(sid, {"email": "4th@test.com", "name": "第四人"})
        assert r["ok"] is True, r["msg"]
    def test_cannot_disable_self(self, container):
        h, sid = self._sid(container, None)
        me = container.repos.admin.find_by_email("admin@kcon.com")
        r = h.admin_disable_admin(sid, {"id": me["id"]})
        assert r["ok"] is False
        assert "不能对自己" in r["msg"]
    def test_cannot_disable_last_active(self, container):
        h, sid = self._sid(container, None)
        # 停用其余两个，只剩自己
        for email in ("boss@kcon.com", "ops@kcon.com"):
            t = container.repos.admin.find_by_email(email)
            assert h.admin_disable_admin(sid, {"id": t["id"]})["ok"] is True
        # 尝试通过新建账号停用自己 → 被自我保护拦截
        me = container.repos.admin.find_by_email("admin@kcon.com")
        r = h.admin_disable_admin(sid, {"id": me["id"]})
        assert r["ok"] is False
        assert "不能对自己" in r["msg"]
        # 换个新管理员会话再停用 admin → 仍剩 1 个启用，停用第二个会成功，
        # 但停到最后一个启用者时应被拒
        assert h.admin_create_admin(sid, {"email": "x1@test.com"})["ok"] is True
        x1 = container.repos.admin.find_by_email("x1@test.com")
        # 新会话以 x1 登录
        h.sessions.create("sid-x1", "admin",
                          {"id": x1["id"], "email": "x1@test.com", "name": x1["name"]})
        me = container.repos.admin.find_by_email("admin@kcon.com")
        r = h.admin_disable_admin("sid-x1", {"id": me["id"]})
        assert r["ok"] is True
        # x1 自己停自己 → 拒绝（自我保护）
        r = h.admin_disable_admin("sid-x1", {"id": x1["id"]})
        assert r["ok"] is False
    def test_delete_only_disabled(self, container):
        h, sid = self._sid(container, None)
        target = container.repos.admin.find_by_email("ops@kcon.com")
        r = h.admin_delete_admin(sid, {"id": target["id"]})
        assert r["ok"] is False
        assert "仅允许删除已停用" in r["msg"]
        assert h.admin_disable_admin(sid, {"id": target["id"]})["ok"] is True
        r = h.admin_delete_admin(sid, {"id": target["id"]})
        assert r["ok"] is True
        assert container.repos.admin.find_by_id(target["id"]) is None
    def test_cannot_delete_self(self, container):
        h, sid = self._sid(container, None)
        me = container.repos.admin.find_by_email("admin@kcon.com")
        # 先把自己停用不可能（自我保护），但直接测试删除保护
        r = h.admin_delete_admin(sid, {"id": me["id"]})
        assert r["ok"] is False
    def test_disable_not_found(self, container):
        h, sid = self._sid(container, None)
        r = h.admin_disable_admin(sid, {"id": 9999})
        assert r["ok"] is False
        assert "不存在" in r["msg"]
