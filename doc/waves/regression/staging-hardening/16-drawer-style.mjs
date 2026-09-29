// ============================================================================
// 飞书《测试问题记录表》2026-09-29 的机器判据 —— 工作台 · row 11（编辑抽屉样式）
//
// 复述卡（见 doc/_autopilot/_ledger-8MBuHU.json）：
//   ① 「更多 ▾」与「保存」之间要有明确间距，不许贴在一起
//   ② 标题「编辑样本」要更明显、与下方内容拉开距离
//   ③ 「最后修改：…」要比标题弱一档（字号更小/颜色更浅）、且不与标题紧贴
//   ④ H5/窄屏（390）抽屉占满、**一行只显示一个表单项**（截图里两列被压扁、值被截断）
//
// 跑法（工作区根）：node doc/waves/regression/staging-hardening/16-drawer-style.mjs
// 前置：后端 8091、工作台 8093（本地 qa-up 环境，账号 lqgadmin/admin123）。
// 退出码：0 = 全过；1 = 有失败项。
// ============================================================================
import { createRequire } from 'node:module'
import path from 'node:path'
const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const WEB = process.env.LQG_WEB_BASE || 'http://127.0.0.1:8093'
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }

const b = await chromium.launch()

async function loginAndOpenDrawer(width, height) {
  const ctx = await b.newContext({ viewport: { width, height } })
  const p = await ctx.newPage()
  await p.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await p.waitForSelector('.login-form input', { timeout: 40000 })
  const i = p.locator('.login-form input')
  await i.nth(0).fill('lqgadmin'); await i.nth(1).fill('admin123')
  await p.click('.login-form button'); await p.waitForTimeout(3500)
  // 样本记录信息表：先直接走 /sample，拿不到行就点左侧菜单
  await p.goto(`${WEB}/sample`, { waitUntil: 'domcontentloaded' }); await p.waitForTimeout(2500)
  if (!(await p.locator('.el-table__row button:has-text("编辑")').count())) {
    const menu = p.locator('.el-menu-item:has-text("样本记录信息表"), .sidebar-container a:has-text("样本记录")')
    if (await menu.count()) { await menu.first().click(); await p.waitForTimeout(2500) }
  }
  const edit = p.locator('.el-table__row button:has-text("编辑")').first()
  if (!(await edit.count())) return { p, ctx, opened: false }
  await edit.click({ timeout: 8000 }).catch(() => {})
  await p.waitForTimeout(1800)
  return { p, ctx, opened: true }
}

// ── 桌面 1440：①②③ ─────────────────────────────────────────────────────────
{
  const { p, ctx, opened } = await loginAndOpenDrawer(1440, 900)
  check('前置：样本列表能打开「编辑样本」抽屉', opened)
  if (opened) {
    const m = await p.evaluate(() => {
      const d = [...document.querySelectorAll('.el-drawer')].find(x => x.getBoundingClientRect().width > 0)
      if (!d) return null
      const title = d.querySelector('.el-drawer__title')
      const head = d.querySelector('.el-drawer__header')
      const last = d.querySelector('.lqg-sample-drawer__lastmod')
      const more = [...d.querySelectorAll('button')].find(x => x.textContent.includes('更多'))
      const save = [...d.querySelectorAll('button')].find(x => x.textContent.trim() === '保存')
      const r = (el) => (el ? el.getBoundingClientRect() : null)
      const cs = (el) => (el ? getComputedStyle(el) : null)
      let gap = null
      if (more && save) {
        const a = r(more), s = r(save)
        // 同一行 → 水平间距；换行 → 垂直间距（取更贴的那一个轴）
        gap = (a.bottom <= s.top + 1) ? { axis: 'v', px: Math.round(s.top - a.bottom) }
            : (a.right <= s.left + 1) ? { axis: 'h', px: Math.round(s.left - a.right) }
            : { axis: 'overlap', px: 0 }
      }
      return {
        titleSize: title ? parseFloat(cs(title).fontSize) : null,
        titleWeight: title ? cs(title).fontWeight : null,
        headMarginBottom: head ? parseFloat(cs(head).marginBottom) : 0,
        headBorderBottom: head ? cs(head).borderBottomWidth : null,
        lastSize: last ? parseFloat(cs(last).fontSize) : null,
        lastMarginBottom: last ? parseFloat(cs(last).marginBottom) : null,
        lastGapFromTitle: (title && last) ? Math.round(r(last).top - r(title).bottom) : null,
        moreSaveGap: gap,
        drawerWidth: Math.round(r(d).width),
      }
    })
    check('抽屉渲染出来了', !!m, m ? '' : '（找不到可见 .el-drawer）')
    if (m) {
      check('② 标题更明显（字号 ≥ 17px 且加粗）', m.titleSize >= 17 && Number(m.titleWeight) >= 600, `size=${m.titleSize} weight=${m.titleWeight}`)
      check('② 标题与内容拉开（header 下边距 ≥ 14px）', m.headMarginBottom >= 14, `${m.headMarginBottom}px`)
      check('③ 「最后修改」比标题弱一档（字号 ≤ 13px）', m.lastSize !== null && m.lastSize <= 13, `${m.lastSize}px vs 标题 ${m.titleSize}px`)
      check('③ 「最后修改」不与标题紧贴（间距 ≥ 6px）', m.lastGapFromTitle !== null && m.lastGapFromTitle >= 6, `${m.lastGapFromTitle}px`)
      check('① 「更多」不与「保存」贴上（同行水平 ≥ 40px / 换行垂直 ≥ 8px）',
        !!m.moreSaveGap && ((m.moreSaveGap.axis === 'h' && m.moreSaveGap.px >= 40) || (m.moreSaveGap.axis === 'v' && m.moreSaveGap.px >= 8)),
        JSON.stringify(m.moreSaveGap))
    }
  }
  await ctx.close()
}

// ── 窄屏 390：④ 抽屉占满 + 一行一个表单项 ───────────────────────────────────
{
  const { p, ctx, opened } = await loginAndOpenDrawer(390, 844)
  check('前置：390 下能打开抽屉', opened)
  if (opened) {
    const m = await p.evaluate(() => {
      const d = [...document.querySelectorAll('.el-drawer')].find(x => x.getBoundingClientRect().width > 0)
      if (!d) return null
      const cols = [...d.querySelectorAll('.el-col')]
        .filter(c => c.getBoundingClientRect().width > 0)
        .map(c => Math.round(c.getBoundingClientRect().width))
      const body = d.querySelector('.el-drawer__body')
      return {
        drawerWidth: Math.round(d.getBoundingClientRect().width),
        viewport: window.innerWidth,
        bodyWidth: body ? Math.round(body.getBoundingClientRect().width) : null,
        colWidths: cols.slice(0, 12),
        maxCol: cols.length ? Math.max(...cols) : 0,
        minCol: cols.length ? Math.min(...cols) : 0,
      }
    })
    check('④ 抽屉占满视口（≥ 视口 - 2px）', !!m && m.drawerWidth >= m.viewport - 2, m ? `${m.drawerWidth} vs ${m.viewport}` : '')
    check('④ 一行只显示一个表单项（每个 el-col 都接近满宽）',
      !!m && m.minCol >= m.bodyWidth * 0.92, m ? `col 宽 ${m.minCol}..${m.maxCol}，body ${m.bodyWidth}` : '')
  }
  await ctx.close()
}

await b.close()
console.log(`\n结果：${fails.length === 0 ? 'PASS' : 'FAIL'}（失败 ${fails.length} 条）`)
if (fails.length) { console.log('失败项：' + fails.join(' / ')); process.exit(1) }
