<script setup lang="ts">
import { computed, ref } from 'vue'
import type { EmbedDetail, EmbedFormValue } from '@/api/embed'
import {
  createExtEmbed,
  createIntEmbed,
  emptyEmbedForm,
  externalEmbedPayload,
  fetchExtEmbedDetail,
  fetchIntEmbedDetail,
  fetchSampleTypeHints,
  internalEmbedPatch,
  internalEmbedPayload,
  toEmbedFormValue,
  updateExtEmbed,
  updateIntEmbed,
} from '@/api/embed'
import type { SampleRow } from '@/api/sample'
import { fetchExtSampleDetail, fetchIntSampleDetail } from '@/api/sample'
import type { EmbedRow } from '@/api/sample'

/**
 * 「选择样本」抛回来的行：样本列表行 + 详情才有的两个收样字段。
 *
 * ★ 这两个字段是**详情**上的（`/mp/int/sample/{id}` / `/mp/ext/sample/{id}` 的收样段），
 *   列表行上没有它们 —— 从别处带 `sampleId` 进来时读的是详情，所以就地放宽这一层类型，
 *   不去动样本域的 `SampleRow`（那是 SAMPLE-MP-001 的公共形状）。
 */
type SampleLike = SampleRow & { receiveDate?: string | null, processTime?: string | null }
import EmbedCard from '@/components/lqg/EmbedCard.vue'
import ErrorState from '@/components/lqg/ErrorState.vue'
import FieldRow from '@/components/lqg/FieldRow.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import NoteBar from '@/components/lqg/NoteBar.vue'
import StatusChip from '@/components/lqg/StatusChip.vue'
import { goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
// ★ 一律显式 import wd-* 的 .vue（SAMPLE-MP-001 坑 6：只靠 easycom 会让该模块的 .js 产物消失）
import WdDatetimePicker from 'wot-design-uni/components/wd-datetime-picker/wd-datetime-picker.vue'
import MarkerRows from '@/components/lqg/MarkerRows.vue'
import SamplePicker from '@/components/lqg/SamplePicker.vue'
import SamplePickerExt from '@/components/lqg/SamplePickerExt.vue'
import StainButtons from '@/components/lqg/StainButtons.vue'
import type { EmbedFieldKey, EmbedFieldSpec, EmbedMode } from './layout'
import {
  embedLayout,
  fieldMaxlength,
  fieldSpecs,
  groupSpecs,
  normalizeMode,
} from './layout'
import { hasOtherStain, stainProblem, toggleStain } from './stain'

// 石蜡包埋送样记录 · 填写页（UI:mp.embed.form）· EMBED-MP-001。
//
// 三种模式**由入口决定**（同样本、类器官两张表）：
//   首页点表进来            → mode=new（内部全字段 / 外部三项）
//   「我的 → 历史编辑记录」  → mode=edit（外部改自己的待核验 / 无效；内部改任何一条有效记录）
//   「内部管理 → 石蜡包埋」  → mode=view（**一律只读**）
//
// 布局只认 `embedLayout(identity, verifyStatus, mine, mode)` 这个纯函数（fixture 驱动）：
//   - 外部**只渲染三项**、石蜡块编号 / 工序 / 染色 / marker / 包埋人 / 操作人 / 备注一个都不出现；
//   - 内部看外部送来的待核验 / 无效 = 只读（核验走核验页 `pages/verify/embed`，后端普通 PUT 也直接拒）；
//   - 只读页右上角的「修改」= `layout.showEditEntry`，把 mode 换成 `edit` 重算同一个纯函数
//     —— 「按钮显不显示」与「能不能改」同源（CR-20260918-07）；
//   - 身份缺失什么都不渲染。
//
// 保存**不要求填完**：七个工序时间全部可空（FLOW:F-EMBED-01.step2：做完一步填一步），
// 补填 = 修改（PUT，patch 语义，不新增行）。
definePage({
  style: {
    navigationBarTitleText: '石蜡包埋送样记录',
  },
})

const store = useUserStore()

const embedId = ref('')
/** 从样本填写页「给这个样本加石蜡块」带过来的样本 id（`?mode=new&sampleId=`） */
const incomingSampleId = ref('')
const mode = ref<EmbedMode>('view')
const loading = ref(true)
const failed = ref(false)
const saving = ref(false)
const detail = ref<Partial<EmbedDetail> | null>(null)
const form = ref<EmbedFormValue>(emptyEmbedForm())
const hints = ref<string[]>([])
/** 日期控件的目标字段与回填毫秒值 */
const pickerField = ref<EmbedFieldKey>('tissueReceiveTime')
const pickerValue = ref<number>(Date.now())
// ★ wot-design-uni 1.14 的 `wd-datetime-picker` **没有 `visible` 这个 prop**（D2 r1 L2 S0-3）：
//   面板开关是组件内部的 `popupShow`，对外只暴露 `open()` / `close()`。持实例、点字段时调 `open()`。
const pickerRef = ref<{ open: () => void } | null>(null)
const samplePickerRef = ref<{ open: () => void } | null>(null)
const extPickerRef = ref<{ open: () => void } | null>(null)

onLoad((options) => {
  embedId.value = String(options?.id ?? '')
  incomingSampleId.value = String(options?.sampleId ?? '')
  mode.value = normalizeMode(options?.mode)
  load()
})

const identity = computed(() => store.identity)
const isInternal = computed(() => identity.value === 'internal')
/** 新增时这条记录就是「我」要建的；有详情时按后端行上的 `mine` */
const mine = computed(() => (mode.value === 'new' ? true : detail.value?.mine === true))
const verifyStatus = computed(() => detail.value?.verifyStatus ?? null)

/** 布局（纯函数）：渲染哪些字段、能不能改、出不出包埋卡片与「修改」 */
const layout = computed(() => embedLayout(identity.value, verifyStatus.value, mine.value, mode.value))
const editable = computed(() => layout.value.editable)
const specs = computed<EmbedFieldSpec[]>(() => fieldSpecs(layout.value, editable.value))
const groups = computed(() => groupSpecs(specs.value))
/** 只读页右上角的「修改」（CR-20260918-07）：内部 + 这条记录内部可改 */
const showEditEntry = computed(() => layout.value.showEditEntry)
/** 外部看自己那条已核验有效的送样：只读 + 包埋卡片（复用 AUTH-EXT-002 的 EmbedCard） */
const showCard = computed(() => layout.value.showCard)
/** 包埋卡片的数据源（`showCard` 为真时详情一定在） */
const cardEmbed = computed<EmbedRow>(() => (detail.value ?? {}) as unknown as EmbedRow)
/** 选了「其他」才出现具体名称那一格 */
const showStainOther = computed(() => hasOtherStain(form.value.stainTypes))

const invalidReason = computed(() => (
  verifyStatus.value === 'invalid' ? String(detail.value?.invalidReason || '') : ''
))

const topNote = computed(() => {
  if (mode.value !== 'edit' || editable.value) {
    return ''
  }
  // 内部：待核验 / 无效的外部送样在这一页只读；核验走核验页（甲方 2026-09-24 第 20 行），改判仍在工作台
  return isInternal.value ? '核验请从首页「待处理」进入，改判请到网页工作台' : '这条送样现在不能修改'
})

// 修改模式顶部小字：最后修改：某某 · 时间。
// 判据是 `updateTime` 非空（= 这一行被改过），不是 `updateByName` 非空
// —— 从没改过的行也可能有 `updateByName`（SAMPLE-MP-001 坑 1）。
const lastModified = computed(() => {
  if (mode.value !== 'edit' || !isInternal.value || !detail.value) {
    return ''
  }
  const when = detail.value.updateTime
  if (!when) {
    return ''
  }
  return `最后修改：${detail.value.updateByName || '—'} · ${when}`
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    // ★ 身份的唯一来源是 `/mp/me`：本页可能是冷启动直接进来的（H5 深链 / 小程序分享），
    //   这时 store 里还没有 me —— 不先拉一次，布局就是空布局，内外部分支根本没机会跑。
    if (!store.me) {
      await store.loadMe()
    }
    await loadHints()
    if (mode.value === 'new') {
      form.value = emptyEmbedForm()
      if (isInternal.value) {
        // 内部新增：操作人默认带当前登录人（可改）
        form.value.operatorName = store.name || ''
      }
      // 从样本填写页「给这个样本加石蜡块」进来：带上样本，并把两格工序时间带出来
      if (incomingSampleId.value) {
        await prefillSample(incomingSampleId.value)
      }
      return
    }
    if (!embedId.value) {
      failed.value = true
      return
    }
    const data = isInternal.value
      ? await fetchIntEmbedDetail(embedId.value)
      : await fetchExtEmbedDetail(embedId.value)
    detail.value = data
    form.value = toEmbedFormValue(data)
  }
  catch {
    failed.value = true
  }
  finally {
    loading.value = false
  }
}

/** 样本类型联想词（字典接口；拉不到不挡填写） */
async function loadHints() {
  try {
    hints.value = await fetchSampleTypeHints()
  }
  catch {
    hints.value = []
  }
}

/**
 * 从别处带进来的样本（`?sampleId=`）：读它的详情，把样本与两格工序时间填好。
 *
 * 拉不到不算错（样本可能刚好被改判无效 / 删了）—— 用户还能自己点「选择样本」重选。
 */
async function prefillSample(sampleId: string) {
  try {
    const sample: SampleLike = isInternal.value
      ? await fetchIntSampleDetail(sampleId)
      : await fetchExtSampleDetail(sampleId)
    onSamplePicked(sample)
  }
  catch {
    // 忽略：下面「选择样本」那一格仍是空的，用户可以自己选
  }
}

function fieldValue(key: EmbedFieldKey): string {
  return (form.value as unknown as Record<string, string>)[key] ?? ''
}

function setField(key: EmbedFieldKey, value: string) {
  // ★ V25：「选择样本」那一格的值**只能**从选择器回填（`onSamplePicked` 同时写 sampleId 与显示文字）。
  //   以前这格是可打字的输入框，打的字经这里原样写进 `sampleId` —— 提交出去就是一个不存在的样本 id。
  //   FieldRow 的选择格已经不再发 update:modelValue，这里再兜一道：sampleId 不接受手输。
  if (key === 'sampleId') {
    return
  }
  ;(form.value as unknown as Record<string, string>)[key] = value
}

/** 日期控件要的是毫秒时间戳（输入输出两侧都要转，栈包 gotchas §6.3） */
function toMs(value: string): number {
  if (!value) {
    return Date.now()
  }
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const ms = new Date(normalized).getTime()
  return Number.isNaN(ms) ? Date.now() : ms
}

function onPick(spec: EmbedFieldSpec) {
  if (!editable.value) {
    return
  }
  if (spec.control === 'select') {
    if (isInternal.value) {
      samplePickerRef.value?.open()
    }
    else {
      extPickerRef.value?.open()
    }
    return
  }
  pickerField.value = spec.key
  pickerValue.value = toMs(fieldValue(spec.key))
  pickerRef.value?.open()
}

function onPicked(event: { value: number | string }) {
  const ms = Number(event.value)
  if (Number.isNaN(ms)) {
    return
  }
  setField(pickerField.value, formatMs(ms))
}

/** 毫秒 → `yyyy-MM-dd`（后端七个工序时间都是 LocalDate / `@JsonFormat("yyyy-MM-dd")`） */
function formatMs(ms: number): string {
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * 选了样本：
 *   · 内部 → 带出组织收样时间 / 组织处理时间（可改；用户已经填过的不覆盖）；
 *   · 外部 → 只记样本与送检单号（样本类型 / 类器官来源类型是外部自己填的两项）。
 */
function onSamplePicked(sample: SampleLike) {
  form.value.sampleId = String(sample.id)
  form.value.sampleLabel = isInternal.value
    ? ([sample.internalNo, sample.submitNo].filter(Boolean).join(' · ') || '')
    : String(sample.submitNo || '')
  if (!isInternal.value) {
    return
  }
  if (!form.value.tissueReceiveTime && sample.receiveDate) {
    form.value.tissueReceiveTime = String(sample.receiveDate).slice(0, 10)
  }
  if (!form.value.tissueProcessTime && sample.processTime) {
    form.value.tissueProcessTime = String(sample.processTime).slice(0, 10)
  }
}

/**
 * 染色切换：**唯一入口**是 `toggleStain`（`stain.ts` 的纯函数，需求层 fixture 驱动）。
 * 页面里不再写一遍「NONE 与其余互斥」——判据只有一份，与工作台共用同一份 fixture。
 */
function onToggleStain(value: string) {
  form.value.stainTypes = toggleStain(form.value.stainTypes, value)
  if (!hasOtherStain(form.value.stainTypes)) {
    form.value.stainOther = ''
  }
}

/** 只读页右上角的「修改」：把 mode 切成 edit，同一个纯函数重算可写性 */
function toEdit() {
  if (!showEditEntry.value) {
    return
  }
  mode.value = 'edit'
}

async function submit() {
  if (!editable.value || saving.value) {
    return
  }
  if (!form.value.sampleId) {
    uni.showToast({ title: '请选择样本', icon: 'none' })
    return
  }
  if (isInternal.value && mode.value === 'new' && !form.value.paraffinBlockNo.trim()) {
    uni.showToast({ title: '请填石蜡块编号', icon: 'none' })
    return
  }
  const problem = stainProblem(form.value.stainTypes, form.value.stainOther)
  if (problem) {
    uni.showToast({ title: problem, icon: 'none' })
    return
  }
  saving.value = true
  try {
    if (mode.value === 'new') {
      if (isInternal.value) {
        await createIntEmbed(internalEmbedPayload(form.value))
      }
      else {
        await createExtEmbed(externalEmbedPayload(form.value))
      }
      uni.showToast({ title: '已提交', icon: 'none' })
    }
    else if (isInternal.value) {
      // ★ 补填 = 修改：走 PUT（patch），**不再 POST 一次** —— 那会新增一行，
      //   而且第二次会因石蜡块编号重复被拒，用户就再也补填不了（ticket 的 counterfeit 第 1 条）。
      await updateIntEmbed(internalEmbedPatch(embedId.value, form.value))
      uni.showToast({ title: '已保存', icon: 'none' })
    }
    else {
      // ★ 外部那条 PUT 是「覆盖两个字段」（AUTH-EXT-002 WARN-3），所以两个都带。
      await updateExtEmbed(embedId.value, externalEmbedPayload(form.value))
      uni.showToast({ title: '已保存', icon: 'none' })
    }
    setTimeout(() => goPage('/pages/history/index'), 600)
  }
  catch (e) {
    if (e instanceof Error && e.message) {
      uni.showToast({ title: e.message, icon: 'none' })
    }
  }
  finally {
    saving.value = false
  }
}
</script>

<template>
  <view class="emb">
    <LoadingState v-if="loading" />

    <ErrorState v-else-if="failed" text="没能加载这条石蜡包埋送样记录" @retry="load" />

    <view v-else-if="specs.length === 0" class="lqg-state">
      <text class="lqg-state__text">没能确认你的身份，请重新登录后再试</text>
    </view>

    <template v-else>
      <view v-if="mode === 'view'" class="lqg-sec emb__sec">
        <StatusChip :value="verifyStatus" />
        <text v-if="showEditEntry" class="emb__edit" @click="toEdit">修改</text>
      </view>

      <text v-if="lastModified" class="emb__meta">{{ lastModified }}</text>
      <NoteBar v-if="topNote" tone="warn" :text="topNote" />
      <NoteBar v-if="invalidReason" tone="danger" :text="`无效 · ${invalidReason}`" />

      <view v-if="showCard" class="emb__card">
        <EmbedCard :embed="cardEmbed" />
      </view>

      <view v-for="group in groups" :key="group.key" class="emb__grp">
        <view class="lqg-gl">{{ group.title }}</view>
        <view class="emb__group">
          <template v-for="spec in group.specs" :key="spec.key">
            <view v-if="spec.control === 'stain'" class="emb__block">
              <text class="emb__block-t">{{ spec.label }}</text>
              <StainButtons
                :model-value="form.stainTypes"
                :disabled="!spec.editable"
                @toggle="onToggleStain"
              />
              <FieldRow
                v-if="showStainOther"
                label="具体名称"
                :readonly="!spec.editable"
                :maxlength="100"
                :model-value="form.stainOther"
                placeholder="例如 Masson"
                @update:model-value="(v: string) => form.stainOther = v"
              />
            </view>

            <view v-else-if="spec.control === 'markers'" class="emb__block">
              <text class="emb__block-t">{{ spec.label }}</text>
              <MarkerRows
                :model-value="form.markers"
                :disabled="!spec.editable"
                @update:model-value="(v: { markerName: string, expression: string }[]) => form.markers = v"
              />
            </view>

            <FieldRow
              v-else
              :label="spec.label"
              :control="spec.control"
              :readonly="!spec.editable"
              :required="spec.key === 'sampleId' || (isInternal && spec.key === 'paraffinBlockNo')" marker-side="after"
              :mono="spec.key === 'sampleId' || spec.key === 'paraffinBlockNo'"
              :maxlength="fieldMaxlength(spec.key)"
              :model-value="spec.control === 'select' ? form.sampleLabel : fieldValue(spec.key)"
              :placeholder="spec.key === 'sampleType' && hints.length ? `${hints[0]} 等` : undefined"
              @update:model-value="(v: string) => setField(spec.key, v)"
              @pick="onPick(spec)"
            />
          </template>
        </view>
      </view>

      <view class="lqg-bar-spacer" />
    </template>

    <view v-if="!loading && !failed && specs.length > 0" class="lqg-bar emb__bar">
      <button v-if="editable" class="emb__btn" :disabled="saving" @click="submit">
        {{ mode === 'new' ? '提交' : '保存' }}
      </button>
    </view>

    <SamplePicker ref="samplePickerRef" :disabled="!editable" @pick="onSamplePicked" />
    <SamplePickerExt ref="extPickerRef" :disabled="!editable" @pick="onSamplePicked" />

    <wd-datetime-picker
      ref="pickerRef"
      v-model="pickerValue"
      type="date"
      title="选择时间"
      @confirm="onPicked"
    >
      <!-- ★ 给默认插槽放一个空节点（G12）：没有默认插槽时 wd-datetime-picker 会自己渲染一行
           「值 ›」的 cell，页面底部就多出一行没有标签的「今天日期 ›」。面板开关只靠 open()。 -->
      <view />
    </wd-datetime-picker>
  </view>
</template>

<style lang="scss" scoped>
.emb {
  padding: var(--lqg-sp-5) 0 0;
}

.emb__sec {
  padding-top: 0;
}

.emb__edit {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-primary);
}

.emb__meta {
  display: block;
  margin: var(--lqg-sp-5) var(--lqg-gutter) 0;
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.emb__card {
  margin: var(--lqg-gap) var(--lqg-gutter) 0;
}

.emb__group {
  margin: 0 var(--lqg-gutter);
  overflow: hidden;
  border-radius: var(--lqg-radius-card);
  background: var(--lqg-card);
  box-shadow: var(--lqg-shadow-sm);
}

.emb__block {
  padding: var(--lqg-sp-5) var(--lqg-sp-6);
  border-top: 1px solid var(--lqg-line);
}

.emb__block-t {
  display: block;
  margin-bottom: var(--lqg-sp-3);
  font-size: var(--lqg-fs-body);
  color: var(--lqg-ink-2);
}

.emb__bar {
  display: flex;
  align-items: center;
}

.emb__btn {
  flex: 1;
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

.emb__btn::after {
  border: none;
}
</style>
