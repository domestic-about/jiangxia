// 2026-09-30 下午一轮（Kevin 真机后提的）判据：
//   工作台  ① 左侧菜单「质控文档」在「样本记录信息表」上面；lqgadmin 看不到「系统管理」
//   小程序 H5（内部身份）
//          ② 质控文档页签有「填写 / 编辑质控文档」入口，进去是样本列表
//          ③ 编辑页：三个页签；改一个字段 → 页签显示「未保存」→ 保存后是「草稿」，重进页面值还在
//          ④ 评分表：四项选完出合计；保存后后端回填的合计与页面一致
//          ⑤ 图片位：传一张图 → 多一张缩略图；删掉 → 回到原来张数
//          ⑥ 附件：传一个文件 → 附件列表多一行
//          ⑦ 完成并同步 → 已完成；撤回 → 草稿
//          ⑧ 预览：出逐页图
//   小程序 H5（外部身份）
//          ⑨ 质控文档页签没有编辑入口；深链进编辑列表只看到一句话
//   ⑩ 表单每一行都有下划线（卡片最后一行除外）—— H5 侧；真机那一半看 FieldRow 的 virtualHost（见注释）
//
// 跑法（本机三件套）：
//   LQG_WEB_BASE=http://127.0.0.1:8082 LQG_MP_BASE=http://127.0.0.1:9100 node doc/waves/regression/staging-hardening/18-mp-qc-edit.mjs
// ★ 会改 dev 库里一个样本的质控文档（写入后再撤回、删掉测试图片），不要对着测试站 / 生产跑。
import { createRequire } from 'node:module'
import path from 'node:path'
import fs from 'node:fs'
import os from 'node:os'

const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const WEB = (process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082').replace(/\/$/, '')
const MP = (process.env.LQG_MP_BASE || 'http://127.0.0.1:9100').replace(/\/$/, '')
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }
const wait = (p, ms) => p.waitForTimeout(ms)

// 测试用的小图与小文件（1×1 PNG / 一段文字）
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'lqg-18-'))
const PNG = path.join(tmp, 'probe.png')
fs.writeFileSync(PNG, Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64'))
const TXT = path.join(tmp, 'probe-附件.txt')
fs.writeFileSync(TXT, 'lqg probe attachment')

const b = await chromium.launch()

// ══ 工作台菜单 ══════════════════════════════════════════════════════════════
console.log(`工作台：${WEB}`)
{
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const w = await ctx.newPage()
  await w.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await w.waitForSelector('.login-form input', { timeout: 40000 })
  const i = w.locator('.login-form input')
  await i.nth(0).fill('lqgadmin'); await i.nth(1).fill('admin123')
  await w.click('.login-form button'); await wait(w, 4000)
  const menus = await w.evaluate(() => [...document.querySelectorAll('.sidebar-container .el-menu > .el-menu-item, .sidebar-container .el-menu > .el-sub-menu > .el-sub-menu__title, .sidebar-container .el-menu > a .el-menu-item, .sidebar-container .el-menu > div > a .el-menu-item')]
    .map(e => e.textContent.replace(/\s+/g, '').replace(/\d+$/, '')))
  const iQc = menus.findIndex(t => t.startsWith('质控文档'))
  const iSample = menus.findIndex(t => t.startsWith('样本记录信息表'))
  check('① 「质控文档」在「样本记录信息表」上面', iQc >= 0 && iSample >= 0 && iQc < iSample, menus.join(' / '))
  check('① lqgadmin 看不到「系统管理」', !menus.some(t => t.startsWith('系统管理')))
  await ctx.close()
}

// ══ 小程序 H5 ════════════════════════════════════════════════════════════════
console.log(`\n小程序 H5：${MP}`)
const ctx = await b.newContext({ viewport: { width: 390, height: 844 } })
const m = await ctx.newPage()
m.on('dialog', d => d.accept().catch(() => {}))

async function mpLogin(match) {
  await m.goto(`${MP}/#/pages/login/index`, { waitUntil: 'networkidle' }); await wait(m, 2500)
  const box = m.locator('.login__box')
  if (await box.count()) { await box.first().click(); await wait(m, 300) }
  const btns = m.locator('.login__mock-btn')
  const n = await btns.count()
  for (let k = 0; k < n; k++) {
    const t = (await btns.nth(k).innerText()).replace(/\s+/g, ' ')
    if (match(t)) {
      const me = m.waitForResponse(r => r.url().includes('/mp/me'), { timeout: 40000 }).catch(() => null)
      await btns.nth(k).click(); await me; await wait(m, 2500)
      return t
    }
  }
  return null
}
/** uni.showModal 在 H5 上是 .uni-modal：点确定 */
async function confirmModal() {
  const btn = m.locator('.uni-modal .uni-modal__btn_primary')
  await btn.waitFor({ timeout: 8000 })
  await btn.click(); await wait(m, 300)
}
async function tabStatus() {
  return m.evaluate(() => [...document.querySelectorAll('.qce__tab')].map(t => t.querySelector('.qce__tab-s')?.textContent.trim()))
}

check('前置：内部身份登录', !!(await mpLogin(t => t.includes('内部'))))

// ② 入口 + 列表
await m.goto(`${MP}/#/pages/doc/index`, { waitUntil: 'networkidle' }); await wait(m, 2500)
const entry = m.locator('.doc__edit')
check('② 质控文档页签有「填写 / 编辑质控文档」入口', await entry.count() === 1)
await entry.click(); await wait(m, 3000)
const rows = m.locator('.qcl__item')
check('② 进去是样本列表（有行）', await rows.count() > 0, `${await rows.count()} 行`)

// 挑一个「未开始」的样本来改（改完会撤回，不留已完成状态）
const pick = m.locator('.qcl__item', { has: m.locator('.qcl__progress--none') }).first()
const target = (await pick.count()) ? pick : rows.first()
const targetNo = (await target.locator('.qcl__no').textContent()).trim()
await target.click(); await wait(m, 3500)
check('③ 进编辑页，三个页签', await m.locator('.qce__tab').count() === 3, `样本 ${targetNo}`)

// ⑩ 下划线（H5 侧）
const lines = await m.evaluate(() => {
  const groups = [...document.querySelectorAll('.qce__group')].filter(g => g.offsetParent !== null)
  const first = groups[0]
  const frs = [...first.querySelectorAll(':scope > .fr, :scope > .qce__viab')]
  return frs.map(el => getComputedStyle(el).borderBottomWidth)
})
check('⑩ 卡片里除最后一行外每行都有 1px 下划线', lines.length >= 3 && lines.slice(0, -1).every(w => w === '1px') && lines[lines.length - 1] === '0px', JSON.stringify(lines))

// ③ 改字段 → 未保存 → 保存 → 草稿 → 重进仍在
const stamp = `P-PROBE-${Date.now() % 100000}`
const patient = m.locator('.qce__group').first().locator('input').first()
await patient.fill(stamp); await wait(m, 400)
check('③ 改了字段后页签显示「未保存」', (await tabStatus())[0] === '未保存', JSON.stringify(await tabStatus()))
const saveResp = m.waitForResponse(r => r.url().includes('/sample-qc') && r.request().method() === 'PUT', { timeout: 15000 }).catch(() => null)
await m.locator('.qce__btn--p').click()
const sr = await saveResp
await wait(m, 2500)
check('③ 保存请求成功', !!sr && (await sr.json()).code === 200)
check('③ 保存后页签是「草稿」', (await tabStatus())[0] === '草稿', JSON.stringify(await tabStatus()))
await m.reload({ waitUntil: 'networkidle' }); await wait(m, 3500)
const again = await m.locator('.qce__group').first().locator('input').first().inputValue()
check('③ 重进页面，刚保存的值还在', again === stamp, again)

// ⑤ 图片：传一张、再删掉
const imgCount = () => m.locator('.qce__group').nth(2).locator('.qis__img').count()
const before = await imgCount()
const chooser = m.waitForEvent('filechooser', { timeout: 10000 }).catch(() => null)
await m.locator('.qce__group').nth(2).locator('.qis__add').click()
const fc = await chooser
if (fc) {
  const bind = m.waitForResponse(r => r.url().includes('/sample-qc/image') && r.request().method() === 'POST', { timeout: 20000 }).catch(() => null)
  await fc.setFiles(PNG)
  const br = await bind
  await wait(m, 3000)
  check('⑤ 传图：绑定接口成功', !!br && (await br.json()).code === 200)
  check('⑤ 传图后多一张缩略图', await imgCount() === before + 1, `${before} → ${await imgCount()}`)
  await m.locator('.qce__group').nth(2).locator('.qis__del').last().click()
  await confirmModal(); await wait(m, 2500)
  check('⑤ 删图后回到原来张数', await imgCount() === before, `${await imgCount()}`)
} else {
  check('⑤ 点「添加图片」弹出选图', false)
}

// ⑥ 附件
const attCount = () => m.locator('.qat__row').count()
const attBefore = await attCount()
const chooser2 = m.waitForEvent('filechooser', { timeout: 10000 }).catch(() => null)
await m.locator('.qat__add').first().click()
const fc2 = await chooser2
if (fc2) {
  const bind = m.waitForResponse(r => r.url().includes('/sample-qc/attachment') && r.request().method() === 'POST', { timeout: 20000 }).catch(() => null)
  await fc2.setFiles(TXT)
  const br = await bind
  await wait(m, 3000)
  check('⑥ 附件：绑定接口成功', !!br && (await br.json()).code === 200)
  check('⑥ 附件列表多一行', await attCount() === attBefore + 1, `${attBefore} → ${await attCount()}`)
  await m.locator('.qat__del').last().click(); await confirmModal(); await wait(m, 2500)
} else {
  check('⑥ 点「添加附件」弹出选文件', false)
}

// ④ 评分
await m.locator('.qce__tab').nth(2).click(); await wait(m, 800)
const scoreRows = m.locator('.qce__score')
const nScore = await scoreRows.count()
for (let k = 0; k < nScore; k++) {
  await scoreRows.nth(k).locator('.lqg-seg__item').first().click(); await wait(m, 150)
}
const pageTotal = (await m.locator('.qce__total-v').textContent()).trim()
check('④ 四项都选了出合计', nScore === 4 && /^\d+$/.test(pageTotal), `合计 ${pageTotal}`)
await m.locator('.qce__btn--p').click(); await wait(m, 3000)
check('④ 保存后评分页签是「草稿」', (await tabStatus())[2] === '草稿', JSON.stringify(await tabStatus()))

// ⑦ 完成并同步 → 撤回（样本质控表）
await m.locator('.qce__tab').nth(0).click(); await wait(m, 600)
await m.locator('.qce__btn--s').nth(1).click(); await confirmModal(); await wait(m, 3500)
check('⑦ 完成并同步后是「已完成」', (await tabStatus())[0] === '已完成', JSON.stringify(await tabStatus()))
// ② 已完成的文档出现在「质控文档」页签后，卡片右上角有「编辑」（要等后台把内部版渲染完才会列出来）
const editUrl = m.url()
let edits = 0
for (let k = 0; k < 15 && edits === 0; k++) {
  await m.goto(`${MP}/#/pages/doc/index`, { waitUntil: 'networkidle' }); await wait(m, 2500)
  edits = await m.locator('.gcd', { hasText: targetNo }).locator('.gcd__edit').count()
}
check('② 已完成的文档卡片上有「编辑」', edits === 1, `样本 ${targetNo}`)
if (edits) {
  await m.locator('.gcd', { hasText: targetNo }).locator('.gcd__edit').click(); await wait(m, 3000)
  check('② 点卡片上的「编辑」回到这个样本的编辑页', m.url().includes('/pages/qc/edit') && (await m.locator('.qce__no').textContent()).trim() === targetNo, m.url())
} else {
  await m.goto(editUrl, { waitUntil: 'networkidle' }); await wait(m, 3000)
}
await m.locator('.qce__btn--s').nth(1).click(); await confirmModal(); await wait(m, 3000)
check('⑦ 撤回后回到「草稿」', (await tabStatus())[0] === '草稿', JSON.stringify(await tabStatus()))

// ⑧ 预览
await m.locator('.qce__btn--s').nth(0).click()
let pagesShown = 0
for (let k = 0; k < 30; k++) {
  await wait(m, 2000)
  pagesShown = await m.locator('.qcp image, .qcp img').count()
  if (pagesShown > 0 || await m.locator('.qcp .lqg-state').count() > 0 && !(await m.locator('.qcp__wait').count())) break
}
check('⑧ 预览出逐页图', pagesShown > 0, `${pagesShown} 张`)

// ⑨ 外部身份
check('前置：外部身份登录', !!(await mpLogin(t => t.includes('外部'))))
await m.goto(`${MP}/#/pages/doc/index`, { waitUntil: 'networkidle' }); await wait(m, 2500)
check('⑨ 外部：质控文档页签没有编辑入口', await m.locator('.doc__edit').count() === 0 && await m.locator('.gcd__edit').count() === 0)
let listCalled = false
m.on('request', r => { if (r.url().includes('/lqg/qc/')) listCalled = true })
await m.goto(`${MP}/#/pages/qc/list`, { waitUntil: 'networkidle' }); await wait(m, 2500)
const txt = await m.evaluate(() => document.body.innerText)
check('⑨ 外部深链进编辑列表：只一句话、不发请求', txt.includes('只给中心内部人员开放') && !listCalled)

await ctx.close()
await b.close()
fs.rmSync(tmp, { recursive: true, force: true })

console.log(`\n结果：${fails.length ? `FAIL（失败 ${fails.length} 条）` : 'PASS（失败 0 条）'}`)
if (fails.length) { console.log('失败项：' + fails.join(' / ')); process.exit(1) }
