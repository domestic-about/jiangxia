/**
 * D2 / r1 / L2 —— 工作台样本总表（group 2）。
 *
 * 只验不修；不改产品代码；不覆盖 D1 的 shots。
 * 前置：后端 8081（dev + --api-decrypt.enabled=false）+ reseed + plus-ui dev 8082。
 * 跑法：LQG_WEB_BASE=http://127.0.0.1:8082 node doc/waves/regression/D2/L2-web-plusui.mjs
 */
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const BASE = process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082'
const SHOTS = path.join(HERE, 'shots/L2-web')
mkdirSync(SHOTS, { recursive: true })

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

const idOfRow = async (row) => {
  const t = await row.innerText().catch(() => '')
  const m = t.match(/SJ(\d{8})/)
  return m ? String(9000001000 + (Number(m[1]) - 90000000)) : null
}

/** 行 id 列表（顺序保留） */
async function rowIds() {
  const rows = page.locator('.el-table__body tr')
  const n = await rows.count()
  const out = []
  for (let i = 0; i < n; i++) out.push(await idOfRow(rows.nth(i)))
  return out
}

/** 查询表单里的表单项（按 label 精确前缀），只在主内容区找，避开抽屉 */
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

/** 点搜索并拿到本次 /lqg/sample/list 的 id 集合（来源：接口返回 .rows[].id，转字符串） */
async function search() {
  const [resp] = await Promise.all([
    page.waitForResponse(r => r.url().includes('/lqg/sample/list'), { timeout: 25000 }),
    page.click('.app-main button:has-text("搜索")'),
  ])
  const body = await resp.json().catch(() => null)
  if (!body || !Array.isArray(body.rows)) return { ids: null, order: null, total: null }
  return { ids: body.rows.map(r => String(r.id)).sort(), order: body.rows.map(r => String(r.id)), total: body.total }
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

try {
  // ── 登录 ────────────────────────────────────────────────────────────────
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

  // ── 打开样本总表（走菜单，不 hardOpen，顺带验菜单可达） ─────────────────
  const menuOk = await page.locator('.el-menu-item:has-text("样本总表")').first().click({ timeout: 15000 }).then(() => true).catch(() => false)
  await page.waitForResponse(r => r.url().includes('/lqg/sample/list'), { timeout: 25000 }).catch(() => {})
  await page.waitForTimeout(1500)
  check('WEB-L2-00 侧边栏点「样本总表」进入页面', menuOk && /sample/.test(page.url()), `url=${page.url()}`)
  await page.screenshot({ path: path.join(SHOTS, '10-sample-list.png'), fullPage: true })

  // ── ① 待核验置顶 + 浅黄 ────────────────────────────────────────────────
  const ids = await rowIds()
  const rowsLoc = page.locator('.el-table__body tr')
  const cls0 = await rowsLoc.nth(0).getAttribute('class')
  const cls1 = await rowsLoc.nth(1).getAttribute('class')
  const cls2 = await rowsLoc.nth(2).getAttribute('class')
  check('WEB-L2-01 待核验行置顶（前两行 = 1007 / 1002，均为 pending 行样式）',
    ids[0] === '9000001007' && ids[1] === '9000001002'
    && /lqg-sample__row-pending/.test(cls0 || '') && /lqg-sample__row-pending/.test(cls1 || ''),
    `ids=${JSON.stringify(ids)} cls0=${cls0} cls1=${cls1}`)
  check('WEB-L2-02 第三行起不是待核验样式（没有把整表都涂成 pending）', !/lqg-sample__row-pending/.test(cls2 || ''), `cls2=${cls2}`)

  const bgTr = await rowsLoc.nth(0).evaluate(el => getComputedStyle(el).backgroundColor)
  const bgTd = await rowsLoc.nth(0).locator('td').first().evaluate(el => getComputedStyle(el).backgroundColor)
  const WARN = 'rgb(252, 241, 218)' // --lqg-warn-soft: #fcf1da
  check('WEB-L2-03 待核验行的底色 = 浅黄 #fcf1da（计算样式）', bgTr === WARN || bgTd === WARN, `tr=${bgTr} td=${bgTd}`)

  // ── ② 九种（本页全部 11 个）筛选逐项点一遍 ──────────────────────────────
  const today = new Date()
  const d = n => { const x = new Date(today); x.setDate(x.getDate() - n); return x.toISOString().slice(0, 10) }
  const CASES = [
    { n: '来源单位=A 医院', do: () => pickSelect('来源单位', 'A 医院'), expect: ['9000001001', '9000001002', '9000001003', '9000001004', '9000001005', '9000001007'] },
    { n: '组别=肝胆外科组（先选来源单位）', do: async () => { await pickSelect('来源单位', 'A 医院'); await pickSelect('组别', '肝胆外科组') }, expect: ['9000001001', '9000001002', '9000001003', '9000001004', '9000001007'] },
    { n: '样本类别=类器官', do: () => pickSelect('样本类别', '类器官'), expect: ['9000001009'] },
    { n: '提交来源=内部', do: () => pickSelect('提交来源', '内部'), expect: ['9000001008', '9000001009'] },
    { n: '核验状态=待核验', do: () => pickSelect('核验状态', '待核验'), expect: ['9000001002', '9000001007'] },
    { n: `收样日期区间=${d(26)}..${d(19)}`, do: () => setDateRange(d(26), d(19)), expect: ['9000001004', '9000001005'] },
    { n: '组织类型=肝组织（模糊）', do: () => fillFilter('组织类型', '肝组织'), expect: ['9000001001', '9000001003', '9000001004', '9000001007', '9000001008'] },
    { n: '内部编号=T-hli01', do: () => fillFilter('内部编号', 'T-hli01'), expect: ['9000001001'] },
    { n: '操作人=李工', do: () => fillFilter('操作人', '李工'), expect: ['9000001001', '9000001004', '9000001005', '9000001006', '9000001008', '9000001009'] },
    { n: '供体姓名=测试供体甲（精确）', do: () => fillFilter('供体姓名', '测试供体甲'), expect: ['9000001001'] },
    { n: '住院号=ZY0000001（精确）', do: () => fillFilter('住院号', 'ZY0000001'), expect: ['9000001001'] },
  ]
  let filterBad = 0
  for (let i = 0; i < CASES.length; i++) {
    const c = CASES[i]
    await resetFilters()
    try { await c.do() } catch (e) { check(`WEB-L2-04.${i + 1} 筛选「${c.n}」`, false, '操作失败: ' + e.message); filterBad++; continue }
    const r = await search()
    const ok = eqSet(r.ids, c.expect.slice().sort())
    if (!ok) filterBad++
    await page.screenshot({ path: path.join(SHOTS, `11-filter-${i + 1}.png`) })
    check(`WEB-L2-04.${i + 1} 筛选「${c.n}」→ 列表 id 集合一致`, ok, `got=${JSON.stringify(r.ids)} expect=${JSON.stringify(c.expect.slice().sort())}`)
  }
  check('WEB-L2-05 11 种筛选 UI 驱动全部与期望集合一致', filterBad === 0, `bad=${filterBad}/${CASES.length}`)

  // 组合筛选（accept 里那一条三条件）
  await resetFilters()
  await pickSelect('来源单位', 'A 医院')
  await pickSelect('核验状态', '有效')
  await pickSelect('提交来源', '外部')
  const combo = await search()
  check('WEB-L2-06 组合筛选（A 医院 + 有效 + 外部）→ {1001,1004,1005}',
    eqSet(combo.ids, ['9000001001', '9000001004', '9000001005'].sort()), `got=${JSON.stringify(combo.ids)}`)
  await resetFilters()

  // ── ②b 样本类别下拉的选项来源（字典 key 探针）+ 后端同名筛选对照 ─────────────
  await resetFilters()
  let sampleKindOpts = null
  try {
    const fi = await filterItem('样本类别')
    await fi.locator('.el-select').first().click()
    await page.waitForTimeout(1200)
    sampleKindOpts = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').allInnerTexts()
    const ddText = await page.locator('.el-select-dropdown:visible').first().innerText().catch(() => '')
    await page.screenshot({ path: path.join(SHOTS, '11b-samplekind-dropdown.png') })
    check('WEB-L2-06a 样本类别下拉有「组织样本 / 类器官」两个选项',
      sampleKindOpts.length === 2 && sampleKindOpts.some(t => /类器官/.test(t)),
      `opts=${JSON.stringify(sampleKindOpts)} dd=${JSON.stringify(ddText.replace(/\n/g, '|').slice(0, 60))}`)
    await page.keyboard.press('Escape')
    await page.waitForTimeout(300)
  } catch (e) {
    check('WEB-L2-06a 样本类别下拉有「组织样本 / 类器官」两个选项', false, '探针异常: ' + e.message)
  }
  // 根因对照：页面请求的字典 key `sample_kind` 在库里不存在，库里真名是 `lqg_sample_kind`
  const dictProbe = await page.evaluate(async () => {
    const out = {}
    for (const k of ['sample_kind', 'lqg_sample_kind']) {
      const r = await fetch(`/dev-api/system/dict/data/type/${k}`)
      const j = await r.json().catch(() => ({}))
      out[k] = (j.data || []).map(x => x.dictLabel)
    }
    return out
  }).catch(e => ({ err: String(e) }))
  check('WEB-L2-06b 字典 key 对照：页面用的 sample_kind 为空、库里真名 lqg_sample_kind 有 2 条',
    Array.isArray(dictProbe.sample_kind) && dictProbe.sample_kind.length === 0
    && Array.isArray(dictProbe.lqg_sample_kind) && dictProbe.lqg_sample_kind.length === 2,
    `probe=${JSON.stringify(dictProbe)} (api.sh 另行佐证：GET /lqg/sample/list?sampleKind=organoid → [1009]，后端正常)`)
  await resetFilters()

  // ── ③ 核验抽屉两个出口 ──────────────────────────────────────────────────
  const pendingRow = page.locator('.el-table__body tr').filter({ hasText: 'SJ90000002' }).first()
  await pendingRow.locator('button:has-text("核验")').click()
  await page.waitForSelector('.el-drawer:visible', { timeout: 20000 })
  await page.waitForTimeout(1200)
  const drawerText = await page.locator('.el-drawer:visible').innerText().catch(() => '')
  await page.screenshot({ path: path.join(SHOTS, '12-verify-drawer-1002.png'), fullPage: true })
  check('WEB-L2-07 待核验行点「核验」开抽屉，「判为有效并保存」「判为无效」两个出口同时在',
    /判为有效并保存/.test(drawerText) && /判为无效/.test(drawerText),
    JSON.stringify(drawerText.split('\n').filter(l => /判为|核验|样本/.test(l)).slice(0, 6)))

  // 出口 A：判为有效（缺内部编号必须先被拦）
  await page.locator('.el-drawer:visible').locator('button:has-text("判为有效并保存")').click()
  await page.waitForTimeout(1200)
  const stillOpen = await page.locator('.el-drawer:visible').count()
  const warnText = await page.locator('.el-drawer:visible').innerText().catch(() => '')
  check('WEB-L2-08 出口 A 缺内部编号时被拦（抽屉不关，出现必填提示）',
    stillOpen > 0 && /必填|请/.test(warnText), `open=${stillOpen}`)

  // 补上收样日期 + 内部编号 → 判为有效
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
  const di = await dItem('内部编号')
  await di.locator('input').first().fill('T-l2a01')
  const dr = await dItem('收样日期')
  await dr.locator('input').first().fill(d(1))
  await page.keyboard.press('Enter')
  await page.waitForTimeout(500)
  const [vr] = await Promise.all([
    page.waitForResponse(r => r.url().includes('/lqg/sample/9000001002/verify'), { timeout: 25000 }).catch(() => null),
    drawer.locator('button:has-text("判为有效并保存")').click(),
  ])
  const vbody = vr ? await vr.json().catch(() => null) : null
  await page.waitForTimeout(2000)
  const afterValid = await rowIds()
  const row1002 = page.locator('.el-table__body tr').filter({ hasText: 'SJ90000002' }).first()
  const t1002 = await row1002.innerText().catch(() => '')
  check('WEB-L2-09 出口 A：填内部编号后判有效成功，列表里 1002 变「有效」且不再置顶',
    vbody && vbody.code === 200 && /有效/.test(t1002) && afterValid[0] !== '9000001002',
    `code=${vbody && vbody.code} row=${JSON.stringify(t1002.replace(/\n/g, '|').slice(0, 120))} first=${afterValid[0]}`)
  await page.screenshot({ path: path.join(SHOTS, '13-after-valid.png'), fullPage: true })

  // 出口 B：判为无效（原因必填）
  const row1007 = page.locator('.el-table__body tr').filter({ hasText: 'SJ90000007' }).first()
  await row1007.locator('button:has-text("核验")').click()
  await page.waitForSelector('.el-drawer:visible', { timeout: 20000 })
  await page.waitForTimeout(1200)
  await page.locator('.el-drawer:visible').locator('button:has-text("判为无效")').first().click()
  await page.waitForTimeout(900)
  await page.screenshot({ path: path.join(SHOTS, '14-invalid-reason-dialog.png'), fullPage: true })
  const dlg = page.locator('.el-dialog:visible').first()
  const dlgText = await dlg.innerText().catch(() => '')
  // 空原因先点一次 → 必须被拦
  await dlg.locator('button:has-text("判为无效")').click()
  await page.waitForTimeout(900)
  const dlgStill = await page.locator('.el-dialog:visible').count()
  check('WEB-L2-10 出口 B：原因为空时被拦（弹窗不关）', dlgStill > 0 && /必须填原因|必填|请填|请写/.test(await dlg.innerText().catch(() => '')), `open=${dlgStill} text=${JSON.stringify((await dlg.innerText().catch(() => '')).slice(0, 80))}`)
  await dlg.locator('textarea, input').first().fill('L2 独立复验：判无效原因探针')
  const [vr2] = await Promise.all([
    page.waitForResponse(r => r.url().includes('/lqg/sample/9000001007/verify'), { timeout: 25000 }).catch(() => null),
    dlg.locator('button:has-text("判为无效")').click(),
  ])
  const vbody2 = vr2 ? await vr2.json().catch(() => null) : null
  await page.waitForTimeout(2200)
  const row1007b = page.locator('.el-table__body tr').filter({ hasText: 'SJ90000007' }).first()
  const t1007 = await row1007b.innerText().catch(() => '')
  check('WEB-L2-11 出口 B：填原因后判无效成功，列表里 1007 变「无效」',
    vbody2 && vbody2.code === 200 && /无效/.test(t1007),
    `code=${vbody2 && vbody2.code} row=${JSON.stringify(t1007.replace(/\n/g, '|').slice(0, 120))}`)
  await page.screenshot({ path: path.join(SHOTS, '15-after-invalid.png'), fullPage: true })

  check('WEB-L2-12 全流程无未捕获前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
} catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 3).join(' | ') : String(e))
} finally {
  const failed = results.filter(r => !r.ok)
  console.log(`\n== D2-r1-L2-web ${results.length - failed.length}/${results.length} 通过 ==`)
  console.log(`SHOTS=${SHOTS}`)
  await browser.close()
  process.exit(failed.length === 0 ? 0 : 1)
}
