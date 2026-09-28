#!/usr/bin/env node
/**
 * 小程序上传（SYS-STAGING-001 起；V07 补发布守卫与参数）
 *
 *   LQG_WX_APPID=wx.... LQG_WX_PRIVATE_KEY=/abs/path/private.wx....key \
 *   pnpm upload:mp --mode=test|staging|production [--robot=N] [--version=x.y.z] [--desc=说明] [--skip-build] [--build-only]
 *
 * 参数：
 *   --mode=        vite mode，决定加载 env/.env + env/.env.<mode>，缺省 test（与旧用法一致）
 *   --robot=N      miniprogram-ci 机器人编号 1–30；缺省 production=1，其余=2（与 dongjiaoshan 同一分工）
 *   --version=     上传版本号；缺省 production = package.json 的 version，其余 = <version>.<mode>.<提交号>
 *   --desc=        版本描述（微信后台「项目备注」列）；缺省按 mode 生成一句中文
 *   --skip-build   不重新构建，直接上传 dist/build/mp-weixin-<mode> 里现成的产物（守卫照样检查它）
 *   --build-only   **只构建 + 过两道守卫，不上传**（2026-09-28 加）。用途：微信要求上传来源 IP 在
 *                  「小程序代码上传」白名单里，而开发机常年在代理后面（出口 IP 不稳），所以
 *                  构建留本机（项目纪律：不同 OS 产物不同）、**上传改到固定 IP 的服务器上**跑。
 *                  deploy.sh 的 miniapp 阶段就是「本机 --build-only → 同步产物 → 服务器 --skip-build」。
 *
 * 流程（栈包 gotchas §6.5：**只走本地构建 + miniprogram-ci，不用 CI 机器构建**）：
 *   0. **发布守卫**（只在 production 模式拦截，其它模式只提示）：
 *        接口地址不是 https / 是回环或内网地址 / 是测试环境域名 / 为空，
 *        appid 为空或是 wx0000000000000000 这类占位，
 *        mock 登录开着（env 里 VITE_MOCK_LOGIN=1，或产物里搜得到 mock: 前缀）
 *      —— 任一条命中就拒绝上传，逐条说明原因，退出码 3。
 *      ★ 守卫只在上传脚本里：普通的 `pnpm build:mp-weixin` 不受影响（票面 accept 要跑它）。
 *   1. 本地 `uni build -p mp-weixin --mode <mode>`，产物写到 dist/build/mp-weixin-<mode>
 *      （UNI_OUTPUT_DIR 覆盖默认目录，不覆盖 dist/build/mp-weixin —— 那是 accept 检查的生产构建产物）；
 *      appid 与接口地址以**本次生效的值**注入构建（进程环境变量 > env/.env.<mode> > env/.env）。
 *   2. 构建后再对**产物**过一遍守卫（接口地址、mock 痕迹、回环地址），防「env 看着对、包里不对」。
 *   3. miniprogram-ci upload → 微信公众平台「版本管理」；非 production 另出一张体验版二维码（不进仓库）。
 *
 * 缺 appid / 上传密钥时**不会假装成功**：打印缺什么并以非零码退出，构建产物已经就位，拿到凭据后可直接重跑。
 */
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const HERE = path.resolve(import.meta.dirname, '..')

// ── 参数 ──────────────────────────────────────────────────────────────────────
function parseArgs(argv) {
  const out = { mode: 'test', robot: null, version: null, desc: null, skipBuild: false, buildOnly: false }
  for (const arg of argv) {
    if (arg.startsWith('--mode=')) out.mode = arg.slice('--mode='.length)
    else if (arg.startsWith('--robot=')) out.robot = arg.slice('--robot='.length)
    else if (arg.startsWith('--version=')) out.version = arg.slice('--version='.length)
    else if (arg.startsWith('--desc=')) out.desc = arg.slice('--desc='.length)
    else if (arg === '--skip-build') out.skipBuild = true
    else if (arg === '--build-only') out.buildOnly = true
    else {
      console.error(`[upload:mp] 不认识的参数：${arg}`)
      process.exit(2)
    }
  }
  return out
}

const die = (msg, code = 2) => {
  console.error(`\n[upload:mp] ✗ ${msg}\n`)
  process.exit(code)
}

const args = parseArgs(process.argv.slice(2))
const MODE = args.mode
if (!/^[a-z]+$/.test(MODE)) die(`--mode 只收小写字母（如 test / staging / production），收到「${MODE}」`)
const IS_PROD = MODE === 'production'
const OUT_DIR = path.join(HERE, 'dist', 'build', `mp-weixin-${MODE}`)

const ROBOT = args.robot === null ? (IS_PROD ? 1 : 2) : Number(args.robot)
if (!Number.isInteger(ROBOT) || ROBOT < 1 || ROBOT > 30) die(`--robot 必须是 1–30 的整数，收到「${args.robot}」`)

// ── 0. 读 env/.env 与 env/.env.<mode>（与 vite.config.ts 的 envDir 一致；进程环境变量优先）────
function loadEnvFile(file) {
  if (!existsSync(file)) return {}
  const out = {}
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    if (/^\s*#/.test(line)) continue
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/)
    if (m) out[m[1]] = m[2].replace(/^['"]|['"]$/g, '').trim()
  }
  return out
}
if (!existsSync(path.join(HERE, 'env', `.env.${MODE}`))) die(`没有 env/.env.${MODE}：mode 写错了？`)
const fileEnv = { ...loadEnvFile(path.join(HERE, 'env', '.env')), ...loadEnvFile(path.join(HERE, 'env', `.env.${MODE}`)) }
/** 生效值：进程环境变量 > env 文件（vite 的 loadEnv 也是这个优先级） */
const effective = key => (process.env[key] !== undefined ? process.env[key] : fileEnv[key]) ?? ''

const APPID = process.env.LQG_WX_APPID || effective('VITE_WX_APPID')
const KEY_PATH = process.env.LQG_WX_PRIVATE_KEY || ''
const BASE_URL = effective('VITE_SERVER_BASEURL')
const MOCK = effective('VITE_MOCK_LOGIN')

// ── 守卫 ──────────────────────────────────────────────────────────────────────
/** 回环 / 内网 / 本机名：正式包里一个都不能有 */
function privateHostReason(host) {
  const h = host.toLowerCase().replace(/^\[|\]$/g, '')
  if (h === 'localhost' || h.endsWith('.localhost')) return '本机名 localhost'
  if (h === '::1' || h === '0.0.0.0') return '回环 / 未指定地址'
  if (h.endsWith('.local') || h.endsWith('.lan') || h.endsWith('.internal')) return '局域网主机名'
  const m = h.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/)
  if (m) {
    const [a, b] = [Number(m[1]), Number(m[2])]
    if (a === 127) return '回环地址 127.x'
    if (a === 10) return '内网地址 10.x'
    if (a === 172 && b >= 16 && b <= 31) return '内网地址 172.16–31.x'
    if (a === 192 && b === 168) return '内网地址 192.168.x'
    if (a === 169 && b === 254) return '链路本地地址 169.254.x'
    if (a === 0) return '未指定地址 0.x'
  }
  if (/^f[cd][0-9a-f]{2}:/.test(h) || h.startsWith('fe80:')) return '内网 IPv6 地址'
  return ''
}

/** 接口地址的问题清单（空数组 = 通过） */
function baseUrlProblems(url) {
  const problems = []
  if (!url) {
    problems.push('接口地址 VITE_SERVER_BASEURL 为空（env/.env.production 里还没填正式域名，也没用环境变量注入）')
    return problems
  }
  let parsed
  try {
    parsed = new URL(url)
  }
  catch {
    problems.push(`接口地址不是合法 URL：${url}`)
    return problems
  }
  if (parsed.protocol !== 'https:') problems.push(`接口地址不是 https：${url}（小程序正式版的 request 合法域名只认 https）`)
  const why = privateHostReason(parsed.hostname)
  if (why) problems.push(`接口地址是${why}：${url}（真机上连不到，正式包里不许出现）`)
  // 测试环境域名只对 production 算问题（test / staging 本来就指它）
  if (IS_PROD && /(^|\.)tianda\.studio$/i.test(parsed.hostname)) problems.push(`接口地址是测试环境域名：${url}（线上用户的数据会写进测试库）`)
  if (/(^|\.)(example\.(com|org|net)|invalid|test)$/i.test(parsed.hostname)) problems.push(`接口地址是占位域名：${url}`)
  if (parsed.pathname && parsed.pathname !== '/') problems.push(`接口地址不要带路径：${url}`)
  return problems
}

function appidProblems(appid) {
  if (!appid) return ['小程序 appid 为空（设置 LQG_WX_APPID，或在 env 里写 VITE_WX_APPID）']
  if (/^wx0+$/i.test(appid) || /^wxmock/i.test(appid) || appid === 'touristappid') return [`小程序 appid 是占位值「${appid}」，用它上传一定被微信拒`]
  if (!/^wx[0-9a-f]{16}$/i.test(appid)) return [`小程序 appid 格式不对「${appid}」（应为 wx + 16 位十六进制）`]
  return []
}

function mockProblems() {
  // ★ 2026-09-28 改文案（台账 #358）：不再说「冒充任意身份」——后端已经收窄成
  //   只认 lqg.auth.mock-identities 里预置的演示身份，且手机号由服务端决定。
  //   这里保留提示是为了**拦住正式版**：体验版带这个入口是 Kevin 明确要的（测试人员快速切换账号），
  //   但正式版绝不能带（生产构建里没有这个变量）。
  return MOCK === '1'
    ? ['mock 登录开着（VITE_MOCK_LOGIN=1）：这个包里会带「测试身份登录」快捷入口。'
       + '体验版有意为之（测试人员一键切换账号），但**正式版必须关**；'
       + '后端只接受预置演示身份（lqg.auth.mock-identities），任意身份会被拒']
    : []
}

/** 产物检查：mock 痕迹、回环 / 内网地址、接口地址是否真的打进了包 */
function artifactProblems(dir, url) {
  const problems = []
  if (!existsSync(path.join(dir, 'app.json'))) return [`产物不完整：${path.relative(HERE, dir)}/app.json 不存在`]
  const files = []
  const walk = (d) => {
    for (const n of readdirSync(d)) {
      const p = path.join(d, n)
      if (statSync(p).isDirectory()) walk(p)
      else if (/\.(js|json|wxml)$/.test(n)) files.push(p)
    }
  }
  walk(dir)
  const hits = { mock: [], loopback: [] }
  let hasBase = !url
  for (const f of files) {
    const s = readFileSync(f, 'utf8')
    const rel = path.relative(dir, f)
    if (/mock:|mock-openid-|1380000\d{4}/.test(s)) hits.mock.push(rel)
    if (/(?:https?:)?\/\/(?:127\.\d+\.\d+\.\d+|localhost|0\.0\.0\.0|192\.168\.\d+\.\d+|10\.\d+\.\d+\.\d+)(?::\d+)?/.test(s)) hits.loopback.push(rel)
    if (url && s.includes(url)) hasBase = true
  }
  if (hits.mock.length) problems.push(`产物里有 mock 登录的痕迹：${hits.mock.slice(0, 5).join('、')}`)
  if (hits.loopback.length) problems.push(`产物里有回环 / 内网地址：${hits.loopback.slice(0, 5).join('、')}`)
  if (!hasBase) problems.push(`产物里找不到接口地址 ${url}（构建时没吃到这个值？）`)
  return problems
}

function report(stage, problems) {
  if (problems.length === 0) {
    console.log(`[upload:mp] ✓ 发布守卫（${stage}）通过`)
    return
  }
  const title = IS_PROD ? `✗ 发布守卫（${stage}）拒绝上传 production：` : `! 发布守卫（${stage}）提示（${MODE} 模式只提示，不拦）：`
  console.error(`\n[upload:mp] ${title}`)
  problems.forEach((p, i) => console.error(`  ${i + 1}. ${p}`))
  if (IS_PROD) {
    console.error('\n  改好 env/.env.production（或用环境变量注入 VITE_SERVER_BASEURL / LQG_WX_APPID）后重跑；')
    console.error('  普通的 pnpm build:mp-weixin 不受这道守卫影响。\n')
    process.exit(3)
  }
}

console.log(`[upload:mp] mode=${MODE}  robot=${ROBOT}  appid=${APPID || '(未提供)'}`)
console.log(`[upload:mp] serverBaseUrl=${BASE_URL || '(未设置)'}  mockLogin=${MOCK === '1' ? '开' : '关'}`)

// 构建前：配置层面的守卫（production 下命中即退出，不白跑一次构建）
report('配置', [...baseUrlProblems(BASE_URL), ...appidProblems(APPID), ...mockProblems()])

// ── 1. 本地构建 ─────────────────────────────────────────────────────────────
if (args.skipBuild) {
  console.log(`[upload:mp] --skip-build：不重新构建，直接用 ${path.relative(HERE, OUT_DIR)}`)
}
else {
  mkdirSync(OUT_DIR, { recursive: true })
  console.log(`[upload:mp] 构建 → ${path.relative(HERE, OUT_DIR)}`)
  execFileSync('pnpm', ['exec', 'uni', 'build', '-p', 'mp-weixin', '--mode', MODE], {
    cwd: HERE,
    stdio: 'inherit',
    env: {
      ...process.env,
      UNI_OUTPUT_DIR: OUT_DIR,
      // 以本次生效的值构建：manifest 里的 appid 与请求根地址都和上传的一致
      ...(APPID ? { VITE_WX_APPID: APPID } : {}),
      VITE_SERVER_BASEURL: BASE_URL,
    },
  })
}

// 构建后：产物层面的守卫
report('产物', artifactProblems(OUT_DIR, BASE_URL))

// --build-only：构建与两道守卫都过了就收工（上传留给固定 IP 的服务器，见文件头说明）
if (args.buildOnly) {
  console.log(`[upload:mp] ✓ --build-only：产物已就位 ${path.relative(HERE, OUT_DIR)}（本次不上传）`)
  process.exit(0)
}

// ── 2. 凭据检查（缺了就说清楚缺什么，不硬猜）────────────────────────────────
if (appidProblems(APPID).length) {
  die(
    `${appidProblems(APPID)[0]}。\n`
    + `  需要 Kevin 做：① 给出小程序 appid（测试用乙方测试号或甲方主体号）；\n`
    + `                ② 在「微信公众平台 → 开发管理 → 开发设置」生成**上传密钥**并下载 private.<appid>.key；\n`
    + `                ③ 把接口域名加进「服务器域名 → request 合法域名」（必须 https 且已备案）。\n`
    + `  构建产物已就位：${path.relative(HERE, OUT_DIR)}（拿到凭据后重跑，可加 --skip-build）`,
  )
}
if (!KEY_PATH || !existsSync(KEY_PATH)) {
  die(
    `缺上传密钥：设置 LQG_WX_PRIVATE_KEY=<private.${APPID}.key 的绝对路径>。\n`
    + `  构建产物已就位：${path.relative(HERE, OUT_DIR)}（拿到密钥后重跑，可加 --skip-build）`,
  )
}

// ── 3. 上传 ──────────────────────────────────────────────────────────────────
const pkg = JSON.parse(readFileSync(path.join(HERE, 'package.json'), 'utf8'))
const commit = process.env.LQG_BUILD_COMMIT || (() => {
  try {
    return execFileSync('git', ['rev-parse', '--short=10', 'HEAD'], { cwd: HERE }).toString().trim()
  }
  catch {
    return 'nogit'
  }
})()
const version = args.version || (IS_PROD ? pkg.version : `${pkg.version}.${MODE}.${commit}`)
if (IS_PROD && !/^\d+\.\d+\.\d+$/.test(version)) die(`production 的版本号必须是 x.y.z，收到「${version}」`)
const desc = args.desc || (IS_PROD ? `正式版 ${version}` : `测试环境体验版（${MODE}，提交 ${commit}）`)

const ci = (await import('miniprogram-ci')).default
const project = new ci.Project({
  appid: APPID,
  type: 'miniProgram',
  projectPath: OUT_DIR,
  privateKeyPath: path.resolve(KEY_PATH),
  ignores: ['node_modules/**/*'],
})

console.log(`[upload:mp] 上传版本 ${version}（robot ${ROBOT}）…`)
await ci.upload({
  project,
  version,
  desc,
  robot: ROBOT,
  setting: { es6: true, minify: true, urlCheck: false },
  onProgressUpdate: t => process.stdout.write(`\r  ${String(t).slice(0, 120)}`),
})
console.log(`\n[upload:mp] ✓ 上传完成 —— 到「微信公众平台 → 版本管理」${IS_PROD ? '提交审核' : '把它设为体验版'}`)

if (!IS_PROD) {
  const qr = path.join(HERE, 'dist', `体验版二维码-${MODE}-${commit}.png`)
  console.log('[upload:mp] 生成体验版二维码 …')
  await ci.preview({
    project,
    desc: `${MODE} 体验版二维码（${commit}）`,
    robot: ROBOT,
    qrcodeFormat: 'image',
    qrcodeOutputDest: qr,
    setting: { es6: true, minify: true, urlCheck: false },
  })
  console.log(`[upload:mp] ✓ 二维码：${qr}`)
  console.log('[upload:mp] 二维码**不要提交进仓库**（已经落在 dist/，git 忽略了 code/miniapp/dist）')
}
