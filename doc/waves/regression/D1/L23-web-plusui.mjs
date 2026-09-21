/**
 * D1 / L2 端侧断言（QA 分片 L23，独立重跑）——网页工作台（plus-ui）+ 真后端。
 *
 * 覆盖（qa_scope L2 第 2 条）：lqgadmin 登录 → 「人员与单位」三个页面各走一遍（截图）：
 *   ① 内部人员授权：列表 + 「按手机号授权」弹窗（已登录过小程序 → 提示将原地升级）
 *   ② 来源单位与组别：左单位右组别的主从结构（点单位 → 右栏联动）
 *   ③ 外部用户：列表 + 核验弹窗（自填档案 → 必须二选一「新建 / 归并到已有」）
 *
 * ★ 本脚本同时记录一条**已复现的缺陷**（见 doc/waves/qa/D1-r1-L23.json 的 S1 issue）：
 *   在 `pnpm dev`（8082）下，登录后除了落地的首页，**点任何菜单导航都会让 .app-main 变成 `<!---->`**；
 *   直接刷新该 URL 又能渲染。触发点是 layout/components/AppMain.vue 的 `<transition mode="out-in">`。
 *   构建产物（pnpm build:dev 的静态产物 + 同一后端）三页都正常 —— 所以截图走「硬刷新到目标 URL」，
 *   同时把「点菜单 → 空白」这一步也跑出来留证。
 *
 * 前置：后端 8081、plus-ui dev server 8082（.env.development 指向 8081）。
 * 跑法：node doc/waves/regression/D1/L23-web-plusui.mjs
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
const SHOTS = path.join(HERE, 'shots-L23')
mkdirSync(SHOTS, { recursive: true })

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

/** 左侧菜单：折叠状态下子项 rect 全是 0×0，先展开「人员与单位」目录再点子项 */
async function openMenu(itemText) {
  const isExpanded = async () => {
    const box = await page.locator(`.el-menu-item:has-text("${itemText}")`).first().boundingBox().catch(() => null)
    return !!(box && box.width > 0 && box.height > 0)
  }
  if (!(await isExpanded())) {
    await page.click('.el-sub-menu__title:has-text("人员与单位")')
    await page.waitForTimeout(900)
  }
  await page.click(`.el-menu-item:has-text("${itemText}")`)
  await page.waitForTimeout(1600)
}

const appMainLen = () => page.evaluate(() => document.querySelector('.app-main')?.innerHTML.length ?? -1)
const bodyText = () => page.evaluate(() => document.body.innerText)

/** 硬刷新到目标路由（绕过 dev server 下失效的菜单导航），等页面内容起来 */
async function hardOpen(pathname, waitText) {
  await page.goto(`${BASE}${pathname}`)
  await page.waitForSelector('.el-menu', { timeout: 30000 })
  if (waitText) await page.waitForSelector(`text=${waitText}`, { timeout: 30000 }).catch(() => {})
  await page.waitForTimeout(1500)
}

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1560, height: 950 }, deviceScaleFactor: 1 })
const page = await ctx.newPage()
page.on('pageerror', e => console.log('[pageerror]', e.message))
page.on('console', m => { if (m.type() === 'error') console.log('[console.error]', m.text().slice(0, 200)) })

try {
  // ── 登录 ────────────────────────────────────────────────────────────────
  await page.goto(`${BASE}/`)
  await page.evaluate(() => localStorage.clear()).catch(() => {})
  await page.goto(`${BASE}/`)
  await page.waitForSelector('.login-form input', { timeout: 30000 })
  await page.screenshot({ path: path.join(SHOTS, '10-web-login.png') })
  const inputs = page.locator('.login-form input')
  await inputs.nth(0).fill('lqgadmin')
  await inputs.nth(1).fill('admin123')
  await page.click('.login-form button')
  await page.waitForSelector('.el-menu', { timeout: 30000 })
  await page.waitForTimeout(2200)
  await page.screenshot({ path: path.join(SHOTS, '11-web-home.png') })
  check('lqgadmin 用工作台账号密码登录成功（带侧边栏的壳）',
    (await page.$$('.el-menu-item')).length > 0,
    (await page.$$eval('.el-menu-item', els => els.map(e => e.innerText.trim()).join(' | '))).slice(0, 300))

  // ── 【缺陷复现】dev server 下菜单导航 → 空白页 ──────────────────────────
  await openMenu('内部人员授权')
  const blankLen = await appMainLen()
  await page.screenshot({ path: path.join(SHOTS, '11b-web-menu-nav-blank.png') })
  check('【缺陷复现】dev server 下点菜单导航后 .app-main 渲染成空注释（不是 pass/fail 断言，留证）',
    blankLen <= 20, `URL=${page.url()} .app-main innerHTML length=${blankLen}`)

  // ── ① 内部人员授权 ──────────────────────────────────────────────────────
  await hardOpen('/auth/staff', '按手机号授权')
  check('① 内部人员授权页 URL', /auth\/staff/.test(page.url()), page.url())
  const staffText = await bodyText()
  await page.screenshot({ path: path.join(SHOTS, '12-web-staff-list.png'), fullPage: true })
  check('① 列表恰好两行 seed 内部账号（测试管理员 / 李工）',
    staffText.includes('测试管理员') && staffText.includes('李工')
    && !staffText.includes('L3') && !staffText.includes('新号'),
    JSON.stringify(staffText.split('\n').filter(l => l.includes('13') || l.includes('李工') || l.includes('管理员')).slice(0, 8)))
  await page.click('button:has-text("按手机号授权")')
  await page.waitForSelector('.el-dialog', { timeout: 20000 })
  await page.waitForTimeout(500)
  await page.screenshot({ path: path.join(SHOTS, '13-web-staff-grant-dialog-empty.png') })
  const dlgInputs = page.locator('.el-dialog input')
  await dlgInputs.nth(0).fill('13800000011') // extA：已用小程序登录过的外部账号
  await dlgInputs.nth(0).blur()
  await page.waitForTimeout(1800)
  await page.screenshot({ path: path.join(SHOTS, '14-web-staff-grant-dialog-upgrade.png'), fullPage: true })
  const dlgText = await page.evaluate(() => document.querySelector('.el-dialog')?.innerText || '')
  check('① 授权弹窗对已登录过的手机号提示「原地升级」',
    dlgText.includes('该手机号已登录过小程序，将把原账号升级为内部人员'),
    JSON.stringify(dlgText.split('\n').filter(l => l.includes('升级') || l.includes('预建'))))
  check('① 授权弹窗字段齐全（手机号 / 姓名 / 角色 / 工作台初始密码）',
    dlgText.includes('手机号') && dlgText.includes('姓名') && dlgText.includes('工作台初始密码'))
  // 对没有账号的手机号应给「预建」提示（正反两侧都断，避免只堵一个方向）
  await dlgInputs.nth(0).fill('13800000091')
  await dlgInputs.nth(0).blur()
  await page.waitForTimeout(1800)
  const dlgText2 = await page.evaluate(() => document.querySelector('.el-dialog')?.innerText || '')
  check('① 对没有账号的手机号提示「预建内部账号」',
    dlgText2.includes('该手机号还没有账号，将预建一个内部账号'),
    JSON.stringify(dlgText2.split('\n').filter(l => l.includes('升级') || l.includes('预建'))))
  await page.keyboard.press('Escape')
  await page.waitForTimeout(700)

  // ── ② 来源单位与组别（主从） ────────────────────────────────────────────
  await hardOpen('/auth/unit', '来源单位')
  check('② 来源单位与组别页 URL', /auth\/unit/.test(page.url()), page.url())
  await page.screenshot({ path: path.join(SHOTS, '15-web-unit-group.png'), fullPage: true })
  const unitText = await bodyText()
  check('② 左栏单位列表含 A 医院 / B 大学 / 已停用单位',
    unitText.includes('A 医院') && unitText.includes('B 大学') && unitText.includes('已停用单位'),
    JSON.stringify(unitText.split('\n').filter(l => l.includes('医院') || l.includes('大学') || l.includes('停用')).slice(0, 8)))
  await page.click('.el-table__row:has-text("A 医院")')
  await page.waitForTimeout(1500)
  await page.screenshot({ path: path.join(SHOTS, '16-web-unit-group-master-detail.png'), fullPage: true })
  const unitText2 = await bodyText()
  check('② 点单位后右栏联动出该单位的组别（肝胆外科组 / 消化内科组，不含 B 大学的组）',
    unitText2.includes('肝胆外科组') && unitText2.includes('消化内科组') && !unitText2.includes('类器官课题组'),
    JSON.stringify(unitText2.split('\n').filter(l => l.includes('组')).slice(0, 10)))

  // ── ③ 外部用户核验 ──────────────────────────────────────────────────────
  await hardOpen('/auth/extuser', '外部用户')
  check('③ 外部用户页 URL', /auth\/extuser/.test(page.url()), page.url())
  await page.waitForTimeout(800)
  await page.screenshot({ path: path.join(SHOTS, '17-web-extuser-list.png'), fullPage: true })
  const extText = await bodyText()
  check('③ 列表含待核验的 周医生(extE) 与自填的 吴同学(extF)',
    extText.includes('周医生') && extText.includes('吴同学'),
    JSON.stringify(extText.split('\n').filter(l => l.includes('医生') || l.includes('同学')).slice(0, 10)))
  check('③ 自填档案带「自填」标记', extText.includes('自填'))
  // 吴同学 = 自填「C 研究所 / 肿瘤组」待核验 → 核验弹窗必须出现「新建 / 归并」二选一
  await page.click('.el-table__row:has-text("吴同学") button:has-text("核验")')
  await page.waitForSelector('.el-dialog', { timeout: 20000 })
  await page.waitForTimeout(900)
  await page.screenshot({ path: path.join(SHOTS, '18-web-extuser-verify-dialog.png'), fullPage: true })
  const vdlg = await page.evaluate(() => document.querySelector('.el-dialog')?.innerText || '')
  check('③ 自填档案的核验弹窗出现「新建 / 归并到已有」二选一',
    vdlg.includes('新建') && vdlg.includes('归并到已有'),
    JSON.stringify(vdlg.split('\n').filter(l => l.includes('新建') || l.includes('归并') || l.includes('自填')).slice(0, 6)))
  check('③ 核验弹窗有通过 / 驳回两个出口', vdlg.includes('通过') && vdlg.includes('驳回'))
  await page.keyboard.press('Escape')
  await page.waitForTimeout(500)
  // 已核验的人（王医生）入口应叫「改归组」
  check('③ 已核验的 王医生 行入口是「改归组」',
    (await bodyText()).includes('改归组'))
}
catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n')[0] : String(e))
}
finally {
  const failed = results.filter(r => !r.ok)
  console.log(`\n== L2(web-plusui) ${results.length - failed.length}/${results.length} 通过 ==`)
  console.log(`SHOTS=${SHOTS}`)
  await browser.close()
  process.exit(failed.length === 0 ? 0 : 1)
}
