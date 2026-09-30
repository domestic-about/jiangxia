// 飞书测试问题表 2026-09-30 一轮（Kevin 拍板：工作台加「质控文档」板块、其余未改的全修、改名「类器官送样记录」）的判据。
//
// 真 DOM + 真网络（不是源码 grep）：
//   工作台  ① 左侧菜单有「质控文档」「类器官送样记录」，没有「类器官收样记录」
//          ② 质控文档列表：有行、每行三格状态、筛选「已全部完成」只剩进度=已全部完成的行
//          ③ 点「进入」到编辑页（带 from=qc-docs），「返回质控文档」回到列表
//          ④ 新增冻存批次 / 新增石蜡包埋 / 编辑样本 三个抽屉里，所有表单标签都是单行、没有被裁
//          ⑤ 导出「类器官送样记录」：真下载 xlsx，工作表名是新名字、字体是微软雅黑
//   小程序 H5  ⑥ 底部页签叫「质控文档」，类型筛选里是全称「类器官质量评分」
//          ⑦ 石蜡包埋 / -80 冻存 表单：长标签不掉单字（标签只占一行）
//          ⑧ 首页入口叫「类器官送样记录」；合作单位首页第三格占满整行
//
// 跑法（本机三件套：bash .tmp/local-env/local.sh up）：
//   LQG_WEB_BASE=http://127.0.0.1:8082 LQG_MP_BASE=http://127.0.0.1:9100 LQG_API_BASE=http://127.0.0.1:8081 \
//     node doc/waves/regression/staging-hardening/17-feishu-0930.mjs
// ★ ⑧ 的「第三格占满」在 H5 上没有自定义组件宿主节点，所以 H5 通过**不能**证明真机也好了；
//   真机那一半要在体验版上看（见 EntryGrid.vue 的注释）。
import { createRequire } from 'node:module'
import path from 'node:path'
import fs from 'node:fs'
import os from 'node:os'
import { execFileSync } from 'node:child_process'

const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const WEB = (process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082').replace(/\/$/, '')
const MP = (process.env.LQG_MP_BASE || 'http://127.0.0.1:9100').replace(/\/$/, '')
const API = (process.env.LQG_API_BASE || 'http://127.0.0.1:8081').replace(/\/$/, '')
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }
const wait = (p, ms) => p.waitForTimeout(ms)

const b = await chromium.launch()

// ══ 工作台 ══════════════════════════════════════════════════════════════════
console.log(`工作台：${WEB}`)
const wctx = await b.newContext({ viewport: { width: 1440, height: 900 }, acceptDownloads: true })
const w = await wctx.newPage()
await w.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
await w.waitForSelector('.login-form input', { timeout: 40000 })
const inputs = w.locator('.login-form input')
await inputs.nth(0).fill('lqgadmin'); await inputs.nth(1).fill('admin123')
await w.click('.login-form button'); await wait(w, 4000)

// ① 菜单
const menus = await w.evaluate(() => [...document.querySelectorAll('.sidebar-container .el-menu-item, .sidebar-container .el-sub-menu__title')].map(e => e.textContent.replace(/\s+/g, '').replace(/\d+$/, '')))
check('① 左侧菜单有「质控文档」', menus.some(t => t.startsWith('质控文档')), menus.join(' / '))
check('① 左侧菜单有「类器官送样记录」', menus.some(t => t.startsWith('类器官送样记录')))
check('① 左侧菜单没有旧名「类器官收样记录」', !menus.some(t => t.includes('收样记录')))

// ② 质控文档列表
const listResp = w.waitForResponse(r => r.url().includes('/dev-api/lqg/qc/list'), { timeout: 20000 }).catch(() => null)
await w.goto(`${WEB}/qc-docs`, { waitUntil: 'domcontentloaded' })
const lr = await listResp
const listJson = lr ? await lr.json().catch(() => null) : null
await wait(w, 1500)
const table = await w.evaluate(() => {
  const rows = [...document.querySelectorAll('.lqg-qclist .el-table__body-wrapper .el-table__row')]
  return {
    title: document.querySelector('.lqg-qclist__title')?.textContent.trim(),
    rows: rows.length,
    tagsPerRow: rows.map(r => r.querySelectorAll('.el-tag').length),
    heads: [...document.querySelectorAll('.lqg-qclist .el-table__header th')].map(th => th.textContent.trim()),
  }
})
check('② 列表接口 200 且有数据', listJson?.code === 200 && (listJson?.total ?? 0) > 0, `code=${listJson?.code} total=${listJson?.total}`)
check('② 页面标题是「质控文档」', table.title === '质控文档', table.title)
check('② 表格有行', table.rows > 0, `行数 ${table.rows}`)
check('② 每行三格状态（样本质控 / 类器官质控 / 质量评分）', table.tagsPerRow.length > 0 && table.tagsPerRow.every(n => n === 3), JSON.stringify(table.tagsPerRow.slice(0, 6)))
check('② 表头含三份表名', ['样本质控表', '类器官质控表', '类器官质量评分表'].every(h => table.heads.includes(h)), table.heads.join(' / '))

// ② 按进度筛选：接口真的只回这一种进度
for (const progress of ['none', 'doing', 'done']) {
  const r = await w.evaluate(async ({ api, progress }) => {
    const res = await fetch(`${api}/lqg/qc/list?progress=${progress}&pageNum=1&pageSize=100`, {
      // plus-ui 把 token 放在 localStorage 的 Admin-Token（useStorage 存的是原串）；clientid 取自 .env.development
      headers: { Authorization: 'Bearer ' + (localStorage.getItem('Admin-Token') || ''), clientid: 'e5cd7e4891bf95d1d19206ce24a7b32e' },
    }).catch(() => null)
    return res ? res.json().catch(() => null) : null
  }, { api: '/dev-api', progress })
  const rows = r?.rows ?? []
  check(`② 筛选 progress=${progress} 只回这一种进度`, r?.code === 200 && rows.every(x => x.progress === progress), `code=${r?.code} 行数=${rows.length}`)
}

// ③ 进入 → 返回
const firstLink = w.locator('.lqg-qclist .el-table__body-wrapper .el-table__row').first().locator('button:has-text("进入")')
await firstLink.click(); await wait(w, 2500)
const editorUrl = w.url()
check('③ 点「进入」到质控文档编辑页并带 from=qc-docs', editorUrl.includes('/qc-console/qc-editor') && editorUrl.includes('from=qc-docs'), editorUrl)
const backText = (await w.locator('.lqg-qc-editor__back').textContent().catch(() => '') || '').trim()
check('③ 编辑页的返回链接写「返回质控文档」', backText.includes('返回质控文档'), backText)
await w.locator('.lqg-qc-editor__back').click(); await wait(w, 2000)
check('③ 点返回回到质控文档列表', w.url().includes('/qc-docs'), w.url())

// ④ 抽屉标签不折行、不被裁
async function labelsSingleLine(name) {
  return w.evaluate((name) => {
    const d = [...document.querySelectorAll('.el-drawer')].find(x => x.getBoundingClientRect().width > 0)
    if (!d) return { name, found: false }
    const labels = [...d.querySelectorAll('.el-form-item__label')].filter(l => l.getBoundingClientRect().width > 0 && l.textContent.trim())
    const bad = labels.filter(l => {
      const lh = parseFloat(getComputedStyle(l).lineHeight) || 32
      return l.scrollWidth > l.clientWidth + 1 || l.getBoundingClientRect().height > lh * 1.5
    }).map(l => `${l.textContent.trim()}(${l.scrollWidth}/${l.clientWidth}px,h${Math.round(l.getBoundingClientRect().height)})`)
    return { name, found: true, count: labels.length, bad }
  }, name)
}
async function closeDrawer() {
  await w.keyboard.press('Escape'); await wait(w, 800)
}
for (const [name, url, btn] of [
  ['新增冻存批次', '/cryo', 'button:has-text("新增")'],
  ['新增石蜡包埋', '/embed', 'button:has-text("新增")'],
  ['新增样本', '/sample', 'button:has-text("新增")'],
]) {
  await w.goto(`${WEB}${url}`, { waitUntil: 'domcontentloaded' }); await wait(w, 2500)
  await w.locator(btn).first().click().catch(() => {}); await wait(w, 1500)
  const r = await labelsSingleLine(name)
  check(`④ ${name}抽屉：标签全部单行、不被裁`, r.found && r.bad.length === 0, r.found ? `标签 ${r.count} 个${r.bad.length ? '；有问题：' + r.bad.join('、') : ''}` : '抽屉没打开')
  await closeDrawer()
}

// ⑤ 导出：真下载一份「类器官送样记录」
await w.goto(`${WEB}/sample-organoid`, { waitUntil: 'domcontentloaded' }); await wait(w, 2500)
const dl = w.waitForEvent('download', { timeout: 30000 }).catch(() => null)
await w.locator('button:has-text("导出")').first().click().catch(() => {})
const download = await dl
if (download) {
  const file = path.join(os.tmpdir(), `lqg-organoid-export-${Date.now()}.xlsx`)
  await download.saveAs(file)
  const workbook = execFileSync('unzip', ['-p', file, 'xl/workbook.xml']).toString()
  const styles = execFileSync('unzip', ['-p', file, 'xl/styles.xml']).toString()
  const sheet1 = execFileSync('unzip', ['-p', file, 'xl/worksheets/sheet1.xml']).toString()
  check('⑤ 导出文件名是「类器官送样记录…」', download.suggestedFilename().includes('类器官送样记录'), download.suggestedFilename())
  check('⑤ 工作表名是「类器官送样记录」', workbook.includes('name="类器官送样记录"'), (workbook.match(/<sheet [^>]*name="([^"]+)"/) || [])[1])
  check('⑤ 字体统一「微软雅黑」', styles.includes('微软雅黑') && !/name val="宋体"/.test(styles))
  check('⑤ 有细框线', /<left style="thin"/.test(styles))
  check('⑤ 表头行冻结', /<pane [^>]*ySplit="1(\.0)?"/.test(sheet1))
  check('⑤ 设了列宽', /<col [^>]*customWidth="(1|true)"/.test(sheet1))
  fs.rmSync(file, { force: true })
} else {
  check('⑤ 导出触发了下载', false, '没等到下载事件')
}
await wctx.close()

// ══ 小程序 H5 ════════════════════════════════════════════════════════════════
console.log(`\n小程序 H5：${MP}`)
const mctx = await b.newContext({ viewport: { width: 390, height: 844 } })
const m = await mctx.newPage()
async function mpLogin(match) {
  await m.goto(`${MP}/#/pages/login/index`, { waitUntil: 'networkidle' }); await wait(m, 2500)
  const box = m.locator('.login__box')
  if (await box.count()) { await box.first().click(); await wait(m, 300) }
  const btns = m.locator('.login__mock-btn')
  const n = await btns.count()
  for (let i = 0; i < n; i++) {
    const t = (await btns.nth(i).innerText()).replace(/\s+/g, ' ')
    if (match(t)) {
      const me = m.waitForResponse(r => r.url().includes('/mp/me'), { timeout: 40000 }).catch(() => null)
      await btns.nth(i).click(); await me; await wait(m, 2500)
      return t
    }
  }
  return null
}
const who = await mpLogin(t => t.includes('内部'))
check('前置：小程序用内部身份登录', !!who && !m.url().includes('login'), who || m.url())

// ⑧ 首页入口名
const tiles = await m.evaluate(() => [...document.querySelectorAll('.lqg-tile__t')].map(e => e.textContent.trim()))
check('⑧ 首页入口叫「类器官送样记录」', tiles.includes('类器官送样记录') && !tiles.some(t => t.includes('收样')), tiles.join(' / '))

// ⑥ 页签与筛选
const tabText = await m.evaluate(() => [...document.querySelectorAll('.uni-tabbar__item')].map(e => e.textContent.trim()))
check('⑥ 底部页签叫「质控文档」', tabText.includes('质控文档'), tabText.join(' / '))
await m.goto(`${MP}/#/pages/doc/index`, { waitUntil: 'networkidle' }); await wait(m, 2500)
const chips = await m.evaluate(() => [...document.querySelectorAll('.doc__chip')].map(e => e.textContent.trim()))
check('⑥ 类型筛选是全称「类器官质量评分」', chips.includes('类器官质量评分'), chips.join(' / '))
const section = await m.evaluate(() => document.querySelector('.lqg-gl')?.textContent.trim())
check('⑥ 板块标题是「类器官样本质量控制记录表」', section === '类器官样本质量控制记录表', section)

// ⑦ 长标签只占一行
async function labelLines(url, labels) {
  await m.goto(`${MP}/#/${url}`, { waitUntil: 'networkidle' }); await wait(m, 2500)
  return m.evaluate((labels) => labels.map((text) => {
    const el = [...document.querySelectorAll('.wd-cell__title text, .wd-cell__title')].find(e => e.textContent.trim() === text)
    if (!el) return { text, found: false }
    const r = el.getBoundingClientRect()
    const lh = parseFloat(getComputedStyle(el).lineHeight) || parseFloat(getComputedStyle(el).fontSize) * 1.4
    return { text, found: true, h: Math.round(r.height), lh: Math.round(lh), oneLine: r.height <= lh * 1.5 }
  }), labels)
}
for (const [url, labels] of [
  ['pages/embed/form?mode=new', ['琼脂糖包埋样本送样时间', '琼脂糖包埋样本时间']],
  ['pages/cryo/form?mode=new', ['-80度超低温冰箱转移至液氮时间', '暂存-80度超低温冰箱']],
]) {
  const res = await labelLines(url, labels)
  for (const r of res) {
    check(`⑦ ${url}：「${r.text}」只占一行`, r.found && r.oneLine, r.found ? `高 ${r.h}px / 行高 ${r.lh}px` : '没找到这个标签')
  }
}

// ⑧ 合作单位首页第三格占满整行（H5 侧）
const ext = await mpLogin(t => t.includes('外部'))
if (ext) {
  await m.goto(`${MP}/#/pages/index/index`, { waitUntil: 'networkidle' }); await wait(m, 2500)
  const g = await m.evaluate(() => {
    const grid = document.querySelector('.lqg-grid')
    const tiles = [...document.querySelectorAll('.lqg-grid .lqg-tile')]
    return { grid: grid?.getBoundingClientRect().width ?? 0, widths: tiles.map(t => Math.round(t.getBoundingClientRect().width)), n: tiles.length }
  })
  const last = g.widths[g.widths.length - 1]
  check('⑧ 合作单位首页第三格占满整行（H5）', g.n === 3 && last > g.widths[0] * 1.6, `格宽 ${g.widths.join(' / ')}，网格 ${Math.round(g.grid)}`)
} else {
  check('⑧ 能用外部身份登录', false)
}
await mctx.close()
await b.close()

console.log(`\n结果：${fails.length ? `FAIL（失败 ${fails.length} 条）` : 'PASS（失败 0 条）'}`)
if (fails.length) { console.log('失败项：' + fails.join(' / ')); process.exit(1) }
