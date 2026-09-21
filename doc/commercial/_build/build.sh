#!/usr/bin/env bash
# 出合同的 docx（装了 LibreOffice 则同时出 pdf）。管线沿用 health-monitor：md_prep → pandoc → format_docx。
# 用法：bash doc/commercial/_build/build.sh
set -euo pipefail
cd "$(dirname "$0")/.."   # doc/commercial
OUT=_build/out; mkdir -p "$OUT"
MD="类器官送检与样本管理系统开发合同.md"; BASE="${MD%.md}"
python3 _build/md_prep.py "$MD"
pandoc "$MD" -f markdown+east_asian_line_breaks-smart -t docx --resource-path=. -o "$OUT/$BASE.docx" 2> "_build/pandoc.log" || { cat _build/pandoc.log; exit 1; }
python3 _build/format_docx.py "$OUT/$BASE.docx" --footer "类器官送检与样本管理系统开发合同 · ZH-2026-LQG-SJ" --title "类器官送检与样本管理系统开发合同" --subject "ZH-2026-LQG-SJ"
echo "built $OUT/$BASE.docx"
for c in soffice /Applications/LibreOffice.app/Contents/MacOS/soffice; do
  if command -v "$c" >/dev/null 2>&1 || [ -x "$c" ]; then "$c" --headless --convert-to pdf --outdir "$OUT" "$OUT/$BASE.docx" >/dev/null 2>&1 && echo "pdf exported" || echo "pdf export failed (docx still fine)"; break; fi
done
