// CR-20261012-22 界面回归（本机工作台 :8082、小程序 H5 :9100、后端 :8081、库 lqg-dev-postgres）：
//   node doc/waves/regression/staging-hardening/30-operator-stat-ui-1012.mjs
// ① 工作台「操作人统计」：四张表各算各的、时间段、数字点过去列表筛出来的就是这么多条
// ② 工作台三个抽屉：操作人 / 包埋人 / 冻存人是「选人」，不再是输入框
// ③ 小程序：选人底部弹框（不能手填）、「我的 → 操作人统计」
// 自己会 reseed，建一个测试用的内部人员「刘佳」（13900002201）和几条记录；结束时删掉并 reseed 回验收基线。截图在 shots-30/。
import { execFileSync, execSync } from 'node:child_process'
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'

const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const WEB = (process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082').replace(/\/$/, '')
const MP = (process.env.LQG_MP_BASE || 'http://127.0.0.1:9100').replace(/\/$/, '')
const PG = process.env.LQG_DEV_PG_CONTAINER || 'lqg-dev-postgres'
const SHOTS = 'doc/waves/regression/staging-hardening/shots-30'
fs.mkdirSync(SHOTS, { recursive: true })
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }
const reseed = () => execSync('bash doc/verify/reseed.sh --yes', { stdio: 'ignore' })
const sql = q => execFileSync('docker', ['exec', '-i', PG, 'psql', '-U', 'lqg', '-d', 'lqg_dev', '-tA', '-c', q], { encoding: 'utf8' }).trim()
const api = (...args) => JSON.parse(execFileSync('bash', ['doc/verify/api.sh', ...args], { encoding: 'utf8' }))
const dropTestStaff = () => sql(`DELETE FROM sys_user_role WHERE user_id IN (SELECT user_id FROM sys_user WHERE phonenumber = '13900002201');
  DELETE FROM t_lqg_ext_profile WHERE user_id IN (SELECT user_id FROM sys_user WHERE phonenumber = '13900002201');
  DELETE FROM sys_user WHERE phonenumber = '13900002201'`)
const pad = n => String(n).padStart(2, '0')
const ymd = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const TODAY = ymd(new Date())
const TAG = `T-ui${Date.now().toString().slice(-6)}`

// ── 数据：刘佳做了 2 条样本、包埋 1 块、冻 1 批（5 支）；另有 1 条历史手填「袁琴琴」────────────────
dropTestStaff()
reseed()
const LIU = String(api('POST', '/lqg/auth/staff', JSON.stringify({ phone: '13900002201', name: '刘佳', roleKey: 'lqg_internal', password: 'Test@12345' })).data.userId)
const base = { sampleKind: 'tissue', sourceUnitName: 'A 医院', species: '人', tissueType: '肝组织', receiveDate: TODAY }
const s1 = String(api('POST', '/lqg/sample', JSON.stringify({ ...base, internalNo: `${TAG}-1`, operatorId: LIU })).data)
api('POST', '/lqg/sample', JSON.stringify({ ...base, internalNo: `${TAG}-2`, operatorId: LIU }))
api('POST', '/lqg/sample', JSON.stringify({ ...base, internalNo: `${TAG}-3`, operatorName: '袁琴琴' }))
api('POST', '/lqg/embed', JSON.stringify({ sampleId: s1, paraffinBlockNo: `${TAG}-E1`, sampleType: '组织', paraffinEmbedTime: TODAY, embedById: LIU, operatorId: LIU }))
api('POST', '/lqg/cryo/batch', JSON.stringify({ sampleId: s1, cryoName: `${TAG}-C1`, passage: 'P2', freezeTime: TODAY, initQty: 5, inMinus80: 'N', ln2Location: '回归罐', frozenById: LIU }))

const b = await chromium.launch().catch(() => chromium.launch({ channel: 'chrome' }))
try {
  const wait = (p, ms) => p.waitForTimeout(ms)

  // ── ① 工作台 · 操作人统计 ─────────────────────────────────────────────────
  console.log('① 工作台 · 操作人统计')
  const wctx = await b.newContext({ viewport: { width: 1360, height: 900 } })
  const p = await wctx.newPage()
  await p.goto(`${WEB}/login`, { waitUntil: 'networkidle' })
  await p.waitForSelector('.login-form input', { timeout: 40000 })
  await p.locator('.login-form input').nth(0).fill('lqgadmin'); await p.locator('.login-form input').nth(1).fill('admin123')
  await p.click('.login-form button')
  await p.waitForFunction(() => !location.pathname.includes('login'), null, { timeout: 60000 }); await wait(p, 2500)
  const menu = (await p.locator('.sidebar-container .el-menu-item, .sidebar-container .el-sub-menu__title').allInnerTexts()).map(s => s.replace(/\s+/g, ' ').trim()).filter(Boolean)
  const mi = name => menu.findIndex(t => t.startsWith(name))
  check('左侧菜单有「操作人统计」，在「冻存管理」后面、「人员与单位」前面', mi('操作人统计') > mi('冻存管理') && mi('操作人统计') < mi('人员与单位') && mi('冻存管理') >= 0, menu.join(' / '))

  await p.goto(`${WEB}/operator-stat`, { waitUntil: 'networkidle' }); await wait(p, 2500)
  const sheetCard = key => p.locator(`.lqg-opstat__sheet[data-sheet="${key}"]`)
  const titles = (await p.locator('.lqg-opstat__sheet-title').allInnerTexts()).map(s => s.trim())
  check('四张表各一块，顺序固定', JSON.stringify(titles) === JSON.stringify(['样本记录信息表', '类器官送样记录', '石蜡包埋送样记录', '-80 冻存记录']), titles.join(' / '))
  const activePreset = async () => (await p.locator('.lqg-opstat__bar .el-radio-button.is-active').innerText()).trim()
  check('默认看「本月」', (await activePreset()) === '本月', await activePreset())
  const rowsOf = async key => (await sheetCard(key).locator('.el-table__body-wrapper tbody tr').allInnerTexts()).map(s => s.replace(/\s+/g, ' ').trim())
  const totalOf = async key => (await sheetCard(key).locator('.el-table__footer-wrapper tr').innerText()).replace(/\s+/g, ' ').trim()
  let rows = await rowsOf('tissue')
  check('本月 · 样本记录信息表：刘佳 2、袁琴琴 1（标「未关联账号」），本月之前的李工不在', rows.some(r => /^刘佳 2$/.test(r)) && rows.some(r => /^袁琴琴 未关联账号 1$/.test(r)) && !rows.some(r => r.startsWith('李工')), rows.join(' | '))
  check('合计行 = 各行之和', (await totalOf('tissue')) === '合计 3', await totalOf('tissue'))
  await p.screenshot({ path: `${SHOTS}/web-1-stat-month.png`, fullPage: true })

  await p.locator('.lqg-opstat__bar .el-radio-button', { hasText: '全部' }).click(); await wait(p, 1800)
  rows = await rowsOf('tissue')
  check('全部 · 样本记录信息表：李工 5、刘佳 2、袁琴琴 1（按数量从多到少）', JSON.stringify(rows) === JSON.stringify(['李工 5', '刘佳 2', '袁琴琴 未关联账号 1']), rows.join(' | '))
  check('全部 · 时间段写「全部时间」', (await p.locator('.lqg-opstat__range-text').innerText()).trim() === '全部时间')
  rows = await rowsOf('embed')
  const embedHead = (await sheetCard('embed').locator('.el-table__header-wrapper th').allInnerTexts()).map(s => s.replace(/\s+/g, '').trim())
  check('石蜡包埋：包埋人、操作人各一列', JSON.stringify(embedHead) === JSON.stringify(['姓名', '包埋人（块）', '操作人（块）']), embedHead.join(' / '))
  check('石蜡包埋：李工 1 / 1、刘佳 1 / 1、没填的 3 / 3 排在最后', rows[rows.length - 1] === '未填 3 3' && rows.includes('李工 1 1') && rows.includes('刘佳 1 1'), rows.join(' | '))
  rows = await rowsOf('cryo')
  check('-80 冻存：批次、支数两列（李工 7 批 33 支、刘佳 1 批 5 支）', rows.includes('李工 7 33') && rows.includes('刘佳 1 5') && (await totalOf('cryo')) === '合计 8 38', `${rows.join(' | ')} / ${await totalOf('cryo')}`)
  const subtitle = (await p.locator('.lqg-opstat__subtitle').innerText()).trim()
  check('页头说清：各算各的、不加在一起、质控表不统计', /各算各的/.test(subtitle) && /质控表不统计/.test(subtitle), subtitle)
  await p.screenshot({ path: `${SHOTS}/web-2-stat-all.png`, fullPage: true })

  // 自定义：只填结束日期 = 这一天及之前
  await p.locator('.lqg-opstat__bar .el-radio-button', { hasText: '自定义' }).click(); await wait(p, 500)
  const yesterday = new Date(); yesterday.setDate(yesterday.getDate() - 1)
  const endInput = p.locator('.lqg-opstat__date input').nth(1)
  await endInput.fill(ymd(yesterday)); await endInput.press('Enter'); await wait(p, 300)
  await p.locator('.lqg-opstat__bar button', { hasText: '查询' }).click(); await wait(p, 1800)
  const rangeText = (await p.locator('.lqg-opstat__range-text').innerText()).trim()
  rows = await rowsOf('tissue')
  check('自定义只填结束日期：写「… 及之前」，今天新录的刘佳 / 袁琴琴不在里面', rangeText === `${ymd(yesterday)} 及之前` && JSON.stringify(rows) === JSON.stringify(['李工 5']), `${rangeText} · ${rows.join(' | ')}`)
  await p.screenshot({ path: `${SHOTS}/web-3-stat-custom.png`, fullPage: true })

  // 数字点过去：列表筛出来的就是这么多条
  await p.locator('.lqg-opstat__bar .el-radio-button', { hasText: '本月' }).click(); await wait(p, 1800)
  const bodyRows = () => p.locator('.el-table__body-wrapper tbody tr.el-table__row').count()
  const filterText = async label => (await p.locator('.el-form-item', { has: p.locator('.el-form-item__label', { hasText: new RegExp(`^${label}$`) }) }).first().locator('.el-select__selected-item, .el-select__placeholder').allInnerTexts()).map(s => s.trim()).filter(Boolean).join('')
  await sheetCard('tissue').locator('tr', { hasText: '刘佳' }).locator('a.lqg-opstat__num').click(); await wait(p, 3000)
  check('点样本记录信息表里刘佳的「2」→ 到样本记录信息表，筛出来正好 2 条', p.url().includes('/sample?') && (await bodyRows()) === 2, `${p.url().replace(WEB, '')} · ${await bodyRows()} 行`)
  check('筛选框里是「刘佳」，核验状态是有效', (await filterText('操作人')) === '刘佳' && /有效/.test(await filterText('核验状态')), `${await filterText('操作人')} / ${await filterText('核验状态')}`)
  await p.screenshot({ path: `${SHOTS}/web-4-sample-by-person.png` })

  await p.goto(`${WEB}/operator-stat`, { waitUntil: 'networkidle' }); await wait(p, 2500)
  await sheetCard('tissue').locator('tr', { hasText: '袁琴琴' }).locator('a.lqg-opstat__num').click(); await wait(p, 3000)
  check('点「袁琴琴（未关联账号）」的 1 → 筛出那条手填的记录，筛选框写明是未关联账号', (await bodyRows()) === 1 && (await filterText('操作人')) === '袁琴琴（未关联账号）', `${await bodyRows()} 行 · ${await filterText('操作人')}`)

  await p.goto(`${WEB}/operator-stat`, { waitUntil: 'networkidle' }); await wait(p, 2500)
  await sheetCard('embed').locator('tr', { hasText: '刘佳' }).locator('a.lqg-opstat__num').first().click(); await wait(p, 3000)
  check('点石蜡包埋「包埋人」列里刘佳的 1 → 到石蜡包埋，包埋人筛选是刘佳，1 条', p.url().includes('/embed?') && (await bodyRows()) === 1 && (await filterText('包埋人')) === '刘佳' && (await filterText('操作人')) === '全部', `${await bodyRows()} 行 · 包埋人 ${await filterText('包埋人')} / 操作人 ${await filterText('操作人')}`)
  await p.screenshot({ path: `${SHOTS}/web-5-embed-by-person.png` })

  await p.goto(`${WEB}/operator-stat`, { waitUntil: 'networkidle' }); await wait(p, 2500)
  await sheetCard('cryo').locator('tr', { hasText: '刘佳' }).locator('a.lqg-opstat__num').last().click(); await wait(p, 3000)
  check('点 -80 冻存里刘佳的支数 → 到冻存管理，冻存人筛选是刘佳，1 批', p.url().includes('/cryo?') && (await bodyRows()) === 1 && (await filterText('冻存人')) === '刘佳', `${await bodyRows()} 行 · ${await filterText('冻存人')}`)

  // ── ② 工作台 · 三个抽屉里是「选人」────────────────────────────────────────
  console.log('② 工作台 · 抽屉里的操作人 / 包埋人 / 冻存人')
  const formItem = label => p.locator('.el-drawer .el-form-item', { has: p.locator('.el-form-item__label', { hasText: new RegExp(`^${label}$`) }) }).first()
  const selected = async label => (await formItem(label).locator('.el-select__selected-item, .el-select__placeholder').allInnerTexts()).map(s => s.trim()).filter(Boolean).join('')
  // 只数看得见的选项（被搜索过滤掉的选项还在 DOM 里，只是隐藏了）
  const options = async () => (await p.locator('.el-select-dropdown:visible .el-select-dropdown__item:visible').allInnerTexts()).map(s => s.trim())
  /** 关抽屉：点右上角 ×（ESC 第一下只会收起还开着的下拉）；改过东西会先问一句 → 点「不保存，关闭」 */
  const closeDrawer = async () => {
    await p.locator('.el-drawer:visible .el-drawer__close-btn').first().click(); await wait(p, 600)
    const confirm = p.locator('.el-message-box:visible .el-message-box__btns .el-button--primary')
    if (await confirm.count()) { await confirm.first().click() }
    await p.locator('.el-drawer:visible').waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
    await wait(p, 500)
  }

  await p.goto(`${WEB}/sample`, { waitUntil: 'networkidle' }); await wait(p, 2500)
  await p.locator('button', { hasText: /新增/ }).first().click(); await wait(p, 1500)
  check('新增样本：操作人是下拉（不是输入框），默认当前登录人', (await formItem('操作人').locator('.el-select').count()) === 1 && (await formItem('操作人').locator('input.el-input__inner').count()) === 0 && (await selected('操作人')) === '测试管理员', await selected('操作人'))
  await formItem('操作人').locator('.el-select').click(); await wait(p, 600)
  let opts = await options()
  check('下拉里是内部人员名单：刘佳 / 李工 / 测试管理员', JSON.stringify([...opts].sort()) === JSON.stringify(['刘佳', '李工', '测试管理员'].sort()), opts.join(' / '))
  await formItem('操作人').locator('input').fill('袁琴琴'); await wait(p, 500)
  opts = await options()
  check('★ 输入名单外的名字：没有可选的（不能手填出一个新名字）', opts.length === 0, opts.join(' / ') || '（空）')
  await formItem('操作人').locator('input').fill('刘'); await wait(p, 500)
  await p.locator('.el-select-dropdown:visible .el-select-dropdown__item', { hasText: '刘佳' }).click(); await wait(p, 400)
  check('选了刘佳', (await selected('操作人')) === '刘佳', await selected('操作人'))
  await p.screenshot({ path: `${SHOTS}/web-6-sample-drawer.png` })
  await closeDrawer()

  // 历史手填的记录：看得见是谁、带标注
  const legacyRow = p.locator('.el-table__body-wrapper tbody tr', { hasText: `${TAG}-3` }).first()
  await legacyRow.locator('button').first().click(); await wait(p, 2000)
  check('打开历史手填「袁琴琴」的记录：操作人显示「袁琴琴（未关联账号）」', (await selected('操作人')) === '袁琴琴（未关联账号）', await selected('操作人'))
  await closeDrawer()

  await p.goto(`${WEB}/embed`, { waitUntil: 'networkidle' }); await wait(p, 2500)
  await p.locator('button', { hasText: /新增/ }).first().click(); await wait(p, 1500)
  check('新增石蜡包埋：包埋人、操作人都是下拉；操作人默认当前登录人，包埋人空着', (await formItem('包埋人').locator('.el-select').count()) === 1 && (await formItem('操作人').locator('.el-select').count()) === 1 && (await selected('操作人')) === '测试管理员' && (await selected('包埋人')) === '请选择', `包埋人 ${await selected('包埋人')} / 操作人 ${await selected('操作人')}`)
  await p.screenshot({ path: `${SHOTS}/web-7-embed-drawer.png` })
  await closeDrawer()

  await p.goto(`${WEB}/cryo`, { waitUntil: 'networkidle' }); await wait(p, 2500)
  await p.locator('button', { hasText: /新增/ }).first().click(); await wait(p, 1500)
  check('新增冻存批次：冻存人是下拉，默认当前登录人', (await formItem('冻存人').locator('.el-select').count()) === 1 && (await selected('冻存人')) === '测试管理员', await selected('冻存人'))
  await closeDrawer()
  await wctx.close()

  // ── ③ 小程序（内部 · 李工）──────────────────────────────────────────────
  console.log('③ 小程序 · 选人弹框与操作人统计')
  async function session(match, viewport = { width: 375, height: 760 }) {
    const ctx = await b.newContext({ viewport })
    const m = await ctx.newPage()
    await m.goto(`${MP}/#/pages/login/index`, { waitUntil: 'networkidle' }); await wait(m, 2500)
    const box = m.locator('.login__box'); if (await box.count()) { await box.first().click(); await wait(m, 300) }
    const btns = m.locator('.login__mock-btn')
    for (let k = 0; k < await btns.count(); k++) {
      if (match.test((await btns.nth(k).innerText()).replace(/\s+/g, ' '))) {
        const me = m.waitForResponse(r => r.url().includes('/mp/me'), { timeout: 40000 }).catch(() => null)
        await btns.nth(k).click(); await me; await wait(m, 2500)
        break
      }
    }
    const open = async (url) => { await m.goto(`${MP}/#/${url}`, { waitUntil: 'networkidle' }); await m.reload({ waitUntil: 'networkidle' }); await wait(m, 3000) }
    return { ctx, m, open }
  }
  const { ctx, m, open } = await session(/李工/)
  const cell = label => m.locator('.wd-cell', { has: m.locator('.wd-cell__title', { hasText: new RegExp(`^\\s*${label}`) }) }).first()
  const sheetRows = async () => (await m.locator('.sfs__row:visible').allInnerTexts()).map(s => s.replace(/\s+/g, ' ').trim())

  await open('pages/sample/form?mode=new')
  check('新增样本记录：操作人是一格「点开选」，默认李工（当前登录人）', (await cell('操作人').count()) === 1 && (await cell('操作人').innerText()).includes('李工'), (await cell('操作人').innerText().catch(() => '')).replace(/\s+/g, ' '))
  check('操作人不再是输入框', (await m.locator('.wd-input', { has: m.locator('.wd-input__label', { hasText: '操作人' }) }).count()) === 0)
  await cell('操作人').click(); await wait(m, 1000)
  let sr = await sheetRows()
  check('面板：内部人员名单一行一个，现在选的李工打勾', JSON.stringify([...sr].sort()) === JSON.stringify(['刘佳', '李工', '测试管理员'].sort()) && JSON.stringify((await m.locator('.sfs__row--on:visible').allInnerTexts()).map(s => s.trim())) === JSON.stringify(['李工']), sr.join(' / '))
  await m.screenshot({ path: `${SHOTS}/mp-1-staff-sheet.png` })
  const sheetInput = () => m.locator('.sfs__search input')
  await sheetInput().fill('袁琴琴'); await wait(m, 500)
  sr = await sheetRows()
  const emptyText = (await m.locator('.sfs__empty:visible').innerText().catch(() => '')).trim()
  check('★ 搜名单外的「袁琴琴」：一行都没有、也没有「使用…」，底下写着去哪加人', sr.length === 0 && emptyText === '名单里没有「袁琴琴」' && (await m.locator('.sfs__hint:visible').innerText()).includes('内部人员授权'), `${sr.join(' / ') || '（无行）'} · ${emptyText}`)
  await m.screenshot({ path: `${SHOTS}/mp-2-staff-nomatch.png` })
  await sheetInput().fill('刘'); await wait(m, 500)
  await m.locator('.sfs__row:visible', { hasText: '刘佳' }).click(); await wait(m, 800)
  check('搜「刘」点刘佳：格子显示刘佳、面板收起', (await cell('操作人').innerText()).includes('刘佳') && !(await m.locator('.sfs__row').first().isVisible().catch(() => false)))

  // 改一条已有的记录（李工 → 刘佳 录的那条改回李工）并保存 → 请求体带账号 id，库里两列一起变
  const editId = sql(`select id from t_lqg_sample where internal_no = '${TAG}-2'`)
  await open(`pages/sample/form?id=${editId}&mode=edit`)
  check('打开刘佳录的那条：操作人格子里是刘佳', (await cell('操作人').innerText()).includes('刘佳'))
  await cell('操作人').click(); await wait(m, 1000)
  await m.locator('.sfs__row:visible', { hasText: '李工' }).click(); await wait(m, 800)
  const saved = m.waitForResponse(r => r.url().includes('/mp/int/sample') && r.request().method() === 'PUT', { timeout: 20000 }).catch(() => null)
  await m.locator('.form__btn').click(); const resp = await saved; await wait(m, 1500)
  const posted = resp ? resp.request().postDataJSON() : null
  const body = resp ? await resp.json().catch(() => null) : null
  check('改成李工并保存：请求体里是李工的账号 id + 姓名', body?.code === 200 && String(posted?.operatorId) === '9000000101' && posted?.operatorName === '李工', JSON.stringify({ code: body?.code, msg: body?.msg, operatorId: posted?.operatorId, operatorName: posted?.operatorName }))
  check('库里这条样本的操作人两列一起变成李工', sql(`select operator_id || '/' || operator_name from t_lqg_sample where id = ${editId}`) === '9000000101/李工')

  await open('pages/cryo/form?mode=new')
  check('新增冻存：冻存人是「点开选」，默认李工', (await cell('冻存人').count()) === 1 && (await cell('冻存人').innerText()).includes('李工') && (await m.locator('.wd-input', { has: m.locator('.wd-input__label', { hasText: '冻存人' }) }).count()) === 0)
  await open('pages/embed/form?mode=new')
  check('新增石蜡包埋：包埋人、操作人都是「点开选」', (await cell('包埋人').count()) === 1 && (await cell('操作人').count()) === 1 && (await cell('操作人').innerText()).includes('李工'))
  await cell('包埋人').click(); await wait(m, 1000)
  check('点包埋人：面板标题是「包埋人」，可以清空（选填）时才出「清空」', (await m.locator('.sfs__t:visible').innerText()).trim() === '包埋人' && (await m.locator('.sfs__clear:visible').count()) === 0)
  await m.locator('.sfs__row:visible', { hasText: '刘佳' }).click(); await wait(m, 800)
  await cell('包埋人').click(); await wait(m, 1000)
  check('选了之后再打开：出现「清空」', (await m.locator('.sfs__clear:visible').count()) === 1)
  await m.locator('.sfs__clear:visible').click(); await wait(m, 800)
  check('点「清空」：包埋人回到没填', !(await cell('包埋人').innerText()).includes('刘佳'))

  // 历史手填的记录：看得见是谁
  const legacyId = sql(`select id from t_lqg_sample where internal_no = '${TAG}-3'`)
  await open(`pages/sample/form?id=${legacyId}&mode=edit`)
  check('打开历史手填「袁琴琴」的记录：格子里还是袁琴琴', (await cell('操作人').innerText()).includes('袁琴琴'))
  await cell('操作人').click(); await wait(m, 1000)
  sr = await sheetRows()
  check('面板里它排在最后、标「未关联账号」、打勾', sr[sr.length - 1] === '袁琴琴（未关联账号）' && (await m.locator('.sfs__row--on:visible').innerText()).trim() === '袁琴琴（未关联账号）', sr.join(' / '))
  await m.screenshot({ path: `${SHOTS}/mp-3-staff-legacy.png` })

  // 我的 → 操作人统计
  await open('pages/me/index')
  const statRow = m.locator('text=操作人统计').first()
  check('「我的」内部管理下面有「操作人统计」', await statRow.isVisible().catch(() => false))
  await m.screenshot({ path: `${SHOTS}/mp-4-me.png`, fullPage: true })
  await statRow.click(); await wait(m, 3000)
  check('点进去是操作人统计页', m.url().includes('pages/stat/operator'), m.url().replace(MP, ''))
  const chipOn = async () => (await m.locator('.ops__chip.lqg-seg__item--on').innerText()).trim()
  check('默认「本月」，写着起止日期', (await chipOn()) === '本月' && /^\d{4}-\d{2}-01 至 \d{4}-\d{2}-\d{2}$/.test((await m.locator('.ops__range').innerText()).trim()), `${await chipOn()} · ${(await m.locator('.ops__range').innerText()).trim()}`)
  await m.locator('.ops__chip', { hasText: /^全部$/ }).click(); await wait(m, 2000)
  const opsRows = async i => (await m.locator('.ops__sheet').nth(i).locator('.ops__row').allInnerTexts()).map(s => s.replace(/\s+/g, ' ').trim())
  const sheetTitles = (await m.locator('.ops__sheet-t').allInnerTexts()).map(s => s.trim())
  check('四张表各一块', JSON.stringify(sheetTitles) === JSON.stringify(['样本记录信息表', '类器官送样记录', '石蜡包埋送样记录', '-80 冻存记录']), sheetTitles.join(' / '))
  let or = await opsRows(0)
  check('全部 · 样本记录信息表：我（李工）置顶并标「我」6 条（含刚改过来的 1 条），刘佳 1，袁琴琴标「未关联账号」，最后是合计', or[1] === '李工 我 6' && or.includes('刘佳 1') && or.includes('袁琴琴 未关联账号 1') && or[or.length - 1] === '合计 8', or.join(' | '))
  or = await opsRows(3)
  check('-80 冻存：批次、支数两列', /批次（批）/.test(or[0]) && /支数（支）/.test(or[0]) && or.includes('李工 我 7 33') && or[or.length - 1] === '合计 8 38', or.join(' | '))
  const wide = await m.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)
  check('375 宽下没有横向溢出', !wide)
  await m.screenshot({ path: `${SHOTS}/mp-5-stat-all.png`, fullPage: true })
  await m.locator('.ops__chip', { hasText: '自定义' }).click(); await wait(m, 800)
  check('自定义：开始、结束各一行，都写「不限」；时间段是「全部时间」', (await m.locator('.ops__date').count()) === 2 && (await m.locator('.ops__date-v--none').count()) === 2 && (await m.locator('.ops__range').innerText()).trim() === '全部时间')
  await m.screenshot({ path: `${SHOTS}/mp-6-stat-custom.png` })
  await ctx.close()

  // 合作单位：没有入口、深链进来也看不到数
  const ext = await session(/王医生/)
  await ext.open('pages/me/index')
  check('合作单位的「我的」里没有「操作人统计」', (await ext.m.locator('text=操作人统计').count()) === 0)
  let statCalls = 0
  ext.m.on('request', r => { if (r.url().includes('/stat/operator') && !r.url().includes('/pages/')) statCalls++ })
  await ext.open('pages/stat/operator')
  const extText = (await ext.m.locator('body').innerText()).replace(/\s+/g, ' ')
  check('合作单位深链进统计页：只有一句「只给中心内部人员开放」，一个统计请求都不发', extText.includes('操作人统计只给中心内部人员开放') && (await ext.m.locator('.ops__sheet').count()) === 0 && statCalls === 0, `请求 ${statCalls} 次`)
  await ext.ctx.close()
}
finally {
  await b.close()
  dropTestStaff()
  reseed()
}
console.log(fails.length ? `\n结果：FAIL —— ${fails.join('；')}` : '\n全部通过')
process.exit(fails.length ? 1 : 0)
