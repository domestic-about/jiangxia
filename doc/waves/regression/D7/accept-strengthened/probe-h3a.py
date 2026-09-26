#!/usr/bin/env python3
"""SYS-ACCEPT-001 · 热点 H3a 的判据（**唯一允许读源码的例外**）。

判据（DOC-MP-002 acc1 / ticket §2 硬要求 1）：`showMenu: true` 必须出现在**真代码**里 ——
先把注释（行注释 + 块注释）剥掉再断言，所以「把真代码那行删掉、只在注释里留字面量」
（= D7 r1 L2 证伪 F1）必然变红。

同时要求它出现在**调用参数位置**：`showMenu` 所在的对象字面量里必须有 `filePath`
（= `uni.openDocument({filePath, fileType, showMenu: true})`），
而不是文件里随便哪个位置出现一个同名 token。

★ 为什么这一条允许读源码：`showMenu` 是**平台参数**，H5（本轮验收面）上
  `uni.openDocument` 不存在（如实退化成 `window.open`），真机上才可观测；
  它没有可在 H5 上施加的行为变异，所以本票按 ticket §2 的口径用「剥注释后的真代码」判。
  这是本票六个热点里唯一一条做不到纯行为化的 —— 报告里如实标注。

退出码：0 = 判据成立（绿）｜1 = 判据被违反（红）｜2 = 用法/环境错。
证据：doc/waves/regression/D7/accept-strengthened/observations/H3a.json
"""
import json
import os
import re
import sys
from datetime import datetime, timezone

# ★ 2026-09-23 按 CR-20260923-09 更新：ROOT 不再写死工作区绝对路径（独立验收查实：谁在副本里重放，
#   写死的 ROOT 都会把变异写进工作区源码、在工作区执行 git checkout）→ 从脚本自身位置推导：
#   accept-strengthened → D7 → regression → waves → doc → 仓库根（上溯 5 级）。
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..", ".."))
if not os.path.isfile(os.path.join(ROOT, "code", "miniapp", "src", "pages.json")):
    sys.stderr.write(f"[error] ROOT 推导错了：{ROOT} 下没有 code/miniapp/src/pages.json\n")
    sys.exit(2)
OUT = os.path.join(ROOT, "doc/waves/regression/D7/accept-strengthened/observations")
SRC = os.path.join(ROOT, "code/miniapp/src/utils/fileHandoff.ts")

results = []
observed = {}


def rec(name, ok, detail=""):
    results.append({"name": name, "ok": bool(ok), "detail": str(detail)[:1200]})
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"  :: {str(detail)[:420]}" if detail else ""))
    return bool(ok)


def strip_comments(text):
    """剥掉行注释与块注释（本文件的相关行没有把 `//` 写进字符串字面量）。"""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return "\n".join(line.split("//", 1)[0] for line in text.split("\n"))


def main():
    if not os.path.isfile(SRC):
        print(f"[error] 缺 {SRC}", file=sys.stderr)
        return 2
    raw = open(SRC, encoding="utf-8").read()
    code = strip_comments(raw)

    raw_hits = re.findall(r"showMenu\s*:\s*true", raw)
    code_hits = list(re.finditer(r"showMenu\s*:\s*true", code))
    observed["source_file"] = SRC.replace(ROOT + "/", "")
    observed["occurrences_in_raw_file"] = len(raw_hits)
    observed["occurrences_after_stripping_comments"] = len(code_hits)
    observed["file_sha256"] = __import__("hashlib").sha256(raw.encode()).hexdigest()

    rec("H3a:源码里存在 showMenu: true 字面量（含注释内）", len(raw_hits) >= 1, f"raw={len(raw_hits)}")
    rec("H3a:★ 剥掉注释后 `showMenu: true` 仍在真代码里（注释满足不了这条）",
        len(code_hits) == 1, f"raw={len(raw_hits)} code={len(code_hits)}")
    if len(code_hits) == 1:
        m = code_hits[0]
        brace = code.rfind("{", 0, m.start())
        segment = code[brace:m.start()]
        line_no = code[:m.start()].count("\n") + 1
        snippet = code.split("\n")[line_no - 1].strip()
        observed["code_position"] = {"line": line_no, "snippet": snippet,
                                     "enclosing_object_has_filePath": "filePath" in segment}
        rec("H3a:★ 它出现在 `openDocument({filePath, …, showMenu: true})` 的参数位置（同一对象字面量里有 filePath）",
            "filePath" in segment, json.dumps(observed["code_position"], ensure_ascii=False))
        rec("H3a:调用点是 uni.openDocument（平台查看器的菜单开关）",
            "openDocument" in code, f"openDocument in code = {'openDocument' in code}")
    else:
        observed["code_position"] = None
        rec("H3a:★ 它出现在 openDocument 的参数位置", False, f"剥注释后命中 {len(code_hits)} 处")

    green = not [r for r in results if not r["ok"]]
    payload = {"hotspot": "H3a", "green": green, "ran_at": datetime.now(timezone.utc).isoformat(),
               "results": results, "observed": observed}
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "H3a.json"), "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)
    passed = len([r for r in results if r["ok"]])
    print(f"\n==== H3a: {passed}/{len(results)} PASS → "
          f"{'GREEN（判据成立）' if green else 'RED（判据被违反）'} ====")
    return 0 if green else 1


if __name__ == "__main__":
    sys.exit(main())
