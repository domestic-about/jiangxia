<script setup lang="ts">
import { computed, ref } from 'vue'
import type { DocListRow } from '@/api/doc'
import { fetchExtDocList, fetchIntDocList } from '@/api/doc'
import DocGroupCard from '@/components/lqg/DocGroupCard.vue'
import EmptyState from '@/components/lqg/EmptyState.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import { DOC_SECTION_NAME, DOC_TAB_NAME } from '@/config/app'
import { groupDocs } from '@/pages/doc/group'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'

// 文档页签（UI:mp.doc.list / FLOW:F-DOC-02.step1 · 方案 A「按样本分组」）。
//
// 口径（票面 §0 最容易做反的四条）：
//   1. 组标题由**接口按身份给**（内部 = 内部编号 + 来源单位；外部 = 送检单号 + 掩码姓名）——
//      本页只渲染 `title` / `subtitle`，**不拼内部编号**（accept 2 的禁字 grep）。
//   2. 分组与排序全在 `groupDocs`（纯函数 + fixture 单测）：组内固定顺序、组间按组内最新完成时间倒序。
//   3. 「合并预览 / 合并下载」只在该组 ≥ 2 份时出现；每份一行右侧有「下载」——
//      本张按钮只上形态，点了提示「即将开放」（弹层与打开在 DOC-MP-002）。
//   4. 合作单位看到的同样是那几份，**前端不按身份再挑文档类型**；身份只决定打哪个清单接口
//      （内部 `/mp/int/doc/list`、外部 `/mp/ext/doc/list`）。
// ★ 页签名与板块名走配置（REQ-DOC-010 是 clarify，改名不发版）：页签在 pages.config.ts 里引
//   `DOC_TAB_NAME`（构建期），页面里用 `DOC_SECTION_NAME` 出板块标题，导航栏标题运行时也取配置。
definePage({
  style: {
    navigationBarTitleText: '文档',
  },
})

const store = useUserStore()

const rows = ref<DocListRow[]>([])
const loading = ref(true)
const failed = ref(false)

/** 时间范围：全部 / 近一周 / 近一月 / 自定义（自定义给两个日期） */
type RangeKey = 'all' | 'week' | 'month' | 'custom'
const range = ref<RangeKey>('all')
const customBegin = ref('')
const customEnd = ref('')

/** 文档类型：不限 / 三种已完成文档（merged 不是「一份文档」，不进筛选条） */
const kind = ref('')
const KIND_FILTERS = [
  { value: '', label: '全部类型' },
  { value: 'sample_qc', label: '样本质控表' },
  { value: 'organoid_qc', label: '类器官质控表' },
  { value: 'organoid_score', label: '类器官质量评分表' },
]
const RANGES: Array<{ value: RangeKey, label: string }> = [
  { value: 'all', label: '全部时间' },
  { value: 'week', label: '近一周' },
  { value: 'month', label: '近一月' },
  { value: 'custom', label: '自定义' },
]

const groups = computed(() => groupDocs(rows.value))

/** 身份的**唯一**来源是 /mp/me（SYS-MP-001）：认不出来就不猜，按外部那条（最窄）打 */
const identity = computed(() => normalizeIdentity(store.identity))

/** `yyyy-MM-dd`（拼给接口的完成时间范围；接口的止端按当天 23:59:59 收口） */
function dayText(offsetDays: number): string {
  const d = new Date()
  d.setDate(d.getDate() - offsetDays)
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

/** 当前筛选 → 接口参数（全部 / 不限时不下发，别用空串把范围收没了） */
function rangeParams(): { publishedBegin?: string, publishedEnd?: string } {
  if (range.value === 'week') {
    return { publishedBegin: dayText(6) }
  }
  if (range.value === 'month') {
    return { publishedBegin: dayText(29) }
  }
  if (range.value === 'custom') {
    return {
      publishedBegin: customBegin.value || undefined,
      publishedEnd: customEnd.value || undefined,
    }
  }
  return {}
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    // 身份还没拉回来时按外部那条打（最窄的那条），拿到 /mp/me 之后再刷一次
    const fetcher = identity.value === 'internal' ? fetchIntDocList : fetchExtDocList
    const res = await fetcher({ docKind: kind.value || undefined, pageSize: 100, ...rangeParams() })
    rows.value = res.rows ?? []
  }
  catch {
    failed.value = true
  }
  finally {
    loading.value = false
  }
}

function pickRange(value: RangeKey) {
  range.value = value
  if (value === 'custom' && !customBegin.value) {
    customBegin.value = dayText(6)
    customEnd.value = dayText(0)
  }
  if (value === 'custom') {
    return // 自定义要等两个日期都选完再拉（不然会连着打两次请求）
  }
  load()
}

function pickKind(value: string) {
  if (kind.value === value) {
    return
  }
  kind.value = value
  load()
}

function pickCustom() {
  load()
}

onShow(async () => {
  uni.setNavigationBarTitle({ title: DOC_TAB_NAME })
  if (!store.me) {
    try {
      await store.loadMe()
    }
    catch {
      // /mp/me 失败也要把清单试一遍：清单接口自己会判身份（外部拿不到内部清单）
    }
  }
  load()
})
</script>

<template>
  <view class="doc">
    <!-- 顶部筛选：时间范围 + 文档类型（UI:mp.doc.list） -->
    <view class="lqg-filter doc__filter">
      <text
        v-for="item in RANGES"
        :key="item.value"
        class="lqg-filter__chip"
        :class="{ 'lqg-filter__chip--on': range === item.value }"
        @click="pickRange(item.value)"
      >{{ item.label }}</text>
    </view>

    <!-- 自定义：两个原生日期选择器（选完才拉） -->
    <view v-if="range === 'custom'" class="doc__custom">
      <picker mode="date" :value="customBegin" @change="(e: any) => { customBegin = e.detail.value }">
        <text class="doc__date">{{ customBegin || '开始日期' }}</text>
      </picker>
      <text class="doc__tilde">~</text>
      <picker mode="date" :value="customEnd" @change="(e: any) => { customEnd = e.detail.value }">
        <text class="doc__date">{{ customEnd || '结束日期' }}</text>
      </picker>
      <text class="doc__ok" @click="pickCustom">确定</text>
    </view>

    <view class="lqg-filter doc__filter">
      <text
        v-for="item in KIND_FILTERS"
        :key="item.value || 'all'"
        class="lqg-filter__chip"
        :class="{ 'lqg-filter__chip--on': kind === item.value }"
        @click="pickKind(item.value)"
      >{{ item.label }}</text>
    </view>

    <view class="lqg-gl">{{ DOC_SECTION_NAME }}</view>

    <LoadingState v-if="loading" />
    <ErrorState v-else-if="failed" text="没能加载文档清单" @retry="load" />
    <template v-else-if="groups.length">
      <DocGroupCard v-for="group in groups" :key="group.sampleId" :group="group" />
    </template>
    <!-- 空状态：甲方口径逐字（UI:mp.doc.list / ticket §2） -->
    <EmptyState v-else state="empty" text="结果出具后会显示在这里" />
  </view>
</template>

<style lang="scss" scoped>
.doc {
  padding: var(--lqg-sp-4) 0 calc(var(--lqg-sp-7) + env(safe-area-inset-bottom));
}

.doc__filter {
  margin-top: 0;
}

.doc__custom {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-4);
  margin: var(--lqg-sp-4) var(--lqg-gutter) 0;
  padding: var(--lqg-sp-4) var(--lqg-sp-5);
  background: var(--lqg-card);
  border-radius: var(--lqg-radius-field);
  box-shadow: var(--lqg-shadow-sm);
}

.doc__date {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink);
}

.doc__tilde {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.doc__ok {
  margin-left: auto;
  padding: 2px var(--lqg-sp-5);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-primary);
  background: var(--lqg-primary-soft);
  border-radius: var(--lqg-radius-seg);
}
</style>
