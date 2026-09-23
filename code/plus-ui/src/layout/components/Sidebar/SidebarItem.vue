<template>
  <div v-if="!item.hidden">
    <template v-if="hasOneShowingChild(item, item.children) && (!onlyOneChild.children || onlyOneChild.noShowingChildren) && !item.alwaysShow">
      <app-link v-if="onlyOneChild.meta" :to="resolvePath(onlyOneChild.path, onlyOneChild.query)">
        <el-menu-item :index="resolvePath(onlyOneChild.path)" :class="{ 'submenu-title-noDropdown': !isNest }">
          <svg-icon :icon-class="onlyOneChild.meta.icon || (item.meta && item.meta.icon)" />
          <template #title>
            <!-- 待办角标（SYS-HOME-001）：数字来自 store/modules/lqgTodo.ts，
                 与工作台首页五张卡片是**同一次请求**的结果；为 0 时 hidden（不显示角标）。 -->
            <el-badge :value="badgeOf(onlyOneChild)" :max="99" :hidden="badgeOf(onlyOneChild) <= 0" class="lqg-menu-badge">
              <span class="menu-title" :title="hasTitle(onlyOneChild.meta.title)">{{ onlyOneChild.meta.title }}</span>
            </el-badge>
          </template>
        </el-menu-item>
      </app-link>
    </template>

    <el-sub-menu v-else ref="subMenu" :index="resolvePath(item.path)" teleported>
      <template v-if="item.meta" #title>
        <svg-icon :icon-class="item.meta ? item.meta.icon : ''" />
        <!-- 父菜单的角标（人员与单位 = 待核验外部用户）：同样读 lqgTodo store，0 不显示 -->
        <el-badge :value="badgeOf(item)" :max="99" :hidden="badgeOf(item) <= 0" class="lqg-menu-badge">
          <span class="menu-title" :title="hasTitle(item.meta?.title)">{{ item.meta?.title }}</span>
        </el-badge>
      </template>

      <sidebar-item
        v-for="(child, index) in item.children"
        :key="child.path + index"
        :is-nest="true"
        :item="child"
        :base-path="resolvePath(child.path)"
        class="nest-menu"
      />
    </el-sub-menu>
  </div>
</template>

<script setup lang="ts">
import { isExternal } from '@/utils/validate';
import AppLink from './Link.vue';
import { getNormalPath } from '@/utils/ruoyi';
import { RouteRecordRaw } from 'vue-router';
import { useLqgTodoStore } from '@/store/modules/lqgTodo';

const props = defineProps({
  item: {
    type: Object as PropType<RouteRecordRaw>,
    required: true
  },
  isNest: {
    type: Boolean,
    default: false
  },
  basePath: {
    type: String,
    default: ''
  }
});

const onlyOneChild = ref<any>({});

const hasOneShowingChild = (parent: RouteRecordRaw, children?: RouteRecordRaw[]) => {
  if (!children) {
    children = [];
  }
  const showingChildren = children.filter((item) => {
    if (item.hidden) {
      return false;
    }
    onlyOneChild.value = item;
    return true;
  });

  // When there is only one child router, the child router is displayed by default
  if (showingChildren.length === 1) {
    return true;
  }

  // Show parent if there are no child router to display
  if (showingChildren.length === 0) {
    onlyOneChild.value = { ...parent, path: '', noShowingChildren: true };
    return true;
  }

  return false;
};

const resolvePath = (routePath: string, routeQuery?: string): any => {
  if (isExternal(routePath)) {
    return routePath;
  }
  if (isExternal(props.basePath as string)) {
    return props.basePath;
  }
  if (routeQuery) {
    const query = JSON.parse(routeQuery);
    return { path: getNormalPath(props.basePath + '/' + routePath), query: query };
  }
  return getNormalPath(props.basePath + '/' + routePath);
};

const hasTitle = (title: string | undefined): string => {
  if (!title || title.length <= 5) {
    return '';
  }
  return title;
};

// ============================================================================
// 待办角标（SYS-HOME-001 · UI:admin.home）
//
// ★★ 侧边栏**不自己请求**接口：数字从 store/modules/lqgTodo.ts 读，与工作台首页的五张卡片
//    是**同一次**请求的结果（ticket §0.1 硬要求 ③）。侧边栏若自己再拉一次，两个请求之间
//    有人核验了一条样本，就会出现「角标 3、进去卡片 2」——老师会怀疑系统。
//    （accept 2 最后两段断的就是这条：Sidebar 里必须出现 lqgTodo，且不许出现接口路径。）
//
// ★ 角标的四个落点：样本总表 / 石蜡包埋 / 冻存管理 / 人员与单位（父菜单）。
//    映射写在 store 的 badgeOf() 里，这里只做「取数」与「为 0 不显示」。
// ============================================================================
const lqgTodoStore = useLqgTodoStore();

/** 按菜单项解析后的路径取角标数字（0 = 这里没有待办 → 模板里 :hidden 掉） */
const badgeOf = (target: any): number => {
  if (!target) {
    return 0;
  }
  const resolved = resolvePath(target.path, target.query);
  return lqgTodoStore.badgeOf(typeof resolved === 'string' ? resolved : resolved?.path);
};

// 侧边栏挂载时补一次（若首页已经拉过就什么都不做 —— 不重复请求）
onMounted(() => {
  lqgTodoStore.ensureLoaded();
});
</script>

<style scoped lang="scss">
// 角标挂在菜单文字上：让 el-badge 的包装层不破坏 el-menu 的省略号布局
.lqg-menu-badge {
  display: inline-flex;
  align-items: center;
  max-width: 100%;

  :deep(.el-badge__content) {
    top: 10px;
    right: 2px;
  }
}
</style>
