#!/usr/bin/env python3
"""SYS-ACCEPT-001 · 六个热点的**已定义变异**施加器（ticket §1 表格最后一列）。

  python3 mutate.py apply   <HOTSPOT>   # 施加变异（幂等性不做保证；调用方保证是干净树）
  python3 mutate.py restore <HOTSPOT>   # 还原（git checkout -- <file>，并核对 sha256 回到原值）
  python3 mutate.py check   <HOTSPOT>   # 只打印该热点的目标文件与 sha256

设计纪律（ticket §2 / 派单 §1②）：
  · 变异**精确到一处**：每个热点显式写死 old → new，并在替换前断言 old 在文件里**恰好出现一次**；
    出现 0 次或多次 → exit 1（绝不「尽力而为」地改个大概）。
  · 还原用 `git checkout -- <file>`（六个目标文件在起点都是干净的 tracked 文件），
    还原后核对 sha256 与施加前**逐字节一致** —— 这是「真的还原了」的机器证据。
  · 不使用 `git stash`（不往 stash list 里留东西）。

退出码：0 = 成功｜1 = 变异/还原没做到（调用方必须当成失败，不许吞）。
"""
import hashlib
import json
import os
import re
import subprocess
import sys

# ★ 2026-09-23 按 CR-20260923-09 更新：ROOT 不再写死工作区绝对路径（独立验收查实：谁在副本里重放，
#   写死的 ROOT 都会把变异写进工作区源码、在工作区执行 git checkout）→ 从脚本自身位置推导：
#   accept-strengthened → D7 → regression → waves → doc → 仓库根（上溯 5 级）。
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..", ".."))
if not os.path.isfile(os.path.join(ROOT, "code", "miniapp", "src", "pages.json")):
    sys.stderr.write(f"[error] ROOT 推导错了：{ROOT} 下没有 code/miniapp/src/pages.json\n")
    sys.exit(2)

F_HANDOFF = "code/miniapp/src/utils/fileHandoff.ts"
F_DOWNLOADBAR = "code/miniapp/src/components/lqg/DownloadBar.vue"
F_LEDGER = "code/miniapp/src/pages/ledger/index.vue"
F_THUMB = "code/miniapp/src/components/lqg/ThumbStrip.vue"
F_HOME = "code/plus-ui/src/views/lqg/home/index.vue"
F_PREVIEW = "code/plus-ui/src/views/lqg/qc/components/PreviewPane.vue"

# 每个热点：(文件, [(old, new, 期望出现次数), …])
MUTATIONS = {
    # H1a：去掉导出调用点的 `header: authHeader()`（= D7 r1 L2 证伪 F3 的形态）
    "H1a": (F_LEDGER, [(
        "    const path = await downloadToTemp(exportUrl(sheet.value.key, filters.value), {\n"
        "      header: authHeader(),\n"
        "      requireAuth: true,\n"
        "    })\n",
        "    const path = await downloadToTemp(exportUrl(sheet.value.key, filters.value))\n",
        1,
    )]),
    # H1b：给文档下载的调用点传回 authHeader()（= D7 r1 S1 缺陷本体：API 鉴权头塞到 OSS 直链上）
    "H1b": (F_DOWNLOADBAR, [
        ("import { downloadToTemp, openFile, shareFile } from '@/utils/fileHandoff'\n",
         "import { downloadToTemp, openFile, shareFile } from '@/utils/fileHandoff'\n"
         "import { authHeader } from '@/pages/ledger/export'\n",
         1),
        ("    const filePath = await downloadToTemp(url)\n",
         "    const filePath = await downloadToTemp(url, { header: authHeader(), requireAuth: true })\n",
         1),
    ]),
    # H2：把两张卡片的数字写死成**错的数**（真值 2 / 1，= 证伪 F4；CR-20260924-10 起样本卡拆成两张，写死组织那张）
    "H2": (F_HOME, [
        (':value="todo.pendingTissue"', ':value="7"', 1),
        (':value="todo.pendingEmbeds"', ':value="9"', 1),
    ]),
    # H3a：删掉真代码那一行，只在注释里保留字面量（= 证伪 F1）
    "H3a": (F_HANDOFF, [(
        "      showMenu: true,\n",
        "      // H3a 变异：真代码那一行已删，字面量只留在注释里\n"
        "      // showMenu: true,\n",
        1,
    )]),
    # H3b：把「看原图」改成打开 previewUrl（= 证伪 F2）
    "H3b": (F_THUMB, [(
        "  const urls = list.map(item => item.url)\n",
        "  const urls = usable.value.map(item => thumbUrlOf(item))\n",
        1,
    )]),
    # H4：删掉「下载合并 PDF」整个按钮（= 证伪 F6）
    "H4": (F_PREVIEW, None),   # 行块删除，见 apply() 里的特判
}


def sha(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def full(rel):
    return os.path.join(ROOT, rel)


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)


def apply_h4(text):
    """删掉「下载合并 PDF」那个 el-button 整块（行块级精确删除）。"""
    lines = text.split("\n")
    idx = [i for i, ln in enumerate(lines) if "downloadMerged('pdf')" in ln]
    if len(idx) != 1:
        raise SystemExit(f"[error] H4：`downloadMerged('pdf')` 命中 {len(idx)} 次（期望 1）")
    mid = idx[0]
    start = None
    for i in range(mid, -1, -1):
        if lines[i].strip().startswith("<el-button"):
            start = i
            break
    if start is None:
        raise SystemExit("[error] H4：找不到包住它的 <el-button 起始行")
    end = None
    for i in range(mid, len(lines)):
        if lines[i].strip() == "</el-button>":
            end = i
            break
    if end is None:
        raise SystemExit("[error] H4：找不到 </el-button> 结束行")
    removed = lines[start:end + 1]
    if len(removed) < 3:
        raise SystemExit(f"[error] H4：要删的块只有 {len(removed)} 行，形状不对")
    return "\n".join(lines[:start] + lines[end + 1:]), removed


def do_apply(hot):
    rel, rules = MUTATIONS[hot]
    path = full(rel)
    before = sha(path)
    text = open(path, encoding="utf-8").read()
    rec = {"hotspot": hot, "file": rel, "before_sha256": before}
    if hot == "H4":
        text2, removed = apply_h4(text)
        rec["removed_lines"] = [r.strip() for r in removed]
    else:
        for old, new, want in rules:
            got = text.count(old)
            if got != want:
                raise SystemExit(f"[error] {hot}：old 串在 {rel} 里出现 {got} 次（期望 {want}）—— 变异没施加")
            text = text.replace(old, new)
        text2 = text
        rec["replacements"] = len(rules)
    open(path, "w", encoding="utf-8").write(text2)
    after = sha(path)
    if after == before:
        raise SystemExit(f"[error] {hot}：写完 sha256 没变 —— 变异等于没施加")
    rec["after_sha256"] = after
    rec["applied"] = True
    # ★ 只打**一行** JSON：调用方直接 json.loads 整段 stdout（多行会 Extra data，实测踩过）
    print(json.dumps(rec, ensure_ascii=False))
    return 0


def do_restore(hot):
    rel, _ = MUTATIONS[hot]
    path = full(rel)
    before = sha(path)
    r = git("checkout", "--", rel)
    if r.returncode != 0:
        raise SystemExit(f"[error] {hot}：git checkout -- {rel} 失败：{r.stderr.strip()}")
    after = sha(path)
    d = git("diff", "--quiet", "--", rel)
    restored = (d.returncode == 0)
    print(json.dumps({"hotspot": hot, "file": rel, "sha_before_restore": before,
                      "sha_after_restore": after, "diff_vs_HEAD_clean": restored,
                      "restored": restored}, ensure_ascii=False))
    if not restored:
        raise SystemExit(f"[error] {hot}：还原后 {rel} 与 HEAD 仍有差异")
    return 0


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ("apply", "restore", "check"):
        print(__doc__)
        return 2
    verb, hot = sys.argv[1], sys.argv[2]
    if hot not in MUTATIONS:
        print(f"[error] 未知热点 {hot}；可选：{', '.join(MUTATIONS)}", file=sys.stderr)
        return 2
    rel, _ = MUTATIONS[hot]
    if verb == "check":
        print(json.dumps({"hotspot": hot, "file": rel, "sha256": sha(full(rel))}, ensure_ascii=False))
        return 0
    return do_apply(hot) if verb == "apply" else do_restore(hot)


if __name__ == "__main__":
    sys.exit(main())
