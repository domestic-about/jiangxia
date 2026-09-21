<script setup lang="ts">
import { computed } from 'vue'

// StatusChip（落地规范 §5.6）：`.lqg-tag` 浅底深字，按字典值取修饰类。
// 颜色只在 `components.scss` 里给，页面与本组件都不写色值。
//
// 词表（字典 `lqg_verify_status` / `lqg_submit_source` / `lqg_doc_status`）：
//   pending 琥珀 · valid 绿 · invalid 红 · internal 青绿 · external 蓝 · draft 琥珀 · published 绿
withDefaults(defineProps<{
  /** 字典值（未知值不渲染，避免出现一个没有颜色的空徽标） */
  value?: string | null
  /** 自定义文案（默认按字典值给中文） */
  text?: string
}>(), {
  value: '',
  text: '',
})

const TEXT: Record<string, string> = {
  pending: '待核验',
  valid: '有效',
  invalid: '无效',
  internal: '内部',
  external: '外部',
  draft: '草稿',
  published: '已发布',
}

/** 认得的字典值才给修饰类；不认得的一个类都不加（但不影响文案） */
function toneOf(value: string): string {
  return value && TEXT[value] ? `lqg-tag--${value}` : ''
}

const label = computed(() => props.text || TEXT[props.value || ''] || '')
</script>

<template>
  <text v-if="label" class="lqg-tag" :class="toneOf(value || '')">{{ label }}</text>
</template>
