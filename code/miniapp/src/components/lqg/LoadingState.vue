<script setup lang="ts">
import EmptyState from './EmptyState.vue'

// LoadingState（落地规范 §5.11）：加载中那一支的薄别名 —— 实际渲染在 `EmptyState.vue`。
// 拆成三个名字是为了让页面按语义挑组件，而不是让每个页面自己拼 `.lqg-skel` 骨架块。
withDefaults(defineProps<{ text?: string }>(), { text: '' })
</script>

<template>
  <EmptyState state="loading" :text="text" />
</template>
