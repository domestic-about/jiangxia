/**
 * SAMPLE-MP-001 的端侧截图脚本（H5 dev server + Playwright）。
 *
 * ★ 微信开发者工具在本 agent 沙箱里跑不通（CLI 要写 `~/Library/Application Support/微信开发者工具/**`
 *   → EPERM，且首次要扫码）→ 端侧证据用 H5 覆盖，完工报告里如实写「开发者工具 / 真机未覆盖」。
 * ★ **不读任何 PNG 进上下文**：本脚本只把图写到盘上，断言全部读 DOM 文本 / 计算值。
 *
 * 数据来源：
 *   - 内外部身份与 token：真的调后端 `POST /auth/login`（mock 登录）拿 token，
 *     `GET /mp/me` 用 Playwright 打桩（省一次请求，也让身份切换可控）；
 *   - 列表 / 详情：**真的走后端**（H5 dev server 的 `/lqg-api` 代理 → 8081），
 *     只有「内部看待核验样本」这一屏需要后端当时的状态，用 route 打桩出那一份详情。
 *
 * 跑法：起 `pnpm dev:h5 --port 9200`（带 VITE_MOCK_LOGIN=1）后 `node scripts/shots-sample-mp001.mjs`
 */
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright'

const WS = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid'
const OUT = `${WS}/doc/waves/reports/SAMPLE-MP-001`
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
}

const PHONE = { staff: '13800000001', extA: '13800000011' }

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

const tokens = { staff: await login('staff'), extA: await login('extA') }
console.log('tokens ok:', Object.keys(tokens).join(', '))

/** 内部详情打桩：把 1002（外部送来待核验）的 editable 摆成 false，给「待核验只读」那一屏 */
const PENDING_DETAIL = {
  id: '9000001002',
  submitNo: 'SJ90000002',
  sampleKind: 'tissue',
  submitSource: 'external',
  submitterId: '9000000111',
  verifyStatus: 'pending',
  sourceUnitName: 'A 医院',
  donorName: '测试供体乙',
  gender: 'female',
  age: '48',
  hospitalNo: 'ZY0000002',
  tissueType: '胆管组织',
  hasPathology: 'N',
  remark: null,
  receiveDate: null,
  internalNo: null,
  isFixed: null,
  processTime: null,
  hasQcSheet: null,
  hasViabilityReport: null,
  operatorName: null,
  mine: true,
  editable: false,
  handlerName: '王医生',
  updateByName: '王医生',
  updateTime: null,
  createTime: '2026-09-21 00:00:01',
}

const browser = await chromium.launch()
const context = await browser.newContext({
  viewport: { width: 390, height: 900 },
  deviceScaleFactor: 2,
})
const page = await context.newPage()

/**
 * 每次导航前把 token 写进 localStorage。
 * ★ 不能用 `addInitScript` 反复注册：它**累加**且后注册的先跑，最后一个生效，
 *   同一个 context 里换身份会拿到上一个人的 token（实测踩过，外部那一屏整页空白）。
 *   用 `page.evaluate` 在当前 origin 上覆写，再 goto 目标路由。
 */
let stubPending = false
async function setToken(value) {
  await page.goto(`${BASE}/#/pages/index/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate((t) => {
    window.localStorage.setItem('lqg_mp_token', t)
  }, value)
  // ★ 同一 context 里换身份时必须让 store 丢掉上一个人的 me：pinia 是模块级单例，
  //   只换 localStorage 里的 token 的话 `/mp/me` 不会重拉，页签与列表还是上一个人的（实测踩过）。
  await page.evaluate(() => {
    window.location.reload()
  })
  await page.waitForTimeout(300)
}

async function open(hash, token, options = {}) {
  await setTokenStub(options.stubPending === true)
  await page.goto('about:blank')
  await setToken(token)
  await page.goto(`${BASE}/#${hash}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1100)
}

/** 待核验详情打桩：只用一条常驻 route，靠开关决定是否拦（反复 page.route 会叠加处理器） */
await page.route('**/mp/int/sample/9000001002**', async (route) => {
  if (!stubPending) {
    await route.continue()
    return
  }
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, msg: '操作成功', data: PENDING_DETAIL }),
  })
})
function setTokenStub(value) {
  stubPending = value
}

async function shot(name) {
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true })
  console.log(`shot ${name}`)
}

// ── 外部 ────────────────────────────────────────────────────────────────────
await open('/pages/history/index', tokens.extA)
const extTabs = await page.locator('.lqg-sheets__item').allInnerTexts()
console.log('外部页签 =', JSON.stringify(extTabs))
await shot('01-ext-history-off')
const extRowsOff = await page.locator('.scard__code').allInnerTexts()
console.log('外部行（开关关）=', JSON.stringify(extRowsOff))

await page.locator('.his__switch .wd-switch').click()
await page.waitForTimeout(1200)
await shot('02-ext-history-on')
const extRowsOn = await page.locator('.scard__code').allInnerTexts()
console.log('外部行（开关开）=', JSON.stringify(extRowsOn))

// 外部详情：无效样本 1003（有红条 + 修改后重新提交）
await open('/pages/sample/detail-ext?id=9000001003', tokens.extA)
const invalidNote = await page.locator('.lqg-note').allInnerTexts()
const hasResubmit = await page.locator('.det__btn').count()
console.log('详情（无效）红条 =', JSON.stringify(invalidNote), ' 重提按钮数 =', hasResubmit)
await shot('03-ext-detail-invalid')

// 外部详情：有效样本 1001（无重提按钮）
await open('/pages/sample/detail-ext?id=9000001001', tokens.extA)
const validResubmit = await page.locator('.det__btn').count()
const internalNoRows = await page.getByText('内部编号').count()
console.log('详情（有效）重提按钮数 =', validResubmit, ' 内部编号行数（默认关应为 0）=', internalNoRows)
await shot('04-ext-detail-valid')

// 外部填写页（新增）
await open('/pages/sample/form?mode=new', tokens.extA)
const extGroups = await page.locator('.lqg-gl').allInnerTexts()
const extOcr = await page.locator('.lqg-ocr').count()
console.log('外部填写页分组 =', JSON.stringify(extGroups), ' 识别条 =', extOcr)
await shot('05-ext-form-new')

// ── 内部 ────────────────────────────────────────────────────────────────────
await open('/pages/history/index', tokens.staff)
const intTabs = await page.locator('.lqg-sheets__item').allInnerTexts()
const intRowsOff = await page.locator('.scard__code').allInnerTexts()
const intOwnersOff = await page.locator('.scard__owner').allInnerTexts()
const intActionsOff = await page.locator('.scard__act').allInnerTexts()
console.log('内部页签 =', JSON.stringify(intTabs))
console.log('内部行（开关关）=', JSON.stringify(intRowsOff), ' 经手人 =', JSON.stringify(intOwnersOff), ' 新增/修改 =', JSON.stringify(intActionsOff))
await shot('06-int-history-off')

await page.locator('.his__switch .wd-switch').click()
await page.waitForTimeout(1200)
const intRowsOn = await page.locator('.scard__code').allInnerTexts()
console.log('内部行（开关开）=', JSON.stringify(intRowsOn))
await shot('07-int-history-on')

// 内部填写页（新增：能看到收样信息一组）
await open('/pages/sample/form?mode=new', tokens.staff)
const intGroups = await page.locator('.lqg-gl').allInnerTexts()
console.log('内部填写页分组 =', JSON.stringify(intGroups))
await shot('08-int-form-new')

// 内部修改模式（有效样本 1001：可改）
await open('/pages/sample/form?id=9000001001&mode=edit', tokens.staff)
const editBtn = await page.locator('.form__btn').count()
const editMeta = await page.locator('.form__meta').allInnerTexts()
const editGroups = await page.locator('.lqg-gl').allInnerTexts()
console.log('修改模式（有效）保存按钮 =', editBtn, ' 顶部小字 =', JSON.stringify(editMeta), ' 分组 =', JSON.stringify(editGroups))
await shot('09-int-form-edit-valid')

// 内部修改模式（待核验 1002：只读 + 顶部提示）
await open('/pages/sample/form?id=9000001002&mode=edit', tokens.staff, { stubPending: true })
const pendingBtn = await page.locator('.form__btn').count()
const pendingNote = await page.locator('.lqg-note').allInnerTexts()
console.log('修改模式（待核验）保存按钮 =', pendingBtn, ' 顶部提示 =', JSON.stringify(pendingNote))
await shot('10-int-form-edit-pending')

// 内部只读模式（内部管理点一行：view）
await open('/pages/sample/form?id=9000001001&mode=view', tokens.staff)
const viewBtn = await page.locator('.form__btn').count()
const viewInputs = await page.locator('input').count()
console.log('只读模式（view）保存按钮 =', viewBtn, ' 输入控件数 =', viewInputs)
await shot('11-int-form-view')

// mode 缺失：按只读
await open('/pages/sample/form?id=9000001001', tokens.staff)
const noModeBtn = await page.locator('.form__btn').count()
console.log('mode 缺失 保存按钮 =', noModeBtn, '（应 0）')
await shot('12-int-form-nomode')

await context.close()
await browser.close()
console.log('done')
