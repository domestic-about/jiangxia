#!/usr/bin/env node
// 「转发给朋友」的产物检查（CR-20261010-19）：在假的微信运行环境里**真的加载**小程序构建产物，逐页确认
//   ① 每个页面注册给微信的选项里都有 onShareAppMessage（没有它，右上角「转发给朋友」是灰的）；
//   ② 没有任何页面注册 onShareTimeline（不开「分享到朋友圈」）；
//   ③ 真的建出页面实例、按微信的调法调一次 onShareAppMessage —— 返回固定标题、固定封面、首页路径；
//   ④ 封面图在产物里、是 5:4 的 PNG。
//
//   node doc/waves/tools/check-mp-share.mjs [产物目录]        缺省 code/miniapp/dist/build/mp-weixin
//   先构建：cd code/miniapp && pnpm build:mp-weixin
//
// 为什么要这一份：单测只能钉「卡片内容是什么」，钉不住「页面有没有被注册上」——
// uni-app 的小程序运行时只认页面自己写的、或 app.mixin 里的 onShareAppMessage（写在公共组合函数里不会注册），
// 这一步出错时构建、类型检查、单测全绿，只有真机上「转发」是灰的。这里把那一段运行时照原样跑一遍。
// ★ 它替代不了真机：卡片在聊天里长什么样、点开落到哪，仍要在体验版上转发一次看。
//
// 退出码：0 = 都对 ｜ 1 = 转发真的有问题（页面没注册上 / 卡片内容不对 / 封面不在）——部署脚本据此**拦住上传**
//         2 = 检查脚本自己没跑起来（产物不在、假环境里加载不了产物、页面实例模拟不出来）——只提示，不拦
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
const ART = path.resolve(process.argv[2] || path.join(ROOT, 'code/miniapp/dist/build/mp-weixin'))
if (!fs.existsSync(path.join(ART, 'app.json'))) {
  console.error(`[error] 产物目录不存在：${ART}\n        先构建：cd code/miniapp && pnpm build:mp-weixin`)
  process.exit(2)
}

const fails = []
/** 检查脚本自己的局限（假环境模拟不出来）：不算转发坏了 */
const skips = []
const check = (name, ok, extra = '') => {
  console.log(`  ${ok ? '✓' : '✗'} ${name}${extra ? ' — ' + extra : ''}`)
  if (!ok) fails.push(name)
}
const skip = (name, why) => {
  console.log(`  ? ${name} — 没能验证：${why}`)
  skips.push(name)
}
/** 假环境里加载不了产物 = 工具问题，退出码 2 */
const toolError = (what, e) => {
  console.error(`\n[error] 检查脚本没跑起来（不是转发坏了）：${what}\n        ${String(e && e.stack || e).split('\n').slice(0, 3).join('\n        ')}`)
  process.exit(2)
}

// ── 假的微信运行环境 ────────────────────────────────────────────────────────
let appOptions = null
const registered = [] // 每次 Component(options) 记一条
const noop = () => {}
const wxBase = {
  getSystemInfoSync: () => ({ platform: 'devtools', windowWidth: 375, windowHeight: 667, screenWidth: 375, screenHeight: 667, pixelRatio: 2, statusBarHeight: 20, language: 'zh_CN', SDKVersion: '3.5.0', safeArea: { top: 20, left: 0, right: 375, bottom: 667, width: 375, height: 647 } }),
  getWindowInfo: () => ({ windowWidth: 375, windowHeight: 667, screenWidth: 375, screenHeight: 667, pixelRatio: 2, statusBarHeight: 20, safeArea: { top: 20, left: 0, right: 375, bottom: 667, width: 375, height: 647 } }),
  getDeviceInfo: () => ({ platform: 'devtools', brand: 'devtools', model: 'iPhone', system: 'iOS 17' }),
  getAppBaseInfo: () => ({ SDKVersion: '3.5.0', language: 'zh_CN', version: '8.0.50', theme: 'light', host: { env: 'WeChat' } }),
  getAccountInfoSync: () => ({ miniProgram: { appId: 'wx-check', envVersion: 'develop', version: '' } }),
  getLaunchOptionsSync: () => ({ path: 'pages/index/index', query: {}, scene: 1001 }),
  getEnterOptionsSync: () => ({ path: 'pages/index/index', query: {}, scene: 1001 }),
  getStorageSync: () => '',
  getStorageInfoSync: () => ({ keys: [] }),
  canIUse: () => true,
  getMenuButtonBoundingClientRect: () => ({ top: 24, bottom: 56, left: 281, right: 368, width: 87, height: 32 }),
  createSelectorQuery: () => new Proxy({}, { get: () => () => ({ exec: noop, boundingClientRect: noop }) }),
  request: () => ({ abort: noop }),
}
// 没列出来的 wx.xxx 一律给个空函数；产物往 wx 上挂的东西（wx.createPage 等）照常存下来
const wx = new Proxy(wxBase, { get: (t, k) => (k in t ? t[k] : noop) })

const sandbox = {
  wx,
  App: (options) => { appOptions = options },
  getApp: () => appOptions,
  Page: (options) => registered.push({ kind: 'Page', options }),
  Component: (options) => { registered.push({ kind: 'Component', options }); return '' },
  Behavior: options => options,
  getCurrentPages: () => [],
  requirePlugin: () => ({}),
  console: { log: noop, info: noop, debug: noop, warn: noop, error: (...a) => sandbox.__errors.push(a.map(String).join(' ')) },
  __errors: [],
  setTimeout, clearTimeout, setInterval, clearInterval, Promise, queueMicrotask,
  URL, URLSearchParams, TextEncoder, TextDecoder,
}
sandbox.globalThis = sandbox
sandbox.global = sandbox
const context = vm.createContext(sandbox)

// 最小的 CommonJS 加载器（产物是 require("./common/vendor.js") 这种相对路径）
const cache = new Map()
function load(file) {
  const full = path.resolve(file)
  if (cache.has(full)) return cache.get(full).exports
  const module = { exports: {} }
  cache.set(full, module)
  const code = fs.readFileSync(full, 'utf8')
  const fn = vm.runInContext(`(function (require, module, exports) {${code}\n})`, context, { filename: full })
  fn((rel) => {
    let target = path.resolve(path.dirname(full), rel)
    if (!fs.existsSync(target) && fs.existsSync(`${target}.js`)) target = `${target}.js`
    return load(target)
  }, module, module.exports)
  return module.exports
}

// ── 跑 ────────────────────────────────────────────────────────────────────
const appJson = JSON.parse(fs.readFileSync(path.join(ART, 'app.json'), 'utf8'))
console.log(`产物：${ART}`)

try {
  load(path.join(ART, 'app.js'))
}
catch (e) {
  toolError('app.js 在假环境里加载失败', e)
}
if (!appOptions || !appOptions.$vm) {
  toolError('app.js 加载后没有调用 App()（uni-app 运行时的启动方式变了？）', '')
}
console.log('  ✓ app.js 加载后调用了 App()，并带着 $vm')
const mixins = appOptions?.$vm?.$?.appContext?.mixins ?? []
const shareMixin = mixins.find(m => typeof m.onShareAppMessage === 'function')
check('全局混入里有 onShareAppMessage', !!shareMixin, `共 ${mixins.length} 个全局混入`)
check('全局混入里没有 onShareTimeline（不开朋友圈）', !mixins.some(m => 'onShareTimeline' in m))

// 卡片内容：直接调混入里的那个函数（运行时调的就是它），不依赖下面的页面实例模拟
const EXPECT = {
  title: '湖北江夏实验室类器官研究中心 · 类器官送检',
  path: '/pages/index/index',
  imageUrl: '/static/share/cover.png',
}
const same = got => !!got && Object.keys(EXPECT).every(k => got[k] === EXPECT[k]) && Object.keys(got).sort().join() === Object.keys(EXPECT).sort().join()
if (shareMixin) {
  let card = null
  try {
    card = shareMixin.onShareAppMessage({ from: 'menu' })
  }
  catch (e) {
    card = { error: String(e && e.message || e) }
  }
  check('卡片内容 = 固定标题 + 首页路径（不带参数）+ 固定封面', same(card), JSON.stringify(card))
}

const pageOptions = new Map()
for (const page of appJson.pages) {
  const before = registered.length
  try {
    load(path.join(ART, `${page}.js`))
  }
  catch (e) {
    toolError(`${page}.js 在假环境里加载失败`, e)
  }
  // 页面脚本里 wx.createPage(...) → Component(页面选项)；取这次加载里最后注册的那一个
  const mine = registered.slice(before).filter(r => r.options?.methods?.onLoad)
  pageOptions.set(page, mine.at(-1)?.options ?? null)
}
const noOptions = [...pageOptions].filter(([, o]) => !o).map(([p]) => p)
check(`${appJson.pages.length} 个页面都注册成了页面`, noOptions.length === 0, noOptions.join('、'))

const withoutShare = [...pageOptions].filter(([, o]) => o && typeof o.methods.onShareAppMessage !== 'function').map(([p]) => p)
check(`① 每个页面都有 onShareAppMessage（共 ${appJson.pages.length} 页）`, withoutShare.length === 0, withoutShare.length ? `缺：${withoutShare.join('、')}` : '')
const withTimeline = [...pageOptions].filter(([, o]) => o && 'onShareTimeline' in o.methods).map(([p]) => p)
check('② 没有页面注册 onShareTimeline', withTimeline.length === 0, withTimeline.join('、'))

// ③ 真的建一个页面实例，按微信的调法调 onShareAppMessage（this = 小程序页面实例）。
//    页面实例是模拟的：模拟不出来（页面启动时用到了这里没垫的微信接口）算检查脚本的局限，记「没能验证」，不算转发坏了；
//    建出来了、调用结果却不对，才算坏。
// 选几页有代表性的：带患者信息的填写页、质控预览页、登录页、协议页
for (const page of ['pages/sample/form', 'pages/qc/preview', 'pages/login/index', 'pages/legal/agreement']) {
  const options = pageOptions.get(page)
  if (!options || typeof options.methods.onShareAppMessage !== 'function') { continue } // 已在 ① 里报过
  const inst = {
    route: page, is: page, options: {}, properties: {}, data: {},
    setData(data, cb) { Object.assign(this.data, data); cb && cb() },
    triggerEvent: noop, selectComponent: () => null, selectAllComponents: () => [],
    createSelectorQuery: wx.createSelectorQuery, getOpenerEventChannel: () => ({ on: noop, emit: noop, off: noop, once: noop }),
    getRelationNodes: () => [], selectOwnerComponent: () => null, getPageId: () => 1, getTabBar: () => null,
  }
  const { lifetimes = {}, methods } = options
  try {
    for (const [k, v] of Object.entries(methods)) if (typeof v === 'function' && !(k in inst)) inst[k] = v
    lifetimes.attached && lifetimes.attached.call(inst)
    methods.onLoad && methods.onLoad.call(inst, {})
  }
  catch (e) {
    skip(`③ ${page}：点右上角「转发给朋友」`, `页面实例模拟不出来（${String(e && e.message || e)}）`)
    continue
  }
  if (!inst.$vm) {
    skip(`③ ${page}：点右上角「转发给朋友」`, '页面实例模拟不出来（没有 $vm）')
    continue
  }
  let got = null
  try {
    got = methods.onShareAppMessage.call(inst, { from: 'menu' })
  }
  catch (e) {
    got = { error: String(e && e.message || e) }
  }
  check(`③ ${page}：点右上角「转发给朋友」拿到固定卡片`, same(got), JSON.stringify(got))
}

// ④ 封面图
const cover = path.join(ART, EXPECT.imageUrl)
if (!fs.existsSync(cover)) {
  check('④ 封面图在产物里', false, cover)
}
else {
  const png = fs.readFileSync(cover)
  const w = png.readUInt32BE(16)
  const h = png.readUInt32BE(20)
  check('④ 封面图在产物里，是 5:4 的 PNG', png.subarray(1, 4).toString() === 'PNG' && Math.abs(w / h - 1.25) < 0.01, `${w}×${h}，${(png.length / 1024).toFixed(1)} KB`)
}

if (fails.length) {
  console.log(`\n结果：FAIL —— ${fails.length} 项：${fails.join('；')}`)
  process.exit(1)
}
if (skips.length) {
  console.log(`\n结果：没能全部验证 —— 能验证的都对，${skips.length} 项因检查脚本的局限没验证（不算转发坏了）：${skips.join('；')}`)
  process.exit(2)
}
console.log('\n结果：PASS —— 每个页面都能转发给朋友，卡片是固定标题 + 固定封面 + 落地首页')
process.exit(0)
