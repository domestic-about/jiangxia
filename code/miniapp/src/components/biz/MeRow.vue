<script setup lang="ts">
import LineIcon from '@/components/ui/LineIcon.vue'

// 「我的」里的一行（落地规范 §5.3：行首图标井 32×32、圆角 10）。
// 用 `.lqg-card--flush` 包一组的写法在页面里做，这一行只负责内容。
//
// Kevin 2026-09-24 本机验收「我的页面加一些设计感」，整页的行统一成一个样子：
// - 行首：青绿浅底图标井 + 18px 线性图标（`line-icons.ts`，与首页宫格同一套），不再是单字方块；
// - 行高至少 52，标题 15、说明 12；
// - 行尾：线性右箭头（16px、弱色），全页同一个；
// - 分隔线：组内第二行起传 `line`，线从文字起、不从卡片边起（图标井下面不划线），全页同一种。
//   分隔线由本组件自己画，不靠父组件往本组件上挂 class（小程序里挂上去的 class 不一定生效）。
withDefaults(defineProps<{
  /** 行首图标井里的线性图标名 */
  icon: string
  title: string
  desc?: string
  /** 右侧附加内容（如「已核验」徽标）的插槽开关 */
  showArrow?: boolean
  /** 组内第二行起为 true：上方画一条分隔线 */
  line?: boolean
}>(), {
  desc: '',
  showArrow: true,
  line: false,
})
</script>

<template>
  <view class="merow" :class="{ 'merow--line': line }">
    <view class="merow__well">
      <LineIcon :name="icon" :size="18" />
    </view>
    <view class="merow__body">
      <text class="merow__t">{{ title }}</text>
      <text v-if="desc" class="merow__d">{{ desc }}</text>
    </view>
    <view class="merow__end">
      <slot name="end" />
      <view v-if="showArrow" class="merow__arrow">
        <LineIcon name="chevron" :size="16" />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.merow {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-5);
  min-height: var(--lqg-cell-h);
  padding: var(--lqg-sp-5) var(--lqg-sp-6);
}

/* 分隔线从文字起：左 = 行内边距 14 + 图标井 32 + 间距 12 */
.merow--line::before {
  content: '';
  position: absolute;
  top: 0;
  left: 58px;
  right: 0;
  height: 1px;
  background: var(--lqg-line);
}

.merow__well {
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: var(--lqg-radius-field);
  background: var(--lqg-primary-soft);
  color: var(--lqg-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.merow__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.merow__t {
  font-size: var(--lqg-fs-title);
  color: var(--lqg-ink);
}

.merow__d {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.merow__end {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
  flex: none;
}

.merow__arrow {
  display: flex;
  color: var(--lqg-ink-3);
}
</style>
