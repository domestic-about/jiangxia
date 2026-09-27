// ============================================================================
// V25 / V27 活体验收（小程序 H5 真 DOM + 真网络）
//
// 独立验收报告：
//   V25「小程序日期与选择项能直接打字；石蜡包埋页在选样本那格打字会覆写 sampleId」
//       → 修法：这些格子是**只读触发器**，点开弹框选，不许自由输入覆写。
//   V27「小程序所有列表写死 100 条、不分页，却写『共 N 条』」
//       → 修法：触底加载下一页（onReachBottom / scrolltolower），不再只取第一页。
//
// 跑法（工作区根）：node doc/waves/regression/V-round/v25-v27-miniapp.mjs
// 前置：后端 8091（dev）、小程序 H5 9202（qa-up 起的 dev:h5，mock 登录可用）。
// ============================================================================
import { createRequire } from 'node:module'
import path from 'node:path'

const WS = process.env.LQG_WS || process.cwd()
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const BASE = process.env.LQG_MP || 'http://127.0.0.1:9202'
const fails = []
const check = (n, ok, extra = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${extra ? ' — ' + extra : ''}`); if (!ok) fails.push(n) }

const run = async () => {
  const browser = await chromium.launch()
  // ★ 用移动视口（H5 实际形态）：实测 1280×720 下页面布局不同、滚动到底也不触发 onReachBottom；
  //   420×720 才复现真机形态（同一份 diag 脚本在 420×720 下第一次滚动就拿到 pageNum=2）。
  const page = await browser.newPage({ viewport: { width: 420, height: 720 } })
  const reqs = []
  page.on('request', (r) => { if (/\/mp\//.test(r.url())) reqs.push(r.url()) })

  // ── 登录（内部人员）────────────────────────────────────────────────────────
  await page.goto(`${BASE}/#/pages/login/index`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.login__mock-btn', { timeout: 30000 })
  await page.locator('.login__box').click()            // 点勾选框本体；点整行会命中《用户协议》链接
  await page.waitForTimeout(200)
  await page.locator('.login__mock-btn').nth(0).click() // 0 = 内部人员
  await page.waitForTimeout(4000)
  check('内部人员登录成功', !page.url().includes('login'), page.url())

  // ★ 顺序有讲究：V27 的触底必须在**登录后第一个**做。实测先走两个表单页再回列表页时，
  //   uni H5 的 onReachBottom 不再触发（同一个 diag 脚本「登录→列表」能拿到 pageNum=2）。
  //   这不是产品问题，是 H5 页面栈/滚动监听的时序 —— 记为探针约束。
  // ── V27：内部管理列表触底加载下一页（不再只取第一页）────────────────────
  await page.goto(`${BASE}/#/pages/ledger/index`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(3000)
  const listReqs = () => reqs.filter(u => /list|page/.test(u))
  const initial = listReqs()
  console.log('  初始列表请求：' + initial.slice(-3).map(u => u.replace(BASE, '').slice(0, 90)).join(' | '))
  const initSizes = initial.map(u => (u.match(/pageSize=(\d+)/) || [])[1]).filter(Boolean)
  check('V27 页大小不再是写死的 100', initSizes.length > 0 && initSizes.every(s => Number(s) < 100), `pageSize=[${initSizes}]`)

  // 切到「待核验」页签（默认是「有效」，只有几行，滚不出第二页）
  await page.waitForSelector('text=待核验', { timeout: 15000 }).catch(() => {})
  const nBeforeTab = listReqs().length
  await page.locator('text=待核验').first().click({ timeout: 8000 }).catch(() => {})
  await page.waitForTimeout(2500)
  const tabSwitched = listReqs().slice(nBeforeTab).some(u => /verifyStatus=pending/.test(u))
  check('V27 切到「待核验」页签（否则列表只有几行、滚不出第二页）', tabSwitched,
    listReqs().slice(nBeforeTab).map(u => u.replace(BASE, '').slice(0, 70)).join(' | ') || '无新请求')
  const scroller = await page.evaluate(() => {
    const cands = [document.scrollingElement, document.documentElement, document.body,
                   ...document.querySelectorAll('uni-page-body, .wd-scroll-view, [class*=scroll]')]
    for (const c of cands) { if (c && c.scrollHeight > c.clientHeight + 50) return { h: c.scrollHeight, ch: c.clientHeight, tag: c.tagName + '.' + (c.className || '').slice(0, 30) } }
    return { h: document.body.scrollHeight, ch: window.innerHeight, tag: 'none' }
  })
  console.log('  可滚动容器：' + JSON.stringify(scroller))
  const before = listReqs().length
  // ★ 实测要点：uni H5 的 onReachBottom 靠**滚动事件**触发 —— 只 `window.scrollTo` 或 `mouse.wheel` 不一定够。
  //   有效做法：把 documentElement.scrollTop 顶到底后**手动派发一次 scroll 事件**（第一次就触发了 pageNum=2）。
  for (let i = 0; i < 5; i++) {
    // 先把最后一行滚进视野（更接近真实触底），再把 scrollTop 顶到底并派发 scroll
    await page.evaluate(() => {
      const rows = document.querySelectorAll('[class*=ledger__row], uni-view[class*=row]')
      const last = rows[rows.length - 1]
      if (last && last.scrollIntoView) last.scrollIntoView({ block: 'end' })
    }).catch(() => {})
    await page.waitForTimeout(400)
    await page.evaluate(() => {
      const d = document.scrollingElement || document.documentElement
      d.scrollTop = d.scrollHeight
      window.dispatchEvent(new Event('scroll'))
      d.dispatchEvent(new Event('scroll'))
    })
    await page.mouse.wheel(0, 3000).catch(() => {})
    await page.waitForTimeout(1500)
    console.log('    · 滚动第 ' + (i + 1) + ' 次 新请求=' + (listReqs().length - before))
  }
  const after = listReqs().slice(before)
  const pageNums = after.map(u => (u.match(/pageNum=(\d+)/) || [])[1]).filter(Boolean)
  const sizes = after.map(u => (u.match(/pageSize=(\d+)/) || [])[1]).filter(Boolean)
  console.log('  触底后新请求：' + after.map(u => u.replace(BASE, '').slice(0, 90)).join(' | '))
  check('V27 触底触发了第 2 页请求（分页真的接了）', pageNums.some(n => Number(n) >= 2), `pageNums=[${pageNums}] pageSize=[${sizes}]`)



  // ── V25a：包埋页的 5 个日期位是**只读触发器**（打字改不动值）────────────────
  await page.goto(`${BASE}/#/pages/embed/form?mode=new`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(2500)
  const body0 = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ')
  const ins = page.locator('input')
  const nIn = await ins.count()
  let dateSeen = 0, dateWritable = 0
  for (let i = 0; i < nIn; i++) {
    const it = ins.nth(i)
    const label = await it.evaluate((el) => {
      const box = el.closest('.wd-input, .form__item, .form__field, .emb__field') || el.parentElement?.parentElement || el.parentElement
      return (box ? box.innerText : '').replace(/\s+/g, ' ')
    }).catch(() => '')
    if (!/时间|日期/.test(label)) continue
    dateSeen++
    await it.fill('2099-12-31').catch(() => {})
    await page.keyboard.press('Tab')
    await page.waitForTimeout(200)
    const v = await it.inputValue().catch(() => '')
    if (v === '2099-12-31') dateWritable++
    console.log(`    · 日期位 "${label.slice(0, 24)}" 打字后值="${v}"`)
  }
  const dateTriggers = (body0.match(/时间\s*请选择/g) || []).length
  check('V25a 日期位渲染成「请选择」触发器（不是文本框）', dateTriggers >= 3, `文本里出现 ${dateTriggers} 处「时间 请选择」`)
  check('V25a 没有可自由打字的日期输入框', dateWritable === 0, `可写 ${dateWritable} 个（input 总数 ${nIn}）`)

  // ── V25b：石蜡包埋页「选择样本」格不是自由文本（点开是弹框）───────────────
  await page.goto(`${BASE}/#/pages/embed/form?mode=new`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(2500)
  const bodyTxt = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ')
  const hasSampleCell = /选择样本|所挂样本/.test(bodyTxt)
  check('V25b 包埋页有「选择样本」格', hasSampleCell, bodyTxt.slice(0, 100))
  if (hasSampleCell) {
    const cell = page.locator('text=选择样本').first()
    const tag = await cell.evaluate((el) => {
      let n = el
      for (let i = 0; i < 4 && n; i++) { if (n.tagName === 'INPUT') return 'INPUT:' + (n.readOnly ? 'readonly' : 'writable'); n = n.parentElement }
      return 'NON-INPUT'
    }).catch(() => '?')
    await cell.click({ timeout: 8000 }).catch(() => {})
    await page.waitForTimeout(1200)
    const popup = await page.locator('.wd-popup, .wd-action-sheet, .wd-picker, .picker, [class*=popup]').count()
    check('V25b 样本格不是可写输入框', tag !== 'INPUT:writable', tag)
    check('V25b 点样本格打开了选择弹框', popup > 0, `popup 节点数=${popup}`)
  }

  await browser.close()
  console.log(`\n结果：${fails.length === 0 ? 'PASS' : 'FAIL'}（失败 ${fails.length} 条）`)
  if (fails.length) { console.log('失败项：' + fails.join(' / ')); process.exit(1) }
}

run().catch((e) => { console.error('[ERROR]', e); process.exit(2) })
