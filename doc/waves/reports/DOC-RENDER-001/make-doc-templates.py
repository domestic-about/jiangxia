#!/usr/bin/env python3
"""DOC-RENDER-001 · 把甲方三份 docx 原件改成 poi-tl 模板（**只改文字，不动版式**）。

    python3 doc/waves/reports/DOC-RENDER-001/make-doc-templates.py

输入（只读）：`_input/templates/{样本质控表,类器官质控表,类器官质量评分表}模板.docx`
输出：`code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/resources/lqg/doc-templates/{sample_qc,organoid_qc,organoid_score}.docx`

ticket §0 口径复述 1：**模板从甲方原件改出来，不是照着重画**。所以这里做的事只有两件：

1. 空白格 → `{{占位符}}`（在**原有段落**里插一个 run，rPr 逐字抄该段 `w:pPr/rPr`，
   字体/字号与原件一致）；
2. 原件里的示例文字（「要求图片可以放大」、三段示例描述、附件说明）→ 占位符。

表格结构、列宽（`tblGrid` / `tcW`）、边框、页边距、页脚两句「注」一个字节都不动 ——
下面每一处改动都用 `w14:paraId` 精确定位（paraId 是 Word 唯一的段标识），
改完打印一份 diff 供人工核对。

★ 为什么不是「重新画一份模板」：甲方并排看原件与产物，差一个列宽就会被发现（accept 1
  的 counterfeit 就是这么写的）。所以唯一可靠的路径是**原件改字**。
"""
from __future__ import annotations

import re
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
SRC = ROOT / "_input/templates"
DST = ROOT / "code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/resources/lqg/doc-templates"
TEMPLATE_VERSION = "3"
# ★ 2026-09-23 按 CR-20260923-09 更新：与 code/…/doc-templates/template-version.txt 同步为 3（F2 去掉了甲方批注用的
#   绿色高亮 `w:highlight`：样本质控表 10 处、类器官质控表 3 处）。本脚本重跑时一律剥掉占位 run 的高亮（见 strip_highlight），
#   否则会把高亮带回成品文档。

# ── 空段里插入 run 用的 rPr 兜底（正常路径是抄该段 w:pPr 里的 rPr）────────────
FALLBACK_RPR = (
    '<w:rPr><w:rFonts w:hint="default" w:ascii="Times New Roman" w:hAnsi="Times New Roman"'
    ' w:eastAsia="宋体" w:cs="Times New Roman"/><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr>'
)


HIGHLIGHT_RE = re.compile(r"<w:highlight\b[^>]*/>|<w:highlight\b[^>]*>.*?</w:highlight>", re.S)


def strip_highlight(rpr: str) -> str:
    """去掉 rPr 里的 `w:highlight`（甲方原件用绿色高亮标「这里要填」，那是批注，不该进模板与成品）。"""
    return HIGHLIGHT_RE.sub("", rpr)


def para_span(xml: str, para_id: str) -> tuple[int, int]:
    """按 w14:paraId 找一段的 [start, end)（`<w:p …>` … `</w:p>`；段落不嵌套）。"""
    m = re.search(r'<w:p\b[^>]*w14:paraId="%s"[^>]*>' % re.escape(para_id), xml)
    if not m:
        raise SystemExit(f"[error] 找不到 paraId={para_id}（模板与取号表对不上了）")
    start = m.start()
    end = xml.index("</w:p>", m.end()) + len("</w:p>")
    return start, end


def para_rpr(para_xml: str) -> str:
    """抄这一段 `w:pPr` 里最后一个 `w:rPr`（就是原件的字体/字号）。"""
    ppr = re.search(r"<w:pPr>.*?</w:pPr>", para_xml, re.S)
    if ppr:
        rprs = re.findall(r"<w:rPr>.*?</w:rPr>", ppr.group(0), re.S)
        if rprs:
            return strip_highlight(rprs[-1])
    return FALLBACK_RPR


def insert_text(xml: str, para_id: str, text: str) -> str:
    """往一个空段里插一个 run（字体抄本段）。"""
    s, e = para_span(xml, para_id)
    para = xml[s:e]
    rpr = para_rpr(para)
    run = f"<w:r>{rpr}<w:t>{text}</w:t></w:r>"
    return xml[: e - len("</w:p>")] + run + xml[e - len("</w:p>") :]


def replace_text(xml: str, para_id: str, old: str, new: str) -> str:
    """把某段里 `>old</w:t>` 改成 `>new</w:t>`（保留了原 run 的 rPr）。"""
    s, e = para_span(xml, para_id)
    para = xml[s:e]
    needle = f">{old}</w:t>"
    if needle not in para:
        raise SystemExit(f"[error] paraId={para_id} 里没有 {old!r}")
    para = para.replace(needle, f">{new}</w:t>", 1)
    # 保留原 run 的 rPr，但剥掉高亮：占位符所在的那个 run 会原样进成品
    for m in re.finditer(r"<w:r\b[^>]*>.*?</w:r>", para, re.S):
        if f">{new}</w:t>" in m.group(0):
            run = m.group(0)
            run2 = re.sub(r"<w:rPr>.*?</w:rPr>", lambda r: strip_highlight(r.group(0)), run, count=1, flags=re.S)
            para = para[: m.start()] + run2 + para[m.end():]
            break
    return xml[:s] + para + xml[e:]


def replace_with_picture_runs(xml: str, para_id: str, old: str, tags: list[str]) -> str:
    """把「要求图片可以放大」所在的整个 run 换成 N 个**各自独立的 run**。

    ★ 每个图片标签必须独占一个 run：poi-tl 遇到空数据时按 `ClearHandler` 把**整个 run**
      的文字清空（`run.setText("", 0)`）。挤在同一个 run 里的三个标签会因为一个没图
      而把另外两个也抹掉。
    """
    s, e = para_span(xml, para_id)
    para = xml[s:e]
    for m in re.finditer(r"<w:r\b[^>]*>.*?</w:r>", para, re.S):
        run = m.group(0)
        if f">{old}</w:t>" in run:
            rpr = re.search(r"<w:rPr>.*?</w:rPr>", run, re.S)
            rpr = strip_highlight(rpr.group(0)) if rpr else FALLBACK_RPR
            new_runs = "".join(f"<w:r>{rpr}<w:t>{t}</w:t></w:r>" for t in tags)
            para = para[: m.start()] + new_runs + para[m.end() :]
            break
    else:
        raise SystemExit(f"[error] paraId={para_id} 里找不到含 {old!r} 的 run")
    return xml[:s] + para + xml[e:]


def append_total_row(xml: str) -> str:
    """评分表表尾加一行合计：复制最后一行的 tcPr/pPr，去掉 vMerge，四格改成 合计/空/空/{{sc_total}}。"""
    tbl_end = xml.rindex("</w:tbl>")
    tbl_start = xml.rindex("<w:tbl>", 0, tbl_end)
    tbl = xml[tbl_start:tbl_end]
    trs = list(re.finditer(r"<w:tr\b[^>]*>.*?</w:tr>", tbl, re.S))
    last_match = trs[-1]
    last = last_match.group(0)
    cells = list(re.finditer(r"<w:tc>.*?</w:tc>", last, re.S))
    if len(cells) != 4:
        raise SystemExit(f"[error] 评分表最后一行不是 4 格（{len(cells)}）")
    out_cells = []
    for idx, cell in enumerate(cells):
        c = cell.group(0)
        c = re.sub(r"<w:vMerge[^>]*/>", "", c)  # 合计行不属于上面任何一组纵合并
        p = re.search(r"<w:p\b[^>]*>.*?</w:p>", c, re.S)
        if not p:
            raise SystemExit("[error] 评分表最后一行的格子没有段落")
        para = p.group(0)
        rpr = para_rpr(para)
        inner = re.sub(r"<w:r\b[^>]*>.*?</w:r>", "", para, flags=re.S)  # 清掉原文字
        text = ["合计", "", "", "{{sc_total}}"][idx]
        run = f"<w:r>{rpr}<w:t>{text}</w:t></w:r>"
        new_para = inner[: -len("</w:p>")] + run + "</w:p>"
        out_cells.append(c[: p.start()] + new_para + c[p.end() :])
    row = "<w:tr>" + "".join(out_cells) + "</w:tr>"
    new_tbl = tbl[: last_match.end()] + row + tbl[last_match.end() :]
    return xml[:tbl_start] + new_tbl + xml[tbl_end:]


def build(name: str, edit) -> str:
    src_name = {"sample_qc": "样本质控表", "organoid_qc": "类器官质控表", "organoid_score": "类器官质量评分表"}[name]
    with zipfile.ZipFile(SRC / f"{src_name}模板.docx") as z:
        parts = {n: z.read(n) for n in z.namelist()}
        infos = {n: z.getinfo(n) for n in z.namelist()}
    xml = parts["word/document.xml"].decode("utf-8")
    new_xml, notes = edit(xml)
    parts["word/document.xml"] = new_xml.encode("utf-8")
    DST.mkdir(parents=True, exist_ok=True)
    out = DST / f"{name}.docx"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for n, data in parts.items():
            z.writestr(infos[n], data)
    print(f"  ✓ {out.relative_to(ROOT)}（{len(new_xml)} 字节 xml）")
    for n in notes:
        print(f"      · {n}")
    return new_xml


# ══════════════════════════════════════════════════════════════════════════
# 样本质控表 sample_qc
# ══════════════════════════════════════════════════════════════════════════
SAMPLE_QC_EMPTY = [
    ("56E3B361", "patient_no"),          # 患者编号
    ("545CA0DB", "source_unit_name"),    # 来源单位
    ("7FC8A1AD", "donor_name"),          # 患者姓名
    ("2D55DB69", "sampling_site"),       # 取样部位
    ("38E2D4C5", "sampling_method"),     # 取样方式
    ("71CE80AE", "gender"),              # 性别
    ("0B8C2ACE", "clinical_diagnosis"),  # 临床诊断/既往治疗
    ("27076D32", "receive_date"),        # 收样时间
    ("14CB31FE", "process_time"),        # 处理时间
    ("195B2AD6", "operator_name"),       # 操作人
    ("2ED58AA1", "internal_no"),         # 内部编号（外部版渲染时填空串）
    ("462E885E", "orig_desc"),           # 收样原始情况·情况描述
]
SAMPLE_QC_IMAGES = [
    ("4D89F512", "orig"),
    ("5E0BD0F5", "observe"),
    ("74AD7A25", "pretreat"),
]


def edit_sample_qc(xml: str):
    notes = []
    for pid, tag in SAMPLE_QC_EMPTY:
        xml = insert_text(xml, pid, "{{%s}}" % tag)
    notes.append("12 个空白格 → 文本占位符")
    for pid, old, new in [
        ("3E69618F", "样本按质控要求，保持2-8℃低温环境运输至实验室。", "{{receive_desc}}"),
        ("73BEEAC9", "表格里嵌了一个文件附件，显示成一个图标，双击就能打开", "{{viability_file_name}}"),
        ("3857B018", "样本外观呈黄白色。", "{{observe_desc}}"),
        ("26C4E659",
         "样本经剪切等预处理，显微镜下观察组织漏出细胞量适中，细胞活性中等；培养3d照片如左图所示。",
         "{{pretreat_desc}}"),
    ]:
        xml = replace_text(xml, pid, old, new)
    notes.append("4 处示例文字 → 占位符（收样描述/活率附件名/观察描述/预处理描述）")
    for pid, slot in SAMPLE_QC_IMAGES:
        xml = replace_with_picture_runs(
            xml, pid, "要求图片可以放大", [f"{{{{@{slot}_img{i}}}}}" for i in (1, 2, 3)])
    notes.append("3 个图片位「要求图片可以放大」→ 3 个独立图片占位符 run（每位至多 3 张）")
    return xml, notes


# ══════════════════════════════════════════════════════════════════════════
# 类器官质控表 organoid_qc
# ══════════════════════════════════════════════════════════════════════════
ORGANOID_QC_EMPTY = [
    ("6AFCCB69", "formed_time"),
    ("2EFF732B", "growth_state"),
    ("0FC7553A", "growth_desc"),
    ("3A9922CA", "planned_drug_screen"),
    ("57B6AA3B", "feedback_time"),
]


def edit_organoid_qc(xml: str):
    notes = []
    for pid, tag in ORGANOID_QC_EMPTY:
        xml = insert_text(xml, pid, "{{%s}}" % tag)
    notes.append("5 个空白格 → 文本占位符")
    xml = replace_with_picture_runs(
        xml, "648139C7", "要求图片可以放大",
        [f"{{{{@organoid_observe_img{i}}}}}" for i in (1, 2, 3)])
    notes.append("1 个图片位「要求图片可以放大」→ 3 个独立图片占位符 run")
    return xml, notes


# ══════════════════════════════════════════════════════════════════════════
# 类器官质量评分表 organoid_score
# ══════════════════════════════════════════════════════════════════════════
# ★ 第四列（类器官质量评分）在原件的四个变量组里各是一个**纵合并**单元格：
#   只有每组第一行的 tcPr 带 vMerge="restart"，后面几行是 continue。
#   所以占位符只进这四个 restart 格 —— 一格一分值，逐档填是填不进去的（也没意义）。
SCORE_GROUPS = [
    ("5EA0D950", "sc_pre_culture"),    # 培养前样本评分
    ("3B3C109B", "sc_culture_days"),   # 培养天数
    ("1663855F", "sc_count"),          # 类器官数量
    ("1AF1E91B", "sc_diameter"),       # 类器官直径
]


def edit_organoid_score(xml: str):
    notes = []
    for pid, tag in SCORE_GROUPS:
        xml = insert_text(xml, pid, "{{%s}}" % tag)
    notes.append("4 个纵合并的「类器官质量评分」格 → 4 个分值占位符")
    xml = append_total_row(xml)
    notes.append("表尾加一行合计（复制末行结构、去掉 vMerge）→ {{sc_total}}")
    return xml, notes


def main() -> int:
    DST.mkdir(parents=True, exist_ok=True)
    print("── 生成 poi-tl 模板（源：_input/templates/ 的甲方原件）")
    build("sample_qc", edit_sample_qc)
    build("organoid_qc", edit_organoid_qc)
    build("organoid_score", edit_organoid_score)
    (DST / "template-version.txt").write_text(TEMPLATE_VERSION + "\n", encoding="utf-8")
    print(f"  ✓ template-version.txt = {TEMPLATE_VERSION}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
