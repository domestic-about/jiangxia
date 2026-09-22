#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DOC-PDF-001 · 把三份模板里的「宋体 / Times New Roman」换成容器里装得下的开源字体。

为什么必须换（ticket §0 口径复述 1）：甲方模板的 eastAsia 字体是**宋体**，Linux 容器里没有；
不换的话 LibreOffice 回退到 DejaVu Sans —— 中文全是方框、pdftotext 抽不出字（accept 1 前两段红）。

替换表（字体文件与许可都在 code/deploy/common/gotenberg/fonts/）：
  eastAsia : 宋体 / SimSun / NSimSun          → Noto Serif SC   （思源宋体，OFL-1.1）
  ascii/hAnsi/cs : Times New Roman / 宋体     → Tinos           （Times New Roman 度量兼容，OFL-1.1）
  ★ Latin 一起换的原因：容器里没有 Times New Roman，LibreOffice 会回退到 **Liberation Serif**，
    而 accept 1 明写 `! pdffonts | grep -qiE 'DejaVu|Liberation'`。Tinos 与 Times 同度量，
    版式不漂、与甲方原件的差异只剩「拉丁字形本身」。

做法跟 DOC-RENDER-001 的 make-doc-templates.py 同一路子：**在甲方原件上就地改属性**，
不重画、不动任何文字与表格结构（tblGrid 逐字节不变由探针证明）。
改完把 template-version.txt 加一（REQ-DOC-011：换模板 = 换文件 + 版本号加一，指纹随之全量失效重出）。

用法：
    python3 doc/waves/reports/DOC-PDF-001/make-font-templates.py --check   # 只看现状，不写
    python3 doc/waves/reports/DOC-PDF-001/make-font-templates.py           # 就地改模板 + 版本号 +1
"""

import argparse
import re
import shutil
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
TPL_DIR = REPO / "code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/resources/lqg/doc-templates"
VERSION_FILE = TPL_DIR / "template-version.txt"

CJK_FONT = "Noto Serif SC"
LATIN_FONT = "Tinos"

CJK_NAMES = {"宋体", "SimSun", "NSimSun", "新宋体", "思源宋体", "Source Han Serif SC",
             "Noto Serif CJK SC", "Noto Serif SC"}
LATIN_NAMES = {"Times New Roman", "Tinos", "Cambria", "Georgia", "宋体", "SimSun"}

# 属性名 → 目标字体；主题字体（asciiTheme / eastAsiaTheme …）一律展开成显式字体名：
# 主题字体最终会落到 theme1.xml 的 minorFont（Calibri 一类），容器里同样没有。
ATTRS = ("ascii", "hAnsi", "cs", "eastAsia")
THEME_ATTRS = ("asciiTheme", "hAnsiTheme", "eastAsiaTheme", "cstheme")

RFONTS_RE = re.compile(r"<w:rFonts\b[^>]*/?>")
ATTR_RE = re.compile(r'([\w:]+)="([^"]*)"')


def rewrite_rfonts(tag: str):
    """把一个 <w:rFonts .../> 重写成显式开源字体；返回（新标签, 改动说明或 None）。"""
    attrs = dict(ATTR_RE.findall(tag))
    if not attrs:
        return tag, None
    new = {}
    if "w:hint" in attrs:
        new["w:hint"] = attrs["w:hint"]
    new["w:ascii"] = LATIN_FONT
    new["w:hAnsi"] = LATIN_FONT
    new["w:eastAsia"] = CJK_FONT
    new["w:cs"] = LATIN_FONT
    body = " ".join(f'{k}="{v}"' for k, v in new.items())
    new_tag = f"<w:rFonts {body}/>"
    if new_tag == tag:
        return tag, None
    return new_tag, f"{tag}  →  {new_tag}"


def rewrite_theme(text: str, part: str, changes: list):
    """主题字体（theme1.xml 的 majorFont/minorFont）也指向容器里装得下的字体。

    这一步是**兜底**：所有 `<w:rFonts>`（含 styles.xml 的 docDefaults）已经显式写死了
    Tinos / Noto Serif SC，主题字体本来不会再生效；但留一份「主题字体 = Calibri / 宋体」
    在包里，将来谁加一个不带 rFonts 的 run 就会悄悄回退到容器里没有的字体 → 又变成方框或
    Liberation。改掉它 = 把这条退路也堵死。
    """
    def sub(pattern, repl):
        nonlocal text
        new, n = re.subn(pattern, repl, text)
        if n:
            changes.append((part, f"{pattern} × {n} → {repl}"))
            text = new

    sub(r'(<a:latin typeface=")[^"]*(")', rf"\g<1>{LATIN_FONT}\g<2>")
    sub(r'(<a:cs typeface=")[^"]*(")', rf"\g<1>{LATIN_FONT}\g<2>")
    sub(r'(<a:ea typeface=")[^"]*(")', rf"\g<1>{CJK_FONT}\g<2>")
    # 简体中文的 script 字体（Hans）就是「宋体」那一格
    sub(r'(<a:font script="Hans" typeface=")[^"]*(")', rf"\g<1>{CJK_FONT}\g<2>")
    return text


def process_docx(path: Path):
    zin = zipfile.ZipFile(path)
    items = zin.infolist()
    out_items = []
    changes = []
    for info in items:
        data = zin.read(info.filename)
        if info.filename.endswith(".xml"):
            text = data.decode("utf-8")

            def repl(m):
                new_tag, desc = rewrite_rfonts(m.group(0))
                if desc:
                    changes.append((info.filename, desc))
                return new_tag

            new_text = RFONTS_RE.sub(repl, text)
            if info.filename.startswith("word/theme/"):
                new_text = rewrite_theme(new_text, info.filename, changes)
            if new_text != text:
                data = new_text.encode("utf-8")
        out_items.append((info, data))
    zin.close()
    return out_items, changes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="只打印现状 / 将要做的改动，不写文件")
    args = ap.parse_args()

    version = VERSION_FILE.read_text(encoding="utf-8").strip()
    print(f"模板目录：{TPL_DIR}")
    print(f"当前 template-version = {version}")

    for name in ("sample_qc.docx", "organoid_qc.docx", "organoid_score.docx"):
        path = TPL_DIR / name
        z = zipfile.ZipFile(path)
        before = {}
        for n in z.namelist():
            if n.endswith(".xml"):
                t = z.read(n).decode("utf-8")
                for m in RFONTS_RE.finditer(t):
                    before[m.group(0)] = before.get(m.group(0), 0) + 1
        z.close()
        print(f"\n════ {name}（改前 {len(before)} 种 rFonts）")
        for tag, cnt in sorted(before.items(), key=lambda kv: -kv[1]):
            print(f"   {cnt:4d}  {tag}")

    if args.check:
        print("\n[--check] 未写任何文件")
        return 0

    for name in ("sample_qc.docx", "organoid_qc.docx", "organoid_score.docx"):
        path = TPL_DIR / name
        items, changes = process_docx(path)
        tmp = path.with_suffix(".docx.new")
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
            for info, data in items:
                zout.writestr(info, data)
        shutil.move(str(tmp), str(path))
        print(f"\n════ {name}：改写 {len(changes)} 处 rFonts")
        for part, desc in changes[:6]:
            print(f"   [{part}] {desc}")
        if len(changes) > 6:
            print(f"   … 其余 {len(changes) - 6} 处同类")

    new_version = str(int(version) + 1)
    VERSION_FILE.write_text(new_version + "\n", encoding="utf-8")
    print(f"\ntemplate-version：{version} → {new_version}（换模板必须加一，指纹随之全量失效重出）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
