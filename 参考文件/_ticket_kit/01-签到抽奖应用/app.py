# -*- coding: utf-8 -*-
"""
KCon 2026 自助签到 + 抽奖小应用 v5
==================================
观众端：扫码 → 手机号/后四位 或 姓名搜索+尾号确认 → 识别票源 → 点签到
        → 系统分配随机端口号(1-65535) → 大字号显示，提示截图保存
管理端：/admin 自动打钩+签到时间+端口号；上传Excel/CSV、持续追加、导出
抽奖端：/lottery 大屏滚动抽号；分上午场/下午场（按截止时间过滤）；
        一号只中一次；作废重抽；兑奖核验防伪造
用法：python3 app.py [--port 8000]
数据文件（同目录）：attendees.csv / signed.json / ports.json / winners.json / session.json
"""
import csv
import io
import json
import os
import random
import socket
import sys
import threading
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CSV_PATH = os.path.join(BASE_DIR, "attendees.csv")
SIGNED_PATH = os.path.join(BASE_DIR, "signed.json")
PORTS_PATH = os.path.join(BASE_DIR, "ports.json")
WINNERS_PATH = os.path.join(BASE_DIR, "winners.json")
SESSION_PATH = os.path.join(BASE_DIR, "session.json")
CHANNELS = {
    "S": "嘶吼", "T": "TGO", "C": "崔牛会", "H": "活动行",
    "D": "对公票", "G": "赠票", "V": "VIP/嘉宾/媒体",
}
_lock = threading.Lock()
ATTENDEES = []
SIGNED = {}     # phone -> {"time":"HH:MM:SS","date":"YYYY-MM-DD"}
PORTS = {}      # phone -> port
WINNERS = []    # [{phone,port,name,channel,time,date}]
SESSION = {"round": "morning", "cutoff": ""}   # round: morning/afternoon/all
def load_attendees():
    if not os.path.exists(CSV_PATH):
        return []
    for enc in ("utf-8-sig", "utf-8", "gbk"):
        try:
            with open(CSV_PATH, encoding=enc) as f:
                reader = csv.DictReader(f)
                rows = []
                for r in reader:
                    def pick(*keys):
                        for k in keys:
                            if k in r and r[k] is not None:
                                return str(r[k]).strip()
                        return ""
                    name = pick("姓名", "name")
                    phone = pick("手机号", "phone", "电话", "手机")
                    ch = pick("渠道标识", "channel", "渠道", "来源").upper()
                    tk = pick("票种", "ticket")
                    if phone:
                        rows.append({"name": name, "phone": phone, "channel": ch, "ticket": tk})
                return rows
        except (UnicodeDecodeError, UnicodeError):
            continue
    return []
def save_attendees():
    tmp = CSV_PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["姓名", "手机号", "渠道标识", "票种"])
        for a in ATTENDEES:
            w.writerow([a["name"], a["phone"], a["channel"], a["ticket"]])
    os.replace(tmp, CSV_PATH)
def _load_json(path, default):
    if os.path.exists(path):
        try:
            with open(path, encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return default
    return default
def _save_json(path, obj, indent=None):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=indent)
    os.replace(tmp, path)
def find_attendee(q):
    """支持完整手机号 / 后4位（唯一才返回）"""
    q = q.strip()
    exact = [a for a in ATTENDEES if a["phone"] == q]
    if exact:
        return exact[0]
    if len(q) == 4:
        tail = [a for a in ATTENDEES if a["phone"].endswith(q)]
        if len(tail) == 1:
            return tail[0]
    return None
def search_by_name(name):
    name = name.strip()
    if not name:
        return []
    return [a for a in ATTENDEES if name.lower() in a["name"].lower()]
def mask_phone(p):
    return p[:3] + "****" + p[-4:] if len(p) >= 8 else p
def assign_port(phone):
    if phone in PORTS:
        return PORTS[phone]
    used = set(PORTS.values())
    while True:
        p = random.randint(1, 65535)
        if p not in used:
            PORTS[phone] = p
            _save_json(PORTS_PATH, PORTS)
            return p
def eligible_pool(round_=None):
    """抽奖池：当日签到 + 未中奖 + （按场次截止过滤）"""
    today = datetime.now().strftime("%Y-%m-%d")
    won_phones = {w["phone"] for w in WINNERS}
    cutoff = SESSION.get("cutoff", "") if round_ is None else ""
    pool = []
    for ph, port in PORTS.items():
        rec = SIGNED.get(ph)
        if not rec or rec.get("date") != today:
            continue
        if ph in won_phones:
            continue
        if cutoff and rec.get("time", "99:99:99") > cutoff:
            continue
        pool.append((port, ph))
    return pool
def merge_rows(new_rows, mode="append"):
    global ATTENDEES
    if mode == "replace":
        ATTENDEES = []
    existing = {a["phone"]: a for a in ATTENDEES}
    added = updated = skipped = 0
    for r in new_rows:
        if r["phone"] in existing:
            a = existing[r["phone"]]
            changed = False
            for k in ("name", "channel", "ticket"):
                if r[k] and a[k] != r[k]:
                    a[k] = r[k]; changed = True
            updated += 1 if changed else 0
            skipped += 0 if changed else 1
        else:
            ATTENDEES.append(r)
            existing[r["phone"]] = r
            added += 1
    save_attendees()
    return added, updated, skipped, len(ATTENDEES)
def parse_upload(filename, content_bytes):
    name = (filename or "").lower()
    if name.endswith(".csv"):
        for enc in ("utf-8-sig", "utf-8", "gbk"):
            try:
                text = content_bytes.decode(enc)
                reader = csv.DictReader(io.StringIO(text))
                rows = []
                for r in reader:
                    def pick(*keys):
                        for k in keys:
                            if k in r and r[k] is not None:
                                return str(r[k]).strip()
                        return ""
                    phone = pick("手机号", "phone", "电话", "手机")
                    if phone:
                        rows.append({"name": pick("姓名", "name"), "phone": phone,
                                     "channel": pick("渠道标识", "channel", "渠道", "来源").upper(),
                                     "ticket": pick("票种", "ticket")})
                return rows, None
            except (UnicodeDecodeError, UnicodeError):
                continue
        return [], "CSV 编码无法识别，请另存为 UTF-8"
    if name.endswith((".xlsx", ".xls")):
        try:
            from openpyxl import load_workbook
        except ImportError:
            return [], "服务器缺少 openpyxl，无法解析 Excel，请改用 CSV"
        try:
            wb = load_workbook(io.BytesIO(content_bytes), read_only=True, data_only=True)
            ws = wb.active
            it = ws.iter_rows(values_only=True)
            header = next(it, None)
            if not header:
                return [], "Excel 为空"
            idx = {}
            for i, h in enumerate(header):
                h = str(h).strip() if h is not None else ""
                if h in ("姓名", "name"): idx["name"] = i
                elif h in ("手机号", "phone", "电话", "手机"): idx["phone"] = i
                elif h in ("渠道标识", "channel", "渠道", "来源"): idx["channel"] = i
                elif h in ("票种", "ticket"): idx["ticket"] = i
            if "phone" not in idx:
                return [], "未找到「手机号」列"
            rows = []
            for row in it:
                if row is None: continue
                def g(k):
                    i = idx.get(k)
                    if i is None or i >= len(row) or row[i] is None: return ""
                    return str(row[i]).strip()
                phone = g("phone")
                if phone:
                    rows.append({"name": g("name"), "phone": phone,
                                 "channel": g("channel").upper(), "ticket": g("ticket")})
            return rows, None
        except Exception as e:
            return [], "Excel 解析失败：%s" % e
    return [], "不支持的文件类型，请上传 .xlsx/.xls/.csv"
def esc(s):
    return (str(s if s is not None else "").replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;"))
PAGE_CSS = """
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;
background:#f0f4f8;min-height:100vh;display:flex;justify-content:center;padding:24px 12px}
.card{background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(31,78,121,.12);
max-width:420px;width:100%;padding:28px 22px}
.logo{font-size:13px;color:#1f4e79;font-weight:600;letter-spacing:1px;text-align:center}
h1{font-size:20px;color:#1a2b3c;text-align:center;margin:8px 0 14px}
.tabs{display:flex;gap:8px;margin-bottom:14px}
.tab{flex:1;text-align:center;padding:10px;border-radius:8px;background:#eef2f6;
color:#567;font-size:14px;font-weight:600;cursor:pointer}
.tab.on{background:#1f4e79;color:#fff}
input[type=text]{width:100%;padding:14px 16px;font-size:17px;border:2px solid #d5e0ea;
border-radius:10px;outline:none}
input[type=text]:focus{border-color:#1f4e79}
.btn{display:block;width:100%;padding:14px;font-size:17px;font-weight:600;color:#fff;
background:#1f4e79;border:none;border-radius:10px;margin-top:14px;cursor:pointer}
.btn:active{opacity:.85}
.btn.green{background:#1e7e46}
.btn.gray{background:#8aa}
.btn:disabled{background:#9db4c6}
.info{margin-top:16px}
.info .row{display:flex;justify-content:space-between;padding:12px 4px;
border-bottom:1px solid #eef2f6;font-size:15px}
.info .row b{color:#1a2b3c}
.tag{display:inline-block;padding:3px 10px;border-radius:20px;font-size:13px;
font-weight:600;background:#e8f0f8;color:#1f4e79}
.ok{text-align:center;padding:6px 0}
.ok .icon{font-size:50px}
.ok h2{font-size:19px;color:#1e7e46;margin:8px 0 4px}
.ok p{color:#667;font-size:14px;line-height:1.7}
.err{background:#fdf0f0;color:#c0392b;padding:12px;border-radius:10px;
font-size:14px;text-align:center;margin-top:14px}
.hint{color:#8aa;font-size:12px;text-align:center;margin-top:16px;line-height:1.7}
.portbox{background:#1f4e79;color:#fff;border-radius:12px;padding:16px;margin:14px 0;text-align:center}
.portbox .pn{font-size:46px;font-weight:800;letter-spacing:5px;line-height:1.1}
.portbox .pl{font-size:12px;opacity:.8;margin-top:4px}
.cand{border:1px solid #d5e0ea;border-radius:10px;padding:12px;margin-top:10px;
display:flex;justify-content:space-between;align-items:center;font-size:14px}
.cand b{color:#1a2b3c}
.cand .pick{background:#1e7e46;color:#fff;border:none;border-radius:6px;
padding:6px 14px;font-size:13px;cursor:pointer}
"""
SIGNIN_PAGE = """<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>KCon 2026 签到</title><style>__CSS__</style></head><body>
<div class="card">
  <div class="logo">KCON 2026 · 知道创宇</div>
  <h1>参会签到</h1>
  <div id="step0">
    <div class="tabs">
      <div class="tab on" id="tabPhone" onclick="switchTab('phone')">手机号签到</div>
      <div class="tab" id="tabName" onclick="switchTab('name')">姓名签到</div>
    </div>
    <div id="phoneBox">
      <input type="text" id="phone" inputmode="numeric" maxlength="11" placeholder="输入购票手机号（或后四位）">
      <button class="btn" onclick="lookup()">查询票务信息</button>
    </div>
    <div id="nameBox" style="display:none">
      <input type="text" id="nameq" placeholder="输入您的姓名">
      <button class="btn" onclick="searchName()">搜索我的名字</button>
    </div>
    <div class="hint">找不到信息请联系现场工作人员</div>
  </div>
  <div id="candList" style="display:none"></div>
  <div id="step1" style="display:none">
    <div class="info" id="info"></div>
    <button class="btn green" id="signbtn" onclick="doSign()">确认签到</button>
    <button class="btn gray" onclick="reset()">返回重新输入</button>
  </div>
  <div id="step2" style="display:none"></div>
  <div id="errbox"></div>
</div>
<script>
var cur = null;
function show(id){["step0","candList","step1","step2"].forEach(function(s){
  document.getElementById(s).style.display = s===id?"block":"none";});}
function err(msg){document.getElementById("errbox").innerHTML='<div class="err">'+msg+'</div>';}
function switchTab(t){
  document.getElementById("tabPhone").className = t==="phone"?"tab on":"tab";
  document.getElementById("tabName").className = t==="name"?"tab on":"tab";
  document.getElementById("phoneBox").style.display = t==="phone"?"block":"none";
  document.getElementById("nameBox").style.display = t==="name"?"block":"none";
  document.getElementById("errbox").innerHTML="";
}
function post(path,body){
  return fetch(path,{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},
    body:body}).then(function(r){return r.json();});
}
function lookup(){
  document.getElementById("errbox").innerHTML="";
  var p = document.getElementById("phone").value.trim();
  if(!/^[0-9]{4,11}$/.test(p)){err("请输入手机号或后四位");return;}
  post("/api/lookup","q="+encodeURIComponent(p)).then(function(d){
    if(!d.found){err(d.msg||"未找到票务信息");return;}
    renderInfo(d);
  });
}
function searchName(){
  document.getElementById("errbox").innerHTML="";
  var n = document.getElementById("nameq").value.trim();
  if(!n){err("请输入姓名");return;}
  post("/api/search_name","q="+encodeURIComponent(n)).then(function(d){
    if(!d.list.length){err("未找到该姓名的报名信息");return;}
    if(d.list.length===1){renderInfo(Object.assign({found:true},d.list[0]));return;}
    var html='<h1 style="font-size:16px">找到多人，请选自己并核对尾号</h1>';
    d.list.forEach(function(c){
      html+='<div class="cand"><span>'+esc(c.name)+'<br><small>'+esc(c.phone_mask)+
        ' · '+esc(c.channel_name)+'</small></span>'+
        '<button class="pick" onclick="pickOne(\\''+c.phone+'\\')">是我</button></div>';
    });
    html+='<button class="btn gray" onclick="reset()">返回</button>';
    document.getElementById("candList").innerHTML=html;
    show("candList");
  });
}
function pickOne(phone){
  post("/api/lookup","q="+encodeURIComponent(phone)).then(function(d){
    if(!d.found){err("选择失败，请重试");return;}
    renderInfo(d);
  });
}
function renderInfo(d){
  cur = d;
  document.getElementById("info").innerHTML =
    '<div class="row"><span>姓名</span><b>'+esc(d.name)+'</b></div>'+
    '<div class="row"><span>票源渠道</span><span class="tag">'+esc(d.channel_name)+'</span></div>'+
    '<div class="row"><span>票种</span><b>'+esc(d.ticket||"参会票")+'</b></div>'+
    (d.signed ? '<div class="row"><span>状态</span><b style="color:#1e7e46">已签到 '+esc(d.signed_time)+'</b></div>' : '')+
    (d.port ? '<div class="row"><span>抽奖端口号</span><b style="color:#1f4e79;font-size:20px">'+esc(d.port)+'</b></div>' : '');
  var btn = document.getElementById("signbtn");
  if(d.signed){btn.disabled=true;btn.textContent="已完成签到";}
  else{btn.disabled=false;btn.textContent="确认签到";}
  show("step1");
}
function doSign(){
  if(!cur)return;
  var btn = document.getElementById("signbtn");btn.disabled=true;
  post("/api/sign","q="+encodeURIComponent(cur.phone)).then(function(d){
    if(!d.ok){err(d.msg||"签到失败");btn.disabled=false;return;}
    document.getElementById("step2").innerHTML =
      '<div class="ok"><div class="icon">🎉</div><h2>签到成功</h2>'+
      '<p>'+esc(d.name)+' · '+esc(d.channel_name)+' · '+esc(d.time)+'</p>'+
      '<p style="color:#c0392b;font-weight:600;margin-top:6px">本次大会奖品丰厚</p>'+
      '<div class="portbox"><div class="pn">'+esc(d.port)+'</div>'+
      '<div class="pl">系统随机为您分配的抽奖端口号</div></div>'+
      '<p style="font-size:15px;font-weight:600;color:#1a2b3c">📸 请务必截图保存本页面</p>'+
      '<p>此端口号是现场抽奖与兑奖的唯一凭证</p>'+
      '<p class="hint">忘记截图？重新输入手机号即可再次查看<br>仅限当日签到者参与对应场次抽奖</p></div>'+
      '<button class="btn gray" onclick="reset()">返回</button>';
    show("step2");
  }).catch(function(){err("网络异常，请重试");btn.disabled=false;});
}
function reset(){cur=null;document.getElementById("phone").value="";
  document.getElementById("nameq").value="";document.getElementById("errbox").innerHTML="";show("step0");}
function esc(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;");}
</script></body></html>"""
ADMIN_PAGE = """<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>KCon 2026 签到管理台</title><style>__CSS__
.wrap{max-width:980px;width:100%}
.stats{display:flex;gap:12px;flex-wrap:wrap;margin-bottom:16px}
.stat{background:#fff;border-radius:12px;padding:14px 20px;flex:1;min-width:130px;
box-shadow:0 2px 12px rgba(31,78,121,.08)}
.stat .num{font-size:26px;font-weight:700;color:#1f4e79}
.stat .lbl{font-size:12px;color:#789;margin-top:2px}
.bar{background:#fff;border-radius:12px;padding:14px 18px;margin-bottom:16px;
box-shadow:0 2px 12px rgba(31,78,121,.08)}
.bar h3{font-size:14px;color:#1f4e79;margin-bottom:10px}
.chline{display:flex;align-items:center;margin:6px 0;font-size:13px}
.chline .nm{width:110px;color:#345}
.chline .track{flex:1;background:#eef2f6;border-radius:6px;height:18px;overflow:hidden}
.chline .fill{height:100%;background:#1f4e79;border-radius:6px}
.chline .ct{width:70px;text-align:right;color:#1f4e79;font-weight:600}
table{width:100%;border-collapse:collapse;background:#fff;border-radius:12px;
overflow:hidden;box-shadow:0 2px 12px rgba(31,78,121,.08);font-size:13px}
th{background:#1f4e79;color:#fff;padding:10px 8px;text-align:left;font-weight:600}
td{padding:9px 8px;border-bottom:1px solid #eef2f6;color:#234}
tr.done td{background:#f2faf5}
.yes{color:#1e7e46;font-weight:700}.no{color:#b00}
.tools{display:flex;gap:10px;margin-bottom:16px;flex-wrap:wrap;align-items:center}
.tools a,.tools button,.tools label{padding:9px 18px;border-radius:8px;border:none;
font-size:14px;font-weight:600;cursor:pointer;text-decoration:none;display:inline-block}
.exp{background:#1e7e46;color:#fff}.qrbtn{background:#1f4e79;color:#fff}.dl{background:#8aa;color:#fff}
.urlbox{background:#fff;border-radius:12px;padding:14px 18px;margin-bottom:16px;
box-shadow:0 2px 12px rgba(31,78,121,.08);font-size:13px;color:#345;line-height:1.8}
.urlbox code{background:#eef2f6;padding:2px 8px;border-radius:4px;color:#1f4e79}
.upload{background:#fff;border-radius:12px;padding:16px 18px;margin-bottom:16px;
box-shadow:0 2px 12px rgba(31,78,121,.08)}
.upload h3{font-size:14px;color:#1f4e79;margin-bottom:10px}
.upload .row{display:flex;gap:10px;align-items:center;flex-wrap:wrap}
.upload input[type=file]{font-size:13px;padding:6px;border:1px solid #d5e0ea;border-radius:8px}
.upload select{font-size:13px;padding:8px;border:1px solid #d5e0ea;border-radius:8px}
.upload .go{background:#1f4e79;color:#fff;padding:9px 22px;border:none;border-radius:8px;
font-size:14px;font-weight:600;cursor:pointer}
.msg{margin-top:10px;font-size:13px;padding:10px 12px;border-radius:8px;display:none}
.msg.ok{background:#eaf7ef;color:#1e7e46;display:block}
.msg.err{background:#fdf0f0;color:#c0392b;display:block}
</style></head><body><div class="wrap">
<h1 style="font-size:20px;color:#1a2b3c;margin-bottom:14px">KCon 2026 签到管理台</h1>
<div class="urlbox">
  📱 <b>观众签到地址</b>：<code>__SIGNIN_URL__</code><br>
  🎁 <b>抽奖大屏地址</b>：<code>__LOTTERY_URL__</code>
</div>
<div class="upload">
  <h3>📥 上传票务名单（Excel / CSV）</h3>
  <div class="row">
    <input type="file" id="file" accept=".xlsx,.xls,.csv">
    <select id="mode"><option value="append">追加（按手机号去重）</option>
      <option value="replace">替换（清空后导入）</option></select>
    <button class="go" onclick="upload()">上传导入</button>
    <a class="dl" href="/template">下载模板</a>
  </div>
  <div class="msg" id="umsg"></div>
  <p style="font-size:12px;color:#9ab;margin-top:8px">表头：姓名 / 手机号 / 渠道标识 / 票种</p>
</div>
<div class="tools">
  <a class="exp" href="/export" download>⬇ 导出签到Excel</a>
  <a class="qrbtn" href="/lottery" target="_blank">🎁 抽奖页面</a>
  <button class="qrbtn" onclick="window.print()">🖨 打印本页</button>
</div>
<div class="stats" id="stats"></div>
<div class="bar"><h3>各渠道签到进度</h3><div id="chbars"></div></div>
<table><thead><tr><th>姓名</th><th>手机号</th><th>渠道</th><th>票种</th>
<th>抽奖号</th><th>状态</th><th>签到时间</th></tr></thead><tbody id="rows"></tbody></table>
<p style="color:#9ab;font-size:12px;text-align:center;margin:16px 0">每5秒自动刷新 · 数据仅存本机</p>
</div>
<script>
function esc(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");}
function refresh(){
  fetch("/api/admin_data").then(function(r){return r.json();}).then(function(d){
    document.getElementById("stats").innerHTML =
      '<div class="stat"><div class="num">'+d.total+'</div><div class="lbl">总人数</div></div>'+
      '<div class="stat"><div class="num" style="color:#1e7e46">'+d.signed_count+'</div><div class="lbl">已签到</div></div>'+
      '<div class="stat"><div class="num">'+d.unsign+'</div><div class="lbl">未签到</div></div>'+
      '<div class="stat"><div class="num">'+d.rate+'%</div><div class="lbl">签到率</div></div>';
    var bars="";
    d.channels.forEach(function(c){
      var pct=c.total?Math.round(c.signed*100/c.total):0;
      bars+='<div class="chline"><span class="nm">'+esc(c.name)+'</span>'+
        '<div class="track"><div class="fill" style="width:'+pct+'%"></div></div>'+
        '<span class="ct">'+c.signed+'/'+c.total+'</span></div>';
    });
    document.getElementById("chbars").innerHTML=bars;
    var rows="";
    d.list.forEach(function(a){
      rows+='<tr class="'+(a.signed?"done":"")+'"><td>'+esc(a.name)+'</td><td>'+esc(a.phone_mask)+'</td>'+
        '<td>'+esc(a.channel_name)+'</td><td>'+esc(a.ticket)+'</td>'+
        '<td>'+(a.port?'<b style="color:#1f4e79">'+esc(a.port)+'</b>':'—')+'</td>'+
        '<td>'+(a.signed?'<span class="yes">✅ 已签到</span>':'<span class="no">未签到</span>')+'</td>'+
        '<td>'+esc(a.signed_time||"—")+'</td></tr>';
    });
    document.getElementById("rows").innerHTML=rows;
  });
}
function upload(){
  var f=document.getElementById("file").files[0];var msg=document.getElementById("umsg");
  if(!f){msg.className="msg err";msg.textContent="请先选择文件";return;}
  var fd=new FormData();fd.append("file",f);fd.append("mode",document.getElementById("mode").value);
  msg.className="msg";msg.textContent="上传中...";
  fetch("/api/upload",{method:"POST",body:fd}).then(function(r){return r.json();}).then(function(d){
    if(d.ok){msg.className="msg ok";msg.textContent="导入成功：新增 "+d.added+"，更新 "+d.updated+"，跳过 "+d.skipped+"，共 "+d.total+" 人";
      document.getElementById("file").value="";refresh();}
    else{msg.className="msg err";msg.textContent="导入失败："+d.msg;}
  }).catch(function(){msg.className="msg err";msg.textContent="上传异常";});
}
setInterval(refresh,5000);refresh();
</script></body></html>"""
LOTTERY_PAGE = """<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>KCon 2026 现场抽奖</title><style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;
background:#0e2233;min-height:100vh;display:flex;flex-direction:column;
align-items:center;justify-content:center;padding:24px;color:#fff}
.t{font-size:16px;color:#7fb3e0;letter-spacing:3px;margin-bottom:14px}
.rounds{display:flex;gap:10px;margin-bottom:16px}
.rbtn{padding:8px 20px;border-radius:8px;border:1px solid #2a5a86;background:#123047;
color:#9cc;font-size:14px;cursor:pointer}
.rbtn.on{background:#1f4e79;color:#fff;border-color:#1f4e79;font-weight:700}
.numbox{background:#123047;border:2px solid #2a5a86;border-radius:20px;
padding:30px 56px;text-align:center;min-width:320px}
.num{font-size:88px;font-weight:800;letter-spacing:8px;color:#ffd94d;line-height:1.1}
.winfo{font-size:18px;color:#cfe6f7;margin-top:10px}
.draw{margin-top:22px;padding:16px 56px;font-size:22px;font-weight:700;color:#fff;
background:#1e7e46;border:none;border-radius:12px;cursor:pointer;letter-spacing:4px}
.draw:active{opacity:.85}.draw:disabled{background:#3a5a6a;cursor:default}
.reset{margin-top:10px;padding:7px 18px;font-size:13px;color:#9ab;background:none;
border:1px solid #3a5a6a;border-radius:8px;cursor:pointer}
.msg{margin-top:12px;font-size:14px;color:#ff9a9a;min-height:20px}
.pool{margin-top:12px;font-size:14px;color:#7fb3e0;background:#123047;
padding:8px 18px;border-radius:8px}
.list{margin-top:16px;font-size:14px;color:#cfe6f7;background:#123047;
border-radius:12px;padding:14px 22px;max-width:560px;width:100%}
.list h3{font-size:14px;color:#7fb3e0;margin-bottom:8px}
.list .row{display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid #1a3a54}
.list .row b{color:#ffd94d}
.undo{color:#ff9a9a;background:none;border:none;cursor:pointer;font-size:14px}
.verify{margin-top:14px;background:#123047;border-radius:12px;padding:14px 22px;max-width:560px;width:100%}
.verify h3{font-size:14px;color:#7fb3e0;margin-bottom:10px}
.verify .vrow{display:flex;gap:10px}
.verify input{flex:1;padding:10px 14px;font-size:16px;border-radius:8px;
border:1px solid #2a5a86;background:#0e2233;color:#fff;letter-spacing:2px}
.verify button{padding:10px 24px;font-size:15px;font-weight:600;color:#fff;
background:#1f4e79;border:none;border-radius:8px;cursor:pointer}
.verify .vres{margin-top:10px;font-size:14px;color:#cfe6f7;line-height:1.8;min-height:22px}
.vres .ok{color:#7ee2a8;font-weight:700}.vres .bad{color:#ff9a9a;font-weight:700}
.cut{margin-top:8px;font-size:13px;color:#9cc}
.cut input{background:#0e2233;border:1px solid #2a5a86;color:#fff;padding:6px 10px;
border-radius:6px;font-size:13px}
</style></head><body>
<div class="t">KCON 2026 · 现场抽奖</div>
<div class="rounds">
  <div class="rbtn on" id="rbMorning" onclick="setRound('morning')">上午场</div>
  <div class="rbtn" id="rbAfternoon" onclick="setRound('afternoon')">下午场</div>
  <div class="rbtn" id="rbAll" onclick="setRound('all')">全天</div>
</div>
<div class="cut">本场签到截止时刻（早于此时间签到才可参与）：
  <input type="text" id="cutoff" placeholder="如 12:00:00，留空不限" onchange="setCutoff()">
</div>
<div class="numbox"><div class="num" id="num">----</div><div class="winfo" id="winfo">点击开始抽奖</div></div>
<button class="draw" id="drawbtn" onclick="draw()">🎁 抽 奖</button>
<button class="reset" onclick="resetDraw()">清空中奖记录</button>
<div class="msg" id="msg"></div>
<div class="pool" id="pool">加载中...</div>
<div class="list"><h3>中奖名单（点 ✕ 作废可重抽）</h3><div id="winners"></div></div>
<div class="verify"><h3>🔍 兑奖核验（防伪造截图）</h3>
  <div class="vrow"><input type="text" id="vport" inputmode="numeric" maxlength="5" placeholder="输入端口号">
  <button onclick="verifyPort()">核验</button></div>
  <div class="vres" id="vres"></div>
</div>
<script>
var round = "morning";
function esc(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");}
function postForm(path,body){return fetch(path,{method:"POST",
  headers:{"Content-Type":"application/x-www-form-urlencoded"},body:body})
  .then(function(r){return r.json();});}
function setRound(r){
  round=r;
  document.getElementById("rbMorning").className=r==="morning"?"rbtn on":"rbtn";
  document.getElementById("rbAfternoon").className=r==="afternoon"?"rbtn on":"rbtn";
  document.getElementById("rbAll").className=r==="all"?"rbtn on":"rbtn";
  postForm("/api/set_round","q="+r).then(function(){refreshWinners();});
}
function setCutoff(){
  var c=document.getElementById("cutoff").value.trim();
  postForm("/api/set_cutoff","q="+encodeURIComponent(c)).then(function(){refreshWinners();});
}
function draw(){
  var btn=document.getElementById("drawbtn");btn.disabled=true;
  document.getElementById("msg").textContent="";
  var el=document.getElementById("num");var ticks=0;
  var timer=setInterval(function(){
    el.textContent=String(Math.floor(Math.random()*65535)+1);
    if(++ticks>=18){clearInterval(timer);doDraw();}
  },60);
}
function doDraw(){
  postForm("/api/draw","q="+round).then(function(d){
    var btn=document.getElementById("drawbtn");
    if(!d.ok){document.getElementById("msg").textContent=d.msg;
      document.getElementById("num").textContent="----";btn.disabled=false;return;}
    document.getElementById("num").textContent=d.port;
    document.getElementById("winfo").textContent="🎉 "+d.name+" · "+d.channel_name;
    btn.disabled=false;refreshWinners();
  }).catch(function(){document.getElementById("msg").textContent="网络异常";
    document.getElementById("drawbtn").disabled=false;});
}
function refreshWinners(){
  fetch("/api/winners").then(function(r){return r.json();}).then(function(d){
    var html="";
    if(!d.list.length){html="<div style='color:#5a7a94'>暂无中奖记录</div>";}
    d.list.forEach(function(w){
      html+='<div class="row"><span><b>端口 '+w.port+'</b> · '+esc(w.name)+
        ' ['+esc(w.round_name)+']</span><span>'+esc(w.time)+
        ' <button class="undo" onclick="undoDraw(\\''+w.phone+'\\')">✕</button></span></div>';
    });
    document.getElementById("winners").innerHTML=html;
    var pool=document.getElementById("pool");
    if(d.today_signed===0){pool.innerHTML="⚠️ 今日还没有人签到，请先让观众签到";pool.style.color="#ffd94d";}
    else{pool.innerHTML="当前【"+d.round_name+"】可抽奖号 "+d.pool+" 个 · 今日已签到 "+d.today_signed+" 人 · 已中奖 "+d.list.length+" 人";pool.style.color="#7fb3e0";}
    if(d.cutoff)document.getElementById("cutoff").value=d.cutoff;
  });
}
function undoDraw(phone){
  if(!confirm("作废这条中奖记录？号码将回到抽奖池。"))return;
  postForm("/api/draw_undo","q="+encodeURIComponent(phone)).then(function(d){
    document.getElementById("msg").textContent=d.ok?"已作废，可重新抽奖":d.msg;
    refreshWinners();});
}
function resetDraw(){
  if(!confirm("确定清空所有中奖记录？"))return;
  postForm("/api/draw_reset","").then(function(){
    document.getElementById("num").textContent="----";
    document.getElementById("winfo").textContent="点击开始抽奖";refreshWinners();});
}
function verifyPort(){
  var p=document.getElementById("vport").value.trim();
  var res=document.getElementById("vres");
  if(!/^[0-9]{1,5}$/.test(p)){res.innerHTML='<span class="bad">请输入端口号</span>';return;}
  postForm("/api/verify_port","q="+encodeURIComponent(p)).then(function(d){
    if(!d.ok){res.innerHTML='<span class="bad">⚠️ '+esc(d.msg)+'</span>';return;}
    res.innerHTML='<span class="ok">✅ 端口 '+esc(d.port)+' 有效</span><br>持有人：<b>'+
      esc(d.name)+'</b>（'+esc(d.phone_mask)+' · '+esc(d.channel_name)+'）<br>'+
      (d.won?'中奖状态：<span class="ok">已中奖（'+esc(d.won_time)+'）</span>，可兑奖':'中奖状态：未中奖')+
      '<br><span style="color:#7fb3e0">兑奖前请让领奖人报出姓名或手机尾号核对</span>';
  });
}
refreshWinners();setInterval(refreshWinners,5000);
</script></body></html>"""
class Handler(BaseHTTPRequestHandler):
    def _send(self, html, status=200, ctype="text/html; charset=utf-8"):
        data = html.encode("utf-8") if isinstance(html, str) else html
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
    def _json(self, obj, status=200):
        self._send(json.dumps(obj, ensure_ascii=False), status=status,
                   ctype="application/json; charset=utf-8")
    def log_message(self, fmt, *args):
        pass
    def do_GET(self):
        path = urlparse(self.path).path
        base = get_base_url()
        if path == "/":
            self._send(SIGNIN_PAGE.replace("__CSS__", PAGE_CSS))
        elif path == "/admin":
            self._send(ADMIN_PAGE.replace("__CSS__", PAGE_CSS)
                       .replace("__SIGNIN_URL__", base + "/")
                       .replace("__LOTTERY_URL__", base + "/lottery"))
        elif path == "/lottery":
            self._send(LOTTERY_PAGE)
        elif path == "/api/admin_data":
            self._json(admin_data())
        elif path == "/api/winners":
            with _lock:
                lst = [dict(w, channel_name=CHANNELS.get(w["channel"], w["channel"] or "未知"),
                            round_name=ROUND_NAMES.get(w.get("round", ""), ""))
                       for w in WINNERS]
                today = datetime.now().strftime("%Y-%m-%d")
                today_signed = sum(1 for ph, r in SIGNED.items() if r.get("date") == today)
                pool = len(eligible_pool())
            self._json({"list": lst, "today_signed": today_signed, "pool": pool,
                        "round": SESSION["round"],
                        "round_name": ROUND_NAMES.get(SESSION["round"], ""),
                        "cutoff": SESSION.get("cutoff", "")})
        elif path == "/template":
            self._send_template()
        elif path == "/export":
            self.export_excel()
        else:
            self._send("<h3>404</h3>", status=404)
    def do_POST(self):
        path = urlparse(self.path).path
        if path == "/api/upload":
            self.handle_upload(); return
        length = int(self.headers.get("Content-Length", 0))
        body = parse_qs(self.rfile.read(length).decode("utf-8"))
        q = (body.get("q") or [""])[0].strip()
        if path == "/api/lookup":
            with _lock:
                a = find_attendee(q)
                if not a:
                    msg = "未找到票务信息"
                    if len(q) == 4:
                        tail = [x for x in ATTENDEES if x["phone"].endswith(q)]
                        if len(tail) > 1:
                            msg = "该尾号对应多位参会人，请输入完整手机号"
                    self._json({"found": False, "msg": msg}); return
                rec = SIGNED.get(a["phone"])
                self._json({"found": True, "name": a["name"], "phone": a["phone"],
                            "channel_name": CHANNELS.get(a["channel"], a["channel"] or "未知"),
                            "ticket": a["ticket"], "signed": bool(rec),
                            "signed_time": rec["time"] if rec else "",
                            "port": PORTS.get(a["phone"], "")})
        elif path == "/api/search_name":
            with _lock:
                cands = search_by_name(q)
                out = [{"name": c["name"], "phone": c["phone"],
                        "phone_mask": mask_phone(c["phone"]),
                        "channel_name": CHANNELS.get(c["channel"], c["channel"] or "未知")}
                       for c in cands[:20]]
            self._json({"list": out})
        elif path == "/api/sign":
            with _lock:
                a = find_attendee(q)
                if not a:
                    self._json({"ok": False, "msg": "未找到票务信息"}); return
                now = datetime.now()
                rec = SIGNED.get(a["phone"])
                if rec:
                    msg = "该手机号已签到（%s）" % rec["time"]
                else:
                    SIGNED[a["phone"]] = {"time": now.strftime("%H:%M:%S"),
                                          "date": now.strftime("%Y-%m-%d")}
                    _save_json(SIGNED_PATH, SIGNED); msg = ""
                port = assign_port(a["phone"])
                self._json({"ok": True, "name": a["name"],
                            "channel_name": CHANNELS.get(a["channel"], a["channel"] or "未知"),
                            "time": SIGNED[a["phone"]]["time"], "msg": msg, "port": port})
        elif path == "/api/set_round":
            with _lock:
                SESSION["round"] = q if q in ROUND_NAMES else "all"
                _save_json(SESSION_PATH, SESSION)
            self._json({"ok": True})
        elif path == "/api/set_cutoff":
            with _lock:
                SESSION["cutoff"] = q
                _save_json(SESSION_PATH, SESSION)
            self._json({"ok": True})
        elif path == "/api/draw":
            with _lock:
                pool = eligible_pool()
                if not pool:
                    if not any(r.get("date") == datetime.now().strftime("%Y-%m-%d") for r in SIGNED.values()):
                        self._json({"ok": False, "msg": "今日还没有人签到，没有抽奖号"}); return
                    self._json({"ok": False, "msg": "本场可抽奖号已抽完（或未到截止时间）"}); return
                port, phone = random.choice(pool)
                a = next((x for x in ATTENDEES if x["phone"] == phone), None)
                name = a["name"] if a else ""; ch = a["channel"] if a else ""
                rec = {"phone": phone, "port": port, "name": name, "channel": ch,
                       "time": datetime.now().strftime("%H:%M:%S"),
                       "date": datetime.now().strftime("%Y-%m-%d"),
                       "round": SESSION["round"]}
                WINNERS.append(rec); _save_json(WINNERS_PATH, WINNERS)
            self._json({"ok": True, "port": port, "name": name,
                        "channel_name": CHANNELS.get(ch, ch or "未知"), "time": rec["time"]})
        elif path == "/api/draw_undo":
            with _lock:
                idx = next((i for i, w in enumerate(WINNERS) if w["phone"] == q), None)
                if idx is None:
                    self._json({"ok": False, "msg": "未找到该中奖记录"}); return
                removed = WINNERS.pop(idx); _save_json(WINNERS_PATH, WINNERS)
            self._json({"ok": True, "msg": "已作废端口 %s" % removed["port"]})
        elif path == "/api/draw_reset":
            with _lock:
                WINNERS.clear(); _save_json(WINNERS_PATH, WINNERS)
            self._json({"ok": True})
        elif path == "/api/verify_port":
            with _lock:
                holder = next((a for a in ATTENDEES if str(PORTS.get(a["phone"], "")) == q), None)
                if not holder:
                    self._json({"ok": False, "msg": "该端口号未发放，可能为伪造截图"}); return
                won = next((w for w in WINNERS if w["phone"] == holder["phone"]), None)
                self._json({"ok": True, "name": holder["name"],
                            "phone_mask": mask_phone(holder["phone"]),
                            "channel_name": CHANNELS.get(holder["channel"], holder["channel"] or "未知"),
                            "port": PORTS[holder["phone"]],
                            "won": bool(won), "won_time": won["time"] if won else ""})
        else:
            self._send("<h3>404</h3>", status=404)
    def handle_upload(self):
        ctype = self.headers.get("Content-Type", "")
        if "multipart/form-data" not in ctype:
            self._json({"ok": False, "msg": "请求格式错误"}); return
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length)
        boundary = ctype.split("boundary=")[-1].strip().strip('"')
        parts = raw.split(("--" + boundary).encode())
        filename = None; content = None; mode = "append"
        for part in parts:
            if not part or part in (b"--\r\n", b"--") or b"\r\n\r\n" not in part:
                continue
            head, _, body = part.partition(b"\r\n\r\n")
            body = body.rstrip(b"\r\n")
            head_text = head.decode("utf-8", "ignore")
            if 'name="file"' in head_text:
                for line in head_text.split("\r\n"):
                    if "filename=" in line:
                        filename = line.split("filename=")[-1].strip().strip('"')
                content = body
            elif 'name="mode"' in head_text:
                mode = body.decode("utf-8", "ignore").strip() or "append"
        if not filename or content is None:
            self._json({"ok": False, "msg": "未收到文件"}); return
        rows, error = parse_upload(filename, content)
        if error:
            self._json({"ok": False, "msg": error}); return
        if not rows:
            self._json({"ok": False, "msg": "文件中没有有效数据（需含手机号列）"}); return
        with _lock:
            added, updated, skipped, total = merge_rows(rows, mode)
        self._json({"ok": True, "added": added, "updated": updated,
                    "skipped": skipped, "total": total})
    def _send_template(self):
        try:
            from openpyxl import Workbook
            from openpyxl.styles import Font, PatternFill, Alignment
        except ImportError:
            self._send("<h3>需要 openpyxl</h3>", status=500); return
        wb = Workbook(); ws = wb.active; ws.title = "票务名单"
        fill = PatternFill("solid", fgColor="1F4E79")
        for i, htext in enumerate(["姓名", "手机号", "渠道标识", "票种"], 1):
            c = ws.cell(row=1, column=i, value=htext)
            c.font = Font(bold=True, color="FFFFFF"); c.fill = fill
            c.alignment = Alignment(horizontal="center")
        for ri, row in enumerate([["张三", "13800000001", "S", "散客票"],
                                  ["李四", "13800000002", "T", "合作渠道票"]], 2):
            for ci, v in enumerate(row, 1):
                ws.cell(row=ri, column=ci, value=v)
        buf = io.BytesIO(); wb.save(buf); data = buf.getvalue()
        self.send_response(200)
        self.send_header("Content-Type",
                         "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        self.send_header("Content-Disposition", "attachment; filename=template.xlsx")
        self.send_header("Content-Length", str(len(data))); self.end_headers()
        self.wfile.write(data)
    def export_excel(self):
        try:
            from openpyxl import Workbook
            from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
        except ImportError:
            self._send("<h3>导出需要 openpyxl</h3>", status=500); return
        wb = Workbook(); ws = wb.active; ws.title = "签到记录"
        thin = Side(style="thin", color="BFBFBF")
        border = Border(left=thin, right=thin, top=thin, bottom=thin)
        fill = PatternFill("solid", fgColor="1F4E79")
        for i, htext in enumerate(["姓名", "手机号", "渠道", "票种", "抽奖端口号",
                                   "是否签到", "签到日期", "签到时间", "是否中奖"], 1):
            c = ws.cell(row=1, column=i, value=htext)
            c.font = Font(bold=True, color="FFFFFF", size=11); c.fill = fill
            c.alignment = Alignment(horizontal="center"); c.border = border
        won_map = {w["phone"]: w["time"] for w in WINNERS}
        with _lock:
            rows = sorted(ATTENDEES, key=lambda x: (x["phone"] not in SIGNED, x["phone"]))
            for ri, a in enumerate(rows, 2):
                rec = SIGNED.get(a["phone"])
                vals = [a["name"], a["phone"], CHANNELS.get(a["channel"], a["channel"]),
                        a["ticket"], PORTS.get(a["phone"], ""),
                        "已签到" if rec else "未签到",
                        rec["date"] if rec else "", rec["time"] if rec else "",
                        "中奖" if a["phone"] in won_map else ""]
                for ci, v in enumerate(vals, 1):
                    c = ws.cell(row=ri, column=ci, value=v); c.border = border
        for i, w in enumerate([14, 16, 12, 14, 12, 10, 12, 12, 10], 1):
            ws.column_dimensions[chr(64 + i)].width = w
        buf = io.BytesIO(); wb.save(buf); data = buf.getvalue()
        self.send_response(200)
        self.send_header("Content-Type",
                         "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        self.send_header("Content-Disposition", "attachment; filename=checkin.xlsx")
        self.send_header("Content-Length", str(len(data))); self.end_headers()
        self.wfile.write(data)
ROUND_NAMES = {"morning": "上午场", "afternoon": "下午场", "all": "全天"}
def admin_data():
    with _lock:
        total = len(ATTENDEES)
        signed_count = sum(1 for a in ATTENDEES if a["phone"] in SIGNED)
        ch_stats = {}
        for a in ATTENDEES:
            key = a["channel"] or "?"
            st = ch_stats.setdefault(key, {"name": CHANNELS.get(key, key or "未知"),
                                           "total": 0, "signed": 0})
            st["total"] += 1
            if a["phone"] in SIGNED:
                st["signed"] += 1
        lst = []
        for a in ATTENDEES:
            rec = SIGNED.get(a["phone"])
            lst.append({"name": a["name"], "phone_mask": mask_phone(a["phone"]),
                        "channel_name": CHANNELS.get(a["channel"], a["channel"] or "未知"),
                        "ticket": a["ticket"], "signed": bool(rec),
                        "port": PORTS.get(a["phone"], ""),
                        "signed_time": rec["time"] if rec else ""})
        lst.sort(key=lambda x: (not x["signed"], x["name"]))
    return {"total": total, "signed_count": signed_count,
            "unsign": total - signed_count,
            "rate": round(signed_count * 100 / total) if total else 0,
            "channels": sorted(ch_stats.values(), key=lambda c: -c["total"]),
            "list": lst}
def get_base_url():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80)); ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return "http://%s:%d" % (ip, getattr(get_base_url, "_port", 8000))
def main():
    port = 8000
    if "--port" in sys.argv:
        port = int(sys.argv[sys.argv.index("--port") + 1])
    get_base_url._port = port
    global ATTENDEES, SIGNED, PORTS, WINNERS, SESSION
    ATTENDEES = load_attendees()
    SIGNED = _load_json(SIGNED_PATH, {})
    PORTS = _load_json(PORTS_PATH, {})
    WINNERS = _load_json(WINNERS_PATH, [])
    SESSION = _load_json(SESSION_PATH, {"round": "morning", "cutoff": ""})
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print("=" * 46)
    print("KCon 2026 自助签到+抽奖 v5 已启动")
    print("观众签到页:  http://0.0.0.0:%d/" % port)
    print("管理台:      http://0.0.0.0:%d/admin" % port)
    print("抽奖页面:    http://0.0.0.0:%d/lottery" % port)
    print("本机访问:    http://127.0.0.1:%d/" % port)
    print("已导入票务总表: %d 人 | 今日已签到: %d 人" % (len(ATTENDEES), len(SIGNED)))
    print("按 Ctrl+C 停止")
    print("=" * 46)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")
if __name__ == "__main__":
    main()