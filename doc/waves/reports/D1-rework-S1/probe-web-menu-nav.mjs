/**
 * D1 QA 门第 1 轮 S1 返工项（SYS-WEB-001）的**回归探针**。
 *
 * 被测缺陷：plus-ui 在 `pnpm dev`（vite dev server）下，从「首页」(/index) 点侧边栏任何菜单
 * 都会让 `.app-main` 渲染成 `<!---->`（innerHTML 长度 = 7）→ 整片空白页，控制台无报错；
 * 硬刷新同一个 URL 又正常。
 *
 * 根因（见 doc/waves/reports/D1-rework-S1.md）：
 *   src/views/index.vue 的模板顶层有一条 HTML 注释 → vite dev 保留注释（生产构建丢弃），
 *   于是该页面的根 vnode 是 Fragment（patchFlag = STABLE_FRAGMENT|DEV_ROOT_FRAGMENT）
 *   → AppMain.vue 的 <transition mode="out-in"> 把过渡钩子挂在 Fragment 上，
 *   而渲染器卸载 Fragment 时只逐个 remove 子节点、不回调 Fragment 上的 afterLeave
 *   → BaseTransition 的 state.isLeaving 永远为 true → 之后每次渲染只输出 `<!---->`。
 *
 * 本探针**故意从 /index 出发**（即从那个 Fragment 根页面离开），这样：
 *   - 修复前：第 1 跳就红（.app-main = 7）；
 *   - 修复后：四页全绿，且 enter 动画（animate__fadeIn）仍在。
 * 之所以能红，是它真的在测这条离开 Fragment 根页面的路径 —— 负对照实测见报告 §4。
 *
 * 前置：后端 8081、plus-ui dev server 8082（或 HEADLESS=0 有头跑，结论一致）。
 * 跑法：node doc/waves/reports/D1-rework-S1/probe-web-menu-nav.mjs
 *      HEADLESS=0 node doc/waves/reports/D1-rework-S1/probe-web-menu-nav.mjs    # 有头对照
 *      SHOTS=<dir> node ...                                                     # 指定截图目录
 */
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const BASE = process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082'
const HEADLESS = process.env.HEADLESS !== '0'
const SHOTS = process.env.SHOTS || path.join(HERE, 'shots')
mkdirSync(SHOTS, { recursive: true })

const results = []
const check = (name, ok, detail) => {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

// CHANNEL=chrome 用本机 Google Chrome（有头跑必须用它：本机只装了 playwright 的
// chromium-headless-shell，没有配得上的有头 chromium 构建）。
const CHANNEL = process.env.CHANNEL || ''
const browser = await chromium.launch({ headless: HEADLESS, ...(CHANNEL ? { channel: CHANNEL } : {}) })
const ctx = await browser.newContext({ viewport: { width: 1560, height: 950 }, deviceScaleFactor: 1 })
const page = await ctx.newPage()
const consoleErrors = []
page.on('pageerror', e => consoleErrors.push('[pageerror] ' + e.message))
page.on('console', m => { if (m.type() === 'error') consoleErrors.push('[console.error] ' + m.text()) })

const appMainLen = () => page.evaluate(() => document.querySelector('.app-main')?.innerHTML.length ?? -1)
const bodyText = () => page.evaluate(() => document.body.innerText)
const routeOf = () => page.evaluate(() => location.pathname)

/** 点侧边栏菜单：若子项不可见（父目录折叠），先展开它的父目录 */
async function clickMenu(label, parentDir) {
  const item = page.locator(`.el-menu-item:has-text("${label}")`).first()
  let box = await item.boundingBox().catch(() => null)
  if (!(box && box.width > 0) && parentDir) {
    await page.click(`.el-sub-menu__title:has-text("${parentDir}")`)
    await page.waitForTimeout(900)
    box = await item.boundingBox().catch(() => null)
  }
  if (!(box && box.width > 0)) throw new Error(`菜单项「${label}」不可见（父目录 ${parentDir} 展开失败？）`)
  await item.click()
  await page.waitForTimeout(2200)
}

/** 记录一次导航过程中 .app-main 子树里出现过的 class，用于断言 enter 动画类仍在 */
async function armClassRecorder() {
  await page.evaluate(() => {
    const main = document.querySelector('.app-main')
    window.__cls = new Set()
    const grab = () => main.querySelectorAll('*').forEach(el => { if (el.className) String(el.className).split(/\s+/).forEach(c => c && window.__cls.add(c)) })
    grab()
    window.__mo = new MutationObserver(grab)
    window.__mo.observe(main, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] })
  })
}
const recordedClasses = () => page.evaluate(() => { window.__mo && window.__mo.disconnect(); return [...window.__cls] })

try {
  // ── 登录（落在 /index，也就是那个 Fragment 根页面）─────────────────────
  await page.goto(`${BASE}/`)
  await page.evaluate(() => localStorage.clear())
  await page.goto(`${BASE}/`)
  await page.waitForSelector('.login-form input', { timeout: 30000 })
  const inputs = page.locator('.login-form input')
  await inputs.nth(0).fill(process.env.LQG_ADMIN_USER || 'lqgadmin')
  await inputs.nth(1).fill(process.env.LQG_ADMIN_PASSWORD || 'admin123')
  await page.click('.login-form button')
  await page.waitForSelector('.el-menu', { timeout: 30000 })
  await page.waitForTimeout(2200)
  check('登录后落在首页 /index 且首页有内容', (await routeOf()) === '/index' && (await appMainLen()) > 100,
    `url=${await routeOf()} .app-main innerHTML length=${await appMainLen()}`)

  // ── 第 1 跳：离开首页（Fragment 根页面）→ 内部人员授权。修复前这里就空白 ──
  await armClassRecorder()
  await clickMenu('内部人员授权', '人员与单位')
  const len1 = await appMainLen()
  const txt1 = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, `probe-01-staff-${HEADLESS ? 'headless' : 'headed'}.png`), fullPage: true })
  check('① 离开首页 → 内部人员授权「人员与单位」页正常渲染（回归点）',
    len1 > 1000 && /auth\/staff/.test(await routeOf()) && txt1.includes('按手机号授权'),
    `.app-main innerHTML length=${len1} url=${await routeOf()}`)
  const cls1 = await recordedClasses()
  check('① enter 过渡动画类仍在（animate.css fadeIn 挂上了新页根元素）',
    cls1.includes('animate__fadeIn') && cls1.includes('animate__animated'),
    JSON.stringify(cls1.filter(c => c.startsWith('animate__')).slice(0, 6)))

  // ── 第 2 跳：外部用户 5130 ────────────────────────────────────────────
  await clickMenu('外部用户', '人员与单位')
  const len2 = await appMainLen()
  const txt2 = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, `probe-02-extuser-${HEADLESS ? 'headless' : 'headed'}.png`), fullPage: true })
  check('② 侧边栏 → 外部用户（5130）正常渲染',
    len2 > 1000 && /auth\/extuser/.test(await routeOf()) && txt2.includes('外部用户'),
    `.app-main innerHTML length=${len2} url=${await routeOf()}`)

  // ── 第 3 跳：字典管理 ─────────────────────────────────────────────────
  await clickMenu('字典管理', '系统管理')
  const len3 = await appMainLen()
  const txt3 = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, `probe-03-dict-${HEADLESS ? 'headless' : 'headed'}.png`), fullPage: true })
  check('③ 侧边栏 → 字典管理正常渲染',
    len3 > 1000 && /system\/dict/.test(await routeOf()) && txt3.includes('字典'),
    `.app-main innerHTML length=${len3} url=${await routeOf()}`)

  // ── 第 4 跳：参数设置 ─────────────────────────────────────────────────
  await clickMenu('参数设置', '系统管理')
  const len4 = await appMainLen()
  const txt4 = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, `probe-04-config-${HEADLESS ? 'headless' : 'headed'}.png`), fullPage: true })
  check('④ 侧边栏 → 参数设置正常渲染',
    len4 > 1000 && /system\/config/.test(await routeOf()) && txt4.includes('参数'),
    `.app-main innerHTML length=${len4} url=${await routeOf()}`)

  // ── 第 5 跳：回到首页，再从首页出发一次（确保不是「第一次之后就好了」）──
  await clickMenu('首页')
  const len5 = await appMainLen()
  check('⑤ 侧边栏 → 首页正常渲染', len5 > 100 && (await routeOf()) === '/index',
    `.app-main innerHTML length=${len5} url=${await routeOf()}`)
  await clickMenu('来源单位与组别', '人员与单位')
  const len6 = await appMainLen()
  await page.screenshot({ path: path.join(SHOTS, `probe-05-unit-${HEADLESS ? 'headless' : 'headed'}.png`), fullPage: true })
  check('⑥ 再离开一次首页 → 来源单位与组别仍正常（不是只好了第一跳）',
    len6 > 1000 && /auth\/unit/.test(await routeOf()),
    `.app-main innerHTML length=${len6} url=${await routeOf()}`)

  check('全程无 console.error / pageerror', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '))
} catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n')[0] : String(e))
} finally {
  const failed = results.filter(r => !r.ok)
  console.log(`\n== SYS-WEB-001 菜单导航回归探针 ${results.length - failed.length}/${results.length} 通过（headless=${HEADLESS}）==`)
  console.log(`SHOTS=${SHOTS}`)
  await browser.close()
  process.exit(failed.length === 0 ? 0 : 1)
}
