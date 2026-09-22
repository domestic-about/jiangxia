<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import EmptyState from '@/components/lqg/EmptyState.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import SampleCard from '@/components/lqg/SampleCard.vue'
import type { EntryKey } from '@/pages/index/entries'
import { ENTRY_TITLE, entriesFor } from '@/pages/index/entries'
import { goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import WdSwitch from 'wot-design-uni/components/wd-switch/wd-switch.vue'
import type { HistoryRow } from './sources'
import { UNREGISTERED_TEXT, sourceOf } from './sources'

// 「我的 → 历史编辑记录」· UI:mp.history（SAMPLE-MP-001）。
//
// ★ 页签清单**直接用 `entriesFor(identity)`**（ticket 口径复述 6）：不另写一份，
//   内部四张、外部三张，全部由 SYS-MP-001 那个纯函数给。
// ★ 顶部「只看我提交的」开关**内外部共用同一个**（CR-20260918-07，默认关）：
//   外部打开 → 取数时带 `onlyMine`；内部打开 → 取数时带 `mine=true`。
//   **默认（关）时内部不传 mine = 中心全部内部人员经手的记录**（甲方 9-18 的原话）。
// ★ 还没注册数据源的页签显示空状态（本张只有样本记录这一档）。
definePage({
  style: {
    navigationBarTitleText: '历史编辑记录',
  },
})

const store = useUserStore()
const active = ref<EntryKey>('sample')
const onlyMine = ref(false)
const rows = ref<HistoryRow[]>([])
const loading = ref(false)
const failed = ref(false)

const identity = computed(() => store.identity)
/** 页签清单：同一个纯函数，不另写一份 */
const tabs = computed<EntryKey[]>(() => entriesFor(store.identity))

const source = computed(() => sourceOf(active.value))

function titleOf(key: EntryKey): string {
  return ENTRY_TITLE[key]
}

async function load() {
  const src = source.value
  if (!src) {
    rows.value = []
    return
  }
  loading.value = true
  failed.value = false
  try {
    // ★ 身份的唯一来源是 `/mp/me`：本页可能是冷启动直接进来的（H5 深链 / 小程序分享），
    //   这时 store 里还没有 me —— 必须先把它拉回来，否则 entriesFor(undefined) 出空页签、
    //   取数也没有身份可用（实测踩过：页面渲染出来了，但一个请求都没发）。
    if (!store.me) {
      await store.loadMe()
    }
    const who = store.identity === 'internal' ? 'internal' : store.identity === 'external' ? 'external' : null
    if (!who) {
      rows.value = []
      return
    }
    const list = await src.fetch(who, onlyMine.value)
    rows.value = list.map(item => src.toRow(item))
  }
  catch {
    failed.value = true
    rows.value = []
  }
  finally {
    loading.value = false
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

    <!-- 这一档还没有数据源（后续 ticket 注册） -->
    <EmptyState v-else-if="!source" :text="UNREGISTERED_TEXT" />

    <EmptyState v-else-if="rows.length === 0" :text="source.emptyText" />

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
</style>
