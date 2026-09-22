/**
 * D3 / r2 / L2 —— issue #147 首验：核验抽屉在 verify 模式下渲染了哪些可填项？填进去保存后有没有落库？
 *   ① extA 走真实 UI 建一条待核验外部送样（拿一条「所挂样本已核验有效」的行，判有效按钮才可点）
 *   ② 工作台打开核验抽屉 → DOM 取证：逐项列 label + 控件类型 + disabled/readonly
 *   ③ 真的往「工序时间 / 染色 / marker / 操作人 / 备注」里填值
 *   ④ 点「判为有效并保存」→ 抓 /verify 请求体 + 库内对照（这些字段落库了吗）
 * 不读图、不 mock。跑法：node doc/waves/regression/D3/L2-r2-issue147.mjs
 */
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import { chromium, WEB, mockLogin, resetToLogin, gotoPage, sleep, shotsDir, makeChecker, dbOne, WS } from './L2-lib.mjs'

const S = shotsDir('r2-issue147')
const { check, finish } = makeChecker('D3-r2-L2-issue147')
const BLOCK = 'T-r2-147'
const SAMPLE_ID = '9000001001'
const SRC = '.wd-input.is-cell, .wd-cell'
const mpRowIdx = (page, label) => page.evaluate(([sel, lb]) => [...document.querySelectorAll(sel)]
  .findIndex(e => ((e.querySelector('.wd-input__label-inner, .wd-cell__title') || {}).innerText || '').trim() === lb), [SRC, label])

execFileSync('bash', ['doc/verify/reseed.sh', '--yes'], { cwd: WS, encoding: 'utf8', stdio: 'ignore' })

const browser = await chromium.launch({ headless: true })
const mp = await (await browser.newContext({ viewport: { width: 430, height: 900 } })).newPage()
const web = await (await browser.newContext({ viewport: { width: 1680, height: 1000 } })).newPage()
const errs = []
web.on('pageerror', e => errs.push('web:' + e.message))
const verifyReqs = []
web.on('request', r => {
  if (/\/lqg\/embed\/\d+\/verify/.test(r.url())) verifyReqs.push(r.postData() || '')
})

try {
  // ① extA 真实 UI 建待核验送样
  await resetToLogin(mp)
  await mockLogin(mp, 'extA')
  await gotoPage(mp, 'pages/index/index')
  await mp.waitForSelector('.lqg-tile', { timeout: 20000 })
  await sleep(900)
  await mp.locator('.lqg-tile', { hasText: '石蜡包埋送样记录' }).first().click()
  await sleep(3000)
  await mp.waitForSelector('.emb', { timeout: 20000 })
  await sleep(1200)
  const si = await mpRowIdx(mp, '选择样本')
  if (si < 0) throw new Error('找不到「选择样本」')
  await mp.locator(SRC).nth(si).click()
  await mp.waitForSelector('.spx__item', { timeout: 20000 })
  await sleep(1500)
  await mp.locator('.spx__item', { hasText: 'SJ90000001' }).first().click()
  await sleep(1500)
  const ti = await mpRowIdx(mp, '样本类型')
  await mp.locator(SRC).nth(ti).locator('input,textarea').first().fill('组织')
  await sleep(400)
  await mp.locator('.emb__btn', { hasText: '提交' }).first().click()
  await sleep(3500)
  const newId = dbOne(`SELECT id FROM t_lqg_embed WHERE sample_id=${SAMPLE_ID} AND verify_status='pending' AND del_flag='0'`)
  check('#147-00 前置：extA 真实 UI 建出一条 pending 外部送样（挂已核验有效的 1001）',
    /^\d+$/.test(newId), `id=${newId}`)

  // ② 工作台打开核验抽屉
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

  // ③ DOM 取证：verify 模式下渲染了哪些可填项 + 是否禁用
  const items = await web.evaluate(() => {
    const dr = [...document.querySelectorAll('.el-drawer')].find(d => d.getBoundingClientRect().height > 0)
    return [...dr.querySelectorAll('.el-form-item')].map(fi => {
      const label = (fi.querySelector('.el-form-item__label') || {}).innerText || ''
      const ctrl = fi.querySelector('input,textarea')
      const segs = [...fi.querySelectorAll('.el-checkbox-button, .el-radio-button')].map(b => b.innerText.trim())
      return {
        label: label.trim(),
        ctrl: ctrl ? ctrl.tagName.toLowerCase() : (segs.length ? 'segb' : 'none'),
        disabled: ctrl ? !!ctrl.disabled : null,
        readonly: ctrl ? !!ctrl.readOnly : null,
        value: ctrl ? String(ctrl.value || '').slice(0, 30) : '',
        segs
      }
    })
  })
  console.log('#147 抽屉 form-item 清单：\n' + items.map(i => `  [${i.label}] ${i.ctrl} disabled=${i.disabled} readonly=${i.readonly} val="${i.value}" segs=${JSON.stringify(i.segs)}`).join('\n'))
  const alertTxt = (await web.locator('.el-drawer:visible .el-alert').first().innerText().catch(() => '')).replace(/\n/g, '|')
  const sections = await web.locator('.el-drawer:visible .lqg-embed-drawer__section').allInnerTexts()
  await web.screenshot({ path: path.join(S, '147-01-verify-drawer-dom.png'), fullPage: true })
  const editable = items.filter(i => i.ctrl && i.ctrl !== 'none' && !i.disabled && !i.readonly)
  const hasProcess = items.some(i => /脱水时间|组织收样时间|切片时间/.test(i.label))
  const hasStain = items.some(i => /染色/.test(i.label))
  const hasMarker = items.some(i => /marker/i.test(i.label))
  check('#147-01 verify 抽屉除了 石蜡块编号 之外，还渲染了工序/染色/marker/操作人/备注等可填项（DOM 取证）',
    hasProcess && hasStain && hasMarker && editable.length > 3,
    `可填控件=${editable.length} 工序=${hasProcess} 染色=${hasStain} marker=${hasMarker} sections=${JSON.stringify(sections)}`)
  check('#147-02 这些输入在 verify 模式下没有被禁用（disabled=false / readonly=false）',
    editable.filter(i => /脱水时间|染色|marker|操作人|备注/.test(i.label)).length >= 3,
    `editableLabels=${JSON.stringify(editable.map(i => i.label))}`)

  // ④ 真的填值
  const findFi = async (re) => {
    const cnt = await web.locator('.el-drawer:visible .el-form-item').count()
    for (let i = 0; i < cnt; i++) {
      const fi = web.locator('.el-drawer:visible .el-form-item').nth(i)
      const lb = (await fi.locator('.el-form-item__label').first().innerText().catch(() => '')).trim()
      if (re.test(lb)) return fi
    }
    return null
  }
  const dehy = await findFi(/脱水时间/)
  await dehy.locator('input').first().fill('2026-09-01')
  await dehy.locator('input').first().press('Enter')
  // ★ 不要按 Escape：el-drawer 默认 close-on-press-escape=true，会把整个抽屉关掉
  await web.locator('.el-drawer:visible .lqg-embed-drawer__section').first().click().catch(() => {})
  await sleep(700)
  const dehyVal = await (await findFi(/脱水时间/)).locator('input').first().inputValue()
  const stainFi = await findFi(/^染色/)
  await stainFi.locator('.el-checkbox-button', { hasText: /HE/ }).first().click().catch(async () => {
    await stainFi.locator('.el-checkbox-button').first().click()
  })
  await sleep(500)
  const segState = await stainFi.locator('.el-checkbox-button.is-checked').allInnerTexts().catch(() => [])
  // marker：先加一行再填名
  const markerFi = await findFi(/marker/i)
  const hasMarkerRow = await markerFi.locator('.lqg-embed-drawer__marker-name').count()
  if (!hasMarkerRow) await markerFi.locator('button:has-text("新增")').first().click().catch(() => {})
  await sleep(400)
  const markerRows = await markerFi.locator('.lqg-embed-drawer__marker-name').count()
  if (markerRows > 0) {
    await markerFi.locator('.lqg-embed-drawer__marker-name input').first().fill('M-147')
    const exprSeg = markerFi.locator('.lqg-embed-drawer__marker-row').first().locator('.el-radio-button')
    if (await exprSeg.count()) await exprSeg.first().click().catch(() => {})
  }
  await sleep(400)
  const opFi = await findFi(/操作人/)
  if (opFi) await opFi.locator('input').first().fill('测-147操作人')
  const rmFi = await findFi(/备注/)
  if (rmFi) await rmFi.locator('textarea').first().fill('测-147备注')
  const blockFi = await findFi(/石蜡块编号/)
  await blockFi.locator('input').first().fill(BLOCK)
  await sleep(500)
  const filledState = {
    dehy: dehyVal,
    stain: segState,
    marker: markerRows > 0 ? await markerFi.locator('.lqg-embed-drawer__marker-name input').first().inputValue() : '__NO_ROW__',
    op: opFi ? await opFi.locator('input').first().inputValue() : '__NO_FI__',
    remark: rmFi ? await rmFi.locator('textarea').first().inputValue() : '__NO_FI__',
    block: await blockFi.locator('input').first().inputValue()
  }
  console.log('#147 已填：' + JSON.stringify(filledState))
  await web.screenshot({ path: path.join(S, '147-02-fields-filled.png'), fullPage: true })
  check('#147-03 工序时间/染色/marker/操作人/备注 都真的填进去了（UI 接受输入）',
    filledState.dehy === '2026-09-01' && filledState.stain.length > 0 && filledState.marker !== '__NO_ROW__' && filledState.marker !== '' && filledState.remark === '测-147备注',
    `filled=${JSON.stringify(filledState)}`)

  // ⑤ 点判为有效并保存 → 抓请求体 + 库内对照
  verifyReqs.length = 0
  await web.locator('.el-drawer:visible .el-drawer__footer button', { hasText: /判为有效/ }).first().click()
  await sleep(3500)
  const rowAfter = dbOne(`SELECT verify_status||'|'||COALESCE(paraffin_block_no,'-')||'|'||COALESCE(dehydrate_time::text,'NULL')||'|'||COALESCE(stain_types,'NULL')||'|'||COALESCE(operator_name,'NULL')||'|'||COALESCE(remark,'NULL')||'|'||COALESCE(embed_by,'NULL') FROM t_lqg_embed WHERE id=${newId}`)
  const markersAfter = dbOne(`SELECT count(*) FROM t_lqg_embed_marker WHERE embed_id=${newId}`)
  const toasts = (await web.locator('.el-message').allInnerTexts().catch(() => [])).join(' / ')
  await web.screenshot({ path: path.join(S, '147-03-after-verify.png'), fullPage: true })
  console.log('#147 verify 请求体：' + JSON.stringify(verifyReqs))
  console.log('#147 库内行：' + rowAfter + '  markers=' + markersAfter)
  check('#147-04 点「判为有效并保存」成功（核验本身走通：valid + 编号），但发出去的请求体只含契约 3 键的子集',
    rowAfter.startsWith(`valid|${BLOCK}|`) && verifyReqs.length === 1 && (() => {
      const k = Object.keys(JSON.parse(verifyReqs[0]))
      return k.every(x => ['action', 'paraffinBlockNo', 'reason'].includes(x)) && !k.some(x => /Time|stain|marker|operator|remark|embedBy/i.test(x))
    })(),
    `row=${rowAfter} body=${verifyReqs[0]}`)
  const [, , dehyDb, stainDb, opDb, remarkDb] = rowAfter.split('|')
  check('#147-05 抽屉里填的 脱水时间 被静默丢弃（库内仍 NULL）',
    dehyDb === 'NULL', `row=${rowAfter}`)
  check('#147-06 抽屉里填的 染色 / 操作人 / 备注 / marker 也被丢弃（库内未变、marker 0 行）',
    stainDb === 'NULL' && opDb === 'NULL' && remarkDb === 'NULL' && markersAfter === '0',
    `row=${rowAfter} markers=${markersAfter}`)
  check('#147-07 抽屉里的提示文案是否明确告知「本次填写不保存」',
    /之后照常补工序与染色/.test(alertTxt) && !/不保存|不会保存|请到编辑/.test(alertTxt),
    `alert="${alertTxt}"`)
  check('#147-08 无 JS 运行时报错', errs.length === 0, errs.join(' || '))
} catch (e) {
  check('#147-EXCEPTION', false, String(e && e.stack || e).slice(0, 700))
}
finish()
await browser.close()
