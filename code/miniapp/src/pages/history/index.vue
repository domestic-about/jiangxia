<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import EmptyState from '@/components/lqg/EmptyState.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import SampleCard from '@/components/lqg/SampleCard.vue'
import type { EntryKey } from '@/pages/index/entries'
import { ENTRY_SHORT, entriesFor } from '@/pages/index/entries'
import { goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { pagingFooterText, usePagedList } from '@/utils/paging'
import WdSwitch from 'wot-design-uni/components/wd-switch/wd-switch.vue'
import type { HistoryRaw, HistoryRow } from './sources'
import { UNREGISTERED_TEXT, sourceOf } from './sources'

// 「我的 → 历史编辑记录」· UI:mp.history（SAMPLE-MP-001）。
//
// ★ 页签清单**直接用 `entriesFor(identity)`**（ticket 口径复述 6）：不另写一份，
//   内部四张、外部三张，全部由 SYS-MP-001 那个纯函数给。
// ★ 页签文字用**短名**（`ENTRY_SHORT`：样本记录 / 类器官收样 / 石蜡包埋 / -80 冻存，与内部管理表格页、
//   待核验同一份）：Kevin 2026-09-24 本机验收「顶部 tab 文字不要换行」—— 390 宽下四个全称必折成两行。
// ★ 顶部「只看我提交的」开关**内外部共用同一个**（CR-20260918-07，默认关）：
//   外部打开 → 取数时带 `onlyMine`；内部打开 → 取数时带 `mine=true`。
//   **默认（关）时内部不传 mine = 中心全部内部人员经手的记录**（甲方 9-18 的原话）。
// ★ 查不到数据源的页签显示空状态（四档都已注册，这一支只是兜底）。
// ★ 触底分页（V27）：先取一页，滑到底再取下一页；底部一行写「共 N 条」（N 只认后端 total）。
definePage({
  style: {
    navigationBarTitleText: '历史编辑记录',
  },
})

const store = useUserStore()
const active = ref<EntryKey>('sample')
const onlyMine = ref(false)

const identity = computed(() => store.identity)
/** 页签清单：同一个纯函数，不另写一份 */
const tabs = computed<EntryKey[]>(() => entriesFor(store.identity))

const source = computed(() => sourceOf(active.value))

/** 页签文字：短名（全称并排放不下，见上面的口径） */
function titleOf(key: EntryKey): string {
  return ENTRY_SHORT[key]
}

/** 当前身份（只认 `/mp/me`；缺失 / 不认识 → null，不取数） */
function currentWho(): 'internal' | 'external' | null {
  return store.identity === 'internal' ? 'internal' : store.identity === 'external' ? 'external' : null
}

const pager = usePagedList<HistoryRaw>({
  fetchPage: (pageNum, pageSize) => {
    const src = source.value
    const who = currentWho()
    if (!src || !who) {
      return Promise.resolve({ rows: [], total: 0 })
    }
    return src.fetch(who, onlyMine.value, pageNum, pageSize)
  },
  keyOf: row => String(row.id),
})

const loading = pager.loading
const failed = pager.failed
/** 页面行：取回来的原始行按当前页签的数据源转一遍 */
const rows = computed<HistoryRow[]>(() => {
  const src = source.value
  return src ? pager.rows.value.map(item => src.toRow(item)) : []
})
const footerText = computed(() => pagingFooterText({
  loadingMore: pager.loadingMore.value,
  moreFailed: pager.moreFailed.value,
  finished: pager.finished.value,
  total: pager.total.value,
  count: rows.value.length,
}))

async function load() {
  if (!source.value) {
    pager.clear()
    return
  }
  // ★ 身份的唯一来源是 `/mp/me`：本页可能是冷启动直接进来的（H5 深链 / 小程序分享），
  //   这时 store 里还没有 me —— 必须先把它拉回来，否则 entriesFor(undefined) 出空页签、
  //   取数也没有身份可用（实测踩过：页面渲染出来了，但一个请求都没发）。
  if (!store.me) {
    try {
      await store.loadMe()
    }
    catch {
      pager.clear()
      pager.failed.value = true
      return
    }
  }
  if (!currentWho()) {
    pager.clear()
    return
  }
  await pager.reload()
}

/** 底部那一行：取下一页失败时点它重试 */
function onFooterTap() {
  if (pager.moreFailed.value) {
    pager.loadMore()
  }
}

function pickTab(key: EntryKey) {
  if (active.value === key) {
    return
  }
  active.value = key
  load()
}

function onPickRow(row: HistoryRow) {
  const src = source.value
  if (!src) {
    return
  }
  const who = identity.value === 'internal' ? 'internal' : 'external'
  goPage(src.target(who, row))
}

// 开关一改就重新取数（内外部都是同一个开关）
watch(onlyMine, () => load())

// ★ 两个钩子都要：小程序走 onShow（从详情页返回要刷新），H5 dev（本票的端侧证据）走 onMounted
//（H5 首屏直接进这一页时 onShow 不触发 —— 实测踩过：页面渲染出来了但一个请求都没发）。
// `started` 保证同一屏只取一次数，不因为两个钩子都跑而重复请求。
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

// 滑到底取下一页（取完、正在取、第一页没成功时 loadMore 自己什么都不做）
onReachBottom(() => {
  pager.loadMore()
})
</script>

<template>
  <view class="his">
    <!-- 页签：清单来自 entriesFor(identity) -->
    <view class="lqg-sheets">
      <text
        v-for="tab in tabs"
        :key="tab"
        class="lqg-sheets__item"
        :class="{ 'lqg-sheets__item--on': tab === active }"
        @click="pickTab(tab)"
      >{{ titleOf(tab) }}</text>
    </view>

    <!-- 只看我提交的（默认关；内外部共用同一个开关） -->
    <view class="his__switch">
      <text class="his__switch-t">只看我提交的</text>
      <wd-switch v-model="onlyMine" size="20px" />
    </view>

    <LoadingState v-if="loading" />

    <ErrorState v-else-if="failed" text="没能加载历史编辑记录" @retry="load" />

    <!-- 查不到数据源的页签（兜底） -->
    <EmptyState v-else-if="!source" state="empty" :text="UNREGISTERED_TEXT" />

    <EmptyState v-else-if="rows.length === 0" state="empty" :text="source.emptyText" />

    <view v-else class="lqg-card lqg-card--flush his__list">
      <view
        v-for="(row, index) in rows"
        :key="row.id"
        class="his__item"
        :class="{ 'his__item--line': index > 0 }"
        @click="onPickRow(row)"
      >
        <SampleCard
          :code="row.code"
          :summary="row.summary"
          :owner="row.owner"
          :action="row.action"
          :date="row.date"
          :status="row.status"
          :status-text="row.statusText"
          :reason="row.reason"
        />
      </view>
    </view>

    <!-- 触底分页的底部一行：正在加载 / 失败点这里重试 / 共 N 条（N = 后端 total） -->
    <view v-if="!loading && !failed && rows.length > 0" class="his__more" @click="onFooterTap">
      <text class="his__more-t">{{ footerText }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.his {
  padding: 0 0 calc(var(--lqg-sp-7) + env(safe-area-inset-bottom));
}

.his__switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: var(--lqg-sp-5) var(--lqg-gutter) 0;
  padding: var(--lqg-sp-4) var(--lqg-sp-6);
  background: var(--lqg-card);
  border-radius: var(--lqg-radius-ctl);
  box-shadow: var(--lqg-shadow-sm);
}

.his__switch-t {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink);
}

.his__list {
  margin: var(--lqg-gap) var(--lqg-gutter) 0;
}

.his__item--line {
  border-top: 1px solid var(--lqg-line);
}

.his__more {
  padding: var(--lqg-sp-6) var(--lqg-gutter) 0;
  text-align: center;
}

.his__more-t {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}
</style>
