#!/usr/bin/env python3
"""核对 requirements.yaml 每条 REQ 的 text 是否真是 source 那一行（附近）的原文。

「text 原文照抄」是 REQ 清单的铁律，但照抄最容易在手写时走样（漏字、顺手润色）。
这里做机器核对：去掉空白与 markdown 表格线后，text 必须是 source 文件的子串，
且 source 标的行号 ±2 行内能找到 text 的开头 12 个字。

用法（cwd = 项目根）：python3 doc/tools/check_req_sources.py
退出码：0 全部对得上 / 1 有对不上的。
来源是设计回流（source 以 design-options/ 开头）的 REQ 不核对行号，只核对文件存在。
"""
import argparse
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
REQ = ROOT / "doc" / "requirements.yaml"
# 原文是跨多行的 Word 表格、在 REQ 里压成一行的条目：只核对每个单元格文字都出现在源文件里
TABLE_COMPRESSED = {"REQ-QC-001", "REQ-QC-005", "REQ-QC-007"}


def norm(s: str) -> str:
    s = re.sub(r"[\s　|`*]+", "", s)
    return s.replace("…", "")


def main() -> int:
    data = yaml.safe_load(REQ.read_text(encoding="utf-8"))
    bad = 0
    for r in data["requirements"]:
        rid, src, text = r["id"], r["source"], (r.get("text") or "").strip()
        fname, _, anchor = src.partition("#")
        path = ROOT / "_input" / fname
        if not path.exists():
            path = ROOT / fname          # 设计回流：相对项目根
        if not path.exists():
            print(f"❌ {rid}: source 文件不存在 {fname}")
            bad += 1
            continue
        if not anchor.startswith("L"):
            continue
        lines = path.read_text(encoding="utf-8").splitlines()
        n = int(re.match(r"L(\d+)", anchor).group(1))
        if n > len(lines):
            print(f"❌ {rid}: {fname} 只有 {len(lines)} 行，标了 L{n}")
            bad += 1
            continue
        whole = norm("\n".join(lines))
        near = norm("\n".join(lines[max(0, n - 3): n + 2]))
        if rid in TABLE_COMPRESSED:
            cells = [c for c in re.split(r"[|/；;]", text) if norm(c)]
            miss = [c.strip() for c in cells if norm(c) not in whole]
            if miss:
                print(f"❌ {rid}: 这些单元格文字在源文件里找不到 → {miss[:4]}")
                bad += 1
            continue
        t = norm(text)
        if t not in whole:
            # 找出第一个对不上的位置，方便改
            k = 0
            while k < len(t) and t[: k + 1] in whole:
                k += 1
            print(f"❌ {rid}: text 不是 {fname} 的原文；从这里开始对不上 → 「{t[max(0,k-8):k+12]}」")
            bad += 1
        elif t[:12] not in near:
            print(f"❌ {rid}: 原文在文件里，但不在 L{n}±2 行内（行号标错）")
            bad += 1
    total = len(data["requirements"])
    print(f"\n核对 {total} 条，对不上 {bad} 条")
    return 1 if bad else 0


if __name__ == "__main__":
    # 只加 --help：不带参数时行为与改动前完全一致（直接执行核对）。
    argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    ).parse_args()
    sys.exit(main())
