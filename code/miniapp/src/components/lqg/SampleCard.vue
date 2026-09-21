<script setup lang="ts">
import StatusChip from '@/components/lqg/StatusChip.vue'

// SampleCard：历史编辑记录里的一行（落地规范 §5.1 卡片 + §5.6 徽标）。
//
// 内容块按 `UI:mp.history` 两帧取（帧画于方向 A 之前，只取内容块与排布）：
//   - 主行：编号类等宽（内部编号 / 送检单号），右侧状态徽标
//   - 次行：摘要（类型 · 单位）· 归属（我 / 同组 某某 / 经手人）· 新增 / 修改 · 日期
// 组件本身不认识「谁是谁」：文案由页面算好传进来（口径都在 `api/sample.ts` 的纯函数里）。
withDefaults(defineProps<{
  /** 等宽显示的编号（内部编号，没有则送检单号） */
  code: string
  /** 第二行摘要 */
  summary?: string
  /** 第三行：归属或经手人 */
  owner?: string
  /** 「新增」/「修改」 */
  action?: string
  /** 日期 */
  date?: string
  /** 核验状态（字典值，交给 StatusChip） */
  status?: string
  /** 状态徽标文案（默认按字典值） */
  statusText?: string
}>(), {
  summary: '',
  owner: '',
  action: '',
  date: '',
  status: '',
  statusText: '',
})
</script>

<template>
  <view class="scard">
    <view class="scard__top">
      <text class="scard__code lqg-mono">{{ code }}</text>
      <StatusChip :value="status" :text="statusText" />
    </view>
    <view v-if="summary" class="scard__mid">
      <text class="scard__sum">{{ summary }}</text>
    </view>
    <view class="scard__bot">
      <text v-if="owner" class="scard__owner">{{ owner }}</text>
      <text v-if="action" class="scard__act">{{ action }}</text>
      <text v-if="date" class="scard__date lqg-num">{{ date }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.scard {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-3);
  padding: var(--lqg-sp-6);
}

.scard__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lqg-sp-4);
}

.scard__code {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.scard__sum {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
}

.scard__bot {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-4);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.scard__owner {
  color: var(--lqg-primary);
}

.scard__date {
  margin-left: auto;
}

.scard__act {
  color: var(--lqg-warn);
}
</style>
