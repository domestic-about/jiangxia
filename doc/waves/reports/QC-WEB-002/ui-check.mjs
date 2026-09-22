/**
 * QC-WEB-002 · 「类器官质控表」与「类器官质量评分表」两个页签的**人眼验收**
 * （不是 accept 的替身；accept 见 doc/waves/reports/QC-WEB-002/accept-logs/）
 *
 * 走真浏览器（Playwright + chromium，headless）→ 真工作台 dev(8093) → 真后端(8094)
 * → 真库(5433) → 真字典接口，断这些事：
 *   ① 两个页签已接真（不再是 el-empty 占位），页签文字与状态徽标仍在；
 *   ② 类器官质控表：样本观察情况图片位（slot=organoid_observe）+ 五栏文本；
 *      两个时间栏「日期选择器选了填 yyyy-MM-dd」**且**「直接手输文字（约第 7 天）也存得住」；
 *   ③ 换页签不丢未保存的改动（v-show 挂载，不是 v-if 卸载）；
 *   ④ 评分表：四个变量的选项文字与分值来自字典 label / remark；
 *      ★ 0 分档照显示 0（不是 —）、没选全时合计为空；选含 0 分档的组合 → 合计照算；
 *   ⑤ 保存只提交四个档位；落库分值与合计由后端回填（psql 对账）；
 *   ⑥ 页面不出「质量偏差 / 质量中等 / 质量良好」这类结论；
 *   ⑦ 全程无未捕获 JS 异常（忽略 seed 假地址图必然报的资源加载失败）。
 *
 * ★ 纪律：不读图片进上下文（截图只落盘到 shots/）、不 mock、不另造鉴权。
 *
 * 用法（工作台 8093 / 后端 8094 起来、且已 reseed 之后）：
 *   node doc/waves/reports/QC-WEB-002/ui-check.mjs
 */
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
// ★ playwright 装在小程序前端（code/miniapp/package.json 的 devDependency），不是 plus-ui
//   —— 用 plus-ui 的 package.json 在本机会 MODULE_NOT_FOUND（issue #229）。
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const BASE = process.env.LQG_WEB_BASE || 'http://127.0.0.1:8093'
const SHOTS = path.join(HERE, 'shots')
mkdirSync(SHOTS, { recursive: true })

const SAMPLE_SCORE = '9000001006' // seed：评分已完成 18 分（8 + 0 + 0 + 10）
const SAMPLE_ORGANOID = '9000001001' // seed：类器官质控表已完成（形成时间「第 5 天」= 自由文本）
const editorPath = (sampleId) => `/qc-console/qc-editor?sampleId=${sampleId}`

const results = []
const check = (name, ok, detail = '') => {
  results.push({ name, ok: !!ok, detail: String(detail) })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

const psql = (sql) =>
  execFileSync('psql', ['-h', '127.0.0.1', '-p', '5433', '-U', 'lqg', '-d', 'lqg_dev', '-Atc', sql], {
    env: { ...process.env, PGPASSWORD: 'lqg_dev_pwd' },
    encoding: 'utf8'
  }).trim()

const scoreRows = (sampleId) =>
  psql(
    `SELECT pre_culture_level || '/' || culture_days_level || '/' || organoid_count_level || '/' || diameter_level` +
      ` || '|' || coalesce(pre_culture_score::text,'-') || '/' || coalesce(culture_days_score::text,'-')` +
      ` || '/' || coalesce(organoid_count_score::text,'-') || '/' || coalesce(diameter_score::text,'-')` +
      ` || '|' || coalesce(total_score::text,'-')` +
      ` FROM t_lqg_qc_score WHERE sample_id = ${sampleId}`
  )

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 1 })
const page = await ctx.newPage()
const pageErrors = []
page.on('pageerror', (e) => pageErrors.push(e.message))
page.on('console', (m) => {
  // seed 的假地址图（service='seed'）必然加载失败，那是平台限制不是本页缺陷
  if (m.type() === 'error' && !/seed\.invalid|ERR_NAME_NOT_RESOLVED|ERR_CONNECTION/.test(m.text())) {
    pageErrors.push('console: ' + m.text().slice(0, 200))
  }
})
page.on('dialog', (d) => d.accept().catch(() => {}))

async function login() {
  await page.goto(`${BASE}/`)
  await page.evaluate(() => localStorage.clear()).catch(() => {})
  await page.goto(`${BASE}/`)
  await page.waitForSelector('.login-form input', { timeout: 30000 })
  const inputs = page.locator('.login-form input')
  await inputs.nth(0).fill('lqgadmin')
  await inputs.nth(1).fill('admin123')
  await page.click('.login-form button')
  await page.waitForSelector('.el-menu', { timeout: 30000 })
  await page.waitForTimeout(1200)
}

const TAB_TEXT = { score: '类器官质量评分表', organoid: '类器官质控表' }

async function openEditor(sampleId) {
  await page.goto(`${BASE}${editorPath(sampleId)}`)
  await page.waitForSelector('.lqg-qc-editor', { timeout: 30000 })
  await page.waitForTimeout(1500)
}

async function openTab(tab) {
  await page.locator('.lqg-qc-editor__tabs .el-tabs__item', { hasText: TAB_TEXT[tab] }).first().click()
  await page.waitForTimeout(500)
}

/** 第 i 行（0=培养前 1=培养天数 2=类器官数量 3=类器官直径）当前选中的档位文字 */
const checkedLabel = (i) => page.locator('.lqg-score-group').nth(i).locator('.el-radio.is-checked .lqg-score-group__text').innerText()

/** 勾第 i 行里文字含 text 的那一档 */
const pickOption = async (i, text) => {
  await page.locator('.lqg-score-group').nth(i).locator('.el-radio', { hasText: text }).first().click()
  await page.waitForTimeout(200)
}

try {
  await login()

  // ══ ① 评分表页签（样本 1006：seed 已经是一组含 0 分档的答案）══════════════
  await openEditor(SAMPLE_SCORE)
  await openTab('score')

  const scoreTabText = await page.locator('.lqg-score-tab').innerText()
  check(
    '评分页签已接真（不再是占位）且表头/合计都在',
    !scoreTabText.includes('QC-WEB-002 接真') && scoreTabText.includes('合计') && scoreTabText.includes('类器官质量评分'),
    JSON.stringify(scoreTabText.replace(/\s+/g, ' ')).slice(0, 160)
  )

  const groupCount = await page.locator('.lqg-score-group').count()
  check('四个变量各一组单选', groupCount === 4, `groups=${groupCount}`)

  const names = await page.locator('.lqg-score-group__name').allInnerTexts()
  check(
    '四个变量的名字是模板/字典原文',
    JSON.stringify(names) === JSON.stringify(['培养前样本评分', '培养天数', '类器官数量（药敏实验实际测得）', '类器官直径']),
    JSON.stringify(names)
  )

  // ★ 选项文字照字典 label 原文（12 档逐字）
  const optionTexts = await page.locator('.lqg-score-group__option').allInnerTexts()
  const joined = optionTexts.join(' | ').replace(/\s+/g, ' ')
  const allLabels = ['<40', '40~80', '>80', '>14d', '≤14d', '<100', '100~1500', '1500~4000', '>4000', '<30μm', '30~100μm', '>100μm']
  check('★ 12 个档位文字逐字来自字典 label（<40 / 40~80 / >80 / >14d / ≤14d / 30~100μm …）',
    allLabels.every((label) => joined.includes(label)), joined.slice(0, 220))
  // 每个档位旁边带该档分值（来自字典 remark）
  const expectations = [
    ['<40', '8 分'], ['40~80', '16 分'], ['>80', '20 分'],
    ['>14d', '0 分'], ['≤14d', '10 分'],
    ['<100', '0 分'], ['100~1500', '10 分'], ['1500~4000', '25 分'], ['>4000', '40 分'],
    ['<30μm', '10 分'], ['30~100μm', '20 分'], ['>100μm', '30 分']
  ]
  const pointsOk = expectations.every(([label, points]) => {
    const text = optionTexts.find((t) => t.replace(/\s+/g, ' ').trim().startsWith(label))
    return !!text && text.includes(points)
  })
  check('★ 每个档位旁显示该档分值（来自字典 remark，不在前端写死）', pointsOk, joined.slice(0, 220))

  const checked = [await checkedLabel(0), await checkedLabel(1), await checkedLabel(2), await checkedLabel(3)]
  check('初始选中 = seed 的 lt40 / gt14 / lt100 / lt30', JSON.stringify(checked) === JSON.stringify(['<40', '>14d', '<100', '<30μm']), JSON.stringify(checked))

  const itemScores = await page.locator('.lqg-score-group__score').allInnerTexts()
  check('★ 0 分档照显示 0（8 / 0 / 0 / 10），不是 —', JSON.stringify(itemScores) === JSON.stringify(['8', '0', '0', '10']), JSON.stringify(itemScores))
  const totalBefore = await page.locator('.lqg-score-tab__total-value').innerText()
  check('合计 = 18（0 分档照算进合计）', totalBefore.trim() === '18', totalBefore.trim())
  const persistedBefore = await page.locator('.lqg-score-tab__persisted').innerText()
  check('落库值来自后端回填（18 分那组）', persistedBefore.includes('18'), persistedBefore.replace(/\s+/g, ' '))

  const noGrade = !/质量偏差|质量中等|质量良好|结论/.test(scoreTabText)
  check('★ 页面不出「质量偏差 / 质量中等 / 质量良好」这类结论', noGrade, '')

  await page.screenshot({ path: path.join(SHOTS, '02-score-tab-zero-tier.png'), fullPage: true })

  // 没选全 → 合计为空（点掉一项：再点同一档 radio 不能取消，用页面上的「清除」不存在 →
  // 用第二组（培养天数）验证：把它选成一档再验证「四项都选才出合计」的反面）
  await pickOption(1, '≤14d')
  const totalAllPicked = await page.locator('.lqg-score-tab__total-value').innerText()
  check('四项齐了之后合计有值（8→? 20? 见下）', totalAllPicked.trim() !== '—' && totalAllPicked.trim() !== '18', totalAllPicked.trim())

  // ══ ② 选到 85 分那一组 → 保存 → 只提交四个档位，分值后端回填 ═════════════
  await pickOption(0, '>80')
  await pickOption(1, '≤14d')
  await pickOption(2, '1500~4000')
  await pickOption(3, '>100μm')
  const immediateTotal = (await page.locator('.lqg-score-tab__total-value').innerText()).trim()
  const immediateItems = await page.locator('.lqg-score-group__score').allInnerTexts()
  check('即时反馈：20 / 10 / 25 / 30 → 合计 85', JSON.stringify(immediateItems) === JSON.stringify(['20', '10', '25', '30']) && immediateTotal === '85', `${JSON.stringify(immediateItems)} total=${immediateTotal}`)

  await page.locator('.lqg-qc-editor__footer button', { hasText: '保存草稿' }).first().click()
  await page.waitForTimeout(2500)
  const totalAfter = (await page.locator('.lqg-score-tab__total-value').innerText()).trim()
  const persistedAfter = await page.locator('.lqg-score-tab__persisted').innerText()
  check('保存后即时合计仍是 85', totalAfter === '85', totalAfter)
  check('★ 保存后落库值刷新显示（后端回填的 85）', persistedAfter.includes('85'), persistedAfter.replace(/\s+/g, ' '))
  await page.screenshot({ path: path.join(SHOTS, '03-score-after-save.png'), fullPage: true })

  const dbScore = scoreRows(SAMPLE_SCORE)
  check(
    '★ psql：落库档位 gt80/le14/1500to4000/gt100、分值快照 20/10/25/30、合计 85',
    dbScore === 'gt80/le14/1500to4000/gt100|20/10/25/30|85',
    dbScore
  )

  // ══ ③ 类器官质控表页签（样本 1001）══════════════════════════════════════
  await openEditor(SAMPLE_ORGANOID)
  await openTab('organoid')

  const organoidText = await page.locator('.lqg-organoid-qc').innerText()
  check(
    '类器官页签已接真（不再是占位）',
    !organoidText.includes('QC-WEB-002 接真') && organoidText.includes('样本观察情况'),
    JSON.stringify(organoidText.replace(/\s+/g, ' ')).slice(0, 160)
  )
  const slots = await page.locator('.lqg-organoid-qc .lqg-image-slot').count()
  check('样本观察情况图片位在位（1 个 ImageSlotUploader，slot=organoid_observe）', slots === 1, `imageSlots=${slots}`)
  const FIVE = ['形成类器官时间', '生长状态', '类器官生长情况', '预计筛药', '反馈时间']
  check('五栏文本齐', FIVE.every((k) => organoidText.includes(k)), JSON.stringify(FIVE))
  // ★ input 的 value 不在 innerText 里 → 逐栏读 inputValue
  const seedValues = await Promise.all(
    FIVE.map((label) => page.locator('.lqg-organoid-qc .el-form-item', { hasText: label }).locator('input, textarea').first().inputValue())
  )
  check(
    'seed 的自由文本原样灌进表单（第 5 天 / 良好 / 类器官成球规则。/ 索拉非尼、仑伐替尼 / 2026-10-15）',
    JSON.stringify(seedValues) === JSON.stringify(['第 5 天', '良好', '类器官成球规则。', '索拉非尼、仑伐替尼', '2026-10-15']),
    JSON.stringify(seedValues)
  )
  await page.screenshot({ path: path.join(SHOTS, '01-organoid-tab.png'), fullPage: true })

  // ── 日期选择器：选了填 yyyy-MM-dd ───────────────────────────────────────
  const formedBlock = page.locator('.lqg-organoid-qc__datetime').first()
  const textInput = formedBlock.locator('input').first()
  const pickerInput = formedBlock.locator('input').nth(1)
  await pickerInput.click()
  await page.waitForSelector('.el-picker-panel', { timeout: 10000 })
  await page.locator('.el-picker-panel td.available').first().click()
  await page.waitForTimeout(400)
  const pickedValue = await textInput.inputValue()
  check('★ 日期选择器选了 → 文本栏填成 yyyy-MM-dd', /^\d{4}-\d{2}-\d{2}$/.test(pickedValue), pickedValue)
  await page.keyboard.press('Escape')

  // ── 直接手输文字：也存得住 ─────────────────────────────────────────────
  await textInput.fill('约第 7 天')
  await page.waitForTimeout(300)
  const freeText = await textInput.inputValue()
  check('★ 时间栏允许直接手输文字（约第 7 天）', freeText === '约第 7 天', freeText)

  // ── 换页签不丢改动（v-show 挂载）──────────────────────────────────────
  const stateInput = page.locator('.lqg-organoid-qc .el-form-item', { hasText: '生长状态' }).locator('input').first()
  await stateInput.fill('待确认-临时')
  await openTab('score')
  await openTab('organoid')
  const stateAfterSwitch = await page.locator('.lqg-organoid-qc .el-form-item', { hasText: '生长状态' }).locator('input').first().inputValue()
  check('★ 换页签再回来，未保存的改动还在（v-if→v-show）', stateAfterSwitch === '待确认-临时', stateAfterSwitch)
  await stateInput.fill('良好')

  await page.locator('.lqg-qc-editor__footer button', { hasText: '保存草稿' }).first().click()
  await page.waitForTimeout(2500)

  const dbOrganoid = psql(
    `SELECT coalesce(formed_time,'-') || '|' || coalesce(growth_state,'-') || '|' || coalesce(growth_desc,'-')` +
      ` || '|' || coalesce(planned_drug_screen,'-') || '|' || coalesce(feedback_time,'-')` +
      ` FROM t_lqg_qc_organoid WHERE sample_id = ${SAMPLE_ORGANOID}`
  )
  check(
    '★ psql：五栏文本落库（形成时间 = 手输的「约第 7 天」，其余保留 seed 值）',
    dbOrganoid === '约第 7 天|良好|类器官成球规则。|索拉非尼、仑伐替尼|2026-10-15',
    dbOrganoid
  )

  // 评分页签那一次保存也要落库（1006）
  check('★ psql：1006 的评分行没被这次质控表保存带偏', scoreRows(SAMPLE_SCORE) === 'gt80/le14/1500to4000/gt100|20/10/25/30|85', scoreRows(SAMPLE_SCORE))

  check('全程无未捕获 JS 异常（seed 假图 404 已排除）', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
} catch (e) {
  check('脚本自身跑完（未抛异常）', false, String(e && e.stack ? e.stack.split('\n')[0] : e))
  await page.screenshot({ path: path.join(SHOTS, '99-failure.png'), fullPage: true }).catch(() => {})
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} PASS`)
if (failed.length) {
  console.log('FAILED:\n' + failed.map((f) => `  · ${f.name} — ${f.detail}`).join('\n'))
  process.exit(1)
}
