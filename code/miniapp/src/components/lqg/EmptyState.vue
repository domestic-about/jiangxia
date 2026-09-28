<script setup lang="ts">
// EmptyState / LoadingState / ErrorState（落地规范 §5.11）：
// 空态一句人话、不配插画；加载用 `.lqg-skel` 骨架块排出列表形状；失败一句人话加描边「重试」。
// 文案以 ui-index 各页写的为准（历史编辑记录的空态就是「你填过的记录会出现在这里」）。
withDefaults(defineProps<{
  /** empty / loading / error 三支 */
  state: 'loading' | 'empty' | 'error'
  text?: string
}>(), {
  text: '',
})

const emit = defineEmits<{
  (e: 'retry'): void
}>()
</script>

<template>
  <view v-if="state === 'loading'" class="lqg-state">
    <view class="lqg-skel es__bar" />
    <view class="lqg-skel es__bar" />
    <view class="lqg-skel es__bar es__bar--short" />
  </view>

  <view v-else class="lqg-state">
    <text class="lqg-state__text">
      {{ text || (state === 'empty' ? '这里还没有内容' : '没能加载，下拉重试') }}
    </text>
    <button v-if="state === 'error'" class="es__retry" @click="emit('retry')">
      重试
    </button>
  </view>
</template>

<style lang="scss" scoped>
.es__bar {
  width: 100%;
  height: 18px;
}

.es__bar--short {
  width: 60%;
}

.es__retry {
  height: 36px;
  line-height: 36px;
  padding: 0 var(--lqg-sp-8);
  font-size: var(--lqg-fs-body);
  color: var(--lqg-primary);
  background: var(--lqg-card);
  border: 1px solid var(--lqg-primary);
  border-radius: var(--lqg-radius-seg);
}

.es__retry::after {
  border: none;
}
</style>
