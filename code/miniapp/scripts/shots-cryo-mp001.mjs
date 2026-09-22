/**
 * CRYO-MP-001 的端侧截图脚本（H5 dev server + Playwright）。
 *
 * ★ 微信开发者工具在本 agent 沙箱里跑不通（同 SYS-MP-001 / SAMPLE-MP-001 / SAMPLE-MP-002 / EMBED-MP-001）
 *   → 端侧证据用 H5 覆盖，报告里如实写「开发者工具 / 真机未覆盖」。
 * ★ **不读任何 PNG 进上下文**：图只写盘，断言全部读 DOM 文本 / 元素计数 / 计算值。
 * ★ 造数据全部走真接口（工作台的 to-ln2 / 流水改一笔、小程序的 PUT），**不直连库**；
 *   `/mp/me` 用 Playwright 打桩（让身份切换可控），其余请求真的打到 8081。
 *
 * 跑法：先 `pnpm dev:h5 --port 9200`（env 里已带 VITE_MOCK_LOGIN=1），
 *      再 `node scripts/shots-cryo-mp001.mjs`。脚本首尾各 reseed 一次。
 */
import { mkdirSync } from 'node:fs'
import { execSync } from 'node:child_process'
import { chromium } from 'playwright'

const WS = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid'
const OUT = `${WS}/doc/waves/reports/CRYO-MP-001`
const BASE = 'http://127.0.0.1:9200'
const API = `${BASE}/lqg-api`
const CLIENT_ID = '22b2aecd0710671691ec1c07f2542b9d'
mkdirSync(OUT, { recursive: true })

const ME = {
  staff: {
    userId: '9000000101',
    name: '李工',
    phoneMasked: '138****0001',
    identity: 'internal',
    ext: null,
  },
}
const PHONE = { staff: '13800000001' }

async function login(key) {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', clientid: CLIENT_ID },
    body: JSON.stringify({
      clientId: CLIENT_ID,
      grantType: 'xcx',
      tenantId: '000000',
      xcxCode: `mock:${key}`,
      phoneCode: `mock:${PHONE[key]}`,
    }),
  })
  const body = await res.json()
  if (body.code !== 200 || !body.data?.access_token) {
    throw new Error(`${key} 登录失败：${JSON.stringify(body)}`)
  }
  return body.data.access_token
}

// ── 0) 干净 seed（脚本收尾还会再 reseed 一次）────────────────────────────────
execSync('bash doc/verify/reseed.sh --yes', { cwd: WS, stdio: 'ignore' })
const tokens = { staff: await login('staff') }
console.log('tokens ok:', Object.keys(tokens).join(', '))

async function api(key, method, path, body) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${tokens[key]}`,
      clientid: CLIENT_ID,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  try {
    return JSON.parse(text)
  }
  catch {
    return text
  }
}

// 造两条「被改过」的证据（都走真接口）：
//   ① 工作台改一笔 3003 的取走登记 → 弹层里那一行要标「已改」；
//   ② 小程序 PUT 一次 3003 的批次备注 → 修改模式顶部要出「最后修改：李工 · 时间」。
console.log('工作台改一笔 3003 的登记 =', JSON.stringify(await api('staff', 'PUT',
  '/lqg/cryo/batch/9000003003/flow/9000003102', { purpose: '药敏实验（更正）' })))
console.log('小程序改一次 3003 的备注 =', JSON.stringify(await api('staff', 'PUT',
  '/mp/int/cryo/batch', { id: 9000003003, remark: '截图脚本：改过一次' })))

// ── 浏览器 ─────────────────────────────────────────────────────────────────
const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 390, height: 900 }, deviceScaleFactor: 2 })
const page = await context.newPage()

await page.route('**/mp/me', async (route) => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, msg: '操作成功', data: ME.staff }),
  })
})

async function open(hash) {
  await page.goto('about:blank')
  await page.goto(`${BASE}/#/pages/index/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(t => window.localStorage.setItem('lqg_mp_token', t), tokens.staff)
  await page.evaluate(() => window.location.reload())
  await page.waitForTimeout(300)
  await page.goto(`${BASE}/#${hash}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1300)
}

async function shot(name) {
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true })
  console.log(`shot ${name}`)
}

const texts = sel => page.locator(sel).allInnerTexts()

// ── 1) 内部管理 · -80 冻存工作表 ────────────────────────────────────────────
await open('/pages/ledger/index?sheet=cryo')
console.log('【工作表·冻存】页签 =', JSON.stringify(await texts('.lqg-sheets__item')))
console.log('【工作表·冻存】计数行 =', JSON.stringify((await texts('.lqg-count')).join('')))
console.log('【工作表·冻存】表头 =', JSON.stringify(await texts('.lqg-ledger__th')))
console.log('【工作表·冻存】筛选 chips（数字来自 tabCounts）=', JSON.stringify(await texts('.ledger-page__chip')))
console.log('【工作表·冻存】搜索框 =', await page.locator('.ledger-page__input').count())
console.log('【工作表·冻存】行数 =', await page.locator('.ledger__row').count(),
  ' 超期行 =', await page.locator('.lqg-ledger__row--overdue').count())
console.log('【工作表·冻存】冻结格 =', JSON.stringify(await texts('.ledger__row .lqg-ledger__fz')))
const overdueCells = await page.$$eval('.lqg-ledger__row--overdue .lqg-ledger__fz', els => els.map(e => e.innerText))
console.log('【工作表·冻存】超期两行 =', JSON.stringify(overdueCells))
const subColor = await page.evaluate(() => {
  const el = document.querySelector('.lqg-ledger__row--overdue .lqg-ledger__fz-sub')
  return el ? getComputedStyle(el).color : null
})
console.log('【工作表·冻存】超期小字颜色 =', JSON.stringify(subColor))
await shot('01-ledger-cryo')

const scrolled = await page.evaluate(() => {
  const root = document.querySelector('.lqg-ledger')
  if (!root)
    return { ok: false, reason: 'no .lqg-ledger' }
  const all = [root, ...root.querySelectorAll('*')]
  const box = all.find(el => el.scrollWidth > el.clientWidth + 8)
  if (!box)
    return { ok: false, reason: 'no scrollable descendant' }
  box.scrollLeft = box.scrollWidth
  return { ok: true, scrollLeft: box.scrollLeft, scrollWidth: box.scrollWidth, clientWidth: box.clientWidth }
})
console.log('【工作表·冻存】横滑后 =', JSON.stringify(scrolled))
await page.waitForTimeout(400)
await shot('02-ledger-cryo-scrolled')
const frozenStyle = await page.evaluate(() => {
  const el = document.querySelector('.lqg-ledger__fz')
  const cs = el ? getComputedStyle(el) : null
  return cs ? { position: cs.position, left: cs.left } : null
})
console.log('【工作表·冻存】冻结列计算样式 =', JSON.stringify(frozenStyle))

// ── 2) 切到「-80 超期」页签：只 2 行，页签数字仍是整表的 7 / 2 / 2 ───────────
await page.locator('.ledger-page__chip', { hasText: '-80 超期' }).first().click()
await page.waitForTimeout(1300)
console.log('【工作表·超期页签】行数 =', await page.locator('.ledger__row').count())
console.log('【工作表·超期页签】页签数字（不随筛选收窄）=', JSON.stringify(await texts('.ledger-page__chip')))
console.log('【工作表·超期页签】冻结格 =', JSON.stringify(await texts('.ledger__row .lqg-ledger__fz')))
await shot('03-ledger-cryo-overdue-tab')

// ── 3) 点一行 → 只读的批次详情弹层（取用登记 + 右上角「修改」）───────────────
await open('/pages/ledger/index?sheet=cryo')
await page.locator('.ledger__row', { hasText: 'T-hli05-GZ-N-P7-EM2-2e5' }).first().click()
await page.waitForTimeout(1500)
console.log('【批次详情弹层】抬头 =', JSON.stringify(await texts('.cbs__name')))
console.log('【批次详情弹层】摘要 =', JSON.stringify(await texts('.cbs__meta')))
console.log('【批次详情弹层】登记行数 =', await page.locator('.cbs__item').count(),
  ' 行文本 =', JSON.stringify(await texts('.cbs__item')))
console.log('【批次详情弹层】已改标记 =', JSON.stringify(await texts('.cbs__edited')))
console.log('【批次详情弹层】「修改」入口 =', await page.locator('.cbs__edit').count(),
  ' 修改文案 =', JSON.stringify(await texts('.cbs__edit')))
console.log('【批次详情弹层】底部小字 =', JSON.stringify(await texts('.cbs__note')))
// ★ 「取走 / 补入」这些字只作为**登记类型文案**出现在行里（`.cbs__kind`），不是按钮：
//   弹层里 button 0 个、input 0 个，唯一可点的文案就是右上角那一个「修改」。
console.log('【批次详情弹层】按钮 =', await page.locator('.cbs button').count(),
  ' 输入控件 =', await page.locator('.cbs input').count(),
  ' 行内类型文案（不是按钮）=', JSON.stringify(await texts('.cbs__kind')),
  ' 可点文案 =', JSON.stringify(await texts('.cbs__edit')))
await shot('04-cryo-batch-sheet')

// ── 4) 弹层右上角「修改」→ 本条记录的填写页修改模式 ─────────────────────────
await page.locator('.cbs__edit').first().click()
await page.waitForTimeout(1600)
console.log('【详情→修改】URL =', page.url())
console.log('【详情→修改】保存按钮 =', await page.locator('.cryo__btn').count(),
  ' 顶部小字 =', JSON.stringify(await texts('.cryo__meta')))
console.log('【详情→修改】字段 =', JSON.stringify([
  ...(await texts('.wd-cell__title')), ...(await texts('.wd-input__label')),
]))
console.log('【详情→修改】输入控件数 =', await page.locator('.cryo__group input').count())
const editValues = await page.$$eval('.cryo__group input', els => els.map(e => e.value))
console.log('【详情→修改】输入值 =', JSON.stringify(editValues))
console.log('【详情→修改】暂存 -80 按钮 =', JSON.stringify(await texts('.lqg-seg__item')))
await shot('05-cryo-form-edit-from-detail')

// ── 5) 填写页新增：首页点表进来，带 `sampleId` 时用「内部编号-」预填 ──────────
await open('/pages/cryo/form?mode=new&sampleId=9000001001')
console.log('【填写页·新增】URL =', page.url())
console.log('【填写页·新增】字段 =', JSON.stringify([
  ...(await texts('.wd-cell__title')), ...(await texts('.wd-input__label')),
]))
console.log('【填写页·新增】提交按钮 =', await page.locator('.cryo__btn').count(),
  ' 暂存 -80 按钮 =', JSON.stringify(await texts('.lqg-seg__item')))
const newValues = await page.$$eval('.cryo__group input', els => els.map(e => e.value))
console.log('【填写页·新增】输入值（含「内部编号-」预填）=', JSON.stringify(newValues))
console.log('【填写页·新增】代数前缀 =', JSON.stringify(await texts('.cryo__passage-p')))
await shot('06-cryo-form-new-prefilled')

// ── 6) 历史编辑记录 · -80 冻存页签（默认全中心带经手人 / 开关打开只看我）────
await open('/pages/history/index')
await page.locator('.lqg-sheets__item', { hasText: '-80 冻存' }).first().click()
await page.waitForTimeout(1500)
console.log('【历史·冻存·开关关】页签 =', JSON.stringify(await texts('.lqg-sheets__item')))
console.log('【历史·冻存·开关关】行 =', JSON.stringify(await texts('.scard__code')))
console.log('【历史·冻存·开关关】摘要 =', JSON.stringify(await texts('.scard__sum')))
console.log('【历史·冻存·开关关】经手人 =', JSON.stringify(await texts('.scard__owner')))
console.log('【历史·冻存·开关关】新增/修改 =', JSON.stringify(await texts('.scard__act')))
await shot('07-int-history-cryo-off')

await page.locator('.his__switch .wd-switch').click()
await page.waitForTimeout(1500)
console.log('【历史·冻存·开关开】行 =', JSON.stringify(await texts('.scard__code')))
console.log('【历史·冻存·开关开】经手人 =', JSON.stringify(await texts('.scard__owner')))
await shot('08-int-history-cryo-on')

await context.close()
await browser.close()
execSync('bash doc/verify/reseed.sh --yes', { cwd: WS, stdio: 'ignore' })
console.log('done')
