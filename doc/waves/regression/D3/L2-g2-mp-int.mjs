/**
 * D3 / r1 / L2 —— 组 2：小程序内部（staff）
 *   ① 首页点「石蜡包埋送样记录」新增 → 填必填项 → 提交（真后端 /mp/int/embed POST）
 *   ② 我的 → 内部管理 → 石蜡包埋工作表：冻结格 = 石蜡块编号，第二行工序圆点（只读）—— 记 BEFORE
 *   ③ 我的 → 历史编辑记录 → 石蜡包埋页签 → 点这条 → 补填脱水时间 → 保存（PUT）—— 记 AFTER 圆点变化
 *   ④ 样本修改页「给这个样本加石蜡块」带着样本进填写页
 * 前置：后端 8081 + reseed + miniapp H5 9200（VITE_MOCK_LOGIN=1）。跑法：node doc/waves/regression/D3/L2-g2-mp-int.mjs
 */
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import { chromium, mockLogin, resetToLogin, gotoPage, sleep, shotsDir, makeChecker, dbOne, WS } from './L2-lib.mjs'

const S = shotsDir('g2-mp-int')
const { check, finish } = makeChecker('D3-r1-L2-g2')
const BLOCK = 'T-r1l2-01'
const SAMPLE_ID = '9000001001'

// 每次跑先 reseed（本脚本自己建 T-r1l2-01，重跑必须回到干净 seed）
execFileSync('bash', ['doc/verify/reseed.sh', '--yes'], { cwd: WS, encoding: 'utf8', stdio: 'ignore' })

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 430, height: 900 } })
const page = await ctx.newPage()
const pageErrors = []
page.on('pageerror', e => pageErrors.push(e.message))
const reqs = []
page.on('request', r => {
  if (/\/mp\/(int|ext)\/embed/.test(r.url()) && ['POST', 'PUT'].includes(r.method())) {
    reqs.push({ method: r.method(), url: r.url(), body: r.postData() })
  }
})

const SRC = '.wd-input.is-cell, .wd-cell'
const rowIdx = label => page.evaluate(([sel, lb]) => [...document.querySelectorAll(sel)]
  .findIndex(e => ((e.querySelector('.wd-input__label-inner, .wd-cell__title') || {}).innerText || '').trim() === lb), [SRC, label])

async function clickRow(label) {
  const i = await rowIdx(label)
  if (i < 0) throw new Error(`找不到字段「${label}」`)
  await page.locator(SRC).nth(i).click()
  return i
}

async function fieldValue(label) {
  const i = await rowIdx(label)
  if (i < 0) return '__NO_ROW__'
  return page.evaluate(([sel, idx]) => {
    const r = [...document.querySelectorAll(sel)][idx]
    const inp = r.querySelector('input,textarea')
    if (inp) return inp.value
    const v = r.querySelector('.wd-cell__value')
    return v ? v.innerText.trim() : r.innerText.replace(/\n/g, '|')
  }, [SRC, i])
}

/** 日期选择器：持实例 open() → 点「完成」取当前值（value 是毫秒时间戳，后端要 yyyy-MM-dd） */
async function pickDateToday() {
  await page.waitForFunction(() => [...document.querySelectorAll('.wd-datetime-picker__popup')]
    .some(e => e.getBoundingClientRect().height > 0), { timeout: 12000 })
  await sleep(700)
  await page.locator('.wd-datetime-picker__popup').filter({ visible: true }).first()
    .locator('.wd-datetime-picker__action', { hasText: '完成' }).first().click()
  await sleep(800)
}

/** 内部管理表格页：全部行的冻结格 + 圆点 */
async function ledgerDots() {
  await page.waitForSelector('.ledger__row', { timeout: 20000 })
  await sleep(1200)
  return page.evaluate(() => [...document.querySelectorAll('.ledger__row')].map(r => ({
    frozen: (r.querySelector('.ledger__fz-main') || {}).innerText || '',
    sub: (r.querySelector('.lqg-ledger__fz-sub') || {}).innerText || '',
    tone: r.className,
  })))
}

async function gotoMe() {
  await gotoPage(page, 'pages/me/index')
  await page.waitForSelector('.me', { timeout: 20000 })
  await sleep(1000)
}

try {
  await resetToLogin(page)
  await mockLogin(page, 'staff')

  // ─────── ① 首页 → 石蜡包埋送样记录（新增） ───────
  await gotoPage(page, 'pages/index/index')
  await page.waitForSelector('.lqg-tile', { timeout: 20000 })
  await sleep(900)
  const tiles = await page.locator('.lqg-tile').allInnerTexts()
  check('G2A-01 内部首页有「石蜡包埋送样记录」入口',
    tiles.some(t => /石蜡包埋送样记录/.test(t)), `tiles=${JSON.stringify(tiles)}`)
  await page.screenshot({ path: path.join(S, 'g2a-01-home-internal.png'), fullPage: true })

  await page.locator('.lqg-tile', { hasText: '石蜡包埋送样记录' }).first().click()
  await sleep(3000)
  check('G2A-02 点首页入口直接进石蜡包埋填写页（新增模式）',
    /pages\/embed\/form/.test(page.url()) && /mode=new/.test(page.url()), `url=${page.url()}`)

  await page.waitForSelector('.emb', { timeout: 20000 })
  await sleep(1200)
  const formTxt = await page.locator('.emb').innerText()
  const formRows = await page.evaluate(sel => [...document.querySelectorAll(sel)]
    .map(e => (e.querySelector('.wd-input__label-inner, .wd-cell__title') || {}).innerText || ''), SRC)
  check('G2A-03 内部填写页是全字段（含石蜡块编号 / 七个工序 / 染色 / marker）',
    /石蜡块编号/.test(formTxt) && /脱水时间/.test(formTxt) && /切片时间/.test(formTxt) && /mark/.test(formTxt),
    `rows=${JSON.stringify(formRows)}`)
  check('G2A-04 必填项在页面上真的标了必填（选择样本 + 石蜡块编号）',
    /选择样本/.test(formTxt) && formTxt.includes('*'), `txt=${formTxt.replace(/\n/g, '|').slice(0, 200)}`)
  await page.screenshot({ path: path.join(S, 'g2a-02-form-new.png'), fullPage: true })

  // 选样本：内部只列已核验有效的样本
  await clickRow('选择样本')
  await page.waitForSelector('.sp__item', { timeout: 20000 })
  await sleep(1200)
  const spItems = await page.locator('.sp__item').allInnerTexts()
  check('G2A-05 内部选样本弹层只列已核验有效的样本（有 T-hli01，没有 pending 的 SJ90000002）',
    spItems.some(t => /T-hli01/.test(t)) && !spItems.some(t => /SJ90000002/.test(t)),
    `items=${JSON.stringify(spItems).slice(0, 300)}`)
  await page.screenshot({ path: path.join(S, 'g2a-03-sample-picker.png') })
  await page.locator('.sp__item', { hasText: 'T-hli01' }).first().click()
  await sleep(1500)
  const auto1 = await fieldValue('组织收样时间')
  const auto2 = await fieldValue('组织处理时间')
  check('G2A-06 选样本后自动带出前两个工序时间（组织收样 / 组织处理）',
    /\d{4}-\d{2}-\d{2}/.test(String(auto1)) && /\d{4}-\d{2}-\d{2}/.test(String(auto2)),
    `组织收样=${auto1} 组织处理=${auto2}`)

  // 石蜡块编号（必填）
  const bi = await rowIdx('石蜡块编号')
  const bInput = page.locator(SRC).nth(bi).locator('input').first()
  await bInput.click(); await bInput.fill(BLOCK); await sleep(400)

  // 提交
  const before = Number(dbOne(`SELECT count(*) FROM t_lqg_embed WHERE paraffin_block_no='${BLOCK}'`))
  await page.locator('.emb__btn', { hasText: '提交' }).first().click()
  await sleep(3500)
  const after = Number(dbOne(`SELECT count(*) FROM t_lqg_embed WHERE paraffin_block_no='${BLOCK}'`))
  const newId = dbOne(`SELECT id FROM t_lqg_embed WHERE paraffin_block_no='${BLOCK}' AND del_flag='0'`)
  check('G2A-07 提交真的落库（POST /mp/int/embed）：新增 1 行、挂对样本、记了新增人',
    before === 0 && after === 1
    && dbOne(`SELECT sample_id||'|'||create_by||'|'||verify_status FROM t_lqg_embed WHERE id=${newId}`) === `${SAMPLE_ID}|9000000101|valid`,
    `id=${newId} sample|create_by|status=${dbOne(`SELECT sample_id||'|'||create_by||'|'||verify_status FROM t_lqg_embed WHERE id=${newId}`)}`)
  const post = reqs.find(r => r.method === 'POST')
  check('G2A-08 提交的请求体里日期是 yyyy-MM-dd 字符串（不是毫秒时间戳）',
    !!post && /"tissueReceiveTime":"\d{4}-\d{2}-\d{2}"/.test(post.body || ''),
    `body=${(post && post.body || '').slice(0, 300)}`)
  check('G2A-09 提交后离开填写页（回到上一页 / 首页，不卡死在表单）',
    !/pages\/embed\/form\?mode=new/.test(page.url()), `url=${page.url()}`)
  await page.screenshot({ path: path.join(S, 'g2a-04-after-submit.png'), fullPage: true })

  // ─────── ② 内部管理 · 石蜡包埋工作表（只读）—— BEFORE ───────
  await gotoMe()
  const meTxt = await page.locator('.me').innerText()
  check('G2B-01 内部「我的」有内部管理板块四个入口', /内部管理/.test(meTxt) && /石蜡包埋送样记录/.test(meTxt),
    `me=${meTxt.replace(/\n/g, '|').slice(0, 260)}`)
  await page.screenshot({ path: path.join(S, 'g2b-01-me-internal.png'), fullPage: true })
  await page.locator('.adm .me-row, .lqg-card .me-row', { hasText: '石蜡包埋送样记录' }).first().click().catch(async () => {
    await page.locator('text=石蜡包埋送样记录').last().click()
  })
  await sleep(3500)
  check('G2B-02 内部管理进的是表格页且停在石蜡包埋工作表',
    /pages\/ledger\/index/.test(page.url()) && /sheet=embed/.test(page.url()), `url=${page.url()}`)

  const rows1 = await ledgerDots()
  const mine1 = rows1.find(r => r.frozen === BLOCK)
  check('G2B-03 冻结格 = 石蜡块编号（新记录=T-r1l2-01；待核验的 2006 显示送检单号+待核验）',
    !!mine1 && rows1.some(r => /SJ90000002/.test(r.frozen) && /待核验/.test(r.sub)),
    `rows=${JSON.stringify(rows1)}`)
  check('G2B-04 新记录的工序圆点是 8 格进度（前 2 格已由样本带出）',
    !!mine1 && (mine1.sub.match(/[●○]/g) || []).length === 8, `sub=${mine1 && mine1.sub}`)
  const readonly = await page.evaluate(() => document.querySelectorAll('.ledger__row input, .ledger__row textarea').length)
  const hasAddBtn = await page.locator('.ledger-page__bar button:has-text("新增")').count()
  const exportDisabled = await page.locator('.ledger-page__export').isDisabled().catch(() => null)
  check('G2B-05 表格只读：行内没有输入控件、没有新增（导出按钮状态记一笔）',
    readonly === 0 && hasAddBtn === 0, `inputs=${readonly} addBtn=${hasAddBtn} exportDisabled=${exportDisabled}`)
  await page.screenshot({ path: path.join(S, 'g2b-02-ledger-embed-before.png'), fullPage: true })
  const dotsBefore = mine1 ? (mine1.sub.match(/●/g) || []).length : -1

  // 点一行 → 只读详情（mode=view），右上角有「修改」
  await page.locator('.ledger__row', { hasText: BLOCK }).first().click()
  await sleep(3000)
  const viewUrl = page.url()
  const viewTxt = await page.locator('.emb').innerText().catch(() => '')
  const editEntryCount = await page.locator('.emb__edit').count()
  check('G2B-06 点一行进只读详情（mode=view），右上角「修改」入口在',
    /mode=view/.test(viewUrl) && editEntryCount > 0, `url=${viewUrl} editEntry=${editEntryCount}`)
  const viewInputs = await page.evaluate(() => document.querySelectorAll('.emb input:not([disabled])').length)
  check('G2B-07 只读详情里字段不可编辑（可编辑输入框数 = 0）', viewInputs === 0 && /mode=view/.test(viewUrl), `enabledInputs=${viewInputs} url=${viewUrl}`)
  await page.screenshot({ path: path.join(S, 'g2b-03-ledger-detail-view.png'), fullPage: true })

  // ─────── ③ 历史编辑记录 → 补填脱水时间 ───────
  await gotoPage(page, 'pages/history/index')
  await page.waitForSelector('.his__switch', { timeout: 20000 })
  await sleep(1200)
  await page.locator('.lqg-sheets__item', { hasText: '石蜡包埋' }).first().click()
  await sleep(2500)
  const swOn = await page.evaluate(() => {
    const s = document.querySelector('.his__switch .wd-switch')
    return s ? s.className : '__NO_SWITCH__'
  })
  const hRows = await page.evaluate(() => [...document.querySelectorAll('.his__item')].map(e => e.innerText.replace(/\n/g, '|')))
  check('G2C-01 内部历史记录页签存在且「只看我提交的」开关默认关',
    !/--on/.test(swOn) && hRows.length > 0, `switch=${swOn} rows=${hRows.length}`)
  check('G2C-02 内部历史默认 = 中心全部内部人员（含别人录的 2002 / T-E02-1）',
    hRows.some(t => /T-E01-2|T-E02-1/.test(t)), `rows=${JSON.stringify(hRows).slice(0, 500)}`)
  check('G2C-03 新提交的这一条出现在历史里', hRows.some(t => t.includes(BLOCK)), `rows=${JSON.stringify(hRows).slice(0, 400)}`)
  await page.screenshot({ path: path.join(S, 'g2c-01-history-embed.png'), fullPage: true })

  // 打开开关对照
  await page.locator('.his__switch .wd-switch').first().click()
  await sleep(2200)
  const hRowsMine = await page.evaluate(() => [...document.querySelectorAll('.his__item')].map(e => e.innerText.replace(/\n/g, '|')))
  check('G2C-04 打开「只看我提交的」后收窄（记录变少或集合变子集，且仍含本人这条）',
    hRowsMine.length <= hRows.length && hRowsMine.some(t => t.includes(BLOCK)),
    `all=${hRows.length} mine=${hRowsMine.length}`)
  await page.screenshot({ path: path.join(S, 'g2c-02-history-mine.png'), fullPage: true })
  await page.locator('.his__switch .wd-switch').first().click()
  await sleep(2000)

  // 点这一条 → 修改模式
  await page.locator('.his__item', { hasText: BLOCK }).first().click()
  await sleep(3000)
  check('G2C-05 内部点历史里的石蜡包埋行 → 进修改模式（mode=edit）',
    /pages\/embed\/form/.test(page.url()) && /mode=edit/.test(page.url()), `url=${page.url()}`)
  await page.waitForSelector('.emb', { timeout: 20000 })
  await sleep(1200)
  const editTxt = await page.locator('.emb').innerText()
  check('G2C-06 修改模式顶部有「最后修改」小字', /最后修改/.test(editTxt), `txt=${editTxt.replace(/\n/g, '|').slice(0, 160)}`)
  const beforeDehydrate = dbOne(`SELECT COALESCE(dehydrate_time::text,'NULL') FROM t_lqg_embed WHERE id=${newId}`)
  await clickRow('脱水时间')
  await pickDateToday()
  const pickedVal = await fieldValue('脱水时间')
  check('G2C-07 日期选择器能打开并写回 yyyy-MM-dd（毫秒时间戳已转换）',
    /^\d{4}-\d{2}-\d{2}$/.test(String(pickedVal)), `field=${pickedVal}`)
  await page.screenshot({ path: path.join(S, 'g2c-03-edit-dehydrate.png'), fullPage: true })

  await page.locator('.emb__btn', { hasText: '保存' }).first().click()
  await sleep(3500)
  const afterDehydrate = dbOne(`SELECT COALESCE(dehydrate_time::text,'NULL')||'|'||CASE WHEN update_by IS NULL THEN 'noupd' ELSE update_by::text END FROM t_lqg_embed WHERE id=${newId}`)
  check('G2C-08 补填真的保存：库里只改脱水时间、不新增行、记了修改人',
    beforeDehydrate === 'NULL' && /^\d{4}-\d{2}-\d{2}\|9000000101$/.test(afterDehydrate)
    && dbOne(`SELECT count(*) FROM t_lqg_embed WHERE sample_id=${SAMPLE_ID} AND del_flag='0'`) === '3',
    `before=${beforeDehydrate} after=${afterDehydrate} rows1001=${dbOne(`SELECT count(*) FROM t_lqg_embed WHERE sample_id=${SAMPLE_ID} AND del_flag='0'`)}`)
  const put = reqs.find(r => r.method === 'PUT')
  check('G2C-09 保存请求体里 dehydrateTime 是 yyyy-MM-dd 字符串',
    !!put && /"dehydrateTime":"\d{4}-\d{2}-\d{2}"/.test(put.body || ''), `body=${(put && put.body || '').slice(0, 300)}`)

  // ─────── ④ 圆点变化 ───────
  await gotoPage(page, 'pages/ledger/index?sheet=embed')
  const rows2 = await ledgerDots()
  const mine2 = rows2.find(r => r.frozen === BLOCK)
  const dotsAfter = mine2 ? (mine2.sub.match(/●/g) || []).length : -1
  check('G2D-01 补填脱水时间后冻结格第二行工序圆点真的多亮一个',
    dotsBefore >= 0 && dotsAfter === dotsBefore + 1, `before=${dotsBefore} after=${dotsAfter} sub=${mine2 && mine2.sub}`)
  check('G2D-02 圆点变化只影响这一条（别的行不变）',
    rows2.filter(r => r.frozen !== BLOCK).every(r => (rows1.find(x => x.frozen === r.frozen) || {}).sub === r.sub),
    `after=${JSON.stringify(rows2.map(r => [r.frozen, r.sub]))}`)
  await page.screenshot({ path: path.join(S, 'g2d-01-ledger-embed-after.png'), fullPage: true })

  // 历史里这条排到最前 + 经手人 = 李工
  await gotoPage(page, 'pages/history/index')
  await page.waitForSelector('.his__switch', { timeout: 20000 })
  await sleep(1000)
  await page.locator('.lqg-sheets__item', { hasText: '石蜡包埋' }).first().click()
  await sleep(2500)
  const hRows2 = await page.evaluate(() => [...document.querySelectorAll('.his__item')].map(e => e.innerText.replace(/\n/g, '|')))
  check('G2D-03 改完排到历史最前，行上经手人 = 李工（update_by）',
    hRows2.length > 0 && hRows2[0].includes(BLOCK) && /李工|我/.test(hRows2[0]),
    `first=${hRows2[0]} all=${JSON.stringify(hRows2.map(r => r.slice(0, 30)))}`)
  await page.screenshot({ path: path.join(S, 'g2d-02-history-after-edit.png'), fullPage: true })

  // ─────── ⑤ 样本修改页「给这个样本加石蜡块」带样本进填写页 ───────
  await gotoPage(page, `pages/sample/form?mode=edit&id=${SAMPLE_ID}`)
  await page.waitForSelector('.form', { timeout: 20000 }).catch(() => {})
  await sleep(1800)
  const link = page.locator('.form__link', { hasText: '给这个样本加石蜡块' }).first()
  const linkCount = await link.count()
  const linkTxt = linkCount ? await link.innerText() : '__NO_LINK__'
  check('G2E-01 样本修改页有「给这个样本加石蜡块」入口（已点亮，不是置灰占位）',
    linkCount > 0 && /加石蜡块/.test(linkTxt), `count=${linkCount} txt=${linkTxt}`)
  await page.screenshot({ path: path.join(S, 'g2e-01-sample-form.png'), fullPage: true })
  if (linkCount > 0) {
    await link.click()
    await sleep(3000)
    check('G2E-02 点它带着样本进石蜡包埋填写页（sampleId 在 URL 上）',
      /pages\/embed\/form/.test(page.url()) && /sampleId=9000001001/.test(page.url()), `url=${page.url()}`)
    await page.waitForSelector('.emb', { timeout: 20000 })
    await sleep(1500)
    const sampleField = await fieldValue('选择样本')
    check('G2E-03 填写页里样本已经带好（显示 T-hli01，不用再选）',
      /T-hli01/.test(String(sampleField)), `选择样本=${sampleField}`)
    await page.screenshot({ path: path.join(S, 'g2e-02-embed-with-sample.png'), fullPage: true })
  }

  check('G2Z-99 小程序页面无 JS 运行时报错', pageErrors.length === 0, pageErrors.slice(0, 3).join(' || '))
} catch (e) {
  check('G2-EXCEPTION', false, String(e && e.stack || e).slice(0, 900))
} finally {
  await page.screenshot({ path: path.join(S, 'g2z-final.png') }).catch(() => {})
  await browser.close()
  process.exit(finish() === 0 ? 0 : 1)
}
