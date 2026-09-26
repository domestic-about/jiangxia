// SYS-ACCEPT-001 · 六个热点的**行为判据**探针（H3a 是唯一允许读源码的例外，见 probe-h3a.py）。
//
//   node probe.mjs H1a   → SYS-EXPORT-001  · 导出请求真发一次：请求头必须带 Authorization + clientid，且 200/真 xlsx
//   node probe.mjs H1b   → SYS-EXPORT-001  · 文档下载真点一次：OSS 直链请求**不带** Authorization，且 200
//   node probe.mjs H2    → SYS-HOME-001    · 真 DOM 读六张卡片数字 == 同一次 /lqg/home/todo 的返回值（CR-20260924-10 起待核验样本拆成两张）
//   node probe.mjs H3b   → DOC-MP-002      · 点缩略图后打开层拿到的 src == 原图 url（且 ≠ previewUrl）
//   node probe.mjs H4    → DOC-PUBLISH-001 · 真 DOM 数下载入口恰好 4 个，且各自 format/合并位正确
//
// 退出码：0 = 判据成立（绿）｜1 = 判据被违反（红）｜2 = 用法/环境错。
// 证据：doc/waves/regression/D7/accept-strengthened/observations/<HOTSPOT>.json
//
// ★ Playwright require 锚点 = code/miniapp/package.json（issue #229）。
// ★ 截图不落盘、PNG 不进上下文。
import fs from 'node:fs'
import path from 'node:path'
import {
  ROOT, OBS, WEB, MP, OSS,
  playwright, collector, done, headerOf,
  loginWeb, loginH5Internal,
} from './common.mjs'

const HOT = String(process.argv[2] || '').toUpperCase()
const { chromium } = playwright()

// ════════════════════════════════════════════════════════════════════════════
async function h1a() {
  const c = collector('H1a')
  const exportCalls = []
  const browser = await chromium.launch()
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const page = await ctx.newPage()
  // 导出的响应体是 xlsx（页面 fetch 会把它消费掉）→ 用 route.fetch() 拿原始字节最可靠
  await page.route('**/mp/int/export/**', async (route) => {
    const req = route.request()
    try {
      const response = await route.fetch()
      const buf = await response.body()
      exportCalls.push({
        url: req.url().split('?')[0] + (req.url().includes('?') ? '?' + req.url().split('?')[1] : ''),
        status: response.status(),
        headers: req.headers(),
        bytes: buf.length,
        magic: buf.slice(0, 2).toString('latin1'),
      })
      await route.fulfill({ response, body: buf })
    } catch (e) {
      exportCalls.push({ url: req.url(), status: 0, headers: req.headers(), bytes: null, magic: null, err: String(e).slice(0, 200) })
      await route.abort()
    }
  })
  const okLogin = await loginH5Internal(page)
  c.rec('H1a:内部人员 mock 登录 H5(9204)', okLogin, page.url())

  await page.locator('.uni-tabbar__item', { hasText: '我的' }).first().click()
  await page.waitForTimeout(2500)
  await page.locator('.adm .merow', { hasText: '样本记录信息表' }).first().click()
  await page.waitForTimeout(3000)
  await page.locator('.ledger-page__chip', { hasText: '待核验' }).first().click()
  await page.waitForTimeout(2500)
  await page.locator('.ledger-page__export').first().click()
  await page.waitForTimeout(9000)

  c.rec('H1a:导出请求真的发出（真点一次「导出 Excel」）', exportCalls.length >= 1, JSON.stringify(exportCalls.map(x => x.url)))
  const call = exportCalls[exportCalls.length - 1]
  if (call) {
    const auth = headerOf(call.headers, 'authorization')
    const clientid = headerOf(call.headers, 'clientid')
    c.note('export_url', call.url)
    c.note('export_request_headers', {
      authorization: auth ? auth.slice(0, 22) + '…（Bearer JWT）' : '(无)',
      clientid: clientid || '(无)',
      all_keys: Object.keys(call.headers).filter(k => /authorization|clientid|content-type|accept/i.test(k)),
    })
    c.note('export_response', { status: call.status, bytes: call.bytes, magic: call.magic })
    c.rec('H1a:★ 导出请求头带 Authorization（Bearer JWT）', /^Bearer .+/.test(auth), JSON.stringify({ hasAuth: !!auth }))
    c.rec('H1a:★ 导出请求头带 clientid', !!clientid, JSON.stringify({ clientid }))
    c.rec('H1a:★ 导出响应 200', call.status === 200, String(call.status))
    c.rec('H1a:★ 响应体是真 xlsx（ZIP 魔数 PK + 有字节数）', call.magic === 'PK' && call.bytes > 1000,
      JSON.stringify({ magic: call.magic, bytes: call.bytes }))
    c.rec('H1a:地址带上筛选项 verifyStatus=pending（筛了什么导出什么）', /verifyStatus=pending/.test(call.url), call.url)
  } else {
    c.rec('H1a:★ 导出请求头带 Authorization（Bearer JWT）', false, '没有导出请求')
  }
  const green = c.finish()
  await done(browser, green)
}

// ════════════════════════════════════════════════════════════════════════════
async function h1b() {
  const c = collector('H1b')
  const ossReq = [], ossRes = [], api = []
  const browser = await chromium.launch()
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const page = await ctx.newPage()
  page.on('request', (r) => {
    const u = r.url()
    if (u.startsWith(OSS)) ossReq.push({ url: u.split('?')[0], headers: r.headers() })
  })
  page.on('response', async (r) => {
    const u = r.url()
    try {
      if (u.startsWith(OSS)) ossRes.push({ url: u.split('?')[0], status: r.status(), ct: r.headers()['content-type'] || '' })
      if (u.includes('/mp/int/doc/')) api.push({ url: u, status: r.status(), body: (await r.text()).slice(0, 1500) })
    } catch { /* ignore */ }
  })
  const okLogin = await loginH5Internal(page)
  c.rec('H1b:内部人员 mock 登录 H5(9204)', okLogin, page.url())
  await page.locator('.uni-tabbar__item', { hasText: '文档' }).first().click()
  await page.waitForTimeout(3000)
  c.rec('H1b:进入文档页签', page.url().includes('pages/doc/index'), page.url())

  const firstCard = page.locator('.gcd').first()
  const dl = firstCard.locator('.gcd__dl').first()
  const hasDl = (await firstCard.locator('.gcd__dl').count()) >= 1
  c.rec('H1b:第一组卡片上有「下载」入口（渲染产物已就绪）', hasDl, JSON.stringify(await firstCard.locator('.gcd__dl').count()))
  if (!hasDl) { const green = c.finish(); await done(browser, green) }

  const beforeApi = api.length, beforeOss = ossReq.length, beforeOssRes = ossRes.length
  await dl.click()
  await page.waitForSelector('.ds__t', { timeout: 15000 })
  // ★ 格式选择会跨弹层留存（已知 S3）→ 显式点一次 Word，保证这一次就是单份 Word 下载
  await page.locator('.db__fmt-item', { hasText: 'Word' }).click()
  await page.waitForTimeout(250)
  await page.locator('.db__btn', { hasText: '打开' }).click()
  await page.waitForTimeout(8000)

  const dlReq = api.slice(beforeApi).filter(x => /\/download\?format=/.test(x.url))
  let dlBody = null
  try { dlBody = JSON.parse(dlReq[dlReq.length - 1].body) } catch { /* ignore */ }
  const signedUrl = String(dlBody?.data?.url || '')
  c.note('backend_signed_url', signedUrl.split('?')[0] + (signedUrl.includes('?') ? '?X-Amz-…' : ''))
  c.rec('H1b:后端签发 OSS 预签名直链（code=200 + url 指向 127.0.0.1:9000）',
    dlBody?.code === 200 && signedUrl.startsWith(OSS),
    JSON.stringify({ api: dlReq.map(x => x.url), code: dlBody?.code, url: signedUrl.slice(0, 110) }))

  const reqs = ossReq.slice(beforeOss)
  const ress = ossRes.slice(beforeOssRes)
  const withAuth = reqs.filter(r => Object.keys(r.headers).some(k => k.toLowerCase() === 'authorization'))
  const withClientId = reqs.filter(r => Object.keys(r.headers).some(k => k.toLowerCase() === 'clientid'))
  c.note('oss_request_headers', reqs.map(r => ({ url: r.url, header_keys: Object.keys(r.headers), authorization: headerOf(r.headers, 'authorization') ? '(有！)' : '(无)', clientid: headerOf(r.headers, 'clientid') || '(无)' })))
  c.note('oss_response', ress)
  c.rec('H1b:★ OSS 直链请求真的发出（真点一次单份下载）', reqs.length >= 1, JSON.stringify(reqs.map(r => r.url)))
  c.rec('H1b:★ OSS 直链请求头**没有 Authorization**（API 鉴权头不能塞到预签名直链上）',
    reqs.length >= 1 && withAuth.length === 0,
    JSON.stringify({ n: reqs.length, authHeaders: withAuth.map(r => headerOf(r.headers, 'authorization')).map(s => String(s).slice(0, 20)) }))
  c.rec('H1b:★ OSS 响应码 = 200（MinIO 没判「multiple authentication types」）',
    ress.some(r => r.status === 200),
    JSON.stringify(ress))
  c.note('clientid_on_oss_request_count', withClientId.length)

  const hint = await page.locator('.db__hint').allInnerTexts().catch(() => [])
  c.rec('H1b:页面没有进失败态（.db__hint 里没有「暂时下不了」）',
    !JSON.stringify(hint).includes('暂时下不了'), JSON.stringify(hint))
  const green = c.finish()
  await done(browser, green)
}

// ════════════════════════════════════════════════════════════════════════════
async function h2() {
  const c = collector('H2')
  const todo = []
  const browser = await chromium.launch()
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1100 }, locale: 'zh-CN' })
  const page = await ctx.newPage()
  page.on('response', async (r) => {
    if (r.url().includes('/lqg/home/todo')) {
      try { todo.push({ url: r.url(), status: r.status(), body: JSON.parse(await r.text()) }) } catch { /* ignore */ }
    }
  })
  await loginWeb(page)
  c.rec('H2:工作台登录 lqgadmin', true, page.url())
  await page.goto(`${WEB}/index`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.lqg-home__cards', { timeout: 30000 })
  await page.waitForTimeout(3500)

  const cards = await page.locator('.lqg-home__cards > *').evaluateAll(els => els.map((e) => {
    const t = e.querySelector('.lqg-todo-card__label')
    const v = e.querySelector('.lqg-todo-card__num')
    return { title: t ? t.textContent.trim() : e.innerText.split('\n')[0], value: v ? v.textContent.trim() : null }
  }))
  const last = todo[todo.length - 1]
  const apiData = last?.body?.data || null
  c.note('api_get_lqg_home_todo', { url: last?.url || '(没抓到)', status: last?.status ?? null, data: apiData })
  c.note('dom_cards', cards)
  c.rec('H2:真 DOM 渲染出六张待办卡片', cards.length === 6, JSON.stringify(cards.map(x => x.title)))
  c.rec('H2:抓到同一次 /lqg/home/todo 的响应（判据的另一侧是真接口返回值）',
    !!apiData, JSON.stringify({ n: todo.length, data: apiData }))

  const map = [
    ['样本记录', 'pendingTissue'], ['类器官收样', 'pendingOrganoid'], ['石蜡', 'pendingEmbeds'],
    ['超期', 'cryoOverdue'], ['外部', 'pendingExtUsers'], ['渲染', 'renderFailed'],
  ]
  const pairs = []
  if (apiData) {
    for (const [kw, key] of map) {
      const card = cards.find(x => (x.title || '').includes(kw))
      const want = String(apiData[key])
      pairs.push({ card: kw, field: key, api_value: want, dom_value: card ? card.value : null })
      c.rec(`H2:★ 卡片「${kw}」的 DOM 数字 == /lqg/home/todo.${key}`,
        !!card && card.value === want, JSON.stringify({ dom: card ? card.value : null, api: want }))
    }
  }
  c.note('dom_vs_api', pairs)
  const green = c.finish()
  await done(browser, green)
}

// ════════════════════════════════════════════════════════════════════════════
async function h3b() {
  const c = collector('H3b')
  const pages = []
  const browser = await chromium.launch()
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const page = await ctx.newPage()
  page.on('response', async (r) => {
    if (/\/mp\/int\/doc\/9000001001\/sample_qc\/pages/.test(r.url())) {
      try { pages.push(JSON.parse(await r.text())) } catch { /* ignore */ }
    }
  })
  const okLogin = await loginH5Internal(page)
  c.rec('H3b:内部人员 mock 登录 H5(9204)', okLogin, page.url())
  await page.goto(`${MP}/#/pages/doc/preview?sampleId=9000001001&docKind=sample_qc`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.tst__thumb', { timeout: 40000 })
  await page.waitForTimeout(1500)

  const body = pages[pages.length - 1]
  const images = (body?.data?.images || []).filter(im => String(im?.url || '').trim() || String(im?.previewUrl || '').trim())
  const thumbCount = await page.locator('.tst__thumb').count()
  c.note('pages_api_images', images.map(im => ({ url: String(im.url || '').split('?')[0], previewUrl: String(im.previewUrl || '').split('?')[0] })))
  c.rec('H3b:抓到 pages 接口的 images（原图 url / previewUrl 都在里面）', images.length >= 1, JSON.stringify({ n: images.length }))
  c.rec('H3b:真 DOM 缩略图数 == 可取到链接的图片数', thumbCount === images.length,
    JSON.stringify({ thumbs: thumbCount, images: images.length }))
  if (images.length === 0 || thumbCount === 0) { const green = c.finish(); await done(browser, green) }

  const pathOf = u => String(u || '').split('?')[0]
  await page.locator('.tst__thumb').first().click()
  await page.waitForTimeout(2000)
  const opened = await page.evaluate(() => {
    const el = document.querySelector('uni-swiper')
    if (!el) return null
    return [...el.querySelectorAll('img')].map(i => i.getAttribute('src')).filter(Boolean)
  })
  const target = images[0]
  const wantOrig = pathOf(target.url)
  const wantPrev = pathOf(target.previewUrl)
  const openedPaths = (opened || []).map(pathOf)
  c.note('thumb_click_expected', { original_url: wantOrig, preview_url: wantPrev })
  c.note('thumb_click_opened_src', openedPaths)
  c.rec('H3b:点缩略图 → H5 打开层出现（对应真机 wx.previewImage）', Array.isArray(opened) && opened.length >= 1, JSON.stringify(openedPaths))
  c.rec('H3b:★ 打开层拿到的 src == 原图 url', openedPaths.includes(wantOrig), JSON.stringify({ opened: openedPaths, want: wantOrig }))
  c.rec('H3b:★ 打开层拿到的 src ≠ previewUrl（看的是原图不是预览图）',
    wantPrev !== wantOrig && !openedPaths.includes(wantPrev), JSON.stringify({ opened: openedPaths, preview: wantPrev }))
  const green = c.finish()
  await done(browser, green)
}

// ════════════════════════════════════════════════════════════════════════════
async function h4() {
  const c = collector('H4')
  const net = []
  const browser = await chromium.launch()
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1100 }, locale: 'zh-CN' })
  const page = await ctx.newPage()
  ctx.on('page', p => { p.close().catch(() => {}) })   // 下载会 window.open 签名链接 → 立刻收掉，别留着
  page.on('response', (r) => {
    if (/\/lqg\/doc\//.test(r.url())) net.push({ url: r.url().replace(WEB, ''), status: r.status(), method: r.request().method() })
  })
  await loginWeb(page)
  c.rec('H4:工作台登录 lqgadmin', true, page.url())
  await page.goto(`${WEB}/qc-console/qc-editor?sampleId=9000001001`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.lqg-qc-editor__tabs', { timeout: 30000 })
  await page.waitForTimeout(2500)
  await page.getByRole('button', { name: /预览/ }).first().click()
  await page.waitForSelector('.lqg-preview-pane__page-img img', { timeout: 40000 })
  await page.waitForTimeout(2000)

  const row = page.locator('.lqg-preview-pane__downloads-row button')
  const labels = (await row.allInnerTexts()).map(s => s.replace(/\s+/g, ' ').trim())
  c.note('dom_download_buttons', labels)
  c.rec('H4:★ 真 DOM 渲染出的下载入口恰好 4 个', labels.length === 4, JSON.stringify({ count: labels.length, labels }))

  const cases = [
    ['下载 Word', 'docx', false],
    ['下载 PDF', 'pdf', false],
    ['下载合并 Word', 'docx', true],
    ['下载合并 PDF', 'pdf', true],
  ]
  const hits = []
  for (const [label, fmt, merged] of cases) {
    const before = net.length
    const btn = row.filter({ hasText: label }).first()
    if ((await btn.count()) === 0) { hits.push({ label, found: false }); continue }
    await btn.click().catch(() => {})
    let one = null
    for (let i = 0; i < 30; i++) {
      await page.waitForTimeout(300)
      one = net.slice(before).filter(x => /\/download\?/.test(x.url)).pop()
      if (one) break
    }
    const url = one?.url || ''
    const okFmt = url.includes(`format=${fmt}`)
    const okMerged = url.includes('/merged/') === merged
    hits.push({ label, found: true, requested: url.split('?')[0], query: url.split('?')[1] || '', format_ok: okFmt, merged_ok: okMerged, status: one?.status ?? null })
    c.rec(`H4:★ 「${label}」→ 真请求 format=${fmt}${merged ? ' + 合并件' : ''}`,
      !!one && okFmt && okMerged, JSON.stringify(one || 'no request'))
  }
  c.note('download_button_hits', hits)
  const expectedLabels = cases.map(x => x[0])
  c.rec('H4:四个入口的文案恰好是 [Word, PDF, 合并 Word, 合并 PDF]',
    JSON.stringify(labels) === JSON.stringify(expectedLabels), JSON.stringify({ labels, expectedLabels }))
  const green = c.finish()
  await done(browser, green)
}

const table = { H1A: h1a, H1B: h1b, H2: h2, H3B: h3b, H4: h4 }
if (!table[HOT]) {
  console.error(`[error] 未知热点 ${HOT}；可选：${Object.keys(table).join(' / ')}（H3a 见 probe-h3a.py）`)
  process.exit(2)
}
fs.mkdirSync(OBS, { recursive: true })
await table[HOT]()
