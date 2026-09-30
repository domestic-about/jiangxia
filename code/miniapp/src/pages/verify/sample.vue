<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SelectorUnit } from '@/api/unit-group'
import { fetchUnits, unitOptionsFor } from '@/api/unit-group'
import { VERIFIED_EVENT, fetchVerifySample, verifySample } from '@/api/verify'
import ErrorState from '@/components/lqg/ErrorState.vue'
import FieldRow from '@/components/lqg/FieldRow.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import NoteBar from '@/components/lqg/NoteBar.vue'
import ReasonSheet from '@/components/lqg/ReasonSheet.vue'
import SegButtons from '@/components/lqg/SegButtons.vue'
import SourceUnitSheet from '@/components/lqg/SourceUnitSheet.vue'
import StatusChip from '@/components/lqg/StatusChip.vue'
import { useUserStore } from '@/store/user'
import { normalizeIdentity } from '@/types/identity'
// ★ 显式 .vue 路径导入（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失）
import WdDatetimePicker from 'wot-design-uni/components/wd-datetime-picker/wd-datetime-picker.vue'
import { backAfterVerify, verifyFailed } from './feedback'
import type { SampleFieldKey, SampleKind, VerifyAction, VerifySampleForm } from './rules'
import {
  MAX_INVALID_REASON,
  RECEIVE_FIELDS,
  SAMPLE_LABEL,
  SUBMIT_FIELDS,
  kindOf,
  sampleControl,
  sampleMaxlength,
  sampleProblem,
  sampleRequired,
  sampleVerifyBody,
  submitChanges,
  toVerifySampleForm,
} from './rules'
import { minuteOf } from './tabs'

// 样本核验页（样本记录信息表 / 类器官收样记录共用；甲方 2026-09-24 第 20 行，Kevin 定：小程序里也能核验）。
//
// 入口：「待核验」列表点一行；「我的 → 内部管理」表格里点一条待核验记录（`sheets.ts` 的 `target`）。
// 与工作台核验抽屉（`SampleDrawer.vue`，mode=verify）逐项对齐，后端 `PUT /lqg/sample/{id}/verify` 是权威：
//   ① 送检信息（合作单位填的）：可以直接改；**改过才随核验一起保存**（整段，见 `rules.ts` 文件头）；
//   ② 收样信息：判有效时收样日期、内部编号必填（内部编号全库唯一，后端判），其余选填；操作人默认当前登录人；
//   ③ 底部「判为无效」「判为有效并保存」：判无效要写原因（合作单位看得到），收样信息不保存。
// 后端的业务拒绝（内部编号已存在……）原样弹框、停在本页让人改；成功后回到来的那一页并让它重新取数。
// 这条记录已经不是待核验（别人刚核过、或深链进来）→ 只说一句「已经核验过了」，不给按钮。
definePage({
  style: {
    navigationBarTitleText: '核验',
  },
})

const store = useUserStore()

const sampleId = ref('')
const loading = ref(true)
const failed = ref(false)
const saving = ref(false)
const detail = ref<Record<string, unknown> | null>(null)
/** 打开时的表单（「改过没有」拿它比） */
const original = ref<VerifySampleForm>(toVerifySampleForm(null))
const form = ref<VerifySampleForm>(toVerifySampleForm(null))
const units = ref<SelectorUnit[]>([])
/** 来源单位是不是手填（没挂单位 id） */
const manualUnit = ref(false)
const unitSheetRef = ref<{ open: () => void } | null>(null)
const reasonRef = ref<{ open: () => void, reset: () => void } | null>(null)
const pickerRef = ref<{ open: () => void } | null>(null)
const pickerField = ref<SampleFieldKey>('receiveDate')
const pickerValue = ref<number>(Date.now())

onLoad((options) => {
  sampleId.value = String(options?.id ?? '')
  load()
})

const isInternal = computed(() => normalizeIdentity(store.identity) === 'internal')
const kind = computed<SampleKind>(() => kindOf(detail.value as { sampleKind?: unknown } | null))
const status = computed(() => String(detail.value?.verifyStatus ?? ''))
const isPendingNow = computed(() => status.value === 'pending')
const kindTitle = computed(() => (kind.value === 'organoid' ? '类器官送样记录' : '样本记录信息表'))
const submitKeys = computed(() => SUBMIT_FIELDS[kind.value])
const receiveKeys = computed(() => RECEIVE_FIELDS[kind.value])
const changed = computed(() => submitChanges(kind.value, original.value, form.value))
/** 顶部一行：送检单号 · 提交人 · 时间 */
const metaLine = computed(() => {
  const d = detail.value ?? {}
  const who = String(d.submitterName ?? '').trim()
  const when = minuteOf(d.updateTime || d.createTime)
  return [who && `${who} 提交`, when].filter(Boolean).join(' · ')
})
const unitOptions = computed<SelectorUnit[]>(() => unitOptionsFor(store.identity, units.value, store.ext))

async function load() {
  loading.value = true
  failed.value = false
  try {
    // ★ 身份的唯一来源是 `/mp/me`：本页可能是深链直接进来的
    if (!store.me) {
      await store.loadMe()
    }
    if (!isInternal.value) {
      return
    }
    if (!sampleId.value) {
      failed.value = true
      return
    }
    const data = await fetchVerifySample(sampleId.value)
    detail.value = data as Record<string, unknown>
    original.value = toVerifySampleForm(detail.value, store.name)
    form.value = { ...original.value }
    manualUnit.value = original.value.sourceUnitId === null && !!original.value.sourceUnitName.trim()
    uni.setNavigationBarTitle({ title: `核验 · ${kindTitle.value}` })
    await loadUnits()
  }
  catch {
    failed.value = true
  }
  finally {
    loading.value = false
  }
}

/** 单位列表（拉不到不挡核验，只剩「手动填写」） */
async function loadUnits() {
  try {
    units.value = await fetchUnits()
  }
  catch {
    units.value = []
  }
}

function fieldValue(key: SampleFieldKey): string {
  return form.value[key] ?? ''
}

function setField(key: SampleFieldKey, value: string) {
  form.value = { ...form.value, [key]: value }
}

function optionsFor(key: SampleFieldKey) {
  if (key === 'gender') {
    return [
      { value: 'male', label: '男' },
      { value: 'female', label: '女' },
      { value: 'unknown', label: '未知' },
    ]
  }
  return [
    { value: 'Y', label: '有' },
    { value: 'N', label: '无' },
  ]
}

function placeholderOf(key: SampleFieldKey): string | undefined {
  if (key === 'passage') {
    return '如 P3，可不填'
  }
  if (key === 'age') {
    return '如 56 或 3月龄'
  }
  return undefined
}

// ── 来源单位（与填写页同一个底部面板） ────────────────────────────────────

function onUnitPick(unit: SelectorUnit) {
  manualUnit.value = false
  form.value = { ...form.value, sourceUnitId: unit.unitId, sourceUnitName: unit.unitName }
}

function onUnitManual() {
  manualUnit.value = true
  form.value = { ...form.value, sourceUnitId: null }
}

function onUnitName(name: string) {
  form.value = { ...form.value, sourceUnitName: name }
}

// ── 日期 / 时间（底部弹框，与填写页同一个控件） ──────────────────────────

function toMs(value: string): number {
  if (!value) {
    return Date.now()
  }
  const ms = new Date(value.includes('T') ? value : value.replace(' ', 'T')).getTime()
  return Number.isNaN(ms) ? Date.now() : ms
}

/** 毫秒 → `yyyy-MM-dd`（日期）/ `yyyy-MM-dd HH:mm:ss`（时间），与后端 `@JsonFormat` 同形 */
function formatMs(ms: number, type: 'date' | 'datetime'): string {
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  const date = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  return type === 'date' ? date : `${date} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}

function onPick(key: SampleFieldKey) {
  if (key === 'sourceUnitName') {
    unitSheetRef.value?.open()
    return
  }
  pickerField.value = key
  pickerValue.value = toMs(fieldValue(key))
  pickerRef.value?.open()
}

function onPicked(event: { value: number | string }) {
  const ms = Number(event.value)
  if (Number.isNaN(ms)) {
    return
  }
  const key = pickerField.value
  setField(key, formatMs(ms, sampleControl(key) === 'datetime' ? 'datetime' : 'date'))
}

// ── 提交 ──────────────────────────────────────────────────────────────────

async function submit(action: VerifyAction, reason = '') {
  if (saving.value || !isPendingNow.value) {
    return
  }
  const problem = sampleProblem(kind.value, action, form.value, reason)
  if (problem) {
    uni.showToast({ title: problem, icon: 'none' })
    return
  }
  saving.value = true
  try {
    const body = sampleVerifyBody(kind.value, action, original.value, form.value, reason, 'passage' in (detail.value ?? {}))
    await verifySample(sampleId.value, body)
    reasonRef.value?.reset()
    uni.$emit(VERIFIED_EVENT)
    backAfterVerify(action === 'valid' ? '已判为有效' : '已判为无效')
  }
  catch (e) {
    // 后端原话（内部编号已存在、送检段不合格……）弹框给人看，停在本页改
    verifyFailed(e)
  }
  finally {
    saving.value = false
  }
}

function openInvalid() {
  if (saving.value) {
    return
  }
  reasonRef.value?.open()
}
</script>

<template>
  <view class="vs">
    <LoadingState v-if="loading" />

    <view v-else-if="!isInternal" class="lqg-state">
      <text class="lqg-state__text">核验只给内部人员开放</text>
    </view>

    <ErrorState v-else-if="failed" text="没能加载这条记录" @retry="load" />

    <template v-else>
      <view class="lqg-sec vs__head">
        <view class="vs__id">
          <text class="vs__no lqg-mono">{{ String(detail?.submitNo ?? '—') }}</text>
          <text v-if="metaLine" class="vs__meta">{{ metaLine }}</text>
        </view>
        <StatusChip :value="status" />
      </view>

      <!-- 已经不是待核验（别人刚核过 / 深链进来）：只说一句，不给按钮 -->
      <NoteBar
        v-if="!isPendingNow"
        tone="warn"
        text="这条记录已经核验过了；要改判请到网页工作台"
      />

      <template v-else>
        <NoteBar
          v-if="changed.length > 0"
          :text="`送检信息改了 ${changed.length} 项，会随核验一起保存`"
        />

        <!-- ① 送检信息：合作单位填的，可以直接改 -->
        <view class="lqg-gl">
          <text>送检信息</text>
          <text class="lqg-gl__x">合作单位填的，可以直接改</text>
        </view>
        <view class="vs__group">
          <FieldRow
            v-for="key in submitKeys"
            :key="key"
            :label="SAMPLE_LABEL[key]"
            :control="sampleControl(key)"
            :required="sampleRequired(kind, key)" marker-side="after"
            :mono="key === 'hospitalNo'"
            :maxlength="sampleMaxlength(key)"
            :model-value="fieldValue(key)"
            :placeholder="placeholderOf(key)"
            @update:model-value="(v: string) => setField(key, v)"
            @pick="onPick(key)"
          >
            <SegButtons
              v-if="sampleControl(key) === 'seg'"
              :options="optionsFor(key)"
              :model-value="fieldValue(key)"
              @update:model-value="(v: string) => setField(key, v)"
            />
          </FieldRow>
        </view>

        <!-- ② 收样信息：判为有效时填（收样日期、内部编号必填） -->
        <view class="lqg-gl">
          <text>收样信息</text>
          <text class="lqg-gl__x">判为有效时填</text>
        </view>
        <view class="vs__group">
          <FieldRow
            v-for="key in receiveKeys"
            :key="key"
            :label="SAMPLE_LABEL[key]"
            :control="sampleControl(key)"
            :required="sampleRequired(kind, key)" marker-side="after"
            :mono="key === 'internalNo'"
            :maxlength="sampleMaxlength(key)"
            :model-value="fieldValue(key)"
            @update:model-value="(v: string) => setField(key, v)"
            @pick="onPick(key)"
          >
            <SegButtons
              v-if="sampleControl(key) === 'seg'"
              :options="optionsFor(key)"
              :model-value="fieldValue(key)"
              @update:model-value="(v: string) => setField(key, v)"
            />
          </FieldRow>
        </view>

        <view class="lqg-bar-spacer" />
      </template>
    </template>

    <!-- 底部：判为无效（写原因）/ 判为有效并保存 -->
    <view v-if="!loading && !failed && isInternal && isPendingNow" class="lqg-bar vs__bar">
      <button class="vs__btn vs__btn--no" :disabled="saving" @click="openInvalid">
        判为无效
      </button>
      <button class="vs__btn vs__btn--ok" :disabled="saving" @click="submit('valid')">
        {{ saving ? '正在保存…' : '判为有效并保存' }}
      </button>
    </view>

    <ReasonSheet
      ref="reasonRef"
      :busy="saving"
      :maxlength="MAX_INVALID_REASON"
      :notes="['判为无效只保存原因和送检信息的修改，收样信息不保存']"
      @confirm="(reason: string) => submit('invalid', reason)"
    />

    <SourceUnitSheet
      ref="unitSheetRef"
      :units="unitOptions"
      :unit-id="form.sourceUnitId"
      :unit-name="form.sourceUnitName"
      :manual="manualUnit"
      @pick="onUnitPick"
      @manual="onUnitManual"
      @update:unit-name="onUnitName"
    />

    <wd-datetime-picker
      ref="pickerRef"
      v-model="pickerValue"
      :type="sampleControl(pickerField) === 'datetime' ? 'datetime' : 'date'"
      title="选择时间"
      @confirm="onPicked"
    >
      <!-- ★ 默认插槽放一个空节点（G12）：否则 wd-datetime-picker 自己渲染一行「值 ›」的 cell -->
      <view />
    </wd-datetime-picker>
  </view>
</template>

<style lang="scss" scoped>
.vs {
  padding: var(--lqg-sp-5) 0 0;
}

.vs__head {
  padding-top: 0;
  align-items: center;
}

.vs__id {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-1);
  min-width: 0;
}

.vs__no {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-ink);
}

.vs__meta {
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.vs__group {
  margin: 0 var(--lqg-gutter);
  overflow: hidden;
  border-radius: var(--lqg-radius-card);
  background: var(--lqg-card);
  box-shadow: var(--lqg-shadow-sm);
}

.vs__bar {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-5);
}

.vs__btn {
  height: var(--lqg-btn-h);
  line-height: var(--lqg-btn-h);
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  border-radius: var(--lqg-radius-ctl);
}

.vs__btn::after {
  border: none;
}

.vs__btn--no {
  flex: 1;
  color: var(--lqg-danger);
  background: var(--lqg-card);
  border: 1px solid var(--lqg-danger);
}

.vs__btn--ok {
  flex: 2;
  color: var(--lqg-on-primary);
  background: var(--lqg-primary);
  border: none;
  box-shadow: var(--lqg-shadow-brand);
}
</style>
