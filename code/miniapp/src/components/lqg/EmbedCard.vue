<script setup lang="ts">
import { computed } from 'vue'
import type { EmbedRow } from '@/api/sample'
import StatusChip from '@/components/lqg/StatusChip.vue'

// EmbedCard：外部样本详情第②段的一张石蜡包埋卡片（`UI:mp.sample.detail.ext`，AUTH-EXT-002）。
//
// 内容块（甲方的口径 + 票面 §2）：编号等宽、「已切片」徽标、工序时间、染色与 marker 徽标、
// **操作人与包埋人**（CR-20260918-07 起放开）；没编号的送样（外部提交还没核验 / 被判无效）
// 显示「待核验」或「无效 · 原因」—— 不能显示成一块空白石蜡。
//
// ★ 三条禁字是硬约束（accept 段 3 直接 grep 这个文件，来源是甲方原话「看不到冻存信息」）：
//   本文件里**不许出现内部编号、核验人、冻存相关的字段名**，注释里也不要写 ——
//   「关着时连键都不出」是后端的事，端侧连这个词都不该有，免得哪天有人顺手把它渲染出来。
//
// ★ `const props =` 不能省（D2 r1 L2 S0-2）：只写 `defineProps…` 时编译器不生成运行时
//   `props` 变量，脚本里引用会抛 ReferenceError → 整张卡片渲染不出来。
const props = defineProps<{ embed: EmbedRow }>()

/** marker 表达的字典 `lqg_marker_expr` 中文（字典值不动，只换显示） */
const EXPR_TEXT: Record<string, string> = {
  negative: '阴性',
  weak: '弱表达',
  strong: '强表达',
}

/** 染色 `lqg_stain_type` 的显示名（OTHER 后面带上具体名称） */
const STAIN_TEXT: Record<string, string> = {
  HE: 'HE',
  IF: 'IF',
  IHC: 'IHC',
  NONE: '无染色',
}

function str(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

const status = computed(() => str(props.embed.verifyStatus))
const blockNo = computed(() => str(props.embed.paraffinBlockNo))
const sectioned = computed(() => props.embed.sectioned === true)
const isInvalid = computed(() => status.value === 'invalid')
const isPending = computed(() => status.value === 'pending')
const reason = computed(() => str(props.embed.invalidReason))

/**
 * 卡片头一行：有编号就是编号（对外的标识就是石蜡块编号，等宽显示）；
 * 没编号的送样说清它走到哪一步 —— 外部提交的送样在核验前本来就没有编号，不许现编。
 */
const heading = computed(() => {
  if (blockNo.value) {
    return blockNo.value
  }
  if (isInvalid.value) {
    return '无效'
  }
  return isPending.value ? '待核验' : '石蜡包埋送样'
})

/** 工序时间（七项，有值才占一行）与样本类型 */
const rows = computed(() => {
  const src = props.embed as unknown as Record<string, unknown>
  const out: Array<{ label: string, value: string }> = []
  const type = [str(src.sampleType), str(src.organoidSourceType)].filter(Boolean).join(' · ')
  if (type) {
    out.push({ label: '样本类型', value: type })
  }
  const times: Array<[string, string]> = [
    ['组织收样', str(src.tissueReceiveTime)],
    ['组织处理', str(src.tissueProcessTime)],
    ['琼脂糖包埋', str(src.agaroseEmbedTime)],
    ['脱水', str(src.dehydrateTime)],
    ['琼脂糖送样', str(src.agaroseSendTime)],
    ['石蜡包埋', str(src.paraffinEmbedTime)],
    ['切片', str(src.sectionTime)],
  ]
  for (const [label, value] of times) {
    if (value) {
      out.push({ label, value })
    }
  }
  return out
})

/** 染色徽标（OTHER 带上具体名称，与后端落库的固定顺序一致） */
const stains = computed(() => {
  const other = str(props.embed.stainOther)
  const list = props.embed.stainTypes ?? []
  return list
    .filter(v => !!v)
    .map((v) => {
      if (v === 'OTHER') {
        return other ? `其他（${other}）` : '其他'
      }
      return STAIN_TEXT[v] || v
    })
})

/** marker 徽标：只有名称与表达（后端给的 markers 就只有这两个键） */
const markers = computed(() => {
  const list = props.embed.markers ?? []
  return list
    .filter(m => str(m?.markerName) || str(m?.expression))
    .map(m => `${str(m?.markerName) || 'marker'}：${EXPR_TEXT[str(m?.expression)] || str(m?.expression) || '—'}`)
})

/** 操作人与包埋人：CR-20260918-07 起对外可见（甲方原话「可以看得到操作人、包埋人」） */
const people = computed(() => {
  const out: string[] = []
  const operator = str(props.embed.operatorName)
  const embedBy = str(props.embed.embedBy)
  if (operator) {
    out.push(`操作人 ${operator}`)
  }
  if (embedBy) {
    out.push(`包埋人 ${embedBy}`)
  }
  return out
})
</script>

<template>
  <view class="ecard lqg-card">
    <view class="ecard__top">
      <text class="ecard__no lqg-mono" :class="{ 'ecard__no--plain': !blockNo }">{{ heading }}</text>
      <view class="ecard__flags">
        <text v-if="sectioned" class="lqg-tag lqg-tag--valid">已切片</text>
        <StatusChip v-if="!blockNo" :value="status" />
      </view>
    </view>

    <!-- 无效：带原因（外部要照它改后重提） -->
    <view v-if="isInvalid && reason" class="ecard__reason">
      <text class="ecard__reason-t">无效 · {{ reason }}</text>
    </view>

    <view v-if="rows.length" class="ecard__rows">
      <view v-for="row in rows" :key="row.label" class="ecard__row">
        <text class="ecard__k">{{ row.label }}</text>
        <text class="ecard__v">{{ row.value }}</text>
      </view>
    </view>

    <view v-if="stains.length || markers.length" class="ecard__badges">
      <text v-for="stain in stains" :key="`s-${stain}`" class="lqg-tag lqg-tag--primary">{{ stain }}</text>
      <text v-for="marker in markers" :key="`m-${marker}`" class="lqg-tag lqg-tag--external">{{ marker }}</text>
    </view>

    <view v-if="people.length" class="ecard__bot">
      <text v-for="person in people" :key="person" class="ecard__person">{{ person }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 色值与圆角一律走 tokens（组件内零色值字面量是硬约束，SYS-MP-001 的 accept 3 守着） */
.ecard {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-4);
  padding: var(--lqg-sp-6);
}

.ecard__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lqg-sp-4);
}

.ecard__no {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

/* 没编号的送样：标题是状态词而不是编号，去掉等宽的「编号感」弱化一档 */
.ecard__no--plain {
  font-family: var(--lqg-font);
  font-weight: var(--lqg-fw-medium);
  color: var(--lqg-ink-2);
}

.ecard__flags {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-3);
  flex: none;
}

.ecard__reason {
  padding: var(--lqg-sp-3) var(--lqg-sp-4);
  background: var(--lqg-danger-soft);
  border-radius: var(--lqg-radius-ctl);
}

.ecard__reason-t {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-danger);
}

.ecard__rows {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-2);
}

.ecard__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lqg-sp-5);
}

.ecard__k {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
  flex: none;
}

.ecard__v {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-2);
  text-align: right;
}

.ecard__badges {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lqg-sp-3);
}

.ecard__bot {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lqg-sp-5);
  padding-top: var(--lqg-sp-3);
  border-top: 1px solid var(--lqg-line);
}

.ecard__person {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}
</style>
