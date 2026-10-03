// 2026-10-03 飞书问题（小程序行12）的机器判据：
//   小程序 H5（内部身份，390 宽）—— -80 冻存记录填写页
//     M12a 新增：「-80度超低温冰箱转移至液氮时间」是可点的日期格（有 ›、不是只读的「—」），占位「未转液氮不填」
//     M12b 新增：选了冻存时间与转液氮时间、填了液氮位置后提交 —— 发给后端的请求体里带 toLn2Time，库里这一批的 to_ln2_time 就是选的那天
//     M12c 不选转液氮时间提交 —— 请求体 toLn2Time 为 null，库里为空（还在 -80）
//     M12d 修改模式：这一格显示已登记的日期且可点；顶部提示不再说「转液氮在别处登记」
//     M12e 只读模式：这一格是只读的（没有 ›）
//   「转液氮时间不早于冻存时间」的前端判据在单测 src/api/cryo.spec.ts 里（同一天可以、早一天拒）；后端同一条由 CRYO-MODEL-001 的 accept 管。
//
// 跑法（本机三件套）：
//   LQG_MP_BASE=http://127.0.0.1:9100 node doc/waves/regression/staging-hardening/21-feishu-1003.mjs
// ★ 会往 dev 库写两条冻存批次（名称以 R21- 开头），结束时硬删掉；只对本机跑，不要对测试站跑。
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'
import path from 'node:path'

const require = createRequire(path.join(process.cwd(), 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const MP = (process.env.LQG_MP_BASE || 'http://127.0.0.1:9100').replace(/\/$/, '')
const PG = process.env.LQG_DEV_PG_CONTAINER || 'lqg-dev-postgres'
const SAMPLE_ID = '9000001001' // seed：已核验有效、内部编号 T-hli01
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }
const wait = (p, ms) => p.waitForTimeout(ms)
const sql = q => execFileSync('docker', ['exec', PG, 'psql', '-U', 'lqg', '-d', 'lqg_dev', '-Atc', q], { encoding: 'utf8' }).trim()
const cleanup = () => sql("delete from t_lqg_cryo_batch where cryo_name like 'R21-%'")

const b = await chromium.launch()
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
 * 打开一页并**整页重载**：H5 上只改 hash 到同一路由时 uni 复用页面实例（不重跑 onLoad），
 * 表单会带着上一次的值 —— 那样测的就不是「新打开的这一页」了。
 */
async function open(url) {
  await m.goto(`${MP}/#/${url}`, { waitUntil: 'networkidle' })
  await m.reload({ waitUntil: 'networkidle' }); await wait(m, 3000)
}

/** 某个标签的那一行：是不是可点的日期格、显示的值 */
const rowOf = label => m.evaluate((label) => {
  const cell = [...document.querySelectorAll('.wd-cell')].find(c => c.querySelector('.wd-cell__title')?.textContent.trim().startsWith(label))
  if (!cell) return null
  return {
    picker: !!cell.querySelector('.fr__arrow'),
    value: (cell.querySelector('.fr__text')?.textContent || '').trim(),
    placeholder: (cell.querySelector('.fr__ph')?.textContent || '').trim(),
  }
}, label)

/** 点开某一格的日期面板、直接点「确定」（面板的初值由页面给） */
async function pickDate(label) {
  await m.locator('.wd-cell', { has: m.locator('.wd-cell__title', { hasText: label }) }).first().click()
  await wait(m, 800)
  await m.locator('.wd-datetime-picker__action:not(.wd-datetime-picker__action--cancel)').filter({ visible: true }).first().click()
  await wait(m, 800)
}

/** 填一格文本（按标签找 wd-input） */
async function fillText(label, value) {
  const input = m.locator('.wd-input', { has: m.locator('.wd-input__label', { hasText: label }) }).first().locator('input')
  await input.fill(value)
}

/** 填齐必填项并提交，返回发出去的请求体 */
async function submitNew(name, withLn2) {
  await open(`pages/cryo/form?mode=new&sampleId=${SAMPLE_ID}`)
  await pickDate('冻存时间')
  await fillText('冻存样品名称', name)
  await fillText('冻存数量', '3')
  await m.locator('.cryo__passage-in input, input.cryo__passage-in').first().fill('2')
  if (withLn2) {
    await pickDate('-80度超低温冰箱转移至液氮时间')
    await fillText('液氮储存位置', '9号罐-R21')
  }
  const req = m.waitForRequest(r => r.url().includes('/mp/int/cryo/batch') && r.method() === 'POST', { timeout: 15000 }).catch(() => null)
  const res = m.waitForResponse(r => r.url().includes('/mp/int/cryo/batch') && r.request().method() === 'POST', { timeout: 15000 }).catch(() => null)
  await m.locator('.cryo__btn').click()
  const [rq, rs] = await Promise.all([req, res])
  const body = rq ? JSON.parse(rq.postData() || '{}') : null
  const json = rs ? await rs.json().catch(() => null) : null
  await wait(m, 1500)
  return { body, code: json?.code, msg: json?.msg }
}

try {
  cleanup()
  check('前置：内部身份登录', !!(await mpLogin(t => t.includes('内部'))))

  // M12a
  await open(`pages/cryo/form?mode=new&sampleId=${SAMPLE_ID}`)
  const fresh = await rowOf('-80度超低温冰箱转移至液氮时间')
  check('M12a 新增页：转液氮时间是可点的日期格', !!fresh?.picker, JSON.stringify(fresh))
  check('M12a 新增页：占位「未转液氮不填」', fresh?.placeholder === '未转液氮不填', JSON.stringify(fresh))

  // M12b
  const today = sql("select to_char(current_date, 'YYYY-MM-DD')")
  const withLn2 = await submitNew('R21-ln2', true)
  check('M12b 提交成功', withLn2.code === 200, JSON.stringify({ code: withLn2.code, msg: withLn2.msg }))
  check('M12b 请求体带 toLn2Time（= 选的那天）', withLn2.body?.toLn2Time === today, JSON.stringify(withLn2.body))
  const dbLn2 = sql("select coalesce(to_char(to_ln2_time,'YYYY-MM-DD'),'') || '|' || coalesce(ln2_location,'') from t_lqg_cryo_batch where cryo_name = 'R21-ln2' and del_flag = '0'")
  check('M12b 库里 to_ln2_time / 液氮位置落盘', dbLn2 === `${today}|9号罐-R21`, dbLn2)

  // M12c
  const noLn2 = await submitNew('R21-m80', false)
  check('M12c 不选也能提交', noLn2.code === 200, JSON.stringify({ code: noLn2.code, msg: noLn2.msg }))
  check('M12c 请求体 toLn2Time = null', !!noLn2.body && noLn2.body.toLn2Time === null, JSON.stringify(noLn2.body))
  const dbM80 = sql("select coalesce(to_char(to_ln2_time,'YYYY-MM-DD'),'空') from t_lqg_cryo_batch where cryo_name = 'R21-m80' and del_flag = '0'")
  check('M12c 库里 to_ln2_time 为空', dbM80 === '空', dbM80)

  // M12d / M12e
  const id = sql("select id from t_lqg_cryo_batch where cryo_name = 'R21-ln2' and del_flag = '0'")
  await open(`pages/cryo/form?mode=edit&id=${id}`)
  const edit = await rowOf('-80度超低温冰箱转移至液氮时间')
  check('M12d 修改页：显示已登记的日期且可点', !!edit?.picker && edit.value === today, JSON.stringify(edit))
  const note = await m.evaluate(() => ([...document.querySelectorAll('.lqg-note')].map(e => e.textContent).join(' ')))
  check('M12d 修改页提示只说「取走、补入」去别处登记，不再带「转液氮」', note.includes('取走、补入：') && !note.includes('转液氮'), note.trim())
  await open(`pages/cryo/form?mode=view&id=${id}`)
  const view = await rowOf('-80度超低温冰箱转移至液氮时间')
  check('M12e 只读页：这一格只读（没有 ›）', !!view && !view.picker && view.value === today, JSON.stringify(view))
}
finally {
  cleanup()
  await b.close()
}

console.log(fails.length ? `\n✗ ${fails.length} 项没过：${fails.join(' / ')}` : '\n✓ 全部通过')
process.exit(fails.length ? 1 : 0)
