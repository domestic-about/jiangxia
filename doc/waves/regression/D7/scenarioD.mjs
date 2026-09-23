// D7 L2 · 场景 D：工作台首页五张待办卡片 + 侧边菜单角标（SYS-HOME-001）
//   ① 五张卡片点击直达（route + query） ② 为 0 变灰不隐藏 ③ 菜单角标（含石蜡包埋）与卡片数字一致
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'
const require = createRequire('/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/code/miniapp/package.json')
const { chromium } = require('playwright')
const WEB = 'http://127.0.0.1:8093'
const OUT = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/.tmp/qa-d7-L2'
const SHOTS = path.join(OUT, 'shots')
const results = []
const rec = (name, ok, detail = '') => { results.push({ name, ok: !!ok, detail: String(detail).slice(0, 1500) }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  :: ' + String(detail).slice(0, 460) : ''}`) }
const todoReqs = []
const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1100 }, locale: 'zh-CN' })
const page = await ctx.newPage()
page.on('request', r => { if (r.url().includes('/lqg/home/todo')) todoReqs.push(r.url()) })
await page.goto(WEB + '/login', { waitUntil: 'domcontentloaded' })
await page.waitForSelector('input[type="text"]')
await page.fill('input[type="text"]', 'lqgadmin'); await page.fill('input[type="password"]', 'admin123')
await page.getByRole('button', { name: /登\s*录/ }).first().click()
await page.waitForURL(u => !u.pathname.includes('/login'))
await page.goto(WEB + '/index', { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.lqg-home__cards', { timeout: 30000 })
await page.waitForTimeout(3000)

// ── D1 五张卡片都在（含石蜡包埋），数字与接口一致 ────────────────────────
const cardCount = await page.locator('.lqg-home__cards > *').count()
rec('D1:首页五张待办卡片', cardCount === 5, `cards=${cardCount}`)
const cards = await page.locator('.lqg-home__cards > *').evaluateAll(els => els.map((e) => {
  const t = e.querySelector('.lqg-todo-card__label')
  const v = e.querySelector('.lqg-todo-card__num')
  return { cls: e.className, title: t ? t.textContent.trim() : e.innerText.split('\n')[0], value: v ? v.textContent.trim() : null, text: e.innerText.replace(/\n/g, '|') }
}))
console.log('CARDS', JSON.stringify(cards, null, 1))
const byTitle = (kw) => cards.find(c => (c.title || c.text).includes(kw))
const expect = { 样本: '2', 石蜡: '1', 超期: '2', 外部: '2', 渲染: '0' }
for (const [kw, want] of Object.entries(expect)) {
  const c = byTitle(kw)
  rec(`D1:卡片「${kw}」数字=${want}（与 /lqg/home/todo 一致）`, !!c && c.value === want, c ? JSON.stringify(c) : 'not found')
}
// 为 0 变灰不隐藏
const zeroCard = byTitle('渲染')
const zeroVisible = await page.locator('.lqg-home__cards > *').nth(cards.indexOf(zeroCard)).isVisible()
rec('D1:渲染失败=0 的卡片仍显示（不是 v-if 隐藏）', !!zeroCard && zeroVisible, JSON.stringify(zeroCard))
const zeroCls = zeroCard?.cls || ''
rec('D1:渲染失败=0 的卡片带「变灰」样式类', /zero|is-0|muted/i.test(zeroCls), zeroCls)
const zeroHint = zeroCard?.text || ''
rec('D1:为 0 的卡片有专属于 0 的说明文案', /没有|暂无|无待办|一切正常/.test(zeroHint), zeroHint)
// 非零卡片不带灰类
const nonZeroCls = byTitle('样本')?.cls || ''
rec('D1:非 0 卡片不带变灰类', !/zero|is-0|muted/i.test(nonZeroCls), nonZeroCls)
await page.screenshot({ path: path.join(SHOTS, 'D-01-home.png'), fullPage: true })

// ── D2 五张卡片点击直达 ─────────────────────────────────────────────────
const targets = [
  ['样本', /\/sample/, /verifyStatus=pending/],
  ['石蜡', /\/embed/, /verifyStatus=pending/],
  ['超期', /\/cryo/, /overdueOnly=true/],
  ['外部', /\/auth/, /bindStatus=pending/],
  ['渲染', /doc-console/, null]
]
for (const [kw, pathRe, queryRe] of targets) {
  await page.goto(WEB + '/index', { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.lqg-home__cards', { timeout: 20000 }); await page.waitForTimeout(1500)
  const idx = cards.findIndex(c => (c.title || c.text).includes(kw))
  // 点卡片里的「去处理」链接/按钮（整卡可点或按钮可点，两种都试）
  const card = page.locator('.lqg-home__cards > *').nth(idx)
  await card.locator('a, button, [class*=go], [class*=link]').first().click({ timeout: 10000 }).catch(async () => { await card.click() })
  await page.waitForTimeout(2200)
  const u = page.url()
  let ok = pathRe.test(u)
  if (queryRe) ok = ok && queryRe.test(u)
  rec(`D2:卡片「${kw}」直达 ${pathRe}${queryRe ? ' + ' + queryRe : ''}`, ok, u.replace(WEB, ''))
}
await page.screenshot({ path: path.join(SHOTS, 'D-02-card-target.png'), fullPage: true })

// ── D3 菜单角标 与 卡片数字一致（含石蜡包埋） ────────────────────────────
todoReqs.length = 0
await page.goto(WEB + '/index', { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.lqg-home__cards'); await page.waitForTimeout(3000)
const badges = await page.locator('.el-menu .el-badge').evaluateAll(els => els.map(e => ({
  text: e.innerText.trim(),
  hidden: e.classList.contains('is-hidden') || /display:\s*none/.test(getComputedStyle(e.querySelector('.el-badge__content') || e).display),
  content: (() => { const c = e.querySelector('.el-badge__content'); return c ? { disp: getComputedStyle(c).display, text: c.textContent.trim() } : null })(),
  parent: (e.closest('li') ? e.closest('li').innerText.replace(/\n/g, '|').slice(0, 60) : '')
})))
console.log('BADGES', JSON.stringify(badges, null, 1))
const visBadge = (kw) => badges.find(b => b.parent.includes(kw) && b.content && b.content.disp !== 'none')
const bSample = visBadge('样本')
const bEmbed = visBadge('石蜡')
const bCryo = visBadge('冻存')
const bAuth = badges.find(b => b.parent.includes('人员') || b.parent.includes('单位') || b.parent.includes('外部'))
rec('D3:样本总表角标=2 与卡片一致', bSample?.content?.text === '2', JSON.stringify(bSample))
rec('D3:★石蜡包埋角标=1 与卡片一致（含石蜡包埋）', bEmbed?.content?.text === '1', JSON.stringify(bEmbed))
rec('D3:冻存管理角标=2 与卡片一致', bCryo?.content?.text === '2', JSON.stringify(bCryo))
rec('D3:人员与单位角标=2 与卡片一致', !!bAuth && bAuth.content?.text === '2', JSON.stringify(badges.filter(b => b.parent.includes('人员') || b.parent.includes('单位'))))
// 为 0 不显示（渲染失败无菜单项；冻存超期=2 不是 0）。这里断言：没有内容为 "0" 的可见角标
const zeroBadges = badges.filter(b => b.content && b.content.disp !== 'none' && b.content.text === '0')
rec('D3:没有可见的「0」角标（为 0 不显示）', zeroBadges.length === 0, JSON.stringify(zeroBadges))
// 侧边栏自己不再请求一次 home/todo（与首页共用 store）
rec('D3:进首页后 /lqg/home/todo 只被请求一次（角标与卡片同源）', todoReqs.length === 1, JSON.stringify(todoReqs))
await page.screenshot({ path: path.join(SHOTS, 'D-03-sidebar-badges.png'), fullPage: true })

fs.writeFileSync(path.join(OUT, 'scenarioD.json'), JSON.stringify({ results, cards, badges, todoReqs }, null, 1))
const failed = results.filter(r => !r.ok)
console.log(`\n==== scenario D: ${results.length - failed.length}/${results.length} PASS ====`)
if (failed.length) console.log('FAILED:', failed.map(f => f.name).join(' | '))
await browser.close()
