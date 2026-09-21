<script setup lang="ts">
import { computed } from 'vue'
import type { ResolvedIdentity } from '@/types/identity'
import { identityLabel, identityTagClass } from '@/types/identity'

// 「我的」页头（UI:mp.me，落地规范 §5.3）：
// 头像缺省显示姓的首字 → 姓名 + 身份徽标 → 手机号（中间四位掩码）。
// 身份徽标只用 `.lqg-tag` + 身份修饰类，颜色不在页面里写。
const props = withDefaults(defineProps<{
  name?: string
  phoneMasked?: string
  identity?: ResolvedIdentity
}>(), {
  name: '',
  phoneMasked: '',
  identity: null,
})

const initial = computed(() => (props.name ? props.name.slice(0, 1) : '·'))
const label = computed(() => identityLabel(props.identity))
const tagClass = computed(() => identityTagClass(props.identity))
const phone = computed(() => props.phoneMasked || '手机号未知')
</script>

<template>
  <view class="mehead">
    <view class="mehead__av">
      <text class="mehead__avt">{{ initial }}</text>
    </view>
    <view class="mehead__id">
      <view class="mehead__row">
        <text class="mehead__name">{{ name || '未命名' }}</text>
        <text v-if="label" class="lqg-tag" :class="tagClass">{{ label }}</text>
      </view>
      <text class="mehead__phone lqg-mono">{{ phone }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.mehead {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-6);
}

.mehead__av {
  width: 52px;
  height: 52px;
  flex: none;
  border-radius: var(--lqg-radius-ctl);
  background: var(--lqg-primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}

.mehead__avt {
  font-size: var(--lqg-fs-greet);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-primary);
}

.mehead__id {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-1);
  min-width: 0;
}

.mehead__row {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
}

.mehead__name {
  font-size: var(--lqg-fs-greet);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.mehead__phone {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
}
</style>
