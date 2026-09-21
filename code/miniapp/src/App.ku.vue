<script setup lang="ts">
// @uni-ku/root 的根组件外壳（插件的约定文件名，删掉构建会报 Could not resolve "./App.ku.vue"）。
// 全局只需要一个 toast / message-box 容器，主题变量走 src/style/tokens.scss（挂在 page 上），
// 所以这里不再套 wd-config-provider。
</script>

<template>
  <KuRootView />
  <wd-toast />
  <wd-message-box />
</template>
