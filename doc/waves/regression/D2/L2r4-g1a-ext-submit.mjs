/**
 * D2 / r4 / L2 —— 组 1 上半：外部（extA）真填表提交 → 历史编辑记录出现待核验。
 * 本片自写、独立复跑；不用 D2 目录既有脚本的结论（r1 返工改过那里的期望值）。
 *
 * 覆盖 qa_scope L2-1：
 *   外部首页三格没有数字 → 点「样本记录信息表」→ 新增送检（mode=new）→ 我的 → 历史编辑记录出现待核验
 *
 * 微信开发者工具在本沙箱跑不通（EPERM + 需扫码）→ miniapp H5(9200, VITE_MOCK_LOGIN=1) + Playwright 等价覆盖。
 * **开发者工具 / 真机未覆盖**。
 *
 * 前置：后端 8081 + reseed + miniapp 9200。跑法：node doc/waves/regression/D2/L2r4-g1a-ext-submit.mjs
 */
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import { chromium, MP, WS, SEED_LABEL, makeChecker, shotsDir, dbOne, resetToLogin, mockLogin, gotoPage, bodyText, fillByLabel, rowText, segPick, gotoHistory, historyRows, sleep } from './L2r4-lib.mjs'
import { saveState } from './L2r4-state.mjs'

const S = shotsDir('L2r4-mp-g1a')
const { check, finish } = makeChecker('D2-r4-L2-g1a')
const DONOR = 'R4供体甲'
const HOSP = 'R4ZY0001'

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await ctx.newPage()
const pageErrors = []
page.on('pageerror', e => pageErrors.push(e.message))
page.on('console', m => { if (m.type() === 'error') pageErrors.push('console:' + m.text()) })

try {
  await resetToLogin(page)
  await mockLogin(page, 'extA')
  await page.screenshot({ path: path.join(S, 'g1a-01-ext-home.png'), fullPage: true })

  const tiles = await page.$$eval('.lqg-tile', els => els.map(e => e.innerText.split('\n').filter(Boolean)))
  check('G1a-01 外部首页三格、顺序 = 模板顺序',
    JSON.stringify(tiles.map(t => t[1])) === JSON.stringify(['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录']),
    JSON.stringify(tiles.map(t => t[1])))
  const digits = await page.evaluate(() => (document.body.innerText.match(/\d+/g) || []))
  check('G1a-02 外部首页没有任何数字', digits.length === 0, `digits=${JSON.stringify(digits)}`)

  // 点「样本记录信息表」→ 直接进填写页（mode=new 由入口给）
  await page.locator('.lqg-tile', { hasText: '样本记录信息表' }).first().click()
  await sleep(2600)
  const enterUrl = page.url()
  const enterBody = await bodyText(page)
  await page.screenshot({ path: path.join(S, 'g1a-02-entry-new-form.png'), fullPage: true })
  check('G1a-03 点「样本记录信息表」直接进填写页且 mode=new（可填、有提交按钮）',
    /pages\/sample\/form/.test(enterUrl) && /mode=new/.test(enterUrl) && /提交/.test(enterBody),
    `url=${enterUrl} body含提交=${/提交/.test(enterBody)}`)

  // 外部不该看到收样段
  check('G1a-04 外部新增页不渲染收样段（无「收样信息 / 收样日期 / 内部编号」）',
    !/收样信息|收样日期|内部编号|有无固定/.test(enterBody), enterBody.slice(0, 120).replace(/\n/g, '|'))

  // 填一条有效的送检
  await fillByLabel(page, '供体姓名', DONOR)
  await segPick(page, '性别', '男')
  await fillByLabel(page, '年龄', '54')
  await fillByLabel(page, '住院号', HOSP)
  await fillByLabel(page, '组织类型', '胆管组织')
  await segPick(page, '有无病理', '有')
  await fillByLabel(page, '备注', 'D2 r4 L2 新增送检')
  // 来源单位：默认带档案单位；真操作一下底部弹层（外部表单的来源单位也能点）
  const unitRow = page.locator('.wd-input.is-cell', { has: page.locator('.wd-input__label-inner:text-is("来源单位")') }).first()
  if (await unitRow.count() > 0) {
    await unitRow.click()
    await sleep(1000)
    const sheetVisible = await page.locator('.wd-popup, .lqg-sheet').first().isVisible().catch(() => false)
    if (sheetVisible) {
      const chip = page.locator('.lqg-filter__chip, .org__unit').first()
      if (await chip.count() > 0) { await chip.click(); await sleep(800) }
      else await page.keyboard.press('Escape')
    }
    await sleep(400)
  }
  const unitVal = await rowText(page, '来源单位')
  await page.screenshot({ path: path.join(S, 'g1a-03-filled.png'), fullPage: true })
  check('G1a-05 送的这一条：来源单位有值（必填）', !/请选择|请填写/.test(unitVal), unitVal)

  const before = Number(dbOne('SELECT count(*) FROM t_lqg_sample'))
  await page.locator('.form__btn', { hasText: '提交' }).first().click()
  await sleep(3500)
  await page.screenshot({ path: path.join(S, 'g1a-04-after-submit.png'), fullPage: true })
  const after = Number(dbOne('SELECT count(*) FROM t_lqg_sample'))
  const newRow = dbOne(`SELECT id || '|' || submit_no || '|' || verify_status || '|' || submit_source || '|' || sample_kind FROM t_lqg_sample WHERE remark='D2 r4 L2 新增送检' ORDER BY id DESC LIMIT 1`)
  const rawDonor = dbOne("SELECT donor_name FROM t_lqg_sample WHERE remark='D2 r4 L2 新增送检' LIMIT 1")
  const apiDonor = JSON.parse(execFileSync('bash',
    ['doc/verify/api.sh', '--as', 'extA', 'GET', `/mp/ext/sample/${newRow.split('|')[0]}`], { cwd: WS, encoding: 'utf8' })).data.donorName
  check('G1a-06 提交真的落库：+1 行 / pending / external / tissue；donor_name 落的是密文，接口读回 = 我填的明文',
    after === before + 1 && /pending\|external\|tissue/.test(newRow) && rawDonor !== DONOR && apiDonor === DONOR,
    `before=${before} after=${after} row=${newRow} 库里=${rawDonor} 接口=${apiDonor}`)
  const NEW_ID = newRow.split('|')[0]
  saveState({ NEW_ID, SUBMIT_NO: newRow.split('|')[1] })

  // 我的 → 历史编辑记录：这一条要出现且是待核验
  await gotoPage(page, 'pages/me/index')
  await page.screenshot({ path: path.join(S, 'g1a-05-me-ext.png'), fullPage: true })
  const meBody = await bodyText(page)
  check('G1a-07 外部「我的」有「历史编辑记录」、有「单位与组别」、没有「内部管理」',
    /历史编辑记录/.test(meBody) && /单位与组别/.test(meBody) && !/内部管理/.test(meBody),
    meBody.replace(/\n/g, '|').slice(0, 220))

  await gotoHistory(page)
  await page.screenshot({ path: path.join(S, 'g1a-06-history-pending.png'), fullPage: true })
  const rows = await historyRows(page)
  const mineRow = rows.find(r => /R4/.test(r.text) && !/类器官/.test(r.text))
  const byDb = dbOne(`SELECT submit_no FROM t_lqg_sample WHERE id=${NEW_ID}`)
  const hit = rows.find(r => r.code === byDb) || mineRow
  check('G1a-08 历史编辑记录出现这一条、状态 = 待核验',
    !!hit && /待核验/.test(hit.statusText), `code=${byDb} rows=${JSON.stringify(rows.slice(0, 4).map(r => r.code + ':' + r.statusText))}`)

  check('G1a-09 全程无未捕获前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
  console.log(`NEW_ID=${NEW_ID}`)
} catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 5).join(' | ') : String(e))
} finally {
  const bad = finish()
  console.log(`SHOTS=${S}`)
  await browser.close()
  process.exit(bad === 0 ? 0 : 1)
}
