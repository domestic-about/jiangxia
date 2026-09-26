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

2026-09-23 按 CR-20260923-09 更新（独立验收查实：环境类失败被记成该级 pass，D7-r1-L01 mvn exit 1 照记 L0 pass）：
  · 级的状态**算**出来：该级有 fail → fail；否则有 env（环境 / 工具坏，含编译失败、前端构建失败、reseed 失败）→ blocked；
    该级一步都没跑（例如 L0 环境不对提前收工，L1 根本没开始）→ blocked；全 pass（known 不算红）才是 pass。
    blocked 不是 pass 也不是 fail：`task_state.py qa merge` 只认 pass / fail，blocked 的级不会被当成「跑过了」。
  · **门退出码不为 0、或任一级不是 pass → 不写正式审计文件**（doc/waves/qa/<PHASE>-r<n>-L01.json），
    只写草稿 `<logdir>/<PHASE>-r<n>-L01.draft.json` 供人看；`--audit` 指到 doc/waves/qa/ 里同样拒写。
    gate.json 里 `audit_formal` 标明这次有没有写正式审计。

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


def escalated_in_phase(phase):
    """状态里 escalated 的票 —— 未实现项必须在审计里**显式标注**（模板要求），
    否则 QA 报告会假装这一级全覆盖了。"""
    try:
        with open(os.path.join(ROOT, "doc", "waves", "state.json"), encoding="utf-8") as f:
            d = json.load(f)
        return sorted(k for k, v in (d.get("tickets") or {}).items()
                      if isinstance(v, dict) and v.get("phase") == phase
                      and v.get("status") == "escalated")
    except Exception:
        return []


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

    def level_status(ss):
        """fail（有未登记的红）> blocked（有环境 / 工具坏，或这一级一步都没跑）> pass。
        known（已登记的非产品缺陷红）不算红。★ 环境坏绝不能让这一级记 pass：「没验成」不是「验过了」。"""
        if not ss:
            return "blocked"
        if any(s["status"] == "fail" for s in ss):
            return "fail"
        if any(s["status"] == "env" for s in ss):
            return "blocked"
        return "pass"

    l0_status = level_status(l0)
    l1_status = level_status(l1)
    _esc = escalated_in_phase(a.phase)
    _esc_note = ("\n⚠️ 本任务有未实现项（escalated，accept 重放按状态跳过、**不**算产品缺陷）："
                 + ", ".join(_esc)) if _esc else ""

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
    _esc = escalated_in_phase(a.phase)
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
        elif s["status"] == "known":
            kr = []
            if acc_path and os.path.exists(acc_path):
                try:
                    kr = json.load(open(acc_path, encoding="utf-8")).get("known_red") or []
                except Exception:
                    kr = []
            for r in kr:
                issues.append({
                    "severity": "S3", "level": level_of(s["id"]), "type": "harness",
                    "ticket": r.get("ticket") or a.phase,
                    "title": f"[模式 B gate] {r['ticket']} acc{r['index']} 红，但已登记为非产品缺陷"
                             f"（issue {r['known_red']['issue']}）：{r['known_red']['reason'][:80]}",
                    "detail": f"gate.sh 步骤 {s['id']} 报 known-red：{s['msg']}。"
                              f"该 accept 的失败原因已登记在 issue {r['known_red']['issue']}（票面/环境/harness 缺陷），"
                              f"不是产品行为不符 → 封顶 S3，不拦门；但**断言本身仍然红**，不是已修。"
                              f"逐条日志 {os.path.relpath(r.get('log') or '', ROOT)}。"
                              f"定级依据：与 qa merge 对 type=harness 的处理一致（长在检查工具/票面/环境上的问题不许拉返工）。",
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

    # ── 正式审计只在「门退出码 0 且两级都 pass」时写 ─────────────────────────
    formal_ok = (a.exit == 0 and l0_status == "pass" and l1_status == "pass")
    refused = ""
    default_formal = os.path.join(QA_DIR, f"{a.phase}-r{rnd}-L01.json")
    audit_path = a.audit or default_formal
    in_qa_dir = os.path.abspath(audit_path).startswith(os.path.abspath(QA_DIR) + os.sep)
    if not formal_ok and in_qa_dir:
        refused = (f"门退出码 {a.exit}、L0={l0_status}、L1={l1_status} —— 不是全绿，禁止写正式审计文件 "
                   f"{os.path.relpath(audit_path, ROOT)}")
        draft_dir = a.logdir or os.path.dirname(os.path.abspath(a.gate))
        audit_path = os.path.join(draft_dir, f"{a.phase}-r{rnd}-L01.draft.json")
    os.makedirs(os.path.dirname(audit_path), exist_ok=True)
    audit = {
        "phase": a.phase, "round": rnd, "auditor": "independent",
        "generated_by": "doc/waves/tools/gate.sh + gate-audit.py（模式 B：L0+L1 脚本化，非 LLM 审计）",
        "levels": {
            "L0": {"status": l0_status, "evidence": l0_ev + _esc_note},
            "L1": {"status": l1_status, "evidence": l1_ev + _esc_note},
        },
        "escalated_noted": escalated_in_phase(a.phase),
        "issues": issues,
    }
    if not formal_ok:
        # 草稿：auditor 不写 independent（qa merge 会拒收），并写明为什么不是正式审计
        audit["auditor"] = "draft-not-for-merge"
        audit["draft_reason"] = refused or (f"门退出码 {a.exit}、L0={l0_status}、L1={l1_status} —— 不是全绿；"
                                            f"按 --audit 写到非正式位置，不能拿去 qa merge")
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
        "audit_formal": bool(formal_ok and in_qa_dir),
        "audit_refused": refused,
    }
    os.makedirs(os.path.dirname(os.path.abspath(a.gate)), exist_ok=True)
    with open(a.gate, "w", encoding="utf-8") as f:
        json.dump(gate, f, ensure_ascii=False, indent=1)

    print(f"[ok] gate.json → {os.path.relpath(a.gate, ROOT)}")
    if refused:
        print(f"[refuse] {refused}")
        print(f"[draft] 审计草稿 → {os.path.relpath(audit_path, ROOT)}（{a.phase} r{rnd}；L0={l0_status} L1={l1_status}；"
              f"步骤 {len(steps)}，issue {len(issues)}；auditor=draft-not-for-merge，qa merge 不收）")
    else:
        print(f"[ok] 审计 → {os.path.relpath(audit_path, ROOT)}（{a.phase} r{rnd}；L0={l0_status} L1={l1_status}；"
              f"步骤 {len(steps)}，issue {len(issues)}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
