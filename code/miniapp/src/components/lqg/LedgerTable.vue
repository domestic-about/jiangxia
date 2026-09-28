<script setup lang="ts">
// 内部管理表格页的**哑组件**（UI:mp.ledger ③ / 落地规范 §5.9）。
//
// ★ 它不认识任何业务字段、也**不知道任何列名**：表头文案由页面从 `pages/ledger/columns.ts`
//   传进来，单元格文案由页面预先算成矩阵。所以「页面里手写列清单」这条 counterfeit
//   在这两个文件里都无从发生（accept 第 2 条的禁字 grep 断的就是它）。
// ★ props 全部是可序列化的纯数据（字符串 / 数组），没有函数 prop —— 小程序端渲染层
//   拿不到函数，函数 prop 是「H5 看着对、真机一片空白」的经典来源。
// ★ 第一列冻结：`position: sticky; left: 0`（`.lqg-ledger__fz`，来自 components.scss）；
//   表头在纵向也尽量吸顶（`sticky; top: 0`）。页面本身不横滚，只有这张表横滚。
// ★ 列宽只在这里的 CSS 里（冻结 118 / 其余 120），整表宽度由页面算好传进来
//   （不依赖 `width: max-content`，小程序 WebView 上不稳）。
//
// 一行 = `{id, tone, frozen, sub, cells[]}`：cells 的顺序与 `columnLabels` 一致。
// 形状与 `pages/ledger/sheets.ts` 的 `LedgerTableRow` 逐字段同形，靠 TypeScript 的
// 结构化匹配对接（组件因此不 import 任何页面模块；`<script setup>` 里也不允许 export 类型）。
interface LedgerTableRow {
  id: string
  tone: string
  frozen: string
  sub: string
  cells: string[]
}

defineProps<{
  frozenLabel: string
  columnLabels: string[]
  rows: LedgerTableRow[]
  /** 整表宽度（含单位，如 `1798px`），由页面按列数算好 */
  tableWidth: string
}>()

const emit = defineEmits<{ (e: 'row-tap', row: LedgerTableRow): void }>()
</script>

<template>
  <scroll-view class="lqg-ledger ledger" scroll-x>
    <view class="ledger__head" :style="{ width: tableWidth }">
      <view class="lqg-ledger__th lqg-ledger__fz ledger__fz ledger__cell">
        {{ frozenLabel }}
      </view>
      <view
        v-for="(label, index) in columnLabels"
        :key="index"
        class="lqg-ledger__th ledger__cell ledger__col"
      >
        {{ label }}
      </view>
    </view>

    <view
      v-for="row in rows"
      :key="row.id"
      class="ledger__row"
      :class="row.tone ? `lqg-ledger__row--${row.tone}` : ''"
      :style="{ width: tableWidth }"
      @click="emit('row-tap', row)"
    >
      <view class="lqg-ledger__td lqg-ledger__fz ledger__fz ledger__cell">
        <text class="ledger__fz-main lqg-mono">{{ row.frozen }}</text>
        <text class="lqg-ledger__fz-sub">{{ row.sub }}</text>
      </view>
      <view
        v-for="(cell, index) in row.cells"
        :key="index"
        class="lqg-ledger__td ledger__cell ledger__col"
      >
        {{ cell }}
      </view>
    </view>
  </scroll-view>
</template>

<style lang="scss" scoped>
.ledger {
  overflow: hidden;
  /* scroll-view 自带 width: 100%，再叠上 .lqg-ledger 左右各 16 的外边距，整页就比屏宽多出 16px、
     能被横向拖动（390 宽实测 scrollWidth 406）。宽度交还给块级默认的「撑满减外边距」。 */
  width: auto;
}

.ledger__head {
  display: flex;
  position: sticky;
  top: 0;
  z-index: 2;
}

.ledger__row {
  display: flex;
}

/* 列宽固定：冻结 118（与 .lqg-ledger__fz 一致）、其余 120；列多靠横滑，不靠换行 */
.ledger__fz {
  flex: none;
  width: 118px;
}

.ledger__col {
  flex: none;
  width: 120px;
}

/* 表头与单元格：不换行 + 超长省略 */
.ledger__cell {
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 表头例外：**折行不省略**（2026-09-24 甲方要看清「-80度超低温冰箱转移至液氮时间」这一列；
   列宽 120 只放得下八九个字，省略成「-80度超低温冰…」就看不出是哪一列）。
   整行表头随最长那一格一起变高（flex 默认拉伸），各格底色连成一片。 */
.ledger__head .ledger__cell {
  white-space: normal;
  word-break: break-all;
  line-height: 1.35;
}

.ledger__fz-main {
  display: block;
  font-size: var(--lqg-fs-base);
  font-weight: var(--lqg-fw-semibold);
}

/* 超期行冻结格第二行小字（「已超 N 天」）红字加粗 —— 与待核验行的琥珀小字同一个写法。
   这一条原先加在 src/style/components.scss 里，让那份文件比设计权威
   doc/design-options/direction-a/components.scss 多出一行；挪到组件自己的样式里，两份范式文件逐字一致。 */
.lqg-ledger__row--overdue .lqg-ledger__fz-sub {
  color: var(--lqg-danger);
  font-weight: var(--lqg-fw-semibold);
}

/* 冻存已取空的行（2026-09-24 甲方「支数取空的要提示」）：冻结格小字「已取空 · 初始 N 支」琥珀加粗 */
.lqg-ledger__row--emptied .lqg-ledger__fz-sub {
  color: var(--lqg-warn);
  font-weight: var(--lqg-fw-semibold);
}
</style>
