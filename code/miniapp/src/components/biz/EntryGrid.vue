<script setup lang="ts">
import type { EntryKey } from '@/pages/index/entries'
import EntryTile from '@/components/ui/EntryTile.vue'
import { presentationOf } from './entry-presentation'

// 首页「填写」宫格（UI:mp.home.entries）。
// 入口清单由上层的 `entriesFor(identity)` 决定（本组件不做任何身份判断）：
// - 内部 4 个 → 2×2
// - 外部 3 个 → 前两格一行，第三格 `.lqg-tile--full` 横向占满整行
const props = defineProps<{
  entries: EntryKey[]
}>()

const emit = defineEmits<{
  (e: 'pick', key: EntryKey): void
}>()

/**
 * 外部三格时最后一格占满整行（UI:mp.home.entries：不留半宽的孤格）。
 *
 * ★ 2026-09-30 飞书「小程序」第 17 行截图：真机上第三格仍是半宽、却换成了横排，
 *   于是「石蜡包埋送样记录」「包埋、切片、染色」都各掉一个字到下一行。原因：EntryTile 是自定义组件，
 *   小程序里网格的直接子元素是**组件宿主节点**，写在组件内部 `.lqg-tile--full` 上的 `grid-column: span 2`
 *   够不到网格（H5 没有宿主节点，所以 H5 上是好的）。
 *   → 占满整行的 class 放在组件标签上（小程序落到宿主节点、H5 透传到根元素，两端都是网格的直接子元素）；
 *   `full` 仍负责组件内部的横排。
 */
function isFull(index: number): boolean {
  return props.entries.length === 3 && index === 2
}
</script>

<template>
  <view class="lqg-sec">
    <text class="lqg-sec__t">填写</text>
    <text class="lqg-sec__x">点表新增一条</text>
  </view>

  <view class="lqg-grid">
    <EntryTile
      v-for="(key, index) in entries"
      :key="key"
      :icon="presentationOf(key).icon"
      :title="presentationOf(key).title"
      :desc="presentationOf(key).desc"
      :full="isFull(index)"
      :class="{ 'lqg-grid__span2': isFull(index) }"
      @click="emit('pick', key)"
    />
  </view>
</template>

<style lang="scss">
/* 占满整行加在组件标签上（原因见上方 isFull 注释）。
 * ★ 不写 scoped、也不加进 src/style/components.scss：后者要与设计权威 direction-a/components.scss 逐字一致；
 *   这里是本组件模板里的节点（子组件宿主节点属于本组件模板），组件样式够得到。 */
.lqg-grid__span2 {
  grid-column: span 2;
}
</style>
