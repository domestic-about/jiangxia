<script setup lang="ts">
import PlaceholderPage from '@/components/ui/PlaceholderPage.vue'

// 占位页（SYS-MP-001 §2）：本张只打通路由与入口，
// 正文内容归属 SYS-RELEASE-001。
definePage({
  style: {
    navigationBarTitleText: '用户协议',
  },
})
</script>

<template>
  <PlaceholderPage title="用户协议" owner="SYS-RELEASE-001" desc="协议正文在 SYS-RELEASE-001，本张先放占位标题。" />
</template>
