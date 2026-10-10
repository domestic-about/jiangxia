// 「转发给朋友」卡片封面（CR-20261010-19）：src/static/share/cover.png，5:4，不含任何业务数据。
//
//   node scripts/make-share-cover.mjs        （在 code/miniapp 下跑）
//
// 为什么要一张固定封面：`onShareAppMessage` 不给 imageUrl 时，微信拿「当前页面截图」当封面 ——
// 在填写页、质控文档页点转发会把供体姓名、住院号一起发出去（见 src/utils/share.ts）。
//
// 画法：一段 HTML 用 Playwright 截成 PNG —— 颜色读 src/style/tokens.scss 的 --lqg-primary / -deep，
// 文案读 src/config/legal.ts 的运营方与小程序名；图形是工作台 favicon 那个细胞团（中心一个环 + 外圈 7 个圆）。
// 全是平涂色块（不用渐变、不用照片），PNG 才压得小：主包上限 2MB，封面控制在 100KB 以内（share.spec.ts 钉着）。
import { readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const OUT = path.join(root, 'src/static/share/cover.png')
const W = 500
const H = 400

const tokens = readFileSync(path.join(root, 'src/style/tokens.scss'), 'utf8')
const token = (name) => {
  const m = tokens.match(new RegExp(`--${name}:\\s*(#[0-9a-fA-F]{6})`))
  if (!m) throw new Error(`tokens.scss 里找不到 --${name}`)
  return m[1]
}
const legal = readFileSync(path.join(root, 'src/config/legal.ts'), 'utf8')
const constant = (name) => {
  const m = legal.match(new RegExp(`export const ${name} = '([^']+)'`))
  if (!m) throw new Error(`legal.ts 里找不到 ${name}`)
  return m[1]
}

const primary = token('lqg-primary')
const deep = token('lqg-primary-deep')
const operator = constant('LEGAL_OPERATOR')
const service = constant('LEGAL_SERVICE')

/** 细胞团：中心一个环 + 外圈 7 个实心圆（与工作台 favicon 同一个图形） */
function mark(size) {
  const c = size / 2
  const ring = size * 0.33
  const r = size * 0.115
  const dots = Array.from({ length: 7 }, (_, i) => {
    const a = (-90 + (360 / 7) * i) * Math.PI / 180
    return `<circle cx="${(c + ring * Math.cos(a)).toFixed(2)}" cy="${(c + ring * Math.sin(a)).toFixed(2)}" r="${r.toFixed(2)}" fill="#fff"/>`
  }).join('')
  return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">${dots}`
    + `<circle cx="${c}" cy="${c}" r="${(size * 0.1).toFixed(2)}" fill="none" stroke="#fff" stroke-width="${(size * 0.045).toFixed(2)}"/></svg>`
}

const html = `<!doctype html><html><head><meta charset="utf-8"><style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body { width: ${W}px; height: ${H}px; }
  body { position: relative; overflow: hidden; background: ${primary}; color: #fff;
         font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif; }
  /* 右下角一块深一档的大圆，给平涂底一点层次（仍是纯色块） */
  .blob { position: absolute; right: -150px; bottom: -190px; width: 460px; height: 460px; border-radius: 50%; background: ${deep}; }
  .wrap { position: absolute; inset: 0; padding: 44px 44px 0; display: flex; flex-direction: column; }
  .mark { width: 92px; height: 92px; }
  .lab { margin-top: 34px; font-size: 21px; line-height: 1.3; opacity: .86; letter-spacing: .5px; }
  .title { margin-top: 8px; font-size: 62px; line-height: 1.15; font-weight: 700; letter-spacing: 2px; }
  .line { margin-top: 22px; width: 44px; height: 4px; border-radius: 2px; background: #fff; opacity: .9; }
  .slogan { margin-top: 18px; font-size: 19px; line-height: 1.4; opacity: .82; }
</style></head><body>
  <div class="blob"></div>
  <div class="wrap">
    <div class="mark">${mark(92)}</div>
    <div class="lab">${operator}</div>
    <div class="title">${service}</div>
    <div class="line"></div>
    <div class="slogan">送检、收样、包埋、冻存，一个入口记到底</div>
  </div>
</body></html>`

// 没装 Playwright 自带的浏览器时用本机的 Google Chrome
const browser = await chromium.launch().catch(() => chromium.launch({ channel: 'chrome' }))
try {
  const page = await (await browser.newContext({ viewport: { width: W, height: H }, deviceScaleFactor: 2 })).newPage()
  await page.setContent(html, { waitUntil: 'load' })
  await page.screenshot({ path: OUT, type: 'png' })
}
finally {
  await browser.close()
}
const kb = (statSync(OUT).size / 1024).toFixed(1)
console.log(`[ok] ${path.relative(root, OUT)}  ${W * 2}×${H * 2}  ${kb} KB`)
