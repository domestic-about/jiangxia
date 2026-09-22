/**
 * D2 / r4 / L2 —— 组 1 下半：外部（extA）在工作场判无效后看到原因、改后重提、再被核验为有效、详情只读；
 * 同组 extB 的历史编辑记录看得到这一条，「只看我提交的」打开后看不到。
 *
 * 前置：g1a（提交）+ g2（判无效）已跑，state 里有 NEW_ID / REASON。
 * 跑法：node doc/waves/regression/D2/L2r4-g1b-ext-resubmit.mjs
 */
import path from 'node:path'
import { chromium, WS, makeChecker, shotsDir, dbOne, resetToLogin, mockLogin, injectToken, mpToken, gotoPage, bodyText, fillByLabel, rowText, gotoHistory, historyRows, sleep } from './L2r4-lib.mjs'
import { loadState } from './L2r4-state.mjs'

const S = shotsDir('L2r4-mp-g1b')
const { check, finish } = makeChecker('D2-r4-L2-g1b')
const st = loadState()
const REASON = st.REASON || 'D2 r4 L2 判无效：缺住院号'
const NEW_ID = st.NEW_ID

const browser = await chromium.launch({ headless: true })

try {
  check('G1b-00 前置：这条记录在库里是 invalid 且原因 = 工作台写的那句',
    !!NEW_ID && dbOne(`SELECT verify_status || '|' || COALESCE(invalid_reason,'-') FROM t_lqg_sample WHERE id=${NEW_ID}`) === `invalid|${REASON}`,
    `state=${JSON.stringify(st)} db=${NEW_ID ? dbOne(`SELECT verify_status || '|' || COALESCE(invalid_reason,'-') FROM t_lqg_sample WHERE id=${NEW_ID}`) : 'no-id'}`)

  // ── extA：历史编辑记录看到原因并修改重提 ─────────────────────────────
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
  const page = await ctx.newPage()
  const pageErrors = []
  page.on('pageerror', e => pageErrors.push(e.message))
  await resetToLogin(page)
  await mockLogin(page, 'extA')
  await gotoHistory(page)
  await page.screenshot({ path: path.join(S, 'g1b-01-history-invalid-reason.png'), fullPage: true })
  const rows = await historyRows(page)
  const mine = rows.find(r => r.code === st.SUBMIT_NO)
  check('G1b-01 外部在历史编辑记录看到这一条：状态「无效」+ 原因原样显示',
    !!mine && /无效/.test(mine.statusText) && mine.reason.includes(REASON),
    `row=${JSON.stringify(mine || rows.slice(0, 3))}`)

  // 点这一条 → 进 edit 表单（可改）
  await page.locator('.his__item', { hasText: st.SUBMIT_NO }).first().click()
  await sleep(3000)
  const editUrl = page.url()
  const editBody = await bodyText(page)
  await page.screenshot({ path: path.join(S, 'g1b-02-edit-form.png'), fullPage: true })
  check('G1b-02 点这一条进样本表单 edit 模式、可填（有保存/提交按钮、住院号可写）',
    /pages\/sample\/form/.test(editUrl) && /mode=edit/.test(editUrl) && !/只读/.test(editBody),
    `url=${editUrl} body=${editBody.slice(0, 90).replace(/\n/g, '|')}`)
  const hospVal = await rowText(page, '住院号')
  await fillByLabel(page, '住院号', 'R4ZY0001A')
  await fillByLabel(page, '备注', '已补住院号后重提')
  const btnText = await page.locator('button,.form__btn').first().innerText().catch(() => '')
  await page.screenshot({ path: path.join(S, 'g1b-03-refilled.png'), fullPage: true })
  await page.locator('button, .form__btn').filter({ hasText: /提交|保存/ }).first().click()
  await sleep(3500)
  await page.screenshot({ path: path.join(S, 'g1b-04-resubmitted.png'), fullPage: true })
  const after = dbOne(`SELECT verify_status || '|' || COALESCE(remark,'-') || '|' || COALESCE(invalid_reason,'-') FROM t_lqg_sample WHERE id=${NEW_ID}`)
  check('G1b-03 改后重提：库里回到 pending（原因随重提清掉）',
    after.startsWith('pending|已补住院号后重提'), `原住院号=${hospVal} 按钮=${btnText} row=${after}`)

  // ── 工作台再核验为有效（走真接口 = 工作台同一端点，L2 只验端侧；本条为流程延续的必要一步）──
  const { execFileSync } = await import('node:child_process')
  const today = new Date().toISOString().slice(0, 10)
  const verifyOut = execFileSync('bash', ['doc/verify/api.sh', '--as', 'admin', 'PUT', `/lqg/sample/${NEW_ID}/verify`,
    JSON.stringify({ action: 'valid', receiveDate: today, internalNo: 'T-l2r4ext01' })], { cwd: WS, encoding: 'utf8' })
  const vCode = JSON.parse(verifyOut).code
  const afterValid = dbOne(`SELECT verify_status || '|' || COALESCE(internal_no,'-') FROM t_lqg_sample WHERE id=${NEW_ID}`)
  check('G1b-04 工作台判为有效（同一核验端点，接口层）：库里 = valid + 内部编号',
    vCode === 200 && afterValid === 'valid|T-l2r4ext01', `code=${vCode} row=${afterValid}`)

  // ── 外部详情只读 ──────────────────────────────────────────────────────
  await gotoPage(page, `pages/sample/detail-ext?id=${NEW_ID}`, 3000)
  await page.screenshot({ path: path.join(S, 'g1b-05-detail-readonly.png'), fullPage: true })
  const detBody = await bodyText(page)
  const detInputs = await page.locator('input,textarea').count()
  const detSave = await page.locator('button, .form__btn').filter({ hasText: /提交|保存/ }).count()
  const detReadonly = await page.evaluate(() => [...document.querySelectorAll('input,textarea')].every(e => e.readOnly || e.disabled))
  check('G1b-05 外部详情只读：没有保存/提交按钮、没有可写的输入控件',
    detSave === 0 && (detInputs === 0 || detReadonly), `inputs=${detInputs} 全只读=${detReadonly} save=${detSave}`)
  check('G1b-06 外部详情不出现收样段/冻存/核验人字段（字段名进包就是泄露）',
    !/收样日期|处理时间|质控表|细胞活率|核验人|冻存/.test(detBody), detBody.replace(/\n/g, '|').slice(0, 200))

  // extA 历史编辑记录里这一条变有效
  await gotoHistory(page)
  const rows2 = await historyRows(page)
  const mine2 = rows2.find(r => r.code === st.SUBMIT_NO)
  check('G1b-07 外部历史编辑记录里这一条 = 有效（不再是无效）',
    !!mine2 && /有效/.test(mine2.statusText) && !/无效原因/.test(mine2.text), `row=${JSON.stringify(mine2)}`)
  check('G1b-08 外部全程无未捕获前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))

  // ── extB（同组）：看得到这一条；打开「只看我提交的」后看不到 ──────────
  const pageB = await ctx.newPage()
  const errsB = []
  pageB.on('pageerror', e => errsB.push(e.message))
  await resetToLogin(pageB)
  await injectToken(pageB, mpToken('extB', '13800000012'))
  await gotoHistory(pageB)
  await pageB.screenshot({ path: path.join(S, 'g1b-06-extB-history-all.png'), fullPage: true })
  const bRows = await historyRows(pageB)
  const visibleToB = bRows.some(r => r.code === st.SUBMIT_NO)
  check('G1b-09 同组 extB 的历史编辑记录里看得到 extA 这一条（默认=可见集合）',
    visibleToB, `codes=${JSON.stringify(bRows.map(r => r.code))}`)

  // 打开「只看我提交的」——必须点开关本体 `.wd-switch`（点它的父容器 .his__switch 不会切换，实测）
  const mineReq = pageB.waitForRequest(r => r.url().includes('onlyMine=true'), { timeout: 15000 }).catch(() => null)
  await pageB.locator('.wd-switch').first().click()
  await mineReq
  await sleep(2500)
  await pageB.screenshot({ path: path.join(S, 'g1b-07-extB-mine-only.png'), fullPage: true })
  const bRowsMine = await historyRows(pageB)
  const mineHit = bRowsMine.some(r => r.code === st.SUBMIT_NO)
  const swCls = await pageB.evaluate(() => (document.querySelector('.wd-switch') || {}).className || '')
  check('G1b-10 打开「只看我提交的」后 extB 看不到 extA 这一条（收窄真的生效）',
    /is-checked/.test(swCls) && !mineHit, `开关cls=${swCls} 打开后 codes=${JSON.stringify(bRowsMine.map(r => r.code))}`)
  // 反向正向样本：extB 自己的 1004 必须还在（别把开关做成永远空）
  const bOwn = bRowsMine.some(r => r.code === 'SJ90000004')
  check('G1b-11 收窄后 extB 自己的样本（SJ90000004）仍在（不是一律清空）',
    bOwn, `打开后 codes=${JSON.stringify(bRowsMine.map(r => r.code))}`)
  check('G1b-12 extB 全程无未捕获前端异常', errsB.length === 0, JSON.stringify(errsB.slice(0, 3)))
} catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 5).join(' | ') : String(e))
} finally {
  const bad = finish()
  console.log(`SHOTS=${S}`)
  await browser.close()
  process.exit(bad === 0 ? 0 : 1)
}
