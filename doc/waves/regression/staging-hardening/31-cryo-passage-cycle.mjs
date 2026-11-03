// CR-20261031-24：本机后端 + 工作台 + 小程序 H5 的传代周期回归。
// 启动服务后运行：node doc/waves/regression/staging-hardening/31-cryo-passage-cycle.mjs
// 只新增一个带唯一名称的测试批次，finally 删除；不 reseed、不改已有批次。
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'
import { execFileSync } from 'node:child_process'

const require = createRequire(path.resolve('code/miniapp/package.json'))
const { chromium } = require('playwright')
const API = process.env.LQG_API_BASE || 'http://127.0.0.1:18081'
const WEB = process.env.LQG_WEB_BASE || 'http://127.0.0.1:18082'
const MP = process.env.LQG_MP_BASE || 'http://127.0.0.1:19100'
for (const url of [API, WEB, MP]) assert(['localhost', '127.0.0.1'].includes(new URL(url).hostname), '仅允许本机回归')
const OUT = process.env.LQG_TEST_OUTPUT || '/tmp/jiangxia-cryo-cycle'
fs.mkdirSync(OUT, { recursive: true })
const clientOf = file => fs.readFileSync(file, 'utf8').match(/VITE_APP_CLIENT_ID\s*=\s*['"]([^'"]+)/)[1]
const pcClient = clientOf('code/plus-ui/.env.development')
const mpClient = clientOf('code/miniapp/env/.env')
const password = process.env.LQG_TEST_PASSWORD || 'admin123'
const tag = `cycle-regression-${Date.now()}`
const checks = []
const check = (name, value) => { assert(value, name); checks.push(name); console.log(`PASS ${name}`) }
const request = async (client, token, method, route, body) => {
  const response = await fetch(API + route, { signal: AbortSignal.timeout(20000), method, headers: { 'Content-Type': 'application/json', clientid: client, ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
  return response.json()
}
const login = async (client, data) => {
  const result = await request(client, '', 'POST', '/auth/login', { clientId: client, tenantId: '000000', ...data })
  assert.equal(result.code, 200, result.msg)
  return result.data.access_token
}
const pcToken = await login(pcClient, { grantType: 'password', username: 'lqgadmin', password })
const mpToken = await login(mpClient, { grantType: 'xcx', xcxCode: 'mock:staff', phoneCode: 'mock:13800000001' })
const api = (method, route, body) => request(pcClient, pcToken, method, route, body)
const result = await api('GET', '/lqg/sample/list?verifyStatus=valid&pageSize=1')
assert.equal(result.code, 200, result.msg)
const sample = result.rows[0]
assert(sample, '需要至少一个有效样本')
let batchId
let browser
try {
  browser = await chromium.launch().catch(() => chromium.launch({ channel: 'chrome' }))
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const web = await context.newPage()
  web.setDefaultTimeout(30000)
  await web.goto(`${WEB}/login`)
  await web.locator('.login-form input').nth(0).fill('lqgadmin')
  await web.locator('.login-form input').nth(1).fill(password)
  await web.locator('.login-form button').click()
  await web.waitForURL(url => !url.pathname.includes('login'))
  await web.goto(`${WEB}/cryo?sampleId=${sample.id}&add=1`)
  const drawer = web.locator('.el-drawer:visible')
  await drawer.waitFor()
  const field = label => drawer.locator('.el-form-item').filter({ has: web.locator('.el-form-item__label').filter({ hasText: new RegExp(`^${label}$`) }) }).locator('input').first()
  await field('冻存样品').fill(tag)
  await field('冻存时间').fill('2026-10-31')
  await field('冻存时间').press('Tab')
  await field('冻存数量/支').fill('5')
  await field('代数').fill('P3')
  await drawer.getByText('是', { exact: true }).click()
  await field('传代周期').fill('7')
  await web.screenshot({ path: `${OUT}/web-create.png`, fullPage: true })
  const [created] = await Promise.all([
    web.waitForResponse(r => r.url().endsWith('/lqg/cryo/batch') && r.request().method() === 'POST'),
    drawer.getByRole('button', { name: /^保\s*存$/ }).click(),
  ])
  const createdBody = await created.json()
  assert.equal(createdBody.code, 200, createdBody.msg)
  batchId = createdBody.data
  check('工作台新建：手动填写每 7 天并保存', (await api('GET', `/lqg/cryo/batch/${batchId}`)).data.passageCycleDays === 7)
  await drawer.waitFor({ state: 'hidden' })
  await web.getByText('每 7 天', { exact: true }).first().waitFor()
  check('工作台列表显示每 7 天', true)

  const mctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
  const mp = await mctx.newPage()
  mp.setDefaultTimeout(30000)
  await mp.goto(`${MP}/#/pages/login/index`)
  await mp.waitForFunction(() => typeof uni !== 'undefined')
  await mp.evaluate(token => uni.setStorageSync('lqg_mp_token', token), mpToken)
  const openMp = async mode => {
    await mp.goto(`${MP}/#/pages/cryo/form?mode=${mode}&id=${batchId}`)
    await mp.reload()
    await mp.locator(mode === 'view' ? '.cryo__ro' : '.cryo__cycle-in input').first().waitFor()
  }
  await openMp('edit')
  const cycleInput = mp.locator('.cryo__cycle-in input')
  assert.equal(await cycleInput.inputValue(), '7')
  check('小程序编辑回填工作台填写的 7 天', true)
  await cycleInput.fill('3.5')
  await mp.getByText('保存', { exact: true }).click()
  await mp.getByText('请填写有效的传代周期天数（正整数）', { exact: true }).waitFor()
  check('小程序拦截小数，保存值仍为 7 天', (await api('GET', `/lqg/cryo/batch/${batchId}`)).data.passageCycleDays === 7)
  await cycleInput.fill('14')
  await mp.getByText('请填写有效的传代周期天数（正整数）', { exact: true }).waitFor({ state: 'hidden' })
  const cycleBox = await mp.locator('.cryo__cycle').boundingBox()
  const unitBox = await mp.locator('.cryo__cycle').getByText('天', { exact: true }).boundingBox()
  check('手机宽度下「每 / 天」均在输入行内完整显示', unitBox.x + unitBox.width <= cycleBox.x + cycleBox.width + 1)
  await mp.screenshot({ path: `${OUT}/miniapp-edit.png`, fullPage: true })
  let saved = mp.waitForResponse(r => r.url().includes('/mp/int/cryo/batch') && r.request().method() === 'PUT')
  await mp.getByText('保存', { exact: true }).click()
  assert.equal((await (await saved).json()).code, 200)
  check('小程序修改为每 14 天，后端保存成功', (await api('GET', `/lqg/cryo/batch/${batchId}`)).data.passageCycleDays === 14)
  await openMp('view')
  await mp.getByText('每 14 天', { exact: true }).waitFor()
  check('小程序只读页显示每 14 天', true)
  await openMp('edit')
  await cycleInput.fill('')
  saved = mp.waitForResponse(r => r.url().includes('/mp/int/cryo/batch') && r.request().method() === 'PUT')
  await mp.getByText('保存', { exact: true }).click()
  assert.equal((await (await saved).json()).code, 200)
  check('小程序清空后真的写成 null', (await api('GET', `/lqg/cryo/batch/${batchId}`)).data.passageCycleDays === null)

  await web.goto(`${WEB}/cryo?sampleId=${sample.id}`)
  await web.reload()
  const row = web.locator('tr.el-table__row').filter({ hasText: tag })
  await row.getByText('编辑', { exact: true }).click()
  await drawer.waitFor()
  assert.equal(await field('传代周期').inputValue(), '')
  check('工作台重新编辑回填为空', true)
  await field('传代周期').fill('0')
  await drawer.getByRole('button', { name: /^保\s*存$/ }).click()
  await drawer.getByText('请填写有效的传代周期天数（正整数）', { exact: true }).waitFor()
  check('工作台拦截零天', true)
  await field('传代周期').fill('21')
  const [updated] = await Promise.all([
    web.waitForResponse(r => r.url().endsWith('/lqg/cryo/batch') && r.request().method() === 'PUT'),
    drawer.getByRole('button', { name: /^保\s*存$/ }).click(),
  ])
  assert.equal((await updated.json()).code, 200)
  check('工作台修改为每 21 天', (await api('GET', `/lqg/cryo/batch/${batchId}`)).data.passageCycleDays === 21)

  for (const invalid of [0, -1, 3.5, 2147483648]) {
    const rejected = await api('PUT', '/lqg/cryo/batch', { id: batchId, passageCycleDays: invalid })
    assert.equal(rejected.code, 400, `非法天数 ${invalid}`)
  }
  check('API 拒绝零、负数、小数与溢出，原值保持 21 天', (await api('GET', `/lqg/cryo/batch/${batchId}`)).data.passageCycleDays === 21)
  await api('PUT', '/lqg/cryo/batch', { id: batchId, remark: '只改备注' })
  check('旧客户端省略周期字段时保留原值', (await api('GET', `/lqg/cryo/batch/${batchId}`)).data.passageCycleDays === 21)
  const mpDetail = await request(mpClient, mpToken, 'GET', `/mp/int/cryo/batch/${batchId}`)
  check('两端详情读取同一个周期值', mpDetail.data.passageCycleDays === 21)

  const exportResponse = await fetch(`${API}/lqg/cryo/batch/export?cryoName=${tag}`, { method: 'POST', headers: { clientid: pcClient, Authorization: `Bearer ${pcToken}` } })
  const xlsx = `${OUT}/cryo-export.xlsx`
  fs.writeFileSync(xlsx, Buffer.from(await exportResponse.arrayBuffer()))
  execFileSync('python3', ['-c', `import sys, zipfile, xml.etree.ElementTree as E
with zipfile.ZipFile(sys.argv[1]) as z:
 ns={'s':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
 shared=[]
 if 'xl/sharedStrings.xml' in z.namelist():
  shared=[''.join(x.itertext()) for x in E.fromstring(z.read('xl/sharedStrings.xml'))]
 rows=E.fromstring(z.read('xl/worksheets/sheet1.xml')).findall('.//s:row',ns)
 def values(row):
  out=[]
  for c in row:
   v=c.find('s:v',ns)
   out.append(shared[int(v.text)] if c.get('t')=='s' else ''.join(c.itertext()) if c.get('t')=='inlineStr' else v.text if v is not None else '')
  return out
 assert values(rows[0])[-1]=='传代周期', values(rows[0])
 assert len(values(rows[0]))==13, values(rows[0])
 assert values(rows[1])[-1]=='每 21 天', values(rows[1])
`, xlsx])
  check('实际 Excel 文件为 13 列，末列传代周期 = 每 21 天', true)
  await mp.goto(`${MP}/#/pages/ledger/index?sheet=cryo`)
  await mp.reload()
  await mp.getByText(tag, { exact: true }).first().click()
  const detailCycle = mp.locator('.cbs__field').filter({ hasText: '传代周期' }).getByText('每 21 天', { exact: true })
  await detailCycle.waitFor()
  // 等待弹层入场动画结束，避免把仍在屏幕下方的内容当作可见详情。
  await mp.waitForFunction(() => {
    const sheet = document.querySelector('.cbs')
    if (!sheet) return false
    const rect = sheet.getBoundingClientRect()
    return rect.height > 100 && rect.top >= 0 && rect.bottom <= window.innerHeight + 1
  })
  await detailCycle.scrollIntoViewIfNeeded()
  check('小程序批次详情弹层显示每 21 天', true)
  await mp.screenshot({ path: `${OUT}/miniapp-detail.png`, fullPage: false })
  console.log(`全部 ${checks.length} 项通过；截图与导出文件：${OUT}`)
} finally {
  if (browser) await browser.close()
  if (batchId) {
    const removed = await api('DELETE', `/lqg/cryo/batch/${batchId}`)
    assert.equal(removed.code, 200, '测试批次清理失败')
    console.log('已删除本轮创建的测试批次')
  }
}
