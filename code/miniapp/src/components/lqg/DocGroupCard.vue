<script setup lang="ts">
import type { DocGroup } from '@/pages/doc/group'
import { docKindLabel } from '@/pages/doc/group'
import { goPage } from '@/router/config'

// 一张「按样本分组」的文档卡（DOC-MP-001 · UI:mp.doc.list 方案 A）。
//
// 结构与排布（图廊帧 mp-doc-a 只取内容块与排布，外观走方向 A 落地规范 §5.1 / §5.5）：
//   卡头：组标题（内部编号 / 送检单号）+ 组副标题（来源单位 / 供体姓名掩码）
//   每份一行：文档名 + 完成时间 + 右侧「下载」小按钮（点这一行别处进预览）
//   组底：≥2 份时两个按钮「合并预览」「合并下载」
//
// ★ 本张（DOC-MP-001）按钮**只上形态**，点了提示「即将开放」：真正打开 / 发送到微信
//   与合并件的渲染触发在 DOC-MP-002（票面 §3 边界）。甲方 2026-09-17 要求列表上直接能下
//   （CR-20260917-04）——按钮在列表上，弹层后接。
// ★ 组标题**只用接口给的 title / subtitle**：前端不拼内部编号（accept 2 的禁字 grep）。
// ★ 只用 `.lqg-*` 范式类与 token，零色值字面量（落地规范 §8 红线）。
const props = defineProps<{
  group: DocGroup
}>()

/** 预览页占位（DOC-MP-002 换真页） */
function openPreview(docKind?: string | null) {
  const kind = docKind ? `&docKind=${docKind}` : ''
  goPage(`/pages/doc/preview?sampleId=${props.group.sampleId}${kind}`)
}

/** 下载 / 合并下载 / 合并预览：本张先提示「即将开放」（DOC-MP-002 接弹层与打开） */
function notYet() {
  uni.showToast({ title: '即将开放', icon: 'none' })
}
</script>

<template>
  <view class="lqg-card gcd">
    <view class="gcd__head">
      <text class="gcd__title">{{ group.title }}</text>
      <text v-if="group.subtitle" class="gcd__sub">{{ group.subtitle }}</text>
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
      <text class="gcd__dl" @click.stop="notYet">下载</text>
    </view>

    <!-- 组底：该样本已完成 ≥ 2 份才出（UI:mp.doc.list） -->
    <view v-if="group.showMerge" class="gcd__merge">
      <text class="gcd__mbtn gcd__mbtn--s" @click="notYet">合并预览</text>
      <text class="gcd__mbtn gcd__mbtn--p" @click="notYet">合并下载</text>
    </view>
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
  height: var(--lqg-btn-h-sm);
  line-height: var(--lqg-btn-h-sm);
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
