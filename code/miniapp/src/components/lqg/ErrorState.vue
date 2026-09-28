<script setup lang="ts">
import EmptyState from './EmptyState.vue'

// ErrorState（落地规范 §5.11）：失败那一支的薄别名（一句人话 + 描边「重试」）。
withDefaults(defineProps<{ text?: string }>(), { text: '' })

const emit = defineEmits<{
  (e: 'retry'): void
}>()
</script>

<template>
  <EmptyState state="error" :text="text" @retry="emit('retry')" />
</template>
