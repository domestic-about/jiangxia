#!/usr/bin/env python3
"""蓝图内部互相对账（authority_lint check 只查 ticket → 权威，不查权威之间）。

查四件事：
  X1 flows 的 steps[].writes 指向的 FIELD: 锚真的在 field-ssot.yaml 里
  X2 flows / ui-index 的 related_reqs 指向的 REQ 真的在 requirements.yaml 里，且不是 deferred / dropped
  X3 每条在范围内的 REQ 至少被一条 FLOW 或 UI 记录引用（蓝图没接住的需求 = 拆 ticket 时一定漏）
  X4 每个业务字段至少被一个流程步骤写到（没人写的字段 = 模型里的死字段，或流程漏了一步）
     —— 主键 id、纯外键（*_id 且 comment 以 FK 开头）、sort 不查
  X5 ui-index 的 prototype 若指向 gallery.html#锚点，该锚点必须真的在 gallery.html 里（防指到不存在的草案帧）
用法（cwd = 项目根）：python3 doc/tools/check_authority_xref.py
退出码：0 全过 / 1 有 error（X1 X2 X3）/ 2 仅 warning（X4）
"""
import argparse
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "doc" / "authority"


def load(p):
    return yaml.safe_load(p.read_text(encoding="utf-8"))


def main() -> int:
    reqs = {r["id"]: r for r in load(ROOT / "doc" / "requirements.yaml")["requirements"]}
    in_scope = {k for k, r in reqs.items() if r.get("status") not in ("deferred", "dropped")}
    ssot = load(A / "field-ssot.yaml")
    fields = {}
    for t in ssot["tables"]:
        for f in t["fields"]:
            fields[f"FIELD:{t['table']}.{f['name']}"] = f
    errors, warnings = [], []
    written, req_hit = set(), set()
    gallery = ROOT / "doc" / "design-options" / "gallery.html"
    gallery_ids = set(re.findall(r'id="([^"]+)"', gallery.read_text(encoding="utf-8"))) if gallery.exists() else set()
    for fn in ("flows.yaml", "ui-index.yaml"):
        for rec in load(A / fn)["records"]:
            m = re.match(r"doc/design-options/gallery\.html#([\w-]+)$", str(rec.get("prototype") or ""))
            if m and m.group(1) not in gallery_ids:
                errors.append(f"X5 {rec['id']}: prototype 锚点 #{m.group(1)} 不在 gallery.html 里")
            for rid in rec.get("related_reqs") or []:
                if rid not in reqs:
                    errors.append(f"X2 {rec['id']}: related_reqs 指向不存在的 {rid}")
                elif rid not in in_scope:
                    errors.append(f"X2 {rec['id']}: related_reqs 指向本期不做的 {rid}")
                else:
                    req_hit.add(rid)
            for st in rec.get("steps") or []:
                for w in st.get("writes") or []:
                    if w not in fields:
                        errors.append(f"X1 {st['id']}: writes 指向不存在的 {w}")
                    written.add(w)
    for rid in sorted(in_scope - req_hit):
        errors.append(f"X3 {rid}（{reqs[rid]['module']}）没有被任何 FLOW / UI 记录引用")
    for aid, f in fields.items():
        name = f["name"]
        if name in ("id", "sort") or aid in written:
            continue
        if name.endswith("_id") and str(f.get("comment", "")).startswith("FK"):
            continue
        warnings.append(f"X4 {aid} 没有任何流程步骤写它")
    print(f"REQ 在范围内 {len(in_scope)} 条 · 被蓝图引用 {len(req_hit)} 条 · 业务字段 {len(fields)} 个 · 有步骤写入 {len(written)} 个")
    for e in errors:
        print("❌", e)
    for w in warnings:
        print("🟡", w)
    if not errors and not warnings:
        print("✅ 蓝图内部互相对得上")
    return 1 if errors else (2 if warnings else 0)


if __name__ == "__main__":
    # 只加 --help：不带参数时行为与改动前完全一致（直接执行对账）。
    argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    ).parse_args()
    sys.exit(main())
