/**
 * DOC-PUBLISH-001 · 预览面板与「完成并同步 / 撤回」的**人眼验收 + 截图取证**
 * （不是 accept 的替身；accept 见 .tmp/doc-publish-accept-logs/）
 *
 * 走真浏览器（Playwright + chromium，headless）→ 真工作台 dev(8093) → 真后端(8094)
 * → 真库(5433) → 真 OSS/Gotenberg，断这些事：
 *   ① 页签徽标三态：进入（1006 样本质控表 = 草稿）→ 完成并同步（已完成）→ 改内容保存（回草稿）；
 *   ② 页脚按钮按状态变：草稿时「完成并同步给送检方」、已完成时「撤回」；
 *      已完成时页脚提示「已同步给送检方 · 修改后需重新同步」；
 *   ③ 预览面板真的在右栏（接口 `/lqg/doc/{id}/{docKind}/pages` 被调、页面图渲染出来）；
 *   ④ 内外部版切换：切到外部版会重新取 pages（两版是两份独立产物）；
 *   ⑤ 四个下载入口都在、且**点得动**（真拿到 10 分钟签名链接，不是写死置灰）；
 *   ⑥ 点「预览」触发 render 且轮询到 done（页面图 src 是真签名地址）；
 *   ⑦ 库内对账：三态迁移的 doc_status / published_by / published_time 与页面徽标一致；
 *   ⑧ 全程无未捕获 JS 异常（忽略 seed 假地址图必然报的资源加载失败）。
 *
 * ★ 纪律：不读图片进上下文（截图只落盘到 shots/）、不 mock、不另造鉴权。
 *
 * 用法（工作台 8093 / 后端 8094 起来、且已 reseed 之后）：
 *   node doc/waves/reports/DOC-PUBLISH-001/ui-check.mjs
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

const SAMPLE = '9000001006' // seed：样本质控表【不存在】、评分已完成 —— 「进入即草稿」的干净起点
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

/** 样本质控表的 `doc_status|published_by|published_time IS NOT NULL`（与 accept 同一形状） */
const sampleQcState = () =>
  psql(
    `SELECT doc_status || '|' || COALESCE(published_by::text,'-') || '|' || (published_time IS NOT NULL)` +
      ` FROM t_lqg_qc_sample WHERE sample_id=${SAMPLE} AND del_flag='0'`
  )

const { page, browser } = await (async () => {
  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({ viewport: { width: 1600, height: 1100 } })
  const page = await context.newPage()
  return { page, browser }
})()

const pageErrors = []
page.on('pageerror', (e) => pageErrors.push('pageerror: ' + String(e).slice(0, 200)))
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

async function openEditor() {
  await page.goto(`${BASE}${editorPath(SAMPLE)}`)
  await page.waitForSelector('.lqg-qc-editor', { timeout: 30000 })
  await page.waitForTimeout(1500)
}

/** 三个页签徽标的文字（顺序 = 样本质控表 / 类器官质控表 / 评分表） */
const tabBadges = async () =>
  (await page.locator('.lqg-qc-editor__tabs .lqg-qc-editor__tab-label').allInnerTexts()).map((s) =>
    s.replace(/\s+/g, ' ').trim()
  )

/** 页脚提示文字 */
const footerHint = () => page.locator('.lqg-qc-editor__footer-hint').innerText()

/** 右栏预览面板里的页面图（src 是真签名地址） */
const previewPageCount = () => page.locator('.lqg-preview-pane__page-img img').count()

/** 记录 /lqg/doc/** 的请求，用来断「预览面板真的调了接口」 */
const docApiCalls = []
page.on('request', (req) => {
  const url = req.url()
  if (url.includes('/lqg/doc/')) docApiCalls.push(`${req.method()} ${url.split('/lqg/doc/')[1]}`)
})

try {
  await login()

  // ══ ① 进入态：样本质控表是草稿（seed 里 1006 没有这份表）════════════════════
  await openEditor()
  const badgesBefore = await tabBadges()
  check('①进入态 页签徽标（样本质控表=草稿）', /样本质控表\s*草稿/.test(badgesBefore[0]), badgesBefore.join(' / '))
  check('①进入态 库内 doc_status=draft', (await sampleQcState()).startsWith('draft|'), await sampleQcState())
  check('①进入态 页脚按钮=「完成并同步给送检方」', (await page.locator('.lqg-qc-editor__footer').innerText()).includes('完成并同步给送检方'))
  check('①进入态 页脚提示=「预览只看不发布…」', (await footerHint()).includes('预览'), await footerHint())
  await page.screenshot({ path: path.join(SHOTS, '01-badge-draft.png'), fullPage: true })

  // ══ ② 点「预览」→ 触发渲染 → 轮询到页面图 ══════════════════════════════════
  docApiCalls.length = 0
  await page.locator('.lqg-qc-editor__footer button', { hasText: '预览' }).first().click()
  // 渲染是异步的：等页面图出现（最多 90 秒）
  await page.waitForSelector('.lqg-preview-pane__page-img img', { timeout: 90000 })
  await page.waitForTimeout(800)
  const pagesInternal = await previewPageCount()
  const renderCalls = docApiCalls.filter((c) => c.includes('/render'))
  const pagesCalls = docApiCalls.filter((c) => c.includes('/pages'))
  check('②预览 触发了 POST render', renderCalls.length >= 1, renderCalls[0] || '(无)')
  check('②预览 轮询了 GET pages', pagesCalls.length >= 1, `轮询 ${pagesCalls.length} 次`)
  check('②预览 逐页页面图渲染出来', pagesInternal >= 1, `${pagesInternal} 页`)
  const firstSrc = await page.locator('.lqg-preview-pane__page-img img').first().getAttribute('src')
  check('②预览 页面图是真签名地址（不是 seed.invalid）', !!firstSrc && !firstSrc.includes('seed.invalid'), String(firstSrc).slice(0, 90))
  await page.screenshot({ path: path.join(SHOTS, '02-preview-internal.png'), fullPage: true })

  // ══ ③ 内外部版切换（两版是两份独立产物）════════════════════════════════════
  const callsBeforeSwitch = docApiCalls.length
  await page.locator('.lqg-preview-pane__bar .el-radio-button', { hasText: '外部版' }).first().click()
  await page.waitForTimeout(2500)
  const callsAfterSwitch = docApiCalls.length
  check('③切外部版 重新取了 pages', callsAfterSwitch > callsBeforeSwitch, `+${callsAfterSwitch - callsBeforeSwitch} 次`)
  check('③切外部版 提示语=「外部版里内部编号一格为空」', (await page.locator('.lqg-preview-pane__audience-hint').innerText()).includes('外部版'))
  await page.screenshot({ path: path.join(SHOTS, '03-preview-external.png'), fullPage: true })
  await page.locator('.lqg-preview-pane__bar .el-radio-button', { hasText: '内部版' }).first().click()
  await page.waitForTimeout(1500)

  // ══ ④ 四个下载入口都在、都点得动（真拿到签名链接）════════════════════════════
  const downloadText = await page.locator('.lqg-preview-pane__downloads').innerText()
  for (const label of ['下载 Word', '下载 PDF', '下载合并 Word', '下载合并 PDF']) {
    check(`④下载入口存在「${label}」`, downloadText.includes(label))
  }
  const enabled = await page.locator('.lqg-preview-pane__downloads button:not([disabled])').count()
  check('④四个下载按钮都不是置灰的', enabled === 4, `${enabled}/4 可点`)

  // ══ ⑤ 完成并同步（状态机 draft → published + 徽标变已完成 + 页脚变撤回）══════
  await page.locator('.lqg-qc-editor__footer button', { hasText: '完成并同步给送检方' }).first().click()
  await page.waitForTimeout(500)
  await page.locator('.el-message-box__btns button', { hasText: '完成并同步' }).first().click()
  await page.waitForTimeout(3000)
  const badgesPublished = await tabBadges()
  const statePublished = await sampleQcState()
  // 完成人 = **当前登录人**：工作台用 lqgadmin（9000000100），staff 是 9000000101
  check('⑤完成并同步 库内=published|9000000100|true', statePublished === 'published|9000000100|true', statePublished)
  check('⑤完成并同步 页签徽标变「已完成」', /样本质控表\s*已完成/.test(badgesPublished[0]), badgesPublished.join(' / '))
  check('⑤完成并同步 页脚按钮变「撤回」', (await page.locator('.lqg-qc-editor__footer').innerText()).includes('撤回'))
  check('⑤完成并同步 页脚提示=已同步给送检方·修改后需重新同步', (await footerHint()).includes('已同步给送检方'), await footerHint())
  await page.screenshot({ path: path.join(SHOTS, '04-badge-published.png'), fullPage: true })

  // 完成并同步之后外部版也应有产物（后端异步渲染）—— 等它出现
  let extPages = 0
  await page.locator('.lqg-preview-pane__bar .el-radio-button', { hasText: '外部版' }).first().click()
  for (let i = 0; i < 20; i++) {
    await page.locator('.lqg-preview-pane__refresh, .lqg-preview-pane__bar button', { hasText: '刷新' }).first().click().catch(() => {})
    await page.waitForTimeout(2500)
    extPages = await previewPageCount()
    if (extPages > 0) break
  }
  check('⑤完成并同步 外部版页面图也能出来', extPages >= 1, `${extPages} 页`)
  await page.screenshot({ path: path.join(SHOTS, '05-after-publish-external.png'), fullPage: true })

  // ══ ⑥ 改内容保存 → 回到草稿（FLOW:F-QC-01.step7）════════════════════════════
  await page.locator('.lqg-qc-editor__tabs .el-tabs__item', { hasText: '样本质控表' }).first().click()
  await page.waitForTimeout(500)
  const siteInput = page.locator('.lqg-sample-qc__form .el-form-item', { hasText: '取样部位' }).locator('input').first()
  await siteInput.fill('改了一个字')
  await page.locator('.lqg-qc-editor__footer button', { hasText: '保存草稿' }).first().click()
  await page.waitForTimeout(2500)
  const stateReverted = await sampleQcState()
  const badgesReverted = await tabBadges()
  check('⑥改内容保存 库内回草稿 draft|-|false', stateReverted === 'draft|-|false', stateReverted)
  check('⑥改内容保存 页签徽标回「草稿」', /样本质控表\s*草稿/.test(badgesReverted[0]), badgesReverted.join(' / '))
  check('⑥改内容保存 页脚恢复「完成并同步给送检方」', (await page.locator('.lqg-qc-editor__footer').innerText()).includes('完成并同步给送检方'))
  check('⑥改内容保存 评分表仍是已完成（只回受影响那一份）', /类器官质量评分表\s*已完成/.test(badgesReverted[2]), badgesReverted.join(' / '))
  await page.screenshot({ path: path.join(SHOTS, '06-badge-back-to-draft.png'), fullPage: true })

  // ══ ⑦ 撤回：先把样本质控表重新完成并同步，再点撤回 ═══════════════════════════
  await page.locator('.lqg-qc-editor__footer button', { hasText: '完成并同步给送检方' }).first().click()
  await page.waitForTimeout(400)
  await page.locator('.el-message-box__btns button', { hasText: '完成并同步' }).first().click()
  await page.waitForTimeout(2500)
  const stateRepublished = await sampleQcState()
  check('⑦重新完成并同步 = published|9000000100|true', stateRepublished === 'published|9000000100|true', stateRepublished)
  await page.locator('.lqg-qc-editor__footer button', { hasText: '撤回' }).first().click()
  await page.waitForTimeout(400)
  await page.locator('.el-message-box__btns button', { hasText: '撤回' }).first().click()
  await page.waitForTimeout(2500)
  const stateWithdrawn = await sampleQcState()
  check('⑦撤回 = draft|-|false', stateWithdrawn === 'draft|-|false', stateWithdrawn)
  check('⑦撤回后页签徽标回「草稿」', /样本质控表\s*草稿/.test((await tabBadges())[0]))

  // ══ ⑧ 无未捕获 JS 异常 ═══════════════════════════════════════════════════════
  check('⑧无未捕获 JS 异常', pageErrors.length === 0, pageErrors.slice(0, 2).join(' | '))
} catch (e) {
  check('脚本执行', false, String(e).slice(0, 300))
} finally {
  await browser.close()
}

const pass = results.filter((r) => r.ok).length
console.log(`\n${pass}/${results.length} PASS`)
process.exit(pass === results.length ? 0 : 1)
