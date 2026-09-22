/**
 * DOC-MP-002 的取证脚本 —— 跑在**真实 H5 dev server + 真后端 + 真库**上
 * （Kevin 2026-09-22 的口径：小程序一律走本地 Mock，验收面 = H5 + Playwright 真 DOM）。
 *
 * 跑法（★ cwd 必须是 code/miniapp —— Playwright 的 require 锚点是本包的 package.json，
 * 用 plus-ui 的会 MODULE_NOT_FOUND，issue #229）：
 *
 *   cd code/miniapp && node scripts/shots-doc-mp002.mjs
 *
 * ★ 本脚本只把 PNG **写到磁盘**，并把「断言值」打到 stdout —— 不把截图读进上下文。
 * ★ **平台专属能力如实标注**：`wx.openDocument` / `wx.shareFileMessage` / 双指缩放
 *   在 H5 上不可验；H5 上能验的是「组件在、按钮在、点了真的会去打下载接口」，
 *   以及「没有这个平台能力时给一句人话而不是报错」。
 */
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright'

const BASE = process.argv[2] || 'http://127.0.0.1:9204'
const OUT = process.argv[3]
  || '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/doc/waves/reports/DOC-MP-002'

mkdirSync(OUT, { recursive: true })
const SAMPLE = '9000001001'

async function login(page, pick) {
  await page.goto(`${BASE}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login__mock-btn', { timeout: 30000 })
  // ★ 点勾选框而不是整行（整行中心落在《用户协议》的 @click.stop 链接上）
  await page.click('.login__box')
  await page.locator('.login__mock-btn', { hasText: pick }).first().click()
  await page.waitForFunction(() => !location.hash.includes('login'), null, { timeout: 30000 })
  await page.waitForTimeout(500)
}

/** 预览页的形状（DOM 真值） */
async function previewShape(page) {
  return page.evaluate(() => {
    // uni-h5 的 <image> 落成 <uni-image>，真 src 在内层 <img> 或背景图里
    function imgSrc(el) {
      const inner = el.querySelector('img')
      if (inner && inner.getAttribute('src')) {
        return inner.getAttribute('src')
      }
      const attr = el.getAttribute('src')
      if (attr) {
        return attr
      }
      const bg = getComputedStyle(el).backgroundImage || ''
      const m = /url\(["']?([^"')]+)["']?\)/.exec(bg)
      return m ? m[1] : ''
    }
    return {
      tabs: Array.from(document.querySelectorAll('.dt__item')).map(n => n.textContent?.trim() ?? ''),
      activeTab: document.querySelector('.lqg-seg__item--on')?.textContent?.trim() ?? '',
      pageImgs: Array.from(document.querySelectorAll('.piv__img')).map(imgSrc),
      thumbImgs: Array.from(document.querySelectorAll('.tst__thumb')).map(imgSrc),
      thumbSectionTitle: document.querySelector('.tst .lqg-gl')?.textContent?.trim() ?? '',
      attachments: Array.from(document.querySelectorAll('.att__name')).map(n => n.textContent?.trim() ?? ''),
      attSectionTitle: document.querySelector('.att .lqg-gl')?.textContent?.trim() ?? '',
      formatChips: Array.from(document.querySelectorAll('.db__fmt-item')).map(n => n.textContent?.trim() ?? ''),
      barButtons: Array.from(document.querySelectorAll('.db__btn')).map(n => n.textContent?.trim() ?? ''),
      barNote: document.querySelector('.db__note')?.textContent?.trim() ?? '',
      stateText: document.querySelector('.lqg-state__text')?.textContent?.trim() ?? '',
      // uni-h5 把 <button> 落成 <uni-button>：两个都认，另外认页面自己的重试按钮
      hasRetry: Array.from(document.querySelectorAll('button, uni-button, .pv__retry, .es__retry'))
        .some(b => b.textContent?.includes('重试')),
    }
  })
}

/** uni-h5 的 previewImage = 一个 `position:fixed; z-index:999; background:rgba(0,0,0,.8)` 的遮罩 */
async function overlayImgs(page) {
  return page.evaluate(() => {
    const out = []
    document.querySelectorAll('div').forEach((d) => {
      const cs = getComputedStyle(d)
      if (cs.position === 'fixed' && cs.zIndex === '999' && cs.backgroundColor === 'rgba(0, 0, 0, 0.8)') {
        d.querySelectorAll('img').forEach(i => out.push(i.src))
      }
    })
    return out
  })
}

/** 点开看原图那一层（点缩略图 / 点页面图）→ 返回全屏看图器里实际打开的地址 */
async function tapAndReadOverlay(page, selector, index) {
  await page.locator(selector).nth(index).click()
  await page.waitForTimeout(900)
  const opened = await overlayImgs(page)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)
  return opened
}

const browser = await chromium.launch()
const out = {}
/** 只跑第④段（「合并件还在生成」那条路）时用它，避免覆盖前三段的截图 */
const ONLY_GENERATING = process.env.LQG_MP002_ONLY_GENERATING === '1'

// ══════════════════════════════════════════════════════════════════════════
// ① 内部：文档列表上的「下载」→ 弹层（DownloadSheet 里就是 DownloadBar）
// ══════════════════════════════════════════════════════════════════════════
if (!ONLY_GENERATING) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
  const page = await ctx.newPage()
  await login(page, '李工')
  await page.goto(`${BASE}/#/pages/doc/index`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.gcd', { timeout: 30000 })
  await page.waitForTimeout(800)
  await page.screenshot({ path: `${OUT}/01-list-download-sheet.png`, fullPage: true })

  const list = await page.evaluate(() => ({
    groups: Array.from(document.querySelectorAll('.gcd__title')).map(n => n.textContent?.trim() ?? ''),
    rows: Array.from(document.querySelectorAll('.gcd__row .gcd__name')).map(n => n.textContent?.trim() ?? ''),
    downloadBtns: document.querySelectorAll('.gcd__dl').length,
    mergePreview: Array.from(document.querySelectorAll('.gcd__mbtn')).filter(b => b.textContent?.includes('合并预览')).length,
    mergeDownload: Array.from(document.querySelectorAll('.gcd__mbtn')).filter(b => b.textContent?.includes('合并下载')).length,
  }))

  // 点第一组的「下载」→ 弹层
  const groups = await page.$$('.gcd')
  let sheet = {}
  if (groups.length > 0) {
    const dl = await groups[groups.length - 1].$('.gcd__dl')
    if (dl) {
      await dl.click()
      await page.waitForSelector('.ds .db', { timeout: 10000 })
      await page.waitForTimeout(400)
      await page.screenshot({ path: `${OUT}/02-download-sheet-open.png`, fullPage: true })
      sheet = await page.evaluate(() => ({
        title: document.querySelector('.ds__t')?.textContent?.trim() ?? '',
        closeText: document.querySelector('.ds__x')?.textContent?.trim() ?? '',
        formatChips: Array.from(document.querySelectorAll('.ds .db__fmt-item')).map(n => n.textContent?.trim() ?? ''),
        buttons: Array.from(document.querySelectorAll('.ds .db__btn')).map(n => n.textContent?.trim() ?? ''),
        sheetHead: document.querySelector('.ds .lqg-sheet__head') !== null,
      }))
    }
  }
  out.list = { ...list, sheet }
  await ctx.close()
}

// ══════════════════════════════════════════════════════════════════════════
// ② 内部预览页：三份切换 + 页面图 + 看原图缩略条 + 附件 + 底部下载栏
// ══════════════════════════════════════════════════════════════════════════
if (!ONLY_GENERATING) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
  const page = await ctx.newPage()
  const calls = []
  page.on('request', (req) => {
    const u = req.url()
    if (u.includes('/mp/int/doc/') || u.includes('/mp/ext/doc/')) {
      calls.push(u.replace(BASE, '').replace(/^.*?(\/mp\/)/, '$1'))
    }
  })
  // H5 上点「打开」可能 window.open 新标签 —— 直接关掉，别污染上下文
  ctx.on('page', p => p.close().catch(() => {}))

  await login(page, '李工')
  await page.goto(`${BASE}/#/pages/doc/preview?sampleId=${SAMPLE}&docKind=sample_qc`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.piv__img', { timeout: 30000 })
  await page.waitForTimeout(900)
  await page.screenshot({ path: `${OUT}/03-preview-internal.png`, fullPage: true })
  const initial = await previewShape(page)

  // ① 第一层放大：点**页面图** → 全屏看图器（uni-h5 的 previewImage 实现）
  const pageOverlay = await tapAndReadOverlay(page, '.piv__img', 0)

  // ② 第二层放大：点**文档中的图片**缩略图 → 看**原图**
  //    ★ 断言的是「缩略图的显示地址」与「点开实际打开的地址」是**两个不同的地址**
  //      （大图有独立预览图；点开必须是原图，否则甲方「看得更清楚一点」就落空了）
  const thumbOverlay = await tapAndReadOverlay(page, '.tst__thumb', 0)
  const zoom = {
    thumbShown: initial.thumbImgs[0] ?? '',
    originalOpened: thumbOverlay[0] ?? '',
    differs: !!initial.thumbImgs[0] && initial.thumbImgs[0] !== (thumbOverlay[0] ?? ''),
    pageOpened: pageOverlay[0] ?? '',
    pageMatchesFirstPage: (pageOverlay[0] ?? '') === (initial.pageImgs[0] ?? ''),
  }

  // 点到「合并」那一份（顶部切换条第三/第四格）
  const mergedTab = page.locator('.dt__item', { hasText: '合并' }).first()
  if (await mergedTab.count() > 0) {
    await mergedTab.click()
    await page.waitForSelector('.piv__img', { timeout: 30000 })
    await page.waitForTimeout(600)
    await page.screenshot({ path: `${OUT}/04-preview-merged.png`, fullPage: true })
  }
  const merged = await previewShape(page)

  // 底部栏：切到 PDF 再点「打开」与「发送到微信」——断言**真的去打了下载接口**
  const before = calls.length
  await page.locator('.db__fmt-item', { hasText: 'PDF' }).first().click()
  await page.locator('.db__btn', { hasText: '打开' }).first().click()
  await page.waitForTimeout(2500)
  const afterOpen = calls.slice(before)
  await page.locator('.db__btn', { hasText: '发送到微信' }).first().click()
  // toast 只留 ~1.5s：点完马上抓（uni 的 toast 是 <uni-toast>，不是 wd-toast）
  // ★ H5 上没有 `wx.shareFileMessage`（实测 uni-h5 的 API 表里没有它）：
  //   按实现应当给一句人话（平台差异如实退化），这里把它抓下来。
  //   H5 的 `uni.downloadFile` 走的是浏览器 fetch + blob，比小程序慢，所以轮询给到 ~9s。
  let toastText = ''
  let barHint = ''
  for (let i = 0; i < 60; i += 1) {
    const snap = await page.evaluate(() => ({
      // uni-h5 的 toast 是 <uni-toast> 标签（class 是 uni-fade-*），文字在 .uni-simple-toast__text 里
      toast: document.querySelector('uni-toast, .uni-sample-toast, .uni-simple-toast__text')?.textContent?.trim() ?? '',
      hint: document.querySelector('.db__hint')?.textContent?.trim() ?? '',
    }))
    if (snap.toast && !toastText) {
      toastText = snap.toast
    }
    if (snap.hint) {
      barHint = snap.hint
    }
    if (toastText) {
      break
    }
    await page.waitForTimeout(150)
  }
  await page.waitForTimeout(1200)
  const afterShare = calls.slice(before)
  await page.screenshot({ path: `${OUT}/05-download-bar-actions.png`, fullPage: true })

  out.internalPreview = {
    ...initial,
    zoom,
    mergedTab: { tabs: merged.tabs, activeTab: merged.activeTab, pageImgs: merged.pageImgs.length },
    downloadCalls: { afterOpen, afterShare, toastText, barHint },
  }
  await ctx.close()
}

// ══════════════════════════════════════════════════════════════════════════
// ③ 外部（extA）：同一条预览链路的**外部版** —— 只有页面图，没有图片位 / 附件
// ══════════════════════════════════════════════════════════════════════════
if (!ONLY_GENERATING) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
  const page = await ctx.newPage()
  await login(page, '王医生')
  await page.goto(`${BASE}/#/pages/doc/preview?sampleId=${SAMPLE}&docKind=sample_qc`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.piv__img', { timeout: 30000 })
  await page.waitForTimeout(900)
  await page.screenshot({ path: `${OUT}/06-preview-external.png`, fullPage: true })
  const shape = await previewShape(page)
  out.externalPreview = {
    ...shape,
    bodyHasInternalNo: await page.evaluate(() => /T-hli01|T-hco04|内部编号/.test(document.body.innerText)),
  }
  await ctx.close()
}

// ══════════════════════════════════════════════════════════════════════════
// ④ ★「份数够但合并件还没渲染好」：撤一份 → 合并件立刻消失 → 打开合并那一份
//    = 「文档生成中」；脚本外面把那一份重新发布 → 页面自己轮询到 → 变成真页面
// ══════════════════════════════════════════════════════════════════════════
if (process.env.LQG_MP002_GENERATING === '1') {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
  const page = await ctx.newPage()
  await login(page, '李工')
  await page.goto(`${BASE}/#/pages/doc/preview?sampleId=${SAMPLE}&docKind=merged`, { waitUntil: 'domcontentloaded' })
  // 生成中那一屏（清单里还没有 merged 行）
  await page.waitForSelector('.pv__retry', { timeout: 30000 })
  await page.waitForTimeout(600)
  await page.screenshot({ path: `${OUT}/07-merged-generating.png`, fullPage: true })
  const generating = await previewShape(page)
  // 外面把成员重新发布 + 渲染合并件 → 页面自己的轮询应当把它变成真页面
  let recovered = false
  try {
    await page.waitForSelector('.piv__img', { timeout: 90000 })
    recovered = true
    await page.waitForTimeout(600)
    await page.screenshot({ path: `${OUT}/08-merged-recovered.png`, fullPage: true })
  }
  catch {
    recovered = false
  }
  out.mergedGenerating = {
    generatingText: generating.stateText,
    generatingHasRetry: generating.hasRetry,
    recovered,
    recoveredPageImgs: recovered ? (await previewShape(page)).pageImgs.length : 0,
  }
  await ctx.close()
}

await browser.close()
console.log(JSON.stringify(out, null, 2))
