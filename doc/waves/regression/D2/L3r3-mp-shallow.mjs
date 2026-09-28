/**
 * D2 r3 · L3 —— 浅 H5 DOM 检查（**本片自写脚本**，不复用 r1/r2 的 mjs 当证据；不复跑 L2 深度走查）。
 * 覆盖：
 *   ① 第 6 条：新外部用户（13800000099，0 样本、未填组别）首页与历史编辑记录**不报错**
 *   ② 第 7 条：「内部管理」板块外部不渲染 / 内部渲染（四入口）
 *   ③ 第 7 条：身份缺失（identity=null）与契约外身份串（"INTERNAL"）时首页与「我的」什么都不渲染
 * 前置：后端 8081 + reseed + miniapp H5 dev 9200（VITE_MOCK_LOGIN=1）。
 * 跑法：node doc/waves/regression/D2/L3r3-mp-shallow.mjs
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
const SHOTS = path.join(HERE, 'shots/L3r3-mp')
mkdirSync(SHOTS, { recursive: true })

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail !== undefined ? ` — ${detail}` : ''}`)
}

const ERR_RE = /没能加载|加载失败|重试|系统异常|网络异常|请求失败/

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await ctx.newPage()
const pageErrors = []
const consoleErrs = []
page.on('pageerror', e => pageErrors.push(String(e.message)))
page.on('console', m => { if (m.type() === 'error') consoleErrs.push(m.text()) })

const shot = n => page.screenshot({ path: path.join(SHOTS, `${n}.png`), fullPage: false }).catch(() => {})
const bodyText = () => page.evaluate(() => document.body.innerText)
const tiles = () => page.$$eval('.lqg-tile', els => els.map(e => e.innerText.replace(/\s+/g, ' ').trim())).catch(() => [])

async function resetToLogin() {
  await page.goto(`${MP}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
  await page.reload({ waitUntil: 'domcontentloaded' })   // 硬 reload 复位协议勾选
  await page.waitForSelector('.login__agree', { timeout: 30000 })
  await page.waitForTimeout(400)
}

async function mockLogin(label) {
  const on = await page.$eval('.login__box', el => el.className.includes('login__box--on')).catch(() => false)
  if (!on) await page.click('.login__box')               // 先勾协议，再点调试登录
  await page.click(`.login__mock-btn:has-text("${label}")`)
  await page.waitForSelector('.lqg-tile, .home__empty', { timeout: 30000 })
  await page.waitForTimeout(900)
}

async function goHash(hash, waitSel) {
  await page.evaluate(h => { location.hash = h }, hash)
  await page.waitForTimeout(250)
  await page.reload({ waitUntil: 'domcontentloaded' })
  if (waitSel) await page.waitForSelector(waitSel, { timeout: 30000 }).catch(() => {})
  await page.waitForTimeout(900)
}

// ───────────── ① 新外部用户：首页 3 格、不报错；历史编辑记录走空态 ─────────────
await resetToLogin()
await mockLogin('外部人员 · 新号（未绑定）')
{
  const t = await bodyText()
  const tl = await tiles()
  check('① 新号首页宫格 = 3（外部三张表）', tl.length === 3, `tiles=${JSON.stringify(tl)}`)
  check('① 新号首页无错误/重试文案', !ERR_RE.test(t), JSON.stringify(t.slice(0, 140)))
  check('① 新号首页无 pageerror', pageErrors.length === 0, pageErrors.join(' | ').slice(0, 160))
  await shot('r3-a1-newbie-home')

  pageErrors.length = 0
  await goHash('#/pages/history/index', '.his__switch')
  const h = await bodyText()
  check('① 历史编辑记录不报错（不是 ErrorState）', !ERR_RE.test(h), JSON.stringify(h.slice(0, 220)))
  check('① 历史编辑记录有页签（数据源可用）', await page.$$eval('.his__switch', e => e.length).catch(() => 0) > 0)
  check('① 历史编辑记录无 pageerror', pageErrors.length === 0, pageErrors.join(' | ').slice(0, 160))
  await shot('r3-a2-newbie-history')
}

// ───────────── ② 「内部管理」板块：外部不渲染 / 内部渲染四入口 ─────────────
pageErrors.length = 0
await resetToLogin()
await mockLogin('外部人员 · 王医生（已核验）')
{
  check('② 外部（extA）首页宫格 = 3', (await tiles()).length === 3)
  await goHash('#/pages/me/index', '.me')
  const t = await bodyText()
  check('② 外部「我的」不含「内部管理」', !t.includes('内部管理'), JSON.stringify(t.slice(0, 200)))
  check('② 外部「我的」含「历史编辑记录」+「单位与组别」', t.includes('历史编辑记录') && t.includes('单位与组别'))
  await shot('r3-b1-ext-me')
}

pageErrors.length = 0
await resetToLogin()
await mockLogin('内部人员 · 李工')
{
  check('③ 内部（staff）首页宫格 = 4', (await tiles()).length === 4, `tiles=${JSON.stringify(await tiles())}`)
  await goHash('#/pages/me/index', '.me')
  const t = await bodyText()
  check('② 内部「我的」含「内部管理」', t.includes('内部管理'), JSON.stringify(t.slice(0, 260)))
  const four = ['样本记录信息表', '类器官收样', '石蜡包埋', '冻存']
  const missing = four.filter(k => !t.includes(k))
  check('② 内部管理四入口齐全', missing.length === 0, `miss=${JSON.stringify(missing)}`)
  check('② 内部「我的」不含「单位与组别」', !t.includes('单位与组别'))
  await shot('r3-b2-staff-me')
}

// ───────────── ③ 身份缺失 / 契约外身份串 ─────────────
await resetToLogin()
await mockLogin('内部人员 · 李工')
const stub = (identity) => page.route('**/mp/me*', r => r.fulfill({
  status: 200, contentType: 'application/json',
  body: JSON.stringify({ code: 200, msg: '操作成功', data: { userId: '1', name: 'x', phoneMasked: '', identity, ext: null } }),
}))

await stub(null)
pageErrors.length = 0
await goHash('#/pages/index/index', '.home')
{
  const tl = await tiles()
  const t = await bodyText()
  check('③ identity=null：首页 0 格', tl.length === 0, `tiles=${tl.length}`)
  check('③ identity=null：首页出「没能确认你的身份」兜底、不默认内部', t.includes('没能确认你的身份'), JSON.stringify(t.slice(0, 160)))
  check('③ identity=null：首页无 pageerror', pageErrors.length === 0, pageErrors.join(' | ').slice(0, 160))
  await shot('r3-c1-ident-null-home')
  await goHash('#/pages/me/index', '.me')
  const t2 = await bodyText()
  check('③ identity=null：「我的」不渲染 历史编辑记录/内部管理/单位与组别',
    !t2.includes('历史编辑记录') && !t2.includes('内部管理') && !t2.includes('单位与组别'), JSON.stringify(t2.slice(0, 200)))
  await shot('r3-c2-ident-null-me')
}
await page.unroute('**/mp/me*')

await stub('INTERNAL')   // 契约外大小写
await goHash('#/pages/index/index', '.home')
{
  const tl = await tiles()
  check('③ identity="INTERNAL"（契约外）：首页 0 格（绝不默认内部）', tl.length === 0, `tiles=${tl.length}`)
}
await page.unroute('**/mp/me*')

console.log(`\n== pageerror ${pageErrors.length} / console.error ${consoleErrs.length} ==`)
consoleErrs.slice(0, 5).forEach(e => console.log('  console.error:', e.slice(0, 160)))
const pass = results.filter(r => r.ok).length
console.log(`\n== L3r3 浅 H5：${pass}/${results.length} ==`)
await browser.close()
process.exit(results.every(r => r.ok) ? 0 : 1)
