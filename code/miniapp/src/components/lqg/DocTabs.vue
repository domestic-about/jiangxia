<script setup lang="ts">
import { ref, watch } from 'vue'

// 顶部文档切换条（UI:mp.doc.preview / FLOW:F-DOC-02.step2）· DOC-MP-002。
//
// 该样本**已完成**的几份 + ≥2 份时的「合并」；点哪个看哪个。
//
// ★★ 顺序与「要不要出合并」**不在这里判**：那是 DOC-MP-001 的 `groupDocs`
//   （`pages/doc/group.ts`，纯函数 + fixture 单测）——组内固定顺序
//   （样本质控表 → 类器官质控表 → 评分表）、合并入口由「非 merged 份数 ≥ 2」决定。
//   本组件只把 `tabs` 渲染出来、把点击 emit 出去，**一行排序都不写**（口径复述 4：
//   「复用 groupDocs 决定顺序与要不要合并，别另写排序」）。
// ★ 合并件「还在生成」时不禁用它：点进去看到的是「文档生成中」那一屏（而不是点了没反应）。
// ★ 视觉按方向 A：切换条的每一块就是 §5.5 的 SegButtons 块（`.lqg-seg__item`），零色值字面量。
// ★ 一行排开、不折到第二行（Kevin 2026-09-24 本机验收「页签文字不要换行」）：三份全称 +「合并」在 390 宽
//   放不下，这一行自己横向滑动；文字仍是全称（与列表卡片、弹层里的文档名一致）。
//   当前那一块自动滑进可视区（从列表点「合并预览」进来时，「合并」在最右边，不滑就看不见选中了谁）。
const props = withDefaults(defineProps<{
  /** 已由 groupDocs 定好顺序的几份 + 末尾的「合并」（有合并入口时才给） */
  tabs: Array<{ docKind: string, label: string }>
  /** 当前这一份 */
  current: string
  /** 合并件此刻在不在（'generating' 时在「合并」上挂一个小标） */
  mergedState?: 'ready' | 'generating' | 'absent'
}>(), {
  mergedState: 'absent',
})

/**
 * 要滑进可视区的那一块的 id。
 * ★ 等切换条**渲染完**再给（`flush: 'post'`）：scroll-view 在 id 变化的那一刻去找这个节点，
 *   页面先定当前份、后拿到清单时，节点还没画出来就给 id，它找不到、之后也不会再滑（H5 实测）。
 */
const intoView = ref('')
watch(
  () => [props.current, props.tabs.map(tab => tab.docKind).join(',')],
  () => {
    intoView.value = props.tabs.some(tab => tab.docKind === props.current) ? `dt-${props.current}` : ''
  },
  { flush: 'post', immediate: true },
)

const emit = defineEmits<{
  (e: 'change', docKind: string): void
}>()

function pick(docKind: string, current: string) {
  if (docKind !== current) {
    emit('change', docKind)
  }
}
</script>

<template>
  <scroll-view class="dt" scroll-x :show-scrollbar="false" enhanced scroll-with-animation :scroll-into-view="intoView">
    <view class="dt__row">
      <text
        v-for="tab in tabs"
        :id="`dt-${tab.docKind}`"
        :key="tab.docKind"
        class="lqg-seg__item dt__item"
        :class="{ 'lqg-seg__item--on': tab.docKind === current }"
        @click="pick(tab.docKind, current)"
      >{{ tab.label }}<text
        v-if="tab.docKind === 'merged' && mergedState === 'generating'"
        class="dt__badge"
      >生成中</text></text>
    </view>
  </scroll-view>
</template>

<style lang="scss" scoped>
/* scroll-view 自带 width: 100%，再叠左右外边距会比屏宽多出 32px（LedgerTable 踩过），宽度交还给块级默认 */
.dt {
  width: auto;
  margin: 0 var(--lqg-gutter) var(--lqg-gap);
  white-space: nowrap;
}

/* 不挂 `.lqg-seg`（它是 flex-wrap: wrap，会把「合并」折到第二行）：块仍是 `.lqg-seg__item`，间距同 `.lqg-seg` */
.dt__row {
  display: inline-flex;
  gap: var(--lqg-sp-2);
}

.dt__item {
  flex: none;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  gap: var(--lqg-sp-2);
  height: 34px;
}

.dt__badge {
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-warn);
  background: var(--lqg-warn-soft);
  border-radius: var(--lqg-radius-badge);
  padding: 1px var(--lqg-sp-2);
}
</style>
