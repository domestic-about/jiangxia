#!/usr/bin/env python3
"""SYS-ACCEPT-001 · 把六个热点的观测值 + 变异结果合成一份 `evidence.json`。

  python3 merge-evidence.py <mode: mutation|verify-only> <scope_csv> <mutation_outcomes.json>

`evidence.json` 的契约（ticket SYS-ACCEPT-001 accept 2 第 3 段直接读它）：
  · `hotspots` 的键集合必须恰好是 {H1a,H1b,H2,H3a,H3b,H4}；
  · 每个热点都要有**真实运行证据**（真请求头 / 真 DOM 读数 / 真 URL / 真响应码），
    不是「字段存在」——H3a 例外，它是剥注释后的真代码位置（本票唯一做不到纯行为化的一条，已标注）。
"""
import glob
import json
import os
import sys
from datetime import datetime, timezone

ROOT = "/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid"
OUT = os.path.join(ROOT, "doc/waves/regression/D7/accept-strengthened")
OBS = os.path.join(OUT, "observations")

EXPECT = ["H1a", "H1b", "H2", "H3a", "H3b", "H4"]

META = {
    "H1a": {
        "ticket": "SYS-EXPORT-001",
        "accept": "acc2",
        "criterion": "真发一次「导出 Excel」：请求头必须带 Authorization(Bearer) + clientid，响应 200 且响应体是真 xlsx（ZIP 魔数 PK）",
        "old_criterion": "grep -E 'Authorization|clientid' src/pages/ledger/export.ts src/utils/fileHandoff.ts（只问两个文件里有没有这两个词）",
        "mutation": "去掉 src/pages/ledger/index.vue 里 downloadToTemp 的 `header: authHeader(), requireAuth: true`",
        "behavioral": True,
    },
    "H1b": {
        "ticket": "SYS-EXPORT-001",
        "accept": "acc2",
        "criterion": "真点一次单份「下载」：后端签发 OSS 预签名直链后，浏览器对直链的请求头**不含 Authorization**，且 OSS 响应 200",
        "old_criterion": "同上一条 grep（调用点与目标 URL 零覆盖；这正是 D7 r1 S1 的盲区）",
        "mutation": "给 src/components/lqg/DownloadBar.vue 的 downloadToTemp 传回 authHeader()",
        "behavioral": True,
    },
    "H2": {
        "ticket": "SYS-HOME-001",
        "accept": "acc2",
        "criterion": "工作台首页真 DOM 上五张卡片的数字 == 同一次 GET /lqg/home/todo 的返回值",
        "old_criterion": "grep 'home/todo' src/api/lqg/home.ts + grep -c TodoCard >= 5 …（只问「卡片接了接口」，不看页面显示什么）",
        "mutation": "把卡片 :value=\"todo.pendingSamples\" → 写死 7、:value=\"todo.pendingEmbeds\" → 写死 9（真值 2 / 1）",
        "behavioral": True,
    },
    "H3a": {
        "ticket": "DOC-MP-002",
        "accept": "acc1",
        "criterion": "剥掉注释后，`showMenu: true` 仍出现在真代码里，且位于 uni.openDocument({filePath, …, showMenu: true}) 的参数位置",
        "old_criterion": "grep -qE 'showMenu:[[:space:]]*true' src/utils/fileHandoff.ts（注释里的字面量也算命中）",
        "mutation": "删掉 fileHandoff.ts 里真代码的 `showMenu: true,`，只在注释里保留字面量",
        "behavioral": False,
        "behavioral_note": "showMenu 是**平台参数**，H5（本轮验收面，owner 已把真机换成 H5+mock）上 uni.openDocument 不存在、如实退化成 window.open，"
                           "没有可在 H5 施加的行为变异。按 ticket §2 的显式例外用「剥注释后的真代码位置」判，并证明它与注释无关（本变异即该证明）。",
    },
    "H3b": {
        "ticket": "DOC-MP-002",
        "accept": "acc1",
        "criterion": "点「文档中的图片」缩略图后，打开层（H5 = wx.previewImage 覆盖层）拿到的 src == pages 接口给的原图 url，且 ≠ previewUrl",
        "old_criterion": "grep -q 'previewImage' src/components/lqg/ThumbStrip.vue（只看组件里出现过这个词）",
        "mutation": "把 ThumbStrip.vue#open 的 urls 从「原图 url」换成 usable 的 previewUrl",
        "behavioral": True,
    },
    "H4": {
        "ticket": "DOC-PUBLISH-001",
        "accept": "acc2",
        "criterion": "工作台预览面板真 DOM 渲染出的下载入口恰好 4 个，且逐个点下去真发出 format/合并位正确的下载请求",
        "old_criterion": "grep -cE \"format.*(docx|pdf)|'docx'|'pdf'\" PreviewPane.vue >= 2（只钉住文里有 docx 与 pdf 两个字面量）",
        "mutation": "删掉 PreviewPane.vue 里「下载合并 PDF」整个 el-button",
        "behavioral": True,
    },
}


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "verify-only"
    scope = (sys.argv[2] if len(sys.argv) > 2 else ",".join(EXPECT)).split(",")
    outcomes_path = sys.argv[3] if len(sys.argv) > 3 else ""
    outcomes = {}
    if outcomes_path and os.path.isfile(outcomes_path):
        outcomes = json.load(open(outcomes_path, encoding="utf-8"))

    hotspots = {}
    missing = []
    for hot in EXPECT:
        p = os.path.join(OBS, f"{hot}.json")
        if not os.path.isfile(p):
            missing.append(hot)
            continue
        obs = json.load(open(p, encoding="utf-8"))
        meta = META[hot]
        hotspots[hot] = {
            "ticket": meta["ticket"], "accept": meta["accept"],
            "criterion": meta["criterion"], "old_criterion": meta["old_criterion"],
            "mutation": meta["mutation"], "behavioral": meta["behavioral"],
            **({"behavioral_note": meta["behavioral_note"]} if "behavioral_note" in meta else {}),
            "result": "green" if obs.get("green") else "red",
            "checked_at": obs.get("ran_at"),
            "observed": obs.get("observed") or {},
            "assertions": [{"name": r["name"], "ok": r["ok"], "detail": r["detail"]} for r in obs.get("results", [])],
        }

    doc = {
        "ticket": "SYS-ACCEPT-001",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "mode": mode,
        "scope": scope,
        "all_six_present": sorted(hotspots) == sorted(EXPECT),
        "missing": missing,
        "tree_green": all(v["result"] == "green" for v in hotspots.values()) if hotspots else False,
        "hotspots": hotspots,
        "mutations": outcomes.get("mutations", {}),
        "tree_after": outcomes.get("tree_after", ""),
        "env": outcomes.get("env", {}),
    }
    os.makedirs(OUT, exist_ok=True)
    # ★ `--verify-only` 只证明「未改坏的树全绿」，它会把上一次**变异验证**的结果覆盖掉。
    #   变异验证是错峰跑的（accept 1 先跑变异、accept 2 再跑 verify-only），所以：
    #   · mutation 模式：额外落一份 `evidence-mutation.json`（变异的耐久档案）；
    #   · verify-only 模式：把那份档案挂到 `previous_mutation_run` 下 —— 观测值不丢，
    #     也不假装这次 verify-only 跑过变异（`mutations` 仍如实为 {}）。
    mut_path = os.path.join(OUT, "evidence-mutation.json")
    if mode == "mutation":
        with open(mut_path, "w", encoding="utf-8") as f:
            json.dump(doc, f, ensure_ascii=False, indent=1)
    else:
        try:
            prev = json.load(open(mut_path, encoding="utf-8"))
            doc["previous_mutation_run"] = {
                "generated_at": prev.get("generated_at"),
                "tree_after": prev.get("tree_after"),
                "mutations": prev.get("mutations", {}),
            }
        except Exception:
            doc["previous_mutation_run"] = None
    with open(os.path.join(OUT, "evidence.json"), "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    print(f"[ok] evidence.json 落盘：{len(hotspots)} 个热点"
          + (f"，缺 {missing}" if missing else "，六个齐")
          + (f"；变异档案 evidence-mutation.json {'已更新' if mode == 'mutation' else '已挂到 previous_mutation_run' if doc.get('previous_mutation_run') else '不存在'}"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
