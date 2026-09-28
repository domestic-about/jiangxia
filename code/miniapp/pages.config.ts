import { defineUniPages } from '@uni-helper/vite-plugin-uni-pages'
import { DOC_TAB_NAME } from './src/config/app'

/**
 * 小程序页面配置（由 @uni-helper/vite-plugin-uni-pages 生成 pages.json，**别手改产物**）。
 *
 * - 全局导航栏：白底黑字；页面底 #F3F6F6（= tokens.scss 的 --lqg-bg）
 *   这是小程序**页面配置**不是样式，允许写色值（落地规范 §0.2 / accept 第 3 条的排除项）。
 * - 页签顺序钉死：首页 / 文档 / 我的（ticket §2；accept 第 1 条 jq 断言）。
 *   用原生 tabbar：白底、青绿选中色，选中/未选中色与 --lqg-primary 同一组。
 * - 页签名取 `src/config/app.ts` 的 `DOC_TAB_NAME`（REQ-DOC-010 是 clarify：改名不发版，
 *   改一处两边都变 —— 页面里的板块名取同一份配置的 `DOC_SECTION_NAME`）。
 */
export default defineUniPages({
  globalStyle: {
    navigationStyle: 'default',
    navigationBarTitleText: '类器官送检',
    navigationBarBackgroundColor: '#FFFFFF',
    navigationBarTextStyle: 'black',
    backgroundColor: '#F3F6F6',
    backgroundTextStyle: 'dark',
  },
  easycom: {
    autoscan: true,
    custom: {
      '^wd-(.*)': 'wot-design-uni/components/wd-$1/wd-$1.vue',
    },
  },
  tabBar: {
    color: '#93A1A8',
    selectedColor: '#0E7C7B',
    backgroundColor: '#FFFFFF',
    borderStyle: 'black',
    list: [
      {
        pagePath: 'pages/index/index',
        text: '首页',
        iconPath: 'static/tabbar/home.png',
        selectedIconPath: 'static/tabbar/homeHL.png',
      },
      {
        pagePath: 'pages/doc/index',
        text: DOC_TAB_NAME,
        iconPath: 'static/tabbar/docs.png',
        selectedIconPath: 'static/tabbar/docsHL.png',
      },
      {
        pagePath: 'pages/me/index',
        text: '我的',
        iconPath: 'static/tabbar/personal.png',
        selectedIconPath: 'static/tabbar/personalHL.png',
      },
    ],
  },
})
