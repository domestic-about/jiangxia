<script setup lang="ts">
import { computed, ref } from 'vue'
import type { EmbedRow, SampleDetail } from '@/api/sample'
import { fetchExtSampleDetail } from '@/api/sample'
import EmbedCard from '@/components/lqg/EmbedCard.vue'
import NoteBar from '@/components/lqg/NoteBar.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import StatusChip from '@/components/lqg/StatusChip.vue'
import { goPage } from '@/router/config'

// 样本详情（外部版）· UI:mp.sample.detail.ext（SAMPLE-MP-001 做第①段，AUTH-EXT-002 接第②段）。
//
// 三段（① 送检信息 ② 石蜡包埋情况 ③ 质控文档）：
//   ② 本票接上：该样本名下每条石蜡包埋记录一张 `EmbedCard`，**含外部提交还没核验的送样**
//      （没编号、标「待核验」或「无效 · 原因」）；没有则「暂无包埋记录」。
//   ③ 的文档在 AUTH-EXT-003，仍是空状态。
//
// ★ 五条硬口径：
//   1. **内部编号一行照接口给的渲染**（CR-20260918-07）：外部接口在开关关着时
//      **根本不给这个键**，页面就不显示这一行；打开后接口给了才显示。
//      前端**不读系统参数、也不写死「永不渲染」** —— 该给不该给是后端的事。
//   2. **冻存信息与核验人全页不出现**：模板里连字段名都没有（不是置灰）。
//      收样段的其余字段同理：外面那个 `detail-ext.vue` 禁字 grep 卡的就是这件事。
//   3. 无效时顶部红条 + 「修改后重新提交」，**仅 `editable=true` 时出现**（进 mode=edit）。
//   4. 可写性以后端详情的 `editable` 为准（同组别人的样本可看不可改）。
//   5. 包埋卡片的内容与禁字口径在 `components/lqg/EmbedCard.vue` 里；本页只负责
//      「有几张、有没有」——`embeds` 空数组与缺键都按「暂无包埋记录」处理。
definePage({
  style: {
    navigationBarTitleText: '样本详情',
  },
})

const detail = ref<SampleDetail | null>(null)
const loading = ref(true)
const failed = ref(false)
const sampleId = ref<string>('')

onLoad((options) => {
  sampleId.value = String(options?.id ?? '')
  load()
})

async function load() {
  if (!sampleId.value) {
    failed.value = true
    loading.value = false
    return
  }
  loading.value = true
  failed.value = false
  try {
    detail.value = await fetchExtSampleDetail(sampleId.value)
  }
  catch {
    failed.value = true
  }
  finally {
    loading.value = false
  }
}

const verifyStatus = computed(() => detail.value?.verifyStatus || '')
const isInvalid = computed(() => verifyStatus.value === 'invalid')
/** 无效原因（红条正文；没给原因时给一句兜底人话） */
const invalidReason = computed(() => detail.value?.invalidReason || '这条记录被判无效，请按核验意见修改后重新提交')
/** 只有后端说可改（本人 + 待核验 / 无效）才出「修改后重新提交」 */
const canResubmit = computed(() => detail.value?.editable === true && (isInvalid.value || verifyStatus.value === 'pending'))
/** 内部编号：接口给了才显示（开关在后端） */
const internalNo = computed(() => (detail.value as Record<string, unknown> | null)?.['internalNo'] as string | undefined)
/**
 * 第②段的石蜡包埋卡片（AUTH-EXT-002）：后端按可见样本集合给全部未删记录，
 * **含外部自己提交还没核验的送样**（`paraffinBlockNo` 空）。空数组 / 缺键都按空处理。
 */
const embeds = computed<EmbedRow[]>(() => detail.value?.embeds ?? [])

function resubmit() {
  goPage(`/pages/sample/form?id=${sampleId.value}&mode=edit`)
}

/** 送检信息三段里这个字段有值才渲染一行（空值不占位置） */
function has(value: unknown): boolean {
  return value !== null && value !== undefined && String(value) !== ''
}

const rows = computed(() => {
  const d = detail.value
  if (!d) {
    return [] as Array<{ label: string, value: string }>
  }
  return [
    { label: '送检单号', value: str(d.submitNo) },
    { label: '供体姓名', value: str(d.donorName) },
    { label: '性别', value: genderText(d.gender) },
    { label: '年龄', value: str(d.age) },
    { label: '住院号', value: str(d.hospitalNo) },
    { label: '组织类型', value: str(d.tissueType) || str(d.organoidType) },
    { label: '有无病理', value: ynText(d.hasPathology) },
    { label: '来源单位', value: str(d.sourceUnitName) },
    { label: '备注', value: str(d.remark) },
  ].filter(r => r.value !== '')
})

function str(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

function genderText(value: unknown): string {
  const map: Record<string, string> = { male: '男', female: '女', unknown: '未知' }
  return map[str(value)] || ''
}

function ynText(value: unknown): string {
  const map: Record<string, string> = { Y: '有', N: '无' }
  return map[str(value)] || ''
}
</script>

<template>
  <view class="det">
    <LoadingState v-if="loading" />

    <ErrorState v-else-if="failed" text="没能加载这条样本" @retry="load" />

    <template v-else-if="detail">
      <!-- 无效：红条 + 修改后重新提交（仅 editable=true） -->
      <NoteBar v-if="isInvalid" tone="danger" :text="invalidReason" />
      <view v-if="canResubmit" class="lqg-bar det__bar">
        <button class="det__btn" @click="resubmit">
          修改后重新提交
        </button>
      </view>

      <!-- ① 送检信息 -->
      <view class="lqg-gl">送检信息</view>
      <view class="lqg-card lqg-card--flush">
        <view class="det__row">
          <text class="det__k">核验状态</text>
          <StatusChip :value="verifyStatus" />
        </view>
        <!-- 内部编号：接口给了才渲染这一行（CR-20260918-07；开关在后端） -->
        <view v-if="has(internalNo)" class="det__row">
          <text class="det__k">内部编号</text>
          <text class="det__v lqg-mono">{{ internalNo }}</text>
        </view>
        <view v-for="row in rows" :key="row.label" class="det__row">
          <text class="det__k">{{ row.label }}</text>
          <text class="det__v">{{ row.value }}</text>
        </view>
      </view>

      <!-- ② 石蜡包埋情况（AUTH-EXT-002）：每条记录一张卡，含还没核验的送样 -->
      <view class="lqg-gl">石蜡包埋情况</view>
      <view class="det__embeds">
        <template v-if="embeds.length">
          <EmbedCard v-for="embed in embeds" :key="String(embed.id)" :embed="embed" />
        </template>
        <view v-else class="lqg-card">
          <text class="det__empty">暂无包埋记录</text>
        </view>
      </view>

      <!-- ③ 质控文档（三份 Word 在 AUTH-EXT-003，本张空状态） -->
      <view class="lqg-gl">质控文档</view>
      <view class="lqg-card">
        <text class="det__empty">结果出具后会显示在这里</text>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.det {
  padding: var(--lqg-sp-5) 0 calc(var(--lqg-sp-7) + env(safe-area-inset-bottom));
}

.det__bar {
  margin-top: var(--lqg-sp-5);
}

/* 第②段：一叠包埋卡片（每条一张），左右留 gutter、卡片之间留间距 */
.det__embeds {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-4);
  padding: 0 var(--lqg-gutter);
}

.det__btn {
  width: 100%;
  height: var(--lqg-btn-h);
  line-height: var(--lqg-btn-h);
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-on-primary);
  background: var(--lqg-primary);
  border: none;
  border-radius: var(--lqg-radius-ctl);
  box-shadow: var(--lqg-shadow-brand);
}

.det__btn::after {
  border: none;
}

.det__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lqg-sp-5);
  min-height: var(--lqg-cell-h);
  padding: var(--lqg-sp-5) var(--lqg-sp-6);
}

.det__k {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
  flex: none;
}

.det__v {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink);
  text-align: right;
}

.det__empty {
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-3);
}
</style>
