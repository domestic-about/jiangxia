#!/usr/bin/env python3
"""渲染出的 Word 的检查器：把 docx 当 zip 拆开，拼出全文（跨 run 拼接），数嵌入的图片。

  python3 doc/verify/docx_check.py --file x.docx [--contains 文本]… [--not-contains 文本]… [--min-images N] [--max-images N] [--no-placeholder]

  --contains / --not-contains  可重复；比较前两侧都去掉全部空白
  --min-images / --max-images  word/media/ 下的图片个数
  --no-placeholder             全文里不得残留 {{…}} 这类 poi-tl 占位符（模板变量没被填上 = 渲染漏了字段）
退出码：0 满足 / 1 不满足 / 2 文件打不开或不是 docx。
"""
import argparse
import re
import sys
import zipfile


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--file", required=True)
    ap.add_argument("--contains", action="append", default=[])
    ap.add_argument("--not-contains", action="append", default=[])
    ap.add_argument("--min-images", type=int)
    ap.add_argument("--max-images", type=int)
    ap.add_argument("--no-placeholder", action="store_true")
    a = ap.parse_args()
    try:
        z = zipfile.ZipFile(a.file)
        names = z.namelist()
        parts = [n for n in names if re.fullmatch(r"word/(document|header\d*|footer\d*|footnotes)\.xml", n)]
        xml = "".join(z.read(n).decode("utf-8") for n in parts)
    except Exception as e:  # noqa: BLE001
        sys.stderr.write(f"[error] 打不开 docx：{e}\n")
        return 2
    text = "".join(re.findall(r"<w:t[^>]*>([^<]*)</w:t>", xml))
    text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    flat = re.sub(r"\s+", "", text)
    images = [n for n in names if n.startswith("word/media/")]
    bad = []
    for c in a.contains:
        if re.sub(r"\s+", "", c) not in flat:
            bad.append(f"缺少文本：{c}")
    for c in a.not_contains:
        if re.sub(r"\s+", "", c) in flat:
            bad.append(f"不该出现的文本：{c}")
    if a.min_images is not None and len(images) < a.min_images:
        bad.append(f"图片 {len(images)} 张，少于 {a.min_images}")
    if a.max_images is not None and len(images) > a.max_images:
        bad.append(f"图片 {len(images)} 张，多于 {a.max_images}")
    if a.no_placeholder and re.search(r"\{\{[^}]*\}\}", text):
        bad.append(f"残留占位符：{re.findall(r'{{[^}]*}}', text)[:5]}")
    if bad:
        print("\n".join("✗ " + b for b in bad))
        return 1
    print(f"✓ 全文 {len(flat)} 字，图片 {len(images)} 张")
    return 0


if __name__ == "__main__":
    sys.exit(main())
