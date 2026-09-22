/**
 * D2 r4 · L3 —— 浅 H5 DOM 检查（**本片自写脚本**，不复跑 L2 的深度 UI 走查）。
 * 覆盖 qa_scope L3 第 6、7 条的端侧渲染段：
 *   第 6 条：新外部用户（13800000099，0 样本、未填组别）首页 + 历史编辑记录**不报错**
 *   第 7 条：「内部管理」板块外部不渲染 / 内部渲染；身份缺失时首页与「我的」什么都不渲染
 * 第 7 条的「外部类器官入参夹带内部字段不生效」是接口段，在 L3r4-api.sh 里断。
 *
 * 前置：后端 8081（本片自起）+ reseed + miniapp H5 dev 9200（VITE_MOCK_LOGIN=1）。
 * 跑法：node doc/waves/regression/D2/L3r4-mp-shallow.mjs
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
const SHOTS = path.join(HERE, 'shots/L3r4-mp')
mkdirSync(SHOTS, { recursive: true })

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail !== undefined ? ` — ${detail}` : ''}`)
}

// 「页面没报错」的判据：不出现任何一种加载/系统错误文案
const ERR_RE = /没能加载|加载失败|系统异常|网络异常|请求失败|无法连接|Internal Server Error/
// 三个只按身份出现的板块名（身份缺失时一个都不该出现）
const SECTION_TITLES = ['历史编辑记录', '单位与组别', '内部管理']

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await ctx.newPage()
let pageErrors = []
let consoleErrs = []
page.on('pageerror', e => pageErrors.push(String(e.message)))
page.on('console', (m) => { if (m.type() === 'error') consoleErrs.push(m.text()) })

const shot = n => page.screenshot({ path: path.join(SHOTS, `${n}.png`), fullPage: false }).catch(() => {})
const bodyText = () => page.evaluate(() => document.body.innerText)
const tileTitles = () => page.$$eval('.lqg-tile__t', els => els.map(e => e.innerText.trim())).catch(() => [])
const tileCount = () => page.$$eval('.lqg-tile', els => els.length).catch(() => 0)
const meRowTitles = () => page.$$eval('.merow__t', els => els.map(e => e.innerText.trim())).catch(() => [])
const fresh = () => { pageErrors = []; consoleErrs = [] }

async function resetToLogin() {
  await page.goto(`${MP}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
  await page.reload({ waitUntil: 'domcontentloaded' })   // 硬 reload 复位协议勾选（hash 路由下 goto 不重载）
  await page.waitForSelector('.login__agree', { timeout: 30000 })
  await page.waitForTimeout(400)
}

async function mockLogin(label) {
  const on = await page.$eval('.login__box', el => el.className.includes('login__box--on')).catch(() => false)
  if (!on) await page.click('.login__box')               // ★ 先勾协议，再点调试登录
  await page.click(`.login__mock-btn:has-text("${label}")`)
  await page.waitForSelector('.lqg-tile, .lqg-state', { timeout: 30000 })
  await page.waitForTimeout(900)
}

async function gotoRoute(hash) {
  await page.goto(`${MP}/#${hash}`, { waitUntil: 'domcontentloaded' })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
}

console.log('===== C6 空数据：新外部用户（newbie1 / 13800000099，0 样本、未填组别）=====')
await resetToLogin()
fresh()
await mockLogin('外部人员 · 新号（未绑定）')
await shot('C6-newbie-home')
let t = await tileTitles()
let bt = await bodyText()
check('C6-1 新外部用户首页不报错', !ERR_RE.test(bt), bt.slice(0, 120).replace(/\n/g, ' | '))
check('C6-1 首页 3 格且无「-80 冻存」', t.length === 3 && !t.some(x => x.includes('冻存')), JSON.stringify(t))
check('C6-1 无 pageerror/console error', pageErrors.length === 0 && consoleErrs.length === 0,
  JSON.stringify([...pageErrors, ...consoleErrs].slice(0, 3)))
// 我的 → 历史编辑记录
await gotoRoute('/pages/me/index')
await shot('C6-newbie-me')
const meT = await meRowTitles()
check('C6-2 新外部用户「我的」不报错', !ERR_RE.test(await bodyText()), JSON.stringify(meT))
await page.click('.merow:has-text("历史编辑记录")')
await page.waitForSelector('.his', { timeout: 30000 })
await page.waitForTimeout(1200)
await shot('C6-newbie-history')
bt = await bodyText()
check('C6-2 历史编辑记录页不报错（0 条走空状态）', !ERR_RE.test(bt), bt.slice(0, 160).replace(/\n/g, ' | '))
check('C6-2 历史编辑记录页无 pageerror', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
// 「只看我提交的」开关在空数据上也不炸
const sw = await page.$('.his__switch .wd-switch')
if (sw) { await sw.click(); await page.waitForTimeout(1000) }
check('C6-2 空数据下切「只看我提交的」仍不报错', !ERR_RE.test(await bodyText()), '')

console.log('\n===== C7-渲染 「内部管理」板块按身份出现 =====')
await resetToLogin()
fresh()
await mockLogin('外部人员 · 王医生（已核验）')
await gotoRoute('/pages/me/index')
await shot('C7-extA-me')
let extMe = await meRowTitles()
let extBody = await bodyText()
check('C7-1 外部 extA「我的」有「历史编辑记录」+「单位与组别」区块',
  extMe.includes('历史编辑记录') && /单位与组别/.test(extBody), JSON.stringify(extMe))
check('C7-1 外部不含「内部管理」板块（不渲染，不是置灰）',
  !extBody.includes('内部管理') && !extBody.includes('核验、冻存取用请到网页工作台'), JSON.stringify(extMe))
check('C7-1 外部页无 .adm 板块节点', (await page.$$('.adm')).length === 0, '')
check('C7-1 外部页无 pageerror', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))

await resetToLogin()
fresh()
await mockLogin('内部人员 · 李工')
await gotoRoute('/pages/me/index')
await shot('C7-staff-me')
let stMe = await meRowTitles()
let stBody = await bodyText()
check('C7-1b 内部 staff「我的」有「内部管理」板块标题', stMe.includes('历史编辑记录') && /内部管理/.test(stBody), JSON.stringify(stMe))
const admRows = await page.$$eval('.adm .merow__t', els => els.map(e => e.innerText.trim()))
check('C7-1b 内部管理板块 4 个入口（样本/类器官/石蜡/冻存）', admRows.length === 4, JSON.stringify(admRows))
check('C7-1b 内部页无 pageerror', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))

console.log('\n===== C7-渲染 身份缺失时首页与「我的」什么都不渲染 =====')
// 用 route stub 把 /mp/me 的 identity 置空 / 置成不认识的串 —— 直接打渲染分支（token 仍在，不会被 401 弹走）
async function withIdentityStub(value, fn) {
  await page.route('**/lqg-api/mp/me*', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, msg: '操作成功', data: { userId: '1', name: 'x', phoneMasked: '138****0000', identity: value, ext: null } }),
  }))
  try { await fn() } finally { await page.unroute('**/lqg-api/mp/me*') }
}

// 先真登录拿 token，再 stub
await resetToLogin()
await mockLogin('外部人员 · 王医生（已核验）')

await withIdentityStub(null, async () => {
  fresh()
  await gotoRoute('/pages/index/index')
  await shot('C7-identity-null-home')
  const tc = await tileCount()
  const txt = await bodyText()
  check('C7-2 identity=null 首页 0 个入口格', tc === 0, `tiles=${tc}`)
  check('C7-2 identity=null 首页显示「没能确认你的身份」且不报错', /没能确认你的身份/.test(txt) && !ERR_RE.test(txt), txt.slice(0, 120).replace(/\n/g, ' | '))
  await gotoRoute('/pages/me/index')
  await shot('C7-identity-null-me')
  const rows = await meRowTitles()
  const mtxt = await bodyText()
  check('C7-2 identity=null「我的」0 个板块', rows.length === 0 && !SECTION_TITLES.some(s => mtxt.includes(s)),
    JSON.stringify(rows) + ' | ' + mtxt.slice(0, 100).replace(/\n/g, ' '))
})

await withIdentityStub('INTERNAL', async () => {
  fresh()
  await gotoRoute('/pages/index/index')
  await shot('C7-identity-unknown-home')
  const tc = await tileCount()
  check('C7-2b identity=未知串("INTERNAL") 首页 0 个入口格（不默认当内部）', tc === 0, `tiles=${tc}`)
  await gotoRoute('/pages/me/index')
  const rows = await meRowTitles()
  check('C7-2b identity=未知串「我的」0 个板块', rows.length === 0, JSON.stringify(rows))
})

// 真·无 token：清掉本地 token 后直接进首页
await resetToLogin()
fresh()
await gotoRoute('/pages/index/index')
await shot('C7-no-token-home')
const noTokTiles = await tileCount()
const noTokTxt = await bodyText()
check('C7-3 无 token（身份缺失）首页 0 个入口格', noTokTiles === 0, `tiles=${noTokTiles}`)
check('C7-3 无 token 时不出现任何错误文案', !ERR_RE.test(noTokTxt) || /没能确认你的身份/.test(noTokTxt), noTokTxt.slice(0, 120).replace(/\n/g, ' | '))
await gotoRoute('/pages/me/index')
await shot('C7-no-token-me')
const noTokRows = await meRowTitles()
check('C7-3 无 token「我的」0 个板块', noTokRows.length === 0, JSON.stringify(noTokRows))

const pass = results.filter(r => r.ok).length
console.log(`\n== D2-r4-L3-mp-shallow: PASS=${pass} FAIL=${results.length - pass} ==`)
await browser.close()
process.exit(results.length - pass === 0 ? 0 : 1)
