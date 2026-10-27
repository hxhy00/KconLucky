"""邮件模板：KCon2026 主视觉 HTML（10 封 + 验证码）。"""
from typing import Any, Dict, List
SHELL = """<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>{subject}</title></head>
<body style="margin:0;padding:0;background:#07090d;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#07090d;padding:24px 0;">
<tr><td align="center">
<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:600px;max-width:600px;background:#0b0e14;border-radius:8px;overflow:hidden;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',Helvetica,Arial,sans-serif;">
<tr><td style="height:5px;background:#00e5ff;font-size:0;line-height:0;">&nbsp;</td></tr>
<tr><td style="height:2px;background:#ff2d95;font-size:0;line-height:0;">&nbsp;</td></tr>
<tr><td style="padding:28px 40px 20px 40px;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"><tr>
<td align="left" style="font-size:34px;font-weight:800;color:#00e5ff;letter-spacing:1px;line-height:1.2;">KCon</td>
<td align="right" style="font-size:13px;color:#8b96a8;line-height:1.6;">
<span style="color:#ff2d95;font-weight:700;font-size:15px;">{theme}</span><br>{site_name}</td>
</tr></table></td></tr>
<tr><td style="padding:0 40px;"><div style="height:1px;background:linear-gradient(90deg,#00e5ff 0%,#ff2d95 40%,#1a1f2b 100%);font-size:0;line-height:0;">&nbsp;</div></td></tr>
<tr><td style="padding:32px 40px 8px 40px;color:#e8edf3;font-size:15px;line-height:1.85;">{body}</td></tr>
<tr><td style="padding:28px 40px 32px 40px;">
<div style="height:1px;background:#1a1f2b;font-size:0;line-height:0;margin-bottom:18px;">&nbsp;</div>
<div style="font-size:12px;color:#6b7688;line-height:1.9;">
KCon 组委会 &nbsp;|&nbsp; {system_email}<br>本邮件由系统自动发送，请勿直接回复。<br>如非本人操作，请忽略本邮件。</div>
</td></tr></table></td></tr></table></body></html>"""
def btn(text: str, url: str, color: str = "#00e5ff") -> str:
    """按钮组件。"""
    fg = "#0b0e14" if color == "#00e5ff" else "#ffffff"
    return (f'<table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:22px 0;">'
            f'<tr><td align="center" style="border-radius:6px;background:{color};">'
            f'<a href="{url}" target="_blank" style="display:inline-block;padding:13px 34px;font-size:15px;'
            f'font-weight:700;color:{fg};text-decoration:none;border-radius:6px;">{text}</a>'
            f'</td></tr></table>')
def info_table(rows: List[tuple]) -> str:
    """信息表格组件。"""
    html = ('<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" '
            'style="margin:18px 0;background:#111620;border-radius:6px;border-left:3px solid #00e5ff;">')
    for k, v in rows:
        html += (f'<tr><td style="padding:11px 18px;font-size:13px;color:#8b96a8;width:110px;'
                 f'border-bottom:1px solid #1a1f2b;">{k}</td>'
                 f'<td style="padding:11px 18px;font-size:14px;color:#e8edf3;'
                 f'border-bottom:1px solid #1a1f2b;">{v}</td></tr>')
    return html + "</table>"
def qr_block(datauri: str, caption: str = "") -> str:
    """二维码组件；datauri 为空时不渲染（现场签到走主系统，无扫码场景）。"""
    if not datauri:
        return ""
    cap = (f'<div style="font-size:12px;color:#6b7688;margin-top:10px;">{caption}</div>'
           if caption else "")
    return ('<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" '
            'style="margin:22px 0;"><tr><td align="center">'
            f'<img src="{datauri}" width="220" alt="二维码" '
            'style="display:block;border-radius:8px;border:1px solid #1a1f2b;">'
            f'{cap}</td></tr></table>')
def code_box(code: str) -> str:
    """编码展示框。"""
    return ('<div style="margin:18px 0;padding:16px;background:#111620;border:1px dashed #00e5ff;'
            'border-radius:6px;text-align:center;font-family:Menlo,Consolas,monospace;font-size:20px;'
            f'letter-spacing:3px;color:#00e5ff;font-weight:700;">{code}</div>')
def priority_tag() -> str:
    """优先级标签。"""
    return ('<div style="display:inline-block;padding:4px 12px;background:#ff2d95;color:#fff;'
            'font-size:12px;font-weight:700;border-radius:4px;margin-bottom:14px;">优先级：高</div>')
def data_table(headers: List[str], rows: List[List[Any]]) -> str:
    """数据表格组件。"""
    th = "".join(f'<th style="padding:10px 12px;font-size:12px;color:#8b96a8;text-align:left;'
                 f'border-bottom:1px solid #2a3140;font-weight:600;">{h}</th>' for h in headers)
    trs = ""
    for row in rows:
        tds = "".join(f'<td style="padding:10px 12px;font-size:13px;color:#e8edf3;'
                      f'border-bottom:1px solid #1a1f2b;">{c}</td>' for c in row)
        trs += f"<tr>{tds}</tr>"
    return ('<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" '
            'style="margin:16px 0;background:#111620;border-radius:6px;overflow:hidden;">'
            f'<tr style="background:#161c28;">{th}</tr>{trs}</table>')
def wrap(subject: str, body: str, site_name: str, theme: str, system_email: str) -> str:
    """套用主视觉外壳。"""
    return SHELL.format(subject=subject, body=body, theme=theme,
                        site_name=site_name, system_email=system_email)
def render_m01(ctx: Dict[str, Any], site_name: str, theme: str, system_email: str) -> str:
    """M-01 渠道开通通知。"""
    body = (f'<p style="margin:0 0 16px 0;">{ctx["approver_name"]} 您好：</p>'
            f'<p style="margin:0 0 16px 0;">KCon2026 赠票渠道 '
            f'<b style="color:#00e5ff;">「{ctx["channel_name"]}」</b> 已为您开通管理账号。</p>'
            + info_table([("登录邮箱", ctx["approver_email"]), ("登录方式", "邮箱 + 邮件验证码"),
                          ("渠道配额", f'{ctx["quota"]} 张'),
                          ("单人上限", f'{ctx["referrer_cap"]} 张')])
            + '<p style="margin:16px 0 8px 0;font-size:14px;color:#8b96a8;">推荐人注册链接（请分发给推荐人）：</p>'
            + f'<div style="padding:14px;background:#111620;border-radius:6px;word-break:break-all;'
              f'font-size:13px;color:#00e5ff;">{ctx["register_url"]}</div>'
            + btn("登录管理后台", ctx["login_url"])
            + '<p style="margin:16px 0 0 0;font-size:13px;color:#8b96a8;">请及时登录系统查看并分发链接。</p>')
    return wrap("您的渠道管理账号已开通", body, site_name, theme, system_email)
def render_m02(ctx: Dict[str, Any], site_name: str, theme: str, system_email: str) -> str:
    """M-02 推荐人审批。"""
    body = (priority_tag()
            + f'<p style="margin:0 0 16px 0;"><b style="color:#00e5ff;">{ctx["referrer_name"]}</b> '
              '申请成为赠票推荐人，请审批。</p>'
            + info_table([("姓名", ctx["referrer_name"]), ("公司", ctx["company"]),
                          ("部门", ctx["department"]), ("手机", ctx["mobile"]),
                          ("邮箱", ctx["email"]), ("申请时间", ctx["created_at"])])
            + '<p style="margin:16px 0 8px 0;">如同意本次申请，请点击下方按钮（一次性有效，7 天内有效）：</p>'
            + btn("同意该推荐人注册", ctx["approve_url"])
            + f'<p style="margin:8px 0 0 0;font-size:12px;color:#6b7688;word-break:break-all;">'
              f'若按钮无法点击，请复制链接：{ctx["approve_url"]}</p>'
            + f'<p style="margin:16px 0 0 0;font-size:13px;color:#8b96a8;">也可登录系统在"待审批推荐人"'
              f'列表中操作：<a href="{ctx["login_url"]}" style="color:#00e5ff;">{ctx["login_url"]}</a></p>')
    return wrap(f'「{ctx["referrer_name"]}」申请成为赠票推荐人，请审批', body, site_name, theme, system_email)
def render_m03(ctx: Dict[str, Any], site_name: str, theme: str, system_email: str) -> str:
    """M-03 推荐人专属链接。"""
    body = (f'<p style="margin:0 0 16px 0;">{ctx["referrer_name"]} 您好：</p>'
            '<p style="margin:0 0 16px 0;">您已成为 KCon2026 赠票推荐人，以下是您的专属赠票链接与二维码。</p>'
            '<p style="margin:0 0 8px 0;font-size:14px;color:#8b96a8;">'
            '请将链接或二维码分发给您希望邀请的参会人，同一链接可多次转发。</p>'
            + f'<div style="padding:14px;background:#111620;border-radius:6px;word-break:break-all;'
              f'font-size:13px;color:#00e5ff;">{ctx["referral_url"]}</div>'
            + qr_block(ctx["qr"], "扫码打开赠票注册页")
            + info_table([("所属渠道", ctx["channel_name"]),
                          ("赠票额度", f'{ctx["referrer_cap"]} 张'),
                          ("已发放", f'{ctx["issued_count"]} 张')]))
    return wrap("请查收KCon专属赠票链接", body, site_name, theme, system_email)
def render_m03r(ctx: Dict[str, Any], site_name: str, theme: str, system_email: str) -> str:
    """M-03R 推荐人拒绝通知。"""
    body = (f'<p style="margin:0 0 16px 0;">{ctx["referrer_name"]} 您好：</p>'
            '<p style="margin:0 0 16px 0;">很抱歉，您申请成为 KCon2026 赠票推荐人未通过渠道审批。</p>'
            + info_table([("渠道", ctx["channel_name"]), ("审批时间", ctx["rejected_at"])])
            + f'<p style="margin:16px 0 0 0;font-size:13px;color:#8b96a8;">如有疑问，请联系渠道负责人：'
              f'<a href="mailto:{ctx["approver_email"]}" style="color:#00e5ff;">{ctx["approver_email"]}</a></p>'
            + '<p style="margin:8px 0 0 0;font-size:13px;color:#8b96a8;">'
              '7 天后如需重新申请，可再次访问原注册链接。</p>')
    return wrap("您的推荐人申请未通过", body, site_name, theme, system_email)
def render_m04(ctx: Dict[str, Any], site_name: str, theme: str, system_email: str) -> str:
    """M-04 赠票申请审批。"""
    mobile = ctx.get("mobile") or "--"
    body = (priority_tag()
            + '<p style="margin:0 0 16px 0;">以下为赠票申请人信息：</p>'
            + info_table([("申请人", ctx["recipient_name"]), ("单位", ctx["company"]),
                          ("手机号", mobile), ("邮箱", ctx["email"]),
                          ("申请时间", ctx["created_at"])])
            + f'<p style="margin:16px 0 0 0;">您已发放赠票 '
              f'<b style="color:#00e5ff;">{ctx["issued_count"]}</b> 张'
              f'（上限 {ctx["referrer_cap"]} 张），如同意本次申请，请点击下方按钮。</p>'
            + btn("同意该赠票申请", ctx["approve_url"], "#ff2d95")
            + f'<p style="margin:8px 0 0 0;font-size:12px;color:#6b7688;word-break:break-all;">'
              f'一次性链接：{ctx["approve_url"]}</p>'
            + f'<p style="margin:14px 0 0 0;font-size:12px;color:#6b7688;">若链接失效，可访问 '
              f'<a href="{ctx["resend_url"]}" style="color:#00e5ff;">{ctx["resend_url"]}</a> '
              '重新获取审批链接。</p>')
    return wrap(f'「{ctx["recipient_name"]}」申请KCon2026大会赠票，请审批！',
                body, site_name, theme, system_email)
def render_m05(ctx: Dict[str, Any], site_name: str, theme: str, system_email: str) -> str:
    """M-05 注册成功（含注册码二维码）。"""
    body = (f'<p style="margin:0 0 16px 0;">尊敬的 <b style="color:#00e5ff;">{ctx["recipient_name"]}</b>：</p>'
            '<p style="margin:0 0 16px 0;">感谢注册 KCon2026！</p>'
            '<p style="margin:0 0 16px 0;color:#b8c2d1;">KCon 十四年见证行业风云变幻，始终不曾落下脚步。'
            '今年的主题是<b style="color:#ff2d95;">「奕智」</b>，我们来研讨安全与 AI 的相爱相杀。</p>'
            '<p style="margin:0 0 8px 0;">以下是您的注册码，现场凭此码领取参会资料，请妥善保管：</p>'
            + qr_block(ctx["qr"], "现场出示此二维码签到并领取资料")
            + code_box(ctx["code"])
            + f'<p style="margin:24px 0 0 0;text-align:right;color:#b8c2d1;">'
              f'KCon组委会 &amp; {ctx["referrer_name"]} 敬上</p>')
    return wrap('恭喜获得KCon2026参会资格，期待莅临现场共同研讨"AI的安全风险与安全的AI应用"',
                body, site_name, theme, system_email)
def render_m06(ctx: Dict[str, Any], site_name: str, theme: str, system_email: str) -> str:
    """M-06 赠票注册成功（通知推荐人）。"""
    mobile = ctx.get("mobile") or "--"
    body = (f'<p style="margin:0 0 16px 0;">{ctx["referrer_name"]}：您好！</p>'
            '<p style="margin:0 0 16px 0;">您发放的 KCon 门票被成功注册，信息如下：</p>'
            + info_table([("姓名", ctx["recipient_name"]), ("单位", ctx["company"]),
                          ("手机", mobile), ("邮箱", ctx["email"])])
            + '<p style="margin:16px 0 8px 0;">注册码：</p>'
            + f'<div style="padding:12px;background:#111620;border-radius:6px;word-break:break-all;'
              f'font-size:13px;color:#00e5ff;">{ctx["code_url"]}</div>'
            + qr_block(ctx["qr"], "该参会人的注册码二维码")
            + f'<p style="margin:20px 0 0 0;font-size:15px;">您已发放 '
              f'<b style="color:#ff2d95;font-size:18px;">{ctx["issued_count"]}</b> 张 KCon 赠票！'
              f'（上限 {ctx["referrer_cap"]} 张）</p>')
    return wrap(f'您赠与「{ctx["recipient_name"]}」的KCon2026门票成功注册！',
                body, site_name, theme, system_email)
def render_m07(ctx: Dict[str, Any], site_name: str, theme: str, system_email: str) -> str:
    """M-07 参会人已签到（通知推荐人）。"""
    mobile = ctx.get("mobile") or "--"
    body = (f'<p style="margin:0 0 16px 0;">{ctx["referrer_name"]} 您好：</p>'
            f'<p style="margin:0 0 16px 0;">您邀请的 KCon2026 参会人 '
            f'<b style="color:#00e5ff;">「{ctx["recipient_name"]}」</b> 已到场签到！联系方式如下：</p>'
            + info_table([("姓名", ctx["recipient_name"]), ("单位", ctx["company"]),
                          ("手机", mobile), ("邮箱", ctx["email"]),
                          ("签到时间", ctx["checked_at"])])
            + '<p style="margin:18px 0 0 0;color:#b8c2d1;">请妥善接待。</p>')
    return wrap(f'您邀请的参会人「{ctx["recipient_name"]}」已到场签到！',
                body, site_name, theme, system_email)
def render_m08(ctx: Dict[str, Any], site_name: str, theme: str, system_email: str) -> str:
    """M-08 签到汇总（通知审批员）。"""
    rows = [(x["name"], x["department"], x["invited"], x["checked_in"])
            for x in ctx["referrers"]]
    new_rows = [(x["referrer_name"], x["referrer_department"], x["recipient_name"],
                 x["recipient_company"], x["checked_at"]) for x in ctx["new_checkins"]]
    body = (f'<p style="margin:0 0 16px 0;">{ctx["approver_name"]} 您好：</p>'
            f'<p style="margin:0 0 16px 0;">{ctx["referrer_company"]} {ctx["referrer_department"]} '
            f'{ctx["referrer_name"]} 邀请的 {ctx["recipient_company"]} {ctx["recipient_name"]} '
            '已到现场签到，请关注。</p>'
            f'<p style="margin:20px 0 8px 0;font-size:14px;color:#8b96a8;">本时段新增签到'
            f'（{ctx["period_start"]} ~ {ctx["period_end"]}，共 {ctx["period_count"]} 人）：</p>'
            + data_table(["推荐人", "推荐人部门", "签到人", "签到人单位", "签到时间"], new_rows)
            + '<p style="margin:20px 0 8px 0;font-size:14px;color:#8b96a8;">'
              '您管理的推荐人邀请参会信息如下：</p>'
            + data_table(["姓名", "推荐人部门", "邀请数量", "到场签到数量"], rows))
    return wrap(f'「{ctx["referrer_name"]}」邀请的「{ctx["recipient_name"]}」已到场签到，请关注',
                body, site_name, theme, system_email)
def render_m09(ctx: Dict[str, Any], site_name: str, theme: str, system_email: str) -> str:
    """M-09 数据日报（通知管理员）。"""
    body = (f'<p style="margin:0 0 16px 0;">{ctx["admin_name"]} 您好：</p>'
            f'<p style="margin:0 0 16px 0;">渠道 <b style="color:#00e5ff;">「{ctx["channel_name"]}」</b> '
            f'{ctx["date"]} 数据如下：</p>'
            + info_table([("配额", f'{ctx["quota"]} 张'), ("已使用", f'{ctx["used"]} 张'),
                          ("剩余", f'{ctx["remaining"]} 张'),
                          ("推荐人", f'{ctx["referrer_count"]} 人'),
                          ("邀请总数", f'{ctx["invited_count"]} 人'),
                          ("注册成功", f'{ctx["approved_count"]} 人'),
                          ("已签到", f'{ctx["checked_in_count"]} 人'),
                          ("签到率", f'{ctx["checkin_rate"]}%')]))
    return wrap(f'渠道「{ctx["channel_name"]}」赠票数据日报', body, site_name, theme, system_email)
def render_code(ctx: Dict[str, Any], site_name: str, theme: str, system_email: str) -> str:
    """验证码邮件。"""
    body = ('<p style="margin:0 0 16px 0;">您好：</p>'
            '<p style="margin:0 0 16px 0;">您正在登录 KCon2026 赠票系统，本次验证码为：</p>'
            + code_box(ctx["code"])
            + '<p style="margin:16px 0 0 0;font-size:13px;color:#8b96a8;">'
              '验证码 10 分钟内有效，请勿泄露给他人。</p>'
            + '<p style="margin:8px 0 0 0;font-size:13px;color:#8b96a8;">'
              '如非本人操作，请忽略本邮件。</p>')
    return wrap("KCon2026 登录验证码", body, site_name, theme, system_email)
RENDERERS = {
    "M-01": render_m01, "M-02": render_m02, "M-03": render_m03, "M-03R": render_m03r,
    "M-04": render_m04, "M-05": render_m05, "M-06": render_m06, "M-07": render_m07,
    "M-08": render_m08, "M-09": render_m09, "CODE": render_code,
}
def render(template: str, ctx: Dict[str, Any], site_name: str,
           theme: str, system_email: str) -> str:
    """渲染邮件 HTML。
    Raises:
        ValueError: 未知模板
    """
    fn = RENDERERS.get(template)
    if fn is None:
        raise ValueError(f"未知邮件模板: {template}")
    return fn(ctx, site_name, theme, system_email)