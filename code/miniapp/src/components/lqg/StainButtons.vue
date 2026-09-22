<script setup lang="ts">
import { STAIN_OPTIONS } from '@/pages/embed/stain'

// 染色五按钮（UI:mp.embed.form / FLOW:F-EMBED-01.step3 / 落地规范 §5.5）。
//
// ★ 本组件**不做互斥判断**：它只把「这一次点了哪一个」抛给页面，页面过 `toggleStain`
//   （`stain.ts` 的纯函数，需求层 fixture 驱动）。判据只有一份 —— 组件里再写一遍
//   NONE 互斥就是第二份判据，迟早与 fixture 不一致。
// ★ 只读时仍然渲染这五个按钮（保持「看得到选了哪些」），只是点不动。
const props = withDefaults(defineProps<{
  /** 当前选中的染色 value 数组 */
  modelValue?: string[]
  /** 只读 */
  disabled?: boolean
}>(), {
  modelValue: () => [],
  disabled: false,
})

const emit = defineEmits<{ (e: 'toggle', value: string): void }>()

function on(value: string) {
  if (props.disabled) {
    return
  }
  emit('toggle', value)
}
</script>

<template>
  <view class="lqg-seg stb">
    <text
      v-for="opt in STAIN_OPTIONS"
      :key="opt.value"
      class="lqg-seg__item"
      :class="{ 'lqg-seg__item--on': modelValue.includes(opt.value) }"
      @click="on(opt.value)"
    >{{ opt.label }}</text>
  </view>
</template>

<style lang="scss" scoped>
.stb {
  justify-content: flex-end;
}
</style>
