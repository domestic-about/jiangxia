<script setup lang="ts">
import type { EntryKey } from '@/pages/index/entries'
import MeRow from '@/components/biz/MeRow.vue'
import { presentationOf } from '@/components/biz/entry-presentation'

// 「我的 · 内部管理」板块（UI:mp.me）：
// **只给内部人员**——外部整块不渲染（不是置灰），由页面按 `meSections(identity)` 决定挂不挂。
// 四个入口进表格页（SAMPLE-MP-002 起建，本张先放占位页）；底部小字逐字照 UI:mp.me。
defineProps<{
  entries: EntryKey[]
  note: string
}>()

const emit = defineEmits<{
  (e: 'pick', key: EntryKey): void
}>()
</script>

<template>
  <view class="lqg-card lqg-card--flush adm">
    <MeRow
      v-for="(key, index) in entries"
      :key="key"
      :mark="presentationOf(key).mark"
      :title="presentationOf(key).title"
      :class="{ 'adm__row--line': index > 0 }"
      @click="emit('pick', key)"
    />
    <view class="adm__note">
      <text class="adm__notet">{{ note }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.adm__row--line {
  border-top: 1px solid var(--lqg-line);
}

.adm__note {
  padding: var(--lqg-sp-5) var(--lqg-sp-6);
  border-top: 1px solid var(--lqg-line);
  background: var(--lqg-inset);
}

.adm__notet {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-2);
}
</style>
