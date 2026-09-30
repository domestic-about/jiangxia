<script setup lang="ts">
import { computed, ref } from 'vue'
import type { QcDocKind } from '@/api/qc'
import { fetchQcPages, renderQcDoc } from '@/api/qc'
import EmptyState from '@/components/lqg/EmptyState.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import PageImageViewer from '@/components/lqg/PageImageViewer.vue'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
import { QC_TABS } from './status'

// 编辑质控文档时的「预览」：内部版逐页图，**草稿也能看**（与工作台编辑页右侧预览同一个接口）。
//
// 顺序照工作台 PreviewPane：先触发一次渲染（内容没变会命中缓存、立刻 done）→ 回 pending 就轮询页面图，
// 直到 done / failed。已完成的文档在「质控文档」页签里看（pages/doc/preview），这一页只管编辑中的预览。
// ★ 只给内部人员。
definePage({
  style: {
    navigationBarTitleText: '预览',
  },
})

const POLL_INTERVAL_MS = 2000
const POLL_MAX = 45

const store = useUserStore()
const isInternal = computed(() => normalizeIdentity(store.identity) === 'internal')

const sampleId = ref('')
const docKind = ref<QcDocKind>('sample_qc')
const no = ref('')
const status = ref<'loading' | 'pending' | 'done' | 'failed'>('loading')
const errorMsg = ref('')
const pages = ref<Array<{ pageNo?: number | null, url?: string | null }>>([])
let disposed = false

const title = computed(() => QC_TABS.find(t => t.kind === docKind.value)?.label ?? '质控文档')

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

async function run() {
  if (!store.me) {
    try {
      await store.loadMe()
    }
    catch {
      status.value = 'failed'
      errorMsg.value = '没能确认登录身份'
      return
    }
  }
  if (!isInternal.value) {
    return
  }
  status.value = 'loading'
  errorMsg.value = ''
  pages.value = []
  try {
    const res = await renderQcDoc(sampleId.value, docKind.value)
    if (res?.status === 'failed') {
      status.value = 'failed'
      errorMsg.value = res.errorMsg || '生成失败'
      return
    }
    status.value = 'pending'
    for (let i = 0; i < POLL_MAX && !disposed; i++) {
      const p = await fetchQcPages(sampleId.value, docKind.value).catch(() => null)
      if (p?.status === 'done') {
        pages.value = p.pages ?? []
        status.value = 'done'
        return
      }
      if (p?.status === 'failed') {
        status.value = 'failed'
        errorMsg.value = p.errorMsg || '生成失败'
        return
      }
      await sleep(POLL_INTERVAL_MS)
    }
    status.value = 'failed'
    errorMsg.value = '生成时间有点长，请稍后再点一次预览'
  }
  catch (err) {
    status.value = 'failed'
    errorMsg.value = (err as Error)?.message || '生成失败'
  }
}

onLoad((query) => {
  const q = (query ?? {}) as Record<string, string>
  sampleId.value = String(q.sampleId ?? '')
  const kind = QC_TABS.find(t => t.kind === q.docKind)?.kind
  if (kind) {
    docKind.value = kind
  }
  no.value = decodeURIComponent(String(q.no ?? ''))
  uni.setNavigationBarTitle({ title: `预览 · ${title.value}` })
  run()
})

onUnload(() => {
  disposed = true
})
</script>

<template>
  <view class="qcp">
    <EmptyState v-if="!isInternal && status !== 'loading'" state="empty" text="只给中心内部人员开放" />

    <template v-else>
      <view class="qcp__head">
        <text class="qcp__no lqg-mono">{{ no || '—' }}</text>
        <text class="qcp__t">{{ title }} · 内部版预览（草稿也能看；送检方看不到，完成并同步后才看得到）</text>
      </view>

      <view v-if="status === 'loading' || status === 'pending'">
        <LoadingState />
        <text v-if="status === 'pending'" class="qcp__wait">正在生成页面，稍等几秒…</text>
      </view>

      <ErrorState v-else-if="status === 'failed'" :text="`预览没生成出来：${errorMsg}`" @retry="run" />

      <EmptyState v-else-if="pages.length === 0" state="empty" text="这份文档还没有页面" />

      <PageImageViewer v-else :pages="pages" />
    </template>
  </view>
</template>

<style lang="scss" scoped>
.qcp {
  padding: var(--lqg-sp-5) 0 var(--lqg-sp-8);
}

.qcp__head {
  margin: 0 var(--lqg-gutter) var(--lqg-sp-4);
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-2);
}

.qcp__no {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
}

.qcp__t {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.qcp__wait {
  display: block;
  text-align: center;
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}
</style>
