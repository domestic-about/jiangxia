// ============================================================================
// 飞书《测试问题记录表》2026-09-28 三条 UI 问题的机器判据（可复跑）
//   小程序 · 行6：必填项角标在文字**右边**、标签左对齐
//   工作台 · 行9：① 侧边菜单角标不被截断 ② 筛选项标签不换行 ③ 筛选与下方多留间距
//   工作台 · 行10：抽屉在 H5（窄屏）下不溢出、桌面保持固定宽
//
// 跑法（工作区根）：node doc/waves/regression/V-round/feishu-20260928-ui.mjs
// 前置：后端 8091、工作台 8093、小程序 H5 9202。
// ============================================================================
import { createRequire } from 'node:module'
import path from 'node:path'
const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')
const WEB = 'http://127.0.0.1:8093', MP = 'http://127.0.0.1:9202'
const fails = []; const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }
const b = await chromium.launch()

// ── 小程序：必填角标在文字右边 ──────────────────────────────────────────────
{
  const ctx = await b.newContext({ viewport: { width: 420, height: 900 } }); const p = await ctx.newPage()
  await p.goto(`${MP}/#/pages/login/index`, { waitUntil: 'networkidle' })
  await p.waitForSelector('.login__mock-btn'); await p.locator('.login__box').click(); await p.waitForTimeout(200)
  await p.locator('.login__mock-btn').nth(0).click(); await p.waitForTimeout(4000)
  await p.goto(`${MP}/#/pages/sample/form?mode=new`, { waitUntil: 'networkidle' }); await p.waitForTimeout(3000)
  const r = await p.evaluate(() => {
    const cells = [...document.querySelectorAll('.wd-cell')].filter(c => c.querySelector('.wd-cell__required'))
    return cells.slice(0, 5).map(c => {
      const req = c.querySelector('.wd-cell__required'), t = c.querySelector('.wd-cell__title')
      return { 在文字后: t ? !!(t.compareDocumentPosition(req) & Node.DOCUMENT_POSITION_FOLLOWING) : null,
               用了left类: req.classList.contains('wd-cell__required--left'),
               对齐: t ? getComputedStyle(t).textAlign : null }
    })
  })
  check('小程序：找到必填角标', r.length > 0, `共 ${r.length} 个`)
  check('小程序：角标都在文字右边（且不用 --left 类）', r.every(x => x.在文字后 && !x.用了left类), JSON.stringify(r.slice(0, 2)))
  check('小程序：标签左对齐', r.every(x => x.对齐 === 'left'))
  await ctx.close()
}

// ── 工作台：角标不截断 / 标签不换行 / 间距 / 抽屉窄屏 ────────────────────────
{
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } }); const p = await ctx.newPage()
  await p.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' }); await p.waitForSelector('.login-form input', { timeout: 30000 })
  const i = p.locator('.login-form input'); await i.nth(0).fill('lqgadmin'); await i.nth(1).fill('admin123')
  await p.click('.login-form button'); await p.waitForTimeout(3500)
  await p.goto(`${WEB}/index`, { waitUntil: 'domcontentloaded' }); await p.waitForTimeout(3000)
  const badge = await p.evaluate(() => {
    const el = [...document.querySelectorAll('.lqg-menu-badge .el-badge__content')].find(e => e.offsetParent !== null && !e.classList.contains('is-hidden'))
    if (!el) return null
    const r = el.getBoundingClientRect(); let a = el.parentElement, cl = null
    while (a && a !== document.body) { const cs = getComputedStyle(a); if (cs.overflowY !== 'visible' || cs.overflow !== 'visible') { cl = a; break } a = a.parentElement }
    return { 上边被截: cl ? Math.max(0, Math.round(cl.getBoundingClientRect().top - r.top)) : 0 }
  })
  check('工作台：侧边菜单角标不被截断', badge && badge.上边被截 === 0, JSON.stringify(badge))

  for (const r of ['/sample', '/embed', '/cryo']) {
    await p.goto(`${WEB}${r}`, { waitUntil: 'domcontentloaded' }); await p.waitForTimeout(2200)
    const o = await p.evaluate(() => {
      const f = document.querySelector('.el-form.lqg-sample__filter, .el-form.lqg-embed__filter, .el-form.lqg-cryo__filter')
      const labels = [...document.querySelectorAll('.el-form-item__label')]
      return { mb: f ? getComputedStyle(f).marginBottom : null,
               ws: labels[0] ? getComputedStyle(labels[0]).whiteSpace : null,
               换行: labels.filter(l => l.scrollHeight > l.clientHeight + 2).map(l => l.innerText.trim()) }
    })
    check(`工作台 ${r}：筛选下边距 ≥ 16px`, o.mb === '16px', o.mb)
    check(`工作台 ${r}：标签白色空间 nowrap`, o.ws === 'nowrap', o.ws)
    check(`工作台 ${r}：没有换行的标签`, o.换行.length === 0, JSON.stringify(o.换行))
  }
  await ctx.close()
}

// ── 抽屉：窄屏占满、不溢出；桌面固定宽 ──────────────────────────────────────
for (const vw of [1440, 390]) {
  const ctx = await b.newContext({ viewport: { width: vw, height: 844 } }); const p = await ctx.newPage()
  await p.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' }); await p.waitForSelector('.login-form input', { timeout: 30000 })
  const i = p.locator('.login-form input'); await i.nth(0).fill('lqgadmin'); await i.nth(1).fill('admin123')
  await p.click('.login-form button'); await p.waitForTimeout(3000)
  await p.goto(`${WEB}/embed`, { waitUntil: 'domcontentloaded' }); await p.waitForTimeout(2500)
  const btn = p.locator('.el-table__row button:has-text("编辑")').first()
  if (await p.locator('.el-table__row button:has-text("编辑")').count()) await btn.click({ timeout: 8000 }).catch(() => {})
  else await p.locator('.el-table__row button').first().click({ timeout: 8000 }).catch(() => {})
  await p.waitForTimeout(2200)
  const o = await p.evaluate(() => {
    const d = [...document.querySelectorAll('.el-drawer')].find(x => x.getBoundingClientRect().width > 0)
    if (!d) return null
    const r = d.getBoundingClientRect()
    const over = [...d.querySelectorAll('*')].filter(e => e.getBoundingClientRect().right > window.innerWidth + 2 && e.getBoundingClientRect().width > 0).length
    return { 宽: Math.round(r.width), 溢出: Math.round(r.right - window.innerWidth), 内部横向溢出元素: over }
  })
  check(`抽屉 @${vw}px：不超出右边界`, o && o.溢出 <= 0, JSON.stringify(o))
  check(`抽屉 @${vw}px：内部无横向溢出`, o && o.内部横向溢出元素 === 0, JSON.stringify(o))
  if (vw === 390) check('抽屉 @390px：满宽', o && o.宽 === 390, JSON.stringify(o))
  if (vw === 1440) check('抽屉 @1440px：保持固定宽 780', o && o.宽 === 780, JSON.stringify(o))
  await ctx.close()
}
await b.close()
console.log(`\n结果：${fails.length === 0 ? 'PASS' : 'FAIL'}（失败 ${fails.length} 条）`)
if (fails.length) { console.log('失败项：' + fails.join(' / ')); process.exit(1) }
