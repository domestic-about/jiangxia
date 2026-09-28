// 选图 → 压缩到长边 ≤ 2000px（`FLOW:F-OCR-01.step1`：wx.chooseMedia（相机 / 相册，单张）
// → 前端压缩到长边 ≤ 2000px → 上传）。
//
// 为什么要单独一个文件：`OcrBar.vue` 只该管状态机与视觉，不该塞进一堆 canvas 细节；
// 而且这两个函数是**纯 IO 包装**，测试与截图脚本都从这一层看行为。
//
// 三平台口径：
//   - 小程序 / App：`uni.chooseMedia` 原生支持（ticket §2 的写法逐字落地），压缩用
//     `uni.compressImage`（原生实现，画质与内存都比 canvas 稳）；
//   - H5（本票端侧取证的路径）：uni-h5 **没有** `chooseMedia`，只能回落到 `uni.chooseImage`
//     （它内部就是 `input[type=file]`，`sourceType: ['camera']` 会带 `capture`）；
//     压缩用 canvas（`uni.compressImage` 在 H5 也不存在）。
//   ★ 回落不等于换口径：调用方（OcrBar）始终只说「相机 / 相册、单张」，两平台拿到的东西一样。

/** 长边上限（px）——`FLOW:F-OCR-01.step1` 明写的数 */
export const OCR_MAX_EDGE = 2000

/** 选图结果：`path` 是能直接喂 `uni.uploadFile` 的临时路径；`file` 只在 H5 下有（原始 File） */
export interface PickedImage {
  path: string
  size: number
  /** 长边 ≤ 2000 已被保证（压过或本来就不超） */
  compressed: boolean
}

/** 用户取消选图 —— 不是错误，调用方据此**不弹失败提示** */
export const PICK_CANCELLED = 'ocr-pick-cancelled'

export function isCancelled(err: unknown): boolean {
  return err === PICK_CANCELLED
}

interface ChooseMediaFile {
  tempFilePath?: string
  size?: number
  file?: File
}

/** 最小化的 uni API 形状（`uni` 的 d.ts 在 H5 平台没有 chooseMedia，这里按特性检测用） */
interface UniAny {
  chooseMedia?: (options: Record<string, unknown>) => void
  chooseImage?: (options: Record<string, unknown>) => void
  compressImage?: (options: Record<string, unknown>) => void
  getImageInfo?: (options: Record<string, unknown>) => void
}

function api(): UniAny {
  return uni as unknown as UniAny
}

/**
 * 扫码 / 拍照 / 相册取一张图（单张）。
 *
 * @param sourceType `['camera']` = 拍照识别；`['album']` = 从相册选
 */
export function pickImage(sourceType: 'camera' | 'album'): Promise<PickedImage> {
  const u = api()
  if (typeof u.chooseMedia === 'function') {
    return new Promise((resolve, reject) => {
      u.chooseMedia!({
        count: 1,
        mediaType: ['image'],
        sourceType: [sourceType],
        success: (res: { tempFiles?: ChooseMediaFile[] }) => {
          const first = res?.tempFiles?.[0]
          const path = first?.tempFilePath || ''
          if (!path) {
            reject(new Error('没能取到图片'))
            return
          }
          resolve({ path, size: Number(first?.size) || 0, compressed: false })
        },
        fail: (err: { errMsg?: string }) => reject(isCancelMsg(err?.errMsg) ? PICK_CANCELLED : new Error(err?.errMsg || '没能取到图片')),
      })
    })
  }
  // H5 回落（uni-h5 没有 chooseMedia）：uni.chooseImage 的 tempFiles 是 File 数组，
  // `path` getter 会把它变成 blob URL —— uploadFile 那条路收的就是它。
  if (typeof u.chooseImage === 'function') {
    return new Promise((resolve, reject) => {
      u.chooseImage!({
        count: 1,
        sizeType: ['original'],
        sourceType: [sourceType],
        success: (res: { tempFilePaths?: string[], tempFiles?: Array<File & { path?: string }> }) => {
          const file = res?.tempFiles?.[0]
          const path = res?.tempFilePaths?.[0] || file?.path || ''
          if (!path) {
            reject(new Error('没能取到图片'))
            return
          }
          resolve({ path, size: Number(file?.size) || 0, compressed: false })
        },
        fail: (err: { errMsg?: string }) => reject(isCancelMsg(err?.errMsg) ? PICK_CANCELLED : new Error(err?.errMsg || '没能取到图片')),
      })
    })
  }
  return Promise.reject(new Error('当前环境不支持选图'))
}

function isCancelMsg(msg?: string): boolean {
  return !!msg && msg.includes('cancel')
}

/**
 * 压到长边 ≤ 2000px。
 *
 * - 用 `uni.getImageInfo` 拿到真实像素（拿不到 → **原样返回**：宁可上传大图，
 *   也不要因为量不出尺寸就把选好的图丢掉）；
 * - 本来就不超 → 不动，不重编码（重编码一次就掉一次画质）；
 * - 超了 → 先试原生 `uni.compressImage`（`compressedWidth` / `compressedHeight` 已实测可用），
 *   再试 canvas（H5）；两条都不行 → 原样返回，**不报错、不挡填表**。
 */
export async function compressToMaxEdge(picked: PickedImage): Promise<PickedImage> {
  const size = await imageSize(picked.path).catch(() => null)
  if (!size || (size.width <= OCR_MAX_EDGE && size.height <= OCR_MAX_EDGE)) {
    return { ...picked, compressed: false }
  }
  const ratio = OCR_MAX_EDGE / Math.max(size.width, size.height)
  const width = Math.max(1, Math.round(size.width * ratio))
  const height = Math.max(1, Math.round(size.height * ratio))
  const native = await compressNative(picked.path, width, height).catch(() => '')
  if (native) {
    return { path: native, size: picked.size, compressed: true }
  }
  const canvas = await compressByCanvas(picked, width, height).catch(() => null)
  if (canvas) {
    return canvas
  }
  return { ...picked, compressed: false }
}

function imageSize(path: string): Promise<{ width: number, height: number }> {
  const u = api()
  if (typeof u.getImageInfo === 'function') {
    return new Promise((resolve, reject) => {
      u.getImageInfo!({
        src: path,
        success: (res: { width: number, height: number }) => resolve({ width: Number(res.width) || 0, height: Number(res.height) || 0 }),
        fail: () => reject(new Error('量不出图片尺寸')),
      })
    })
  }
  return loadImage(path).then(img => ({ width: img.naturalWidth || img.width, height: img.naturalHeight || img.height }))
}

function compressNative(path: string, width: number, height: number): Promise<string> {
  const u = api()
  if (typeof u.compressImage !== 'function') {
    return Promise.resolve('')
  }
  return new Promise((resolve, reject) => {
    u.compressImage!({
      src: path,
      quality: 85,
      compressedWidth: width,
      compressedHeight: height,
      success: (res: { tempFilePath?: string }) => resolve(res?.tempFilePath || ''),
      fail: () => reject(new Error('原生压缩失败')),
    })
  })
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('图片加载失败'))
    img.src = src
  })
}

/** canvas 压缩（只在 H5 下有 `document`） */
async function compressByCanvas(picked: PickedImage, width: number, height: number): Promise<PickedImage | null> {
  if (typeof document === 'undefined' || typeof document.createElement !== 'function') {
    return null
  }
  const img = await loadImage(picked.path)
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    return null
  }
  ctx.drawImage(img, 0, 0, width, height)
  const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(b => resolve(b), 'image/jpeg', 0.85))
  if (!blob) {
    return null
  }
  const name = `ocr-${Date.now()}.jpg`
  const file = typeof File === 'function' ? new File([blob], name, { type: 'image/jpeg' }) : null
  const url = file ? URL.createObjectURL(file) : canvas.toDataURL('image/jpeg', 0.85)
  return { path: url, size: blob.size, compressed: true }
}
