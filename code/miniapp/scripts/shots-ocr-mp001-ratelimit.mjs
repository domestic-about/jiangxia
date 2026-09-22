/**
 * OCR-MP-001 · 限流路径的端侧取证（`UI:mp.sample.form.ocr`：「识别失败 / 超时 / 被限流 →
 * 一句『没识别出来，请手动填写』，不阻塞填表」）。
 *
 * 為什麼单独一个脚本：同一用户每分钟 6 次，主脚本（`shots-ocr-mp001.mjs`）已经把 extA 的额度
 * 用掉大半；限流这一条要**先把额度烧到只剩 0**再点按钮，塞进主脚本会和别的用例抢桶。
 *
 * 步骤（照着 OCR-IMPL-001 的现场笔记做，别省任何一步）：
 *   1. `redis-cli del` 掉 `global:rate_limit:/mp/ocr/*` —— 桶状态在 Redisson 里 24h 存活，
 *      删 key 后**同一个 JVM 实例仍持有旧状态**，所以要等 >60s 才是干净现场；
 *   2. 用 extC 连打 6 次（全部 200）把额度烧完；
 *   3. 浏览器里真的点「拍照识别」→ 第 7 次被限流 → 页面必须显示「没识别出来，请手动填写」，
 *      且表单仍可填（不阻塞）。
 *
 * 跑法：node scripts/shots-ocr-mp001-ratelimit.mjs
 */
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'

const WS = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid'
const MP_PORT = process.env.LQG_MP_PORT || '9204'
const BASE = `http://127.0.0.1:${MP_PORT}`
const API = `${BASE}/lqg-api`
const BACKEND = 'http://127.0.0.1:8094'
const CLIENT_ID = '22b2aecd0710671691ec1c07f2542b9d'
const REDIS_PASSWORD = 'lqg_dev_redis_pwd'
const OUT = `${WS}/doc/waves/reports/OCR-MP-001`
const IMG = `${WS}/doc/verify/fixtures/ocr-sample.png`
const RATE_PATTERN = 'global:rate_limit:/mp/ocr/*'

const require = createRequire(path.join(WS, 'code/miniapp/package.json'))
const { chromium } = require('playwright')
mkdirSync(OUT, { recursive: true })

const log = []
function say(line) {
  log.push(line)
  console.log(line)
}

/** 清桶 + 等旧状态过期（见文件头第 1 步） */
function clearBuckets(waitSeconds = 62) {
  const keys = execFileSync('redis-cli', [
    '-p', '6380', '-a', REDIS_PASSWORD, '--no-auth-warning', '--scan', '--pattern', RATE_PATTERN,
  ], { encoding: 'utf-8' }).trim().split('\n').filter(Boolean)
  if (keys.length) {
    execFileSync('redis-cli', ['-p', '6380', '-a', REDIS_PASSWORD, '--no-auth-warning', 'del', ...keys])
  }
  say(`[rate] 删除 redis 桶 ${keys.length} 个，等 ${waitSeconds}s 让 JVM 里的旧桶状态过期`)
  execFileSync('sleep', [String(waitSeconds)])
}

async function login(key, phone) {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', clientid: CLIENT_ID },
    body: JSON.stringify({
      clientId: CLIENT_ID,
      grantType: 'xcx',
      tenantId: '000000',
      xcxCode: `mock:${key}`,
      phoneCode: `mock:${phone}`,
    }),
  })
  const body = await res.json()
  if (body.code !== 200 || !body.data?.access_token) {
    throw new Error(`${key} 登录失败：${JSON.stringify(body)}`)
  }
  return body.data.access_token
}

/** 直接打后端（不走 H5 代理），把 extC 的额度烧到 0 */
async function burnQuota(token, times) {
  const results = []
  const { readFile } = await import('node:fs/promises')
  const img = await readFile(IMG)
  for (let i = 0; i < times; i++) {
    const boundary = `----lqg${Math.random().toString(16).slice(2)}`
    const head = Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="ocr-sample.png"\r\nContent-Type: image/png\r\n\r\n`)
    const tail = Buffer.from(`\r\n--${boundary}--\r\n`)
    const payload = Buffer.concat([head, img, tail])
    const res = await fetch(`${BACKEND}/mp/ocr/recognize`, {
      method: 'POST',
      headers: {
        'Content-Type': `multipart/form-data; boundary=${boundary}`,
        'clientid': CLIENT_ID,
        'Authorization': `Bearer ${token}`,
        'X-Ocr-Stub-Case': '01',
      },
      body: payload,
    })
    const body = await res.json()
    results.push({ n: i + 1, code: body.code, msg: body.msg })
  }
  return results
}

clearBuckets(62)

const tokenExtC = await login('extC', '13800000013')
const burned = await burnQuota(tokenExtC, 6)
say(`[rate] extC 前 6 次：${burned.map(b => b.code).join(',')}（应全是 200）`)
if (burned.some(b => b.code !== 200)) {
  say('[assert] 前 6 次不该被限流 FAIL')
  process.exitCode = 1
}

// 浏览器里真的点按钮 → 第 7 次
const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 390, height: 900 }, deviceScaleFactor: 2 })
const page = await context.newPage()
const calls = []
page.on('response', async (res) => {
  if (!res.url().includes('/mp/ocr/recognize')) {
    return
  }
  let body = null
  try {
    body = await res.json()
  }
  catch {
    body = null
  }
  calls.push({ url: res.url(), status: res.status(), body })
})

await page.goto(`${BASE}/#/pages/index/index`, { waitUntil: 'domcontentloaded' })
await page.evaluate(t => window.localStorage.setItem('lqg_mp_token', t), tokenExtC)
await page.goto('about:blank')
await page.goto(`${BASE}/#/pages/sample/form?mode=new&stubCase=01`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(1500)

const chooser = page.waitForEvent('filechooser', { timeout: 15000 })
await page.locator('.lqg-ocr__btn--p').click()
const fc = await chooser
await fc.setFiles(IMG)
await page.waitForTimeout(4000)

const tip = (await page.locator('.lqg-ocr__tip').innerText()).trim()
const markCount = await page.locator('.wd-cell .lqg-tag--ocr, .wd-input .lqg-tag--ocr').count()
say(`[rate] 第 7 次（浏览器点击）提示 = ${JSON.stringify(tip)}`)
say(`[rate] 失败态 DOM 小标数 = ${markCount}（应 0）`)
say(`[rate] 第 7 次响应体 = ${JSON.stringify(calls.at(-1)?.body)}`)

if (tip !== '没识别出来，请手动填写') {
  say('[assert] 限流时的提示语必须逐字是「没识别出来，请手动填写」 FAIL')
  process.exitCode = 1
}
else {
  say('[assert] 限流 → 同一句失败提示 OK')
}
if (markCount !== 0) {
  say('[assert] 限流时不该有任何识别小标 FAIL')
  process.exitCode = 1
}

// 不阻塞填表：还能输入、提交按钮还在
await page.locator('.wd-input').filter({ has: page.locator('.wd-input__label').filter({ hasText: '供体姓名' }) })
  .first().locator('input').fill('限流后手填')
await page.locator('.wd-input').filter({ has: page.locator('.wd-input__label').filter({ hasText: '组织类型' }) })
  .first().locator('input').fill('肝组织')
await page.waitForTimeout(300)
const filled = await page.locator('.wd-input').filter({ has: page.locator('.wd-input__label').filter({ hasText: '供体姓名' }) })
  .first().locator('input').inputValue()
const submit = await page.locator('.form__btn').count()
say(`[rate] 限流后仍能手填 = ${JSON.stringify(filled)}，提交按钮 = ${submit}`)
if (filled !== '限流后手填' || submit !== 1) {
  say('[assert] 限流不该阻塞填表 FAIL')
  process.exitCode = 1
}
else {
  say('[assert] 限流不阻塞填表 OK')
}

await page.screenshot({ path: `${OUT}/11-ocr-rate-limited-manual-ok.png`, fullPage: true })
say('[shot] 11-ocr-rate-limited-manual-ok.png')

writeFileSync(`${OUT}/rate-limit-live.txt`, `${log.join('\n')}\n`)
writeFileSync(`${OUT}/rate-limit-calls.json`, `${JSON.stringify({ burned, browserCalls: calls }, null, 2)}\n`)

await context.close()
await browser.close()
say(`[done] exitCode = ${process.exitCode ?? 0}`)
process.exit(process.exitCode ?? 0)
