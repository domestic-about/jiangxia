#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DOC-PDF-001 · 出「甲方原件 vs 容器渲染」并排截图（给 DOC-PROOF-001 打样用）。

左 = `_input/templates/<原件>.docx` 在**本机**转出来的 PDF 第 1 页（本机有 macOS 的宋体/宋体替代）；
右 = 后端接口真渲染 + 真下载的 PDF 第 1 页（**Gotenberg 容器**里出的，中文字体 = Noto Serif SC）。

要看的一句话：版式（表格线、列宽、行高、文字位置）没动 —— 动的只是**字形**：
eastAsia 宋体 → Noto Serif SC（思源宋体），ascii/hAnsi Times New Roman → Tinos（度量兼容）。
拉丁字母与数字的**宽度**因此与原件一致，中文只是换了一套开源字形。

用法（cwd = 仓库根）：
    python3 doc/waves/reports/DOC-PDF-001/make-compare-shots.py
"""

import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parents[4]
OUT = REPO / "doc/waves/reports/DOC-PDF-001/compare"
RENDERED = REPO / "doc/waves/reports/DOC-PDF-001/rendered"
FONT = REPO / "code/deploy/common/gotenberg/fonts/NotoSerifSC-Regular.otf"
DPI = 110

# (输出名, 甲方原件, 本票渲染出来的 PDF)
PAIRS = [
    ("sample_qc-甲方原件-vs-容器渲染.png", "样本质控表模板.docx", "sample_qc-9000001001-internal.pdf"),
    ("organoid_qc-甲方原件-vs-容器渲染.png", "类器官质控表模板.docx", "organoid_qc-9000001001-internal.pdf"),
    ("organoid_score-甲方原件-vs-容器渲染.png", "类器官质量评分表模板.docx", "organoid_score-9000001001-internal.pdf"),
]

ORIG_DIR = Path("/tmp/lqg-orig")
TMP = Path("/tmp/lqg-compare")


def first_page_png(pdf: Path, tag: str) -> Image.Image:
    TMP.mkdir(parents=True, exist_ok=True)
    subprocess.run(["pdftoppm", "-r", str(DPI), "-png", "-f", "1", "-l", "1",
                    str(pdf), str(TMP / tag)], check=True)
    produced = sorted(TMP.glob(tag + "*.png"))[0]
    return Image.open(produced).convert("RGB")


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    font = ImageFont.truetype(str(FONT), 22)
    small = ImageFont.truetype(str(FONT), 18)
    for name, original, rendered in PAIRS:
        orig_pdf = ORIG_DIR / (Path(original).stem + ".pdf")
        if not orig_pdf.exists():
            print(f"[skip] 缺 {orig_pdf}（先用本机 LibreOffice 转一份，见脚本注释）", file=sys.stderr)
            continue
        left = first_page_png(orig_pdf, "orig")
        right = first_page_png(RENDERED / rendered, "rend")
        height = max(left.height, right.height)
        left = left.resize((int(left.width * height / left.height), height))
        right = right.resize((int(right.width * height / right.height), height))
        gap, strip = 24, 96
        canvas = Image.new("RGB", (left.width + gap + right.width, height + strip), "white")
        canvas.paste(left, (0, strip))
        canvas.paste(right, (left.width + gap, strip))
        draw = ImageDraw.Draw(canvas)
        draw.text((8, 8), "甲方原件（本机 LibreOffice）", fill="black", font=font)
        draw.text((8, 40), f"{original} · 宋体 + Times New Roman", fill="#666666", font=small)
        draw.text((left.width + gap + 8, 8), "本票渲染（Gotenberg 容器）", fill="black", font=font)
        draw.text((left.width + gap + 8, 40), f"{rendered} · Noto Serif SC + Tinos", fill="#666666", font=small)
        canvas.save(OUT / name)
        print(f"[ok] {OUT / name}  {canvas.size}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
