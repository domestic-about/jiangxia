/**
 * SYS-EXPORT-001 的取证脚本 —— 跑在**真实 H5 dev server + 真后端 + 真库**上
 * （Kevin 2026-09-22 的口径：小程序一律走本地 Mock，验收面 = H5 + Playwright 真 DOM）。
 *
 * 跑法（★ cwd 必须是 code/miniapp —— Playwright 的 require 锚点是本包的 package.json，
 * 用 plus-ui 的会 MODULE_NOT_FOUND，issue #229）：
 *
 *   bash doc/waves/tools/qa-up.sh --backend-port 8094 --web-port 8093 --mp-port 9204
 *   cd code/miniapp && node scripts/shots-sys-export-001.mjs
 *
 * ★ 本脚本只把 PNG **写到磁盘**，并把「断言值」打到 stdout —— 不把截图读进上下文。
 * ★ **平台专属能力如实标注**：`wx.openDocument`（微信查看器右上角保存 / 转发）与
 *   `wx.shareFileMessage`（发送到微信）在 H5 上**不可验**（DOC-MP-002 实测
 *   `typeof uni.shareFileMessage === "undefined"`）。H5 上能验的是：
 *     · 表格页底部「导出 Excel」真的点亮了（不再是置灰占位）；
 *     · 点了真的按**当前筛选**去打 `/mp/int/export/{sheet}`，且请求**带 Authorization / clientid**；
 *     · 响应是非空 xlsx（真后端真库）；
 *     · 导出后弹出「打开 / 发送到微信」两个选项（公共段 `utils/fileHandoff.ts` 的消费面）。
 */
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright'

const BASE = process.argv[2] || 'http://127.0.0.1:9204'
const OUT = process.argv[3]
  || '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/doc/waves/reports/SYS-EXPORT-001'

mkdirSync(OUT, { recursive: true })

async function login(page) {
  await page.goto(`${BASE}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login__mock-btn', { timeout: 30000 })
  // ★ 点勾选框而不是整行（整行中心落在《用户协议》的 @click.stop 链接上）
  await page.click('.login__box')
  await page.locator('.login__mock-btn', { hasText: '李工' }).first().click()
  await page.waitForFunction(() => !location.hash.includes('login'), null, { timeout: 30000 })
  await page.waitForTimeout(600)
}

/** 表格页底部栏 / 筛选行的 DOM 真值 */
async function ledgerShape(page) {
  return page.evaluate(() => ({
    sheets: Array.from(document.querySelectorAll('.lqg-sheets__item')).map(n => n.textContent?.trim() ?? ''),
    activeSheet: document.querySelector('.lqg-sheets__item--on')?.textContent?.trim() ?? '',
    count: document.querySelector('.lqg-count')?.textContent?.trim() ?? '',
    filterChips: Array.from(document.querySelectorAll('.ledger-page__chip')).map(n => n.textContent?.trim() ?? ''),
    exportText: document.querySelector('.ledger-page__export')?.textContent?.trim() ?? '',
    exportDisabled: !!document.querySelector('.ledger-page__export')?.hasAttribute('disabled'),
    note: document.querySelector('.ledger-page__note')?.textContent?.trim() ?? '',
    // 页面里不许再有「导出在后续版本开放」那种占位实现
    hasPlaceholderToast: document.body.innerHTML.includes('导出在后续版本开放'),
    firstRowInternalNo: document.querySelector('.ledger__fz')?.textContent?.trim() ?? '',
  }))
}

/** 弹出来的操作选择器（uni-h5 落成一个 fixed 的 action-sheet） */
async function actionSheet(page) {
  return page.evaluate(() => {
    const out = []
    document.querySelectorAll('div, uni-actionsheet, .uni-actionsheet').forEach((d) => {
      const cs = getComputedStyle(d)
      if ((cs.position === 'fixed' || cs.position === 'absolute') && cs.zIndex && Number(cs.zIndex) >= 100) {
        d.querySelectorAll('.uni-actionsheet__cell, .uni-actionsheet__action, li, button, view').forEach((c) => {
          const t = (c.textContent ?? '').trim()
          if (t && t.length <= 12 && !out.includes(t)) {
            out.push(t)
          }
        })
      }
    })
    return out
  })
}

const browser = await chromium.launch()
const out = {}
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
// 记下「这一次导出到底下到了多少字节」（blob 的真实大小）
await ctx.addInitScript(() => {
  const original = URL.createObjectURL.bind(URL)
  URL.createObjectURL = (blob) => {
    window.__lqgHandoffBlob = { size: blob && blob.size, type: blob && blob.type }
    return original(blob)
  }
})
const page = await ctx.newPage()

/** 抓导出请求（URL + 请求头 + 响应码 / 内容类型 / 字节数） */
const seen = []
page.on('request', (req) => {
  const url = req.url()
  if (url.includes('/mp/int/export/')) {
    seen.push({ kind: 'request', method: req.method(), url, headers: req.headers() })
  }
})
page.on('response', async (res) => {
  const url = res.url()
  if (url.includes('/mp/int/export/')) {
    let bytes = -1
    try {
      bytes = (await res.body()).length
    }
    catch {
      bytes = -1
    }
    seen.push({
      kind: 'response',
      url,
      status: res.status(),
      contentType: res.headers()['content-type'] ?? '',
      contentDisposition: res.headers()['content-disposition'] ?? '',
      bytes,
    })
  }
})

/**
 * 「打开 / 发送到微信」拿到的是不是**非空的本地文件**。
 *
 * ★ H5 上没有 `wx.downloadFile`：公共段退化成 `fetch` + `blob:`（`fileHandoff.ts`）。
 *   所以在页面上把 `URL.createObjectURL` 拦一道，把 blob 的**真实字节数**记下来 ——
 *   这比读 Playwright 的 response body 可靠（那边常因流已被消费给出 0）。
 *   真机上的临时文件路径（`wxfile://…`）在 H5 上不可验，报告如实标注。
 */
async function blobEvidence(page) {
  return page.evaluate(() => {
    const info = window.__lqgHandoffBlob
    return info
      ? { present: true, size: info.size, type: info.type, isXlsx: String(info.type).includes('spreadsheetml') }
      : { present: false, size: 0, type: '', isXlsx: false }
  })
}

// ── ① 默认进「样本记录」表：底部只有「导出 Excel」，已点亮（不再是置灰占位） ──
await login(page)
await page.goto(`${BASE}/#/pages/ledger/index?sheet=tissue`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.ledger-page__export', { timeout: 30000 })
await page.waitForTimeout(1000)
out.beforeFilter = await ledgerShape(page)
await page.screenshot({ path: `${OUT}/01-ledger-export-enabled.png`, fullPage: true })

// ── ② 筛「待核验」→ 导出：请求必须只带该筛选（用户筛了什么就导出什么） ──
await page.locator('.ledger-page__chip', { hasText: '待核验' }).first().click()
await page.waitForTimeout(1200)
out.filtered = await ledgerShape(page)
await page.screenshot({ path: `${OUT}/02-ledger-filter-pending.png`, fullPage: true })

await page.locator('.ledger-page__export').click()
// 导出请求发出后弹「打开 / 发送到微信」；弹层开着的时候截图
await page.waitForTimeout(2500)
out.actionSheet = await actionSheet(page)
out.tissueBlob = await blobEvidence(page)
await page.screenshot({ path: `${OUT}/03-handoff-action-sheet.png`, fullPage: true })
// 关掉弹层（点空白），别挡住后面的操作
await page.keyboard.press('Escape')
await page.waitForTimeout(500)

// ── ③ 换到 -80 冻存表：页签切「-80 超期」后再导出（页签值也要带过去） ──
await page.locator('.lqg-sheets__item', { hasText: '-80 冻存' }).first().click()
await page.waitForTimeout(1200)
await page.locator('.ledger-page__chip', { hasText: '-80 超期' }).first().click()
await page.waitForTimeout(1200)
out.cryoShape = await ledgerShape(page)
await page.locator('.ledger-page__export').click()
await page.waitForTimeout(2500)
await page.keyboard.press('Escape')
await page.waitForTimeout(400)

out.requests = seen.map((r) => {
  if (r.kind === 'request') {
    return {
      kind: 'request',
      method: r.method,
      url: r.url,
      authorization: r.headers.authorization ?? null,
      clientid: r.headers.clientid ?? null,
      hasAuthHeader: !!r.headers.authorization,
      hasClientid: !!r.headers.clientid,
    }
  }
  return r
})

out.assertions = {
  exportEnabled: out.beforeFilter.exportDisabled === false,
  noPlaceholderToast: out.beforeFilter.hasPlaceholderToast === false,
  noteIsNewWording: out.beforeFilter.note === '核验、冻存取用请到网页工作台',
  handedOff: out.actionSheet.includes('打开') && out.actionSheet.includes('发送到微信'),
  tissueFilteredRequest: out.requests.some(r => r.kind === 'request'
    && r.url.includes('/mp/int/export/tissue') && r.url.includes('verifyStatus=pending')),
  cryoOverdueRequest: out.requests.some(r => r.kind === 'request'
    && r.url.includes('/mp/int/export/cryo') && r.url.includes('overdueOnly=true')),
  everyRequestHasAuth: out.requests.filter(r => r.kind === 'request').every(r => r.hasAuthHeader),
  everyRequestHasClientid: out.requests.filter(r => r.kind === 'request').every(r => r.hasClientid),
  everyResponseIsXlsx: out.requests.filter(r => r.kind === 'response')
    .every(r => r.status === 200 && r.contentType.includes('spreadsheetml')),
  chineseFileNameInHeader: out.requests.filter(r => r.kind === 'response')
    .some(r => r.contentDisposition.includes("filename*=utf-8''")),
  downloadedNonEmptyXlsx: out.tissueBlob.present && out.tissueBlob.size > 1000 && out.tissueBlob.isXlsx,
}

console.log(JSON.stringify(out, null, 2))
await ctx.close()
await browser.close()
