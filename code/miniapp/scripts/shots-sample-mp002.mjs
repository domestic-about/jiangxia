/**
 * SAMPLE-MP-002 的端侧截图脚本（H5 dev server + Playwright）。
 *
 * ★ 微信开发者工具在本 agent 沙箱里跑不通（同 SYS-MP-001 / SAMPLE-MP-001 的 WARN）→ 端侧证据用 H5 覆盖，
 *   报告里如实写「开发者工具 / 真机未覆盖」。
 * ★ **不读任何 PNG 进上下文**：图只写盘，断言全部读 DOM 文本 / 元素计数 / 计算值。
 *
 * 跑法：先 `pnpm dev:h5 --port 9200`（env/.env.development 里已带 VITE_MOCK_LOGIN=1），
 *      再 `node scripts/shots-sample-mp002.mjs`。脚本自己会：
 *        1) reseed（干净 seed）；
 *        2) 用真接口造两条数据：内部新增类器官 T-oco55、外部（extC）提交一条待核验类器官；
 *        3) 逐屏截图 + 打 DOM 断言。
 */
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright'

const WS = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid'
const OUT = `${WS}/doc/waves/reports/SAMPLE-MP-002`
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
  extA: {
    userId: '9000000111',
    name: '王医生',
    phoneMasked: '138****0011',
    identity: 'external',
    ext: {
      unitId: 9000009001,
      unitName: 'A 医院',
      groupId: 9000009101,
      groupName: '肝胆外科组',
      unitNameInput: null,
      groupNameInput: null,
      bindStatus: 'verified',
      rejectReason: null,
    },
  },
  extC: {
    userId: '9000000113',
    name: '赵医生',
    phoneMasked: '138****0013',
    identity: 'external',
    ext: {
      unitId: 9000009001,
      unitName: 'A 医院',
      groupId: 9000009102,
      groupName: '病理组',
      unitNameInput: null,
      groupNameInput: null,
      bindStatus: 'verified',
      rejectReason: null,
    },
  },
}

const PHONE = { staff: '13800000001', extA: '13800000011', extC: '13800000013' }

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

// ── 0) 造数据（真接口，不直连库）─────────────────────────────────────────────
const tokens = { staff: await login('staff'), extA: await login('extA'), extC: await login('extC') }
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
  let parsed = null
  try {
    parsed = JSON.parse(text)
  }
  catch {
    parsed = text
  }
  return parsed
}

const created = await api('staff', 'POST', '/mp/int/sample', {
  sampleKind: 'organoid',
  sourceUnitId: 9000009002,
  organoidType: '肝类器官',
  receiveDate: '2026-09-17',
  internalNo: 'T-oco55',
  hasViabilityReport: 'Y',
  operatorName: '李工',
})
console.log('内部新增类器官 =', JSON.stringify(created))
const extCreated = await api('extC', 'POST', '/mp/ext/organoid', {
  sourceUnitName: 'A 医院',
  organoidType: '胃类器官',
  remark: '外部送类器官',
})
console.log('外部提交类器官 =', JSON.stringify(extCreated))

const organoidList = await api('staff', 'GET', '/mp/int/sample/list?pageSize=100&sampleKind=organoid')
const rowByNo = new Map(organoidList.rows.map(r => [r.internalNo, r]))
const T_OCO55 = String(rowByNo.get('T-oco55').id)
const T_OCO01 = String(rowByNo.get('T-oco01').id)
// 外部那条还没有内部编号 → 从外部接口拿 id
const extList = await api('extC', 'GET', '/mp/ext/sample/list?pageSize=100&sampleKind=organoid&onlyMine=true')
const EXT_ORGANOID = String(extList.rows[0].id)
console.log('ids:', JSON.stringify({ T_OCO55, T_OCO01, EXT_ORGANOID }))

// ── 浏览器 ─────────────────────────────────────────────────────────────────
const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 390, height: 900 }, deviceScaleFactor: 2 })
const page = await context.newPage()

/** 当前身份：`/mp/me` 打桩（省一次真请求，也让身份切换可控） */
let currentMe = ME.staff
await page.route('**/mp/me', async (route) => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, msg: '操作成功', data: currentMe }),
  })
})

/**
 * 每次导航前把 token 写进 localStorage（不能用 addInitScript 累加，见 SAMPLE-MP-001 的坑）。
 * 换身份时必须 reload 让 pinia store 丢掉上一个人的 me。
 */
async function setToken(value) {
  await page.goto(`${BASE}/#/pages/index/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(t => window.localStorage.setItem('lqg_mp_token', t), value)
  await page.evaluate(() => window.location.reload())
  await page.waitForTimeout(300)
}

async function open(hash, key) {
  currentMe = ME[key]
  await page.goto('about:blank')
  await setToken(tokens[key])
  await page.goto(`${BASE}/#${hash}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
}

async function shot(name) {
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true })
  console.log(`shot ${name}`)
}

const texts = sel => page.locator(sel).allInnerTexts()

// ── 1) 表格页 · 样本记录 ────────────────────────────────────────────────────
await open('/pages/ledger/index?sheet=tissue', 'staff')
const sheetTabs = await texts('.lqg-sheets__item')
const countLine = (await texts('.lqg-count')).join('')
const headers = await texts('.lqg-ledger__th')
const frozenCells = await texts('.ledger__row .lqg-ledger__fz')
const pendingRows = await page.locator('.lqg-ledger__row--pending').count()
const totalRows = await page.locator('.ledger__row').count()
console.log('【表格·样本】页签 =', JSON.stringify(sheetTabs))
console.log('【表格·样本】计数行 =', JSON.stringify(countLine))
console.log('【表格·样本】表头 =', JSON.stringify(headers))
console.log('【表格·样本】行数 =', totalRows, ' 待核验行 =', pendingRows)
console.log('【表格·样本】冻结格（前 3 行）=', JSON.stringify(frozenCells.slice(0, 3)))
await shot('01-ledger-tissue')

// 横滑：把 scroll-view 里那个可横滚的容器 scrollLeft 拉到最后
const scrolled = await page.evaluate(() => {
  const root = document.querySelector('.lqg-ledger')
  if (!root)
    return { ok: false, reason: 'no .lqg-ledger' }
  const all = [root, ...root.querySelectorAll('*')]
  const box = all.find(el => el.scrollWidth > el.clientWidth + 8)
  if (!box)
    return { ok: false, reason: 'no scrollable descendant', w: root.scrollWidth }
  box.scrollLeft = box.scrollWidth
  return { ok: true, scrollLeft: box.scrollLeft, scrollWidth: box.scrollWidth, clientWidth: box.clientWidth }
})
console.log('【表格·样本】横滑后 =', JSON.stringify(scrolled))
await page.waitForTimeout(400)
await shot('02-ledger-tissue-scrolled')

// 冻结列在第一行的计算样式（sticky 生效的机器证据）
const frozenStyle = await page.evaluate(() => {
  const el = document.querySelector('.lqg-ledger__fz')
  if (!el)
    return null
  const cs = getComputedStyle(el)
  return { position: cs.position, left: cs.left }
})
console.log('【表格·样本】冻结列计算样式 =', JSON.stringify(frozenStyle))

// ── 2) 表格页 · 类器官收样 ──────────────────────────────────────────────────
await open('/pages/ledger/index?sheet=organoid', 'staff')
const orgHeaders = await texts('.lqg-ledger__th')
const orgFrozen = await texts('.ledger__row .lqg-ledger__fz')
const orgCount = (await texts('.lqg-count')).join('')
console.log('【表格·类器官】表头 =', JSON.stringify(orgHeaders))
console.log('【表格·类器官】冻结格 =', JSON.stringify(orgFrozen))
console.log('【表格·类器官】计数行 =', JSON.stringify(orgCount))
await shot('03-ledger-organoid')

// ── 3) 没注册的 sheet → 落到第一个已注册的 ──────────────────────────────────
await open('/pages/ledger/index?sheet=embed', 'staff')
const fallbackTabs = await texts('.lqg-sheets__item')
const fallbackOn = await page.locator('.lqg-sheets__item--on').allInnerTexts()
console.log('【表格·未注册 sheet】页签 =', JSON.stringify(fallbackTabs), ' 高亮 =', JSON.stringify(fallbackOn))
await shot('04-ledger-unregistered-sheet-fallback')

// ── 4) 点一行 → 只读填写页 → 右上角「修改」→ 修改模式 ────────────────────────
// 4a) 有效样本（T-hli01）：只读页**有**「修改」，点它切到修改模式
await open('/pages/ledger/index?sheet=tissue', 'staff')
await page.locator('.ledger__row', { hasText: 'T-hli01' }).first().click()
await page.waitForTimeout(1400)
const viewHash = page.url()
const viewSubmit = await page.locator('.form__btn').count()
const viewEdit = await page.getByText('修改', { exact: true }).count()
const viewInputs = await page.locator('input').count()
console.log('【点行→只读(有效)】URL =', viewHash)
console.log('【点行→只读(有效)】提交按钮 =', viewSubmit, ' 修改入口 =', viewEdit, ' 输入控件 =', viewInputs)
await shot('05-ledger-row-to-view')

const viewLabels = await texts('.wd-cell__title')
const viewValues = await texts('.fr__text')
console.log('【点行→只读(有效)】字段 =', JSON.stringify(viewLabels.slice(0, 6)), ' 值 =', JSON.stringify(viewValues.slice(0, 6)))

await page.getByText('修改', { exact: true }).first().click()
await page.waitForTimeout(500)
const editSubmit = await page.locator('.form__btn').count()
const editInputs = await page.locator('input').count()
console.log('【只读→点修改】提交按钮 =', editSubmit, ' 输入控件 =', editInputs)
await shot('06-ledger-view-to-edit')

// 4b) 待核验的外部样本（SJ…）：只读页**没有**「修改」（核验在工作台，CR-20260918-07）
await open('/pages/ledger/index?sheet=tissue', 'staff')
await page.locator('.lqg-ledger__row--pending').first().click()
await page.waitForTimeout(1400)
const pendHash = page.url()
const pendSubmit = await page.locator('.form__btn').count()
const pendEdit = await page.getByText('修改', { exact: true }).count()
const pendNote = await texts('.lqg-note')
console.log('【点行→只读(待核验)】URL =', pendHash, ' 提交按钮 =', pendSubmit, ' 修改入口 =', pendEdit, ' 提示 =', JSON.stringify(pendNote))
await shot('07-ledger-row-to-view-pending')

// ── 5) 类器官填写页：内部管理进来只读 / 点修改后可改 ─────────────────────────
await open(`/pages/organoid/form?id=${T_OCO01}&mode=view`, 'staff')
const orgViewGroups = await texts('.lqg-gl')
const orgViewEdit = await page.getByText('修改', { exact: true }).count()
const orgViewSubmit = await page.locator('.org__btn').count()
console.log('【类器官·内部只读】分组 =', JSON.stringify(orgViewGroups), ' 修改入口 =', orgViewEdit, ' 保存按钮 =', orgViewSubmit)
console.log('【类器官·内部只读】备注字段数（模板 B 没有备注，应 0）=', await page.getByText('备注', { exact: true }).count())
await shot('08-organoid-view-valid')

await page.getByText('修改', { exact: true }).first().click()
await page.waitForTimeout(500)
const orgEditSubmit = await page.locator('.org__btn').count()
const orgEditMeta = await texts('.org__meta')
console.log('【类器官·点修改】保存按钮 =', orgEditSubmit, ' 顶部小字 =', JSON.stringify(orgEditMeta))
await shot('09-organoid-edit-valid')

// 内部看外部送来的待核验：只读且**没有「修改」**
await open(`/pages/organoid/form?id=${EXT_ORGANOID}&mode=view`, 'staff')
const pendingEdit = await page.getByText('修改', { exact: true }).count()
const pendingSubmit = await page.locator('.org__btn').count()
const pendingGroups = await texts('.lqg-gl')
console.log('【类器官·内部看外部待核验】修改入口 =', pendingEdit, ' 保存按钮 =', pendingSubmit, ' 分组 =', JSON.stringify(pendingGroups))
await shot('10-organoid-view-ext-pending')

// ── 6) 类器官填写页 · 新增（内部七项 / 外部三项）────────────────────────────
await open('/pages/organoid/form?mode=new', 'staff')
const intGroups = await texts('.lqg-gl')
const intSubmit = await page.locator('.org__btn').count()
const intLabels = await texts('.wd-cell__title')
console.log('【类器官·内部新增】分组 =', JSON.stringify(intGroups), ' 提交按钮 =', intSubmit)
console.log('【类器官·内部新增】字段 =', JSON.stringify(intLabels))
await shot('11-organoid-new-internal')

await open('/pages/organoid/form?mode=new', 'extA')
const extGroups = await texts('.lqg-gl')
const extLabels = await texts('.wd-cell__title')
console.log('【类器官·外部新增】分组 =', JSON.stringify(extGroups), ' 字段 =', JSON.stringify(extLabels))
console.log('【类器官·外部新增】收样信息组数（应 0）=', await page.getByText('收样信息', { exact: true }).count())
await shot('12-organoid-new-external')

// ── 7) 历史编辑记录 · 类器官页签 ────────────────────────────────────────────
await open('/pages/history/index', 'staff')
await page.getByText('类器官收样记录', { exact: true }).click()
await page.waitForTimeout(1400)
const intHisCodes = await texts('.scard__code')
const intHisOwners = await texts('.scard__owner')
const intHisActs = await texts('.scard__act')
console.log('【历史·内部·类器官】行 =', JSON.stringify(intHisCodes))
console.log('【历史·内部·类器官】经手人 =', JSON.stringify(intHisOwners), ' 新增/修改 =', JSON.stringify(intHisActs))
await shot('13-history-organoid-internal')

await open('/pages/history/index', 'extC')
await page.getByText('类器官收样记录', { exact: true }).click()
await page.waitForTimeout(1400)
const extHisCodes = await texts('.scard__code')
console.log('【历史·外部(extC)·类器官】行 =', JSON.stringify(extHisCodes))
await shot('14-history-organoid-extC')

await open('/pages/history/index', 'extA')
await page.getByText('类器官收样记录', { exact: true }).click()
await page.waitForTimeout(1400)
const extAHisCodes = await texts('.scard__code')
const extAEmpty = await texts('.lqg-state__text')
console.log('【历史·外部(extA)·类器官】行 =', JSON.stringify(extAHisCodes), ' 空态 =', JSON.stringify(extAEmpty))
await shot('15-history-organoid-extA-empty')

await context.close()
await browser.close()
console.log('done')
