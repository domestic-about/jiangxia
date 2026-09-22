<script setup lang="ts">
// 底部下载操作栏（UI:mp.doc.preview 底部固定栏 / FLOW:F-DOC-02.step3+step4）· DOC-MP-002。
//
// 一份实现、**两处入口都用它**（CR-20260917-04）：
//   ① 预览页底部（`preview.vue` 把它放进 `.lqg-bar`）；
//   ② 文档列表上每份的「下载」/ 组底「合并下载」（`DocGroupCard` → `DownloadSheet` 弹层里包着它）。
//   ★ accept 1 的 counterfeit 点名的就是「列表另写一套 downloadFile + openDocument」——
//     两处入口一套实现，所以「一处带 showMenu、一处不带」这种漂移不可能发生。
//
// 三件事：
//   1. 格式切换 **PDF / Word**（`format=pdf|docx`，默认 Word，与后端默认一致）；
//   2. 「打开」→ 下到临时目录 → 微信内置查看器（`utils/fileHandoff.ts` 的公共段）；
//   3. 「发送到微信」→ 转发文件到聊天（同一段；`filePath` + 文件名）。
//   ★ 小程序**没有**「存到手机文件夹」的接口（票面 §0 平台限制）：所以这儿只有「打开」与
//     「发送到微信」两个动作，下面那行小字就是告诉甲方这件事。
//
// ★★ **「临时文件 → 打开 / 发送到微信」是公共段**（SYS-EXPORT-001 抽到 `src/utils/fileHandoff.ts`）：
//    表格导出（`pages/ledger/index.vue`）与本组件调的是同一份平台实现。本组件**只负责**
//    「合并件生成中 / 取下载链接 / 文件名 / 失败只给一句人话」，平台调用一律走公共段
//    （所以这儿不再自己写下载 / 打开 / 转发）。
//
// ★★ **合并件「份数够但还没渲染好」**：`docKind=merged` 且清单里还没有 `merged` 行时，
//    不直接把 404 当失败 —— 进「文档生成中」态、轮询到渲染好（最多 60s，见
//    `download.ts#waitForMerged`），超时给「重试」而不是死等（issue #259 / DOC-MP-001 §7.4）。
// ★ 失败态**只说一句人话**（不显示后端的 msg / 错误码）——accept 1 第 5 段。
// ★ 视觉按方向 A §5.8：主操作实底 + `--lqg-shadow-brand`，次操作描边/soft；零色值字面量。
import type { DocFormat } from '@/pages/doc/download'
import { fetchDocDownload, fetchSampleDocs } from '@/api/doc'
import { authHeader } from '@/pages/ledger/export'
import { downloadFileName, hasMergedRow, normalizeFormat, openDocumentType, waitForMerged } from '@/pages/doc/download'
// ★★ 「临时文件 → 打开 / 发送到微信」这一段是**公共段**（SYS-EXPORT-001 抽到
//   `utils/fileHandoff.ts`）：文档下载（本组件）与表格导出（`pages/ledger/index.vue`）
//   共用同一份平台实现 —— 两份就会漂（一处带菜单、一处不带，或一处漏带鉴权头）。
import { downloadToTemp, openFile, shareFile } from '@/utils/fileHandoff'
import { computed, ref } from 'vue'

const props = withDefaults(defineProps<{
  sampleId: string | number
  /** sample_qc / organoid_qc / organoid_score / merged */
  docKind: string
  identity: 'internal' | 'external' | null
  /** 编号（内部编号 / 送检单号）：转发文件时的兜底文件名要用 */
  no?: string
  /** 弹层里空间紧：底部那行小字收短 */
  compact?: boolean
}>(), {
  no: '',
  compact: false,
})

const emit = defineEmits<{
  /** 合并件从「生成中」变成可用时通知父组件（预览页据此重新取 pages） */
  (e: 'ready'): void
  /** 合并件还在异步渲染（清单里还没有 merged 行）——父组件可据此把主体切到「生成中」那一屏 */
  (e: 'generating'): void
}>()

type Phase = 'idle' | 'checking' | 'downloading' | 'generating' | 'failed'

const format = ref<DocFormat>('docx')
const phase = ref<Phase>('idle')

const busy = computed(() => phase.value === 'checking' || phase.value === 'downloading')
const isMerged = computed(() => props.docKind === 'merged')
const generating = computed(() => phase.value === 'generating')
const failed = computed(() => phase.value === 'failed')

/** 状态行：生成中 / 正在取链接 / 下载中 / 失败一句话（**没有**内部错误） */
const hint = computed(() => {
  switch (phase.value) {
    case 'checking':
      return '正在准备下载…'
    case 'downloading':
      return '正在下载…'
    case 'generating':
      return '文档生成中，稍等片刻…'
    case 'failed':
      return '暂时下不了，请稍后再试'
    default:
      return ''
  }
})

function pick(value: DocFormat) {
  if (!busy.value) {
    format.value = value
  }
}

/**
 * 合并件尤其要过这一道：清单里没有 `merged` 行 = 还在异步渲染。
 * 返回 false 表示等超时了（调用方给「重试」）。
 */
async function ensureDownloadable(): Promise<boolean> {
  if (!isMerged.value) {
    return true
  }
  phase.value = 'checking'
  const first = await fetchSampleDocs(props.sampleId, props.identity)
  if (hasMergedRow(first)) {
    return true
  }
  phase.value = 'generating'
  emit('generating') // 让父组件把页面主体也切到「生成中」那一屏（预览页用）
  const ok = await waitForMerged(() => fetchSampleDocs(props.sampleId, props.identity).then(rows => hasMergedRow(rows)))
  if (ok) {
    emit('ready')
  }
  return ok
}

/**
 * H5（本地 Mock 验收面）的兜底：浏览器打开。
 *
 * ★ 「临时文件 → 打开 / 发送到微信」的**平台实现已经在 `utils/fileHandoff.ts`**
 *   （SYS-EXPORT-001 抽的公共段，导出与文档下载共用）；这里只剩「一步都下不下来」时
 *   的兜底 —— 直接开那个签名链接（PDF 能看）。
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

async function run(mode: 'open' | 'share') {
  if (busy.value || generating.value) {
    return
  }
  phase.value = 'checking'
  try {
    if (!(await ensureDownloadable())) {
      phase.value = 'generating'
      return
    }
    phase.value = 'downloading'
    const info = await fetchDocDownload(props.sampleId, props.docKind, format.value, props.identity)
    const url = String(info?.url ?? '')
    if (!url) {
      throw new Error('下载接口没给 url')
    }
    // 文件名优先用服务端给的（同一个规则）；没有再按 ticket §0 口径 5 在本地拼
    const fileName = String(info?.fileName ?? '').trim() || downloadFileName(props.docKind, props.no, format.value)

    // ★ 公共段：带鉴权头下到临时目录（`authHeader()` 给 Authorization + clientid）
    const filePath = await downloadToTemp(url, authHeader())
    if (!filePath) {
      // 平台没有下载能力：退化成浏览器打开 / 一句提示
      if (mode === 'open') {
        openInBrowser(url)
      }
      else {
        uni.showToast({ title: '「发送到微信」要在微信里用', icon: 'none' })
      }
      phase.value = 'idle'
      return
    }
    if (mode === 'open') {
      await openFile(filePath, openDocumentType(fileName))
    }
    else {
      await shareFile(filePath, fileName)
    }
    phase.value = 'idle'
  }
  catch {
    // ★ 只给一句人话：后端的 msg（可能带渲染实现细节）不往用户面前放
    phase.value = 'failed'
  }
}

/** 生成中 / 失败之后的重试入口 */
function retry() {
  if (phase.value === 'generating' || phase.value === 'failed') {
    phase.value = 'idle'
    run('open')
  }
}

defineExpose({ retry })
</script>

<template>
  <view class="db">
    <view class="db__row">
      <!-- 格式切换：PDF / Word（默认 Word，与后端 format 默认值一致） -->
      <view class="lqg-seg db__fmt">
        <text
          class="lqg-seg__item db__fmt-item"
          :class="{ 'lqg-seg__item--on': format === 'docx' }"
          @click="pick('docx')"
        >Word</text>
        <text
          class="lqg-seg__item db__fmt-item"
          :class="{ 'lqg-seg__item--on': format === 'pdf' }"
          @click="pick('pdf')"
        >PDF</text>
      </view>

      <view class="db__acts">
        <button class="db__btn db__btn--s" :disabled="busy" @click="run('open')">
          打开
        </button>
        <button class="db__btn db__btn--p" :disabled="busy" @click="run('share')">
          发送到微信
        </button>
      </view>
    </view>

    <view v-if="hint || generating || failed" class="db__state">
      <text class="db__hint">{{ hint }}</text>
      <text v-if="generating || failed" class="db__retry" @click="retry">重试</text>
    </view>

    <!-- 平台限制如实写出来（票面 §0：小程序没有「存到手机文件夹」的接口） -->
    <text v-if="!compact" class="db__note">
      小程序不能直接存进手机文件夹：「打开」后可用右上角菜单保存或用其他应用打开，也可以「发送到微信」。
    </text>
  </view>
</template>

<style lang="scss" scoped>
.db {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-3);
}

.db__row {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-4);
}

.db__fmt {
  flex: none;
}

.db__fmt-item {
  height: 34px;
  min-width: 52px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.db__acts {
  flex: 1;
  display: flex;
  gap: var(--lqg-sp-4);
}

.db__btn {
  flex: 1;
  height: var(--lqg-btn-h);
  line-height: var(--lqg-btn-h);
  padding: 0 var(--lqg-sp-3);
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  border: none;
  border-radius: var(--lqg-radius-ctl);
}

.db__btn::after {
  border: none;
}

/* 次操作：soft 底（不加辉光） */
.db__btn--s {
  color: var(--lqg-primary);
  background: var(--lqg-primary-soft);
}

/* 主操作：实底 + 辉光（落地规范 §5.8） */
.db__btn--p {
  color: var(--lqg-on-primary);
  background: var(--lqg-primary);
  box-shadow: var(--lqg-shadow-brand);
}

.db__btn[disabled] {
  opacity: 0.6;
}

.db__state {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lqg-sp-4);
  padding: var(--lqg-sp-2) var(--lqg-sp-3) 0;
}

.db__hint {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.db__retry {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-primary);
}

.db__note {
  padding: 0 var(--lqg-sp-3);
  font-size: var(--lqg-fs-xs);
  line-height: 1.5;
  color: var(--lqg-ink-3);
}
</style>
