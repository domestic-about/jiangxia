<script setup lang="ts">
import { computed } from 'vue'
import type { ResolvedIdentity } from '@/types/identity'
import { identityLabel, identityTagClass } from '@/types/identity'

// 首页问候行（UI:mp.home ①，落地规范 §5.2）：
// 「某某，上午好」+ 身份徽标；外部再一行「单位 · 组别 · 核验状态」。
// 身份徽标用 `.lqg-tag`（不用 wd-tag：它非 plain 时文字恒白）。
const props = withDefaults(defineProps<{
  name?: string
  identity?: ResolvedIdentity
  /** 外部那一行：单位 · 组别 · 核验状态 */
  secondary?: string
}>(), {
  name: '',
  identity: null,
  secondary: '',
})

const greeting = computed(() => {
  const hour = new Date().getHours()
  const period = hour < 6 ? '凌晨' : hour < 12 ? '上午' : hour < 18 ? '下午' : '晚上'
  // 没有姓名（新号还没填）就只问候，不拼「你好，晚上好」
  return props.name ? `${props.name}，${period}好` : `${period}好`
})

const label = computed(() => identityLabel(props.identity))
const tagClass = computed(() => identityTagClass(props.identity))
</script>

<template>
  <view class="idbar">
    <view class="idbar__row">
      <text class="idbar__greet">{{ greeting }}</text>
      <text v-if="label" class="lqg-tag" :class="tagClass">{{ label }}</text>
    </view>
    <text v-if="secondary" class="idbar__sub">{{ secondary }}</text>
  </view>
</template>

<style lang="scss" scoped>
.idbar {
  padding: 18px var(--lqg-gutter) 0;
}

.idbar__row {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
}

.idbar__greet {
  font-size: var(--lqg-fs-greet);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.idbar__sub {
  display: block;
  margin-top: var(--lqg-sp-1);
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
}
</style>
