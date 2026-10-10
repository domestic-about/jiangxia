import { createSSRApp } from 'vue'
import App from './App.vue'
import store from './store'
import { sharePayload } from './utils/share'

import '@/style/index.scss'
import 'virtual:uno.css'

export function createApp() {
  const app = createSSRApp(App)
  app.use(store)

  // 「转发给朋友」（CR-20261010-19）：全局混入 —— 每个页面右上角「···」里的「转发给朋友」都可用，
  // 卡片内容全站一份（`utils/share.ts`：固定标题、固定封面、落地首页）。
  // ★ 必须走全局混入：uni-app 的小程序运行时建页面时只认两处的 `onShareAppMessage` ——
  //   页面自己写的，或者 `app.mixin` 里的（`initMixinRuntimeHooks`）；写在公共组合函数里页面不会被注册上。
  // ★ 以后哪个页面要发别的内容，在那个页面自己写 `onShareAppMessage` 即可（后注册的生效），
  //   但封面必须仍给固定图 —— 不给就是当前页截图，会把患者信息带出去。
  app.mixin({
    onShareAppMessage() {
      return sharePayload()
    },
  })

  return {
    app,
  }
}
