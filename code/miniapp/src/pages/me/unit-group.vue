<script setup lang="ts">
import PlaceholderPage from '@/components/ui/PlaceholderPage.vue'

// 占位页（SYS-MP-001 §2）：本张只打通路由与入口，
// 正文内容归属 AUTH-GROUP-001。
definePage({
  style: {
    navigationBarTitleText: '单位与组别',
  },
})
</script>

<template>
  <PlaceholderPage title="单位与组别" owner="AUTH-GROUP-001" desc="外部填 / 改单位与组别，改完回到待核验。" />
</template>
