#!/usr/bin/env python3
"""按「能不能与别的票并行跑」给 ticket 分类（Kevin 2026-09-22 选定的 B 方案：只并行纯前端票）。

    python3 doc/waves/tools/classify-parallel.py            # 全部未完成票
    python3 doc/waves/tools/classify-parallel.py --all      # 含已完成的

判据只认 ticket front-matter 的 `accept` 块（验收定义），**不认正文**：

  needs_db    = accept 里出现 reseed.sh / api.sh / db.py / ddl_vs_ssot.py
                 → 会改库或连后端；两张这种票并行必踩 reseed 窗口（D1 实测出了孤儿账号）
  needs_maven = accept 里出现 mvn
                 → 会写 code/RuoYi-Vue-Plus/**/target/；同 worktree 并发构建会互相覆盖，
                   而且与「另一个 agent 正跑着的后端 jar」抢同一份产物

**可并行（PARALLEL-SAFE）= 两者都不需要**。典型就是「小程序/plus-ui 只跑 pnpm build + vitest
fixture」的票：它们的 pnpm 各自在自己的目录（code/miniapp 与 code/plus-ui），互不写对方。

真正的硬约束仍然成立：**同一时刻只允许一张票碰 PG 5433 与 8081**。所以并行只发生在
「一张 DB-free 票 + 一张要 DB 的票」之间，而不是两张都要 DB。
"""
import argparse
import glob
import os
import re
import sys

WS = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
TICKETS = os.path.join(WS, "doc", "tickets")

DB_PAT = re.compile(r"reseed\.sh|api\.sh|db\.py|ddl_vs_ssot\.py")
MAVEN_PAT = re.compile(r"\bmvn\b")
PNPM_PAT = re.compile(r"\bpnpm\b")


def accept_block(text):
    """取 front-matter 里 accept: 到下一个顶层键之间的文本。"""
    m = re.search(r"^accept:\n(.*?)(?=^[a-z_]+:\s*$|\n^---\s*$)", text, re.S | re.M)
    return m.group(1) if m else ""


def front_matter(text):
    m = re.match(r"^---\n(.*?)\n---\n", text, re.S)
    return m.group(1) if m else ""


def field(fm, name):
    m = re.search(r"^%s:\s*(.*)$" % name, fm, re.M)
    return m.group(1).strip() if m else ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--all", action="store_true", help="连已完成的票一起列")
    a = ap.parse_args()

    rows = []
    for p in sorted(glob.glob(os.path.join(TICKETS, "*", "prompt.md"))):
        tid = os.path.basename(os.path.dirname(p))
        text = open(p, encoding="utf-8").read()
        fm = front_matter(text)
        acc = accept_block(text)
        phase = field(fm, "phase")
        rows.append((
            phase, tid,
            bool(DB_PAT.search(acc)),
            bool(MAVEN_PAT.search(acc)),
            bool(PNPM_PAT.search(acc)),
        ))

    rows.sort(key=lambda r: (r[0], r[1]))
    print("阶段  ticket                  needs_db  needs_maven  needs_pnpm  可并行")
    print("-" * 82)
    safe = []
    for phase, tid, db, mv, pn in rows:
        ok = (not db) and (not mv)
        if ok:
            safe.append(tid)
        print("%-5s %-23s %-9s %-12s %-11s %s" %
              (phase, tid, "YES" if db else "-", "YES" if mv else "-",
               "YES" if pn else "-", "★ PARALLEL-SAFE" if ok else ""))
    print("-" * 82)
    print("可并行的票（%d）：%s" % (len(safe), ", ".join(safe) or "无"))
    print("\n注：可并行 ≠ 一定并行。同一时刻只允许一张票碰 PG 5433 与 8081；")
    print("    并行只能发生在「一张 DB-free 票 + 一张要 DB 的票」之间。")


if __name__ == "__main__":
    main()
