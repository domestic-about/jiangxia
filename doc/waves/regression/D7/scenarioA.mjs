// D7 L2 · 场景 A：小程序 H5 文档页签 + 预览页（内部 staff / 外部 extB 各走一遍）
//
// ★ 口径替换①：真机（体验版）→ H5(9204) + mock 登录 + Playwright 真 DOM + 真后端 8094 + 真库 5433。
// ★ 平台专属能力（shareFileMessage / openDocument 菜单 / downloadFile 合法域名）在 H5 上不可验，
//   脚本里如实标注「仅代码路径存在 / 需真机」。
// ★ 不把 PNG 读进上下文：截图只落盘。
// ★ Playwright require 锚点 = code/miniapp/package.json（issue #229）。
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'

const require = createRequire('/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/code/miniapp/package.json')
const { chromium } = require('playwright')

const BASE = process.env.L2_BASE || 'http://127.0.0.1:9204'
const OUT = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/.tmp/qa-d7-L2'
const SHOTS = path.join(OUT, 'shots')
fs.mkdirSync(SHOTS, { recursive: true })

const which = process.argv[2] || 'internal'   // internal | external
const results = []
function rec(name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail: String(detail).slice(0, 1200) })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  :: ' + String(detail).slice(0, 400) : ''}`)
}

const api = []       // 捕获到的业务接口
const oss = []       // 捕获到的 OSS 直连（H5 的 downloadFile 兜底走 fetch）
const jsErrors = []
const toasts = []

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 } })
const page = await ctx.newPage()

page.on('response', async (res) => {
  const u = res.url()
  try {
    if (u.includes('/mp/int/doc/') || u.includes('/mp/ext/doc/') || u.includes('/auth/login') || u.includes('/mp/me')) {
      const body = await res.text()
      api.push({ url: u, status: res.status(), body: body.slice(0, 1500) })
    }
    if (u.startsWith('http://127.0.0.1:9000/')) {
      oss.push({ url: u.split('?')[0], status: res.status(), ct: res.headers()['content-type'] || '' })
    }
  } catch { /* body 读不到就算了 */ }
})
page.on('pageerror', e => jsErrors.push(String(e.message).slice(0, 300)))
page.on('console', (m) => {
  const t = m.text()
  if (/原图格式|发送到微信|打不开|暂时下不了|稍后再试/.test(t)) toasts.push(t)
})
// toast 在 H5 里是 DOM 节点，另有 DOM 读取

async function loginAs(kind) {
  // ★ extB 不在 UI 的 mock 按钮里（MOCK_SEEDS 只有 staff/extA/newbie1）→
  //   点 extA 的按钮，但把登录请求体里的 mock:extA/extA 手机号换成 extB（同一种 mock 路径）。
  if (kind === 'external') {
    await page.route('**/lqg-api/auth/login', async (route) => {
      const req = route.request()
      const body = JSON.parse(req.postData() || '{}')
      body.xcxCode = 'mock:extB'
      body.phoneCode = 'mock:13800000012'
      await route.continue({ postData: JSON.stringify(body) })
    })
  }
  await page.goto(`${BASE}/#/pages/login/index`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.login__mock-btn', { timeout: 30000 })
  await page.locator('.login__box').click()          // ★ 点勾选框本体：点整行会命中《用户协议》链接
  await page.waitForTimeout(200)
  const btn = page.locator('.login__mock-btn')
  await btn.nth(kind === 'external' ? 1 : 0).click() // 1 = 外部人员·王医生（已核验）
  await page.waitForTimeout(4000)
  const url = page.url()
  rec(`login.${kind}:登录后离开登录页`, !url.includes('login'), url)
  const meRes = api.filter(x => x.url.includes('/mp/me')).pop()
  let identity = null
  try { identity = JSON.parse(meRes.body).data.identity } catch { }
  rec(`login.${kind}:/mp/me 身份=${kind === 'internal' ? 'internal' : 'external'}`, identity === kind,
    meRes ? meRes.body.slice(0, 200) : 'no /mp/me')
  if (kind === 'external') await page.unroute('**/lqg-api/auth/login')
}

async function gotoDocTab() {
  // 真实路由 pages/doc/index（tab 页）。先点 tabBar，验证 tab 本身可达。
  const tab = page.locator('.uni-tabbar__item', { hasText: '文档' })
  if (await tab.count()) {
    await tab.first().click()
  } else {
    await page.goto(`${BASE}/#/pages/doc/index`)
  }
  await page.waitForTimeout(2500)
}

// ════════════════════════════════════════════════════════════════════════
await loginAs(which)
await gotoDocTab()
rec('tab:进入文档页签', page.url().includes('pages/doc/index'), page.url())

// ── A1 分组卡片：组标题/副标题/组内固定顺序/份数 ─────────────────────────
const titles = await page.locator('.gcd__title').allInnerTexts()
const subs = await page.locator('.gcd__sub').allInnerTexts()
const cardCount = await page.locator('.gcd').count()
console.log('CARDS', cardCount, JSON.stringify(titles), JSON.stringify(subs))
if (which === 'internal') {
  // 内部：1001(T-hli01/A 医院, 3+merged) → 1006(T-hco04/B 大学, 1) → 1004(T-hli02/A 医院, 1)
  rec('A1 内部:组标题=内部编号', titles[0] === 'T-hli01' && titles[1] === 'T-hco04' && titles[2] === 'T-hli02', JSON.stringify(titles))
  rec('A1 内部:组副标题=来源单位', subs[0] === 'A 医院' && subs[1] === 'B 大学', JSON.stringify(subs))
  rec('A1:组间按最新完成时间倒序(T-hli01 最先)', titles[0] === 'T-hli01', JSON.stringify(titles))
} else {
  rec('A1 外部:组标题=送检单号', titles[0] === 'SJ90000001', JSON.stringify(titles))
  rec('A1 外部:组副标题=掩码姓名', subs[0] === '测**', JSON.stringify(subs))
}
await page.screenshot({ path: path.join(SHOTS, `A-${which}-01-doclist.png`), fullPage: true })

// 第一张卡（1001）的组内顺序 + 每份一行「下载」+ 组底两按钮
const firstCard = page.locator('.gcd').first()
const rowNames = await firstCard.locator('.gcd__name').allInnerTexts()
const dlBtns = await firstCard.locator('.gcd__dl').allInnerTexts()
const mergeBtns = await firstCard.locator('.gcd__mbtn').allInnerTexts()
rec('A1:组内固定顺序 样本质控表→类器官质控表→评分表',
  JSON.stringify(rowNames) === JSON.stringify(['样本质控表', '类器官质控表', '类器官质量评分表']), JSON.stringify(rowNames))
rec('A1:每份一行右侧有「下载」', dlBtns.length === 3 && dlBtns.every(t => t.trim() === '下载'), JSON.stringify(dlBtns))
rec('A1:组底有「合并预览」「合并下载」', JSON.stringify(mergeBtns) === JSON.stringify(['合并预览', '合并下载']), JSON.stringify(mergeBtns))
// 只有 1001 组 ≥2 份 → 只有它出合并按钮
const mergeGroups = await page.locator('.gcd__merge').count()
rec('A1:仅 ≥2 份的组出合并入口(1 个组)', mergeGroups === 1, `mergeGroups=${mergeGroups}`)

// ── A3 合并下载（**先做**：验 DownloadBar 的默认格式确实是 Word）→ 发送到微信 ─
const beforeA3 = api.length
await firstCard.locator('.gcd__mbtn', { hasText: '合并下载' }).click()
await page.waitForSelector('.ds__t', { timeout: 15000 })
rec('A3:合并下载弹层标题=质控文档（合并）', (await page.locator('.ds__t').innerText()).trim() === '质控文档（合并）',
  (await page.locator('.ds__t').innerText()).trim())
rec('A3:弹层内是 DownloadBar（.db 组件）', await page.locator('.ds .db').count() === 1)
await page.screenshot({ path: path.join(SHOTS, `A-${which}-03-merge-sheet.png`) })
const fmtOn = (await page.locator('.db__fmt-item', { hasText: 'Word' }).getAttribute('class')) || ''
rec('A3:默认格式 = Word', fmtOn.includes('--on'), fmtOn)
const beforeOss3 = oss.length
await page.locator('.db__btn', { hasText: '发送到微信' }).click()
await page.waitForTimeout(6000)
const dlReq3 = api.slice(beforeA3).filter(x => /\/download\?format=docx/.test(x.url))
const dlBody3 = dlReq3.length ? JSON.parse(dlReq3[dlReq3.length - 1].body) : null
rec('A3:合并件 Word 下载接口 code=200', dlReq3.length > 0 && dlBody3?.code === 200, dlReq3.map(x => x.url).join(' | '))
rec('A3:合并件文件名=质控文档（合并）-<编号>',
  /质控文档（合并）/.test(String(dlBody3?.data?.fileName || '')),
  String(dlBody3?.data?.fileName || ''))
// H5 上没有 shareFileMessage → 一句人话（不当失败）；真机行为不可验
const toastText = await page.locator('uni-toast, .uni-toast').allInnerTexts().catch(() => [])
const hintText = await page.locator('.db__hint').allInnerTexts().catch(() => [])
const oss3 = oss.slice(beforeOss3)
rec('A3:合并件 Word 真的取到字节（OSS 200）', oss3.some(x => x.status === 200),
  `oss=${JSON.stringify(oss3)} hint=${JSON.stringify(hintText)} toast=${JSON.stringify(toastText)}`)
rec('A3:H5 无 shareFileMessage → 给一句人话（真机转发不可验）',
  JSON.stringify(toastText).includes('发送到微信') || JSON.stringify(hintText).includes('发送到微信') || toasts.some(t => t.includes('发送到微信')),
  JSON.stringify({ toastText, hintText, toasts }))
await page.locator('.ds__x').click().catch(() => { })
await page.waitForTimeout(500)

// ── A2 单份下载：PDF → 打开（H5 退化为 fetch+blob+window.open） ──────────
const beforeA2 = api.length
await firstCard.locator('.gcd__dl').first().click()      // 「样本质控表」行的下载
await page.waitForSelector('.ds__t', { timeout: 15000 })
const sheetTitle = (await page.locator('.ds__t').innerText()).trim()
rec('A2:列表「下载」打开弹层', sheetTitle === '样本质控表', sheetTitle)
await page.screenshot({ path: path.join(SHOTS, `A-${which}-02-downloadsheet.png`) })

await page.locator('.db__fmt-item', { hasText: 'PDF' }).click()
const beforeOss2 = oss.length
const popupP = ctx.waitForEvent('page', { timeout: 15000 }).catch(() => null)
await page.locator('.db__btn', { hasText: '打开' }).click()
await page.waitForTimeout(6000)
const dlReq = api.slice(beforeA2).filter(x => /\/download\?format=pdf/.test(x.url))
const dlBody = dlReq.length ? JSON.parse(dlReq[dlReq.length - 1].body) : null
rec('A2:PDF 下载接口被调且 code=200', dlReq.length > 0 && dlBody?.code === 200,
  dlReq.map(x => x.url).join(' | '))
rec('A2:返回的是 OSS 签名链接 + 文件名', !!dlBody?.data?.url && !!dlBody?.data?.fileName,
  JSON.stringify({ url: String(dlBody?.data?.url || '').slice(0, 90), fileName: dlBody?.data?.fileName }))
const ossHit = oss.slice(beforeOss2).filter(x => x.status === 200)
const hint2 = await page.locator('.db__hint').allInnerTexts().catch(() => [])
rec('A2:H5 兜底真的把文件取下来了(OSS 200)', ossHit.length >= 1,
  `oss=${JSON.stringify(oss.slice(beforeOss2))} hint=${JSON.stringify(hint2)}`)
const popup = await popupP
rec('A2:H5「打开」= window.open(blob)（真机走 wx.openDocument{showMenu:true}，H5 不可验）',
  !!popup, popup ? popup.url().slice(0, 60) : 'no popup（可能被浏览器 popup 拦截）')
if (popup) await popup.close().catch(() => { })
// 关掉弹层
await page.locator('.ds__x').click().catch(() => { })
await page.waitForTimeout(600)

// ── A4 点行进预览：顶部三份切换 ────────────────────────────────────────
const beforeA4 = api.length
await firstCard.locator('.gcd__row').first().click()
await page.waitForTimeout(4000)
rec('A4:进入预览页', page.url().includes('pages/doc/preview'), page.url())
const tabTexts = (await page.locator('.dt__item').allInnerTexts()).map(s => s.replace(/\s+/g, ''))
rec('A4:顶部切换条=三份+合并', JSON.stringify(tabTexts) === JSON.stringify(['样本质控表', '类器官质控表', '类器官质量评分表', '合并']),
  JSON.stringify(tabTexts))
await page.screenshot({ path: path.join(SHOTS, `A-${which}-04-preview-default.png`), fullPage: true })

const pagesReq = api.slice(beforeA4).filter(x => /\/pages/.test(x.url))
rec('A4:进了预览就取 pages 接口', pagesReq.length >= 1, pagesReq.map(x => x.url).join(' | '))
const pageCountDom = async () => page.locator('.piv__page').count()
const pagesOf = async (kind) => {
  for (let i = 0; i < 20; i++) {
    const r = api.filter(x => new RegExp(`/doc/9000001001/${kind}/pages`).test(x.url)).pop()
    if (r) {
      try { return JSON.parse(r.body).data.pages.length } catch { /* 再等 */ }
    }
    await page.waitForTimeout(400)
  }
  return -1
}
const overlayCount = async () => page.locator('uni-swiper').count()
const n1 = await pageCountDom()
rec('A4:样本质控表 页面图数=接口 pages 数=2', n1 === 2 && (await pagesOf('sample_qc')) === 2, `dom=${n1} api=${await pagesOf('sample_qc')}`)

// 逐份切换
for (const [label, kind, want] of [['类器官质控表', 'organoid_qc', 1], ['类器官质量评分表', 'organoid_score', 2], ['合并', 'merged', 5]]) {
  const before = api.length
  await page.locator('.dt__item', { hasText: label }).first().click()
  await page.waitForTimeout(3000)
  const domN = await pageCountDom()
  const apiN = await pagesOf(kind)
  const hit = api.slice(before).some(x => new RegExp(`/doc/9000001001/${kind}/pages`).test(x.url))
  rec(`A4:切到「${label}」→ 重新取 ${kind} 的 pages`, hit, `hit=${hit}`)
  rec(`A4:「${label}」页面图数 dom=${domN} == ${want}`, domN === want && apiN === want, `dom=${domN} api=${apiN}`)
  await page.screenshot({ path: path.join(SHOTS, `A-${which}-05-tab-${kind}.png`), fullPage: true })
}

// ── A5 点页面图全屏放大（uni.previewImage → H5 的 image-preview 覆盖层） ──
await page.locator('.dt__item', { hasText: '样本质控表' }).first().click()
await page.waitForTimeout(2500)
const pivBefore = await overlayCount()
await page.locator('.piv__page').first().click()
await page.waitForTimeout(2000)
const pivAfter = await overlayCount()
const overlayVisible = await page.evaluate(() => {
  const el = document.querySelector('uni-swiper')
  if (!el) return null
  const s = getComputedStyle(el)
  return { cls: el.className, display: s.display, visibility: s.visibility, opacity: s.opacity,
    imgs: [...el.querySelectorAll('img')].map(i => i.getAttribute('src')).slice(0, 4) }
})
rec('A5:点页面图 → H5 图片预览覆盖层出现（对应真机 wx.previewImage 全屏缩放）',
  pivAfter > pivBefore && !!overlayVisible, JSON.stringify({ pivBefore, pivAfter, overlayVisible }).slice(0, 500))
await page.screenshot({ path: path.join(SHOTS, `A-${which}-06-pageviewer.png`) })
await page.keyboard.press('Escape').catch(() => { })
await page.waitForTimeout(800)

// ── A6「文档中的图片」缩略图 → 点开看原图 ────────────────────────────────
if (which === 'internal') {
  const thumbs = await page.locator('.tst__thumb').count()
  rec('A6:缩略图数=可用的真图数(seed 假地址被跳过)', thumbs === 2, `thumbs=${thumbs}`)
  const thumbInfo = await page.evaluate(() => [...document.querySelectorAll('.tst__thumb img, .tst__thumb')].map((el) => {
    const im = el.tagName === 'IMG' ? el : el.querySelector('img')
    return { src: im ? im.getAttribute('src') : null, nw: im ? im.naturalWidth : 0 }
  }))
  console.log('THUMBS', JSON.stringify(thumbInfo).slice(0, 700))
  // 点第 2 张（big.png：url=.png 原图 / previewUrl=.jpg 缩略图）→ 应开原图 .png
  await page.locator('.tst__thumb').nth(1).click()
  await page.waitForTimeout(2000)
  const opened = await page.evaluate(() => {
    const el = document.querySelector('uni-swiper')
    if (!el) return null
    return [...el.querySelectorAll('img')].map(i => i.getAttribute('src')).filter(Boolean)
  })
  rec('A6:点缩略图打开的是**原图** url(.png)，不是 previewUrl(.jpg)',
    Array.isArray(opened) && opened.some(u => /\.png/.test(u)) && !opened.some(u => /\.jpg/.test(u)),
    JSON.stringify(opened).slice(0, 400))
  await page.screenshot({ path: path.join(SHOTS, `A-${which}-07-thumb-open.png`) })
  await page.keyboard.press('Escape').catch(() => { })
  await page.waitForTimeout(800)
  // 附件
  const attNames = await page.locator('.att__name').allInnerTexts()
  rec('A6:附件列表只列可取到链接的那条（seed 假地址跳过）',
    attNames.length === 1 && attNames[0] === 'L2探针-活率报告.pdf', JSON.stringify(attNames))
} else {
  const thumbs = await page.locator('.tst__thumb').count()
  const atts = await page.locator('.att__row').count()
  rec('A6 外部:ExtDocPagesVo 只给页面图 → 无「文档中的图片」「附件」两段',
    thumbs === 0 && atts === 0, `thumbs=${thumbs} atts=${atts}`)
}

// ── A7 外部：清单只给外部版 done 的那几份 + 不带内部编号 ─────────────────
if (which === 'external') {
  const listReq = api.filter(x => x.url.includes('/mp/ext/doc/list') && !x.url.includes('sampleId')).pop()
  const rows = JSON.parse(listReq.body).rows
  const keys = [...new Set(rows.flatMap(r => Object.keys(r)))]
  rec('A7 外部:清单 5 行(1001 三份+merged、1004 sample_qc)', rows.length === 5, JSON.stringify(rows.map(r => `${r.sampleId}:${r.docKind}`)))
  rec('A7 外部:行里没有 internalNo / sourceUnitName', !keys.includes('internalNo') && !keys.includes('sourceUnitName'), JSON.stringify(keys))
  const pgReq = api.filter(x => x.url.includes('/mp/ext/doc/9000001001/sample_qc/pages')).pop()
  const pgBody = JSON.parse(pgReq.body)
  rec('A7 外部:pages 里没有 images/attachments 键（只给页面图）',
    pgBody.data.images === undefined && pgBody.data.attachments === undefined, JSON.stringify(Object.keys(pgBody.data)))
  rec('A7 外部:pages 不泄露 errorMsg/contentHash',
    pgBody.data.errorMsg === undefined && pgBody.data.contentHash === undefined, JSON.stringify(Object.keys(pgBody.data)))
}

// ── 收尾 ────────────────────────────────────────────────────────────────
rec('X:无未捕获 JS 异常（排除资源加载）', jsErrors.filter(e => !/Failed to load resource/i.test(e)).length === 0,
  JSON.stringify(jsErrors.slice(0, 5)))

fs.writeFileSync(path.join(OUT, `scenarioA-${which}.json`),
  JSON.stringify({ which, results, api: api.map(a => ({ url: a.url, status: a.status })), oss, jsErrors, toasts }, null, 1))
const failed = results.filter(r => !r.ok)
console.log(`\n==== ${which}: ${results.length - failed.length}/${results.length} PASS ====`)
if (failed.length) console.log('FAILED:', failed.map(f => f.name).join(' | '))
await browser.close()
process.exit(failed.length ? 1 : 0)
