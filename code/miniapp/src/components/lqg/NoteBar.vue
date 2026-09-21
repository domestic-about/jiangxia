<script setup lang="ts">
// NoteBar（落地规范 §5.1 的 `.lqg-note`）：一句提示条。
// - 默认：中性说明（`.lqg-note`）
// - `tone="warn"`：`.lqg-note--warn`（琥珀）——「核验与改判请到网页工作台」这类
// - `tone="danger"`：`.lqg-note--danger`（红）——无效原因这类
// 颜色全在 `.lqg-*` 类里，本组件不写色值。
withDefaults(defineProps<{
  tone?: 'info' | 'warn' | 'danger'
  text?: string
}>(), {
  tone: 'info',
  text: '',
})
</script>

<template>
  <view class="lqg-note" :class="tone === 'info' ? '' : `lqg-note--${tone}`">
    <text>{{ text }}</text>
  </view>
</template>
