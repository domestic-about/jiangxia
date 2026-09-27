// ============================================================================
// 线上测试环境（https://songjian.tianda.studio）的工作台 UI 判据 —— 2026-09-28
//
// 为什么不复用 doc/waves/regression/V-round/feishu-20260928-ui.mjs：
//   那份钉在**本地** qa-up 环境（8093 / 9202），是飞书三轮修复的**冻结证据**，不该为了
//   一次远程复验去改它。这份是同判据的**远程版**：只查工作台（小程序 H5 没有部署到公网）。
//
// 判据（与飞书表一一对应）：
//   工作台 · 行9 ① 侧边菜单角标不被截断 ② 筛选项标签不换行 ③ 筛选与下方间距 16px
//   工作台 · 行10 抽屉：390px 满宽不溢出 / 1440px 保持 780
//
// 跑法：
//   LQG_UI_BASE=https://songjian.tianda.studio \
//   LQG_UI_USER=lqgadmin LQG_UI_PASSWORD='...' \
//   node doc/waves/regression/staging-hardening/03-deployed-workbench-ui.mjs
//
// 退出码：0 = 全过；1 = 有失败项（失败项会列在最后）。
// ============================================================================
import { createRequire } from 'node:module'
import path from 'node:path'
const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const BASE = (process.env.LQG_UI_BASE || 'https://songjian.tianda.studio').replace(/\/$/, '')
const USER = process.env.LQG_UI_USER || 'lqgadmin'
const PASS = process.env.LQG_UI_PASSWORD
if (!PASS) { console.error('[error] 需要 LQG_UI_PASSWORD（不要在脚本里写死口令）'); process.exit(2) }

const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }
console.log(`目标：${BASE}（用户 ${USER}）\n`)
const b = await chromium.launch()

async function login(ctx) {
  const p = await ctx.newPage()
  // ★ 先等 /auth/code 落地再填表：登录页初始 captchaEnabled=true（login.vue 的 ref 初值），
  //   拿到接口返回（test profile 是 false）后才把验证码那一项**移除**。抢在它之前提交，会被
  //   表单校验「验证码必填」挡下 → 停在 /login，后面所有断言拿到 null（2026-09-28 实测踩过）。
  const codeP = p.waitForResponse(r => r.url().includes('/auth/code'), { timeout: 30000 }).catch(() => null)
  await p.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  await p.waitForSelector('.login-form input', { timeout: 45000 })
  await codeP
  await p.waitForTimeout(800)
  const i = p.locator('.login-form input')
  await i.nth(0).fill(USER); await i.nth(1).fill(PASS)
  // 万一后端仍开着验证码（captchaEnabled=true），填一个占位值把流程走完——UI 判据与验证码无关
  const code = p.locator('.login-form input[placeholder="验证码"]')
  if (await code.count()) await code.first().fill('0000')
  await p.click('.login-form button')
  // ★ 反空断言：先证明「真的登进去了」。没有这一条，登录失败会表现为后面一堆 null 的 ✗，
  //   看不出根因（2026-09-28 第一版探针就是这样，13 条 ✗ 全是登录没成）。
  await p.waitForURL(u => !String(u).includes('/login'), { timeout: 30000 }).catch(() => {})
  const ok = !p.url().includes('/login')
  check('线上 前置：能登录进工作台（否则后面所有断言都不可信）', ok, p.url())
  if (!ok) {
    console.log('  登录页文字：' + (await p.evaluate(() => document.body.innerText.replace(/\s+/g, ' ').slice(0, 200))))
    await b.close(); console.log('\n结果：FAIL（前置失败，后续未执行）'); process.exit(1)
  }
  await p.waitForTimeout(1500)
  return p
}

// ── 行9：角标 / 标签换行 / 间距 ──────────────────────────────────────────────
{
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const p = await login(ctx)
  await p.goto(`${BASE}/index`, { waitUntil: 'domcontentloaded' }); await p.waitForTimeout(3000)

  const badge = await p.evaluate(() => {
    const el = [...document.querySelectorAll('.lqg-menu-badge .el-badge__content')]
      .find(e => e.offsetParent !== null && !e.classList.contains('is-hidden'))
    if (!el) return null
    const r = el.getBoundingClientRect(); let a = el.parentElement, cl = null
    while (a && a !== document.body) {
      const cs = getComputedStyle(a)
      if (cs.overflowY !== 'visible' || cs.overflow !== 'visible') { cl = a; break }
      a = a.parentElement
    }
    return { 上边被截: cl ? Math.max(0, Math.round(cl.getBoundingClientRect().top - r.top)) : 0 }
  })
  check('线上 行9①：侧边菜单角标不被截断', badge && badge.上边被截 === 0, JSON.stringify(badge))

  for (const r of ['/sample', '/embed', '/cryo']) {
    await p.goto(`${BASE}${r}`, { waitUntil: 'domcontentloaded' }); await p.waitForTimeout(2500)
    const o = await p.evaluate(() => {
      const f = document.querySelector('.el-form.lqg-sample__filter, .el-form.lqg-embed__filter, .el-form.lqg-cryo__filter')
      const labels = [...document.querySelectorAll('.el-form-item__label')]
      return { mb: f ? getComputedStyle(f).marginBottom : null,
               ws: labels[0] ? getComputedStyle(labels[0]).whiteSpace : null,
               换行: labels.filter(l => l.scrollHeight > l.clientHeight + 2).map(l => l.innerText.trim()) }
    })
    check(`线上 行9③ ${r}：筛选下边距 16px`, o.mb === '16px', o.mb)
    check(`线上 行9② ${r}：标签 nowrap`, o.ws === 'nowrap', o.ws)
    check(`线上 行9② ${r}：没有换行的标签`, o.换行.length === 0, JSON.stringify(o.换行))
  }
  await ctx.close()
}

// ── 行10：抽屉宽度（桌面固定 / 窄屏满宽不溢出）──────────────────────────────
for (const vw of [1440, 390]) {
  const ctx = await b.newContext({ viewport: { width: vw, height: 844 } })
  const p = await login(ctx)
  await p.goto(`${BASE}/embed`, { waitUntil: 'domcontentloaded' }); await p.waitForTimeout(2500)
  const btn = p.locator('.el-table__row button:has-text("编辑")').first()
  if (await p.locator('.el-table__row button:has-text("编辑")').count()) await btn.click({ timeout: 10000 }).catch(() => {})
  else await p.locator('.el-table__row button').first().click({ timeout: 10000 }).catch(() => {})
  await p.waitForTimeout(2500)
  const o = await p.evaluate(() => {
    const d = [...document.querySelectorAll('.el-drawer')].find(x => x.getBoundingClientRect().width > 0)
    if (!d) return null
    const r = d.getBoundingClientRect()
    const over = [...d.querySelectorAll('*')]
      .filter(e => e.getBoundingClientRect().right > window.innerWidth + 2 && e.getBoundingClientRect().width > 0).length
    return { 宽: Math.round(r.width), 溢出: Math.round(r.right - window.innerWidth), 内部横向溢出元素: over }
  })
  check(`线上 行10 @${vw}px：抽屉不超出右边界`, o && o.溢出 <= 0, JSON.stringify(o))
  check(`线上 行10 @${vw}px：抽屉内部无横向溢出`, o && o.内部横向溢出元素 === 0, JSON.stringify(o))
  if (vw === 390) check('线上 行10 @390px：抽屉满宽（=390）', o && o.宽 === 390, JSON.stringify(o))
  if (vw === 1440) check('线上 行10 @1440px：抽屉固定宽 780', o && o.宽 === 780, JSON.stringify(o))
  await ctx.close()
}

await b.close()
console.log(`\n结果：${fails.length === 0 ? 'PASS' : 'FAIL'}（失败 ${fails.length} 条）`)
if (fails.length) { console.log('失败项：' + fails.join(' / ')); process.exit(1) }
