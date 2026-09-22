<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SampleDetail, SampleFormValue } from '@/api/sample'
import {
  createExtSample,
  createIntSample,
  emptyForm,
  fetchExtSampleDetail,
  fetchIntSampleDetail,
  toFormValue,
  updateExtSample,
  updateIntSample,
} from '@/api/sample'
import ErrorState from '@/components/lqg/ErrorState.vue'
import FieldRow from '@/components/lqg/FieldRow.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import NoteBar from '@/components/lqg/NoteBar.vue'
import OcrBar from '@/components/lqg/OcrBar.vue'
import SegButtons from '@/components/lqg/SegButtons.vue'
import StatusChip from '@/components/lqg/StatusChip.vue'
import { goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { unitDisplay } from '@/utils/ext-profile'
import { http } from '@/utils/request'
import WdDatetimePicker from 'wot-design-uni/components/wd-datetime-picker/wd-datetime-picker.vue'
import type { FieldSpec, FormFieldKey, FormMode } from './layout'
import {
  RECEIVE_FIELDS,
  fieldSpecs,
  formLayout,
  hasReceiveGroup,
  normalizeMode,
  resolveEditable,
} from './layout'

// 样本记录信息表 · 填写页（UI:mp.sample.form，方案 A：单页分组长表单）· SAMPLE-MP-001。
//
// 三种模式**由入口决定**（ticket §0 口径复述 3）：
//   首页点表进来          → mode=new（内外部一样都是新增一条）
//   「历史编辑记录」点一条 → mode=edit（外部改自己的待核验 / 无效后重提；内部改有效样本）
//   「内部管理」点一行     → mode=view（**一律只读**，本人录的也只读）
// mode 缺失或不认识 → 按只读（`layout.ts` 的 `normalizeMode`，且 layout 里已断）。
//
// 四件最容易做反的事：
//   1. 外部**不渲染收样段**（不是置灰）——字段清单由 `formLayout` 给，外部只拿送检段；
//   2. `editable` **以后端详情为准**（`resolveEditable`）——同组别人的样本可看不可改；
//   3. 内部修改模式**不限本人录的**（CR-20260918-07），但待核验 / 无效只读并提示去工作台核验；
//   4. 身份缺失 → 什么都不渲染（不默认当内部）。
definePage({
  style: {
    navigationBarTitleText: '样本记录信息表',
  },
})

const store = useUserStore()

const sampleId = ref('')
const mode = ref<FormMode>('view')
const loading = ref(true)
const failed = ref(false)
const saving = ref(false)
/** 详情接口给的「能不能改」（口径复述 2：以后端为准）；新增那一次是 null */
const serverEditable = ref<boolean | null>(null)
const detail = ref<SampleDetail | null>(null)
const form = ref<SampleFormValue>(emptyForm())
/** 被**这次识别**预填的字段（`mergeOcrPrefill` 的 `marks`）：右侧出「识别 · 请核对」小标 */
const ocrMarks = ref<Set<string>>(new Set())
/**
 * 识别夹具用例（OCR-MP-001）：`?stubCase=01` 带进页面 → 透给 `OcrBar` → 上传请求带
 * `X-Ocr-Stub-Case`，让 dev / test 的 `StubOcrProvider` 返回那一组 `rawLines`。
 * **只有端侧截图脚本会带这个参数**；生产构建里 `StubOcrProvider` 根本不存在，带了也没人读。
 */
const stubCase = ref('')
const tissueHints = ref<string[]>([])
/** 日期 / 时间控件的 `wd-datetime-picker`：目标字段、回填用的毫秒值、组件实例 */
const pickerField = ref<FormFieldKey>('receiveDate')
const pickerValue = ref<number>(Date.now())
// ★ wot-design-uni 1.14 的 `wd-datetime-picker` **没有 `visible` 这个 prop**（D2 r1 L2 S0-3）：
//   面板开关在组件内部的 `popupShow` 上，对外只暴露 `open()` / `close()`（`types.ts`
//   的 `DatetimePickerExpose`）。所以这里持组件实例、点字段时调 `open()`；
//   以前写 `:visible="pickerOpen"` 只会变成一个落不到任何逻辑上的普通 HTML 属性 → 面板永不弹出。
const pickerRef = ref<{ open: () => void } | null>(null)

onLoad((options) => {
  sampleId.value = String(options?.id ?? '')
  mode.value = normalizeMode(options?.mode)
  stubCase.value = String(options?.stubCase ?? '')
  load()
})

const identity = computed(() => store.identity)
const isInternal = computed(() => identity.value === 'internal')

/** 新增时这条记录就是「我」要建的；有详情时按后端行上的 `mine` */
const mine = computed(() => (mode.value === 'new' ? true : detail.value?.mine === true))

const layout = computed(() => formLayout(
  identity.value,
  detail.value?.verifyStatus ?? null,
  mine.value,
  mode.value,
))
const editable = computed(() => resolveEditable(layout.value, serverEditable.value))
/** 字段清单（顺序 = 布局给的顺序，带标签与控件类型） */
const specs = computed<FieldSpec[]>(() => fieldSpecs(layout.value, editable.value))
const sendSpecs = computed(() => specs.value.filter(s => !(RECEIVE_FIELDS as readonly string[]).includes(s.key)))
const receiveSpecs = computed(() => specs.value.filter(s => (RECEIVE_FIELDS as readonly string[]).includes(s.key)))
const showReceive = computed(() => hasReceiveGroup(layout.value))

// ★ 只读页（内部管理表格页点一行进来）右上角的「修改」（CR-20260918-07）：
//   把 mode 换成 edit **重算同一个纯函数**，算出来可改才显示 —— 「按钮显不显示」与
//   「能不能改」同源。外部送来还没核验的样本算出来是 false，这一页连「修改」都不出现
//   （核验在工作台，绕不过去）。
const canEditFromView = computed(() => mode.value === 'view'
  && formLayout(identity.value, detail.value?.verifyStatus ?? null, mine.value, 'edit').editable)

/** 点「修改」：切成修改模式，可写性由上面同一个纯函数重算 */
function toEdit() {
  if (!canEditFromView.value) {
    return
  }
  mode.value = 'edit'
}

const topNote = computed(() => {
  if (mode.value !== 'edit' || editable.value) {
    return ''
  }
  if (isInternal.value) {
    // 口径复述 4：待核验 / 无效的外部样本在小程序里只读
    return '核验与改判请到网页工作台'
  }
  return '这条记录现在不能修改'
})

// ★ 无效原因（D2 r1 L2 S1-2）：外部从「历史编辑记录」点进来看到的就是这一页，
//   所以原因在这里也出一条红条（权威 FLOW:F-SAMPLE-01.step5：「看到无效及原因 → 修改 → 重新提交」）。
//   原因来自详情接口（`ExtSampleDetailVo.invalidReason`，本来就是外部可见字段），不靠列表传参。
const invalidReason = computed(() => (detail.value?.verifyStatus === 'invalid'
  ? String(detail.value?.invalidReason || '')
  : ''))

// 内部修改模式顶部小字：最后修改：某某 · 时间。
//
// 判据是 `updateTime` 非空（= 这一行被改过），不是 `updateByName` 非空：
// 从没改过的行 `updateByName` 也是创建人的名字（`SampleNameResolver` 的口径），
// 拿它当判据会显示成「最后修改：王医生 · 连字符」（实测踩过）。
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
    //   这时 store 里还没有 me —— 必须先拉回来，否则 `formLayout(undefined,…)` 返回空布局，
    //   页面会显示「没能确认你的身份」而外部 / 内部的分支根本没机会跑。
    if (!store.me) {
      await store.loadMe()
    }
    if (mode.value === 'new') {
      form.value = emptyForm()
      if (isInternal.value) {
        // 内部新增：操作人默认带当前登录人（可改）
        form.value.operatorName = store.name || ''
      }
      else {
        // 外部新增：来源单位默认带档案里的单位名（UI:mp.sample.form）
        form.value.sourceUnitName = unitDisplay(store.ext)
      }
      await loadHints()
      return
    }
    if (!sampleId.value) {
      failed.value = true
      return
    }
    const data = isInternal.value
      ? await fetchIntSampleDetail(sampleId.value)
      : await fetchExtSampleDetail(sampleId.value)
    detail.value = data
    serverEditable.value = data.editable === true
    form.value = toFormValue(data)
    if (layout.value.showOcr) {
      await loadHints()
    }
  }
  catch {
    failed.value = true
  }
  finally {
    loading.value = false
  }
}

/** 组织类型联想词：字典接口 `/mp/dict/hints?type=tissue`（拉不到不挡填写） */
async function loadHints() {
  try {
    tissueHints.value = await http.get<string[]>('/mp/dict/hints', { type: 'tissue' }, { silent: true })
  }
  catch {
    tissueHints.value = []
  }
}

const genderOptions = [
  { value: 'male', label: '男' },
  { value: 'female', label: '女' },
  { value: 'unknown', label: '未知' },
]
const ynOptions = [
  { value: 'Y', label: '有' },
  { value: 'N', label: '无' },
]

function fieldValue(key: FormFieldKey): string {
  return (form.value as unknown as Record<string, string>)[key] ?? ''
}

function setField(key: FormFieldKey, value: string) {
  const before = fieldValue(key)
  ;(form.value as unknown as Record<string, string>)[key] = value
  // 用户动了这一项 → 「识别 · 请核对」小标消失（`FLOW:F-OCR-01.step4`）。
  // ★ 判据是「值**变了**」而不是「控件被点过」：点一下输入框又把原值留下时，小标不该无故消失。
  if (before === value) {
    return
  }
  dropOcrMark(key)
}

/** 摘掉某一项的识别小标（幂等；识别预填本身不摘） */
function dropOcrMark(key: FormFieldKey | string) {
  if (!ocrMarks.value.has(key)) {
    return
  }
  const next = new Set(ocrMarks.value)
  next.delete(key)
  ocrMarks.value = next
}

/**
 * 识别预填回来的那一刻（`OcrBar` 已把值原地写进 `form`）：决定哪些格挂「识别 · 请核对」。
 *
 * ★ 口径：小标说明的是「**这一格的值是识别写的**」，不是「识别这一次认得它」。
 *   两条容易做反的：
 *   1. 识别**认出**了来源单位，但外部新增时那一格在识别前就带了登录人档案里的单位名
 *      （`load()` 的既有逻辑）→ 值不是识别写的，**不挂标**。判据是「点识别那一刻这格是不是空的」
 *      （`onBeforeRecognize` 拍的快照），不是「识别结果里有没有这个键」。
 *   2. 连着识别第二次时，第一次填的值**不该掉标** —— 它仍然是识别写的。所以只对
 *      「这一格现在有值 ∧（原来有标 ∨ 原来为空）」的格挂标，而不是无脑取本次 `marks`。
 *   提交的永远是表单当前值（`FLOW:F-OCR-01.step5`）—— 本函数不碰提交体。
 */
function onPrefilled(marks: string[]) {
  const next = new Set<string>()
  Object.keys(preRecognizeMarked).forEach((key) => {
    const stillFilled = !!fieldValue(key as FormFieldKey)
    const wasRecognized = marks.includes(key) || preRecognizeMarked[key]
    if (stillFilled && wasRecognized) {
      next.add(key)
    }
  })
  ocrMarks.value = next
}

/** 点识别那一刻每格有没有识别小标（用来让「上一次识别填的格」不掉标） */
let preRecognizeMarked: Record<string, boolean> = {}

/** 识别条要开始识别了：先记下这一刻哪些格已经有识别小标（见 `onPrefilled`） */
function onBeforeRecognize() {
  const marked: Record<string, boolean> = {}
  Object.keys(form.value as unknown as Record<string, string>).forEach((key) => {
    marked[key] = ocrMarks.value.has(key)
  })
  preRecognizeMarked = marked
}

function optionsFor(key: FormFieldKey) {
  return key === 'gender' ? genderOptions : ynOptions
}

/** 日期 / 时间控件要的是毫秒时间戳（栈包 gotchas §6.3：输入输出两侧都要转） */
function toMs(value: string): number {
  if (!value) {
    return Date.now()
  }
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const ms = new Date(normalized).getTime()
  return Number.isNaN(ms) ? Date.now() : ms
}

function onPick(key: FormFieldKey) {
  if (!editable.value) {
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
  setField(key, formatMs(ms, key === 'processTime' ? 'datetime' : 'date'))
}

/** 毫秒 → `yyyy-MM-dd`（日期）/ `yyyy-MM-dd HH:mm:ss`（时间），与后端 `@JsonFormat` 同形 */
function formatMs(ms: number, type: 'date' | 'datetime'): string {
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  const date = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  if (type === 'date') {
    return date
  }
  return `${date} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 提交体：送检段 + 收样段（内部 PUT 收部分字段，整份带回去最稳） */
function payload(): Record<string, unknown> {
  const f = form.value
  const body: Record<string, unknown> = {
    sourceUnitName: f.sourceUnitName,
    donorName: f.donorName,
    gender: f.gender,
    age: f.age,
    hospitalNo: f.hospitalNo,
    tissueType: f.tissueType,
    hasPathology: f.hasPathology,
    remark: f.remark,
  }
  // ★ `sampleKind` 只在**新增**时带（issue #105）：它是这条记录的**类目身份**
  //   （`FIELD:t_lqg_sample.sample_kind`），由入口定下、创建时写死，不是可改字段。
  //   修改模式再发它 = 一条别的类别的记录被点错行保存后静默改判（后端同口径 400 兜底）。
  if (mode.value === 'new') {
    body.sampleKind = 'tissue'
  }
  if (isInternal.value) {
    body.receiveDate = f.receiveDate || null
    body.internalNo = f.internalNo
    body.isFixed = f.isFixed
    body.processTime = f.processTime ? f.processTime.replace('T', ' ') : null
    body.hasQcSheet = f.hasQcSheet
    body.hasViabilityReport = f.hasViabilityReport
    body.operatorName = f.operatorName
  }
  return body
}

async function submit() {
  if (!editable.value || saving.value) {
    return
  }
  if (!form.value.donorName.trim()) {
    uni.showToast({ title: '请填供体姓名', icon: 'none' })
    return
  }
  if (!form.value.tissueType.trim()) {
    uni.showToast({ title: '请填组织类型', icon: 'none' })
    return
  }
  if (isInternal.value && !form.value.internalNo.trim()) {
    uni.showToast({ title: '内部编号必填', icon: 'none' })
    return
  }
  saving.value = true
  try {
    if (mode.value === 'new') {
      if (isInternal.value) {
        await createIntSample(payload())
      }
      else {
        await createExtSample(payload())
      }
      uni.showToast({ title: '已提交', icon: 'none' })
    }
    else {
      const body = { ...payload(), id: sampleId.value }
      if (isInternal.value) {
        await updateIntSample(body)
      }
      else {
        await updateExtSample(sampleId.value, body)
      }
      uni.showToast({ title: '已保存', icon: 'none' })
    }
    setTimeout(() => goPage('/pages/history/index'), 600)
  }
  catch (e) {
    // 请求层已经按业务码 toast 过后端给的 msg（例如「待核验…只能在网页工作台核验或改判」）
    if (e instanceof Error && e.message) {
      uni.showToast({ title: e.message, icon: 'none' })
    }
  }
  finally {
    saving.value = false
  }
}

/** 「给这个样本加石蜡块」：带上 `sampleId` 进石蜡包埋填写页（EMBED-MP-001 点亮这条链接） */
function addEmbed() {
  if (!sampleId.value) {
    return
  }
  goPage(`/pages/embed/form?mode=new&sampleId=${sampleId.value}`)
}

/** 「加冻存」：带上 `sampleId` 进冻存填写页（CRYO-MP-001 点亮这条链接） */
function addCryo() {
  if (!sampleId.value) {
    return
  }
  goPage(`/pages/cryo/form?mode=new&sampleId=${sampleId.value}`)
}
</script>

<template>
  <view class="form">
    <LoadingState v-if="loading" />

    <ErrorState v-else-if="failed" text="没能加载这条样本" @retry="load" />

    <!-- 身份缺失：什么都不渲染（不默认当内部） -->
    <view v-else-if="specs.length === 0" class="lqg-state">
      <text class="lqg-state__text">没能确认你的身份，请重新登录后再试</text>
    </view>

    <template v-else>
      <!-- 只读页的样子：左边核验状态，右上角「修改」（CR-20260918-07） -->
      <view v-if="mode === 'view'" class="lqg-sec form__sec">
        <StatusChip :value="detail?.verifyStatus" />
        <text v-if="canEditFromView" class="form__edit" @click="toEdit">修改</text>
      </view>

      <!-- 识别条（OCR-MP-001）：只在新增那一次出现（`layout.showOcr`，编辑已提交样本时为 false） -->
      <OcrBar
        v-if="layout.showOcr"
        :form="form"
        :stub-case="stubCase"
        @recognizing="onBeforeRecognize"
        @prefilled="(_f: SampleFormValue, marks: string[]) => onPrefilled(marks)"
      />

      <text v-if="lastModified" class="form__meta">{{ lastModified }}</text>
      <!-- 无效原因红条：外部改后重提要看得见「为什么被判无效」（S1-2） -->
      <NoteBar v-if="invalidReason" tone="danger" :text="invalidReason" />
      <NoteBar v-if="topNote" tone="warn" :text="topNote" />

      <!-- 送检信息 -->
      <view class="lqg-gl">送检信息</view>
      <view class="form__group">
        <FieldRow
          v-for="spec in sendSpecs"
          :key="spec.key"
          :label="spec.label"
          :control="spec.control"
          :readonly="!spec.editable"
          :required="spec.key === 'donorName' || spec.key === 'tissueType'"
          :model-value="fieldValue(spec.key)"
          :placeholder="spec.key === 'tissueType' && tissueHints.length ? `${tissueHints[0]} 等` : '请填写'"
          :ocr-mark="ocrMarks.has(spec.key)"
          @update:model-value="(v: string) => setField(spec.key, v)"
          @pick="onPick(spec.key)"
        >
          <SegButtons
            v-if="spec.control === 'seg'"
            :options="optionsFor(spec.key)"
            :model-value="fieldValue(spec.key)"
            :disabled="!spec.editable"
            @update:model-value="(v: string) => setField(spec.key, v)"
          />
        </FieldRow>
      </view>

      <!-- 收样信息：外部连这一组标题都不渲染（不是置灰） -->
      <template v-if="showReceive">
        <view class="lqg-gl">收样信息</view>
        <view class="form__group">
          <FieldRow
            v-for="spec in receiveSpecs"
            :key="spec.key"
            :label="spec.label"
            :control="spec.control"
            :readonly="!spec.editable"
            :model-value="fieldValue(spec.key)"
            :ocr-mark="ocrMarks.has(spec.key)"
            @update:model-value="(v: string) => setField(spec.key, v)"
            @pick="onPick(spec.key)"
          >
            <SegButtons
              v-if="spec.control === 'seg'"
              :options="ynOptions"
              :model-value="fieldValue(spec.key)"
              :disabled="!spec.editable"
              @update:model-value="(v: string) => setField(spec.key, v)"
            />
          </FieldRow>
        </view>
      </template>

      <view class="lqg-bar-spacer" />
    </template>

    <!-- 底部固定栏：可写才出「保存 / 提交」；只读页不出（没有可提交的东西） -->
    <view v-if="!loading && !failed && specs.length > 0" class="lqg-bar form__bar">
      <button v-if="editable" class="form__btn" :disabled="saving" @click="submit">
        {{ mode === 'new' ? '提交' : '保存' }}
      </button>
      <view class="form__links">
        <text class="form__link" @click="addEmbed">给这个样本加石蜡块</text>
        <text class="form__link" @click="addCryo">加冻存</text>
      </view>
    </view>

    <!-- 日期 / 时间：底部弹框（落地规范 §5.4）；开关调组件的 open()，不用不存在的 :visible -->
    <wd-datetime-picker
      ref="pickerRef"
      v-model="pickerValue"
      :type="pickerField === 'processTime' ? 'datetime' : 'date'"
      title="选择时间"
      @confirm="onPicked"
    />
  </view>
</template>

<style lang="scss" scoped>
.form {
  padding: var(--lqg-sp-5) 0 0;
}

.form__group {
  margin: 0 var(--lqg-gutter);
  overflow: hidden;
  border-radius: var(--lqg-radius-card);
  background: var(--lqg-card);
  box-shadow: var(--lqg-shadow-sm);
}

.form__sec {
  padding-top: 0;
}

.form__edit {
  font-size: var(--lqg-fs-title);
  font-weight: var(--lqg-fw-semibold);
  color: var(--lqg-primary);
}

.form__meta {
  display: block;
  margin: var(--lqg-sp-5) var(--lqg-gutter) 0;
  font-size: var(--lqg-fs-sm);
  color: var(--lqg-ink-3);
}

.form__bar {
  display: flex;
  align-items: center;
  gap: var(--lqg-sp-5);
}

.form__btn {
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

.form__btn::after {
  border: none;
}

.form__links {
  display: flex;
  flex-direction: column;
  gap: var(--lqg-sp-1);
  flex: none;
}

.form__link {
  font-size: var(--lqg-fs-xs);
  color: var(--lqg-ink-3);
}
</style>
