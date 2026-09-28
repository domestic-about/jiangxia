/**
 * D2 / r4 / L2 —— 组 3 下半（类器官收样）：
 *   ① 内部首页四格 → 类器官收样记录 → 新增一条（七列 + 来源单位必填，真操作底部弹层选择器）→ 落库
 *   ② 我的 → 历史编辑记录（类器官页签）点这一条改一个字段 → 保存成功 → 排到最前
 *   ③ 外部（extC）填类器官收样三项提交 → 落 organoid / external / pending
 * ★ 硬断言沿用 g3a：两个页签各自只出现该 sampleKind（本片再核一遍新增后的行）
 *
 * 微信开发者工具在本沙箱跑不通 → miniapp H5 + Playwright 等价覆盖。**开发者工具/真机未覆盖**。
 * 前置：后端 8081 + reseed + miniapp 9200。跑法：node doc/waves/regression/D2/L2r4-g3b-organoid.mjs
 */
import path from 'node:path'
import { chromium, makeChecker, shotsDir, dbOne, resetToLogin, mockLogin, injectToken, mpToken, gotoPage, bodyText, fillByLabel, rowText, gotoHistory, historyRows, sleep } from './L2r4-lib.mjs'

const S = shotsDir('L2r4-mp-g3b')
const { check, finish } = makeChecker('D2-r4-L2-g3b')
const RUN = Date.now().toString().slice(-6)
const INT_TYPE = `R4类器官甲${RUN}`
const INT_NO = `T-l2r4org${RUN}`
const EXT_TYPE = `R4类器官C${RUN}`


/** 点日期字段行 → 底部弹层 → 点「确认」（真操作弹层；默认值 = 今天） */
async function pickDateField(page, label) {
  const i = await page.evaluate((lb) => {
    const rows = [...document.querySelectorAll('.wd-input.is-cell,.wd-cell')]
    return rows.findIndex(e => {
      const t = e.querySelector('.wd-input__label-inner,.wd-cell__title')
      return t && t.innerText.trim() === lb
    })
  }, label)
  if (i < 0) throw new Error(`找不到「${label}」行`)
  await page.locator('.wd-input.is-cell,.wd-cell').nth(i).click()
  await page.waitForSelector('.wd-datetime-picker__action', { timeout: 8000 })
  await sleep(700)
  const acts = page.locator('.wd-datetime-picker__action')
  await acts.nth((await acts.count()) - 1).click()
  await sleep(900)
}

const browser = await chromium.launch({ headless: true })

/** 点某字段行 → 底部弹层选来源单位 → 选第一个 chip（真操作弹层，不是直接写 model） */
async function pickUnitFromSheet(page, preferText) {
  const i = await page.evaluate(() => {
    const rows = [...document.querySelectorAll('.wd-input.is-cell')]
    return rows.findIndex(e => {
      const t = e.querySelector('.wd-input__label-inner')
      return t && t.innerText.trim() === '来源单位'
    })
  })
  if (i < 0) throw new Error('找不到「来源单位」行')
  await page.locator('.wd-input.is-cell').nth(i).click()
  await page.waitForSelector('.org__unit, .lqg-filter__chip', { timeout: 8000 })
  await sleep(700)
  const chips = page.locator('.org__unit')
  const n = await chips.count()
  let picked = ''
  for (let k = 0; k < n; k++) {
    const t = (await chips.nth(k).innerText()).trim()
    if (!preferText || t.includes(preferText)) { await chips.nth(k).click(); picked = t; break }
  }
  if (!picked && n > 0) { await chips.nth(0).click(); picked = (await chips.nth(0).innerText()).trim() }
  await sleep(900)
  return picked
}

try {
  // ── ① 内部新增类器官收样（首页四格 → 类器官收样记录）──────────────────
  const ctx = await browser.newContext({ viewport: { width: 430, height: 900 }, deviceScaleFactor: 2 })
  const page = await ctx.newPage()
  const pageErrors = []
  page.on('pageerror', e => pageErrors.push(e.message))
  await resetToLogin(page)
  await mockLogin(page, 'staff')
  await page.locator('.lqg-tile', { hasText: '类器官收样记录' }).first().click()
  await sleep(2800)
  const url = page.url()
  await page.screenshot({ path: path.join(S, 'g3b-01-int-organoid-new.png'), fullPage: true })
  check('G3b-01 内部首页点「类器官收样记录」直接进填写页 mode=new',
    /pages\/organoid\/form/.test(url) && /mode=new/.test(url), `url=${url}`)

  const pickedUnit = await pickUnitFromSheet(page, 'A 医院')
  check('G3b-02 ★ 来源单位必填：真的走底部弹层选择器选出了单位（r1 漏这步会误报）',
    !!pickedUnit && !/列表里没有/.test(pickedUnit), `选中=${pickedUnit}`)
  await fillByLabel(page, '类器官类型', INT_TYPE)
  await pickDateField(page, '收样日期')
  await fillByLabel(page, '内部编号', INT_NO)
  await fillByLabel(page, '操作人', '李工')
  const bodyInt = await bodyText(page)
  await page.screenshot({ path: path.join(S, 'g3b-02-int-organoid-filled.png'), fullPage: true })
  const before = Number(dbOne('SELECT count(*) FROM t_lqg_sample'))
  await page.locator('button, .org__btn').filter({ hasText: /提交|保存/ }).first().click()
  await sleep(3500)
  await page.screenshot({ path: path.join(S, 'g3b-03-int-organoid-submitted.png'), fullPage: true })
  const after = Number(dbOne('SELECT count(*) FROM t_lqg_sample'))
  const row = dbOne(`SELECT id || '|' || COALESCE(internal_no,'-') || '|' || sample_kind || '|' || submit_source || '|' || verify_status || '|' || COALESCE(organoid_type,'-') FROM t_lqg_sample WHERE internal_no='${INT_NO}'`)
  check('G3b-03 新增落库：organoid / internal / valid + 我填的类器官类型/内部编号',
    after === before + 1 && new RegExp(`\\|${INT_NO}\\|organoid\\|internal\\|valid\\|${INT_TYPE}$`).test(row),
    `before=${before} after=${after} row=${row}`)
  check('G3b-04 内部类器官新增页有七项（来源单位/类器官类型/收样日期/内部编号/处理时间/细胞活率报告/操作人）',
    ['来源单位', '类器官类型', '收样日期', '内部编号', '处理时间', '细胞活率报告', '操作人'].every(k => bodyInt.includes(k)),
    bodyInt.replace(/\n/g, '|').slice(0, 220))
  const NEW_ORG_ID = row.split('|')[0]

  // ── ② 历史编辑记录（类器官页签）改一条 → 排最前 ────────────────────────
  await gotoHistory(page, '类器官收样记录')
  const rows = await historyRows(page)
  await page.screenshot({ path: path.join(S, 'g3b-04-org-history.png'), fullPage: true })
  const codes = rows.map(r => r.code)
  const kinds = codes.map(c => dbOne(`SELECT sample_kind FROM t_lqg_sample WHERE del_flag='0' AND (internal_no='${c}' OR (coalesce(internal_no,'')='' AND submit_no='${c}')) LIMIT 1`))
  check('G3b-05 ★ 类器官页签逐行 sample_kind=organoid（新增后仍不串台）',
    codes.length > 0 && kinds.every(k => k === 'organoid'), `rows=${JSON.stringify(codes.map((c, i) => c + ':' + kinds[i]))}`)
  const hit = rows.find(r => r.code === INT_NO)
  check('G3b-06 新建的这条出现在类器官页签里', !!hit, `codes=${JSON.stringify(codes)}`)

  await page.locator('.his__item', { hasText: INT_NO }).first().click()
  await sleep(3000)
  const editUrl = page.url()
  const editBody = await bodyText(page)
  await page.screenshot({ path: path.join(S, 'g3b-05-org-edit.png'), fullPage: true })
  check('G3b-07 点这一条进 organoid/edit 且可改（有保存按钮）',
    /pages\/organoid\/form/.test(editUrl) && /mode=edit/.test(editUrl) && /保存/.test(editBody),
    `url=${editUrl} body=${editBody.slice(0, 80).replace(/\n/g, '|')}`)
  const typeBefore = await rowText(page, '类器官类型')
  await fillByLabel(page, '类器官类型', `${INT_TYPE}改`)
  await page.waitForTimeout(700)
  const typeTyped = await rowText(page, '类器官类型')
  await page.screenshot({ path: path.join(S, 'g3b-05a-typed.png'), fullPage: true })
  await page.locator('button, .org__btn').filter({ hasText: /保存|提交/ }).first().click()
  await sleep(3500)
  await page.screenshot({ path: path.join(S, 'g3b-06-org-saved.png'), fullPage: true })
  const dbAfter = dbOne(`SELECT organoid_type || '|' || verify_status || '|' || CASE WHEN update_by IS NULL THEN 'no' ELSE 'yes' END FROM t_lqg_sample WHERE internal_no='${INT_NO}'`)
  check('G3b-08 改后保存成功：库里类型 = 改后值、update_by 有值',
    dbAfter === `${INT_TYPE}改|valid|yes`, `改前=${typeBefore} 页面上打的=${typeTyped} 库里=${dbAfter}`)
  await gotoHistory(page, '类器官收样记录')
  const rows2 = await historyRows(page)
  await page.screenshot({ path: path.join(S, 'g3b-07-org-first.png'), fullPage: true })
  check('G3b-09 改完排到最前（第一行 = T-l2r4org01）',
    rows2[0] && rows2[0].code === INT_NO, `codes=${JSON.stringify(rows2.map(r => r.code))}`)

  // 两页签再核一次不串台
  await gotoHistory(page, '样本记录信息表')
  const sRows = await historyRows(page)
  const sCodes = sRows.map(r => r.code)
  const sKinds = sCodes.map(c => dbOne(`SELECT sample_kind FROM t_lqg_sample WHERE del_flag='0' AND (internal_no='${c}' OR (coalesce(internal_no,'')='' AND submit_no='${c}')) LIMIT 1`))
  await page.screenshot({ path: path.join(S, 'g3b-08-sample-tab-after.png'), fullPage: true })
  check('G3b-10 ★ 新增类器官后，样本页签仍逐行 tissue（不串台）',
    sCodes.length > 0 && sKinds.every(k => k === 'tissue'), `rows=${JSON.stringify(sCodes.map((c, i) => c + ':' + sKinds[i]))}`)
  check('G3b-11 内部全程无未捕获前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))

  // ── ③ 外部 extC 填类器官收样三项 → pending ─────────────────────────────
  const pc = await ctx.newPage()
  const errsC = []
  pc.on('pageerror', e => errsC.push(e.message))
  await resetToLogin(pc)
  await injectToken(pc, mpToken('extC', '13800000013'))
  await gotoPage(pc, 'pages/organoid/form?mode=new', 3200)
  const cBody = await bodyText(pc)
  await pc.screenshot({ path: path.join(S, 'g3b-09-extC-form.png'), fullPage: true })
  check('G3b-12 外部（extC）类器官表单只有三项（来源单位 / 类器官类型 / 备注），无收样段',
    ['来源单位', '类器官类型', '备注'].every(k => cBody.includes(k)) && !/收样日期|内部编号|处理时间|细胞活率|操作人/.test(cBody),
    cBody.replace(/\n/g, '|').slice(0, 180))
  await fillByLabel(pc, '类器官类型', EXT_TYPE)
  await fillByLabel(pc, '备注', 'D2 r4 L2 外部类器官')
  const cBefore = Number(dbOne('SELECT count(*) FROM t_lqg_sample'))
  await pc.locator('button, .org__btn').filter({ hasText: /提交|保存/ }).first().click()
  await sleep(3500)
  await pc.screenshot({ path: path.join(S, 'g3b-10-extC-submitted.png'), fullPage: true })
  const cAfter = Number(dbOne('SELECT count(*) FROM t_lqg_sample'))
  const cRow = dbOne(`SELECT sample_kind || '|' || submit_source || '|' || verify_status FROM t_lqg_sample WHERE organoid_type='${EXT_TYPE}' ORDER BY id DESC LIMIT 1`)
  check('G3b-13 ★ extC 三项提交后落库 = organoid / external / pending',
    cAfter === cBefore + 1 && cRow === 'organoid|external|pending', `before=${cBefore} after=${cAfter} row=${cRow}`)
  check('G3b-14 extC 全程无未捕获前端异常', errsC.length === 0, JSON.stringify(errsC.slice(0, 3)))
} catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 5).join(' | ') : String(e))
} finally {
  const bad = finish()
  console.log(`SHOTS=${S}`)
  await browser.close()
  process.exit(bad === 0 ? 0 : 1)
}
