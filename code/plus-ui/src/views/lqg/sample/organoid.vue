<template>
  <SampleIndex kind="organoid" />
</template>

<script setup name="LqgSampleOrganoid" lang="ts">
// 工作台「类器官收样记录」（菜单 5220，路由 /sample-organoid，component = lqg/sample/organoid）。
//
// ★ CR-20260924-10（甲方 2026-09-24 第 25 行）：原「样本总表」拆成两页，本页只放类器官收样
//   （sample_kind = organoid）；组织样本在 index.vue（样本记录信息表，/sample）。
//   两页是同一个列表（index.vue），本文件只负责把类别钉死成 organoid。
import SampleIndex from './index.vue';
</script>
