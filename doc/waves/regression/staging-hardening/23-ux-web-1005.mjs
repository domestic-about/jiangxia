// 2026-10-05 工作台对抗性 UX 测试（WEB-xx）里「明确是缺陷」那一批的机器判据（lqgadmin，1280×720 为主）：
//   WEB-01 已同步的质控文档改了再点「保存草稿」→ 先弹确认；点「继续编辑」不保存、仍是已完成
//   WEB-02 只打开质控编辑页不保存 → 列表里三份仍是未填写、最近修改为空
//   WEB-03 待核验外部用户的核验弹窗不预选；直接点确定不发请求、只提示先选
//   WEB-04 样本抽屉：没改动 ESC 直接关；有改动 ESC / ×弹确认、点遮罩不关；确认后才关
//   WEB-05 样本表横向滚到最右，「内部编号」列仍钉在左侧
//   WEB-06 列表接口 500 → 表格空白处说「没能加载出来」+ 重新加载，不说「没有符合条件」
//   WEB-07 新增样本不填来源单位点保存 → 字段下方提示来源单位，两格都标星
//   WEB-08 成功 / 警告 / 危险色是 token 值；核验状态标签按状态着色
//   WEB-09 盘点调整的标签完整显示（不截断）
//   WEB-10 先开盘点调整再开补入，一打开没有红字；取走超剩余 → 字段下方提示
//   WEB-11 1280 宽：「搜索」「重置」同一行
//   WEB-12 质控编辑页头部性别显示中文
//   WEB-15 面向用户的中文文案里没有「落库 / 后端 / 软删 / 定时任务 / 读时算 / yyyy-MM-dd」
//   WEB-16 顶栏没有语言切换；个人中心没有「第三方应用」「在线设备」
//   WEB-21 首页不再出现「已驳回」
//   WEB-23 筛选框里按回车即搜索
//   WEB-24 删除样本的确认框：标题「删除」、确认键危险色、点取消不报未处理错误
//
// 跑法（本机三件套，先 reseed）：
//   LQG_WEB_BASE=http://127.0.0.1:8082 node doc/waves/regression/staging-hardening/23-ux-web-1005.mjs
// 只读为主：WEB-02 会让 9000001009 建出三份空草稿（打开编辑页本来就会建），不保存任何改动；其余点到确认框都取消。
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'

const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const WEB = (process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082').replace(/\/$/, '')
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }
const wait = (p, ms) => p.waitForTimeout(ms)

// WEB-15：静态扫中文文案（只看字符串值，不看注释）
{
  const dir = 'code/plus-ui/src/lang/lqg'
  const bad = []
  for (const f of fs.readdirSync(dir).filter(f => f.endsWith('.zh_CN.ts'))) {
    for (const line of fs.readFileSync(path.join(dir, f), 'utf8').split('\n')) {
      const m = line.match(/^\s*\w+:\s*'([^']*)'/)
      if (m && /落库|后端|软删|定时任务|读时算|yyyy-MM-dd/.test(m[1])) bad.push(`${f}: ${m[1]}`)
    }
  }
  check('WEB-15 中文文案里没有技术词', bad.length === 0, bad.slice(0, 3).join(' | '))
}

const b = await chromium.launch()
const ctx = await b.newContext({ viewport: { width: 1280, height: 720 } })
const p = await ctx.newPage()
const pageErrors = []
p.on('pageerror', e => pageErrors.push(e.message))
await p.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
await p.waitForSelector('.login-form input', { timeout: 40000 })
await p.locator('.login-form input').nth(0).fill('lqgadmin'); await p.locator('.login-form input').nth(1).fill('admin123')
await p.click('.login-form button')
await p.waitForFunction(() => !location.pathname.includes('login'), null, { timeout: 60000 }); await wait(p, 2500)
const go = async (u, ms = 3000) => { await p.goto(`${WEB}${u}`, { waitUntil: 'domcontentloaded' }); await wait(p, ms) }
const api = u => p.evaluate(async (u) => {
  const h = { Authorization: 'Bearer ' + (localStorage.getItem('Admin-Token') || ''), clientid: 'e5cd7e4891bf95d1d19206ce24a7b32e' }
  return fetch('/dev-api' + u, { headers: h }).then(r => r.json())
}, u)
const msgbox = () => p.locator('.el-message-box').filter({ visible: true })
const drawerVisible = () => p.locator('.el-drawer').filter({ visible: true }).count().then(n => n > 0)

try {
  // WEB-16 / WEB-21 / WEB-08
  await go('/index')
  check('WEB-16 顶栏没有语言切换', (await p.locator('#lang-select').count()) === 0)
  check('WEB-21 首页不再出现「已驳回」', !(await p.evaluate(() => document.body.innerText)).includes('已驳回'))
  const colors = await p.evaluate(() => { const cs = getComputedStyle(document.documentElement); return ['success', 'warning', 'danger'].map(k => cs.getPropertyValue(`--el-color-${k}`).trim().toLowerCase()) })
  check('WEB-08 成功 / 警告 / 危险色是 token 值', JSON.stringify(colors) === JSON.stringify(['#2f7d4f', '#b7791f', '#b3362b']), JSON.stringify(colors))

  // WEB-05 / WEB-11 / WEB-08 标签 / WEB-23
  await go('/sample')
  const btns = await p.evaluate(() => [...document.querySelectorAll('.lqg-sample__filter button')].filter(e => /搜索|重置/.test(e.textContent)).map(e => Math.round(e.getBoundingClientRect().top)))
  check('WEB-11 1280 宽「搜索」「重置」同一行', btns.length === 2 && btns[0] === btns[1], JSON.stringify(btns))
  const tagClasses = await p.evaluate(() => [...document.querySelectorAll('.el-table__body .el-tag')].filter(e => /^(有效|待核验|无效)$/.test(e.textContent.trim())).map(e => `${e.textContent.trim()}:${[...e.classList].find(c => /el-tag--(success|warning|danger|primary)/.test(c))}`))
  check('WEB-08 核验状态标签按状态着色（有效=success、待核验=warning）', tagClasses.some(t => t === '有效:el-tag--success') && tagClasses.some(t => t === '待核验:el-tag--warning'), JSON.stringify([...new Set(tagClasses)]))
  await p.evaluate(() => { const w = document.querySelector('.el-table__body-wrapper .el-scrollbar__wrap'); if (w) w.scrollLeft = w.scrollWidth })
  await wait(p, 600)
  const frozen = await p.evaluate(() => {
    const th = [...document.querySelectorAll('.el-table__header th')].find(e => e.textContent.trim() === '内部编号')
    if (!th) return null
    const r = th.getBoundingClientRect(); const t = th.closest('.el-table').getBoundingClientRect()
    return { fixed: th.className.includes('fixed-column--left'), visible: r.left >= t.left - 1 && r.right <= t.right + 1 }
  })
  check('WEB-05 横向滚到最右，「内部编号」仍钉在左侧', !!frozen && frozen.fixed && frozen.visible, JSON.stringify(frozen))
  const before = await p.locator('.el-table__body-wrapper tbody tr').count()
  const donor = p.locator('.lqg-sample__filter .el-form-item', { has: p.locator('.el-form-item__label', { hasText: '供体姓名' }) }).locator('input').first()
  await donor.fill('测试供体甲'); await donor.press('Enter'); await wait(p, 2000)
  const after = await p.locator('.el-table__body-wrapper tbody tr').count()
  const afterText = await p.locator('.el-table__body-wrapper tbody').innerText()
  check('WEB-23 筛选框里按回车即搜索', after > 0 && after < before && afterText.includes('测试供体甲'), `${before} → ${after}`)

  // WEB-24 删除确认
  await go('/sample')
  const del = p.locator('.el-table__body-wrapper tbody tr').first().locator('button[title="删除"]')
  if (await del.count()) {
    const errsBefore = pageErrors.length
    await del.click(); await wait(p, 800)
    const box = await p.evaluate(() => { const m = [...document.querySelectorAll('.el-message-box')].find(e => e.offsetParent !== null); return m ? { title: m.querySelector('.el-message-box__title')?.textContent.trim(), danger: !!m.querySelector('.el-button--danger'), text: m.querySelector('.el-message-box__message')?.textContent.trim() } : null })
    check('WEB-24 删除确认：标题「删除」、确认键危险色、不出现「软删」', !!box && box.title === '删除' && box.danger && !box.text.includes('软删'), JSON.stringify(box))
    await msgbox().getByRole('button', { name: '取消' }).click(); await wait(p, 800)
    check('WEB-24 点取消不报未处理错误', pageErrors.length === errsBefore, pageErrors.slice(errsBefore).join(' | '))
  } else check('WEB-24 第一行有「删除」按钮', false)

  // WEB-04 抽屉关前确认
  await go('/sample')
  const openAdd = async () => { await p.getByRole('button', { name: /新增/ }).first().click(); await wait(p, 1200) }
  await openAdd()
  await p.keyboard.press('Escape'); await wait(p, 900)
  check('WEB-04 没改动时 ESC 直接关', !(await drawerVisible()) && (await msgbox().count()) === 0)
  await openAdd()
  const tissue = p.locator('.el-drawer .el-form-item', { has: p.locator('.el-form-item__label', { hasText: /^组织类型/ }) }).locator('input').first()
  await tissue.fill('R23-脏数据'); await wait(p, 300)
  await p.keyboard.press('Escape'); await wait(p, 900)
  check('WEB-04 有改动 ESC → 弹确认、抽屉还在', (await msgbox().count()) === 1 && (await drawerVisible()))
  await msgbox().getByRole('button', { name: '继续编辑' }).click(); await wait(p, 800)
  await p.mouse.click(60, 400); await wait(p, 900)
  check('WEB-04 点遮罩不关', await drawerVisible())
  // WEB-07 来源单位（其它必填项留空也无妨，看来源单位那一句）
  await p.locator('.el-drawer').getByRole('button', { name: '保存', exact: true }).first().click(); await wait(p, 1200)
  const errs = await p.evaluate(() => [...document.querySelectorAll('.el-drawer .el-form-item__error')].map(e => e.textContent.trim()))
  const stars = await p.evaluate(() => [...document.querySelectorAll('.el-drawer .el-form-item.is-required .el-form-item__label')].map(e => e.textContent.trim()))
  check('WEB-07 不填来源单位：字段下方提示', errs.some(e => e.includes('来源单位')), JSON.stringify(errs))
  check('WEB-07 来源单位标星', stars.some(s => s.startsWith('来源单位')), JSON.stringify(stars))
  await p.locator('.el-drawer__close-btn').filter({ visible: true }).first().click(); await wait(p, 900)
  check('WEB-04 有改动点 × → 弹确认', (await msgbox().count()) === 1)
  await msgbox().getByRole('button', { name: '不保存，关闭' }).click(); await wait(p, 900)
  check('WEB-04 确认后才关', !(await drawerVisible()))

  // WEB-06 列表接口出错
  await p.route('**/dev-api/lqg/sample/list**', r => r.fulfill({ status: 500, body: 'boom' }))
  await go('/sample', 3000)
  const empty = await p.evaluate(() => document.querySelector('.el-table__empty-text')?.innerText || '')
  check('WEB-06 接口 500：说没加载出来、有重新加载、不说没有符合条件', empty.includes('没能加载出来') && empty.includes('重新加载') && !empty.includes('没有符合条件'), empty.replace(/\s+/g, ' '))
  await p.unroute('**/dev-api/lqg/sample/list**')

  // WEB-09 / WEB-10 冻存弹窗
  await go('/cryo')
  const row0 = p.locator('.el-table__body-wrapper tbody tr').first()
  await row0.getByRole('button', { name: '盘点调整', exact: true }).click(); await wait(p, 900)
  const lbl = await p.evaluate(() => { const l = [...document.querySelectorAll('.el-dialog .el-form-item__label')].find(x => x.offsetParent !== null && x.textContent.includes('调整量')); return l ? { text: l.textContent.trim(), cut: l.scrollWidth > l.clientWidth + 1 } : null })
  check('WEB-09 盘点调整的标签完整显示', !!lbl && !lbl.cut && lbl.text.startsWith('调整量'), JSON.stringify(lbl))
  await p.locator('.el-dialog').filter({ visible: true }).getByRole('button', { name: /确\s*定|保\s*存/ }).first().click(); await wait(p, 600)
  await p.keyboard.press('Escape'); await wait(p, 600)
  await row0.getByRole('button', { name: '补入', exact: true }).click(); await wait(p, 900)
  const fresh = await p.evaluate(() => [...document.querySelectorAll('.el-dialog .el-form-item__error')].filter(e => e.offsetParent !== null).map(e => e.textContent.trim()))
  check('WEB-10 先开盘点调整再开补入：一打开没有红字', fresh.length === 0, JSON.stringify(fresh))
  await p.keyboard.press('Escape'); await wait(p, 600)
  await row0.getByRole('button', { name: '取走', exact: true }).click(); await wait(p, 900)
  const dlg = p.locator('.el-dialog').filter({ visible: true })
  await dlg.locator('.el-input-number input').fill('999'); await dlg.locator('.el-input-number input').blur()
  await dlg.getByRole('button', { name: /确\s*定|保\s*存/ }).first().click(); await wait(p, 800)
  const over = await p.evaluate(() => [...document.querySelectorAll('.el-dialog .el-form-item__error')].filter(e => e.offsetParent !== null).map(e => e.textContent.trim()))
  check('WEB-10 取走超剩余：字段下方提示', over.some(e => e.includes('不能超过当前剩余')), JSON.stringify(over))
  await p.keyboard.press('Escape'); await wait(p, 600)

  // WEB-12 / WEB-01 质控编辑（9000001001 三份都已同步）
  await go('/qc-console/qc-editor?sampleId=9000001001&from=qc-docs', 5000)
  const head = await p.evaluate(() => document.querySelector('.lqg-qc-editor')?.innerText || '')
  check('WEB-12 质控编辑页头部性别不是英文值', !/\bmale\b|\bfemale\b/.test(head))
  await p.locator('.lqg-qc-editor input[type=text]').nth(1).fill('R23-改一个字'); await wait(p, 300)
  await p.locator('.lqg-qc-editor__footer button').filter({ hasText: /^\s*保存\s*$/ }).click(); await wait(p, 800)
  const t01 = await msgbox().locator('.el-message-box__title').innerText().catch(() => '')
  check('WEB-01 已同步的文档点保存草稿 → 先确认', t01.includes('已同步给送检方'), t01)
  if (await msgbox().count()) { await msgbox().getByRole('button', { name: '继续编辑' }).click(); await wait(p, 800) }
  const st = await api('/lqg/qc/9000001001')
  check('WEB-01 取消后不保存、仍是已完成', st?.data?.sampleQc?.docStatus === 'published', st?.data?.sampleQc?.docStatus)

  // WEB-02 只打开不保存
  await go('/qc-console/qc-editor?sampleId=9000001009&from=qc-docs', 4000)
  const list = await api('/lqg/qc/list?pageNum=1&pageSize=50&keyword=T-oco01')
  const r = (list?.rows || []).find(x => String(x.sampleId) === '9000001009')
  check('WEB-02 只打开不保存：三份仍是未填写、最近修改为空', !!r && r.sampleQcStatus == null && r.organoidQcStatus == null && r.scoreStatus == null && r.lastUpdateTime == null, JSON.stringify(r && { s: r.sampleQcStatus, o: r.organoidQcStatus, sc: r.scoreStatus, t: r.lastUpdateTime, p: r.progress }))

  // D4 底部按钮与小程序同一套；D1 同步前列出空项（不拦）—— 9000001005 只有一份样本质控表草稿
  await go('/qc-console/qc-editor?sampleId=9000001005&from=qc-docs', 5000)
  const foot = () => p.evaluate(() => [...document.querySelectorAll('.lqg-qc-editor__footer button')].filter(e => e.offsetParent !== null).map(e => ({ t: e.textContent.trim(), primary: e.classList.contains('el-button--primary'), x: Math.round(e.getBoundingClientRect().left) })))
  const f0 = await foot()
  check('D4 底部：「保存」次要在左、「完成并同步」主按钮在右', f0.length === 2 && f0[0].t === '保存' && !f0[0].primary && f0[1].t === '完成并同步' && f0[1].primary && f0[1].x > f0[0].x, JSON.stringify(f0))
  await p.locator('.lqg-qc-editor input[type=text]').nth(1).fill('R23-D4'); await wait(p, 400)
  const f1 = await foot()
  check('D4 有改动：右边变「保存并同步」', f1[1]?.t === '保存并同步', JSON.stringify(f1))
  await p.locator('.lqg-qc-editor__footer .el-button--primary').click(); await wait(p, 2500)
  const msg1 = await msgbox().locator('.el-message-box__message').innerText().catch(() => '')
  check('D1 保存并同步：先保存、再在确认框里列出空着的项', msg1.includes('以下几项还空着') && msg1.includes('图片'), msg1.replace(/\s+/g, ' ').slice(0, 80))
  if (await msgbox().count()) { await msgbox().getByRole('button', { name: /取\s*消/ }).click(); await wait(p, 800) }
  const st5 = await api('/lqg/qc/9000001005')
  check('D1 取消同步：已保存、仍是草稿', st5?.data?.sampleQc?.samplingSite === 'R23-D4' && st5?.data?.sampleQc?.docStatus === 'draft', JSON.stringify({ site: st5?.data?.sampleQc?.samplingSite, st: st5?.data?.sampleQc?.docStatus }))

  // WEB-03 核验弹窗
  await go('/auth/extuser')
  const verifyCalls = []
  p.on('request', q => { if (/\/verify/.test(q.url()) && q.method() !== 'GET') verifyCalls.push(q.url()) })
  await p.locator('tr', { hasText: '吴同学' }).getByRole('button', { name: '核验' }).click(); await wait(p, 1000)
  const picked = await p.evaluate(() => { const d = [...document.querySelectorAll('.el-dialog')].find(e => e.offsetParent !== null); return d ? d.querySelectorAll('.el-radio-button.is-active, .el-radio.is-checked').length : -1 })
  check('WEB-03 待核验用户的核验弹窗不预选', picked === 0, `已选 ${picked} 项`)
  await p.locator('.el-dialog').filter({ visible: true }).locator('.el-dialog__footer .el-button--primary').click(); await wait(p, 1000)
  const warn = await p.locator('.el-message').allInnerTexts()
  check('WEB-03 直接点确定：不发请求、提示先选', verifyCalls.length === 0 && warn.some(w => w.includes('请先选择')), `${verifyCalls.length} 次请求；${warn.join(' | ')}`)
  await p.keyboard.press('Escape'); await wait(p, 500)

  // WEB-16 个人中心
  await go('/user/profile')
  const tabs = await p.locator('.el-tabs__item').allInnerTexts()
  check('WEB-16 个人中心没有「第三方应用」「在线设备」', tabs.length > 0 && !tabs.some(t => /第三方应用|在线设备/.test(t)), JSON.stringify(tabs))
}
finally {
  await b.close()
}

console.log(fails.length ? `\n✗ ${fails.length} 项没过：${fails.join(' / ')}` : '\n✓ 全部通过')
process.exit(fails.length ? 1 : 0)
