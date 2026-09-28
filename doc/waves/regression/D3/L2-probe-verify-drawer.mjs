/**
 * 定点复现：工作台核验抽屉「判为有效并保存」（外部待核验送样）
 * 只做一件事：把点按钮前后发出去的请求 / 响应 / JS 异常全抓下来。
 */
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import { chromium, WEB, shotsDir, sleep, WS } from './L2-lib.mjs'

const S = shotsDir('g3b-verify-probe')
execFileSync('bash', ['doc/verify/reseed.sh', '--yes'], { cwd: WS, encoding: 'utf8', stdio: 'ignore' })

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1680, height: 950 } })).newPage()
const log = []
page.on('pageerror', e => log.push(`PAGEERROR: ${e.message}\n${(e.stack || '').split('\n').slice(0, 4).join('\n')}`))
page.on('console', m => { if (m.type() === 'error') log.push(`CONSOLE-ERR: ${m.text().slice(0, 200)}`) })
page.on('request', r => {
  if (/\/lqg\/embed/.test(r.url()) && r.method() !== 'GET') log.push(`REQ ${r.method()} ${r.url()} :: ${(r.postData() || '').slice(0, 300)}`)
})
page.on('response', async r => {
  if (/\/lqg\/embed/.test(r.url()) && r.request().method() !== 'GET') {
    let body = ''
    try { body = (await r.text()).slice(0, 300) } catch {}
    log.push(`RESP ${r.status()} ${r.url()} :: ${body}`)
  }
})

await page.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
await page.evaluate(() => localStorage.clear()).catch(() => {})
await page.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.login-form input', { timeout: 30000 })
const ins = page.locator('.login-form input')
await ins.nth(0).fill('lqgadmin'); await ins.nth(1).fill('admin123')
await page.click('.login-form button')
await page.waitForSelector('.el-menu', { timeout: 30000 })
await sleep(1800)
await page.locator('.el-menu-item:has-text("石蜡包埋")').first().click()
await sleep(2800)

const rows = page.locator('.el-table__body tr')
// 库里先造一条待核验的外部送样（走真接口，与 extA 提交同形）
console.log('rows before:', await rows.count())
await page.screenshot({ path: path.join(S, 'probe-01-list.png'), fullPage: true })

// 用 2006（seed 里现成的待核验外部送样、所挂样本 pending → 按钮置灰）说明对照；
// 真正要复现的是「所挂样本有效」的待核验外部送样：直接查库找一条 —— seed 里没有，
// 所以先经 API 造一条（与 extA 小程序提交同一条路径）。
const out = execFileSync('bash', ['doc/verify/api.sh', '--as', 'extA', 'POST', '/mp/ext/embed',
  JSON.stringify({ sampleId: 9000001001, sampleType: '组织', organoidSourceType: '' })], { cwd: WS, encoding: 'utf8' })
console.log('extA submit:', out.slice(0, 200))
// 硬 reload：路由缓存（keep-alive）会让列表停在旧数据上，探针必须拿最新列表
await page.reload({ waitUntil: 'domcontentloaded' })
await page.waitForSelector('.el-menu', { timeout: 30000 })
await sleep(2000)
await page.goto(`${WEB}/embed`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.el-table__body tr', { timeout: 25000 })
await sleep(2500)

const firstTxt = (await rows.first().innerText()).replace(/\n/g, '|')
console.log('first row after extA submit:', firstTxt.slice(0, 160))
log.length = 0

await rows.first().locator('button', { hasText: /核验/ }).first().click()
await page.waitForFunction(() => [...document.querySelectorAll('.el-drawer')].some(d => d.getBoundingClientRect().height > 0), { timeout: 15000 })
await sleep(1500)

const drawer = page.locator('.el-drawer:visible').first()
const items = drawer.locator('.el-form-item')
const n = await items.count()
let target = -1
for (let i = 0; i < n; i++) {
  const lb = (await items.nth(i).locator('.el-form-item__label').first().innerText().catch(() => '')).trim()
  if (/石蜡块编号/.test(lb)) target = i
}
console.log('form items =', n, 'blockNo index =', target)
const input = items.nth(target).locator('input').first()
await input.fill('T-R1L2-PROBE')
await sleep(500)
console.log('input value after fill =', await input.inputValue())

const btn = drawer.locator('.el-drawer__footer button', { hasText: /判为有效/ }).first()
console.log('btn disabled =', await btn.isDisabled())
await btn.click()
await sleep(4000)
await page.screenshot({ path: path.join(S, 'probe-02-after-click.png'), fullPage: true })
console.log('--- NET/ERR LOG ---')
for (const l of log) console.log(l)
console.log('--- drawer still visible:', await drawer.isVisible().catch(() => 'gone'))
console.log('--- toast:', await page.locator('.el-message').allInnerTexts().catch(() => []))
console.log('--- db row:', execFileSync('bash', ['-c',
  `python3 doc/verify/db.py --quiet --sql "SELECT verify_status||'|'||COALESCE(paraffin_block_no,'-') FROM t_lqg_embed WHERE paraffin_block_no='T-R1L2-PROBE' OR (submit_source='external' AND verify_status='pending')"`],
{ cwd: WS, encoding: 'utf8' }))
await browser.close()
