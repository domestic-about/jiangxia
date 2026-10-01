// 2026-10-01 飞书问题（小程序行23–24、网页工作台行20）的机器判据：
//   工作台
//     W20 登录后右上角「通知公告」的欢迎语不带 RuoYi-Vue-Plus、带「类器官样本」；后端根路径提示语不带上游名；
//         favicon 不再是上游的拳头图（与上游文件哈希不同）；admin 昵称不是「疯狂的狮子Li」；没有「PLUS官网」菜单
//   小程序 H5（内部身份，390 宽）
//     M23① 质控编辑：「临床诊断 / 既往治疗」「收样描述」和「患者编号」在同一张卡片里
//     M23② 底部：「保存」在左、「完成并同步」在右且是主按钮（更宽）；「预览 ›」是文字按钮；有未保存改动时变灰、
//          右边按钮变「保存并同步」
//     M23③ 多行输入框去掉了默认内边距（disable-default-padding）—— H5 上只能查属性，真机上的对齐要在体验版看
//     M24 石蜡包埋：「琼脂糖包埋样本送样时间」与右边的「请选择」同一行、标题单行
//
// 跑法（本机三件套）：
//   LQG_WEB_BASE=http://127.0.0.1:8082 LQG_MP_BASE=http://127.0.0.1:9100 node doc/waves/regression/staging-hardening/20-feishu-1001.mjs
// 只读：不点保存、不改数据（M23② 改一个字段只为看按钮状态，不保存）。
import { createRequire } from 'node:module'
import { createHash } from 'node:crypto'
import path from 'node:path'

const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const WEB = (process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082').replace(/\/$/, '')
const MP = (process.env.LQG_MP_BASE || 'http://127.0.0.1:9100').replace(/\/$/, '')
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }
const wait = (p, ms) => p.waitForTimeout(ms)
// 上游 RuoYi-Vue-Plus 5.5.3 plus-ui/public/favicon.ico 的 sha256（拳头图）
const UPSTREAM_FAVICON_SHA = process.env.LQG_UPSTREAM_FAVICON_SHA || '04032b7524d319db3e2a1c8e493ada1fb84d65eedd72b3f73fa4ae07fcb9854c'

const b = await chromium.launch()

// ══ 工作台 ══════════════════════════════════════════════════════════════════
console.log(`工作台：${WEB}`)
{
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const w = await ctx.newPage()
  await w.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await w.waitForSelector('.login-form input', { timeout: 40000 })
  const i = w.locator('.login-form input')
  await i.nth(0).fill('admin'); await i.nth(1).fill('admin123')
  await w.click('.login-form button')
  await wait(w, 8000) // 欢迎语是登录后 5 秒经 SSE 推过来的
  await w.locator('.right-menu .el-badge').filter({ has: w.locator('svg') }).first().click().catch(() => {})
  await w.locator('.layout-navbars-breadcrumb-user-news').first().waitFor({ timeout: 5000 }).catch(() => {})
  const news = await w.evaluate(() => [...document.querySelectorAll('.layout-navbars-breadcrumb-user-news .content-box-item')].map(e => e.textContent.replace(/\s+/g, ' ').trim()))
  check('W20 通知公告里有欢迎语', news.some(t => t.includes('欢迎登录')), JSON.stringify(news.slice(0, 3)))
  check('W20 欢迎语不带 RuoYi-Vue-Plus、带「类器官样本」', news.length > 0 && !news.some(t => /ruoyi/i.test(t)) && news.some(t => t.includes('类器官样本')), JSON.stringify(news.slice(0, 3)))

  const api = await w.evaluate(async () => {
    const h = { Authorization: 'Bearer ' + (localStorage.getItem('Admin-Token') || ''), clientid: 'e5cd7e4891bf95d1d19206ce24a7b32e' }
    const root = await fetch('/dev-api/').then(r => r.text()).catch(() => '')
    const info = await fetch('/dev-api/system/user/getInfo', { headers: h }).then(r => r.json()).catch(() => null)
    const routers = await fetch('/dev-api/system/menu/getRouters', { headers: h }).then(r => r.json()).catch(() => null)
    const flat = []
    const walk = (xs) => (xs || []).forEach((x) => { flat.push(`${x.meta?.title || ''}|${x.path || ''}`); walk(x.children) })
    walk(routers?.data)
    return { root, nick: info?.data?.user?.nickName, email: info?.data?.user?.email, menus: flat }
  })
  check('W20 后端根路径提示语不带上游名', api.root && !/ruoyi/i.test(api.root), api.root.slice(0, 60))
  check('W20 admin 昵称不再是「疯狂的狮子Li」', !!api.nick && !api.nick.includes('狮子'), `${api.nick} / ${api.email}`)
  check('W20 没有「PLUS官网」菜单', !api.menus.some(m => /PLUS官网|RuoYi-Vue-Plus/i.test(m)), `${api.menus.length} 个菜单`)
  const bodyText = await w.evaluate(() => document.body.innerText)
  check('W20 页面上没有 RuoYi 字样', !/ruoyi|若依|狮子/i.test(bodyText))

  const fav = await w.evaluate(async () => {
    const href = document.querySelector('link[rel="icon"]')?.getAttribute('href') || '/favicon.ico'
    const buf = await fetch(href).then(r => r.arrayBuffer())
    return [...new Uint8Array(buf)]
  })
  const favSha = createHash('sha256').update(Buffer.from(fav)).digest('hex')
  check('W20 favicon 换掉了（不是上游拳头图）', fav.length > 0 && (!UPSTREAM_FAVICON_SHA || favSha !== UPSTREAM_FAVICON_SHA), `sha256 ${favSha.slice(0, 16)}…`)
  await ctx.close()
}

// ══ 小程序 H5 ════════════════════════════════════════════════════════════════
console.log(`\n小程序 H5：${MP}`)
const ctx = await b.newContext({ viewport: { width: 390, height: 844 } })
const m = await ctx.newPage()

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

check('前置：内部身份登录', !!(await mpLogin(t => t.includes('内部'))))

// M24 石蜡包埋：长标题与值同一行
await m.goto(`${MP}/#/pages/embed/form?mode=new`, { waitUntil: 'networkidle' }); await wait(m, 3000)
const longRow = await m.evaluate((label) => {
  const cell = [...document.querySelectorAll('.wd-cell')].find(c => c.querySelector('.wd-cell__title')?.textContent.trim().startsWith(label))
  if (!cell) return null
  const te = cell.querySelector('.wd-cell__title uni-text, .wd-cell__title text') || cell.querySelector('.wd-cell__title')
  const t = te.getBoundingClientRect()
  const v = cell.querySelector('.fr__val').getBoundingClientRect()
  const range = document.createRange(); range.selectNodeContents(cell.querySelector('.wd-cell__title'))
  const lines = new Set([...range.getClientRects()].filter(r => r.width > 0).map(r => Math.round(r.top / 4))).size
  return { vertical: cell.classList.contains('is-vertical'), lines, titleCy: Math.round(t.top + t.height / 2), valCy: Math.round(v.top + v.height / 2), titleR: Math.round(t.right), valL: Math.round(v.left), fs: getComputedStyle(te).fontSize }
}, '琼脂糖包埋样本送样时间')
check('M24 「琼脂糖包埋样本送样时间」不再上下排', !!longRow && !longRow.vertical, JSON.stringify(longRow))
check('M24 标题单行', !!longRow && longRow.lines === 1, JSON.stringify(longRow))
check('M24 长标题字号收一档（14px）', !!longRow && longRow.fs === '14px', JSON.stringify(longRow))
check('M24 标题与「请选择」同一行、不重叠', !!longRow && Math.abs(longRow.titleCy - longRow.valCy) <= 6 && longRow.valL >= longRow.titleR - 1, JSON.stringify(longRow))

// M23 质控编辑
await m.goto(`${MP}/#/pages/qc/list`, { waitUntil: 'networkidle' }); await wait(m, 3000)
await m.locator('.qcl__item').first().click(); await wait(m, 3500)
const card = await m.evaluate(() => {
  const groups = [...document.querySelectorAll('.qce__group')].filter(g => g.offsetParent !== null)
  const labelsOf = g => [...g.querySelectorAll('.wd-input__label-inner, .wd-cell__title, .wd-textarea__label-inner, .qce__viab-l')].map(e => e.textContent.trim())
  return labelsOf(groups[0])
})
check('M23① 临床诊断 / 收样描述 与患者编号同一张卡片', ['患者编号', '临床诊断 / 既往治疗', '收样描述'].every(l => card.includes(l)), JSON.stringify(card))

const bar = () => m.evaluate(() => {
  const r = (sel) => { const e = document.querySelector(sel); if (!e) return null; const b = e.getBoundingClientRect(); return { l: Math.round(b.left), w: Math.round(b.width), text: e.textContent.trim(), tag: e.tagName.toLowerCase(), color: getComputedStyle(e).color, bg: getComputedStyle(e).backgroundColor, off: e.className.includes('--off') } }
  return { save: r('.qce__btn--save'), pub: r('.qce__btn--publish'), preview: r('.qce__preview'), primary: !!document.querySelector('.qce__btn--publish.qce__btn--p') }
})
let st = await bar()
const published = st.pub?.text === '撤回'
check('M23② 「保存」在左、同步按钮在右', !!st.save && !!st.pub && st.save.l < st.pub.l, JSON.stringify([st.save, st.pub]))
if (!published) {
  check('M23② 「完成并同步」是主按钮且更宽', st.primary && st.pub.w > st.save.w && st.pub.text === '完成并同步', JSON.stringify(st.pub))
}
check('M23② 「预览」是文字按钮（不是 button）', !!st.preview && st.preview.tag !== 'button' && st.preview.text.startsWith('预览'), JSON.stringify(st.preview))
check('M23② 没改动时「预览」可点（不灰）', !!st.preview && !st.preview.off, JSON.stringify(st.preview))
// 改一个字段（不保存）→ 预览变灰、右边按钮变「保存并同步」
const first = m.locator('.qce__group').first().locator('input').first()
const orig = await first.inputValue()
await first.fill(`${orig}x`); await wait(m, 400)
st = await bar()
check('M23② 有改动时「预览」变灰', !!st.preview && st.preview.off, JSON.stringify(st.preview))
if (!published) {
  check('M23② 有改动时右边按钮变「保存并同步」', st.pub?.text === '保存并同步', JSON.stringify(st.pub))
}
await first.fill(orig); await wait(m, 300)

// M23③ disable-default-padding（H5 上 uni 的 textarea 没有原生内边距，这里只确认属性已下发到组件）
const ta = await m.evaluate(() => [...document.querySelectorAll('.wd-textarea')].filter(e => e.offsetParent !== null).map((e) => {
  const inner = e.querySelector('textarea')
  const cs = inner ? getComputedStyle(inner) : null
  return { pt: cs?.paddingTop, pb: cs?.paddingBottom }
}))
check('M23③ 多行输入框没有上下内边距', ta.length > 0 && ta.every(x => x.pt === '0px' && x.pb === '0px'), JSON.stringify(ta.slice(0, 3)))

// 顺带（10-01 截图复查时发现）：所有表单的输入框占位提示都完整显示、不被截断；必填星号都在标题右边
const clipped = () => m.evaluate(() => [...document.querySelectorAll('.uni-input-placeholder, .uni-textarea-placeholder')]
  .filter(e => e.offsetParent !== null && e.textContent.trim())
  .filter(e => e.scrollWidth > e.clientWidth + 1)
  .map(e => e.textContent.trim()))
const leftStars = () => m.evaluate(() => [...document.querySelectorAll('.wd-cell__required--left, .wd-input__required--left, .wd-textarea__required--left')]
  .filter(e => e.offsetParent !== null).length)
async function scanForm(tag, url) {
  await m.goto(`${MP}/#/${url}`, { waitUntil: 'networkidle' }); await wait(m, 3000)
  const c = await clipped()
  check(`顺带 ${tag}：占位提示没有被截断`, c.length === 0, c.join('；'))
  check(`顺带 ${tag}：必填星号都在标题右边`, (await leftStars()) === 0)
}
await m.setViewportSize({ width: 375, height: 760 })
for (const [tag, url] of [['样本记录信息表', 'pages/sample/form?mode=new'], ['类器官送样记录', 'pages/organoid/form?mode=new'], ['石蜡包埋', 'pages/embed/form?mode=new'], ['-80 冻存', 'pages/cryo/form?mode=new']]) {
  await scanForm(tag, url)
}
check('前置：外部身份登录', !!(await mpLogin(t => t.includes('外部'))))
for (const [tag, url] of [['外部·样本记录信息表', 'pages/sample/form?mode=new'], ['外部·类器官送样', 'pages/organoid/form?mode=new'], ['外部·石蜡包埋送样', 'pages/embed/form?mode=new']]) {
  await scanForm(tag, url)
}

await b.close()
console.log(fails.length ? `\n✗ ${fails.length} 项没过：${fails.join(' / ')}` : '\n✓ 全部通过')
process.exit(fails.length ? 1 : 0)
