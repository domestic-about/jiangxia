// 「临时文件 → 打开 / 发送到微信」的**公共段**（SYS-EXPORT-001 · FLOW:F-DOC-02.step4）。
//
// ★★ 一处实现、两个调用方（ticket §0 口径 4 / Accept 2 第 4 段）：
//   ① **文档下载**（DOC-MP-002）：`components/lqg/DownloadBar.vue` → Word / PDF；
//   ② **表格导出**（本票）：`pages/ledger/index.vue` → 四张工作表 xlsx。
//   原先这段交互长在 `DownloadBar.vue` 里（`uni.downloadFile` → `openDocument` / 转发文件），
//   导出再抄一份就是**两份平台实现** —— 一处带右上角菜单、一处不带（或一处 showMenu 忘了传），
//   用户拿到的东西就悄悄不一样了。所以抽到这里，两边都调它。
//
// ★★ **两个调用方的 URL 不是一类东西，请求头也就不一样**（D7 返工单 r1-S1 改正 —— 原文
//   写「导出与文档下载都是鉴权接口（不是 OSS 签名链接）」，正是把实现带偏的那句话）：
//   · **文档下载**（`DownloadBar.vue`）：后端签发的是 **OSS 预签名直链**
//     （`http://<oss>/ruoyi/lqg/doc/…?X-Amz-…`），鉴权已经写在 query 串里 → **一个请求头都不带**。
//     多带 `Authorization` 会被 S3/MinIO 判「request has multiple authentication types」
//     → **400 InvalidRequest**（D7 r1 L2 在 H5 上实测 0/4 全失败，逐头隔离：只带 Authorization → 400）。
//   · **表格导出**（`pages/ledger/index.vue`）：地址是**后端域名上的鉴权端点**
//     （`/mp/int/export/{sheet}`，`lqg_internal` 角色面）→ **必须带** `Authorization` + `clientid`；
//     不带就是一段 401 的 JSON，当成 xlsx 打开报「文件已损坏」。
//   所以「要不要头」由**调用方显式声明**（`DownloadOptions.requireAuth`），**不是本函数的全局前提**。
//   ★ 头从哪儿来由调用方给（`authHeader()` 在 `pages/ledger/export.ts`）；本文件不 import 请求层 /
//     不 import `import.meta.env` —— 它要被 node 环境的单测直接 import（`export.spec.ts`，environment=node）。
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

/** `downloadToTemp` 的入参：要下的地址 + **由调用方声明**「这个地址要不要鉴权」 */
export interface DownloadOptions {
  /**
   * 请求头。**只在 `requireAuth: true`（后端鉴权端点）时需要有 `Authorization`**；
   * OSS 预签名直链不传 / 传空对象（多带一个头就会被对象存储判「多重认证」400）。
   *
   * ★ `wx.downloadFile` 的 header 里 `Authorization` 与 `clientid` 两个都要
   *   （缺 clientid 时后端拿不到租户上下文）。
   */
  header?: Record<string, string>
  /**
   * 这个地址是不是**后端鉴权端点**（默认 `false` = 直链 / 签名链接，一个头都不带）。
   *
   * ★ 只有 `true` 时才要求 `header.Authorization`：导出（`/mp/int/export/{sheet}`）传 `true`，
   *   文档下载（OSS 预签名直链）不传。把这条守卫强加给不鉴权的调用方，就是 400 的来源。
   */
  requireAuth?: boolean
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
 * ★ **要不要鉴权头由调用方显式声明**（`opts.requireAuth`）：
 *   · 文档下载（OSS 预签名直链）→ **什么都不传**（带了 `Authorization` 就被对象存储判 400）；
 *   · 表格导出（`/mp/int/export/{sheet}`，后端鉴权端点）→ `{ header: authHeader(), requireAuth: true }`。
 *   `requireAuth: true` 但缺 `Authorization` 时**仍然 throw** —— 这条护栏是给导出留的，别删。
 *
 * @param url  文件地址（相对 / 绝对都收）
 * @param opts 请求头 + 是不是鉴权端点；**不鉴权的调用方什么都不用传**
 * @returns 本地路径 / blob 地址；取不到时 reject（调用方给「重试」）
 */
export async function downloadToTemp(url: FileUrl, opts?: DownloadOptions): Promise<string> {
  const header = opts?.header
  // 鉴权接口：调用方声明了要鉴权却没带头就下不动（真机上拿到的是一段 401 的 JSON，当成 xlsx 打开失败）
  if (opts?.requireAuth && !header?.Authorization) {
    throw new Error('下载缺少鉴权头（调用方声明了 requireAuth，就必须带 Authorization）')
  }
  const target = absoluteUrl(url)
  const api = (uni as any).downloadFile
  if (typeof api === 'function' && !isWebRuntime()) {
    return new Promise((resolve, reject) => {
      api({
        url: target,
        header: header ?? {},
        success: (res: any) => {
          // ★ 2026-09-28 修：`wx.downloadFile` 对 **401/404 也会走 success**（照样给一个 tempFilePath，
          //   内容是错误 JSON）。原来不检查状态码 → 那段 JSON 被当成 xlsx 交给 openDocument →
          //   真机上只报「文件已损坏」，把真正的失败原因（token 失效 / 域名没配）盖掉了。
          const code = Number(res?.statusCode ?? 0)
          const path = String(res?.tempFilePath ?? '')
          if (code && code !== 200) {
            reject(new Error(`downloadFile 返回 HTTP ${code}`))
            return
          }
          if (path) {
            resolve(path)
            return
          }
          reject(new Error('downloadFile 没给临时路径'))
        },
        // ★ `errMsg` 必须带出来：真机最常见的失败是 `downloadFile:fail url not in domain list`
        //   （微信后台没把域名加进 **downloadFile 合法域名** —— 它与 request 合法域名是两张分开的白名单）。
        //   原来这里把 errMsg 吞了，导致线上线下都只看到一句「导出失败，请稍后再试」。
        fail: (res: any) => reject(new Error(`downloadFile 失败：${String(res?.errMsg ?? '（无 errMsg）')}`)),
      })
    })
  }
  // H5：uni-h5 的 downloadFile 不认相对路径 → fetch + blob（头按调用方给的原样带；不鉴权就是空）
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
