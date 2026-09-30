<script setup lang="ts">
// 附件列表（UI:mp.doc.preview 下部 / FLOW:F-DOC-02.step2）· DOC-MP-002。
//
// 点开的逻辑在 utils/openRemoteFile.ts（编辑质控文档页的附件列表共用）：图片全屏看、文档走微信内置查看器
// （小程序**没有**「存到手机文件夹」的接口，票面 §0 平台限制那一条要写进确认单）。
//
// ★ 附件 url 与页面图一样是 10 分钟签名链接：每次点开都重新下载，不缓存路径。
// ★ 平台差异**如实处理**：`uni.downloadFile` / `uni.openDocument` 在小程序端才有；
//   H5（本地 Mock 验收面）退化成浏览器直接打开该签名链接，打不开就一句人话的提示。
// ★ 内外部身份都拿得到 attachments（含细胞活率测定附件；外部那条在独立验收 V24 补齐）→ 传空数组时整段不渲染。
// ★ 视觉按方向 A：`.lqg-card--flush` 里一行一个附件，零色值字面量。
import type { DocAttachmentRow } from '@/api/doc'
import { fileSizeText } from '@/pages/doc/download'
import { openRemoteFile } from '@/utils/openRemoteFile'
import { computed, ref } from 'vue'

const props = defineProps<{
  attachments: DocAttachmentRow[]
}>()

/**
 * ★ 后端给不出签名链接的附件**跳过**（`url` 为 null）——与 `ThumbStrip` 同一个口径：
 * 不挂一排点了没反应的死行。
 */
const usable = computed(() => props.attachments.filter(item => !!String(item?.url ?? '')))

/** 正在下载的那一个（按 url 认；同一时刻只允许一个） */
const busyUrl = ref('')

async function open(item: DocAttachmentRow) {
  const url = String(item?.url ?? '')
  if (!url || busyUrl.value) {
    return
  }
  busyUrl.value = url
  try {
    await openRemoteFile(url, String(item?.fileName ?? ''))
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
