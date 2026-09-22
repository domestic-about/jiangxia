#!/usr/bin/env python3
"""`doc/authority/authority_lint.py` —— 转发垫片（2026-09-22 补）。

为什么有它：**任务书与历次派单里写的都是这个路径**（`python3 doc/authority/authority_lint.py show <锚>`），
而脚本的真实位置在 `~/claude-config/skills/xuqiu/scripts/authority_lint.py`。
结果是 D1–D6 每一任 impl / QA subagent 都被指向一个不存在的文件：只有自己找了 skill 路径的人
才真正取到了权威口径，其余人要么静默跳过、要么在报告里记 WARN（QC-MODEL-001 的 WARN ⑪ 就是这么发现的）。
★ 权威口径是「以蓝图为准」判定的唯一依据，这条路断掉等于把 doc-drift 的判断权交给了运气。

做法：把参数原样转发给 skill 里的真脚本，并自动补 `--ws <项目根>/doc`
（真脚本按 `cwd/doc` 兜底探测，但我们从任意 cwd 调都应当能工作）。
**这不是权威数据的副本**，只是一行转发；权威仍然只有 `doc/authority/*.yaml` 那一份。

用法与真脚本完全一致：
  python3 doc/authority/authority_lint.py show FLOW:F-QC-01.step1
  python3 doc/authority/authority_lint.py check
  python3 doc/authority/authority_lint.py diff
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))          # <项目根>/doc/authority
DOC = os.path.dirname(HERE)                                # <项目根>/doc
REAL = os.path.expanduser("~/claude-config/skills/xuqiu/scripts/authority_lint.py")


def main():
    if not os.path.exists(REAL):
        sys.stderr.write(
            f"[error] 找不到真脚本 {REAL}\n"
            f"        本文件只是转发垫片；请确认 xuqiu skill 已安装，"
            f"或直接跑 `python3 {REAL} --ws {DOC} <子命令>`\n")
        return 2
    argv = [sys.executable, REAL]
    # 用户自己传了 --ws/--authority 就别重复补
    if "--ws" not in sys.argv[1:]:
        argv += ["--ws", DOC]
    argv += sys.argv[1:]
    return subprocess.call(argv)


if __name__ == "__main__":
    sys.exit(main())
