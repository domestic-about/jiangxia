/** extB「只看我提交的」开关侦察：点哪一下才真的发 onlyMine=true？ */
import { chromium, resetToLogin, injectToken, mpToken, gotoHistory, historyRows, sleep } from './L2r4-lib.mjs'

const b = await chromium.launch()
const ctx = await b.newContext({ viewport: { width: 390, height: 844 } })
const p = await ctx.newPage()
const reqs = []
p.on('request', r => { if (r.url().includes('/mp/ext/sample/list') || r.url().includes('/mp/int/sample/list')) reqs.push(r.url().replace(/^.*\/mp/, '/mp')) })
await resetToLogin(p)
await injectToken(p, mpToken('extB', '13800000012'))
await gotoHistory(p)
console.log('rows before =', JSON.stringify((await historyRows(p)).map(r => r.code)))

console.log('=== switch DOM ===')
console.log(await p.evaluate(() => {
  const w = document.querySelector('.his__switch')
  if (!w) return 'NO .his__switch'
  return w.outerHTML.slice(0, 700) + '\n--- classes ---\n' + [...w.querySelectorAll('*')].map(e => e.tagName + '.' + (typeof e.className === 'string' ? e.className : '')).join('\n')
}))

const before = await p.evaluate(() => [...document.querySelectorAll('.his__switch *')].map(e => typeof e.className === 'string' ? e.className : '').join('|'))
// 试三种点法，各看一次是否发请求 / 行集合是否变
for (const [label, fn] of [
  ['点 .wd-switch__body', async () => p.locator('.his__switch .wd-switch__body').first().click({ timeout: 4000 })],
  ['点 .his__switch-t 文字', async () => p.locator('.his__switch-t').first().click({ timeout: 4000 })],
  ['点 .his__switch 容器', async () => p.locator('.his__switch').first().click({ timeout: 4000 })],
]) {
  reqs.length = 0
  try { await fn() } catch (e) { console.log(label, '点击失败', e.message.slice(0, 80)); continue }
  await sleep(2200)
  const after = await p.evaluate(() => [...document.querySelectorAll('.his__switch *')].map(e => typeof e.className === 'string' ? e.className : '').join('|'))
  console.log(`--- ${label}: requests=${JSON.stringify(reqs)} classChanged=${after !== before}`)
  console.log(`    rows=${JSON.stringify((await historyRows(p)).map(r => r.code))}`)
}
await b.close()
