<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { SampleRow } from '@/api/sample'
import { fetchExtMySamplesPage, isPickableExtSample } from '@/api/embed'
import { usePagedList } from '@/utils/paging'
// ★ 显式 .vue 路径导入（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失）
import WdInput from 'wot-design-uni/components/wd-input/wd-input.vue'
import WdPopup from 'wot-design-uni/components/wd-popup/wd-popup.vue'

// 外部「选择样本」（UI:mp.embed.form / FLOW:F-EMBED-01.step6）。
//
// 数据源 = `GET /mp/ext/sample/list?onlyMine=true` + **排除已判无效的**（ticket §2）：
//   · 只列**本人**送检过的（`onlyMine` 由后端按这条样本自己的提交人收窄）；
//   · 待核验的**可以**挂（外部自己刚送检、还没核验的时候就要能送石蜡包埋，后端同口径）；
//   · 显示**送检单号 + 掩码供体姓名**，**不出现内部编号**（REQ-AUTH-013：不想让外部知道内部编号）。
//
// ★ 三条禁字是硬约束（accept 段 3 直接 grep 本文件）：本文件里**不许出现内部编号那个词**，
//   注释里也不要写 —— 外部的 VO 里根本没有这个键，端侧连这个词都不该有。
// ★ 分页（V27）：本人候选先取一页，列表滑到底再取下一页；输入了搜索词就把剩下的页一次取完
//   （搜索只在已取回的候选里按送检单号 / 掩码姓名过滤，不取完会漏掉较早的样本）。
const props = withDefaults(defineProps<{
  /** 只读（页面算好的整页可写性） */
  disabled?: boolean
}>(), {
  disabled: false,
})

const emit = defineEmits<{ (e: 'pick', sample: SampleRow): void }>()

const show = ref(false)
const keyword = ref('')
const loaded = ref(false)

const pager = usePagedList<SampleRow>({
  fetchPage: (pageNum, pageSize) => fetchExtMySamplesPage(pageNum, pageSize),
  keyOf: row => String(row.id),
})
const loading = pager.loading
const failed = pager.failed
/** 已取回的本人候选里能选的（排除已判无效的） */
const all = computed(() => pager.rows.value.filter(isPickableExtSample))

function str(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

/** 选项主行：送检单号（等宽）—— 对外指代一条样本靠它 */
function labelOf(row: SampleRow): string {
  return str(row.submitNo) || '—'
}

/** 选项副行：掩码供体姓名 · 种属 · 组织 / 类器官类型（后端给的就是掩码过的姓名；种属 CR-20261009-18） */
function subOf(row: SampleRow): string {
  const name = str(row.donorNameMasked)
  const kind = str(row.tissueType) || str(row.organoidType)
  return [name, str(row.species), kind].filter(Boolean).join(' · ')
}

/** 前端只在**已拉回来的本人候选**里按送检单号 / 掩码姓名过滤，不另发一次请求 */
const rows = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) {
    return all.value
  }
  return all.value.filter(row => `${labelOf(row)}${subOf(row)}`.includes(kw))
})

async function load() {
  await pager.reload()
  // 第一页恰好全是已判无效的：接着往下取，直到有可选的或取完（免得误显示「没有可送检的样本」）
  while (all.value.length === 0 && !pager.finished.value && !pager.failed.value && !pager.moreFailed.value) {
    await pager.loadMore()
  }
  loaded.value = !pager.failed.value
}

/** 列表滑到底：取下一页 */
function more() {
  pager.loadMore()
}

// 输入了搜索词：把剩下的页一次取完，过滤才覆盖全部本人候选
watch(keyword, (kw) => {
  if (kw.trim() && loaded.value) {
    pager.loadAll()
  }
})

/** 候选多于一屏时把列表装进固定高度的滚动区（弹层不超过屏高三分之二，落地规范 §5.10） */
const scrollable = computed(() => rows.value.length > 6)
/** 列表底部一行：还有下一页 / 正在取 / 取失败时才出 */
const showMore = computed(() => !pager.finished.value || pager.loadingMore.value || pager.moreFailed.value)
const moreText = computed(() => {
  if (pager.loadingMore.value) {
    return '正在加载…'
  }
  return pager.moreFailed.value ? '加载失败，点这里重试' : '上滑加载更多'
})

function open() {
  if (props.disabled) {
    return
  }
  show.value = true
  if (!loaded.value) {
    load()
  }
}

function onKeyword(e: unknown) {
  keyword.value = (e as { detail?: { value?: string } })?.detail?.value ?? ''
}

function pick(row: SampleRow) {
  emit('pick', row)
  show.value = false
}

defineExpose({ open })
</script>

<template>
  <wd-popup
    v-model="show"
    position="bottom"
    custom-style="border-radius: var(--lqg-radius-sheet) var(--lqg-radius-sheet) 0 0"
    @close="show = false"
  >
    <view class="lqg-sheet spx">
      <view class="lqg-sheet__head">
        <text class="spx__t">选择样本</text>
        <text class="spx__x" @click="show = false">关闭</text>
      </view>

      <view class="spx__search">
        <wd-input
          :model-value="keyword"
          placeholder="搜送检单号"
          no-border
          confirm-type="search"
          @update:model-value="onKeyword"
        />
      </view>

      <view v-if="loading" class="spx__tip">
        <text class="spx__tip-t">加载中…</text>
      </view>
      <view v-else-if="failed" class="spx__tip">
        <text class="spx__tip-t">没能加载样本</text>
      </view>
      <view v-else-if="rows.length === 0 && !showMore" class="spx__tip">
        <text class="spx__tip-t">没有可送检的样本</text>
      </view>
      <scroll-view
        v-else
        class="spx__list"
        :class="{ 'spx__list--scroll': scrollable }"
        scroll-y
        @scrolltolower="more"
      >
        <view
          v-for="row in rows"
          :key="String(row.id)"
          class="spx__item"
          @click="pick(row)"
        >
          <text class="spx__code lqg-mono">{{ labelOf(row) }}</text>
          <text class="spx__sub">{{ subOf(row) }}</text>
        </view>
        <view v-if="showMore" class="spx__more" @click="more">
          <text class="spx__tip-t">{{ moreText }}</text>
        </view>
      </scroll-view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.spx__t {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.spx__x {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.spx__search {
  margin-top: var(--lqg-sp-5);
  padding: 0 var(--lqg-sp-4);
  background: var(--lqg-inset);
  border-radius: var(--lqg-radius-ctl);
}

.spx__list {
  margin-top: var(--lqg-sp-4);
  background: var(--lqg-card);
  border-radius: var(--lqg-radius-ctl);
}

/* 候选多时固定高度滚动（scroll-view 纵向滚动要有确定高度） */
.spx__list--scroll {
  height: 50vh;
}

.spx__more {
  padding: var(--lqg-sp-5) 0;
  text-align: center;
}

.spx__item {
  padding: var(--lqg-sp-5) var(--lqg-sp-4);
  border-bottom: 1px solid var(--lqg-line);
}

.spx__code {
  display: block;
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
}

.spx__sub {
  display: block;
  margin-top: 2px;
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.spx__tip {
  padding: var(--lqg-sp-7) 0;
  text-align: center;
}

.spx__tip-t {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}
</style>
