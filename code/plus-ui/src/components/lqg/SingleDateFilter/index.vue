<template>
  <div class="lqg-single-date">
    <el-date-picker
      :model-value="singleDate"
      type="date"
      value-format="YYYY-MM-DD"
      :placeholder="placeholder"
      clearable
      @update:model-value="selectDay"
    />
    <el-tag v-if="statPeriod" closable type="info" class="lqg-single-date__period" @close="selectDay(null)">
      {{ t('lqg.person.statPeriod', { begin: modelValue?.[0], end: modelValue?.[1] }) }}
    </el-tag>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';

// 普通筛选只选一天；沿用现有接口的含首尾日期参数，列表与导出无需另设规则。
// 统计页带来的多日范围显式保留为可清除条件，不能悄悄缩成某一天或隐藏筛选。
const props = defineProps<{ modelValue: [string, string] | null; placeholder: string }>();
const emit = defineEmits<{ 'update:modelValue': [value: [string, string] | null] }>();
const { t } = useI18n();
const statPeriod = computed(() => !!props.modelValue && props.modelValue[0] !== props.modelValue[1]);
const singleDate = computed(() => (props.modelValue && !statPeriod.value ? props.modelValue[0] : null));
const selectDay = (value: unknown) => {
  emit('update:modelValue', typeof value === 'string' && value ? [value, value] : null);
};
</script>

<style scoped lang="scss">
.lqg-single-date {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  min-width: 0;

  :deep(.el-date-editor.el-input) {
    width: 100%;
    min-width: 0;
  }
}
.lqg-single-date__period {
  max-width: 100%;
  height: auto;
  white-space: normal;
  line-height: 1.5;
}
</style>
