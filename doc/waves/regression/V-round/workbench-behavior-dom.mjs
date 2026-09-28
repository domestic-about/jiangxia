// ============================================================================
// 弱断言「行为半句」的 DOM 探针（工作台侧两条，2026-09-27）
//   ① QC-WEB-002 acc2：两个（实为三个）页签**真的接进编辑页、点了会切内容**（原由 grep 组件名代证）
//   ② SYS-WEB-001 acc2：主色 token **真的生效**（原由 grep `tokens.scss` 里的字符串代证；
//                       实测「扫产物字符串」抓不住 —— 见 #331。这里改读 **computed style**）
//
// 【怎么证伪】
//   · ①：把 `editor/index.vue` 的 `TABS` 删掉一项 → 页签数不对 → 本探针必红。
//   · ②：把 `settings.ts` 的 `theme` 改成上游蓝 `#409EFF` → documentElement 的
//        `--el-color-primary` 会变成蓝 → 本探针必红（这正是 tokens.scss 注释里「主色两处必须同值」的护栏）。
//
// 跑法（工作区根）：node doc/waves/regression/V-round/workbench-behavior-dom.mjs [--only=tabs|token]
// 前置：后端 8091、工作台 8093（dev）、seed 已灌（样本 9000001001 存在）。
// ============================================================================
import { createRequire } from 'node:module'
import path from 'node:path'

const WS = process.env.LQG_WS || process.cwd()
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const WEB = process.env.LQG_WEB || 'http://127.0.0.1:8093'
const SAMPLE = 9000001001
const ONLY = ((process.argv.find((a) => a.startsWith('--only=')) || '').split('=')[1] || '').trim()
const fails = []
const check = (n, ok, extra = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${extra ? ' — ' + extra : ''}`); if (!ok) fails.push(n) }

/** 把 #0e7c7b / rgb(14, 124, 123) 之类归一化成 "r,g,b" */
const norm = (s) => {
  const t = String(s || '').trim().toLowerCase()
  const hex = t.match(/^#([0-9a-f]{6})$/)
  if (hex) { const n = parseInt(hex[1], 16); return `${(n >> 16) & 255},${(n >> 8) & 255},${n & 255}` }
  const rgb = t.match(/rgba?\(([^)]+)\)/)
  if (rgb) return rgb[1].split(',').slice(0, 3).map((x) => x.trim()).join(',')
  return t
}

const run = async () => {
  const browser = await chromium.launch()
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()

  await page.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login-form input', { timeout: 30000 })
  const inputs = page.locator('.login-form input')
  await inputs.nth(0).fill('lqgadmin')
  await inputs.nth(1).fill('admin123')
  await page.click('.login-form button')
  await page.waitForTimeout(3000)
  check('登录成功', !page.url().includes('login'), page.url())

  // ── ② 主色 token 的计算值（弱断言第 8 条的正解）────────────────────────────
  if (!ONLY || ONLY === 'token') {
    const vars = await page.evaluate(() => {
      const cs = getComputedStyle(document.documentElement)
      return {
        primary: cs.getPropertyValue('--el-color-primary').trim(),
        lqg: cs.getPropertyValue('--lqg-primary').trim(),
      }
    })
    const got = norm(vars.primary)
    console.log(`  computed --el-color-primary="${vars.primary}"  --lqg-primary="${vars.lqg}"`)
    check('②★ 主色计算值 = #0E7C7B（rgb 14,124,123）', got === '14,124,123', `归一化后=${got}`)
    check('②★ 主色不是上游若依蓝（64,158,255）', got !== '64,158,255')
    check('②★ --lqg-primary 也是同一支青绿', norm(vars.lqg) === '14,124,123', norm(vars.lqg))
  }

  // ── ① QC 编辑页的三个页签真的可切 ─────────────────────────────────────────
  if (!ONLY || ONLY === 'tabs') {
    await page.goto(`${WEB}/qc-console/qc-editor?sampleId=${SAMPLE}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(4000)
    const bodyTxt = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ')
    check('① 编辑页真的打开了（不是 404/空页）', bodyTxt.length > 60 && !/404错误|找不到网页/.test(bodyTxt), bodyTxt.slice(0, 90))
    const tabs = page.locator('.el-tabs__item')
    const n = await tabs.count()
    check('①★ 页签数 = 3（样本质控 / 类器官质控 / 质量评分）', n === 3, `实际 ${n}`)
    const labels = (await tabs.allInnerTexts().catch(() => [])).map((s) => s.trim())
    console.log('  页签文案：' + labels.join(' | '))
    // 逐个点，断言「内容确实换了」：每个页签用它自己的**内容标记词**判断（比面板文本指纹稳）。
    // ★ 第一版用 `.el-tab-pane:visible` 取文本 → 三个都取到空串（选不中）→ 假红；改成全页文本 + 标记词。
    const MARKERS = [
      ['细胞活率', '样本图片', '收样'],          // 样本质控表
      ['类器官生长', '形成类器官', '类器官'],     // 类器官质控表
      ['评分', '变量', '分值'],                  // 类器官质量评分表
    ]
    for (let i = 0; i < n; i++) {
      await tabs.nth(i).click({ timeout: 8000 }).catch(() => {})
      await page.waitForTimeout(1800)
      const active = await tabs.nth(i).getAttribute('aria-selected').catch(() => null)
      check(`①★ 第 ${i + 1} 个页签（${(labels[i] || '?').split('\n')[0]}）点得动且成为选中态`, active === 'true', `aria-selected=${active}`)
      const t = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ')
      // ★ 收紧：标记词若本身出现在**页签标签**里（如「评分」⊂「类器官质量评分表」），
      //   它会在任何页签下都命中标签 → 空对照假绿。只认「不是任何页签标签子串」的标记词。
      const labelText = labels.join(' ')
      const safe = (MARKERS[i] || []).filter((m) => !labelText.includes(m))
      const hit = safe.filter((m) => t.includes(m))
      check(`①★ 第 ${i + 1} 个页签的内容真的渲染出来（只用页签标签之外的标记词）`, hit.length > 0,
        `可用标记=[${safe.join(',')}] 命中=[${hit.join(',')}] 页面前 90 字：${t.slice(0, 90)}`)
    }
  }

  await browser.close()
  console.log(`\n结果：${fails.length === 0 ? 'PASS' : 'FAIL'}（失败 ${fails.length} 条）`)
  if (fails.length) { console.log('失败项：' + fails.join(' / ')); process.exit(1) }
}

run().catch((e) => { console.error('[ERROR]', e); process.exit(2) })
