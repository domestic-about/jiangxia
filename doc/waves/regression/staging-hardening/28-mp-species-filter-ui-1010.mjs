// CR-20261010-21 界面回归（本机小程序 H5 :9100、后端 :8081、库 lqg-dev-postgres）：小程序三处按种属筛。
//   node doc/waves/regression/staging-hardening/28-mp-species-filter-ui-1010.mjs
// ① 内部管理四张表：一排「全部种属 / 人 / 鼠兔 / 移植猪 / 鸡 / 未填」，选了只出该种属、换表保留、冻存页签数字不被带偏
// ② 质控文档页签：内部人员多一行「种属」，合作单位没有这一行
// ③ 质控文档填写列表：种属一排 + 卡片副行带种属
// 自己会 reseed、灌演示数据（V202610101100）、让已完成的文档出内部版；结束时 reseed 回验收基线。截图在 shots-28/。
import { execFileSync, execSync } from 'node:child_process'
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'

const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const MP = (process.env.LQG_MP_BASE || 'http://127.0.0.1:9100').replace(/\/$/, '')
const PG = process.env.LQG_DEV_PG_CONTAINER || 'lqg-dev-postgres'
const DEMO = 'code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202610101100__CR-20261010-21-demo-organoid-samples.sql'
const SHOTS = 'doc/waves/regression/staging-hardening/shots-28'
fs.mkdirSync(SHOTS, { recursive: true })
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }
const reseed = () => execSync('bash doc/verify/reseed.sh --yes', { stdio: 'ignore' })
const api = (...args) => execFileSync('bash', ['doc/verify/api.sh', ...args], { encoding: 'utf8' })
const same = (a, b) => JSON.stringify([...a].sort()) === JSON.stringify([...b].sort())

reseed()
execSync(`docker exec -i ${PG} psql -U lqg -d lqg_dev -q -v ON_ERROR_STOP=1 < ${DEMO}`, { stdio: 'ignore' })
for (const d of ['9000001001/sample_qc', '9000001001/organoid_qc', '9000001001/organoid_score', '9000001004/sample_qc', '9000001006/organoid_score']) {
  api('--as', 'staff', 'POST', `/lqg/doc/${d}/render?audience=internal`)
}

const b = await chromium.launch().catch(() => chromium.launch({ channel: 'chrome' }))
try {
  const wait = (p, ms) => p.waitForTimeout(ms)
  async function session(match) {
    const ctx = await b.newContext({ viewport: { width: 375, height: 760 } })
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
  const texts = async (m, sel) => (await m.locator(sel).allInnerTexts()).map(s => s.replace(/\s+/g, ' ').trim())

  // ── ① 内部管理（内部 · 李工）─────────────────────────────────────────────
  console.log('① 内部管理：四张表的种属一排')
  const { ctx, m, open } = await session(/李工/)
  await open('pages/ledger/index?sheet=organoid')
  const frozen = () => texts(m, '.ledger__row .ledger__fz-main')
  const chips = () => texts(m, '.ledger-page__chip')
  const tap = async (label) => { await m.locator('.ledger-page__chip', { hasText: new RegExp(`^${label}$`) }).first().click(); await wait(m, 1800) }
  const sheetTab = async (re) => { await m.locator('.lqg-sheets__item', { hasText: re }).first().click(); await wait(m, 2000) }
  let c = await chips()
  check('类器官收样记录：有「全部种属 / 人 / 鼠兔 / 移植猪 / 鸡 / 未填」一排', ['全部种属', '人', '鼠兔', '移植猪', '鸡', '未填'].every(x => c.includes(x)), c.join(' / '))
  check('不筛：6 条', (await texts(m, '.lqg-count')).join('').includes('共 6 条'), (await texts(m, '.lqg-count')).join(''))
  await m.screenshot({ path: `${SHOTS}/1-ledger-organoid-all.png` })
  await tap('人')
  check('选「人」：只剩 T-oli11、T-oga12', same(await frozen(), ['T-oli11', 'T-oga12']), (await frozen()).join(' / '))
  check('「人」那一格是选中态、「全部种属」不是', (await m.locator('.ledger-page__chip--on').allInnerTexts()).map(s => s.trim()).includes('人'))
  await m.screenshot({ path: `${SHOTS}/2-ledger-organoid-ren.png` })
  // 换表保留筛选
  await sheetTab(/样本记录/)
  check('换到样本记录信息表：种属筛选还在（人 → 3 条）', (await texts(m, '.lqg-count')).join('').includes('共 3 条') && (await m.locator('.ledger-page__chip--on').allInnerTexts()).map(s => s.trim()).includes('人'), (await texts(m, '.lqg-count')).join(''))
  await tap('未填')
  check('选「未填」：只剩那条待核验的老记录', same(await frozen(), ['SJ90000007']), (await frozen()).join(' / '))
  await tap('移植猪')
  check('选「移植猪」：T-hli02、T-hli05', same(await frozen(), ['T-hli02', 'T-hli05']), (await frozen()).join(' / '))
  await sheetTab(/石蜡/)
  check('石蜡包埋 · 移植猪：T-E02-1、T-E13-1', same(await frozen(), ['T-E02-1', 'T-E13-1']), (await frozen()).join(' / '))
  await sheetTab(/冻存/)
  c = await chips()
  check('-80 冻存：页签数字还在（全部 9 …），种属一排的第一项仍叫「全部种属」', c.some(x => /^全部 \d+$/.test(x)) && c.includes('全部种属'), c.join(' / '))
  check('-80 冻存 · 移植猪：3 批', (await texts(m, '.lqg-count')).join('').includes('共 3 条'), (await texts(m, '.lqg-count')).join(''))
  await tap('鸡')
  check('-80 冻存 · 鸡：演示数据那一批', same(await frozen(), ['T-oco14-JC-T-P1-EM1-5e5']), (await frozen()).join(' / '))
  await m.screenshot({ path: `${SHOTS}/3-ledger-cryo-ji.png` })
  await tap('全部种属')
  check('点回「全部种属」：9 批都回来', (await texts(m, '.lqg-count')).join('').includes('共 9 条'), (await texts(m, '.lqg-count')).join(''))

  // ── ② 质控文档页签（内部）────────────────────────────────────────────────
  console.log('② 质控文档页签：内部多一行「种属」')
  await open('pages/doc/index')
  const labels = await texts(m, '.doc__label')
  check('内部：筛选卡片有「时间 / 类型 / 种属」三行', same(labels, ['时间', '类型', '种属']), labels.join(' / '))
  const speciesRow = m.locator('.doc__row', { has: m.locator('.doc__label', { hasText: '种属' }) })
  const rowChips = (await speciesRow.locator('.doc__chip').allInnerTexts()).map(s => s.trim())
  check('种属一行：全部 / 人 / 鼠兔 / 移植猪 / 鸡 / 未填', same(rowChips, ['全部', '人', '鼠兔', '移植猪', '鸡', '未填']), rowChips.join(' / '))
  const groups = () => texts(m, '.gcd__title')
  check('不筛：三个样本的文档都在', same(await groups(), ['T-hli01', 'T-hli02', 'T-hco04']), (await groups()).join(' / '))
  await speciesRow.locator('.doc__chip', { hasText: /^移植猪$/ }).click(); await wait(m, 2000)
  check('选「移植猪」：只剩 T-hli02 的文档', same(await groups(), ['T-hli02']), (await groups()).join(' / '))
  await m.screenshot({ path: `${SHOTS}/4-doc-tab-zhu.png` })
  await speciesRow.locator('.doc__chip', { hasText: /^鼠兔$/ }).click(); await wait(m, 2000)
  check('选「鼠兔」（没有已完成的文档）：空状态，不报错', (await groups()).length === 0 && !(await m.locator('.lqg-state__text', { hasText: '没能加载' }).count()))

  // ── ③ 质控文档填写列表 ───────────────────────────────────────────────────
  console.log('③ 质控文档填写列表：种属一排 + 卡片上带种属')
  await open('pages/qc/list')
  const qcChips = await texts(m, '.qcl__scroll--species .qcl__chip')
  check('有种属一排', same(qcChips, ['全部种属', '人', '鼠兔', '移植猪', '鸡', '未填']), qcChips.join(' / '))
  await m.locator('.qcl__scroll--species .qcl__chip', { hasText: /^鸡$/ }).click(); await wait(m, 2000)
  check('选「鸡」：T-hco04、T-oco14', same(await texts(m, '.qcl__no'), ['T-hco04', 'T-oco14']), (await texts(m, '.qcl__no')).join(' / '))
  const subs = await texts(m, '.qcl__sub')
  check('卡片副行写着种属', subs.length === 2 && subs.every(s => s.includes('· 鸡 ·')), subs.join(' | '))
  await m.screenshot({ path: `${SHOTS}/5-qc-list-ji.png` })
  await ctx.close()

  // ── 合作单位（王医生）：质控文档页签没有「种属」这一行 ─────────────────────
  console.log('④ 合作单位：质控文档页签没有种属一行')
  const ext = await session(/王医生/)
  await ext.open('pages/doc/index')
  const extLabels = await texts(ext.m, '.doc__label')
  check('合作单位：只有「时间 / 类型」两行', same(extLabels, ['时间', '类型']), extLabels.join(' / '))
  await ext.ctx.close()
}
finally {
  await b.close()
  try { reseed() } catch (e) { console.log('reseed failed', e.message) }
}
console.log(fails.length ? `\n失败 ${fails.length} 项：${fails.join('；')}` : '\n全部通过')
process.exit(fails.length ? 1 : 0)
