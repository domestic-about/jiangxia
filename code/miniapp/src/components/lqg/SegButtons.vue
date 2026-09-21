<script setup lang="ts">
// SegButtons（落地规范 §5.5）：模板里写「按钮」的地方一律用它 ——
// 二态（有无）、三态（性别）、多选互斥都是这一个样子，**不用下拉、不用开关**。
//
// 选中态只有一块 `.lqg-seg__item--on`；颜色全在 `.lqg-*` 类里（零色值字面量）。
withDefaults(defineProps<{
  /** 选项：value + 显示文案 */
  options: Array<{ value: string, label: string }>
  /** 当前值（单选） */
  modelValue?: string
  /** 只读时不响应点击（只读页里按钮组仍是「看」的形态，不换成文字） */
  disabled?: boolean
}>(), {
  modelValue: '',
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

function pick(value: string) {
  if (props.disabled) {
    return
  }
  emit('update:modelValue', value)
}
</script>

<template>
  <view class="lqg-seg" :class="{ 'seg--ro': disabled }">
    <text
      v-for="opt in options"
      :key="opt.value"
      class="lqg-seg__item"
      :class="{ 'lqg-seg__item--on': opt.value === modelValue }"
      @click="pick(opt.value)"
    >{{ opt.label }}</text>
  </view>
</template>

<style lang="scss" scoped>
.seg--ro {
  opacity: 0.85;
}
</style>
