// ============================================================================
// 测试环境的「快速切换账号直接登录」（Kevin 2026-09-28 要求）
//
// 要验的三件事：
//   ① 登录页有**测试身份**面板：标题「测试身份登录」+ 一串账号按钮（内部/外部各若干）
//   ② 页面上**没有任何输入框** —— 测试人员不需要（也不该）输手机号或密码
//   ③ 点其中一个按钮 → **直接就进**（落到首页），且 `/mp/me` 回的身份与该身份相符
//
// 跑法（工作区根）：
//   LQG_MP_BASE=http://127.0.0.1:9202 node doc/waves/regression/staging-hardening/11-quick-account-switch.mjs
// 前置：小程序 H5 dev server 在跑（qa-up 起的 9202），且**本地 dev 构建开着 mock**
//       （env/.env.development 的 VITE_MOCK_LOGIN=1）。体验版同理：env/.env.test 也是 1。
//
// 退出码：0 = 全过；1 = 有失败项。
// ============================================================================
import { createRequire } from 'node:module'
import path from 'node:path'
const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const MP = (process.env.LQG_MP_BASE || 'http://127.0.0.1:9202').replace(/\/$/, '')
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }

console.log(`目标：${MP}\n`)
const b = await chromium.launch()
const ctx = await b.newContext({ viewport: { width: 420, height: 900 } })
const p = await ctx.newPage()

async function openLogin() {
  await p.goto(`${MP}/#/pages/login/index`, { waitUntil: 'networkidle' })
  await p.waitForTimeout(2500)
  // ★ 登录页要求先勾选「我已阅读并同意《用户协议》与《隐私政策》」—— 不勾就点身份按钮，
  //   会被拦下并弹提示，表现是「点了没反应」（我第一版探针就漏了这一步，见下面 ③ 的第一次失败）。
  //   这一条本身也是产品要求（UI:mp.login），所以探针里显式勾上是**模拟真实用户**，不是绕开校验。
  const box = p.locator('.login__box')
  if (await box.count()) { await box.first().click(); await p.waitForTimeout(400) }
}

// ── ①② 面板存在 + 页面上没有输入框 ─────────────────────────────────────────
await openLogin()
const panel = await p.evaluate(() => {
  const box = document.querySelector('.login__mock')
  const btns = [...document.querySelectorAll('.login__mock-btn')]
  const inputs = [...document.querySelectorAll('input, textarea')]
    .filter(e => e.type !== 'checkbox' && e.offsetParent !== null)
  return {
    有面板: !!box && box.offsetParent !== null,
    标题: document.querySelector('.login__mock-t')?.innerText.trim() || '',
    按钮数: btns.length,
    标签: btns.map(b => b.innerText.replace(/\s+/g, ' ').trim()),
    可见输入框: inputs.map(e => e.type || e.tagName),
  }
})
check('① 登录页有「测试身份登录」面板', panel.有面板 && panel.标题.includes('测试身份'), JSON.stringify({ 标题: panel.标题 }))
check('① 面板列出了多个可选账号', panel.按钮数 >= 8, `共 ${panel.按钮数} 个：${panel.标签.slice(0, 3).join(' / ')}…`)
check('① 面板里有内部与外部两类身份',
  panel.标签.some(t => t.includes('内部')) && panel.标签.some(t => t.includes('外部')),
  JSON.stringify(panel.标签.slice(0, 2)))
check('② 页面上没有任何要填的输入框（不用输手机号/密码）', panel.可见输入框.length === 0, JSON.stringify(panel.可见输入框))

// ── ③ 点一下就进：内部身份 ─────────────────────────────────────────────────
async function tap(index) {
  const meP = p.waitForResponse(r => r.url().includes('/mp/me'), { timeout: 40000 }).catch(() => null)
  await p.locator('.login__mock-btn').nth(index).click()
  const res = await meP
  await p.waitForTimeout(2500)
  let identity = null
  if (res) { try { identity = (await res.json())?.data?.identity ?? null } catch { /* 忽略 */ } }
  // 失败时把页面上的提示语一起带出来（例如「请先阅读并同意协议」）
  const toast = await p.evaluate(() => document.body.innerText.replace(/\s+/g, ' ').slice(0, 120))
  const agreed = await p.evaluate(() => !!document.querySelector('.login__box--on'))
  return { identity, url: p.url(), 已勾选协议: agreed, 页面文字: toast }
}

const first = await tap(0)
check('③ 点第 1 个账号（内部）直接就进，且身份=internal',
  first.identity === 'internal' && !first.url.includes('login'),
  JSON.stringify(first))

// ── ③ 外部身份同样一下就进 ──────────────────────────────────────────────────
const extIndex = panel.标签.findIndex(t => t.includes('外部'))
await openLogin()
const second = await tap(extIndex >= 0 ? extIndex : 1)
check('③ 点外部账号同样直接就进，且身份=external',
  second.identity === 'external' && !second.url.includes('login'),
  JSON.stringify(second))

// ── ④ 首页板块点了要有反应（Kevin 报过「点四个板块没反应」）──────────────────
await openLogin()
const third = await tap(0)   // 内部身份
check('④ 前置：内部身份已进入首页', third.identity === 'internal', JSON.stringify({ identity: third.identity }))
const tiles = await p.locator('.lqg-tile').count()
check('④ 首页渲染出填写板块（内部 4 格）', tiles === 4, `实际 ${tiles} 格`)
if (tiles > 0) {
  const before = p.url()
  await p.locator('.lqg-tile').first().click()
  await p.waitForTimeout(2500)
  const after = p.url()
  check('④ 点第一个板块会跳转到该表的填写页',
    after !== before && /pages\/(sample|organoid|embed|cryo)\/form/.test(after),
    `${before} → ${after}`)
}

// ── ⑤ 「我的」页的行点了要有反应（Kevin 截图第二个红框）────────────────────────
await p.goto(`${MP}/#/pages/me/index`, { waitUntil: 'networkidle' })
await p.waitForTimeout(2500)
const rows = await p.locator('.merow').count()
check('⑤ 「我的」页渲染出行（内部：历史编辑记录 + 内部管理 4 行 + 协议 2 行）', rows >= 6, `实际 ${rows} 行`)
if (rows > 0) {
  const before = p.url()
  await p.locator('.merow').first().click()
  await p.waitForTimeout(2500)
  check('⑤ 点第一行（历史编辑记录）会跳转', p.url() !== before, `${before} → ${p.url()}`)
}

await b.close()
console.log(`\n结果：${fails.length === 0 ? 'PASS' : 'FAIL'}（失败 ${fails.length} 条）`)
if (fails.length) { console.log('失败项：' + fails.join(' / ')); process.exit(1) }
