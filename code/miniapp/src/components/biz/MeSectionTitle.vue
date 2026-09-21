<script setup lang="ts">
// 「我的」里的分组标题（落地规范 §5.1 的 `.lqg-gl`）：
// 左标题 + 右侧小字（如「查看 · 筛选 · 导出」）。标题为空时不渲染。
withDefaults(defineProps<{
  title: string
  hint?: string
}>(), {
  hint: '',
})
</script>

<template>
  <view v-if="title" class="lqg-gl">
    <text>{{ title }}</text>
    <text v-if="hint" class="lqg-gl__x">{{ hint }}</text>
  </view>
</template>
