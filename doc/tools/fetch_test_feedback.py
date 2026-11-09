#!/usr/bin/env python3
"""只读拉取江夏飞书测试表：CSV 原文、行号、XLSX、逐单元格截图。

使用：python3 doc/tools/fetch_test_feedback.py --output /tmp/jiangxia-feedback
不写飞书；不改 CLI 账号或授权。输出不要提交到仓库。
"""
import argparse
import csv
import io
import json
import os
from pathlib import Path
import posixpath
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

URL = "https://ncn59pmtdck5.feishu.cn/sheets/MTY3sEbKDhgqnxthuYHcwm08nMg"
SHEETS = {"小程序": "3654e3", "网页工作台": "8MBuHU"}
MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"


def cli(args, cwd):
    # 与 dongjiaoshan 的 feishu_client._lark 一致：本工具使用已有 CLI bot 配置，
    # 不让 shell 为另一应用导出的 HERMES_HOME 误选 Hermes 上下文。
    env = {k: v for k, v in os.environ.items() if k != "HERMES_HOME"}
    env["no_proxy"] = "*"
    result = subprocess.run(["lark-cli", *args, "--as", "bot"], cwd=cwd,
                            env=env, capture_output=True, text=True, check=True)
    payload = json.loads(result.stdout)
    if not payload.get("ok"):
        raise RuntimeError(payload.get("error"))
    return payload


def relationships(z, source):
    return {e.get("Id"): e.get("Target") for e in ET.fromstring(z.read(source))}


def resolve(base, target):
    return target.lstrip("/") if target.startswith("/") else posixpath.normpath(posixpath.join(base, target))


def extract_images(xlsx, output):
    """支持 drawing anchors 以及 2026-11 导出的 Excel richData 单元格图。"""
    output.mkdir(exist_ok=True)
    index = []
    with zipfile.ZipFile(xlsx) as z:
        sheets = ET.fromstring(z.read("xl/workbook.xml")).find(f"{{{MAIN}}}sheets")
        sheet_rels = relationships(z, "xl/_rels/workbook.xml.rels")
        rich = {}
        if "xl/richData/richValueRel.xml" in z.namelist():
            media_rels = relationships(z, "xl/richData/_rels/richValueRel.xml.rels")
            media = [resolve("xl/richData", media_rels[e.get(f"{{{REL}}}id")])
                     for e in ET.fromstring(z.read("xl/richData/richValueRel.xml"))]
            values = [int(list(e)[0].text) for e in ET.fromstring(z.read("xl/richData/rdrichvalue.xml"))]
            metadata = ET.fromstring(z.read("xl/metadata.xml"))
            future = metadata.find(f"{{{MAIN}}}futureMetadata[@name='XLRICHVALUE']")
            future_ids = [int(next(e.iter("{http://schemas.microsoft.com/office/spreadsheetml/2017/richdata}rvb")).get("i"))
                          for e in future]
            for number, block in enumerate(metadata.find(f"{{{MAIN}}}valueMetadata"), 1):
                record = block.find(f"{{{MAIN}}}rc")
                rich[number] = media[values[future_ids[int(record.get("v"))]]]

        def save(sheet, cell, media):
            dest = output / f"{sheet}-{cell}-{len(index) + 1}{Path(media).suffix}"
            dest.write_bytes(z.read(media))
            index.append({"sheet": sheet, "cell": cell, "path": str(dest)})

        for sheet in sheets:
            name = sheet.get("name")
            if name not in SHEETS:
                continue
            source = resolve("xl", sheet_rels[sheet.get(f"{{{REL}}}id")])
            root = ET.fromstring(z.read(source))
            for cell in root.iter(f"{{{MAIN}}}c"):
                if cell.get("vm"):
                    save(name, cell.get("r"), rich[int(cell.get("vm"))])
            rel_file = posixpath.join(posixpath.dirname(source), "_rels", posixpath.basename(source) + ".rels")
            if rel_file not in z.namelist():
                continue
            rels = relationships(z, rel_file)
            for drawing in root.iter(f"{{{MAIN}}}drawing"):
                source_drawing = resolve(posixpath.dirname(source), rels[drawing.get(f"{{{REL}}}id")])
                base = posixpath.dirname(source_drawing)
                dr = relationships(z, posixpath.join(base, "_rels", posixpath.basename(source_drawing) + ".rels"))
                for anchor in ET.fromstring(z.read(source_drawing)):
                    ns = "{http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing}"
                    origin = anchor.find(ns + "from")
                    if origin is None:
                        continue
                    row, col = int(origin.find(ns + "row").text) + 1, int(origin.find(ns + "col").text)
                    letters = ""
                    col += 1
                    while col:
                        col, remainder = divmod(col - 1, 26)
                        letters = chr(65 + remainder) + letters
                    for blip in anchor.iter("{http://schemas.openxmlformats.org/drawingml/2006/main}blip"):
                        save(name, f"{letters}{row}", resolve(base, dr[blip.get(f"{{{REL}}}embed")]))
    return index


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    pending = []
    for name, sheet_id in SHEETS.items():
        response = cli(["sheets", "+csv-get", "--url", URL, "--sheet-id", sheet_id, "--range", "A1:J80"], output)
        (output / f"{name}.json").write_text(json.dumps(response, ensure_ascii=False, indent=2))
        data = response["data"]
        if data["has_more"]:
            raise RuntimeError("取数未完整，禁止按部分结果修复")
        region = data.get("current_region", "")
        end_row = re.search(r"(\d+)$", region)
        if end_row and int(end_row[1]) > 80:
            raise RuntimeError(f"表格有效区域 {region} 超过 A1:J80，请扩大读取范围后重试")
        rows = list(csv.reader(io.StringIO(data["annotated_csv"])))
        if rows[0][2:5] != ["问题描述", "问题截图", "需求截图"] or rows[0][7:9] != ["解决方式", "备注"]:
            raise RuntimeError("表头发生变化，请先核对列语义")
        for row in rows[1:]:
            match = re.match(r"\[row=(\d+)\]", row[0])
            if not match:
                raise RuntimeError("缺少权威行号")
            if row[2].strip() and not row[7].strip():
                pending.append({"sheet": name, "row": int(match[1]), "description": row[2], "revision": data["revision"]})
    cli(["sheets", "+workbook-export", "--url", URL, "--output-path", "test-issues.xlsx"], output)
    images = extract_images(output / "test-issues.xlsx", output / "shots")
    for entry in pending:
        entry["images"] = [i for i in images if i["sheet"] == entry["sheet"] and i["cell"][1:] == str(entry["row"])]
    (output / "pending.json").write_text(json.dumps(pending, ensure_ascii=False, indent=2))
    (output / "image-index.json").write_text(json.dumps(images, ensure_ascii=False, indent=2))
    print(json.dumps({"pending": pending, "image_count": len(images), "output": str(output)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
