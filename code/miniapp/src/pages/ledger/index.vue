<script setup lang="ts">
import { computed, ref } from 'vue'
import { cryoViewOfTab } from '@/api/cryo'
import type { LedgerFilters, LedgerRow } from '@/api/ledger'
import { emptyFilters } from '@/api/ledger'
import { VERIFIED_EVENT } from '@/api/verify'
import CryoBatchSheet from '@/components/lqg/CryoBatchSheet.vue'
import EmptyState from '@/components/lqg/EmptyState.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import LedgerTable from '@/components/lqg/LedgerTable.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import { goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
import { downloadToTemp, openFile, shareFile } from '@/utils/fileHandoff'
import { pagingFooterText, usePagedList } from '@/utils/paging'
import { openDocumentType } from '@/pages/doc/download'
import { authHeader, exportFileName, exportUrl, isExportSheet } from './export'
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
//      ★ 合作单位送来、待核验的那一条点进去是**核验页**（甲方 2026-09-24 第 20 行，去向也在 `target` 里）；
//      核验完回到本页时重新取数（听 `VERIFIED_EVENT`），那一行不再是浅黄的待核验。
//      ★ 例外：**-80 冻存那一档点一行打开批次详情弹层**（`CryoBatchSheet`，UI:mp.cryo.flow；
//      2026-09-24 起弹层里可取走 / 补入 / 转液氮 / 改删登记），要改记录本身走弹层右上角「修改」。
//   4. **页签 / 筛选项上的数字只认接口给的 `tabCounts`**：不拿当前页 rows 去数
//      （冻存那三个页签切到「超期」只剩 2 行时，数字仍是整表的 7 / 2 / 2）。
//   5. **触底分页**（V27）：先取一页，滑到底再取下一页；「共 N 条」的 N 只认接口的 `total`
//      （以前写死取 100 行，却照 total 写「共 N 条」，第 101 行以后永远看不到）。
//
// 页底小字（UI:mp.ledger）—— 表格页已有修改入口，这行里**没有「修改」二字**（CR-20260918-07）；
// 甲方 2026-09-24 第 20 行起核验、冻存登记小程序里也能做，不再把人支到网页工作台。
const INTERNAL_ADMIN_NOTE = '核验、冻存登记在小程序和网页工作台都能做'

definePage({
  style: {
    navigationBarTitleText: '内部管理',
  },
})

const store = useUserStore()

const sheet = ref<LedgerSheet>(sheetOf(undefined))
const filters = ref<LedgerFilters>(emptyFilters())
/** 接口顶层的页签计数（整表口径）；只有冻存那张会给，别的表保持 null */
const tabCounts = ref<Record<string, number> | null>(null)

const pager = usePagedList<LedgerRow>({
  fetchPage: (pageNum, pageSize) => sheet.value.fetch(filters.value, pageNum, pageSize),
  keyOf: row => String(row.id),
  // ★ 页签数字只认**第一页**响应顶层的 `tabCounts`（整表口径）；这张表不给就清成 null，
  //   绝不退回 `rows.length`（那正是 ticket 的 counterfeit 抓的形态）
  onPage: (page, first) => {
    if (first) {
      tabCounts.value = (page as { tabCounts?: Record<string, number> | null }).tabCounts ?? null
    }
  },
})
const rows = pager.rows
/** 「共 N 条」：接口的 total（整表口径），不是已经加载了几行 */
const total = pager.total
const loading = pager.loading
const failed = pager.failed
const footerText = computed(() => pagingFooterText({
  loadingMore: pager.loadingMore.value,
  moreFailed: pager.moreFailed.value,
  finished: pager.finished.value,
  total: pager.total.value,
  count: rows.value.length,
}))
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
  // ★ 身份的唯一来源是 `/mp/me`：本页常常是深链直接进来的（H5 / 分享），
  //   这时 store 里还没有 me，不先拉一次就会「一个请求都不发」。
  if (!store.me) {
    try {
      await store.loadMe()
    }
    catch {
      pager.clear()
      pager.failed.value = true
      tabCounts.value = null
      return
    }
  }
  if (normalizeIdentity(store.identity) !== 'internal') {
    pager.clear()
    tabCounts.value = null
    return
  }
  tabCounts.value = null
  await pager.reload()
}

/** 表格底部那一行：取下一页失败时点它重试 */
function onFooterTap() {
  if (pager.moreFailed.value) {
    pager.loadMore()
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
  // ★ 冻存这一档点一行开的是**批次详情弹层**（UI:mp.cryo.flow），不是填写页：
  //   取用登记在弹层里做；要改记录本身走弹层右上角「修改」（CR-20260918-07）。
  if (sheet.value.key === 'cryo') {
    cryoSheetRef.value?.open(raw)
    return
  }
  goPage(sheet.value.target(raw))
}

// ── ④ 底部「导出 Excel」（SYS-EXPORT-001 / REQ-SYS-017 / FLOW:F-SAMPLE-02.step7）──────
//
// 三步（UI:mp.ledger ④）：按当前筛选导出 → 生成中提示 → 弹出「打开 / 发送到微信」。
// ★ 「临时文件 → 打开 / 发送到微信」是**公共段**（`utils/fileHandoff.ts`），与文档下载同一份；
//   本页与 `DownloadBar.vue` 都不自己写平台调用。
// ★ 地址里的筛选参数与表格页当前筛选逐字一致（`export.ts#exportUrl`，空值不带）——
//   「用户筛了什么就导出什么」；不筛就是全部（不带任何查询串）。

const exporting = ref(false)

/**
 * 把「导出 / 打开 / 发送到微信」这一串的失败统一成一句人话（不显示后端的 msg）。
 *
 * ★ 2026-09-28 改：真机上最常见的失败是**微信后台没把域名加进 downloadFile 合法域名**
 *   （它和 request 合法域名是两张**分开**的白名单），错误文本是
 *   `downloadFile:fail url not in domain list`。这种失败给「请稍后再试」等于把人堵死，
 *   所以单独识别成一句能照着做的提示。
 */
function exportFailed(err?: unknown) {
  const msg = String((err as any)?.message ?? err ?? '')
  if (msg.includes('not in domain list')) {
    uni.showToast({ title: '导出域名未在微信后台配置，请联系管理员', icon: 'none' })
    return
  }
  uni.showToast({ title: '导出失败，请稍后再试', icon: 'none' })
}

/**
 * 「导出 Excel」：下载当前筛选的 xlsx，再让用户选「打开 / 发送到微信」。
 *
 * ★ 下载**必须带鉴权头**（`authHeader()`：Authorization + clientid）—— 导出是**后端域名上的
 *   鉴权端点**（`/mp/int/export/{sheet}`），不像文档下载那样是 OSS 预签名直链。
 *   ★ 「要不要头」在调用点**显式声明**（`requireAuth: true`）：公共段 `downloadToTemp` 不再
 *     把「必须带 Authorization」当成所有调用方的前提（D7 返工单 r1-S1）。
 */
async function exportExcel() {
  if (exporting.value || !isExportSheet(sheet.value.key)) {
    return
  }
  exporting.value = true
  uni.showLoading({ title: '正在导出…', mask: true })
  try {
    const path = await downloadToTemp(exportUrl(sheet.value.key, filters.value), {
      header: authHeader(),
      requireAuth: true,
    })
    uni.hideLoading()
    exporting.value = false
    if (!path) {
      exportFailed()
      return
    }
    await askHandoff(path, exportFileName(sheet.value.key))
  }
  catch (err) {
    uni.hideLoading()
    exporting.value = false
    exportFailed(err)
  }
}

/** 生成中提示之后的那一次选择：打开（微信查看器，右上角可存 / 转发）或发送到微信 */
function askHandoff(path: string, fileName: string) {
  return new Promise<void>((resolve) => {
    uni.showActionSheet({
      itemList: ['打开', '发送到微信'],
      success: async (res) => {
        try {
          if (res.tapIndex === 0) {
            await openFile(path, openDocumentType(fileName))
          }
          else {
            await shareFile(path, fileName)
          }
        }
        catch {
          exportFailed()
        }
        resolve()
      },
      // 用户点空白关掉选择器：不是失败，什么都不做
      fail: () => resolve(),
    })
  })
}

onLoad((options) => {
  sheet.value = sheetOf(options?.sheet)
  const status = String(options?.verifyStatus ?? '')
  if (status) {
    filters.value.verifyStatus = status
  }
  // 冻存那张的页签直达：`?sheet=cryo&tab=overdue|ln2|emptied|all`（首页「-80 超期」跳 tab=overdue）
  if (sheet.value.key === 'cryo') {
    filters.value.cryoView = cryoViewOfTab(options?.tab)
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

// 从本页点进核验页、核验完回来：重新取这张表（那一条已经不是待核验了）
uni.$on(VERIFIED_EVENT, load)
onUnload(() => uni.$off(VERIFIED_EVENT, load))

// 滑到底取下一页（表格只横滑，纵向跟着页面走，所以用页面的触底事件）
onReachBottom(() => {
  pager.loadMore()
})
</script>

<template>
  <view class="ledger-page">
    <!-- 外部身份（深链进来）只给一句话，不画页签 / 筛选 / 列数（UX 测试 MP-16：原来整套内部结构先画出来，下面才写不开放） -->
    <template v-if="!isExternal">
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
    </template>

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

    <!-- 触底分页的底部一行：正在加载 / 失败点这里重试 / 已加载到第几行 -->
    <view v-if="!loading && !failed && !isExternal && rows.length > 0" class="ledger-page__more" @click="onFooterTap">
      <text class="ledger-page__more-t">{{ footerText }}</text>
    </view>

    <!-- 本页底栏比别的页高一截（按钮下面还有一行小字）：垫高一点，最后几行与底部那行字不被底栏盖住 -->
    <view class="lqg-bar-spacer ledger-page__spacer" />

    <!-- ④ 底部只有「导出 Excel」：按当前筛选导出，导出后选「打开 / 发送到微信」
         （SYS-EXPORT-001 点亮；页底小字仍是 CR-20260918-07 的新口径，没有「修改」二字） -->
    <view class="lqg-bar ledger-page__bar">
      <button class="ledger-page__export" :disabled="exporting" @click="exportExcel">
        {{ exporting ? '正在导出…' : '导出 Excel' }}
      </button>
      <text class="ledger-page__note">{{ INTERNAL_ADMIN_NOTE }}</text>
    </view>

    <!-- 冻存那一档点一行打开的批次详情弹层：可取走 / 补入 / 转液氮 / 改删登记，做完表格与页签数字当场刷新 -->
    <CryoBatchSheet ref="cryoSheetRef" @changed="load" />
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

.ledger-page__spacer {
  height: calc(var(--lqg-btn-h) + 56px + env(safe-area-inset-bottom));
}

.ledger-page__more {
  padding: var(--lqg-sp-5) var(--lqg-gutter) 0;
  text-align: center;
}

.ledger-page__more-t {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.ledger-page__note {
  text-align: center;
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-3);
}
</style>
