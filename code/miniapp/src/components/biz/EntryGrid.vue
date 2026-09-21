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

/** 外部三格时最后一格占满整行（UI:mp.home.entries：不留半宽的孤格） */
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
      :mark="presentationOf(key).mark"
      :title="presentationOf(key).title"
      :desc="presentationOf(key).desc"
      :full="isFull(index)"
      @click="emit('pick', key)"
    />
  </view>
</template>
