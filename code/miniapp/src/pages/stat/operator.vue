<script setup lang="ts">
import { computed, ref } from 'vue'
import type { OperatorStatSheet } from '@/api/stat'
import { fetchOperatorStat } from '@/api/stat'
import EmptyState from '@/components/lqg/EmptyState.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
// ★ 显式 .vue 路径导入（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失）
import WdDatetimePicker from 'wot-design-uni/components/wd-datetime-picker/wd-datetime-picker.vue'
import type { DateRange, RangePreset } from './range'
import { RANGE_PRESETS, presetRange, rangeInverted, rangeText, rowTag, rowsMeFirst, sheetNote, ymd } from './range'

// 小程序「操作人统计」（内部人员专用，CR-20261012-22；入口在「我的 → 内部管理」下面）。
//
// 甲方（2026-10-10 / 10-11 微信）：想知道某个操作人这个月 / 这周 / 去年 / 一共做了多少个 ——
// 样本记录信息表（养了多少原代）、类器官送样记录、石蜡包埋送样记录、-80 冻存记录各算各的；质控表不用统计。
//
// ★ 与工作台「操作人统计」同一份结果（`/mp/int/stat/operator`）：表名、列名、每行的姓名都是接口给的。
// ★ 四张表**不加总**。当前登录人那一行置顶并标「我」（「我做了多少个」是打开这页最想看的）。
// ★ 只看不点：逐条核对在工作台的统计页（数字可以点进列表）；小程序这页是随手看数。
// ★ 只给内部人员：外部深链进来显示一句话、一个请求都不发（接口本身也是内部角色才放行）。
definePage({
  style: {
    navigationBarTitleText: '操作人统计',
    enablePullDownRefresh: true,
  },
})

const store = useUserStore()
const isInternal = computed(() => normalizeIdentity(store.identity) === 'internal')

const preset = ref<RangePreset>('month')
const custom = ref<DateRange>({ begin: '', end: '' })
const loading = ref(true)
const failed = ref(false)
const sheets = ref<OperatorStatSheet[]>([])

/** 当前生效的时间段（两端都含；'' = 这一头不限） */
const range = computed<DateRange>(() => preset.value === 'custom' ? custom.value : presetRange(preset.value, new Date()))
const inverted = computed(() => rangeInverted(range.value))

async function load() {
  if (!isInternal.value || inverted.value) {
    loading.value = false
    return
  }
  loading.value = true
  failed.value = false
  try {
    const data = await fetchOperatorStat(range.value)
    sheets.value = data?.sheets ?? []
  }
  catch {
    failed.value = true
  }
  finally {
    loading.value = false
  }
}

function pickPreset(value: RangePreset) {
  if (preset.value === value) {
    return
  }
  preset.value = value
  load()
}

// ── 自定义时间段：两个日期各自可空 ─────────────────────────────────────────────
const pickerRef = ref<{ open: () => void } | null>(null)
const pickerField = ref<'begin' | 'end'>('begin')
const pickerValue = ref<number>(Date.now())

function openDate(field: 'begin' | 'end') {
  pickerField.value = field
  const value = custom.value[field]
  const ms = value ? new Date(`${value}T00:00:00`).getTime() : Date.now()
  pickerValue.value = Number.isNaN(ms) ? Date.now() : ms
  pickerRef.value?.open()
}

function onPicked(event: { value: number | string }) {
  const ms = Number(event.value)
  if (Number.isNaN(ms)) {
    return
  }
  custom.value = { ...custom.value, [pickerField.value]: ymd(new Date(ms)) }
  load()
}

function clearDate(field: 'begin' | 'end') {
  custom.value = { ...custom.value, [field]: '' }
  load()
}

onLoad(async () => {
  // 冷启动直接进来（H5 深链 / 小程序分享）时 store 里还没有 me：先拉回来再判身份
  if (!store.me) {
    try {
      await store.loadMe()
    }
    catch {
      failed.value = true
    }
  }
  load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="ops">
    <EmptyState v-if="store.me && !isInternal" state="empty" text="操作人统计只给中心内部人员开放" />

    <template v-else>
      <!-- 时间段：五个预设 + 自定义 -->
      <view class="lqg-card ops__filter">
        <scroll-view class="ops__scroll" scroll-x :show-scrollbar="false" enhanced>
          <view class="ops__chips">
            <text
              v-for="item in RANGE_PRESETS"
              :key="item.value"
              class="lqg-seg__item ops__chip"
              :class="{ 'lqg-seg__item--on': preset === item.value }"
              @click="pickPreset(item.value)"
            >{{ item.label }}</text>
          </view>
        </scroll-view>

        <!-- 自定义：开始、结束各自可空（只填结束 = 这一天及之前；只填开始 = 这一天及之后） -->
        <view v-if="preset === 'custom'" class="ops__custom">
          <view class="ops__date" @click="openDate('begin')">
            <text class="ops__date-l">开始</text>
            <text class="ops__date-v" :class="{ 'ops__date-v--none': !custom.begin }">{{ custom.begin || '不限' }}</text>
            <text v-if="custom.begin" class="ops__date-x" @click.stop="clearDate('begin')">清除</text>
          </view>
          <view class="ops__date" @click="openDate('end')">
            <text class="ops__date-l">结束</text>
            <text class="ops__date-v" :class="{ 'ops__date-v--none': !custom.end }">{{ custom.end || '不限' }}</text>
            <text v-if="custom.end" class="ops__date-x" @click.stop="clearDate('end')">清除</text>
          </view>
        </view>

        <text v-if="inverted" class="ops__range ops__range--bad">开始日期不能晚于结束日期</text>
        <text v-else class="ops__range">{{ rangeText(range) }}</text>
      </view>

      <LoadingState v-if="loading" />

      <ErrorState v-else-if="failed" text="没能加载操作人统计" @retry="load" />

      <template v-else-if="!inverted">
        <view class="lqg-count">四张表各算各的，不加在一起 · 质控表不统计</view>

        <view v-for="sheet in sheets" :key="sheet.sheet" class="lqg-card ops__sheet">
          <view class="ops__sheet-head">
            <text class="ops__sheet-t">{{ sheet.title }}</text>
            <text class="ops__sheet-n">{{ sheetNote(sheet) }}</text>
          </view>

          <!-- 表头：姓名 + 这张表的几个数 -->
          <view class="ops__row ops__row--head">
            <text class="ops__name">姓名</text>
            <text v-for="metric in sheet.metrics" :key="metric.key" class="ops__num ops__num--head">{{ metric.label }}（{{ metric.unit }}）</text>
          </view>

          <view v-if="sheet.rows.length === 0" class="ops__empty">这段时间没有记录</view>

          <view
            v-for="row in rowsMeFirst(sheet.rows)"
            :key="row.key"
            class="ops__row"
            :class="{ 'ops__row--me': row.me, 'ops__row--none': row.key === 'none' }"
          >
            <view class="ops__name">
              <text class="ops__name-t">{{ row.name }}</text>
              <text v-if="rowTag(row) === 'me'" class="lqg-tag lqg-tag--valid ops__tag">我</text>
              <text v-else-if="rowTag(row) === 'unlinked'" class="lqg-tag lqg-tag--pending ops__tag">未关联账号</text>
            </view>
            <text v-for="metric in sheet.metrics" :key="metric.key" class="ops__num">{{ row.values[metric.key] ?? 0 }}</text>
          </view>

          <view v-if="sheet.rows.length > 0" class="ops__row ops__row--total">
            <text class="ops__name">合计</text>
            <text v-for="metric in sheet.metrics" :key="metric.key" class="ops__num">{{ sheet.totals[metric.key] ?? 0 }}</text>
          </view>
        </view>

        <view class="ops__foot">
          <text>「未关联账号」是以前手填的姓名，没对上内部人员账号；把那些记录的这一栏重新选一次就会并到那个人名下。</text>
          <text>要逐条核对，请到网页工作台的「操作人统计」，数字可以点进列表。</text>
        </view>
      </template>
    </template>

    <wd-datetime-picker
      ref="pickerRef"
      v-model="pickerValue"
      type="date"
      title="选择日期"
      @confirm="onPicked"
    >
      <!-- ★ 给默认插槽放一个空节点（G12）：没有默认插槽时 wd-datetime-picker 会自己渲染一行「值 ›」的 cell -->
      <view />
    </wd-datetime-picker>
  </view>
</template>

<style lang="scss" scoped>
.ops {
  padding: var(--lqg-sp-5) 0 var(--lqg-sp-8);
}

.ops__filter {
  margin: 0 var(--lqg-gutter);
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-4);
}

.ops__scroll {
  width: 100%;
  white-space: nowrap;
}

.ops__chips {
  display: inline-flex;
  gap: var(--lqg-sp-2);
}

.ops__chip {
  flex: none;
}

.ops__custom {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-3);
}

/* 一个日期一行：行高 44，点击区够大；右边的「清除」单独可点 */
.ops__date {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-4);
  min-height: 44px;
  padding: 0 var(--lqg-sp-5);
  border-radius: var(--lqg-radius-field);
  background: var(--lqg-bg);
}

.ops__date-l {
  flex: none;
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.ops__date-v {
  flex: 1;
  min-width: 0;
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
}

.ops__date-v--none {
  color: var(--lqg-ink-3);
}

.ops__date-x {
  flex: none;
  padding: var(--lqg-sp-3) 0 var(--lqg-sp-3) var(--lqg-sp-4);
  font-size: var(--lqg-fs-body);
  color: var(--lqg-primary);
}

.ops__range {
  font-size: var(--lqg-fs-body);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.ops__range--bad {
  color: var(--lqg-danger);
}

.ops__sheet {
  margin: 0 var(--lqg-gutter) var(--lqg-sp-4);
  display: flex;
  flex-direction: column;
}

.ops__sheet-head {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-1);
  padding-bottom: var(--lqg-sp-3);
}

.ops__sheet-t {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.ops__sheet-n {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

/* 一个人一行：姓名靠左吃满，数字靠右等宽对齐 */
.ops__row {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
  min-height: 44px;
  border-top: 1px solid var(--lqg-line);
}

.ops__row--head {
  min-height: 32px;
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.ops__name {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
}

.ops__row--head .ops__name {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.ops__name-t {
  min-width: 0;
  word-break: break-all;
}

.ops__tag {
  flex: none;
}

.ops__num {
  flex: none;
  width: 84px;
  text-align: right;
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  font-variant-numeric: tabular-nums;
  color: var(--lqg-ink);
}

.ops__num--head {
  font-size: var(--lqg-fs-sm);
  font-weight: normal;
  color: var(--lqg-ink-3);
}

.ops__row--me .ops__name-t,
.ops__row--me .ops__num {
  color: var(--lqg-primary);
}

.ops__row--none .ops__name-t,
.ops__row--none .ops__num {
  color: var(--lqg-ink-3);
  font-weight: normal;
}

.ops__row--total .ops__name {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
}

.ops__empty {
  padding: var(--lqg-sp-6) 0;
  border-top: 1px solid var(--lqg-line);
  text-align: center;
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.ops__foot {
  margin: var(--lqg-sp-5) var(--lqg-sp-8) 0;
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-2);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
  line-height: 1.6;
}
</style>
