/**
 * D2 / r4 / L2 —— 侦察脚本（本片自写）：把首页 / 表单 / 历史 / 我的 / 表格页的 DOM 文本结构打出来，
 * 只用于确定选择器；不是断言证据。
 * 跑法：node doc/waves/regression/D2/L2r4-recon.mjs [page]
 */
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')
const MP = process.env.LQG_H5_BASE || 'http://127.0.0.1:9200'

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await ctx.newPage()
page.on('pageerror', e => console.log('[pageerror]', e.message.slice(0, 200)))

async function login(label) {
  await page.goto(`${MP}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login__agree', { timeout: 30000 })
  await page.waitForTimeout(500)
  const on = await page.$eval('.login__box', el => el.className.includes('login__box--on')).catch(() => false)
  if (!on) await page.click('.login__box')
  await page.click(`.login__mock-btn:has-text("${label}")`)
  await page.waitForSelector('.lqg-tile', { timeout: 30000 })
  await page.waitForTimeout(900)
}

const which = process.argv[2] || 'index'
await login(process.env.LQG_AS || '外部人员 · 王医生（已核验）')

if (which === 'index') {
  console.log('=== index tiles ===')
  console.log(await page.evaluate(() => [...document.querySelectorAll('.lqg-tile')].map(e => JSON.stringify(e.innerText.replace(/\n/g, '|'))).join('\n')))
  console.log('=== index body text ===')
  console.log((await page.evaluate(() => document.body.innerText)).slice(0, 1200))
} else if (which === 'form') {
  const url = process.env.LQG_URL || 'pages/sample/form?mode=new'
  await page.goto(`${MP}/#/${url}`, { waitUntil: 'domcontentloaded' })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(3000)
  console.log('=== url ===', page.url())
  console.log('=== cells ===')
  console.log(await page.evaluate(() => [...document.querySelectorAll('.wd-cell')].map((e, i) => `${i}:${e.className}|${e.innerText.replace(/\n/g, '~')}`).join('\n')).catch(e => 'ERR ' + e.message))
  console.log('=== inputs ===')
  console.log(await page.evaluate(() => [...document.querySelectorAll('input,textarea')].map((e, i) => `${i}:${e.tagName}|${e.className}|${e.placeholder || ''}|${e.value || ''}|ro=${e.readOnly}`).join('\n')))
  console.log('=== segs ===')
  console.log(await page.evaluate(() => [...document.querySelectorAll('.lqg-seg')].map((e, i) => `${i}:${e.innerText.replace(/\n/g, '/')}`).join('\n')))
  console.log('=== buttons ===')
  console.log(await page.evaluate(() => [...document.querySelectorAll('button,.form__btn,.lqg-bar button')].map((e, i) => `${i}:${e.tagName}|${e.className}|${e.innerText.replace(/\n/g, '~')}`).join('\n')))
  console.log('=== BODY ===')
  console.log((await page.evaluate(() => document.body.innerText)).slice(0, 2500))
} else if (which === 'body') {
  await page.goto(`${MP}/#/${process.env.LQG_URL}`, { waitUntil: 'domcontentloaded' })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(3000)
  console.log('=== url ===', page.url())
  console.log((await page.evaluate(() => document.body.innerText)).slice(0, 3000))
}

await browser.close()
