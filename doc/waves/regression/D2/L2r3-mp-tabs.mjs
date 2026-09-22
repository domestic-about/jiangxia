/**
 * D2 / r3 / L2 —— 组 3 硬断言（本片自写，独立复验）：
 *   「历史编辑记录」内部两个页签（样本记录信息表 / 类器官收样记录）
 *   ★ 每个页签只出现该 sampleKind 的记录（逐行拿 code 回库核对 sample_kind）
 *   ★ 点一行进**对应类别**的表单（样本 → pages/sample/form；类器官 → pages/organoid/form）
 *
 * 微信开发者工具在本沙箱跑不通（EPERM + 需扫码）→ miniapp H5(9200, VITE_MOCK_LOGIN=1) + Playwright 等价覆盖。
 * **开发者工具 / 真机未覆盖**。
 *
 * 前置：后端 8081 + reseed + miniapp 9200。跑法：node doc/waves/regression/D2/L2r3-mp-tabs.mjs
 */
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const MP = process.env.LQG_H5_BASE || 'http://127.0.0.1:9200'
const SHOTS = path.join(HERE, 'shots/L2r3-mp')
mkdirSync(SHOTS, { recursive: true })

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

function dbOne(sql) {
  try {
    return execFileSync('psql', ['-h', '127.0.0.1', '-p', '5433', '-U', 'lqg', '-d', 'lqg_dev', '-Atc', sql],
      { env: { ...process.env, PGPASSWORD: 'lqg_dev_pwd' }, encoding: 'utf8' }).trim()
  } catch (e) { return '__ERR__' + e.message.slice(0, 80) }
}

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await ctx.newPage()
const pageErrors = []
page.on('pageerror', e => pageErrors.push(e.message))

async function resetToLogin() {
  await page.goto(`${MP}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login__agree', { timeout: 25000 })
  await page.waitForTimeout(400)
}
async function mockLogin(label) {
  const on = await page.$eval('.login__box', el => el.className.includes('login__box--on')).catch(() => false)
  if (!on) await page.click('.login__box')
  await page.click(`.login__mock-btn:has-text("${label}")`)
  await page.waitForSelector('.lqg-tile', { timeout: 25000 })
  await page.waitForTimeout(800)
}
async function gotoHistory() {
  await page.goto(`${MP}/#/pages/history/index`, { waitUntil: 'domcontentloaded' })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.his__switch', { timeout: 25000 })
  await page.waitForTimeout(1800)
}
/** 当前页签下每一行的 code + 摘要文本 */
async function readRows() {
  return page.evaluate(() => [...document.querySelectorAll('.his__item')].map(e => ({
    text: e.innerText.replace(/\n/g, '|'),
    code: (e.innerText.split('\n')[0] || '').trim(),
  })))
}
/** code → 库内 sample_kind（code 规则 = internal_no || submit_no，与页面 sources.ts codeOf 一致） */
function kindOf(code) {
  return dbOne(`SELECT sample_kind FROM t_lqg_sample WHERE del_flag='0' AND (internal_no='${code}' OR (coalesce(internal_no,'')='' AND submit_no='${code}')) LIMIT 1`)
}

try {
  await resetToLogin()
  await mockLogin('内部人员 · 李工')
  await gotoHistory()
  await page.screenshot({ path: path.join(SHOTS, 't-01-history-tabs.png'), fullPage: true })

  const tabNames = await page.$$eval('.lqg-sheets__item', els => els.map(e => e.innerText.trim()))
  check('R3T-01 内部历史编辑记录出现「样本记录信息表 / 类器官收样记录」两个页签',
    tabNames.includes('样本记录信息表') && tabNames.includes('类器官收样记录'), JSON.stringify(tabNames))

  // ── 页签①：样本记录信息表 → 逐行必须 tissue ────────────────────────────
  await page.locator('.lqg-sheets__item:has-text("样本记录信息表")').first().click()
  await page.waitForTimeout(2200)
  await page.screenshot({ path: path.join(SHOTS, 't-02-tab-sample.png'), fullPage: true })
  const sampleRows = await readRows()
  const sampleKinds = sampleRows.map(r => ({ code: r.code, kind: kindOf(r.code) }))
  const sampleBad = sampleKinds.filter(x => x.kind !== 'tissue')
  check('R3T-02 样本页签逐行 sample_kind=tissue（非 tissue 行 = 0，且至少有 1 行正向样本）',
    sampleRows.length > 0 && sampleBad.length === 0, `rows=${JSON.stringify(sampleKinds)}`)

  // ── 页签②：类器官收样记录 → 逐行必须 organoid ──────────────────────────
  await page.locator('.lqg-sheets__item:has-text("类器官收样")').first().click()
  await page.waitForTimeout(2500)
  await page.screenshot({ path: path.join(SHOTS, 't-03-tab-organoid.png'), fullPage: true })
  const orgRows = await readRows()
  const orgKinds = orgRows.map(r => ({ code: r.code, kind: kindOf(r.code) }))
  const orgBad = orgKinds.filter(x => x.kind !== 'organoid')
  check('R3T-03 类器官页签逐行 sample_kind=organoid（非 organoid 行 = 0，且至少有 1 行正向样本）',
    orgRows.length > 0 && orgBad.length === 0, `rows=${JSON.stringify(orgKinds)}`)

  // 两页签行集合不相交
  const sc = new Set(sampleRows.map(r => r.code))
  const oc = new Set(orgRows.map(r => r.code))
  const inter = [...sc].filter(x => oc.has(x))
  check('R3T-04 两个页签的行集合不相交', inter.length === 0, `sample=${JSON.stringify([...sc])} organoid=${JSON.stringify([...oc])} inter=${JSON.stringify(inter)}`)

  // ── 点类器官行 → 必须进 pages/organoid/form ────────────────────────────
  await page.locator('.his__item').first().click()
  await page.waitForTimeout(2800)
  await page.screenshot({ path: path.join(SHOTS, 't-04-organoid-row-target.png'), fullPage: true })
  const orgUrl = page.url()
  check('R3T-05 类器官页签点一行 → 进 pages/organoid/form（对应类别表单）',
    /pages\/organoid\/form/.test(orgUrl), `url=${orgUrl}`)

  // ── 点样本行 → 必须进 pages/sample/form（不能进 organoid）──────────────
  await gotoHistory()
  await page.locator('.lqg-sheets__item:has-text("样本记录信息表")').first().click()
  await page.waitForTimeout(2200)
  await page.locator('.his__item').first().click()
  await page.waitForTimeout(2800)
  await page.screenshot({ path: path.join(SHOTS, 't-05-sample-row-target.png'), fullPage: true })
  const smpUrl = page.url()
  check('R3T-06 样本页签点一行 → 进 pages/sample/form（不是 organoid 表单）',
    /pages\/sample\/form/.test(smpUrl) && !/organoid/.test(smpUrl), `url=${smpUrl}`)

  check('R3T-07 全程无未捕获前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
} catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 4).join(' | ') : String(e))
} finally {
  const failed = results.filter(r => !r.ok)
  console.log(`\n== D2-r3-L2-mp-tabs ${results.length - failed.length}/${results.length} 通过 ==`)
  console.log(`SHOTS=${SHOTS}`)
  await browser.close()
  process.exit(failed.length === 0 ? 0 : 1)
}
