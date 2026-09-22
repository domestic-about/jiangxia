// 「临时文件 → 打开 / 发送到微信」的**公共段**（SYS-EXPORT-001 · FLOW:F-DOC-02.step4）。
//
// ★★ 一处实现、两个调用方（ticket §0 口径 4 / Accept 2 第 4 段）：
//   ① **文档下载**（DOC-MP-002）：`components/lqg/DownloadBar.vue` → Word / PDF；
//   ② **表格导出**（本票）：`pages/ledger/index.vue` → 四张工作表 xlsx。
//   原先这段交互长在 `DownloadBar.vue` 里（`uni.downloadFile` → `openDocument` / 转发文件），
//   导出再抄一份就是**两份平台实现** —— 一处带右上角菜单、一处不带，或一处忘带鉴权头，
//   用户拿到的东西就悄悄不一样了。所以抽到这里，两边都调它。
//
// ★ **下载必须带鉴权头**（ticket §0 口径 3 / Accept 2 第 5 段）：导出与文档下载都是
//   **鉴权接口**（不是 OSS 签名链接），所以 `downloadToTemp` 收一个 header 参数，里面
//   必须带 `Authorization`（框架的 `downloadFile` 也吃它）。
//   头从哪儿来由调用方给（`authHeader()` 在 `pages/ledger/export.ts` / `DownloadBar` 里），
//   本文件不 import 请求层 / 不 import `import.meta.env` —— 它要被 node 环境的单测直接
//   import（`export.spec.ts` 用 vitest，environment=node）。
//
// ★ 平台差异**如实退化**，不假装成功：
//   · `shareFileMessage` 只有小程序端有（H5 上 `typeof uni.shareFileMessage === "undefined"`，
//     DOC-MP-002 实测）→ 给一句人话，不当成失败；
//   · `openDocument` 小程序端一定有（微信内置查看器，**把菜单开关传开**才有右上角保存 / 转发）；
//     H5 上没有 → 退化成浏览器打开该链接（PDF / xlsx 由浏览器自己决定怎么处理）。
//   ★ 真机 / 微信里未覆盖（appid 未提供，走本地 Mock 口径）：报告里如实写。
import { resolveBaseUrl } from '@/utils/baseUrl'

/** 追加在 URL 后面的查询串（空串 = 不追加），由调用方拼好 */
export type FileUrl = string

/** `downloadToTemp` 的入参：要下的地址 + 要带的请求头 */
export interface DownloadOptions {
  /**
   * 请求头。**必须带 `Authorization`**（导出 / 文档下载都是鉴权接口）。
   *
   * ★ `wx.downloadFile` 的 header 里 `Authorization` 与 `clientid` 两个都要
   *   （缺 clientid 时后端拿不到租户上下文）。
   */
  header?: Record<string, string>
}

/** 小数坑：H5 上没有 `uni.downloadFile` 时，下载退化成浏览器直接打开链接 */
declare const window: any
/**
 * 这个地址是不是「已经在本地了」（blob: / data: / 微信的临时文件路径）。
 *
 * 用来决定「打开」这一步怎么走：已经在本地的直接交给平台打开器，
 * 还没下下来的（http/https 签名链接）先落到临时文件。
 */
export function isLocalFile(path?: string | null): boolean {
  const value = String(path ?? '').trim().toLowerCase()
  return value.startsWith('blob:') || value.startsWith('data:') || value.startsWith('wxfile:')
    || value.startsWith('file:')
}

/** 地址是不是 http(s)（要下载） */
export function isRemoteUrl(url?: string | null): boolean {
  return /^https?:\/\//i.test(String(url ?? '').trim())
}

/**
 * 一份文件该用什么 `fileType` 交给小程序的文档查看器。
 *
 * ★ 这是 `pages/doc/download.ts#openDocumentType` 的**薄包装**：那一条判据已经被
 *   DOC-MP-002 的单测钉住（`download.fixture.spec.ts` 里 `.xlsx → xls` 那条），
 *   本文件不复制它的规则 —— 只把「拼 URL / 传输」这层平台能力收口。
 */
export { openDocumentType } from '@/pages/doc/download'

/**
 * 从远端取到**临时文件路径**，供「打开 / 发送到微信」使用。
 *
 * - 小程序端：`uni.downloadFile({url, header})` → `tempFilePath`
 * - H5（本地 Mock 验收面）：uni-h5 的 `downloadFile` 不认相对路径 → 用 `fetch(url, {header})`
 *   取 blob，再造一个 `blob:` 地址（浏览器打开 / `<a download>` 都吃）
 *
 * ★ **地址要先是完整的**：导出给的是相对地址（`/mp/int/export/tissue?…`），
 *   根地址的判据只有一处（`utils/baseUrl.ts`）—— `uni.request` 会自己拼，
 *   `downloadFile` / `fetch` 不会（H5 上相对路径会被 dev server 当成前端路由，
 *   拿回来一页 517 字节的 HTML）。所以这里统一先拼一次。
 *
 * @param url    文件地址（相对 / 绝对都收）
 * @param header 请求头；**必须带 `Authorization`**（鉴权接口，不是签名链接）。
 *               ★ 两个调用方都要传：`pages/ledger/index.vue`（导出）与
 *               `components/lqg/DownloadBar.vue`（文档下载）—— 后者漏了就是
 *               「文档下载在真机上 401」的静默回归（Accept 2 第 5 段）。
 * @returns 本地路径 / blob 地址；取不到时 reject（调用方给「重试」）
 */
export async function downloadToTemp(url: FileUrl, header?: Record<string, string>): Promise<string> {
  // 鉴权接口：没带头就下不动（真机上拿到的是一段 401 的 JSON，当成 xlsx 打开失败）
  if (!header || !header.Authorization) {
    throw new Error('下载缺少鉴权头（导出 / 文档下载都要带 Authorization）')
  }
  const target = absoluteUrl(url)
  const api = (uni as any).downloadFile
  if (typeof api === 'function' && !isWebRuntime()) {
    return new Promise((resolve, reject) => {
      api({
        url: target,
        header: header ?? {},
        success: (res: any) => {
          const path = String(res?.tempFilePath ?? '')
          if (path) {
            resolve(path)
            return
          }
          reject(new Error('downloadFile 没给临时路径'))
        },
        fail: () => reject(new Error('downloadFile 失败')),
      })
    })
  }
  // H5：uni-h5 的 downloadFile 不吃鉴权头 / 不认相对路径 → fetch + blob
  return downloadToBlob(target, header)
}

/** 平台是不是浏览器（H5 本地 Mock 验收面）。条件编译 + 运行时双保险。 */
function isWebRuntime(): boolean {
  // #ifdef H5
  return true
  // #endif
  // eslint-disable-next-line no-unreachable
  return false
}

/**
 * 补全根地址（相对地址才拼；绝对地址原样返回）。
 *
 * ★ 根地址的唯一来源是 `utils/baseUrl.ts`（H5 dev 下是 vite 代理前缀 `/lqg-api`，
 *   小程序 / 真机下是 `VITE_SERVER_BASEURL`）—— 与 `utils/request.ts`、`api/ocr.ts` 同源。
 */
export function absoluteUrl(url: string): string {
  const value = String(url ?? '').trim()
  if (!value || isRemoteUrl(value)) {
    return value
  }
  const base = resolveBaseUrl()
  if (!base) {
    return value
  }
  // H5 dev 的 base 是相对前缀（`/lqg-api`）：`uni.downloadFile` / `fetch` 都要求绝对地址
  if (base.startsWith('http')) {
    return `${base.replace(/\/+$/, '')}${value}`
  }
  // #ifdef H5
  return `${location.origin}${base.replace(/\/+$/, '')}${value}`
  // #endif
  // eslint-disable-next-line no-unreachable
  return `${base.replace(/\/+$/, '')}${value}`
}

/** H5：`fetch` → `blob:` 地址（真机永远走不到这里；见文件头的平台差异说明） */
async function downloadToBlob(url: string, header?: Record<string, string>): Promise<string> {
  // #ifdef H5
  const res = await fetch(url, { headers: header ?? {} })
  if (!res.ok) {
    throw new Error(`下载失败（${res.status}）`)
  }
  const blob = await res.blob()
  return URL.createObjectURL(blob)
  // #endif
  // eslint-disable-next-line no-unreachable
  throw new Error('当前平台既没有下载能力，也没有浏览器兜底')
}

/**
 * 「打开」：微信内置文档查看器（**菜单开关要传开** —— 右上角才有保存 / 用其他应用打开）。
 *
 * - 小程序端：平台查看器；打开时把「显示菜单」传开（Accept 2 第 3 段 grep 钉着它）
 * - H5：没有查看器 → 新标签打开那个地址（blob 能开，http 链接也能）
 *
 * @param path     本地路径 / blob 地址（`downloadToTemp` 的返回值）
 * @param fileType 文档类型（`xls` / `pdf` / `docx`…）；认不出来给 `undefined`，由平台按扩展名猜
 */
export function openFile(path: string, fileType?: string): Promise<void> {
  if (!path) {
    return Promise.reject(new Error('没有可打开的文件'))
  }
  // blob: 地址只可能来自 H5 那条兜底下载 → 浏览器直接开（小程序端拿不到 blob）
  if (String(path).toLowerCase().startsWith('blob:')) {
    openInBrowser(path)
    return Promise.resolve()
  }
  return openLocalFile(path, fileType)
}

/**
 * 小程序端：把本地文件交给微信内置文档查看器；打开时把「显示菜单」传开，
 * 右上角才有保存 / 用其他应用打开（Accept 2 第 3 段 grep 钉着它）。
 *
 * ★ 条件编译成**独立函数**（不写在方法体中间），这样被编译掉的整段是自洽的
 *   （UniApp 的 `#ifdef` 是注释级预处理，按行裁掉）。
 */
function openLocalFile(path: string, fileType?: string): Promise<void> {
  // #ifdef MP-WEIXIN || MP-ALIPAY || MP-BAIDU || MP-TOUTIAO || MP-QQ || APP-PLUS
  return new Promise((resolve, reject) => {
    const api = (uni as any).openDocument
    if (typeof api !== 'function') {
      openInBrowser(path)
      resolve()
      return
    }
    api({
      filePath: path,
      fileType,
      // 打开时把菜单传开：微信查看器右上角要有保存 / 转发入口
      showMenu: true,
      success: () => resolve(),
      fail: () => reject(new Error('打开文件失败')),
    })
  })
  // #endif
  // #ifndef MP-WEIXIN || MP-ALIPAY || MP-BAIDU || MP-TOUTIAO || MP-QQ || APP-PLUS
  // 平台没有文档查看器（H5）→ 如实退化成浏览器打开
  openInBrowser(path)
  return Promise.resolve()
  // #endif
}

/**
 * 「发送到微信」：转发文件到聊天。
 *
 * ★ **平台专属能力**：`shareFileMessage` 只有小程序端有（H5 上实测 `typeof undefined`）。
 *   H5 上给一句人话、**不当成失败**（否则本地 Mock 验收面会红得毫无意义）。
 *
 * @param path     本地文件路径（blob 地址小程序端用不了 → 这句人话也覆盖了那种情况）
 * @param fileName 转发出去的文件名（`wx.shareFileMessage` 必须显式给名，临时路径没有名字）
 */
export function shareFile(path: string, fileName: string): Promise<void> {
  return new Promise((resolve, reject) => {
    if (!path) {
      reject(new Error('没有可转发的文件'))
      return
    }
    const api = (uni as any).shareFileMessage
    if (typeof api !== 'function') {
      uni.showToast({ title: '「发送到微信」要在微信里用', icon: 'none' })
      resolve()
      return
    }
    api({
      filePath: path,
      fileName,
      success: () => resolve(),
      fail: () => reject(new Error('转发文件失败')),
    })
  })
}

/**
 * H5 / 没有平台查看器的端：如实退化。
 *
 * ★ 这里**不是**小程序的行为（小程序端 `openDocument` 一定在，走微信内置查看器）。
 *   平台专属能力（微信的查看器 / 转发到聊天）在 H5 上不可完整验，报告里如实标注。
 */
function openInBrowser(url: string) {
  // #ifdef H5
  if (url && typeof window !== 'undefined' && typeof window.open === 'function') {
    window.open(url, '_blank')
    return
  }
  // #endif
  uni.showToast({ title: '这个文件暂时打不开，请稍后再试', icon: 'none' })
}
