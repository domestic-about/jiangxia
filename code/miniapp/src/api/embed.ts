// 石蜡包埋送样记录的接口层 + 表格页单元格格式化（EMBED-MP-001）。
//
// 契约：`doc/api-contract.md` 第 61 / 63 / 64 行。
//   内部：`GET /mp/int/embed/list`（搜索 keyword / 核验状态 / 染色 + `sort=recent`（历史编辑记录）
//         + `mine=true`（「只看我提交的」开关打开才带））、`GET /mp/int/embed/{id}`、
//         `POST /mp/int/embed`、`PUT /mp/int/embed`（**补丁**：只改传了的字段）
//   外部：`GET /mp/ext/embed/{id}`、`POST /mp/ext/embed`、`PUT /mp/ext/embed/{id}`、`GET /mp/ext/embed/list`
//
// ★ 分页响应是 `{code,msg,rows,total}`（没有 `data` 键）→ 一律带 `raw: true`（SAMPLE-MP-001 坑 3）。
// ★ `sort=recent`（CR-20260918-07）是**历史编辑记录**那一档的取数口：默认**中心全部内部人员**
//   （**不带 `mine`**），开关打开才**另外**带 `mine=true` 收窄到本人；行上带 `handlerName`
//   （经手人 = 最后修改人，没改过就是创建人）与 `mine`。表格页两个都不带（带了就不是全表）。
// ★ 外部 `PUT /mp/ext/embed/{id}` 是**覆盖两个字段**（AUTH-EXT-002 WARN-3：不传会被清成 null），
//   所以外部的提交体**永远两个字段都带**。
import type { EmbedMarker, EmbedRow, SampleRow } from '@/api/sample'
import type { LedgerFilters, LedgerRow } from '@/api/ledger'
import { PAGE_SIZE } from '@/utils/paging'
import { http } from '@/utils/request'
import { wasEdited } from '@/utils/edited'

// ── 行 / 详情形状 ────────────────────────────────────────────────────────────

/**
 * 内部接口的行 / 详情（`EmbedVo`）：比对外那份（`ExtEmbedVo`）多内部编号、来源单位、
 * 备注、提交来源、经手人与「最后修改」（契约第 61 / 63 行）。
 *
 * ★ `internalNo` 是**所挂样本的**内部编号（读时带出、不落库）—— 模板 C 的「样本编号」就是它。
 */
export interface EmbedDetail extends EmbedRow {
  internalNo?: string | null
  sourceUnitName?: string | null
  /** 所挂样本自己的核验状态（与这条送样的状态不是一回事） */
  sampleVerifyStatus?: string | null
  submitSource?: string | null
  remark?: string | null
  /** 经手人（= 最后修改人，没改过就是创建人） */
  handlerName?: string | null
  updateByName?: string | null
  /** 空 = 从没被改过（「新增 / 修改」的判据，SAMPLE-MP-001 坑 1） */
  updateTime?: string | null
  createTime?: string | null
}

/** 表格页那一行的形状（行仍来自 `/mp/int/embed/list`，字段名 = 模板 C 的列） */
export type EmbedLedgerRow = LedgerRow & {
  paraffinBlockNo?: string | null
  sampleId?: string | number | null
  internalNo?: string | null
  submitNo?: string | null
  sampleType?: string | null
  organoidSourceType?: string | null
  tissueReceiveTime?: string | null
  tissueProcessTime?: string | null
  agaroseEmbedTime?: string | null
  embedBy?: string | null
  dehydrateTime?: string | null
  agaroseSendTime?: string | null
  paraffinEmbedTime?: string | null
  sectionTime?: string | null
  sectioned?: boolean | null
  stainTypes?: string[] | null
  stainOther?: string | null
  markers?: EmbedMarker[] | null
  operatorName?: string | null
  remark?: string | null
  submitSource?: string | null
}

/** 一条 marker 表达的表单值（只有名称与表达两个键，与后端 `EmbedMarkerBo` 同形） */
export interface EmbedMarkerValue {
  markerName: string
  expression: string
}

/** 石蜡包埋表单值（内外部共用一份；外部只渲染其中的三项） */
export interface EmbedFormValue {
  /** 所挂样本 id（字符串形式，选项里给的就是字符串） */
  sampleId: string
  /** 选样本那一格显示什么（内部编号 / 送检单号）—— 只用于显示，不提交 */
  sampleLabel: string
  paraffinBlockNo: string
  sampleType: string
  organoidSourceType: string
  tissueReceiveTime: string
  tissueProcessTime: string
  agaroseEmbedTime: string
  embedBy: string
  dehydrateTime: string
  agaroseSendTime: string
  paraffinEmbedTime: string
  sectionTime: string
  stainTypes: string[]
  stainOther: string
  markers: EmbedMarkerValue[]
  operatorName: string
  remark: string
}

/** 空表单（新增时用） */
export function emptyEmbedForm(): EmbedFormValue {
  return {
    sampleId: '',
    sampleLabel: '',
    paraffinBlockNo: '',
    sampleType: '',
    organoidSourceType: '',
    tissueReceiveTime: '',
    tissueProcessTime: '',
    agaroseEmbedTime: '',
    embedBy: '',
    dehydrateTime: '',
    agaroseSendTime: '',
    paraffinEmbedTime: '',
    sectionTime: '',
    stainTypes: [],
    stainOther: '',
    markers: [],
    operatorName: '',
    remark: '',
  }
}

function str(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

/** 详情 → 表单值（详情没给的键回落空串，绝不 undefined，免得 v-model 变 uncontrolled） */
export function toEmbedFormValue(detail: Partial<EmbedDetail> | null | undefined): EmbedFormValue {
  const base = emptyEmbedForm()
  if (!detail) {
    return base
  }
  return {
    sampleId: str(detail.sampleId),
    // 选样本那一格的显示值：内部是内部编号，外部（VO 里根本没有这个键）落到送检单号
    sampleLabel: sampleLabelOf(detail),
    paraffinBlockNo: str(detail.paraffinBlockNo),
    sampleType: str(detail.sampleType),
    organoidSourceType: str(detail.organoidSourceType),
    tissueReceiveTime: dayOf(detail.tissueReceiveTime),
    tissueProcessTime: dayOf(detail.tissueProcessTime),
    agaroseEmbedTime: dayOf(detail.agaroseEmbedTime),
    embedBy: str(detail.embedBy),
    dehydrateTime: dayOf(detail.dehydrateTime),
    agaroseSendTime: dayOf(detail.agaroseSendTime),
    paraffinEmbedTime: dayOf(detail.paraffinEmbedTime),
    sectionTime: dayOf(detail.sectionTime),
    stainTypes: (detail.stainTypes ?? []).map(v => str(v)).filter(Boolean),
    stainOther: str(detail.stainOther),
    markers: (detail.markers ?? []).map(m => ({
      markerName: str(m?.markerName),
      expression: str(m?.expression),
    })),
    operatorName: str(detail.operatorName),
    remark: str(detail.remark),
  }
}

/** 日期只留 `yyyy-MM-dd`（后端给的是 LocalDate，这里兜一层字符串截断） */
function dayOf(value: unknown): string {
  return str(value).slice(0, 10)
}

/**
 * 「选择样本」那一格显示什么。
 *
 * ★ 内部 = 内部编号（选样本就是按内部编号搜的）；外部 = 送检单号
 *   （外部的 VO 里**根本没有**内部编号这个键 —— REQ-AUTH-013 不让外部知道内部编号）。
 */
function sampleLabelOf(detail: Partial<EmbedDetail>): string {
  return str(detail.internalNo).trim() || str(detail.submitNo).trim()
}

/** 空白的 marker 行（点了「＋ 加一行」） */
export function emptyMarkerRow(): EmbedMarkerValue {
  return { markerName: '', expression: '' }
}

/** marker 行 → 提交体：**整行都空的行直接丢掉**（不然会落一条只有表达没有名称的空行） */
function markersPayload(form: EmbedFormValue): Array<Record<string, unknown>> {
  return form.markers
    .filter(row => row.markerName.trim() || row.expression)
    .map(row => ({
      markerName: row.markerName.trim(),
      expression: row.expression,
    }))
}

/**
 * 内部**新增**提交体（`POST /mp/int/embed`）：内部录入直接有效、石蜡块编号必填，
 * 前两个工序时间由后端从样本带出（这里传了就用传的）。
 */
export function internalEmbedPayload(form: EmbedFormValue): Record<string, unknown> {
  return {
    sampleId: form.sampleId,
    paraffinBlockNo: form.paraffinBlockNo.trim(),
    sampleType: form.sampleType.trim(),
    organoidSourceType: form.organoidSourceType.trim(),
    tissueReceiveTime: form.tissueReceiveTime || null,
    tissueProcessTime: form.tissueProcessTime || null,
    agaroseEmbedTime: form.agaroseEmbedTime || null,
    embedBy: form.embedBy.trim(),
    dehydrateTime: form.dehydrateTime || null,
    agaroseSendTime: form.agaroseSendTime || null,
    paraffinEmbedTime: form.paraffinEmbedTime || null,
    sectionTime: form.sectionTime || null,
    stainTypes: form.stainTypes,
    stainOther: form.stainOther.trim(),
    markers: markersPayload(form),
    operatorName: form.operatorName.trim(),
    remark: form.remark.trim(),
  }
}

/**
 * 内部**修改 / 补填**（`PUT /mp/int/embed`）：同一个提交体 + `id`。
 *
 * ★ 后端是**补丁**语义：**没出现的键不改**；出现且为 `null` / 空串的键**清空**；把必填项清空回 400。
 *   本提交体把页面上的每一格都带上（空着的日期是 `null`）—— 所以在页面上清掉一个工序时间、
 *   保存后库里也就清掉了（以前「清不掉」的边界已经没有了）。
 * ★ `stainTypes` / `markers` **总是带**：后端对这两个是「传了就整组替换」，
 *   而它们反映的就是页面上的当前选择（空数组 = 用户把它们都去掉了）。
 * ★ `paraffinBlockNo` 总是带：改编号撞到别的石蜡块会被后端拒（编号全库唯一）。
 */
export function internalEmbedPatch(id: string, form: EmbedFormValue): Record<string, unknown> {
  return { id, ...internalEmbedPayload(form) }
}

/**
 * 外部提交 / 改后重提体（`POST /mp/ext/embed`、`PUT /mp/ext/embed/{id}`）。
 *
 * ★ **两个字段都带**：外部那条 PUT 是「覆盖」不是补丁（AUTH-EXT-002 WARN-3），
 *   少带一个会把库里的值清成 null。石蜡块编号 / 工序 / 染色 / marker / 包埋人 / 操作人 /
 *   备注**一个都不带**（后端 `ExtEmbedSubmitBo` 里也没有这些键）。
 */
export function externalEmbedPayload(form: EmbedFormValue): Record<string, unknown> {
  return {
    sampleId: form.sampleId,
    sampleType: form.sampleType.trim(),
    organoidSourceType: form.organoidSourceType.trim(),
  }
}

// ── 内部（小程序 · 内部人员）─────────────────────────────────────────────────

/** 内部列表：表格页（keyword / verifyStatus / stain）与历史编辑记录（sort=recent[&mine=true]）共用 */
export function fetchIntEmbedList(params: {
  keyword?: string
  verifyStatus?: string
  stain?: string
  sort?: string
  mine?: boolean
  pageNum?: number
  pageSize?: number
}) {
  return http.get<{ rows: EmbedDetail[], total: number }>(
    '/mp/int/embed/list',
    {
      keyword: params.keyword ? params.keyword.trim() : undefined,
      verifyStatus: params.verifyStatus || undefined,
      stain: params.stain || undefined,
      sort: params.sort,
      // 开关关着时不带这个参数（不带 = 中心全员，CR-20260918-07）
      mine: params.mine ? true : undefined,
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? PAGE_SIZE,
    },
    // 分页接口的形状是 `{code,msg,rows,total}`（没有 data 键）
    { raw: true },
  )
}

export function fetchIntEmbedDetail(id: string | number) {
  return http.get<EmbedDetail>(`/mp/int/embed/${id}`)
}

export function createIntEmbed(payload: Record<string, unknown>) {
  return http.post<string | number>('/mp/int/embed', payload)
}

export function updateIntEmbed(payload: Record<string, unknown>) {
  return http.put<void>('/mp/int/embed', payload)
}

/**
 * 内部「选择样本」的候选：**只列已核验有效的样本**（UI:mp.embed.form / FLOW:F-EMBED-01.step1）。
 *
 * 复用样本域那条内部读路径（`keyword` = 内部编号等值 / 来源单位模糊，SAMPLE-MP-002 落的）。
 * 分页（V27）：弹层里滑到底再取下一页。
 */
export function fetchIntValidSamples(keyword: string, pageNum = 1, pageSize = PAGE_SIZE) {
  return http.get<{ rows: SampleRow[], total: number }>(
    '/mp/int/sample/list',
    {
      verifyStatus: 'valid',
      keyword: keyword.trim() || undefined,
      pageNum,
      pageSize,
    },
    { raw: true },
  )
}

// ── 外部（小程序 · 合作单位）────────────────────────────────────────────────

/** 外部列表（历史编辑记录的「石蜡包埋」页签）：外部接口本来就按最近倒序，不另收 sort */
export function fetchExtEmbedList(params: { onlyMine?: boolean, pageNum?: number, pageSize?: number }) {
  return http.get<{ rows: EmbedDetail[], total: number }>(
    '/mp/ext/embed/list',
    {
      onlyMine: params.onlyMine ? true : undefined,
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? PAGE_SIZE,
    },
    { raw: true },
  )
}

export function fetchExtEmbedDetail(id: string | number) {
  return http.get<EmbedDetail>(`/mp/ext/embed/${id}`)
}

export function createExtEmbed(payload: Record<string, unknown>) {
  return http.post<string | number>('/mp/ext/embed', payload)
}

export function updateExtEmbed(id: string | number, payload: Record<string, unknown>) {
  return http.put<void>(`/mp/ext/embed/${id}`, payload)
}

/**
 * 外部「选择样本」的候选：`onlyMine=true` 的**一页**（V27：分页取，不再写死 100 条）。
 *
 * ★ **排除已判无效的**在显示层做（{@link isPickableExtSample}），不在这里筛：
 *   在取数函数里筛掉会让「这一页不满」被误判成「已经取完」，后面的页就再也取不到了。
 * ★ 待核验的样本**可以**挂（外部自己刚送检、还没核验的时候就要能送石蜡包埋）——
 *   后端 `EmbedExternalService.submit` 的口径也是「本人送检过、没被判无效」。
 * ★ 候选里**不出现内部编号**：显示送检单号 + 掩码供体姓名（REQ-AUTH-013）。
 */
export function fetchExtMySamplesPage(pageNum = 1, pageSize = PAGE_SIZE) {
  return http.get<{ rows: SampleRow[], total: number }>(
    '/mp/ext/sample/list',
    { onlyMine: true, pageNum, pageSize },
    { raw: true },
  )
}

/** 外部「选择样本」能不能选这一条：没被判无效（ticket §2 / UI:mp.embed.form） */
export function isPickableExtSample(row: SampleRow): boolean {
  return row.verifyStatus !== 'invalid'
}

/** 样本类型那一格的联想词：字典接口 `/mp/dict/hints?type=sample`（拉不到不挡填写） */
export function fetchSampleTypeHints() {
  return http.get<string[]>('/mp/dict/hints', { type: 'sample' }, { silent: true })
}

/**
 * 内部管理表格页 · 石蜡包埋工作表的取数（UI:mp.embed.list）。
 *
 * ★ 筛选是**该表自己的**三个：搜索（石蜡块编号 / 内部编号，后端 `keyword` 是「二选一命中」）、
 *   核验状态、染色（数组包含语义，后端 `stain`）。
 * ★ **不带** `sort=recent` / `mine`：带了这页就变成「有人经手过的」而不是全表
 *   （那两个参数是「历史编辑记录」的取数口，CR-20260918-07）。
 */
export function fetchEmbedLedgerRows(filters: LedgerFilters, pageNum = 1, pageSize = PAGE_SIZE) {
  return http.get<{ rows: LedgerRow[], total: number }>(
    '/mp/int/embed/list',
    {
      keyword: filters.keyword.trim() || undefined,
      verifyStatus: filters.verifyStatus || undefined,
      stain: filters.stain || undefined,
      pageNum,
      pageSize,
    },
    // 分页接口的形状是 `{code,msg,rows,total}`（没有 data 键）
    { raw: true },
  )
}

// ── 表格页：行 → 单元格 / 冻结格 / 行底色 ────────────────────────────────────
//
// ★ 列名 / 列序仍然只从 `pages/ledger/columns.ts` 来（`ledgerColumns('embed')`）；
//   本文件只回答「某一列的 key 在这张表的行对象上取哪个字段、怎么显示」。

/**
 * 列 key → 行字段名的别名表。
 *
 * ★ 只有一条：`columns.ts` 里 embed 那张表的第二列（模板列名「样本编号」）key 写成
 *   `sampleSubmitNo`，而 `EmbedVo` 上这一格的实际字段是 `internalNo`
 *   （UI:mp.embed.list：「样本编号（= 内部编号）」；`submitNo` 是**送检单号**，另一回事）。
 *   列名与列序一个字节没改（那是甲方模板），这里只做字段名对齐。
 */
const KEY_ALIAS: Record<string, string> = { sampleSubmitNo: 'internalNo' }

/** 染色 `lqg_stain_type` 的显示名（OTHER 带上具体名称） */
const STAIN_TEXT: Record<string, string> = { HE: 'HE', IF: 'IF', IHC: 'IHC', NONE: '无染色' }
/** marker 表达 `lqg_marker_expr` 的显示名 */
const EXPR_TEXT: Record<string, string> = { negative: '阴性', weak: '弱表达', strong: '强表达' }

/** 数组 / 空值 → 单元格文案（空值给「—」，表格里留空白看不出「没有」还是「没加载」） */
function cellText(row: EmbedLedgerRow, key: string): string {
  if (key === 'stainTypes') {
    const other = str(row.stainOther)
    const list = (row.stainTypes ?? []).filter(Boolean)
    if (list.length === 0) {
      return '—'
    }
    return list
      .map(v => (v === 'OTHER' ? (other ? `其他（${other}）` : '其他') : (STAIN_TEXT[v] || v)))
      .join('、')
  }
  if (key === 'markers') {
    const list = (row.markers ?? []).filter(m => str(m?.markerName) || str(m?.expression))
    if (list.length === 0) {
      return '—'
    }
    return list
      .map(m => `${str(m?.markerName) || 'marker'}：${EXPR_TEXT[str(m?.expression)] || str(m?.expression) || '—'}`)
      .join('；')
  }
  // 工序时间是日期，只显示到天
  if (key.endsWith('Time')) {
    return dayOf(row[key]) || '—'
  }
  return str(row[key]) || '—'
}

/** 一行 × 一列 → 单元格文案（列 key 只从 `columns.ts` 来，这里不认列名） */
export function embedLedgerCell(row: LedgerRow, key: string): string {
  return cellText(asEmbedRow(row), KEY_ALIAS[key] ?? key)
}

/** 表格页的行在 embed 域是 `EmbedLedgerRow`（`LedgerRow` 只是一个带索引签名的公共壳） */
function asEmbedRow(row: LedgerRow): EmbedLedgerRow {
  return row as unknown as EmbedLedgerRow
}

/**
 * 冻结格主值 = **石蜡块编号**；外部提交还没核验的还没有编号 → 显示**送检单号**
 * （UI:mp.embed.list：外部提交还没核验的显示送检单号 +「待核验」）。
 */
export function embedLedgerFrozen(row: LedgerRow): string {
  const r = asEmbedRow(row)
  return str(r.paraffinBlockNo).trim() || str(r.submitNo).trim() || '—'
}

/** 七个工序时间（+ 包埋人）的进度小圆点：填了几个亮几个 */
export function embedProgressDots(row: LedgerRow): string {
  const r = asEmbedRow(row)
  const slots = [
    r.tissueReceiveTime,
    r.tissueProcessTime,
    r.agaroseEmbedTime,
    r.dehydrateTime,
    r.agaroseSendTime,
    r.paraffinEmbedTime,
    r.sectionTime,
    r.embedBy,
  ]
  return slots.map(value => (str(value).trim() ? '●' : '○')).join('')
}

/**
 * 冻结格第二行小字 = 工序进度小圆点（UI:mp.embed.list）；
 * 外部提交还没核验 / 被判无效的那两条再把状态写在前面（编号为空时它就是这一行的身份）。
 */
export function embedLedgerSub(row: LedgerRow): string {
  const r = asEmbedRow(row)
  const status = str(r.verifyStatus)
  const label = status === 'pending' ? '待核验' : status === 'invalid' ? '无效' : ''
  const dots = embedProgressDots(row)
  if (!str(r.paraffinBlockNo).trim() && label) {
    return `${label} · ${dots}`
  }
  return dots
}

/** 行底色：待核验浅黄（与样本两张表同一口径） */
export function embedLedgerTone(row: LedgerRow): '' | 'pending' {
  return str(asEmbedRow(row).verifyStatus) === 'pending' ? 'pending' : ''
}

// ── 历史编辑记录：一行怎么摘要 ───────────────────────────────────────────────

/** 编号类：石蜡块编号优先；还没有编号（外部提交待核验 / 无效）用送检单号 +「待核验 / 无效」 */
export function embedHistoryCode(row: Partial<EmbedDetail>): string {
  const blockNo = str(row.paraffinBlockNo).trim()
  if (blockNo) {
    return blockNo
  }
  const submitNo = str(row.submitNo).trim()
  const status = str(row.verifyStatus)
  const label = status === 'pending' ? '待核验' : status === 'invalid' ? '无效' : ''
  return submitNo ? `${submitNo} · ${label}` : label
}

/** 摘要 = 样本类型 · 类器官来源类型 */
export function embedHistorySummary(row: Partial<EmbedDetail>): string {
  return [str(row.sampleType), str(row.organoidSourceType)].filter(Boolean).join(' · ')
}

/** 日期：改过看 `updateTime`、没改过看 `createTime`（只留到天） */
export function embedHistoryDate(row: Partial<EmbedDetail>): string {
  return dayOf(row.updateTime) || dayOf(row.createTime)
}

/** 「新增 / 修改」：更新时间晚于创建时间才算改过（utils/edited.ts，UX 测试 MP-08） */
export function embedHistoryAction(row: Partial<EmbedDetail>): string {
  return wasEdited(row) ? '修改' : '新增'
}
