// 2026-09-30 晚一轮飞书问题（小程序行19–22、网页工作台行18–19）的机器判据：
//   工作台（1440 宽）
//     W18① 质控文档编辑：「临床诊断 / 既往治疗」与「收样描述」并排
//     W18② 图片位与「情况描述」同一排（描述在图片右边）
//     W18③ 右侧「刷新」和「预览」标题同一行、靠右
//     W19① 「人员与单位」收起时不挂数字（有待办只给小红点）；展开后数字挂在「外部用户」上、父菜单红点消失
//     W19② 菜单角标小一号（高 ≤ 16px）
//   小程序 H5（390 宽）
//     M19 备注：标签在左、文字在右并靠右，高度不再是一大块（< 80px）
//     M20 质控编辑：「情况描述」同备注；图片缩略图一格 ≤ 80px
//     M21 各表单所有标题单行（内部：样本 / 类器官 / 石蜡包埋 / 冻存 / 质控编辑；外部：石蜡包埋送样）
//     M22 单位与组别：底部两个按钮一排，「保存」在右
//
// 跑法（本机三件套）：
//   LQG_WEB_BASE=http://127.0.0.1:8082 LQG_MP_BASE=http://127.0.0.1:9100 node doc/waves/regression/staging-hardening/19-feishu-0930b.mjs
// 只读：不点保存、不改数据。
import { createRequire } from 'node:module'
import path from 'node:path'

const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const WEB = (process.env.LQG_WEB_BASE || 'http://127.0.0.1:8082').replace(/\/$/, '')
const MP = (process.env.LQG_MP_BASE || 'http://127.0.0.1:9100').replace(/\/$/, '')
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }
const wait = (p, ms) => p.waitForTimeout(ms)

const b = await chromium.launch()

// ══ 工作台 ══════════════════════════════════════════════════════════════════
console.log(`工作台：${WEB}`)
{
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const w = await ctx.newPage()
  await w.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await w.waitForSelector('.login-form input', { timeout: 40000 })
  const i = w.locator('.login-form input')
  await i.nth(0).fill('lqgadmin'); await i.nth(1).fill('admin123')
  await w.click('.login-form button'); await wait(w, 4000)

  // W19：菜单角标
  const menuState = () => w.evaluate(() => {
    const sub = [...document.querySelectorAll('.sidebar-container .el-sub-menu')].find(s => s.querySelector('.el-sub-menu__title')?.textContent.includes('人员与单位'))
    if (!sub) return null
    const title = sub.querySelector('.el-sub-menu__title')
    const vis = (e) => e && !e.classList.contains('is-hidden') && getComputedStyle(e).display !== 'none' && e.offsetParent !== null
    const parentBadge = title.querySelector('.el-badge__content')
    const child = [...sub.querySelectorAll('.el-menu-item')].find(e => e.textContent.includes('外部用户'))
    const childBadge = child?.querySelector('.el-badge__content')
    const allNum = [...document.querySelectorAll('.sidebar-container .lqg-menu-badge .el-badge__content:not(.is-dot)')].filter(vis)
    return {
      opened: sub.classList.contains('is-opened'),
      parentNumber: vis(parentBadge) && !parentBadge.classList.contains('is-dot') ? parentBadge.textContent.trim() : '',
      parentDot: vis(parentBadge) && parentBadge.classList.contains('is-dot'),
      childNumber: vis(childBadge) ? childBadge.textContent.trim() : '',
      badgeHeights: allNum.map(e => Math.round(e.getBoundingClientRect().height)),
    }
  })
  const todo = await w.evaluate(async () => {
    const res = await fetch('/dev-api/lqg/home/todo', { headers: { Authorization: 'Bearer ' + (localStorage.getItem('Admin-Token') || ''), clientid: 'e5cd7e4891bf95d1d19206ce24a7b32e' } }).catch(() => null)
    return res ? res.json().catch(() => null) : null
  })
  const pendingExt = Number(todo?.data?.pendingExtUsers || 0)
  let s = await menuState()
  if (s?.opened) { await w.locator('.el-sub-menu__title', { hasText: '人员与单位' }).click(); await wait(w, 600); s = await menuState() }
  check('W19① 收起时父菜单不挂数字', s && s.parentNumber === '', JSON.stringify(s))
  check('W19① 收起时有待办 → 父菜单小红点', pendingExt === 0 ? !s.parentDot : s.parentDot, `待核验外部用户 ${pendingExt}`)
  await w.locator('.el-sub-menu__title', { hasText: '人员与单位' }).click(); await wait(w, 800)
  s = await menuState()
  check('W19① 展开后数字挂在「外部用户」上', pendingExt === 0 ? s.childNumber === '' : s.childNumber === String(pendingExt), JSON.stringify(s))
  check('W19① 展开后父菜单红点消失', !s.parentDot && s.parentNumber === '')
  check('W19② 角标小一号（高 ≤ 16px）', s.badgeHeights.length > 0 && s.badgeHeights.every(h => h <= 16), JSON.stringify(s.badgeHeights))

  // W18：质控文档编辑页
  await w.goto(`${WEB}/qc-docs`, { waitUntil: 'domcontentloaded' }); await wait(w, 3000)
  await w.locator('.lqg-qclist .el-table__body-wrapper .el-table__row').first().locator('button:has-text("进入")').click()
  await wait(w, 3500)
  const lay = await w.evaluate(() => {
    const rect = (e) => { const r = e?.getBoundingClientRect(); return r ? { l: Math.round(r.left), t: Math.round(r.top), r: Math.round(r.right), b: Math.round(r.bottom), cy: Math.round(r.top + r.height / 2) } : null }
    const item = (label) => [...document.querySelectorAll('.lqg-sample-qc .el-form-item')].find(f => f.querySelector('.el-form-item__label')?.textContent.trim() === label)
    const slot = document.querySelector('.lqg-sample-qc__slot-body')
    const head = document.querySelector('.lqg-preview-pane__head')
    return {
      diag: rect(item('临床诊断 / 既往治疗')),
      recv: rect(item('收样描述')),
      img: rect(slot?.querySelector('.lqg-image-slot')),
      desc: rect(slot?.querySelector('.lqg-sample-qc__desc')),
      head: rect(head),
      title: rect(head?.querySelector('.lqg-preview-pane__title')),
      titleText: head?.querySelector('.lqg-preview-pane__title')?.textContent.trim(),
      refresh: rect(head?.querySelector('button')),
      refreshText: head?.querySelector('button')?.textContent.trim(),
      oldBar: [...document.querySelectorAll('.lqg-preview-pane__bar button')].some(bt => bt.textContent.includes('刷新')),
    }
  })
  check('W18① 临床诊断 / 既往治疗 与 收样描述 并排', lay.diag && lay.recv && Math.abs(lay.diag.t - lay.recv.t) <= 2 && lay.recv.l > lay.diag.r, JSON.stringify([lay.diag, lay.recv]))
  check('W18② 图片与情况描述同一排', lay.img && lay.desc && lay.desc.l > lay.img.r && Math.abs(lay.desc.t - lay.img.t) <= 30, JSON.stringify([lay.img, lay.desc]))
  check('W18③ 「刷新」与「预览」标题同一行', lay.title && lay.refresh && Math.abs(lay.title.cy - lay.refresh.cy) <= 4 && lay.titleText === '预览' && lay.refreshText === '刷新', JSON.stringify([lay.title, lay.refresh]))
  check('W18③ 「刷新」靠右', lay.refresh && lay.head && lay.head.r - lay.refresh.r <= 2, JSON.stringify([lay.head, lay.refresh]))
  check('W18③ 原来那行单独的「刷新」没了', !lay.oldBar)
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

/**
 * 页面上所有表单标题：每个都应只占一行。
 * ★ 数的是**文字实际排了几行**（Range 的逐行矩形），不看元素高度：wd-textarea 的标签区是 flex、
 *   会被右边长高的文本框拉高，元素高了不代表字折行了。
 */
const labelsOneLine = () => m.evaluate(() => [...document.querySelectorAll('.wd-cell__title, .wd-input__label-inner, .wd-textarea__label-inner')]
  .filter(e => e.offsetParent !== null && e.textContent.trim())
  .map((e) => {
    const range = document.createRange()
    range.selectNodeContents(e)
    const tops = new Set([...range.getClientRects()].filter(r => r.width > 0).map(r => Math.round(r.top / 4)))
    return { text: e.textContent.trim(), lines: tops.size, ok: tops.size === 1 }
  }))

/** 多行文本（备注 / 情况描述）：标签在左、文字在右并靠右、不是一大块 */
const textareaRows = (label) => m.evaluate((label) => [...document.querySelectorAll('.wd-textarea.is-cell')]
  .filter(e => e.offsetParent !== null && e.querySelector('.wd-textarea__label-inner')?.textContent.trim() === label)
  .map((e) => {
    const l = e.querySelector('.wd-textarea__label').getBoundingClientRect()
    const ta = e.querySelector('textarea, .uni-textarea-wrapper, uni-textarea')
    const tr = (e.querySelector('.wd-textarea__value') || ta).getBoundingClientRect()
    const inner = e.querySelector('textarea')
    return {
      empty: !(inner?.value || '').trim(),
      sameRow: Math.abs(l.top - tr.top) <= 12 && tr.left >= l.right - 1,
      align: inner ? getComputedStyle(inner).textAlign : '',
      h: Math.round(e.getBoundingClientRect().height),
    }
  }), label)

async function checkForm(tag, url, extra) {
  await m.goto(`${MP}/#/${url}`, { waitUntil: 'networkidle' }); await wait(m, 3000)
  if (extra) await extra()
  const labels = await labelsOneLine()
  const bad = labels.filter(x => !x.ok)
  check(`M21 ${tag}：所有标题单行（${labels.length} 个）`, labels.length > 0 && bad.length === 0, bad.map(x => `${x.text} 排了 ${x.lines} 行`).join('；'))
  return labels
}

check('前置：内部身份登录', !!(await mpLogin(t => t.includes('内部'))))
await checkForm('样本记录信息表', 'pages/sample/form?mode=new')
const remark = await textareaRows('备注')
check('M19 备注：标签在左、文字在右', remark.length > 0 && remark.every(r => r.sameRow), JSON.stringify(remark))
check('M19 备注：文字靠右', remark.length > 0 && remark.every(r => r.align === 'right'), JSON.stringify(remark))
// 高度随内容长：空的时候约两行高；有内容时跟着字数长，不算「一大块」
check('M19 备注：空的时候不再是一大块（< 80px）', remark.length > 0 && remark.filter(r => r.empty).every(r => r.h < 80), JSON.stringify(remark))
await checkForm('类器官送样记录', 'pages/organoid/form?mode=new')
await checkForm('石蜡包埋', 'pages/embed/form?mode=new')
await checkForm('-80 冻存', 'pages/cryo/form?mode=new')

// 质控编辑：从列表进第一个样本
await m.goto(`${MP}/#/pages/qc/list`, { waitUntil: 'networkidle' }); await wait(m, 3000)
await m.locator('.qcl__item').first().click(); await wait(m, 3500)
const qcLabels = await labelsOneLine()
check(`M21 质控编辑：所有标题单行（${qcLabels.length} 个）`, qcLabels.length > 0 && qcLabels.every(x => x.ok), qcLabels.filter(x => !x.ok).map(x => x.text).join('；'))
const desc = await textareaRows('情况描述')
check('M20 情况描述：标签在左、文字在右并靠右', desc.length === 3 && desc.every(r => r.sameRow && r.align === 'right'), JSON.stringify(desc))
check('M20 情况描述：空的时候不再是一大块（< 80px）', desc.length === 3 && desc.filter(r => r.empty).every(r => r.h < 80), JSON.stringify(desc))
const thumb = await m.evaluate(() => {
  const c = [...document.querySelectorAll('.qis__cell')].find(e => e.offsetParent !== null)
  return c ? Math.round(c.getBoundingClientRect().width) : 0
})
check('M20 图片缩略图一格 ≤ 80px', thumb > 0 && thumb <= 80, `${thumb}px`)

// 外部身份：石蜡包埋送样（截图里「类器官来源类型」折行的那张）+ 单位与组别按钮
check('前置：外部身份登录', !!(await mpLogin(t => t.includes('外部'))))
const extEmbed = await checkForm('外部·石蜡包埋送样', 'pages/embed/form?mode=new')
check('M21 外部·石蜡包埋送样：有「类器官来源类型」且单行', extEmbed.some(x => x.text.startsWith('类器官来源类型') && x.ok), JSON.stringify(extEmbed.filter(x => x.text.startsWith('类器官'))))

await m.goto(`${MP}/#/pages/me/unit-group`, { waitUntil: 'networkidle' }); await wait(m, 3000)
const btns = await m.evaluate(() => [...document.querySelectorAll('.profile__bar .profile__btn')].map((e) => {
  const r = e.getBoundingClientRect()
  return { text: e.textContent.trim(), t: Math.round(r.top), l: Math.round(r.left) }
}))
check('M22 单位与组别：两个按钮同一排', btns.length === 2 && Math.abs(btns[0].t - btns[1].t) <= 1, JSON.stringify(btns))
const save = btns.find(x => x.text.startsWith('保存') || x.text.startsWith('提交中'))
const home = btns.find(x => x.text === '返回首页')
check('M22 「保存」在右边', !!save && !!home && save.l > home.l, JSON.stringify(btns))

await b.close()
console.log(fails.length ? `\n✗ ${fails.length} 项没过：${fails.join(' / ')}` : '\n✓ 全部通过')
process.exit(fails.length ? 1 : 0)
