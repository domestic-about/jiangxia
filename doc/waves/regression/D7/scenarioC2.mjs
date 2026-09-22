// D7 L2 · 场景 C2：完成前后页签徽标变化（工作台，DOC-PUBLISH-001 两态状态机的前端面）
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'
const require = createRequire('/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/code/miniapp/package.json')
const { chromium } = require('playwright')
const WEB = 'http://127.0.0.1:8093'
const OUT = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/.tmp/qa-d7-L2'
const SHOTS = path.join(OUT, 'shots')
const results = []
const rec = (name, ok, detail = '') => { results.push({ name, ok: !!ok, detail: String(detail).slice(0, 800) }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  :: ' + String(detail).slice(0, 400) : ''}`) }
const net = []
const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1100 }, locale: 'zh-CN' })
const page = await ctx.newPage()
page.on('response', async (r) => { if (/\/lqg\/qc\//.test(r.url())) { let b = ''; try { b = (await r.text()).slice(0, 500) } catch { } net.push({ status: r.status(), url: r.url().replace(WEB, ''), method: r.request().method(), body: b }) } })
await page.goto(WEB + '/login', { waitUntil: 'domcontentloaded' })
await page.waitForSelector('input[type="text"]')
await page.fill('input[type="text"]', 'lqgadmin'); await page.fill('input[type="password"]', 'admin123')
await page.getByRole('button', { name: /登\s*录/ }).first().click()
await page.waitForURL(u => !u.pathname.includes('/login'))

const badges = async () => (await page.locator('.lqg-qc-editor__tab-label').allInnerTexts()).map(s => s.replace(/\s+/g, ''))
const footer = async () => (await page.locator('.lqg-qc-editor__footer, .lqg-qc-editor__actions, footer').first().innerText().catch(() => '')).replace(/\s+/g, ' ')

// 1004：样本质控表 published / 类器官质控表 draft
await page.goto(`${WEB}/qc-console/qc-editor?sampleId=9000001004`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.lqg-qc-editor__tabs'); await page.waitForTimeout(2500)
rec('C5:1004 起点徽标（样本=已完成 / 类器官=草稿）',
  JSON.stringify(await badges()) === JSON.stringify(['样本质控表已完成', '类器官质控表草稿', '类器官质量评分表草稿']),
  JSON.stringify(await badges()))
await page.screenshot({ path: path.join(SHOTS, 'C-05-badge-before.png'), fullPage: true })

// 切到「类器官质控表」页签
await page.locator('.lqg-qc-editor__tab-label', { hasText: '类器官质控表' }).first().click()
await page.waitForTimeout(2000)
const f1 = await footer()
rec('C5:草稿态底部按钮 =「完成并同步给送检方」', /完成并同步/.test(f1) && !/撤回/.test(f1), f1)
const before = net.length
await page.getByRole('button', { name: /完成并同步/ }).first().click()
await page.waitForSelector('.el-message-box', { timeout: 15000 })
const boxText = (await page.locator('.el-message-box').innerText()).replace(/\s+/g, ' ')
rec('C5:弹确认框（要求二次确认）', /完成|同步/.test(boxText), boxText.slice(0, 160))
await page.locator('.el-message-box__btns button').filter({ hasText: /完成并同步|确\s*定|确认/ }).first().click()
await page.waitForTimeout(4000)
const pub = net.slice(before).filter(x => /publish/.test(x.url))
rec('C5:调 POST …/organoid-qc/publish 且 code=200', pub.length >= 1 && JSON.parse(pub[pub.length - 1].body || '{}').code === 200,
  JSON.stringify(pub.map(x => `${x.method} ${x.url} ${x.status}`)))
rec('C5:完成并同步后徽标 → 已完成',
  JSON.stringify(await badges()) === JSON.stringify(['样本质控表已完成', '类器官质控表已完成', '类器官质量评分表草稿']),
  JSON.stringify(await badges()))
const f2 = await footer()
rec('C5:已完成态底部按钮变「撤回」+ 页脚提示已同步', /撤回/.test(f2) && /已同步|重新同步/.test(f2), f2)
await page.screenshot({ path: path.join(SHOTS, 'C-06-badge-after-publish.png'), fullPage: true })

// 反向：撤回 → 徽标回草稿
const before2 = net.length
await page.getByRole('button', { name: /撤回/ }).first().click()
await page.waitForSelector('.el-message-box', { timeout: 15000 })
await page.locator('.el-message-box__btns button').filter({ hasText: /撤回|确\s*定|确认/ }).first().click()
await page.waitForTimeout(4000)
const unp = net.slice(before2).filter(x => /unpublish/.test(x.url))
rec('C5:调 POST …/unpublish 且 code=200', unp.length >= 1 && JSON.parse(unp[unp.length - 1].body || '{}').code === 200, JSON.stringify(unp.map(x => x.url)))
rec('C5:撤回后徽标 → 草稿', JSON.stringify(await badges()) === JSON.stringify(['样本质控表已完成', '类器官质控表草稿', '类器官质量评分表草稿']), JSON.stringify(await badges()))
await page.screenshot({ path: path.join(SHOTS, 'C-07-badge-after-unpublish.png'), fullPage: true })

fs.writeFileSync(path.join(OUT, 'scenarioC2.json'), JSON.stringify({ results, net }, null, 1))
const failed = results.filter(r => !r.ok)
console.log(`\n==== scenario C2: ${results.length - failed.length}/${results.length} PASS ====`)
if (failed.length) console.log('FAILED:', failed.map(f => f.name).join(' | '))
await browser.close()
