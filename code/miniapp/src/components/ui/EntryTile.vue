<script setup lang="ts">
import LineIcon from '@/components/ui/LineIcon.vue'

// 首页入口格（UI:mp.home.entries，落地规范 §5.2 `.lqg-tile`）。
// - 图标井 `.lqg-well`（42×42、青绿浅底）里放一个 22px 的线性图标（`line-icons.ts`，与「我的」同一套）
// - 入口上**不放角标**（UI:mp.home.entries：入口上不放角标）
// - 外部三格时第三格传 `full` → `.lqg-tile--full` 横向占满整行
withDefaults(defineProps<{
  /** 图标井里的线性图标名 */
  icon: string
  title: string
  desc?: string
  full?: boolean
}>(), {
  desc: '',
  full: false,
})
</script>

<template>
  <view class="lqg-tile" :class="{ 'lqg-tile--full': full }">
    <view class="lqg-well">
      <LineIcon :name="icon" :size="22" />
    </view>
    <view class="tile__body">
      <text class="lqg-tile__t">{{ title }}</text>
      <text v-if="desc" class="lqg-tile__d">{{ desc }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.tile__body {
  display: flex;
  flex-direction: column;
}
</style>
