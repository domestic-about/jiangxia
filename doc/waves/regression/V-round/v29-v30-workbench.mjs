// ============================================================================
// V29 / V30 活体验收（工作台真 DOM）
//
// 独立验收报告：
//   V29「工作台『渲染失败』卡片直达开发调试页，没有失败清单」→ 修法：点开是「渲染失败与缺图」清单抽屉。
//   V30「工作台文档控制台默认填测试样本 id 9000001001，页面上还露票号」→ 修法：不默认填、不露票号。
//
// 本脚本不读实现、不做源码 grep：真浏览器登录工作台 → 首页点「渲染失败」卡片 → 看是不是**抽屉清单**而不是
// 跳到调试页；再进 /doc-console → 看样本 id 输入框是否为空、页面文字里有没有种子票号。
//
// 跑法（工作区根）：node doc/waves/regression/V-round/v29-v30-workbench.mjs
// 前置：后端 8091（dev）、工作台 8093、seed 已灌。
// ============================================================================
import { createRequire } from 'node:module'
import path from 'node:path'

const WS = process.env.LQG_WS || process.cwd()
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const WEB = process.env.LQG_WEB || 'http://127.0.0.1:8093'
const fails = []
const check = (n, ok, extra = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${extra ? ' — ' + extra : ''}`); if (!ok) fails.push(n) }

const run = async () => {
  const browser = await chromium.launch()
  const page = await browser.newPage()

  // 登录
  await page.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login-form input', { timeout: 30000 })
  const inputs = page.locator('.login-form input')
  await inputs.nth(0).fill('lqgadmin')
  await inputs.nth(1).fill('admin123')
  await page.click('.login-form button')
  await page.waitForTimeout(2500)
  check('登录成功', !page.url().includes('login'), page.url())

  // ── V29：首页「渲染失败」卡片 → 应弹清单抽屉，而不是跳调试页 ────────────────
  await page.goto(`${WEB}/index`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(2000)
  const card = page.locator('text=渲染失败').first()
  const hasCard = await card.count() > 0
  check('首页有「渲染失败」卡片', hasCard)
  if (hasCard) {
    const urlBefore = page.url()
    await card.click({ timeout: 8000 }).catch(() => {})
    await page.waitForTimeout(1500)
    const drawer = await page.locator('.el-drawer:visible, .el-dialog:visible').count()
    const urlAfter = page.url()
    const bodyTxt = (await page.locator('.el-drawer:visible, .el-dialog:visible').first().innerText().catch(() => '')).replace(/\s+/g, ' ')
    check('V29 点开是抽屉/弹层（不是跳页）', drawer > 0 && urlAfter === urlBefore,
      `drawer=${drawer} urlChanged=${urlAfter !== urlBefore}`)
    check('V29 抽屉里有失败/缺图清单内容', /失败|缺图|渲染/.test(bodyTxt), bodyTxt.slice(0, 120))
  }

  // ── V30：文档控制台不默认填样本 id、页面不露票号 ───────────────────────────
  // ★ 先试几个候选路由再断言：菜单 path 是 `doc-console`，但它在 `qc-console` 父菜单下，
  //   实测 /doc-console 直接 404。**必须先确认页面不是 404**，否则「页面里没有样本 id」是空对照假绿。
  let v30ok = false
  for (const p of ['/qc-console/doc-console', '/doc-console']) {
    await page.goto(`${WEB}${p}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(1800)
    const t = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ')
    if (!/404错误|找不到网页/.test(t)) { console.log(`  · V30 用路由 ${p}`); v30ok = true; break }
  }
  check('V30 页面可打开（不是 404 空对照）', v30ok)
  const txt = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ')
  check('V30 页面不出现测试样本 id 9000001001', !txt.includes('9000001001'))
  check('V30 页面不出现种子票号（T-hli01 / SJ90000001）', !/T-hli01|SJ90000001/.test(txt))
  const idInput = page.locator('input').first()
  const idVal = await idInput.inputValue().catch(() => '(读不到)')
  check('V30 样本 id 输入框为空（不预填）', idVal === '', `value="${idVal}"`)
  console.log('  页面文字（前 200 字）：' + txt.slice(0, 200))

  await browser.close()
  console.log(`\n结果：${fails.length === 0 ? 'PASS' : 'FAIL'}（失败 ${fails.length} 条）`)
  if (fails.length) { console.log('失败项：' + fails.join(' / ')); process.exit(1) }
}

run().catch((e) => { console.error('[ERROR]', e); process.exit(2) })
