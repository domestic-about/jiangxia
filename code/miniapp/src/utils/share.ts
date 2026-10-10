// 「转发给朋友」的卡片内容（CR-20261010-19）—— 全站只有这一份，`main.ts` 的全局混入每个页面都用它。
//
// 用途：实验室人员把小程序发给合作单位、同事之间互相发 —— 转发的是**小程序本身**，不是某一条记录。
//
// 三件最容易做反的事（都有单测钉着）：
//   1. **封面必须是固定图**：不给 `imageUrl` 时，微信默认拿「当前页面的截图」当卡片封面 ——
//      在样本填写页、质控文档页点转发，供体姓名、住院号就跟着截图发出去了。所以这里恒给一张不含任何数据的品牌图。
//   2. **落地页恒为首页、不带任何参数**：不把当前页的路径 / 记录 id 放进卡片（别人点开看到的应该是自己的首页，
//      不是「某条样本」的深链；没登录的会被 `App.vue` 带去登录页，能看什么仍由后端按身份决定）。
//   3. **标题是固定文案**：不拼当前页的任何内容（单位名、样本编号、患者信息都不许出现在卡片上）。
//
// 只开「转发给朋友」（`onShareAppMessage`）；**不开**「分享到朋友圈」（`onShareTimeline`）——
// 这是给合作单位用的业务工具，不往朋友圈发。
import { LEGAL_OPERATOR, LEGAL_SERVICE } from '@/config/legal'
import { HOME_PAGE } from '@/router/config'

/** 卡片封面：5:4 的品牌图（`scripts/make-share-cover.mjs` 生成，不含任何业务数据） */
export const SHARE_COVER = '/static/share/cover.png'

/** 卡片标题：运营方 + 小程序名（与登录页同一组文案） */
export const SHARE_TITLE = `${LEGAL_OPERATOR} · ${LEGAL_SERVICE}`

/** 微信 `onShareAppMessage` 的返回值 */
export interface SharePayload {
  title: string
  path: string
  imageUrl: string
}

/** 转发卡片内容：与当前在哪一页、登录的是谁都无关 */
export function sharePayload(): SharePayload {
  return {
    title: SHARE_TITLE,
    path: HOME_PAGE,
    imageUrl: SHARE_COVER,
  }
}
