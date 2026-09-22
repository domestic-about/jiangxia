// 小程序里「配置化」的几句文案（REQ-DOC-010 是 clarify：页签名与板块名甲方还没定）。
//
// ★ 为什么单独一个文件：改名**不发版**——页签文字在 pages.config.ts 里引它（构建期），
//   页面内的板块标题在运行时引它；只有一处字面量，改一处两边都跟着变。
export const DOC_TAB_NAME = '文档'
export const DOC_SECTION_NAME = '质控文档'
