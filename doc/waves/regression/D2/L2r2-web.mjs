/**
 * D2 / r2 / L2 —— 工作台样本总表（组 2）· 独立复验（本片自写，不复用 L2-web-plusui.mjs 的断言）。
 *
 * 期望值来源：本脚本自己用 psql 从**库里**现算（不是抄 accept 或旧脚本的硬编码集合）；
 * 只有加密列（供体姓名 / 住院号）的精确筛选用 doc/verify/README.md 的 seed 表推断单行。
 * 只验不修；只落自己的 shots/L2r2-web/，不覆盖别人的图。
 * 跑法：LQG_WEB_BASE=http://127.0.0.1:8082 node doc/waves/regression/D2/L2r2-web.mjs
 */
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const BASE = process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082'
const SHOTS = path.join(HERE, 'shots/L2r2-web')
mkdirSync(SHOTS, { recursive: true })

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

// ── 库：期望集合一律现算（唯一真源），AI 不写死 seed 期望 ──────────────────
// ★ 一律用**送检单号**做集合比较：18/19 位雪花 id 过一遍 JS `Number` 会掉精度
//   （2102092335193042945 → 2102092335193043000），r2 首跑就是被这个坑到。
function dbCodes(sql) {
  const out = execFileSync('psql', ['-h', '127.0.0.1', '-p', '5433', '-U', 'lqg', '-d', 'lqg_dev', '-Atc', sql],
    { env: { ...process.env, PGPASSWORD: 'lqg_dev_pwd' }, encoding: 'utf8' }).trim()
  return out ? out.split('\n').map(s => s.trim()).sort() : []
}
/** 保序取列（不排序），用于断行序 */
function dbCol(sql) {
  const out = execFileSync('psql', ['-h', '127.0.0.1', '-p', '5433', '-U', 'lqg', '-d', 'lqg_dev', '-Atc', sql],
    { env: { ...process.env, PGPASSWORD: 'lqg_dev_pwd' }, encoding: 'utf8' }).trim()
  return out ? out.split('\n').map(s => s.trim()) : []
}
function dbOne(sql) {
  return execFileSync('psql', ['-h', '127.0.0.1', '-p', '5433', '-U', 'lqg', '-d', 'lqg_dev', '-Atc', sql],
    { env: { ...process.env, PGPASSWORD: 'lqg_dev_pwd' }, encoding: 'utf8' }).trim()
}
const VIS = "del_flag='0'"

const codeOfRow = async (row) => {
  const t = await row.innerText().catch(() => '')
  const m = t.match(/SJ\d{8}/)
  return m ? m[0] : null
}
async function rowCodes() {
  const rows = page.locator('.el-table__body tr')
  const n = await rows.count()
  const out = []
  for (let i = 0; i < n; i++) out.push(await codeOfRow(rows.nth(i)))
  return out
}
async function filterItem(label) {
  const items = page.locator('.app-main .el-form-item')
  const n = await items.count()
  for (let i = 0; i < n; i++) {
    const t = (await items.nth(i).locator('.el-form-item__label').first().innerText().catch(() => '')).trim()
    if (t.startsWith(label)) return items.nth(i)
  }
  return null
}
async function pickSelect(label, optionText) {
  const fi = await filterItem(label)
  if (!fi) throw new Error(`找不到筛选项 ${label}`)
  await fi.locator('.el-select').first().click()
  await page.waitForTimeout(500)
  const opt = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: optionText }).first()
  await opt.click({ timeout: 8000 })
  await page.waitForTimeout(400)
}
async function fillFilter(label, val) {
  const fi = await filterItem(label)
  if (!fi) throw new Error(`找不到筛选项 ${label}`)
  await fi.locator('input').first().fill(val)
}
async function setDateRange(begin, end) {
  const fi = await filterItem('收样日期')
  const ins = fi.locator('input')
  await ins.nth(0).click()
  await page.waitForTimeout(300)
  await ins.nth(0).fill(begin)
  await page.keyboard.press('Enter')
  await page.waitForTimeout(400)
  await ins.nth(1).fill(end)
  await page.keyboard.press('Enter')
  await page.waitForTimeout(400)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)
}
/** 点搜索并读本次 /lqg/sample/list 的响应体（送检单号集合 + 顺序 + total） */
async function search() {
  const [resp] = await Promise.all([
    page.waitForResponse(r => r.url().includes('/lqg/sample/list'), { timeout: 25000 }),
    page.click('.app-main button:has-text("搜索")'),
  ])
  const body = await resp.json().catch(() => null)
  if (!body || !Array.isArray(body.rows)) return { ids: null, order: null, total: null }
  return { ids: body.rows.map(r => String(r.submitNo)).sort(), order: body.rows.map(r => String(r.submitNo)), total: body.total }
}
async function resetFilters() {
  await page.click('.app-main button:has-text("重置")')
  await page.waitForTimeout(1200)
}
const eqSet = (a, b) => Array.isArray(a) && Array.isArray(b) && JSON.stringify(a) === JSON.stringify(b)

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1600, height: 950 }, deviceScaleFactor: 1 })
const page = await ctx.newPage()
const pageErrors = []
page.on('pageerror', e => { pageErrors.push(e.message); console.log('[pageerror]', e.message) })

const today = new Date()
const d = n => { const x = new Date(today); x.setDate(x.getDate() - n); return x.toISOString().slice(0, 10) }

try {
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => localStorage.clear()).catch(() => {})
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login-form input', { timeout: 30000 })
  const inputs = page.locator('.login-form input')
  await inputs.nth(0).fill('lqgadmin')
  await inputs.nth(1).fill('admin123')
  await page.click('.login-form button')
  await page.waitForSelector('.el-menu', { timeout: 30000 })
  await page.waitForTimeout(2000)

  const menuOk = await page.locator('.el-menu-item:has-text("样本总表")').first().click({ timeout: 15000 }).then(() => true).catch(() => false)
  await page.waitForResponse(r => r.url().includes('/lqg/sample/list'), { timeout: 25000 }).catch(() => {})
  await page.waitForTimeout(1500)
  check('R2W-00 侧边栏点「样本总表」进入页面', menuOk && /sample/.test(page.url()), `url=${page.url()}`)
  await page.screenshot({ path: path.join(SHOTS, '10-sample-list.png'), fullPage: true })

  // ── ① 待核验置顶（全部 pending 都在前）+ 浅黄 ───────────────────────────
  //   与后端同源：ORDER BY (verify_status='pending') DESC, create_time DESC, id DESC，只取第 1 页。
  const PAGE = 10
  const codes = await rowCodes()
  const dbTop = dbCol(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} ORDER BY (verify_status='pending') DESC, create_time DESC, id DESC LIMIT ${PAGE}`)
  const dbPendingAll = dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND verify_status='pending'`)
  const dbTotal = Number(dbOne(`SELECT count(*) FROM t_lqg_sample WHERE ${VIS}`))
  check('R2W-01 第 1 页行序 == 库内同一条 ORDER BY（待核验全部置顶，其余按创建时间倒序）',
    JSON.stringify(codes) === JSON.stringify(dbTop),
    `ui=${JSON.stringify(codes)} db=${JSON.stringify(dbTop)} total=${dbTotal}`)
  check('R2W-01b 第 1 页行数 == min(页大小, 库内可见行数)（没漏行也没多行）',
    codes.length === Math.min(PAGE, dbTotal), `rows=${codes.length} total=${dbTotal}`)
  const pendingOnPage = Math.min(dbPendingAll.length, PAGE)
  const clsTop = await page.locator('.el-table__body tr').nth(0).getAttribute('class')
  const clsBelow = pendingOnPage < codes.length ? await page.locator('.el-table__body tr').nth(pendingOnPage).getAttribute('class') : ''
  check('R2W-02 置顶行带 pending 样式类，第一个非 pending 行不带',
    /pending/.test(clsTop || '') && (pendingOnPage >= codes.length || !/pending/.test(clsBelow || '')),
    `pendingOnPage=${pendingOnPage} cls0=${clsTop} clsBelow=${clsBelow}`)
  const bgTr = await page.locator('.el-table__body tr').nth(0).evaluate(el => getComputedStyle(el).backgroundColor)
  const bgTd = await page.locator('.el-table__body tr').nth(0).locator('td').first().evaluate(el => getComputedStyle(el).backgroundColor)
  check('R2W-03 待核验行底色 = 浅黄 #fcf1da（计算样式）', bgTr === 'rgb(252, 241, 218)' || bgTd === 'rgb(252, 241, 218)', `tr=${bgTr} td=${bgTd}`)

  // ── ② 每种筛选：期望集合全部现算 ────────────────────────────────────────
  const A = 9000009001 // A 医院 unit_id（库里查）
  const unitA = dbOne(`SELECT id FROM t_lqg_source_unit WHERE unit_name LIKE 'A%院' LIMIT 1`)
  const grpA = dbOne(`SELECT id FROM t_lqg_unit_group WHERE group_name LIKE '肝胆%' LIMIT 1`)
  const expUnitA = dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND source_unit_name=(SELECT unit_name FROM t_lqg_source_unit WHERE id='${unitA || A}')`)
  const expGroup = dbCodes(`SELECT s.submit_no FROM t_lqg_sample s JOIN t_lqg_ext_profile p ON p.user_id=s.submitter_id WHERE s.del_flag='0' AND p.unit_id='${unitA || A}' AND p.group_id='${grpA}'`)
  const CASES = [
    { n: '来源单位=A 医院', do: () => pickSelect('来源单位', 'A 医院'), expect: expUnitA },
    // ★ 关键用例（本轮 S1）：B 大学在 seed 里有两条 —— SJ90000006（外部）与 SJ90000009（**内部** 类器官，
    //   source_unit_id/source_unit_name 都落在 B 大学）。产品当前按**提交人的外部档案**筛，内部那条永远筛不出来，
    //   而列表里它的「来源单位」列明明写着 B 大学（见 L2r2-probe-sourceunit.mjs）。
    { n: '来源单位=B 大学（含内部录的 1009）', do: () => pickSelect('来源单位', 'B 大学'), expect: dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND source_unit_name='B 大学'`) },
    { n: '组别=肝胆外科组（先选来源单位）', do: async () => { await pickSelect('来源单位', 'A 医院'); await pickSelect('组别', '肝胆外科组') }, expect: expGroup },
    { n: '样本类别=类器官', do: () => pickSelect('样本类别', '类器官'), expect: dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND sample_kind='organoid'`) },
    { n: '提交来源=内部', do: () => pickSelect('提交来源', '内部'), expect: dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND submit_source='internal'`) },
    { n: '核验状态=待核验', do: () => pickSelect('核验状态', '待核验'), expect: dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND verify_status='pending'`) },
    { n: `收样日期区间=${d(26)}..${d(19)}`, do: () => setDateRange(d(26), d(19)), expect: dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND receive_date BETWEEN '${d(26)}' AND '${d(19)}'`) },
    { n: '组织类型=肝组织（模糊）', do: () => fillFilter('组织类型', '肝组织'), expect: dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND tissue_type LIKE '%肝组织%'`) },
    { n: '内部编号=T-hli01', do: () => fillFilter('内部编号', 'T-hli01'), expect: dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND internal_no='T-hli01'`) },
    { n: '操作人=李工', do: () => fillFilter('操作人', '李工'), expect: dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND operator_name LIKE '%李工%'`) },
    // 加密列：库里是密文，精确匹配的期望按 seed 身份表（README）单行推断，并断「该行就是 1001」
    { n: '供体姓名=测试供体甲（精确）', do: () => fillFilter('供体姓名', '测试供体甲'), expect: ['SJ90000001'] },
    { n: '住院号=ZY0000001（精确）', do: () => fillFilter('住院号', 'ZY0000001'), expect: ['SJ90000001'] },
  ]
  let filterBad = 0
  for (let i = 0; i < CASES.length; i++) {
    const c = CASES[i]
    await resetFilters()
    try { await c.do() } catch (e) { check(`R2W-04.${i + 1} 筛选「${c.n}」`, false, '操作失败: ' + e.message); filterBad++; continue }
    const r = await search()
    const ok = eqSet(r.ids, c.expect)
    if (!ok) filterBad++
    await page.screenshot({ path: path.join(SHOTS, `11-filter-${i + 1}.png`) })
    check(`R2W-04.${i + 1} 筛选「${c.n}」→ 列表 id 集合 == 库内同条件集合`, ok, `got=${JSON.stringify(r.ids)} dbExpect=${JSON.stringify(c.expect)}`)
  }
  check('R2W-05 11 种筛选 UI 驱动全部与「库内现算」一致', filterBad === 0, `bad=${filterBad}/${CASES.length}`)

  // 组合筛选（库内现算）
  await resetFilters()
  await pickSelect('来源单位', 'A 医院')
  await pickSelect('核验状态', '有效')
  await pickSelect('提交来源', '外部')
  const combo = await search()
  const expCombo = dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND source_unit_name=(SELECT unit_name FROM t_lqg_source_unit WHERE id='${unitA || A}') AND verify_status='valid' AND submit_source='external'`)
  check('R2W-06 组合筛选（A 医院 + 有效 + 外部）== 库内现算', eqSet(combo.ids, expCombo), `got=${JSON.stringify(combo.ids)} db=${JSON.stringify(expCombo)}`)
  await resetFilters()

  // ── ②b r1 S1-1 复验：样本类别下拉真的有「组织样本 / 类器官」两项 ─────────
  let kindOpts = null
  try {
    await resetFilters()
    const fi = await filterItem('样本类别')
    await fi.locator('.el-select').first().click()
    await page.waitForTimeout(1200)
    kindOpts = (await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').allInnerTexts()).map(s => s.trim())
    await page.screenshot({ path: path.join(SHOTS, '11b-samplekind-dropdown.png') })
    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)
  } catch (e) { console.log('kind dropdown probe fail', e.message) }
  check('R2W-06a【r1-S1-1 复验】样本类别下拉 = 组织样本 / 类器官 两项',
    JSON.stringify(kindOpts) === JSON.stringify(['组织样本', '类器官']), `opts=${JSON.stringify(kindOpts)}`)
  // 同一路径的「行内 dict-tag」：样本类别列对 1009 必须渲染「类器官」而不是空 / 原始值
  await resetFilters()
  await pickSelect('样本类别', '类器官')
  await search()
  const rowTxt1009 = await page.locator('.el-table__body tr').first().innerText().catch(() => '')
  check('R2W-06b【r1-S1-1 复验】列表「样本类别」列对 organoid 行渲染中文标签「类器官」（dict-tag 生效）',
    /类器官/.test(rowTxt1009) && !/organoid/.test(rowTxt1009), JSON.stringify(rowTxt1009.replace(/\n/g, '|').slice(0, 120)))
  await resetFilters()

  // ── ③ 核验抽屉两个出口 ─────────────────────────────────────────────────
  const pendingNo = dbPendingAll[0]
  const pendingId = dbOne(`SELECT id FROM t_lqg_sample WHERE submit_no='${pendingNo}'`)
  const pendingRow = page.locator('.el-table__body tr').filter({ hasText: pendingNo }).first()
  await pendingRow.locator('button:has-text("核验")').click()
  await page.waitForSelector('.el-drawer:visible', { timeout: 20000 })
  await page.waitForTimeout(1200)
  const drawerText = await page.locator('.el-drawer:visible').innerText().catch(() => '')
  await page.screenshot({ path: path.join(SHOTS, '12-verify-drawer.png'), fullPage: true })
  check('R2W-07 核验抽屉「判为有效并保存」「判为无效」两个出口同时在',
    /判为有效并保存/.test(drawerText) && /判为无效/.test(drawerText), JSON.stringify(drawerText.replace(/\n/g, '|').slice(0, 160)))

  await page.locator('.el-drawer:visible').locator('button:has-text("判为有效并保存")').click()
  await page.waitForTimeout(1200)
  const stillOpen = await page.locator('.el-drawer:visible').count()
  check('R2W-08 出口 A 缺内部编号时被拦（抽屉不关）', stillOpen > 0, `open=${stillOpen}`)

  const drawer = page.locator('.el-drawer:visible')
  const dItem = async (label) => {
    const items = drawer.locator('.el-form-item')
    const n = await items.count()
    for (let i = 0; i < n; i++) {
      const t = (await items.nth(i).locator('.el-form-item__label').first().innerText().catch(() => '')).trim()
      if (t === label) return items.nth(i)
    }
    return null
  }
  const INNO_A = 'T-r2w' + String(Date.now()).slice(-6)
  await (await dItem('内部编号')).locator('input').first().fill(INNO_A)
  await (await dItem('收样日期')).locator('input').first().fill(d(1))
  await page.keyboard.press('Enter')
  await page.waitForTimeout(500)
  const [vr] = await Promise.all([
    page.waitForResponse(r => r.url().includes(`/lqg/sample/${pendingId}/verify`), { timeout: 25000 }).catch(() => null),
    drawer.locator('button:has-text("判为有效并保存")').click(),
  ])
  const vbody = vr ? await vr.json().catch(() => null) : null
  await page.waitForTimeout(2000)
  const dbAfterValid = dbOne(`SELECT verify_status || '|' || COALESCE(internal_no,'-') FROM t_lqg_sample WHERE id=${pendingId}`)
  check('R2W-09 出口 A：判有效成功（code=200 且库内 valid|' + INNO_A + '）',
    vbody && vbody.code === 200 && dbAfterValid === `valid|${INNO_A}`, `code=${vbody && vbody.code} db=${dbAfterValid}`)
  await page.screenshot({ path: path.join(SHOTS, '13-after-valid.png'), fullPage: true })

  const pend2 = dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND verify_status='pending' AND submit_no<>'${pendingNo}'`)
  if (!pend2.length) throw new Error('库内只剩一条待核验样本，出口 B 无样本可判（本脚本的前置是 reseed 后的干净库）')
  const no2 = pend2[0]
  const pend2Id = dbOne(`SELECT id FROM t_lqg_sample WHERE submit_no='${no2}'`)
  await (await page.locator('.el-table__body tr').filter({ hasText: no2 }).first()).locator('button:has-text("核验")').click()
  await page.waitForSelector('.el-drawer:visible', { timeout: 20000 })
  await page.waitForTimeout(1200)
  await page.locator('.el-drawer:visible').locator('button:has-text("判为无效")').first().click()
  await page.waitForTimeout(900)
  await page.screenshot({ path: path.join(SHOTS, '14-invalid-reason-dialog.png'), fullPage: true })
  const dlg = page.locator('.el-dialog:visible').first()
  await dlg.locator('button:has-text("判为无效")').click()
  await page.waitForTimeout(900)
  const dlgStill = await page.locator('.el-dialog:visible').count()
  check('R2W-10 出口 B：原因为空被拦（弹窗不关）', dlgStill > 0, `open=${dlgStill}`)
  const REASON = 'R2W 独立复验原因：缺住院号'
  await dlg.locator('textarea, input').first().fill(REASON)
  const [vr2] = await Promise.all([
    page.waitForResponse(r => r.url().includes(`/lqg/sample/${pend2Id}/verify`), { timeout: 25000 }).catch(() => null),
    dlg.locator('button:has-text("判为无效")').click(),
  ])
  const vbody2 = vr2 ? await vr2.json().catch(() => null) : null
  await page.waitForTimeout(2200)
  const dbAfterInv = dbOne(`SELECT verify_status || '|' || COALESCE(invalid_reason,'-') FROM t_lqg_sample WHERE id=${pend2Id}`)
  check('R2W-11 出口 B：判无效成功（code=200 且库内 invalid + 原因原样）',
    vbody2 && vbody2.code === 200 && dbAfterInv === `invalid|${REASON}`, `code=${vbody2 && vbody2.code} db=${dbAfterInv}`)
  await page.screenshot({ path: path.join(SHOTS, '15-after-invalid.png'), fullPage: true })

  // ── ④ 范围外线索复核：工作台列表行是否渲染无效原因 ───────────────────────
  await resetFilters()
  const invNo = dbCodes(`SELECT submit_no FROM t_lqg_sample WHERE ${VIS} AND verify_status='invalid'`)[0]
  const invReason = dbOne(`SELECT invalid_reason FROM t_lqg_sample WHERE submit_no='${invNo}'`)
  const invRowText = await page.locator('.el-table__body tr').filter({ hasText: invNo }).first().innerText().catch(() => '')
  const listHasReason = invRowText.includes(invReason)
  await page.screenshot({ path: path.join(SHOTS, '16-invalid-row-reason.png'), fullPage: true })
  // 抽屉（详情）里必须能看到原因 —— 这是工作台唯一的入口
  const invRow = page.locator('.el-table__body tr').filter({ hasText: invNo }).first()
  await invRow.locator('button:has-text("编辑")').first().click({ timeout: 10000 }).catch(e => console.log('open drawer fail', e.message))
  await page.waitForTimeout(2000)
  const drawerInv = await page.locator('.el-drawer:visible').innerText({ timeout: 8000 }).catch(() => '')
  await page.screenshot({ path: path.join(SHOTS, '17-invalid-drawer-reason.png'), fullPage: true })
  const drawerHasReason = drawerInv.includes(invReason)
  check('R2W-12【线索复核】工作台列表行渲染无效原因 = NO（行内无原因；抽屉/详情里可见 = ' + drawerHasReason + '）',
    listHasReason === false,
    `row=${JSON.stringify(invRowText.replace(/\n/g, '|').slice(0, 140))} reason=${JSON.stringify(invReason)} drawerHasReason=${drawerHasReason}`)

  check('R2W-13 全流程无未捕获前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
} catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 3).join(' | ') : String(e))
} finally {
  const failed = results.filter(r => !r.ok)
  console.log(`\n== D2-r2-L2-web ${results.length - failed.length}/${results.length} 通过 ==`)
  console.log(`SHOTS=${SHOTS}`)
  await browser.close()
  process.exit(failed.length === 0 ? 0 : 1)
}
