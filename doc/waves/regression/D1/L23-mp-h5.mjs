/**
 * D1 / L2 端侧断言（QA 分片 L23，独立重跑）——小程序 H5 等价路径。
 *
 * 背景（机器事实，不是实现说明）：微信开发者工具在本沙箱跑不通（CLI 要写
 * ~/Library/Application Support/微信开发者工具/** EPERM 且需人工扫码）。按项目规矩用
 * **H5 构建 + Playwright** 覆盖能覆盖的项，开发者工具 / 真机项如实记为未覆盖。
 *
 * 覆盖（qa_scope L2 第 1 条）：
 *   登录页 → 内部首页四个入口 / 外部首页三个入口（没有 -80 冻存记录）、两种首页都没有数字
 *   → 我的（内部有「内部管理」板块、外部有「单位与组别」，都有「历史编辑记录」）
 *   → 单位与组别页（截图）→ 拒绝授权手机号停在登录页。
 *
 * 前置：后端 8081（.tmp/run-backend.sh）、miniapp H5 dev server 9200。
 * 跑法：node doc/waves/regression/D1/L23-mp-h5.mjs
 */
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const BASE = process.env.LQG_H5_BASE || 'http://127.0.0.1:9200'
const SHOTS = path.join(HERE, 'shots-L23')
mkdirSync(SHOTS, { recursive: true })

const INTERNAL_ENTRIES = ['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录', '-80 冻存记录']
const EXTERNAL_ENTRIES = ['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录']

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

function digitsOutside80(text) {
  return text.replace(/-80/g, '').match(/\d/g) || []
}

async function resetToLogin(page) {
  await page.goto(`${BASE}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => {
    window.localStorage.clear()
    window.sessionStorage.clear()
  })
  await page.goto(`${BASE}/#/pages/login/index`)
  await page.waitForSelector('.login__agree', { timeout: 20000 })
  await page.waitForTimeout(300)
}

async function mockLogin(page, label) {
  await page.click('.login__box')
  await page.click(`.login__mock-btn:has-text("${label}")`)
  // uni H5 下 reLaunch 到 tab 页后 hash 会被归一成 #/（不是 #/pages/index/index），
  // 所以用首页宫格的出现来判落地，不用 URL。
  await page.waitForSelector('.lqg-tile', { timeout: 20000 })
  await page.waitForTimeout(500)
}

async function entriesOf(page) {
  return page.$$eval('.lqg-tile .lqg-tile__t', els => els.map(e => e.textContent.trim()))
}

async function gotoMe(page) {
  // tab 页：H5 下点底部原生 tabbar 的「我的」（切 tab 后 hash 可能仍是 #/）
  await page.click('.uni-tabbar__item:has-text("我的")')
  await page.waitForSelector('.me__logout', { timeout: 20000 })
  await page.waitForTimeout(500)
}

async function bodyText(page) {
  return page.evaluate(() => document.body.innerText)
}

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await ctx.newPage()
page.on('pageerror', e => console.log('[pageerror]', e.message))

try {
  // ── 0. 登录页 + 拒绝授权手机号停在登录页 ─────────────────────────────────
  await resetToLogin(page)
  await page.screenshot({ path: path.join(SHOTS, '00-login.png'), fullPage: true })
  check('登录页渲染（协议勾选 + 微信手机号快捷登录按钮）',
    await page.$('.login__btn') !== null,
    (await page.$eval('.login__btn', e => e.textContent.trim())))

  await page.click('.login__box')
  // 模拟「拒绝授权手机号」：getphonenumber 回调 detail 里没有 code
  // （真机扫码授权在本沙箱不可用，这里走的是页面里同一条拒绝分支）
  await page.$eval('.login__btn', (el) => {
    el.dispatchEvent(new CustomEvent('getphonenumber', { detail: {}, bubbles: true, composed: true }))
  })
  await page.waitForTimeout(800)
  const urlAfterReject = page.url()
  const textAfterReject = await bodyText(page)
  const toastText = await page.$$eval('.uni-toast, .uni-sample-toast, [class*="toast"]', els => els.map(e => e.innerText).join(' | ')).catch(() => '')
  await page.screenshot({ path: path.join(SHOTS, '00-login-reject-phone.png'), fullPage: true })
  check('拒绝授权手机号：停在登录页（URL 仍在 login）',
    /pages\/login\/index/.test(urlAfterReject), urlAfterReject)
  check('拒绝授权手机号：提示「需要手机号才能送检和查看结果」',
    `${textAfterReject} ${toastText}`.includes('需要手机号才能送检和查看结果'),
    `toast="${toastText.replace(/\n/g, ' ')}"`)
  check('拒绝授权手机号：没有落到首页',
    !/pages\/index\/index/.test(urlAfterReject), urlAfterReject)

  // ── 1. 内部首页 ─────────────────────────────────────────────────────────
  await resetToLogin(page)
  await mockLogin(page, '内部人员 · 李工')
  await page.screenshot({ path: path.join(SHOTS, '01-home-internal.png'), fullPage: true })
  const intEntries = await entriesOf(page)
  check('内部首页四个入口、顺序 = 模板顺序',
    JSON.stringify(intEntries) === JSON.stringify(INTERNAL_ENTRIES), JSON.stringify(intEntries))
  const intHomeText = await bodyText(page)
  check('内部首页含 -80 冻存记录', intHomeText.includes('-80 冻存记录'))
  const intDigits = digitsOutside80(intHomeText)
  check('内部首页没有任何数字（去掉表名里的 -80 后无数字）',
    intDigits.length === 0, `digits=${JSON.stringify(intDigits)}`)
  const intBadges = await page.$$eval('[class*="badge"], [class*="is-dot"], [class*="StatTile"]', els => els.length).catch(() => -1)
  check('内部首页没有角标 / 数字摘要元素', intBadges === 0, `matched=${intBadges}`)

  // ── 2. 内部「我的」 ─────────────────────────────────────────────────────
  await gotoMe(page)
  await page.screenshot({ path: path.join(SHOTS, '02-me-internal.png'), fullPage: true })
  const intMeText = await bodyText(page)
  check('内部「我的」有「历史编辑记录」', intMeText.includes('历史编辑记录'))
  check('内部「我的」有「内部管理」板块', intMeText.includes('内部管理'))
  check('内部「我的」没有「单位与组别」', !intMeText.includes('单位与组别'))
  check('内部管理板块底部小字 = 「核验、冻存取用请到网页工作台」',
    intMeText.includes('核验、冻存取用请到网页工作台') && !intMeText.includes('修改、核验、冻存取用请到网页工作台'),
    JSON.stringify(intMeText.split('\n').filter(l => l.includes('请到网页工作台'))))

  // ── 3. 外部首页 ─────────────────────────────────────────────────────────
  await resetToLogin(page)
  await mockLogin(page, '外部人员 · 王医生（已核验）')
  await page.screenshot({ path: path.join(SHOTS, '03-home-external.png'), fullPage: true })
  const extEntries = await entriesOf(page)
  check('外部首页三个入口、顺序 = 模板顺序（没有 -80 冻存记录）',
    JSON.stringify(extEntries) === JSON.stringify(EXTERNAL_ENTRIES), JSON.stringify(extEntries))
  const extHomeText = await bodyText(page)
  check('外部首页不含 -80 冻存记录 / 冻存字样',
    !extHomeText.includes('冻存'), extHomeText.includes('冻存') ? '含冻存' : 'ok')
  const extDigits = digitsOutside80(extHomeText)
  check('外部首页没有任何数字', extDigits.length === 0, `digits=${JSON.stringify(extDigits)}`)
  check('外部首页第三格横向占满整行（.lqg-tile--full）',
    (await page.$$('.lqg-tile--full')).length === 1)
  check('外部首页有单位 · 组别 · 核验状态行',
    extHomeText.includes('A 医院') && extHomeText.includes('肝胆外科组'),
    JSON.stringify(extHomeText.split('\n').filter(l => l.includes('医院') || l.includes('组'))))

  // ── 4. 外部「我的」 ─────────────────────────────────────────────────────
  await gotoMe(page)
  await page.screenshot({ path: path.join(SHOTS, '04-me-external.png'), fullPage: true })
  const extMeText = await bodyText(page)
  check('外部「我的」有「历史编辑记录」', extMeText.includes('历史编辑记录'))
  check('外部「我的」有「单位与组别」', extMeText.includes('单位与组别'))
  check('外部「我的」没有「内部管理」板块（不渲染，不是置灰）',
    !extMeText.includes('内部管理'))

  // ── 5. 单位与组别页（截图） ─────────────────────────────────────────────
  // 这一行的 title 是「单位 · 组别」的值本身，「单位与组别」只出现在板块标题上；
  // 用这一行特有的 desc 定位（改了需要中心重新核验）
  await page.click('.merow:has-text("改了需要中心重新核验")')
  await page.waitForURL(/pages\/me\/unit-group/, { timeout: 20000 })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: path.join(SHOTS, '05-unit-group.png'), fullPage: true })
  const ugText = await bodyText(page)
  check('单位与组别页渲染（单位/组别选择 + 保存入口 + 核验状态）',
    ugText.includes('单位') && ugText.includes('组别')
    && (ugText.includes('保存') || ugText.includes('提交'))
    && (ugText.includes('已核验') || ugText.includes('待核验')),
    JSON.stringify(ugText.slice(0, 400)))
  check('单位与组别页 URL 正确', /pages\/me\/unit-group/.test(page.url()), page.url())
}
catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n')[0] : String(e))
}
finally {
  const failed = results.filter(r => !r.ok)
  console.log(`\n== L2(mp-h5) ${results.length - failed.length}/${results.length} 通过 ==`)
  console.log(`SHOTS=${SHOTS}`)
  await browser.close()
  process.exit(failed.length === 0 ? 0 : 1)
}
