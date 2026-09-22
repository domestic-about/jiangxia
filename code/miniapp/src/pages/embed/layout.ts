// 石蜡包埋送样记录填写页布局的**纯函数**（EMBED-MP-001 §2 / UI:mp.embed.form）。
//
// 权威：UI:mp.embed.form（CR-20260917-05 外部可填、CR-20260918-07 只读页右上角「修改」）；
// 期望值全部在 `doc/verify/fixtures/embed-form-cases.json` 里，本文件不自带期望表，
// `layout.fixture.spec.ts` 逐例对着 fixture 断（fixture 一个字节没改）。
//
// 五件最容易做反的事（ticket §0 与 fixture 的 `_doc`）：
//
// 1. **外部只有三项**——不是置灰、不是 `v-show=false`：字段名一旦进 `fields`，它就会被渲染进
//    外部的包（accept 的 counterfeit 点名「外部复用内部全字段表单再隐藏」）。石蜡块编号、工序时间、
//    染色、marker、包埋人、操作人、备注在外面**一个都不出现**。
// 2. **`editable` 由这个纯函数给**（identity / verifyStatus / mine / mode 四件套），fixture
//    逐例断的就是它。外部送来还没核验 / 判无效的送样在内部侧恒不可改（核验在工作台，绕不过去）。
// 3. **`mode=view`（内部管理点一行进来）一律只读**，本人录的也只读。只读页右上角的「修改」把
//    mode 切成 `edit` 之后**重算**这个函数才决定能不能改 —— 「按钮显不显示」与「能不能改」同源。
// 4. **`showEditEntry`**（CR-20260918-07 新增的返回键，fixture 里没有、也不改 fixture：
//    它只可能是 `mode=view && identity=internal && 这条记录内部可改`）。
// 5. **身份缺失什么都不渲染**（不是「默认当内部」）：`fields` 为空、`editable=false`。
//
// 外部有效后只读并出「包埋卡片」（`showCard`）—— 复用 AUTH-EXT-002 的 `EmbedCard.vue`。
import type { ResolvedIdentity } from '@/types/identity'
import { normalizeIdentity } from '@/types/identity'

/** 三种入口模式；除此之外（含缺失 / 空串 / 不认识）一律按只读处理 */
export type EmbedMode = 'new' | 'edit' | 'view'

/** 外部三项（fixture 的 `externalFields`，顺序即显示顺序） */
export const EXTERNAL_FIELDS = ['sampleId', 'sampleType', 'organoidSourceType'] as const

/** 内部 16 项（fixture 的 `internalFields`，顺序即显示顺序 = 甲方模板 C 的列序） */
export const INTERNAL_FIELDS = [
  'sampleId',
  'paraffinBlockNo',
  'sampleType',
  'organoidSourceType',
  'tissueReceiveTime',
  'tissueProcessTime',
  'agaroseEmbedTime',
  'embedBy',
  'dehydrateTime',
  'agaroseSendTime',
  'paraffinEmbedTime',
  'sectionTime',
  'stainTypes',
  'markers',
  'operatorName',
  'remark',
] as const

export type EmbedFieldKey = (typeof INTERNAL_FIELDS)[number] | (typeof EXTERNAL_FIELDS)[number]

/** 七个工序时间（FLOW:F-EMBED-01.step2；全部可空、做完一步填一步） */
export const PROCESS_TIME_FIELDS = [
  'tissueReceiveTime',
  'tissueProcessTime',
  'agaroseEmbedTime',
  'dehydrateTime',
  'agaroseSendTime',
  'paraffinEmbedTime',
  'sectionTime',
] as const

/** 布局结果：渲染哪些字段（顺序即显示顺序）、能不能改、出不出包埋卡片、出不出「修改」 */
export interface EmbedLayout {
  fields: EmbedFieldKey[]
  editable: boolean
  /** 外部看自己那条已核验有效的送样：只读 + 出包埋卡片（复用 EmbedCard） */
  showCard: boolean
  /** 只读详情右上角的「修改」（CR-20260918-07） */
  showEditEntry: boolean
}

/** 入口模式归一化：只认三个字面量，其余（含 undefined / '' / 'EDIT'）都当只读的 `view` */
export function normalizeMode(raw: unknown): EmbedMode {
  return raw === 'new' || raw === 'edit' || raw === 'view' ? raw : 'view'
}

/** 这一行在内部侧能不能改：只有已核验有效的记录（核验在工作台） */
export function isInternalEditable(verifyStatus: string | null): boolean {
  return verifyStatus === 'valid'
}

// 石蜡包埋填写页布局。
//
// @param identity     只认后端 `/mp/me` 的 `identity`（缺失 / 空 / 不认识 → 空布局）
// @param verifyStatus 详情的核验状态；`null` = 新增（还没有这条记录）
// @param mine         这一行是不是当前用户提交的（详情的 `mine`；新增时按 true 传）
// @param mode         入口模式（`new` / `edit` / `view`；其余按只读）
export function embedLayout(
  identity: unknown,
  verifyStatus: string | null,
  mine: boolean,
  mode: unknown,
): EmbedLayout {
  const resolved: ResolvedIdentity = normalizeIdentity(identity)
  // 口径 5：身份未知 → 什么都不渲染（一个字段都不给，更不给「可写」）
  if (resolved === null) {
    return { fields: [], editable: false, showCard: false, showEditEntry: false }
  }

  const entry = normalizeMode(mode)
  const isInternal = resolved === 'internal'
  // 口径 1：外部只有三项（内部字段名根本不进 fields）
  const fields: EmbedFieldKey[] = isInternal ? [...INTERNAL_FIELDS] : [...EXTERNAL_FIELDS]
  // 口径：外部看自己那条「已核验有效」的送样 → 只读 + 包埋卡片
  const showCard = !isInternal && verifyStatus === 'valid'

  if (entry === 'view') {
    // 口径 3：内部管理点一行进来一律只读；「修改」是把 mode 换成 edit 再算一次
    return {
      fields,
      editable: false,
      showCard,
      // 口径 4：只读详情右上角的「修改」—— 内部 + 这条记录内部可改
      showEditEntry: isInternal && isInternalEditable(verifyStatus),
    }
  }
  if (entry === 'new') {
    // 新增：内外部都能填（外部三项 → pending，内部全字段 → valid）
    return { fields, editable: true, showCard, showEditEntry: false }
  }

  // edit：内部 = 已核验有效的记录（谁录的都能改，CR-20260918-07）；
  //       外部 = 本人 + 待核验 / 无效（有效之后只读）
  const editable = isInternal
    ? isInternalEditable(verifyStatus)
    : mine && (verifyStatus === 'pending' || verifyStatus === 'invalid')
  return { fields, editable, showCard, showEditEntry: false }
}

/**
 * 只读页右上角「修改」点下去之后能不能真的改（= 把 mode 换成 `edit` 重算一次）。
 *
 * 与 {@link embedLayout} 同源：这里不另写一份判据，免得出现「按钮出来了、点进去还是只读」。
 */
export function canEditFromView(identity: unknown, verifyStatus: string | null, mine: boolean): boolean {
  return embedLayout(identity, verifyStatus, mine, 'edit').showEditEntry
    || (normalizeIdentity(identity) === 'internal' && embedLayout(identity, verifyStatus, mine, 'edit').editable)
}

/** 字段的呈现方式：文本 / 底部弹框选日期 / 选择器（选样本）/ 染色五按钮 / marker 多行 / 多行文本 */
export type EmbedControl = 'text' | 'date' | 'select' | 'stain' | 'markers' | 'textarea'

/** 一个可渲染的字段：key + 标签 + 控件类型 + 是否可改（整页一个答案） */
export interface EmbedFieldSpec {
  key: EmbedFieldKey
  label: string
  control: EmbedControl
  editable: boolean
}

/** 字段的中文标签（**逐字对甲方模板 C 的列名**；顺序与 INTERNAL_FIELDS 一致） */
const LABELS: Record<EmbedFieldKey, string> = {
  sampleId: '选择样本',
  paraffinBlockNo: '石蜡块编号',
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
  markers: 'mark 的表达情况',
  operatorName: '操作人',
  remark: '备注',
}

/** 控件类型（落地规范 §5.4 / §5.5：日期与选择走底部弹框，按钮组不换成下拉或开关） */
const CONTROLS: Partial<Record<EmbedFieldKey, EmbedControl>> = {
  sampleId: 'select',
  tissueReceiveTime: 'date',
  tissueProcessTime: 'date',
  agaroseEmbedTime: 'date',
  dehydrateTime: 'date',
  agaroseSendTime: 'date',
  paraffinEmbedTime: 'date',
  sectionTime: 'date',
  stainTypes: 'stain',
  markers: 'markers',
  remark: 'textarea',
}

/** 字段中文标签 */
export function fieldLabel(key: EmbedFieldKey): string {
  return LABELS[key]
}

/** 布局 → 可渲染字段清单：顺序就是显示顺序，`editable` 是**整页可写性**的统一答案 */
export function fieldSpecs(layout: EmbedLayout, editable: boolean): EmbedFieldSpec[] {
  return layout.fields.map(key => ({
    key,
    label: LABELS[key],
    control: CONTROLS[key] ?? 'text',
    editable,
  }))
}

/** 这一页要不要出「选择样本」（外部只有三项，样本是它的第一格；内部同理） */
export function hasSamplePicker(layout: EmbedLayout): boolean {
  return layout.fields.includes('sampleId')
}

/** 表单上的分组（**只影响排版**，不改 `fields` 的顺序 —— fixture 断的是 fields） */
export type EmbedGroupKey = 'sample' | 'process' | 'stain' | 'other'

const GROUP_OF: Record<EmbedFieldKey, EmbedGroupKey> = {
  sampleId: 'sample',
  paraffinBlockNo: 'sample',
  sampleType: 'sample',
  organoidSourceType: 'sample',
  tissueReceiveTime: 'process',
  tissueProcessTime: 'process',
  agaroseEmbedTime: 'process',
  embedBy: 'process',
  dehydrateTime: 'process',
  agaroseSendTime: 'process',
  paraffinEmbedTime: 'process',
  sectionTime: 'process',
  stainTypes: 'stain',
  markers: 'stain',
  operatorName: 'other',
  remark: 'other',
}

export const GROUP_TITLE: Record<EmbedGroupKey, string> = {
  sample: '送检信息',
  process: '工序时间',
  stain: '染色与 marker',
  other: '其他',
}

/** 把字段清单按分组切开（组内保持 `fields` 的原顺序） */
export function groupSpecs(specs: EmbedFieldSpec[]): Array<{ key: EmbedGroupKey, title: string, specs: EmbedFieldSpec[] }> {
  const order: EmbedGroupKey[] = ['sample', 'process', 'stain', 'other']
  return order
    .map(key => ({
      key,
      title: GROUP_TITLE[key],
      specs: specs.filter(spec => GROUP_OF[spec.key] === key),
    }))
    .filter(group => group.specs.length > 0)
}

/** 取数：`/mp/dict/hints?type=sample` 的联想词类型（样本类型那一格用） */
export const SAMPLE_HINT_TYPE = 'sample'
