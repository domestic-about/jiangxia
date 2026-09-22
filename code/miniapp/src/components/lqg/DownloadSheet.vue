<script setup lang="ts">
// 列表上的下载弹层（CR-20260917-04：甲方要「列表上直接能下」）· DOC-MP-002。
//
// 结构 = `wd-popup`（落地规范 §5.10：底部弹层、上两角圆角 16、蒙层 `--lqg-mask`、
// 内容用 `.lqg-sheet`、高度不超过屏高三分之二）**包一层 `DownloadBar`**。
//
// ★★ **不在这里另写一套 downloadFile / openDocument**：本文件只做两件事 ——
//    收下「下载哪一份」、把 `DownloadBar` 放进弹层。accept 1 的 counterfeit 第 4 条
//    点名的就是「列表另写一套 → 一处带 showMenu、一处不带」，那正是迟早要出的漂移。
//   → 列表上每份的「下载」、组底「合并下载」，与预览页底部调的是**同一个**组件、同一个接口。
// ★ 身份从 `/mp/me` 的 store 归一化（唯一来源）——列表页与预览页同一个口径。
// ★ `merged` 那份「份数够但还没渲染好」由 `DownloadBar` 处理（生成中 + 轮询 + 重试）。
// ★ 视觉按方向 A：`--lqg-radius-sheet`、零色值字面量。
import DownloadBar from '@/components/lqg/DownloadBar.vue'
import { normalizeIdentity } from '@/types/identity'
import { useUserStore } from '@/store/user'
import { computed, ref } from 'vue'
// ★ 显式 import wd-popup 的 .vue（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失）
import WdPopup from 'wot-design-uni/components/wd-popup/wd-popup.vue'

export interface DownloadSheetTarget {
  sampleId: string | number
  /** sample_qc / organoid_qc / organoid_score / merged */
  docKind: string
  /** 编号（内部编号 / 送检单号）——只有兜底文件名要用 */
  no?: string
  /** 弹层标题：文档名（+「合并」时说明是合并件） */
  title?: string
}

const store = useUserStore()
const identity = computed(() => normalizeIdentity(store.identity))

const show = ref(false)
const target = ref<DownloadSheetTarget>({ sampleId: '', docKind: 'sample_qc' })

const title = computed(() => target.value.title || '下载')

/** 列表上点「下载」/「合并下载」时调：打开弹层并切到对应那一份 */
function open(next: DownloadSheetTarget) {
  target.value = { ...next }
  show.value = true
}

defineExpose({ open })
</script>

<template>
  <wd-popup
    v-model="show"
    position="bottom"
    :z-index="1000"
    safe-area-inset-bottom
    custom-style="border-radius: var(--lqg-radius-sheet) var(--lqg-radius-sheet) 0 0"
    @close="show = false"
  >
    <view class="lqg-sheet ds">
      <view class="lqg-sheet__head">
        <text class="ds__t">{{ title }}</text>
        <text class="ds__x" @click="show = false">关闭</text>
      </view>

      <DownloadBar
        :sample-id="target.sampleId"
        :doc-kind="target.docKind"
        :identity="identity"
        :no="target.no"
        compact
      />
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.ds__t {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.ds__x {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}
</style>
