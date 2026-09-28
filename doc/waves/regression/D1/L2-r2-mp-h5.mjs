/**
 * D1 / L2 端侧断言（r2 独立片，自建）——小程序 H5 等价路径。
 *
 * 与 r1 的 L23-mp-h5.mjs 的关系：**独立重写**，不改 r1 的文件、不写 r1 的 shots 目录。
 * r1 的结论只当交叉参照，不作为本脚本的输入。
 *
 * 环境事实（机器事实）：微信开发者工具在本沙箱跑不通（CLI 要写
 * ~/Library/Application Support/微信开发者工具/** EPERM + 需人工扫码），所以这一侧用
 * **H5 dev server + Playwright** 覆盖，开发者工具 / 真机项如实记为未覆盖，不冒充。
 *
 * 覆盖（phase-plan D1 qa_scope L2 第 1 条）：
 *   登录页 → 内部首页四入口 / 外部首页三入口（没有 -80 冻存记录）、两种首页都没有数字
 *   → 我的（内部「内部管理」/ 外部「单位与组别」，都有「历史编辑记录」）
 *   → 单位与组别页（截图）→ 拒绝授权手机号停在登录页。
 *
 * 前置：后端 8081（.tmp/run-backend.sh）+ reseed + miniapp H5（dev 9200 或 build 产物静态服务）。
 * 跑法：node doc/waves/regression/D1/L2-r2-mp-h5.mjs            # 默认 dev 9200
 *       LQG_H5_BASE=http://127.0.0.1:8099 LQG_SHOT_TAG=build-h5 node …   # build 产物
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
const TAG = process.env.LQG_SHOT_TAG || 'mp-dev'
const SHOTS = path.join(HERE, 'shots-L23-r2', TAG)
mkdirSync(SHOTS, { recursive: true })

// 甲方模板顺序（权威：UI:mp.home.entries + fixtures/home-entries-cases.json）
const INTERNAL_ENTRIES = ['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录', '-80 冻存记录']
const EXTERNAL_ENTRIES = ['样本记录信息表', '类器官收样记录', '石蜡包埋送样记录']

const results = []
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

/** 首页容器的文字（不含底部 tabbar） */
const homeText = p => p.$eval('.home', e => e.innerText).catch(() => '')
const meText = p => p.$eval('.me', e => e.innerText).catch(() => '')
const bodyText = p => p.evaluate(() => document.body.innerText)

/** 「没有数字」的严格口径：把表名里的 -80 去掉后，页面文字里一个数字都不许有 */
function digitsOutside80(text) {
  return (text.replace(/-80/g, '').match(/\d/g) || [])
}

/** 角标 / 计数摘要元素（身份徽标用 .lqg-tag，不算角标） */
const BADGE_SEL = '[class*="badge"],[class*="is-dot"],[class*="StatTile"],[class*="count"]'

async function resetToLogin(page) {
  await page.goto(`${BASE}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => { window.localStorage.clear(); window.sessionStorage.clear() })
  // ★ 必须**硬重载**：同 URL 只有 hash 变化时 goto 不重新加载，页面内存态（`agreed` 那个
  //   ref）会留到下一次登录 —— 于是 mockLogin 里再点一次 .login__box 反而把它**取消勾选**，
  //   调试登录被「请先阅读并勾选…」挡下，等 .lqg-tile 一路超时。
  //   （这是 harness 自己的坑，不是产品缺陷：onMockLogin 与 onGetPhoneNumber 一样会校验 agreed。）
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login__agree', { timeout: 25000 })
  await page.waitForTimeout(400)
}

async function mockLogin(page, label) {
  // 勾选状态做成幂等的：已经勾上就别再点（点第二次会取消）
  const on = await page.$eval('.login__box', el => el.className.includes('login__box--on')).catch(() => false)
  if (!on) await page.click('.login__box')
  await page.click(`.login__mock-btn:has-text("${label}")`)
  await page.waitForSelector('.lqg-tile', { timeout: 25000 })
  await page.waitForTimeout(700)
}

const entriesOf = page => page.$$eval('.lqg-tile .lqg-tile__t', els => els.map(e => e.textContent.trim()))

async function gotoMe(page) {
  await page.click('.uni-tabbar__item:has-text("我的")')
  await page.waitForSelector('.me__logout', { timeout: 25000 })
  await page.waitForTimeout(600)
}

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const page = await ctx.newPage()
const pageErrors = []
page.on('pageerror', e => { pageErrors.push(e.message); console.log('[pageerror]', e.message) })

try {
  // ══ 0. 登录页 + 两个分支：未勾选 / 拒绝授权手机号 ═══════════════════════
  await resetToLogin(page)
  await page.screenshot({ path: path.join(SHOTS, '00-login.png'), fullPage: true })
  const btnText = await page.$eval('.login__btn', e => e.textContent.trim())
  check('MP-01 登录页渲染：一个微信手机号快捷登录主按钮 + 协议勾选，无账号密码框',
    btnText === '微信手机号快捷登录'
    && await page.$('.login__agree') !== null
    && (await page.$$('input[type=password]')).length === 0
    && !(await bodyText(page)).includes('内部登录')
    && !(await bodyText(page)).includes('外部登录'),
    `btn="${btnText}"`)

  // 分支 A：没勾协议就点登录 → 提示勾选，仍停登录页
  await page.$eval('.login__btn', el => el.dispatchEvent(
    new CustomEvent('getphonenumber', { detail: { code: 'x' }, bubbles: true, composed: true })))
  await page.waitForTimeout(800)
  const toastA = await page.$$eval('.uni-toast,[class*="toast"]', els => els.map(e => e.innerText).join('|')).catch(() => '')
  await page.screenshot({ path: path.join(SHOTS, '00-login-noagree.png'), fullPage: true })
  check('MP-02 未勾选协议点登录：提示先勾选，仍停在登录页',
    /pages\/login\/index/.test(page.url()) && toastA.includes('请先阅读并勾选'),
    `url=${page.url()} toast="${toastA.replace(/\n/g, ' ')}"`)

  // 分支 B：勾了协议，但用户拒绝授权手机号（回调 detail 里没有 code）
  await resetToLogin(page)
  await page.click('.login__box')
  await page.$eval('.login__btn', el => el.dispatchEvent(
    new CustomEvent('getphonenumber', { detail: {}, bubbles: true, composed: true })))
  await page.waitForTimeout(900)
  const toastB = await page.$$eval('.uni-toast,[class*="toast"]', els => els.map(e => e.innerText).join('|')).catch(() => '')
  await page.screenshot({ path: path.join(SHOTS, '00-login-reject-phone.png'), fullPage: true })
  const tokenAfterReject = await page.evaluate(() => window.localStorage.getItem('lqg-token'))
  check('MP-03 拒绝授权手机号：停在登录页、没有 token、没有落到首页',
    /pages\/login\/index/.test(page.url())
    && !/pages\/index\/index/.test(page.url())
    && !tokenAfterReject,
    `url=${page.url()} token=${tokenAfterReject}`)
  check('MP-04 拒绝授权手机号：提示「需要手机号才能送检和查看结果」',
    `${await bodyText(page)} ${toastB}`.includes('需要手机号才能送检和查看结果'),
    `toast="${toastB.replace(/\n/g, ' ')}"`)

  // ══ 1. 内部首页 ════════════════════════════════════════════════════════
  await resetToLogin(page)
  await mockLogin(page, '内部人员 · 李工')
  await page.screenshot({ path: path.join(SHOTS, '01-home-internal.png'), fullPage: true })
  const intEntries = await entriesOf(page)
  check('MP-05 内部首页四个入口、顺序 = 模板顺序',
    JSON.stringify(intEntries) === JSON.stringify(INTERNAL_ENTRIES), JSON.stringify(intEntries))
  const intHome = await homeText(page)
  check('MP-06 内部首页含 -80 冻存记录入口（内部专属）', intHome.includes('-80 冻存记录'))
  const intDigits = digitsOutside80(intHome)
  check('MP-07 内部首页没有任何数字（去掉表名里的 -80 后无数字）',
    intDigits.length === 0, `digits=${JSON.stringify(intDigits)} text=${JSON.stringify(intHome.replace(/\n/g, '/'))}`)
  const intBadges = await page.$$eval(BADGE_SEL, els => els.length).catch(() => -1)
  check('MP-08 内部首页没有角标 / 计数摘要元素', intBadges === 0, `matched=${intBadges}`)
  // BADGE_SEL 必须当参数传进页面上下文：页面函数里直接引外层常量会 ReferenceError
  const intTileBadges = await page.$$eval('.lqg-tile', (els, sel) => els.filter(e => e.querySelector(sel)).length, BADGE_SEL)
  check('MP-09 内部首页每个入口格里也没有角标', intTileBadges === 0, `tiles_with_badge=${intTileBadges}`)

  // ══ 2. 内部「我的」 ════════════════════════════════════════════════════
  await gotoMe(page)
  await page.screenshot({ path: path.join(SHOTS, '02-me-internal.png'), fullPage: true })
  const intMe = await meText(page)
  check('MP-10 内部「我的」有「历史编辑记录」', intMe.includes('历史编辑记录'))
  check('MP-11 内部「我的」有「内部管理」板块 + 四个只读表入口', intMe.includes('内部管理')
    && INTERNAL_ENTRIES.every(t => intMe.includes(t)),
    JSON.stringify(intMe.split('\n').filter(l => l.length > 1).slice(0, 20)))
  check('MP-12 内部管理板块底部小字 = 「核验、冻存取用请到网页工作台」（不含「修改、」）',
    intMe.includes('核验、冻存取用请到网页工作台') && !intMe.includes('修改、核验、冻存取用请到网页工作台'),
    JSON.stringify(intMe.split('\n').filter(l => l.includes('请到网页工作台'))))
  check('MP-13 内部「我的」不出现「单位与组别」板块', !intMe.includes('单位与组别'))

  // ══ 3. 外部首页（含换身份后不残留内部入口） ═════════════════════════════
  await resetToLogin(page)
  await mockLogin(page, '外部人员 · 王医生（已核验）')
  await page.screenshot({ path: path.join(SHOTS, '03-home-external.png'), fullPage: true })
  const extEntries = await entriesOf(page)
  check('MP-14 外部首页三个入口、顺序 = 模板顺序（同一次会话从内部切过来后不残留冻存格）',
    JSON.stringify(extEntries) === JSON.stringify(EXTERNAL_ENTRIES), JSON.stringify(extEntries))
  const extHome = await homeText(page)
  check('MP-15 外部首页完全没有「冻存」字样', !extHome.includes('冻存'),
    extHome.includes('冻存') ? '含冻存' : 'ok')
  const extDigits = digitsOutside80(extHome)
  check('MP-16 外部首页没有任何数字', extDigits.length === 0,
    `digits=${JSON.stringify(extDigits)} text=${JSON.stringify(extHome.replace(/\n/g, '/'))}`)
  const extBadges = await page.$$eval(BADGE_SEL, els => els.length).catch(() => -1)
  check('MP-17 外部首页没有角标 / 计数摘要元素', extBadges === 0, `matched=${extBadges}`)
  check('MP-18 外部首页身份行 = 「A 医院 · 肝胆外科组 · 已核验」',
    extHome.includes('A 医院') && extHome.includes('肝胆外科组') && extHome.includes('已核验'),
    JSON.stringify(extHome.split('\n').filter(l => l.includes('医院') || l.includes('核验'))))

  // 加强：点首页第一格必须落到该表的填写页（entryTarget 的端侧落地）
  await page.click('.lqg-tile:has-text("样本记录信息表")')
  await page.waitForTimeout(1400)
  await page.screenshot({ path: path.join(SHOTS, '03b-entry-sample-form.png'), fullPage: true })
  check('MP-19 外部点「样本记录信息表」落到填写页 /pages/sample/form',
    /pages\/sample\/form/.test(page.url()), page.url())

  // ══ 4. 外部「我的」 + 单位与组别页 ══════════════════════════════════════
  await resetToLogin(page)
  await mockLogin(page, '外部人员 · 王医生（已核验）')
  await gotoMe(page)
  await page.screenshot({ path: path.join(SHOTS, '04-me-external.png'), fullPage: true })
  const extMe = await meText(page)
  check('MP-20 外部「我的」有「历史编辑记录」', extMe.includes('历史编辑记录'))
  check('MP-21 外部「我的」有「单位与组别」（标题 + 「A 医院 · 肝胆外科组」+ 已核验）',
    extMe.includes('单位与组别') && extMe.includes('A 医院 · 肝胆外科组') && extMe.includes('已核验'),
    JSON.stringify(extMe.split('\n').filter(l => l.includes('医院') || l.includes('核验') || l.includes('单位'))))
  check('MP-22 外部「我的」不渲染「内部管理」板块（不是置灰）', !extMe.includes('内部管理'))

  await page.click('.merow:has-text("改了需要中心重新核验")')
  await page.waitForURL(/pages\/me\/unit-group/, { timeout: 25000 })
  await page.waitForTimeout(1400)
  await page.screenshot({ path: path.join(SHOTS, '05-unit-group.png'), fullPage: true })
  const ugText = await bodyText(page)
  check('MP-23 单位与组别页 URL 正确', /pages\/me\/unit-group/.test(page.url()), page.url())
  check('MP-24 单位与组别页渲染：顶部口径 + 当前状态 + 姓名/单位/组别 + 保存/返回 + 回待核验说明',
    ugText.includes('核验通过后，可与同组同事互看样本')
    && ugText.includes('当前状态') && ugText.includes('已核验')
    && ugText.includes('姓名') && ugText.includes('单位') && ugText.includes('组别')
    && ugText.includes('保存') && ugText.includes('返回首页')
    && ugText.includes('改完单位或组别会回到「待核验」'),
    JSON.stringify(ugText.split('\n').filter(l => l.trim()).slice(0, 24)))
  check('MP-25 单位与组别页没有渲染失败态',
    !ugText.includes('没能加载你的档案') && !ugText.includes('没能确认你的身份'))

  check('MP-26 全流程无未捕获的前端异常', pageErrors.length === 0, JSON.stringify(pageErrors.slice(0, 3)))
}
catch (e) {
  check('脚本异常', false, e && e.stack ? e.stack.split('\n').slice(0, 2).join(' | ') : String(e))
}
finally {
  const failed = results.filter(r => !r.ok)
  console.log(`\n== L2-r2(mp-h5 ${TAG}) ${results.length - failed.length}/${results.length} 通过 ==`)
  console.log(`SHOTS=${SHOTS}`)
  await browser.close()
  process.exit(failed.length === 0 ? 0 : 1)
}
