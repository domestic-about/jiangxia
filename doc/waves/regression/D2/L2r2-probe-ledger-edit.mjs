/**
 * D2 / r2 / L2 微探针：内部管理表格页「点一行 → 只读详情」上是否有权威要求的「修改」入口
 * （doc/authority/ui-index.yaml UI:mp.ledger:「点一行 → 填写页只读模式，右上角「修改」切到修改模式」，
 *  UI:mp.ledger.tissue 同句）。只读不改产品。
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
try {
  await page.goto(`${MP}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login__agree', { timeout: 25000 })
  if (!(await page.$eval('.login__box', el => el.className.includes('login__box--on')).catch(() => false))) await page.click('.login__box')
  await page.click('.login__mock-btn:has-text("内部人员 · 李工")')
  await page.waitForSelector('.lqg-tile', { timeout: 25000 })
  await page.goto(`${MP}/#/pages/ledger/index`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.lqg-ledger__th', { timeout: 25000 })
  await page.waitForTimeout(1500)
  await page.locator('.ledger__row').filter({ hasText: 'T-hli05' }).first().click()
  await page.waitForTimeout(2500)
  const info = await page.evaluate(() => ({
    url: location.hash,
    buttons: [...document.querySelectorAll('uni-button, button')].map(e => e.innerText.trim()).filter(Boolean),
    texts: [...document.querySelectorAll('text, .nav__right, [class*="nav"], [class*="edit"]')].map(e => e.innerText.trim()).filter(t => t && t.length < 12),
    inputs: document.querySelectorAll('input, textarea').length,
  }))
  await page.screenshot({ path: path.join(SHOTS, 'x-10-ledger-view-buttons.png'), fullPage: true })
  console.log(JSON.stringify(info, null, 1))
  // 点「修改」→ 应切到可写（mode=edit + 保存按钮 + 有输入控件）
  const editLink = page.locator('text=修改').first()
  if (await editLink.count()) {
    await editLink.click()
    await page.waitForTimeout(2500)
    const after = await page.evaluate(() => ({
      url: location.hash,
      inputs: document.querySelectorAll('input, textarea').length,
      buttons: [...document.querySelectorAll('uni-button')].map(e => e.innerText.trim()).filter(Boolean),
    }))
    await page.screenshot({ path: path.join(SHOTS, 'x-11-ledger-edit-mode.png'), fullPage: true })
    console.log('AFTER_CLICK', JSON.stringify(after))
  }
} catch (e) {
  console.log('ERR', e.message)
} finally { await browser.close() }
