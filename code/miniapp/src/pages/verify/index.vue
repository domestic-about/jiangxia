<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { PendingEmbedRow, PendingSampleRow } from '@/api/verify'
import { VERIFIED_EVENT, fetchPendingEmbeds, fetchPendingSamples } from '@/api/verify'
import EmptyState from '@/components/lqg/EmptyState.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import SampleCard from '@/components/lqg/SampleCard.vue'
import { goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
import { pagingFooterText, usePagedList } from '@/utils/paging'
import type { VerifyCard, VerifyTab } from './tabs'
import {
  VERIFY_EMPTY_TEXT,
  VERIFY_TABS,
  VERIFY_TAB_TITLE,
  embedCardOf,
  sampleCardOf,
  tabOf,
  verifyTarget,
} from './tabs'

// 待核验（内部人员在小程序里核验；甲方 2026-09-24 第 20 行，Kevin 定）。
//
// 入口：首页「待处理」的两个「待核验」项（`?tab=tissue|embed`）。内部管理表格页点一条待核验记录
// 直接进核验页，不经过这里。
// ★ 三个页签：样本记录 / 类器官收样 / 石蜡包埋；页签上的数是各自接口的 `total`（整表口径，
//   与首页、工作台首页同一个「待核验」），不是当前页的行数。
// ★ 列的是全部待核验（不按人筛）：谁核都行，核过的就不在这里了。
// ★ 点一行进核验页；核验完回来（`VERIFIED_EVENT`）重新取列表与三个数。
// ★ 只给内部人员：外部连入口都没有，深链进来显示一句话、一个请求都不发（接口本身也 403）。
definePage({
  style: {
    navigationBarTitleText: '待核验',
  },
})

type PendingRow = PendingSampleRow | PendingEmbedRow

const store = useUserStore()
const active = ref<VerifyTab>('tissue')
/** 三个页签上的数（取不到的那个不显示数） */
const counts = ref<Partial<Record<VerifyTab, number>>>({})
/** 身份拉不到 / 不是内部：整页只给一句话 */
const notInternal = ref(false)

function fetchTab(tab: VerifyTab, pageNum: number, pageSize: number) {
  return tab === 'embed'
    ? fetchPendingEmbeds(pageNum, pageSize)
    : fetchPendingSamples(tab, pageNum, pageSize)
}

const pager = usePagedList<PendingRow>({
  fetchPage: (pageNum, pageSize) => fetchTab(active.value, pageNum, pageSize),
  keyOf: row => String(row.id),
  // 当前页签的数以列表第一页的 total 为准（与列表同一次响应，不会一个是旧的一个是新的）
  onPage: (page, first) => {
    if (first && typeof page.total === 'number') {
      counts.value = { ...counts.value, [active.value]: page.total }
    }
  },
})

const loading = pager.loading
const failed = pager.failed
const cards = computed<VerifyCard[]>(() => pager.rows.value.map(row => (
  active.value === 'embed' ? embedCardOf(row as PendingEmbedRow) : sampleCardOf(row as PendingSampleRow)
)))
const footerText = computed(() => pagingFooterText({
  loadingMore: pager.loadingMore.value,
  moreFailed: pager.moreFailed.value,
  finished: pager.finished.value,
  total: pager.total.value,
  count: cards.value.length,
}))

function tabText(tab: VerifyTab): string {
  const n = counts.value[tab]
  return typeof n === 'number' ? `${VERIFY_TAB_TITLE[tab]} ${n}` : VERIFY_TAB_TITLE[tab]
}

/** 另外两个页签的数：各取一行，只要 total（失败就不显示数，不挡列表） */
async function loadOtherCounts() {
  const others = VERIFY_TABS.filter(tab => tab !== active.value)
  const results = await Promise.allSettled(others.map(tab => fetchTab(tab, 1, 1)))
  const next = { ...counts.value }
  results.forEach((res, index) => {
    const tab = others[index]
    if (res.status === 'fulfilled' && typeof res.value?.total === 'number') {
      next[tab] = res.value.total
    }
    else {
      delete next[tab]
    }
  })
  counts.value = next
}

async function load() {
  // ★ 身份的唯一来源是 `/mp/me`：本页可能是深链直接进来的，这时 store 里还没有 me
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
  notInternal.value = normalizeIdentity(store.identity) !== 'internal'
  if (notInternal.value) {
    pager.clear()
    counts.value = {}
    return
  }
  await Promise.all([pager.reload(), loadOtherCounts()])
}

function pickTab(tab: VerifyTab) {
  if (active.value === tab) {
    return
  }
  active.value = tab
  load()
}

function onPickCard(card: VerifyCard) {
  goPage(verifyTarget(active.value, card.id))
}

/** 底部那一行：取下一页失败时点它重试 */
function onFooterTap() {
  if (pager.moreFailed.value) {
    pager.loadMore()
  }
}

onLoad((options) => {
  active.value = tabOf(options?.tab)
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

// 核验完回来：这一条已经不是待核验了，列表与三个数都重取
uni.$on(VERIFIED_EVENT, load)
onUnload(() => uni.$off(VERIFIED_EVENT, load))

onReachBottom(() => {
  pager.loadMore()
})
</script>

<template>
  <view class="vfy">
    <view class="lqg-sheets">
      <text
        v-for="tab in VERIFY_TABS"
        :key="tab"
        class="lqg-sheets__item"
        :class="{ 'lqg-sheets__item--on': tab === active }"
        @click="pickTab(tab)"
      >{{ tabText(tab) }}</text>
    </view>

    <EmptyState v-if="notInternal" state="empty" text="核验只给内部人员开放" />

    <LoadingState v-else-if="loading" />

    <ErrorState v-else-if="failed" text="没能加载待核验的记录" @retry="load" />

    <EmptyState v-else-if="cards.length === 0" state="empty" :text="VERIFY_EMPTY_TEXT" />

    <template v-else>
      <view class="lqg-count">点一条进去核验：判为有效要填收样信息，判为无效要写原因</view>
      <view class="lqg-card lqg-card--flush vfy__list">
        <view
          v-for="(card, index) in cards"
          :key="card.id"
          class="vfy__item"
          :class="{ 'vfy__item--line': index > 0 }"
          @click="onPickCard(card)"
        >
          <SampleCard
            :code="card.code"
            :summary="card.summary"
            :owner="card.owner"
            :action="card.action"
            :date="card.date"
            status="pending"
          />
        </view>
      </view>

      <view class="vfy__more" @click="onFooterTap">
        <text class="vfy__more-t">{{ footerText }}</text>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.vfy {
  padding: var(--lqg-sp-5) 0 calc(var(--lqg-sp-7) + env(safe-area-inset-bottom));
}

.vfy__list {
  margin: 0 var(--lqg-gutter);
}

.vfy__item--line {
  border-top: 1px solid var(--lqg-line);
}

.vfy__more {
  padding: var(--lqg-sp-6) var(--lqg-gutter) 0;
  text-align: center;
}

.vfy__more-t {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}
</style>
