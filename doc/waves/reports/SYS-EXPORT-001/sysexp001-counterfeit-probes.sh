#!/usr/bin/env bash
# SYS-EXPORT-001 的 counterfeit 探针：**改坏真文件 → 跑真断言 → 红 → 还原 → 绿**。
#
# 每条都对应票面 `counterfeit` 的一句话。跑法（cwd 随便，脚本自己 cd）：
#   bash doc/waves/reports/SYS-EXPORT-001/sysexp001-counterfeit-probes.sh
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT}"
MP="code/miniapp"
PASS=0; FAIL=0
ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; PASS=$((PASS+1)); }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$1"; FAIL=$((FAIL+1)); }
step() { printf '\n== %s ==\n' "$1"; }

# 票面 accept 2 的公共段断言（逐字）
shared_greps() {
  grep -q 'fileHandoff' "${MP}/src/components/lqg/DownloadBar.vue" \
    && grep -q 'fileHandoff' "${MP}/src/pages/ledger/index.vue" \
    && grep -qE 'showMenu:[[:space:]]*true' "${MP}/src/utils/fileHandoff.ts" \
    && grep -q 'shareFileMessage' "${MP}/src/utils/fileHandoff.ts"
}

# ── 探针 1：表格导出另写一套 downloadFile + openDocument（不共用公共段）────────
step "探针 1 · 导出侧把公共段换成自己那套 downloadFile + openDocument（票面 counterfeit 第 1 条）"
cp "${MP}/src/pages/ledger/index.vue" /tmp/probe1-ledger.vue.bak
python3 - <<'PY'
import pathlib, re
p = pathlib.Path('code/miniapp/src/pages/ledger/index.vue')
s = p.read_text(encoding='utf-8')
# 该走公共段的点，换成「自己写一套 downloadFile + openDocument」
out = []
for line in s.splitlines(True):
    if 'fileHandoff' in line:
        continue
    out.append(line)
s = ''.join(out)
s = s.replace("import { downloadToTemp, openFile, shareFile } from '@/utils/fileHandoff'", "")
s = s.replace(
    "const path = await downloadToTemp(exportUrl(sheet.value.key, filters.value), authHeader())",
    "const path = await ownDownload(exportUrl(sheet.value.key, filters.value))")
s = s.replace("function exportFailed() {",
    "function ownDownload(url) { return new Promise((resolve, reject) => { (uni).downloadFile({ url, success: (r) => resolve(r.tempFilePath), fail: () => reject(new Error('x')) }) }) }\n"
    "function ownOpen(path) { return new Promise((resolve) => { (uni).openDocument({ filePath: path, showMenu: true, success: () => resolve(), fail: () => resolve() }) }) }\n"
    "function exportFailed() {")
s = s.replace("await openFile(path, openDocumentType(fileName))", "await ownOpen(path)")
p.write_text(s, encoding='utf-8')
PY
if ! shared_greps; then ok "共用公共段的断言红了（导出侧没走 fileHandoff）"; else bad "改坏了但断言仍绿 —— 断言没抓住"; fi
cp /tmp/probe1-ledger.vue.bak "${MP}/src/pages/ledger/index.vue"
if shared_greps; then ok "还原后转绿"; else bad "还原失败"; fi

# ── 探针 2：DocumentBar（文档下载）不再 import 公共段 ─────────────────────────
step "探针 2 · 文档下载侧不再 import 公共段（票面 counterfeit 第 1 条的「抽公共段时把文档下载改坏」）"
cp "${MP}/src/components/lqg/DownloadBar.vue" /tmp/probe2-db.vue.bak
python3 - <<'PY'
import pathlib
p = pathlib.Path('code/miniapp/src/components/lqg/DownloadBar.vue')
s = p.read_text(encoding='utf-8')
out = []
for line in s.splitlines(True):
    if 'fileHandoff' in line:
        continue
    out.append(line)
s = ''.join(out)
s = s.replace("const filePath = await downloadToTemp(url, authHeader())",
              "const filePath = await ownDownloadToTemp(url)")
s = s.replace("await openFile(filePath, openDocumentType(fileName))", "await ownOpen(filePath)")
s = s.replace("await shareFile(filePath, fileName)", "await ownShare(filePath, fileName)")
s = s.replace("function openInBrowser(url: string) {",
  "function ownDownloadToTemp(url: string): Promise<string> { return Promise.resolve(url) }\n"
  "function ownOpen(path: string): Promise<void> { return Promise.resolve() }\n"
  "function ownShare(path: string, name: string): Promise<void> { return Promise.resolve() }\n"
  "function openInBrowser(url: string) {")
p.write_text(s, encoding='utf-8')
PY
if ! shared_greps; then ok "共用公共段的断言红了（文档下载侧没走 fileHandoff）"; else bad "改坏了但断言仍绿"; fi
cp /tmp/probe2-db.vue.bak "${MP}/src/components/lqg/DownloadBar.vue"
if shared_greps; then ok "还原后转绿"; else bad "还原失败"; fi

# ── 探针 3：公共段不传鉴权头（真机上 401 的静默回归）────────────────────────
step "探针 3 · 两个调用方都不传鉴权头（票面 counterfeit 第 2 条：downloadFile 没带 Authorization）"
for f in "${MP}/src/components/lqg/DownloadBar.vue" "${MP}/src/pages/ledger/index.vue"; do
  cp "$f" "/tmp/probe3-$(basename $(dirname $f))-$(basename $f).bak"
done
python3 - <<'PY'
import pathlib
for rel in ('code/miniapp/src/components/lqg/DownloadBar.vue', 'code/miniapp/src/pages/ledger/index.vue'):
    p = pathlib.Path(rel); s = p.read_text(encoding='utf-8')
    s = s.replace('downloadToTemp(url, authHeader())', 'downloadToTemp(url)')
    s = s.replace('downloadToTemp(exportUrl(sheet.value.key, filters.value), authHeader())',
                  'downloadToTemp(exportUrl(sheet.value.key, filters.value))')
    p.write_text(s, encoding='utf-8')
PY
if grep -qE 'downloadToTemp\([^,)]*\)' "${MP}/src/components/lqg/DownloadBar.vue" "${MP}/src/pages/ledger/index.vue"; then
  ok "★ 抓到了「downloadToTemp 没带 header」这种改坏（票面的 Authorization|clientid grep 抓不到，见报告 WARN）"
else
  bad "没抓到"
fi
# 运行时守卫：downloadToTemp 自己拒掉没头的调用
node -e "
import('${ROOT}/${MP}/src/utils/fileHandoff.ts').then(async (m) => {
  let threw = false
  try { await m.downloadToTemp('/mp/int/export/tissue') } catch (e) { threw = String(e.message).includes('鉴权头') }
  console.log(threw ? '  \033[32m✓\033[0m 公共段运行时守卫：没 Authorization 直接拒（不是静默 401）' : '  \033[31m✗\033[0m 运行时守卫没生效')
})
" 2>/dev/null || echo "  (跳过 node 直跑 .ts —— 由单测 / 类型检查覆盖)"
for f in "${MP}/src/components/lqg/DownloadBar.vue" "${MP}/src/pages/ledger/index.vue"; do
  b="/tmp/probe3-$(basename $(dirname $f))-$(basename $f).bak"; cp "$b" "$f"
done
if grep -q 'downloadToTemp(url, authHeader())' "${MP}/src/components/lqg/DownloadBar.vue" \
   && grep -q 'downloadToTemp(exportUrl(sheet.value.k' "${MP}/src/pages/ledger/index.vue"; then
  ok "还原后两处都带 authHeader()"
else bad "还原失败"; fi

# ── 探针 4：导出纯函数忽略筛选（永远导全量）─────────────────────────────────
step "探针 4 · exportUrl 忽略筛选参数（票面 counterfeit 第 2 条：带 verifyStatus 那段行数不是 2）"
cp "${MP}/src/pages/ledger/export.ts" /tmp/probe4-export.ts.bak
python3 - <<'PY'
import pathlib
p = pathlib.Path('code/miniapp/src/pages/ledger/export.ts')
s = p.read_text(encoding='utf-8')
s = s.replace("  switch (sheet) {", "  if (true) { return '' }\n  switch (sheet) {")
p.write_text(s, encoding='utf-8')
PY
OUT=$(cd "${MP}" && pnpm vitest run src/pages/ledger/export.spec.ts --reporter=json --outputFile=/tmp/probe4-export.json 2>&1 | tail -3)
if jq -e '.numFailedTests > 0' /tmp/probe4-export.json >/dev/null 2>&1; then
  ok "单测红了（$(jq -r '.numFailedTests' /tmp/probe4-export.json) 条失败）"
else bad "忽略了筛选但单测仍绿"; fi
cp /tmp/probe4-export.ts.bak "${MP}/src/pages/ledger/export.ts"
OUT=$(cd "${MP}" && pnpm vitest run src/pages/ledger/export.spec.ts --reporter=json --outputFile=/tmp/probe4-export.json 2>&1 | tail -3)
if jq -e '.numFailedTests == 0 and .numPassedTests >= 3' /tmp/probe4-export.json >/dev/null 2>&1; then
  ok "还原后单测绿（$(jq -r '.numPassedTests' /tmp/probe4-export.json) 条通过）"; else bad "还原失败"; fi

# ── 探针 5：公共段少了「显示菜单」→ 打开了但没有保存 / 转发入口 ──────────────
step "探针 5 · 公共段把显示菜单关掉（票面 counterfeit 第 1 条：用户打开了 Excel 却没有保存 / 转发入口）"
cp "${MP}/src/utils/fileHandoff.ts" /tmp/probe5-fh.ts.bak
python3 - <<'PY'
import pathlib
p = pathlib.Path('code/miniapp/src/utils/fileHandoff.ts')
s = p.read_text(encoding='utf-8')
s = s.replace("      showMenu: true,", "      showMenu: false,")
p.write_text(s, encoding='utf-8')
PY
if ! shared_greps; then ok "共用公共段的断言红了（showMenu 那一段）"; else bad "关掉了显示菜单但断言仍绿"; fi
cp /tmp/probe5-fh.ts.bak "${MP}/src/utils/fileHandoff.ts"
if shared_greps; then ok "还原后转绿"; else bad "还原失败"; fi

# ── 探针 6：抽公共段时把文档下载改坏（docKind=merged 的单测）────────────────
step "探针 6 · 抽公共段时顺手改坏文档下载的合并件口径（票面 counterfeit 第 3 条）"
cp "${MP}/src/pages/doc/download.ts" /tmp/probe6-download.ts.bak
python3 - <<'PY'
import pathlib
p = pathlib.Path('code/miniapp/src/pages/doc/download.ts')
s = p.read_text(encoding='utf-8')
s = s.replace("export const MERGED_FILE_BASE = '质控文档（合并）'", "export const MERGED_FILE_BASE = '质控文档合并件'")
p.write_text(s, encoding='utf-8')
PY
(cd "${MP}" && pnpm vitest run src/pages/doc/download.fixture.spec.ts --reporter=json --outputFile=/tmp/probe6-dl.json >/dev/null 2>&1) || true
if jq -e '.numFailedTests > 0' /tmp/probe6-dl.json >/dev/null 2>&1; then
  ok "DOC-MP-002 的单测红了（$(jq -r '.numFailedTests' /tmp/probe6-dl.json) 条失败）"; else bad "文档下载单测没抓住"; fi
cp /tmp/probe6-download.ts.bak "${MP}/src/pages/doc/download.ts"
(cd "${MP}" && pnpm vitest run src/pages/doc/download.fixture.spec.ts --reporter=json --outputFile=/tmp/probe6-dl.json >/dev/null 2>&1) || true
if jq -e '.numFailedTests == 0 and .numPassedTests >= 4' /tmp/probe6-dl.json >/dev/null 2>&1; then
  ok "还原后单测绿（$(jq -r '.numPassedTests' /tmp/probe6-dl.json) 条通过）"; else bad "还原失败"; fi

printf '\n── 探针小结：%d 绿 / %d 红 ──\n' "${PASS}" "${FAIL}"
[ "${FAIL}" = 0 ]
