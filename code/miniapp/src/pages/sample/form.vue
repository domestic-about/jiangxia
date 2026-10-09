<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { SampleDetail, SampleFormValue, UnitIdCandidate } from '@/api/sample'
import {
  createExtSample,
  createIntSample,
  emptyForm,
  fetchExtSampleDetail,
  fetchIntSampleDetail,
  sourceUnitIdFor,
  toFormValue,
  updateExtSample,
  updateIntSample,
} from '@/api/sample'
import type { SelectorUnit } from '@/api/unit-group'
import { boundUnitOf, fetchUnits, unitOptionsFor } from '@/api/unit-group'
import ErrorState from '@/components/lqg/ErrorState.vue'
import FieldRow from '@/components/lqg/FieldRow.vue'
import LoadingState from '@/components/lqg/LoadingState.vue'
import NoteBar from '@/components/lqg/NoteBar.vue'
import OcrBar from '@/components/lqg/OcrBar.vue'
import SegButtons from '@/components/lqg/SegButtons.vue'
import SourceUnitSheet from '@/components/lqg/SourceUnitSheet.vue'
import SpeciesSheet from '@/components/lqg/SpeciesSheet.vue'
import StatusChip from '@/components/lqg/StatusChip.vue'
import { finishTo, goPage } from '@/router/config'
import { useUserStore } from '@/store/user'
import { unitDisplay } from '@/utils/ext-profile'
import { http } from '@/utils/request'
import { DEFAULT_SPECIES, fetchSpeciesOptions, speciesProblem } from '@/utils/species'
import WdDatetimePicker from 'wot-design-uni/components/wd-datetime-picker/wd-datetime-picker.vue'
import type { FieldSpec, FormFieldKey, FormMode } from './layout'
import { useLeaveGuard } from '@/utils/leaveGuard'
import {
  RECEIVE_FIELDS,
  fieldMaxlength,
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
/** 种属的常用值（字典 lqg_species，CR-20261009-18；拉不到用内置四个，不挡填写） */
const speciesOptions = ref<string[]>([...DEFAULT_SPECIES])
const speciesSheetRef = ref<{ open: () => void } | null>(null)
/** 启用中的单位（`/mp/ext/units`，拉不到不挡填写）：内部的来源单位从这里选（V01） */
const units = ref<SelectorUnit[]>([])
/** 来源单位：选中的单位 id（手填时为 null）与「手动填写」开关；名字在 `form.sourceUnitName` */
const unitId = ref<string | number | null>(null)
const manualUnit = ref(false)
const unitSheetRef = ref<{ open: () => void } | null>(null)
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
// 有没保存的改动时按返回先问一句（UX 测试 MP-04）：加载完、可写时记基线
const leave = useLeaveGuard(() => form.value, () => !loading.value && !failed.value && editable.value)
/** 字段清单（顺序 = 布局给的顺序，带标签与控件类型） */
const specs = computed<FieldSpec[]>(() => fieldSpecs(layout.value, editable.value))
const sendSpecs = computed(() => specs.value.filter(s => !(RECEIVE_FIELDS as readonly string[]).includes(s.key)))
const receiveSpecs = computed(() => specs.value.filter(s => (RECEIVE_FIELDS as readonly string[]).includes(s.key)))
const showReceive = computed(() => hasReceiveGroup(layout.value))

// ★ 只读页（内部管理表格页点一行进来）右上角的「修改」（CR-20260918-07）：
//   把 mode 换成 edit **重算同一个纯函数**，算出来可改才显示 —— 「按钮显不显示」与
//   「能不能改」同源。外部送来还没核验的样本算出来是 false，这一页连「修改」都不出现
//   （核验走核验页，绕不过去）。
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
    // 口径复述 4：待核验 / 无效的外部样本在这一页只读；核验走核验页（甲方 2026-09-24 第 20 行），改判仍在工作台
    return '核验请从首页「待处理」进入，改判请到网页工作台'
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
        // 外部新增：来源单位默认带档案里的单位（UI:mp.sample.form）。绑定了单位（待核验 / 已核验）
        // 就带它的 id 与名字（与类器官表单同一个来源）；只有自填单位名的，按手填处理（不带 id）。
        const bound = boundUnitOf(store.ext)
        unitId.value = bound ? bound.unitId : null
        form.value.sourceUnitName = bound ? bound.unitName : unitDisplay(store.ext)
        manualUnit.value = !bound && !!form.value.sourceUnitName
      }
      await Promise.all([loadHints(), loadUnits(), loadSpecies()])
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
    await Promise.all([loadUnits(), loadSpecies()])
    // 来源单位：内部详情带 id；外部详情没有这个键 → 名字与本人绑定单位相同才算选中它，否则按手填
    unitId.value = data.sourceUnitId ?? sourceUnitIdFor(form.value.sourceUnitName, unitCandidates())
    manualUnit.value = unitId.value === null && !!form.value.sourceUnitName.trim()
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

/** 单位列表（与类器官表单同一个接口；拉不到不挡填写，只剩「手动填写」） */
async function loadUnits() {
  try {
    units.value = await fetchUnits()
  }
  catch {
    units.value = []
  }
}

/**
 * 来源单位面板里列哪些单位（与后端同口径，V01）：内部 = 全部启用单位（内部路径**只认 id**，不按名字回找）；
 * 外部 = 只有本人绑定的那一个（后端只收本人绑定单位的 id，别的单位 id 回 400）。
 */
const unitOptions = computed<SelectorUnit[]>(() => unitOptionsFor(store.identity, units.value, store.ext))

/** 外部档案里还没有绑定单位时，面板里给的一句话 */
const unitEmptyHint = computed(() => (isInternal.value ? '' : '档案里还没有绑定单位：可以手动填写，也可以先到「我的 → 单位与组别」补充'))

/**
 * 来源单位 id 的候选（名字与其中某一项**逐字相同**才带它的 id，`sourceUnitIdFor`）：
 *   ① 详情里的 id（内部详情才有；外部详情的 VO 没有这个键）；② 面板里能选的单位（见 `unitOptions`）。
 * 外部因此只可能带上本人绑定单位的 id；识别预填或手填成了别的名字 → 不带 id。
 */
function unitCandidates(): UnitIdCandidate[] {
  const list: UnitIdCandidate[] = []
  if (detail.value && isInternal.value) {
    list.push({ id: detail.value.sourceUnitId, name: detail.value.sourceUnitName })
  }
  unitOptions.value.forEach(unit => list.push({ id: unit.unitId, name: unit.unitName }))
  return list
}

/** 当前选中的单位 id 对应的单位名（对不上 → ''） */
function nameOfUnit(id: string | number | null): string {
  if (id === null) {
    return ''
  }
  const hit = unitCandidates().find(c => c.id !== null && c.id !== undefined && String(c.id) === String(id))
  return String(hit?.name ?? '').trim()
}

// 名字被别处改了（拍照识别预填）：不在手填状态时按新名字重新对 id —— 对不上就转成手填，不留旧 id
watch(() => form.value.sourceUnitName, (name) => {
  if (manualUnit.value || nameOfUnit(unitId.value) === String(name ?? '').trim()) {
    return
  }
  unitId.value = sourceUnitIdFor(name ?? '', unitCandidates())
  manualUnit.value = unitId.value === null && !!String(name ?? '').trim()
})

/** 面板里选了一个单位：id 与名字一起写回（名字是快照） */
function onUnitPick(unit: SelectorUnit) {
  unitId.value = unit.unitId
  manualUnit.value = false
  setField('sourceUnitName', unit.unitName)
}

/** 「列表里没有，手动填写」：清掉 id，只留名字 */
function onUnitManual() {
  unitId.value = null
  manualUnit.value = true
}

function onUnitName(name: string) {
  setField('sourceUnitName', name)
}

/** 提交用的单位 id：手填不带；选中的 id 与当前名字对不上（名字被改过）也不带 */
function currentUnitId(): string | number | null {
  if (manualUnit.value) {
    return null
  }
  const name = form.value.sourceUnitName.trim()
  if (!name) {
    return null
  }
  if (unitId.value !== null && nameOfUnit(unitId.value) === name) {
    return unitId.value
  }
  return sourceUnitIdFor(name, unitCandidates())
}

/**
 * 外部还没有来源单位（新用户、档案里没绑定单位）时的引导（后端「来源单位不能为空」同一件事）：
 * 告诉他可以手填，或者去「我的 → 单位与组别」补充后再提交。
 */
function guideToUnitGroup() {
  uni.showModal({
    title: '还没有来源单位',
    content: '可以在「来源单位」里手动填写；也可以先到「我的 → 单位与组别」补充单位与组别，之后提交会自动带上。',
    confirmText: '去补充',
    cancelText: '知道了',
    success: (res) => {
      if (res.confirm) {
        goPage('/pages/me/unit-group')
      }
    },
  })
}

/** 种属常用值：字典接口 `/mp/dict/hints?type=species`（拉不到用内置四个） */
async function loadSpecies() {
  speciesOptions.value = await fetchSpeciesOptions()
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

/**
 * 必填标记与提交前校验（G26：与后端同一口径，不另起一套）——
 *   · 来源单位：`t_lqg_sample.source_unit_name` 非空（field-ssot），内外部都要；
 *   · 种属：两类、内外部都必填（CR-20261009-18）；
 *   · 组织类型：tissue 类必填（`SampleKindRules.missingRequiredFields`）；
 *   · 供体姓名：**外部必填、内部选填**（后端口径：外部送检必须写清供体，内部补录可以空着）；
 *   · 内部另要收样日期、内部编号（内部新增直接有效，这两项必须有）。
 */
function isRequired(key: FormFieldKey): boolean {
  // 种属（CR-20261009-18）：两类、内外部都必填（后端 SubmitSegmentRules 同口径）
  if (key === 'sourceUnitName' || key === 'species' || key === 'tissueType') {
    return true
  }
  if (key === 'donorName') {
    return !isInternal.value
  }
  return isInternal.value && (key === 'receiveDate' || key === 'internalNo')
}

/** 占位提示：组织类型给字典联想词，年龄提示可以写「3月龄」（G15：年龄是文本，不是数字），其余按控件默认 */
function placeholderOf(key: FormFieldKey): string | undefined {
  if (key === 'tissueType' && tissueHints.value.length) {
    return `${tissueHints.value[0]} 等`
  }
  if (key === 'age') {
    return '如 56 或 3月龄'
  }
  return undefined
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
  if (key === 'sourceUnitName') {
    unitSheetRef.value?.open()
    return
  }
  if (key === 'species') {
    speciesSheetRef.value?.open()
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
    // ★ V01：来源单位 id 与名字一起发（以前只发名字 → 外部从小程序交的组织样本 source_unit_id 为空，
    //   工作台按单位筛选、按单位导出都查不到）。内部发面板里选中的单位 id（内部路径不按名字回找）；
    //   外部只可能是本人绑定单位的 id；手填 / 名字改过就不带 id。
    sourceUnitId: currentUnitId(),
    sourceUnitName: f.sourceUnitName,
    species: f.species.trim(),
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
  // 必填项与 isRequired 同一口径（G26）：来源单位、种属、组织类型；外部另要供体姓名；内部另要收样日期、内部编号
  if (!form.value.sourceUnitName.trim()) {
    if (isInternal.value) {
      uni.showToast({ title: '请选择来源单位', icon: 'none' })
    }
    else {
      guideToUnitGroup()
    }
    return
  }
  const speciesError = speciesProblem(form.value.species)
  if (speciesError) {
    uni.showToast({ title: speciesError, icon: 'none' })
    return
  }
  if (!isInternal.value && !form.value.donorName.trim()) {
    uni.showToast({ title: '请填供体姓名', icon: 'none' })
    return
  }
  if (!form.value.tissueType.trim()) {
    uni.showToast({ title: '请填组织类型', icon: 'none' })
    return
  }
  if (isInternal.value && !form.value.receiveDate) {
    uni.showToast({ title: '请选收样日期', icon: 'none' })
    return
  }
  if (isInternal.value && !form.value.internalNo.trim()) {
    uni.showToast({ title: '内部编号必填', icon: 'none' })
    return
  }
  saving.value = true
  // 成功后按钮一直禁用到离开本页（UX 测试 MP-01：原来 finally 先复位，600ms 空窗里连点会重复建一条）
  let done = false
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
    done = true
    leave.release()
    setTimeout(() => finishTo('/pages/history/index?tab=sample'), 600)
  }
  catch (e) {
    // 外部新用户撞上后端「来源单位不能为空」：换成去「我的 → 单位与组别」的引导
    if (!isInternal.value && e instanceof Error && /来源单位.*(不能为空|为空|必填)/.test(e.message)) {
      guideToUnitGroup()
      return
    }
    // 请求层已经按业务码 toast 过后端给的 msg（例如「待核验…只能在网页工作台核验或改判」）
    if (e instanceof Error && e.message) {
      uni.showToast({ title: e.message, icon: 'none' })
    }
  }
  finally {
    if (!done) {
      saving.value = false
    }
  }
}

/**
 * 「给这个样本加石蜡块」「加冻存」两个小链接（UI:mp.sample.form：内部修改模式才有，V26）：
 * 只给**内部账号 + 修改模式 + 已有样本 id**，且这条样本**已核验有效** —— 两个目标页的
 * 「选择样本」都只列有效样本（`SamplePicker` 钉在 `verifyStatus=valid`），待核验 / 无效的样本
 * 带过去也提交不了（UI 锚：待核验、无效的外部样本整页只读，只提示去工作台核验）。
 * 以前常显 —— 外部点进冻存页看到「没能确认你的身份」，新增模式下还没有样本 id，点了什么都不发生（死链）。
 */
const showAddLinks = computed(() => isInternal.value
  && mode.value === 'edit'
  && !!sampleId.value
  && detail.value?.verifyStatus === 'valid')

/** 底部栏：有「提交 / 保存」或有两个小链接才出（都没有时不留一条空白栏） */
const showBar = computed(() => editable.value || showAddLinks.value)

/** 「给这个样本加石蜡块」：带上 `sampleId` 进石蜡包埋填写页（EMBED-MP-001 点亮这条链接） */
function addEmbed() {
  if (!showAddLinks.value) {
    return
  }
  goPage(`/pages/embed/form?mode=new&sampleId=${sampleId.value}`)
}

/** 「加冻存」：带上 `sampleId` 进冻存填写页（CRYO-MP-001 点亮这条链接） */
function addCryo() {
  if (!showAddLinks.value) {
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
          :required="isRequired(spec.key)" marker-side="after"
          :mono="spec.key === 'hospitalNo'"
          :maxlength="fieldMaxlength(spec.key)"
          :model-value="fieldValue(spec.key)"
          :placeholder="placeholderOf(spec.key)"
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
            :required="isRequired(spec.key)" marker-side="after"
            :mono="spec.key === 'internalNo'"
            :maxlength="fieldMaxlength(spec.key)"
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

    <!-- 底部固定栏：可写才出「保存 / 提交」；两个小链接只给内部修改模式（V26）；都没有就不出栏 -->
    <view v-if="!loading && !failed && specs.length > 0 && showBar" class="lqg-bar form__bar">
      <button v-if="editable" class="form__btn" :disabled="saving" @click="submit">
        {{ mode === 'new' ? '提交' : '保存' }}
      </button>
      <view v-if="showAddLinks" class="form__links">
        <text class="form__link" @click="addEmbed">给这个样本加石蜡块</text>
        <text class="form__link" @click="addCryo">加冻存</text>
      </view>
    </view>

    <!-- 来源单位：底部弹框（内部 = 全部启用单位；外部 = 本人绑定的单位；都能手填） -->
    <SourceUnitSheet
      ref="unitSheetRef"
      :units="unitOptions"
      :unit-id="unitId"
      :unit-name="form.sourceUnitName"
      :manual="manualUnit"
      :disabled="!editable"
      :empty-hint="unitEmptyHint"
      @pick="onUnitPick"
      @manual="onUnitManual"
      @update:unit-name="onUnitName"
    />

    <!-- 种属：底部弹框（CR-20261009-18）—— 字典常用值 + 「列表里没有，手动填写」 -->
    <SpeciesSheet
      ref="speciesSheetRef"
      :model-value="form.species"
      :options="speciesOptions"
      :disabled="!editable"
      @update:model-value="(v: string) => setField('species', v)"
    />

    <!-- 日期 / 时间：底部弹框（落地规范 §5.4）；开关调组件的 open()，不用不存在的 :visible -->
    <wd-datetime-picker
      ref="pickerRef"
      v-model="pickerValue"
      :type="pickerField === 'processTime' ? 'datetime' : 'date'"
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
