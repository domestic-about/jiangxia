/**
 * D1 / L2 端侧断言（r2 独立片，自建）—— 网页工作台 plus-ui + 真后端（8081）。
 *
 * 覆盖 phase-plan D1 qa_scope L2 第 2 条：
 *   lqgadmin 登录 → 「人员与单位」三个页面各走一遍（截图）：
 *     ① 内部人员授权：列表 + 「按手机号授权」弹窗（已登录过小程序 → 原地升级；没账号 → 预建）
 *        + 改角色 / 重置密码 / 撤销二次确认三个行操作
 *     ② 来源单位与组别：左单位右组别主从（点单位 → 右栏联动）+ 新增单位 / 新增组别弹窗
 *     ③ 外部用户：列表 + 核验弹窗（自填档案 → 必须二选一「新建 / 归并到已有」；驳回必填原因；
 *        已核验的人入口叫「改归组」）
 *
 * ★ 本轮独立复验 r1 的 S1（dev server 下点菜单 → .app-main 只剩 `<!---->` 白屏）：用**自建**的
 *   菜单导航遍历（4 个菜单来回点 + 回首页 + 再进一次），每一步都断 .app-main 里有该页的真实
 *   内容，而不只是「长度 > 200」。dev(8082) 与 build 产物（静态服务）两条路都要跑。
 *
 * 跑法：
 *   LQG_WEB_BASE=http://127.0.0.1:8082 LQG_SHOT_TAG=web-dev  node doc/waves/regression/D1/L2-r2-web-plusui-indep.mjs
 *   LQG_WEB_BASE=http://127.0.0.1:8083 LQG_SHOT_TAG=web-build node doc/waves/regression/D1/L2-r2-web-plusui-indep.mjs
 *
 * 前置：后端 8081（带 --api-decrypt.enabled=false）+ reseed。
 */
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const BASE = process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082'
const TAG = process.env.LQG_SHOT_TAG || 'web-dev'
const SHOTS = path.join(HERE, 'shots-L23-r2', TAG)
mkdirSync(SHOTS, { recursive: true })

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

const bodyText = () => page.evaluate(() => document.body.innerText)
const appMainHtml = () => page.evaluate(() => document.querySelector('.app-main')?.innerHTML ?? '')
const appMainText = () => page.evaluate(() => document.querySelector('.app-main')?.innerText ?? '')
// ★ Element Plus 关闭弹窗后 .el-dialog 仍留在 DOM（v-show），所以一律只看**可见**的那一个：
//   否则 querySelector('.el-dialog') / waitForSelector('.el-dialog') 会拿到上一个已关闭的弹窗，
//   读出过期文案甚至永远等不到 visible（本脚本第一版就踩了这条）。
const dialogText = () => page.locator('.el-dialog:visible').first().innerText().catch(() => '')

/** 关掉当前可见弹窗：优先点「取 消」（确定路径），必要时再 Esc。dropdown 开着时 Esc 只关下拉。 */
async function closeDialog() {
  for (let i = 0; i < 3; i++) {
    const vis = page.locator('.el-dialog:visible')
    if ((await vis.count()) === 0) return true
    const cancel = vis.first().locator('button:has-text("取 消")')
    if ((await cancel.count()) > 0) {
      await cancel.first().click({ timeout: 5000 }).catch(() => {})
    } else {
      await page.keyboard.press('Escape').catch(() => {})
    }
    await page.waitForTimeout(600)
  }
  return (await page.locator('.el-dialog:visible').count()) === 0
}

// ★★ L2-r2 收尾 harness 加固（只动等待/选择器，不动断言、不动产品代码）：
//    dev(8082) 上前一版会在两处抛异常 —— ① 等 `.el-dialog` 15s 超时；② 第二次点
//    `.el-table__row:has-text("吴同学") button:has-text("核验")` 30s click 超时。独立探针查明：
//    这两处**都不是产品问题**，是「前一步残留的 overlay / 下拉 / 弹窗过渡把下一次点击吞掉，
//    或用固定 sleep 读到了上一状态的文案」。所以加两个只关于等待的 helper：
//      · waitDialogText 轮询到目标文案出现（固定 sleep 会读到上一次的手机号提示）
//      · clickRowAndWait 点行内按钮→等弹窗，失败就重试，状态脏了整页 hardOpen 重来
async function waitDialogText(re, timeout = 12000) {
  const end = Date.now() + timeout
  let last = ''
  while (Date.now() < end) {
    last = await dialogText()
    if (re.test(last)) return last
    await page.waitForTimeout(400)
  }
  return last
}

/** 点表格行内按钮并等目标弹窗；点空/被吞就重试，连续失败则整页重载清状态 */
async function clickRowAndWait(rowSel, btnSel, dialogSel, pagePath, waitText) {
  for (let attempt = 0; attempt < 4; attempt++) {
    if (attempt > 0) {
      await closeDialog()
      await page.keyboard.press('Escape').catch(() => {})
      await page.waitForTimeout(700)
      if (attempt >= 2) await hardOpen(pagePath, waitText)
    }
    const btn = page.locator(`${rowSel} ${btnSel}`).first()
    const clicked = await btn.click({ timeout: 8000 }).then(() => true).catch(() => false)
    const opened = clicked
      && await page.waitForSelector(dialogSel, { timeout: 10000 }).then(() => true).catch(() => false)
    if (opened) return true
    console.log(`[harness] clickRowAndWait 重试 attempt=${attempt} clicked=${clicked} row=${rowSel} btn=${btnSel}`)
  }
  return false
}

/** 左侧菜单：折叠状态下子项 rect 全是 0×0，先展开「人员与单位」目录再点子项 */
async function openMenu(itemText) {
  const box = await page.locator(`.el-menu-item:has-text("${itemText}")`).first().boundingBox().catch(() => null)
  if (!(box && box.width > 0 && box.height > 0)) {
    await page.click('.el-sub-menu__title:has-text("人员与单位")')
    await page.waitForTimeout(900)
  }
  await page.click(`.el-menu-item:has-text("${itemText}")`)
  await page.waitForTimeout(1800)
}

/** 硬刷新到目标路由（页面内容细节走这条路，导航本身另测） */
async function hardOpen(pathname, waitText) {
  await page.goto(`${BASE}${pathname}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.el-menu', { timeout: 30000 })
  if (waitText) await page.waitForSelector(`text=${waitText}`, { timeout: 30000 }).catch(() => {})
  await page.waitForTimeout(1500)
}

const pageErrors = []
const consoleErrors = []

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1560, height: 950 }, deviceScaleFactor: 1 })
const page = await ctx.newPage()
page.on('pageerror', e => { pageErrors.push(e.message); console.log('[pageerror]', e.message) })
page.on('console', m => { if (m.type() === 'error') { consoleErrors.push(m.text()); console.log('[console.error]', m.text().slice(0, 200)) } })

try {
  // ── 登录 ────────────────────────────────────────────────────────────────
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => localStorage.clear()).catch(() => {})
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login-form input', { timeout: 30000 })
  await page.screenshot({ path: path.join(SHOTS, '10-web-login.png') })
  const inputs = page.locator('.login-form input')
  await inputs.nth(0).fill('lqgadmin')
  await inputs.nth(1).fill('admin123')
  await page.click('.login-form button')
  await page.waitForSelector('.el-menu', { timeout: 30000 })
  await page.waitForTimeout(2200)
  await page.screenshot({ path: path.join(SHOTS, '11-web-home.png') })
  const menuItems = await page.$$eval('.el-menu-item, .el-sub-menu__title', els => els.map(e => e.innerText.trim()))
  check('WEB-01 lqgadmin 账号密码登录成功（带侧边栏的壳）', menuItems.length > 0, JSON.stringify(menuItems).slice(0, 240))

  // ── 落地的首页必须真的渲染（上游 index.vue 模板顶层带注释 = dev 下的 Fragment 根）──
  const homeHtml = await appMainHtml()
  const homeText = await appMainText()
  check('WEB-02 登录落地页 /index 渲染出首页内容（不是 <!---->）',
    /工作台首页建设中/.test(homeText) && homeHtml.replace(/<!---->/g, '').length > 100,
    `url=${page.url()} len=${homeHtml.length} text=${JSON.stringify(homeText.replace(/\n/g, '/').slice(0, 80))}`)

  // ══ ★ 独立复验 r1 S1：菜单导航必须渲染出目标页的真实内容 ══════════════════
  // 每一步都断「该页独有的一段文字」出现在 .app-main 里 —— 只断长度会被别的残留内容蒙混。
  const NAV_CASES = [
    { menu: '内部人员授权', url: /auth\/staff/, marker: '按手机号授权', extra: '测试管理员' },
    { menu: '来源单位与组别', url: /auth\/unit/, marker: '来源单位', extra: 'A 医院' },
    { menu: '外部用户', url: /auth\/extuser/, marker: '核验状态', extra: '王医生' },
    // 回头再点一次第一个：若 isLeaving 卡死，这里必白屏
    { menu: '内部人员授权', url: /auth\/staff/, marker: '按手机号授权', extra: '李工' },
    { menu: '首页', url: /\/index$/, marker: '工作台首页建设中', extra: '' },
    { menu: '外部用户', url: /auth\/extuser/, marker: '核验状态', extra: '吴同学' },
  ]
  let navBad = 0
  for (let i = 0; i < NAV_CASES.length; i++) {
    const c = NAV_CASES[i]
    await openMenu(c.menu)
    const html = await appMainHtml()
    const text = await appMainText()
    const stripped = html.replace(/<!---->/g, '').trim()
    const ok = c.url.test(page.url()) && stripped.length > 200 && text.includes(c.marker) && (!c.extra || text.includes(c.extra))
    if (!ok) navBad++
    await page.screenshot({ path: path.join(SHOTS, `11b-nav-${i + 1}-${c.menu}.png`) })
    check(`WEB-03.${i + 1} 点菜单「${c.menu}」→ 目标页真实渲染（含「${c.marker}」${c.extra ? `与「${c.extra}」` : ''}）`,
      ok, `url=${page.url()} len=${html.length} stripped=${stripped.length} text=${JSON.stringify(text.replace(/\n/g, '/').slice(0, 90))}`)
  }
  check('WEB-04 菜单导航无白屏步（r1 S1 独立复验）', navBad === 0, `bad=${navBad}/${NAV_CASES.length}`)

  // ══ ① 内部人员授权 ═════════════════════════════════════════════════════
  await hardOpen('/auth/staff', '按手机号授权')
  check('WEB-05 ① 内部人员授权页 URL', /auth\/staff/.test(page.url()), page.url())
  const staffText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, '12-web-staff-list.png'), fullPage: true })
  // seed：内部账号恰好两个（测试管理员 lqgadmin / 李工 staff）；外部账号不许出现
  check('WEB-06 ① 内部人员列表 = seed 的两个内部账号（测试管理员 / 李工），不含外部账号',
    staffText.includes('测试管理员') && staffText.includes('李工')
    && !staffText.includes('王医生') && !staffText.includes('陈医生') && !staffText.includes('赵医生'),
    JSON.stringify(staffText.split('\n').filter(l => /13\d{9}|管理员|李工|医生/.test(l)).slice(0, 10)))
  await page.click('button:has-text("按手机号授权")')
  await page.waitForSelector('.el-dialog:visible', { timeout: 20000 })
  await page.waitForTimeout(500)
  await page.screenshot({ path: path.join(SHOTS, '13-web-staff-grant-empty.png') })
  const dlgInputs = page.locator('.el-dialog:visible input')
  await dlgInputs.nth(0).fill('13800000011') // extA：已用小程序登录过
  await dlgInputs.nth(0).blur()
  // ★ 轮询到「升级」文案，别用固定 sleep：dev 下固定 sleep 会读到上一状态的文案（r2 收尾加固）
  const dlg1 = await waitDialogText(/该手机号已登录过小程序，将把原账号升级为内部人员/, 12000)
  await page.screenshot({ path: path.join(SHOTS, '14-web-staff-grant-upgrade.png'), fullPage: true })
  check('WEB-07 ① 已登录过的手机号 → 弹窗提示「原地升级为内部人员」',
    dlg1.includes('该手机号已登录过小程序，将把原账号升级为内部人员'),
    JSON.stringify(dlg1.split('\n').filter(l => l.includes('升级') || l.includes('预建'))))
  check('WEB-08 ① 授权弹窗字段齐全（手机号 / 姓名 / 角色 / 工作台初始密码）',
    ['手机号', '姓名', '角色', '工作台初始密码'].every(s => dlg1.includes(s)),
    JSON.stringify(dlg1.split('\n').filter(l => l.trim()).slice(0, 12)))
  await dlgInputs.nth(0).fill('13800000091') // 库里没有的手机号
  await dlgInputs.nth(0).blur()
  const dlg2 = await waitDialogText(/该手机号还没有账号，将预建一个内部账号/, 12000)
  check('WEB-09 ① 库里没有的手机号 → 弹窗提示「将预建一个内部账号」',
    dlg2.includes('该手机号还没有账号，将预建一个内部账号'),
    JSON.stringify(dlg2.split('\n').filter(l => l.includes('升级') || l.includes('预建'))))
  // 关法按 UI:admin.auth.staff / 实现注释的口径：**点蒙层可关**
  await page.locator('.el-overlay:visible').first().click({ position: { x: 8, y: 8 } })
  await page.waitForTimeout(800)
  check('WEB-10 ① 授权弹窗点蒙层可关（不在弹窗区域点一下就关掉）', (await page.locator('.el-dialog:visible').count()) === 0,
    `visible dialogs=${await page.locator('.el-dialog:visible').count()}`)

  // 行操作：李工那行三个图标按钮（0 改角色 / 1 重置密码 / 2 撤销）
  const liRow = page.locator('.el-table__row:has-text("李工")').first()
  const rowBtns = liRow.locator('button')
  const rowBtnCount = await rowBtns.count()
  check('WEB-11 ① 李工行有三个行操作按钮（改角色 / 重置密码 / 撤销）', rowBtnCount === 3, `buttons=${rowBtnCount}`)
  if (rowBtnCount >= 3) {
    await clickRowAndWait('.el-table__row:has-text("李工")', 'button >> nth=0', '.el-dialog:visible', '/auth/staff', '按手机号授权')
    await page.waitForTimeout(700)
    await page.screenshot({ path: path.join(SHOTS, '15-web-staff-role-dialog.png') })
    const roleDlg = await dialogText()
    check('WEB-12 ① 改角色弹窗：标题「修改角色」+ 姓名 + 角色下拉',
      roleDlg.includes('修改角色') && roleDlg.includes('李工') && roleDlg.includes('角色'),
      JSON.stringify(roleDlg.split('\n').filter(l => l.trim()).slice(0, 8)))
    await closeDialog()
    await clickRowAndWait('.el-table__row:has-text("李工")', 'button >> nth=1', '.el-dialog:visible', '/auth/staff', '按手机号授权')
    await page.waitForTimeout(700)
    await page.screenshot({ path: path.join(SHOTS, '16-web-staff-pwd-dialog.png') })
    const pwdDlg = await dialogText()
    check('WEB-13 ① 重置密码弹窗：标题「重置工作台密码」+ 新密码输入框',
      pwdDlg.includes('重置工作台密码') && pwdDlg.includes('新密码'),
      JSON.stringify(pwdDlg.split('\n').filter(l => l.trim()).slice(0, 8)))
    await closeDialog()
    await clickRowAndWait('.el-table__row:has-text("李工")', 'button >> nth=2', '.el-message-box:visible', '/auth/staff', '按手机号授权')
    await page.waitForTimeout(600)
    await page.screenshot({ path: path.join(SHOTS, '17-web-staff-revoke-confirm.png') })
    const mbText = await page.evaluate(() => document.querySelector('.el-message-box')?.innerText ?? '')
    check('WEB-14 ① 撤销授权有二次确认，且文案点名「李工」并说明降回外部 / 立即退出',
      mbText.includes('李工') && mbText.includes('外部') && mbText.includes('立即退出'),
      JSON.stringify(mbText.replace(/\n/g, '/').slice(0, 160)))
    // 取消，不做任何写操作
    await page.locator('.el-message-box:visible button:has-text("取消")').first().click()
    await page.waitForTimeout(700)
    check('WEB-15 ① 取消撤销后列表不变（李工仍在，两条内部账号）',
      (await bodyText()).includes('李工') && (await page.locator('.el-table__row').count()) === 2,
      `rows=${await page.locator('.el-table__row').count()}`)
  }

  // ══ ② 来源单位与组别（主从） ════════════════════════════════════════════
  await hardOpen('/auth/unit', '来源单位')
  check('WEB-16 ② 来源单位与组别页 URL', /auth\/unit/.test(page.url()), page.url())
  await page.screenshot({ path: path.join(SHOTS, '18-web-unit-initial.png'), fullPage: true })
  /** 第 n 个 el-table 的行 → 单元格文本（0 = 左栏单位表，1 = 右栏组别表） */
  const tableRows = n => page.locator('.el-table').nth(n).locator('.el-table__row')
    .evaluateAll(rows => rows.map(r => Array.from(r.querySelectorAll('td')).map(td => td.innerText.trim())))
  const unitText0 = await bodyText()
  const unitRows0 = await tableRows(0)
  // 期望值 = seed 的机器事实（doc/verify/seed/03-unit-group.sql / db.py 查过）：
  //   A 医院 组别数 2 启用 ｜ B 大学 1 启用 ｜ 已停用单位 0 停用
  const expectUnits = [
    ['A 医院', '2', '启用'],
    ['B 大学', '1', '启用'],
    ['已停用单位', '0', '停用'],
  ]
  const unitMatch = expectUnits.every(([name, cnt, st]) => unitRows0.some(r => r[0] === name && r[1] === cnt && r.some(c => c === st)))
  check('WEB-17 ② 左栏三个 seed 单位 + 组别数 + 状态（A 医院 2 启用 / B 大学 1 启用 / 已停用单位 0 停用）',
    ['A 医院', 'B 大学', '已停用单位'].every(s => unitText0.includes(s)) && unitMatch
    && unitText0.includes('来源单位') && unitText0.includes('该单位的组别'),
    `rows=${JSON.stringify(unitRows0)}`)
  await page.click('.el-table__row:has-text("A 医院")')
  await page.waitForTimeout(1800)
  await page.screenshot({ path: path.join(SHOTS, '19-web-unit-master-detail.png'), fullPage: true })
  const unitText1 = await bodyText()
  check('WEB-18 ② 点「A 医院」→ 右栏联动出它自己的两个组别（肝胆外科组 / 消化内科组），不含 B 大学的组',
    unitText1.includes('肝胆外科组') && unitText1.includes('消化内科组') && !unitText1.includes('类器官课题组'),
    JSON.stringify(unitText1.split('\n').filter(l => l.includes('组')).slice(0, 12)))
  // UI:admin.auth.unit：显示每个组别下已核验的外部人数（seed：肝胆外科组 2 / 消化内科组 1）
  const groupRows0 = await tableRows(1)
  check('WEB-18b ② 右栏每个组别显示「已核验人数」（肝胆外科组 = 2、消化内科组 = 1）',
    groupRows0.some(r => r[0] === '肝胆外科组' && r[1] === '2') && groupRows0.some(r => r[0] === '消化内科组' && r[1] === '1'),
    JSON.stringify(groupRows0))
  // 再看 B 大学 → 右栏必须换成类器官课题组（证明「联动」不是只渲染了一次）
  await page.click('.el-table__row:has-text("B 大学")')
  await page.waitForTimeout(1800)
  const unitText2 = await bodyText()
  check('WEB-19 ② 换点「B 大学」→ 右栏换成类器官课题组（主从真的联动）',
    unitText2.includes('类器官课题组') && !unitText2.includes('肝胆外科组'),
    JSON.stringify(unitText2.split('\n').filter(l => l.includes('组')).slice(0, 12)))
  await page.screenshot({ path: path.join(SHOTS, '20-web-unit-b-univ.png'), fullPage: true })
  // 新增单位 / 新增组别弹窗各开一次（不提交）
  await page.click('button:has-text("新增单位")')
  await page.waitForSelector('.el-dialog:visible', { timeout: 15000 })
  await page.waitForTimeout(600)
  await page.screenshot({ path: path.join(SHOTS, '21-web-unit-add-dialog.png') })
  const addUnitDlg = await dialogText()
  // 「全库唯一」在 placeholder 上（innerText 里没有），要读 placeholder
  const addUnitPh = await page.locator('.el-dialog:visible input').first().getAttribute('placeholder').catch(() => '')
  check('WEB-22 ②「新增单位」弹窗：标题 + 单位名称（placeholder「全库唯一」）+ 备注',
    addUnitDlg.includes('新增来源单位') && addUnitDlg.includes('单位名称') && addUnitDlg.includes('备注')
    && String(addUnitPh).includes('全库唯一'),
    `dialog=${JSON.stringify(addUnitDlg.split('\n').filter(l => l.trim()).slice(0, 8))} placeholder="${addUnitPh}"`)
  await closeDialog()
  await page.click('button:has-text("新增组别")')
  await page.waitForSelector('.el-dialog:visible', { timeout: 15000 })
  await page.waitForTimeout(600)
  await page.screenshot({ path: path.join(SHOTS, '22-web-unit-add-group-dialog.png') })
  const addGrpDlg = await dialogText()
  const addGrpPh = await page.locator('.el-dialog:visible input').first().getAttribute('placeholder').catch(() => '')
  check('WEB-23 ②「新增组别」弹窗：所属单位 = 当前选中的 B 大学 + placeholder「同一单位内不可重名」',
    addGrpDlg.includes('新增组别') && addGrpDlg.includes('所属单位') && addGrpDlg.includes('B 大学')
    && String(addGrpPh).includes('同一单位内不可重名'),
    `dialog=${JSON.stringify(addGrpDlg.split('\n').filter(l => l.trim()).slice(0, 8))} placeholder="${addGrpPh}"`)
  await closeDialog()

  // ══ ③ 外部用户核验 ═════════════════════════════════════════════════════
  await hardOpen('/auth/extuser', '外部用户')
  check('WEB-24 ③ 外部用户页 URL', /auth\/extuser/.test(page.url()), page.url())
  const extText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, '23-web-extuser-list.png'), fullPage: true })
  check('WEB-25 ③ 列表含六个 seed 外部用户，且待核验的 周医生 / 吴同学 都在',
    ['王医生', '陈医生', '赵医生', '孙老师', '周医生', '吴同学'].every(s => extText.includes(s)),
    JSON.stringify(extText.split('\n').filter(l => /医生|同学|老师/.test(l)).slice(0, 14)))
  check('WEB-26 ③ 自填档案（吴同学）带「自填」标记与未填写列', extText.includes('自填'),
    JSON.stringify(extText.split('\n').filter(l => l.includes('自填')).slice(0, 4)))
  // 吴同学：自填 → 核验弹窗必须二选一
  await clickRowAndWait('.el-table__row:has-text("吴同学")', 'button:has-text("核验")', '.el-dialog:visible', '/auth/extuser', '核验状态')
  await page.waitForTimeout(900)
  await page.screenshot({ path: path.join(SHOTS, '24-web-extuser-verify-selfinput.png'), fullPage: true })
  const vdlg = await dialogText()
  check('WEB-27 ③ 自填档案核验弹窗：当前填写 C 研究所 / 肿瘤组 + 「必须二选一」提示 + 新建/归并两个选项',
    vdlg.includes('C 研究所') && vdlg.includes('肿瘤组')
    && vdlg.includes('必须二选一') && vdlg.includes('新建') && vdlg.includes('归并到已有'),
    JSON.stringify(vdlg.split('\n').filter(l => /新建|归并|二选一|研究所|肿瘤/.test(l)).slice(0, 8)))
  // 归并模式：选单位 → 组别下拉联动
  await page.locator('.el-dialog:visible .el-radio:has-text("归并到已有")').first().click()
  await page.waitForTimeout(600)
  const mergeSelects = await page.locator('.el-dialog:visible .el-select').count()
  check('WEB-28 ③ 选「归并到已有」→ 出现单位 / 组别两个下拉', mergeSelects >= 2, `selects=${mergeSelects}`)
  if (mergeSelects >= 2) {
    await page.locator('.el-dialog:visible .el-select').nth(0).click()
    await page.waitForTimeout(700)
    const opts = await page.$$eval('.el-select-dropdown__item:visible', els => els.map(e => e.innerText.trim()))
    check('WEB-29 ③ 归并的单位下拉只列启用单位（A 医院 / B 大学，不含已停用单位）',
      opts.includes('A 医院') && opts.includes('B 大学') && !opts.some(o => o.includes('已停用')),
      JSON.stringify(opts.slice(0, 6)))
    await page.click('.el-select-dropdown__item:visible:has-text("A 医院")')
    await page.waitForTimeout(1400)
    await page.locator('.el-dialog:visible .el-select').nth(1).click()
    await page.waitForTimeout(900)
    const opts2 = await page.$$eval('.el-select-dropdown__item:visible', els => els.map(e => e.innerText.trim()))
    check('WEB-30 ③ 归并的组别下拉只列 A 医院下的组别（肝胆外科组 / 消化内科组）',
      opts2.includes('肝胆外科组') && opts2.includes('消化内科组') && !opts2.includes('类器官课题组'),
      JSON.stringify(opts2.slice(0, 6)))
    await page.keyboard.press('Escape').catch(() => {}) // 关下拉（Element 的 select 自己吃掉这次 Esc）
    await page.waitForTimeout(600)
  }
  await page.screenshot({ path: path.join(SHOTS, '25-web-extuser-verify-merge.png'), fullPage: true })
  // 驳回分支：把弹窗彻底关掉再重开，避免被上一步的 Esc / 下拉状态影响
  await closeDialog()
  await clickRowAndWait('.el-table__row:has-text("吴同学")', 'button:has-text("核验")', '.el-dialog:visible', '/auth/extuser', '核验状态')
  await page.waitForTimeout(700)
  await page.locator('.el-dialog:visible .el-radio-button:has-text("驳回")').first().click()
  await page.waitForTimeout(700)
  await page.screenshot({ path: path.join(SHOTS, '26-web-extuser-verify-reject.png'), fullPage: true })
  const rejectDlg = await dialogText()
  check('WEB-31 ③ 切到「驳回」→ 出现必填的驳回原因多行输入',
    rejectDlg.includes('驳回原因') && (await page.locator('.el-dialog:visible textarea').count()) >= 1,
    JSON.stringify(rejectDlg.split('\n').filter(l => /驳回/.test(l)).slice(0, 4)))
  // 不填原因直接确定要被挡下（不改数据、不发请求）
  await page.locator('.el-dialog:visible button:has-text("确 定")').first().click()
  await page.waitForTimeout(1200)
  const stillOpen = (await page.locator('.el-dialog:visible').count()) > 0
  const toastErr = await page.$$eval('.el-message', els => els.map(e => e.innerText).join('|')).catch(() => '')
  check('WEB-32 ③ 驳回不填原因 → 被前端挡下（弹窗不关 + 提示「驳回必须填写原因」）',
    stillOpen && toastErr.includes('驳回必须填写原因'), `dialogOpen=${stillOpen} msg="${toastErr.replace(/\n/g, ' ')}"`)
  await closeDialog()
  // 周医生：待核验但非自填 → 通过分支的说明
  await clickRowAndWait('.el-table__row:has-text("周医生")', 'button:has-text("核验")', '.el-dialog:visible', '/auth/extuser', '核验状态')
  await page.waitForTimeout(900)
  await page.screenshot({ path: path.join(SHOTS, '27-web-extuser-verify-zhou.png'), fullPage: true })
  const zdlg = await dialogText()
  check('WEB-33 ③ 周医生（待核验 · 非自填）核验弹窗：当前填写 A 医院 · 肝胆外科组 + 通过后互看的说明，且无「二选一」',
    zdlg.includes('A 医院') && zdlg.includes('肝胆外科组')
    && zdlg.includes('通过后他与同单位同组的同事互相可见样本')
    && !zdlg.includes('必须二选一'),
    JSON.stringify(zdlg.split('\n').filter(l => l.trim()).slice(0, 10)))
  await closeDialog()
  // 已核验的人：入口叫「改归组」
  const verifiedRow = page.locator('.el-table__row:has-text("王医生")').first()
  const verifiedBtnText = await verifiedRow.locator('button').first().innerText()
  check('WEB-34 ③ 已核验的 王医生 行入口 = 「改归组」', verifiedBtnText.includes('改归组'), `btn="${verifiedBtnText.trim()}"`)
  await clickRowAndWait('.el-table__row:has-text("王医生")', 'button:has-text("改归组")', '.el-dialog:visible', '/auth/extuser', '核验状态')
  await page.waitForTimeout(1200)
  await page.screenshot({ path: path.join(SHOTS, '28-web-extuser-regroup.png'), fullPage: true })
  const rdlg = await dialogText()
  check('WEB-35 ③ 改归组弹窗预选当前单位 / 组别（A 医院 + 肝胆外科组）',
    rdlg.includes('A 医院') && rdlg.includes('肝胆外科组'),
    JSON.stringify(rdlg.split('\n').filter(l => l.trim()).slice(0, 10)))
  await closeDialog()

  check('WEB-36 全流程没有未捕获的前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
}
catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 3).join(' | ') : String(e))
}
finally {
  const failed = results.filter(r => !r.ok)
  console.log(`\n== L2-r2(web-plusui ${TAG}) ${results.length - failed.length}/${results.length} 通过 ==`)
  console.log(`SHOTS=${SHOTS}`)
  await browser.close()
  process.exit(failed.length === 0 ? 0 : 1)
}
