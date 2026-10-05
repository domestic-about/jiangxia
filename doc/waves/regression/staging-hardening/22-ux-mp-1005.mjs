// 2026-10-05 小程序对抗性 UX 测试（MP-xx）里「明确是缺陷」那一批的机器判据（H5，内部 / 外部身份，375 宽）：
//   MP-01 连点「提交」只发一次 POST、库里只多一条
//   MP-02 提交成功后表单页不留在页面栈里：返回回不到填满的表单
//   MP-05 提交后落在历史编辑记录的对应页签（-80 冻存）
//   MP-08 刚新建、没改过的记录在历史里标「新增」不是「修改」
//   MP-03 长标签那一行的占位不折行（375 / 320 宽）
//   MP-11 选择类格子的值超长时不把「›」挤掉
//   MP-09 冻存批次弹层里取用登记的「改」「删」点击区 ≥ 40×40、两块点击区不重叠
//   MP-18 冻存表里没有「已超 0 天」
//   MP-15 石蜡包埋 marker 行是白底（按钮块在上面看得出来）
//   MP-16 外部身份深链进内部管理：不画页签 / 筛选；进 -80 冻存填写页：说「只给中心内部人员开放」，不说「没能确认你的身份」
//   MP-04 「离开前询问」只在小程序生效，H5 测不了 —— 需真机确认
//
// 跑法（本机三件套）：
//   LQG_MP_BASE=http://127.0.0.1:9100 node doc/waves/regression/staging-hardening/22-ux-mp-1005.mjs
// ★ 会往 dev 库写冻存批次（名称以 R22- 开头），结束时硬删掉；只对本机跑。
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'
import path from 'node:path'

const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const MP = (process.env.LQG_MP_BASE || 'http://127.0.0.1:9100').replace(/\/$/, '')
const PG = process.env.LQG_DEV_PG_CONTAINER || 'lqg-dev-postgres'
const SAMPLE_ID = '9000001001'
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }
const wait = (p, ms) => p.waitForTimeout(ms)
const sql = q => execFileSync('docker', ['exec', PG, 'psql', '-U', 'lqg', '-d', 'lqg_dev', '-Atc', q], { encoding: 'utf8' }).trim()
const cleanup = () => {
  sql("delete from t_lqg_cryo_flow where batch_id in (select id from t_lqg_cryo_batch where cryo_name like 'R22-%')")
  sql("delete from t_lqg_cryo_batch where cryo_name like 'R22-%'")
}

const b = await chromium.launch()
const ctx = await b.newContext({ viewport: { width: 375, height: 667 } })
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
/** 打开一页并整页重载（H5 只改 hash 到同一路由时会复用页面实例） */
async function open(url) {
  await m.goto(`${MP}/#/${url}`, { waitUntil: 'networkidle' })
  await m.reload({ waitUntil: 'networkidle' }); await wait(m, 3000)
}
async function pickDate(label) {
  await m.locator('.wd-cell', { has: m.locator('.wd-cell__title', { hasText: label }) }).first().click()
  await wait(m, 800)
  await m.locator('.wd-datetime-picker__action:not(.wd-datetime-picker__action--cancel)').filter({ visible: true }).first().click()
  await wait(m, 800)
}
async function fillText(label, value) {
  await m.locator('.wd-input', { has: m.locator('.wd-input__label', { hasText: label }) }).first().locator('input').fill(value)
}
/** 长标签那一行的占位：是不是单行（高度不超过一行半） */
const phOneLine = () => m.evaluate(() => {
  const cell = [...document.querySelectorAll('.wd-cell')].find(c => c.querySelector('.wd-cell__title')?.textContent.trim().startsWith('-80度超低温冰箱转移至液氮时间'))
  const ph = cell?.querySelector('.fr__ph')
  if (!ph) return null
  const r = ph.getBoundingClientRect(); const lh = parseFloat(getComputedStyle(ph).lineHeight) || parseFloat(getComputedStyle(ph).fontSize) * 1.4
  return { h: Math.round(r.height), lh: Math.round(lh), text: ph.textContent.trim(), arrow: !!cell.querySelector('.fr__arrow') && cell.querySelector('.fr__arrow').getBoundingClientRect().width > 0 }
})

try {
  cleanup()
  check('前置：内部身份登录', !!(await mpLogin(t => t.includes('内部'))))

  // MP-03 占位不折行（375 / 320）
  await open(`pages/cryo/form?mode=new&sampleId=${SAMPLE_ID}`)
  let ph = await phOneLine()
  check('MP-03 375 宽：转液氮时间的占位单行、› 还在', !!ph && ph.h <= ph.lh * 1.5 && ph.arrow, JSON.stringify(ph))
  await m.setViewportSize({ width: 320, height: 568 }); await wait(m, 800)
  ph = await phOneLine()
  check('MP-03 320 宽：占位单行（放不下就省略号，不竖排）', !!ph && ph.h <= ph.lh * 1.5, JSON.stringify(ph))
  await m.setViewportSize({ width: 375, height: 667 }); await wait(m, 500)

  // MP-01 连点只发一次
  await pickDate('冻存时间')
  await fillText('冻存样品名称', 'R22-dbl')
  await fillText('冻存数量', '2')
  await m.locator('.cryo__passage-in input, input.cryo__passage-in').first().fill('2')
  const posts = []
  m.on('request', r => { if (r.url().includes('/mp/int/cryo/batch') && r.method() === 'POST') posts.push(r.url()) })
  await m.locator('.cryo__btn').click()
  await wait(m, 350)
  await m.locator('.cryo__btn').click({ timeout: 2000 }).catch(() => {})
  await wait(m, 300)
  await m.locator('.cryo__btn').click({ timeout: 2000 }).catch(() => {})
  await wait(m, 2500)
  check('MP-01 连点三次只发一次 POST', posts.length === 1, `POST ${posts.length} 次`)
  check('MP-01 库里只多一条', sql("select count(*) from t_lqg_cryo_batch where cryo_name = 'R22-dbl' and del_flag = '0'") === '1')

  // MP-05 落在 -80 冻存页签；MP-08 标「新增」
  const url = m.url()
  check('MP-05 提交后进历史编辑记录且带 tab=cryo', url.includes('pages/history/index') && url.includes('tab=cryo'), url)
  const on = await m.evaluate(() => document.querySelector('.lqg-sheets__item--on')?.textContent.trim())
  check('MP-05 当前页签是「-80 冻存」', on === '-80 冻存', on)
  await wait(m, 1500)
  const card = await m.evaluate(() => {
    const el = [...document.querySelectorAll('.his__list > *, .sample-card, [class*="card"]')].find(e => e.textContent.includes('R22-dbl'))
    return el ? el.textContent.replace(/\s+/g, ' ').trim() : null
  })
  check('MP-08 刚建的记录标「新增」不是「修改」', !!card && card.includes('新增') && !card.includes('修改'), card?.slice(0, 80))

  // MP-02 返回回不到表单
  await m.evaluate(() => uni.navigateBack())
  await wait(m, 2000)
  check('MP-02 提交后返回，回不到填满的表单', !m.url().includes('pages/cryo/form'), m.url())

  // MP-11 选择类格子值超长不挤掉 ›
  await open('pages/sample/form?mode=new')
  const longCell = await m.evaluate(() => {
    const out = []
    for (const cell of document.querySelectorAll('.wd-cell')) {
      // 新增页的选择格还没值（显示的是占位）：把占位那个节点改成「有值」的样子再塞超长值（同一组件、同一套 scoped 属性）
      const v = cell.querySelector('.fr__text--one') || cell.querySelector('.fr__ph'); const a = cell.querySelector('.fr__arrow')
      if (!v || !a) continue
      v.className = v.className.replace('fr__ph', 'fr__text fr__text--one')
      v.textContent = '湖北省某某某市某某某区某某某某某某医院某某某某某某某某科室某某某某组'
      const cr = cell.getBoundingClientRect(); const ar = a.getBoundingClientRect(); const vr = v.getBoundingClientRect()
      out.push({ arrowIn: ar.width > 0 && ar.right <= cr.right + 1, valR: Math.round(vr.right), arrowL: Math.round(ar.left) })
      break
    }
    return out[0] || null
  })
  check('MP-11 选择格的值超长：› 仍在格内、值不压住它', !!longCell && longCell.arrowIn && longCell.valR <= longCell.arrowL + 1, JSON.stringify(longCell))

  // MP-18 / MP-09 冻存表与批次弹层
  await open('pages/ledger/index?sheet=cryo')
  // 只看超期标签本身（冻结格小字「剩 N / 初始 M 支 · …」）；seed 备注原文里写着「已超 0 天」，那是数据不是标签
  const labels = await m.evaluate(() => [...document.querySelectorAll('*')].filter(e => e.children.length === 0 && /剩 .+ 支 · /.test(e.textContent)).map(e => e.textContent.trim()))
  check('MP-18 超期标签里没有「已超 0 天」、阈值当天写「今天到期」', labels.length > 0 && !labels.some(t => t.includes('已超 0 天')) && labels.some(t => t.includes('今天到期')), JSON.stringify(labels.filter(t => /到期|已超/.test(t))))
  // 找一批有取用登记的（seed 3001 名下有流水）打开弹层
  const name = sql("select b.cryo_name from t_lqg_cryo_batch b where b.del_flag='0' and exists (select 1 from t_lqg_cryo_flow f where f.batch_id=b.id and f.del_flag='0' and f.flow_type <> 'adjust') order by b.id limit 1")
  await m.getByText(name, { exact: false }).first().click(); await wait(m, 2500)
  const acts = await m.evaluate(() => [...document.querySelectorAll('.cbs__row-act')].filter(e => e.offsetParent !== null).slice(0, 2).map((e) => {
    const r = e.getBoundingClientRect(); return { t: e.textContent.trim(), l: Math.round(r.left), r: Math.round(r.right), w: Math.round(r.width), h: Math.round(r.height) }
  }))
  check('MP-09 「改」「删」点击区 ≥ 40×40', acts.length === 2 && acts.every(a => a.w >= 40 && a.h >= 40), JSON.stringify(acts))
  check('MP-09 「改」「删」两块点击区不重叠', acts.length === 2 && acts[0].r <= acts[1].l + 1, JSON.stringify(acts))

  // MP-15 marker 行白底
  await open('pages/embed/form?mode=new')
  await m.locator('.mkr__add').first().click().catch(() => {}); await wait(m, 800)
  const mk = await m.evaluate(() => {
    const row = document.querySelector('.mkr__row'); const item = row?.querySelector('.lqg-seg__item:not(.lqg-seg__item--on)')
    return row ? { row: getComputedStyle(row).backgroundColor, item: item ? getComputedStyle(item).backgroundColor : null } : null
  })
  check('MP-15 marker 行白底、与未选中的按钮块底色不同', !!mk && mk.row === 'rgb(255, 255, 255)' && mk.item !== mk.row, JSON.stringify(mk))

  // D1 小程序「完成并同步」：确认框列出还空着的项（不拦）；点取消不发同步
  await open(`pages/qc/edit?sampleId=9000001005`)
  const pubs = []
  m.on('request', q => { if (/\/publish$/.test(q.url())) pubs.push(q.url()) })
  await m.locator('.qce__btn--publish').first().click(); await wait(m, 1200)
  const modal = await m.evaluate(() => document.querySelector('.uni-modal')?.innerText || '')
  check('D1 小程序同步前列出空着的项', modal.includes('以下几项还空着') && modal.includes('图片'), modal.replace(/\s+/g, ' ').slice(0, 80))
  await m.locator('.uni-modal__btn_default').click().catch(() => {}); await wait(m, 800)
  check('D1 点取消不发同步请求', pubs.length === 0, `${pubs.length} 次`)

  // MP-16 外部深链
  check('前置：外部身份登录', !!(await mpLogin(t => t.includes('外部'))))
  await open('pages/ledger/index')
  const ext = await m.evaluate(() => ({ sheets: !!document.querySelector('.lqg-sheets'), filter: !!document.querySelector('.lqg-filter'), text: document.body.innerText.includes('只给内部人员开放') }))
  check('MP-16 外部进内部管理：不画页签与筛选、只一句话', !ext.sheets && !ext.filter && ext.text, JSON.stringify(ext))
  await open('pages/cryo/form?mode=new')
  const cryoExt = await m.evaluate(() => document.body.innerText)
  check('MP-16 外部进 -80 冻存填写页：说只给内部开放、不说身份没确认', cryoExt.includes('只给中心内部人员开放') && !cryoExt.includes('没能确认你的身份'))
}
finally {
  cleanup()
  await b.close()
}

console.log(fails.length ? `\n✗ ${fails.length} 项没过：${fails.join(' / ')}` : '\n✓ 全部通过')
process.exit(fails.length ? 1 : 0)
