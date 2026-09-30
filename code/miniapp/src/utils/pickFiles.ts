// 选图 / 选文件（编辑质控文档页用）。
//
// ★ 小程序里选文件只有一条路：`chooseMessageFile`（从微信聊天记录里选）—— 小程序**不能**直接读手机文件夹。
//   活率报告、附件这类文件，先发到自己的微信（文件传输助手）再在这里选。H5 用浏览器的选文件。
// ★ 选图 / 拍照在真机上要先在小程序后台声明《用户隐私保护指引》（相机 / 相册），没声明就不弹授权、
//   直接失败（errno 112）—— 与「拍照识别」同一个前提（飞书小程序 row7，Kevin：测试小程序暂时办不了）。
//   这里把这类失败翻译成一句能照着做的话，不笼统说「失败」。

export interface PickedFile {
  path: string
  name: string
  size?: number
}

/** 取消选择不算错误：返回 null，调用方什么都不做 */
function isCancel(err: unknown): boolean {
  return /cancel/i.test(String((err as { errMsg?: string })?.errMsg ?? err ?? ''))
}

/** 把平台错误翻成一句人话 */
export function pickErrorText(err: unknown): string {
  const msg = String((err as { errMsg?: string, message?: string })?.errMsg ?? (err as { message?: string })?.message ?? err ?? '')
  if (/privacy|隐私|112|scope is not declared/i.test(msg)) {
    return '需在小程序后台声明用户隐私保护指引（相机 / 相册）后才能选图'
  }
  if (/auth deny|authorize/i.test(msg)) {
    return '没有相机 / 相册权限，请在右上角「…→设置」里打开'
  }
  return '没能选到文件，请再试一次'
}

/** 选图（相册或拍照，原图；最多 count 张） */
export function pickImages(count = 9): Promise<PickedFile[] | null> {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count,
      sizeType: ['original'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const files = (res.tempFiles as Array<{ path: string, size?: number, name?: string }> | undefined) ?? []
        const paths = (res.tempFilePaths as string[] | undefined) ?? []
        const list = files.length > 0
          ? files.map((f, i) => ({ path: f.path || paths[i], size: f.size, name: f.name || fileNameOf(f.path || paths[i]) }))
          : paths.map(p => ({ path: p, name: fileNameOf(p) }))
        resolve(list)
      },
      fail: (err) => {
        if (isCancel(err)) {
          resolve(null)
          return
        }
        reject(err)
      },
    })
  })
}

/** 选一个文件（小程序：从聊天记录里选；H5：浏览器选文件） */
export function pickFile(): Promise<PickedFile | null> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.chooseMessageFile({
      count: 1,
      type: 'file',
      success: (res) => {
        const f = res.tempFiles?.[0]
        resolve(f ? { path: f.path, name: f.name, size: f.size } : null)
      },
      fail: (err) => {
        if (isCancel(err)) {
          resolve(null)
          return
        }
        reject(err)
      },
    })
    // #endif
    // #ifndef MP-WEIXIN
    uni.chooseFile({
      count: 1,
      success: (res) => {
        const f = (res.tempFiles as Array<{ path: string, name?: string, size?: number }>)?.[0]
        resolve(f ? { path: f.path, name: f.name || fileNameOf(f.path), size: f.size } : null)
      },
      fail: (err) => {
        if (isCancel(err)) {
          resolve(null)
          return
        }
        reject(err)
      },
    })
    // #endif
  })
}

function fileNameOf(path: string): string {
  const tail = String(path || '').split(/[\\/]/).pop() || ''
  return tail || 'file'
}
