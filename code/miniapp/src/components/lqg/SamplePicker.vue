<script setup lang="ts">
import { ref } from 'vue'
import type { SampleRow } from '@/api/sample'
import { fetchIntValidSamples } from '@/api/embed'
// ★ 显式 .vue 路径导入（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失）
import WdInput from 'wot-design-uni/components/wd-input/wd-input.vue'
import WdPopup from 'wot-design-uni/components/wd-popup/wd-popup.vue'

// 内部「选择样本」（UI:mp.embed.form：搜内部编号，**只列已核验有效的样本**）。
//
// ★ 数据源 = `/mp/int/sample/list?verifyStatus=valid&keyword=`（内部编号等值 / 来源单位模糊）
//   —— 「只列有效样本」这条口径钉在后端那个筛选参数上，前端不做二次判断（不做第二份判据）。
// ★ 底部弹层的形态照落地规范 §5.4（日期与选择走底部弹框）；点一行即选中并关闭。
// ★ 组件本身不认识「选了之后干什么」：只把整条样本行抛给页面（页面自己带出两个工序时间）。
const props = withDefaults(defineProps<{
  /** 只读（页面算好的整页可写性） */
  disabled?: boolean
}>(), {
  disabled: false,
})

const emit = defineEmits<{ (e: 'pick', sample: SampleRow): void }>()

const show = ref(false)
const keyword = ref('')
const rows = ref<SampleRow[]>([])
const loading = ref(false)
const failed = ref(false)

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

async function search() {
  loading.value = true
  failed.value = false
  try {
    const page = await fetchIntValidSamples(keyword.value, 20)
    rows.value = page.rows ?? []
  }
  catch {
    failed.value = true
    rows.value = []
  }
  finally {
    loading.value = false
  }
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
      <view v-else-if="rows.length === 0" class="sp__tip">
        <text class="sp__tip-t">没有已核验有效的样本</text>
      </view>
      <view v-else class="sp__list">
        <view
          v-for="row in rows"
          :key="String(row.id)"
          class="sp__item"
          @click="pick(row)"
        >
          <text class="sp__code lqg-mono">{{ labelOf(row) }}</text>
          <text class="sp__sub">{{ subOf(row) }}</text>
        </view>
      </view>
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
