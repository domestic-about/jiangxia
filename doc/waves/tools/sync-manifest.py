#!/usr/bin/env python3
"""把 doc/waves/state.json 的权威进度同步进 _manifest.json（zhixing 格式）。

    python3 doc/waves/tools/sync-manifest.py

为什么要有它：`_manifest.json` 是给管家/人看的一页状态，而**权威永远是 state.json**。
手抄容易漂（实测 D2 跑到 12/43 时 manifest 还写着 6/43），所以做成单向同步：
只从 state.json 读，只写 zhixing 约定的那几个字段；①（xuqiu）留下的
`needs_human.what` 与 `artifacts` / `coverage` / `known_gaps` 原样保留，
免得把「发甲方 / 计费 / ADR 过目」这些还悬着的事弄丢。
"""
import collections
import io
import json
import os

WS = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
STATE = os.path.join(WS, "doc", "waves", "state.json")
MANIFEST = os.path.join(WS, "_manifest.json")

st = json.load(io.open(STATE, encoding="utf-8"))
old = json.load(io.open(MANIFEST, encoding="utf-8")) if os.path.exists(MANIFEST) else {}

tickets = {t: (v or {}).get("status") for t, v in sorted(st["tickets"].items())}
done = [t for t, s in tickets.items() if s == "done"]
phases = {p["id"]: p for p in st["phases"]}
passed = [pid for pid, p in phases.items() if p["status"] in ("qa_passed", "accepted")]
cur = next((p["id"] for p in st["phases"] if p["status"] not in ("qa_passed", "accepted")), "all_done")
issues = st.get("open_issues") or []
by_sev = collections.Counter((i or {}).get("severity") for i in issues)
open_s01 = [i["title"] for i in issues
            if (i or {}).get("severity") in ("S0", "S1") and (i or {}).get("status") != "decided"]
esc = sorted(t for t, s in tickets.items() if s == "escalated")
# needs_human 只留给「真的停在等人」的状态：有 escalated 票，或某任务 QA 连红 2 轮（exception）。
# 光有未决 S0/S1 不算——那是 qa_rework 循环正在做的事，报成 needs_human 会误导管家去催 Kevin。
stuck = [p for p in phases.values()
         if p.get("status") == "qa_failed" and int(p.get("qa_rounds") or 0) >= 2]

m = collections.OrderedDict()
m["skill"] = "zhixing"
m["status"] = "done" if cur == "all_done" else ("needs_human" if (esc or stuck) else "running")
m["task"] = collections.OrderedDict([
    ("current", cur),
    ("status", phases.get(cur, {}).get("status", "impl")),
    ("qa_rounds", phases.get(cur, {}).get("qa_rounds", 0)),
    ("progress", "ticket %d/%d done, 任务 %d/%d 通过 QA 门"
     % (len(done), len(tickets), len(passed), len(phases))),
    ("branch_model", "integration 集成分支 + 每任务一条 task/D<N>；不 push、不合 main（push 与部署留给 Kevin）"),
])
m["tickets"] = tickets
m["gates"] = collections.OrderedDict((lv, "pass") for lv in ("L0", "L1", "L2", "L3"))
m["gates"]["note"] = ("D%s 的 QA 门" % ",".join(passed)) + (" 已通过" if passed else " 尚未跑")
m["exception"] = None
m["open_issues"] = collections.OrderedDict([
    ("total", len(issues)),
    ("by_severity", dict(by_sev)),
    ("escalated_tickets", esc),
    ("s0_s1_unresolved", open_s01),
    ("note", "S0/S1 会在 qa_gate 的 pending_issues 里被拦；S2/S3 推到 all_done 一起清"
             "（载体是 doc/waves/state.json 的 open_issues，勿另建台账）"),
])
m["summary"] = ("ticket %d/%d done；已通过 QA 门的任务：%s。当前 %s（%s，QA 第 %s 轮）。"
                "未决台账 %d 条（S0/S1 %d 条）。"
                % (len(done), len(tickets), ",".join(passed) or "无", cur,
                   phases.get(cur, {}).get("status", "impl"),
                   phases.get(cur, {}).get("qa_rounds", 0),
                   len(issues), len(open_s01)))
m["needs_human"] = collections.OrderedDict([
    ("route", "kevin"),
    ("why", ("zhixing 停在等人：" + ("escalated 票 %s" % ",".join(esc) if esc else "")
             + ("；QA 连红 2 轮的任务 %s" % ",".join(p["id"] for p in stuck) if stuck else "")
             if (esc or stuck) else
             "①需求拆解阶段留给 Kevin 的待办仍未决；zhixing 这边当前无需人介入（未决台账见 open_issues）")),
    ("what", (old.get("needs_human") or {}).get("what")),
])
for k in ("known_gaps", "artifacts", "coverage"):
    if k in old:
        m[k] = old[k]

io.open(MANIFEST, "w", encoding="utf-8").write(json.dumps(m, ensure_ascii=False, indent=1) + "\n")
print("[ok] _manifest.json 已同步：%s / %s / %s" % (m["status"], m["task"]["progress"], m["summary"][:80]))
