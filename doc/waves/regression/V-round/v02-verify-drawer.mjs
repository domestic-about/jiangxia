// ============================================================================
// V02 活体验收：核验抽屉里改的「送检段 / 实验室补填段」是否真的随保存发出去
//
// 独立验收报告 V02（#147）：抽屉里送检信息显示为可编辑，点「判为有效并保存」只提交收样段，
// **送检段的修改被静默丢弃**。修法（V02/V02b）：verify 请求带 `fill` 段，与核验结论同一事务保存。
//
// 本脚本不读实现方报告、不做源码 grep：真浏览器登录工作台 → 石蜡包埋列表 → 打开待核验那条的
// 核验抽屉 → 改一个补填字段 → 点保存 → **拦住真实请求体**，断言 `fill` 段在、且带的正是改后的值；
// 随后用 API 复核库里确实落了这个值。
//
// 跑法（工作区根）：
//   node doc/waves/regression/V-round/v02-verify-drawer.mjs
// 前置：后端 8091（dev）、工作台 8093、seed 已灌且存在一条 verify_status=pending 的石蜡包埋送样。
// ============================================================================
import { createRequire } from 'node:module'
import path from 'node:path'
import { execFileSync } from 'node:child_process'

const WS = process.env.LQG_WS || process.cwd()
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const WEB = process.env.LQG_WEB || 'http://127.0.0.1:8093'
const API = process.env.LQG_API || 'http://127.0.0.1:8091'
const ENVF = process.env.LQG_VERIFY_ENV_FILE || path.join(WS, '.tmp/qa-env/8091/verify.env')

const log = (...a) => console.log(...a)
const fails = []
const check = (name, ok, extra = '') => {
  log(`  ${ok ? '✓' : '✗'} ${name}${extra ? ' — ' + extra : ''}`)
  if (!ok) fails.push(name)
}

/** 用 api.sh 打后端（复用项目的身份夹具），返回解析后的 JSON */
const api = (args) => {
  const out = execFileSync('bash', ['doc/verify/api.sh', ...args], {
    cwd: WS, encoding: 'utf8', env: { ...process.env, LQG_VERIFY_ENV_FILE: ENVF }
  })
  try { return JSON.parse(out) } catch { return { raw: out } }
}

const run = async () => {
  // ── 0. 在一条「已核验有效」的样本上造一条待核验送样（核验抽屉的「判为有效」要求所挂样本已核验）──
  const SAMPLE = Number(process.env.LQG_SAMPLE || 9000001001)
  const created = api(['--as', 'extA', 'POST', '/mp/ext/embed', JSON.stringify({ sampleId: SAMPLE, sampleType: '组织' })])
  log(`造待核验送样：${JSON.stringify(created).slice(0, 160)}`)
  const sample = api(['--as', 'staff', 'GET', `/lqg/sample/${SAMPLE}`])
  const submitNo = sample?.data?.submitNo ?? sample?.data?.submit_no ?? ''
  const internalNo = sample?.data?.internalNo ?? sample?.data?.internal_no ?? ''
  log(`目标样本：id=${SAMPLE} 送检单号=${submitNo} 内部编号=${internalNo}`)

  const pending = api(['--as', 'staff', 'GET', '/lqg/embed/list?pageNum=1&pageSize=50&verifyStatus=pending'])
  const row = (pending.rows || []).find((r) => String(r.sampleId ?? r.sample_id) === String(SAMPLE)) || (pending.rows || [])[0]
  if (!row) {
    console.error('[STOP] 造完仍查不到待核验送样，API 可能没打通')
    process.exit(2)
  }
  log(`待核验送样：id=${row.id} sample=${row.sampleId ?? row.sample_id} 石蜡块编号=${row.embedNo ?? row.embed_no ?? '-'}`)

  const browser = await chromium.launch()
  const page = await browser.newPage()
  const seen = []
  const reqs = []
  const bad = []
  page.on('request', (r) => {
    const u = r.url()
    if (/\/lqg\//.test(u) || /\/mp\//.test(u)) {
      reqs.push({ method: r.method(), url: u, body: r.postData() })
    }
    if (r.method() !== 'GET' && /\/lqg\/(embed|qc|doc)/.test(u)) {
      seen.push({ method: r.method(), url: u, body: r.postData() })
    }
  })
  page.on('response', async (res) => {
    const u = res.url()
    if ((/\/lqg\//.test(u) || /\/mp\//.test(u)) && res.status() >= 400) {
      bad.push({ status: res.status(), url: u, body: (await res.text().catch(() => '')).slice(0, 200) })
    }
  })
  page.on('pageerror', (e) => bad.push({ status: 'PAGEERROR', url: '(console)', body: String(e && e.message || e).slice(0, 300) }))
  page.on('console', (m) => { if (m.type() === 'error') bad.push({ status: 'CONSOLE', url: '(console)', body: m.text().slice(0, 300) }) })
  page.on('requestfailed', (r) => bad.push({ status: 'FAILED', url: r.url(), body: String(r.failure()?.errorText || '') }))

  // ── 1. 登录工作台（与 D1 回归脚本同一入口）────────────────────────────────
  await page.goto(`${WEB}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login-form input', { timeout: 30000 })
  const inputs = page.locator('.login-form input')
  await inputs.nth(0).fill('lqgadmin')
  await inputs.nth(1).fill('admin123')
  await page.click('.login-form button')
  await page.waitForTimeout(2500)
  check('登录后离开登录页', !page.url().includes('login'), page.url())

  // ── 2. 直接进石蜡包埋列表，按待核验筛选 ────────────────────────────────────
  await page.goto(`${WEB}/embed`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.el-table__row', { timeout: 30000 })

  // 只筛待核验，保证第一条就是要核验的
  const sel = page.locator('.el-form-item:has-text("核验状态") .el-select').first()
  if (await sel.count()) {
    await sel.click()
    await page.waitForTimeout(400)
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("待核验")').first().click()
    await page.locator('button:has-text("搜索")').first().click()
    await page.waitForTimeout(1200)
  }

  // ── 3. 打开核验抽屉（按目标样本的送检单号 / 内部编号定位那一行）──────────────
  const pick = async () => {
    const rows = page.locator('.el-table__row')
    const cnt = await rows.count()
    for (let i = 0; i < cnt; i++) {
      const txt = (await rows.nth(i).innerText().catch(() => '')) || ''
      if ((submitNo && txt.includes(submitNo)) || (internalNo && txt.includes(internalNo))) return rows.nth(i)
    }
    return rows.first()
  }
  const firstRow = await pick()
  const rowBtns = firstRow.locator('button')
  const n = await rowBtns.count()
  let opened = false
  for (let i = 0; i < n; i++) {
    const txt = (await rowBtns.nth(i).innerText().catch(() => '')) || ''
    if (/核验/.test(txt)) { await rowBtns.nth(i).click(); opened = true; break }
  }
  if (!opened && n > 0) { await rowBtns.nth(0).click(); opened = true }
  check('打开核验抽屉', opened)
  await page.waitForSelector('.el-drawer:visible', { timeout: 20000 })

  // 等表单真的渲染出来（抽屉元素可见 ≠ 内容加载完）
  await page.waitForSelector('.el-drawer:visible .el-form-item', { timeout: 20000 }).catch(() => {})
  await page.waitForTimeout(500)

  // 抽屉里的字段标签与提示（定位不到输入框时用来诊断，不猜）
  const dlg = page.locator('.el-drawer:visible').first()
  log('  抽屉标题：' + (await dlg.locator('.el-drawer__title').first().innerText().catch(() => '?')))
  const inputsAll = await dlg.locator('input').count().catch(() => -1)
  log(`  抽屉内 input 数=${inputsAll}  form-item 数=${await dlg.locator('.el-form-item').count().catch(() => -1)}`)
  const body = (await dlg.innerText().catch(() => '')).replace(/\s+/g, ' ')
  log('  抽屉正文（前 500 字）：' + body.slice(0, 500))
  const labels = await page.locator('.el-drawer:visible .el-form-item__label').allInnerTexts().catch(() => [])
  log('  抽屉字段标签：' + labels.map((s) => s.trim()).filter(Boolean).join(' / '))
  const hints = await page.locator('.el-drawer:visible .el-alert, .el-drawer:visible .lqg-embed-drawer__hint').allInnerTexts().catch(() => [])
  if (hints.length) log('  抽屉提示：' + hints.map((s) => s.trim().replace(/\s+/g, ' ')).join(' | ').slice(0, 300))

  // ── 4. 填必填的石蜡块编号 + 改一个实验室补填字段（脱水时间）────────────────
  const BLOCK = `V02-${Date.now().toString().slice(-6)}`
  const blockField = page.locator('.el-drawer:visible .el-form-item:has-text("石蜡块编号") input').first()
  if (await blockField.count()) { await blockField.fill(BLOCK); await page.keyboard.press('Tab') }
  check('填了必填的石蜡块编号', await blockField.count() > 0, BLOCK)
  const NEW_VAL = '2026-09-26'
  const field = page.locator('.el-drawer:visible .el-form-item:has-text("脱水时间") input').first()
  if (await field.count()) {
    await field.fill(NEW_VAL)
    await page.keyboard.press('Tab')
  } else {
    log('  ! 没找到「脱水时间」输入框，跳过改值（仍验请求体是否带 fill）')
  }
  await page.waitForTimeout(300)

  // ── 5. 点「判为有效并保存」，抓请求体 ─────────────────────────────────────
  const saveBtn = page.locator('.el-drawer:visible button:has-text("判为有效")').first()
  const disabled = await saveBtn.isDisabled().catch(() => true)
  check('「判为有效并保存」可点（说明所挂样本已核验有效）', !disabled)
  if (disabled) {
    const why = await page.locator('.el-drawer:visible .el-alert').allInnerTexts().catch(() => [])
    log('  ! 按钮置灰原因：' + why.join(' | ').replace(/\s+/g, ' ').slice(0, 200))
    await browser.close()
    log('\n结果：FAIL（抽屉打不开到可保存态，V02 无法验收）')
    process.exit(1)
  }
  const before = seen.length
  await saveBtn.click()
  await page.waitForTimeout(3500)

  log('\n  ── 本次会话的 /lqg 与 /mp 请求 ──')
  for (const r of reqs.slice(-14)) log(`    ${r.method} ${r.url.replace(/^https?:\/\/[^/]+/, '')}`)
  if (bad.length) { log('  ── 非 2xx / 失败请求 ──'); for (const b of bad) log(`    ${b.status} ${b.url.replace(/^https?:\/\/[^/]+/, '')} ${b.body.replace(/\s+/g,' ').slice(0,120)}`) }
  const verifyReq = seen.slice(before).find((r) => /\/verify/.test(r.url) || /\/lqg\/embed/.test(r.url))
  log('\n  ── 捕获到的写请求 ──')
  for (const r of seen) log(`    ${r.method} ${r.url}\n      body=${(r.body || '').slice(0, 400)}`)

  check('保存真的发出了写请求', !!verifyReq, verifyReq ? verifyReq.url : '没有任何写请求')
  if (verifyReq) {
    let body = {}
    try { body = JSON.parse(verifyReq.body || '{}') } catch { /* 非 JSON */ }
    const fill = body.fill
    check('请求体带 fill 段（V02 要害）', !!fill, fill ? Object.keys(fill).join(',') : '没有 fill')
    if (fill) {
      check('fill 里带了刚改的脱水时间', String(fill.dehydrateTime || '').startsWith(NEW_VAL),
        `dehydrateTime=${fill.dehydrateTime}`)
    }
  }

  // ── 6. 库里复核（改的值真落了）────────────────────────────────────────────
  await page.waitForTimeout(800)
  const after = api(['--as', 'staff', 'GET', `/lqg/embed/${row.id}`])
  const dbVal = after?.data?.dehydrateTime ?? after?.data?.dehydrate_time
  check('库里脱水时间 = 抽屉里改的值', String(dbVal || '').startsWith(NEW_VAL), `库里=${dbVal}`)
  check('这条已判为有效', (after?.data?.verifyStatus ?? after?.data?.verify_status) === 'valid',
    `verifyStatus=${after?.data?.verifyStatus}`)

  await browser.close()
  log(`\n结果：${fails.length === 0 ? 'PASS' : 'FAIL'}（失败 ${fails.length} 条）`)
  if (fails.length) { log('失败项：' + fails.join(' / ')); process.exit(1) }
}

run().catch((e) => { console.error('[ERROR]', e); process.exit(2) })
