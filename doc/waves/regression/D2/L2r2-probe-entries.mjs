/**
 * D2 / r2 / L2 微探针：首页**每一个**宫格点进去的落点（r1-S0-1「首页四个入口点进去是填写页且可提交」的逐格复验）。
 * D2 内的两格（样本记录信息表 / 类器官收样记录）必须带 mode=new；石蜡包埋 / -80 冻存是 D3/D4 的页面，
 * 只记录落点是不是错误态，不作为 D2 判据。只读不改产品。
 */
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')
const MP = 'http://127.0.0.1:9200'
const SHOTS = path.join(HERE, 'shots/L2r2-mp-extra')
mkdirSync(SHOTS, { recursive: true })

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 390, height: 844 } })).newPage()
const out = {}
async function login(label) {
  await page.goto(`${MP}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login__agree', { timeout: 25000 })
  if (!(await page.$eval('.login__box', el => el.className.includes('login__box--on')).catch(() => false))) await page.click('.login__box')
  await page.click(`.login__mock-btn:has-text("${label}")`)
  await page.waitForSelector('.lqg-tile', { timeout: 25000 })
  await page.waitForTimeout(700)
}
try {
  for (const [who, label] of [['extA', '外部人员 · 王医生（已核验）'], ['staff', '内部人员 · 李工']]) {
    await login(label)
    const tiles = await page.$$eval('.lqg-tile .lqg-tile__t', els => els.map(e => e.textContent.trim()))
    out[who] = {}
    for (const t of tiles) {
      await page.goto(`${MP}/#/pages/index/index`, { waitUntil: 'domcontentloaded' })
      await page.waitForSelector('.lqg-tile', { timeout: 25000 })
      await page.waitForTimeout(400)
      await page.click(`.lqg-tile:has-text("${t}")`)
      await page.waitForTimeout(2200)
      const txt = await page.evaluate(() => document.body.innerText)
      const submits = await page.locator('uni-button:has-text("提交")').count()
      out[who][t] = { url: page.url().replace(MP, ''), submit: submits, loadErr: /没能加载/.test(txt) }
      await page.screenshot({ path: path.join(SHOTS, `x-entry-${who}-${t.slice(0, 4)}.png`), fullPage: true })
    }
  }
  console.log(JSON.stringify(out, null, 1))
} catch (e) { console.log('ERR', e.message, JSON.stringify(out, null, 1)) } finally { await browser.close() }
