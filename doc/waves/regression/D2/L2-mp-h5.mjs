/**
 * D2 / r1 / L2 —— 小程序 H5 等价路径（group 1 外部体验路径 + group 3 内部小程序）。
 *
 * 微信开发者工具在本沙箱跑不通（CLI 要写 ~/Library/Application Support/微信开发者工具/** EPERM +
 * 需人工扫码），所以本片用 H5 dev server（9200, VITE_MOCK_LOGIN=1）+ Playwright 等价覆盖；
 * 开发者工具 / 真机项如实记为未覆盖，不冒充。
 *
 * extA/extF 走产品自己的调试登录面板；extB/extC 不在面板的 3 个 seed 里，用后端 mock 登录
 * （ADR-0008，`xcxCode=mock:<key>`）拿 token 后注入 localStorage `lqg_mp_token` —— 走的是同一条
 * mock 路径，只是绕开面板按钮，脚本里会标注。
 *
 * 前置：后端 8081 + reseed + miniapp H5 9200 + plus-ui 8082。
 * 跑法：node doc/waves/regression/D2/L2-mp-h5.mjs
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
const SHOTS = path.join(HERE, 'shots/L2-mp')
mkdirSync(SHOTS, { recursive: true })

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

// ── 库（只读、只用于拿「刚建出来的那一行」的 id / 编号） ──────────────────────
function dbOne(sql) {
  return execFileSync('psql', ['-h', '127.0.0.1', '-p', '5433', '-U', 'lqg', '-d', 'lqg_dev', '-Atc', sql],
    { env: { ...process.env, PGPASSWORD: 'lqg_dev_pwd' }, encoding: 'utf8' }).trim()
}

/** 后端 mock 登录拿 token（extB/extC 不在调试面板的 3 个 seed 里） */
function mockToken(as) {
  execFileSync('bash', ['doc/verify/api.sh', '--as', as, 'GET', '/mp/me'], { cwd: WS, stdio: 'ignore' })
  const dir = process.env.TMPDIR || '/tmp'
  const f = readdirSync(dir).filter(x => x.startsWith(`lqg-verify-token-${as}-`)).sort().pop()
  return readFileSync(path.join(dir, f), 'utf8').split('\n')[0]
}

const browser = await chromium.launch({ headless: true })
const ctxMp = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await ctxMp.newPage()
const ctxWeb = await browser.newContext({ viewport: { width: 1600, height: 950 } })
const web = await ctxWeb.newPage()
const pageErrors = []
const consoleErrs = []
page.on('pageerror', e => { pageErrors.push('[mp] ' + e.message); console.log('[pageerror mp]', e.message) })
page.on('console', m => { if (m.type() === 'error') { consoleErrs.push(m.text()); console.log('[console.error mp]', m.text().slice(0, 160)) } })
web.on('pageerror', e => { pageErrors.push('[web] ' + e.message); console.log('[pageerror web]', e.message) })

const bodyText = (p = page) => p.evaluate(() => document.body.innerText)
const homeText = () => page.$eval('.home', e => e.innerText).catch(() => '')
const digitsOutside80 = t => (t.replace(/-80/g, '').match(/\d/g) || [])

// ── 小程序登录 ─────────────────────────────────────────────────────────────
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
  await page.waitForTimeout(700)
}
/** extB / extC：后端 mock 登录 token 注入（同一条 mock 路径，绕开面板按钮） */
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
  // 先回到首页（tab 页）再点 tabbar，避免停在上一次跳转后的非 tab 页
  await page.goto(`${MP}/#/pages/index/index`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.lqg-tile', { timeout: 25000 })
  await page.click('.uni-tabbar__item:has-text("我的")')
  await page.waitForSelector('.me__logout', { timeout: 25000 })
  await page.waitForTimeout(700)
}
async function gotoHistory() {
  await page.goto(`${MP}/#/pages/history/index`, { waitUntil: 'domcontentloaded' })
  // ★ hash 路由下 goto 不重载：页面实例可能是上一次的旧数据（实测踩到——核验成有效后
  //   列表还按旧的 editable 跳 mode=edit）。硬 reload 复位。
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.his__switch', { timeout: 25000 })
  await page.waitForTimeout(1800)
}

// ── 小程序表单字段 ─────────────────────────────────────────────────────────
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
  if ((await inp.count()) === 0) throw new Error(`字段 ${label} 不是可输入的（只读或非文本控件）`)
  await inp.first().fill(val)
}
async function mpSeg(label, opt) {
  const r = await mpField(label)
  if (!r) throw new Error(`找不到字段 ${label}`)
  await r.locator(`.lqg-seg__item:has-text("${opt}")`).first().click()
  await page.waitForTimeout(250)
}
const mpBtn = t => page.locator(`uni-button:has-text("${t}")`)
const mpToast = () => page.$$eval('.uni-toast,[class*="toast"]', els => els.map(e => e.innerText).join('|')).catch(() => '')

// ── 工作台核验（web 侧 UI 驱动） ───────────────────────────────────────────
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
async function webRow(submitNo) {
  return web.locator('.el-table__body tr').filter({ hasText: submitNo }).first()
}
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
/** 工作台把某条判为无效 */
async function verifyInvalid(submitNo, reason) {
  const row = await webRow(submitNo)
  await row.locator('button:has-text("核验")').click()
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
/** 工作台把某条判为有效 */
async function verifyValid(submitNo, internalNo) {
  await webOpenSample()
  const row = await webRow(submitNo)
  await row.locator('button:has-text("核验")').click()
  await web.waitForSelector('.el-drawer:visible', { timeout: 20000 })
  await web.waitForTimeout(1000)
  const di = await drawerField('内部编号')
  await di.locator('input').first().fill(internalNo)
  const dr = await drawerField('收样日期')
  const today = new Date().toISOString().slice(0, 10)
  await dr.locator('input').first().fill(today)
  await web.keyboard.press('Enter')
  await web.waitForTimeout(500)
  const [r] = await Promise.all([
    web.waitForResponse(x => /\/verify$/.test(x.url()), { timeout: 25000 }).catch(() => null),
    drawer().locator('button:has-text("判为有效并保存")').click(),
  ])
  await web.waitForTimeout(2000)
  return r ? (await r.json().catch(() => ({}))).code : null
}

try {
  // ══════════ 组 1 · 外部（extA）体验路径 ══════════════════════════════════
  await resetToLogin()
  await mockLogin('外部人员 · 王医生（已核验）')
  await page.screenshot({ path: path.join(SHOTS, 'g1-01-home-extA.png'), fullPage: true })
  const tiles = await page.$$eval('.lqg-tile .lqg-tile__t', els => els.map(e => e.textContent.trim()))
  check('G1-01 extA 首页三格、顺序 = 模板顺序', JSON.stringify(tiles) === JSON.stringify(['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录']), JSON.stringify(tiles))
  const hDigits = digitsOutside80(await homeText())
  check('G1-02 extA 首页没有任何数字', hDigits.length === 0, `digits=${JSON.stringify(hDigits)}`)

  // 入口：点「样本记录信息表」→ 应该是新增填写页
  await page.click('.lqg-tile:has-text("样本记录信息表")')
  await page.waitForTimeout(2500)
  const entryText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g1-02-entry-sample.png'), fullPage: true })
  const entryUrl = page.url()
  const hasSubmit = (await mpBtn('提交').count()) > 0
  check('G1-03 首页点「样本记录信息表」→ 进新增填写页（有「提交」按钮、不是错误态）',
    /pages\/sample\/form/.test(entryUrl) && hasSubmit && !/没能加载/.test(entryText),
    `url=${entryUrl} hasSubmit=${hasSubmit} text=${JSON.stringify(entryText.replace(/\n/g, '|').slice(0, 120))}`)

  // 用显式 mode=new 继续走完主线（入口缺陷本身已在上一条记账）
  await page.goto(`${MP}/#/pages/sample/form?mode=new`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.wd-input', { timeout: 25000 })
  await page.waitForTimeout(2000)
  const newFormText = await bodyText()
  check('G1-04 显式 mode=new 时是外部新增页：不渲染收样段（送检段 8 项）、有提交按钮',
    /送检信息/.test(newFormText) && !/收样信息/.test(newFormText) && (await mpBtn('提交').count()) > 0,
    JSON.stringify(newFormText.replace(/\n/g, '|').slice(0, 160)))
  const DONOR = 'L2复验供体甲'
  await mpFill('供体姓名', DONOR)
  await mpFill('组织类型', '肝组织')
  await mpFill('备注', 'D2 L2 独立复验')
  await page.screenshot({ path: path.join(SHOTS, 'g1-05-new-filled.png'), fullPage: true })
  await page.click('uni-button:has-text("提交")')
  await page.waitForTimeout(3500)
  const afterSubmit = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g1-06-submitted.png'), fullPage: true })
  const NEW = dbOne("SELECT id || '|' || submit_no FROM t_lqg_sample WHERE submitter_id=9000000111 AND del_flag='0' AND create_time > now() - interval '10 minutes' ORDER BY id DESC LIMIT 1")
  const [NEW_ID, NEW_NO] = NEW.split('|')
  check('G1-05 extA 提交送检成功（库里出现今天新建的一行）', !!NEW_ID && /^SJ\d+$/.test(NEW_NO || ''), `toast=${JSON.stringify(await mpToast())} new=${NEW} tail=${JSON.stringify(afterSubmit.replace(/\n/g, '|').slice(-120))}`)

  // 我的 → 历史编辑记录出现待核验
  await gotoMe()
  await page.click('.merow:has-text("历史编辑记录")')
  await page.waitForSelector('.his__item', { timeout: 25000 })
  await page.waitForTimeout(1500)
  await page.screenshot({ path: path.join(SHOTS, 'g1-07-history-pending.png'), fullPage: true })
  let hist = await bodyText()
  check('G1-06 extA 历史编辑记录里新记录 + 状态「待核验」', hist.includes(NEW_NO) && /待核验/.test(hist),
    JSON.stringify(hist.replace(/\n/g, '|').slice(0, 200)))

  // 工作台核验为无效
  await webLogin()
  await webOpenSample()
  const codeInv = await verifyInvalid(NEW_NO, 'L2复验：缺住院号，请补')
  check('G1-07 工作台核验为无效（code=200，库里 verify_status=invalid）',
    codeInv === 200 && dbOne(`SELECT verify_status FROM t_lqg_sample WHERE id=${NEW_ID}`) === 'invalid',
    `code=${codeInv} status=${dbOne(`SELECT verify_status FROM t_lqg_sample WHERE id=${NEW_ID}`)}`)
  await web.screenshot({ path: path.join(SHOTS, 'g1-08-web-invalid.png'), fullPage: true })

  // 外部在历史编辑记录看到原因并修改重提
  await gotoHistory()
  const histInvalid = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g1-09-history-invalid.png'), fullPage: true })
  const rowNew = page.locator('.his__item').filter({ hasText: NEW_NO }).first()
  await rowNew.click()
  await page.waitForTimeout(2500)
  const editText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g1-10-history-open-invalid.png'), fullPage: true })
  check('G1-08 外部在历史编辑记录看到无效原因（列表或打开后可见「L2复验：缺住院号，请补」）',
    /L2复验：缺住院号，请补/.test(histInvalid) || /L2复验：缺住院号，请补/.test(editText),
    `list=${JSON.stringify(histInvalid.replace(/\n/g, '|').slice(0, 160))} opened=${JSON.stringify(editText.replace(/\n/g, '|').slice(0, 160))}`)
  check('G1-09 无效记录打开后是「修改后重新提交」表单（可写：能填字段）',
    /pages\/sample\/form/.test(page.url()) && (await page.locator('.wd-input input').count()) > 0,
    `url=${page.url()} inputs=${await page.locator('.wd-input input').count()}`)

  // 修改重提
  await mpFill('备注', 'D2 L2 已补住院号后重提')
  await page.click('uni-button:has-text("保存")')
  await page.waitForTimeout(3500)
  await page.screenshot({ path: path.join(SHOTS, 'g1-11-resubmitted.png'), fullPage: true })
  check('G1-10 extA 修改后保存成功（库里 remark 更新且状态回到 pending）',
    dbOne(`SELECT remark FROM t_lqg_sample WHERE id=${NEW_ID}`) === 'D2 L2 已补住院号后重提'
    && dbOne(`SELECT verify_status FROM t_lqg_sample WHERE id=${NEW_ID}`) === 'pending',
    `remark=${dbOne(`SELECT remark FROM t_lqg_sample WHERE id=${NEW_ID}`)} status=${dbOne(`SELECT verify_status FROM t_lqg_sample WHERE id=${NEW_ID}`)}`)

  // 工作台核验为有效
  const codeVal = await verifyValid(NEW_NO, 'T-l2ext01')
  await web.screenshot({ path: path.join(SHOTS, 'g1-12-web-valid.png'), fullPage: true })
  check('G1-11 工作台核验为有效（code=200，库里 valid + 内部编号 T-l2ext01）',
    codeVal === 200 && dbOne(`SELECT verify_status || '|' || internal_no FROM t_lqg_sample WHERE id=${NEW_ID}`) === 'valid|T-l2ext01',
    `code=${codeVal} db=${dbOne(`SELECT verify_status || '|' || internal_no FROM t_lqg_sample WHERE id=${NEW_ID}`)}`)

  // 外部详情只读
  await gotoHistory()
  const rowValid = page.locator('.his__item').filter({ hasText: NEW_NO }).first()
  await rowValid.click()
  await page.waitForTimeout(2500)
  const roText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g1-13-ext-readonly.png'), fullPage: true })
  const roInputs = await page.locator('.wd-input input').count()
  const roSubmit = await mpBtn('提交').count()
  check('G1-12 核验有效后外部再看这条是只读（无输入控件、无提交/保存按钮；状态徽标缺失另记 G1-06/StatusChip 问题）',
    roInputs === 0 && roSubmit === 0,
    `url=${page.url()} inputs=${roInputs} submit=${roSubmit} text=${JSON.stringify(roText.replace(/\n/g, '|').slice(0, 140))}`)

  // extB（同组）：看得到 + 「只看我提交的」后看不到
  await tokenLogin('extB')
  await gotoHistory()
  const extBHist = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g1-14-extB-history.png'), fullPage: true })
  check('G1-13 extB（同组）历史编辑记录里看得到 extA 这条', extBHist.includes(NEW_NO),
    JSON.stringify(extBHist.replace(/\n/g, '|').slice(0, 200)))
  // 打开「只看我提交的」（默认关）
  await page.click('.his__switch .wd-switch')
  await page.waitForTimeout(2500)
  const extBOnly = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g1-15-extB-onlymine.png'), fullPage: true })
  check('G1-14 extB 打开「只看我提交的」后看不到 extA 这条', !extBOnly.includes(NEW_NO),
    JSON.stringify(extBOnly.replace(/\n/g, '|').slice(0, 200)))

  // ══════════ 组 3 · 内部（staff）小程序 ══════════════════════════════════
  await resetToLogin()
  await mockLogin('内部人员 · 李工')
  await page.screenshot({ path: path.join(SHOTS, 'g3-01-home-staff.png'), fullPage: true })
  const intTiles = await page.$$eval('.lqg-tile .lqg-tile__t', els => els.map(e => e.textContent.trim()))
  check('G3-01 staff 首页四格、顺序 = 模板顺序',
    JSON.stringify(intTiles) === JSON.stringify(['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录', '-80 冻存记录']), JSON.stringify(intTiles))
  await page.click('.lqg-tile:has-text("样本记录信息表")')
  await page.waitForTimeout(2500)
  const intEntryText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g3-02-entry-sample.png'), fullPage: true })
  check('G3-02 staff 首页点「样本记录信息表」→ 进新增填写页',
    (await mpBtn('提交').count()) > 0 && !/没能加载/.test(intEntryText),
    `url=${page.url()} text=${JSON.stringify(intEntryText.replace(/\n/g, '|').slice(0, 120))}`)

  // 内部管理 → 表格页
  await gotoMe()
  const meText = await bodyText()
  check('G3-03 staff 「我的」有「内部管理」四张只读表入口',
    /内部管理/.test(meText) && ['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录', '-80 冻存记录'].every(t => meText.includes(t)),
    JSON.stringify(meText.replace(/\n/g, '|').slice(0, 220)))
  await page.goto(`${MP}/#/pages/ledger/index`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.lqg-ledger__th', { timeout: 25000 })
  await page.waitForTimeout(1800)
  await page.screenshot({ path: path.join(SHOTS, 'g3-03-ledger-tissue.png'), fullPage: true })
  const tabs = await page.$$eval('.lqg-sheets__item', els => els.map(e => e.innerText.trim()))
  check('G3-04 表格页两个工作表：样本记录 / 类器官收样', JSON.stringify(tabs) === JSON.stringify(['样本记录', '类器官收样']), JSON.stringify(tabs))
  const TISSUE_COLS = ['内部编号', '来源单位', '供体姓名', '性别', '年龄', '住院号', '组织类型', '收样日期', '有无固定', '处理时间', '质控表', '细胞活率报告', '操作人', '备注', '切片染色']
  const headTissue = await page.$$eval('.lqg-ledger__th', els => els.map(e => e.innerText.trim()))
  check('G3-05 样本记录表头 = 冻结列内部编号 + 模板列序 + 追加切片染色（15 列，逐字逐序）',
    JSON.stringify(headTissue) === JSON.stringify(TISSUE_COLS), JSON.stringify(headTissue))
  // 第二个工作表
  await page.locator('.lqg-sheets__item:has-text("类器官收样")').click()
  await page.waitForTimeout(2000)
  const headOrg = await page.$$eval('.lqg-ledger__th', els => els.map(e => e.innerText.trim()))
  const ORG_COLS = ['内部编号', '来源单位', '类器官类型', '收样日期', '处理时间', '细胞活率报告', '操作人', '切片染色']
  await page.screenshot({ path: path.join(SHOTS, 'g3-04-ledger-organoid.png'), fullPage: true })
  check('G3-06 类器官收样表头 = 冻结列内部编号 + 模板列序 + 切片染色（8 列）',
    JSON.stringify(headOrg) === JSON.stringify(ORG_COLS), JSON.stringify(headOrg))
  // 首列冻结 + 横滑
  const fz = await page.$eval('.lqg-ledger__fz', e => { const s = getComputedStyle(e); return { pos: s.position, left: s.left } })
  check('G3-07 首列冻结：position=sticky / left=0px（计算样式）', fz.pos === 'sticky' && fz.left === '0px', JSON.stringify(fz))
  const sc = await page.evaluate(() => {
    const root = document.querySelector('.lqg-ledger')
    const cands = [root, ...root.querySelectorAll('*')]
    for (const e of cands) {
      const s = getComputedStyle(e)
      if (['auto', 'scroll'].includes(s.overflowX) && e.scrollWidth > e.clientWidth + 5) {
        return { tag: e.tagName + '.' + e.className, ow: s.overflowX, w: e.scrollWidth, cw: e.clientWidth }
      }
    }
    const head = document.querySelector('.lqg-ledger__head')
    return { none: true, headW: head ? head.getBoundingClientRect().width : -1, rootCw: root.clientWidth }
  }).catch(() => null)
  check('G3-08 表格自身可横滑到全部列（存在 overflow-x=auto/scroll 且 scrollWidth > clientWidth 的元素）',
    !!sc && !sc.none && sc.w > sc.cw, JSON.stringify(sc))
  const btns = await page.$$eval('uni-button, button', els => els.map(e => e.innerText.trim()).filter(t => t && !t.includes('导出 Excel')))
  const barText = await bodyText()
  check('G3-09 表格页没有「新增」「保存」按钮（底部只有置灰的导出 Excel）',
    !btns.some(t => /新增|保存/.test(t)) && /导出 Excel/.test(barText)
    && await page.$eval('.ledger-page__export', e => !!(e.disabled || e.hasAttribute('disabled'))).catch(() => false),
    `buttons=${JSON.stringify(btns)}`)
  // 点一行 → 只读
  await page.locator('.lqg-sheets__item:has-text("样本记录")').click()
  await page.waitForTimeout(1500)
  await page.locator('.ledger__row').first().click()
  await page.waitForTimeout(2500)
  await page.screenshot({ path: path.join(SHOTS, 'g3-05-ledger-row-readonly.png'), fullPage: true })
  const roLedger = { inputs: await page.locator('input, textarea').count(), save: await mpBtn('提交').count(), url: page.url() }
  check('G3-10 表格页点一行 → 该表填写页只读（mode=view：无输入、无提交/保存）',
    /mode=view/.test(roLedger.url) && roLedger.inputs === 0 && roLedger.save === 0, JSON.stringify(roLedger))

  // 历史编辑记录：点本人录的有效样本 → 改一个字段 → 保存成功 → 排到最前
  await gotoHistory()
  await page.screenshot({ path: path.join(SHOTS, 'g3-06-history-staff.png'), fullPage: true })
  const beforeRemark = dbOne("SELECT remark FROM t_lqg_sample WHERE id=9000001008")
  const ownRow = page.locator('.his__item').filter({ hasText: 'T-hli05' }).first()
  await ownRow.click()
  await page.waitForTimeout(2500)
  const editOwnText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g3-07-edit-own-valid.png'), fullPage: true })
  let ownEditOk = /pages\/sample\/form/.test(page.url()) && /收样信息/.test(editOwnText) && (await mpBtn('保存').count()) > 0
  check('G3-11 内部历史编辑记录点本人有效样本 → 进 edit 可写（渲染收样段 + 保存按钮）', ownEditOk,
    `url=${page.url()} text=${JSON.stringify(editOwnText.replace(/\n/g, '|').slice(0, 160))}`)
  if (ownEditOk) {
    await mpFill('备注', 'D2 L2 内部改一笔')
    await page.click('uni-button:has-text("保存")')
    await page.waitForTimeout(3000)
    await page.screenshot({ path: path.join(SHOTS, 'g3-08-own-saved.png'), fullPage: true })
    const nowRemark = dbOne('SELECT remark FROM t_lqg_sample WHERE id=9000001008')
    check('G3-12 内部改一个字段保存成功（库里 remark 变、update_by 有值）',
      nowRemark === 'D2 L2 内部改一笔' && dbOne('SELECT COALESCE(update_by::text,\'-\') FROM t_lqg_sample WHERE id=9000001008') !== '-',
      `before=${beforeRemark} now=${nowRemark} update_by=${dbOne('SELECT COALESCE(update_by::text,\'-\') FROM t_lqg_sample WHERE id=9000001008')}`)
    await gotoHistory()
    const firstCode = await page.locator('.his__item').first().innerText().catch(() => '')
    await page.screenshot({ path: path.join(SHOTS, 'g3-09-history-after-edit.png'), fullPage: true })
    check('G3-13 改过的那条排到历史编辑记录最前', /T-hli05/.test(firstCode), JSON.stringify(firstCode.replace(/\n/g, '|').slice(0, 120)))
  }

  // 类器官收样：内部 7 列可新增
  await page.goto(`${MP}/#/pages/organoid/form?mode=new`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.wd-input, .wd-cell', { timeout: 25000 })
  await page.waitForTimeout(2000)
  const orgText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g3-10-organoid-internal.png'), fullPage: true })
  const ORG7 = ['来源单位', '类器官类型', '收样日期', '内部编号', '处理时间', '细胞活率报告', '操作人']
  check('G3-14 内部类器官收样填写页 7 项齐（来源单位/类器官类型/收样日期/内部编号/处理时间/细胞活率报告/操作人）',
    ORG7.every(t => orgText.includes(t)) && /送检信息/.test(orgText) && /收样信息/.test(orgText),
    JSON.stringify(orgText.replace(/\n/g, '|').slice(0, 200)))
  // 7 列里唯一的日期控件（收样日期，必填）能不能填
  const rdRow = await mpField('收样日期')
  await rdRow.click()
  await page.waitForTimeout(1500)
  const picker = {
    action: await page.locator('.wd-datetime-picker__action').count(),
    popup: await page.locator('.wd-datetime-picker__popup').count(),
    visible: await page.locator('.wd-datetime-picker__popup').first().isVisible().catch(() => false),
  }
  await page.screenshot({ path: path.join(SHOTS, 'g3-11-organoid-datepicker.png'), fullPage: true })
  check('G3-15 内部类器官收样：点「收样日期」能弹出日期选择器（有确定/取消动作条）',
    picker.action >= 2 && picker.visible === true, JSON.stringify(picker))
  // 收尾：能填就填完提交，不能填就如实记账
  let orgSubmitted = false
  if (picker.action >= 2) {
    await page.locator('.wd-datetime-picker__action').last().click()
    await page.waitForTimeout(800)
  }
  try {
    await mpFill('类器官类型', 'L2复验类器官')
    await mpFill('内部编号', 'T-l2org01')
    await page.click('uni-button:has-text("提交")')
    await page.waitForTimeout(3500)
    orgSubmitted = dbOne("SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-l2org01'") === '1'
  } catch (e) { console.log('[g3 organoid] 提交失败：', e.message) }
  await page.screenshot({ path: path.join(SHOTS, 'g3-12-organoid-submit.png'), fullPage: true })
  check('G3-16 内部类器官收样 7 列能新增一条（库里出现 T-l2org01）', orgSubmitted,
    `db=${dbOne("SELECT COALESCE(id::text,'-') FROM t_lqg_sample WHERE internal_no='T-l2org01'")} toast=${JSON.stringify(await mpToast())}`)

  // 外部（extC）类器官收样三项 → 待核验
  await tokenLogin('extC')
  await page.goto(`${MP}/#/pages/organoid/form?mode=new`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.wd-input, .wd-cell', { timeout: 25000 })
  await page.waitForTimeout(2000)
  const extOrgText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, 'g3-13-organoid-extC.png'), fullPage: true })
  check('G3-17 extC 类器官收样填写页正好三项（来源单位 / 类器官类型 / 备注），没有内部编号等内部字段',
    ['来源单位', '类器官类型', '备注'].every(t => extOrgText.includes(t))
    && !/内部编号|收样日期|处理时间|细胞活率报告|操作人/.test(extOrgText),
    JSON.stringify(extOrgText.replace(/\n/g, '|').slice(0, 200)))
  await mpFill('类器官类型', 'L2复验extC类器官')
  await mpFill('备注', 'D2 L2 extC 探针')
  await page.click('uni-button:has-text("提交")')
  await page.waitForTimeout(3500)
  await page.screenshot({ path: path.join(SHOTS, 'g3-14-organoid-extC-submitted.png'), fullPage: true })
  const extCRow = dbOne("SELECT COALESCE(verify_status,'-') || '|' || COALESCE(submit_source,'-') || '|' || COALESCE(sample_kind,'-') FROM t_lqg_sample WHERE submitter_id=9000000113 AND sample_kind='organoid' AND del_flag='0' AND create_time > now() - interval '10 minutes' ORDER BY id DESC LIMIT 1")
  check('G3-18 extC 三项提交后落库 = organoid / external / pending', extCRow === 'pending|external|organoid',
    `db=${extCRow} toast=${JSON.stringify(await mpToast())}`)

  // 状态徽标专项：历史列表每行应有核验状态徽标（UI:mp.history）
  await gotoHistory()
  const chipInfo = await page.evaluate(() => ({
    items: document.querySelectorAll('.his__item').length,
    chips: document.querySelectorAll('.his__item .lqg-tag').length,
  }))
  check('G3-19 历史编辑记录每行都有核验状态徽标（UI:mp.history 要求）', chipInfo.chips > 0 && chipInfo.chips >= chipInfo.items, JSON.stringify(chipInfo))
  const chipErr = consoleErrs.filter(e => /StatusChip|props is not defined/.test(e))
  check('G3-20 StatusChip 渲染抛 ReferenceError（根因）', chipErr.length > 0, JSON.stringify(chipErr.slice(0, 2)))
  check('G3-21 其余流程无未捕获前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
} catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 3).join(' | ') : String(e))
} finally {
  const failed = results.filter(r => !r.ok)
  console.log(`\n== D2-r1-L2-mp ${results.length - failed.length}/${results.length} 通过 ==`)
  console.log(`SHOTS=${SHOTS}`)
  await browser.close()
  process.exit(failed.length === 0 ? 0 : 1)
}
