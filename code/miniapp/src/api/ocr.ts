// 拍照识别的接口层（OCR-MP-001）。
//
// 契约：`doc/api-contract.md` 第 92-97 行「OCR」一节。
//   POST /mp/ocr/recognize   multipart `file`（jpg / png，≤5MB）→ `{rawLines, fields}`   内外部都能调
//   GET  /lqg/ocr/status     → `{provider, paidEnabled}`（**工作台**那半，小程序不调）
//
// 三条纪律（ticket §0 口径复述 3 与 §3）：
//   1. **只调自己后端的 `/mp/ocr/recognize`**，不直连任何第三方识别服务 —— 云厂商的
//      域名与密钥一旦进小程序包就是被人抓走的结局（accept 第 2 条最后一段在源码与
//      产物里 grep 这些词，命中即红）；
//   2. **图片不落库不存图**：这里只上传到后端，前端不留副本（`OcrBar` 用完即弃）；
//   3. 走 `uni.uploadFile` —— `uni.request` 不能传文件。**根地址与鉴权头必须与
//      `utils/request.ts` 同源**（`resolveBaseUrl()` + `clientid` + `Bearer`），
//      否则 H5 代理开着时上传会绕过代理直连后端端口（跨域 + 401 双杀）。
import { resolveBaseUrl } from '@/utils/request'
import { getToken } from '@/utils/auth'

/** 识别接口的 multipart 字段名（契约逐字：`file`） */
export const OCR_FILE_FIELD = 'file'

/** 识别接口路径 */
export const OCR_RECOGNIZE_URL = '/mp/ocr/recognize'

/** 响应体里的 `data`（`OcrRecognizeVo`） */
export interface OcrRecognizeData {
  /** 识别服务返回的原始文本行（给填写人对照） */
  rawLines?: string[] | null
  /** 解析出的字段；解析不出的键**不出现** */
  fields?: Record<string, string> | null
}

/** 上传进度回调参数（`uni.uploadFile` 的 `onProgressUpdate`） */
export interface UploadProgress {
  progress: number
}

/** `uploadOcrImage` 的可选参数 */
export interface RecognizeOptions {
  /**
   * 测试桩用例（`X-Ocr-Stub-Case`，只被 dev / test 的 `StubOcrProvider` 读）。
   * 生产上这个头没有消费者 —— 带上也无害（后端 `required = false`）。
   */
  stubCase?: string
  /** 进度回调（0-100），用来画识别中的细进度条 */
  onProgress?: (progress: UploadProgress) => void
}

/** 上传任务的最小形状（要能 `abort`：用户重选 / 连点两下时不留悬挂请求） */
export interface OcrUploadTask {
  abort: () => void
}

/** `startOcrUpload` 的返回：任务句柄 + 完成 Promise（**任务要先拿到手**，Promise 是后到的） */
export interface OcrUpload {
  task: OcrUploadTask
  done: Promise<OcrRecognizeData>
}

/**
 * 上传一张图去做识别。
 *
 * @param filePath 图片临时路径（`pickImage` 给的；小程序是 `wxfile://`，H5 是 blob URL）
 * @param options  测试桩用例与进度回调
 * @returns        `{ task, done }`：`task.abort()` 能掐断这次上传，`done` 是接口给的那个
 *                 `data`（**不在这里判业务码**：失败由调用方统一转成「没识别出来，请手动填写」）
 */
export function startOcrUpload(filePath: string, options: RecognizeOptions = {}): OcrUpload {
  const header: Record<string, string> = {
    // 与 `utils/request.ts` 同源：缺 clientid 后端判 token 无效
    clientid: (import.meta.env.VITE_APP_CLIENT_ID as string) || '',
  }
  const token = getToken()
  if (token) {
    header.Authorization = `Bearer ${token}`
  }
  if (options.stubCase) {
    header['X-Ocr-Stub-Case'] = options.stubCase
  }
  const formData: Record<string, string> = {}
  if (options.stubCase) {
    formData.stubCase = options.stubCase
  }

  let task: OcrUploadTask = { abort: () => {} }
  const done = new Promise<OcrRecognizeData>((resolve, reject) => {
    task = uni.uploadFile({
      url: `${resolveBaseUrl()}${OCR_RECOGNIZE_URL}`,
      filePath,
      name: OCR_FILE_FIELD,
      header,
      formData,
      success: (res) => {
        const body = parseBody(res.data)
        if (!body) {
          reject(new Error('识别结果看不懂'))
          return
        }
        if (body.code !== 200) {
          reject(new Error(body.msg || '识别失败'))
          return
        }
        resolve((body.data || {}) as OcrRecognizeData)
      },
      fail: (err) => {
        reject(new Error(err?.errMsg || '网络连接失败，请检查网络'))
      },
    }) as unknown as OcrUploadTask

    if (options.onProgress && task && typeof (task as unknown as { onProgressUpdate?: unknown }).onProgressUpdate === 'function') {
      ;(task as unknown as { onProgressUpdate: (cb: (p: UploadProgress) => void) => void })
        .onProgressUpdate(options.onProgress)
    }
  })
  return { task, done }
}

interface UploadBody {
  code: number
  msg?: string
  data?: OcrRecognizeData
}

/** `uploadFile` 的 `data` 可能是字符串（小程序）也可能是对象（H5）—— 两边都收 */
function parseBody(raw: unknown): UploadBody | null {
  if (raw && typeof raw === 'object') {
    return raw as UploadBody
  }
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw)
      return parsed && typeof parsed === 'object' ? parsed as UploadBody : null
    }
    catch {
      return null
    }
  }
  return null
}

/**
 * 这个小程序**不直连**任何第三方识别服务：识别统一走后端，密钥只在后端。
 * 本注释本身就是实现纪律的留痕 —— 谁想在端上加一个云厂商域名，先看这里。
 */
