/**
 * D2 / r1 / L3 —— 浅 H5 DOM 检查（**不复跑 L2 的深度走查**，只验 L3 的三小项）：
 *   ① 第 6 条：新外部用户（无样本、未填组别）的首页与历史编辑记录**不报错**
 *   ② 第 7 条前半：「内部管理」板块外部不渲染 / 内部渲染
 *   ③ 第 7 条前半：身份缺失时首页与「我的」一格/一块都不渲染
 *
 * 前置：后端 8081 + reseed + miniapp H5 dev 9200（VITE_MOCK_LOGIN=1）。
 * 跑法：node doc/waves/regression/D2/L3-mp-shallow.mjs
 * 截图只落盘，不进上下文。
 */
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const MP = process.env.LQG_H5_BASE || 'http://127.0.0.1:9200'
const SHOTS = path.join(HERE, 'shots/L3-mp')
mkdirSync(SHOTS, { recursive: true })

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await ctx.newPage()
const pageErrors = []
const consoleErrs = []
page.on('pageerror', e => pageErrors.push(e.message))
page.on('console', m => { if (m.type() === 'error') consoleErrs.push(m.text()) })

const shot = n => page.screenshot({ path: path.join(SHOTS, `${n}.png`), fullPage: false }).catch(() => {})
const bodyText = () => page.evaluate(() => document.body.innerText)
const tileCount = () => page.$$eval('.lqg-tile', els => els.length).catch(() => -1)

async function resetToLogin() {
  await page.goto(`${MP}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login__agree', { timeout: 30000 })
  await page.waitForTimeout(400)
}

async function mockLogin(label, expectTiles) {
  const on = await page.$eval('.login__box', el => el.className.includes('login__box--on')).catch(() => false)
  if (!on) await page.click('.login__box')
  await page.click(`.login__mock-btn:has-text("${label}")`)
  await page.waitForSelector('.lqg-tile', { timeout: 30000 })
  await page.waitForTimeout(800)
  if (expectTiles != null) {
    const n = await tileCount()
    check(`首页宫格数 = ${expectTiles}（${label}）`, n === expectTiles, `实际 ${n}`)
  }
}

async function goHash(hash, waitSel) {
  await page.evaluate(h => { location.hash = h }, hash)
  await page.waitForTimeout(300)
  await page.reload({ waitUntil: 'domcontentloaded' })   // 硬 reload：hash 路由下 goto 不重载
  if (waitSel) await page.waitForSelector(waitSel, { timeout: 30000 }).catch(() => {})
  await page.waitForTimeout(900)
}

// ─────────────────────────────────────────────────────────────────────────────
// ① 新外部用户 newbie1（13800000099：库里 0 条样本、ext_profile unbound、unit/group 全 null）
// ─────────────────────────────────────────────────────────────────────────────
await resetToLogin()
await mockLogin('外部人员 · 新号（未绑定）', 3)
{
  const t = await bodyText()
  const bad = /没能加载|加载失败|重试|系统异常|网络异常/.test(t)
  check('① newbie1 首页不报错（无失败/重试文案）', !bad, JSON.stringify(t.slice(0, 120)))
  check('① newbie1 首页仍未绑定提示可见（unbound）', /单位|组别/.test(t), JSON.stringify(t.slice(0, 160)))
  check('① newbie1 首页无 pageerror', pageErrors.length === 0, pageErrors.join(' | ').slice(0, 200))
  await shot('L3-a1-newbie-home')

  await goHash('#/pages/history/index', '.his__switch')
  const h = await bodyText()
  const hbad = /没能加载历史编辑记录|系统异常|网络异常/.test(h)
  check('① newbie1 历史编辑记录不报错（有数据源且走空态，不是 ErrorState）', !hbad, JSON.stringify(h.slice(0, 200)))
  await shot('L3-a2-newbie-history')
}

// ─────────────────────────────────────────────────────────────────────────────
// ② extA 的「我的」不渲染内部管理；staff 的「我的」渲染内部管理
// ─────────────────────────────────────────────────────────────────────────────
pageErrors.length = 0
await resetToLogin()
await mockLogin('外部人员 · 王医生（已核验）', 3)
await goHash('#/pages/me/index', '.me')
{
  const t = await bodyText()
  check('② 外部「我的」正文不含「内部管理」', !t.includes('内部管理'), JSON.stringify(t.slice(0, 200)))
  check('② 外部「我的」有「历史编辑记录」与「单位与组别」', t.includes('历史编辑记录') && t.includes('单位与组别'))
  await shot('L3-b1-ext-me')

  pageErrors.length = 0
  await resetToLogin()
  await mockLogin('内部人员 · 李工', 4)
  await goHash('#/pages/me/index', '.me')
  const t2 = await bodyText()
  check('② 内部「我的」正文含「内部管理」', t2.includes('内部管理'), JSON.stringify(t2.slice(0, 240)))
  check('② 内部「我的」不含「单位与组别」区块', !t2.includes('单位与组别'))
  await shot('L3-b2-staff-me')
}

// ─────────────────────────────────────────────────────────────────────────────
// ③ 身份缺失：拦截 /mp/me，identity 置 null → 首页一格不渲染 / 我的一块不出
// ─────────────────────────────────────────────────────────────────────────────
await resetToLogin()
await mockLogin('内部人员 · 李工', 4)
await page.route('**/mp/me*', async route => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, msg: '操作成功', data: { userId: '1', name: 'x', phoneMasked: '', identity: null, ext: null } }),
  })
})
await goHash('#/pages/index/index', '.home')
{
  const n = await tileCount()
  const t = await bodyText()
  check('③ identity=null：首页宫格 0 格', n === 0, `tiles=${n}`)
  check('③ identity=null：首页出「没能确认你的身份」兜底（不默认内部）', t.includes('没能确认你的身份'), JSON.stringify(t.slice(0, 160)))
  check('③ identity=null：首页无 pageerror', pageErrors.length === 0, pageErrors.join(' | ').slice(0, 200))
  await shot('L3-c1-identity-missing-home')

  await goHash('#/pages/me/index', '.me')
  const t2 = await bodyText()
  check('③ identity=null：「我的」不渲染历史编辑记录 / 内部管理 / 单位与组别',
    !t2.includes('历史编辑记录') && !t2.includes('内部管理') && !t2.includes('单位与组别'), JSON.stringify(t2.slice(0, 200)))
  await shot('L3-c2-identity-missing-me')
}
// 未知身份串（大小写/其它值）同样按未知处理
await page.unroute('**/mp/me*')
await page.route('**/mp/me*', async route => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, msg: '操作成功', data: { userId: '1', name: 'x', phoneMasked: '', identity: 'INTERNAL', ext: null } }),
  })
})
await goHash('#/pages/index/index', '.home')
{
  const n = await tileCount()
  check('③ identity="INTERNAL"（契约外大小写）：首页宫格 0 格（不默认内部）', n === 0, `tiles=${n}`)
}
await page.unroute('**/mp/me*')

console.log(`\n== pageerror ${pageErrors.length} 条 / console.error ${consoleErrs.length} 条 ==`)
consoleErrs.slice(0, 5).forEach(e => console.log('  console.error:', e.slice(0, 160)))

const pass = results.filter(r => r.ok).length
console.log(`\n== L3 浅 H5：${pass}/${results.length} ==`)
await browser.close()
process.exit(results.every(r => r.ok) ? 0 : 1)
