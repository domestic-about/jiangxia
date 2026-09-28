/**
 * EMBED-MP-001 的端侧截图脚本（H5 dev server + Playwright）。
 *
 * ★ 微信开发者工具在本 agent 沙箱里跑不通（同 SYS-MP-001 / SAMPLE-MP-001 / SAMPLE-MP-002 的 WARN）
 *   → 端侧证据用 H5 覆盖，报告里如实写「开发者工具 / 真机未覆盖」。
 * ★ **不读任何 PNG 进上下文**：图只写盘，断言全部读 DOM 文本 / 元素计数 / 计算值。
 *
 * 跑法：先 `pnpm dev:h5 --port 9200`（env 里已带 VITE_MOCK_LOGIN=1），
 *      再 `node scripts/shots-embed-mp001.mjs`。脚本自己会：
 *        1) 不直连库：造数据全部走真接口（外部那条送样先判无效、再核验有效）；
 *        2) 逐屏截图 + 打 DOM 断言。
 */
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright'
import { execSync } from 'node:child_process'

const WS = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid'
const OUT = `${WS}/doc/waves/reports/EMBED-MP-001`
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

// ── 0) 干净 seed（脚本收尾还会再 reseed 一次）────────────────────────────────
execSync('bash doc/verify/reseed.sh --yes', { cwd: WS, stdio: 'ignore' })
const tokens = { staff: await login('staff'), extA: await login('extA') }
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

// ── 浏览器 ─────────────────────────────────────────────────────────────────
const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 390, height: 900 }, deviceScaleFactor: 2 })
const page = await context.newPage()

let currentMe = ME.staff
await page.route('**/mp/me', async (route) => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, msg: '操作成功', data: currentMe }),
  })
})

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

// ── 1) 内部管理 · 石蜡包埋工作表 ────────────────────────────────────────────
await open('/pages/ledger/index?sheet=embed', 'staff')
console.log('【表格·石蜡包埋】页签 =', JSON.stringify(await texts('.lqg-sheets__item')))
console.log('【表格·石蜡包埋】计数行 =', JSON.stringify((await texts('.lqg-count')).join('')))
console.log('【表格·石蜡包埋】表头 =', JSON.stringify(await texts('.lqg-ledger__th')))
console.log('【表格·石蜡包埋】筛选 chips =', JSON.stringify(await texts('.ledger-page__chip')))
const searchPh = await page.evaluate(() => document.querySelector('.ledger-page__input input')?.getAttribute('placeholder') ?? null)
console.log('【表格·石蜡包埋】搜索框占位 =', JSON.stringify(searchPh))
console.log('【表格·石蜡包埋】行数 =', await page.locator('.ledger__row').count(),
  ' 待核验行 =', await page.locator('.lqg-ledger__row--pending').count())
console.log('【表格·石蜡包埋】冻结格+第二行 =',
  JSON.stringify(await page.locator('.ledger__row .lqg-ledger__fz').allInnerTexts()))
await shot('01-ledger-embed')

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
console.log('【表格·石蜡包埋】横滑后 =', JSON.stringify(scrolled))
await page.waitForTimeout(400)
await shot('02-ledger-embed-scrolled')
const frozenStyle = await page.evaluate(() => {
  const el = document.querySelector('.lqg-ledger__fz')
  const cs = el ? getComputedStyle(el) : null
  return cs ? { position: cs.position, left: cs.left } : null
})
console.log('【表格·石蜡包埋】冻结列计算样式 =', JSON.stringify(frozenStyle))

// ── 2) 点一行 → 只读详情 → 右上角「修改」→ 修改模式 ─────────────────────────
// 先补填一次（走真接口）：2001 从没被改过 → updateTime 为 null，只读页不会出「最后修改」那一行
console.log('补填 2001 =', JSON.stringify(await api('staff', 'PUT', '/mp/int/embed',
  { id: 9000002001, agaroseEmbedTime: '2026-09-18' })))
await open('/pages/ledger/index?sheet=embed', 'staff')
await page.locator('.ledger__row', { hasText: 'T-E01-1' }).first().click()
await page.waitForTimeout(1500)
console.log('【点行→只读】URL =', page.url())
console.log('【点行→只读】提交按钮 =', await page.locator('.emb__btn').count(),
  ' 修改入口 =', await page.getByText('修改', { exact: true }).count(),
  ' 输入控件 =', await page.locator('input').count())
console.log('【点行→只读】字段 =', JSON.stringify((await texts('.wd-cell__title')).slice(0, 8)))
await shot('03-ledger-embed-view')

await page.getByText('修改', { exact: true }).first().click()
await page.waitForTimeout(600)
console.log('【只读→点修改】提交按钮 =', await page.locator('.emb__btn').count(),
  ' 输入控件 =', await page.locator('input').count(),
  ' 顶部小字 =', JSON.stringify(await texts('.emb__meta')))
await shot('04-ledger-embed-edit')

// ── 3) 内部填写页（新增，含 marker 多行）────────────────────────────────────
await open('/pages/embed/form?mode=new', 'staff')
console.log('【内部填写页】分组 =', JSON.stringify(await texts('.lqg-gl')))
console.log('【内部填写页】字段 =', JSON.stringify(await texts('.wd-input__label')))
console.log('【内部填写页】染色按钮 =', JSON.stringify(await texts('.lqg-seg__item')))
console.log('【内部填写页】marker 空态 =', JSON.stringify(await texts('.mkr__empty-t')))
await shot('05-int-embed-form-new')

await page.getByText('＋ 加一行').click()
await page.waitForTimeout(200)
await page.getByText('＋ 加一行').click()
await page.waitForTimeout(400)
console.log('【内部填写页】marker 行数 =', await page.locator('.mkr__row').count(),
  ' 行标题 =', JSON.stringify(await texts('.mkr__no')))
await shot('06-int-embed-form-markers')

// ── 4) 历史编辑记录 · 石蜡包埋页签（默认全中心 / 开关打开只看我）─────────────
await open('/pages/history/index', 'staff')
await page.locator('.lqg-sheets__item', { hasText: '石蜡包埋' }).first().click()
await page.waitForTimeout(1400)
console.log('【历史·内部】页签 =', JSON.stringify(await texts('.lqg-sheets__item')))
console.log('【历史·内部·开关关】行 =', JSON.stringify(await texts('.scard__code')))
console.log('【历史·内部·开关关】经手人 =', JSON.stringify(await texts('.scard__owner')))
console.log('【历史·内部·开关关】新增/修改 =', JSON.stringify(await texts('.scard__act')))
await shot('07-int-history-embed-off')

await page.locator('.his__switch .wd-switch').click()
await page.waitForTimeout(1400)
console.log('【历史·内部·开关开】行 =', JSON.stringify(await texts('.scard__code')))
console.log('【历史·内部·开关开】经手人 =', JSON.stringify(await texts('.scard__owner')))
await shot('08-int-history-embed-on')

// ── 5) 外部填写页：新增 / 无效重提 / 有效只读 ────────────────────────────────
await open('/pages/embed/form?mode=new', 'extA')
console.log('【外部填写页·新增】分组 =', JSON.stringify(await texts('.lqg-gl')))
console.log('【外部填写页·新增】字段 =', JSON.stringify([
  ...(await texts('.wd-cell__title')), ...(await texts('.wd-input__label')),
]))
console.log('【外部填写页·新增】提交按钮 =', await page.locator('.emb__btn').count())
await shot('09-ext-embed-form-new')

// 选样本弹层：只列本人送检过、没判无效的（送检单号 + 掩码供体姓名，不出现内部编号）
await page.locator('.wd-input, .wd-cell', { hasText: '选择样本' }).first().click()
await page.waitForTimeout(1200)
const pickerRows = await texts('.spx__item')
console.log('【外部填写页·选样本】候选 =', JSON.stringify(pickerRows))
await shot('10-ext-embed-sample-picker')

// 实验室判无效 → 外部改后重提（红条 + 可改）
console.log('判无效 =', JSON.stringify(await api('staff', 'PUT', '/lqg/embed/9000002006/verify',
  { action: 'invalid', reason: '信息不全：样本类型与送检单不符' })))
await open('/pages/embed/form?id=9000002006&mode=edit', 'extA')
console.log('【外部填写页·无效】红条 =', JSON.stringify(await texts('.lqg-note')),
  ' 提交按钮 =', await page.locator('.emb__btn').count())
await shot('11-ext-embed-form-invalid')

// 实验室核验有效（先把所挂样本核验有效）→ 外部只读 + 包埋卡片
console.log('样本核验 =', JSON.stringify(await api('staff', 'PUT', '/lqg/sample/9000001002/verify',
  { action: 'valid', receiveDate: '2026-09-17', internalNo: 'T-hli77' })))
console.log('送样核验 =', JSON.stringify(await api('staff', 'PUT', '/lqg/embed/9000002006/verify',
  { action: 'valid', paraffinBlockNo: 'T-E06-1' })))
await open('/pages/embed/form?id=9000002006&mode=edit', 'extA')
console.log('【外部填写页·有效】提交按钮 =', await page.locator('.emb__btn').count(),
  ' 包埋卡片 =', await page.locator('.ecard').count(),
  ' 卡片抬头 =', JSON.stringify(await texts('.ecard__no')))
console.log('【外部填写页·有效】字段 =', JSON.stringify(await texts('.wd-cell__title')))
await shot('12-ext-embed-form-valid')

await context.close()
await browser.close()
execSync('bash doc/verify/reseed.sh --yes', { cwd: WS, stdio: 'ignore' })
console.log('done')
