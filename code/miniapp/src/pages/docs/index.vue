<script setup lang="ts">
import PlaceholderPage from '@/components/ui/PlaceholderPage.vue'

// 占位页（SYS-MP-001 §2）：本张只打通路由与入口，
// 正文内容归属 DOC-MP-001。
definePage({
  style: {
    navigationBarTitleText: '文档',
  },
})
</script>

<template>
  <PlaceholderPage title="文档" owner="DOC-MP-001" desc="文档页签：本张只放空状态，内容在 DOC-MP-001。" />
</template>
