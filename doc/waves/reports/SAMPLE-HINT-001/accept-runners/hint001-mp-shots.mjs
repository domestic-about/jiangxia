/**
 * SAMPLE-HINT-001 · 小程序表格页「切片染色」列的端侧证据（H5 dev server + Playwright）。
 *
 * ★ 微信开发者工具在本 agent 沙箱里跑不通（同 SYS-MP-001 / SAMPLE-MP-001 / SAMPLE-MP-002 的 WARN）
 *   → 端侧证据用 H5 覆盖，报告里如实写「开发者工具 / 真机未覆盖」。
 * ★ **不读任何 PNG 进上下文**：图只写盘，断言全部读 DOM 文本 / 元素计数。
 * ★ 断言的两侧不同源：一侧是表格页最后一列的 DOM 文本，一侧是 `/mp/int/sample/list` 的 `hint`。
 *
 * 跑法（脚本要放在**有 playwright 的包**旁边，例如 `cp` 到 `code/miniapp/` 再 `node`）：
 *   cd code/miniapp && node hint001-mp-shots.mjs
 * 前置：`pnpm dev:h5 --port 9200`（env/.env.development 里已带 VITE_MOCK_LOGIN=1）+ 后端 8081。
 */
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright'

const WS = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid'
const OUT = `${WS}/doc/waves/reports/SAMPLE-HINT-001`
const BASE = 'http://127.0.0.1:9200'
const API = `${BASE}/lqg-api`
const CLIENT_ID = '22b2aecd0710671691ec1c07f2542b9d'
mkdirSync(OUT, { recursive: true })

const ME = {
  userId: '9000000101',
  name: '李工',
  phoneMasked: '138****0001',
  identity: 'internal',
  ext: null,
}

async function login(key, phone) {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', clientid: CLIENT_ID },
    body: JSON.stringify({
      clientId: CLIENT_ID,
      grantType: 'xcx',
      tenantId: '000000',
      xcxCode: `mock:${key}`,
      phoneCode: `mock:${phone}`,
    }),
  })
  const body = await res.json()
  if (body.code !== 200 || !body.data?.access_token) {
    throw new Error(`${key} 登录失败：${JSON.stringify(body)}`)
  }
  return body.data.access_token
}

const token = await login('staff', '13800000001')
console.log('token ok')

// 接口那一侧（不同源）：直接拿 /mp/int/sample/list 的行
async function apiList(sampleKind) {
  const res = await fetch(`${API}/mp/int/sample/list?pageSize=100&sampleKind=${sampleKind}`, {
    headers: { Authorization: `Bearer ${token}`, clientid: CLIENT_ID },
  })
  const body = await res.json()
  return body.rows
}

const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 390, height: 900 }, deviceScaleFactor: 2 })
const page = await context.newPage()
await page.route('**/mp/me', async (route) => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, msg: '操作成功', data: ME }),
  })
})

async function setToken(value) {
  await page.goto(`${BASE}/#/pages/index/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(t => window.localStorage.setItem('lqg_mp_token', t), value)
  await page.evaluate(() => window.location.reload())
  await page.waitForTimeout(300)
}

async function open(hash) {
  await page.goto('about:blank')
  await setToken(token)
  await page.goto(`${BASE}/#${hash}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
}

async function shot(name) {
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true })
  console.log(`shot ${name}`)
}

/** 每行：冻结格主值 + 单元格矩阵 */
async function readTable() {
  return page.evaluate(() => {
    const headers = [...document.querySelectorAll('.lqg-ledger__th')].map(e => e.innerText.trim())
    const rows = [...document.querySelectorAll('.ledger__row')].map((tr) => {
      const fz = tr.querySelector('.lqg-ledger__fz')
      const cells = [...tr.querySelectorAll('.ledger__col')].map(e => e.innerText.trim())
      return {
        frozen: fz?.querySelector('.ledger__fz-main')?.innerText.trim() ?? fz?.innerText.trim(),
        last: cells[cells.length - 1],
        cells,
      }
    })
    return { headers, rows }
  })
}

// ── 1) 样本记录 ─────────────────────────────────────────────────────────────
await open('/pages/ledger/index?sheet=tissue')
const tissue = await readTable()
console.log('【样本记录】最后一列表头 =', JSON.stringify(tissue.headers[tissue.headers.length - 1]))
console.log('【样本记录】各行切片染色 =', JSON.stringify(tissue.rows.map(r => [r.frozen, r.last])))
await shot('04-mp-ledger-tissue-stain-hint')

// ── 2) 类器官收样 ───────────────────────────────────────────────────────────
await open('/pages/ledger/index?sheet=organoid')
const organoid = await readTable()
console.log('【类器官收样】最后一列表头 =', JSON.stringify(organoid.headers[organoid.headers.length - 1]))
console.log('【类器官收样】各行切片染色 =', JSON.stringify(organoid.rows.map(r => [r.frozen, r.last])))
await shot('05-mp-ledger-organoid-stain-hint')

// ── 3) 两侧对账（DOM 最后一列 vs 接口 hint）──────────────────────────────────
const expected = (hint) => {
  const n = hint?.blockCount ?? 0
  if (n <= 0) return '—'
  const abbr = { HE: 'HE', IF: 'IF', IHC: 'IHC', OTHER: '其他' }
  const parts = [`石蜡块 ${n}`]
  if (hint?.sectioned) parts.push('已切片')
  const stains = (hint?.stains ?? []).map(s => abbr[s] ?? s)
  if (stains.length) parts.push(stains.join(' / '))
  return parts.join(' · ')
}

const tissueApi = await apiList('tissue')
const organoidApi = await apiList('organoid')

const byInternal = (rows) => {
  const map = new Map()
  for (const r of rows) map.set(r.internalNo ?? r.submitNo, r)
  return map
}

let mismatches = []
for (const [sheet, domRows, apiRows] of [['tissue', tissue.rows, tissueApi], ['organoid', organoid.rows, organoidApi]]) {
  const api = byInternal(apiRows)
  for (const row of domRows) {
    const hit = api.get(row.frozen)
    if (!hit) { mismatches.push(`${sheet}:${row.frozen}:接口里没有这一行`); continue }
    const want = expected(hit.hint)
    if (want !== row.last) mismatches.push(`${sheet}:${row.frozen}: DOM=${row.last} != API=${want}`)
  }
}

const evidence = {
  tissueLastHeader: tissue.headers[tissue.headers.length - 1],
  organoidLastHeader: organoid.headers[organoid.headers.length - 1],
  tissueRows: tissue.rows.map(r => [r.frozen, r.last]),
  organoidRows: organoid.rows.map(r => [r.frozen, r.last]),
  apiTissue: tissueApi.map(r => [r.internalNo ?? r.submitNo, expected(r.hint)]),
  apiOrganoid: organoidApi.map(r => [r.internalNo ?? r.submitNo, expected(r.hint)]),
  mismatches,
}
console.log('两侧对账 mismatches =', JSON.stringify(mismatches))
const { writeFileSync } = await import('node:fs')
writeFileSync(`${OUT}/probe-mp-stain-hint.json`, JSON.stringify(evidence, null, 2))

await browser.close()
console.log('DONE')
