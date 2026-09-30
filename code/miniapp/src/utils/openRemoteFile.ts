// 打开一个远端文件（附件 / 活率报告）：从 components/lqg/AttachmentList.vue 抽出来，
// 预览页的附件列表与编辑页的附件列表共用同一套口径。
//
// 点开 → `uni.downloadFile` 到临时目录 → **图片**走 `uni.previewImage`、
// **文档**（pdf / doc / xls / ppt）走 `uni.openDocument({showMenu: true})`
// —— 微信内置查看器，右上角菜单可以保存 / 用其他应用打开。
// ★ url 是 10 分钟签名链接：每次点开都重新下载，不缓存路径。
// ★ H5 没有 downloadFile / openDocument：如实退化成浏览器直接打开签名链接。
import { isImageFile, openDocumentType } from '@/pages/doc/download'

function downloadToTemp(url: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const api = (uni as any).downloadFile
    if (typeof api !== 'function') {
      resolve('')
      return
    }
    api({
      url,
      success: (res: any) => resolve(String(res?.tempFilePath ?? '')),
      fail: () => reject(new Error('downloadFile 失败')),
    })
  })
}

function openDoc(filePath: string, fileName: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const api = (uni as any).openDocument
    if (typeof api !== 'function') {
      resolve()
      return
    }
    api({
      filePath,
      fileType: openDocumentType(fileName),
      // 右上角菜单（保存 / 用其他应用打开）——与 DownloadBar 的「打开」同一个口径
      showMenu: true,
      success: () => resolve(),
      fail: () => reject(new Error('openDocument 失败')),
    })
  })
}

/** H5 / 没有这两个平台能力的端：如实退化成浏览器打开，打不开就提示 */
function openInBrowser(url: string) {
  // #ifdef H5
  window.open(url, '_blank')
  // #endif
  // #ifndef H5
  uni.showToast({ title: '这个附件暂时打不开，请稍后再试', icon: 'none' })
  // #endif
}

/** 打开一个远端文件；失败时只给一句人话（微信的错误码对用户没有意义） */
export async function openRemoteFile(url: string, fileName: string): Promise<void> {
  if (!url) {
    return
  }
  if (isImageFile(fileName)) {
    uni.previewImage({ urls: [url], current: 0 })
    return
  }
  try {
    const filePath = await downloadToTemp(url)
    if (!filePath) {
      openInBrowser(url)
      return
    }
    await openDoc(filePath, fileName)
  }
  catch {
    uni.showToast({ title: '这个附件暂时打不开，请稍后再试', icon: 'none' })
  }
}
