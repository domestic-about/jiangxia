<script setup lang="ts">
import { computed, ref } from 'vue'
import type { QcListRow } from '@/api/qc'
import { fetchQcList } from '@/api/qc'
import EmptyState from '@/components/lqg/EmptyState.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import { goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
import { pagingFooterText, usePagedList } from '@/utils/paging'
import { PROGRESS_FILTERS, progressText, QC_TABS, statusTag, statusText } from './status'

// 小程序「填写质控文档」列表（内部人员专用；甲方 2026-09-30：小程序里也能编辑质控文档）。
//
// 与工作台「质控文档」板块同一个接口（GET /lqg/qc/list）：一行一个**已核验有效**的样本 +
// 三份表各自的状态；按关键字、填写进度筛选；点一行进编辑页。
// ★ 只给内部人员：外部深链进来显示一句话、一个请求都不发（接口本身也按权限串 403）。
definePage({
  style: {
    navigationBarTitleText: '填写质控文档',
    enablePullDownRefresh: true,
  },
})

const store = useUserStore()
const isInternal = computed(() => normalizeIdentity(store.identity) === 'internal')

const keyword = ref('')
const progress = ref('')

const pager = usePagedList<QcListRow>({
  fetchPage: (pageNum, pageSize) => fetchQcList({
    keyword: keyword.value.trim() || undefined,
    progress: progress.value || undefined,
    pageNum,
    pageSize,
  }),
  keyOf: row => String(row.sampleId),
})
const { rows, total, loading, failed } = pager
const footerText = computed(() => pagingFooterText({
  loadingMore: pager.loadingMore.value,
  moreFailed: pager.moreFailed.value,
  finished: pager.finished.value,
  total: pager.total.value,
  count: pager.rows.value.length,
}))

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
  if (!isInternal.value) {
    pager.clear()
    return
  }
  await pager.reload()
}

function pickProgress(value: string) {
  if (progress.value === value) {
    return
  }
  progress.value = value
  load()
}

function open(row: QcListRow) {
  goPage(`/pages/qc/edit?sampleId=${row.sampleId}`)
}

function onFooterTap() {
  pager.loadMore()
}

// 从编辑页回来（保存 / 完成并同步过）状态要是新的
onShow(() => {
  load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  pager.loadMore()
})
</script>

<template>
  <view class="qcl">
    <EmptyState v-if="!isInternal" state="empty" text="编辑质控文档只给中心内部人员开放" />

    <template v-else>
      <view class="lqg-card qcl__filter">
        <input
          v-model="keyword"
          class="qcl__search"
          placeholder="搜内部编号或来源单位"
          confirm-type="search"
          @confirm="load"
        >
        <scroll-view class="qcl__scroll" scroll-x :show-scrollbar="false" enhanced>
          <view class="qcl__chips">
            <text
              v-for="item in PROGRESS_FILTERS"
              :key="item.value || 'all'"
              class="lqg-seg__item qcl__chip"
              :class="{ 'lqg-seg__item--on': progress === item.value }"
              @click="pickProgress(item.value)"
            >{{ item.label }}</text>
          </view>
        </scroll-view>
      </view>

      <LoadingState v-if="loading" />

      <ErrorState v-else-if="failed" text="没能加载质控文档列表" @retry="load" />

      <EmptyState v-else-if="rows.length === 0" state="empty" text="没有符合条件的样本（只有已核验有效的样本才有质控文档）" />

      <template v-else>
        <view class="lqg-count">共 {{ total }} 个样本 · 点一个进去填写三份质控表</view>
        <view
          v-for="row in rows"
          :key="String(row.sampleId)"
          class="lqg-card qcl__item"
          @click="open(row)"
        >
          <view class="qcl__head">
            <text class="qcl__no lqg-mono">{{ row.internalNo || '—' }}</text>
            <text class="qcl__progress" :class="`qcl__progress--${row.progress}`">{{ progressText(row.progress, row.publishedCount) }}</text>
          </view>
          <text class="qcl__sub">{{ row.sourceUnitName || '—' }} · {{ row.sampleKind === 'organoid' ? '类器官送样记录' : '样本记录信息表' }} · 收样 {{ row.receiveDate || '—' }}</text>
          <view class="qcl__docs">
            <view v-for="tab in QC_TABS" :key="tab.type" class="qcl__doc">
              <text class="qcl__doc-name">{{ tab.short }}</text>
              <text class="lqg-tag" :class="`lqg-tag--${statusTag(tab.type === 'sample-qc' ? row.sampleQcStatus : tab.type === 'organoid-qc' ? row.organoidQcStatus : row.scoreStatus)}`">
                {{ statusText(tab.type === 'sample-qc' ? row.sampleQcStatus : tab.type === 'organoid-qc' ? row.organoidQcStatus : row.scoreStatus) }}
              </text>
            </view>
          </view>
        </view>

        <view class="qcl__more" @click="onFooterTap">
          <text class="qcl__more-t">{{ footerText }}</text>
        </view>
      </template>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.qcl {
  padding: var(--lqg-sp-5) 0 var(--lqg-sp-8);
}

.qcl__filter {
  margin: 0 var(--lqg-gutter);
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-4);
}

.qcl__search {
  height: 40px;
  padding: 0 var(--lqg-sp-5);
  border-radius: var(--lqg-radius-ctl);
  background: var(--lqg-bg);
  font-size: var(--lqg-fs-body);
}

.qcl__scroll {
  width: 100%;
  white-space: nowrap;
}

.qcl__chips {
  display: inline-flex;
  gap: var(--lqg-sp-2);
}

.qcl__chip {
  flex: none;
}

.qcl__item {
  margin: 0 var(--lqg-gutter) var(--lqg-sp-4);
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-3);
}

.qcl__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lqg-sp-3);
}

.qcl__no {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.qcl__progress {
  flex: none;
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-2);
}

.qcl__progress--done {
  color: var(--lqg-ok);
  font-weight: var(--lqg-fw-semibold);
}

.qcl__progress--none {
  color: var(--lqg-ink-3);
}

.qcl__sub {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.qcl__docs {
  display: flex;
  gap: var(--lqg-sp-3);
}

.qcl__doc {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--lqg-sp-1);
}

.qcl__doc-name {
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-2);
  white-space: nowrap;
}

.lqg-tag--none {
  background: var(--lqg-bg);
  color: var(--lqg-ink-3);
}

.qcl__more {
  padding: var(--lqg-sp-4) 0;
  text-align: center;
}

.qcl__more-t {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}
</style>
