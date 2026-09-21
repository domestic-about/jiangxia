<script setup lang="ts">
// 占位页（SYS-MP-001 §2）：本张只把路由与入口打通，
// 填写页 / 表格页 / 历史编辑记录页 / 文档页签 / 协议正文分别在各域 ticket 里做。
withDefaults(defineProps<{
  /** 页面标题（与 pages.json 的导航标题一致） */
  title: string
  /** 这一页归属哪张 ticket */
  owner?: string
  /** 一句话说明 */
  desc?: string
}>(), {
  owner: '',
  desc: '这一页的内容在后续 ticket 里做，本张只打通入口。',
})
</script>

<template>
  <view class="ph">
    <view class="lqg-card ph__card">
      <text class="ph__t">{{ title }}</text>
      <text class="ph__d">{{ desc }}</text>
      <text v-if="owner" class="ph__o">内容归属：{{ owner }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.ph {
  padding: var(--lqg-gap) var(--lqg-gutter);
}

.ph__card {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-3);
}

.ph__t {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.ph__d {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
}

.ph__o {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}
</style>
