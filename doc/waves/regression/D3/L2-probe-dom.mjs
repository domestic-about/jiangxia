/** 一次性探针：把小程序 H5 的日期选择器 / 表格页 / 历史页 DOM 打出来（不是断言证据）。 */
import { chromium, MP, mockLogin, resetToLogin, gotoPage, sleep } from './L2-lib.mjs'

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 430, height: 900 } })).newPage()
page.on('pageerror', e => console.log('PAGEERR', e.message))

await resetToLogin(page)
await mockLogin(page, 'staff')

// 表格页
await gotoPage(page, 'pages/ledger/index?sheet=embed')
console.log('--- ledger tabs:', JSON.stringify(await page.locator('.lqg-sheets__item').allInnerTexts()))
console.log('--- ledger count:', await page.locator('.lqg-count').innerText().catch(() => 'none'))
console.log('--- ledger rows:', await page.locator('.ledger__row').count())
console.log('--- frozen:', JSON.stringify(await page.locator('.ledger__fz-main').allInnerTexts()))
console.log('--- subs:', JSON.stringify(await page.locator('.lqg-ledger__fz-sub').allInnerTexts()))
console.log('--- heads:', JSON.stringify(await page.locator('.ledger__head').innerText().catch(() => '')))

// 填写页 new
await gotoPage(page, 'pages/embed/form?mode=new')
console.log('--- emb body:', (await page.locator('.emb').innerText().catch(() => 'NOEMB')).replace(/\n/g, '|').slice(0, 600))
console.log('--- emb rows:', JSON.stringify(await page.evaluate(() => [...document.querySelectorAll('.wd-input.is-cell, .wd-cell')].map(e => (e.querySelector('.wd-input__label-inner, .wd-cell__title') || {}).innerText))))

// 打开日期选择器
const idx = await page.evaluate(() => [...document.querySelectorAll('.wd-input.is-cell, .wd-cell')].findIndex(e => ((e.querySelector('.wd-input__label-inner, .wd-cell__title') || {}).innerText || '').trim() === '脱水时间'))
console.log('--- 脱水时间 row idx =', idx)
if (idx >= 0) {
  await page.locator('.wd-input.is-cell, .wd-cell').nth(idx).click()
  await sleep(1200)
  const dump = await page.evaluate(() => {
    const vis = [...document.querySelectorAll('.wd-popup, .wd-picker, .wd-datetime-picker')].filter(e => e.offsetParent !== null)
    return vis.map(v => ({ cls: v.className, html: v.outerHTML.slice(0, 2500) }))
  })
  console.log('--- picker dump:', JSON.stringify(dump, null, 1).slice(0, 3000))
  await page.screenshot({ path: '/tmp/d3l2-picker.png' })
}
await browser.close()
