<template>
  <div class="p-2 lqg-opstat">
    <el-card shadow="hover">
      <template #header>
        <div class="lqg-opstat__head">
          <span class="lqg-opstat__title">{{ t('lqg.stat.operator.title') }}</span>
          <span class="lqg-opstat__subtitle">{{ t('lqg.stat.operator.subtitle') }}</span>
        </div>
      </template>

      <!-- 筛选：时间段（五个预设 + 自定义）、操作人、表类型 —— 后两个默认全部 -->
      <el-form label-width="64px" class="lqg-opstat__filter" @submit.prevent>
        <el-form-item :label="t('lqg.stat.operator.filter.range')">
          <div class="lqg-opstat__bar">
            <SegButtons :model-value="preset" :options="presetOptions" :clearable="false" @update:model-value="pickPreset" />
            <!-- 自定义：开始、结束各自可空（只填结束 = 这一天及之前；只填开始 = 这一天及之后） -->
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
            <span v-if="inverted" class="lqg-opstat__error">{{ t('lqg.stat.operator.inverted') }}</span>
            <span v-else class="lqg-opstat__range-text">{{ rangeText }}</span>
          </div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.stat.operator.filter.person')">
              <el-select
                v-model="personKey"
                filterable
                clearable
                :placeholder="t('lqg.stat.operator.filter.all')"
                class="lqg-opstat__control"
                data-filter="person"
              >
                <el-option v-for="option in people" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6">
            <el-form-item :label="t('lqg.stat.operator.filter.sheet')">
              <el-select v-model="sheetKey" clearable :placeholder="t('lqg.stat.operator.filter.all')" class="lqg-opstat__control" data-filter="sheet">
                <el-option v-for="sheet in sheets" :key="sheet.sheet" :label="sheet.title" :value="sheet.sheet" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="24" :md="8" :lg="12">
            <el-form-item label-width="0">
              <el-button icon="Refresh" :loading="loading" @click="load">{{ t('lqg.stat.operator.refresh') }}</el-button>
              <el-button v-if="personKey || sheetKey" link type="primary" @click="clearFilters">{{ t('lqg.stat.operator.filter.clear') }}</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 一张表：一行一个人，一列是一张表里的一个数（表头第一层是表名）。各列各算各的，不横向相加 -->
      <el-table
        v-loading="loading"
        :data="table.rows"
        border
        :row-class-name="rowClassName"
        :show-summary="showSummary"
        :summary-method="summaryOf"
        class="lqg-opstat__table"
      >
        <template #empty>
          <table-empty :error="loadError" :text="t('lqg.stat.operator.empty')" @retry="load" />
        </template>
        <el-table-column :label="t('lqg.stat.operator.colName')" min-width="180" fixed>
          <template #default="scope">
            <span class="lqg-opstat__name">{{ scope.row.name }}</span>
            <el-tag v-if="scope.row.me" size="small" type="success" effect="plain" class="lqg-opstat__tag">{{ t('lqg.stat.operator.me') }}</el-tag>
            <el-tooltip v-else-if="isUnlinked(scope.row)" :content="t('lqg.stat.operator.unlinkedTip')" placement="top">
              <el-tag size="small" type="warning" effect="plain" class="lqg-opstat__tag">{{ t('lqg.stat.operator.unlinked') }}</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column v-for="group in table.groups" :key="group.sheet" align="center">
          <template #header>
            <div class="lqg-opstat__group" :data-sheet="group.sheet">
              <span class="lqg-opstat__group-title">{{ group.title }}</span>
              <span class="lqg-opstat__group-by">{{ t('lqg.stat.operator.by', { label: group.dateLabel }) }}</span>
            </div>
          </template>
          <el-table-column
            v-for="column in group.columns"
            :key="column.id"
            :column-key="column.id"
            align="right"
            min-width="124"
            sortable
            :sort-method="(a: PivotRow, b: PivotRow) => (a.cells[column.id] ?? 0) - (b.cells[column.id] ?? 0)"
          >
            <template #header>{{ column.label }}（{{ column.unit }}）</template>
            <template #default="scope">
              <!-- 数字可点：带着「这个人 + 这段时间」跳到对应列表，筛出来的就是这几条 -->
              <router-link
                v-if="(scope.row.cells[column.id] ?? 0) > 0"
                :to="statLink(column.sheet, column.metric, scope.row.key, scope.row.name, range)"
                class="lqg-opstat__num"
                :title="scope.row.key === PERSON_NONE ? t('lqg.stat.operator.noneTip') : undefined"
              >
                {{ scope.row.cells[column.id] }}
              </router-link>
              <span v-else class="lqg-opstat__zero">0</span>
            </template>
          </el-table-column>
        </el-table-column>
      </el-table>

      <!-- 每张表的数是怎么数的（口径）；只列当前显示的表 -->
      <ul class="lqg-opstat__notes">
        <li v-for="group in table.groups" :key="group.sheet">
          <span class="lqg-opstat__notes-title">{{ group.title }}</span>{{ t(`lqg.stat.operator.note.${group.sheet}`) }}
        </li>
        <li>{{ t('lqg.stat.operator.noteCommon') }}</li>
      </ul>
    </el-card>
  </div>
</template>

<script setup name="LqgOperatorStat" lang="ts">
// 工作台「操作人统计」（菜单 5010，路由 /operator-stat，component = lqg/stat/operator/index；CR-20261012-22）。
//
// 甲方（2026-10-10 / 10-11 微信）：想知道某个操作人这个月 / 这周 / 去年 / 一共做了多少个 ——
// 样本记录信息表（养了多少原代）、类器官送样记录、石蜡包埋送样记录、-80 冻存记录各算各的；质控表不用统计。
//
// ★ CR-20261013-23（Kevin 看了测试站）：四张小表并成**一张表** —— 一行一个人，一列是一张表里的一个数；
//   顶部加「操作人」「表类型」两个筛选（默认全部）。问的是「某个人做了多少」，横着一行就看全了。
// ★ 各列各算各的，**不横向相加**（样本记录信息表是从原代开始养的，和别的表里的操作人不是一回事）。
// ★ 数字每次现算；从列表改完回来（页面被 keep-alive 缓存）会自动重取一次。
// ★ 接口没变（四张表各一块）：并表与两个筛选在 pivot.ts，时间段与跳转在 range.ts（都是纯函数，有 spec）。
import { getOperatorStat } from '@/api/lqg/stat';
import type { OperatorStatSheet } from '@/api/lqg/stat';
import SegButtons from '@/components/lqg/SegButtons/index.vue';
import TableEmpty from '@/components/lqg/TableEmpty/index.vue';
import { PERSON_NONE } from '@/components/lqg/StaffSelect/staff';
import { useStaff } from '@/components/lqg/StaffSelect/useStaff';
import { useI18n } from 'vue-i18n';
import { personOptions, pivot } from './pivot';
import type { PivotRow } from './pivot';
import { RANGE_PRESETS, presetRange, rangeInverted, statLink } from './range';
import type { DateRange, RangePreset } from './range';

const { t } = useI18n();
const { staff, load: loadStaff } = useStaff();

const preset = ref<RangePreset>('month');
const custom = reactive<DateRange>({ begin: null, end: null });
/** 只看这个人（行的 key）；空 = 全部 */
const personKey = ref<string | null>(null);
/** 只看这张表；空 = 全部 */
const sheetKey = ref<string | null>(null);
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

/** 「操作人」的选项：名单里的人 + 数据里出现但不在名单里的（带标注）+ 未填 */
const people = computed(() =>
  personOptions(staff.value, sheets.value, {
    unlinked: t('lqg.person.unlinked'),
    gone: t('lqg.person.gone'),
    none: t('lqg.person.none')
  })
);

/** 那一张表：操作人、表类型两个筛选都在同一份结果上做（不重新请求） */
const table = computed(() =>
  pivot(sheets.value, {
    personKey: personKey.value || null,
    sheet: sheetKey.value || null,
    personName: people.value.find((option) => option.value === personKey.value)?.label
  })
);

/** 合计行：只看一个人时就是他自己那一行，不重复显示 */
const showSummary = computed(() => !personKey.value && table.value.rows.length > 0);

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
    // 请求层已经弹过报错；表头还在，表里说「没加载出来」
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

const clearFilters = () => {
  personKey.value = null;
  sheetKey.value = null;
};

const isUnlinked = (row: PivotRow) => !row.linked && row.key !== PERSON_NONE;

const rowClassName = ({ row }: { row: PivotRow }) => (row.me ? 'lqg-opstat__row-me' : row.key === PERSON_NONE ? 'lqg-opstat__row-none' : '');

/** 合计行：第一格「合计」，其余是每一列各行之和（按当前显示的列，顺序 = 表头顺序） */
const summaryOf = () => [
  t('lqg.stat.operator.total'),
  ...table.value.groups.flatMap((group) => group.columns.map((column) => String(table.value.totals[column.id] ?? 0)))
];

let mounted = false;
onMounted(async () => {
  loadStaff();
  await load();
  mounted = true;
});

// 从列表改完操作人回来：页面是缓存的，重取一次数字才对得上
onActivated(() => {
  if (mounted) {
    loadStaff();
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
  .lqg-opstat__filter {
    margin-bottom: 4px;
  }
  .lqg-opstat__bar {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }
  .lqg-opstat__control {
    width: 100%;
  }
  .lqg-opstat__date {
    width: 190px;
  }
  .lqg-opstat__to {
    color: var(--lqg-ink-3);
  }
  .lqg-opstat__range-text {
    font-size: 13px;
    color: var(--lqg-ink-3);
  }
  .lqg-opstat__error {
    font-size: 13px;
    color: var(--el-color-danger);
  }
  /* 表头第一层：表名 + 「按哪个时间」两行 */
  .lqg-opstat__group {
    display: flex;
    flex-direction: column;
    align-items: center;
    line-height: 1.4;
  }
  .lqg-opstat__group-title {
    font-weight: 600;
    color: var(--lqg-ink);
  }
  .lqg-opstat__group-by {
    font-size: 12px;
    font-weight: normal;
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
  .lqg-opstat__notes {
    margin: 12px 0 0;
    padding-left: 18px;
    font-size: 12px;
    line-height: 1.8;
    color: var(--lqg-ink-3);
  }
  .lqg-opstat__notes-title {
    margin-right: 6px;
    color: var(--lqg-ink-2);
  }
  :deep(.lqg-opstat__row-me) {
    --el-table-tr-bg-color: var(--el-color-success-light-9);
  }
  :deep(.lqg-opstat__row-none) .lqg-opstat__name {
    color: var(--lqg-ink-3);
  }
}
</style>
