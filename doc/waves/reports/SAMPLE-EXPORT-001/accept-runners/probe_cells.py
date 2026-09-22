#!/usr/bin/env python3
"""SAMPLE-EXPORT-001 的 counterfeit 探针：把导出的**每一个格子**拿出来，逐条排掉 ticket 点名的造假形态。

  python3 probe_cells.py tissue /tmp/lqg-tissue.xlsx
  python3 probe_cells.py organoid /tmp/lqg-organoid.xlsx

与 xlsx_header.py 的分工：那个对表头与指定格；这里做**全表扫描**（有没有漏出 Y/N、male、
Base64 密文、软删行、另一类的行、模板没有的列）。退出码 0 = 全部排掉 / 1 = 命中。
"""
import base64
import binascii
import sys

import openpyxl

FAIL = []


def check(cond, label):
    print(("  ✓ " if cond else "  ✗ ") + label)
    if not cond:
        FAIL.append(label)


def load(path):
    ws = openpyxl.load_workbook(path, read_only=True, data_only=True).worksheets[0]
    rows = [[("" if c is None else str(c)) for c in r] for r in ws.iter_rows(values_only=True)]
    header = rows[0]
    while header and header[-1] == "":
        header.pop()
    data = [r for r in rows[1:] if any(c not in (None, "") for c in r)]
    return header, data


def looks_like_cipher(value):
    """裸 Base64 密文（AES/ECB 的 16 字节块 → 24 字符且以 = 结尾）。"""
    text = (value or "").strip()
    if len(text) < 16 or len(text) % 4 != 0:
        return False
    try:
        raw = base64.b64decode(text, validate=True)
    except (binascii.Error, ValueError):
        return False
    return len(raw) % 16 == 0


def main():
    kind, path = sys.argv[1], sys.argv[2]
    header, data = load(path)
    cells = [c for r in data for c in r]
    col = {name: i for i, name in enumerate(header)}

    print(f"== {kind}：{path}（{len(data)} 行 × {len(header)} 列）==")

    # ① 模板没有的列不许出现（导出 VO 不是列表 VO）
    banned_cols = ["送检单号", "核验状态", "有无病理", "提交人", "组别", "最后修改", "类别"]
    check(not [c for c in banned_cols if c in header],
          f"表头没有列表 VO 才有的列（{', '.join(banned_cols)}）")

    # ② 另一类的行不许混进来 / 软删行不许出现
    if kind == "tissue":
        check("T-oco01" not in " ".join(cells), "类器官那条（T-oco01）没有混进 tissue 导出")
        check("T-del99" not in " ".join(cells), "软删的 1010（T-del99）没有导出")
    else:
        check("T-hli01" not in " ".join(cells), "组织样本（T-hli01）没有混进 organoid 导出")

    # ③ 按钮列导「有 / 无」，不是 Y / N
    flag_cols = [c for c in ("有无固定", "质控表", "细胞活率报告") if c in col]
    flag_values = sorted({r[col[c]] for r in data for c in flag_cols if r[col[c]] != ""})
    check([v for v in flag_values if v not in ("有", "无")] == [], f"按钮列只有「有 / 无」：{flag_values}")
    # 待核验 / 无效的行这些格子还没填 → 必须留空（不是 "null" / "—" / "N"）
    blanks = sum(1 for r in data for c in flag_cols if r[col[c]] == "")
    if kind == "tissue":
        check(blanks > 0, f"没选的「有无」按钮留空（空格子数 {blanks}）")

    # ④ 性别导中文，不是 male / female
    gender_values = sorted({r[col["性别"]] for r in data if "性别" in col and r[col["性别"]] != ""})
    check([v for v in gender_values if v not in ("男", "女", "未知")] == [], f"性别列只有 男/女/未知：{gender_values}")

    # ⑤ 加密列是明文，不是裸 Base64 密文
    for name in ("供体姓名", "住院号"):
        if name not in col:
            continue
        values = [r[col[name]] for r in data if r[col[name]] != ""]
        cipher = [v for v in values if looks_like_cipher(v)]
        check(not cipher, f"「{name}」是明文（没有裸 Base64 密文）：{values[:3]}…")

    # ⑥ 日期格格式
    if "收样日期" in col:
        bad = [r[col["收样日期"]] for r in data if r[col["收样日期"]] and len(r[col["收样日期"]]) != 10]
        check(not bad, f"收样日期是 yyyy-MM-dd：{bad[:3]}")
    if "处理时间" in col:
        bad = [r[col["处理时间"]] for r in data
               if r[col["处理时间"]] and len(r[col["处理时间"]]) != 19]
        check(not bad, f"处理时间是 yyyy-MM-dd HH:mm:ss：{bad[:3]}")

    print("PROBE " + ("PASS" if not FAIL else "FAIL：" + "; ".join(FAIL)))
    return 1 if FAIL else 0


if __name__ == "__main__":
    sys.exit(main())
