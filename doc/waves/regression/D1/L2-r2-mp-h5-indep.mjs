/**
 * D1 / L2 端侧断言（r2 独立片，自建）—— 小程序 H5 等价路径（覆盖 qa_scope L2 第 1 条）。
 *
 * ★ 本文件是与 r1 / 上一任 r2 脚本**互相独立**的重写：期望值全部来自
 *   doc/authority（UI:mp.login / UI:mp.home.entries / UI:mp.me / UI:mp.me.profile）+ doc/verify/seed
 *   的机器事实（db.py 查出来的单位 / 组别 / 档案），不抄任何既有脚本的断言值。
 *
 * 覆盖：
 *   登录页（未勾选 / 拒绝授权手机号两条分支必须停在登录页）
 *   → 内部首页 4 入口 2×2、外部首页 3 入口（第 3 格横向占满）= 两种首页都没有任何数字 / 角标
 *   → 我的（内部：历史编辑记录 + 内部管理 4 入口 + 底部小字；外部：历史编辑记录 + 单位与组别）
 *   → 单位与组别页（回填 + 改归组保存闭环 → 回到「待核验」）
 *
 * 微信开发者工具在本沙箱跑不通（CLI 要写 ~/Library/Application Support/微信开发者工具/** → EPERM
 * 且需人工扫码）：本片用 H5 构建等价路径覆盖，**不冒充**开发者工具 / 真机结果（见 audit evidence）。
 *
 * 跑法：LQG_H5_BASE=http://127.0.0.1:9200 LQG_SHOT_TAG=mp-h5-indep node doc/waves/regression/D1/L2-r2-mp-h5-indep.mjs
 * 前置：后端 8081 + reseed + miniapp H5 dev（VITE_MOCK_LOGIN=1，否则没有调试登录面板）。
 */
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const BASE = process.env.LQG_H5_BASE || 'http://127.0.0.1:9200'
const TAG = process.env.LQG_SHOT_TAG || 'mp-h5-indep'
const SHOTS = path.join(HERE, 'shots-L23-r2', TAG)
mkdirSync(SHOTS, { recursive: true })

// 权威顺序（UI:mp.home.entries / UI:mp.me）
const INT_ENTRIES = ['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录', '-80 冻存记录']
const EXT_ENTRIES = ['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录']
const INT_ADMIN_NOTE = '核验、冻存取用请到网页工作台'

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

const homeText = p => p.$eval('.home', e => e.innerText).catch(() => '')
const meText = p => p.$eval('.me', e => e.innerText).catch(() => '')
const bodyText = p => p.evaluate(() => document.body.innerText)
const tiles = p => p.$$eval('.lqg-tile', els => els.map(e => ({
  t: (e.querySelector('.lqg-tile__t')?.textContent || '').trim(),
  full: e.className.includes('lqg-tile--full'),
})))
const tileBoxes = p => p.$$eval('.lqg-tile', els => els.map((e) => {
  const r = e.getBoundingClientRect()
  return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) }
}))
const toastText = p => p.$$eval('.uni-toast,[class*="toast"]', els => els.map(e => e.innerText).join('|')).catch(() => '')
/** 「没有数字」的严格口径：把表名里的 -80 去掉后，正文里一个数字都不许有 */
const digitsOutside80 = t => (t.replace(/-80/g, '').match(/\d/g) || [])
/** 角标 / 计数摘要元素（身份徽标 .lqg-tag 不算角标） */
const BADGE_SEL = '[class*="badge"],[class*="is-dot"],[class*="StatTile"],[class*="count"]'
const badgeCount = p => p.$$eval(BADGE_SEL, els => els.length).catch(() => -1)

async function resetToLogin(page) {
  await page.goto(`${BASE}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => { window.localStorage.clear(); window.sessionStorage.clear() })
  // ★ 硬重载：同 URL 只变 hash 时 goto 不重新加载页面，页面内存态的 agreed ref 会留到下一次登录。
  // 真实小程序里重新 reLaunch 到登录页是新的页面实例，这里必须用 reload 对齐语义。
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login__agree', { timeout: 30000 })
  await page.waitForTimeout(400)
}

async function mockLogin(page, label) {
  // 勾选做成幂等：已经勾上就别再点（再点一次会取消勾选）
  const on = await page.$eval('.login__box', el => el.className.includes('login__box--on')).catch(() => false)
  if (!on) {
    await page.click('.login__box')
  }
  await page.click(`.login__mock-btn:has-text("${label}")`)
  await page.waitForSelector('.lqg-tile', { timeout: 30000 })
  await page.waitForTimeout(900)
}

async function gotoMe(page) {
  // ★ L2-r2 收尾 harness 加固（只动等待/导航，不动断言）：uni-app H5 在**非 tab 页**（MP-12 会
  //   落到 /pages/cryo/form）会把 `.uni-tabbar` 留在 DOM 里但**不可见**——所以必须断
  //   **可见性**而不是 count()（count() 会把隐藏节点也算进去 → 直接 click 30s 超时）。
  //   不可见就先回首页 tab 页再点。
  const tab = page.locator('.uni-tabbar__item:has-text("我的")')
  const usable = (await tab.count()) > 0 && await tab.first().isVisible().catch(() => false)
  if (!usable) {
    await page.goto(`${BASE}/#/pages/index/index`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.uni-tabbar__item:visible', { timeout: 30000 })
    await page.waitForTimeout(1200)
  }
  await tab.first().click({ timeout: 10000 })
  await page.waitForSelector('.me__logout', { timeout: 30000 })
  await page.waitForTimeout(800)
}

const pageErrors = []
const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await ctx.newPage()
page.on('pageerror', e => { pageErrors.push(e.message); console.log('[pageerror]', e.message) })

try {
  // ══ 0. 登录页 + 两条必须停在登录页的分支 ═══════════════════════════════
  await resetToLogin(page)
  await page.screenshot({ path: path.join(SHOTS, '00-login.png'), fullPage: true })
  const btnText = await page.$eval('.login__btn', e => e.textContent.trim())
  check('MP-01 登录页：主按钮「微信手机号快捷登录」+ 协议勾选，没有账号密码框与内/外部入口',
    btnText === '微信手机号快捷登录'
    && (await page.$('.login__agree')) !== null
    && (await page.$$('input[type=password]')).length === 0
    && !(await bodyText(page)).includes('内部登录')
    && !(await bodyText(page)).includes('外部登录'),
    `btn="${btnText}"`)

  // 分支 A：没勾协议就点主按钮（真实回调带 code）
  await page.$eval('.login__btn', el => el.dispatchEvent(
    new CustomEvent('getphonenumber', { detail: { code: 'x' }, bubbles: true, composed: true })))
  await page.waitForTimeout(900)
  const toastA = await toastText(page)
  await page.screenshot({ path: path.join(SHOTS, '01-login-noagree.png'), fullPage: true })
  check('MP-02 未勾协议点登录：提示先勾选，仍停在登录页、没有 token',
    /pages\/login\/index/.test(page.url())
    && toastA.includes('请先阅读并勾选')
    && !(await page.evaluate(() => window.localStorage.getItem('lqg-token'))),
    `url=${page.url()} toast="${toastA.replace(/\n/g, ' ')}"`)

  // 分支 B：勾了协议，用户拒绝授权手机号（微信回调带 errMsg、没有 code）
  await resetToLogin(page)
  await page.click('.login__box')
  await page.$eval('.login__btn', el => el.dispatchEvent(
    new CustomEvent('getphonenumber', { detail: { errMsg: 'getPhoneNumber:fail user deny' }, bubbles: true, composed: true })))
  await page.waitForTimeout(1000)
  const toastB = await toastText(page)
  await page.screenshot({ path: path.join(SHOTS, '02-login-reject-phone.png'), fullPage: true })
  const afterReject = {
    url: page.url(),
    token: await page.evaluate(() => window.localStorage.getItem('lqg-token')),
    homeTiles: (await page.$$('.lqg-tile')).length,
    stillLoginBtn: (await page.$('.login__btn')) !== null,
  }
  check('MP-03 拒绝授权手机号：停在登录页（没有 token、没有落到首页宫格、主按钮还在）',
    /pages\/login\/index/.test(afterReject.url) && !afterReject.token
    && afterReject.homeTiles === 0 && afterReject.stillLoginBtn,
    JSON.stringify(afterReject))
  check('MP-04 拒绝授权手机号：提示「需要手机号才能送检和查看结果」',
    `${await bodyText(page)} ${toastB}`.includes('需要手机号才能送检和查看结果'),
    `toast="${toastB.replace(/\n/g, ' ')}"`)

  // ══ 1. 内部首页 ════════════════════════════════════════════════════════
  await resetToLogin(page)
  await mockLogin(page, '内部人员 · 李工')
  await page.screenshot({ path: path.join(SHOTS, '10-home-internal.png'), fullPage: true })
  const intTiles = await tiles(page)
  check('MP-05 内部首页四格、顺序 = 模板顺序',
    JSON.stringify(intTiles.map(t => t.t)) === JSON.stringify(INT_ENTRIES), JSON.stringify(intTiles.map(t => t.t)))
  const intHome = await homeText(page)
  check('MP-06 内部首页区块标题「填写」+ 副标题「点表新增一条」',
    intHome.includes('填写') && intHome.includes('点表新增一条'),
    JSON.stringify(intHome.split('\n').filter(l => l.trim()).slice(0, 6)))
  const intDigits = digitsOutside80(intHome)
  check('MP-07 内部首页没有任何数字（去掉表名里的 -80 后正文零数字）',
    intDigits.length === 0, `digits=${JSON.stringify(intDigits)} text=${JSON.stringify(intHome.replace(/\n/g, '/'))}`)
  check('MP-08 内部首页没有角标 / 计数摘要元素', (await badgeCount(page)) === 0, `matched=${await badgeCount(page)}`)
  // BADGE_SEL 必须当参数传进页面上下文：页面函数里直接引外层常量会 ReferenceError
  const intTileBadges = await page.$$eval('.lqg-tile', (els, sel) => els.filter(e => e.querySelector(sel)).length, BADGE_SEL)
  check('MP-09 内部首页每一格内部也没有角标', intTileBadges === 0, `tiles_with_badge=${intTileBadges}`)
  check('MP-10 内部首页身份行 = 「李工，…好」+ 徽标「内部人员」',
    /李工，[凌晨上午下午晚上]+好/.test(intHome) && intHome.includes('内部人员'),
    JSON.stringify(intHome.split('\n').slice(0, 3)))
  const intBoxes = await tileBoxes(page)
  const intGeometryOk = intBoxes.length === 4
    && intBoxes[0].y === intBoxes[1].y && intBoxes[2].y === intBoxes[3].y && intBoxes[2].y > intBoxes[0].y
    && Math.abs(intBoxes[0].w - intBoxes[1].w) <= 2 && Math.abs(intBoxes[2].w - intBoxes[3].w) <= 2
    && !intTiles.some(t => t.full)
  check('MP-11 内部首页是 2×2 宫格（四格等宽两行，没有横跨整行的格）', intGeometryOk, JSON.stringify(intBoxes))

  // 内部独占入口的落地：点第 4 格 -80 冻存记录
  await page.click('.lqg-tile:has-text("-80 冻存记录")')
  await page.waitForTimeout(1800)
  await page.screenshot({ path: path.join(SHOTS, '11-internal-cryo-entry.png'), fullPage: true })
  check('MP-12 内部点第 4 格「-80 冻存记录」→ /pages/cryo/form', /pages\/cryo\/form/.test(page.url()), page.url())

  // ══ 2. 内部「我的」 ════════════════════════════════════════════════════
  await gotoMe(page)
  await page.screenshot({ path: path.join(SHOTS, '12-me-internal.png'), fullPage: true })
  const intMe = await meText(page)
  check('MP-13 内部「我的」有「历史编辑记录」', intMe.includes('历史编辑记录'))
  check('MP-14 内部「我的」有「内部管理」板块 + 四个只读表入口（顺序 = 模板顺序）',
    intMe.includes('内部管理') && (() => {
      const idx = INT_ENTRIES.map(t => intMe.indexOf(t))
      return idx.every(i => i >= 0) && idx.every((v, i) => i === 0 || v > idx[i - 1])
    })(),
    JSON.stringify(intMe.split('\n').filter(l => l.length > 1).slice(0, 20)))
  check('MP-15 内部管理板块底部小字逐字 = 「核验、冻存取用请到网页工作台」（不含「修改、」）',
    intMe.includes(INT_ADMIN_NOTE) && !intMe.includes(`修改、${INT_ADMIN_NOTE}`),
    JSON.stringify(intMe.split('\n').filter(l => l.includes('请到网页工作台'))))
  check('MP-16 内部「我的」不出现「单位与组别」板块', !intMe.includes('单位与组别'))
  check('MP-17 内部「我的」页头 = 李工 + 手机号掩码 138****0001 + 徽标「内部人员」',
    intMe.includes('李工') && intMe.includes('138****0001') && intMe.includes('内部人员'),
    JSON.stringify(intMe.split('\n').slice(0, 4)))
  await page.click('.merow:has-text("历史编辑记录")')
  await page.waitForTimeout(1600)
  await page.screenshot({ path: path.join(SHOTS, '13-internal-history.png'), fullPage: true })
  check('MP-18 内部点「历史编辑记录」→ /pages/history/index',
    /pages\/history\/index/.test(page.url()), page.url())

  // ══ 3. 外部首页 ════════════════════════════════════════════════════════
  await resetToLogin(page)
  await mockLogin(page, '外部人员 · 王医生（已核验）')
  await page.screenshot({ path: path.join(SHOTS, '20-home-external.png'), fullPage: true })
  const extTiles = await tiles(page)
  check('MP-19 外部首页三格、顺序 = 模板顺序（从内部切换过来也不残留冻存格）',
    JSON.stringify(extTiles.map(t => t.t)) === JSON.stringify(EXT_ENTRIES), JSON.stringify(extTiles.map(t => t.t)))
  const extHome = await homeText(page)
  check('MP-20 外部首页完全没有「冻存」字样', !extHome.includes('冻存'), extHome.includes('冻存') ? '含冻存' : 'ok')
  const extDigits = digitsOutside80(extHome)
  check('MP-21 外部首页没有任何数字', extDigits.length === 0,
    `digits=${JSON.stringify(extDigits)} text=${JSON.stringify(extHome.replace(/\n/g, '/'))}`)
  check('MP-22 外部首页没有角标 / 计数摘要元素', (await badgeCount(page)) === 0, `matched=${await badgeCount(page)}`)
  check('MP-23 外部首页身份行 = 「A 医院 · 肝胆外科组 · 已核验」+ 徽标「合作单位」',
    extHome.includes('A 医院 · 肝胆外科组 · 已核验') && extHome.includes('合作单位'),
    JSON.stringify(extHome.split('\n').filter(l => l.includes('医院') || l.includes('单位')).slice(0, 4)))
  const extBoxes = await tileBoxes(page)
  const gridBox = await page.$eval('.lqg-grid', e => { const r = e.getBoundingClientRect(); return { x: Math.round(r.x), w: Math.round(r.width) } }).catch(() => null)
  const extGeometryOk = extBoxes.length === 3
    && extBoxes[0].y === extBoxes[1].y && extBoxes[2].y > extBoxes[0].y
    && extTiles[2].full === true && !extTiles[0].full && !extTiles[1].full
    && extBoxes[2].w > extBoxes[0].w * 1.8
    // 第三格宽度 ≈ 两列之和（跨 2 列），且不超过宫格容器宽度
    && extBoxes[2].w >= extBoxes[0].w + extBoxes[1].w - 2
    && (!gridBox || extBoxes[2].w <= gridBox.w + 2)
  check('MP-24 外部首页第三格横向占满整行（不留半宽孤格，UI:mp.home.entries 逐字要求）',
    extGeometryOk, `tiles=${JSON.stringify(extBoxes)} grid=${JSON.stringify(gridBox)}`)

  await page.click('.lqg-tile:has-text("样本记录信息表")')
  await page.waitForTimeout(1800)
  await page.screenshot({ path: path.join(SHOTS, '21-external-sample-entry.png'), fullPage: true })
  check('MP-25 外部点「样本记录信息表」→ /pages/sample/form（点哪格都是该表填写页）',
    /pages\/sample\/form/.test(page.url()), page.url())

  // ══ 4. 外部「我的」 + 单位与组别页 ══════════════════════════════════════
  await resetToLogin(page)
  await mockLogin(page, '外部人员 · 王医生（已核验）')
  await gotoMe(page)
  await page.screenshot({ path: path.join(SHOTS, '22-me-external.png'), fullPage: true })
  const extMe = await meText(page)
  check('MP-26 外部「我的」有「历史编辑记录」', extMe.includes('历史编辑记录'))
  check('MP-27 外部「我的」有「单位与组别」板块（当前值 A 医院 · 肝胆外科组 + 徽标「已核验」）',
    extMe.includes('单位与组别') && extMe.includes('A 医院 · 肝胆外科组') && extMe.includes('已核验'),
    JSON.stringify(extMe.split('\n').filter(l => l.includes('医院') || l.includes('核验') || l.includes('单位'))))
  check('MP-28 外部「我的」不渲染「内部管理」板块（不是置灰）', !extMe.includes('内部管理'))
  check('MP-29 外部「我的」页头 = 王医生 + 手机号掩码 138****0011 + 徽标「合作单位」',
    extMe.includes('王医生') && extMe.includes('138****0011') && extMe.includes('合作单位'),
    JSON.stringify(extMe.split('\n').slice(0, 4)))

  await page.click('.merow:has-text("改了需要中心重新核验")')
  await page.waitForURL(/pages\/me\/unit-group/, { timeout: 30000 })
  await page.waitForTimeout(1800)
  await page.screenshot({ path: path.join(SHOTS, '23-unit-group.png'), fullPage: true })
  const ugText = await bodyText(page)
  check('MP-30 单位与组别页 URL 正确', /pages\/me\/unit-group/.test(page.url()), page.url())
  check('MP-31 单位与组别页：顶部口径逐字 + 当前状态「已核验」+ 「当前已核验：A 医院 · 肝胆外科组」',
    ugText.includes('核验通过后，可与同组同事互看样本')
    && ugText.includes('当前状态') && ugText.includes('已核验')
    && ugText.includes('当前已核验：A 医院 · 肝胆外科组'),
    JSON.stringify(ugText.split('\n').filter(l => l.trim()).slice(0, 20)))
  check('MP-32 单位与组别页没有渲染失败态（没有「没能加载你的档案」/「没能确认你的身份」）',
    !ugText.includes('没能加载你的档案') && !ugText.includes('没能确认你的身份'))
  // 表单必须回填当前档案（不是空壳）：姓名 = 王医生，单位 / 组别 = A 医院 / 肝胆外科组
  const ugForm = await page.evaluate(() => {
    const inputs = Array.from(document.querySelectorAll('.profile__input')).map(el => {
      // ★ L2-r2 收尾 harness 修复（只改选择器）：uni-app H5 把 `<input class="profile__input">`
      //   编译成 `<uni-input class="profile__input">` 包一个内层 `<input class="uni-input-input">`，
      //   直接读 wrapper 的 `.value` 恒为 undefined（探针实证：内层 input.value === '王医生'）。
      const inner = el.matches('input') ? el : el.querySelector('input')
      return inner ? inner.value : (el.value ?? '')
    })
    const picks = Array.from(document.querySelectorAll('.picker__value')).map(e => e.innerText.trim())
    return { inputs, picks }
  })
  check('MP-33 单位与组别页表单回填当前档案（姓名 王医生 / 单位 A 医院 / 组别 肝胆外科组）',
    ugForm.inputs.includes('王医生') && ugForm.picks.join('|').includes('A 医院') && ugForm.picks.join('|').includes('肝胆外科组'),
    JSON.stringify(ugForm))
  check('MP-34 单位与组别页有保存 / 返回首页 / 「改完单位或组别会回到待核验」的说明',
    ugText.includes('保存') && ugText.includes('返回首页')
    && ugText.includes('改完单位或组别会回到「待核验」'),
    JSON.stringify(ugText.split('\n').filter(l => l.includes('保存') || l.includes('待核验')).slice(0, 4)))

  // ★ 加强：真的改一次组别并保存 → 页面必须回到「待核验」（脚本末尾会 reseed 还原）
  // 证明这一页不是「渲染得好看但点了没反应」，也证明 AUTH-GROUP-001 的端侧闭环通。
  await page.click('.picker__field:has-text("组别")')
  await page.waitForTimeout(1200)
  await page.screenshot({ path: path.join(SHOTS, '24-unit-group-picker.png'), fullPage: true })
  const sheetText = await bodyText(page)
  check('MP-35 点「组别」弹出选择面板：列表 = A 医院下的两个启用组别 + 「列表里没有，手动填写」',
    sheetText.includes('选择组别') && sheetText.includes('肝胆外科组') && sheetText.includes('消化内科组')
    && sheetText.includes('列表里没有，手动填写'),
    JSON.stringify(sheetText.split('\n').filter(l => l.includes('组')).slice(0, 8)))
  await page.click('.sheet__item:has-text("消化内科组")')
  await page.waitForTimeout(900)
  const picked = await page.evaluate(() => Array.from(document.querySelectorAll('.picker__value')).map(e => e.innerText.trim()))
  check('MP-36 选中「消化内科组」后单位 / 组别两栏都显示正确（A 医院 / 消化内科组）',
    picked.join('|').includes('A 医院') && picked.join('|').includes('消化内科组'), JSON.stringify(picked))
  await page.click('.profile__btn--p')
  await page.waitForTimeout(900)
  const saveToast = await toastText(page)
  await page.waitForTimeout(2200)
  await page.screenshot({ path: path.join(SHOTS, '25-unit-group-after-save.png'), fullPage: true })
  const afterSave = { url: page.url(), text: await bodyText(page) }
  check('MP-37 保存改归组：提示「已提交，等待核验」，回到「我的」后状态徽标变「待核验」',
    saveToast.includes('已提交，等待核验')
    && afterSave.text.includes('待核验') && !afterSave.text.includes('已核验')
    && /pages\/(me\/index|me\/unit-group)/.test(afterSave.url),
    `toast="${saveToast.replace(/\n/g, ' ')}" url=${afterSave.url} text=${JSON.stringify(afterSave.text.split('\n').filter(l => l.trim()).slice(0, 12))}`)

  check('MP-38 全流程无未捕获的前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
}
catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 3).join(' | ') : String(e))
}
finally {
  const failed = results.filter(r => !r.ok)
  console.log(`\n== L2-r2(mp-h5 ${TAG}) ${results.length - failed.length}/${results.length} 通过 ==`)
  console.log(`SHOTS=${SHOTS}`)
  await browser.close()
  process.exit(failed.length === 0 ? 0 : 1)
}
