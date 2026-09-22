/**
 * D2 / r4 / L2 —— 组 2：工作台样本总表。
 *   ① 九种筛选在 UI 上各点一遍，与**我在本轮 accept/API 上独立取的 id 集合**对照（期望值不是抄来的常量）
 *   ② 待核验行浅黄置顶（DOM class + 计算样式色值）
 *   ③ 核验抽屉两个出口（判有效 / 判无效）各走一遍（截图 + 库里副作用）
 * 本片自写、独立复跑；不引用 D2 目录既有脚本的结论。
 * 前置：后端 8081 + reseed + plus-ui 8082。跑法：node doc/waves/regression/D2/L2r4-g2-web-table.mjs
 */
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import { chromium, WEB, WS, makeChecker, shotsDir, dbOne, sleep } from './L2r4-lib.mjs'
import { loadState, saveState } from './L2r4-state.mjs'

const S = shotsDir('L2r4-web-g2')
const { check, finish } = makeChecker('D2-r4-L2-g2')
const T = 'T-l2r4a01'
const REASON = 'D2 r4 L2 判无效：缺住院号'

function apiIds(qs) {
  const out = execFileSync('bash', ['doc/verify/api.sh', '--as', 'staff', 'GET', `/lqg/sample/list?pageSize=100&${qs}`],
    { cwd: WS, encoding: 'utf8' })
  return JSON.parse(out).rows.map(r => String(r.id)).sort()
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 950 } })).newPage()
const pageErrors = []
page.on('pageerror', e => pageErrors.push(e.message))

/** 行的库内 id：**按页面上真实渲染出来的送检单号回库查**（不靠 id 推算，自动生成的 id 不在小号段） */
const idOfRow = async (row) => {
  const t = await row.innerText().catch(() => '')
  const m = t.match(/SJ\d+/)
  if (!m) return null
  const id = dbOne(`SELECT id FROM t_lqg_sample WHERE submit_no='${m[0]}' LIMIT 1`)
  return /^\d+$/.test(id) ? id : null
}
async function rowIds() {
  const rows = page.locator('.el-table__body tr')
  const n = await rows.count()
  const out = []
  for (let i = 0; i < n; i++) out.push(await idOfRow(rows.nth(i)))
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
  await sleep(500)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: optionText }).first().click({ timeout: 8000 })
  await sleep(400)
}
async function fillFilter(label, val) {
  const fi = await filterItem(label)
  if (!fi) throw new Error(`找不到筛选项 ${label}`)
  await fi.locator('input').first().fill(val)
}
async function setDateRange(b, e) {
  const fi = await filterItem('收样日期')
  const ins = fi.locator('input')
  await ins.nth(0).click(); await sleep(300)
  await ins.nth(0).fill(b); await page.keyboard.press('Enter'); await sleep(400)
  await ins.nth(1).fill(e); await page.keyboard.press('Enter'); await sleep(400)
  await page.keyboard.press('Escape'); await sleep(300)
}
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
  await sleep(1300)
}
const eqSet = (a, b) => Array.isArray(a) && Array.isArray(b) && JSON.stringify(a) === JSON.stringify(b)

try {
  // ── 登录 ──────────────────────────────────────────────────────────────
  await page.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => localStorage.clear()).catch(() => {})
  await page.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login-form input', { timeout: 30000 })
  const ins = page.locator('.login-form input')
  await ins.nth(0).fill('lqgadmin')
  await ins.nth(1).fill('admin123')
  await page.click('.login-form button')
  await page.waitForSelector('.el-menu', { timeout: 30000 })
  await sleep(2000)
  await page.locator('.el-menu-item:has-text("样本总表")').first().click({ timeout: 15000 })
  await page.waitForResponse(r => r.url().includes('/lqg/sample/list'), { timeout: 25000 }).catch(() => {})
  await sleep(1500)
  check('G2-00 从侧边栏进入样本总表', /sample/.test(page.url()), `url=${page.url()}`)
  await page.screenshot({ path: path.join(S, 'g2-00-list.png'), fullPage: true })

  // ── ① 待核验浅黄置顶 ──────────────────────────────────────────────────
  const ids = await rowIds()
  const rowsLoc = page.locator('.el-table__body tr')
  const pendDb = dbOne("SELECT string_agg(id::text, ',' ORDER BY COALESCE(update_time,create_time) DESC, id DESC) FROM t_lqg_sample WHERE del_flag='0' AND verify_status='pending'")
  check('G2-01 待核验行置顶：前 N 行 = 库里 pending 集合的 DB 排序',
    pendDb.split(',').every((id, i) => ids[i] === id) && ids.length > 0,
    `ui前几行=${JSON.stringify(ids.slice(0, 4))} dbPending=${pendDb}`)
  const cls0 = await rowsLoc.nth(0).getAttribute('class')
  const pendCount = Number(dbOne("SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND verify_status='pending'"))
  const clsN = await rowsLoc.nth(pendCount).getAttribute('class')
  const bgTr = await rowsLoc.nth(0).evaluate(el => getComputedStyle(el).backgroundColor)
  const bgTd = await rowsLoc.nth(0).locator('td').first().evaluate(el => getComputedStyle(el).backgroundColor)
  check(`G2-02 待核验行样式类 lqg-sample__row-pending；第 ${pendCount + 1} 行（第一条非待核验）不是（没把整表涂黄）`,
    /lqg-sample__row-pending/.test(cls0 || '') && !/lqg-sample__row-pending/.test(clsN || ''), `cls0=${cls0} cls[${pendCount}]=${clsN}`)
  check('G2-03 待核验行底色 = 浅黄（计算样式 #fcf1da / rgb(252, 241, 218)）',
    bgTr === 'rgb(252, 241, 218)' || bgTd === 'rgb(252, 241, 218)', `tr=${bgTr} td=${bgTd}`)

  // ── ② 九种筛选（期望集合 = 本轮 API 独立取）────────────────────────────
  const today = new Date()
  const d = n => { const x = new Date(today); x.setDate(x.getDate() - n); return x.toISOString().slice(0, 10) }
  const B = d(26), E = d(19)
  const CASES = [
    { n: '全量（不筛）', qs: '', do: async () => {} },
    { n: '来源单位=A 医院', qs: 'sourceUnitId=9000009001', do: () => pickSelect('来源单位', 'A 医院') },
    // 组别控件依赖来源单位（页面上 sourceUnitId 为空时组别禁用），所以 UI 这一路必须先选单位；
    // 对照的 API 查询用同一对条件（accept 里 SAMPLE-WEB-001 钉死的就是这一对）。
    { n: '组别=肝胆外科组（+来源单位 A 医院）', qs: 'sourceUnitId=9000009001&groupId=9000009101', do: async () => { await pickSelect('来源单位', 'A 医院'); await pickSelect('组别', '肝胆外科组') } },
    { n: '样本类别=类器官', qs: 'sampleKind=organoid', do: () => pickSelect('样本类别', '类器官') },
    { n: '提交来源=内部', qs: 'submitSource=internal', do: () => pickSelect('提交来源', '内部') },
    { n: '核验状态=待核验', qs: 'verifyStatus=pending', do: () => pickSelect('核验状态', '待核验') },
    { n: `收样日期 ${B}..${E}`, qs: `receiveDateBegin=${B}&receiveDateEnd=${E}`, do: () => setDateRange(B, E) },
    { n: '组合：A 医院+有效+外部', qs: 'sourceUnitId=9000009001&verifyStatus=valid&submitSource=external', do: async () => { await pickSelect('来源单位', 'A 医院'); await pickSelect('核验状态', '有效'); await pickSelect('提交来源', '外部') } },
    { n: '供体姓名=测试供体甲（精确）', qs: `donorName=${encodeURIComponent('测试供体甲')}`, do: () => fillFilter('供体姓名', '测试供体甲'), minus1010: true },
  ]
  let bad = 0
  for (let i = 0; i < CASES.length; i++) {
    const c = CASES[i]
    await resetFilters()
    try { await c.do() } catch (e) { check(`G2-04.${i + 1} 筛选「${c.n}」`, false, '操作失败: ' + e.message); bad++; continue }
    const r = await search()
    let expect = apiIds(c.qs).filter(x => x !== '9000001010')
    const got = (r.ids || []).filter(x => x !== '9000001010')
    const ok = eqSet(got, expect) && !(r.ids || []).includes('9000001010')
    if (!ok) bad++
    await page.screenshot({ path: path.join(S, `g2-04-filter-${i + 1}.png`) })
    check(`G2-04.${i + 1} 筛选「${c.n}」→ UI id 集合 = 本轮 API 集合，且软删 1010 不出现`, ok,
      `ui=${JSON.stringify(got)} api=${JSON.stringify(expect)} total=${r.total}`)
  }
  check('G2-05 九种筛选 UI 驱动全部一致', bad === 0, `bad=${bad}/${CASES.length}`)
  await resetFilters()

  // ── ③ 核验抽屉 · 出口一：判为有效（拿 seed 的 1007 走一遍）─────────────
  const row7 = page.locator('.el-table__body tr').filter({ hasText: 'SJ90000007' }).first()
  await row7.locator('button:has-text("核验")').click()
  await page.waitForSelector('.el-drawer:visible', { timeout: 20000 })
  await sleep(1800)
  await page.screenshot({ path: path.join(S, 'g2-06-drawer-verify.png'), fullPage: true })
  const drawerBody = await page.locator('.el-drawer:visible').first().innerText()
  check('G2-06 核验抽屉打开：两个出口「判为有效并保存」「判为无效」都在',
    /判为有效并保存/.test(drawerBody) && /判为无效/.test(drawerBody), drawerBody.replace(/\n/g, '|').slice(0, 160))
  const dItems = page.locator('.el-drawer:visible .el-form-item')
  const dn = await dItems.count()
  for (let i = 0; i < dn; i++) {
    const lb = (await dItems.nth(i).locator('.el-form-item__label').first().innerText().catch(() => '')).trim()
    if (lb === '内部编号') await dItems.nth(i).locator('input').first().fill(T)
    if (lb === '收样日期') {
      const inp = dItems.nth(i).locator('input').first()
      if (!(await inp.inputValue())) {
        await inp.click(); await sleep(400)
        await inp.fill(today.toISOString().slice(0, 10)); await page.keyboard.press('Enter'); await sleep(400)
      }
    }
  }
  await page.locator('.el-drawer:visible button:has-text("判为有效并保存")').first().click()
  await sleep(3000)
  await page.screenshot({ path: path.join(S, 'g2-07-verify-valid.png'), fullPage: true })
  const rowValid = dbOne(`SELECT verify_status || '|' || COALESCE(internal_no,'-') || '|' || COALESCE(receive_date::text,'-') FROM t_lqg_sample WHERE id=9000001007`)
  check('G2-07 判为有效：库里 1007 = valid + 我填的内部编号 + 收样日期有值',
    rowValid.startsWith(`valid|${T}|`) && !rowValid.endsWith('|-'), `row=${rowValid}`)
  await page.keyboard.press('Escape'); await sleep(600)

  // ── ③ 核验抽屉 · 出口二：判为无效 —— 打的是 G1a 刚交的那一条 ────────────
  const st = loadState()
  check('G2-08 拿到 G1a 提交的样本 id / 单号（同一条记录贯穿全流程）',
    !!st.NEW_ID && dbOne(`SELECT verify_status FROM t_lqg_sample WHERE id=${st.NEW_ID}`) === 'pending',
    `state=${JSON.stringify(st)} db=${dbOne(`SELECT verify_status FROM t_lqg_sample WHERE id=${st.NEW_ID}`)}`)
  await resetFilters()
  const rowNew = page.locator('.el-table__body tr').filter({ hasText: st.SUBMIT_NO }).first()
  await rowNew.locator('button:has-text("核验")').click()
  await page.waitForSelector('.el-drawer:visible', { timeout: 20000 })
  await sleep(1500)
  await page.locator('.el-drawer:visible button:has-text("判为无效")').first().click()
  await page.waitForSelector('.el-dialog:visible textarea, .el-dialog:visible input', { timeout: 15000 })
  await sleep(900)
  await page.screenshot({ path: path.join(S, 'g2-08-invalid-dialog.png'), fullPage: true })
  const dlg = page.locator('.el-dialog:visible').first()
  await dlg.locator('textarea, input').first().fill(REASON)
  await dlg.locator('button:has-text("判为无效")').first().click()
  await sleep(3000)
  await page.screenshot({ path: path.join(S, 'g2-09-verified-invalid.png'), fullPage: true })
  const rowInvalid = dbOne(`SELECT verify_status || '|' || COALESCE(invalid_reason,'-') FROM t_lqg_sample WHERE id=${st.NEW_ID}`)
  check('G2-09 判为无效：库里这条 = invalid + 我写的原话原因',
    rowInvalid === `invalid|${REASON}`, `row=${rowInvalid}`)
  saveState({ ...st, REASON, T_INTERNAL: T })

  check('G2-10 工作台全程无未捕获前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
} catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 5).join(' | ') : String(e))
} finally {
  const badc = finish()
  console.log(`SHOTS=${S}`)
  await browser.close()
  process.exit(badc === 0 ? 0 : 1)
}
