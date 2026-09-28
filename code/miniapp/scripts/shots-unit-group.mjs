/**
 * AUTH-GROUP-001 · 小程序「单位与组别」页截图（临时工具，不入库）
 *
 * 微信开发者工具在 subagent 沙箱里跑不通（SYS-MP-001 WARN-1），所以用 `pnpm build:h5` 的产物
 * + Playwright 覆盖：外部「我的 → 单位与组别」页（姓名 + 单位 → 组别联动选择器 + 末项「手动填写」）、
 * 保存前的待核验态、被驳回态（带原因）、首页 unbound 提示条进这一页。
 *
 * /mp/me、/mp/ext/units、/mp/ext/profile 全部路由拦截打桩（不依赖后端进程）。
 *
 * 跑法（cwd = code/miniapp）：node scripts/shots-unit-group.mjs
 */
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { existsSync, mkdirSync } from 'node:fs'
import { extname, join, normalize } from 'node:path'
import { chromium } from 'playwright'

const WS = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid'
const ROOT = `${WS}/code/miniapp/dist/build/h5`
const OUT = `${WS}/doc/waves/reports/AUTH-GROUP-001`
const PORT = 9210

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.ico': 'image/x-icon',
}

// GET /mp/ext/units 的真实响应（跟后端一致：只有 id 与名称、只含 active）
const UNITS = [
  { unitId: 9000009001, unitName: 'A 医院', groups: [{ groupId: 9000009101, groupName: '肝胆外科组' }, { groupId: 9000009102, groupName: '消化内科组' }] },
  { unitId: 9000009002, unitName: 'B 大学', groups: [{ groupId: 9000009103, groupName: '类器官课题组' }] },
]

const ME = {
  // 外部 · 已核验（进来改单位 / 组别）
  verified: {
    userId: '9000000111', name: '王医生', phoneMasked: '138****0011', identity: 'external',
    ext: { unitId: 9000009001, unitName: 'A 医院', groupId: 9000009101, groupName: '肝胆外科组', unitNameInput: null, groupNameInput: null, bindStatus: 'verified', rejectReason: null },
  },
  // 外部 · 刚保存完（回到待核验）
  pending: {
    userId: '9000000111', name: '王医生', phoneMasked: '138****0011', identity: 'external',
    ext: { unitId: 9000009001, unitName: 'A 医院', groupId: 9000009102, groupName: '消化内科组', unitNameInput: null, groupNameInput: null, bindStatus: 'pending', rejectReason: null },
  },
  // 外部 · 被驳回（显示原因）
  rejected: {
    userId: '9000000111', name: '王医生', phoneMasked: '138****0011', identity: 'external',
    ext: { unitId: null, unitName: null, groupId: null, groupName: null, unitNameInput: 'C 研究所', groupNameInput: '肿瘤组', bindStatus: 'rejected', rejectReason: '组别名请写全称，如「肝胆外科组」' },
  },
  // 外部 · 未填写（首页提示条那条路）
  unbound: {
    userId: '2101945077365493762', name: 'wx_13800000099', phoneMasked: '138****0099', identity: 'external',
    ext: { unitId: null, unitName: null, groupId: null, groupName: null, unitNameInput: null, groupNameInput: null, bindStatus: 'unbound', rejectReason: null },
  },
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${PORT}`)
  let p = decodeURIComponent(url.pathname)
  if (p === '/' || p === '') p = '/index.html'
  const file = normalize(join(ROOT, p))
  if (!file.startsWith(ROOT) || !existsSync(file)) {
    res.writeHead(404).end('not found')
    return
  }
  const body = await readFile(file)
  res.writeHead(200, { 'Content-Type': MIME[extname(file)] || 'application/octet-stream' })
  res.end(body)
})

await new Promise(r => server.listen(PORT, '127.0.0.1', r))
console.log(`static server on http://127.0.0.1:${PORT}`)
mkdirSync(OUT, { recursive: true })

const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await context.newPage()

let meKey = 'verified'
let savedPayload = null
await page.route('**/mp/me**', async (route) => {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: '操作成功', data: ME[meKey] }) })
})
await page.route('**/mp/ext/units**', async (route) => {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: '操作成功', data: UNITS }) })
})
await page.route('**/mp/ext/profile**', async (route) => {
  savedPayload = JSON.parse(route.request().postData() || '{}')
  // 保存成功 = 回到待核验：后续 /mp/me 就返回 pending 那份
  meKey = 'pending'
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: '操作成功', data: null }) })
})

await context.addInitScript(() => {
  window.localStorage.setItem('lqg_mp_token', JSON.stringify('e2e-token'))
})

const BASE = `http://127.0.0.1:${PORT}/#`

async function goto(hash, identity) {
  meKey = identity
  await page.goto('about:blank')
  await page.goto(`${BASE}${hash}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(900)
}

// ── 01：已核验的人进来（表单预填 + 状态徽标）────────────────────────────────
await goto('/pages/me/unit-group', 'verified')
await page.screenshot({ path: `${OUT}/10-mp-profile-verified.png`, fullPage: true })
const verifiedProbe = await page.evaluate(() => ({
  url: location.hash,
  text: document.querySelector('.profile')?.textContent?.replace(/\s+/g, ' ').trim(),
  unitLabel: document.querySelectorAll('.picker__text')[0]?.textContent?.trim(),
  groupLabel: document.querySelectorAll('.picker__text')[1]?.textContent?.trim(),
}))
console.log('verified probe', JSON.stringify(verifiedProbe))

// ── 02：单位选择面板（末项「列表里没有，手动填写」）─────────────────────────
await page.locator('.picker__field').first().click()
await page.waitForTimeout(600)
await page.screenshot({ path: `${OUT}/11-mp-unit-sheet.png`, fullPage: true })
const unitSheetProbe = await page.evaluate(() => ({
  title: Array.from(document.querySelectorAll('.sheet__title')).find(e => e.offsetParent !== null)?.textContent?.trim(),
  // 只看可见面板（关掉的 wd-popup 留在 DOM 里，display:none）
  items: Array.from(document.querySelectorAll('.sheet__item')).filter(e => e.offsetParent !== null).map(e => e.textContent.trim()),
}))
console.log('unit sheet probe', JSON.stringify(unitSheetProbe))

// ── 03：选 B 大学 → 组别面板只剩它的组别（联动）─────────────────────────────
await page.evaluate(() => {
  const it = Array.from(document.querySelectorAll('.sheet__item')).find(e => e.textContent.includes('B 大学'))
  it?.click()
})
await page.waitForTimeout(700)
await page.locator('.picker__field').nth(1).click()
await page.waitForTimeout(700)
await page.screenshot({ path: `${OUT}/12-mp-group-sheet-linked.png`, fullPage: true })
const groupSheetProbe = await page.evaluate(() => ({
  title: Array.from(document.querySelectorAll('.sheet__title')).find(e => e.offsetParent !== null)?.textContent?.trim(),
  sub: document.querySelector('.sheet__sub')?.textContent?.trim(),
  items: Array.from(document.querySelectorAll('.sheet__item')).filter(e => e.offsetParent !== null).map(e => e.textContent.trim()),
}))
console.log('group sheet probe', JSON.stringify(groupSheetProbe))

// ── 04：末项「手动填写」→ 出单位名 / 组别名输入框 ───────────────────────────
await page.evaluate(() => {
  const it = Array.from(document.querySelectorAll('.sheet__item')).find(e => e.textContent.includes('手动填写'))
  it?.click()
})
await page.waitForTimeout(800)
await page.screenshot({ path: `${OUT}/13-mp-manual-input.png`, fullPage: true })
const manualProbe = await page.evaluate(() => ({
  labels: Array.from(document.querySelectorAll('.picker__label')).map(e => e.textContent.trim()),
  // ★ H5 的 uni-input 把 placeholder 渲染成隐藏 div，不是 <input placeholder>
  placeholders: Array.from(document.querySelectorAll('.picker__input .uni-input-placeholder')).map(e => e.textContent.trim()),
  values: Array.from(document.querySelectorAll('.picker__input .uni-input-input')).map(e => e.value),
}))
console.log('manual probe', JSON.stringify(manualProbe))

// ── 05：保存 → 回「我的」→ 状态显示待核验 ───────────────────────────────────
// 手动填写路径：填上两个名字（H5 的输入框是 uni-input 里的 .uni-input-input）
await page.locator('.picker__input .uni-input-input').nth(0).fill('C 研究所')
await page.locator('.picker__input .uni-input-input').nth(1).fill('肿瘤组')
await page.waitForTimeout(300)
await page.screenshot({ path: `${OUT}/13b-mp-manual-filled.png`, fullPage: true })
await page.locator('uni-button', { hasText: '保存' }).first().click()
await page.waitForTimeout(2500)
await page.screenshot({ path: `${OUT}/14-mp-after-save-me-pending.png`, fullPage: true })
const afterSaveProbe = await page.evaluate(() => ({
  url: location.hash,
  text: document.querySelector('.me')?.textContent?.replace(/\s+/g, ' ').trim().slice(0, 400),
}))
console.log('after save payload', JSON.stringify(savedPayload))
console.log('after save probe', JSON.stringify(afterSaveProbe))

// ── 06：被驳回态（显示原因）────────────────────────────────────────────────
await goto('/pages/me/unit-group', 'rejected')
await page.screenshot({ path: `${OUT}/15-mp-profile-rejected.png`, fullPage: true })
const rejectedProbe = await page.evaluate(() => ({
  text: document.querySelector('.profile')?.textContent?.replace(/\s+/g, ' ').trim(),
}))
console.log('rejected probe', JSON.stringify(rejectedProbe))

// ── 07：首页 unbound 提示条 → 进单位与组别页 ────────────────────────────────
await goto('/pages/index/index', 'unbound')
await page.screenshot({ path: `${OUT}/16-mp-home-unbound-hint.png`, fullPage: true })
const homeProbe = await page.evaluate(() => ({
  note: document.querySelector('.lqg-note')?.textContent?.trim(),
}))
console.log('home probe', JSON.stringify(homeProbe))
await page.locator('.lqg-note').first().click()
await page.waitForTimeout(1200)
await page.screenshot({ path: `${OUT}/17-mp-hint-to-unit-group.png`, fullPage: true })
const hintTargetProbe = await page.evaluate(() => ({
  url: location.hash,
  hasProfile: !!document.querySelector('.profile'),
}))
console.log('hint target probe', JSON.stringify(hintTargetProbe))

await browser.close()
server.close()
console.log('done')
