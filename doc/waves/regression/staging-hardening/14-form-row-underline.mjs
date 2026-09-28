// ============================================================================
// 「四个记录卡的输入样式」判据（Kevin 2026-09-28 要求）
//
// 要求原话：「每个单项加一个比较浅的下划线」「每一个输入项尽量左右对齐」。
//
// 判据（真 DOM + computed style，不看源码）：
//   ① 每一行 `.fr` 都有一条 **1px 的浅色**下划线（色值必须是设计 token 里的
//      `--lqg-line` = #e2e9ea），**最后一行除外**（否则与卡片底边叠成双线）；
//   ② 所有标签的**左边界**在同一条竖线上（同一列内，容差 1px）；
//   ③ 所有取值区域的**右边界**在同一条竖线上（容差 2px —— 取值宽度不同，右对齐才是要的）。
//
// 跑法：LQG_MP_BASE=http://127.0.0.1:9202 node doc/waves/regression/staging-hardening/14-form-row-underline.mjs
// 前置：H5 dev 在 9202；本地后端 8091（mock 登录开着，用「内部人员·李工」进）。
// 退出码：0 = 全过；1 = 有失败项。
// ============================================================================
import { createRequire } from 'node:module'
import path from 'node:path'
const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const MP = (process.env.LQG_MP_BASE || 'http://127.0.0.1:9202').replace(/\/$/, '')
const SAMPLE = process.env.LQG_SAMPLE_ID || '9000001007'   // 截图里那条：SJ90000007 · 待核验
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }

const LIGHT_LINE = 'rgb(226, 233, 234)'   // #e2e9ea = --lqg-line

console.log(`目标：${MP}  样本：${SAMPLE}\n`)
const b = await chromium.launch()
const ctx = await b.newContext({ viewport: { width: 420, height: 900 } })
const p = await ctx.newPage()

// ── 登录（内部身份 → 核验页可写）────────────────────────────────────────────
await p.goto(`${MP}/#/pages/login/index`, { waitUntil: 'networkidle' })
await p.waitForTimeout(2500)
const box = p.locator('.login__box')
if (await box.count()) await box.first().click()
await p.waitForTimeout(300)
const meP = p.waitForResponse(r => r.url().includes('/mp/me'), { timeout: 40000 }).catch(() => null)
await p.locator('.login__mock-btn').first().click()
await meP
await p.waitForTimeout(2000)
check('前置：已用内部身份登录', !p.url().includes('login'), p.url())

// ── 打开核验页（截图那一屏）─────────────────────────────────────────────────
await p.goto(`${MP}/#/pages/verify/sample?id=${SAMPLE}&mode=verify`, { waitUntil: 'networkidle' })
await p.waitForTimeout(3000)
const rows = await p.evaluate(() => {
  const list = [...document.querySelectorAll('.fr')]
  return list.map((el, i) => {
    const cs = getComputedStyle(el)
    const label = el.querySelector('.wd-cell__title, .wd-input__label')
    const val = el.querySelector('.wd-cell__value, .wd-input__value, .fr__val')
    const r = el.getBoundingClientRect()
    return {
      i,
      最后一行: i === list.length - 1,
      下划线宽: cs.borderBottomWidth,
      下划线色: cs.borderBottomColor,
      下划线样式: cs.borderBottomStyle,
      标签左: label ? Math.round(label.getBoundingClientRect().left) : null,
      取值右: val ? Math.round(val.getBoundingClientRect().right) : null,
      行宽: Math.round(r.width),
    }
  })
})
check('① 核验页渲染出字段行', rows.length >= 4, `共 ${rows.length} 行`)
if (rows.length === 0) { await b.close(); console.log('\n结果：FAIL（没渲染出字段行，后续判据无意义）'); process.exit(1) }

const middle = rows.filter(r => !r.最后一行)
const badLine = middle.filter(r => r.下划线样式 !== 'solid' || parseFloat(r.下划线宽) !== 1 || r.下划线色 !== LIGHT_LINE)
check('② 非末行都有 1px 浅色下划线（--lqg-line #e2e9ea）', badLine.length === 0,
  badLine.length ? JSON.stringify(badLine.slice(0, 3)) : `${middle.length} 行全部命中`)
const last = rows.find(r => r.最后一行)
check('③ 最后一行不画线（不与卡片底边叠双线）', !last || parseFloat(last.下划线宽) === 0,
  last ? `末行 border=${last.下划线宽}` : '（只有一行）')

const lefts = [...new Set(rows.map(r => r.标签左).filter(v => v !== null))]
const rights = [...new Set(rows.map(r => r.取值右).filter(v => v !== null))]
check('④ 标签左边界在同一条竖线上', lefts.length === 1, `不同的左边界：${JSON.stringify(lefts)}`)
check('⑤ 取值右边界在同一条竖线上（容差 2px）',
  rights.length > 0 && Math.max(...rights) - Math.min(...rights) <= 2,
  `不同的右边界：${JSON.stringify(rights)}`)

await b.close()
console.log(`\n结果：${fails.length === 0 ? 'PASS' : 'FAIL'}（失败 ${fails.length} 条）`)
if (fails.length) { console.log('失败项：' + fails.join(' / ')); process.exit(1) }
