#!/usr/bin/env python3
"""CR-20261009-18 · 样本质控表模板加一行「种属」（模板 v5 → v6）。

    python3 doc/waves/reports/CR-20261009-18/add-species-row.py

就地改 `code/…/doc-templates/sample_qc.docx`：在「性别 / 临床诊断」那一行后面插入一行
「种属 | {{species}}」。新行**照抄**模板里「收样描述」那一行（左格 1 列标签 + 右格跨 5 列的值格，
同样的边框、单元格边距、最小行高 602、字体宋体 / Times New Roman 小四），只换文字与三个 w14:paraId。
表格的列宽（tblGrid）与其它行一个字节都不动。

幂等：模板里已经有 `{{species}}` 就什么都不做。
"""
from __future__ import annotations

import re
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
DOCX = ROOT / "code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/resources/lqg/doc-templates/sample_qc.docx"

# 新行三个 w14:paraId（行 + 两段）：模板里没用过的值，< 0x80000000
NEW_IDS = {"6BD080E4": "5A1E0018", "7DC6604D": "5A1E0019", "3E69618F": "5A1E001A"}


def main() -> int:
    with zipfile.ZipFile(DOCX) as z:
        xml = z.read("word/document.xml").decode("utf-8")
    if "{{species}}" in xml:
        print("[skip] 模板里已经有 {{species}}")
        return 0
    rows = list(re.finditer(r"<w:tr\b.*?</w:tr>", xml, re.S))
    anchor = next(r for r in rows if ">性别<" in r.group(0))
    template = next(r for r in rows if "{{receive_desc}}" in r.group(0)).group(0)
    for old in NEW_IDS:
        assert old in template, f"「收样描述」行里找不到 paraId {old}（模板变了，先核对）"
    row = template.replace(">收样描述<", ">种属<").replace("{{receive_desc}}", "{{species}}")
    for old, new in NEW_IDS.items():
        assert new not in xml, f"paraId {new} 已被占用"
        row = row.replace(f'w14:paraId="{old}"', f'w14:paraId="{new}"')
    xml = xml[: anchor.end()] + row + xml[anchor.end():]

    tmp = Path(tempfile.mkstemp(suffix=".docx")[1])
    with zipfile.ZipFile(DOCX) as src, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data = xml.encode("utf-8") if item.filename == "word/document.xml" else src.read(item.filename)
            dst.writestr(item, data)
    shutil.move(tmp, DOCX)
    print(f"[ok] {DOCX.name}：「性别」行后插入「种属 | {{{{species}}}}」")
    return 0


if __name__ == "__main__":
    sys.exit(main())
