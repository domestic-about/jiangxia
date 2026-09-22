/**
 * D3 / r2 / L2 —— #145 独立复验（第二分支）：核验抽屉「判为无效」也必须直接走 /verify，且原因落库。
 *   ① 工作台打开 seed 里那条待核验外部送样（2006 / SJ90000002，所挂样本未核验 → 判有效置灰）
 *   ② 点「判为无效」→ 填原因 → 保存
 *   ③ 抓网络：必须且只发 PUT /lqg/embed/{id}/verify（不得预发 PUT /lqg/embed）；库内 invalid + 原因
 * 不读图、不 mock。跑法：node doc/waves/regression/D3/L2-r2-issue145-invalid.mjs
 */
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { chromium, WEB, sleep, shotsDir, makeChecker, dbOne, WS } from './L2-lib.mjs'

const S = shotsDir('r2-issue145')
const { check, finish } = makeChecker('D3-r2-L2-issue145-invalid')
const REASON = '测-r2-145-无效原因'

execFileSync('bash', ['doc/verify/reseed.sh', '--yes'], { cwd: WS, encoding: 'utf8', stdio: 'ignore' })

const browser = await chromium.launch({ headless: true })
const web = await (await browser.newContext({ viewport: { width: 1680, height: 1000 } })).newPage()
const errs = []
web.on('pageerror', e => errs.push('web:' + e.message))
const net = []
web.on('response', async r => {
  if (/\/lqg\/embed/.test(r.url()) && r.request().method() !== 'GET') {
    let b = ''
    try { b = (await r.text()).slice(0, 150) } catch {}
    net.push(`${r.request().method()} ${r.url().replace(/^.*\/dev-api/, '')} -> ${r.status()} ${(b.match(/"msg":"([^"]*)"/) || [])[1] || ''}`)
  }
})

try {
  const targetId = dbOne(`SELECT id FROM t_lqg_embed WHERE verify_status='pending' AND submit_source='external' AND del_flag='0' ORDER BY create_time DESC LIMIT 1`)
  await web.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await web.evaluate(() => localStorage.clear()).catch(() => {})
  await web.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await web.waitForSelector('.login-form input', { timeout: 30000 })
  const ins = web.locator('.login-form input')
  await ins.nth(0).fill('lqgadmin')
  await ins.nth(1).fill('admin123')
  await web.click('.login-form button')
  await web.waitForSelector('.el-menu', { timeout: 30000 })
  await sleep(2000)
  await web.locator('.el-menu-item:has-text("石蜡包埋")').first().click({ timeout: 15000 })
  await web.waitForResponse(r => r.url().includes('/lqg/embed/list'), { timeout: 25000 }).catch(() => {})
  await sleep(2200)
  await web.locator('.el-table__body tr').first().locator('button', { hasText: /核验/ }).first().click()
  await web.waitForFunction(() => [...document.querySelectorAll('.el-drawer')].some(d => d.getBoundingClientRect().height > 0), { timeout: 15000 })
  await sleep(1500)
  const validDisabled = await web.locator('.el-drawer:visible .el-drawer__footer button', { hasText: /判为有效/ }).first().isDisabled()
  await web.screenshot({ path: path.join(S, '145-invalid-01-drawer.png'), fullPage: true })

  net.length = 0
  await web.locator('.el-drawer:visible .el-drawer__footer button', { hasText: /判为无效/ }).first().click()
  await web.waitForSelector('.el-dialog:visible textarea', { timeout: 10000 })
  await sleep(500)
  // 先空原因提交一次（必填校验）
  await web.locator('.el-dialog:visible .el-dialog__footer button', { hasText: /判为无效/ }).first().click()
  await sleep(1200)
  const afterEmptyReason = dbOne(`SELECT verify_status FROM t_lqg_embed WHERE id=${targetId}`)
  const emptyNet = net.length
  await web.locator('.el-dialog:visible textarea').first().fill(REASON)
  await sleep(400)
  await web.locator('.el-dialog:visible .el-dialog__footer button', { hasText: /判为无效/ }).first().click()
  await sleep(3000)
  const row = dbOne(`SELECT verify_status||'|'||COALESCE(invalid_reason,'NULL')||'|'||COALESCE(verify_by::text,'noverify') FROM t_lqg_embed WHERE id=${targetId}`)
  const adminId = dbOne(`SELECT user_id FROM sys_user WHERE user_name='lqgadmin'`)
  const toasts = (await web.locator('.el-message').allInnerTexts().catch(() => [])).join(' / ')
  await web.screenshot({ path: path.join(S, '145-invalid-02-after.png'), fullPage: true })
  console.log('net=' + JSON.stringify(net))
  check('#145-01 所挂样本未核验有效 → 「判为有效」置灰', validDisabled === true, `disabled=${validDisabled}`)
  check('#145-02 原因必填：空原因点「判为无效」不发请求、库里不变',
    afterEmptyReason === 'pending' && emptyNet === 0, `status=${afterEmptyReason} netBefore=${emptyNet}`)
  check('#145-03 [#145] 判为无效直接发 PUT /lqg/embed/{id}/verify（200），无预发 PUT /lqg/embed',
    net.some(x => new RegExp(`^PUT /lqg/embed/${targetId}/verify -> 200`).test(x)) && !net.some(x => /^PUT \/lqg\/embed -> /.test(x)),
    `net=${JSON.stringify(net)}`)
  check('#145-04 判为无效真的落库：invalid + 原因 + 核验人',
    row === `invalid|${REASON}|${adminId}`, `row=${row} expect=invalid|${REASON}|${adminId}`)
  check('#145-05 有可见成功反馈', toasts.length > 0, `toasts="${toasts}"`)
  check('#145-06 无 JS 运行时报错', errs.length === 0, errs.join(' || '))
} catch (e) {
  check('#145-EXCEPTION', false, String(e && e.stack || e).slice(0, 700))
}
finish()
await browser.close()
