// 类器官收样填写页布局的**纯函数**（SAMPLE-MP-002 §2 / UI:mp.organoid.form）。
//
// 权威：UI:mp.organoid.form（CR-20260917-05 外部可填、CR-20260918-07 只读页右上角「修改」）；
// 期望值全部在 `doc/verify/fixtures/organoid-form-cases.json` 里，本文件不自带期望表，
// `layout.fixture.spec.ts` 逐例对着 fixture 断。
//
// 四件最容易做反的事（ticket §0 与 fixture 的 `_doc`）：
//
// 1. **外部永远没有收样段**——不是置灰、不是 `v-show=false`：字段名一旦进 `fields`，
//    它就会被渲染进外部的包。所以这里给外部**只返回那四项**（送检信息）。
// 2. **`editable` 由这个纯函数给**（identity / verifyStatus / mine / mode 四件套）：
//    fixture 逐例断的就是它。外部送来还没核验的样本在内部侧恒不可改（核验在工作台）。
// 3. **`mode=view`（内部管理点一行进来）一律只读**，本人录的也只读；
//    只读页右上角的「修改」把 mode 切成 `edit` 之后**重算**这个函数才决定能不能改
//    —— 「按钮显不显示」与「能不能改」同源（CR-20260918-07）。
// 4. **身份缺失什么都不渲染**（不是「默认当内部」）：`fields` 为空、`editable=false`。
//
// 类器官收样记录是模板 B 的 7 列（REQ-SAMPLE-007）+「代数」：内部八项全部可填；
// 外部四项（Kevin 2026-09-17 晚定三项，CR-20260917-05；+ 代数）提交后待核验。
//
// ★「代数」（`passage`）不是模板 B 的列：甲方 2026-09-24 测试问题记录表第 18 行要求
//   「合作单位和内部人员的都要再添加一项：代数」（CR-20260924-10）。它紧跟「类器官类型」，
//   属于送检信息（外部能填、能改），fixture 里记在 `inserted`，不混进模板列。
// ★「种属」（`species`）同理：甲方 2026-10-09 要求（CR-20261009-18），紧跟「来源单位」，内外部都填、必填；
//   常用值从字典来（人 / 鼠兔 / 移植猪 / 鸡），列表里没有的可以手填（SpeciesSheet）。
import type { ResolvedIdentity } from '@/types/identity'
import { normalizeIdentity } from '@/types/identity'

/** 三种入口模式；除此之外（含缺失 / 空串 / 不认识）一律按只读处理 */
export type OrganoidMode = 'new' | 'edit' | 'view'

/** 外部五项（fixture 的 `externalFields`，顺序即显示顺序）：来源单位、种属、类器官类型、代数、备注 */
export const EXTERNAL_FIELDS = ['sourceUnitName', 'species', 'organoidType', 'passage', 'remark'] as const

/**
 * 内部九项（fixture 的 `internalFields`，顺序即显示顺序 = 模板 B 的列序，「来源单位」后插入「种属」
 * （CR-20261009-18）、「类器官类型」后插入「代数」）
 */
export const INTERNAL_FIELDS = [
  'sourceUnitName',
  'species',
  'organoidType',
  'passage',
  'receiveDate',
  'internalNo',
  'processTime',
  'hasViabilityReport',
  'operatorName',
] as const

export type OrganoidFieldKey = (typeof INTERNAL_FIELDS)[number] | (typeof EXTERNAL_FIELDS)[number]

/** 收样段字段（**只有内部**看得到）：内部九项里除去「来源单位 / 种属 / 类器官类型 / 代数」之外的五项 */
export const RECEIVE_FIELDS = [
  'receiveDate',
  'internalNo',
  'processTime',
  'hasViabilityReport',
  'operatorName',
] as const

/** 布局结果：渲染哪些字段（顺序即显示顺序）、能不能改 */
export interface OrganoidLayout {
  fields: OrganoidFieldKey[]
  editable: boolean
}

/** 入口模式归一化：只认三个字面量，其余（含 undefined / '' / 'EDIT'）都当只读的 `view` */
export function normalizeMode(raw: unknown): OrganoidMode {
  return raw === 'new' || raw === 'edit' || raw === 'view' ? raw : 'view'
}

// 类器官收样填写页布局。
//
// @param identity     只认后端 `/mp/me` 的 `identity`（缺失 / 空 / 不认识 → 空布局）
// @param verifyStatus 详情的核验状态；`null` = 新增（还没有这条记录）
// @param mine         这一行是不是当前用户提交的（详情的 `mine`；新增时按 true 传）
// @param mode         入口模式（`new` / `edit` / `view`；其余按只读）
export function organoidLayout(
  identity: unknown,
  verifyStatus: string | null,
  mine: boolean,
  mode: unknown,
): OrganoidLayout {
  const resolved: ResolvedIdentity = normalizeIdentity(identity)
  // 口径 4：身份未知 → 什么都不渲染（一个字段都不给，更不给「可写」）
  if (resolved === null) {
    return { fields: [], editable: false }
  }

  const entry = normalizeMode(mode)
  const isInternal = resolved === 'internal'
  // 口径 1：外部连收样段的字段名都不出现
  const fields: OrganoidFieldKey[] = isInternal ? [...INTERNAL_FIELDS] : [...EXTERNAL_FIELDS]

  // 口径 3：内部管理点一行进来（view）一律只读；「修改」是把 mode 换成 edit 再算一次
  if (entry === 'view') {
    return { fields, editable: false }
  }
  // 新增：内外部都能填（外部四项 → pending，内部八项 → valid）
  if (entry === 'new') {
    return { fields, editable: true }
  }

  // edit：内部 = 有效样本（谁录的都能改）；外部 = 本人 + 待核验 / 无效
  if (isInternal) {
    return { fields, editable: verifyStatus === 'valid' }
  }
  return {
    fields,
    editable: mine && (verifyStatus === 'pending' || verifyStatus === 'invalid'),
  }
}

/** 这一页要不要出「收样信息」这一组：外部一个字段都没有时，连组标题都不渲染 */
export function hasReceiveGroup(layout: OrganoidLayout): boolean {
  return layout.fields.some(key => (RECEIVE_FIELDS as readonly string[]).includes(key))
}

/** 字段的呈现方式：文本 / 数字（字符串）输入 / 底部弹框选日期 / 按钮组 / 多行文本框 / 选择器 */
export type OrganoidControl = 'text' | 'date' | 'datetime' | 'seg' | 'textarea' | 'select'

/** 一个可渲染的字段：key + 标签 + 控件类型 + 是否可改（整页一个答案） */
export interface OrganoidFieldSpec {
  key: OrganoidFieldKey
  label: string
  control: OrganoidControl
  editable: boolean
}

/** 字段的中文标签（**逐字对甲方模板 B 的列名**，「代数」是甲方后加的；顺序与 INTERNAL_FIELDS 一致） */
const LABELS: Record<OrganoidFieldKey, string> = {
  sourceUnitName: '来源单位',
  species: '种属',
  organoidType: '类器官类型',
  passage: '代数',
  receiveDate: '收样日期',
  internalNo: '内部编号',
  processTime: '处理时间',
  hasViabilityReport: '细胞活率报告',
  operatorName: '操作人',
  remark: '备注',
}

/** 控件类型（落地规范 §5.4 / §5.5：日期与选择走底部弹框，按钮组不换成下拉或开关） */
const CONTROLS: Partial<Record<OrganoidFieldKey, OrganoidControl>> = {
  sourceUnitName: 'select',
  species: 'select',
  receiveDate: 'date',
  processTime: 'datetime',
  hasViabilityReport: 'seg',
  remark: 'textarea',
}

/** 字段中文标签 */
export function fieldLabel(key: OrganoidFieldKey): string {
  return LABELS[key]
}

/** 可输入字段最多几个字（与后端校验、库里的列长同一口径）；来源单位的手填名在单位面板里（100） */
const MAXLENGTH: Partial<Record<OrganoidFieldKey, number>> = {
  species: 50,
  organoidType: 100,
  // 代数形如 P3 / P12 / P999（与冻存批次同一规则 ^P\d{1,3}$），最长 4 个字
  passage: 4,
  internalNo: 64,
  operatorName: 50,
  remark: 500,
}

export function fieldMaxlength(key: OrganoidFieldKey): number | undefined {
  return MAXLENGTH[key]
}

/** 布局 → 可渲染字段清单：顺序就是显示顺序，`editable` 是**整页可写性**的统一答案 */
export function fieldSpecs(layout: OrganoidLayout, editable: boolean): OrganoidFieldSpec[] {
  return layout.fields.map(key => ({
    key,
    label: LABELS[key],
    control: CONTROLS[key] ?? 'text',
    editable,
  }))
}

/** 取数：`/mp/dict/hints?type=organoid` 的联想词类型（类器官类型那一格用） */
export const ORGANOID_HINT_TYPE = 'organoid'

// ── 代数（CR-20260924-10）──────────────────────────────────────────────────

/** 代数格式不对时的提示（与后端 `SubmitSegmentRules.MSG_PASSAGE` 同一句） */
export const PASSAGE_HINT = '代数请填 P 加数字，如 P3'

/**
 * 代数的提交值：去首尾空白、开头的小写 p 转大写；空 = 没填（选填）。
 * 与后端 `SubmitSegmentRules.normalizePassage` 同一个归一化 —— 前端先转好，用户在只读页看到的就是落库的样子。
 */
export function normalizePassage(raw: unknown): string {
  const value = raw === null || raw === undefined ? '' : String(raw).trim()
  return value.startsWith('p') ? `P${value.slice(1)}` : value
}

/**
 * 代数的问题（空串 = 没问题）：选填；填了就必须形如 P3（`P` + 1~3 位数字，与冻存批次的代数同一规则）。
 * 前端先拦一次给人话提示；后端同一规则兜底（格式不对 400「代数请填 P 加数字，如 P3」）。
 */
export function passageProblem(raw: unknown): string {
  const value = normalizePassage(raw)
  if (!value) {
    return ''
  }
  return /^P\d{1,3}$/.test(value) ? '' : PASSAGE_HINT
}
