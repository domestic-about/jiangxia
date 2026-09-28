<template>
  <div class="app-container home">
    <lqg-home />
  </div>
</template>

<script setup lang="ts">
// ============================================================================
// 工作台落地页（SYS-WEB-001 的壳 + SYS-HOME-001 的内容）
//
// UI:admin.home 的五张待办卡片与「最近提交」在 views/lqg/home/index.vue，
// 这里只把它挂到上游的首页路由（constantRoutes 里 path '/index' → views/index.vue）。
//
// ★★ 两条写在 script 里、**不许挪进 template** 的纪律（D1 QA r1 的 S1，见
//    doc/waves/reports/D1-rework-S1.md）：
//   1) 模板**顶层不能有 HTML 注释、也不能有第二个根节点** —— vite dev 会保留模板注释，
//      于是本页根 vnode 变成 Fragment（dev 下 enter 动画静默不播，且历史上会诱发
//      AppMain 的 out-in 卡死白屏）。要写说明就写在这里，或写进那个唯一根元素的**内部**。
//   2) 绝对不要给 AppMain 的 <transition> 加回 mode="out-in"。
//
// 本页刻意**不发任何请求**：数字与最近提交都在 LqgHome 里，数字取自
// store/modules/lqgTodo.ts 的那一次 /lqg/home/todo（与侧边菜单角标同源）。
// ============================================================================
import LqgHome from '@/views/lqg/home/index.vue';
</script>

<style lang="scss" scoped>
.home {
  :deep(.el-card) {
    border: 1px solid var(--lqg-line);
    background: var(--lqg-card);
    border-radius: 8px;
  }
}
</style>
