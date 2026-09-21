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
  'mp-weixin': {
    appid: VITE_WX_APPID,
    setting: {
      urlCheck: false,
      es6: true,
      minified: true,
      postcss: true,
    },
    usingComponents: true,
    lazyCodeLoading: 'requiredComponents',
  },
  h5: {
    router: {
      base: VITE_APP_PUBLIC_BASE,
      mode: 'hash',
    },
  },
})
