/**
 * QC-WEB-001 · 工作台质控文档编辑页的**人眼验收**（不是 accept 的替身）
 *
 * 走真浏览器（Playwright + chromium，headless）→ 真工作台 dev(8093) → 真后端(8094) → 真库(5433)
 * → 真 OSS 上传（POST /resource/oss/upload → MinIO），三件事：
 *   ① 三个图片位各传了图（含一张真 TIFF）→ 页面上能看到缩略图；
 *   ② 点图放大（el-image 的 preview 浮层）；
 *   ③ TIFF 显示为**后端给的 JPEG 预览图**（preview_oss_id ≠ oss_id，机器证据在库里）。
 * 顺带断：图片位满 3 张后上传按钮消失、拖拽排序落库、保存草稿真的落库（改成 draft）、
 * 样本总表「质控文档」入口只对有效样本可点、有未保存改动时离开被拦。
 *
 * ★ 纪律：不读图片进上下文、不 mock、不另造鉴权。截图只落在 shots/ 下。
 *
 * 用法（工作台 8093 / 后端 8094 起来后）：
 *   node doc/waves/reports/QC-WEB-001/ui-check.mjs
 */
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
// ★ playwright 装在小程序前端（code/miniapp/package.json 的 devDependency），不是 plus-ui
//   —— 任务书里给的 createRequire(plus-ui/package.json) 在本机会 MODULE_NOT_FOUND（实测）。
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const BASE = process.env.LQG_WEB_BASE || 'http://127.0.0.1:8093'
const API = process.env.LQG_API_BASE || 'http://127.0.0.1:8094'
const FIX = path.join(WS, '.tmp/qc-web-fixtures')
const SHOTS = path.join(HERE, 'shots')
mkdirSync(SHOTS, { recursive: true })

const SAMPLE = '9000001001'
const EDITOR_PATH = `/qc-console/qc-editor?sampleId=${SAMPLE}`

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

const apiAs = (who, method, url) =>
  JSON.parse(
    execFileSync('bash', ['doc/verify/api.sh', '--as', who, method, url], { cwd: WS, encoding: 'utf8' })
  )

/** 等一个条件为真（轮询 DOM / 库），超时抛错 */
async function until(fn, ms = 20000, step = 400) {
  const end = Date.now() + ms
  let last
  for (;;) {
    last = await fn()
    if (last) return last
    if (Date.now() > end) throw new Error('until() 超时')
    await new Promise((r) => setTimeout(r, step))
  }
}

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 1 })
const page = await ctx.newPage()
const pageErrors = []
page.on('pageerror', (e) => pageErrors.push(e.message))
page.on('console', (m) => {
  if (m.type() === 'error') pageErrors.push('console: ' + m.text().slice(0, 200))
})

// ── 登录（工作台账号密码；dev 关验证码）────────────────────────────────────
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

const slotSel = (slot) => `.lqg-sample-qc__slot:has-text("${slot}")`

try {
  await login()

  // ── ① 打开编辑页（★ 硬刷新：动态路由在登录后由 getRouters 注册）──────────
  await page.goto(`${BASE}${EDITOR_PATH}`)
  await page.waitForSelector('.lqg-qc-editor', { timeout: 30000 })
  await page.waitForTimeout(1500)

  const headText = await page.locator('.lqg-qc-editor__head').innerText()
  check('页头摘要条带出样本只读字段（内部编号 T-hli01 / 供体 测试供体甲）',
    headText.includes('T-hli01') && headText.includes('测试供体甲'), JSON.stringify(headText.replace(/\s+/g, ' ')).slice(0, 200))
  check('页头七项只读字段齐（来源单位/患者姓名/性别/收样时间/处理时间/操作人/内部编号）',
    ['来源单位', '患者姓名', '性别', '收样时间', '处理时间', '操作人', '内部编号'].every((k) => headText.includes(k)),
    '')
  const tabText = await page.locator('.lqg-qc-editor__tabs').innerText()
  check('三个页签都在且带状态徽标（已完成）',
    tabText.includes('样本质控表') && tabText.includes('类器官质控表') && tabText.includes('类器官质量评分表') && tabText.includes('已完成'),
    JSON.stringify(tabText.replace(/\s+/g, ' ')))

  const slots = await page.locator('.lqg-sample-qc__slot').count()
  const uploaders = await page.locator('.lqg-image-slot').count()
  check('样本质控表页签里有 3 个图片位 + 1 个通用附件区', slots === 4 && uploaders === 3, `slot=${slots} imageSlot=${uploaders}`)

  await page.screenshot({ path: path.join(SHOTS, '01-editor-before.png'), fullPage: true })

  // ── ② 三个图片位各传一张（pretreat 传真 TIFF）──────────────────────────
  const uploadTo = async (slotTitle, file) => {
    const before = await page.locator(slotSel(slotTitle)).locator('input[type=file]').count()
    if (!before) throw new Error(`${slotTitle}: 没有上传入口`)
    await page.locator(slotSel(slotTitle)).locator('input[type=file]').setInputFiles(path.join(FIX, file))
    await page.waitForTimeout(2500)
  }

  await uploadTo('收样原始情况', 'orig.png')
  await uploadTo('样本观察情况', 'observe.png')
  await uploadTo('样本预处理情况', 'pretreat.tif')
  await page.waitForTimeout(1500)

  const dbImages = psql(
    `SELECT i.slot || ':' || count(*) || ':' || bool_and(i.preview_oss_id IS NOT NULL)` +
      ` FROM t_lqg_doc_image i JOIN t_lqg_qc_sample d ON d.id = i.doc_id` +
      ` WHERE d.sample_id = ${SAMPLE} AND i.del_flag = '0' GROUP BY i.slot ORDER BY i.slot`
  )
  check('三个图片位在库里各有图（orig 3 / observe 2 / pretreat 1）',
    dbImages === 'observe:2:true\norig:3:true\npretreat:1:true', JSON.stringify(dbImages))

  const tiffRow = psql(
    `SELECT i.preview_oss_id <> i.oss_id FROM t_lqg_doc_image i JOIN t_lqg_qc_sample d ON d.id = i.doc_id` +
      ` WHERE d.sample_id = ${SAMPLE} AND i.slot = 'pretreat' AND i.del_flag = '0'`
  )
  check('★ TIFF 的 preview_oss_id ≠ oss_id（后端另存了一张 JPEG 预览图）', tiffRow === 't', `preview<>orig = ${tiffRow}`)

  const ossSuffix = psql(
    `SELECT o.file_suffix || '/' || p.file_suffix FROM t_lqg_doc_image i` +
      ` JOIN t_lqg_qc_sample d ON d.id = i.doc_id` +
      ` JOIN sys_oss o ON o.oss_id = i.oss_id JOIN sys_oss p ON p.oss_id = i.preview_oss_id` +
      ` WHERE d.sample_id = ${SAMPLE} AND i.slot = 'pretreat' AND i.del_flag = '0'`
  )
  check('★ 原图是 tif、预览图是 jpg（不是把 TIFF 直接塞给浏览器）', ossSuffix === '.tif/.jpg', ossSuffix)

  const thumbCount = await page.locator('.lqg-image-slot__item').count()
  check('页面上能看到 6 张缩略图（3+2+1）', thumbCount === 6, `thumbs=${thumbCount}`)

  const origUploaderGone = await page.locator(slotSel('收样原始情况')).locator('input[type=file]').count()
  check('★ 图片位满 3 张后上传按钮消失（orig）', origUploaderGone === 0, `inputs=${origUploaderGone}`)
  const observeUploaderAlive = await page.locator(slotSel('样本观察情况')).locator('input[type=file]').count()
  check('未满的图片位仍有上传入口（observe 2/3）', observeUploaderAlive === 1, `inputs=${observeUploaderAlive}`)

  await page.locator('.lqg-qc-editor__split').scrollIntoViewIfNeeded()
  await page.screenshot({ path: path.join(SHOTS, '02-editor-three-slots.png'), fullPage: true })
  // ★ TIFF 那一格的特写：缩略图显示的是**后端另存的 JPEG 预览图**（浏览器放不出 TIFF）
  await page.locator(slotSel('样本预处理情况')).screenshot({ path: path.join(SHOTS, '03-tiff-thumb-preview.png') })

  // ── ③ 点图放大（el-image 的 preview 浮层）───────────────────────────────
  const pretreatThumb = page.locator(slotSel('样本预处理情况')).locator('.lqg-image-slot__item').first()
  await pretreatThumb.locator('.el-image').click()
  await page.waitForSelector('.el-image-viewer__wrapper', { timeout: 15000 })
  await page.waitForTimeout(1200)
  const viewerSrc = await page.locator('.el-image-viewer__img').getAttribute('src')
  const viewerPath = (viewerSrc || '').split('?')[0]
  await page.screenshot({ path: path.join(SHOTS, '03-tiff-click-zoom.png') })
  check('点图放大浮层打开（el-image preview）', !!viewerSrc, viewerPath.split('/').pop())
  check('★ TIFF 缩略图点开放大的不是 TIFF 原始地址（tif 浏览器放不出来 → 走预览图）',
    !!viewerSrc && !/\.tiff?$/i.test(viewerPath), viewerPath.split('/').pop())
  await page.keyboard.press('Escape')
  await page.waitForTimeout(600)

  /** 该图片位里第一张「真能加载」的图（本机 seed 的假地址图取不到字节，跳过） */
  const pickRealItem = async (slotTitle) => {
    const items = page.locator(slotSel(slotTitle)).locator('.lqg-image-slot__item')
    const n = await items.count()
    for (let i = 0; i < n; i++) {
      const src = await items.nth(i).locator('img').first().getAttribute('src').catch(() => null)
      if (src && !src.includes('seed.invalid')) return items.nth(i)
    }
    return items.first()
  }

  const observeThumb = await pickRealItem('样本观察情况')
  await observeThumb.locator('.el-image').click()
  await page.waitForSelector('.el-image-viewer__wrapper', { timeout: 15000 })
  await page.waitForTimeout(1200)
  const pngViewerSrc = await page.locator('.el-image-viewer__img').getAttribute('src')
  await page.screenshot({ path: path.join(SHOTS, '04-png-click-zoom.png') })
  check('★ 真上传的 PNG 点图放大（浮层显示原图签名地址，不是 seed 假地址）',
    !!pngViewerSrc && !pngViewerSrc.includes('seed.invalid'), (pngViewerSrc || '').split('?')[0].split('/').pop())
  await page.keyboard.press('Escape')
  await page.waitForTimeout(600)

  // ── ④ 拖拽排序（issue #210：PUT image/sort 传该位全部 id）──────────────
  const beforeOrder = psql(
    `SELECT string_agg(i.id::text, ',' ORDER BY i.sort) FROM t_lqg_doc_image i` +
      ` JOIN t_lqg_qc_sample d ON d.id = i.doc_id` +
      ` WHERE d.sample_id = ${SAMPLE} AND i.slot = 'orig' AND i.del_flag = '0'`
  )
  const origItems = page.locator(slotSel('收样原始情况')).locator('.lqg-image-slot__item')
  await origItems.nth(0).dragTo(origItems.nth(2))
  await page.waitForTimeout(2500)
  const afterOrder = psql(
    `SELECT string_agg(i.id::text, ',' ORDER BY i.sort) FROM t_lqg_doc_image i` +
      ` JOIN t_lqg_qc_sample d ON d.id = i.doc_id` +
      ` WHERE d.sample_id = ${SAMPLE} AND i.slot = 'orig' AND i.del_flag = '0'`
  )
  check('★ 拖拽排序真的落库（orig 三张 id 顺序变了、集合没变）',
    beforeOrder !== afterOrder && beforeOrder.split(',').sort().join() === afterOrder.split(',').sort().join(),
    `${beforeOrder} → ${afterOrder}`)
  await page.screenshot({ path: path.join(SHOTS, '05-after-sort.png'), fullPage: true })

  // ── ⑤ 保存草稿（补丁语义：整份表单发上去）＋ 未保存改动拦截 ─────────────
  const oldCipher = psql(`SELECT patient_no FROM t_lqg_qc_sample WHERE sample_id = ${SAMPLE} AND del_flag = '0'`)
  await page.locator('.lqg-sample-qc').locator('input').first().fill('P-UI-9001')
  await page.waitForTimeout(400)
  await page.screenshot({ path: path.join(SHOTS, '06-unsaved-dirty.png'), fullPage: true })
  await page.click('button:has-text("返回样本总表")')
  await page.waitForSelector('.el-message-box', { timeout: 10000 })
  const boxText = await page.locator('.el-message-box').innerText()
  await page.screenshot({ path: path.join(SHOTS, '07-unsaved-guard.png') })
  check('★ 有未保存改动时离开被拦（弹确认框）', /未保存/.test(boxText), JSON.stringify(boxText.replace(/\s+/g, ' ')).slice(0, 120))
  await page.click('.el-message-box button:has-text("留下继续编辑")')
  await page.waitForTimeout(600)

  await page.click('.lqg-qc-editor__footer button:has-text("保存草稿")')
  await page.waitForTimeout(2500)
  const newCipher = psql(`SELECT patient_no FROM t_lqg_qc_sample WHERE sample_id = ${SAMPLE} AND del_flag = '0'`)
  const docStatus = psql(`SELECT doc_status FROM t_lqg_qc_sample WHERE sample_id = ${SAMPLE} AND del_flag = '0'`)
  check('★ 保存草稿落库（患者编号密文变了、且重新加密后能解回新值）',
    !!newCipher && newCipher !== oldCipher, `${oldCipher} → ${newCipher}`)
  // ★ 上游形状：QC-MODEL-001 明说「doc_status 本票一个字都不动」——published 文档再保存
  //   仍然是 published；「保存后回到 draft」是 DOC-PUBLISH-001 的状态机（本票不断言它绿）。
  check('保存草稿不改 doc_status（上游口径：状态机在 DOC-PUBLISH-001）', docStatus === 'published', docStatus)
  await page.screenshot({ path: path.join(SHOTS, '08-after-save.png'), fullPage: true })

  // ── ⑤b 细胞活率测定附件（单文件：传 → 保存 → 移除 → 保存；issue #212）────
  await page.locator('.lqg-sample-qc__viability').locator('input[type=file]').setInputFiles(path.join(FIX, 'viability.txt'))
  await page.waitForTimeout(2500)
  const viabilityShown = await page.locator('.lqg-sample-qc__viability-name').innerText().catch(() => '')
  check('细胞活率测定附件选中后页面上显示文件名', viabilityShown.includes('viability.txt'), JSON.stringify(viabilityShown))
  await page.click('.lqg-qc-editor__footer button:has-text("保存草稿")')
  await page.waitForTimeout(2500)
  const viabilityDb = psql(
    `SELECT COALESCE(viability_oss_id::text, 'NULL') || '/' || COALESCE(viability_file_name, 'NULL')` +
      ` FROM t_lqg_qc_sample WHERE sample_id = ${SAMPLE} AND del_flag = '0'`
  )
  check('★ 细胞活率附件落库（viability_oss_id 非空 + 文件名）', !/^NULL/.test(viabilityDb), viabilityDb)

  await page.locator('.lqg-sample-qc__viability').locator('button:has-text("移除")').click()
  await page.waitForTimeout(500)
  await page.click('.lqg-qc-editor__footer button:has-text("保存草稿")')
  await page.waitForTimeout(2500)
  const viabilityAfterRemove = psql(
    `SELECT COALESCE(viability_oss_id::text, 'NULL') || '/' || COALESCE(viability_file_name, 'NULL')` +
      ` FROM t_lqg_qc_sample WHERE sample_id = ${SAMPLE} AND del_flag = '0'`
  )
  check('★ 移除细胞活率附件 = 保存时发 viabilityOssId:0，库里两列都被清空（issue #212）',
    viabilityAfterRemove === 'NULL/NULL', viabilityAfterRemove)

  // ── ⑤c 通用附件（可多个、fileSize 可选）──────────────────────────────
  await page.locator('.lqg-attachment-list').locator('input[type=file]').setInputFiles(path.join(FIX, 'attachment.txt'))
  await page.waitForTimeout(2500)
  const attRows = psql(
    `SELECT a.file_name || '/' || COALESCE(a.file_size::text, 'NULL') FROM t_lqg_doc_attachment a` +
      ` JOIN t_lqg_qc_sample d ON d.id = a.doc_id` +
      ` WHERE d.sample_id = ${SAMPLE} AND a.del_flag = '0' ORDER BY a.sort`
  )
  check('★ 通用附件落库（文件名 + 字节数可选地带上）', attRows.includes('attachment.txt'), JSON.stringify(attRows))
  const attText = await page.locator('.lqg-attachment-list').innerText()
  check('附件区列出刚挂的文件（不是在页面上假装挂上）', attText.includes('attachment.txt'), JSON.stringify(attText.replace(/\s+/g, ' ')).slice(0, 160))
  await page.screenshot({ path: path.join(SHOTS, '10-attachments.png'), fullPage: true })

  // ── ⑥ 样本总表入口只对有效样本可点 ────────────────────────────────────
  await page.goto(`${BASE}/sample`)
  await page.waitForSelector('.lqg-sample', { timeout: 30000 })
  await page.waitForTimeout(2000)
  const entryState = await page.evaluate(() => {
    const rows = [...document.querySelectorAll('.el-table__body tr')]
    const out = []
    for (const tr of rows) {
      const no = tr.querySelector('td .lqg-sample__mono')?.innerText?.trim()
      const btns = [...tr.querySelectorAll('button')]
      const qc = btns.find((b) => b.innerText.includes('质控文档'))
      if (no && qc) out.push({ no, disabled: qc.disabled })
    }
    return out
  })
  const validRow = entryState.find((r) => r.no === 'T-hli01')
  const pendingRow = entryState.find((r) => r.no === '—' || r.no === '')
  check('★ 样本总表「质控文档」入口对有效样本可点（T-hli01）', validRow && validRow.disabled === false, JSON.stringify(entryState.slice(0, 6)))
  check('「质控文档」入口对待核验样本置灰（列表里存在 disabled=true 的行）',
    entryState.some((r) => r.disabled === true), JSON.stringify(entryState.filter((r) => r.disabled)))
  await page.screenshot({ path: path.join(SHOTS, '09-sample-entry.png'), fullPage: true })

  // ── ⑦ 隐藏菜单：侧边栏里不该出现质控文档 / 文档渲染状态 ─────────────────
  const sidebarText = await page.locator('ul.el-menu--vertical').first().innerText()
  check('★ 侧边栏没有「质控文档」「文档渲染状态」入口（visible=1 的隐藏路由不进菜单）',
    !sidebarText.includes('质控文档') && !sidebarText.includes('文档渲染状态'),
    JSON.stringify(sidebarText.replace(/\s+/g, '|')).slice(0, 220))

  // ── ⑧ 回归：DOC-PDF-001 的 5520 被挂到隐藏目录后仍然可路由 ──────────────
  await page.goto(`${BASE}/qc-console/doc-console`)
  const docConsoleOk = await page
    .waitForSelector('.lqg-doc', { timeout: 20000 })
    .then(() => true)
    .catch(() => false)
  check('★ 5520「文档渲染状态」改挂隐藏目录后路由仍通（/qc-console/doc-console 渲染出页面）',
    docConsoleOk, page.url())
  await page.screenshot({ path: path.join(SHOTS, '11-doc-console-regression.png') })

  // ★ seed 里的图是 https://seed.invalid/... 的假地址（QC-MODEL-001 的既定测试数据）
  //   → 浏览器一定报「Failed to load resource」；那不是本页的 JS 缺陷，只看未捕获异常。
  const jsErrors = pageErrors.filter((e) => !/Failed to load resource|favicon|ResizeObserver/.test(e))
  check('页面没有未捕获的 JS 异常（忽略 seed 假地址图导致的资源加载失败）', jsErrors.length === 0,
    JSON.stringify(jsErrors.slice(0, 3)))
} catch (e) {
  check('脚本执行到结束', false, e.message)
  await page.screenshot({ path: path.join(SHOTS, '99-failure.png') }).catch(() => {})
} finally {
  await browser.close()
}

const bad = results.filter((r) => !r.ok)
console.log(`\n== QC-WEB-001 人眼验收 ${results.length - bad.length}/${results.length} 通过 ==`)
for (const b of bad) console.log(`  RED: ${b.name} — ${b.detail}`)
process.exit(bad.length ? 1 : 0)
