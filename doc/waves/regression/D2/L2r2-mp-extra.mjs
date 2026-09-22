/**
 * D2 / r2 / L2 —— 补齐 r1 主脚本没跑到/跑歪的几条（组 1 / 组 3 增补），独立复验。
 *
 * 目的（都是 qa_scope 明写、但主脚本 L2r2-mp.mjs 没覆盖到的）：
 *   ① 内部「首页四格点样本记录信息表 → 直接进填写页**新增**」要真的提交出一条（不只验页面形态）；
 *   ② 「类器官收样 …**可从历史编辑记录修改**」；
 *   ③ 只读（mode=view）页的按钮组选中态要和**库内值**对得上（R2M-30b 在空值行上跑歪了）；
 *   ④ 表格页「待核验的外部样本」冻结列应显示**送检单号 + 待核验**徽标（UI:mp.ledger）；
 *   ⑤ 内部历史编辑记录 = 四张表页签。
 *
 * 跑法（在 L2r2-mp.mjs 之后跑；依赖它建的 T-r2org1）：node doc/waves/regression/D2/L2r2-mp-extra.mjs
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
const SHOTS = path.join(HERE, 'shots/L2r2-mp-extra')
mkdirSync(SHOTS, { recursive: true })

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
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

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await ctx.newPage()
const pageErrors = []
page.on('pageerror', e => { pageErrors.push(e.message); console.log('[pageerror]', e.message) })

const bodyText = () => page.evaluate(() => document.body.innerText)
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
  if ((await inp.count()) === 0) throw new Error(`字段 ${label} 不可输入`)
  await inp.first().fill(val)
}
const mpBtn = t => page.locator(`uni-button:has-text("${t}")`)
async function mockLogin(label) {
  await page.goto(`${MP}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login__agree', { timeout: 25000 })
  await page.waitForTimeout(400)
  const on = await page.$eval('.login__box', el => el.className.includes('login__box--on')).catch(() => false)
  if (!on) await page.click('.login__box')
  await page.click(`.login__mock-btn:has-text("${label}")`)
  await page.waitForSelector('.lqg-tile', { timeout: 25000 })
  await page.waitForTimeout(800)
}
async function gotoHistory() {
  await page.goto(`${MP}/#/pages/history/index`, { waitUntil: 'domcontentloaded' })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.his__switch', { timeout: 25000 })
  await page.waitForTimeout(1800)
}

try {
  await mockLogin('内部人员 · 李工')

  // ① 首页四格 → 样本记录信息表 → 真提交一条内部组织样本
  await page.click('.lqg-tile:has-text("样本记录信息表")')
  await page.waitForTimeout(2500)
  const url1 = page.url()
  const hasSubmit = (await mpBtn('提交').count()) > 0
  await page.screenshot({ path: path.join(SHOTS, 'x-01-internal-new.png'), fullPage: true })
  const INNO = 'T-r2t' + String(Date.now()).slice(-6)
  await mpFill('来源单位', 'A 医院')
  await mpFill('供体姓名', 'R2复验供体乙')
  await mpFill('组织类型', '肝组织')
  await mpFill('内部编号', INNO)
  // 收样日期（内部必填的日期控件）：点开 → 确认
  const rd = await mpField('收样日期')
  if (rd) {
    await rd.click()
    await page.waitForTimeout(1200)
    const act = await page.locator('.wd-datetime-picker__action').count()
    if (act >= 2) { await page.locator('.wd-datetime-picker__action').last().click(); await page.waitForTimeout(700) }
  }
  await page.screenshot({ path: path.join(SHOTS, 'x-02-internal-new-filled.png'), fullPage: true })
  await page.click('uni-button:has-text("提交")')
  await page.waitForTimeout(3500)
  await page.screenshot({ path: path.join(SHOTS, 'x-03-internal-new-submitted.png'), fullPage: true })
  const tRow = dbOne(`SELECT COALESCE(id::text,'-') || '|' || COALESCE(submit_source,'-') || '|' || COALESCE(sample_kind,'-') || '|' || COALESCE(verify_status,'-') || '|' || COALESCE(operator_name,'-') FROM t_lqg_sample WHERE internal_no='${INNO}'`)
  check('R2MX-01 staff 首页点「样本记录信息表」→ 该页直接**新增并提交成功**（库内 = internal|tissue|valid）',
    /mode=new/.test(url1) && hasSubmit && /^\d+\|internal\|tissue\|valid\|/.test(tRow),
    `url=${url1} db=${tRow}`)

  // ② 内部历史编辑记录 = 四张表页签
  await gotoHistory()
  const tabs = await page.$$eval('.lqg-sheets__item', els => els.map(e => e.innerText.trim()))
  await page.screenshot({ path: path.join(SHOTS, 'x-04-history-staff-tabs.png'), fullPage: true })
  check('R2MX-02 staff 历史编辑记录 = 四张表页签（样本记录信息表 / 类器官收样记录 / 石蜡包埋送样记录 / -80 冻存记录）',
    ['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录', '-80 冻存记录'].every(t => tabs.includes(t)), JSON.stringify(tabs))

  // ③ 类器官收样：从历史编辑记录改一条（T-r2org1）
  const orgId = dbOne("SELECT id FROM t_lqg_sample WHERE internal_no='T-r2org1'")
  const beforeType = dbOne(`SELECT organoid_type FROM t_lqg_sample WHERE id=${orgId}`)
  await page.locator('.lqg-sheets__item:has-text("类器官收样")').first().click()
  await page.waitForTimeout(2000)
  await page.screenshot({ path: path.join(SHOTS, 'x-05-history-organoid-tab.png'), fullPage: true })
  const orgTabText = await bodyText()
  const orgRow = page.locator('.his__item').filter({ hasText: 'T-r2org1' }).first()
  const rowFound = (await orgRow.count()) > 0
  if (rowFound) {
    await orgRow.click()
    await page.waitForTimeout(2500)
  }
  const editUrl = page.url()
  await page.screenshot({ path: path.join(SHOTS, 'x-06-organoid-edit.png'), fullPage: true })
  let saved = false
  if (rowFound && /pages\/organoid\/form/.test(editUrl)) {
    await mpFill('类器官类型', 'R2复验类器官已改')
    await page.click('uni-button:has-text("保存")')
    await page.waitForTimeout(3200)
    saved = dbOne(`SELECT organoid_type FROM t_lqg_sample WHERE id=${orgId}`) === 'R2复验类器官已改'
    await page.screenshot({ path: path.join(SHOTS, 'x-07-organoid-saved.png'), fullPage: true })
  }
  check('R2MX-03 内部类器官收样可从「历史编辑记录」修改并保存成功（库内类器官类型变）',
    rowFound && /pages\/organoid\/form/.test(editUrl) && saved,
    `tab=${JSON.stringify(orgTabText.replace(/\n/g, '|').slice(0, 120))} url=${editUrl} before=${beforeType} after=${dbOne(`SELECT organoid_type FROM t_lqg_sample WHERE id=${orgId}`)}`)

  // ④ 只读（mode=view）按钮组选中态 vs 库内值：用 T-hli05（1008，库里 有/有/Y/N 有值）
  await page.goto(`${MP}/#/pages/ledger/index`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.lqg-ledger__th', { timeout: 25000 })
  await page.waitForTimeout(1800)
  const pendingCell = await page.evaluate(() => {
    const rows = [...document.querySelectorAll('.ledger__row')]
    const r = rows.find(x => /^SJ\d/.test((x.innerText || '').trim()))
    return r ? r.innerText.replace(/\n/g, '|') : null
  }).catch(() => null)
  await page.screenshot({ path: path.join(SHOTS, 'x-08-ledger-pending-row.png'), fullPage: true })
  check('R2MX-04 表格页里「待核验的外部样本」冻结列显示送检单号 + 待核验（UI:mp.ledger）',
    !!pendingCell && /^SJ\d+/.test(pendingCell) && /待核验/.test(pendingCell), `cell=${JSON.stringify(pendingCell)}`)

  const hRow = page.locator('.ledger__row').filter({ hasText: 'T-hli05' }).first()
  const hFound = (await hRow.count()) > 0
  await hRow.click()
  await page.waitForTimeout(2500)
  const viewUrl = page.url()
  const dbSeg = dbOne("SELECT gender || '|' || COALESCE(is_fixed,'') || '|' || COALESCE(has_pathology,'') || '|' || COALESCE(has_qc_sheet,'') || '|' || COALESCE(has_viability_report,'') FROM t_lqg_sample WHERE id=9000001008")
  const segs = await page.evaluate(() => {
    const out = {}
    for (const seg of document.querySelectorAll('.lqg-seg')) {
      const label = (seg.closest('.wd-cell, .wd-input')?.querySelector('.wd-input__label-inner, .wd-cell__title')?.innerText || '').replace('*', '').trim()
      out[label] = [...seg.querySelectorAll('.lqg-seg__item')].map(e => (e.className.includes('--on') ? '[x]' : '[ ]') + e.innerText.trim()).join(' ')
    }
    return out
  }).catch(() => ({}))
  await page.screenshot({ path: path.join(SHOTS, 'x-09-readonly-segs.png'), fullPage: true })
  // 期望：库内值非空的按钮组，恰好一项选中且文案与库值对应
  const MAP = {
    性别: { female: '女', male: '男', unknown: '未知' },
    有无固定: { Y: '有', N: '无' },
    有无病理: { Y: '有', N: '无' },
    质控表: { Y: '有', N: '无' },
    细胞活率报告: { Y: '有', N: '无' },
  }
  const dbCol = { 性别: 'female', 有无固定: 'Y', 有无病理: '', 质控表: 'Y', 细胞活率报告: 'Y' } // 下面用真库值覆盖
  const [g, isf, hp, qc, vi] = dbSeg.split('|')
  Object.assign(dbCol, { 性别: g, 有无固定: isf, 有无病理: hp, 质控表: qc, 细胞活率报告: vi })
  const segProblems = []
  for (const [label, val] of Object.entries(dbCol)) {
    const want = MAP[label][val]
    const txt = segs[label]
    if (!txt) { segProblems.push(`${label}:NOSEg`); continue }
    if (!want) { continue }
    const onCount = (txt.match(/\[x\]/g) || []).length
    if (onCount !== 1 || !txt.includes('[x]' + want)) segProblems.push(`${label}:db=${val} want=${want} got=${txt}`)
  }
  check('R2MX-05【r1-S0-2 复验】只读页按钮组选中态与库内值一致（库内有值的组各恰好 1 项 on 且文案对得上）',
    /mode=view/.test(viewUrl) && hFound && segProblems.length === 0,
    `url=${viewUrl} hFound=${hFound} db=${dbSeg} segs=${JSON.stringify(segs)} problems=${JSON.stringify(segProblems)}`)

  check('R2MX-06 全程无未捕获前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
} catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 3).join(' | ') : String(e))
} finally {
  const failed = results.filter(r => !r.ok)
  console.log(`\n== D2-r2-L2-mp-extra ${results.length - failed.length}/${results.length} 通过 ==`)
  console.log(`SHOTS=${SHOTS}`)
  await browser.close()
  process.exit(failed.length === 0 ? 0 : 1)
}
