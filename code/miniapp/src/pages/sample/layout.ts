// 填写页布局的**纯函数**（SAMPLE-MP-001 §2）。
//
// 权威：UI:mp.sample.form / UI:mp.sample.detail.ext（CR-20260917-05 / CR-20260918-07）；
// 期望值全部在 `doc/verify/fixtures/sample-form-cases.json` 里，本文件不自带期望表，
// `layout.fixture.spec.ts` 逐例对着 fixture 断。
//
// 四件最容易做反的事（ticket §0 的口径复述 1-4）：
//
// 1. **外部永远不渲染收样段**——不是置灰、不是 `v-show=false`：字段名一旦进 `fields`，
//    它就会被渲染进外部的包，用禁字 grep 就能把它抓出来。所以这里给外部**只返回送检段**。
// 2. **`editable` 以后端详情为准**：本函数只给「按身份 / 状态 / 是不是我的 / 入口模式」
//    推出来的**结构结论**；页面上真正的那一个布尔值由 `resolveEditable(layout, serverEditable)`
//    合成——详情接口给了就用它（同组别人的样本可看不可改就是这么落地的），
//    没给（新增那一次）才用本函数的结论。
// 3. **入口模式决定可写性**：`new` = 首页点表新增；`edit` = 历史编辑记录点一条进来；
//    `view` = 内部管理表格页点一行进来（**一律只读**，本人录的也只读）。
//    `mode` 缺失或不认识 → 按只读，**绝不按可改**。
// 4. **身份缺失什么都不渲染**（不是「默认当内部」）：`fields` 为空、`editable=false`。
//
// 状态口径：外部 = 本人 + 待核验 / 无效才可改（有效的整页只读）；内部 = 有效样本谁录的都能改
// （CR-20260918-07，不限本人），待核验与无效只读——核验与改判在网页工作台。

import type { ResolvedIdentity } from '@/types/identity'
import { normalizeIdentity } from '@/types/identity'

/** 三种入口模式；除此之外（含缺失 / 空串 / 不认识）一律按只读处理 */
export type FormMode = 'new' | 'edit' | 'view'

/** 送检段字段 key，数组顺序即显示顺序（fixture 的 `sendFields`） */
export const SEND_FIELDS = [
  'sourceUnitName',
  'donorName',
  'gender',
  'age',
  'hospitalNo',
  'tissueType',
  'hasPathology',
  'remark',
] as const

/** 收样段字段 key（fixture 的 `receiveFields`；**仅内部**可见可填） */
export const RECEIVE_FIELDS = [
  'receiveDate',
  'internalNo',
  'isFixed',
  'processTime',
  'hasQcSheet',
  'hasViabilityReport',
  'operatorName',
] as const

export type FormFieldKey = (typeof SEND_FIELDS)[number] | (typeof RECEIVE_FIELDS)[number]

/** 布局结果：渲染哪些字段（顺序即显示顺序）、能不能改、要不要出识别条 */
export interface FormLayout {
  fields: FormFieldKey[]
  editable: boolean
  showOcr: boolean
}

/** 入口模式归一化：只认三个字面量，其余（含 undefined / '' / 'EDIT'）都当只读的 `view` */
export function normalizeMode(raw: unknown): FormMode {
  return raw === 'new' || raw === 'edit' || raw === 'view' ? raw : 'view'
}

// 按后端详情合成「这一页能不能改」。
//
// ★ 两条判据**相乘**，不是后者覆盖前者：
// 1. 入口模式是硬闸 —— `view`（内部管理点一行进来）**一律只读**，本人录的也只读。
// 这一条来自 fixture 的「内部管理查看有效样本（本人的也只读）」；
// 2. 详情接口给的 `editable`（口径复述 2：同组别人的样本可看不可改，判据在服务端）。
//
// ★ 为什么不是「详情给了就用详情」：后端详情的 `editable` 说的是「这条记录**能不能改**」，
// 它不知道这一页是**从哪个入口**进来的。直接拿它覆盖 `view`，内部管理点一行就会冒出保存按钮
// （实测踩过）。反过来 `serverEditable=false` 一定压得住 `layout.editable=true`
// （外部看同组别人的待核验、内部看待核验 / 无效都是这一支）。
export function resolveEditable(layout: FormLayout, serverEditable: boolean | null | undefined): boolean {
  if (!layout.editable) {
    return false
  }
  if (typeof serverEditable === 'boolean') {
    return layout.editable && serverEditable
  }
  return layout.editable
}

// 填写页布局。
//
// @param identity    只认后端 `/mp/me` 的 `identity`（缺失 / 空 / 不认识 → 空布局）
// @param verifyStatus 详情的核验状态；`null` = 新增（还没有这条记录）
// @param mine        这一行是不是当前用户提交的（详情的 `mine`；新增时按 true 传）
// @param mode        入口模式（`new` / `edit` / `view`；其余按只读）
export function formLayout(
  identity: unknown,
  verifyStatus: string | null,
  mine: boolean,
  mode: unknown,
): FormLayout {
  const resolved: ResolvedIdentity = normalizeIdentity(identity)
  // 口径 4：身份未知 → 什么都不渲染（一个字段都不给，更不给「可写」）
  if (resolved === null) {
    return { fields: [], editable: false, showOcr: false }
  }

  const entry = normalizeMode(mode)
  const isInternal = resolved === 'internal'
  // 收样段只有内部看得到；外部连字段名都不该出现（口径 1）
  const fields: FormFieldKey[] = isInternal ? [...SEND_FIELDS, ...RECEIVE_FIELDS] : [...SEND_FIELDS]

  // 口径 3：内部管理进来的只读页一律不可改（本人录的也只读）
  if (entry === 'view') {
    return { fields, editable: false, showOcr: false }
  }
  // 新增：内外部都能填，顶部出识别条插槽（内容在 OCR-MP-001）
  if (entry === 'new') {
    return { fields, editable: true, showOcr: true }
  }

  // edit：按状态与「是不是我的」定可写性
  if (isInternal) {
    // 内部修改模式：有效样本可改（**不限本人录的**，CR-20260918-07）；
    // 待核验 / 无效的外部样本只读 —— 核验与改判在工作台。
    return { fields, editable: verifyStatus === 'valid', showOcr: false }
  }
  // 外部修改模式：本人 + 待核验 / 无效 → 可改后重提；有效、别人的 → 整页只读
  return {
    fields,
    editable: mine && (verifyStatus === 'pending' || verifyStatus === 'invalid'),
    showOcr: false,
  }
}

/** 字段的呈现方式：文本输入 / 数字（字符串）输入 / 底部弹框选日期 / 按钮组 / 多行文本框 */
export type FieldControl = 'text' | 'digit' | 'date' | 'datetime' | 'seg' | 'textarea'

/** 一个可渲染的字段：key + 标签 + 控件类型 + 是否可改（取不到详情时按不可改） */
export interface FieldSpec {
  key: FormFieldKey
  label: string
  control: FieldControl
  editable: boolean
}

/** 字段的中文标签（顺序与 SEND_FIELDS / RECEIVE_FIELDS 一致） */
const LABELS: Record<FormFieldKey, string> = {
  sourceUnitName: '来源单位',
  donorName: '供体姓名',
  gender: '性别',
  age: '年龄',
  hospitalNo: '住院号',
  tissueType: '组织类型',
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

/** 字段的控件类型（落地规范 §5.4 / §5.5：日期走底部弹框，按钮组不换成下拉或开关） */
const CONTROLS: Partial<Record<FormFieldKey, FieldControl>> = {
  gender: 'seg',
  hasPathology: 'seg',
  isFixed: 'seg',
  hasQcSheet: 'seg',
  hasViabilityReport: 'seg',
  receiveDate: 'date',
  processTime: 'datetime',
  remark: 'textarea',
  age: 'digit',
}

/** 字段中文标签（详情页与只读表单共用） */
export function fieldLabel(key: FormFieldKey): string {
  return LABELS[key]
}

// 布局 → 可渲染的字段清单：顺序就是显示顺序，`editable` 是**整页可写性**的统一答案
// （页面不再逐字段判断 —— 逐字段判断正是「同组别人的样本冒出一个提交按钮」的来源）。
export function fieldSpecs(layout: FormLayout, editable: boolean): FieldSpec[] {
  return layout.fields.map(key => ({
    key,
    label: LABELS[key],
    control: CONTROLS[key] ?? 'text',
    editable,
  }))
}

/** 表单是否要分「收样信息」组：外部一个字段都不渲染时这一组连标题都不出 */
export function hasReceiveGroup(layout: FormLayout): boolean {
  return layout.fields.some(key => (RECEIVE_FIELDS as readonly string[]).includes(key))
}
