<template>
  <div class="lqg-score-group">
    <!-- 左：变量名（模板里的「分类」行首） -->
    <div class="lqg-score-group__name">{{ label }}</div>

    <!-- 中：单选。★ 选项文字与分值都来自字典（useDict 的 label 与 remark），页面不写死 -->
    <el-radio-group class="lqg-score-group__options" :model-value="modelValue ?? ''" :disabled="disabled" @update:model-value="handleChange">
      <el-radio v-for="option in options" :key="option.value" :value="option.value" class="lqg-score-group__option">
        <span class="lqg-score-group__text">{{ option.label }}</span>
        <span v-if="hasPoints(option)" class="lqg-score-group__points">
          {{ t('lqg.qc.score.points', { n: option.remark }) }}
        </span>
      </el-radio>
    </el-radio-group>

    <!-- 右：「类器官质量评分」列 —— 当前这一项的分值（没选 = —） -->
    <div class="lqg-score-group__score">{{ scoreText }}</div>
  </div>
</template>

<script setup name="LqgScoreRadioGroup" lang="ts">
import { useI18n } from 'vue-i18n';

// ============================================================================
// 评分表的一行：变量名 + 一档单选 + 该项分值（QC-WEB-002 / FLOW:F-QC-01.step4）
//
// ★ 「选项文字照模板原文」= 直接用字典的 `dict_label`（`<40` / `40~80` / `>14d` /
//    `30~100μm`…），**不在页面里重写、也不加任何质量等级结论**。
// ★ 分值来自字典的 `remark`（sys_dict_data.remark，唯一来源）；这里是**即时反馈**，
//    保存只提交 `value`，落库分值由后端回填。
// ★ 没选显示 `—`；0 分档显示 `0`（0 和「没选」是两件事，见 score.ts 的口径）。
// ============================================================================

// ★ 类型只在本组件内声明（`<script setup>` 里不能出现 ES module 导出）；
//   父组件按结构传参即可（label / value / remark）。
interface ScoreRadioOption {
  /** 字典 label（照模板原文） */
  label: string;
  /** 字典 value（保存时提交的就是它） */
  value: string;
  /** 字典 remark = 该档分值（可能为空：字典没配） */
  remark?: string | number | null;
}

const props = defineProps<{
  label: string;
  /** 当前选中的档位 value；null / '' = 没选 */
  modelValue?: string | null;
  options: ScoreRadioOption[];
  /** 该项分值（scoreSummary 的结果；null = 没选或字典没配） */
  score?: number | null;
  disabled?: boolean;
}>();

const emit = defineEmits<{ (e: 'update:modelValue', value: string | null): void }>();

const { t } = useI18n();

const hasPoints = (option: ScoreRadioOption) => option.remark !== null && option.remark !== undefined && option.remark !== '';

/** 0 分也照显示；只有 null / undefined 才显示「—」 */
const scoreText = computed(() => (props.score === null || props.score === undefined ? '—' : String(props.score)));

const handleChange = (value: string | number | boolean | undefined) => {
  emit('update:modelValue', value === '' || value === undefined || value === null ? null : String(value));
};
</script>

<style scoped lang="scss">
.lqg-score-group {
  display: grid;
  grid-template-columns: 160px 1fr 64px;
  gap: 8px;
  align-items: center;
  padding: 8px 0;
  border-top: 1px solid var(--lqg-line);

  .lqg-score-group__name {
    font-size: 13px;
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-score-group__options {
    display: flex;
    flex-wrap: wrap;
    gap: 2px 14px;
  }
  .lqg-score-group__option {
    margin-right: 0;
  }
  .lqg-score-group__text {
    color: var(--lqg-ink);
  }
  .lqg-score-group__points {
    margin-left: 4px;
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-score-group__score {
    font-family: var(--lqg-font-mono);
    font-size: 15px;
    color: var(--lqg-ink);
    text-align: right;
  }
  @media (max-width: 1200px) {
    grid-template-columns: 1fr 64px;

    .lqg-score-group__name {
      grid-column: 1 / -1;
    }
  }
}
</style>
