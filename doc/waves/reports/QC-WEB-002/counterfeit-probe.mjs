/**
 * QC-WEB-002 · counterfeit 探针：**页面上那 12 个分值真的来自库里的字典 remark 吗？**
 *
 * 为什么要这一步：accept 2 只断 `ScoreTab.vue` 里出现了 `useDict`。把 12 个档位与分值
 * 硬编码进页面，同样能过那条 grep。这里用「改库 → 页面跟着变 → 改回来」把这条路堵死。
 *
 * ★ 注意：走 RuoYi 的字典管理接口改（`PUT /system/dict/data`）——直接 psql UPDATE
 *   `sys_dict_data` 不会失效 Redis 字典缓存，`GET /system/dict/data/type/...` 仍返旧值
 *   （本探针第一版实测踩到；见报告 §WARN）。改完**必须改回来**（reseed 不覆盖 sys_dict_*）。
 *
 * 用法（工作台 8093 / 后端 8094 起来之后）：
 *   node doc/waves/reports/QC-WEB-002/counterfeit-probe.mjs
 */
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WS = path.resolve(HERE, '../../../..')
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const BASE = process.env.LQG_WEB_BASE || 'http://127.0.0.1:8093'
const SAMPLE = '9000001006'
const DICT_TYPE = 'lqg_score_pre_culture'
const DICT_VALUE = 'lt40'

const results = []
const check = (name, ok, detail = '') => {
  results.push({ name, ok: !!ok, detail: String(detail) })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

const api = (method, url, body) => {
  const args = ['doc/verify/api.sh', '--as', 'admin', method, url]
  if (body) args.push(JSON.stringify(body))
  const raw = execFileSync('bash', args, { cwd: WS, encoding: 'utf8', env: { ...process.env } })
  return JSON.parse(raw.slice(raw.indexOf('{')))
}

const row = () => api('GET', `/system/dict/data/type/${DICT_TYPE}`).data.find((d) => d.dictValue === DICT_VALUE)

/** 改 remark（走字典管理接口 → 会失效 Redis 缓存）；返回改后的值 */
const setRemark = (remark) => {
  const current = row()
  const res = api('PUT', '/system/dict/data', { ...current, remark, createTime: undefined, updateTime: undefined })
  if (res.code !== 200) throw new Error('改字典失败：' + JSON.stringify(res))
  return row().remark
}

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } })
const page = await ctx.newPage()
page.on('dialog', (d) => d.accept().catch(() => {}))

async function login() {
  await page.goto(`${BASE}/`)
  await page.evaluate(() => localStorage.clear()).catch(() => {})
  await page.goto(`${BASE}/`)
  await page.waitForSelector('.login-form input', { timeout: 30000 })
  const inputs = page.locator('.login-form input')
  await inputs.nth(0).fill('lqgadmin')
  await inputs.nth(1).fill('admin123')
  await page.click('.login-form button')
  await page.waitForSelector('.el-menu', { timeout: 30000 })
  await page.waitForTimeout(1200)
}

/** 打开评分页签，读「培养前样本评分」那一行的分值、<40 那档旁的分值文字、合计 */
async function readPage() {
  await page.goto(`${BASE}/qc-console/qc-editor?sampleId=${SAMPLE}`)
  await page.waitForSelector('.lqg-qc-editor', { timeout: 30000 })
  await page.locator('.lqg-qc-editor__tabs .el-tabs__item', { hasText: '类器官质量评分表' }).first().click()
  await page.waitForTimeout(1200)
  // ★ 只读 <40 那一档旁边的分值 span（不是整段 radio 文本，避免 '<40' + '88 分' 拼接歧义）
  const points = await page
    .locator('.lqg-score-group')
    .nth(0)
    .locator('.el-radio', { hasText: '<40' })
    .first()
    .locator('.lqg-score-group__points')
    .innerText()
  const itemScore = await page.locator('.lqg-score-group').nth(0).locator('.lqg-score-group__score').innerText()
  const total = await page.locator('.lqg-score-tab__total-value').innerText()
  return {
    points: points.replace(/\s+/g, ' ').trim(),
    itemScore: itemScore.trim(),
    total: total.trim()
  }
}

try {
  const before = row().remark
  check('前置：库里 lqg_score_pre_culture/lt40 的 remark 是 8', before === '8', before)

  await login()
  const baseline = await readPage()
  check('基线（seed：选中 lt40/gt14/lt100/lt30）：<40 旁「8 分」、该项 8、合计 18（8+0+0+10）',
    baseline.points === '8 分' && baseline.itemScore === '8' && baseline.total === '18',
    JSON.stringify(baseline))

  const changed = setRemark('88')
  check('★ 把字典 remark 改成 88（走字典管理接口，缓存已失效）', changed === '88', changed)
  const after = await readPage()
  check('★ 页面跟着变：<40 那档「88 分」、该项 88、合计 98 → 分值不是前端写死的',
    after.points === '88 分' && after.itemScore === '88' && after.total === '98',
    JSON.stringify(after))
} catch (e) {
  check('探针自身跑完（未抛异常）', false, String(e && e.message ? e.message : e))
} finally {
  // ★ 无论如何都要改回来（reseed 不覆盖 sys_dict_*）
  try {
    const restored = setRemark('8')
    check('收尾：字典 remark 已改回 8（GET 复读确认）', restored === '8', restored)
    if (restored === '8') {
      const back = await readPage()
      check('收尾：页面已回到「8 分」/ 18 合计', back.points === '8 分' && back.total === '18', JSON.stringify(back))
    }
  } catch (e) {
    check('收尾：字典 remark 改回 8', false, String(e && e.message ? e.message : e))
  }
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} PASS`)
if (failed.length) {
  console.log('FAILED:\n' + failed.map((f) => `  · ${f.name} — ${f.detail}`).join('\n'))
  process.exit(1)
}
