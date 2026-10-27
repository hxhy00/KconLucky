#!/usr/bin/env python3
"""KCon 2026 抽奖系统 · 真实端到端用户旅程测试。

对着一个真正跑起来的后端（本地 jar 或容器）走完整现场链路：
  管理员登录 → 建/改账号（含管理员身份）→ 配渠道 → 建名单 → 观众签到
  → 抽奖台抽奖 → 大屏状态 → 兑奖台核验发奖 → 权限边界 → 数据清场 → 备份导出

设计与使用约定：
  * 只用 Python 标准库，不引第三方依赖；
  * 全程真实 HTTP + 真实验证码流程：验证码开启时从 Redis 读取正确答案（captcha_codes:<uuid>）；
  * 失败即非 0 退出，并打印失败步骤与响应体，便于直接贴进排查记录；
  * 运行结束默认清理本次创建的账号与渠道映射（--keep 可保留现场）。

前置条件：
  1. 后端已启动，且能访问管理端接口；
  2. 数据库已执行 sql/00-ry-base.sql + sql/kcon_menu.sql + sql/kcon_lucky.sql；
  3. 验证码开启时需要能读 Redis（默认 `redis-cli -h 127.0.0.1 -p 6379`，
     也可用 --redis-cmd 'docker exec e2e-redis redis-cli' 指定）。

示例：
  python3 scripts/e2e-journey.py --base-url http://127.0.0.1:18080 \
      --admin-phone 13000000000 --admin-password 'Kcon2026Aa.'
"""

import argparse
import json
import random
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

REDIS_CAPTCHA_PREFIX = "captcha_codes:"


class JourneyFailure(Exception):
    """旅程中断：带步骤名与可读原因"""


class Client:
    def __init__(self, base_url, timeout=20):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.token = None

    def call(self, method, path, body=None, token=None, raw=False):
        """返回响应 JSON（dict）；raw=True 时返回 (HTTP 状态码, 原始字节)"""
        status, payload = self._send(method, path, body, token)
        if raw:
            return status, payload
        text = payload.decode("utf-8", errors="replace")
        try:
            return json.loads(text) if text else {}
        except json.JSONDecodeError as error:
            raise JourneyFailure("%s %s 返回的不是 JSON：%s" % (method, path, text[:300])) from error

    def _send(self, method, path, body=None, token=None):
        url = self.base_url + path
        data = None
        headers = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json;charset=UTF-8"
        auth = token if token is not None else self.token
        if auth:
            headers["Authorization"] = "Bearer " + auth
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as error:
            return error.code, error.read()
        except urllib.error.URLError as error:
            raise JourneyFailure("%s %s 连接失败：%s" % (method, path, error.reason)) from error


class Journey:
    def __init__(self, args):
        self.args = args
        self.client = Client(args.base_url)
        self.step_no = 0
        self.steps = 0
        self.suffix = "%08d" % (int(time.time() * 1000) % 100000000)
        self.password = "E2ePass2026"
        self.created_users = []
        self.created_channels = []
        self.port_no = None
        self.attendee_phone = None

    # ---------- 基础设施 ----------

    def ok(self, response, step, allow=None):
        """断言业务成功（RuoYi 统一用响应体 code 表达业务结果）"""
        code = response.get("code")
        if allow and code in allow:
            return response
        if code != 200:
            raise JourneyFailure("%s 失败：code=%s msg=%s" % (step, code, response.get("msg")))
        return response

    def run_step(self, title, func):
        self.step_no += 1
        print("[%02d] %s ... " % (self.step_no, title), end="", flush=True)
        try:
            func()
        except JourneyFailure:
            print("FAIL")
            raise
        print("PASS")
        self.steps += 1

    # ---------- 各步骤 ----------

    def captcha(self):
        status, raw = self.client.call("GET", "/captchaImage", raw=True)
        if status != 200:
            raise JourneyFailure("验证码接口不可用：HTTP %s" % status)
        payload = json.loads(raw.decode("utf-8", errors="replace"))
        if not payload.get("captchaEnabled"):
            return "", ""
        uuid = payload.get("uuid")
        answer = self.redis_get(REDIS_CAPTCHA_PREFIX + str(uuid))
        if not answer:
            raise JourneyFailure(
                "验证码已开启，但读不到 Redis 中的答案（%s%s）。"
                "请用 --redis-cmd 指定可用的 redis-cli，或在测试库关闭 sys.account.captchaEnabled"
                % (REDIS_CAPTCHA_PREFIX, uuid))
        return answer, uuid

    def redis_get(self, key):
        commands = self.args.redis_cmd
        for command in commands:
            try:
                output = subprocess.run(command.split() + ["GET", key],
                                        capture_output=True, text=True, timeout=10)
                if output.returncode == 0 and output.stdout.strip():
                    return output.stdout.strip()
            except (OSError, subprocess.SubprocessError):
                continue
        return None

    def login(self, phone, password, label):
        code, uuid = self.captcha()
        payload = self.client.call("POST", "/login", {
            "username": phone, "password": password, "code": code, "uuid": uuid
        })
        if payload.get("code") != 200:
            raise JourneyFailure("%s 登录失败：code=%s msg=%s"
                                 % (label, payload.get("code"), payload.get("msg")))
        return payload.get("token")

    def step_login_admin(self):
        self.admin_token = self.login(self.args.admin_phone, self.args.admin_password, "管理员")
        if not self.admin_token:
            raise JourneyFailure("管理员登录未返回 token")
        # 后续管理端请求默认带管理员令牌（个别步骤会显式换成岗位令牌）
        self.client.token = self.admin_token

    def step_admin_baseline(self):
        sessions = self.ok(self.client.call("GET", "/admin/session/list"), "场次列表")
        if len(sessions.get("data") or []) < 1:
            raise JourneyFailure("场次表为空，请先执行 sql/kcon_lucky.sql")
        prizes = self.ok(self.client.call("GET", "/admin/prize/list"), "奖品档位列表")
        if int(prizes.get("total") or 0) < 1:
            raise JourneyFailure("奖品档位为空，无法抽奖")

    def create_account(self, nick_name, role_keys, label):
        phone = "137" + self.suffix[-6:] + "%02d" % (len(self.created_users) + 1)
        payload = self.ok(self.client.call("POST", "/admin/staff", {
            "nickName": nick_name,
            "phonenumber": phone,
            "password": self.password,
            "roleKeys": role_keys,
        }), "创建%s账号" % label)
        listing = self.ok(self.client.call(
            "GET", "/admin/staff/list?phonenumber=" + phone), "查询%s账号" % label)
        rows = listing.get("rows") or []
        if len(rows) != 1:
            raise JourneyFailure("新建%s账号后列表查不到（total=%s）" % (label, listing.get("total")))
        user_id = rows[0]["userId"]
        self.created_users.append(user_id)
        return user_id, phone

    def step_account_crud(self):
        # 管理员身份：建 → 改 → 删，对应「管理员也要作为身份之一遵循 CRUD」
        admin_id, admin_phone = self.create_account("E2E 管理员", ["admin"], "管理员")
        self.ok(self.client.call("PUT", "/admin/staff", {
            "userId": admin_id,
            "nickName": "E2E 管理员（改）",
            "phonenumber": admin_phone,
            "roleKeys": ["admin", "kcon_verify_staff"],
        }), "编辑管理员账号")
        row = (self.ok(self.client.call(
            "GET", "/admin/staff/list?phonenumber=" + admin_phone), "复查管理员账号")
               .get("rows") or [])[0]
        if row["nickName"] != "E2E 管理员（改）":
            raise JourneyFailure("编辑未生效：nickName=%s" % row["nickName"])
        if "admin" not in (row.get("roleKeys") or []):
            raise JourneyFailure("编辑后管理员身份丢失：roleKeys=%s" % row.get("roleKeys"))

        # 三个现场岗位
        self.checkin_id, self.checkin_phone = self.create_account(
            "E2E 签到台", ["kcon_checkin_staff"], "签到台")
        self.draw_id, self.draw_phone = self.create_account(
            "E2E 抽奖台", ["kcon_draw_staff"], "抽奖台")
        self.verify_id, self.verify_phone = self.create_account(
            "E2E 兑奖台", ["kcon_verify_staff"], "兑奖台")

        # 删除刚才的管理员账号，验证 D
        self.ok(self.client.call("DELETE", "/admin/staff/%d" % admin_id), "删除管理员账号")
        self.created_users.remove(admin_id)
        left = self.ok(self.client.call(
            "GET", "/admin/staff/list?phonenumber=" + admin_phone), "确认管理员账号已删除")
        if int(left.get("total") or 0) != 0:
            raise JourneyFailure("删除后仍能查到账号")

    def step_prepare_attendee(self):
        channel = "E2E渠道" + self.suffix
        self.ok(self.client.call("POST", "/admin/channel-mapping", {
            "channelSource": channel,
            "benefitType": "E2E 权益",
            "drawEligible": 1,
            "sort": 99,
        }), "创建渠道映射")
        # 新建接口不返回主键，按渠道名回查一次，供收尾清理
        options = self.ok(self.client.call("GET", "/admin/channel-mapping/optionselect"),
                          "回查渠道映射").get("data") or []
        for item in options:
            if item.get("channelSource") == channel:
                self.created_channels.append(item.get("id"))

        self.attendee_phone = "136" + self.suffix[-6:] + "%02d" % (random.randint(10, 99))
        self.ok(self.client.call("POST", "/admin/attendee", {
            "name": "E2E 观众",
            "phone": self.attendee_phone,
            "channel": channel,
            "drawEligible": 1,
            "company": "KCon",
        }), "新增参会人")

    def step_checkin(self):
        query = self.ok(self.client.call("POST", "/app/checkin/query",
                                         {"keyword": self.attendee_phone}, token=""), "签到查询")
        attendee = (query.get("data") or {}).get("attendee") or {}
        attendee_id = attendee.get("id")
        if not attendee_id:
            raise JourneyFailure("签到查询未返回参会人 ID：%s" % query)
        submit = self.ok(self.client.call("POST", "/app/checkin/submit",
                                          {"attendeeId": attendee_id, "phone": self.attendee_phone},
                                          token=""), "确认签到")
        self.port_no = (submit.get("data") or {}).get("portNo")
        if not self.port_no:
            raise JourneyFailure("签到未分配端口号：%s" % submit)

    def step_draw(self):
        draw_token = self.login(self.draw_phone, self.password, "抽奖台")
        result = self.ok(self.client.call("POST", "/admin/draw/draw",
                                          {"sessionCode": "AM"}, token=draw_token), "抽奖")
        winners = (result.get("data") or {}).get("winners") or []
        if not winners:
            raise JourneyFailure("抽奖未产生中奖记录：%s" % result)
        status, raw = self.client.call("GET", "/app/screen/status", token="", raw=True)
        if status != 200:
            raise JourneyFailure("大屏状态接口异常：HTTP %s" % status)
        screen = json.loads(raw.decode("utf-8", errors="replace"))
        if screen.get("code") != 200:
            raise JourneyFailure("大屏状态接口异常：%s" % screen)
        # 大屏公开数据里不应出现管理员专属统计
        data = screen.get("data") or {}
        for key in ("excludedCount", "checkedInToday", "winnerCount"):
            if key in data:
                raise JourneyFailure("大屏响应泄露了管理端字段：%s" % key)

    def step_verify(self):
        verify_token = self.login(self.verify_phone, self.password, "兑奖台")
        check = self.ok(self.client.call("POST", "/admin/verify",
                                         {"portNo": self.port_no, "phone": self.attendee_phone},
                                         token=verify_token), "兑奖核验")
        if (check.get("data") or {}).get("result") != "VALID":
            raise JourneyFailure("端口号 + 手机号未核验通过：%s" % check)
        self.ok(self.client.call("POST", "/admin/verify/receive",
                                 {"portNo": self.port_no, "phone": self.attendee_phone},
                                 token=verify_token), "确认发放")
        # 重复发放必须被拒（不可撤销的写操作）
        again = self.client.call("POST", "/admin/verify/receive",
                                 {"portNo": self.port_no, "phone": self.attendee_phone},
                                 token=verify_token)
        if again.get("code") == 200:
            raise JourneyFailure("同一端口号被重复发放，状态校验失效")

    def step_permissions(self):
        checkin_token = self.login(self.checkin_phone, self.password, "签到台")
        draw_token = self.login(self.draw_phone, self.password, "抽奖台")

        for name, token, method, path, body in [
            ("签到台不能管理账号", checkin_token, "GET", "/admin/staff/list", None),
            ("签到台不能抽奖", checkin_token, "POST", "/admin/draw/draw", {"sessionCode": "AM"}),
            ("抽奖台不能核验发奖", draw_token, "POST", "/admin/verify/receive",
             {"portNo": 1, "phone": "13800000000"}),
        ]:
            response = self.client.call(method, path, body, token=token)
            if response.get("code") != 403:
                raise JourneyFailure("%s：期望 403，实际 code=%s msg=%s"
                                     % (name, response.get("code"), response.get("msg")))

        anonymous = self.client.call("GET", "/admin/attendee/list", token="")
        if anonymous.get("code") != 401:
            raise JourneyFailure("匿名访问管理端未被拦截：%s" % anonymous)

    def step_cleanup_data(self):
        reset = self.ok(self.client.call("POST", "/admin/data/reset",
                                         {"password": self.args.admin_password}), "数据清场")
        listing = self.ok(self.client.call("GET", "/admin/attendee/list"), "清场后名单")
        if int(listing.get("total") or 0) != 0:
            raise JourneyFailure("清场后名单未清空：total=%s" % listing.get("total"))
        if "清场完成" not in (reset.get("msg") or ""):
            raise JourneyFailure("清场提示异常：%s" % reset.get("msg"))

    def step_backup(self):
        status, payload = self.client.call("POST", "/admin/migrate/export", raw=True)
        # 未安装 mysqldump 的环境会返回业务错误：不视为旅程失败，但明确提示
        if status == 200 and payload.startswith(b"--"):
            print("BACKUP-OK", end=" ")
            return
        text = payload.decode("utf-8", errors="replace")[:200]
        print("[WARN 备份导出未通过：%s] " % text.replace("\n", " "), end="")

    # ---------- 主流程 ----------

    def execute(self):
        steps = [
            ("管理员登录（手机号 + 密码 + 验证码）", self.step_login_admin),
            ("管理员基线数据（场次 / 奖品）", self.step_admin_baseline),
            ("账号 CRUD（管理员 + 三岗位）", self.step_account_crud),
            ("会前准备（渠道映射 + 参会名单）", self.step_prepare_attendee),
            ("观众扫码签到拿端口号", self.step_checkin),
            ("抽奖台抽奖 + 大屏状态", self.step_draw),
            ("兑奖台双因子核验 + 发放", self.step_verify),
            ("权限边界（岗位互斥 / 匿名拦截）", self.step_permissions),
            ("数据清场（二次验密）", self.step_cleanup_data),
            ("全量备份导出", self.step_backup),
        ]
        for title, func in steps:
            self.run_step(title, func)
        print("\n端到端旅程通过：%d/%d 步" % (self.steps, len(steps)))

    def cleanup(self):
        if self.args.keep or not self.created_users:
            return
        try:
            for user_id in list(self.created_users):
                self.client.call("DELETE", "/admin/staff/%d" % user_id)
            for mapping_id in self.created_channels:
                self.client.call("DELETE", "/admin/channel-mapping/%d" % mapping_id)
            print("已清理本次创建的 %d 个账号与 %d 条渠道映射"
                  % (len(self.created_users), len(self.created_channels)))
        except JourneyFailure as error:
            print("清理失败（不影响旅程结论）：%s" % error)


def parse_args(argv):
    parser = argparse.ArgumentParser(description="KCon 2026 抽奖系统端到端用户旅程测试")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080",
                        help="后端地址，默认 http://127.0.0.1:8080")
    parser.add_argument("--admin-phone", default="13000000000", help="超管登录手机号")
    parser.add_argument("--admin-password", required=True, help="超管登录密码（清场需要二次验密）")
    parser.add_argument("--redis-cmd", action="append", default=None,
                        help="读取验证码答案的命令前缀，可多次指定；"
                             "默认依次尝试 redis-cli 与 docker exec e2e-redis redis-cli")
    parser.add_argument("--keep", action="store_true", help="保留本次创建的账号与渠道映射")
    args = parser.parse_args(argv)
    if not args.redis_cmd:
        args.redis_cmd = ["redis-cli -h 127.0.0.1 -p 6379",
                          "docker exec e2e-redis redis-cli"]
    return args


def main(argv):
    args = parse_args(argv)
    journey = Journey(args)
    try:
        journey.execute()
    except JourneyFailure as error:
        print("\n端到端旅程失败：%s" % error)
        journey.cleanup()
        return 1
    journey.cleanup()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
