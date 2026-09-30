<script setup lang="ts">
import type { DocGroup } from '@/pages/doc/group'
import { docKindLabel } from '@/pages/doc/group'
import DownloadSheet from '@/components/lqg/DownloadSheet.vue'
import { goPage } from '@/router/config'
import { MERGED_FILE_BASE } from '@/pages/doc/download'
import { ref } from 'vue'

// 一张「按样本分组」的文档卡（DOC-MP-001 · UI:mp.doc.list 方案 A）。
//
// 结构与排布（图廊帧 mp-doc-a 只取内容块与排布，外观走方向 A 落地规范 §5.1 / §5.5）：
//   卡头：组标题（内部编号 / 送检单号）+ 组副标题（来源单位 / 供体姓名掩码）
//   每份一行：文档名 + 完成时间 + 右侧「下载」小按钮（点这一行别处进预览）
//   组底：≥2 份时两个按钮「合并预览」「合并下载」
//
// ★ DOC-MP-002 把三个按钮接上了（CR-20260917-04：列表上直接能下）：
//   「下载」/「合并下载」→ `DownloadSheet` 弹层（里面就是 `DownloadBar`，与预览页**同一套**实现）；
//   「合并预览」→ 预览页 `docKind=merged`。
//   ★ 列表上**不另写**一套 downloadFile / openDocument（accept 1 的 counterfeit 第 4 条）。
// ★ 合并件「份数够但还没渲染好」由 `DownloadBar` 走「生成中 + 轮询 + 重试」那条路。
// ★ 组标题**只用接口给的 title / subtitle**：前端不拼内部编号（accept 2 的禁字 grep）。
// ★ 只用 `.lqg-*` 范式类与 token，零色值字面量（落地规范 §8 红线）。
const props = defineProps<{
  group: DocGroup
  /** 内部人员才传 true：卡片右上角出「编辑」 */
  editable?: boolean
}>()

function openEdit() {
  goPage(`/pages/qc/edit?sampleId=${props.group.sampleId}`)
}

/** 下载弹层（每张卡一个） */
const sheet = ref<{ open: (t: { sampleId: string | number, docKind: string, no?: string, title?: string }) => void } | null>(null)

/** 进预览页（顶部切换条会列出该样本已完成的几份） */
function openPreview(docKind?: string | null) {
  const kind = docKind ? `&docKind=${docKind}` : ''
  goPage(`/pages/doc/preview?sampleId=${props.group.sampleId}${kind}`)
}

/**
 * 「下载」/「合并下载」：打开弹层。
 *
 * `no` 用**接口给的组标题**（内部 = 内部编号、外部 = 送检单号）——那是后端按身份给的，
 * 前端一个字段都不拼；它只在服务端 `fileName` 缺失时当兜底文件名用。
 */
function openDownload(docKind: string) {
  sheet.value?.open({
    sampleId: props.group.sampleId,
    docKind,
    no: props.group.title,
    title: docKind === 'merged' ? MERGED_FILE_BASE : docKindLabel(docKind),
  })
}
</script>

<template>
  <view class="lqg-card gcd">
    <view class="gcd__head">
      <!-- 组标题是编号（内部编号 / 送检单号）：等宽字体（独立验收 G17） -->
      <text class="gcd__title lqg-mono">{{ group.title }}</text>
      <text v-if="group.subtitle" class="gcd__sub">{{ group.subtitle }}</text>
      <!-- 内部人员：编辑这个样本的三份质控表（2026-09-30 甲方要求小程序也能编辑） -->
      <text v-if="editable" class="gcd__edit" @click.stop="openEdit">编辑</text>
    </view>

    <view
      v-for="doc in group.docs"
      :key="String(doc.docKind)"
      class="gcd__row"
      @click="openPreview(doc.docKind)"
    >
      <view class="gcd__main">
        <text class="gcd__name">{{ docKindLabel(doc.docKind) }}</text>
        <text class="gcd__time">{{ doc.publishedTime }}</text>
      </view>
      <!-- 右侧「下载」：点它别冒泡到整行（整行是进预览） -->
      <text class="gcd__dl" @click.stop="openDownload(String(doc.docKind))">下载</text>
    </view>

    <!-- 组底：该样本已完成 ≥ 2 份才出（UI:mp.doc.list） -->
    <view v-if="group.showMerge" class="gcd__merge">
      <text class="gcd__mbtn gcd__mbtn--s" @click="openPreview('merged')">合并预览</text>
      <text class="gcd__mbtn gcd__mbtn--p" @click="openDownload('merged')">合并下载</text>
    </view>

    <!-- 下载弹层：包着 DownloadBar（与预览页底部同一个组件、同一套接口） -->
    <DownloadSheet ref="sheet" />
  </view>
</template>

<style lang="scss" scoped>
/* 组与组之间留间距（卡片自身来自 .lqg-card） */
.gcd {
  margin: 0 var(--lqg-gutter) var(--lqg-gap);
}

.gcd__head {
  display: flex;
  align-items: baseline;
  gap: var(--lqg-sp-4);
  padding-bottom: var(--lqg-sp-4);
  border-bottom: 1px solid var(--lqg-line);
}

.gcd__title {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.gcd__sub {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.gcd__edit {
  margin-left: auto;
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-primary);
}

.gcd__row {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-4);
  min-height: var(--lqg-cell-h);
  border-top: 1px solid var(--lqg-line);
}

.gcd__row:first-of-type {
  border-top: none;
}

.gcd__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.gcd__name {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink);
}

.gcd__time {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

/* 每份一行右侧的「下载」小按钮（描边，不抢主按钮） */
.gcd__dl {
  flex: none;
  padding: 4px var(--lqg-sp-6);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-primary);
  background: var(--lqg-primary-soft);
  border-radius: var(--lqg-radius-seg);
}

/* 组底两个按钮：左「合并预览」次要、右「合并下载」主按钮 */
.gcd__merge {
  display: flex;
  gap: var(--lqg-sp-4);
  padding-top: var(--lqg-sp-5);
  border-top: 1px solid var(--lqg-line);
}

.gcd__mbtn {
  flex: 1;
  height: 40px;
  line-height: 40px;
  text-align: center;
  font-size: var(--lqg-fs-body);
  font-weight: var(--lqg-fw-semibold);
  border-radius: var(--lqg-radius-ctl);
}

.gcd__mbtn--s {
  color: var(--lqg-primary);
  background: var(--lqg-primary-soft);
}

.gcd__mbtn--p {
  color: var(--lqg-on-primary);
  background: var(--lqg-primary);
  box-shadow: var(--lqg-shadow-brand);
}
</style>
