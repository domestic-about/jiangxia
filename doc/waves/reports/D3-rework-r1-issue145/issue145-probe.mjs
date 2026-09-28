/**
 * issue #145（S0 · 工作台核验抽屉「判为有效并保存」点不动）返工后的定点验收探针。
 *
 * 纪律：**走真实 UI 路径**（Playwright + 8082 dev + 真后端 8081 + 真库 5433 + 真 seed），
 * 不读任何图片（截图只落盘），断言只读 DOM 文本 / 网络 / 库内行。
 *
 * 覆盖三条路径：
 *   ① 判有效（外部待核验、所挂样本已核验有效）→ 只发 PUT /lqg/embed/{id}/verify、200、落库、关抽屉、有提示
 *   ② 判无效（另一条 pending）→ /verify 带 reason、200、落库 invalid + 原因
 *   ③ 负对照（编号撞号）→ /verify 被拒、**有 toast**、抽屉不关、库里不变、无未捕获 rejection
 *
 * 跑法：node doc/waves/reports/D3-rework-r1-issue145/issue145-probe.mjs
 * 前置：8081 后端 / 8082 plus-ui dev 都在跑；脚本自己先 reseed。
 */
import path from 'node:path'
import { mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { chromium, WEB, WS, sleep, dbOne, api, makeChecker } from '../../regression/D3/L2-lib.mjs'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const S = path.join(HERE, 'shots')
mkdirSync(S, { recursive: true })

const { check, finish } = makeChecker('issue145')

const SAMPLE_ID = 9000001001 // seed 里已核验有效、挂在 extA 名下的样本
const WEB_USER_ID = 9000000100 // 工作台登录账号 lqgadmin（测试管理员）→ 核验人就是这个 id
const BLOCK = 'T-I145-R1-A'
const REASON = 'issue145 返工验收：外部送样信息不符'
const COLLIDE = 'T-E01-1' // seed 里已存在的石蜡块编号 → 判有效必被拒

const npath = (u) => new URL(u).pathname.replace(/^\/dev-api/, '')
const plainPutRe = /^REQ PUT \/lqg\/embed ::/
const verifyRe = /^REQ PUT \/lqg\/embed\/([^/]+)\/verify :: (.*)$/

execFileSync('bash', ['doc/verify/reseed.sh', '--yes'], { cwd: WS, encoding: 'utf8', stdio: 'ignore' })

// ── 0) 造三条「挂在已核验有效样本 1001 上的外部待核验送样」（走真接口，与 extA 小程序提交同形） ──
const made = []
for (let i = 0; i < 3; i++) {
  const out = api(['--as', 'extA', 'POST', '/mp/ext/embed',
    JSON.stringify({ sampleId: SAMPLE_ID, sampleType: '组织', organoidSourceType: '' })])
  const id = String(JSON.parse(out).data)
  const st = dbOne(`SELECT verify_status||'|'||COALESCE(paraffin_block_no,'-')||'|'||submit_source FROM t_lqg_embed WHERE id=${id}`)
  made.push({ id, st })
}
check('I145-00 造出 3 条外部待核验送样（挂 1001，库内 pending|-|external）',
  made.length === 3 && made.every(m => m.st === 'pending|-|external'), JSON.stringify(made))

// ── 打开工作台 ───────────────────────────────────────────────────────────────
const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1680, height: 950 } })
// ★ el-message / el-notification 默认 3s 就消失，跑完再查一定是空的 —— 用 MutationObserver
//   在它们出现的瞬间就记下来（否则会把「有提示」误判成「静默失败」）。
await ctx.addInitScript(() => {
  window.__lqgToasts = []
  window.__lqgNotes = []
  const scan = () => {
    document.querySelectorAll('.el-message').forEach(el => {
      const t = (el.innerText || '').trim()
      if (t && !window.__lqgToasts.includes(t)) window.__lqgToasts.push(t)
    })
    document.querySelectorAll('.el-notification').forEach(el => {
      const t = (el.innerText || '').trim()
      if (t && !window.__lqgNotes.includes(t)) window.__lqgNotes.push(t)
    })
  }
  document.addEventListener('DOMContentLoaded', () => {
    new MutationObserver(scan).observe(document.body, { childList: true, subtree: true })
    scan()
  })
})
const page = await ctx.newPage()
const net = []
const pageErrors = []
page.on('pageerror', e => pageErrors.push(String(e.message || e).slice(0, 160)))
page.on('request', r => {
  if (/\/lqg\/embed/.test(r.url()) && r.method() !== 'GET') {
    net.push(`REQ ${r.method()} ${npath(r.url())} :: ${(r.postData() || '').slice(0, 200)}`)
  }
})
page.on('response', async r => {
  if (/\/lqg\/embed/.test(r.url()) && r.request().method() !== 'GET') {
    let body = ''
    try { body = (await r.text()).slice(0, 220) } catch {}
    net.push(`RESP ${r.status()} ${npath(r.url())} :: ${body}`)
  }
})

const drawer = () => page.locator('.el-drawer:visible').first()
const drawerOpen = () => drawer().isVisible().catch(() => false)
const toasts = () => page.evaluate(() => (window.__lqgToasts || []).join(' / ')).catch(() => '')
const notes = () => page.evaluate(() => (window.__lqgNotes || []).join(' / ')).catch(() => '')
const resetToasts = () => page.evaluate(() => { window.__lqgToasts = []; window.__lqgNotes = [] }).catch(() => {})

const openTopVerifyDrawer = async () => {
  await page.locator('.el-table__body tr').first().locator('button', { hasText: /核验/ }).first().click()
  await page.waitForFunction(
    () => [...document.querySelectorAll('.el-drawer')].some(d => d.getBoundingClientRect().height > 0),
    { timeout: 15000 })
  await sleep(1300)
}

const fillBlockNo = async (value) => {
  const items = drawer().locator('.el-form-item')
  const n = await items.count()
  for (let i = 0; i < n; i++) {
    const lb = (await items.nth(i).locator('.el-form-item__label').first().innerText().catch(() => '')).trim()
    if (/石蜡块编号/.test(lb)) {
      await items.nth(i).locator('input').first().fill(value)
      return true
    }
  }
  return false
}

const verifyCalls = () => net.map(l => l.match(verifyRe)).filter(Boolean)
const plainPuts = () => net.filter(l => plainPutRe.test(l))

try {
  await page.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => localStorage.clear()).catch(() => {})
  await page.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login-form input', { timeout: 30000 })
  const ins = page.locator('.login-form input')
  await ins.nth(0).fill('lqgadmin')
  await ins.nth(1).fill('admin123')
  await page.click('.login-form button')
  await page.waitForSelector('.el-menu', { timeout: 30000 })
  await sleep(1800)
  await page.locator('.el-menu-item:has-text("石蜡包埋")').first().click()
  await sleep(2800)
  await page.waitForSelector('.el-table__body tr', { timeout: 25000 })
  await sleep(1800)

  // ─────── ① 判有效 ─────────────────────────────────────────────────────────
  net.length = 0
  pageErrors.length = 0
  await resetToasts()
  await openTopVerifyDrawer()
  const alertTxt = (await drawer().locator('.el-alert').first().innerText().catch(() => '')).replace(/\n/g, '|')
  check('I145-01 核验抽屉给出「所挂样本已核验有效」的提示', /已核验有效/.test(alertTxt), `alert=${alertTxt}`)
  check('I145-02 抽屉里找到了「石蜡块编号」输入框', await fillBlockNo(BLOCK))
  await page.screenshot({ path: path.join(S, 'a1-drawer-filled.png'), fullPage: true })
  net.length = 0
  await resetToasts()
  await drawer().locator('.el-drawer__footer button', { hasText: /判为有效/ }).first().click()
  await sleep(3500)

  const vc = verifyCalls()
  const vid = vc.length ? vc[0][1] : 'none'
  const rowState = vid === 'none' ? '__NO_VERIFY__'
    : dbOne(`SELECT verify_status||'|'||COALESCE(paraffin_block_no,'-')||'|'||CASE WHEN verify_by IS NULL THEN 'noverify' ELSE verify_by::text END FROM t_lqg_embed WHERE id=${vid}`)
  const toastA = await toasts()
  const noteA = await notes()
  await page.screenshot({ path: path.join(S, 'a2-after-valid.png'), fullPage: true })

  check('I145-03 判有效只发了一条 PUT /lqg/embed/{id}/verify（普通 PUT /lqg/embed 一条都没发）',
    vc.length === 1 && plainPuts().length === 0, `net=${JSON.stringify(net)}`)
  check('I145-04 verify 请求体带上了判有效的必填项 paraffinBlockNo',
    vc.length === 1 && JSON.parse(vc[0][2] || '{}').paraffinBlockNo === BLOCK && JSON.parse(vc[0][2] || '{}').action === 'valid',
    vc.length ? vc[0][2] : 'none')
  check('I145-05 verify 响应 200，且没有任何 400 的普通保存',
    net.some(l => /^RESP 200 \/lqg\/embed\/[^/]+\/verify/.test(l)) && !net.some(l => /^RESP .* \/lqg\/embed :: \{"code":400/.test(l)),
    JSON.stringify(net))
  check(`I145-06 判有效真的落库：valid + 编号 + 核验人 ${WEB_USER_ID}`, rowState === `valid|${BLOCK}|${WEB_USER_ID}`, `row=${rowState}`)
  check('I145-07 成功有明确提示（el-message 含「已判为有效」）', /已判为有效/.test(toastA), `toasts="${toastA}" notes="${noteA}"`)
  check('I145-08 判有效成功后抽屉关闭', (await drawerOpen()) === false, `drawerOpen=${await drawerOpen()}`)
  check('I145-09 全程没有未捕获 rejection / pageerror', pageErrors.length === 0, pageErrors.join(' || '))

  // ─────── ② 判无效（另一条 pending） ────────────────────────────────────────
  await page.goto(`${WEB}/embed`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.el-table__body tr', { timeout: 25000 })
  await sleep(2200)
  net.length = 0
  pageErrors.length = 0
  await resetToasts()
  await openTopVerifyDrawer()
  await drawer().locator('.el-drawer__footer button', { hasText: /判为无效/ }).first().click()
  await page.waitForSelector('.el-dialog:visible textarea', { timeout: 10000 })
  await page.locator('.el-dialog:visible textarea').first().fill(REASON)
  await sleep(300)
  await page.locator('.el-dialog:visible .el-dialog__footer button', { hasText: /判为无效/ }).first().click()
  await sleep(3500)

  const vc2 = verifyCalls()
  const iid = vc2.length ? vc2[0][1] : 'none'
  const iState = iid === 'none' ? '__NO_VERIFY__'
    : dbOne(`SELECT verify_status||'|'||COALESCE(invalid_reason,'-')||'|'||CASE WHEN verify_by IS NULL THEN 'noverify' ELSE verify_by::text END FROM t_lqg_embed WHERE id=${iid}`)
  const toastB = await toasts()
  const noteB = await notes()
  await page.screenshot({ path: path.join(S, 'b1-after-invalid.png'), fullPage: true })

  check('I145-10 判无效也直接走 /verify（不带普通 PUT），请求体带 reason',
    vc2.length === 1 && plainPuts().length === 0 && JSON.parse(vc2[0][2] || '{}').action === 'invalid' && JSON.parse(vc2[0][2] || '{}').reason === REASON,
    `net=${JSON.stringify(net)}`)
  check(`I145-11 判无效落库：invalid + 原因 + 核验人 ${WEB_USER_ID}`, iState === `invalid|${REASON}|${WEB_USER_ID}`, `row=${iState}`)
  check('I145-12 判无效成功有提示且抽屉关闭', /已判为无效/.test(toastB) && (await drawerOpen()) === false, `toasts="${toastB}" notes="${noteB}"`)
  check('I145-13 判无效路径无未捕获 rejection', pageErrors.length === 0, pageErrors.join(' || '))

  // ─────── ③ 负对照：编号撞号 → 有 toast、抽屉不关、库里不变 ──────────────────
  await page.goto(`${WEB}/embed`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.el-table__body tr', { timeout: 25000 })
  await sleep(2200)
  net.length = 0
  pageErrors.length = 0
  await resetToasts()
  await openTopVerifyDrawer()
  await fillBlockNo(COLLIDE)
  await sleep(400)
  await drawer().locator('.el-drawer__footer button', { hasText: /判为有效/ }).first().click()
  await sleep(900)
  // 同一句话弹了几条？（全局拦截器对 code=500 也会 ElMessage 一次 —— 记条数，别猜）
  const msgNodes = await page.evaluate(() => document.querySelectorAll('.el-message').length)
  await sleep(2600)

  const vc3 = verifyCalls()
  const cid = vc3.length ? vc3[0][1] : 'none'
  const cState = cid === 'none' ? '__NO_VERIFY__' : dbOne(`SELECT verify_status||'|'||COALESCE(paraffin_block_no,'-') FROM t_lqg_embed WHERE id=${cid}`)
  const toastC = await toasts()
  const noteC = await notes()
  const cOpen = await drawerOpen()
  await page.screenshot({ path: path.join(S, 'c1-collide-negative.png'), fullPage: true })

  check('I145-14 负对照：撞号的 verify 请求确实发出且被后端拒（非 200）',
    vc3.length === 1 && net.some(l => /^RESP .* \/lqg\/embed\/[^/]+\/verify :: \{"code":(?!200)/.test(l)),
    JSON.stringify(net))
  check('I145-15 负对照：库内该行仍是 pending|-（什么都没变）', cState === 'pending|-', `row=${cState}`)
  check('I145-16 负对照：失败必 toast（el-message 非空且是后端那句 msg）',
    toastC.length > 0 && /石蜡块编号/.test(toastC), `toasts="${toastC}" msgNodes=${msgNodes} notes="${noteC}"`)
  check('I145-17 负对照：失败不关抽屉（留着让人改编号）', cOpen === true, `drawerOpen=${cOpen}`)
  check('I145-18 负对照：失败也没有未捕获 rejection', pageErrors.length === 0, pageErrors.join(' || '))

  // 判无效失败路径也补一刀：外部的待核验行已被前面用掉两条，这里直接对「已判无效」的行再判有效
  // 会被转移表拒（invalid→valid 只允许内部人员 —— staff 是内部，所以这条会过），略过；转移表的拒绝
  // 已由后端契约测试覆盖，本探针只关心「失败必 toast + 不关抽屉」这一形态（上面第 14-18 条）。
} catch (e) {
  check('I145-EXCEPTION', false, String((e && e.stack) || e).slice(0, 900))
} finally {
  await browser.close()
  process.exit(finish() === 0 ? 0 : 1)
}
