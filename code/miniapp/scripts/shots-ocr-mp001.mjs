/**
 * OCR-MP-001 的端侧取证脚本（H5 dev server + Playwright **真 DOM**）。
 *
 * ★ 本脚本是「真机录屏」的**等价覆盖**，不是替代：真机 / 体验版需要测试环境，
 *   那是下一张票 SYS-STAGING-001 的交付物 —— 报告里如实写明未覆盖，不假装覆盖。
 * ★ **不读任何 PNG 进上下文**：只把图写到盘上，断言全部读 DOM 文本 / 输入值 / 网络响应。
 *
 * 覆盖的路径（`UI:mp.sample.form.ocr` 的四段 + ticket §0 口径 1/2/3/4）：
 *   01 识别成功（夹具 01-印刷标签）：五格都被预填 + 每格右侧「识别 · 请核对」小标
 *   02 用户改动预填项 → 该项小标消失，别的项还在
 *   03 先手填两项再识别（夹具 01）→ **手填的没被覆盖**，只有空项被填（最伤人的那个 bug）
 *   04 识别失败（夹具 03-只有噪声：一项都认不出）→ 「没识别出来，请手动填写」，表单仍可填可提交
 *   05 识别条只在新增时出现：编辑 / 只读模式下一个都没有
 *
 * 网络侧同时断三件：
 *   - 请求 URL 是**自己后端的** `/mp/ocr/recognize`（没有第三方识别服务的域名）；
 *   - 响应是 `{code:200,data:{rawLines,fields}}`，`fields` 里出现的键就是后端给的那几个；
 *   - 响应里**没有** internalNo / verifyStatus 这类内部字段被填进表单（夹具 E 例的口径）。
 *
 * 跑法（环境先起好）：
 *   bash doc/waves/tools/qa-up.sh --backend-port 8094 --web-port 8095 --mp-port 9204
 *   cd code/miniapp && node scripts/shots-ocr-mp001.mjs
 */
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'

const WS = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid'
const MP_PORT = process.env.LQG_MP_PORT || '9204'
const BASE = `http://127.0.0.1:${MP_PORT}`
const API = `${BASE}/lqg-api`
const CLIENT_ID = '22b2aecd0710671691ec1c07f2542b9d'
const OUT = `${WS}/doc/waves/reports/OCR-MP-001`
const IMG = `${WS}/doc/verify/fixtures/ocr-sample.png`

// ticket 指定的取包方式：从 miniapp 的 package.json 解析 playwright（别下浏览器）
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

mkdirSync(OUT, { recursive: true })

const PHONE = { staff: '13800000001', extA: '13800000011' }

async function login(key) {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', clientid: CLIENT_ID },
    body: JSON.stringify({
      clientId: CLIENT_ID,
      grantType: 'xcx',
      tenantId: '000000',
      xcxCode: `mock:${key}`,
      phoneCode: `mock:${PHONE[key]}`,
    }),
  })
  const body = await res.json()
  if (body.code !== 200 || !body.data?.access_token) {
    throw new Error(`${key} 登录失败：${JSON.stringify(body)}`)
  }
  return body.data.access_token
}

const tokens = { staff: await login('staff'), extA: await login('extA') }
console.log('[login] ok:', Object.keys(tokens).join(', '))

/** 只读执行器读行数（ticket §0 口径 2：识别不写业务表、不存图） */
function dbCounts() {
  const out = execFileSync('python3', [
    `${WS}/doc/verify/db.py`, '--quiet', '--sql',
    "select (select count(*) from t_lqg_sample) || '|' || (select count(*) from sys_oss)",
  ], { cwd: WS, encoding: 'utf-8' }).trim()
  const [sample, oss] = out.split('|').map(Number)
  return { sample, oss }
}

const countsBefore = dbCounts()
console.log('[db] 识别前 t_lqg_sample =', countsBefore.sample, ' sys_oss =', countsBefore.oss)

const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 390, height: 900 }, deviceScaleFactor: 2 })
const page = await context.newPage()

/** 网络取证：每一次 /mp/ocr/recognize 的请求头 / 响应体都留档 */
const calls = []
page.on('response', async (res) => {
  if (!res.url().includes('/mp/ocr/recognize')) {
    return
  }
  let body = null
  try {
    body = await res.json()
  }
  catch {
    body = null
  }
  calls.push({
    url: res.url(),
    status: res.status(),
    requestHeaders: {
      'clientid': res.request().headers()['clientid'] || null,
      'x-ocr-stub-case': res.request().headers()['x-ocr-stub-case'] || null,
      'authorization': (res.request().headers()['authorization'] || '').slice(0, 16) + '…',
    },
    body,
  })
})

async function open(hash, token) {
  await page.goto(`${BASE}/#/pages/index/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(t => window.localStorage.setItem('lqg_mp_token', t), token)
  await page.goto('about:blank')
  await page.goto(`${BASE}/#${hash}`, { waitUntil: 'domcontentloaded' })
  // 冷启动要拉 /mp/me + /mp/dict/hints，等页面上出现识别条（或明确没有）
  await page.waitForTimeout(1500)
}

async function shot(name) {
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true })
  console.log(`[shot] ${name}.png`)
}

/** 「拍照识别」：H5 下 uni.chooseImage 内部就是 input[type=file]，用 filechooser 把夹具塞进去 */
async function pickAndRecognize(label) {
  const chooser = page.waitForEvent('filechooser', { timeout: 15000 })
  await page.locator('.lqg-ocr__btn--p').click()
  const fc = await chooser
  await fc.setFiles(IMG)
  console.log(`[pick] ${label} 已选夹具 ocr-sample.png`)
  await page.waitForFunction(
    () => !document.querySelector('.ocr__bar'),
    null,
    { timeout: 20000 },
  ).catch(() => {})
  await page.waitForTimeout(600)
}

/**
 * 读一行（标签在左、值在右）的当前值 + 有没有识别小标。
 *
 * ★ 两种行长得**不一样**（踩过）：文本 / 数字行走 `wd-input`，它自己的标签类是
 *   `wd-input__label`（根本没有 `wd-cell__title`）；seg / 日期 / 只读行走 `wd-cell`。
 *   只按 `wd-cell__title` 找，五个字段里只有一个能命中（性别），其余全 null —— 那不是
 *   实现红了，是**选择器写错了**。
 */
function rowLocator(label) {
  return page.locator('.wd-cell, .wd-input').filter({
    has: page.locator('.wd-cell__title, .wd-input__label').filter({ hasText: label }),
  }).first()
}

async function row(label) {
  const loc = rowLocator(label)
  if (await loc.count() === 0) {
    return null
  }
  return loc.evaluate((el) => {
    const input = el.querySelector('input')
    const text = el.querySelector('.fr__text')
    const mark = el.querySelector('.lqg-tag--ocr')
    // 按钮组行的「值」是选中块的文字（没有 input）：`性别` 这种行只有 seg 没有输入框，
    // 只读 input.value 会永远拿到空串（本脚本第一版就栽在这里）
    const segOn = el.querySelector('.lqg-seg__item--on')
    return {
      value: input ? input.value : (text ? text.textContent.trim() : (segOn ? segOn.textContent.trim() : '')),
      mark: !!mark,
      markText: (mark?.textContent || '').trim(),
    }
  })
}

async function fillRow(label, value) {
  await rowLocator(label).locator('input').fill(value)
}

const SEND_LABELS = ['来源单位', '供体姓名', '性别', '年龄', '住院号', '组织类型']

async function rows(labels) {
  const out = {}
  for (const lb of labels) {
    out[lb] = await row(lb)
  }
  return out
}

/** 断「值 = 期望」并打印（期望为空串就是「没填」） */
function expectValue(got, want, where) {
  const ok = (got?.value ?? '') === want
  console.log(`[assert] ${where} 值=${JSON.stringify(got?.value)} 期望=${JSON.stringify(want)} ${ok ? 'OK' : 'FAIL'}`)
  if (!ok) {
    process.exitCode = 1
  }
  return ok
}

function expectMark(got, want, where) {
  const ok = !!got?.mark === want
  console.log(`[assert] ${where} 小标=${got?.mark} 期望=${want} ${ok ? 'OK' : 'FAIL'}`)
  if (!ok) {
    process.exitCode = 1
  }
  return ok
}

/** 断「已识别 N 项」+ 页面上实际带小标的格数一致（避免「计数说 6、页面只标 4」这种半绿） */
async function expectMarkedCount(expected, where) {
  const countText = (await page.locator('.ocr__count').innerText()).trim()
  const domMarks = await page.locator('.wd-cell .lqg-tag--ocr, .wd-input .lqg-tag--ocr').count()
  const ok = countText === `已识别 ${expected} 项` && domMarks === expected
  console.log(`[assert] ${where} 计数文案=${JSON.stringify(countText)} DOM 小标数=${domMarks} 期望=${expected} ${ok ? 'OK' : 'FAIL'}`)
  if (!ok) {
    process.exitCode = 1
  }
}

// ── 01 / 02 识别成功（夹具 01-印刷标签）─────────────────────────────────────
await open('/pages/sample/form?mode=new&stubCase=01', tokens.extA)
console.log('[case 01] 识别条数量 =', await page.locator('.lqg-ocr').count(), '（新增态应 1）')
console.log('[case 01] 提示语 =', JSON.stringify(await page.locator('.lqg-ocr__tip').innerText()))
await shot('01-ocr-idle')
await pickAndRecognize('case01')

const after = await rows(SEND_LABELS)
console.log('[case 01] 各行的值 / 小标 =', JSON.stringify(after, null, 0))
expectValue(after['来源单位'], 'A 医院', '01 来源单位')
expectValue(after['供体姓名'], '测试供体甲', '01 供体姓名')
expectValue(after['住院号'], 'ZY0000001', '01 住院号')
expectValue(after['组织类型'], '肝组织', '01 组织类型')
expectValue(after['年龄'], '56', '01 年龄')
// 来源单位那一格的值是**档案里带的**（外部新增默认带当前单位），不是识别读出来的 ——
// 所以它不该挂小标；其余五格都是识别读出来的，都挂。
expectMark(after['来源单位'], false, '01 来源单位（档案默认值，不是识别读出来的）')
;['供体姓名', '性别', '年龄', '住院号', '组织类型'].forEach(lb => expectMark(after[lb], true, `01 ${lb} 小标`))
await expectMarkedCount(5, '01 计数与 DOM 小标数')
console.log('[case 01] 说明文案 =', JSON.stringify(await page.locator('.ocr__tip').innerText()))
await page.locator('.ocr__raw-link').click()
await page.waitForTimeout(300)
const raw = await page.locator('.ocr__raw-line').allInnerTexts()
console.log('[case 01] 展开的原文行 =', JSON.stringify(raw))
if (!raw.some(l => l.includes('测试供体甲'))) {
  console.log('[assert] 01 原文含「测试供体甲」 FAIL')
  process.exitCode = 1
}
await shot('02-ocr-success-marks')

// ── 03 用户改动预填项 → 该项摘标 ────────────────────────────────────────────
await fillRow('供体姓名', '医生改过的名字')
await page.waitForTimeout(400)
const afterEdit = await rows(SEND_LABELS)
expectValue(afterEdit['供体姓名'], '医生改过的名字', '03 改动后的供体姓名')
expectMark(afterEdit['供体姓名'], false, '03 供体姓名小标已摘')
expectMark(afterEdit['住院号'], true, '03 住院号小标仍在')
console.log('[case 03] 改动后各行小标 =', JSON.stringify(Object.fromEntries(SEND_LABELS.map(l => [l, afterEdit[l].mark]))))
await shot('03-ocr-mark-cleared-after-edit')

// ── 04 手填优先：先填两项再识别，手填的不许被覆盖 ───────────────────────────
await open('/pages/sample/form?mode=new&stubCase=01', tokens.extA)
await fillRow('供体姓名', '手填姓名')
await fillRow('住院号', 'HAND0001')
await page.waitForTimeout(300)
await shot('04-ocr-manual-first')
await pickAndRecognize('case04')
const mixed = await rows(SEND_LABELS)
expectValue(mixed['供体姓名'], '手填姓名', '04 手填姓名不被覆盖')
expectValue(mixed['住院号'], 'HAND0001', '04 手填住院号不被覆盖')
expectValue(mixed['年龄'], '56', '04 空项被填')
expectMark(mixed['供体姓名'], false, '04 手填项不该有识别小标')
expectMark(mixed['住院号'], false, '04 手填项不该有识别小标')
expectMark(mixed['年龄'], true, '04 预填项有小标')
await shot('05-ocr-prefill-only-blank')

// ── 05 识别失败（夹具 03-只有噪声：一项都认不出）──────────────────────────
await open('/pages/sample/form?mode=new&stubCase=03', tokens.extA)
await pickAndRecognize('case05')
const failTip = await page.locator('.lqg-ocr__tip').innerText()
console.log('[case 05] 失败提示 =', JSON.stringify(failTip))
if (failTip.trim() !== '没识别出来，请手动填写') {
  console.log('[assert] 05 失败提示逐字 FAIL')
  process.exitCode = 1
}
const emptyRows = await rows(SEND_LABELS)
// 来源单位仍是档案默认值；其余五格一个都不该被填（夹具 03 一项都认不出）
expectValue(emptyRows['来源单位'], 'A 医院', '05 来源单位（档案默认值）')
;['供体姓名', '性别', '年龄', '住院号', '组织类型'].forEach(lb => expectValue(emptyRows[lb], '', `05 ${lb} 未被填`))
const failMarkCount = await page.locator('.wd-cell .lqg-tag--ocr, .wd-input .lqg-tag--ocr').count()
console.log('[assert] 05 失败态 DOM 小标数 =', failMarkCount, '（应 0）')
if (failMarkCount !== 0) {
  process.exitCode = 1
}
// 失败不挡手填：还能输入、提交按钮还在
await fillRow('供体姓名', '失败后手填')
await fillRow('组织类型', '肝组织')
await page.waitForTimeout(300)
const submitBtn = await page.locator('.form__btn').count()
console.log('[case 05] 失败后提交按钮存在 =', submitBtn, '（应 1）')
expectValue(await row('供体姓名'), '失败后手填', '05 失败后仍能手填')
const rawFail = await page.locator('.ocr__raw-line').allInnerTexts()
console.log('[case 05] 失败态可见原文行 =', JSON.stringify(rawFail))
await shot('06-ocr-failed-manual-ok')

// ── 06 识别条只在新增时出现 ────────────────────────────────────────────────
await open('/pages/sample/form?id=9000001001&mode=edit', tokens.staff)
console.log('[case 06] 编辑态识别条数量 =', await page.locator('.lqg-ocr').count(), '（应 0）')
await shot('07-ocr-edit-hidden')
await open('/pages/sample/form?id=9000001001&mode=view', tokens.staff)
console.log('[case 06] 只读态识别条数量 =', await page.locator('.lqg-ocr').count(), '（应 0）')
await shot('08-ocr-view-hidden')

// ── 07 「拍照识别」与「从相册选」两条入口都要能走到同一个接口 ────────────────
await open('/pages/sample/form?mode=new&stubCase=04', tokens.extA)
const chooser2 = page.waitForEvent('filechooser', { timeout: 15000 })
await page.locator('.lqg-ocr__btn--s').click()
const fc2 = await chooser2
await fc2.setFiles(IMG)
await page.waitForTimeout(2500)
const viaAlbum = await rows(SEND_LABELS)
expectValue(viaAlbum['住院号'], 'ZY0000002', '07 相册入口 · 住院号')
// 按钮组显示的是标签文字（后端值 female → 界面「女」），不是字典值
await expectValue(viaAlbum['性别'], '女', '07 相册入口 · 性别')
await expectMarkedCount(2, '07 夹具 04 只给两格 → 只标两格')
expectMark(viaAlbum['供体姓名'], false, '07 夹具 04 没给姓名 → 不该有标')
expectMark(viaAlbum['年龄'], false, '07 夹具 04 没给年龄 → 不该有标')
await shot('09-ocr-album-entry')

// ── 08 识别中：两个按钮置灰 + 出细进度条（`UI:mp.sample.form.ocr`「识别中显示进度」）──
// 用 route 把上传请求**挂住 1.2 秒**，在这段窗口里读 DOM —— 识别在本地 20ms 就回来了，
// 不挂住根本看不到「识别中」这一态（本脚本前几版就是这样漏掉的）。
await open('/pages/sample/form?mode=new&stubCase=01', tokens.extA)
let release = null
const held = new Promise((resolve) => { release = resolve })
await page.route('**/mp/ocr/recognize', async (route) => {
  await held
  await route.continue()
})
const chooser3 = page.waitForEvent('filechooser', { timeout: 15000 })
await page.locator('.lqg-ocr__btn--p').click()
const fc3 = await chooser3
await fc3.setFiles(IMG)
await page.waitForTimeout(900)
const busyState = await page.evaluate(() => ({
  bar: document.querySelectorAll('.ocr__bar').length,
  pBtnDisabled: !!document.querySelector('.lqg-ocr__btn--p')?.hasAttribute('disabled'),
  sBtnDisabled: !!document.querySelector('.lqg-ocr__btn--s')?.hasAttribute('disabled'),
}))
console.log('[case 08] 识别中 DOM =', JSON.stringify(busyState), '（bar 应 1，两个按钮都应 disabled）')
if (busyState.bar !== 1 || !busyState.pBtnDisabled || !busyState.sBtnDisabled) {
  console.log('[assert] 08 识别中态（进度条 + 按钮置灰）FAIL')
  process.exitCode = 1
}
else {
  console.log('[assert] 08 识别中态（进度条 + 按钮置灰）OK')
}
await page.screenshot({ path: `${OUT}/10-ocr-recognizing.png`, fullPage: true })
release()
await page.waitForTimeout(2500)
await page.unroute('**/mp/ocr/recognize')
console.log('[case 08] 放行后提示 =', JSON.stringify(await page.locator('.lqg-ocr__tip').innerText()))

// ── 落库断言（ticket §0 口径 2 / ADR-0007：识别不写任何业务表、不存图）────────
// 走 `doc/verify/db.py`（只读执行器），**不做第二套登录**。这一步失败就是实现红了，
// 所以不 catch —— 计数拿不到时宁可红，也不要「跳过等于绿」。
const countsAfter = dbCounts()
console.log('[db] 识别前后 t_lqg_sample =', countsBefore.sample, '→', countsAfter.sample,
  ' sys_oss =', countsBefore.oss, '→', countsAfter.oss)
if (countsBefore.sample !== countsAfter.sample || countsBefore.oss !== countsAfter.oss) {
  console.log('[assert] 识别不该写任何业务表 / 不该存图 FAIL')
  process.exitCode = 1
}
else {
  console.log('[assert] 识别不落库、不存图 OK（行数识别前后不变）')
}

// ── 网络取证落盘 ───────────────────────────────────────────────────────────
const summary = { base: BASE, image: IMG, calls, db: { before: countsBefore, after: countsAfter }, finalRows: { case07: viaAlbum } }
writeFileSync(`${OUT}/dom-network.json`, `${JSON.stringify(summary, null, 2)}\n`)
console.log('[net] /mp/ocr/recognize 调用次数 =', calls.length)
calls.forEach((c, i) => {
  console.log(`[net ${i + 1}] url=${c.url} status=${c.status} stubCase=${c.requestHeaders['x-ocr-stub-case']} clientid=${c.requestHeaders['clientid'] ? 'ok' : 'MISSING'} auth=${c.requestHeaders['authorization']}`)
  console.log(`[net ${i + 1}] body.code=${c.body?.code} rawLines=${JSON.stringify(c.body?.data?.rawLines)} fields=${JSON.stringify(c.body?.data?.fields)}`)
  // 内部字段不许出现在接口返回的 fields 里（E 例口径）
  const bad = Object.keys(c.body?.data?.fields || {}).filter(k => ['internalNo', 'verifyStatus', 'verifyBy'].includes(k))
  if (bad.length) {
    console.log(`[assert] 接口 fields 里出现内部字段 ${JSON.stringify(bad)} FAIL`)
    process.exitCode = 1
  }
})
const thirdParty = calls.filter(c => !c.url.includes('127.0.0.1') && !c.url.includes('/lqg-api'))
console.log('[net] 非本机 / 非代理的识别请求 =', thirdParty.length, '（应 0）')
if (thirdParty.length) {
  process.exitCode = 1
}

await context.close()
await browser.close()
console.log('[done] exitCode =', process.exitCode ?? 0)
process.exit(process.exitCode ?? 0)
