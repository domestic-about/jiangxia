// D7 L2 · 场景 C：工作台（8093）质控文档编辑页右栏「预览面板」
//   · 内外部版切换   · 四个下载按钮真下载   · 完成前后页签徽标变化  （DOC-PUBLISH-001）
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'
const require = createRequire('/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/code/miniapp/package.json')
const { chromium } = require('playwright')

const WEB = 'http://127.0.0.1:8093'
const OUT = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/.tmp/qa-d7-L2'
const SHOTS = path.join(OUT, 'shots')
fs.mkdirSync(SHOTS, { recursive: true })

const results = []
const rec = (name, ok, detail = '') => {
  results.push({ name, ok: !!ok, detail: String(detail).slice(0, 1500) })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  :: ' + String(detail).slice(0, 420) : ''}`)
}
const net = [], jsErrors = []
const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1100 }, locale: 'zh-CN' })
const page = await ctx.newPage()
page.on('pageerror', e => jsErrors.push('pageerror: ' + e.message))
page.on('console', m => { if (m.type() === 'error') jsErrors.push('console.error: ' + m.text().slice(0, 200)) })
page.on('response', async (r) => {
  const u = r.url()
  if (/\/lqg\/doc\//.test(u)) {
    let body = ''; try { body = (await r.text()).slice(0, 900) } catch { }
    net.push({ status: r.status(), method: r.request().method(), url: u.replace(WEB, ''), body })
  }
})
// OSS 直连（下载按钮 window.open 的落点）
const oss = []
ctx.on('response', r => { if (r.url().includes('127.0.0.1:9000')) oss.push({ status: r.status(), url: r.url().split('?')[0], ct: r.headers()['content-type'] || '' }) })

// 登录（dev captcha.enable=false）
await page.goto(WEB + '/login', { waitUntil: 'domcontentloaded' })
await page.waitForSelector('input[type="text"]', { timeout: 30000 })
await page.fill('input[type="text"]', 'lqgadmin')
await page.fill('input[type="password"]', 'admin123')
await page.getByRole('button', { name: /登\s*录/ }).first().click()
await page.waitForURL(u => !u.pathname.includes('/login'), { timeout: 30000 })
rec('C0:工作台登录 lqgadmin', true, page.url())

// ── C1 1001：三个页签都是「已完成」徽标 ────────────────────────────────
await page.goto(`${WEB}/qc-console/qc-editor?sampleId=9000001001`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.lqg-qc-editor__tabs', { timeout: 30000 })
await page.waitForTimeout(2500)
const tabLabels = (await page.locator('.lqg-qc-editor__tab-label').allInnerTexts()).map(s => s.replace(/\s+/g, ''))
rec('C1:三个页签徽标=已完成', tabLabels.length === 3 && tabLabels.every(t => t.includes('已完成')), JSON.stringify(tabLabels))
await page.screenshot({ path: path.join(SHOTS, 'C-01-editor-published.png'), fullPage: true })

// ── C2 预览面板：点「预览」→ 内部版页面图 ────────────────────────────────
const beforeC2 = net.length
await page.getByRole('button', { name: /预览/ }).first().click()
await page.waitForSelector('.lqg-preview-pane__page-img img', { timeout: 40000 })
await page.waitForTimeout(2000)
const renderReq = net.slice(beforeC2).filter(x => /render/.test(x.url))
rec('C2:点「预览」触发 render（内部版）', renderReq.length >= 1 && /audience=internal/.test(renderReq[0].url), JSON.stringify(renderReq.map(x => x.url)))
const imgsInternal = await page.locator('.lqg-preview-pane__page-img img').evaluateAll(els => els.map(e => e.getAttribute('src')))
rec('C2:预览面板逐页显示页面图（2 页，内部版对象键 /internal/）',
  imgsInternal.length === 2 && imgsInternal.every(u => u.includes('/internal/')),
  JSON.stringify(imgsInternal.map(u => u.split('?')[0].split('/').slice(-2).join('/'))))
const hintInternal = await page.locator('.lqg-preview-pane__audience-hint').innerText()
rec('C2:面板标题/提示显示「内部版」', /内部版/.test(hintInternal), hintInternal)
await page.screenshot({ path: path.join(SHOTS, 'C-02-preview-internal.png'), fullPage: true })

// ── C3 切「外部版」→ 重新取页面图（/external/） ──────────────────────────
const beforeC3 = net.length
await page.locator('.lqg-preview-pane__bar .el-radio-button', { hasText: '外部版' }).first().click()
await page.waitForTimeout(3500)
const imgsExternal = await page.locator('.lqg-preview-pane__page-img img').evaluateAll(els => els.map(e => e.getAttribute('src')))
const extReq = net.slice(beforeC3).filter(x => /\/pages/.test(x.url))
rec('C3:切外部版 → 重新取 pages（audience=external）', extReq.some(x => /audience=external/.test(x.url)), JSON.stringify(extReq.map(x => x.url)))
rec('C3:外部版页面图对象键 /external/（与内部版是两份独立产物）',
  imgsExternal.length === 2 && imgsExternal.every(u => u.includes('/external/')),
  JSON.stringify(imgsExternal.map(u => u.split('?')[0].split('/').slice(-2).join('/'))))
const hintExternal = await page.locator('.lqg-preview-pane__audience-hint').innerText()
rec('C3:面板提示切到「外部版」', /外部版/.test(hintExternal), hintExternal)
await page.screenshot({ path: path.join(SHOTS, 'C-03-preview-external.png'), fullPage: true })

// ── C4 四个下载按钮：真下载（GET download → 签名链接 → 新标签打开 → OSS 200） ──
const downloadBtns = await page.locator('.lqg-preview-pane__downloads-row button').allInnerTexts()
rec('C4:下载区恰好四个按钮', downloadBtns.length === 4, JSON.stringify(downloadBtns))
const cases = [['Word', 'docx', false], ['PDF', 'pdf', false], ['合并 Word', 'docx', true], ['合并 PDF', 'pdf', true]]
for (const [label, fmt, merged] of cases) {
  const before = net.length; const beforeOss = oss.length
  const popupP = ctx.waitForEvent('page', { timeout: 20000 }).catch(() => null)
  await page.locator('.lqg-preview-pane__downloads-row button').filter({ hasText: label }).first().click()
  await page.waitForTimeout(3500)
  const reqs = net.slice(before).filter(x => /\/download\?/.test(x.url))
  const one = reqs.pop()
  let body = null; try { body = JSON.parse(one.body) } catch { }
  rec(`C4:「${label}」→ GET download?format=${fmt} 且 code=200`,
    !!one && one.url.includes(`format=${fmt}`) && (one.url.includes('/merged/') === merged) && one.status === 200 && body?.code === 200,
    one ? one.url : 'no request')
  rec(`C4:「${label}」返回签名链接（非空）`,
    !!body?.data?.url, String(body?.data?.url || '').split('?')[0])
  const popup = await popupP
  const popped = popup ? popup.url() : ''
  if (popup) { await popup.waitForTimeout(800).catch(() => { }); await popup.close().catch(() => { }) }
  const hit = oss.slice(beforeOss).filter(x => x.status === 200)
  rec(`C4:「${label}」真下载：浏览器真的取到了字节（OSS 200 ${merged ? 'merged' : ''}）`,
    hit.length >= 1 || /127\.0\.0\.1:9000/.test(popped),
    `popup=${popped.split('?')[0]} oss=${JSON.stringify(hit)}`)
  rec(`C4:「${label}」下载文件名后缀 = .${fmt}`,
    new RegExp(`\\.${fmt}(\\?|$)`).test(String(body?.data?.url || '')),
    String(body?.data?.url || '').split('?')[0])
}
await page.screenshot({ path: path.join(SHOTS, 'C-04-downloads.png'), fullPage: true })
fs.writeFileSync(path.join(OUT, 'scenarioC.json'), JSON.stringify({ results, net, oss, jsErrors }, null, 1))
const failed = results.filter(r => !r.ok)
console.log(`\n==== scenario C: ${results.length - failed.length}/${results.length} PASS ====`)
if (failed.length) console.log('FAILED:', failed.map(f => f.name).join(' | '))
await browser.close()
