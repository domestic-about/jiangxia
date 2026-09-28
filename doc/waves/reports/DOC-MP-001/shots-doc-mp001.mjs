/**
 * DOC-MP-001 的取证截图脚本 —— 跑在**真实 H5 dev server + 真后端 + 真库**上。
 *
 * 覆盖票面 §4.1 要求的三张图（内部视角 / 外部视角 / 空状态）外加外部样本详情第③段。
 *
 * 跑法（★ cwd 必须是 code/miniapp —— Playwright 的 require 锚点是本包的 package.json，
 * 用 plus-ui 的会 MODULE_NOT_FOUND，issue #229）：
 *
 *   cd code/miniapp && node scripts/shots-doc-mp001.mjs
 *
 * ★ 本脚本只把 PNG **写到磁盘**，并在 stdout 打印「断言值」（组数 / 组标题 / 行数 /
 *   合并按钮在不在 / 页面里有没有内部编号）—— 完成任务书 §0.1 ① 的「不要把截图读进上下文」。
 */
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright'

const BASE = process.argv[2] || 'http://127.0.0.1:9204'
const OUT = process.argv[3]
  || '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/doc/waves/reports/DOC-MP-001'

mkdirSync(OUT, { recursive: true })

const PERSONAS = [
  {
    key: 'staff',
    label: '内部人员 · 李工',
    // 点按钮用的短锚（标签里的间隔号在不同字体/编码下容易对不上，用姓名那一截最稳）
    pick: '李工',
    shot: '01-doc-list-internal',
    wait: '.gcd',
  },
  {
    key: 'extA',
    label: '外部人员 · 王医生（已核验）',
    pick: '王医生',
    shot: '02-doc-list-external',
    wait: '.gcd',
  },
  {
    key: 'newbie1',
    label: '外部人员 · 新号（未绑定）',
    pick: '新号',
    shot: '03-doc-empty',
    wait: '.lqg-state',
  },
]

async function login(page, pick) {
  await page.goto(`${BASE}/#/pages/login/index`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login__mock-btn', { timeout: 30000 })
  // ★ 点**勾选框**而不是整行：整行的中心落在《用户协议》那截文字上，
  //   那两个链接是 @click.stop → 会跳协议页，调试按钮就没了
  await page.click('.login__box')
  await page.locator('.login__mock-btn', { hasText: pick }).first().click()
  // uni-h5 的 reLaunch 到首页落成 `#/`（不是 `#/pages/index/index`），所以判「离开登录页」而不是判具体 URL
  await page.waitForFunction(() => !location.hash.includes('login'), null, { timeout: 30000 })
  await page.waitForTimeout(500)
}

async function shape(page) {
  return page.evaluate(() => {
    const groups = Array.from(document.querySelectorAll('.gcd'))
    return {
      groups: groups.length,
      titles: groups.map(g => g.querySelector('.gcd__title')?.textContent?.trim() ?? ''),
      subs: groups.map(g => g.querySelector('.gcd__sub')?.textContent?.trim() ?? ''),
      rows: groups.map(g => Array.from(g.querySelectorAll('.gcd__row .gcd__name')).map(n => n.textContent?.trim() ?? '')),
      times: groups.map(g => Array.from(g.querySelectorAll('.gcd__row .gcd__time')).map(n => n.textContent?.trim() ?? '')),
      downloadBtns: document.querySelectorAll('.gcd__dl').length,
      mergePreview: Array.from(document.querySelectorAll('.gcd__mbtn')).filter(b => b.textContent?.includes('合并预览')).length,
      mergeDownload: Array.from(document.querySelectorAll('.gcd__mbtn')).filter(b => b.textContent?.includes('合并下载')).length,
      emptyText: document.querySelector('.lqg-state__text')?.textContent?.trim() ?? '',
      sectionTitle: document.querySelector('.lqg-gl')?.textContent?.trim() ?? '',
      bodyHasInternalNo: /T-hli01|T-hco04|内部编号/.test(document.body.innerText),
    }
  })
}

const browser = await chromium.launch()
const out = {}

for (const p of PERSONAS) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
  const page = await ctx.newPage()
  await login(page, p.pick)
  const me = await page.evaluate(async () => {
    const res = await fetch('/lqg-api/mp/me', { headers: { clientid: '22b2aecd0710671691ec1c07f2542b9d', Authorization: `Bearer ${localStorage.getItem('lqg_mp_token') || ''}` } })
    return res.json()
  })
  await page.goto(`${BASE}/#/pages/doc/index`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector(p.wait, { timeout: 30000 })
  await page.waitForTimeout(800)
  await page.screenshot({ path: `${OUT}/${p.shot}.png`, fullPage: true })
  out[p.key] = { identity: me?.data?.identity, name: me?.data?.name, ...(await shape(page)) }
  await ctx.close()
}

// 外部样本详情第③段「质控文档」（DOC-MP-001 §2 的接线）
{
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
  const page = await ctx.newPage()
  await login(page, '王医生')
  await page.goto(`${BASE}/#/pages/sample/detail-ext?id=9000001001`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.det__doc', { timeout: 30000 })
  await page.waitForTimeout(800)
  await page.screenshot({ path: `${OUT}/04-ext-sample-docs.png`, fullPage: true })
  out.extSampleDetail = await page.evaluate(() => ({
    docs: Array.from(document.querySelectorAll('.det__doc .det__docname')).map(n => n.textContent?.trim() ?? ''),
    times: Array.from(document.querySelectorAll('.det__doc .det__doctime')).map(n => n.textContent?.trim() ?? ''),
    emptyShown: !!document.querySelector('.det__empty'),
  }))
  await ctx.close()
}

await browser.close()
console.log(JSON.stringify(out, null, 2))
