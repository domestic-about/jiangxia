<template>
  <!--
    SegButtons · 分段按钮组（SAMPLE-WEB-001 产出，后续各域直接用）

    甲方模板里写「按钮」的字段一律用它：有无固定、质控表、细胞活率报告、有无病理、性别。
    为什么不用 el-switch / el-select：
      * 开关只有「开 / 关」两个状态，**没有「还没选」**——新增时数据还没核验，
        开关一渲染出来就已经是「无」，等于替甲方答了题；
      * 下拉把 2-3 个选项藏进一次点击里，甲方模板上的「有 / 无」是并排两个按钮。

    约定：
      * options 元素可以是字符串（'Y'）也可以是 { label, value }；label 缺省时直接显示值。
      * modelValue 单选取标量（string / number / null），多选取数组。
      * exclusive：**互斥值**数组（如 ['NONE'] = 「无染色」与任何染色互斥）。选中互斥值时清掉其它值，
        选中非互斥值时清掉互斥值，两边不会同时亮。
      * 单选下点已选中的项 = 取消选择（emit null）——这是「还没选」这个状态唯一的入口。
        `clearable === false` 时不允许取消（用于「必须选一个」的字段）。

    样式只吃 --lqg-* / --el-* 变量，零颜色字面量（工作台浅色一套）。
  -->
  <div class="lqg-seg" :class="{ 'lqg-seg--disabled': disabled }">
    <el-checkbox-group v-if="multiple" :model-value="selectedArray" :disabled="disabled" @update:model-value="onMultipleChange">
      <el-checkbox-button v-for="opt in normalized" :key="String(opt.value)" :value="opt.value">
        {{ opt.label }}
      </el-checkbox-button>
    </el-checkbox-group>

    <el-radio-group v-else :model-value="selectedScalar" :disabled="disabled" @update:model-value="onScalarChange">
      <el-radio-button v-for="opt in normalized" :key="String(opt.value)" :value="opt.value">
        {{ opt.label }}
      </el-radio-button>
    </el-radio-group>
  </div>
</template>

<script setup name="LqgSegButtons" lang="ts">
/**
 * 分段按钮组（公共组件）。
 *
 * 单选下「再点一次 = 取消选择」：el-radio-group 自己不会因为点了已选项就取消，
 * 所以这里手动处理 —— 这是「还没选」这个状态唯一的入口（甲方模板的三个「有无」都有这个状态）。
 */
export interface SegButtonOption {
  label?: string;
  value: string | number;
}

const props = withDefaults(
  defineProps<{
    /** 选项：字符串数组或 { label, value } 数组 */
    options: Array<string | number | SegButtonOption>;
    /** 单选 = 标量；多选 = 数组 */
    modelValue?: string | number | Array<string | number> | null;
    /** 多选 */
    multiple?: boolean;
    /** 互斥值：选中它时清掉其余，选中其余时清掉它 */
    exclusive?: Array<string | number>;
    /** 单选下是否允许点第二次取消（默认允许 = 有「还没选」这个状态） */
    clearable?: boolean;
    /** 只读 / 置灰 */
    disabled?: boolean;
  }>(),
  {
    modelValue: null,
    multiple: false,
    exclusive: () => [],
    clearable: true,
    disabled: false
  }
);

const emit = defineEmits<{ (e: 'update:modelValue', value: string | number | Array<string | number> | null): void }>();

const normalized = computed<SegButtonOption[]>(() =>
  (props.options ?? []).map((opt) =>
    typeof opt === 'object' && opt !== null
      ? { label: String(opt.label ?? opt.value ?? ''), value: (opt.value ?? '') as string | number }
      : { label: String(opt), value: opt as string | number }
  )
);

const isExclusive = (value: string | number) => (props.exclusive ?? []).some((v) => v === value);

const selectedArray = computed<Array<string | number>>(() =>
  Array.isArray(props.modelValue) ? [...props.modelValue] : props.modelValue === null || props.modelValue === undefined || props.modelValue === '' ? [] : [props.modelValue]
);

const selectedScalar = computed<string | number | undefined>(() =>
  Array.isArray(props.modelValue) ? props.modelValue[0] : (props.modelValue ?? undefined) === null ? undefined : (props.modelValue as string | number)
);

const onMultipleChange = (next: Array<string | number>) => {
  let value = [...next];
  // 本次新增的值里只要有一个互斥值，就只留互斥值；反之若新增的是普通值，把互斥值摘掉
  const added = value.filter((v) => !selectedArray.value.includes(v));
  const removed = selectedArray.value.filter((v) => !value.includes(v));
  if (added.some(isExclusive)) {
    value = value.filter((v) => isExclusive(v) || !added.includes(v));
  } else if (removed.some(isExclusive)) {
    // 取消互斥值：保持已选的普通值
  } else if (added.length > 0) {
    value = value.filter((v) => !isExclusive(v));
  }
  emit('update:modelValue', value);
};

const onScalarChange = (next: string | number | undefined) => {
  if (next === undefined || next === null || next === '') {
    emit('update:modelValue', null);
    return;
  }
  // 点已选中项：允许取消 → 回到「还没选」
  if (next === selectedScalar.value) {
    emit('update:modelValue', props.clearable ? null : next);
    return;
  }
  emit('update:modelValue', next);
};

defineExpose({ normalized });
</script>

<style scoped lang="scss">
.lqg-seg {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;

  :deep(.el-radio-group),
  :deep(.el-checkbox-group) {
    flex-wrap: wrap;
  }

  &.lqg-seg--disabled {
    opacity: 0.6;
  }
}
</style>
