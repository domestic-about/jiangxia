/**
 * AUTH-GROUP-001 · 小程序「单位与组别」页的**功能探针**（临时工具，不入库）
 *
 * 与 shots-unit-group.mjs 同一套桩，但断言的是交互行为而不是出图：
 *   1) 单位面板列出启用单位 + 末项「列表里没有，手动填写」
 *   2) 选一个单位后面板关闭、组别面板只剩该单位的组别（联动）
 *   3) 选一个组别后面板关闭
 *   4) 末项「手动填写」→ 出两个输入框，占位文案在位
 *   5) 保存 → 请求体形状正确（选列表项：只有两个 id；手动填写：只有两个名字）
 *   6) 保存成功 → 跳回「我的」且状态是「待核验」
 *   7) 被驳回的档案 → 显示驳回原因
 *
 * 跑法（cwd = code/miniapp）：node scripts/probe-unit-group.mjs
 */
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { extname, join, normalize } from 'node:path'
import { chromium } from 'playwright'

const WS = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid'
const ROOT = `${WS}/code/miniapp/dist/build/h5`
const PORT = 9211
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'application/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8', '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.ttf': 'font/ttf', '.ico': 'image/x-icon' }

const UNITS = [
  { unitId: 9000009001, unitName: 'A 医院', groups: [{ groupId: 9000009101, groupName: '肝胆外科组' }, { groupId: 9000009102, groupName: '消化内科组' }] },
  { unitId: 9000009002, unitName: 'B 大学', groups: [{ groupId: 9000009103, groupName: '类器官课题组' }] },
]
const ME = {
  verified: { userId: '9000000111', name: '王医生', phoneMasked: '138****0011', identity: 'external', ext: { unitId: 9000009001, unitName: 'A 医院', groupId: 9000009101, groupName: '肝胆外科组', unitNameInput: null, groupNameInput: null, bindStatus: 'verified', rejectReason: null } },
  pending: { userId: '9000000111', name: '王医生', phoneMasked: '138****0011', identity: 'external', ext: { unitId: 9000009001, unitName: 'A 医院', groupId: 9000009102, groupName: '消化内科组', unitNameInput: null, groupNameInput: null, bindStatus: 'pending', rejectReason: null } },
  rejected: { userId: '9000000111', name: '王医生', phoneMasked: '138****0011', identity: 'external', ext: { unitId: null, unitName: null, groupId: null, groupName: null, unitNameInput: 'C 研究所', groupNameInput: '肿瘤组', bindStatus: 'rejected', rejectReason: '组别名请写全称，如「肝胆外科组」' } },
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${PORT}`)
  let p = decodeURIComponent(url.pathname)
  if (p === '/' || p === '') p = '/index.html'
  const file = normalize(join(ROOT, p))
  if (!file.startsWith(ROOT) || !existsSync(file)) { res.writeHead(404).end('not found'); return }
  const body = await readFile(file)
  res.writeHead(200, { 'Content-Type': MIME[extname(file)] || 'application/octet-stream' })
  res.end(body)
})
await new Promise(r => server.listen(PORT, '127.0.0.1', r))

const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await context.newPage()

let meKey = 'verified'
const saved = []
await page.route('**/mp/me**', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: '操作成功', data: ME[meKey] }) }))
await page.route('**/mp/ext/units**', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: '操作成功', data: UNITS }) }))
await page.route('**/mp/ext/profile**', (route) => {
  saved.push(JSON.parse(route.request().postData() || '{}'))
  meKey = 'pending'
  return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, msg: '操作成功', data: null }) })
})
await context.addInitScript(() => { window.localStorage.setItem('lqg_mp_token', JSON.stringify('e2e-token')) })

const BASE = `http://127.0.0.1:${PORT}/#`
const fails = []
const check = (name, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${extra ? ' — ' + extra : ''}`); if (!ok) fails.push(name) }
const vis = async sel => page.locator(sel).first().isVisible().catch(() => false)

async function goto(hash, identity) {
  meKey = identity
  await page.goto('about:blank')
  await page.goto(`${BASE}${hash}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(900)
}

// ── 1 单位面板 ──────────────────────────────────────────────────────────────
await goto('/pages/me/unit-group', 'verified')
check('姓名框预填 = 王医生', (await page.locator('.profile__input .uni-input-input').first().inputValue()) === '王医生', await page.locator('.profile__input .uni-input-input').first().inputValue())
check('单位预填 = A 医院', (await page.locator('.picker__text').nth(0).innerText()).trim() === 'A 医院')
check('组别预填 = 肝胆外科组', (await page.locator('.picker__text').nth(1).innerText()).trim() === '肝胆外科组')
await page.locator('.picker__field').first().click()
await page.waitForTimeout(600)
const unitItems = await page.locator('.sheet:visible .sheet__item').allInnerTexts()
check('单位面板 = 两个启用单位 + 手动填写', JSON.stringify(unitItems.map(s => s.trim())) === JSON.stringify(['A 医院', 'B 大学', '列表里没有，手动填写']), JSON.stringify(unitItems))

// ── 2 选 B 大学 → 面板关闭、组别联动 ───────────────────────────────────────
await page.locator('.sheet__item', { hasText: 'B 大学' }).first().click()
await page.waitForTimeout(700)
check('选完单位后面板关闭', !(await vis('.sheet__title')))
check('单位显示 B 大学', (await page.locator('.picker__text').nth(0).innerText()).trim() === 'B 大学')
check('换单位后组别被清空', (await page.locator('.picker__field').nth(1).innerText()).trim().includes('请选择组别'), (await page.locator('.picker__field').nth(1).innerText()).trim())
await page.locator('.picker__field').nth(1).click()
await page.waitForTimeout(700)
// ★ 只看可见的那个面板：关掉的 wd-popup 留在 DOM 里（display:none），
//   直接 count('.sheet__item') 会把上一个面板的选项一起数进来（实测踩过）。
const groupItems = await page.locator('.sheet:visible .sheet__item').allInnerTexts()
check('组别面板只剩 B 大学的组别（联动）', JSON.stringify(groupItems.map(s => s.trim())) === JSON.stringify(['类器官课题组', '列表里没有，手动填写']), JSON.stringify(groupItems))

// ── 3 选组别 → 面板关闭 ────────────────────────────────────────────────────
await page.locator('.sheet__item', { hasText: '类器官课题组' }).first().click()
await page.waitForTimeout(700)
check('选完组别后面板关闭', !(await vis('.sheet__title')))
check('组别显示 类器官课题组', (await page.locator('.picker__field').nth(1).innerText()).trim().includes('类器官课题组'), (await page.locator('.picker__field').nth(1).innerText()).trim())

// ── 4 保存（列表项路径）：请求体只有两个 id ─────────────────────────────────
await page.locator('uni-button', { hasText: '保存' }).first().click()
await page.waitForTimeout(2200)
check('列表项路径请求体 = {realName, unitId, groupId}', JSON.stringify(saved.at(-1)) === JSON.stringify({ realName: '王医生', unitId: 9000009002, groupId: 9000009103 }), JSON.stringify(saved.at(-1)))
check('保存后跳回「我的」', page.url().includes('/pages/me/index'), page.url())
check('「我的」显示待核验', (await page.locator('.me').innerText()).includes('待核验'))

// ── 5 手动填写路径：请求体只有两个名字 ─────────────────────────────────────
await goto('/pages/me/unit-group', 'verified')
await page.locator('.picker__field').first().click()
await page.waitForTimeout(600)
await page.locator('.sheet__item', { hasText: '手动填写' }).first().click()
await page.waitForTimeout(700)
check('手动填写后出两个输入框', (await page.locator('.picker__input .uni-input-input').count()) === 2, String(await page.locator('.picker__input .uni-input-input').count()))
// ★ H5 的 uni-input 把 placeholder 渲染成一个隐藏 div（不是 <input placeholder>），
//   所以按「隐藏 div 的文案」断，而不是按 attribute（实测踩过）。
const phs = await page.locator('.picker__input .uni-input-placeholder').allInnerTexts()
check('两个输入框有占位文案', phs.length === 2 && phs.every(Boolean), JSON.stringify(phs))
await page.locator('.picker__input .uni-input-input').nth(0).fill('C 研究所')
await page.locator('.picker__input .uni-input-input').nth(1).fill('肿瘤组')
await page.waitForTimeout(300)
await page.locator('uni-button', { hasText: '保存' }).first().click()
await page.waitForTimeout(2200)
check('手动填写路径请求体 = {realName, unitNameInput, groupNameInput}', JSON.stringify(saved.at(-1)) === JSON.stringify({ realName: '王医生', unitNameInput: 'C 研究所', groupNameInput: '肿瘤组' }), JSON.stringify(saved.at(-1)))

// ── 6 被驳回：显示原因 ─────────────────────────────────────────────────────
await goto('/pages/me/unit-group', 'rejected')
const rejText = await page.locator('.profile').innerText()
check('被驳回显示徽标「已驳回」', rejText.includes('已驳回'))
check('被驳回显示原因', rejText.includes('驳回原因：组别名请写全称，如「肝胆外科组」'))
const rejInputs = await page.locator('.picker__input .uni-input-input').evaluateAll(els => els.map(e => e.value))
check('被驳回时自填名回填到输入框', JSON.stringify(rejInputs) === JSON.stringify(['C 研究所', '肿瘤组']), JSON.stringify(rejInputs))

await browser.close()
server.close()
console.log(fails.length === 0 ? '\nPROBE UNIT-GROUP ALL PASS' : `\nPROBE UNIT-GROUP FAILED: ${fails.join(', ')}`)
process.exit(fails.length === 0 ? 0 : 1)
