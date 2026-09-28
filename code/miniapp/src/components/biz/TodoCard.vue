<script setup lang="ts">
import LineIcon from '@/components/ui/LineIcon.vue'
import type { TodoItem } from '@/pages/index/todo'
import { TODO_EMPTY_TEXT } from '@/pages/index/todo'

// 首页「待处理」块（内部人员；甲方 2026-09-24 第 17 行）。
//
// 哑组件：三项的数、弱化、去向都由 `pages/index/todo.ts` 的纯函数算好传进来，本组件只渲染：
// - loading：一行骨架（首次取数时；之后再显示页面时保留上一次的数，不闪）
// - error：一行「待处理没能加载，点一下重试」—— 失败只影响这一块，下面的填写入口照常
// - ready：三项一行一项（标签 · 数 · 右箭头），数为 0 的弱化但不隐藏；三项全 0 → 一行「暂无待处理」
defineProps<{
  state: 'loading' | 'error' | 'ready'
  items: TodoItem[]
  /** 三项都是 0 */
  allClear: boolean
}>()

const emit = defineEmits<{
  (e: 'pick', item: TodoItem): void
  (e: 'retry'): void
}>()
</script>

<template>
  <view class="lqg-sec">
    <text class="lqg-sec__t">待处理</text>
    <text class="lqg-sec__x">点一项去处理</text>
  </view>

  <view class="lqg-card lqg-card--flush todo">
    <view v-if="state === 'loading'" class="todo__line">
      <view class="lqg-skel todo__skel" />
    </view>

    <view v-else-if="state === 'error'" class="todo__line" @click="emit('retry')">
      <text class="todo__hint">待处理没能加载，点一下重试</text>
    </view>

    <view v-else-if="allClear" class="todo__line">
      <text class="todo__hint">{{ TODO_EMPTY_TEXT }}</text>
    </view>

    <template v-else>
      <view
        v-for="(item, index) in items"
        :key="item.key"
        class="todo__row"
        :class="{ 'todo__row--line': index > 0, 'todo__row--muted': item.muted }"
        @click="emit('pick', item)"
      >
        <text class="todo__label">{{ item.label }}</text>
        <text class="todo__count lqg-num">{{ item.count }}</text>
        <view class="todo__arrow">
          <LineIcon name="chevron" :size="16" />
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
/* 左右留屏边距，与下面的宫格对齐（落地规范 §7：屏边距 16） */
.todo {
  margin: 0 var(--lqg-gutter);
}

.todo__row {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
  min-height: 44px;
  padding: 0 var(--lqg-sp-6);
}

.todo__row--line {
  border-top: 1px solid var(--lqg-line);
}

.todo__label {
  flex: 1;
  min-width: 0;
  font-size: var(--lqg-fs-base);
  color: var(--lqg-ink);
}

/* 有待办的数用琥珀（与「待核验」同一语义色）；数为 0 的整行弱化 */
.todo__count {
  font-size: var(--lqg-fs-lg);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-warn);
}

/* 行尾箭头与「我的」各行同一个线性图标 */
.todo__arrow {
  display: flex;
  color: var(--lqg-ink-3);
}

.todo__row--muted .todo__label,
.todo__row--muted .todo__count {
  color: var(--lqg-ink-3);
  font-weight: var(--lqg-fw-regular);
}

.todo__line {
  display: flex;
  align-items: center;
  min-height: 44px;
  padding: 0 var(--lqg-sp-6);
}

.todo__hint {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.todo__skel {
  width: 60%;
  height: 14px;
}
</style>
