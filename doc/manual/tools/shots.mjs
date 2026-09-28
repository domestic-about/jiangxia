// SYS-MANUAL-001 · 操作说明配图脚本（假数据 / 种子数据，绝不用真实样本）
//
// 前置：模式 B 环境起来（后端 8092 / 工作台 8093 / 小程序 H5 9202）
//   bash doc/waves/tools/qa-up.sh --backend-port 8092 --web-port 8093 --mp-port 9202
//   node doc/manual/tools/shots.mjs
//
// 依赖解析锚点必须是 code/miniapp/package.json（code/plus-ui 里没有 playwright，会 MODULE_NOT_FOUND）。
// 浏览器已缓存在 ~/Library/Caches/ms-playwright，不下载。
//
// 配图只落盘，不读进上下文（历史上 agent 读 PNG 导致整轮报废）。

import { createRequire } from 'node:module'
import path from 'node:path'
import fs from 'node:fs'
import url from 'node:url'

const HERE = path.dirname(url.fileURLToPath(import.meta.url))
const OUT = path.resolve(HERE, '../shots')
const WS = path.resolve(HERE, '../../..')
const r = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = r('playwright')

const WB = process.env.LQG_WB || 'http://127.0.0.1:8093'
const MP = process.env.LQG_MP || 'http://127.0.0.1:9202'
const ADMIN = { user: process.env.LQG_ADMIN_USER || 'lqgadmin', pass: process.env.LQG_ADMIN_PASSWORD || 'admin123' }

fs.mkdirSync(OUT, { recursive: true })

const ok = []
const bad = []
const log = []
async function shot(page, name, opts = {}) {
  const file = path.join(OUT, `${name}.png`)
  try {
    // 顺手留一份「这一屏上写了什么」的文字凭证：配图只落盘不读进上下文，
    // 下游要确认图里不是错误态时，读 _page-text.txt 就够了（别去看 PNG）。
    // 取头 400 + 尾 400：底部弹层 / 底部按钮都在正文末尾，只取头会漏掉它们。
    const all = (await page.locator('body').innerText().catch(() => '')).replace(/\s*\n\s*/g, ' | ')
    const text = all.length > 800 ? `${all.slice(0, 400)} … ${all.slice(-400)}` : all
    if (opts.locator) {
      const el = page.locator(opts.locator).first()
      await el.waitFor({ state: 'visible', timeout: opts.timeout || 15000 })
      await page.waitForTimeout(opts.settle ?? 800)
      await el.screenshot({ path: file })
    }
    else {
      await page.waitForTimeout(opts.settle ?? 800)
      await page.screenshot({ path: file, fullPage: !!opts.full })
    }
    const size = fs.statSync(file).size
    if (size < 3000) throw new Error(`太小（${size} 字节）`)
    ok.push(`${name}.png ${size}`)
    log.push(`## ${name}.png\n${text}\n`)
  }
  catch (e) {
    bad.push(`${name}: ${e.message.split('\n')[0]}`)
  }
}

const browser = await chromium.launch()
try {
  // ── 工作台（内部人员版）────────────────────────────────────────────────
  const wbCtx = await browser.newContext({ viewport: { width: 1600, height: 950 }, deviceScaleFactor: 1.5, locale: 'zh-CN' })
  const wb = await wbCtx.newPage()
  await wb.goto(`${WB}/login`, { waitUntil: 'networkidle', timeout: 60000 })
  await shot(wb, 'wb-01-login', { settle: 1200 })
  await wb.locator('input').first().fill(ADMIN.user)
  await wb.locator('input[type=password]').first().fill(ADMIN.pass)
  await wb.getByRole('button', { name: /登\s*录/ }).click()
  await wb.waitForTimeout(4000)

  const go = async (p) => { await wb.goto(`${WB}${p}`, { waitUntil: 'networkidle', timeout: 60000 }); await wb.waitForTimeout(2200) }

  await go('/index')
  await shot(wb, 'wb-02-home')

  await go('/sample')
  await shot(wb, 'wb-03-sample-list')
  try {
    await wb.getByRole('button', { name: '核验' }).first().click()
    await shot(wb, 'wb-04-sample-verify', { locator: '.el-drawer:visible', settle: 1500 })
    await wb.keyboard.press('Escape'); await wb.waitForTimeout(600)
  }
  catch (e) { bad.push(`wb-04-sample-verify: ${e.message.split('\n')[0]}`) }

  await go('/embed')
  await shot(wb, 'wb-05-embed-list')
  try {
    await wb.getByRole('button', { name: '核验' }).first().click()
    await shot(wb, 'wb-06-embed-verify', { locator: '.el-drawer:visible', settle: 1500 })
    await wb.keyboard.press('Escape'); await wb.waitForTimeout(600)
  }
  catch (e) { bad.push(`wb-06-embed-verify: ${e.message.split('\n')[0]}`) }

  await go('/cryo')
  await shot(wb, 'wb-07-cryo-list')
  try {
    await wb.getByRole('button', { name: '流水' }).first().click()
    await shot(wb, 'wb-08-cryo-flow', { locator: '.el-drawer:visible', settle: 1800 })
    await wb.keyboard.press('Escape'); await wb.waitForTimeout(600)
  }
  catch (e) { bad.push(`wb-08-cryo-flow: ${e.message.split('\n')[0]}`) }

  // 质控文档编辑：上半屏（样本摘要 + 三个页签 + 左编辑）用已完成的那条；
  // 拉到页脚再用一条**草稿**的（T-hga03），这样图上出的是「完成并同步」而不是「撤回」
  await go('/qc-console/qc-editor?sampleId=9000001001')
  await shot(wb, 'wb-09-qc-editor', { settle: 2500 })
  try {
    await go('/qc-console/qc-editor?sampleId=9000001005')
    // 先点一次「预览」触发渲染，再点预览面板自己的「刷新」把页面图读出来
    await wb.locator('.lqg-qc-editor__footer').getByRole('button', { name: '预览' }).click()
    await wb.waitForTimeout(6000)
    await wb.locator('.lqg-qc-editor__right').getByRole('button', { name: '刷新' }).click()
    await wb.waitForTimeout(3000)
    await wb.locator('.lqg-qc-editor__footer').scrollIntoViewIfNeeded()
    await wb.waitForTimeout(1500)
    await shot(wb, 'wb-10-qc-preview-publish', { settle: 1500 })
  }
  catch (e) { bad.push(`wb-10-qc-preview-publish: ${e.message.split('\n')[0]}`) }

  await go('/qc-console/doc-console')
  try {
    // 渲染状态页不吃 URL query：手填样本 id、把文档选成「样本质控表」再点刷新状态，
    // 否则默认选中的评分表在 seed 里没内容，图上会是 failed 态
    await wb.locator('.lqg-doc input').first().fill('9000001001')
    await wb.locator('.lqg-doc .el-select').first().click()
    await wb.waitForTimeout(600)
    await wb.locator('.el-select-dropdown__item:visible', { hasText: '样本质控表' }).first().click()
    await wb.waitForTimeout(400)
    await wb.getByRole('button', { name: '刷新状态' }).click()
    await wb.waitForTimeout(2500)
  }
  catch (e) { bad.push(`wb-11-doc-console 填查询: ${e.message.split('\n')[0]}`) }
  await shot(wb, 'wb-11-doc-console')

  await go('/auth/extuser')
  await shot(wb, 'wb-12-extuser')
  await go('/auth/unit')
  await shot(wb, 'wb-13-unit-group')
  await go('/auth/staff')
  await shot(wb, 'wb-14-staff')
  await wbCtx.close()

  // ── 小程序 H5 ──────────────────────────────────────────────────────────
  const mpCtx = await browser.newContext({ viewport: { width: 414, height: 896 }, deviceScaleFactor: 2, locale: 'zh-CN' })
  const mp = await mpCtx.newPage()
  mp.on('pageerror', e => bad.push(`mp pageerror: ${String(e).slice(0, 80)}`))

  const mpGo = async (p) => { await mp.goto(`${MP}/#${p}`, { waitUntil: 'networkidle', timeout: 60000 }); await mp.waitForTimeout(2500) }

  // 内部人员 · 李工
  await mp.goto(`${MP}/#/pages/login/index`, { waitUntil: 'networkidle', timeout: 60000 })
  await mp.waitForTimeout(1500)
  await shot(mp, 'mp-01-login')
  // 真实登录页（裁掉只在 dev 构建里存在的「调试登录」面板）：两份说明里引用的都是这一张
  const mockBox = await mp.locator('.login__mock').boundingBox().catch(() => null)
  await mp.screenshot({
    path: path.join(OUT, 'mp-09-login-real.png'),
    clip: { x: 0, y: 0, width: 414, height: Math.max(320, Math.round((mockBox?.y ?? 560) - 12)) },
  })
  ok.push(`mp-09-login-real.png ${fs.statSync(path.join(OUT, 'mp-09-login-real.png')).size}`)
  log.push('## mp-09-login-real.png\n（= mp-01-login.png 裁掉 dev 才有的「调试登录」面板后的真实登录页）\n')
  await mp.locator('.login__box').click()
  await mp.getByText('内部人员 · 李工', { exact: false }).first().click()
  await mp.waitForTimeout(4500)
  await shot(mp, 'mp-02-home-internal')
  await mpGo('/pages/sample/form?mode=new'); await shot(mp, 'mp-03-sample-form')
  await mpGo('/pages/cryo/form?mode=new'); await shot(mp, 'mp-04-cryo-form')
  await mpGo('/pages/me/index'); await shot(mp, 'mp-05-me-internal')
  await mpGo('/pages/history/index'); await shot(mp, 'mp-06-history-internal')
  await mpGo('/pages/ledger/index?sheet=sample'); await shot(mp, 'mp-07-ledger-sample')
  try {
    await mp.getByText('导出 Excel', { exact: false }).first().click()
    await mp.waitForTimeout(2000)
    await shot(mp, 'mp-08-ledger-export')
    await mp.keyboard.press('Escape'); await mp.waitForTimeout(600)
  }
  catch (e) { bad.push(`mp-08-ledger-export: ${e.message.split('\n')[0]}`) }

  // 外部人员 · 王医生（已核验）—— 换一个全新 context，避免内部 token 残留
  await mpCtx.close()
  const extCtx = await browser.newContext({ viewport: { width: 414, height: 896 }, deviceScaleFactor: 2, locale: 'zh-CN' })
  const ext = await extCtx.newPage()
  ext.on('pageerror', e => bad.push(`ext pageerror: ${String(e).slice(0, 80)}`))
  const extGo = async (p) => { await ext.goto(`${MP}/#${p}`, { waitUntil: 'networkidle', timeout: 60000 }); await ext.waitForTimeout(2500) }
  await ext.goto(`${MP}/#/pages/login/index`, { waitUntil: 'networkidle', timeout: 60000 })
  await ext.waitForTimeout(1500)
  await ext.locator('.login__box').click()
  await ext.getByText('外部人员 · 王医生（已核验）', { exact: false }).first().click()
  await ext.waitForTimeout(4500)
  await shot(ext, 'ext-01-home')
  await extGo('/pages/sample/form?mode=new'); await shot(ext, 'ext-02-sample-form')
  await extGo('/pages/me/index'); await shot(ext, 'ext-03-me')
  await extGo('/pages/history/index'); await shot(ext, 'ext-04-history')
  await extGo('/pages/doc/index'); await shot(ext, 'ext-05-doc-list')
  await extGo('/pages/doc/preview?sampleId=9000001001&docKind=sample_qc'); await shot(ext, 'ext-06-doc-preview', { settle: 3000 })

  // ── 外部版「一页」专用的窄图（c-ext-*.png）─────────────────────────────
  // 手机整屏 414×896，按 33% 栏宽排到 A4 上，一张就约 120mm 高，三张必然溢出到第 2 页。
  // 这里按需要的高度裁一刀：列表 / 表单裁上半屏，文档预览裁下半屏（底部操作栏是 fixed 的）。
  const H = 290
  const crop = async (name, y) => {
    const file = path.join(OUT, `${name}.png`)
    await ext.screenshot({ path: file, clip: { x: 0, y, width: 414, height: H } })
    ok.push(`${name}.png ${fs.statSync(file).size}`)
    log.push(`## ${name}.png\n（外部版一页用的窄图：整屏裁 y=${y}..${y + H}）\n`)
  }
  await extGo('/pages/index/index'); await crop('c-ext-home', 0)
  await extGo('/pages/sample/form?mode=new'); await crop('c-ext-sample-form', 0)
  await extGo('/pages/history/index'); await crop('c-ext-history', 0)
  await extGo('/pages/doc/index'); await crop('c-ext-doc-list', 0)
  await extGo('/pages/doc/preview?sampleId=9000001001&docKind=sample_qc')
  await ext.waitForTimeout(2000); await crop('c-ext-doc-preview', 896 - H)
  await extCtx.close()
}
finally {
  await browser.close()
}

console.log(`\n✓ ${ok.length} 张`)
ok.forEach(s => console.log('  ' + s))
fs.writeFileSync(path.join(OUT, '_page-text.txt'),
  `# 配图上的文字（SYS-MANUAL-001 · 由 tools/shots.mjs 落盘，用于确认图里不是错误态）\n\n${log.join('\n')}`)
if (bad.length) {
  console.log(`\n✗ ${bad.length} 处异常`)
  bad.forEach(s => console.log('  ' + s))
  process.exitCode = 1
}
