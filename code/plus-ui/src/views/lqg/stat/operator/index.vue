<template>
  <div class="p-2 lqg-opstat">
    <el-card shadow="hover">
      <template #header>
        <div class="lqg-opstat__head">
          <span class="lqg-opstat__title">{{ t('lqg.stat.operator.title') }}</span>
          <span class="lqg-opstat__subtitle">{{ t('lqg.stat.operator.subtitle') }}</span>
        </div>
      </template>

      <!-- 时间段：五个预设 + 自定义（开始、结束各自可空 —— 「在这之前 / 之后做了多少」） -->
      <div class="lqg-opstat__bar">
        <SegButtons :model-value="preset" :options="presetOptions" :clearable="false" @update:model-value="pickPreset" />
        <template v-if="preset === 'custom'">
          <el-date-picker
            v-model="custom.begin"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="t('lqg.stat.operator.customBegin')"
            class="lqg-opstat__date"
            clearable
          />
          <span class="lqg-opstat__to">{{ t('lqg.stat.operator.customTo') }}</span>
          <el-date-picker
            v-model="custom.end"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="t('lqg.stat.operator.customEnd')"
            class="lqg-opstat__date"
            clearable
          />
          <el-button type="primary" icon="Search" :disabled="inverted" @click="load">{{ t('lqg.stat.operator.query') }}</el-button>
        </template>
        <el-button link type="primary" icon="Refresh" :loading="loading" @click="load">{{ t('lqg.stat.operator.refresh') }}</el-button>
      </div>
      <div class="lqg-opstat__range">
        <span v-if="inverted" class="lqg-opstat__error">{{ t('lqg.stat.operator.inverted') }}</span>
        <template v-else>
          <span class="lqg-opstat__range-text">{{ rangeText }}</span>
          <span v-if="preset === 'custom'" class="lqg-opstat__hint">{{ t('lqg.stat.operator.customHint') }}</span>
        </template>
      </div>
    </el-card>

    <el-row v-loading="loading" :gutter="12" class="lqg-opstat__sheets">
      <el-col v-for="sheet in sheets" :key="sheet.sheet" :xs="24" :lg="12">
        <el-card shadow="hover" class="lqg-opstat__sheet" :data-sheet="sheet.sheet">
          <template #header>
            <div class="lqg-opstat__sheet-head">
              <span class="lqg-opstat__sheet-title">{{ sheet.title }}</span>
              <span class="lqg-opstat__sheet-by">{{ t('lqg.stat.operator.by', { label: sheet.dateLabel }) }} · {{ t(`lqg.stat.operator.note.${sheet.sheet}`) }}</span>
            </div>
          </template>
          <el-table
            :data="sheet.rows"
            size="small"
            :row-class-name="rowClassName"
            show-summary
            :summary-method="() => summaryOf(sheet)"
          >
            <template #empty>
              <table-empty :error="loadError" :text="t('lqg.stat.operator.empty')" @retry="load" />
            </template>
            <el-table-column :label="t('lqg.stat.operator.colName')" min-width="160">
              <template #default="scope">
                <span class="lqg-opstat__name">{{ scope.row.name }}</span>
                <el-tag v-if="scope.row.me" size="small" type="success" effect="plain" class="lqg-opstat__tag">{{ t('lqg.stat.operator.me') }}</el-tag>
                <el-tooltip v-else-if="isUnlinked(scope.row)" :content="t('lqg.stat.operator.unlinkedTip')" placement="top">
                  <el-tag size="small" type="warning" effect="plain" class="lqg-opstat__tag">{{ t('lqg.stat.operator.unlinked') }}</el-tag>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column v-for="metric in sheet.metrics" :key="metric.key" align="right" min-width="120">
              <template #header>{{ metric.label }}（{{ metric.unit }}）</template>
              <template #default="scope">
                <!-- 数字可点：带着「这个人 + 这段时间」跳到对应列表，筛出来的就是这几条 -->
                <router-link
                  v-if="(scope.row.values[metric.key] ?? 0) > 0"
                  :to="statLink(sheet.sheet, metric.key, scope.row.key, scope.row.name, range)"
                  class="lqg-opstat__num"
                  :title="scope.row.key === PERSON_NONE ? t('lqg.stat.operator.noneTip') : undefined"
                >
                  {{ scope.row.values[metric.key] }}
                </router-link>
                <span v-else class="lqg-opstat__zero">0</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="LqgOperatorStat" lang="ts">
// 工作台「操作人统计」（菜单 5010，路由 /operator-stat，component = lqg/stat/operator/index；CR-20261012-22）。
//
// 甲方（2026-10-10 / 10-11 微信）：想知道某个操作人这个月 / 这周 / 去年 / 一共做了多少个 ——
// 样本记录信息表（养了多少原代）、类器官送样记录、石蜡包埋送样记录、-80 冻存记录各算各的；质控表不用统计。
//
// ★ 四张表**不加总**（样本记录信息表是从原代开始养的，和别的表里的操作人不是一回事）。
// ★ 数字每次现算；从列表改完回来（页面被 keep-alive 缓存）会自动重取一次。
// ★ 表名、列名、每行的姓名与 key 都是接口给的；时间段与跳转在 range.ts（纯函数，有 spec）。
import { getOperatorStat } from '@/api/lqg/stat';
import type { OperatorStatRow, OperatorStatSheet } from '@/api/lqg/stat';
import SegButtons from '@/components/lqg/SegButtons/index.vue';
import TableEmpty from '@/components/lqg/TableEmpty/index.vue';
import { PERSON_NONE } from '@/components/lqg/StaffSelect/staff';
import { useI18n } from 'vue-i18n';
import { RANGE_PRESETS, presetRange, rangeInverted, statLink } from './range';
import type { DateRange, RangePreset } from './range';

const { t } = useI18n();

const preset = ref<RangePreset>('month');
const custom = reactive<DateRange>({ begin: null, end: null });
const loading = ref(false);
const loadError = ref(false);
const sheets = ref<OperatorStatSheet[]>([]);

const presetOptions = computed(() => RANGE_PRESETS.map((value) => ({ value, label: t(`lqg.stat.operator.preset.${value}`) })));

/** 当前生效的时间段（两端都含；null = 这一头不限） */
const range = computed<DateRange>(() =>
  preset.value === 'custom' ? { begin: custom.begin || null, end: custom.end || null } : presetRange(preset.value, new Date())
);

const inverted = computed(() => rangeInverted(range.value));

const rangeText = computed(() => {
  const { begin, end } = range.value;
  if (begin && end) {
    return t('lqg.stat.operator.range.between', { begin, end });
  }
  if (end) {
    return t('lqg.stat.operator.range.until', { end });
  }
  if (begin) {
    return t('lqg.stat.operator.range.since', { begin });
  }
  return t('lqg.stat.operator.range.all');
});

const load = async () => {
  if (inverted.value) {
    return;
  }
  loading.value = true;
  loadError.value = false;
  try {
    const res = await getOperatorStat(range.value);
    sheets.value = res.data?.sheets ?? [];
  } catch {
    // 请求层已经弹过报错；这里保留四张表的框，表里说「没加载出来」
    loadError.value = true;
    sheets.value = sheets.value.map((sheet) => ({ ...sheet, rows: [], totals: {} }));
  } finally {
    loading.value = false;
  }
};

const pickPreset = (value: unknown) => {
  preset.value = (value as RangePreset) ?? 'month';
  // 自定义：等填好日期点「查询」；别的预设点了就查
  if (preset.value !== 'custom') {
    load();
  }
};

const isUnlinked = (row: OperatorStatRow) => !row.linked && row.key !== PERSON_NONE;

const rowClassName = ({ row }: { row: OperatorStatRow }) =>
  row.me ? 'lqg-opstat__row-me' : row.key === PERSON_NONE ? 'lqg-opstat__row-none' : '';

/** 合计行：第一格「合计」，其余是每一列全部行之和（接口给的 totals） */
const summaryOf = (sheet: OperatorStatSheet) => [t('lqg.stat.operator.total'), ...sheet.metrics.map((m) => String(sheet.totals?.[m.key] ?? 0))];

let mounted = false;
onMounted(async () => {
  await load();
  mounted = true;
});

// 从列表改完操作人回来：页面是缓存的，重取一次数字才对得上
onActivated(() => {
  if (mounted) {
    load();
  }
});
</script>

<style scoped lang="scss">
.lqg-opstat {
  .lqg-opstat__head {
    display: flex;
    align-items: baseline;
    gap: 12px;
    flex-wrap: wrap;
  }
  .lqg-opstat__title {
    font-size: 16px;
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-opstat__subtitle {
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-opstat__bar {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }
  .lqg-opstat__date {
    width: 190px;
  }
  .lqg-opstat__to {
    color: var(--lqg-ink-3);
  }
  .lqg-opstat__range {
    margin-top: 10px;
    font-size: 13px;
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
  }
  .lqg-opstat__range-text {
    color: var(--lqg-ink);
    font-weight: 600;
  }
  .lqg-opstat__hint {
    color: var(--lqg-ink-3);
  }
  .lqg-opstat__error {
    color: var(--el-color-danger);
  }
  .lqg-opstat__sheets {
    margin-top: 12px;
    min-height: 120px;
  }
  .lqg-opstat__sheet {
    margin-bottom: 12px;
  }
  .lqg-opstat__sheet-head {
    display: flex;
    align-items: baseline;
    gap: 10px;
    flex-wrap: wrap;
  }
  .lqg-opstat__sheet-title {
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-opstat__sheet-by {
    font-size: 12px;
    color: var(--lqg-ink-3);
  }
  .lqg-opstat__tag {
    margin-left: 8px;
  }
  .lqg-opstat__num {
    color: var(--el-color-primary);
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    text-decoration: none;
    &:hover {
      text-decoration: underline;
    }
  }
  .lqg-opstat__zero {
    color: var(--lqg-ink-3);
    font-variant-numeric: tabular-nums;
  }
  :deep(.lqg-opstat__row-me) {
    --el-table-tr-bg-color: var(--el-color-success-light-9);
  }
  :deep(.lqg-opstat__row-none) .lqg-opstat__name {
    color: var(--lqg-ink-3);
  }
}
</style>
