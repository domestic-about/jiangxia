<script setup lang="ts">
import { computed, ref } from 'vue'
import type { LedgerFilters, LedgerRow } from '@/api/ledger'
import { emptyFilters } from '@/api/ledger'
import CryoBatchSheet from '@/components/lqg/CryoBatchSheet.vue'
import EmptyState from '@/components/lqg/EmptyState.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import LedgerTable from '@/components/lqg/LedgerTable.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import { goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
import type { LedgerFilterKey, LedgerFilterSpec, LedgerSheet, LedgerTableRow } from './sheets'
import { columnsOf, ledgerTableWidth, sheetOf, sheetsFor, toTableRows } from './sheets'

// 内部管理 · 表格页（UI:mp.ledger / UI:mp.sample.list）· SAMPLE-MP-002（工作表注册随各域票增长）。
//
// 三条口径（ticket §0 的复述 2 / 3）：
//   1. **表格本身只读**：这里没有「＋ 一行」、没有行内编辑、没有状态流转；底部只有
//      「导出 Excel」（本张置灰，SYS-EXPORT-001 点亮）。
//   2. **列名、列序只有一个来源**：`pages/ledger/columns.ts`（再往上追是甲方四份 xlsx 原件）。
//      本文件一个列名都不写 —— 表头文案从 `columnsOf(sheet)` 拿。
//   3. **点一行进该表填写页的只读模式**（`mode=view`，在 `sheets.ts` 的 `target` 里）；
//      「修改」在只读页右上角、由那一页自己切（CR-20260918-07）。
//      ★ 例外：**-80 冻存那一档点一行打开只读的批次详情弹层**（`CryoBatchSheet`，
//      UI:mp.cryo.flow），要改记录走弹层右上角「修改」→ `pages/cryo/form?id=&mode=edit`。
//   4. **页签 / 筛选项上的数字只认接口给的 `tabCounts`**：不拿当前页 rows 去数
//      （冻存那三个页签切到「超期」只剩 2 行时，数字仍是整表的 7 / 2 / 2）。
//
// 页底小字逐字照 UI:mp.ledger —— 表格页已有修改入口，这行里**没有「修改」二字**。
const INTERNAL_ADMIN_NOTE = '核验、冻存取用请到网页工作台'

definePage({
  style: {
    navigationBarTitleText: '内部管理',
  },
})

const store = useUserStore()

const sheet = ref<LedgerSheet>(sheetOf(undefined))
const filters = ref<LedgerFilters>(emptyFilters())
const rows = ref<LedgerRow[]>([])
const total = ref(0)
/** 接口顶层的页签计数（整表口径）；只有冻存那张会给，别的表保持 null */
const tabCounts = ref<Record<string, number> | null>(null)
const loading = ref(false)
const failed = ref(false)
/** 冻存那一档点一行打开的只读批次详情弹层（其它表点一行直接进只读填写页） */
const cryoSheetRef = ref<{ open: (row: LedgerRow) => void } | null>(null)

const tabs = computed(() => sheetsFor())
const cols = computed(() => columnsOf(sheet.value))
const columnLabels = computed(() => cols.value?.columns.map(c => c.label) ?? [])
const tableWidth = computed(() => ledgerTableWidth(columnLabels.value.length))
const tableRows = computed<LedgerTableRow[]>(() => toTableRows(rows.value, sheet.value))
/** 该表的筛选项（样本两张表只有核验状态；石蜡包埋那张另有染色） */
const filterGroups = computed(() => sheet.value.filters)
const rowById = computed(() => new Map(rows.value.map(row => [String(row.id), row])))
/** 这一页只给内部人员（外部连入口都没有，「我的」里整块不渲染） */
const isExternal = computed(() => store.me !== null && store.me !== undefined && normalizeIdentity(store.identity) !== 'internal')

function titleOf(key: string): string {
  return tabs.value.find(t => t.key === key)?.short ?? ''
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    // ★ 身份的唯一来源是 `/mp/me`：本页常常是深链直接进来的（H5 / 分享），
    //   这时 store 里还没有 me，不先拉一次就会「一个请求都不发」。
    if (!store.me) {
      await store.loadMe()
    }
    if (normalizeIdentity(store.identity) !== 'internal') {
      rows.value = []
      total.value = 0
      tabCounts.value = null
      return
    }
    const page = await sheet.value.fetch(filters.value, 100)
    rows.value = page.rows ?? []
    total.value = page.total ?? rows.value.length
    // ★ 页签数字只认接口顶层的 `tabCounts`（整表口径）；这张表不给就清成 null，
    //   绝不退回 `rows.length`（那正是 ticket 的 counterfeit 抓的形态）
    tabCounts.value = page.tabCounts ?? null
  }
  catch {
    failed.value = true
    rows.value = []
    total.value = 0
    tabCounts.value = null
  }
  finally {
    loading.value = false
  }
}

/** 顶部切换条：换表重新取数，**筛选条件保留**（ticket §2） */
function pickSheet(item: LedgerSheet) {
  if (sheet.value.key === item.key) {
    return
  }
  sheet.value = item
  uni.setNavigationBarTitle({ title: item.title })
  load()
}

function pickFilter(key: LedgerFilterKey, value: string) {
  if (filters.value[key] === value) {
    return
  }
  filters.value[key] = value
  load()
}

/** 该筛选组当前选中的值（模板里读 `filters[x]` 不便，收一个函数） */
function activeFilter(key: LedgerFilterKey): string {
  return filters.value[key]
}

/**
 * 筛选项 / 页签的文案：该表给了 `chipsText` 就用它把数字补上（冻存那三个页签），
 * 否则原样显示注册表里的 label。数字来自接口的 `tabCounts`，不是页面上的行数。
 */
function chipText(group: LedgerFilterSpec, value: string, label: string): string {
  if (!sheet.value.chipsText) {
    return label
  }
  return sheet.value.chipsText(tabCounts.value)[value] ?? label
}

function onKeyword(value: string) {
  filters.value.keyword = value
}

/** 输入框事件：小程序 / H5 的 event 形状不同，统一在这里取一次值 */
function onKeywordInput(e: unknown) {
  const detail = (e as { detail?: { value?: string } })?.detail
  onKeyword(detail?.value ?? '')
}

function onKeywordConfirm() {
  load()
}

function onRowTap(row: LedgerTableRow) {
  const raw = rowById.value.get(row.id)
  if (!raw) {
    return
  }
  // ★ 冻存这一档点一行开的是**只读的批次详情弹层**（UI:mp.cryo.flow），
  //   不是填写页；要改记录走弹层右上角「修改」（CR-20260918-07）。
  if (sheet.value.key === 'cryo') {
    cryoSheetRef.value?.open(raw)
    return
  }
  goPage(sheet.value.target(raw))
}

/** 「导出 Excel」：本张置灰，SYS-EXPORT-001 点亮（按当前筛选导出） */
function exportNotYet() {
  uni.showToast({ title: '导出在后续版本开放', icon: 'none' })
}

onLoad((options) => {
  sheet.value = sheetOf(options?.sheet)
  const status = String(options?.verifyStatus ?? '')
  if (status) {
    filters.value.verifyStatus = status
  }
  uni.setNavigationBarTitle({ title: sheet.value.title })
  load()
})

// H5 深链首屏不触发 onShow，所以两个钩子都挂；`started` 保证同一屏只取一次数
let started = false
function start() {
  if (started) {
    return
  }
  started = true
  load()
}

onMounted(start)
onShow(start)
</script>

<template>
  <view class="ledger-page">
    <!-- ① 工作表切换条：只列注册表里已注册的两张（没注册的不显示） -->
    <view class="lqg-sheets">
      <text
        v-for="item in tabs"
        :key="item.key"
        class="lqg-sheets__item"
        :class="{ 'lqg-sheets__item--on': item.key === sheet.key }"
        @click="pickSheet(item)"
      >{{ item.short }}</text>
    </view>

    <!-- ② 筛选行：搜索框 + 该表自己的筛选项（核验状态 / 染色，由 sheets.ts 给） -->
    <!-- ② 筛选行：搜索框（该表有搜索才渲染）+ 该表自己的筛选项（由 sheets.ts 给） -->
    <view v-if="sheet.searchPlaceholder" class="lqg-filter">
      <view class="lqg-filter__chip ledger-page__search">
        <input
          class="ledger-page__input"
          type="text"
          :value="filters.keyword"
          :placeholder="sheet.searchPlaceholder"
          placeholder-class="ledger-page__ph"
          confirm-type="search"
          @input="onKeywordInput"
          @confirm="onKeywordConfirm"
        >
      </view>
    </view>
    <view v-for="group in filterGroups" :key="group.key" class="lqg-filter ledger-page__chips">
      <text
        v-for="opt in group.options"
        :key="`${group.key}-${opt.value}`"
        class="lqg-filter__chip ledger-page__chip"
        :class="{ 'ledger-page__chip--on': activeFilter(group.key) === opt.value }"
        @click="pickFilter(group.key, opt.value)"
      >{{ chipText(group, opt.value, opt.label) }}</text>
    </view>

    <view class="lqg-count">共 {{ total }} 条 · 左右滑动看全部 {{ (cols?.columns.length ?? 0) + 1 }} 列</view>

    <LoadingState v-if="loading" />

    <ErrorState v-else-if="failed" text="没能加载这张表" @retry="load" />

    <EmptyState v-else-if="isExternal" state="empty" text="内部管理只给内部人员开放" />

    <EmptyState v-else-if="rows.length === 0" state="empty" text="没有符合条件的记录" />

    <LedgerTable
      v-else-if="cols"
      :frozen-label="cols.frozen.label"
      :column-labels="columnLabels"
      :table-width="tableWidth"
      :rows="tableRows"
      @row-tap="onRowTap"
    />

    <view class="lqg-bar-spacer" />

    <!-- ④ 底部只有「导出 Excel」：本张置灰（SYS-EXPORT-001 点亮） -->
    <view class="lqg-bar ledger-page__bar">
      <button class="ledger-page__export" disabled @click="exportNotYet">
        导出 Excel
      </button>
      <text class="ledger-page__note">{{ INTERNAL_ADMIN_NOTE }}</text>
    </view>

    <!-- 冻存那一档点一行打开的只读批次详情弹层（唯一动作 = 右上角「修改」） -->
    <CryoBatchSheet ref="cryoSheetRef" />
  </view>
</template>

<style lang="scss" scoped>
.ledger-page {
  padding: var(--lqg-sp-5) 0 0;
}

.ledger-page__search {
  flex: 1;
  padding: 0 var(--lqg-sp-5);
}

.ledger-page__input {
  flex: 1;
  height: 38px;
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink);
}

.ledger-page__ph {
  color: var(--lqg-ink-3);
}

.ledger-page__chips {
  flex-wrap: wrap;
}

.ledger-page__chip {
  font-size: var(--lqg-fs-sm);
}

.ledger-page__chip--on {
  background: var(--lqg-primary-soft);
  color: var(--lqg-primary);
  font-weight: var(--lqg-fw-semibold);
  box-shadow: inset 0 0 0 1px var(--lqg-primary);
}

.ledger-page__bar {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-3);
}

.ledger-page__export {
  height: var(--lqg-btn-h);
  line-height: var(--lqg-btn-h);
  font-size: var(--lqg-fs-title);
  color: var(--lqg-primary);
  background: var(--lqg-card);
  border: 1px solid var(--lqg-primary);
  border-radius: var(--lqg-radius-ctl);
}

.ledger-page__export::after {
  border: none;
}

.ledger-page__note {
  text-align: center;
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-3);
}
</style>
