<script setup lang="ts">
import PlaceholderPage from '@/components/ui/PlaceholderPage.vue'

// 占位页（SYS-MP-001 §2）：本张只打通路由与入口，
// 正文内容归属 CRYO-MP-001。
definePage({
  style: {
    navigationBarTitleText: '-80 冻存记录',
  },
})
</script>

<template>
  <PlaceholderPage title="-80 冻存记录 · 填写" owner="CRYO-MP-001" desc="冻存批次新增；外部人员看不到这一项。" />
</template>
