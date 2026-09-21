#!/usr/bin/env python3
"""OCR-SPIKE-001 的验收执行器：**报告里的数字** vs **逐张逐字段的原始记录**，两侧不同源。

  python3 doc/verify/ocr_spike_check.py            # 读 doc/ocr-spike/results.csv 与 doc/ocr-spike/report.md

results.csv 表头固定：photo,kind,handwritten,candidate,field,expected,got,hit
  · kind ∈ tube(管壁) / bag(样本袋) / form(送检单) / screenshot(截图)；handwritten ∈ Y / N；hit ∈ 1 / 0
  · field ∈ donorName / gender / age / hospitalNo / tissueType / sourceUnitName
report.md 里必须有一段机器可读的汇总表，夹在 <!-- summary:begin --> 与 <!-- summary:end --> 之间：
  | 方案 | 是否按次收费 | 照片数 | 字段命中率 | 手写字段命中率 | 单次成本(元) |
  以及一段价格出处，夹在 <!-- pricing:begin --> / <!-- pricing:end --> 之间，每个方案一行，含「查询日期」与一个 http(s) 链接。

判据（任何一条不满足 → exit 1）：
  1 真实照片 ≥ 10 张，且四种 kind 至少出现三种，手写样本 ≥ 3 张
  2 候选方案 ≥ 3 个，其中「不按次收费」≥ 2 个、「按次收费」≥ 1 个（合同：先对比不按次收费的，再给出 AI 的效果对比与费用测算）
  3 每个方案都对每张照片测了全部 6 个字段里它「本应出现」的那些（expected 非空的行不许缺）
  4 汇总表里的两列命中率，与从 results.csv 重新算出来的相差 ≤ 0.5 个百分点
  5 按次收费的方案，单次成本不为空且价格出处里有它的查询日期与链接（价格现查，不凭记忆写）
"""
import argparse
import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CSV = ROOT / "doc" / "ocr-spike" / "results.csv"
REPORT = ROOT / "doc" / "ocr-spike" / "report.md"
FIELDS = {"donorName", "gender", "age", "hospitalNo", "tissueType", "sourceUnitName"}


def pct(s):
    return float(str(s).strip().rstrip("%"))


def main():
    if not CSV.exists() or not REPORT.exists():
        sys.stderr.write("[error] 缺 doc/ocr-spike/results.csv 或 report.md\n")
        return 2
    rows = list(csv.DictReader(CSV.open(encoding="utf-8-sig")))
    bad = []
    photos = {r["photo"]: r for r in rows}
    kinds = {r["kind"] for r in rows}
    hand = {r["photo"] for r in rows if r["handwritten"] == "Y"}
    if len(photos) < 10:
        bad.append(f"照片只有 {len(photos)} 张（≥ 10）")
    if len(kinds & {"tube", "bag", "form", "screenshot"}) < 3:
        bad.append(f"照片种类只有 {sorted(kinds)}（管壁 / 袋子 / 送检单 / 截图至少三种）")
    if len(hand) < 3:
        bad.append(f"手写样本只有 {len(hand)} 张（≥ 3）——手写是这件事的难点，没有手写样本的结论不算数")
    if {r["field"] for r in rows} - FIELDS:
        bad.append(f"出现了不认识的 field：{sorted({r['field'] for r in rows} - FIELDS)}")
    report = REPORT.read_text(encoding="utf-8")
    m = re.search(r"<!-- summary:begin -->(.*?)<!-- summary:end -->", report, re.S)
    p = re.search(r"<!-- pricing:begin -->(.*?)<!-- pricing:end -->", report, re.S)
    if not m or not p:
        bad.append("report.md 缺 summary 或 pricing 标记段")
        print("\n".join("✗ " + b for b in bad))
        return 1
    table = [[c.strip() for c in ln.strip().strip("|").split("|")] for ln in m.group(1).strip().splitlines()
             if ln.strip().startswith("|") and not set(ln.strip()) <= set("|-: ")]
    body = table[1:]
    cands = {r["candidate"] for r in rows}
    if {b[0] for b in body} != cands:
        bad.append(f"汇总表的方案 {sorted(b[0] for b in body)} 与 results.csv 的 {sorted(cands)} 不一致")
    paid = [b for b in body if b[1] in ("是", "Y", "按次收费")]
    free = [b for b in body if b[1] in ("否", "N", "不按次收费")]
    if len(body) < 3 or len(free) < 2 or len(paid) < 1:
        bad.append(f"方案数不够：共 {len(body)}，不按次收费 {len(free)}（≥ 2），按次收费 {len(paid)}（≥ 1）")
    need = {(r["photo"], r["field"]) for r in rows if r["expected"].strip()}
    stat = defaultdict(lambda: [0, 0, 0, 0])
    for r in rows:
        if not r["expected"].strip():
            continue
        s = stat[r["candidate"]]
        s[0] += int(r["hit"]); s[1] += 1
        if r["handwritten"] == "Y":
            s[2] += int(r["hit"]); s[3] += 1
    for c in cands:
        got = {(r["photo"], r["field"]) for r in rows if r["candidate"] == c and r["expected"].strip()}
        if need - got:
            bad.append(f"方案 {c} 漏测 {len(need - got)} 个（照片, 字段）组合")
    for b in body:
        s = stat.get(b[0])
        if not s or not s[1]:
            continue
        real, real_h = 100 * s[0] / s[1], (100 * s[2] / s[3] if s[3] else 0)
        if abs(pct(b[3]) - real) > 0.5:
            bad.append(f"方案 {b[0]} 字段命中率：报告写 {b[3]}，原始记录算出来 {real:.1f}%")
        if s[3] and abs(pct(b[4]) - real_h) > 0.5:
            bad.append(f"方案 {b[0]} 手写字段命中率：报告写 {b[4]}，原始记录算出来 {real_h:.1f}%")
    for b in paid:
        if not re.search(r"\d", b[5]):
            bad.append(f"按次收费的方案 {b[0]} 没写单次成本")
        line = next((ln for ln in p.group(1).splitlines() if b[0] in ln), "")
        if not (re.search(r"20\d\d-\d\d-\d\d", line) and re.search(r"https?://", line)):
            bad.append(f"方案 {b[0]} 的价格出处缺查询日期或链接——价格必须现查")
    if bad:
        print("\n".join("✗ " + b for b in bad))
        return 1
    print(f"✓ {len(photos)} 张照片 × {len(cands)} 个方案；汇总表与原始记录一致")
    return 0


if __name__ == "__main__":
    # 只加 --help：不带参数时行为与改动前完全一致（直接执行验收）。
    argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    ).parse_args()
    sys.exit(main())
