// D7 L2 · 场景 B：小程序 H5「我的 → 内部管理 → 表格页筛待核验 → 导出 Excel → 打开 / 转发」
//                                  （SYS-EXPORT-001）
// ★ 口径替换①：真机 → H5 + mock 登录 + 真 DOM + 真后端。
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'
const require = createRequire('/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/code/miniapp/package.json')
const { chromium } = require('playwright')

const BASE = 'http://127.0.0.1:9204'
const OUT = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/.tmp/qa-d7-L2'
const SHOTS = path.join(OUT, 'shots')
fs.mkdirSync(SHOTS, { recursive: true })

const results = []
const rec = (name, ok, detail = '') => {
  results.push({ name, ok: !!ok, detail: String(detail).slice(0, 1200) })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  :: ' + String(detail).slice(0, 420) : ''}`)
}
const api = [], jsErrors = []
const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 } })
const page = await ctx.newPage()
page.on('response', async (res) => {
  const u = res.url()
  if (u.includes('/mp/') || u.includes('/auth/login')) {
    try {
      const ct = res.headers()['content-type'] || ''
      // 二进制（xlsx）不读 body，只记头部
      const isBin = /spreadsheet|octet-stream/.test(ct)
      const body = isBin ? '' : await res.text()
      api.push({ url: u, status: res.status(), ct, len: Number(res.headers()['content-length'] || 0), body })
    } catch { /* ignore */ }
  }
})
page.on('pageerror', e => jsErrors.push(String(e.message).slice(0, 300)))
page.on('requestfailed', r => jsErrors.push(`reqfail ${r.url().slice(0, 80)} ${r.failure()?.errorText}`))

// 登录（staff）
await page.goto(`${BASE}/#/pages/login/index`, { waitUntil: 'networkidle' })
await page.waitForSelector('.login__mock-btn', { timeout: 30000 })
await page.locator('.login__box').click()
await page.locator('.login__mock-btn').nth(0).click()
await page.waitForTimeout(4000)

// ── B1 我的 → 内部管理 ───────────────────────────────────────────────────
await page.locator('.uni-tabbar__item', { hasText: '我的' }).first().click()
await page.waitForTimeout(2500)
rec('B1:进入「我的」', page.url().includes('pages/me/index'), page.url())
const bodyMe = await page.locator('body').innerText()
rec('B1:我的页出现「内部管理」板块（仅内部身份）', bodyMe.includes('内部管理') && bodyMe.includes('查看 · 筛选 · 导出'),
  bodyMe.replace(/\n+/g, '|').slice(0, 260))
await page.screenshot({ path: path.join(SHOTS, 'B-01-me.png'), fullPage: true })
const adminBtns = page.locator('.iab__item, [class*=admin] [class*=item]')
const adminTexts = await adminBtns.allInnerTexts().catch(() => [])
console.log('ADMIN ENTRIES', JSON.stringify(adminTexts))
// 点「样本记录信息表」入口
await page.getByText('样本记录信息表', { exact: true }).first().click()
await page.waitForTimeout(3000)
rec('B1:进入内部管理表格页（样本记录信息表 = sheet=sample）', page.url().includes('pages/ledger/index') && page.url().includes('sheet=sample'), page.url())
await page.screenshot({ path: path.join(SHOTS, 'B-02-ledger.png'), fullPage: true })

const tableRows = async () => page.locator('.lqg-table__row, tbody tr, [class*=ledger-table] [class*=row]').count()
const rowCount = async () => page.locator('.ledger__row').count()
console.log('ledger DOM class sample', await page.evaluate(() => {
  const el = document.querySelector('.ledger-page')
  return el ? el.outerHTML.slice(0, 1200) : 'no .ledger-page'
}))

// ── B2 筛「待核验」→ 表格行数 = 2（直连库也是 2） ───────────────────────
const chips = await page.locator('.lqg-filter__chip').allInnerTexts()
console.log('CHIPS', JSON.stringify(chips))
await page.locator('.lqg-filter__chip', { hasText: '待核验' }).first().click()
await page.waitForTimeout(2500)
const rowsAfterFilter = await rowCount()
const listReq = api.filter(x => x.url.includes('/mp/int/sample/list')).pop()
const listBody = listReq ? JSON.parse(listReq.body) : null
rec('B2:筛「待核验」→ 表格行数=2 且接口 total=2',
  rowsAfterFilter === 2 && listBody?.total === 2, `dom=${rowsAfterFilter} api=${listBody?.total} url=${listReq?.url}`)
await page.screenshot({ path: path.join(SHOTS, 'B-03-ledger-pending.png'), fullPage: true })

// ── B3 导出 Excel：接口带筛选 + 鉴权头；H5 走 fetch+blob ─────────────────
const before = api.length
const popupP = ctx.waitForEvent('page', { timeout: 20000 }).catch(() => null)
const exportBtn = page.locator('.ledger-page__export')
rec('B3:页底有唯一的「导出 Excel」按钮', await exportBtn.count() === 1 && (await exportBtn.innerText()).includes('导出 Excel'),
  (await exportBtn.allInnerTexts()).join(','))
const noteText = (await page.locator('.ledger-page__note').innerText()).trim()
rec('B3:页底小字是 CR-20260918-07 新口径（无「修改」二字）',
  noteText === '核验、冻存取用请到网页工作台', noteText)
await exportBtn.click()
await page.waitForTimeout(6000)
const expReq = api.slice(before).filter(x => x.url.includes('/mp/int/export/'))
const exp = expReq.pop()
rec('B3:导出接口被调且带筛选 verifyStatus=pending',
  !!exp && /verifyStatus=pending/.test(exp.url), exp ? exp.url : 'no request')
rec('B3:导出返回 xlsx 且 HTTP 200',
  exp?.status === 200 && /spreadsheet|octet-stream/.test(exp?.ct || ''),
  exp ? `status=${exp.status} ct=${exp.ct} len=${exp.len}` : '')
// 选择器（uni.showActionSheet）出现 → 点「打开」
const sheetVisible = await page.locator('uni-actionsheet, .uni-actionsheet').count()
const sheetText = await page.locator('body').innerText()
rec('B3:导出后弹出「打开 / 发送到微信」选择器（真机上此处可右上角转发）',
  sheetVisible > 0 && sheetText.includes('发送到微信'), `sheet=${sheetVisible}`)
await page.screenshot({ path: path.join(SHOTS, 'B-04-actionsheet.png') })
// 点选择器里的「打开」（H5 走 fileHandoff.openFile(blob) → window.open）
await page.locator('uni-actionsheet .uni-actionsheet__cell, .uni-actionsheet__cell').filter({ hasText: '打开' }).first().click().catch(() => {})
await page.waitForTimeout(3000)
const popup = await popupP
rec('B3:H5「打开」= window.open(blob)（真机 wx.openDocument{showMenu:true} + 右上角转发，H5 不可验）',
  !!popup, popup ? popup.url().slice(0, 60) : 'no popup')
if (popup) { await popup.waitForTimeout(1000).catch(() => { }); await popup.close().catch(() => { }) }

rec('X:无未捕获 JS 异常', jsErrors.filter(e => !/Failed to load resource/i.test(e)).length === 0, JSON.stringify(jsErrors.slice(0, 5)))
fs.writeFileSync(path.join(OUT, 'scenarioB.json'), JSON.stringify({ results, api, jsErrors }, null, 1))
const failed = results.filter(r => !r.ok)
console.log(`\n==== scenario B: ${results.length - failed.length}/${results.length} PASS ====`)
if (failed.length) console.log('FAILED:', failed.map(f => f.name).join(' | '))
await browser.close()
