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

// ★ 2026-09-28 修（Kevin 报「小程序里首页四个板块点不动，H5 能点」）：
//   父组件是 `<EntryTile @click="…" />` —— 在**自定义组件**上写 `@click`，
//   H5 靠 Vue 的原生事件兜底能触发，而**小程序**会编译成自定义组件标签的 `bindclick`，
//   只有当子组件 `triggerEvent('click')` 时才会触发。原先本组件既没声明 emits 也不 emit，
//   所以小程序里点了毫无反应。现在显式声明并 emit（声明后 Vue 不再挂原生兜底 → H5 也只触发一次）。
const emit = defineEmits<{ (e: 'click', ev: unknown): void }>()
</script>

<template>
  <view class="lqg-tile" :class="{ 'lqg-tile--full': full }" @click="emit('click', $event)">
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
