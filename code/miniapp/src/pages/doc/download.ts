// 文档预览 / 下载的**纯函数层**（DOC-MP-002 · FLOW:F-DOC-02.step3/step4 · UI:mp.doc.preview）。
//
// ★ 本文件**不 import uni / 不 import 网络层**：`download.fixture.spec.ts` 在 node 环境里
//   直接 import 它跑单测（SYS-MP-001 的 vitest 配置 environment=node）。取数与平台 API
//   在 `api/doc.ts`（取数）与各组件（`uni.downloadFile` / `openDocument` / `previewImage`）里。
// ★ 只 import `group.ts` 的中文名（那里是文档名的唯一一处字典）——它是纯函数模块。
import { docKindLabel } from '@/pages/doc/group'

/** 下载格式（契约第 85/86 行：`format=docx|pdf`） */
export type DocFormat = 'docx' | 'pdf'

/**
 * 合并件的文件名主干。
 *
 * ★ 逐字来自 ticket §0 口径 5：「合并文件叫『质控文档（合并）』」。
 * ★ 后端 `DocKinds.label(MERGED)` 是同一条规则（`DocRenderModelFactory#displayName`
 *   拼出的 `fileName` 就是这个主干 + 编号）——两边**必须一字不差**，否则
 *   「下载下来的」与「发送到微信看到的」会是两个名字。改一处就要改另一处。
 */
export const MERGED_FILE_BASE = '质控文档（合并）'

/** 认不出来的格式一律按 docx（与后端 `format` 默认值同一个口径，不静默给错格式是因为后端会 400） */
export function normalizeFormat(format?: string | null): DocFormat {
  return String(format ?? '').trim().toLowerCase() === 'pdf' ? 'pdf' : 'docx'
}

/** 扩展名（带点） */
export function formatExt(format?: string | null): string {
  return normalizeFormat(format) === 'pdf' ? '.pdf' : '.docx'
}

/** 文档名主干：合并件固定名，单份用 `docKind` 的中文名 */
export function docFileBase(docKind?: string | null): string {
  const kind = String(docKind ?? '').trim()
  if (kind === 'merged') {
    return MERGED_FILE_BASE
  }
  return docKindLabel(kind) || kind || '质控文档'
}

/**
 * 文件名 = **文档名 + 编号**（ticket §0 口径 5）。
 *
 * - 文档名：单份 = `sample_qc` → 「样本质控表」等；合并 = 「质控文档（合并）」
 * - 编号：内部身份给**内部编号**、外部身份给**送检单号**；空则不拼那一段
 *   （与后端 `DocRenderModelFactory#displayName` 的 `submitNo/internalNo` 回落一致）
 * - 扩展名：`.pdf` / `.docx`
 *
 * ★ 这是**客户端兜底用**的名字：`wx.shareFileMessage` 必须显式给 `fileName`，
 *   而 `wx.downloadFile` 只给临时路径（没有可用的名字）。服务端下载接口也会返回
 *   `fileName`（同一个规则），组件优先用服务端那个，没有再回落到这里。
 */
export function downloadFileName(docKind?: string | null, no?: string | null, format?: string | null): string {
  const suffix = String(no ?? '').trim()
  const base = docFileBase(docKind)
  return `${suffix ? `${base}-${suffix}` : base}${formatExt(format)}`
}

/** 小程序 `wx.openDocument` 认的 fileType（认不出来给 undefined → 由微信按扩展名猜） */
export function openDocumentType(fileName?: string | null): 'docx' | 'xls' | 'ppt' | 'pdf' | undefined {
  const ext = extOf(fileName)
  switch (ext) {
    case 'doc':
    case 'docx':
      return 'docx'
    case 'xls':
    case 'xlsx':
      return 'xls'
    case 'ppt':
    case 'pptx':
      return 'ppt'
    case 'pdf':
      return 'pdf'
    default:
      return undefined
  }
}

/** 图片附件走 `wx.previewImage`（文档附件走 `wx.openDocument`） */
export function isImageFile(fileName?: string | null): boolean {
  return IMAGE_EXTS.includes(extOf(fileName))
}

/** 路径（可能带 query）里的扩展名，小写；没有扩展名给空串 */
export function extOf(path?: string | null): string {
  const raw = String(path ?? '').trim()
  if (!raw) {
    return ''
  }
  const noQuery = raw.split(/[?#]/)[0]
  const last = noQuery.slice(noQuery.lastIndexOf('/') + 1)
  const dot = last.lastIndexOf('.')
  return dot >= 0 ? last.slice(dot + 1).toLowerCase() : ''
}

const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp']

/**
 * ★ **小程序打不开的原图格式**（ticket §2：原图是 TIFF 等小程序打不开的格式时
 * 退回预览图并提示）。
 *
 * 显微镜原图常见 TIFF / DICOM / 病理全片扫描格式，`wx.previewImage` 只吃常见位图。
 */
const UNOPENABLE_IMAGE_EXTS = ['tif', 'tiff', 'dcm', 'dicom', 'svs', 'ndpi', 'vms', 'mrxs', 'scn', 'czi', 'lif']

/** 一张图片位（`DocPagesVo.images` 的一项：`url` 原图 / `previewUrl` 缩略图） */
export interface DocImageItem {
  url?: string | null
  previewUrl?: string | null
}

/** 缩略图用 `previewUrl`（后端在没做缩略图时会把 `previewUrl` 回落成原图） */
export function thumbUrlOf(image?: DocImageItem | null): string {
  return String(image?.previewUrl ?? image?.url ?? '')
}

/** 一个附件（`DocPagesVo.attachments` 的一项） */
export interface DocAttachmentItem {
  fileName?: string | null
  fileSize?: number | null
  url?: string | null
}

/** 附件字节数 → 人话（1.2 MB）；没有大小给空串，不编一个 0 */
export function fileSizeText(size?: number | null): string {
  const bytes = Number(size)
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return ''
  }
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/**
 * 「看原图」到底开哪个地址（ticket §0 口径 1 的第二层放大）：
 * 默认开 `url`（**原图**，显微照片的细节靠这个）；原图扩展名属于小程序打不开的那几种
 * （TIFF 等）时退回 `previewUrl` 并把 `fallback` 置真（调用方据此提示一句）。
 * 原图为空也退回（不能开一个空地址）。
 */
export function imageOpenUrl(image?: DocImageItem | null): { url: string, fallback: boolean } {
  const original = String(image?.url ?? '')
  const preview = thumbUrlOf(image)
  const ext = extOf(original)
  if (!original || UNOPENABLE_IMAGE_EXTS.includes(ext)) {
    return { url: preview, fallback: !!preview && preview !== original }
  }
  return { url: original, fallback: false }
}

/** 预览页的三段数据的**状态**（UI:mp.doc.preview：渲染中显示「文档生成中」，失败一句人话） */
export type DocLoadState = 'loading' | 'ready' | 'generating' | 'failed'

/**
 * 一句话状态文案：**绝不出现内部错误**（ticket §2 / accept 1 的禁字 grep）。
 * `generating` 是「份数够但合并件还没渲染好」那条路（异步渲染，见 DOC-MP-001 §7.4）。
 */
export function stateText(state: DocLoadState): string {
  switch (state) {
    case 'loading':
      return '正在加载'
    case 'generating':
      return '文档生成中'
    case 'failed':
      return '文档暂时无法预览，请稍后再试'
    default:
      return ''
  }
}

/** 合并件轮询的节奏（ticket §2：生成中轮询，最多 60 秒） */
export const MERGED_POLL_INTERVAL_MS = 3000
export const MERGED_POLL_TIMEOUT_MS = 60000

/**
 * 清单行里有没有 **已渲染成功** 的合并件（`docKind=merged`）。
 *
 * ★ 这是小程序侧判「合并件到底在不在」的唯一依据：清单接口只给已经算作可用的行
 * （判据在服务端 `DocAvailabilityService`），所以「≥2 份但清单里没有 merged 行」
 * 就是「份数够、点了合并、但那份还没渲染好」这个态。
 * ★ 前端**不**自己判「能不能给出去」，也不调渲染接口（那是工作台的路）。
 */
export function hasMergedRow(rows?: Array<{ docKind?: string | null }> | null): boolean {
  return (rows ?? []).some(row => String(row?.docKind ?? '') === 'merged')
}

/** 等一会儿（`waitForMerged` 用；单独抽出来是为了让单测能把它换掉） */
export function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/**
 * ★★ **「份数够、点了合并、但合并件还没渲染好」这个态的处理**（DOC-MP-001 §7.4 点名的风险，
 * issue #259）。
 *
 * 合并件是**异步渲染**的：某一份完成 / 撤回会触发 `renderService.render(..., MERGED)`，
 * 而在它跑完之前，`pages|download?docKind=merged` 会与「没这份文档」同样地 404。
 * 小程序侧能拿到的、唯一的「合并件到底在不在」的正向信号是**清单里有没有 `merged` 行**
 * （清单只列已经可用的行，判据在服务端 `DocAvailabilityService`）——
 * 所以这里轮询 `probe()`（调用方给的是「清单里有没有 merged 行」），
 * 最多 `timeoutMs`；返回 `false` = 超时了，调用方要给「可重试」的提示而不是死等。
 *
 * ★ 前端**不**自己判可见性、**不**调渲染接口（那是工作台的路，`lqg:doc:render`）。
 * ★ 轮询间隔 3s、总时长 60s（ticket §2：「生成中（轮询 pages，最多 60 秒）」）。
 */
export async function waitForMerged(
  probe: () => Promise<boolean>,
  options: { intervalMs?: number, timeoutMs?: number } = {},
): Promise<boolean> {
  const intervalMs = options.intervalMs ?? MERGED_POLL_INTERVAL_MS
  const timeoutMs = options.timeoutMs ?? MERGED_POLL_TIMEOUT_MS
  const deadline = Date.now() + timeoutMs
  // 先立刻探一次（进页时可能刚好渲染完了，不用先干等 3 秒）
  for (;;) {
    const ok = await probe()
    if (ok) {
      return true
    }
    if (Date.now() + intervalMs > deadline) {
      return false
    }
    await sleep(intervalMs)
  }
}
