#!/usr/bin/env node
/**
 * SYS-STAGING-001 · 小程序测试环境（体验版）上传
 *
 *   LQG_WX_APPID=wx.... \
 *   LQG_WX_PRIVATE_KEY=/abs/path/private.wx....key \
 *   pnpm upload:mp --mode=test
 *
 * 流程（栈包 gotchas §6.5：**只走本地构建 + miniprogram-ci，不用 CI 机器构建**）：
 *   1. 本地 `uni build -p mp-weixin --mode <mode>`；
 *      ★ 产物写到 dist/build/mp-weixin-<mode>（UNI_OUTPUT_DIR 覆盖默认目录），
 *        这样不会覆盖 dist/build/mp-weixin —— 那是**生产构建**的产物，
 *        SYS-MP-001 的 accept 会 grep 它「不许出现 mock:ext」。测试构建里有 mock 调试入口，
 *        两者混在一个目录就会互相打脸。
 *   2. miniprogram-ci upload → 版本出现在微信公众平台的「版本管理」（体验版由平台侧设置/发布）
 *   3. miniprogram-ci preview → 生成体验版二维码 PNG（发给 Kevin，**不进仓库**）
 *
 * 为什么必须本地构建：不同 OS 上 uni-app 的编译产物不同，CI 机器（Linux）编出来的包在真机上
 * 组件会渲染为空（栈包 gotchas §6.5）。
 *
 * 缺 appid / 上传密钥时**不会假装成功**：打印「需要 Kevin 做」的清单并以非零码退出，
 * 但构建产物已经落在 dist/build/mp-weixin-<mode>，拿到凭据后可直接重跑。
 */
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const modeArg = process.argv.find(a => a.startsWith('--mode='))
const MODE = modeArg ? modeArg.split('=')[1] : 'test'
const HERE = path.resolve(import.meta.dirname, '..')
const OUT_DIR = path.join(HERE, 'dist', 'build', `mp-weixin-${MODE}`)

const die = (msg) => {
  console.error(`\n[upload:mp] ✗ ${msg}\n`)
  process.exit(2)
}

// ── 0. 读 env/.env 与 env/.env.<mode>（与 vite.config.ts 的 envDir 一致）──────
function loadEnvFile(file) {
  if (!existsSync(file)) return {}
  const out = {}
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/)
    if (m) out[m[1]] = m[2].replace(/^['"]|['"]$/g, '').trim()
  }
  return out
}
const env = { ...loadEnvFile(path.join(HERE, 'env', '.env')), ...loadEnvFile(path.join(HERE, 'env', `.env.${MODE}`)) }
const APPID = process.env.LQG_WX_APPID || env.VITE_WX_APPID || ''
const KEY_PATH = process.env.LQG_WX_PRIVATE_KEY || ''

console.log(`[upload:mp] mode=${MODE}  appid=${APPID || '(未提供)'}`)
console.log(`[upload:mp] serverBaseUrl=${env.VITE_SERVER_BASEURL || '(未设置)'}  mockLogin=${env.VITE_MOCK_LOGIN || '(关)'}`)

// ── 1. 本地构建 ─────────────────────────────────────────────────────────────
mkdirSync(OUT_DIR, { recursive: true })
console.log(`[upload:mp] 构建 → ${path.relative(HERE, OUT_DIR)}`)
execFileSync('pnpm', ['exec', 'uni', 'build', '-p', 'mp-weixin', '--mode', MODE], {
  cwd: HERE,
  stdio: 'inherit',
  env: { ...process.env, UNI_OUTPUT_DIR: OUT_DIR },
})
if (!existsSync(path.join(OUT_DIR, 'app.json'))) die(`构建产物不完整：${OUT_DIR}/app.json 不存在`)

// ── 2. 凭据检查（缺了就说清楚缺什么，不硬猜）────────────────────────────────
if (!APPID || APPID === 'wx0000000000000000') {
  die(
    `缺小程序 appid：设置 LQG_WX_APPID（或在 env/.env 里改 VITE_WX_APPID）。\n` +
    `  当前是占位值 "${APPID}"，用占位 appid 上传一定被微信拒。\n` +
    `  需要 Kevin 做：① 给出测试用 appid（乙方测试号或甲方主体号）；\n` +
    `                ② 在「微信公众平台 → 开发管理 → 开发设置」生成**上传密钥**并下载 private.<appid>.key；\n` +
    `                ③ 把 songjian.tianda.studio 加进「服务器域名 → request 合法域名」（必须 https 且已备案）。\n` +
    `  构建产物已就位：${path.relative(HERE, OUT_DIR)}（拿到凭据后重跑本命令即可）`,
  )
}
if (!KEY_PATH || !existsSync(KEY_PATH)) {
  die(
    `缺上传密钥：设置 LQG_WX_PRIVATE_KEY=<private.${APPID}.key 的绝对路径>。\n` +
    `  构建产物已就位：${path.relative(HERE, OUT_DIR)}（拿到密钥后重跑本命令即可）`,
  )
}

// ── 3. 上传 + 出体验版二维码 ────────────────────────────────────────────────
const ci = (await import('miniprogram-ci')).default
const pkg = JSON.parse(readFileSync(path.join(HERE, 'package.json'), 'utf8'))
const commit = process.env.LQG_BUILD_COMMIT || execFileSync('git', ['rev-parse', '--short=10', 'HEAD'], { cwd: HERE }).toString().trim()
const version = `${pkg.version}.${MODE}.${commit}`
const project = new ci.Project({
  appid: APPID,
  type: 'miniProgram',
  projectPath: OUT_DIR,
  privateKeyPath: path.resolve(KEY_PATH),
  ignores: ['node_modules/**/*'],
})

console.log(`[upload:mp] 上传版本 ${version} …`)
await ci.upload({
  project,
  version,
  desc: `SYS-STAGING-001 测试环境体验版（${MODE}，commit ${commit}）`,
  setting: { es6: true, minify: true, urlCheck: false },
  onProgressUpdate: (t) => process.stdout.write(`\r  ${String(t).slice(0, 120)}`),
})
console.log('\n[upload:mp] ✓ 上传完成 —— 到「微信公众平台 → 版本管理」把它设为体验版')

const qr = path.join(HERE, 'dist', `体验版二维码-${MODE}-${commit}.png`)
console.log('[upload:mp] 生成体验版二维码 …')
await ci.preview({
  project,
  desc: `测试环境体验版二维码（${commit}）`,
  qrcodeFormat: 'image',
  qrcodeOutputDest: qr,
  setting: { es6: true, minify: true, urlCheck: false },
})
console.log(`[upload:mp] ✓ 二维码：${qr}`)
console.log('[upload:mp] 二维码**不要提交进仓库**（已经落在 dist/，git 忽略了 code/miniapp/dist）')
