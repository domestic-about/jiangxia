#!/usr/bin/env python3
"""导出 Excel 的对账执行器：**导出文件** vs **甲方模板原件**，两侧不同源。

  python3 doc/verify/xlsx_header.py --file /tmp/out.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" \
      [--insert "代数@类器官类型"] [--extra "代数,当前剩余/支"] [--rows 3] [--find "石蜡块编号=T-E01-1" --expect "染色=HE染色、IHC染色,样本编号=T-hli01"]

  · 表头：导出文件第 1 行必须**逐字、按序**等于模板第 1 行（--extra 是允许追加在模板列之后的列，也必须逐字按序）
  · --insert 列@锚点列：甲方后来要求加、模板原件里没有的列，插在模板的「锚点列」后面（多个用逗号隔开，
    按给出的顺序依次插）。例：类器官收样记录 --insert "代数@类器官类型"（甲方 2026-09-24 第 18 行，CR-20260924-10）。
    期望表头 = 模板第 1 行 → 依次插入 --insert → 末尾追加 --extra；锚点列不在模板里 → 退出码 2（用法错）
  · --rows N：数据行数（不含表头）必须等于 N
  · --find 列=值 --expect 列=值,列=值：找到那一行，核对若干单元格（断「有 / 无」这类标签映射、加密列是否导出成明文）
  · --print-header：只打印模板第 1 行（一列一行），不需要 --file。用来把 fixture 里抄的表头与甲方原件 diff（SAMPLE-MP-002）
退出码：0 相符 / 1 不相符 / 2 用法错或文件打不开。
"""
import argparse
import sys

import openpyxl


def first_row(path):
    ws = openpyxl.load_workbook(path, read_only=True, data_only=True).worksheets[0]
    rows = list(ws.iter_rows(values_only=True))
    header = [("" if c is None else str(c)) for c in rows[0]] if rows else []
    while header and header[-1] == "":
        header.pop()
    data = [r for r in rows[1:] if any(c not in (None, "") for c in r)]
    return header, data


def apply_inserts(header, spec):
    """--insert "列@锚点列,列@锚点列"：把每一列插到它的锚点列后面（锚点列必须已在表头里）。"""
    out = list(header)
    for part in (spec or "").split(","):
        if not part.strip():
            continue
        col, sep, after = part.partition("@")
        col, after = col.strip(), after.strip()
        if not sep or not col or not after:
            raise ValueError(f"--insert 的写法是「列@锚点列」：{part!r}")
        if after not in out:
            raise ValueError(f"--insert 的锚点列「{after}」不在模板表头里：{out}")
        out.insert(out.index(after) + 1, col)
    return out


def kv(s):
    out = {}
    for part in (s or "").split(","):
        if part.strip():
            k, _, v = part.partition("=")
            out[k.strip()] = v.strip()
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--file")
    ap.add_argument("--template", required=True)
    ap.add_argument("--insert", default="")
    ap.add_argument("--extra", default="")
    ap.add_argument("--rows", type=int)
    ap.add_argument("--find")
    ap.add_argument("--expect")
    ap.add_argument("--print-header", action="store_true")
    a = ap.parse_args()
    if a.print_header:
        try:
            want, _ = first_row(a.template)
        except Exception as e:  # noqa: BLE001
            sys.stderr.write(f"[error] 打不开文件：{e}\n")
            return 2
        print("\n".join(want))
        return 0
    if not a.file:
        sys.stderr.write("[error] 缺 --file（只有 --print-header 可以不带）\n")
        return 2
    try:
        got, data = first_row(a.file)
        want, _ = first_row(a.template)
    except Exception as e:  # noqa: BLE001
        sys.stderr.write(f"[error] 打不开文件：{e}\n")
        return 2
    try:
        want = apply_inserts(want, a.insert)
    except ValueError as e:
        sys.stderr.write(f"[error] {e}\n")
        return 2
    want = want + [x.strip() for x in a.extra.split(",") if x.strip()]
    bad = []
    if got != want:
        bad.append(f"表头不一致\n    期望 {want}\n    实际 {got}")
    if a.rows is not None and len(data) != a.rows:
        bad.append(f"数据行数 期望 {a.rows} 实际 {len(data)}")
    if a.find:
        (fk, fv), = kv(a.find).items()
        if fk not in got:
            bad.append(f"--find 的列「{fk}」不在表头里")
        else:
            hit = [r for r in data if str(r[got.index(fk)] if r[got.index(fk)] is not None else "") == fv]
            if len(hit) != 1:
                bad.append(f"按 {fk}={fv} 找到 {len(hit)} 行（应恰好 1 行）")
            else:
                for k, v in kv(a.expect).items():
                    cell = hit[0][got.index(k)] if k in got else None
                    if str("" if cell is None else cell) != v:
                        bad.append(f"{fk}={fv} 那一行的「{k}」期望 {v!r} 实际 {cell!r}")
    if bad:
        print("\n".join("✗ " + b for b in bad))
        return 1
    print(f"✓ 表头 {len(got)} 列与模板逐字一致；数据 {len(data)} 行")
    return 0


if __name__ == "__main__":
    sys.exit(main())
