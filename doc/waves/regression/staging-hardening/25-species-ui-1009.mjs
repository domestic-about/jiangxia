// CR-20261009-18「种属」界面回归（本机：小程序 H5 :9100、工作台 :8082、后端 :8081、库 lqg-dev-postgres）。
//   node doc/waves/regression/staging-hardening/25-species-ui-1009.mjs
// 小程序：外部（王医生）新增组织样本 —— 种属一格紧跟来源单位、带必填星；不选拦下；面板四个常用值 + 手填；提交后库里是选的值。
// 工作台：样本 / 石蜡 / 冻存 / 质控文档四张列表都有「种属」列与筛选；样本抽屉有种属下拉。
// 截图在 shots-25/。会建一条外部组织样本（供体姓名 R25-…），结束时删掉。
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'

const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const MP = (process.env.LQG_MP_BASE || 'http://127.0.0.1:9100').replace(/\/$/, '')
const WEB = (process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082').replace(/\/$/, '')
const PG = process.env.LQG_DEV_PG_CONTAINER || 'lqg-dev-postgres'
const SHOTS = 'doc/waves/regression/staging-hardening/shots-25'
fs.mkdirSync(SHOTS, { recursive: true })
const TAG = `R25-${Date.now() % 1000000}`
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }
const wait = (p, ms) => p.waitForTimeout(ms)
const sql = q => execFileSync('docker', ['exec', PG, 'psql', '-U', 'lqg', '-d', 'lqg_dev', '-Atc', q], { encoding: 'utf8' }).trim()
const cleanup = () => sql(`delete from t_lqg_sample where tissue_type = '${TAG}'`)

// 没装 Playwright 自带的浏览器（换机器后常见）时用本机的 Google Chrome
const b = await chromium.launch().catch(() => chromium.launch({ channel: 'chrome' }))
try {
  // ── 小程序 H5（外部 · A 医院已核验）──────────────────────────────────────
  console.log('小程序 · 外部新增组织样本')
  const ctx = await b.newContext({ viewport: { width: 375, height: 667 } })
  const m = await ctx.newPage()
  await m.goto(`${MP}/#/pages/login/index`, { waitUntil: 'networkidle' }); await wait(m, 2500)
  const box = m.locator('.login__box'); if (await box.count()) { await box.first().click(); await wait(m, 300) }
  const btns = m.locator('.login__mock-btn'); const texts = []
  for (let k = 0; k < await btns.count(); k++) texts.push((await btns.nth(k).innerText()).replace(/\s+/g, ' '))
  const idx = texts.findIndex(t => /王医生/.test(t))
  check('找到外部 · 王医生（A 医院 · 已核验）的测试登录', idx >= 0, idx < 0 ? texts.join(' | ') : texts[idx])
  const me = m.waitForResponse(r => r.url().includes('/mp/me'), { timeout: 40000 }).catch(() => null)
  await btns.nth(Math.max(idx, 0)).click(); await me; await wait(m, 2500)

  await m.goto(`${MP}/#/pages/sample/form?mode=new`, { waitUntil: 'networkidle' }); await m.reload({ waitUntil: 'networkidle' }); await wait(m, 3000)
  const titles = await m.locator('.lqg-gl').first().locator('xpath=following-sibling::*[1]').locator('.wd-cell__title, .wd-input__label').allInnerTexts()
  const clean = titles.map(t => t.replace(/\s|\*/g, ''))
  check('送检信息：「种属」紧跟「来源单位」', clean[0] === '来源单位' && clean[1] === '种属', clean.slice(0, 4).join(' / '))
  const speciesCell = m.locator('.wd-cell', { has: m.locator('.wd-cell__title', { hasText: '种属' }) }).first()
  check('种属格带必填星', (await speciesCell.innerHTML()).includes('fr__req') || /\*/.test(await speciesCell.innerText()))
  await m.screenshot({ path: `${SHOTS}/mp-1-form.png` })

  // 不选种属直接提交 → 拦下
  await m.locator('.wd-input', { has: m.locator('.wd-input__label', { hasText: '供体姓名' }) }).first().locator('input').fill('回归供体')
  await m.locator('.wd-input', { has: m.locator('.wd-input__label', { hasText: '组织类型' }) }).first().locator('input').fill(TAG)
  await m.locator('.form__btn').click(); await wait(m, 500)
  const toast = await m.locator('.uni-toast, .uni-sample-toast').allInnerTexts().catch(() => [])
  check('不选种属 → 提示「请选择种属」', toast.join(' ').includes('请选择种属'), toast.join(' '))
  await wait(m, 1800)

  // 面板：一个输入框既搜索又能直接用输入的字（不分「选」「手填」两种模式）
  const rowTexts = async () => (await m.locator('.sps__row').allInnerTexts()).map(s => s.trim())
  const sheetInput = () => m.locator('.sps__search input')
  await speciesCell.click(); await wait(m, 800)
  check('面板：四个常用值一行一个、没有「手动填写」切换', JSON.stringify(await rowTexts()) === JSON.stringify(['人', '鼠兔', '移植猪', '鸡']), (await rowTexts()).join(' / '))
  check('面板：打开时不弹键盘（输入框没有自动聚焦）', !(await sheetInput().evaluate(el => el === document.activeElement)))
  await m.screenshot({ path: `${SHOTS}/mp-2-sheet.png` })
  await sheetInput().fill('猪'); await wait(m, 400)
  let sheetRows = await rowTexts()
  check('输入「猪」：过滤到「移植猪」，顶上出「使用「猪」」', sheetRows[0] === '使用「猪」' && sheetRows[1] === '移植猪' && sheetRows.length === 2, sheetRows.join(' / '))
  await m.screenshot({ path: `${SHOTS}/mp-3-filter.png` })
  await sheetInput().fill('食蟹猴'); await wait(m, 400)
  sheetRows = await rowTexts()
  check('输入字典外的「食蟹猴」：只剩「使用「食蟹猴」」', JSON.stringify(sheetRows) === JSON.stringify(['使用「食蟹猴」']), sheetRows.join(' / '))
  await m.screenshot({ path: `${SHOTS}/mp-4-create.png` })
  await m.locator('.sps__row--create').click(); await wait(m, 800)
  check('点「使用」：格子显示「食蟹猴」、面板收起', (await speciesCell.innerText()).includes('食蟹猴') && !(await m.locator('.sps__row').first().isVisible().catch(() => false)))
  // 再打开：手填的值也列出来、打勾
  await speciesCell.click(); await wait(m, 800)
  sheetRows = await rowTexts()
  const on = (await m.locator('.sps__row--on').allInnerTexts()).map(s => s.trim())
  check('再打开：手填的「食蟹猴」排在最后、打勾', sheetRows.at(-1) === '食蟹猴' && JSON.stringify(on) === JSON.stringify(['食蟹猴']), sheetRows.join(' / '))
  await m.screenshot({ path: `${SHOTS}/mp-5-reopen.png` })
  // 键盘「完成」：输入「鸡」回车 = 选「鸡」
  await sheetInput().fill('鸡'); await sheetInput().press('Enter'); await wait(m, 800)
  check('输入「鸡」按完成：选中「鸡」并收起', (await speciesCell.innerText()).includes('鸡'))
  // 点一行就选中：改选「移植猪」
  await speciesCell.click(); await wait(m, 800)
  await m.locator('.sps__row', { hasText: '移植猪' }).click(); await wait(m, 800)
  check('点「移植猪」一行：格子显示它、面板收起', (await speciesCell.innerText()).includes('移植猪') && !(await m.locator('.sps__row').first().isVisible().catch(() => false)))
  await m.screenshot({ path: `${SHOTS}/mp-6-picked.png` })
  const saved = m.waitForResponse(r => r.url().includes('/mp/ext/sample') && r.request().method() === 'POST', { timeout: 20000 }).catch(() => null)
  await m.locator('.form__btn').click(); const resp = await saved; await wait(m, 1500)
  const body = resp ? await resp.json().catch(() => null) : null
  check('提交成功', body?.code === 200, JSON.stringify(body))
  check('库里 species = 移植猪', sql(`select species from t_lqg_sample where tissue_type = '${TAG}'`) === '移植猪')
  await ctx.close()

  // ── 工作台 ─────────────────────────────────────────────────────────────
  console.log('工作台 · 四张列表与样本抽屉')
  const wctx = await b.newContext({ viewport: { width: 1280, height: 860 } })
  const p = await wctx.newPage()
  await p.goto(`${WEB}/login`, { waitUntil: 'networkidle' })
  await p.waitForSelector('.login-form input', { timeout: 40000 })
  await p.locator('.login-form input').nth(0).fill('lqgadmin'); await p.locator('.login-form input').nth(1).fill('admin123')
  await p.click('.login-form button')
  await p.waitForFunction(() => !location.pathname.includes('login'), null, { timeout: 60000 }); await wait(p, 2500)
  const heads = async () => (await p.locator('.el-table__header-wrapper th').allInnerTexts()).map(s => s.trim()).filter(Boolean)
  /** 筛选区：标签中心点上是不是别的控件（日期区间以前会盖住右边那一格的标签 / 按钮） */
  const coveredLabels = () => p.evaluate(() => [...document.querySelectorAll('.el-form-item')].filter((fi) => {
    const l = fi.querySelector('.el-form-item__label')
    if (!l || !l.textContent.trim()) return false
    const r = l.getBoundingClientRect()
    const hit = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2)
    return hit && !l.contains(hit) && !hit.contains(l)
  }).map(fi => fi.querySelector('.el-form-item__label').textContent.trim()))
  const filterLabels = async () => (await p.locator('.el-form-item__label').allInnerTexts()).map(s => s.trim())

  await p.goto(`${WEB}/sample`, { waitUntil: 'networkidle' }); await wait(p, 2500)
  let h = await heads()
  check('样本记录信息表：表头「种属」紧跟「来源单位」', h[h.indexOf('来源单位') + 1] === '种属', h.slice(0, 8).join(' / '))
  check('样本记录信息表：筛选里有「种属」', (await filterLabels()).includes('种属'))
  // 筛选：下拉里有「未填」+ 四个常用值
  const fItem = p.locator('.el-form-item', { has: p.locator('.el-form-item__label', { hasText: /^种属$/ }) }).first()
  await fItem.locator('.el-select').click(); await wait(p, 600)
  const opts = (await p.locator('.el-select-dropdown:visible .el-select-dropdown__item').allInnerTexts()).map(s => s.trim())
  check('筛选下拉：未填 / 人 / 鼠兔 / 移植猪 / 鸡', ['未填', '人', '鼠兔', '移植猪', '鸡'].every(o => opts.includes(o)), opts.join(' / '))
  await p.locator('.el-select-dropdown:visible .el-select-dropdown__item', { hasText: '移植猪' }).click(); await wait(p, 300)
  await p.locator('button', { hasText: '搜索' }).first().click(); await wait(p, 2000)
  const rows = await p.locator('.el-table__body-wrapper tbody tr').count()
  const col = h.indexOf('种属')
  const cells = await p.locator(`.el-table__body-wrapper tbody tr td:nth-child(${col + 1})`).allInnerTexts()
  check('按「移植猪」筛：每行种属都是移植猪', rows > 0 && cells.every(c => c.trim() === '移植猪'), `${rows} 行：${cells.slice(0, 5).join(',')}`)
  await p.screenshot({ path: `${SHOTS}/web-1-sample-list.png` })
  const covered = await coveredLabels()
  check('样本记录信息表：筛选区没有标签被盖住', covered.length === 0, covered.join(' / '))
  // 新增抽屉
  await p.locator('button', { hasText: /新增/ }).first().click(); await wait(p, 1200)
  const drawer = p.locator('.el-drawer:visible')
  const dl = (await drawer.locator('.el-form-item__label').allInnerTexts()).map(s => s.trim())
  check('样本抽屉：有「种属」一项', dl.includes('种属'), dl.join(' / '))
  await drawer.locator('button', { hasText: /保存|提交|确定/ }).last().click().catch(() => {}); await wait(p, 800)
  const errs = (await drawer.locator('.el-form-item__error').allInnerTexts()).join(' ')
  check('不选种属保存 → 字段下方提示', errs.includes('请选择或输入种属'), errs)
  await p.screenshot({ path: `${SHOTS}/web-2-sample-drawer.png` })
  await p.keyboard.press('Escape'); await wait(p, 600)
  const discard = p.locator('.el-message-box button', { hasText: '不保存' }); if (await discard.count()) await discard.click()

  for (const [url, name, anchor, label] of [['/embed', '石蜡包埋', '样本编号', '紧跟「样本编号」'], ['/cryo', '-80 冻存', '当前剩余/支', '在「当前剩余/支」后'], ['/qc-docs', '质控文档', '来源单位', '紧跟「来源单位」']]) {
    await p.goto(`${WEB}${url}`, { waitUntil: 'networkidle' }); await wait(p, 2500)
    h = await heads()
    check(`${name}：表头「种属」${label}`, h[h.indexOf(anchor) + 1] === '种属', h.join(' / ').slice(0, 160))
    check(`${name}：筛选里有「种属」`, (await filterLabels()).includes('种属'))
    const hidden = await coveredLabels()
    check(`${name}：筛选区没有标签被盖住`, hidden.length === 0, hidden.join(' / '))
    await p.screenshot({ path: `${SHOTS}/web-${name}.png` })
  }
  await wctx.close()
}
finally {
  try { cleanup() } catch (e) { console.log('cleanup failed', e.message) }
  await b.close()
}
console.log(fails.length ? `\n失败 ${fails.length} 项：${fails.join('；')}` : '\n全部通过')
process.exit(fails.length ? 1 : 0)
