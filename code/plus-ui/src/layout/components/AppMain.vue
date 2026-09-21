<template>
  <section class="app-main">
    <router-view v-slot="{ Component, route }">
      <transition :enter-active-class="animate">
        <keep-alive :include="tagsViewStore.cachedViews">
          <component :is="Component" v-if="!route.meta.link" :key="route.path" />
        </keep-alive>
      </transition>
    </router-view>
    <iframe-toggle />
  </section>
</template>

<script setup name="AppMain" lang="ts">
import { useSettingsStore } from '@/store/modules/settings';
import { useTagsViewStore } from '@/store/modules/tagsView';

import IframeToggle from './IframeToggle/index.vue';
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const route = useRoute();
const tagsViewStore = useTagsViewStore();

// 随机动画集合
//
// 【D1 QA r1 返工 · SYS-WEB-001】上游这里写的是
//   <transition :enter-active-class="animate" mode="out-in">
// `mode="out-in"` 要求被切换的组件**恰好是单一元素根**。一旦某个页面的根是 Fragment
// （模板顶层写了注释 —— vite dev 会保留注释，编译成 STABLE_FRAGMENT|DEV_ROOT_FRAGMENT；
//  或者页面干脆写了多个根节点），Vue 会把过渡钩子挂在 Fragment 上，而渲染器卸载
// Fragment 时是逐个 remove 子节点、不会回调 Fragment 上的 afterLeave
// （见 @vue/runtime-core 的 remove()：Fragment + DEV_ROOT_FRAGMENT 分支只 remove 子节点；
//  普通 Fragment 走 removeFragment，同样不看 Fragment 的 transition）。
// 于是 BaseTransition 的 state.isLeaving 永远停在 true，此后每次渲染都只输出 `<!---->`
// —— 工作台整片空白、控制台无任何报错。
// 去掉 `mode="out-in"` 即彻底消除这条路径：out-in 是唯一会设置 isLeaving 的分支。
// 过渡动画语义不变：本组件只给了 enter-active-class，全局也没有定义 .v-leave-* 类
// （grep 过 src/assets/styles 与 element-plus），所以 leave 一侧本来就没有任何动画，
// out-in 的「先等旧页离场」在正常页面下也已经是 0 时长；enter 的 animate.css 动画保留。
const animate = ref<string>('');
const animationEnable = ref(useSettingsStore().animationEnable);
watch(
  () => useSettingsStore().animationEnable,
  (val: boolean) => {
    animationEnable.value = val;
    if (val) {
      animate.value = proxy?.animate.animateList[Math.round(Math.random() * proxy?.animate.animateList.length)] as string;
    } else {
      animate.value = proxy?.animate.defaultAnimate as string;
    }
  },
  { immediate: true }
);

onMounted(() => {
  addIframe();
});

watchEffect(() => {
  addIframe();
});

function addIframe() {
  if (route.meta.link) {
    useTagsViewStore().addIframeView(route);
  }
}
</script>

<style lang="scss" scoped>
.app-main {
  /* 50= navbar  50  */
  min-height: calc(100vh - 50px);
  width: 100%;
  position: relative;
  overflow: hidden;
}

.fixed-header + .app-main {
  padding-top: 50px;
}

.hasTagsView {
  .app-main {
    /* 84 = navbar + tags-view = 50 + 34 */
    min-height: calc(100vh - 84px);
  }

  .fixed-header + .app-main {
    padding-top: 84px;
  }
}
</style>
<style lang="scss">
// fix css style bug in open el-dialog
.el-popup-parent--hidden {
  .fixed-header {
    padding-right: 6px;
  }
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background-color: #f1f1f1;
}

::-webkit-scrollbar-thumb {
  background-color: #c0c0c0;
  border-radius: 3px;
}
</style>
