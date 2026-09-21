<script setup lang="ts">
// 首页入口格（UI:mp.home.entries，落地规范 §5.2 `.lqg-tile`）。
// - 图标井 `.lqg-well` 里放一个线性的字标（方向 A 图里是图标 + 表名）
// - 入口上**不放角标**（UI:mp.home.entries：入口上不放角标）
// - 外部三格时第三格传 `full` → `.lqg-tile--full` 横向占满整行
withDefaults(defineProps<{
  /** 图标井里的一个字 */
  mark: string
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
      <text class="tile__mark">{{ mark }}</text>
    </view>
    <view class="tile__body">
      <text class="lqg-tile__t">{{ title }}</text>
      <text v-if="desc" class="lqg-tile__d">{{ desc }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.tile__mark {
  font-size: 20px;
  font-weight: var(--lqg-fw-semibold);
}

.tile__body {
  display: flex;
  flex-direction: column;
}
</style>
