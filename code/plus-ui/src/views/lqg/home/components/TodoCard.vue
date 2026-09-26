<template>
  <div
    class="lqg-todo-card"
    :class="{ 'is-zero': isZero, 'is-loading': loading }"
    role="button"
    tabindex="0"
    @click="open"
    @keyup.enter="open"
    @keyup.space="open"
  >
    <div class="lqg-todo-card__label">{{ title }}</div>
    <div class="lqg-todo-card__value">
      <!-- ★ 为 0 也照样显示数字 0（accept 2 第 3 段：不许用 v-if 把 0 藏起来） -->
      <span class="lqg-todo-card__num">{{ display }}</span>
      <span v-if="unit" class="lqg-todo-card__unit">{{ unit }}</span>
    </div>
    <div class="lqg-todo-card__hint">{{ isZero ? zeroHint : hint }}</div>
    <div class="lqg-todo-card__go">{{ goText }}</div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import type { RouteLocationRaw } from 'vue-router';

/**
 * 工作台首页的一张待办卡片（UI:admin.home，SYS-HOME-001）。
 *
 * <p>★ <b>为 0 不隐藏</b>：数字为 0 时卡片变灰（`is-zero`）并显示 0，但**不**用 `v-if` 摘掉它
 * —— 实验室的人分不清「今天没有待办」和「这个功能坏了」，而这两件事的处置完全相反。
 * 这正是 accept 2 第 3 段（`! grep -nE 'v-if="…(pendingSamples|…)…> *0"'`）盯的形态。
 *
 * <p>★ 数字是**属性传进来**的（`props.value`），卡片自己不发请求 —— 五张卡片与侧边菜单角标
 * 共用 `store/modules/lqgTodo.ts` 里那一次 `/lqg/home/todo`（ticket §0.1 硬要求 ③）。
 */
const props = withDefaults(
  defineProps<{
    /** 卡片标题（待核验样本 / … ） */
    title: string;
    /** 数字（读时算；0 也要显示） */
    value?: number;
    /** 数字后面的单位（默认「条」） */
    unit?: string;
    /** 有数时的说明 */
    hint?: string;
    /** 为 0 时的说明（默认「现在没有待办」——和「坏了」区分开） */
    zeroHint?: string;
    /** 点击跳哪里（带筛选条件）；不给就不跳路由，改为抛 `open` 事件（例如首页上直接打开一个清单抽屉） */
    to?: RouteLocationRaw;
    /** 右上角那句「去处理」的文案 */
    goText?: string;
    /** 首屏还没拿到数时给个轻提示 */
    loading?: boolean;
  }>(),
  {
    value: 0,
    unit: '条',
    hint: '',
    zeroHint: '',
    goText: '',
    loading: false
  }
);

const emit = defineEmits<{ (e: 'open'): void }>();

const router = useRouter();

const isZero = computed(() => !props.value);
const display = computed(() => (props.value === null || props.value === undefined ? 0 : props.value));

const open = () => {
  if (props.to) {
    router.push(props.to);
  } else {
    emit('open');
  }
};
</script>

<style scoped lang="scss">
.lqg-todo-card {
  position: relative;
  padding: 16px 18px 14px;
  border: 1px solid var(--lqg-line);
  border-left: 4px solid var(--lqg-primary, #0e7c7b);
  border-radius: 8px;
  background: var(--lqg-card);
  cursor: pointer;
  transition: box-shadow 0.15s ease-in-out, transform 0.15s ease-in-out;
  min-height: 132px;

  &:hover {
    box-shadow: 0 2px 12px rgb(0 0 0 / 8%);
    transform: translateY(-1px);
  }

  // ★ 0 = 变灰（不是隐藏）
  &.is-zero {
    border-left-color: var(--lqg-line);
    background: var(--el-fill-color-lighter, #fafafa);

    .lqg-todo-card__num {
      color: var(--lqg-ink-3, #909399);
    }
  }

  &.is-loading {
    opacity: 0.6;
  }

  &__label {
    font-size: 14px;
    color: var(--lqg-ink-2, #606266);
  }

  &__value {
    display: flex;
    align-items: baseline;
    gap: 4px;
    margin: 6px 0 4px;
  }

  &__num {
    font-size: 30px;
    font-weight: 600;
    line-height: 1.1;
    color: var(--lqg-ink, #303133);
  }

  &__unit {
    font-size: 13px;
    color: var(--lqg-ink-3, #909399);
  }

  &__hint {
    font-size: 12px;
    color: var(--lqg-ink-3, #909399);
    min-height: 18px;
  }

  &__go {
    margin-top: 6px;
    font-size: 12px;
    color: var(--lqg-primary, #0e7c7b);
  }
}
</style>
