/**
 * D3 / r1 / L2 —— 组 3：小程序外部（extA / extB）+ 工作台核验闭环
 *   ① extA 首页点「石蜡包埋送样记录」→ 选自己的 1001 → 提交（POST /mp/ext/embed）
 *   ② 工作台石蜡包埋页：这条置顶 + 浅黄
 *   ③ 工作台核验抽屉：判有效 → 给石蜡块编号
 *   ④ extA 在历史编辑记录与 1001 详情里看到这块石蜡
 *   ⑤ extB 打开 1001 详情看到石蜡块卡片，标识是石蜡块编号
 * 前置：后端 8081 + miniapp H5 9200（VITE_MOCK_LOGIN=1）+ plus-ui 8082。跑法：node doc/waves/regression/D3/L2-g3-mp-ext.mjs
 */
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import { chromium, WEB, mockLogin, resetToLogin, injectToken, mpToken, gotoPage, sleep, shotsDir, makeChecker, dbOne, WS } from './L2-lib.mjs'

const S = shotsDir('g3-mp-ext')
const { check, finish } = makeChecker('D3-r1-L2-g3')
const BLOCK = 'T-r1l2-E1'
const SAMPLE_ID = '9000001001'

execFileSync('bash', ['doc/verify/reseed.sh', '--yes'], { cwd: WS, encoding: 'utf8', stdio: 'ignore' })

const browser = await chromium.launch({ headless: true })
const mpCtx = await browser.newContext({ viewport: { width: 430, height: 900 } })
const mp = await mpCtx.newPage()
const webCtx = await browser.newContext({ viewport: { width: 1680, height: 950 } })
const web = await webCtx.newPage()
const errs = []
mp.on('pageerror', e => errs.push('mp:' + e.message))
web.on('pageerror', e => errs.push('web:' + e.message))
const mpReqs = []
mp.on('request', r => {
  if (/\/mp\/ext\/embed/.test(r.url()) && r.method() === 'POST') mpReqs.push(r.postData())
})
/** 工作台写接口的「方法 路径 -> 业务码」序列（缺陷取证用） */
const webNet = []
web.on('response', async r => {
  if (/\/lqg\/embed/.test(r.url()) && r.request().method() !== 'GET') {
    let b = ''
    try { b = (await r.text()).slice(0, 160) } catch {}
    const code = (b.match(/"code":(\d+)/) || [])[1] || r.status()
    webNet.push(`${r.request().method()} ${r.url().replace(/^.*\/dev-api/, '')} -> ${code} ${b}`)
  }
})

const SRC = '.wd-input.is-cell, .wd-cell'
const mpRowIdx = label => mp.evaluate(([sel, lb]) => [...document.querySelectorAll(sel)]
  .findIndex(e => ((e.querySelector('.wd-input__label-inner, .wd-cell__title') || {}).innerText || '').trim() === lb), [SRC, label])
async function mpRowText(label) {
  const i = await mpRowIdx(label)
  if (i < 0) return '__NO_ROW__'
  return mp.evaluate(([sel, idx]) => {
    const r = [...document.querySelectorAll(sel)][idx]
    const inp = r.querySelector('input,textarea')
    return inp ? inp.value : (r.querySelector('.wd-cell__value') || {}).innerText || r.innerText.replace(/\n/g, '|')
  }, [SRC, i])
}
async function mpClickRow(label) {
  const i = await mpRowIdx(label)
  if (i < 0) throw new Error(`找不到字段「${label}」`)
  await mp.locator(SRC).nth(i).click()
}
/** 文本行填值（样本类型 / 类器官来源类型是 text 控件） */
async function mpFill(label, value) {
  const i = await mpRowIdx(label)
  if (i < 0) throw new Error(`找不到字段「${label}」`)
  const inp = mp.locator(SRC).nth(i).locator('input,textarea').first()
  await inp.click()
  await inp.fill(value)
  await sleep(400)
}

async function webLogin() {
  await web.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await web.evaluate(() => localStorage.clear()).catch(() => {})
  await web.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await web.waitForSelector('.login-form input', { timeout: 30000 })
  const ins = web.locator('.login-form input')
  await ins.nth(0).fill('lqgadmin')
  await ins.nth(1).fill('admin123')
  await web.click('.login-form button')
  await web.waitForSelector('.el-menu', { timeout: 30000 })
  await sleep(1800)
}
async function webGotoEmbed() {
  await web.locator('.el-menu-item:has-text("石蜡包埋")').first().click({ timeout: 15000 })
  await web.waitForResponse(r => r.url().includes('/lqg/embed/list'), { timeout: 25000 }).catch(() => {})
  await sleep(2000)
}
async function webWaitDrawer() {
  await web.waitForFunction(() => [...document.querySelectorAll('.el-drawer')].some(d => d.getBoundingClientRect().height > 0), { timeout: 15000 })
  await sleep(1500)
}

try {
  // ─────── ① extA 提交送样 ───────
  await resetToLogin(mp)
  await mockLogin(mp, 'extA')
  await gotoPage(mp, 'pages/index/index')
  await mp.waitForSelector('.lqg-tile', { timeout: 20000 })
  await sleep(900)
  const tiles = await mp.locator('.lqg-tile').allInnerTexts()
  check('G3A-01 外部首页三格里有「石蜡包埋送样记录」、没有冻存',
    tiles.some(t => /石蜡包埋送样记录/.test(t)) && !tiles.some(t => /冻存/.test(t)), `tiles=${JSON.stringify(tiles)}`)
  await mp.screenshot({ path: path.join(S, 'g3a-01-home-extA.png'), fullPage: true })

  await mp.locator('.lqg-tile', { hasText: '石蜡包埋送样记录' }).first().click()
  await sleep(3000)
  check('G3A-02 extA 点首页入口进石蜡包埋填写页（新增）',
    /pages\/embed\/form/.test(mp.url()) && /mode=new/.test(mp.url()), `url=${mp.url()}`)

  await mp.waitForSelector('.emb', { timeout: 20000 })
  await sleep(1500)
  const extTxt = await mp.locator('.emb').innerText()
  const extRows = await mp.evaluate(sel => [...document.querySelectorAll(sel)]
    .map(e => (e.querySelector('.wd-input__label-inner, .wd-cell__title') || {}).innerText || '').filter(Boolean), SRC)
  const forbidden = ['石蜡块编号', '脱水时间', '琼脂糖包埋样本时间', '包埋人', '组织收样时间', '切片时间', '操作人', '备注']
  check('G3A-03 外部填写页只渲染 选择样本 + 样本类型 + 类器官来源类型（无内部字段）',
    extRows.length === 3 && /选择样本/.test(extRows[0]) && !forbidden.some(f => extTxt.includes(f)),
    `rows=${JSON.stringify(extRows)}`)
  await mp.screenshot({ path: path.join(S, 'g3a-02-ext-form.png'), fullPage: true })

  await mpClickRow('选择样本')
  await mp.waitForSelector('.spx__item', { timeout: 20000 })
  await sleep(1500)
  const spx = await mp.locator('.spx__item').allInnerTexts()
  check('G3A-04 外部选样本只列本人送检过、没被判无效的（SJ90000001/02，无 SJ90000003）',
    spx.some(t => /SJ90000001/.test(t)) && spx.some(t => /SJ90000002/.test(t)) && !spx.some(t => /SJ90000003/.test(t)),
    `items=${JSON.stringify(spx)}`)
  check('G3A-05 外部选择器不出现内部编号、供体姓名是掩码',
    !spx.some(t => /T-hli01|T-hli02/.test(t)) && spx.some(t => /王|\*/.test(t)), `items=${JSON.stringify(spx)}`)
  await mp.screenshot({ path: path.join(S, 'g3a-03-ext-sample-picker.png') })

  await mp.locator('.spx__item', { hasText: 'SJ90000001' }).first().click()
  await sleep(1500)
  const picked = await mpRowText('选择样本')
  check('G3A-06 选中自己的 1001（送检单号 SJ90000001）', /SJ90000001/.test(String(picked)), `选择样本=${picked}`)
  await mpFill('样本类型', '组织')
  await sleep(500)
  await mp.screenshot({ path: path.join(S, 'g3a-04-ext-form-filled.png'), fullPage: true })

  await mp.locator('.emb__btn', { hasText: '提交' }).first().click()
  await sleep(3500)
  const extRow = dbOne(`SELECT id||'|'||sample_id||'|'||verify_status||'|'||COALESCE(paraffin_block_no,'-')||'|'||create_by||'|'||submit_source FROM t_lqg_embed WHERE sample_id=${SAMPLE_ID} AND verify_status='pending' AND del_flag='0'`)
  check('G3A-07 提交真的落库为待核验的外部送样（无石蜡块编号、提交人=extA）',
    extRow === `${extRow.split('|')[0]}|${SAMPLE_ID}|pending|-|9000000111|external`, `row=${extRow}`)
  const newId = extRow.split('|')[0]
  check('G3A-08 提交请求体走 /mp/ext/embed 且日期字段是 yyyy-MM-dd（本次为空）',
    mpReqs.length === 1 && /"sampleId":"9000001001"/.test(mpReqs[0]) && !/\d{13}/.test(mpReqs[0]),
    `body=${(mpReqs[0] || '').slice(0, 260)}`)

  // ─────── ② 工作台：置顶浅黄 ───────
  await webLogin()
  await webGotoEmbed()
  const wRows = web.locator('.el-table__body tr')
  const firstTxt = (await wRows.first().innerText()).replace(/\n/g, '|')
  const firstRowBg = await wRows.first().evaluate(el => {
    let e = el
    while (e && getComputedStyle(e).backgroundColor === 'rgba(0, 0, 0, 0)') e = e.parentElement
    return e ? getComputedStyle(e).backgroundColor : 'none'
  })
  const fr = (firstRowBg.match(/\d+/g) || []).map(Number)
  check('G3B-01 extA 刚提交的外部送样在工作台石蜡包埋页置顶',
    /外部/.test(firstTxt) && /待核验/.test(firstTxt), `first=${firstTxt.slice(0, 140)}`)
  check('G3B-02 置顶行是浅黄底（计算样式）',
    fr.length >= 3 && fr[0] > 200 && fr[0] - fr[2] > 12 && Math.abs(fr[0] - fr[1]) < 25, `bg=${firstRowBg}`)
  await web.screenshot({ path: path.join(S, 'g3b-01-web-pending-top.png'), fullPage: true })

  // ─────── ③ 判有效 + 给编号 ───────
  await wRows.first().locator('button', { hasText: /核验/ }).first().click()
  await webWaitDrawer()
  const validBtn = web.locator('.el-drawer:visible .el-drawer__footer button', { hasText: /判为有效/ }).first()
  const disabled = await validBtn.isDisabled()
  const alertTxt = (await web.locator('.el-drawer:visible .el-alert').first().innerText().catch(() => '')).replace(/\n/g, '|')
  check('G3B-03 所挂样本 1001 已核验有效 → 「判为有效并保存」可点（与 2006 的置灰成对照）',
    disabled === false, `disabled=${disabled} alert=${alertTxt}`)
  const blockInput = web.locator('.el-drawer:visible input').filter({ hasNot: web.locator('[readonly]') })
  // 石蜡块编号是必填：先空着点一次，确认给不出编号时不会静默通过
  const beforeVerify = dbOne(`SELECT verify_status FROM t_lqg_embed WHERE id=${newId}`)
  await validBtn.click()
  await sleep(1200)
  const afterEmpty = dbOne(`SELECT verify_status||'|'||COALESCE(paraffin_block_no,'-') FROM t_lqg_embed WHERE id=${newId}`)
  check('G3B-04 石蜡块编号为空时点「判为有效」不会静默通过（库里仍 pending）',
    afterEmpty === 'pending|-', `before=${beforeVerify} afterEmpty=${afterEmpty}`)
  // 填编号
  const n = await web.locator('.el-drawer:visible .el-form-item').count()
  let filled = false
  for (let i = 0; i < n; i++) {
    const fi = web.locator('.el-drawer:visible .el-form-item').nth(i)
    const lb = (await fi.locator('.el-form-item__label').first().innerText().catch(() => '')).trim()
    if (/石蜡块编号/.test(lb)) {
      await fi.locator('input').first().fill(BLOCK)
      filled = true
      break
    }
  }
  check('G3B-05 核验抽屉里能找到并填上「石蜡块编号」', filled, `formItems=${n}`)
  await web.screenshot({ path: path.join(S, 'g3b-02-verify-drawer-filled.png'), fullPage: true })

  // ── 点「判为有效并保存」：抓全部网络 + 可见反馈 + 库内副作用 ──
  webNet.length = 0
  await validBtn.click()
  await sleep(3000)
  const verified = dbOne(`SELECT verify_status||'|'||COALESCE(paraffin_block_no,'-')||'|'||CASE WHEN verify_by IS NULL THEN 'noverify' ELSE verify_by::text END FROM t_lqg_embed WHERE id=${newId}`)
  const toasts = (await web.locator('.el-message').allInnerTexts().catch(() => [])).join(' / ')
  const drawerStillOpen = await web.locator('.el-drawer:visible').first().isVisible().catch(() => false)
  await web.screenshot({ path: path.join(S, 'g3b-03-after-valid-click.png'), fullPage: true })
  check('G3B-06 判为有效真的落库：valid + 编号 + 核验人',
    verified === `valid|${BLOCK}|9000000101`, `row=${verified} net=${JSON.stringify(webNet)}`)
  check('G3B-07 点不动时给用户可见的错误提示（不是静默失败）',
    toasts.length > 0 || !drawerStillOpen, `toasts="${toasts}" drawerOpen=${drawerStillOpen} net=${JSON.stringify(webNet)}`)
  check('G3B-08 缺陷证据：点「判为有效并保存」只发了 PUT /lqg/embed（被后端 400 拒），从没发 PUT /lqg/embed/{id}/verify',
    webNet.some(x => /^PUT \/lqg\/embed -> 400/.test(x)) && !webNet.some(x => /^(PUT|POST) \/lqg\/embed\/.+/.test(x)),
    `net=${JSON.stringify(webNet)}`)

  // ── API 兜底完成核验（★ 明示：这是绕过 UI 缺陷的兜底，不是「UI 通过」；
  //    目的是让下游 extA/extB 的卡片渲染能独立接受检查） ──
  execFileSync('bash', ['doc/verify/api.sh', '--as', 'staff', 'PUT', `/lqg/embed/${newId}/verify`,
    JSON.stringify({ action: 'valid', paraffinBlockNo: BLOCK })], { cwd: WS, encoding: 'utf8' })
  const viaApi = dbOne(`SELECT verify_status||'|'||COALESCE(paraffin_block_no,'-')||'|'||verify_by::text FROM t_lqg_embed WHERE id=${newId}`)
  check('G3B-09 [兜底] 后端 PUT /lqg/embed/{id}/verify 本身是好的（UI 缺陷定位在前端抽屉的预保存）',
    viaApi === `valid|${BLOCK}|9000000101`, `row=${viaApi}`)
  await web.locator('.el-drawer:visible .el-drawer__close-btn').first().click().catch(() => {})
  await sleep(1200)

  // ─────── ④ extA：历史 + 1001 详情 ───────
  await gotoPage(mp, 'pages/history/index')
  await mp.waitForSelector('.his__switch', { timeout: 20000 })
  await sleep(1200)
  await mp.locator('.lqg-sheets__item', { hasText: '石蜡包埋' }).first().click()
  await sleep(2800)
  const aRows = await mp.evaluate(() => [...document.querySelectorAll('.his__item')].map(e => e.innerText.replace(/\n/g, '|')))
  check('G3A-09 extA 历史编辑记录的石蜡包埋页签能看到这块石蜡（标识=石蜡块编号）',
    aRows.some(t => t.includes(BLOCK)), `rows=${JSON.stringify(aRows).slice(0, 400)}`)
  await mp.screenshot({ path: path.join(S, 'g3a-05-ext-history.png'), fullPage: true })

  await gotoPage(mp, `pages/sample/detail-ext?id=${SAMPLE_ID}`)
  await sleep(2800)
  const detTxt = (await mp.locator('.det').innerText().catch(() => '')).replace(/\n/g, '|')
  const cards = await mp.locator('.ecard').count()
  const cardNos = await mp.locator('.ecard__no').allInnerTexts()
  check('G3A-10 extA 在 1001 详情里看到石蜡块卡片，标识是石蜡块编号',
    cards > 0 && cardNos.includes(BLOCK), `cards=${cards} nos=${JSON.stringify(cardNos)}`)
  await mp.screenshot({ path: path.join(S, 'g3a-06-ext-sample-detail.png'), fullPage: true })

  // ─────── ⑤ extB：1001 详情 ───────
  const tok = mpToken('extB', '13800000012')
  await gotoPage(mp, 'pages/login/index')
  await injectToken(mp, tok)
  await gotoPage(mp, `pages/sample/detail-ext?id=${SAMPLE_ID}`)
  await sleep(3000)
  const bCards = await mp.locator('.ecard').count()
  const bNos = await mp.locator('.ecard__no').allInnerTexts()
  const bMe = await mp.evaluate(() => JSON.parse(localStorage.getItem('lqg_mp_me') || 'null'))
  check('G3B-07 extB 打开 1001 详情看到石蜡块卡片，标识是石蜡块编号',
    bCards > 0 && bNos.includes(BLOCK), `cards=${bCards} nos=${JSON.stringify(bNos)} user=${bMe && (bMe.name || bMe.nickName)}`)
  await mp.screenshot({ path: path.join(S, 'g3b-04-extB-sample-detail.png'), fullPage: true })

  check('G3Z-99 小程序与工作台无 JS 运行时报错（已知：G3B-08 那次被拒的 PUT 会抛未捕获 rejection）',
    errs.filter(e => e !== 'web:error').length === 0, errs.slice(0, 4).join(' || '))
} catch (e) {
  check('G3-EXCEPTION', false, String(e && e.stack || e).slice(0, 900))
} finally {
  await mp.screenshot({ path: path.join(S, 'g3z-mp-final.png') }).catch(() => {})
  await browser.close()
  process.exit(finish() === 0 ? 0 : 1)
}
