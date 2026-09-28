import type { EmbedFormValue } from '@/api/embed'
import { stainProblem } from '@/pages/embed/stain'

// 小程序核验页的表单规则（纯函数）：哪些字段、哪些必填、提交体怎么拼。
//
// 权威是后端（本文件只让用户少跑一趟）：
//   样本 `PUT /lqg/sample/{id}/verify` → `SampleVerifyService`（判有效：收样日期 + 内部编号必填、内部编号全库唯一；
//     判无效：原因必填 ≤ 200 字）+ `SubmitSegmentRules`（送检段：来源单位、组织类型 / 类器官类型必填）；
//   石蜡包埋 `PUT /lqg/embed/{id}/verify` → `EmbedVerifyService`（判有效：石蜡块编号必填且全库唯一、所挂样本已核验有效；
//     判无效：原因必填；`fill` 判无效只收样本类型、类器官来源类型）。
// 与工作台两个核验抽屉（`SampleDrawer.vue` / `EmbedDrawer.vue` + `verifyFill.ts`）逐项对齐；
// 期望值在 `doc/verify/fixtures/verify-form-cases.json`，`rules.fixture.spec.ts` 逐例断。
//
// ★ 「改过的才提交」落在两个接口上是两种形状（别做成一种）：
//   · 样本的 `submitSegment` 是**整段替换**（`SampleSubmitSegmentWriter`：没给的键写成空）——
//     所以只能按「整段」判：送检信息一项都没改 → **不带** `submitSegment`（送检段一个字不动）；
//     改了任何一项 → 带**整段**，没改的项带原值。只发改过的那几个键会把别的项清空
//     （实测：只带类器官类型去核验一条组织样本，后端回「组织类型不能为空」）。
//   · 石蜡包埋的 `fill` 是**补丁**（没带的键不动、带了空值清空）→ 真正只带改过的键；一项没改就不带 `fill`。
// ★ 代数 `passage`（A 组给类器官加的列，文本，形如 P3，选填）：详情里有这个键（后端加列之后）就当送检段的
//   正常一项、整段里带原值；详情里没有、也没改 → 不发（不在老后端上多一个它不认识的键）。

// ── 样本记录 / 类器官收样 ──────────────────────────────────────────────────

export type SampleKind = 'tissue' | 'organoid'

/** 核验页的表单值（两类共用一份；各类只渲染自己的字段） */
export interface VerifySampleForm {
  /** 选中的来源单位 id（手填单位时为 null） */
  sourceUnitId: string | number | null
  sourceUnitName: string
  donorName: string
  gender: string
  age: string
  hospitalNo: string
  tissueType: string
  organoidType: string
  passage: string
  hasPathology: string
  remark: string
  receiveDate: string
  internalNo: string
  isFixed: string
  processTime: string
  hasQcSheet: string
  hasViabilityReport: string
  operatorName: string
}

export type SampleFieldKey = Exclude<keyof VerifySampleForm, 'sourceUnitId'>

/**
 * 送检信息（合作单位填的，核验时可以直接改）。顺序 = 填写页的顺序；
 * 类器官的「代数」紧跟「类器官类型」（A 组口径）。
 */
export const SUBMIT_FIELDS: Record<SampleKind, SampleFieldKey[]> = {
  tissue: ['sourceUnitName', 'donorName', 'gender', 'age', 'hospitalNo', 'tissueType', 'hasPathology', 'remark'],
  organoid: ['sourceUnitName', 'organoidType', 'passage', 'remark'],
}

/**
 * 收样信息（实验室判有效时填）。组织样本七项（模板 A）；类器官五项（模板 B：没有有无固定、质控表），
 * 与工作台抽屉的提示「收样段填收样日期、内部编号、处理时间、细胞活率报告、操作人」一致。
 */
export const RECEIVE_FIELDS: Record<SampleKind, SampleFieldKey[]> = {
  tissue: ['receiveDate', 'internalNo', 'isFixed', 'processTime', 'hasQcSheet', 'hasViabilityReport', 'operatorName'],
  organoid: ['receiveDate', 'internalNo', 'processTime', 'hasViabilityReport', 'operatorName'],
}

/** 字段标签（逐字对甲方模板列名；代数是甲方 9-24 加的） */
export const SAMPLE_LABEL: Record<SampleFieldKey, string> = {
  sourceUnitName: '来源单位',
  donorName: '供体姓名',
  gender: '性别',
  age: '年龄',
  hospitalNo: '住院号',
  tissueType: '组织类型',
  organoidType: '类器官类型',
  passage: '代数',
  hasPathology: '有无病理',
  remark: '备注',
  receiveDate: '收样日期',
  internalNo: '内部编号',
  isFixed: '有无固定',
  processTime: '处理时间',
  hasQcSheet: '质控表',
  hasViabilityReport: '细胞活率报告',
  operatorName: '操作人',
}

export type SampleControl = 'text' | 'date' | 'datetime' | 'select' | 'seg' | 'textarea'

const SAMPLE_CONTROL: Partial<Record<SampleFieldKey, SampleControl>> = {
  sourceUnitName: 'select',
  gender: 'seg',
  hasPathology: 'seg',
  isFixed: 'seg',
  hasQcSheet: 'seg',
  hasViabilityReport: 'seg',
  receiveDate: 'date',
  processTime: 'datetime',
  remark: 'textarea',
}

/** 字段的控件类型（没登记的按文本） */
export function sampleControl(key: SampleFieldKey): SampleControl {
  return SAMPLE_CONTROL[key] ?? 'text'
}

/** 可输入字段的字数上限（与后端 `SubmitSegmentRules` / 库里列长同一口径） */
const SAMPLE_MAXLENGTH: Partial<Record<SampleFieldKey, number>> = {
  sourceUnitName: 100,
  donorName: 50,
  age: 20,
  hospitalNo: 50,
  tissueType: 100,
  organoidType: 100,
  passage: 10,
  remark: 500,
  internalNo: 64,
  operatorName: 50,
}

export function sampleMaxlength(key: SampleFieldKey): number | undefined {
  return SAMPLE_MAXLENGTH[key]
}

/** 无效原因字数上限（`t_lqg_sample.invalid_reason` VARCHAR(200)） */
export const MAX_INVALID_REASON = 200

export type VerifyAction = 'valid' | 'invalid'

/**
 * 必填小星号：送检段的来源单位、组织类型 / 类器官类型（两个动作都要，后端校验送检段就查它们）；
 * 收样日期、内部编号只在判有效时必填（判无效不收收样信息）。
 */
export function sampleRequired(kind: SampleKind, key: SampleFieldKey, action: VerifyAction = 'valid'): boolean {
  if (key === 'sourceUnitName') {
    return true
  }
  if (key === (kind === 'organoid' ? 'organoidType' : 'tissueType')) {
    return true
  }
  return action === 'valid' && (key === 'receiveDate' || key === 'internalNo')
}

function str(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

/** 详情 → 表单值；操作人没填过就默认当前登录人（可改，与工作台核验入参的口径一致） */
export function toVerifySampleForm(detail: Record<string, unknown> | null | undefined, me = ''): VerifySampleForm {
  const d = detail ?? {}
  const unitId = d.sourceUnitId
  return {
    sourceUnitId: unitId === null || unitId === undefined || unitId === '' ? null : unitId as string | number,
    sourceUnitName: str(d.sourceUnitName),
    donorName: str(d.donorName),
    gender: str(d.gender),
    age: str(d.age),
    hospitalNo: str(d.hospitalNo),
    tissueType: str(d.tissueType),
    organoidType: str(d.organoidType),
    passage: str(d.passage),
    hasPathology: str(d.hasPathology),
    remark: str(d.remark),
    receiveDate: str(d.receiveDate).slice(0, 10),
    internalNo: str(d.internalNo),
    isFixed: str(d.isFixed),
    processTime: str(d.processTime),
    hasQcSheet: str(d.hasQcSheet),
    hasViabilityReport: str(d.hasViabilityReport),
    operatorName: str(d.operatorName) || me,
  }
}

/** 详情的类别：只认 organoid，其余一律按组织样本（与后端 `SampleKindRules` 同口径） */
export function kindOf(detail: { sampleKind?: unknown } | null | undefined): SampleKind {
  return detail?.sampleKind === 'organoid' ? 'organoid' : 'tissue'
}

/** 代数的规范形：去首尾空白、小写 p 转大写（与后端同一口径，后端才是权威） */
export function normalizePassage(value: string): string {
  const v = str(value).trim()
  return /^p/.test(v) ? `P${v.slice(1)}` : v
}

/** 代数格式：空（选填）或 P 加 1-3 位数字 */
export function passageOk(value: string): boolean {
  const v = normalizePassage(value)
  return v === '' || /^P\d{1,3}$/.test(v)
}

/** 比较用的规范形：去首尾空白（全空白 = 没填）；代数先规范化 */
function canon(key: SampleFieldKey, value: string): string {
  return key === 'passage' ? normalizePassage(value) : str(value).trim()
}

/**
 * 送检段里**改过的**键（按这一类的字段，顺序 = 页面顺序）。
 * 来源单位看「名字」与「选中的单位 id」两样：换了一个同名的单位也算改。
 */
export function submitChanges(kind: SampleKind, before: VerifySampleForm, now: VerifySampleForm): SampleFieldKey[] {
  return SUBMIT_FIELDS[kind].filter((key) => {
    if (key === 'sourceUnitName' && str(before.sourceUnitId) !== str(now.sourceUnitId)) {
      return true
    }
    return canon(key, before[key]) !== canon(key, now[key])
  })
}

/**
 * 送检段（整段替换，见文件头）：本类的全部送检字段 + 来源单位 id。
 * 代数只在「详情里有这个键」或「这次改过」时带。
 */
export function submitSegmentOf(kind: SampleKind, now: VerifySampleForm, withPassage: boolean): Record<string, unknown> {
  const seg: Record<string, unknown> = {
    sourceUnitId: now.sourceUnitId,
    sourceUnitName: now.sourceUnitName.trim(),
  }
  if (kind === 'organoid') {
    seg.organoidType = now.organoidType.trim()
    if (withPassage) {
      seg.passage = normalizePassage(now.passage) || null
    }
  }
  else {
    seg.donorName = now.donorName.trim()
    seg.gender = now.gender || null
    seg.age = now.age.trim()
    seg.hospitalNo = now.hospitalNo.trim()
    seg.tissueType = now.tissueType.trim()
    seg.hasPathology = now.hasPathology || null
  }
  seg.remark = now.remark.trim()
  return seg
}

/**
 * 核验请求体（`PUT /lqg/sample/{id}/verify`）。
 *
 * @param detailHasPassage 详情里有没有 `passage` 这个键（后端加列之后才有）
 * - 判有效：收样日期 + 内部编号 + 本类其余收样项（空着的发 null = 后端不动）；
 * - 判无效：只带原因（收样信息判无效不收）；
 * - 两个动作都一样：送检段改过才带 `submitSegment`（整段）。
 */
export function sampleVerifyBody(
  kind: SampleKind,
  action: VerifyAction,
  before: VerifySampleForm,
  now: VerifySampleForm,
  reason = '',
  detailHasPassage = false,
): Record<string, unknown> {
  const body: Record<string, unknown> = { action }
  if (action === 'valid') {
    body.receiveDate = now.receiveDate || null
    body.internalNo = now.internalNo.trim()
    RECEIVE_FIELDS[kind]
      .filter(key => key !== 'receiveDate' && key !== 'internalNo')
      .forEach((key) => {
        const value = key === 'processTime' ? now.processTime.replace('T', ' ') : now[key].trim()
        body[key] = value || null
      })
  }
  else {
    body.reason = reason.trim()
  }
  const changed = submitChanges(kind, before, now)
  if (changed.length > 0) {
    body.submitSegment = submitSegmentOf(kind, now, detailHasPassage || changed.includes('passage'))
  }
  return body
}

/**
 * 提交前自检（'' = 没问题）。只查必填与格式的前半段，唯一性等以后端为准。
 * 送检段两个动作都查（改过的送检信息会一起保存，后端同样会查）。
 */
export function sampleProblem(kind: SampleKind, action: VerifyAction, now: VerifySampleForm, reason = ''): string {
  if (!now.sourceUnitName.trim()) {
    return '请选择来源单位'
  }
  if (kind === 'organoid' && !now.organoidType.trim()) {
    return '请填类器官类型'
  }
  if (kind === 'tissue' && !now.tissueType.trim()) {
    return '请填组织类型'
  }
  if (kind === 'organoid' && !passageOk(now.passage)) {
    return '代数请填 P 加数字，如 P3'
  }
  if (action === 'valid') {
    if (!now.receiveDate) {
      return '请选收样日期'
    }
    if (!now.internalNo.trim()) {
      return '请填内部编号'
    }
    return ''
  }
  const text = reason.trim()
  if (!text) {
    return '请写判为无效的原因'
  }
  if (Array.from(text).length > MAX_INVALID_REASON) {
    return `无效原因不能超过 ${MAX_INVALID_REASON} 字`
  }
  return ''
}

// ── 石蜡包埋送样 ────────────────────────────────────────────────────────────

/** 合作单位送样时填的两项：判有效、判无效都能一起改（与后端 `EmbedFillRules`、工作台 `verifyFill.ts` 一致） */
export const EMBED_SUBMIT_KEYS = ['sampleType', 'organoidSourceType'] as const

/** 实验室补填的 13 项：只随「判为有效并保存」一起保存（判无效后端不收，带了回 400） */
export const EMBED_LAB_KEYS = [
  'tissueReceiveTime',
  'tissueProcessTime',
  'agaroseEmbedTime',
  'embedBy',
  'dehydrateTime',
  'agaroseSendTime',
  'paraffinEmbedTime',
  'sectionTime',
  'stainTypes',
  'stainOther',
  'markers',
  'operatorName',
  'remark',
] as const

export type EmbedFillKey = (typeof EMBED_SUBMIT_KEYS)[number] | (typeof EMBED_LAB_KEYS)[number]

/** 补填项的中文名（逐字对模板 C 的列名，与填写页同一组） */
export const EMBED_LABEL: Record<EmbedFillKey, string> = {
  sampleType: '样本类型',
  organoidSourceType: '类器官来源类型',
  tissueReceiveTime: '组织收样时间',
  tissueProcessTime: '组织处理时间',
  agaroseEmbedTime: '琼脂糖包埋样本时间',
  embedBy: '包埋人',
  dehydrateTime: '脱水时间',
  agaroseSendTime: '琼脂糖包埋样本送样时间',
  paraffinEmbedTime: '石蜡包埋时间',
  sectionTime: '切片时间',
  stainTypes: '染色',
  stainOther: '染色具体名称',
  markers: 'mark 的表达情况',
  operatorName: '操作人',
  remark: '备注',
}

const EMBED_ALL_KEYS: EmbedFillKey[] = [...EMBED_SUBMIT_KEYS, ...EMBED_LAB_KEYS]

/** 提交用的值：文本去空白（空 = null，补丁语义下即清空）、染色是数组、marker 丢掉整行空的 */
function fillValue(form: EmbedFormValue, key: EmbedFillKey): unknown {
  if (key === 'stainTypes') {
    return [...form.stainTypes]
  }
  if (key === 'markers') {
    return form.markers
      .filter(row => row.markerName.trim() || row.expression)
      .map(row => ({ markerName: row.markerName.trim(), expression: row.expression }))
  }
  const text = str(form[key]).trim()
  return text || null
}

/** 补填段里改过的键（顺序 = 两项 + 13 项的顺序） */
export function embedChanges(before: EmbedFormValue, now: EmbedFormValue): EmbedFillKey[] {
  return EMBED_ALL_KEYS.filter(key => JSON.stringify(fillValue(before, key)) !== JSON.stringify(fillValue(now, key)))
}

/**
 * 判为无效时**不会保存**的那几项（改过的实验室补填项的中文名）：页面在原因面板里明说，不静默丢
 * （同工作台 `EmbedDrawer` 的判无效提示）。
 */
export function embedDroppedOnInvalid(before: EmbedFormValue, now: EmbedFormValue): string[] {
  return embedChanges(before, now)
    .filter(key => (EMBED_LAB_KEYS as readonly string[]).includes(key))
    .map(key => EMBED_LABEL[key])
}

/**
 * 核验请求体（`PUT /lqg/embed/{id}/verify`）。`fill` 是补丁：只带改过的键；一项没改就不带。
 * - 判有效：石蜡块编号 + 改过的补填项（15 项里任意）；
 * - 判无效：原因 + 改过的「样本类型 / 类器官来源类型」（实验室补填项判无效不收）。
 */
export function embedVerifyBody(
  action: VerifyAction,
  before: EmbedFormValue,
  now: EmbedFormValue,
  reason = '',
): Record<string, unknown> {
  const body: Record<string, unknown> = { action }
  let keys = embedChanges(before, now)
  if (action === 'valid') {
    body.paraffinBlockNo = now.paraffinBlockNo.trim()
  }
  else {
    body.reason = reason.trim()
    keys = keys.filter(key => (EMBED_SUBMIT_KEYS as readonly string[]).includes(key))
  }
  if (keys.length > 0) {
    const fill: Record<string, unknown> = {}
    keys.forEach((key) => {
      fill[key] = fillValue(now, key)
    })
    body.fill = fill
  }
  return body
}

/**
 * 提交前自检（'' = 没问题）。
 * @param sampleValid 所挂样本是不是已核验有效（不是 → 判有效直接挡住，后端同样会拒）
 */
export function embedProblem(action: VerifyAction, now: EmbedFormValue, sampleValid: boolean, reason = ''): string {
  if (action === 'valid') {
    if (!sampleValid) {
      return '所挂样本还没核验有效，先核验样本'
    }
    if (!now.paraffinBlockNo.trim()) {
      return '请填石蜡块编号'
    }
    return stainProblem(now.stainTypes, now.stainOther)
  }
  const text = reason.trim()
  if (!text) {
    return '请写判为无效的原因'
  }
  if (Array.from(text).length > MAX_INVALID_REASON) {
    return `无效原因不能超过 ${MAX_INVALID_REASON} 字`
  }
  return ''
}
