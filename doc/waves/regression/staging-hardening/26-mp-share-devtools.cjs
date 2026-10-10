// CR-20261010-19「转发给朋友」—— 在**微信开发者工具的模拟器（真实小程序运行时）**里验证：
// 对真实的页面实例调用 onShareAppMessage（微信就是靠页面实例上有没有它来决定「转发给朋友」亮不亮、卡片发什么）。
//
// 这是手动跑的补充验证（不进 CI：要本机装了开发者工具并登录）。日常的自动判据是
// doc/waves/tools/check-mp-share.mjs（假环境里加载产物，部署脚本每次上传前跑）。
//
// 跑法（三步）：
//   1. 出一份产物，并把**这份产物**的 appid 改成游客 appid（本机登录开发者工具的微信号若不是本小程序的开发者，
//      用真 appid 打不开项目：「登录用户不是该小程序的开发者」）：
//        cd code/miniapp && UNI_OUTPUT_DIR="$PWD/dist/build/mp-weixin-devcheck" pnpm exec uni build -p mp-weixin --mode development
//        改 dist/build/mp-weixin-devcheck/project.config.json 的 "appid" 为 "touristappid"（dist 不进仓库）
//   2. 让开发者工具以自动化方式打开它（必须带 --trust-project，否则卡在「是否信任该项目」，脚本这边只会超时）：
//        /Applications/wechatwebdevtools.app/Contents/MacOS/cli auto --project <上面的目录> --auto-port 9420 --trust-project
//   3. 在装了 miniprogram-automator 的目录里跑本脚本（项目依赖里没有它，临时目录里 npm i miniprogram-automator 即可）：
//        NODE_PATH=<那个目录>/node_modules node doc/waves/regression/staging-hardening/26-mp-share-devtools.cjs
//      跑完：cli close --project <目录>；开发者工具原来没开的话 cli quit。
//
// 只读：不登录、不提交任何东西。不验证「登录后的页面」——本机走系统代理时，模拟器发往 127.0.0.1 的请求会失败，
// 测试登录登不上；卡片内容与页面有没有数据无关，所以不影响结论。
// ★ 它仍然替代不了真机：卡片在聊天里的样子、别人点开后的落地，要在体验版上转发一次看。
const automator = require('miniprogram-automator')
// 自动化库 0.12.1 连接后的版本检查在新版开发者工具上拿不到 SDKVersion 会崩；跳过这一步检查（不影响后面的调用）
const MiniProgram = require('miniprogram-automator/out/MiniProgram').default
MiniProgram.prototype.checkVersion = async function () {}

const WS = process.env.LQG_AUTOMATOR_WS || 'ws://localhost:9420'
const EXPECT = { title: '湖北江夏实验室类器官研究中心 · 类器官送检', path: '/pages/index/index', imageUrl: '/static/share/cover.png' }
const fails = []
const check = (n, ok, x = '') => { console.log(`  ${ok ? '✓' : '✗'} ${n}${x ? ' — ' + x : ''}`); if (!ok) fails.push(n) }
const same = g => !!g && Object.keys(EXPECT).every(k => g[k] === EXPECT[k]) && Object.keys(g).sort().join() === Object.keys(EXPECT).sort().join()
const sleep = ms => new Promise(r => setTimeout(r, ms))

;(async () => {
  const mp = await automator.connect({ wsEndpoint: WS })
  try {
    const info = await mp.systemInfo().catch(() => ({}))
    console.log(`运行环境：微信开发者工具模拟器 · 基础库 ${info.SDKVersion || '?'} · ${info.model || '?'}`)

    async function shareOn(label) {
      const page = await mp.currentPage()
      let got = null
      let err = ''
      try { got = await page.callMethod('onShareAppMessage', { from: 'menu' }) }
      catch (e) { err = String(e && e.message || e) }
      check(`${label}（${page.path}）：onShareAppMessage 返回固定卡片`, same(got), err || JSON.stringify(got))
      return page
    }

    // 登录页、首页（卡片的落地页）。
    // 注：「没登录的人点开卡片先到登录页」靠 App.vue 的 onLaunch（冷启动才走），这里的 reLaunch 不是冷启动、验证不了它；
    //     2026-10-10 第一次用 cli auto 冷启动打开项目时观察到确实落在 pages/login/index。
    await mp.reLaunch('/pages/login/index'); await sleep(2000)
    let page = await shareOn('登录页')
    await mp.reLaunch('/pages/index/index'); await sleep(2500)
    await shareOn('首页（卡片的落地页）')

    await mp.navigateTo('/pages/legal/privacy'); await sleep(1500)
    await shareOn('隐私政策页')
    await mp.navigateBack(); await sleep(800)

    // 表单页与文档页：没登录时页面是「没能加载」，但页面实例照常在，转发钩子与数据无关
    await mp.navigateTo('/pages/sample/form?id=9000001001&mode=view'); await sleep(2500)
    await shareOn('样本记录信息表页')
    await mp.navigateBack(); await sleep(800)
    await mp.navigateTo('/pages/qc/preview?sampleId=9000001001'); await sleep(2500)
    await shareOn('质控文档预览页')

    page = await mp.currentPage()
    let timeline = 'none'
    try { timeline = JSON.stringify(await page.callMethod('onShareTimeline')) }
    catch { timeline = 'none' }
    check('页面实例上没有 onShareTimeline（不开朋友圈）', timeline === 'none' || timeline === 'undefined' || timeline === 'null', timeline)
  }
  finally {
    mp.disconnect()
  }
  console.log(fails.length ? `\n结果：FAIL —— ${fails.join('；')}` : '\n结果：PASS（微信开发者工具模拟器，真实小程序运行时）')
  process.exit(fails.length ? 1 : 0)
})().catch((e) => { console.error('[error]', e && e.stack || e); process.exit(2) })
