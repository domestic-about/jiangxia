// 请求根地址的**唯一来源**（从 `utils/request.ts` 抽出来，SYS-EXPORT-001）。
//
// ★ 为什么单独一个文件：`utils/fileHandoff.ts` 要在**平台侧**拼一个可下载的完整 URL
//   （`wx.downloadFile` / H5 的 `<a download>` 都不吃相对路径），而它跑在 node 环境的
//   单测里（`export.spec.ts`）。把根地址判据留在这个**不 import 任何东西**的模块里，
//   测试里 import `fileHandoff` 就不会顺带把 `utils/request.ts`（连带 `uni.*` 与
//   `import.meta.env`）拉进 node 的加载图。
//
// ★ 根地址只能有一个来源（OCR-MP-001 踩过：`uni.uploadFile` 与 `uni.request` 各拼一次，
//   变成「H5 代理开着、上传却直连后端」）。`utils/request.ts` 从这里 re-export，
//   别的模块仍旧 `import { resolveBaseUrl } from '@/utils/request'` 一字不用改。
//
// - 小程序 / App：直连 `VITE_SERVER_BASEURL`（真机调试时它是 LAN IP，见栈包 gotchas §6.6）
// - H5：走 vite dev server 代理前缀，绕开浏览器跨域
export function resolveBaseUrl(): string {
  const direct = (import.meta.env.VITE_SERVER_BASEURL as string) || ''
  // #ifdef H5
  if (import.meta.env.DEV && import.meta.env.VITE_APP_PROXY_ENABLE === 'true') {
    return (import.meta.env.VITE_APP_PROXY_PREFIX as string) || ''
  }
  // #endif
  return direct
}
