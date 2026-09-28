<script setup lang="ts">
import SegButtons from '@/components/lqg/SegButtons.vue'
import { EXPR_OPTIONS } from '@/pages/embed/stain'
// ★ 显式 .vue 路径导入（SAMPLE-MP-001 坑 6）
import WdInput from 'wot-design-uni/components/wd-input/wd-input.vue'

// mark 表达多行（UI:mp.embed.form / FLOW:F-EMBED-01.step3）：
// 每行 = 名称输入（可空）+ 三按钮单选（阴性 / 弱表达 / 强表达），可增删。
//
// ★ 值域 = 字典 `lqg_marker_expr`（negative / weak / strong），选项常量在 `stain.ts` 里
//   与染色那五个按钮放在一处（同一份字典口径）。
// ★ 只读时不渲染输入控件与「＋」/「删除」——只把已有行显示成文字。
interface MarkerRow {
  markerName: string
  expression: string
}

const props = withDefaults(defineProps<{
  modelValue?: MarkerRow[]
  disabled?: boolean
}>(), {
  modelValue: () => [],
  disabled: false,
})

const emit = defineEmits<{ (e: 'update:modelValue', rows: MarkerRow[]): void }>()

function patch(index: number, next: Partial<MarkerRow>) {
  const rows = props.modelValue.map((row, i) => (i === index ? { ...row, ...next } : row))
  emit('update:modelValue', rows)
}

function add() {
  if (props.disabled) {
    return
  }
  emit('update:modelValue', [...props.modelValue, { markerName: '', expression: '' }])
}

function remove(index: number) {
  if (props.disabled) {
    return
  }
  emit('update:modelValue', props.modelValue.filter((_, i) => i !== index))
}
</script>

<template>
  <view class="mkr">
    <view v-if="modelValue.length === 0" class="mkr__empty">
      <text class="mkr__empty-t">还没有 marker</text>
    </view>
    <view v-for="(row, index) in modelValue" :key="index" class="mkr__row">
      <view class="mkr__head">
        <text class="mkr__no">第 {{ index + 1 }} 行</text>
        <text v-if="!disabled" class="mkr__del" @click="remove(index)">删除</text>
      </view>
      <view v-if="disabled" class="mkr__ro">
        <text class="mkr__ro-name">{{ row.markerName || '（未命名）' }}</text>
        <text class="mkr__ro-expr">{{ EXPR_OPTIONS.find(o => o.value === row.expression)?.label || '—' }}</text>
      </view>
      <template v-else>
        <view class="mkr__name">
          <wd-input
            :model-value="row.markerName"
            placeholder="marker 名称（可空）"
            :maxlength="50"
            no-border
            @update:model-value="(v: string) => patch(index, { markerName: v })"
          />
        </view>
        <SegButtons
          :options="EXPR_OPTIONS"
          :model-value="row.expression"
          :disabled="disabled"
          @update:model-value="(v: string) => patch(index, { expression: v })"
        />
      </template>
    </view>
    <text v-if="!disabled" class="mkr__add" @click="add">＋ 加一行</text>
  </view>
</template>

<style lang="scss" scoped>
.mkr {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-4);
}

.mkr__row {
  padding: var(--lqg-sp-4);
  background: var(--lqg-inset);
  border-radius: var(--lqg-radius-ctl);
}

.mkr__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mkr__no {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.mkr__del {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-danger);
}

.mkr__name {
  margin: var(--lqg-sp-3) 0;
  padding: 0 var(--lqg-sp-3);
  background: var(--lqg-card);
  border-radius: var(--lqg-radius-seg);
}

.mkr__ro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--lqg-sp-2);
}

.mkr__ro-name {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink);
}

.mkr__ro-expr {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
}

.mkr__add {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-primary);
}

.mkr__empty-t {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}
</style>
