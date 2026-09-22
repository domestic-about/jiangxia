<template>
  <div class="lqg-score-tab">
    <div class="lqg-score-tab__head">
      <span class="lqg-score-tab__title">{{ t('lqg.qc.score.title') }}</span>
      <span class="lqg-score-tab__hint">{{ t('lqg.qc.score.immediateHint') }}</span>
    </div>

    <!-- 列头：变量 / 分档 / 该项分值（模板 G 的三列） -->
    <div class="lqg-score-tab__colhead">
      <span>{{ t('lqg.qc.score.variableCol') }}</span>
      <span>{{ t('lqg.qc.score.optionsCol') }}</span>
      <span class="lqg-score-tab__colhead-score">{{ t('lqg.qc.score.scoreCol') }}</span>
    </div>

    <!-- 四组单选：选项文字与分值来自字典（label / remark），页面不写死任何档位与分值 -->
    <ScoreRadioGroup
      v-for="(row, index) in rows"
      :key="row.key"
      :label="t(row.labelKey)"
      :options="row.options"
      :model-value="levels[row.key]"
      :score="summary.items[index]"
      :disabled="readonly"
      @update:model-value="(value) => (levels[row.key] = value)"
    />

    <!-- 合计 = 四项之和；任一项没选 → 空 -->
    <div class="lqg-score-tab__total">
      <span class="lqg-score-tab__total-label">{{ t('lqg.qc.score.total') }}</span>
      <span class="lqg-score-tab__total-value">{{ summary.total === null ? '—' : summary.total }}</span>
      <span v-if="summary.total === null" class="lqg-score-tab__total-pending">{{ t('lqg.qc.score.totalPending') }}</span>
    </div>

    <!-- 落库值（后端按字典 remark 回填）——保存后刷新时看得见权威值 -->
    <div class="lqg-score-tab__persisted">{{ persistedText }}</div>

    <!-- 底部附件区（UI:admin.qc.editor 的左栏底部；后端 /score/attachment 支持） -->
    <section class="lqg-score-tab__attachments">
      <AttachmentList
        :sample-id="sampleId"
        :doc-type="docType"
        :attachments="doc.attachments || []"
        :disabled="readonly"
        @changed="emit('changed')"
      />
    </section>
  </div>
</template>

<script setup name="LqgScoreTab" lang="ts">
import { saveScore, type QcScoreDocVO } from '@/api/lqg/qc';
import { useDict } from '@/utils/dict';
import ScoreRadioGroup from '../components/ScoreRadioGroup.vue';
import AttachmentList from '../components/AttachmentList.vue';
import { SCORE_DICT_TYPES, SCORE_KEYS, scoreSummary, type ScoreDictScores, type ScoreLevelKey } from './score';
import { useI18n } from 'vue-i18n';

// ============================================================================
// 「类器官质量评分表」页签（QC-WEB-002 / FLOW:F-QC-01.step4）
//
// 四个变量各一组单选，选项文字与分值来自 `useDict`（label / remark）：
//   培养前样本评分 <40(8) / 40~80(16) / >80(20)
//   培养天数       >14d(0) / ≤14d(10)
//   类器官数量     <100(0) / 100~1500(10) / 1500~4000(25) / >4000(40)
//   类器官直径     <30μm(10) / 30~100μm(20) / >100μm(30)
//
// ★ 页面上的分值与合计只是**即时反馈**：保存时只提交四个档位（`*Level`），
//   一个 `*Score` 都不传 —— 落库分值由后端按字典 remark 回填（QC-MODEL-001）。
//   保存成功后由父组件 reload，用后端返回的值刷新显示（`persistedText`）。
// ★ 0 分是「选了 0 分那一档」，不是没选（score.ts 的口径）；没选全 → 合计为空。
// ★ 不出「偏差 / 中等 / 良好」这类质量等级结论 —— 那句注是文档页脚的固定文字。
// ============================================================================

const props = defineProps<{
  sampleId: string | number;
  /** 评分表 VO（GET /lqg/qc/{sampleId} 的 data.score） */
  doc: QcScoreDocVO;
  readonly?: boolean;
}>();

const emit = defineEmits<{
  (e: 'changed'): void;
  (e: 'dirty', value: boolean): void;
}>();

const { t } = useI18n();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const docType = 'score' as const;

/** 四张评分字典：选项文字取 label、分值取 remark（唯一来源是 sys_dict_data.remark） */
const dict = useDict(SCORE_DICT_TYPES.preCulture, SCORE_DICT_TYPES.cultureDays, SCORE_DICT_TYPES.organoidCount, SCORE_DICT_TYPES.diameter);

const optionsOf = (key: ScoreLevelKey) =>
  (dict[SCORE_DICT_TYPES[key]] ?? []).map((item) => ({
    label: item.label,
    value: item.value,
    remark: item.remark ?? ''
  }));

const rows = computed(() =>
  SCORE_KEYS.map((key) => ({
    key,
    labelKey: `lqg.qc.score.${key}`,
    options: optionsOf(key)
  }))
);

/** 字典 remark（VARCHAR）→ 数字；scoreSummary 会把空串 / 非数字当成「没有分值」 */
const dictScores = computed<ScoreDictScores>(() => {
  const scores: ScoreDictScores = {};
  for (const key of SCORE_KEYS) {
    const table: Record<string, string> = {};
    for (const item of optionsOf(key)) {
      table[item.value] = String(item.remark ?? '');
    }
    scores[key] = table;
  }
  return scores;
});

/** 当前四个档位；null = 没选（★ 与「选了 0 分档」是两件事） */
const levels = reactive<Record<ScoreLevelKey, string | null>>({
  preCulture: null,
  cultureDays: null,
  organoidCount: null,
  diameter: null
});

const summary = computed(() => scoreSummary(levels, dictScores.value));

const dirty = ref(false);
let syncing = false;

/** 落库值（后端回填）：合计为空时说明「还有项没选」 */
const persistedText = computed(() => {
  const value = props.doc;
  if (!value) {
    return '';
  }
  if (value.totalScore === null || value.totalScore === undefined) {
    return t('lqg.qc.score.persistedEmpty');
  }
  const items = [value.preCultureScore, value.cultureDaysScore, value.organoidCountScore, value.diameterScore]
    .map((item) => (item === null || item === undefined ? '—' : String(item)))
    .join(' + ');
  return t('lqg.qc.score.persisted', { items, total: value.totalScore });
});

const syncFromDoc = (force = false) => {
  if (!props.doc || (dirty.value && !force)) {
    return;
  }
  syncing = true;
  levels.preCulture = props.doc.preCultureLevel ?? null;
  levels.cultureDays = props.doc.cultureDaysLevel ?? null;
  levels.organoidCount = props.doc.organoidCountLevel ?? null;
  levels.diameter = props.doc.diameterLevel ?? null;
  syncing = false;
  setDirty(false);
};

const setDirty = (value: boolean) => {
  dirty.value = value;
  emit('dirty', value);
};

watch(
  () => props.doc,
  () => syncFromDoc(),
  { immediate: true, deep: true }
);
watch(
  levels,
  () => {
    if (!syncing) {
      setDirty(true);
    }
  },
  { deep: true }
);

/** 保存草稿：★ 只发四个档位（一个 `*Score` 都不发；分值后端回填） */
const save = async () => {
  await saveScore(props.sampleId, {
    preCultureLevel: levels.preCulture,
    cultureDaysLevel: levels.cultureDays,
    organoidCountLevel: levels.organoidCount,
    diameterLevel: levels.diameter
  });
  setDirty(false);
  proxy?.$modal.msgSuccess(t('lqg.qc.editor.saved'));
};

defineExpose({ save, isDirty: () => dirty.value, syncFromDoc, summary });
</script>

<style scoped lang="scss">
.lqg-score-tab {
  .lqg-score-tab__head {
    display: flex;
    align-items: baseline;
    gap: 8px;
    flex-wrap: wrap;
    margin-bottom: 4px;
  }
  .lqg-score-tab__title {
    font-size: 13px;
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-score-tab__hint {
    flex: 1;
    min-width: 200px;
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-score-tab__colhead {
    display: grid;
    grid-template-columns: 160px 1fr 64px;
    gap: 8px;
    padding: 4px 0;
    border-bottom: 1px solid var(--lqg-line);
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-score-tab__colhead-score {
    text-align: right;
  }
  .lqg-score-tab__total {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 2px solid var(--lqg-line);
  }
  .lqg-score-tab__total-label {
    font-size: 13px;
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-score-tab__total-value {
    font-family: var(--lqg-font-mono);
    font-size: 20px;
    color: var(--lqg-primary);
  }
  .lqg-score-tab__total-pending {
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-score-tab__persisted {
    margin-top: 6px;
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-score-tab__attachments {
    margin-top: 12px;
    padding-top: 8px;
    border-top: 1px solid var(--lqg-line);
  }
  @media (max-width: 1200px) {
    .lqg-score-tab__colhead {
      grid-template-columns: 1fr 64px;

      span:nth-child(2) {
        display: none;
      }
    }
  }
}
</style>
