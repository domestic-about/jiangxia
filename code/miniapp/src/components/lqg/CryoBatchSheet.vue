<script setup lang="ts">
import { computed, ref } from 'vue'
import type { CryoBatchRow } from '@/api/cryo'
import { cryoLocationText, cryoQtyText } from '@/api/cryo'
import ErrorState from '@/components/lqg/ErrorState.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import type { CryoFlowRecord } from '@/pages/cryo/flow'
import {
  editedText,
  fetchCryoFlows,
  flowBalanceText,
  flowDeltaText,
  flowKindText,
  flowOperatorText,
  flowPurposeText,
  flowTimeText,
  isEdited,
} from '@/pages/cryo/flow'
import { goPage } from '@/router/config'
// ★ 一律显式 import wd-* 的 .vue（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失，
//   而页面在真机上就是空白 —— 表格页那边因此也必须用 .vue 路径 import 本组件，
//   不能走桶口 / easycom 标签，见 accept 第 4 条的那段 grep）。
import WdPopup from 'wot-design-uni/components/wd-popup/wd-popup.vue'

// 冻存批次详情弹层（UI:mp.cryo.flow）· CRYO-MP-001。
//
// ★ **只读**（CR-20260917-05）：上部批次摘要，下部取用登记（时间倒序，每行带操作后剩余、
//   改过的标「已改」）。**没有**取走 / 补入 / 转液氮 / 改删登记的按钮 ——
//   `/mp/int/cryo/**` 上根本没有那些接口，不是「这里不放按钮」。
// ★ CR-20260918-07 加的**唯一**一个动作是右上角「修改」：它只做一件事 ——
//   关掉弹层、跳 `pages/cryo/form?id=&mode=edit`（本条记录的填写页修改模式）。弹层本身仍然只读。
// ★ 超期与剩余**照实显示**后端行上的 `overdue` / `overdueDays` / `remainingQty` / `location`，
//   前端不按天数自己算（CRYO-REMIND-001 的唯一判定在后端；转完液氮 / 取空后当场就变）。
const props = defineProps<{
  /** 预留：弹层自身不含身份判断（入口由表格页把住，接口 403 兜底） */
  disabled?: boolean
}>()

const show = ref(false)
const row = ref<CryoBatchRow | null>(null)
const flows = ref<CryoFlowRecord[]>([])
const loading = ref(false)
const failed = ref(false)

function str(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

/** 上部抬头：冻存样品名称（编号类，等宽） */
const name = computed(() => str(row.value?.cryoName).trim() || '—')

/** 超期徽标文案（后端给的 `overdueDays`；没有就不渲染） */
const overdueText = computed(() => {
  if (row.value?.overdue !== true) {
    return ''
  }
  const days = row.value.overdueDays
  return days === null || days === undefined ? '已超期' : `已超 ${days} 天`
})

/** 上部第二行：剩 N / 初始 M 支 · 位置 · 代数 · 冻存时间（UI:mp.cryo.flow） */
const summaryLine = computed(() => {
  const current = row.value
  if (!current) {
    return ''
  }
  return [
    cryoQtyText(current, false),
    cryoLocationText(current),
    str(current.passage),
    str(current.freezeTime) ? `冻存 ${str(current.freezeTime).slice(0, 10)}` : '',
  ].filter(Boolean).join(' · ')
})

/** 这一批的取用登记（只读口；拉不到就显示失败态 + 重试） */
async function loadFlows() {
  const current = row.value
  if (!current || current.id === null || current.id === undefined) {
    flows.value = []
    return
  }
  loading.value = true
  failed.value = false
  try {
    flows.value = await fetchCryoFlows(current.id as string | number)
  }
  catch {
    failed.value = true
    flows.value = []
  }
  finally {
    loading.value = false
  }
}

/**
 * 表格页点一行时调它（持实例调 open —— 弹层开关不靠 prop，wot 1.14 的 popup 没有 `visible`）。
 */
function open(next: CryoBatchRow) {
  if (props.disabled) {
    return
  }
  row.value = next ?? null
  flows.value = []
  show.value = true
  loadFlows()
}

/** 右上角「修改」（CR-20260918-07）：关掉弹层，跳本条记录的填写页修改模式 */
function toEdit() {
  const current = row.value
  if (!current || current.id === null || current.id === undefined) {
    return
  }
  show.value = false
  goPage(`/pages/cryo/form?id=${current.id}&mode=edit`)
}

defineExpose({ open })
</script>

<template>
  <wd-popup
    v-model="show"
    position="bottom"
    custom-style="border-radius: var(--lqg-radius-sheet) var(--lqg-radius-sheet) 0 0"
    @close="show = false"
  >
    <view class="lqg-sheet cbs">
      <view class="lqg-sheet__head">
        <text class="cbs__name lqg-mono">{{ name }}</text>
        <view class="cbs__acts">
          <text v-if="overdueText" class="lqg-tag lqg-tag--overdue cbs__over">{{ overdueText }}</text>
          <text class="cbs__edit" @click="toEdit">修改</text>
        </view>
      </view>

      <text class="cbs__meta">{{ summaryLine }}</text>

      <view class="lqg-gl">取用登记</view>

      <LoadingState v-if="loading" />

      <ErrorState v-else-if="failed" text="没能加载取用登记" @retry="loadFlows" />

      <view v-else-if="flows.length === 0" class="cbs__empty">
        <text class="cbs__empty-t">还没有取用登记</text>
      </view>

      <view v-else class="cbs__list">
        <view v-for="item in flows" :key="String(item.id)" class="cbs__item">
          <view class="cbs__item-l">
            <text class="cbs__when">{{ flowTimeText(item) }}</text>
            <text class="cbs__kind">{{ flowKindText(item) }}</text>
            <text class="cbs__delta lqg-num">{{ flowDeltaText(item) }}</text>
            <text class="cbs__purpose">{{ flowPurposeText(item) }}</text>
            <text class="cbs__op">{{ flowOperatorText(item) }}</text>
            <text v-if="isEdited(item)" class="lqg-tag lqg-tag--ocr cbs__edited">{{ editedText(item) }}</text>
          </view>
          <text class="cbs__bal lqg-num">{{ flowBalanceText(item) }}</text>
        </view>
      </view>

      <text class="cbs__note">取走、补入、转液氮与修改登记请到网页工作台。每一笔都写明操作后还剩几支，改过的标「已改」。</text>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.cbs__name {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.cbs__acts {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
}

.cbs__edit {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-primary);
}

.cbs__meta {
  display: block;
  margin-top: var(--lqg-sp-3);
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.cbs__list {
  overflow: hidden;
  border-radius: var(--lqg-radius-ctl);
  background: var(--lqg-card);
}

.cbs__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lqg-sp-3);
  padding: var(--lqg-sp-4) 0;
  border-bottom: 1px solid var(--lqg-line);
}

.cbs__item-l {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
  flex-wrap: wrap;
  min-width: 0;
}

.cbs__when {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.cbs__kind {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink);
}

.cbs__delta {
  font-size: var(--lqg-fs-body);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.cbs__purpose,
.cbs__op {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-2);
}

.cbs__edited {
  font-size: var(--lqg-fs-xs);
}

.cbs__bal {
  flex: none;
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.cbs__empty {
  padding: var(--lqg-sp-7) 0;
  text-align: center;
}

.cbs__empty-t {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}

.cbs__note {
  display: block;
  margin-top: var(--lqg-sp-5);
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-3);
}
</style>
