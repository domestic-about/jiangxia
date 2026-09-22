<script setup lang="ts">
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
// ★ 视觉按方向 A：切换条就是 §5.5 的 SegButtons（`.lqg-seg`），零色值字面量。
withDefaults(defineProps<{
  /** 已由 groupDocs 定好顺序的几份 + 末尾的「合并」（有合并入口时才给） */
  tabs: Array<{ docKind: string, label: string }>
  /** 当前这一份 */
  current: string
  /** 合并件此刻在不在（'generating' 时在「合并」上挂一个小标） */
  mergedState?: 'ready' | 'generating' | 'absent'
}>(), {
  mergedState: 'absent',
})

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
  <view class="lqg-seg dt">
    <text
      v-for="tab in tabs"
      :key="tab.docKind"
      class="lqg-seg__item dt__item"
      :class="{ 'lqg-seg__item--on': tab.docKind === current }"
      @click="pick(tab.docKind, current)"
    >{{ tab.label }}<text
      v-if="tab.docKind === 'merged' && mergedState === 'generating'"
      class="dt__badge"
    >生成中</text></text>
  </view>
</template>

<style lang="scss" scoped>
.dt {
  margin: 0 var(--lqg-gutter) var(--lqg-gap);
}

.dt__item {
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
