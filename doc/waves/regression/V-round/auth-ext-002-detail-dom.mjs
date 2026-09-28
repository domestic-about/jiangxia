// ============================================================================
// AUTH-EXT-002 acc3 的行为判据（弱断言清单第 2 条，2026-09-27 改写）
//
// 原文用 7 处源码 grep 证明「外部详情页接上了包埋卡片、有操作人与包埋人、**没有冻存与核验人**」。
// 这是**渲染行为 + 可见性**，source grep 证明不了（模板里有字段名也不代表渲染出来，反之亦然）。
//
// 本探针只认**运行中的系统**：
//   ① 接口层：extA 取 /mp/ext/sample/{id} → 顶层不得出现 verifyBy / verifyTime / internalNo / cryo*；
//      embeds 里 embedBy / operatorName **必须**在（CR-20260918-07 明确要给外部看）。
//   ② 渲染层：H5 打开外部详情页 → 包埋卡片在、操作人/包埋人可见；**「冻存」「核验人」全页不出现**。
//
// 【怎么证伪】（必须做，否则不算达标）
//   · 渲染层：在 detail-ext.vue 模板里临时加一行含「冻存」的文本 → 本探针必须变红（dev server HMR 即可，无需重建）。
//   · 接口层：让后端在外部详情响应里带上 verifyBy → ① 的那几条必须变红。
//
// 跑法（工作区根）：node doc/waves/regression/V-round/auth-ext-002-detail-dom.mjs
// 前置：后端 8091、H5 9202（qa-up 起的 dev:h5，mock 登录可用）、seed 已灌。
// ============================================================================
import { createRequire } from 'node:module'
import path from 'node:path'
import { execFileSync } from 'node:child_process'

const WS = process.env.LQG_WS || process.cwd()
const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')

const MP = process.env.LQG_MP || 'http://127.0.0.1:9202'
const ENVF = process.env.LQG_VERIFY_ENV_FILE || path.join(WS, '.tmp/qa-env/8091/verify.env')
const SAMPLE = 9000001001
const fails = []
const check = (n, ok, extra = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${extra ? ' — ' + extra : ''}`); if (!ok) fails.push(n) }

const api = (args) => JSON.parse(execFileSync('bash', ['doc/verify/api.sh', ...args],
  { cwd: WS, encoding: 'utf8', env: { ...process.env, LQG_VERIFY_ENV_FILE: ENVF } }))

const run = async () => {
  // ── ① 接口层：外部详情响应里不许有内部字段 ─────────────────────────────────
  const r = api(['--as', 'extA', 'GET', `/mp/ext/sample/${SAMPLE}`])
  const flat = JSON.stringify(r.data ?? {})
  for (const k of ['verifyBy', 'verifyTime', 'internalNo', 'cryo', 'cryoBatches']) {
    check(`① 响应里不得出现 ${k}`, !new RegExp(`"${k}"`).test(flat))
  }
  const emb = (r.data?.embeds ?? [])[0] ?? {}
  check('① embeds 给了 embedBy（外部可见，CR-20260918-07）', 'embedBy' in emb)
  check('① embeds 给了 operatorName（外部可见，CR-20260918-07）', 'operatorName' in emb)

  // ── ② 渲染层：真 DOM ───────────────────────────────────────────────────────
  const browser = await chromium.launch()
  const page = await browser.newPage({ viewport: { width: 420, height: 900 } })
  await page.goto(`${MP}/#/pages/login/index`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.login__mock-btn', { timeout: 30000 })
  await page.locator('.login__box').click()
  await page.waitForTimeout(200)
  await page.locator('.login__mock-btn').nth(1).click()   // 1 = 外部人员（extA）
  await page.waitForTimeout(4000)
  check('② 外部登录成功', !page.url().includes('login'), page.url())

  await page.goto(`${MP}/#/pages/sample/detail-ext?id=${SAMPLE}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(3500)
  const txt = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ')
  check('② 详情页真的打开了（不是空页/失败页）', txt.length > 40 && !/没能加载|加载失败/.test(txt), txt.slice(0, 90))
  check('② 包埋卡片在场（有石蜡块编号/包埋字样）', /石蜡块编号|包埋/.test(txt), txt.slice(0, 120))
  check('② 操作人/包埋人对可见', /操作人|包埋人/.test(txt))
  check('②★ 「冻存」全页不出现', !/冻存/.test(txt))
  check('②★ 「核验人」全页不出现', !/核验人/.test(txt))
  console.log('  页面文字（前 260 字）：' + txt.slice(0, 260))

  await browser.close()
  console.log(`\n结果：${fails.length === 0 ? 'PASS' : 'FAIL'}（失败 ${fails.length} 条）`)
  if (fails.length) { console.log('失败项：' + fails.join(' / ')); process.exit(1) }
}

run().catch((e) => { console.error('[ERROR]', e); process.exit(2) })
