/**
 * SYS-HOME-001 的**运行时探针**（工作台首页五张待办卡片 + 侧边菜单角标）。
 *
 * 它同时承担三件本票最容易被做反的事的机器证据：
 *   ① **dev 模式下点菜单不白屏**（D1 的 S1 就出在这里 —— 本票改了 views/index.vue）
 *      → 每一跳都断 `.app-main innerHTML` 长度 > 1000；
 *   ② **卡片与角标同源**：整轮登录只允许出现 **1 次** `GET /lqg/home/todo`
 *      （侧边栏若自己再请求一次就会是 2 次 —— 正是 accept 2 最后一段防的形态）；
 *   ③ **为 0 不隐藏**：数字为 0 的那张卡片仍在 DOM 里、带 `is-zero`、显示 0；
 *      角标为 0 时**不显示**（两条别写反）。
 *
 * 另外断「卡片点进去带筛选条件」与「角标数字 = 接口数字」。
 *
 * 前置：后端 8094 + plus-ui **dev server** 8093（dev 是必须的：生产构建丢模板注释，
 *       白屏那条路径只在 dev 出现）。
 * 跑法：
 *   node doc/waves/reports/SYS-HOME-001/probes/probe-home.mjs
 *   EXPECT_SAMPLES=3 node …      # 外部新交一条类器官收样之后，卡片应变成 3
 *   HEADLESS=0 CHANNEL=chrome node …   # 有头对照（真人浏览器）
 */
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const BASE = process.env.LQG_WEB_BASE || 'http://127.0.0.1:8093'
const HEADLESS = process.env.HEADLESS !== '0'
const CHANNEL = process.env.CHANNEL || ''
const SHOTS = process.env.SHOTS || path.join(HERE, '..', 'shots')
const EXPECT_SAMPLES = Number(process.env.EXPECT_SAMPLES || 2)
mkdirSync(SHOTS, { recursive: true })

const results = []
const check = (name, ok, detail) => {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

const browser = await chromium.launch({ headless: HEADLESS, ...(CHANNEL ? { channel: CHANNEL } : {}) })
const ctx = await browser.newContext({ viewport: { width: 1560, height: 950 }, deviceScaleFactor: 1 })
const page = await ctx.newPage()
const consoleErrors = []
page.on('pageerror', (e) => consoleErrors.push('[pageerror] ' + e.message))
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push('[console.error] ' + m.text())
})

let todoRequests = 0
let recentRequests = 0
const countRequests = (r) => {
  const u = r.url()
  if (u.includes('/lqg/home/todo')) todoRequests++
  if (u.includes('/lqg/home/recent')) recentRequests++
}

const appMainLen = () => page.evaluate(() => document.querySelector('.app-main')?.innerHTML.length ?? -1)
const routeOf = () => page.evaluate(() => location.pathname + location.search)
const bodyText = () => page.evaluate(() => document.body.innerText)

/** 五张卡片：标题 / 数字 / 是否灰（is-zero） */
const cards = () =>
  page.evaluate(() =>
    [...document.querySelectorAll('.lqg-todo-card')].map((el) => ({
      title: el.querySelector('.lqg-todo-card__label')?.textContent?.trim() || '',
      num: el.querySelector('.lqg-todo-card__num')?.textContent?.trim() || '',
      zero: el.classList.contains('is-zero'),
      visible: !!(el.offsetWidth || el.offsetHeight)
    }))
  )

/** 侧边菜单角标：菜单名 / 数字 / 是否真的显示 */
const badges = () =>
  page.evaluate(() =>
    [...document.querySelectorAll('.el-menu .el-badge')].map((b) => {
      const sup = b.querySelector('.el-badge__content')
      const host = b.closest('.el-menu-item, .el-sub-menu__title')
      return {
        menu: b.querySelector('.menu-title')?.textContent?.trim() || (host?.innerText || '').trim(),
        value: sup?.textContent?.trim() || '',
        visible: !!sup && getComputedStyle(sup).display !== 'none'
      }
    })
  )

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
  await page.waitForTimeout(2000)
}

try {
  // ── 登录（落在 /index）────────────────────────────────────────────────
  await page.goto(`${BASE}/`)
  await page.evaluate(() => localStorage.clear())
  await page.goto(`${BASE}/`)
  page.on('request', countRequests)
  await page.waitForSelector('.login-form input', { timeout: 30000 })
  const inputs = page.locator('.login-form input')
  await inputs.nth(0).fill(process.env.LQG_ADMIN_USER || 'lqgadmin')
  await inputs.nth(1).fill(process.env.LQG_ADMIN_PASSWORD || 'admin123')
  await page.click('.login-form button')
  await page.waitForSelector('.lqg-todo-card', { timeout: 30000 })
  await page.waitForTimeout(1800)

  const homeLen = await appMainLen()
  const route1 = await routeOf()
  await page.screenshot({ path: path.join(SHOTS, `home-${HEADLESS ? 'headless' : 'headed'}.png`), fullPage: true })
  check('① 登录落在 /index 且首页渲染出内容（不是白屏）', homeLen > 1000 && route1 === '/index', `len=${homeLen} url=${route1}`)

  // ── 五张卡片：数字 / 为 0 不隐藏 ───────────────────────────────────────
  const cs = await cards()
  check('② 恰好五张待办卡片', cs.length === 5, JSON.stringify(cs.map((c) => c.title)))
  const nums = cs.map((c) => Number(c.num))
  const expected = [EXPECT_SAMPLES, 1, 2, 2, 0]
  check('③ 五个数字与接口/seed 一致（卡片接的是接口，不是写死的数）',
    JSON.stringify(nums) === JSON.stringify(expected),
    `渲染=${JSON.stringify(nums)} 期望=${JSON.stringify(expected)}`)
  check('④ 为 0 的那张卡片**仍在**（不隐藏）且带 is-zero（变灰）',
    cs.length === 5 && cs[4].visible && cs[4].zero && cs[4].num === '0',
    JSON.stringify(cs[4]))

  // ── 卡片与角标：同一次请求 ────────────────────────────────────────────
  check('⑤ 登录到首页整轮只请求了 1 次待办接口（卡片与角标同源）', todoRequests === 1,
    `/lqg/home/todo 请求数=${todoRequests}；/lqg/home/recent 请求数=${recentRequests}`)

  const bs = await badges()
  const pick = (menu) => bs.find((b) => (b.menu || '').startsWith(menu))
  check('⑥ 四个菜单角标与卡片同数（样本总表/石蜡包埋/冻存管理/人员与单位）',
    pick('样本总表')?.value === String(EXPECT_SAMPLES) && pick('石蜡包埋')?.value === '1' &&
      pick('冻存管理')?.value === '2' && pick('人员与单位')?.value === '2',
    JSON.stringify(bs.map((b) => `${b.menu}=${b.visible ? b.value : '(隐藏)'}`)))

  const allBadges = bs.filter((b) => b.visible).map((b) => b.value)
  check('⑦ 角标里没有「0」（为 0 不显示角标）', !allBadges.includes('0'), `可见角标=${JSON.stringify(allBadges)}`)

  // ── 最近提交 ─────────────────────────────────────────────────────────
  const txt = await bodyText()
  const recentRows = await page.evaluate(
    () => document.querySelectorAll('.lqg-home .el-table__body-wrapper tbody tr').length
  )
  check('⑧ 最近提交表有内容、且不含软删的 SJ90000010', recentRows > 0 && !txt.includes('SJ90000010'),
    `行数=${recentRows} 含 SJ90000010=${txt.includes('SJ90000010')}`)

  // ── 卡片点击带筛选条件 ────────────────────────────────────────────────
  await page.locator('.lqg-todo-card').first().click()
  await page.waitForTimeout(2200)
  const route2 = await routeOf()
  const txt2 = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, `card-click-sample-${HEADLESS ? 'headless' : 'headed'}.png`), fullPage: true })
  check('⑨ 点「待核验样本」卡片 → 样本总表且带上 verifyStatus=pending（页面真的套上了筛选）',
    /\/sample/.test(route2) && route2.includes('verifyStatus=pending') && txt2.includes('待核验') && (await appMainLen()) > 1000,
    `url=${route2} .app-main len=${await appMainLen()}`)

  // ── dev 模式点菜单不白屏（D1 的 S1 回归点）────────────────────────────
  const jumps = [
    ['石蜡包埋', null, /\/embed/],
    ['冻存管理', null, /\/cryo/],
    ['外部用户', '人员与单位', /auth\/extuser/],
    ['首页', null, /\/index/]
  ]
  let navOk = true
  const navDetail = []
  for (const [label, parent, re] of jumps) {
    await clickMenu(label, parent)
    const len = await appMainLen()
    const url = await routeOf()
    const ok = len > 1000 && re.test(url)
    navOk = navOk && ok
    navDetail.push(`${label}:${ok ? 'ok' : 'BLANK'}(${len})`)
    await page.screenshot({ path: path.join(SHOTS, `nav-${label}-${HEADLESS ? 'headless' : 'headed'}.png`), fullPage: true })
  }
  check('⑩ dev 模式下从首页离开、连点四个菜单都不白屏（S1 不复发）', navOk, navDetail.join(' '))

  // 回到首页再看一眼：卡片数字与（同一份 store 的）角标仍然一致
  // ★ 注意：回首页会**重新算一次**（这是有意的：数字必须跟着真实变化走），
  //   所以这里断的不是「整轮只有一次请求」（那条由 ⑤ 断初次加载），
  //   而是「刷新之后卡片与角标仍然是同一份数字」。
  await clickMenu('首页')
  const cs2 = await cards()
  const bs2 = await badges()
  const pick2 = (menu) => bs2.find((x) => (x.menu || '').startsWith(menu))
  check('⑪ 回到首页卡片仍是五个、数字不变，且角标与卡片仍是同一份（同源未分叉）',
    cs2.length === 5 && JSON.stringify(cs2.map((c) => Number(c.num))) === JSON.stringify(expected) &&
      pick2('样本总表')?.value === String(Number(cs2[0].num)) && pick2('石蜡包埋')?.value === cs2[1].num &&
      pick2('冻存管理')?.value === cs2[2].num && pick2('人员与单位')?.value === cs2[3].num,
    `cards=${JSON.stringify(cs2.map((c) => Number(c.num)))} badges=[${pick2('样本总表')?.value},${pick2('石蜡包埋')?.value},${pick2('冻存管理')?.value},${pick2('人员与单位')?.value}] todoRequests=${todoRequests}`)

  check('⑫ 全程无 console.error / pageerror', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '))

  // ── 另外四张卡片的落点与筛选（本票同时给那几个页面加了 route.query 认领）────
  const cardTargets = [
    [1, /\/embed/, 'verifyStatus=pending', '待核验'],
    [2, /\/cryo/, 'overdueOnly=true', '超期'],
    [3, /auth\/extuser/, 'bindStatus=pending', '待核验'],
    [4, /qc-console\/doc-console/, '', '文档渲染状态']
  ]
  const cardDetail = []
  let cardOk = true
  for (const [idx, re, query, label] of cardTargets) {
    await clickMenu('首页')
    await page.locator('.lqg-todo-card').nth(idx).click()
    await page.waitForTimeout(2200)
    const url = await routeOf()
    const txt3 = await bodyText()
    const len = await appMainLen()
    const ok = re.test(url) && url.includes(query) && len > 1000 && txt3.includes(label)
    cardOk = cardOk && ok
    cardDetail.push(`card${idx}(url=${url} len=${len} 含「${label}」=${txt3.includes(label)})`)
    await page.screenshot({ path: path.join(SHOTS, `card${idx}-${HEADLESS ? 'headless' : 'headed'}.png`), fullPage: true })
  }
  check('⑬ 另外四张卡片也都直达正确页面、带上筛选条件、页面渲染正常', cardOk, cardDetail.join(' '))
} catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 3).join(' | ') : String(e))
} finally {
  const failed = results.filter((r) => !r.ok)
  console.log(`\n== SYS-HOME-001 首页探针 ${results.length - failed.length}/${results.length} 通过（headless=${HEADLESS}，EXPECT_SAMPLES=${EXPECT_SAMPLES}）==`)
  console.log(`SHOTS=${SHOTS}`)
  await browser.close()
  process.exit(failed.length === 0 ? 0 : 1)
}
