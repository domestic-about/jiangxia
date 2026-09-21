<script setup lang="ts">
// 「我的」里的一行（落地规范 §5.3：行首图标井 32×32、圆角 10）。
// 用 `.lqg-card--flush` 包一组的写法在页面里做，这一行只负责内容。
// 右边默认出一个 `›` 箭头（颜色用 `--lqg-ink-3`）。
withDefaults(defineProps<{
  /** 行首图标井里的一个字 */
  mark: string
  title: string
  desc?: string
  /** 右侧附加内容（如「已核验」徽标）的插槽开关 */
  showArrow?: boolean
}>(), {
  desc: '',
  showArrow: true,
})
</script>

<template>
  <view class="merow">
    <view class="merow__well">
      <text class="merow__mark">{{ mark }}</text>
    </view>
    <view class="merow__body">
      <text class="merow__t">{{ title }}</text>
      <text v-if="desc" class="merow__d">{{ desc }}</text>
    </view>
    <view class="merow__end">
      <slot name="end" />
      <text v-if="showArrow" class="merow__arrow">›</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.merow {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-5);
  min-height: var(--lqg-cell-h);
  padding: var(--lqg-sp-5) var(--lqg-sp-6);
}

.merow__well {
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: var(--lqg-radius-field);
  background: var(--lqg-primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}

.merow__mark {
  font-size: var(--lqg-fs-base);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-primary);
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
  font-size: var(--lqg-fs-lg);
  color: var(--lqg-ink-3);
}
</style>
