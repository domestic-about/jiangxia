<script setup lang="ts">
import { computed, ref } from 'vue'
import type { EmbedFormValue } from '@/api/embed'
import { emptyEmbedForm, toEmbedFormValue } from '@/api/embed'
import { VERIFIED_EVENT, fetchVerifyEmbed, verifyEmbed } from '@/api/verify'
import ErrorState from '@/components/lqg/ErrorState.vue'
import FieldRow from '@/components/lqg/FieldRow.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import MarkerRows from '@/components/lqg/MarkerRows.vue'
import NoteBar from '@/components/lqg/NoteBar.vue'
import ReasonSheet from '@/components/lqg/ReasonSheet.vue'
import StainButtons from '@/components/lqg/StainButtons.vue'
import StatusChip from '@/components/lqg/StatusChip.vue'
import { hasOtherStain, toggleStain } from '@/pages/embed/stain'
import { goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
// ★ 显式 .vue 路径导入（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失）
import WdDatetimePicker from 'wot-design-uni/components/wd-datetime-picker/wd-datetime-picker.vue'
import { backAfterVerify, verifyFailed } from './feedback'
import type { EmbedFillKey, VerifyAction } from './rules'
import { EMBED_LABEL, MAX_INVALID_REASON, embedChanges, embedDroppedOnInvalid, embedProblem, embedVerifyBody } from './rules'
import { minuteOf, verifyTarget } from './tabs'

// 石蜡包埋送样核验页（甲方 2026-09-24 第 20 行，Kevin 定：小程序里也能核验）。
//
// 入口：「待核验」列表的石蜡包埋页签点一行；「我的 → 内部管理」石蜡包埋表里点一条待核验送样。
// 与工作台 `EmbedDrawer.vue`（mode=verify）+ `verifyFill.ts` 逐项对齐，后端 `PUT /lqg/embed/{id}/verify` 是权威：
//   ① 合作单位选的样本（只看）与填的两项（样本类型、类器官来源类型，可以直接改）；
//   ② 石蜡块编号：判有效必填（全库唯一，后端判）；
//   ③ 包埋、切片、染色等工序可以顺手补填，随「判为有效并保存」一起保存（`fill`，只带改过的）；
//   ④ 判无效要写原因；改过的工序补填项判无效不保存 —— 原因面板里明说（不静默丢）。
// ★ 所挂样本自己还没核验有效 → 「判为有效并保存」置灰并写明原因，给一个「先去核验样本」的入口
//   （后端也会拒：一块石蜡挂在一个还没有内部编号的样本上，导出的「样本编号」是空的）。
definePage({
  style: {
    navigationBarTitleText: '核验 · 石蜡包埋送样',
  },
})

/** 七个工序时间（模板 C 的那七列）+ 包埋人：顺序同填写页 */
const PROCESS_KEYS: EmbedFillKey[] = [
  'tissueReceiveTime',
  'tissueProcessTime',
  'agaroseEmbedTime',
  'embedBy',
  'dehydrateTime',
  'agaroseSendTime',
  'paraffinEmbedTime',
  'sectionTime',
]

const store = useUserStore()

const embedId = ref('')
const loading = ref(true)
const failed = ref(false)
const saving = ref(false)
const detail = ref<Record<string, unknown> | null>(null)
/** 打开时的表单（「改过没有」拿它比） */
const original = ref<EmbedFormValue>(emptyEmbedForm())
const form = ref<EmbedFormValue>(emptyEmbedForm())
const reasonRef = ref<{ open: () => void, reset: () => void } | null>(null)
const pickerRef = ref<{ open: () => void } | null>(null)
const pickerField = ref<EmbedFillKey>('tissueReceiveTime')
const pickerValue = ref<number>(Date.now())

onLoad((options) => {
  embedId.value = String(options?.id ?? '')
  load()
})

const isInternal = computed(() => normalizeIdentity(store.identity) === 'internal')
const status = computed(() => String(detail.value?.verifyStatus ?? ''))
const isPendingNow = computed(() => status.value === 'pending')
/** 所挂样本是不是已核验有效（判有效的前提） */
const sampleValid = computed(() => detail.value?.sampleVerifyStatus === 'valid')
const sampleId = computed(() => String(detail.value?.sampleId ?? ''))
/** 所挂样本那一格：送检单号 · 内部编号（样本还没核验就写它的状态：待核验 / 无效） */
const sampleLabel = computed(() => {
  const d = detail.value ?? {}
  const no = String(d.submitNo ?? '').trim()
  const internal = String(d.internalNo ?? '').trim()
  const tail = internal || (d.sampleVerifyStatus === 'pending' ? '待核验' : d.sampleVerifyStatus === 'invalid' ? '无效' : '')
  return [no, tail].filter(Boolean).join(' · ')
})
const metaLine = computed(() => {
  const d = detail.value ?? {}
  const who = String(d.handlerName ?? '').trim()
  const when = minuteOf(d.updateTime || d.createTime)
  return [who && `${who} 提交`, when].filter(Boolean).join(' · ')
})
const changedCount = computed(() => embedChanges(original.value, form.value).length)
const dropped = computed(() => embedDroppedOnInvalid(original.value, form.value))
const reasonNotes = computed(() => (dropped.value.length > 0
  ? [`判为无效不会保存你补填的：${dropped.value.join('、')}（这些在核验有效后才补填）`]
  : []))
const showStainOther = computed(() => hasOtherStain(form.value.stainTypes))

async function load() {
  loading.value = true
  failed.value = false
  try {
    if (!store.me) {
      await store.loadMe()
    }
    if (!isInternal.value) {
      return
    }
    if (!embedId.value) {
      failed.value = true
      return
    }
    const data = await fetchVerifyEmbed(embedId.value)
    detail.value = data as Record<string, unknown>
    original.value = toEmbedFormValue(data)
    form.value = toEmbedFormValue(data)
  }
  catch {
    failed.value = true
  }
  finally {
    loading.value = false
  }
}

function fieldValue(key: EmbedFillKey | 'paraffinBlockNo'): string {
  return String((form.value as unknown as Record<string, unknown>)[key] ?? '')
}

function setField(key: EmbedFillKey | 'paraffinBlockNo', value: string) {
  form.value = { ...form.value, [key]: value }
}

function setMarkers(markers: EmbedFormValue['markers']) {
  form.value = { ...form.value, markers }
}

function isDate(key: EmbedFillKey): boolean {
  return key.endsWith('Time')
}

function onToggleStain(value: string) {
  const stainTypes = toggleStain(form.value.stainTypes, value)
  form.value = { ...form.value, stainTypes, stainOther: hasOtherStain(stainTypes) ? form.value.stainOther : '' }
}

function toMs(value: string): number {
  const ms = value ? new Date(`${value}T00:00:00`).getTime() : Date.now()
  return Number.isNaN(ms) ? Date.now() : ms
}

/** 毫秒 → `yyyy-MM-dd`（七个工序时间都是 LocalDate） */
function formatMs(ms: number): string {
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function onPick(key: EmbedFillKey) {
  pickerField.value = key
  pickerValue.value = toMs(fieldValue(key))
  pickerRef.value?.open()
}

function onPicked(event: { value: number | string }) {
  const ms = Number(event.value)
  if (!Number.isNaN(ms)) {
    setField(pickerField.value, formatMs(ms))
  }
}

/** 先去核验所挂样本（样本核验完回来，本页重新取详情就能判有效了） */
function goVerifySample() {
  if (sampleId.value) {
    goPage(verifyTarget('tissue', sampleId.value))
  }
}

async function submit(action: VerifyAction, reason = '') {
  if (saving.value || !isPendingNow.value) {
    return
  }
  const problem = embedProblem(action, form.value, sampleValid.value, reason)
  if (problem) {
    uni.showToast({ title: problem, icon: 'none' })
    return
  }
  saving.value = true
  try {
    await verifyEmbed(embedId.value, embedVerifyBody(action, original.value, form.value, reason))
    reasonRef.value?.reset()
    uni.$emit(VERIFIED_EVENT)
    backAfterVerify(action === 'valid' ? '已判为有效' : '已判为无效')
  }
  catch (e) {
    // 后端原话（石蜡块编号已存在、所挂样本还未核验有效……）弹框给人看，停在本页改
    verifyFailed(e)
  }
  finally {
    saving.value = false
  }
}

function openInvalid() {
  if (!saving.value) {
    reasonRef.value?.open()
  }
}

// 从这里去核验了所挂样本、回来时（样本核验页广播 `VERIFIED_EVENT`）：重新取所挂样本的状态，
// 判有效就能点了；表单上已经填的保留。本页自己提交成功时也会广播，那时正在提交，跳过。
async function refreshSample() {
  if (saving.value || !detail.value || !isPendingNow.value || sampleValid.value) {
    return
  }
  try {
    const data = await fetchVerifyEmbed(embedId.value)
    detail.value = { ...(detail.value ?? {}), sampleVerifyStatus: data.sampleVerifyStatus, internalNo: data.internalNo }
  }
  catch {
    // 取不到就按原样：判有效时后端还会再判一次
  }
}

uni.$on(VERIFIED_EVENT, refreshSample)
onUnload(() => uni.$off(VERIFIED_EVENT, refreshSample))
</script>

<template>
  <view class="ve">
    <LoadingState v-if="loading" />

    <view v-else-if="!isInternal" class="lqg-state">
      <text class="lqg-state__text">核验只给内部人员开放</text>
    </view>

    <ErrorState v-else-if="failed" text="没能加载这条石蜡包埋送样记录" @retry="load" />

    <template v-else>
      <view class="lqg-sec ve__head">
        <view class="ve__id">
          <text class="ve__no lqg-mono">{{ String(detail?.submitNo ?? '—') }}</text>
          <text v-if="metaLine" class="ve__meta">{{ metaLine }}</text>
        </view>
        <StatusChip :value="status" />
      </view>

      <NoteBar
        v-if="!isPendingNow"
        tone="warn"
        text="这条送样已经核验过了；要改判请到网页工作台"
      />

      <template v-else>
        <!-- 所挂样本还没核验有效：判有效置灰，给一个先去核验样本的入口 -->
        <view v-if="!sampleValid" class="lqg-note lqg-note--warn ve__warn" @click="goVerifySample">
          <text class="ve__warn-t">所挂样本还没核验有效，这条送样暂时只能判为无效</text>
          <text class="ve__warn-a">先去核验样本 ›</text>
        </view>
        <NoteBar v-else-if="changedCount > 0" :text="`改了 ${changedCount} 项，判为有效时一起保存`" />

        <!-- ① 送检信息：合作单位选的样本（只看）+ 填的两项（可以直接改） -->
        <view class="lqg-gl">
          <text>送检信息</text>
          <text class="lqg-gl__x">合作单位填的，可以直接改</text>
        </view>
        <view class="ve__group">
          <FieldRow label="所挂样本" readonly mono :model-value="sampleLabel" />
          <FieldRow
            :label="EMBED_LABEL.sampleType"
            :maxlength="50"
            :model-value="fieldValue('sampleType')"
            @update:model-value="(v: string) => setField('sampleType', v)"
          />
          <FieldRow
            :label="EMBED_LABEL.organoidSourceType"
            :maxlength="100"
            :model-value="fieldValue('organoidSourceType')"
            @update:model-value="(v: string) => setField('organoidSourceType', v)"
          />
        </view>

        <!-- ② 判为有效要给石蜡块编号 -->
        <view class="lqg-gl">
          <text>核验</text>
          <text class="lqg-gl__x">判为有效时必填，全库不能重复</text>
        </view>
        <view class="ve__group">
          <FieldRow
            label="石蜡块编号"
            required
            mono
            :maxlength="64"
            placeholder="例如 T-E01-3"
            :model-value="form.paraffinBlockNo"
            @update:model-value="(v: string) => setField('paraffinBlockNo', v)"
          />
        </view>

        <!-- ③ 工序、染色、其他：可以顺手补填，随「判为有效并保存」一起保存 -->
        <view class="lqg-gl">
          <text>工序时间</text>
          <text class="lqg-gl__x">可以顺手补填，判为有效时一起保存</text>
        </view>
        <view class="ve__group">
          <FieldRow
            v-for="key in PROCESS_KEYS"
            :key="key"
            :label="EMBED_LABEL[key]"
            :control="isDate(key) ? 'date' : 'text'"
            :maxlength="isDate(key) ? undefined : 50"
            :model-value="fieldValue(key)"
            @update:model-value="(v: string) => setField(key, v)"
            @pick="onPick(key)"
          />
        </view>

        <view class="lqg-gl">
          <text>染色与 marker</text>
        </view>
        <view class="ve__group">
          <view class="ve__block">
            <text class="ve__block-t">{{ EMBED_LABEL.stainTypes }}</text>
            <StainButtons :model-value="form.stainTypes" @toggle="onToggleStain" />
            <FieldRow
              v-if="showStainOther"
              label="具体名称"
              :maxlength="100"
              placeholder="例如 Masson"
              :model-value="form.stainOther"
              @update:model-value="(v: string) => setField('stainOther', v)"
            />
          </view>
          <view class="ve__block ve__block--line">
            <text class="ve__block-t">{{ EMBED_LABEL.markers }}</text>
            <MarkerRows
              :model-value="form.markers"
              @update:model-value="setMarkers"
            />
          </view>
        </view>

        <view class="lqg-gl">
          <text>其他</text>
        </view>
        <view class="ve__group">
          <FieldRow
            :label="EMBED_LABEL.operatorName"
            :maxlength="50"
            :model-value="form.operatorName"
            @update:model-value="(v: string) => setField('operatorName', v)"
          />
          <FieldRow
            :label="EMBED_LABEL.remark"
            control="textarea"
            :maxlength="500"
            :model-value="form.remark"
            @update:model-value="(v: string) => setField('remark', v)"
          />
        </view>

        <view class="lqg-bar-spacer" />
      </template>
    </template>

    <view v-if="!loading && !failed && isInternal && isPendingNow" class="lqg-bar ve__bar">
      <button class="ve__btn ve__btn--no" :disabled="saving" @click="openInvalid">
        判为无效
      </button>
      <button class="ve__btn ve__btn--ok" :disabled="saving || !sampleValid" @click="submit('valid')">
        {{ saving ? '正在保存…' : '判为有效并保存' }}
      </button>
    </view>

    <ReasonSheet
      ref="reasonRef"
      :busy="saving"
      :maxlength="MAX_INVALID_REASON"
      :notes="reasonNotes"
      @confirm="(reason: string) => submit('invalid', reason)"
    />

    <wd-datetime-picker
      ref="pickerRef"
      v-model="pickerValue"
      type="date"
      title="选择日期"
      @confirm="onPicked"
    >
      <!-- ★ 默认插槽放一个空节点（G12）：否则 wd-datetime-picker 自己渲染一行「值 ›」的 cell -->
      <view />
    </wd-datetime-picker>
  </view>
</template>

<style lang="scss" scoped>
.ve {
  padding: var(--lqg-sp-5) 0 0;
}

.ve__head {
  padding-top: 0;
  align-items: center;
}

.ve__id {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-1);
  min-width: 0;
}

.ve__no {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.ve__meta {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.ve__warn {
  flex-direction: column;
  align-items: flex-start;
  gap: var(--lqg-sp-2);
}

.ve__warn-t {
  font-size: var(--lqg-fs-sm);
}

.ve__warn-a {
  font-size: var(--lqg-fs-sm);
  font-weight: var(--lqg-fw-semibold);
}

.ve__group {
  margin: 0 var(--lqg-gutter);
  overflow: hidden;
  border-radius: var(--lqg-radius-card);
  background: var(--lqg-card);
  box-shadow: var(--lqg-shadow-sm);
}

.ve__block {
  padding: var(--lqg-sp-5) var(--lqg-sp-6);
}

.ve__block--line {
  border-top: 1px solid var(--lqg-line);
}

.ve__block-t {
  display: block;
  margin-bottom: var(--lqg-sp-3);
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
}

.ve__bar {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-5);
}

.ve__btn {
  height: var(--lqg-btn-h);
  line-height: var(--lqg-btn-h);
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  border-radius: var(--lqg-radius-ctl);
}

.ve__btn::after {
  border: none;
}

.ve__btn--no {
  flex: 1;
  color: var(--lqg-danger);
  background: var(--lqg-card);
  border: 1px solid var(--lqg-danger);
}

.ve__btn--ok {
  flex: 2;
  color: var(--lqg-on-primary);
  background: var(--lqg-primary);
  border: none;
  box-shadow: var(--lqg-shadow-brand);
}
</style>
