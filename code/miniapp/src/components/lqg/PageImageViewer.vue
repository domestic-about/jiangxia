<script setup lang="ts">
// 逐页页面图片（UI:mp.doc.preview 上部 / FLOW:F-DOC-02.step2）· DOC-MP-002。
//
// 点任一页 → 微信内置的全屏看图器（`uni` 的图片预览接口，调用点在下面 `open()`）：
// **双指缩放**就在那里（这是「两层放大」的第一层；第二层是 `ThumbStrip` 的看原图）。
//
// ★ 页面图是 150 DPI 的整页渲染图，放大是「看清版式」，不是「看清显微照片的细节」
//   —— 后者要点 `ThumbStrip` 里的缩略图看**原图**（ticket §0 口径 1）。
// ★ `mode="widthFix"`（宽度撑满、高度按比例）+ `lazy-load`：一页一张图，滚动时再加载。
// ★ 链接是 10 分钟签名链接，不缓存、不预取（DOC-MP-001 §7.5）。
// ★ 视觉按方向 A：页面图装在 `.lqg-card--flush` 里（§5.1），零色值字面量。
import type { DocPageItem } from '@/api/doc'

const props = defineProps<{
  pages: DocPageItem[]
}>()

/** 能开出来的页面图地址（后端偶尔会给 null 的 url，滤掉再算 index） */
function urls(): string[] {
  return props.pages.map(page => String(page?.url ?? '')).filter(Boolean)
}

/** 点第 index 页 → 全屏预览（同一份的全部页都在，可以左右翻） */
function open(index: number) {
  const list = props.pages
  const all = urls()
  if (all.length === 0) {
    return
  }
  const target = String(list[index]?.url ?? '')
  // 目标页没有 url 时 current 取 0（不至于报错，也别开一个空地址）
  const current = target && all.includes(target) ? all.indexOf(target) : 0
  uni.previewImage({ urls: all, current, indicator: 'number' })
}
</script>

<template>
  <view class="lqg-card lqg-card--flush piv">
    <view v-if="pages.length === 0" class="piv__empty">
      <text class="piv__empty-t">这一份还没有页面图</text>
    </view>
    <view
      v-for="(page, index) in pages"
      :key="String(page.pageNo ?? index)"
      class="piv__page"
      @click="open(index)"
    >
      <image
        class="piv__img"
        :src="String(page.url ?? '')"
        mode="widthFix"
        lazy-load
      />
      <text class="piv__no lqg-num">{{ page.pageNo ?? index + 1 }}</text>
    </view>
    <view v-if="pages.length > 0" class="piv__hint">
      <text class="piv__hint-t">点页面图可全屏放大 · 双指缩放</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.piv {
  margin: 0 var(--lqg-gutter);
}

.piv__page {
  position: relative;
  line-height: 0;
  border-bottom: 1px solid var(--lqg-line);
}

.piv__page:last-of-type {
  border-bottom: none;
}

.piv__img {
  width: 100%;
  display: block;
}

/* 页码：浮在页面图右下角 */
.piv__no {
  position: absolute;
  right: var(--lqg-sp-3);
  bottom: var(--lqg-sp-3);
  min-width: 20px;
  text-align: center;
  padding: 1px var(--lqg-sp-2);
  font-size: var(--lqg-fs-xs);
  line-height: 1.5;
  color: var(--lqg-on-primary);
  background: var(--lqg-mask);
  border-radius: var(--lqg-radius-badge);
}

.piv__empty {
  padding: 36px var(--lqg-sp-6);
  text-align: center;
}

.piv__empty-t {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.piv__hint {
  padding: var(--lqg-sp-4) var(--lqg-sp-6);
  background: var(--lqg-inset);
  text-align: center;
}

.piv__hint-t {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}
</style>
