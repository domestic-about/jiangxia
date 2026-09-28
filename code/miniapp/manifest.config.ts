import process from 'node:process'
import { defineManifestConfig } from '@uni-helper/vite-plugin-uni-manifest'
import { loadEnv } from 'vite'

// 读取 --mode 指定的 env（与 vite.config.ts 的 envDir 对齐）
function resolveMode(): string {
  const args = process.argv.slice(2)
  const idx = args.findIndex(arg => arg === '--mode')
  if (idx !== -1 && args[idx + 1]) {
    return args[idx + 1]
  }
  return args[0] === 'build' ? 'production' : 'development'
}

const env = loadEnv(resolveMode(), `${process.cwd()}/env`)
const {
  VITE_APP_TITLE,
  VITE_UNI_APPID,
  VITE_WX_APPID,
  VITE_APP_PUBLIC_BASE,
} = env

/**
 * manifest.json 生成配置（别手改产物 src/manifest.json）。
 * 本项目只发微信小程序（SYS-MP-001）；app / 其它平台不配置。
 */
export default defineManifestConfig({
  name: VITE_APP_TITLE,
  appid: VITE_UNI_APPID,
  description: '类器官送检与样本管理系统 · 小程序',
  versionName: '1.0.1',
  versionCode: '101',
  transformPx: false,
  // ★ 2026-09-28：**不要**开 `lazyCodeLoading: 'requiredComponents'`。
  //   实测（Kevin 真机白屏 + 开发者工具报 `Component is not found in path "wx://not-found"`）：
  //   本项目的产物里有**全局组件**（`global-ku-root`，由 @uni-ku/root 注入，app.json 的
  //   usingComponents 里声明），而「按需注入」只加载页面/组件**显式声明**的组件，
  //   全局注册的那个解析不到 → 根组件缺失 → 整页白屏（真机必现，开发者工具不一定复现）。
  //   微信官方文档与多起同类案例都指向这个组合：
  //     https://developers.weixin.qq.com/miniprogram/dev/framework/ability/lazyload.html
  //     https://blog.csdn.net/weixin_61195170/article/details/146424861
  //   性能上的收益远小于「真机白屏」的代价，所以这里直接不开（缺省即不用按需注入）。
  'mp-weixin': {
    appid: VITE_WX_APPID,
    setting: {
      urlCheck: false,
      es6: true,
      minified: true,
      postcss: true,
    },
    usingComponents: true,
    // 隐私保护指引（V06 / SYS-RELEASE-001）：登录页强制勾选《用户协议》《隐私政策》，
    // 手机号快捷登录、拍照识别用到的相机与相册都是微信的隐私接口 —— 显式声明按隐私指引校验。
    __usePrivacyCheck__: true,
  },
  h5: {
    router: {
      base: VITE_APP_PUBLIC_BASE,
      mode: 'hash',
    },
  },
})
