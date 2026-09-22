/**
 * D3 / r1 / L2 —— 组 1：工作台（石蜡包埋列表 + 录入抽屉 + 核验抽屉 + 样本总表提示列 + 三个导出按钮）
 * 本片自写、独立复跑；不引用任何既有脚本的结论。
 * 前置：后端 8081 + reseed + plus-ui 8082。跑法：node doc/waves/regression/D3/L2-g1-web.mjs
 */
import path from 'node:path'
import fs from 'node:fs'
import { chromium, WEB, makeChecker, shotsDir, dbOne, sleep } from './L2-lib.mjs'

const S = shotsDir('g1-web')
const { check, finish } = makeChecker('D3-r1-L2-g1')

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1680, height: 950 }, acceptDownloads: true })
const page = await ctx.newPage()
const pageErrors = []
page.on('pageerror', e => pageErrors.push(e.message))

/** 等一个**可见**的 el-drawer（页面上会同时挂着隐藏的抽屉实例） */
async function waitVisibleDrawer() {
  await page.waitForFunction(() => [...document.querySelectorAll('.el-drawer')].some(d => d.offsetParent !== null), { timeout: 15000 })
  await sleep(1500)
}

async function login() {
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
}

async function gotoMenu(name) {
  await page.locator(`.el-menu-item:has-text("${name}")`).first().click({ timeout: 15000 })
  await sleep(2500)
}

/** 抓下载：等 download 事件 + 等网络响应双保险 */
async function catchDownload(fn, label) {
  const dl = page.waitForEvent('download', { timeout: 20000 }).catch(() => null)
  let respBody = null
  const rp = page.waitForResponse(r => /\/lqg\/.*\/export/.test(r.url()), { timeout: 25000 }).catch(() => null)
  await fn()
  const d = await dl
  const r = await rp
  if (r) { try { respBody = await r.body() } catch { respBody = null } }
  let file = null
  if (d) {
    file = path.join(S, `${label}.xlsx`)
    await d.saveAs(file)
  }
  const head = file && fs.existsSync(file) ? fs.readFileSync(file).subarray(0, 2).toString('latin1') : ''
  const size = file && fs.existsSync(file) ? fs.statSync(file).size : 0
  return { downloaded: !!d, suggested: d ? d.suggestedFilename() : null, file, size, head, httpBody: respBody }
}

try {
  await login()

  // ─────── A. 石蜡包埋列表 ───────
  await gotoMenu('石蜡包埋')
  await page.waitForResponse(r => r.url().includes('/lqg/embed/list'), { timeout: 25000 }).catch(() => {})
  await sleep(1800)
  check('G1A-01 侧边栏进入石蜡包埋页', /embed/.test(page.url()), `url=${page.url()}`)

  const rows = page.locator('.el-table__body tr')
  const n = await rows.count()
  check('G1A-02 列表行数 = 库里未删石蜡包埋记录数', n === Number(dbOne("SELECT count(*) FROM t_lqg_embed WHERE del_flag='0'")),
    `ui=${n} db=${dbOne("SELECT count(*) FROM t_lqg_embed WHERE del_flag='0'")}`)

  const firstText = (await rows.first().innerText()).replace(/\n/g, '|')
  check('G1A-03 待核验的外部送样（2006）置顶：首行 = 外部 · 待核验 · 待核验',
    /外部/.test(firstText) && (firstText.match(/待核验/g) || []).length >= 2, `first=${firstText.slice(0, 140)}`)

  const cls = await rows.first().getAttribute('class')
  // 行底色在 tr 上（element-plus 的 td 是透明的）；算到最近一个非透明祖先
  const rowBg = await rows.first().evaluate(el => {
    let e = el
    while (e && getComputedStyle(e).backgroundColor === 'rgba(0, 0, 0, 0)') e = e.parentElement
    return e ? getComputedStyle(e).backgroundColor : 'none'
  })
  const rgb = (rowBg.match(/\d+/g) || []).map(Number)
  // 浅黄：r>200 且 b 明显低于 r，r≈g
  const isPaleYellow = rgb.length >= 3 && rgb[0] > 200 && rgb[0] - rgb[2] > 12 && Math.abs(rgb[0] - rgb[1]) < 25
  check('G1A-04 待核验行 DOM class + 计算样式是浅黄底',
    /lqg-embed__row-pending/.test(cls || '') && isPaleYellow, `class=${cls} row.bg=${rowBg}`)

  const notPendingBg = await rows.nth(1).evaluate(el => {
    let e = el
    while (e && getComputedStyle(e).backgroundColor === 'rgba(0, 0, 0, 0)') e = e.parentElement
    return e ? getComputedStyle(e).backgroundColor : 'none'
  })
  const nb = (notPendingBg.match(/\d+/g) || []).map(Number)
  const notYellow = !(nb.length >= 3 && nb[0] > 200 && nb[0] - nb[2] > 12 && Math.abs(nb[0] - nb[1]) < 25)
  check('G1A-05 非待核验行不是浅黄（对照组）', notYellow, `second.bg=${notPendingBg}`)

  const headText = (await page.locator('.el-table__header').first().innerText()).replace(/\n/g, '|')
  check('G1A-06 列表含「内 / 外部」「核验状态」两个徽标列 + 模板 16 列',
    /内\s*\/\s*外部|内部|外部/.test(headText) && /核验状态/.test(headText) && /石蜡块编号/.test(headText) && /mark/.test(headText),
    `head=${headText.slice(0, 200)}`)
  check('G1A-07 待核验行石蜡块编号一格显示「待核验」而不是空',
    /待核验/.test(await rows.first().innerText()), '')

  await page.screenshot({ path: path.join(S, 'g1a-01-embed-list.png'), fullPage: true })

  // ─────── B. 核验抽屉（所挂样本未核验 → 判为有效置灰 + 说明） ───────
  const verifyBtn = rows.first().locator('button', { hasText: /核验/ }).first()
  const hasVerifyBtn = await verifyBtn.count() > 0
  check('G1B-01 待核验行操作列给的是「核验」按钮（不是普通编辑）', hasVerifyBtn,
    (await rows.first().locator('td').last().innerText()).replace(/\n/g, '|'))
  if (hasVerifyBtn) {
    await verifyBtn.click()
    await waitVisibleDrawer()
    const drawerText = (await page.locator('.el-drawer:visible').first().innerText()).replace(/\n/g, '|')
    await page.screenshot({ path: path.join(S, 'g1b-01-verify-drawer.png'), fullPage: true })

    const validBtn = page.locator('.el-drawer:visible .el-drawer__footer button', { hasText: /判为有效/ }).first()
    const disabled = await validBtn.isDisabled().catch(() => null)
    const btnCls = await validBtn.getAttribute('class').catch(() => '')
    check('G1B-02 所挂样本（1002 pending）未核验有效时「判为有效并保存」置灰',
      disabled === true && /is-disabled/.test(btnCls || ''), `disabled=${disabled} class=${btnCls}`)

    const alertText = (await page.locator('.el-drawer:visible .el-alert').first().innerText().catch(() => '')).replace(/\n/g, '|')
    check('G1B-03 抽屉里写明原因（读 sampleVerifyStatus，不是空提示）',
      alertText.length > 0 && /核验|待核验|有效|无效/.test(alertText), `alert=${alertText}`)
    check('G1B-04 提示词含所挂样本的实际状态文案', /待核验/.test(drawerText) || /待核验/.test(alertText),
      `drawer=${drawerText.slice(0, 200)}`)

    // 悬停 tooltip 也要给出原因
    await validBtn.hover({ force: true }).catch(() => {})
    await sleep(900)
    const tip = await page.locator('.el-popper:visible').last().innerText().catch(() => '')
    check('G1B-05 置灰按钮悬停给出说明 tooltip', /核验|有效|状态/.test(tip), `tip=${String(tip).replace(/\n/g, '|')}`)
    await page.screenshot({ path: path.join(S, 'g1b-02-verify-disabled-tooltip.png') })

    await page.locator('.el-drawer:visible .el-drawer__close-btn').first().click().catch(() => {})
    await sleep(1200)
  }

  // ─────── C. 录入/编辑抽屉（有效行） ───────
  const validRow = rows.filter({ hasText: 'T-E01-1' }).first()
  const editBtn = validRow.locator('button', { hasText: /编辑/ }).first()
  if (await editBtn.count() > 0) {
    await editBtn.click()
    await waitVisibleDrawer()
    const dt = (await page.locator('.el-drawer:visible').first().innerText()).replace(/\n/g, '|')
    const dvals = await page.locator('.el-drawer:visible input').evaluateAll(els => els.map(e => e.value).filter(Boolean))
    await page.screenshot({ path: path.join(S, 'g1c-01-edit-drawer.png'), fullPage: true })
    check('G1C-01 编辑抽屉带出石蜡块编号 T-E01-1 与染色 HE/IHC，顶部有「最后修改」小字',
      dvals.includes('T-E01-1') && /HE染色/.test(dt) && /IHC/.test(dt) && /最后修改/.test(dt),
      `values=${JSON.stringify(dvals)} | drawer=${dt.slice(0, 160)}`)
    await page.locator('.el-drawer:visible .el-drawer__close-btn').first().click().catch(() => {})
    await sleep(1000)
  } else {
    check('G1C-01 编辑抽屉可打开', false, '有效行找不到「编辑」按钮')
  }

  // ─────── D. 三个导出按钮真的下到文件 ───────
  const embedExportBtn = page.locator('.app-main button:has-text("导出")').first()
  const r1 = await catchDownload(() => embedExportBtn.click(), 'embed-export')
  check('G1D-01 石蜡包埋「导出」下载到 xlsx 文件（PK 头）',
    r1.downloaded && r1.head === 'PK' && r1.size > 2000,
    `file=${r1.file} size=${r1.size} head=${r1.head} suggested=${r1.suggested}`)
  await page.screenshot({ path: path.join(S, 'g1d-01-embed-export-done.png') })

  await gotoMenu('样本总表')
  await page.waitForResponse(r => r.url().includes('/lqg/sample/list'), { timeout: 25000 }).catch(() => {})
  await sleep(1800)
  const btns = await page.locator('.app-main button').allInnerTexts()
  check('G1D-02 样本总表有两个导出按钮（组织 + 类器官）',
    btns.filter(b => /导出/.test(b)).length >= 2, `buttons=${JSON.stringify(btns)}`)
  await page.screenshot({ path: path.join(S, 'g1d-02-sample-table.png'), fullPage: true })

  // 组织导出
  const btnsLoc = page.locator('.app-main button')
  const count = await btnsLoc.count()
  let tissue = null, organoid = null
  for (let i = 0; i < count; i++) {
    const t = (await btnsLoc.nth(i).innerText()).trim()
    if (/导出/.test(t) && /样本记录信息表|组织/.test(t)) tissue = btnsLoc.nth(i)
    if (/类器官/.test(t) && /导出/.test(t)) organoid = btnsLoc.nth(i)
  }
  if (tissue) {
    const rt = await catchDownload(() => tissue.click(), 'sample-tissue-export')
    check('G1D-03 样本总表「组织」导出下载到 xlsx（PK 头）',
      rt.downloaded && rt.head === 'PK' && rt.size > 2000,
      `file=${rt.file} size=${rt.size} head=${rt.head}`)
  } else check('G1D-03 找到组织导出按钮', false, `buttons=${JSON.stringify(btns)}`)
  await sleep(1200)
  if (organoid) {
    const ro = await catchDownload(() => organoid.click(), 'sample-organoid-export')
    check('G1D-04 样本总表「类器官」导出下载到 xlsx（PK 头）',
      ro.downloaded && ro.head === 'PK' && ro.size > 2000,
      `file=${ro.file} size=${ro.size} head=${ro.head}`)
  } else check('G1D-04 找到类器官导出按钮', false, `buttons=${JSON.stringify(btns)}`)

  // ─────── E. 样本总表「切片染色」提示列：悬停 + 跳转 ───────
  await gotoMenu('样本总表')
  await page.waitForResponse(r => r.url().includes('/lqg/sample/list'), { timeout: 25000 }).catch(() => {})
  await sleep(1800)
  const sHead = (await page.locator('.el-table__header').first().innerText()).replace(/\n/g, '|')
  check('G1E-01 样本总表有「切片染色」提示列', /切片染色|染色/.test(sHead), `head=${sHead.slice(0, 220)}`)

  const tHliRow = page.locator('.el-table__body tr').filter({ hasText: 'T-hli01' }).first()
  const hintBadges = tHliRow.locator('.lqg-hint__badges').first()
  const hintTxt = (await tHliRow.locator('.lqg-hint').first().innerText().catch(() => '')).replace(/\n/g, '|')
  check('G1E-02 T-hli01（1001）行提示 = 石蜡块 2 + 已切片 + HE + IHC',
    /2/.test(hintTxt) && /已切片/.test(hintTxt) && /HE/.test(hintTxt) && /IHC/.test(hintTxt), `hint=${hintTxt}`)

  const noneRow = page.locator('.el-table__body tr').filter({ hasText: 'SJ90000002' }).first()
  const noneTxt = (await noneRow.locator('.lqg-hint').first().innerText().catch(() => '')).replace(/\n/g, '|')
  check('G1E-03 没有有效石蜡块的样本（1002 待核验）显示「—」', /—/.test(noneTxt), `hint=${noneTxt}`)

  let hoverOk = false, hoverDetail = ''
  if (await hintBadges.count() > 0) {
    await hintBadges.hover()
    await sleep(2000)
    hoverDetail = await page.evaluate(() => {
      const ps = [...document.querySelectorAll('.lqg-hint__popper')]
      const vis = ps.find(p => p.offsetParent !== null) || ps[ps.length - 1]
      return vis ? vis.innerText.replace(/\n/g, '|') : '__NO_POPPER__'
    })
    hoverOk = /T-E01-1/.test(hoverDetail) && /T-E01-2/.test(hoverDetail) && /2026-0/.test(hoverDetail)
    await page.screenshot({ path: path.join(S, 'g1e-01-hint-hover.png') })
  }
  check('G1E-04 悬停提示列出各石蜡块编号与切片时间（T-E01-1/T-E01-2）', hoverOk, `popper=${hoverDetail}`)

  // 点提示跳石蜡包埋页并带 sampleId
  if (await hintBadges.count() > 0) {
    await hintBadges.click()
    await sleep(3000)
    const url = page.url()
    const pageTxt = (await page.locator('.app-main').innerText().catch(() => '')).replace(/\n/g, '|')
    await page.screenshot({ path: path.join(S, 'g1e-02-hint-jump.png'), fullPage: true })
    check('G1E-05 点提示跳到石蜡包埋页', /embed/.test(url), `url=${url}`)
    check('G1E-06 跳过去带着该样本过滤（页面出现样本过滤标签 / 行只剩该样本的块）',
      /9000001001|样本/.test(pageTxt) && /T-E01-/.test(pageTxt), `head=${pageTxt.slice(0, 220)}`)
  }

  check('G1Z-99 工作台页面无 JS 运行时报错', pageErrors.length === 0, pageErrors.slice(0, 3).join(' || '))
} catch (e) {
  check('G1-EXCEPTION', false, String(e && e.stack || e).slice(0, 800))
} finally {
  await page.screenshot({ path: path.join(S, 'g1z-final.png') }).catch(() => {})
  await browser.close()
  process.exit(finish() === 0 ? 0 : 1)
}
