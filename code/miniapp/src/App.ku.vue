<script setup lang="ts">
// @uni-ku/root 的根组件外壳（插件的约定文件名，删掉构建会报 Could not resolve "./App.ku.vue"）。
//
// ★ 模板里只放根视图那一个标签，**别再挂 wd-toast / wd-message-box**（V12）：
//   全项目的提示与确认一律走 uni.showToast / uni.showModal，从没调过 useToast / useMessage；
//   而一旦在这里挂了 wd-* 组件，mp-weixin 构建会把本组件的注册代码打进一个
//   「以开发机绝对路径 base64 命名」的共享 chunk，App.ku.js / App.ku.wxss 从包里消失，
//   app.json 里声明的全局组件 global-ku-root 找不到实现，每页报一次组件缺失。
// ★ 注释里**别写根视图标签的原文**：插件用正则把整份文件里**第一处**该标签换成 slot，
//   写进注释就会换错地方，模板里那个原样留下，页面内容投不进来（实测踩过）。
// 主题变量走 src/style/tokens.scss（挂在 page 上），所以也不套 wd-config-provider。
</script>

<template>
  <KuRootView />
</template>
