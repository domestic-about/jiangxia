/** 工作台侦察：登录 + 进样本总表 + 打开待核验行抽屉，打印 DOM 文本与按钮。 */
import path from 'node:path'
import { chromium } from './L2r4-lib.mjs'

const BASE = 'http://127.0.0.1:8082'
const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 950 } })).newPage()
page.on('pageerror', e => console.log('[pageerror]', e.message.slice(0, 200)))

await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
await page.evaluate(() => localStorage.clear()).catch(() => {})
await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.login-form input', { timeout: 30000 })
const ins = page.locator('.login-form input')
await ins.nth(0).fill('lqgadmin')
await ins.nth(1).fill('admin123')
await page.click('.login-form button')
await page.waitForSelector('.el-menu', { timeout: 30000 })
await page.waitForTimeout(2000)
await page.locator('.el-menu-item:has-text("样本总表")').first().click({ timeout: 15000 })
await page.waitForResponse(r => r.url().includes('/lqg/sample/list'), { timeout: 25000 }).catch(() => {})
await page.waitForTimeout(1500)

const mode = process.argv[2] || 'drawer'
if (mode === 'drawer') {
  const row = page.locator('.el-table__body tr').filter({ hasText: 'SJ90000002' }).first()
  await row.locator('button:has-text("核验")').click()
  await page.waitForSelector('.el-drawer:visible', { timeout: 20000 })
  await page.waitForTimeout(1800)
  console.log('=== drawer buttons ===')
  console.log(await page.evaluate(() => [...document.querySelectorAll('.el-drawer:not([style*="display: none"]) button')].map((b, i) => `${i}:${b.innerText.trim()}|disabled=${b.disabled}`).join('\n')))
  console.log('=== drawer text ===')
  console.log((await page.locator('.el-drawer:visible').first().innerText()).slice(0, 2000))
  console.log('=== drawer row values ===')
  console.log(await page.evaluate(() => [...document.querySelectorAll('.el-drawer .el-form-item')].map((e, i) => `${i}:${(e.querySelector('.el-form-item__label')||{}).innerText || ''}=${(e.querySelector('input,textarea')||{}).value ?? (e.innerText||'').replace(/\n/g,'~')}`).join('\n')))
}
await browser.close()
