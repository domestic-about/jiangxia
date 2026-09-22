<script setup lang="ts">
// 附件列表（UI:mp.doc.preview 下部 / FLOW:F-DOC-02.step2）· DOC-MP-002。
//
// 点开 → `uni.downloadFile` 到临时目录 → **图片**走 `uni.previewImage`、
// **文档**（pdf / doc / xls / ppt）走 `uni.openDocument({showMenu: true})`
// —— 微信内置查看器，右上角菜单可以保存 / 用其他应用打开
// （小程序**没有**「存到手机文件夹」的接口，票面 §0 平台限制那一条要写进确认单）。
//
// ★ 附件 url 与页面图一样是 10 分钟签名链接：每次点开都重新下载，不缓存路径。
// ★ 平台差异**如实处理**：`uni.downloadFile` / `uni.openDocument` 在小程序端才有；
//   H5（本地 Mock 验收面）退化成浏览器直接打开该签名链接，打不开就一句人话的提示。
// ★ 外部身份拿不到 attachments（`ExtDocPagesVo` 只给页面图）→ 传空数组时整段不渲染。
// ★ 视觉按方向 A：`.lqg-card--flush` 里一行一个附件，零色值字面量。
import type { DocAttachmentRow } from '@/api/doc'
import { fileSizeText, isImageFile, openDocumentType } from '@/pages/doc/download'
import { computed, ref } from 'vue'

const props = defineProps<{
  attachments: DocAttachmentRow[]
}>()

/**
 * ★ 后端给不出签名链接的附件**跳过**（`url` 为 null）——与 `ThumbStrip` 同一个口径
 * （issue #217 裁定①：取不到的跳过 + WARN，不挂一排点了没反应的死行；
 * seed 的附件就是假地址，`DocArtifactStore#signedUrl` 对它返回 null）。
 */
const usable = computed(() => props.attachments.filter(item => !!String(item?.url ?? '')))

/** 正在下载的那一个（按 url 认；同一时刻只允许一个） */
const busyUrl = ref('')

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

async function open(item: DocAttachmentRow) {
  const url = String(item?.url ?? '')
  const fileName = String(item?.fileName ?? '')
  if (!url || busyUrl.value) {
    return
  }
  // 图片附件：直接全屏看图（不下载）
  if (isImageFile(fileName)) {
    uni.previewImage({ urls: [url], current: 0 })
    return
  }
  busyUrl.value = url
  try {
    const filePath = await downloadToTemp(url)
    if (!filePath) {
      openInBrowser(url)
      return
    }
    await openDoc(filePath, fileName)
  }
  catch {
    // 不把平台错误的原文显示出来（微信的错误码对用户没有意义）
    uni.showToast({ title: '这个附件暂时打不开，请稍后再试', icon: 'none' })
  }
  finally {
    busyUrl.value = ''
  }
}
</script>

<template>
  <view v-if="usable.length > 0" class="att">
    <view class="lqg-gl">附件</view>
    <view class="lqg-card lqg-card--flush">
      <view
        v-for="(item, index) in usable"
        :key="`${item.fileName}-${index}`"
        class="att__row"
        @click="open(item)"
      >
        <view class="att__main">
          <text class="att__name">{{ item.fileName || '附件' }}</text>
          <text v-if="fileSizeText(item.fileSize)" class="att__size lqg-num">{{ fileSizeText(item.fileSize) }}</text>
        </view>
        <text class="att__act">{{ busyUrl === item.url ? '打开中' : '打开' }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.att {
  margin-top: var(--lqg-gap);
}

.att__row {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-4);
  min-height: var(--lqg-cell-h);
  padding: 0 var(--lqg-sp-6);
  border-top: 1px solid var(--lqg-line);
}

.att__row:first-child {
  border-top: none;
}

.att__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.att__name {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink);
  word-break: break-all;
}

.att__size {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.att__act {
  flex: none;
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-primary);
}
</style>
