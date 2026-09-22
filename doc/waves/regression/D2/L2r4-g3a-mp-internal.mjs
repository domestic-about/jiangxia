/**
 * D2 / r4 / L2 —— 组 3 上半（内部小程序）：
 *   ① 首页四格 → 点「样本记录信息表」直接进填写页新增（mode=new 由入口带）
 *   ② 我的 → 历史编辑记录 → 点本人录的有效样本改一个字段、保存成功、排到最前
 *   ③ 我的 → 内部管理 → 表格页：两个工作表列名列序**逐字对甲方模板原件**、首列冻结、左右滑动看全部列、
 *      页面上没有新增与保存、点一行是只读（mode=view）
 *   ④ ★ 硬断言：历史编辑记录内部两个页签各自只出现该 sampleKind 的记录，点一行进对应类别表单
 *
 * 微信开发者工具在本沙箱跑不通（EPERM + 需扫码）→ miniapp H5(9200) + Playwright 等价覆盖。**开发者工具/真机未覆盖**。
 * 前置：后端 8081 + reseed + miniapp 9200。跑法：node doc/waves/regression/D2/L2r4-g3a-mp-internal.mjs
 */
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import { chromium, WS, makeChecker, shotsDir, dbOne, resetToLogin, mockLogin, gotoPage, bodyText, fillByLabel, rowText, gotoHistory, historyRows, sleep } from './L2r4-lib.mjs'

const S = shotsDir('L2r4-mp-g3a')
const { check, finish } = makeChecker('D2-r4-L2-g3a')

/** 甲方模板原件第 1 行（真读 xlsx，不抄代码里的常量） */
function templateHeader(file, sheetIndex = 0) {
  const py = `import openpyxl,sys;wb=openpyxl.load_workbook(${JSON.stringify(file)});ws=wb.worksheets[${sheetIndex}];print('|'.join('' if c.value is None else str(c.value) for c in ws[1]).rstrip('|'))`
  return execFileSync('python3', ['-c', py], { cwd: WS, encoding: 'utf8' }).trim()
}


/** 同一取数口（内部 · tissue · recent）的 code 清单，用于对照页面 */
async function fetchRecentCodes() {
  const out = execFileSync('bash', ['doc/verify/api.sh', '--as', 'staff', 'GET', '/mp/int/sample/list?pageSize=100&sampleKind=tissue&sort=recent'],
    { cwd: WS, encoding: 'utf8' })
  return JSON.parse(out).rows.map(r => r.internalNo || r.submitNo)
}

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 430, height: 900 }, deviceScaleFactor: 2 })
const page = await ctx.newPage()
const pageErrors = []
page.on('pageerror', e => pageErrors.push(e.message))

/** 当前页签下每一行的 code（内部行 code = 内部编号 || 送检单号） */
async function ledgerHeaders() {
  return page.evaluate(() => [...document.querySelectorAll('.lqg-ledger__th')].map(e => e.innerText.trim()))
}

try {
  await resetToLogin(page)
  await mockLogin(page, 'staff')
  await page.screenshot({ path: path.join(S, 'g3a-01-int-home.png'), fullPage: true })

  // ── ① 首页四格 ─────────────────────────────────────────────────────────
  const tiles = await page.$$eval('.lqg-tile', els => els.map(e => e.innerText.split('\n').filter(Boolean)[1]))
  check('G3a-01 内部首页四格、顺序 = 模板顺序（样本 / 类器官 / 石蜡包埋 / -80 冻存）',
    JSON.stringify(tiles) === JSON.stringify(['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录', '-80 冻存记录']), JSON.stringify(tiles))
  await page.locator('.lqg-tile', { hasText: '样本记录信息表' }).first().click()
  await sleep(2600)
  const homeUrl = page.url()
  const homeBody = await bodyText(page)
  await page.screenshot({ path: path.join(S, 'g3a-02-entry-new.png'), fullPage: true })
  check('G3a-02 点「样本记录信息表」直接进填写页、mode=new、可提交（不是错误态 / 不是只读）',
    /pages\/sample\/form/.test(homeUrl) && /mode=new/.test(homeUrl) && /提交/.test(homeBody) && !/没能加载/.test(homeBody),
    `url=${homeUrl} body=${homeBody.slice(0, 80).replace(/\n/g, '|')}`)
  check('G3a-03 内部新增页有收样段（收样日期 / 内部编号 / 处理时间…）',
    /收样信息/.test(homeBody) && /内部编号/.test(homeBody) && /收样日期/.test(homeBody), homeBody.replace(/\n/g, '|').slice(0, 160))

  // ── ② 历史编辑记录：改本人录的有效样本 ─────────────────────────────────
  await gotoHistory(page, '样本记录信息表')
  await page.screenshot({ path: path.join(S, 'g3a-03-history-int.png'), fullPage: true })
  const before = await historyRows(page)
  // 对照组走**同一个取数口** /mp/int/sample/list?sampleKind=tissue&sort=recent（本片不自己重写一遍过滤 SQL，
  // 否则「内部核验过的外部样本算不算经手」这种口径差会变成假红）。
  const apiRecent = await fetchRecentCodes()
  check('G3a-04 样本页签 = /mp/int/sample/list?sampleKind=tissue&sort=recent 的同一集合与顺序',
    before.map(r => r.code).join(',') === apiRecent.join(','),
    `ui=${JSON.stringify(before.map(r => r.code))} api=${JSON.stringify(apiRecent)}`)

  // ★ 硬断言：两个页签各自只出现该 sampleKind 的记录
  const sampleCodes = before.map(r => r.code).filter(Boolean)
  const kinds = sampleCodes.map(c => dbOne(`SELECT sample_kind FROM t_lqg_sample WHERE del_flag='0' AND (internal_no='${c}' OR (coalesce(internal_no,'')='' AND submit_no='${c}')) LIMIT 1`))
  check('G3a-05 ★ 样本页签逐行 sample_kind = tissue（无串台的类器官行）',
    sampleCodes.length > 0 && kinds.every(k => k === 'tissue'),
    `rows=${JSON.stringify(sampleCodes.map((c, i) => c + ':' + kinds[i]))}`)

  const orgRows = await gotoHistory(page, '类器官收样记录').then(() => historyRows(page))
  await page.screenshot({ path: path.join(S, 'g3a-04-history-organoid-tab.png'), fullPage: true })
  const orgCodes = orgRows.map(r => r.code).filter(Boolean)
  const orgKinds = orgCodes.map(c => dbOne(`SELECT sample_kind FROM t_lqg_sample WHERE del_flag='0' AND (internal_no='${c}' OR (coalesce(internal_no,'')='' AND submit_no='${c}')) LIMIT 1`))
  check('G3a-06 ★ 类器官页签逐行 sample_kind = organoid（无串台的样本行）',
    orgCodes.length > 0 && orgKinds.every(k => k === 'organoid'),
    `rows=${JSON.stringify(orgCodes.map((c, i) => c + ':' + orgKinds[i]))}`)
  const inter = sampleCodes.filter(c => orgCodes.includes(c))
  check('G3a-07 两个页签的行集合不相交', inter.length === 0, `相交=${JSON.stringify(inter)}`)

  // 类器官页签点一行 → 进 organoid 表单；样本页签点一行 → 进 sample 表单
  await page.locator('.his__item').first().click()
  await sleep(2800)
  const orgUrl = page.url()
  await page.screenshot({ path: path.join(S, 'g3a-05-organoid-row-target.png'), fullPage: true })
  check('G3a-08 ★ 类器官页签点一行 → pages/organoid/form（对应类别）', /pages\/organoid\/form/.test(orgUrl), `url=${orgUrl}`)
  await gotoHistory(page, '样本记录信息表')
  await page.locator('.his__item').first().click()
  await sleep(2800)
  const smpUrl = page.url()
  await page.screenshot({ path: path.join(S, 'g3a-06-sample-row-target.png'), fullPage: true })
  check('G3a-09 ★ 样本页签点一行 → pages/sample/form（不是 organoid 表单）',
    /pages\/sample\/form/.test(smpUrl) && !/organoid/.test(smpUrl), `url=${smpUrl}`)

  // 点本人录的有效样本（T-hli05 = 1008）改一个字段并保存
  await gotoHistory(page, '样本记录信息表')
  // 只拿**本片自己录的**那条（T-hli05）改；extA 交的那条被工作台核验过、也在最近清单里，
  // 但它的收样段是工作台核出来的，不该拿它当「内部改有效样本」的证据。
  const targetCode = (before.find(r => !/^T-l2r4/.test(r.code)) || before[0]).code
  await page.locator('.his__item', { hasText: targetCode }).first().click()
  await sleep(3000)
  const editUrl = page.url()
  const editBody = await bodyText(page)
  const targetId = dbOne(`SELECT id FROM t_lqg_sample WHERE del_flag='0' AND (internal_no='${targetCode}' OR submit_no='${targetCode}') LIMIT 1`)
  check('G3a-10 点本人录的有效样本进 edit 模式且可保存（内部改有效样本）',
    /mode=edit/.test(editUrl) && /保存/.test(editBody),
    `code=${targetCode} id=${targetId} url=${editUrl} body=${editBody.slice(0, 70).replace(/\n/g, '|')}`)
  const ageBefore = await rowText(page, '年龄')
  await fillByLabel(page, '年龄', '77')
  await page.waitForTimeout(600)
  const ageTyped = await rowText(page, '年龄')
  await page.screenshot({ path: path.join(S, 'g3a-07a-before-save.png'), fullPage: true })
  await page.locator('button, .form__btn').filter({ hasText: /保存|提交/ }).first().click()
  await sleep(3500)
  await page.screenshot({ path: path.join(S, 'g3a-07-edited-saved.png'), fullPage: true })
  const dbEdit = dbOne(`SELECT age || '|' || verify_status || '|' || CASE WHEN update_by IS NULL THEN 'no' ELSE 'yes' END FROM t_lqg_sample WHERE id=${targetId}`)
  check('G3a-11 保存成功：库里年龄 = 我改的值、状态仍 valid、update_by 有值',
    dbEdit === '77|valid|yes', `改前=${ageBefore} 页面上打的=${ageTyped} 库里=${dbEdit}`)

  // 排到最前
  await gotoHistory(page, '样本记录信息表')
  const afterRows = await historyRows(page)
  await page.screenshot({ path: path.join(S, 'g3a-08-moved-first.png'), fullPage: true })
  check('G3a-12 改完这一条排到最前（第一行 = 刚改的那条）',
    afterRows[0] && afterRows[0].code === targetCode, `codes=${JSON.stringify(afterRows.map(r => r.code))}`)
  check('G3a-13 内部行带经手人（本人显示「我」）',
    afterRows[0] && /我/.test(afterRows[0].text), `row0=${JSON.stringify(afterRows[0])}`)

  // ── ③ 内部管理 → 表格页 ────────────────────────────────────────────────
  await gotoPage(page, 'pages/me/index')
  const meText = await bodyText(page)
  check('G3a-14 内部「我的」有「内部管理」板块（外部不渲染）',
    /内部管理/.test(meText) && /历史编辑记录/.test(meText), meText.replace(/\n/g, '|').slice(0, 200))
  await page.screenshot({ path: path.join(S, 'g3a-09a-me.png'), fullPage: true })
  await page.locator('.adm .merow', { hasText: '样本记录信息表' }).first().click()
  await sleep(3000)
  const ledgerUrl = page.url()
  await page.screenshot({ path: path.join(S, 'g3a-09-ledger.png'), fullPage: true })
  check('G3a-15 内部管理进表格页（pages/ledger/index）', /pages\/ledger\/index/.test(ledgerUrl), `url=${ledgerUrl}`)

  const tabs = await page.$$eval('.lqg-sheets__item', els => els.map(e => e.innerText.trim()))
  check('G3a-16 表格页两个工作表：样本记录 / 类器官收样',
    tabs.includes('样本记录') && tabs.includes('类器官收样'), JSON.stringify(tabs))

  // 列名列序 vs 甲方模板原件
  const tplTissue = templateHeader('_input/templates/样本记录信息表模板.xlsx').split('|').filter(Boolean)
  const tplOrg = templateHeader('_input/templates/类器官收样记录模板.xlsx').split('|').filter(Boolean)
  const tissueHead = await ledgerHeaders()
  /** 期望表头 = 甲方模板第 1 行（冻结列提到最前，其余保持原序）+ 追加列「切片染色」 */
  const expectOf = (tpl) => ['内部编号', ...tpl.filter(c => c !== '内部编号'), '切片染色']
  check('G3a-17 样本工作表列名列序 = 甲方模板第 1 行（冻结列提到最前 + 追加「切片染色」）',
    JSON.stringify(tissueHead) === JSON.stringify(expectOf(tplTissue)),
    `ui=${JSON.stringify(tissueHead)} 期望=${JSON.stringify(expectOf(tplTissue))}`)

  const fz = await page.evaluate(() => {
    const th = document.querySelector('.lqg-ledger__th.lqg-ledger__fz')
    const td = document.querySelector('.lqg-ledger__td.lqg-ledger__fz')
    const t = getComputedStyle(th || document.body)
    const d = getComputedStyle(td || document.body)
    return { thPos: t.position, thLeft: t.left, tdPos: d.position, tdLeft: d.left, thText: (th || {}).innerText }
  })
  check('G3a-18 首列冻结（表头与首行单元格 position=sticky / left=0px，计算样式）',
    fz.thPos === 'sticky' && fz.thLeft === '0px' && fz.tdPos === 'sticky' && fz.tdLeft === '0px', JSON.stringify(fz))

  // 左右滑动看全部列
  // H5 下 uni `scroll-view scroll-x` 的真正滚动容器是内层 `.uni-scroll-view`（overflow-x:auto）；
  // 外层自定义元素是 overflow:hidden。别拿外层判横滑 —— 会误判成「滚不动」。
  const scroll = await page.evaluate(async () => {
    const cands = [...document.querySelectorAll('.lqg-ledger .uni-scroll-view, .lqg-ledger')]
    const el = cands.find(e => e.scrollWidth > e.clientWidth) || cands[cands.length - 1]
    if (!el) return { err: 'no table' }
    el.scrollLeft = 99999
    await new Promise(r => setTimeout(r, 400))
    const ths = [...document.querySelectorAll('.lqg-ledger__th')]
    const last = ths[ths.length - 1]
    return {
      cls: el.className, after: el.scrollLeft, scrollWidth: el.scrollWidth, clientWidth: el.clientWidth,
      lastRight: last ? Math.round(last.getBoundingClientRect().right) : null, winW: window.innerWidth,
    }
  })
  await page.screenshot({ path: path.join(S, 'g3a-10-scrolled-right.png'), fullPage: true })
  check('G3a-19 表格可横向滚动（滚动容器 scrollWidth > clientWidth、能滚到最右，滚动后末列进入视口）',
    scroll.scrollWidth > scroll.clientWidth && scroll.after > 0 && scroll.lastRight !== null && scroll.lastRight <= scroll.winW + 130 /* 冻结首列 118 仍盖在上面 */,
    JSON.stringify(scroll))

  // 没有新增 / 保存
  const ledgerBtns = await page.$$eval('button, .lqg-bar button, uni-button', els => els.map(e => e.innerText.trim()).filter(Boolean))
  check('G3a-20 表格页没有「新增 / ＋ / 保存」（导出按钮按本张置灰是允许的）',
    !ledgerBtns.some(t => /新增|保存|添加|\+/.test(t)), JSON.stringify(ledgerBtns))

  // 点一行 → 只读
  await page.locator('.lqg-ledger__td.lqg-ledger__fz').first().click()
  await sleep(3000)
  const viewUrl = page.url()
  const viewBody = await bodyText(page)
  const viewInputs = await page.locator('input,textarea').count()
  const viewSave = await page.locator('button, .form__btn').filter({ hasText: /保存|提交/ }).count()
  await page.screenshot({ path: path.join(S, 'g3a-11-ledger-row-readonly.png'), fullPage: true })
  check('G3a-21 表格页点一行 → mode=view 且只读：没有保存按钮、没有可写控件',
    /mode=view/.test(viewUrl) && viewSave === 0 && viewInputs === 0,
    `url=${viewUrl} inputs=${viewInputs} save=${viewSave} body=${viewBody.slice(0, 90).replace(/\n/g, '|')}`)

  // 类器官工作表列名
  await gotoPage(page, 'pages/ledger/index')
  await page.locator('.lqg-sheets__item', { hasText: '类器官收样' }).first().click()
  await sleep(2500)
  const orgHead = await ledgerHeaders()
  check('G3a-22 类器官收样工作表列名列序 = 甲方模板第 1 行（冻结列最前 + 「切片染色」）',
    JSON.stringify(orgHead) === JSON.stringify(expectOf(tplOrg)),
    `ui=${JSON.stringify(orgHead)} 期望=${JSON.stringify(expectOf(tplOrg))}`)

  check('G3a-23 全程无未捕获前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
} catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 5).join(' | ') : String(e))
} finally {
  const bad = finish()
  console.log(`SHOTS=${S}`)
  await browser.close()
  process.exit(bad === 0 ? 0 : 1)
}
