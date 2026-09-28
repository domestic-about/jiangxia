<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SampleRow } from '@/api/sample'
import { fetchIntValidSamples } from '@/api/embed'
import { usePagedList } from '@/utils/paging'
// ★ 显式 .vue 路径导入（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失）
import WdInput from 'wot-design-uni/components/wd-input/wd-input.vue'
import WdPopup from 'wot-design-uni/components/wd-popup/wd-popup.vue'

// 内部「选择样本」（UI:mp.embed.form：搜内部编号，**只列已核验有效的样本**）。
//
// ★ 数据源 = `/mp/int/sample/list?verifyStatus=valid&keyword=`（内部编号等值 / 来源单位模糊）
//   —— 「只列有效样本」这条口径钉在后端那个筛选参数上，前端不做二次判断（不做第二份判据）。
// ★ 底部弹层的形态照落地规范 §5.4（日期与选择走底部弹框）；点一行即选中并关闭。
// ★ 组件本身不认识「选了之后干什么」：只把整条样本行抛给页面（页面自己带出两个工序时间）。
// ★ 分页（V27）：候选先取一页，列表滑到底再取下一页；搜索换关键词从第一页重取。
const props = withDefaults(defineProps<{
  /** 只读（页面算好的整页可写性） */
  disabled?: boolean
}>(), {
  disabled: false,
})

const emit = defineEmits<{ (e: 'pick', sample: SampleRow): void }>()

const show = ref(false)
const keyword = ref('')

const pager = usePagedList<SampleRow>({
  fetchPage: (pageNum, pageSize) => fetchIntValidSamples(keyword.value, pageNum, pageSize),
  keyOf: row => String(row.id),
})
const rows = pager.rows
const loading = pager.loading
const failed = pager.failed
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

function str(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

/** 选项主行：内部编号（没有则送检单号）—— 与工作台抽屉的 sampleLabel 同形 */
function labelOf(row: SampleRow): string {
  return [str(row.internalNo), str(row.submitNo)].filter(Boolean).join(' · ') || '—'
}

/** 选项副行：组织 / 类器官类型 · 来源单位 */
function subOf(row: SampleRow): string {
  return [str(row.tissueType) || str(row.organoidType), str(row.sourceUnitName)].filter(Boolean).join(' · ')
}

function search() {
  return pager.reload()
}

/** 列表滑到底：取下一页 */
function more() {
  pager.loadMore()
}

/** 页面点「选择样本」那一格时调它（持实例调 open —— 弹层开关不靠 prop） */
function open() {
  if (props.disabled) {
    return
  }
  show.value = true
  search()
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
    <view class="lqg-sheet sp">
      <view class="lqg-sheet__head">
        <text class="sp__t">选择样本</text>
        <text class="sp__x" @click="show = false">关闭</text>
      </view>

      <view class="lqg-filter__chip sp__search">
        <wd-input
          :model-value="keyword"
          placeholder="搜内部编号"
          no-border
          confirm-type="search"
          @update:model-value="onKeyword"
          @confirm="search"
        />
      </view>

      <view v-if="loading" class="sp__tip">
        <text class="sp__tip-t">加载中…</text>
      </view>
      <view v-else-if="failed" class="sp__tip">
        <text class="sp__tip-t">没能加载样本</text>
      </view>
      <view v-else-if="rows.length === 0 && !showMore" class="sp__tip">
        <text class="sp__tip-t">没有已核验有效的样本</text>
      </view>
      <scroll-view
        v-else
        class="sp__list"
        :class="{ 'sp__list--scroll': scrollable }"
        scroll-y
        @scrolltolower="more"
      >
        <view
          v-for="row in rows"
          :key="String(row.id)"
          class="sp__item"
          @click="pick(row)"
        >
          <text class="sp__code lqg-mono">{{ labelOf(row) }}</text>
          <text class="sp__sub">{{ subOf(row) }}</text>
        </view>
        <view v-if="showMore" class="sp__more" @click="more">
          <text class="sp__tip-t">{{ moreText }}</text>
        </view>
      </scroll-view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.sp__t {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.sp__x {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.sp__search {
  margin-top: var(--lqg-sp-5);
}

.sp__list {
  margin-top: var(--lqg-sp-4);
  background: var(--lqg-card);
  border-radius: var(--lqg-radius-ctl);
}

/* 候选多时固定高度滚动（scroll-view 纵向滚动要有确定高度） */
.sp__list--scroll {
  height: 50vh;
}

.sp__more {
  padding: var(--lqg-sp-5) 0;
  text-align: center;
}

.sp__item {
  padding: var(--lqg-sp-5) var(--lqg-sp-4);
  border-bottom: 1px solid var(--lqg-line);
}

.sp__code {
  display: block;
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
}

.sp__sub {
  display: block;
  margin-top: 2px;
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.sp__tip {
  padding: var(--lqg-sp-7) 0;
  text-align: center;
}

.sp__tip-t {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}
</style>
