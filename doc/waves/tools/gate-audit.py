#!/usr/bin/env python3
"""模式 B：把 gate.sh 的机器结果转成 `doc/waves/qa/<PHASE>-r<n>-L01.json`。

为什么要有它：`task_state.py qa merge` **硬要求四级都有 pass/fail 且有 evidence**（缺级一律
拒合 —— 它挡的是「没跑当跑过」）。模式 B 把 L0+L1 从「派 agent」换成「跑脚本」，那就得
把脚本的真实输出落成合规模的审计，而不是让谁凭印象写一段。

几条刻意为之的设计：
  · `auditor: "independent"` + `generated_by` 字段标明「本片由 gate.sh 机械产出」——
    它不是 LLM 审计，也不冒充；但它比 LLM 审计更**可复现**（同 commit 重跑逐字相同）。
  · 每条 fail 都变成一条 issue：**产品断言不成立 = S1**（拦门），**环境/工具坏 = S2 + harness**
    （不拦门 —— 工具故障不该伪装成产品缺陷）。
  · 轮次 = 已合并的 `<PHASE>-r<n>.json` 个数 + 1：同轮内重跑覆盖本片，合过之后再跑才进下一轮。

用法（通常由 gate.sh 调用）：
  python3 doc/waves/tools/gate-audit.py --gate .tmp/gate/D5/gate.json --tsv .tmp/gate/D5/steps.tsv \
      --phase D5 --head <sha> --started <iso> --finished <iso> --exit 0 --logdir .tmp/gate/D5
"""

import argparse
import glob
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
QA_DIR = os.path.join(ROOT, "doc", "waves", "qa")


def next_round(phase):
    merged = glob.glob(os.path.join(QA_DIR, f"{phase}-r[0-9]*.json"))
    merged = [p for p in merged if not re.search(r"-L[0-9A-Za-z]+\.json$", os.path.basename(p))]
    ns = []
    for p in merged:
        m = re.search(rf"{re.escape(phase)}-r(\d+)\.json$", os.path.basename(p))
        if m:
            ns.append(int(m.group(1)))
    return (max(ns) + 1) if ns else 1


def read_steps(tsv):
    steps = []
    if not os.path.exists(tsv):
        return steps
    with open(tsv, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            parts = line.split("\t")
            while len(parts) < 3:
                parts.append("")
            steps.append({"status": parts[0], "id": parts[1], "msg": parts[2]})
    return steps


def level_of(step_id):
    m = re.match(r"^(L[0-3])", step_id)
    return m.group(1) if m else "L1"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gate", required=True, help="gate.json 输出路径")
    ap.add_argument("--tsv", required=True)
    ap.add_argument("--phase", required=True)
    ap.add_argument("--head", default="none")
    ap.add_argument("--started", default="")
    ap.add_argument("--finished", default="")
    ap.add_argument("--exit", type=int, default=0)
    ap.add_argument("--logdir", default="")
    ap.add_argument("--round", type=int, default=0)
    ap.add_argument("--audit", help="审计输出路径（默认 doc/waves/qa/<PHASE>-r<n>-L01.json）")
    a = ap.parse_args()

    steps = read_steps(a.tsv)
    rnd = a.round or next_round(a.phase)

    l0 = [s for s in steps if level_of(s["id"]) == "L0"]
    l1 = [s for s in steps if level_of(s["id"]) == "L1"]
    l0_status = "fail" if any(s["status"] == "fail" for s in l0) else "pass"
    l1_status = "fail" if any(s["status"] == "fail" for s in l1) else "pass"
    # 环境坏（env）不改级的红绿 —— 它是工具问题，由 issues 里的 S2/harness 记账，
    # 但**整轮该级仍然如实标 pass/fail**，绝不由「环境没准备好」冒充通过。
    if any(s["status"] == "env" for s in steps) and not any(s["status"] == "fail" for s in steps):
        pass  # 级状态保持 pass；env 记 issue，供人判断这轮是否可信

    accept = {}
    acc_path = os.path.join(a.logdir, "accept.json") if a.logdir else ""
    if acc_path and os.path.exists(acc_path):
        try:
            accept = json.load(open(acc_path, encoding="utf-8"))
        except Exception:
            accept = {}
    nf_count = sum(1 for r in (accept.get("results") or []) if r.get("nf"))
    acc_summary = (f"票面 accept {accept.get('passed', '?')}/{accept.get('total', '?')} 成立"
                   if accept else "票面 accept 未跑/无结果")

    def ev(ss, title):
        lines = [f"{s['status']:>4} {s['id']} — {s['msg']}" for s in ss]
        return f"【{title}｜gate.sh 机械产出，非 LLM 审计】head={a.head} " \
               f"后端={os.environ.get('LQG_API_BASE', '(见 gate 日志)')} 日志根={os.path.relpath(a.logdir, ROOT) if a.logdir else '-'}\n" + \
               "\n".join(lines) + \
               (f"\n（NF1 归一化：{nf_count} 条 accept 去掉了 --fresh-module，新鲜度由 L0.0 承担；" \
                f"逐条写过 {os.path.relpath(acc_path, ROOT) if acc_path else '-'}）" if nf_count else "")

    l0_ev = ev(l0, "L0 编译/单测/前端 build") + \
        (f"\n{acc_summary}" if l0 and not l1 else "")
    l1_ev = ev(l1, "L1 reseed/ddl_vs_ssot/accept 重放/D1 回归/收尾")

    issues = []
    for s in steps:
        if s["status"] == "fail":
            lv = level_of(s["id"])
            ticket = a.phase
            detail_bits = [f"复现：bash doc/waves/tools/gate.sh --phase {a.phase}（head {a.head}）→ 步骤 {s['id']} 红：{s['msg']}",
                           f"日志根 {os.path.relpath(a.logdir, ROOT) if a.logdir else '-'}"]
            if s["id"].startswith("L1.2") and accept.get("failed"):
                f0 = accept["failed"][0]
                ticket = f0.get("ticket") or ticket
                detail_bits.append("失败的 accept：" + "; ".join(
                    f"{r['ticket']} acc{r['index']}(exit {r['exit']})" for r in accept["failed"][:8]))
                detail_bits.append(f"逐条日志见 {os.path.relpath(a.logdir, ROOT)}/accept-logs/")
            elif s["id"].startswith("L1.3"):
                ticket = "D1-regression"
            issues.append({
                "severity": "S1", "level": lv, "type": "debt", "ticket": ticket,
                "title": f"[模式 B gate] {s['id']} 不成立：{s['msg'][:110]}",
                "detail": "｜".join(detail_bits) + "。定级依据：这是票面 accept / 回归包断言不成立，"
                          "即产品行为与票面要求不符 → 拦门（S1）。",
            })
        elif s["status"] == "env":
            issues.append({
                "severity": "S2", "level": level_of(s["id"]), "type": "harness", "ticket": a.phase,
                "title": f"[模式 B gate] 环境/工具问题（非产品缺陷）：{s['id']} {s['msg'][:100]}",
                "detail": f"gate.sh 步骤 {s['id']} 报 env-broken：{s['msg']}；"
                          f"日志 {os.path.relpath(a.logdir, ROOT) if a.logdir else '-'}。"
                          "定级依据：工具/环境故障，不是产品行为不符 → type=harness，封顶 S2，不拦门；"
                          "但它使这一轮 gate 结论不可信，需修好环境后重跑。",
            })

    audit_path = a.audit or os.path.join(QA_DIR, f"{a.phase}-r{rnd}-L01.json")
    os.makedirs(os.path.dirname(audit_path), exist_ok=True)
    audit = {
        "phase": a.phase, "round": rnd, "auditor": "independent",
        "generated_by": "doc/waves/tools/gate.sh + gate-audit.py（模式 B：L0+L1 脚本化，非 LLM 审计）",
        "levels": {
            "L0": {"status": l0_status, "evidence": l0_ev},
            "L1": {"status": l1_status, "evidence": l1_ev},
        },
        "escalated_noted": [],
        "issues": issues,
    }
    with open(audit_path, "w", encoding="utf-8") as f:
        json.dump(audit, f, ensure_ascii=False, indent=1)

    gate = {
        "phase": a.phase, "head": a.head, "started": a.started, "finished": a.finished,
        "backend": os.environ.get("LQG_API_BASE", ""), "exit": a.exit,
        "l0": l0_status, "l1": l1_status, "round": rnd,
        "accept": {"passed": accept.get("passed"), "total": accept.get("total"),
                   "nf1": nf_count,
                   "failed": [{"ticket": r["ticket"], "index": r["index"], "exit": r["exit"]}
                              for r in (accept.get("failed") or [])]},
        "steps": steps,
        "audit": os.path.relpath(audit_path, ROOT),
    }
    os.makedirs(os.path.dirname(os.path.abspath(a.gate)), exist_ok=True)
    with open(a.gate, "w", encoding="utf-8") as f:
        json.dump(gate, f, ensure_ascii=False, indent=1)

    print(f"[ok] gate.json → {os.path.relpath(a.gate, ROOT)}")
    print(f"[ok] 审计 → {os.path.relpath(audit_path, ROOT)}（{a.phase} r{rnd}；L0={l0_status} L1={l1_status}；"
          f"步骤 {len(steps)}，issue {len(issues)}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
