#!/usr/bin/env python3
"""导出已审批接收人名单（FR-G01/G02），交付下游 KconLucky 系统导入。

用法：
    python3 scripts/export_recipients.py \
        --db data/kcon.db \
        --out recipients_export.csv \
        --conflicts recipients_conflicts.csv

规则：
- 仅导出 recipient.status=2（已审批通过）的接收人；
- 导出列（utf-8-sig）：姓名,手机号,渠道来源,公司,职位,邮箱；职位恒为空；
- 按手机号去重（strip 后比对）：保留 approved_at/created_at（兜底 id）最新的一条，
  其余写入冲突表；手机号为空的行写入冲突表（原因=缺少手机号）且不导出；
- 空邮箱允许导出（下游导入模板邮箱选填）；
- 冲突表列：手机号,姓名,邮箱,渠道来源,推荐人,状态,原因。
"""
import argparse
import csv
import sqlite3
import sys
from pathlib import Path
QUERY = """
SELECT rc.id, rc.name, rc.mobile, rc.email, rc.company, rc.status,
       rc.approved_at, rc.created_at,
       c.name AS channel_name, r.name AS referrer_name
FROM recipient rc
JOIN referrer r ON r.id = rc.referrer_id
JOIN channel c ON c.id = rc.channel_id
WHERE rc.status = 2
ORDER BY rc.id
"""
EXPORT_HEADERS = ["姓名", "手机号", "渠道来源", "公司", "职位", "邮箱"]
CONFLICT_HEADERS = ["手机号", "姓名", "邮箱", "渠道来源", "推荐人", "状态", "原因"]
STATUS_TEXT = {2: "已审批", 4: "已签到"}
def _sort_key(row: dict) -> tuple:
    """去重排序键：approved_at 优先，其次 created_at，最后 id。"""
    return (row["approved_at"] or row["created_at"] or "", row["id"])
def export(db_path: str, out_path: str, conflicts_path: str,
           stdout: bool = False) -> dict:
    """执行导出，返回统计摘要。"""
    db_file = Path(db_path)
    if not db_file.exists():
        print(f"错误：数据库不存在：{db_path}", file=sys.stderr)
        sys.exit(1)
    conn = sqlite3.connect(str(db_file))
    conn.row_factory = sqlite3.Row
    try:
        rows = [dict(r) for r in conn.execute(QUERY)]
    finally:
        conn.close()
    # 分组：缺手机号 / 按手机号分组
    missing_phone: list = []
    by_mobile: dict = {}
    for row in rows:
        mobile = (row["mobile"] or "").strip()
        if not mobile:
            missing_phone.append(row)
        else:
            by_mobile.setdefault(mobile, []).append(row)
    export_rows: list = []
    conflict_rows: list = []
    for row in missing_phone:
        conflict_rows.append([
            "", row["name"], row["email"], row["channel_name"],
            row["referrer_name"], STATUS_TEXT.get(row["status"], str(row["status"])),
            "缺少手机号",
        ])
    for mobile, group in by_mobile.items():
        if len(group) > 1:
            keep = max(group, key=_sort_key)
            for dup in group:
                if dup["id"] == keep["id"]:
                    continue
                conflict_rows.append([
                    mobile, dup["name"], dup["email"], dup["channel_name"],
                    dup["referrer_name"],
                    STATUS_TEXT.get(dup["status"], str(dup["status"])),
                    "重复手机号",
                ])
        else:
            keep = group[0]
        export_rows.append([
            keep["name"], mobile, keep["channel_name"], keep["company"], "",
            keep["email"],
        ])
    out_file = Path(out_path)
    out_file.parent.mkdir(parents=True, exist_ok=True)
    with open(out_file, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(EXPORT_HEADERS)
        writer.writerows(export_rows)
    conflicts_file = Path(conflicts_path)
    conflicts_file.parent.mkdir(parents=True, exist_ok=True)
    with open(conflicts_file, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(CONFLICT_HEADERS)
        writer.writerows(conflict_rows)
    if stdout:
        f = _stringify_rows(export_rows)
        sys.stdout.write(f)
    summary = {
        "approved_total": len(rows),
        "exported": len(export_rows),
        "duplicate_conflicts": sum(1 for r in conflict_rows if r[-1] == "重复手机号"),
        "missing_phone": len(missing_phone),
        "unique_phones": len(by_mobile),
    }
    return summary
def _stringify_rows(rows: list) -> str:
    """将导出行渲染为 CSV 文本（stdout 模式）。"""
    import io
    buf = io.StringIO()
    writer = csv.writer(buf)
    writer.writerow(EXPORT_HEADERS)
    writer.writerows(rows)
    return buf.getvalue()
def main() -> None:
    parser = argparse.ArgumentParser(description="导出已审批接收人名单（交付下游 KconLucky）")
    parser.add_argument("--db", default="data/kcon.db", help="SQLite 数据库路径（默认 data/kcon.db）")
    parser.add_argument("--out", default="recipients_export.csv", help="导出 CSV 路径（默认 recipients_export.csv）")
    parser.add_argument("--conflicts", default="recipients_conflicts.csv", help="冲突 CSV 路径（默认 recipients_conflicts.csv）")
    parser.add_argument("--stdout", action="store_true", help="同时将导出 CSV 打印到标准输出")
    args = parser.parse_args()
    summary = export(args.db, args.out, args.conflicts, stdout=args.stdout)
    print(f"已审批接收人总数：{summary['approved_total']}")
    print(f"导出：{summary['exported']} 条 → {args.out}")
    print(f"重复手机号冲突：{summary['duplicate_conflicts']} 条（明细见 {args.conflicts}）")
    print(f"缺少手机号：{summary['missing_phone']} 条（不导出，明细见 {args.conflicts}）")
    print(f"唯一手机号：{summary['unique_phones']} 个")
if __name__ == "__main__":
    main()
