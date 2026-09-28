// ============================================================================
// 弱断言「行为半句」的 DOM 探针（小程序侧两条，2026-09-27）
//   ① OCR-MP-001 acc2：识别条**只在新增时出现**（编辑已提交样本时不出现）
//   ② CRYO-MP-001 acc4：批次详情弹层带**「修改」入口**，点了真的进修改模式
//
// 原文这两句分别由 `grep showOcr` / `grep mode=edit` 代证 —— 源码里有这个字串，
// 既不能证明渲染出来、也不能证明点了有用。本探针只认真 DOM + 真导航。
//
// 【怎么证伪】
//   · ①：把 `form.vue` 里 `<OcrBar v-if="layout.showOcr">` 的 v-if 改成恒真 → 编辑模式下应能见到「拍照识别」→ 本探针必红。
//   · ②：把 `CryoBatchSheet.vue` 里那句「修改」删掉 → 弹层里找不到入口 → 本探针必红。
//
// 跑法（工作区根）：node doc/waves/regression/V-round/miniapp-behavior-dom.mjs
// 前置：后端 8091、H5 9202（dev:h5 + mock 登录）、seed 已灌。
// ============================================================================
import { createRequire } from 'node:module'
import path from 'node:path'

const WS = process.env.LQG_WS || process.cwd()
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const BASE = process.env.LQG_MP || 'http://127.0.0.1:9202'
// --only=ocr 或 --only=cryo：只跑对应那一段（两张票各自只需要自己那半，省时间）
const ONLY = ((process.argv.find((a) => a.startsWith('--only=')) || '').split('=')[1] || '').trim()
const SAMPLE = 9000001001
const fails = []
const check = (n, ok, extra = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${extra ? ' — ' + extra : ''}`); if (!ok) fails.push(n) }
const txt = async (page) => (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ')

const run = async () => {
  const browser = await chromium.launch()
  // 显式建 context：后面要**在同一个 context 里再开一个页面**（共享登录态、强制重跑 onLoad）。
  // Playwright 不允许对隐式 context 调 newPage()，所以这里必须显式建。
  const ctx = await browser.newContext({ viewport: { width: 420, height: 900 } })
  const page = await ctx.newPage()

  // 登录（内部人员）
  await page.goto(`${BASE}/#/pages/login/index`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.login__mock-btn', { timeout: 30000 })
  await page.locator('.login__box').click()
  await page.waitForTimeout(200)
  await page.locator('.login__mock-btn').nth(0).click()
  await page.waitForTimeout(4000)
  check('内部人员登录成功', !page.url().includes('login'), page.url())

  // ── ① OCR 条：新增出现 / 编辑不出现 ────────────────────────────────────────
  if (!ONLY || ONLY === 'ocr') {
  await page.goto(`${BASE}/#/pages/sample/form?mode=new`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(3000)
  const tNew = await txt(page)
  check('① 新增模式下页面已渲染（有「送检信息」）', /送检信息/.test(tNew), tNew.slice(0, 80))
  check('①★ 新增模式：识别条在场（见到「拍照识别」）', /拍照识别/.test(tNew))

  // ★ 踩坑两连：① uni H5 在同一路径只改 query 时**不会重跑 onLoad**（页面实例复用）；
  //   ② 加 cache-buster 也没用（uni 按路径复用页面栈实例）。可靠做法：在**同一 context**
  //   里新开一个页面（共享 localStorage 的登录态，但页面实例是新的 → onLoad 必然按新参数跑）。
  const page2 = await ctx.newPage()
  await page2.goto(`${BASE}/#/pages/sample/form?id=${SAMPLE}&mode=edit`, { waitUntil: 'networkidle' })
  await page2.waitForTimeout(3500)
  const tEdit = await txt(page2)
  // ★ 守卫要用**文本渲染出来的值**，不能用 input 的 value（innerText 取不到输入框里的值，
  //   我第一次写成找「ZY0000001」→ 假红）。编辑态可辨认的文本证据：来源单位/收样日期/内部编号 都是文本格。
  check('① 编辑模式确实按新参数加载（详情已回填：来源单位/收样日期/内部编号）',
    /A 医院|收样日期 2026-08-28|内部编号/.test(tEdit), tEdit.slice(0, 140))
  check('① 编辑模式下页面已渲染（有「送检信息」）', /送检信息/.test(tEdit), tEdit.slice(0, 80))
  check('①★ 编辑模式：识别条不出现', !/拍照识别/.test(tEdit), tEdit.slice(0, 120))

  }

  // ── ② 冻存批次弹层的「修改」入口 ──────────────────────────────────────────
  if (!ONLY || ONLY === 'cryo') {
  await page.goto(`${BASE}/#/pages/ledger/index`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(3000)
  const sheet = page.locator('text=-80 冻存').first()
  check('② 内部管理里有「-80 冻存」工作表', (await page.locator('text=-80 冻存').count()) > 0)
  if (await sheet.count()) {
    await sheet.click({ timeout: 8000 }).catch(() => {})
    await page.waitForTimeout(3000)
  }
  const rows = page.locator('[class*=ledger__row], [class*=ledger__tr], uni-view[class*=row]')
  const n = await rows.count()
  console.log('  冻存表可点行数（粗筛）=' + n)
  let opened = false
  for (let i = 0; i < Math.min(n, 6); i++) {
    await rows.nth(i).click({ timeout: 5000 }).catch(() => {})
    await page.waitForTimeout(1500)
    const t = await txt(page)
    if (/修改/.test(t)) { opened = true; break }
  }
  const tSheet = await txt(page)
  check('② 点开冻存批次后弹层里出现「修改」入口', opened || /修改/.test(tSheet), tSheet.slice(-160))
  // 真的点一次「修改」，看是不是进了 cryo/form 的 edit 模式
  const editBtn = page.locator('text=修改').last()
  const before = page.url()
  if (await editBtn.count()) {
    await editBtn.click({ timeout: 8000 }).catch(() => {})
    await page.waitForTimeout(3000)
  }
  const after = page.url()
  check('②★ 点「修改」真的进 /pages/cryo/form 的 mode=edit', /pages\/cryo\/form/.test(after) && /mode=edit/.test(after),
    `before=${before.slice(-40)} after=${after.slice(-60)}`)

  }

  await browser.close()
  console.log(`\n结果：${fails.length === 0 ? 'PASS' : 'FAIL'}（失败 ${fails.length} 条）`)
  if (fails.length) { console.log('失败项：' + fails.join(' / ')); process.exit(1) }
}

run().catch((e) => { console.error('[ERROR]', e); process.exit(2) })
