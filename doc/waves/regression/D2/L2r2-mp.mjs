/**
 * D2 / r2 / L2 —— 小程序 H5 等价路径（组 1 外部体验路径 + 组 3 内部小程序）· 独立复验。
 *
 * 本片自写（不复用 L2-mp-h5.mjs 的断言）。与 r1 脚本的三个实质差别：
 *   ① 主线**从首页入口点进去**跑完（不再另开 `?mode=new` 抄近路）——r1 的 S0-1 就是这么被抄近路掩盖的；
 *   ② 内部类器官收样**真的操作「来源单位」**（底部弹层选单位）——r1 harness 漏了这一步；
 *   ③ 五条 r1 拦门项逐条正面复验（入口可提交 / 徽标 / 只读有值 / 按钮组可点 / 收样日期可弹 + 字典下拉）。
 *
 * 微信开发者工具在本沙箱跑不通（写 ~/Library/Application Support/微信开发者工具/** EPERM + 需扫码），
 * 故一律用 miniapp H5 dev(9200, VITE_MOCK_LOGIN=1) + Playwright 等价覆盖；**开发者工具 / 真机未覆盖**。
 * extB / extC 不在调试面板的 3 个 seed 里 → 走后端 mock 登录（ADR-0008）拿 token 注入 localStorage，
 * 仍是产品自己的 mock 路径，只绕开面板按钮。
 *
 * 前置：后端 8081（dev + --api-decrypt.enabled=false）+ reseed + miniapp 9200 + plus-ui 8082。
 * 跑法：node doc/waves/regression/D2/L2r2-mp.mjs
 */
import { createRequire } from 'node:module'
import { mkdirSync, readdirSync, readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const MP = process.env.LQG_H5_BASE || 'http://127.0.0.1:9200'
const WEB = process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082'
const SHOTS = path.join(HERE, 'shots/L2r2-mp')
mkdirSync(SHOTS, { recursive: true })

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}
/** 一段一步：异常不拖垮整条脚本 */
async function step(name, fn) {
  try { await fn() } catch (e) { check(name, false, '异常: ' + (e && e.message ? e.message : String(e))) }
}

function dbOne(sql) {
  try {
    return execFileSync('psql', ['-h', '127.0.0.1', '-p', '5433', '-U', 'lqg', '-d', 'lqg_dev', '-Atc', sql],
      { env: { ...process.env, PGPASSWORD: 'lqg_dev_pwd' }, encoding: 'utf8' }).trim()
  } catch (e) { return '__ERR__' + e.message.slice(0, 60) }
}
function mockToken(as) {
  execFileSync('bash', ['doc/verify/api.sh', '--as', as, 'GET', '/mp/me'], { cwd: WS, stdio: 'ignore' })
  const dir = process.env.TMPDIR || '/tmp'
  const f = readdirSync(dir).filter(x => x.startsWith(`lqg-verify-token-${as}-`)).sort().pop()
  return readFileSync(path.join(dir, f), 'utf8').split('\n')[0]
}
/** 官方模板第 1 行（权威原件），用于表格页表头逐字逐序对账 */
function templateHeader(file) {
  const out = execFileSync('python3', ['doc/verify/xlsx_header.py', '--print-header', '--template', `_input/templates/${file}`],
    { cwd: WS, encoding: 'utf8' })
  return out.split('\n').map(s => s.trim()).filter(Boolean)
}

const browser = await chromium.launch({ headless: true })
const ctxMp = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await ctxMp.newPage()
const ctxWeb = await browser.newContext({ viewport: { width: 1600, height: 950 } })
const web = await ctxWeb.newPage()
const pageErrors = []
const consoleErrs = []
page.on('pageerror', e => { pageErrors.push('[mp] ' + e.message); console.log('[pageerror mp]', e.message) })
page.on('console', m => { if (m.type() === 'error') { consoleErrs.push(m.text()); if (!/favicon/.test(m.text())) console.log('[console.error]', m.text().slice(0, 140)) } })
web.on('pageerror', e => { pageErrors.push('[web] ' + e.message) })

const bodyText = (p = page) => p.evaluate(() => document.body.innerText)
const homeText = () => page.$eval('.home', e => e.innerText).catch(() => '')
const digitsOutside80 = t => (t.replace(/-80/g, '').match(/\d/g) || [])

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
async function tokenLogin(as) {
  const token = mockToken(as)
  await page.goto(`${MP}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(t => { localStorage.clear(); localStorage.setItem('lqg_mp_token', t) }, token)
  await page.goto(`${MP}/#/pages/index/index`, { waitUntil: 'domcontentloaded' })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.lqg-tile', { timeout: 25000 })
  await page.waitForTimeout(900)
}
async function gotoMe() {
  await page.goto(`${MP}/#/pages/index/index`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.lqg-tile', { timeout: 25000 })
  await page.click('.uni-tabbar__item:has-text("我的")')
  await page.waitForSelector('.me__logout', { timeout: 25000 })
  await page.waitForTimeout(700)
}
async function gotoHistory() {
  await page.goto(`${MP}/#/pages/history/index`, { waitUntil: 'domcontentloaded' })
  await page.reload({ waitUntil: 'domcontentloaded' }) // hash 路由下 goto 不重载
  await page.waitForSelector('.his__switch', { timeout: 25000 })
  await page.waitForTimeout(1800)
}
async function mpField(label) {
  const loc = page.locator('.wd-input, .wd-cell, .wd-textarea')
  const n = await loc.count()
  for (let i = 0; i < n; i++) {
    const t = (await loc.nth(i).locator('.wd-input__label-inner, .wd-cell__title, .wd-textarea__label-inner').first().innerText().catch(() => ''))
      .replace('*', '').trim()
    if (t === label) return loc.nth(i)
  }
  return null
}
async function mpFill(label, val) {
  const r = await mpField(label)
  if (!r) throw new Error(`找不到字段 ${label}`)
  const inp = r.locator('input, textarea')
  if ((await inp.count()) === 0) throw new Error(`字段 ${label} 不是可输入的`)
  await inp.first().fill(val)
}
const mpBtn = t => page.locator(`uni-button:has-text("${t}")`)
const mpToast = () => page.$$eval('.uni-toast,[class*="toast"]', els => els.map(e => e.innerText).join('|')).catch(() => '')

async function webLogin() {
  await web.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await web.evaluate(() => localStorage.clear()).catch(() => {})
  await web.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await web.waitForSelector('.login-form input', { timeout: 30000 })
  const i = web.locator('.login-form input')
  await i.nth(0).fill('lqgadmin')
  await i.nth(1).fill('admin123')
  await web.click('.login-form button')
  await web.waitForSelector('.el-menu', { timeout: 30000 })
  await web.waitForTimeout(2000)
}
async function webOpenSample() {
  await web.goto(`${WEB}/sample`, { waitUntil: 'domcontentloaded' })
  await web.waitForSelector('.el-table__body tr', { timeout: 30000 })
  await web.waitForTimeout(1500)
}
const webRow = no => web.locator('.el-table__body tr').filter({ hasText: no }).first()
const drawer = () => web.locator('.el-drawer:visible')
async function drawerField(label) {
  const items = drawer().locator('.el-form-item')
  const n = await items.count()
  for (let i = 0; i < n; i++) {
    const t = (await items.nth(i).locator('.el-form-item__label').first().innerText().catch(() => '')).trim()
    if (t === label) return items.nth(i)
  }
  return null
}
async function verifyInvalid(submitNo, reason) {
  await webOpenSample()
  await webRow(submitNo).locator('button:has-text("核验")').click()
  await web.waitForSelector('.el-drawer:visible', { timeout: 20000 })
  await web.waitForTimeout(1000)
  await drawer().locator('button:has-text("判为无效")').first().click()
  await web.waitForTimeout(800)
  const dlg = web.locator('.el-dialog:visible').first()
  await dlg.locator('textarea, input').first().fill(reason)
  const [r] = await Promise.all([
    web.waitForResponse(x => /\/verify$/.test(x.url()), { timeout: 25000 }).catch(() => null),
    dlg.locator('button:has-text("判为无效")').click(),
  ])
  await web.waitForTimeout(2000)
  return r ? (await r.json().catch(() => ({}))).code : null
}
async function verifyValid(submitNo, internalNo) {
  await webOpenSample()
  await webRow(submitNo).locator('button:has-text("核验")').click()
  await web.waitForSelector('.el-drawer:visible', { timeout: 20000 })
  await web.waitForTimeout(1000)
  await (await drawerField('内部编号')).locator('input').first().fill(internalNo)
  const dr = await drawerField('收样日期')
  await dr.locator('input').first().fill(new Date().toISOString().slice(0, 10))
  await web.keyboard.press('Enter')
  await web.waitForTimeout(500)
  const [r] = await Promise.all([
    web.waitForResponse(x => /\/verify$/.test(x.url()), { timeout: 25000 }).catch(() => null),
    drawer().locator('button:has-text("判为有效并保存")').click(),
  ])
  await web.waitForTimeout(2000)
  return r ? (await r.json().catch(() => ({}))).code : null
}

let NEW_ID = null, NEW_NO = null
const invReason = 'R2M 独立复验：缺住院号，请补'

try {
  // ══════════ 组 1 · 外部（extA）体验路径 ══════════════════════════════════
  await resetToLogin()
  await mockLogin('外部人员 · 王医生（已核验）')
  await page.screenshot({ path: path.join(SHOTS, 'g1-01-home-extA.png'), fullPage: true })
  const tiles = await page.$$eval('.lqg-tile .lqg-tile__t', els => els.map(e => e.textContent.trim()))
  check('R2M-01 extA 首页三格、顺序 = 样本记录信息表 / 类器官收样记录 / 石蜡包埋送样记录',
    JSON.stringify(tiles) === JSON.stringify(['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录']), JSON.stringify(tiles))
  const hDigits = digitsOutside80(await homeText())
  check('R2M-02 extA 首页没有任何数字（-80 不算；外部分页也没有 -80 格）', hDigits.length === 0, `digits=${JSON.stringify(hDigits)}`)

  // ── ①【r1-S0-1 复验】首页点「样本记录信息表」→ 新增填写页，并**从这一页**提交 ──
  await page.click('.lqg-tile:has-text("样本记录信息表")')
  await page.waitForTimeout(2500)
  const entryUrl = page.url()
  const entryText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g1-02-entry-sample.png'), fullPage: true })
  const entryOk = /pages\/sample\/form/.test(entryUrl) && /mode=new/.test(entryUrl)
    && (await mpBtn('提交').count()) > 0 && !/没能加载/.test(entryText)
  check('R2M-03【r1-S0-1 复验】首页「样本记录信息表」→ 新增填写页：URL 带 mode=new + 有「提交」+ 不是错误态',
    entryOk, `url=${entryUrl} hasSubmit=${await mpBtn('提交').count()} text=${JSON.stringify(entryText.replace(/\n/g, '|').slice(0, 100))}`)

  const DONOR = 'R2复验供体甲'
  await mpFill('供体姓名', DONOR)
  await mpFill('组织类型', '肝组织')
  await mpFill('备注', 'D2 r2 L2 独立复验')
  await page.screenshot({ path: path.join(SHOTS, 'g1-03-new-filled.png'), fullPage: true })
  await page.click('uni-button:has-text("提交")')
  await page.waitForTimeout(3500)
  await page.screenshot({ path: path.join(SHOTS, 'g1-04-submitted.png'), fullPage: true })
  const NEW = dbOne("SELECT id || '|' || submit_no || '|' || COALESCE(verify_status,'-') || '|' || COALESCE(submit_source,'-') FROM t_lqg_sample WHERE submitter_id=9000000111 AND del_flag='0' AND create_time > now() - interval '10 minutes' ORDER BY id DESC LIMIT 1")
  ;[NEW_ID, NEW_NO] = (NEW || '').split('|')
  check('R2M-04【r1-S0-1 复验】从首页入口直接提交成功（库内新行 = external / pending）',
    /^SJ\d+$/.test(NEW_NO || '') && NEW.endsWith('|pending|external'),
    `new=${NEW} toast=${JSON.stringify(await mpToast())}`)

  // ── ② 我的 → 历史编辑记录出现待核验 + 每行有状态徽标（r1-S0-2 正面复验）──
  await gotoMe()
  await page.click('.merow:has-text("历史编辑记录")')
  await page.waitForSelector('.his__item', { timeout: 25000 })
  await page.waitForTimeout(1500)
  await page.screenshot({ path: path.join(SHOTS, 'g1-05-history-pending.png'), fullPage: true })
  const hist = await bodyText()
  check('R2M-05 extA 历史编辑记录里新记录出现且状态 = 待核验', hist.includes(NEW_NO || '@@') && /待核验/.test(hist),
    JSON.stringify(hist.replace(/\n/g, '|').slice(0, 180)))
  // 【r1-S0-2 正面复验】每行都有核验状态徽标（DOM 里 .lqg-tag 至少一行一个，且徽标有文案）
  const chipInfo = await page.evaluate(() => {
    const items = [...document.querySelectorAll('.his__item')]
    return {
      items: items.length,
      chips: document.querySelectorAll('.his__item .lqg-tag').length,
      texts: [...document.querySelectorAll('.his__item .lqg-tag')].map(e => e.innerText.trim()).filter(Boolean),
    }
  })
  check('R2M-06【r1-S0-2 复验】历史编辑记录每行都有核验状态徽标（.lqg-tag 数 ≥ 行数且有文案）',
    chipInfo.items > 0 && chipInfo.chips >= chipInfo.items && chipInfo.texts.length >= chipInfo.items, JSON.stringify(chipInfo))
  const chipErr = consoleErrs.filter(e => /StatusChip|props is not defined|ReferenceError/.test(e))
  check('R2M-07【r1-S0-2 复验】渲染期没有 props/ReferenceError 类前端异常', chipErr.length === 0, JSON.stringify(chipErr.slice(0, 2)))

  // ── ③ 工作台判无效 → 外部在历史行内看到原因（r1-S1-2 复验）→ 改后重提 ──
  await webLogin()
  const codeInv = await verifyInvalid(NEW_NO, invReason)
  check('R2M-08 工作台把 extA 这条判为无效（code=200 + 库内 invalid）',
    codeInv === 200 && dbOne(`SELECT verify_status FROM t_lqg_sample WHERE id=${NEW_ID}`) === 'invalid',
    `code=${codeInv} db=${dbOne(`SELECT verify_status || '|' || COALESCE(invalid_reason,'-') FROM t_lqg_sample WHERE id=${NEW_ID}`)}`)
  await web.screenshot({ path: path.join(SHOTS, 'g1-06-web-invalid.png'), fullPage: true })

  await gotoHistory()
  const histInvalid = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g1-07-history-invalid.png'), fullPage: true })
  const rowNew = page.locator('.his__item').filter({ hasText: NEW_NO }).first()
  const listShowsReason = histInvalid.includes(invReason)
  await rowNew.click()
  await page.waitForTimeout(2500)
  const editText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g1-08-history-open-invalid.png'), fullPage: true })
  check('R2M-09【r1-S1-2 复验】外部在历史编辑记录**行内**看到无效原因',
    listShowsReason, `list=${JSON.stringify(histInvalid.replace(/\n/g, '|').slice(0, 200))}`)
  check('R2M-10【r1-S1-2 复验】点开这一条是改后重提的 edit 表单，且表单顶部红条也带原因',
    /pages\/sample\/form/.test(page.url()) && /mode=edit/.test(page.url()) && editText.includes(invReason) && (await mpBtn('保存').count()) > 0,
    `url=${page.url()} text=${JSON.stringify(editText.replace(/\n/g, '|').slice(0, 200))}`)

  await mpFill('备注', 'D2 r2 已补住院号后重提')
  await page.click('uni-button:has-text("保存")')
  await page.waitForTimeout(3500)
  await page.screenshot({ path: path.join(SHOTS, 'g1-09-resubmitted.png'), fullPage: true })
  check('R2M-11 外部改后重提成功（库内备注更新 + 状态回到 pending + 原因清掉）',
    dbOne(`SELECT remark FROM t_lqg_sample WHERE id=${NEW_ID}`) === 'D2 r2 已补住院号后重提'
    && dbOne(`SELECT verify_status || '|' || COALESCE(invalid_reason,'-') FROM t_lqg_sample WHERE id=${NEW_ID}`) === 'pending|-',
    `db=${dbOne(`SELECT remark || '|' || verify_status || '|' || COALESCE(invalid_reason,'-') FROM t_lqg_sample WHERE id=${NEW_ID}`)}`)

  const codeVal = await verifyValid(NEW_NO, 'T-r2m01')
  await web.screenshot({ path: path.join(SHOTS, 'g1-10-web-valid.png'), fullPage: true })
  check('R2M-12 工作台再判为有效（code=200 + 库内 valid|T-r2m01）',
    codeVal === 200 && dbOne(`SELECT verify_status || '|' || COALESCE(internal_no,'-') FROM t_lqg_sample WHERE id=${NEW_ID}`) === 'valid|T-r2m01',
    `code=${codeVal} db=${dbOne(`SELECT verify_status || '|' || COALESCE(internal_no,'-') FROM t_lqg_sample WHERE id=${NEW_ID}`)}`)

  await gotoHistory()
  await page.locator('.his__item').filter({ hasText: NEW_NO }).first().click()
  await page.waitForTimeout(2500)
  const roText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g1-11-ext-readonly.png'), fullPage: true })
  const roInputs = await page.locator('.wd-input input, .wd-textarea textarea').count()
  check('R2M-13 判有效后外部再看这条是只读（无输入控件、无提交/保存按钮）',
    roInputs === 0 && (await mpBtn('提交').count()) === 0 && (await mpBtn('保存').count()) === 0,
    `url=${page.url()} inputs=${roInputs} text=${JSON.stringify(roText.replace(/\n/g, '|').slice(0, 140))}`)
  // 【r1-S0-2 正面复验】只读分支必须**渲染出字段值**（不是一片空白）。
  // 外部只读详情走的是专用页 pages/sample/detail-ext（不是 FieldRow 表格），正文里逐项列出值。
  const roPairs = ['送检单号', NEW_NO, '供体姓名', '组织类型', '肝组织', '来源单位'].filter(t => roText.includes(t))
  check('R2M-13b【r1-S0-2 复验】外部只读详情页把字段值渲染出来了（送检单号/供体姓名/组织类型/来源单位 均在正文）',
    roPairs.length >= 6, `hit=${JSON.stringify(roPairs)} text=${JSON.stringify(roText.replace(/\n/g, '|').slice(0, 200))}`)

  // ── ④ extB（同组）：看得到 + 只看我提交的 → 看不到 ───────────────────────
  await tokenLogin('extB')
  await gotoHistory()
  const extBHist = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g1-12-extB-history.png'), fullPage: true })
  check('R2M-14 extB（同组同单位）历史编辑记录里看得到 extA 这条', extBHist.includes(NEW_NO || '@@'),
    JSON.stringify(extBHist.replace(/\n/g, '|').slice(0, 200)))
  await page.click('.his__switch .wd-switch')
  await page.waitForTimeout(2500)
  const extBOnly = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g1-13-extB-onlymine.png'), fullPage: true })
  check('R2M-15 extB 打开「只看我提交的」后看不到 extA 这条', !extBOnly.includes(NEW_NO || '@@'),
    JSON.stringify(extBOnly.replace(/\n/g, '|').slice(0, 200)))

  // ══════════ 组 3 · 内部（staff）小程序 ══════════════════════════════════
  await resetToLogin()
  await mockLogin('内部人员 · 李工')
  await page.screenshot({ path: path.join(SHOTS, 'g3-01-home-staff.png'), fullPage: true })
  const intTiles = await page.$$eval('.lqg-tile .lqg-tile__t', els => els.map(e => e.textContent.trim()))
  check('R2M-20 staff 首页四格、顺序 = 样本记录信息表 / 类器官收样记录 / 石蜡包埋送样记录 / -80 冻存记录',
    JSON.stringify(intTiles) === JSON.stringify(['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录', '-80 冻存记录']), JSON.stringify(intTiles))

  // ①【r1-S0-1 复验】首页四格点「样本记录信息表」→ 直接进填写页新增
  await page.click('.lqg-tile:has-text("样本记录信息表")')
  await page.waitForTimeout(2500)
  const intEntryUrl = page.url()
  const intEntryText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g3-02-entry-sample.png'), fullPage: true })
  check('R2M-21【r1-S0-1 复验】staff 首页点「样本记录信息表」→ 新增填写页（mode=new + 提交 + 非错误态）',
    /pages\/sample\/form/.test(intEntryUrl) && /mode=new/.test(intEntryUrl) && (await mpBtn('提交').count()) > 0 && !/没能加载/.test(intEntryText),
    `url=${intEntryUrl} text=${JSON.stringify(intEntryText.replace(/\n/g, '|').slice(0, 100))}`)

  // ② 我的 → 内部管理 四张只读表入口
  await gotoMe()
  const meText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g3-03-me.png'), fullPage: true })
  check('R2M-22 staff「我的」有「内部管理」板块 + 四张表入口 + 历史编辑记录',
    /内部管理/.test(meText) && /历史编辑记录/.test(meText)
    && ['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录', '-80 冻存记录'].every(t => meText.includes(t)),
    JSON.stringify(meText.replace(/\n/g, '|').slice(0, 220)))

  // ③ 表格页两个工作表：列名/列序 = 甲方模板第 1 行（冻结列提前 + 切片染色追加）
  await page.goto(`${MP}/#/pages/ledger/index`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.lqg-ledger__th', { timeout: 25000 })
  await page.waitForTimeout(1800)
  await page.screenshot({ path: path.join(SHOTS, 'g3-04-ledger-tissue.png'), fullPage: true })
  const tabs = await page.$$eval('.lqg-sheets__item', els => els.map(e => e.innerText.trim()))
  check('R2M-23 表格页工作表切换条 = 样本记录 / 类器官收样', JSON.stringify(tabs) === JSON.stringify(['样本记录', '类器官收样']), JSON.stringify(tabs))
  const tplSample = templateHeader('样本记录信息表模板.xlsx')            // 14 列，含内部编号
  const expSample = ['内部编号', ...tplSample.filter(c => c !== '内部编号'), '切片染色']
  const headSample = await page.$$eval('.lqg-ledger__th', els => els.map(e => e.innerText.trim()))
  check('R2M-24 样本记录表头 == 甲方模板第 1 行（冻结列「内部编号」提前 + 末尾追加「切片染色」）',
    JSON.stringify(headSample) === JSON.stringify(expSample), `ui=${JSON.stringify(headSample)} expect=${JSON.stringify(expSample)}`)
  await page.locator('.lqg-sheets__item:has-text("类器官收样")').click()
  await page.waitForTimeout(2000)
  await page.screenshot({ path: path.join(SHOTS, 'g3-05-ledger-organoid.png'), fullPage: true })
  const tplOrg = templateHeader('类器官收样记录模板.xlsx')
  const expOrg = ['内部编号', ...tplOrg.filter(c => c !== '内部编号'), '切片染色']
  const headOrg = await page.$$eval('.lqg-ledger__th', els => els.map(e => e.innerText.trim()))
  check('R2M-25 类器官收样表头 == 甲方模板第 1 行（冻结列提前 + 追加「切片染色」）',
    JSON.stringify(headOrg) === JSON.stringify(expOrg), `ui=${JSON.stringify(headOrg)} expect=${JSON.stringify(expOrg)}`)

  // ④ 首列冻结 + 横滑
  const fz = await page.$eval('.lqg-ledger__fz', e => { const s = getComputedStyle(e); return { pos: s.position, left: s.left } })
  check('R2M-26 首列冻结：position=sticky / left=0px（计算样式）', fz.pos === 'sticky' && fz.left === '0px', JSON.stringify(fz))
  const sc = await page.evaluate(() => {
    const root = document.querySelector('.lqg-ledger')
    for (const e of [root, ...root.querySelectorAll('*')]) {
      const s = getComputedStyle(e)
      if (['auto', 'scroll'].includes(s.overflowX) && e.scrollWidth > e.clientWidth + 5) {
        return { tag: e.tagName + '.' + e.className, ow: s.overflowX, w: e.scrollWidth, cw: e.clientWidth }
      }
    }
    return { none: true }
  }).catch(() => null)
  check('R2M-27 表格可左右滑动看全部列（存在 overflow-x 可滚且 scrollWidth > clientWidth）', !!sc && !sc.none && sc.w > sc.cw, JSON.stringify(sc))

  // ⑤ 页面上没有「新增 / 保存」；点一行只读**且有字段值**（r1-S0-2 正面复验）
  const btns = await page.$$eval('uni-button, button', els => els.map(e => e.innerText.trim()).filter(t => t && !t.includes('导出 Excel')))
  const barText = await bodyText()
  check('R2M-28 表格页没有「新增」「保存」（底部只有置灰的导出 Excel）',
    !btns.some(t => /新增|保存/.test(t)) && /导出 Excel/.test(barText), `buttons=${JSON.stringify(btns)}`)
  await page.locator('.lqg-sheets__item:has-text("样本记录")').click()
  await page.waitForTimeout(1500)
  const firstRowCode = (await page.locator('.ledger__row').first().innerText().catch(() => '')).replace(/\n/g, '|')
  await page.locator('.ledger__row').first().click()
  await page.waitForTimeout(2500)
  await page.screenshot({ path: path.join(SHOTS, 'g3-06-ledger-row-readonly.png'), fullPage: true })
  const roLedgerText = await bodyText()
  const roLedgerInputs = await page.locator('input, textarea').count()
  check('R2M-29【r1-S0-2 复验】表格页点一行 → 只读（mode=view、无输入、无提交/保存）',
    /mode=view/.test(page.url()) && roLedgerInputs === 0 && (await mpBtn('提交').count()) === 0 && (await mpBtn('保存').count()) === 0,
    `url=${page.url()} inputs=${roLedgerInputs} firstRow=${JSON.stringify(firstRowCode).slice(0, 80)}`)
  // 只读表单必须有**字段值**（不是空白 FieldRow）
  const valProbe = await page.evaluate(() => {
    const rows = [...document.querySelectorAll('.wd-cell, .wd-input')]
    const out = []
    for (const r of rows) {
      const label = (r.querySelector('.wd-input__label-inner, .wd-cell__title')?.innerText || '').replace('*', '').trim()
      const val = (r.querySelector('.fd__val, .wd-cell__value, .lqg-fd__val')?.innerText || r.innerText || '').replace(/\n+/g, ' ').trim()
      if (label) out.push(label + '=' + val.replace(label, '').trim().slice(0, 24))
    }
    return out.filter(x => !/=$/.test(x)).slice(0, 12)
  }).catch(() => [])
  check('R2M-30【r1-S0-2 复验】只读表单里有字段值（至少 3 个 label 渲染出非空值）',
    valProbe.length >= 3, JSON.stringify(valProbe))
  // 只读页的按钮组仍要能看出「选中的是哪一个」（SegButtons disabled 时只挡点击，不挡 --on）。
  // ★ 口径修正：只对**库内有值**的按钮组要求「恰好 1 项选中」；库内为空的行（如外部新建、没填有无）
  //   本来就不该有选中项（R2MX-05 用库内值非空的行做严格对账）。
  const viewId = (page.url().match(/id=(\d+)/) || [])[1]
  const roSeg = await page.evaluate(() => {
    const out = {}
    for (const seg of document.querySelectorAll('.lqg-seg')) {
      const label = (seg.closest('.wd-cell, .wd-input')?.querySelector('.wd-input__label-inner, .wd-cell__title')?.innerText || '').replace('*', '').trim()
      out[label] = seg.querySelectorAll('.lqg-seg__item--on').length
    }
    return out
  }).catch(() => ({}))
  const dbVals = (viewId ? dbOne(`SELECT gender || '|' || COALESCE(is_fixed,'') || '|' || COALESCE(has_qc_sheet,'') || '|' || COALESCE(has_viability_report,'') FROM t_lqg_sample WHERE id=${viewId}`) : '').split('|')
  const wantOn = { 性别: dbVals[0], 有无固定: dbVals[1], 质控表: dbVals[2], 细胞活率报告: dbVals[3] }
  const segBad = Object.entries(wantOn).filter(([k, v]) => v && roSeg[k] !== 1).map(([k, v]) => `${k}:db=${v}:on=${roSeg[k]}`)
  check('R2M-30b【r1-S0-2 复验】只读页按钮组：库内有值的组各恰好 1 项选中（空值组允许 0）',
    Object.keys(roSeg).length > 0 && segBad.length === 0, `id=${viewId} db=${JSON.stringify(wantOn)} segs=${JSON.stringify(roSeg)} bad=${JSON.stringify(segBad)}`)

  // ⑥ 内部类器官收样：7 项 + **操作来源单位** + 收样日期 open() + 提交成功（r1-S0-3 + harness 漏操作复验）
  await page.goto(`${MP}/#/pages/organoid/form?mode=new`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.wd-input, .wd-cell', { timeout: 25000 })
  await page.waitForTimeout(2000)
  const orgText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g3-07-organoid-internal.png'), fullPage: true })
  const ORG7 = ['来源单位', '类器官类型', '收样日期', '内部编号', '处理时间', '细胞活率报告', '操作人']
  check('R2M-31 内部类器官收样填写页 7 项齐（含收样段与送检段）',
    ORG7.every(t => orgText.includes(t)) && /送检信息/.test(orgText) && /收样信息/.test(orgText),
    JSON.stringify(orgText.replace(/\n/g, '|').slice(0, 200)))

  // 收样日期：点开必须有弹层（open() 生效）
  const rdRow = await mpField('收样日期')
  await rdRow.click()
  await page.waitForTimeout(1500)
  const picker = {
    action: await page.locator('.wd-datetime-picker__action').count(),
    visible: await page.locator('.wd-datetime-picker__popup').first().isVisible().catch(() => false),
  }
  await page.screenshot({ path: path.join(SHOTS, 'g3-08-organoid-datepicker.png'), fullPage: true })
  check('R2M-32【r1-S0-3 复验】点「收样日期」弹出日期选择器（有确定/取消动作条且可见）',
    picker.action >= 2 && picker.visible === true, JSON.stringify(picker))
  if (picker.action >= 2) { await page.locator('.wd-datetime-picker__action').last().click(); await page.waitForTimeout(800) }

  // 来源单位（必填）：点开底部弹层 → 选一个单位（r1 harness 就是漏了这一步）
  let unitPicked = null
  const srcRow = await mpField('来源单位')
  if (srcRow) {
    await srcRow.click()
    await page.waitForTimeout(1500)
    const chips = await page.locator('.org__unit').allInnerTexts().catch(() => [])
    await page.screenshot({ path: path.join(SHOTS, 'g3-09-organoid-unitsheet.png'), fullPage: true })
    const unit = page.locator('.org__unit').filter({ hasNotText: '手动填写' }).first()
    if (await unit.count() > 0) { await unit.click(); unitPicked = (await srcRow.innerText().catch(() => '')).replace(/\n/g, ' ').trim() }
    await page.waitForTimeout(600)
  }
  check('R2M-33 内部类器官收样「来源单位」有底部弹层且能选中一个单位（必填项真被填上）',
    !!srcRow && (unitPicked || '').length > 0, `chips=${JSON.stringify((await page.locator('.org__unit').allInnerTexts().catch(() => [])).slice(0, 5))} field=${JSON.stringify(unitPicked)}`)

  await mpFill('类器官类型', 'R2复验类器官')
  await mpFill('内部编号', 'T-r2org1')
  await page.click('uni-button:has-text("提交")')
  await page.waitForTimeout(3500)
  await page.screenshot({ path: path.join(SHOTS, 'g3-10-organoid-submit.png'), fullPage: true })
  const orgRow = dbOne("SELECT COALESCE(id::text,'-') || '|' || COALESCE(submit_source,'-') || '|' || COALESCE(sample_kind,'-') || '|' || COALESCE(verify_status,'-') FROM t_lqg_sample WHERE internal_no='T-r2org1'")
  check('R2M-34【r1-S0-3 + harness 漏操作 复验】内部类器官收样提交成功（库内出现 T-r2org1 = internal|organoid|valid）',
    /^\d+\|internal\|organoid\|valid$/.test(orgRow), `db=${orgRow} toast=${JSON.stringify(await mpToast())}`)

  // ⑦ 内部历史编辑记录：点本人录的有效样本 → 改一字段保存成功 → 排最前；顺带验按钮组可点（r1-S0-2）
  await gotoHistory()
  await page.screenshot({ path: path.join(SHOTS, 'g3-11-history-staff.png'), fullPage: true })
  const beforeRemark = dbOne("SELECT remark FROM t_lqg_sample WHERE id=9000001008")
  const ownRow = page.locator('.his__item').filter({ hasText: 'T-hli05' }).first()
  await ownRow.click()
  await page.waitForTimeout(2500)
  await page.screenshot({ path: path.join(SHOTS, 'g3-12-edit-own-valid.png'), fullPage: true })
  const editOwnText = await bodyText()
  const ownEditOk = /pages\/sample\/form/.test(page.url()) && /收样信息/.test(editOwnText) && (await mpBtn('保存').count()) > 0
  check('R2M-35 内部历史编辑记录点本人有效样本 → 进 edit 可写（收样段 + 保存按钮）', ownEditOk,
    `url=${page.url()} text=${JSON.stringify(editOwnText.replace(/\n/g, '|').slice(0, 140))}`)

  // 按钮组可点：性别 / 有无固定 / 质控表 / 细胞活率报告（`--on` 会切换）
  // ★ 点击后**重新定位**该选项（组件重渲染会换节点），并多等一会儿再读 class。
  const segProbe = []
  for (const [label, opt] of [['性别', '女'], ['有无固定', '有'], ['质控表', '有'], ['细胞活率报告', '无']]) {
    const r = await mpField(label)
    if (!r) { segProbe.push(`${label}:NOFIELD`); continue }
    const item = r.locator('.lqg-seg__item').filter({ hasText: opt }).first()
    if ((await item.count()) === 0) { segProbe.push(`${label}:NOOPT`); continue }
    const before = ((await item.getAttribute('class')) || '').includes('--on') ? 'ON' : 'off'
    await item.click()
    await page.waitForTimeout(900)
    const after = ((await r.locator('.lqg-seg__item').filter({ hasText: opt }).first().getAttribute('class')) || '')
    const onCount = await r.locator('.lqg-seg__item--on').count()
    segProbe.push(`${label}:${before}->${onCount === 1 && after.includes('--on') ? 'ON' : 'NO-ON'}`)
  }
  await page.screenshot({ path: path.join(SHOTS, 'g3-13-seg-probe.png'), fullPage: true })
  check('R2M-36【r1-S0-2 复验】性别 / 有无固定 / 质控表 / 细胞活率报告 按钮组点得动（点击后被点项带 --on 且同组恰有 1 个选中）',
    segProbe.every(x => x.endsWith('ON')), JSON.stringify(segProbe))

  if (ownEditOk) {
    await mpFill('备注', 'D2 r2 内部改一笔')
    await page.click('uni-button:has-text("保存")')
    await page.waitForTimeout(3200)
    await page.screenshot({ path: path.join(SHOTS, 'g3-14-own-saved.png'), fullPage: true })
    check('R2M-37 内部改一个字段保存成功（库内 remark 变 + update_by 有值）',
      dbOne('SELECT remark FROM t_lqg_sample WHERE id=9000001008') === 'D2 r2 内部改一笔'
      && dbOne("SELECT COALESCE(update_by::text,'-') FROM t_lqg_sample WHERE id=9000001008") !== '-',
      `before=${beforeRemark} now=${dbOne('SELECT remark FROM t_lqg_sample WHERE id=9000001008')} update_by=${dbOne("SELECT COALESCE(update_by::text,'-') FROM t_lqg_sample WHERE id=9000001008")}`)
    await gotoHistory()
    const firstCode = await page.locator('.his__item').first().innerText().catch(() => '')
    await page.screenshot({ path: path.join(SHOTS, 'g3-15-history-after-edit.png'), fullPage: true })
    check('R2M-38 改过的那条排到历史编辑记录最前', /T-hli05/.test(firstCode), JSON.stringify(firstCode.replace(/\n/g, '|').slice(0, 120)))
  }

  // ⑧ extC 外部类器官收样三项 → 待核验
  await tokenLogin('extC')
  await page.goto(`${MP}/#/pages/organoid/form?mode=new`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.wd-input, .wd-cell', { timeout: 25000 })
  await page.waitForTimeout(2000)
  const extOrgText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g3-16-organoid-extC.png'), fullPage: true })
  check('R2M-39 extC 类器官收样页正好三项（来源单位 / 类器官类型 / 备注），没有内部字段',
    ['来源单位', '类器官类型', '备注'].every(t => extOrgText.includes(t))
    && !/内部编号|收样日期|处理时间|细胞活率报告|操作人/.test(extOrgText),
    JSON.stringify(extOrgText.replace(/\n/g, '|').slice(0, 180)))
  await mpFill('类器官类型', 'R2复验extC类器官')
  await mpFill('备注', 'D2 r2 extC 探针')
  await page.click('uni-button:has-text("提交")')
  await page.waitForTimeout(3500)
  await page.screenshot({ path: path.join(SHOTS, 'g3-17-organoid-extC-submitted.png'), fullPage: true })
  const extCRow = dbOne("SELECT COALESCE(verify_status,'-') || '|' || COALESCE(submit_source,'-') || '|' || COALESCE(sample_kind,'-') FROM t_lqg_sample WHERE submitter_id=9000000113 AND sample_kind='organoid' AND del_flag='0' AND create_time > now() - interval '10 minutes' ORDER BY id DESC LIMIT 1")
  check('R2M-40 extC 三项提交后落库 = pending|external|organoid', extCRow === 'pending|external|organoid',
    `db=${extCRow} toast=${JSON.stringify(await mpToast())}`)

  check('R2M-41 全程无未捕获前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
} catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 3).join(' | ') : String(e))
} finally {
  const failed = results.filter(r => !r.ok)
  console.log(`\n== D2-r2-L2-mp ${results.length - failed.length}/${results.length} 通过 ==`)
  console.log(`SHOTS=${SHOTS}`)
  await browser.close()
  process.exit(failed.length === 0 ? 0 : 1)
}
