/**
 * D2 / r2 / L2 微探针（**本轮新发现**）：工作台样本总表「来源单位」筛选筛不出**内部录的**样本。
 *
 * 现象：样本总表里 SJ90000009（内部 · 类器官 · T-oco01）的「来源单位」列显示「B 大学」，
 *       但把筛选「来源单位」选成「B 大学」后这张表里没有它（接口 `sourceUnitId=9000009002` 只回 SJ90000006）。
 * 口径对照：`SampleQueryService.list` 把「来源单位 / 组别」一律走 `t_lqg_ext_profile`（提交人的外部档案）取 user_id 集合，
 *       于是 `submit_source='internal'` 的行（提交人是内部账号、没有外部档案）永远筛不出来 —— 哪怕样本行自己的
 *       `source_unit_id / source_unit_name` 就落在该单位上（seed 1009 = B 大学 / 9000009002）。
 * 权威：`UI:admin.sample.list` 的表格列里就有「来源单位」，筛选区也有；`doc/requirements.yaml:709` 甲方原话
 *       「你想根据来源单位全部是 1 的去看也可以」；`FLOW:F-SAMPLE-02.step5` 导出的范围 = 当前筛选结果（同一筛选被 D3 复用）。
 *
 * 只读不改产品。跑法：node doc/waves/regression/D2/L2r2-probe-sourceunit.mjs
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
const dbOne = sql => execFileSync('psql', ['-h', '127.0.0.1', '-p', '5433', '-U', 'lqg', '-d', 'lqg_dev', '-Atc', sql],
  { env: { ...process.env, PGPASSWORD: 'lqg_dev_pwd' }, encoding: 'utf8' }).trim()

const out = {}
const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 950 } })).newPage()
try {
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => localStorage.clear()).catch(() => {})
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login-form input', { timeout: 30000 })
  const i = page.locator('.login-form input')
  await i.nth(0).fill('lqgadmin')
  await i.nth(1).fill('admin123')
  await page.click('.login-form button')
  await page.waitForSelector('.el-menu', { timeout: 30000 })
  await page.waitForTimeout(2000)
  await page.goto(`${BASE}/sample`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.el-table__body tr', { timeout: 30000 })
  await page.waitForTimeout(1500)

  // 先按「内部编号 = T-oco01」筛出 1009，证明它确实在表里、且「来源单位」列显示 B 大学
  const findItem = async (label) => {
    const items = page.locator('.app-main .el-form-item')
    for (let k = 0; k < await items.count(); k++) {
      const t = (await items.nth(k).locator('.el-form-item__label').first().innerText().catch(() => '')).trim()
      if (t.startsWith(label)) return items.nth(k)
    }
    return null
  }
  const inoItem = await findItem('内部编号')
  await inoItem.locator('input').first().fill('T-oco01')
  await Promise.all([
    page.waitForResponse(r => r.url().includes('/lqg/sample/list'), { timeout: 25000 }),
    page.click('.app-main button:has-text("搜索")'),
  ])
  await page.waitForTimeout(1200)
  out.row1009_by_internalNo = (await page.locator('.el-table__body').innerText().catch(() => '')).replace(/\n/g, '|')
  await page.screenshot({ path: path.join(SHOTS, '18-sourceunit-via-internalNo.png'), fullPage: true })
  await page.click('.app-main button:has-text("重置")')
  await page.waitForTimeout(1200)

  // 选「来源单位 = B 大学」→ 搜索
  const items = page.locator('.app-main .el-form-item')
  let fi = null
  for (let k = 0; k < await items.count(); k++) {
    const t = (await items.nth(k).locator('.el-form-item__label').first().innerText().catch(() => '')).trim()
    if (t.startsWith('来源单位')) { fi = items.nth(k); break }
  }
  await fi.locator('.el-select').first().click()
  await page.waitForTimeout(600)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: 'B 大学' }).first().click()
  await page.waitForTimeout(500)
  const [resp] = await Promise.all([
    page.waitForResponse(r => r.url().includes('/lqg/sample/list'), { timeout: 25000 }),
    page.click('.app-main button:has-text("搜索")'),
  ])
  const body = await resp.json().catch(() => null)
  const rows = (body && body.rows) || []
  out.filter_B_university = { total: body && body.total, submitNos: rows.map(r => String(r.submitNo)) }
  const tableText = await page.locator('.el-table__body').innerText().catch(() => '')
  out.tableHas1009 = tableText.includes('SJ90000009')
  await page.screenshot({ path: path.join(SHOTS, '19-sourceunit-filter-B.png'), fullPage: true })

  out.db = {
    s1009: dbOne("SELECT submit_source || '|' || source_unit_id || '|' || source_unit_name || '|' || internal_no FROM t_lqg_sample WHERE submit_no='SJ90000009'"),
    unitB: dbOne("SELECT id FROM t_lqg_source_unit WHERE unit_name='B 大学'"),
    dbRowsWithUnitB: dbOne("SELECT string_agg(submit_no, ',' ORDER BY submit_no) FROM t_lqg_sample WHERE del_flag='0' AND source_unit_name='B 大学'"),
  }
  console.log(JSON.stringify(out, null, 1))
  console.log('VERDICT ' + ((!out.tableHas1009 && /9000009002/.test(out.db.s1009) && out.db.dbRowsWithUnitB.includes('SJ90000009')) ? 'CONFIRMED_BUG' : 'NOT_REPRODUCED'))
} catch (e) { console.log('ERR', e.message, JSON.stringify(out, null, 1)) } finally { await browser.close() }
