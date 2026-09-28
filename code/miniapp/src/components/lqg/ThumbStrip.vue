<script setup lang="ts">
// 「文档中的图片」横向缩略图条（UI:mp.doc.preview 中部 / FLOW:F-DOC-02.step2）· DOC-MP-002。
//
// ★★ **这是「两层放大」的第二层，也是最容易被做反的一处**（ticket §0 口径 1 +
//   accept 1 的 counterfeit 第一条）：
//     · **缩略图**用后端的 `previewUrl`（省流量）；
//     · **点开看的是 `url`（原图）** —— 甲方 L121「想看得更清楚一点」说的就是显微照片
//       的细节，而页面图只有 150 DPI。点开还是预览图 = 这个需求当场落空。
//   → 全屏看图器喂的是 `imageOpenUrl()` 算出来的地址（原图优先，调用点在下面 `open()`）。
// ★ 原图是 TIFF / DICOM / 全片扫描格式时微信的看图器打不开：退回 `previewUrl`
//   并提示一句（`imageOpenUrl` 的 `fallback`）。**不是**默认就给预览图。
// ★ 内外部身份都拿得到 images（外部那条在独立验收 V24 补齐，后端咽喉逐个核过对象）；
//   父组件传空数组时本组件整段不渲染（不是「加载失败」）。
// ★ 视觉按方向 A：横向 `scroll-view` + `.lqg-card--flush`，零色值字面量。
import type { DocImageRow } from '@/api/doc'
import { imageOpenUrl, thumbUrlOf } from '@/pages/doc/download'
import { computed } from 'vue'

const props = defineProps<{
  images: DocImageRow[]
}>()

/**
 * ★ 后端给不出签名链接的图片位**跳过**（`url` 与 `previewUrl` 都是 null）。
 *
 * 取不到对象的图（没有可签发的链接）不在页面上挂一排空 `<image>`；真图照常显示。
 * （文档本身缺图的口径另见 #217：外部版缺图整份不对外，内部版照出并记缺图。）
 */
const usable = computed(() => props.images.filter(image => !!thumbUrlOf(image)))

/** 每张图点开用的地址（原图；打不开的格式退预览图） */
function openUrls(): Array<{ url: string, fallback: boolean }> {
  return usable.value
    .map(image => imageOpenUrl(image))
    .filter(item => !!item.url)
}

function open(index: number) {
  const list = openUrls()
  if (list.length === 0) {
    return
  }
  // 目标那张自己退回预览图了就提示一句（仍然开图，不是拦着不开）
  const target = list[Math.min(index, list.length - 1)]
  if (target?.fallback) {
    uni.showToast({ title: '原图格式微信打不开，已显示预览图', icon: 'none' })
  }
  const urls = list.map(item => item.url)
  uni.previewImage({ urls, current: Math.min(index, urls.length - 1), indicator: 'number' })
}
</script>

<template>
  <view v-if="usable.length > 0" class="tst">
    <view class="lqg-gl">
      文档中的图片
      <text class="lqg-gl__x">点开看原图</text>
    </view>
    <scroll-view class="tst__scroll" scroll-x :show-scrollbar="false">
      <view class="tst__row">
        <image
          v-for="(image, index) in usable"
          :key="index"
          class="tst__thumb"
          :src="thumbUrlOf(image)"
          mode="aspectFill"
          lazy-load
          @click="open(index)"
        />
      </view>
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
.tst {
  margin-top: var(--lqg-gap);
}

.tst__scroll {
  width: 100%;
  white-space: nowrap;
}

.tst__row {
  display: inline-flex;
  gap: var(--lqg-sp-4);
  padding: 0 var(--lqg-gutter) var(--lqg-sp-2);
}

.tst__thumb {
  width: 84px;
  height: 84px;
  flex: none;
  border-radius: var(--lqg-radius-ctl);
  background: var(--lqg-inset);
  box-shadow: var(--lqg-shadow-sm);
}
</style>
