/**
 * H5 截图/录屏脚本（SYS-MP-001 完工报告用）。
 *
 * 微信开发者工具需要扫码登录，本 agent 沙箱里跑不通（见完工报告 WARN），
 * 所以用 `pnpm build:h5` 的产物 + Playwright 覆盖能覆盖的项：
 * 三个页签、登录页、内部首页 2×2、外部首页三格、内部「我的」（有内部管理板块）、
 * 外部「我的」（没有）、以及「点入口 → 该表填写页占位页」的跳转录屏。
 *
 * /mp/me 与 /auth/login 用 Playwright 路由拦截打桩（不依赖后端进程）。
 *
 * 跑法：node scripts/shots-h5.mjs
 */
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { existsSync, mkdirSync } from 'node:fs'
import { extname, join, normalize } from 'node:path'
import { chromium } from 'playwright'

const WS = '/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid'
const ROOT = `${WS}/code/miniapp/dist/build/h5`
const OUT = `${WS}/doc/waves/reports/SYS-MP-001`
const PORT = 9200

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.ico': 'image/x-icon',
}

const ME = {
  internal: {
    userId: '9000000101',
    name: '李工',
    phoneMasked: '138****0001',
    identity: 'internal',
    ext: null,
  },
  external: {
    userId: '9000000111',
    name: '王医生',
    phoneMasked: '138****0011',
    identity: 'external',
    ext: {
      unitId: 9000009001,
      unitName: 'A 医院',
      groupId: 9000009101,
      groupName: '肝胆外科组',
      unitNameInput: null,
      groupNameInput: null,
      bindStatus: 'verified',
      rejectReason: null,
    },
  },
  externalUnbound: {
    userId: '2101930617171116034',
    name: 'wx_13800000099',
    phoneMasked: '138****0099',
    identity: 'external',
    ext: {
      unitId: null,
      unitName: null,
      groupId: null,
      groupName: null,
      unitNameInput: null,
      groupNameInput: null,
      bindStatus: 'unbound',
      rejectReason: null,
    },
  },
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${PORT}`)
  let p = decodeURIComponent(url.pathname)
  if (p === '/' || p === '') {
    p = '/index.html'
  }
  const file = normalize(join(ROOT, p))
  if (!file.startsWith(ROOT) || !existsSync(file)) {
    res.writeHead(404).end('not found')
    return
  }
  const body = await readFile(file)
  res.writeHead(200, { 'Content-Type': MIME[extname(file)] || 'application/octet-stream' })
  res.end(body)
})

await new Promise(r => server.listen(PORT, '127.0.0.1', r))
console.log(`static server on http://127.0.0.1:${PORT}`)
mkdirSync(OUT, { recursive: true })

const browser = await chromium.launch()
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 2,
  recordVideo: { dir: `${OUT}/video`, size: { width: 390, height: 844 } },
})
const page = await context.newPage()

// 打桩：/auth/login 与 /mp/me；meKey 决定返回哪一份身份
let meKey = 'internal'
await page.route('**/mp/me**', async (route) => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, msg: '操作成功', data: ME[meKey] }),
  })
})
await page.route('**/auth/login**', async (route) => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, msg: '操作成功', data: { access_token: 'e2e-token' } }),
  })
})

// 预置 token（跳过登录页的拦路），uni H5 的 storage 落在 localStorage
await context.addInitScript(() => {
  window.localStorage.setItem('lqg_mp_token', JSON.stringify('e2e-token'))
})

const BASE = `http://127.0.0.1:${PORT}/#`

async function shot(name, hash, identity) {
  meKey = identity
  // 先落白页再进目标路由：同一 hash 连续 goto 不会触发 H5 的 onShow，
  // store 里会留着上一个身份的 me（内外部首页会长得一样）
  await page.goto('about:blank')
  await page.goto(`${BASE}${hash}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  const tiles = await page.locator('.lqg-tile').count()
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true })
  console.log(`shot ${name} (tiles=${tiles})`)
  return tiles
}

// 1 三个页签 + 登录页
await context.clearCookies()
await page.goto(`${BASE}/pages/login/index`, { waitUntil: 'networkidle' })
await page.waitForTimeout(500)
// 登录页要没有 token 才不会被守卫弹走；先清掉
await page.evaluate(() => window.localStorage.clear())
await page.goto(`${BASE}/pages/login/index`, { waitUntil: 'networkidle' })
await page.waitForTimeout(700)
await page.screenshot({ path: `${OUT}/01-login.png`, fullPage: true })
console.log('shot 01-login')

await context.addInitScript(() => {
  window.localStorage.setItem('lqg_mp_token', JSON.stringify('e2e-token'))
})

// 2 内部首页（2×2）
const inTiles = await shot('02-home-internal', '/pages/index/index', 'internal')
// 3 外部首页（三格，第三格占满整行）
const exTiles = await shot('03-home-external', '/pages/index/index', 'external')
// 3b 外部未绑定（提示条）
await shot('03b-home-external-unbound', '/pages/index/index', 'externalUnbound')
// 4 内部的「我的」（有内部管理板块）
await shot('04-me-internal', '/pages/me/index', 'internal')
// 5 外部的「我的」（没有内部管理板块）
await shot('05-me-external', '/pages/me/index', 'external')
// 6 文档页签
await shot('06-docs', '/pages/docs/index', 'internal')

// 7 跳转录屏：内部四格 → 各表填写页占位页
meKey = 'internal'
await page.goto('about:blank')
await page.goto(`${BASE}/pages/index/index`, { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
const tiles = await page.locator('.lqg-tile').count()
console.log('internal tiles =', tiles, '(home-internal shot =', inTiles, ')')
for (let i = 0; i < tiles; i++) {
  const t = page.locator('.lqg-tile').nth(i)
  const title = (await t.locator('.lqg-tile__t').innerText()).trim()
  await t.click()
  await page.waitForTimeout(900)
  const url = page.url()
  await page.screenshot({ path: `${OUT}/07-tile-${i + 1}.png`, fullPage: true })
  console.log(`tile#${i + 1} ${title} -> ${url}`)
  await page.goBack()
  await page.waitForTimeout(600)
}

// 8 外部三格的第三格（占满整行）也要能点到填写页
meKey = 'external'
await page.goto('about:blank')
await page.goto(`${BASE}/pages/index/index`, { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
const extTiles = await page.locator('.lqg-tile').count()
console.log('external tiles =', extTiles, '(home-external shot =', exTiles, ')')
await page.locator('.lqg-tile').nth(2).click()
await page.waitForTimeout(900)
await page.screenshot({ path: `${OUT}/08-ext-third-tile.png`, fullPage: true })
console.log('external tile#3 ->', page.url())

await context.close()
await browser.close()
server.close()
console.log('done')
