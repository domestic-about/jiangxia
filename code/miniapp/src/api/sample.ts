// 样本记录信息表（SAMPLE-MP-001）的前端接口层。
//
// 契约：`doc/api-contract.md` 第 49-51 行。
//   GET  /mp/int/sample/list?sampleKind=&sort=recent[&mine=true]   内部历史编辑记录 / 表格页
//   GET  /mp/int/sample/{id}                                       内部详情（带 updateByName / updateTime）
//   POST /mp/int/sample                                            内部新增（直接 valid）
//   PUT  /mp/int/sample                                            内部修改（valid 谁录的都能改）
//   GET  /mp/ext/sample/list?sampleKind=&onlyMine=                 外部历史编辑记录
//   GET  /mp/ext/sample/{id}                                       外部详情（掩码 only in list）
//   POST /mp/ext/sample                                            外部送检（→ pending）
//   PUT  /mp/ext/sample/{id}                                       外部改后重提（仅本人 pending / invalid）
//
// ★ 内部接口的行里带 `handlerName` / `mine`（CR-20260918-07）：「经手人」与「是不是我经手的」；
//   外部接口的行里带 `donorNameMasked`（列表打码、详情才有全名）。
// ★ `sort=recent` 是**取数口**不是纯排序：它只给「中心内部人员经手过的」记录，
//   不带 `mine` = 中心全员；开关打开才另带 `mine=true` 收窄到本人。
import { PAGE_SIZE } from '@/utils/paging'
import { http } from '@/utils/request'
import { wasEdited } from '@/utils/edited'

/** 列表行（内部接口；外部接口少了 `internalNo`、多了 `donorNameMasked`） */
export interface SampleRow {
  id: string | number
  submitNo: string
  sampleKind: string
  verifyStatus: string
  /** 内部编号：待核验的外部样本还没有 → 空 */
  internalNo?: string | null
  /** 来源单位名称 */
  sourceUnitName?: string | null
  /** 列表里是掩码（外部接口） */
  donorNameMasked?: string | null
  /** 详情 / 内部列表里有全名 */
  donorName?: string | null
  tissueType?: string | null
  organoidType?: string | null
  /** 代数（只有类器官收样记录有，形如 P3；CR-20260924-10）—— 内部行 / 内外部详情都给 */
  passage?: string | null
  submitterName?: string | null
  /** 经手人（= 最后修改人，没改过就是创建人）—— 内部行 */
  handlerName?: string | null
  /** 是不是当前登录人经手的 —— 内部行 */
  mine?: boolean | null
  /** 最后修改人姓名 —— 详情 */
  updateByName?: string | null
  /** 最后修改时间：**空 = 新增，非空 = 修改** */
  updateTime?: string | null
  createTime?: string | null
  /** 可写性（内部详情 / 外部详情） */
  editable?: boolean | null
  invalidReason?: string | null
}

/**
 * 详情：外部详情多两段（石蜡包埋卡片 AUTH-EXT-002 / 质控文档 AUTH-EXT-003），
 * 内部详情另带收样段全字段。
 *
 * ★ 外部详情里的 `internalNo`：**只有后端开关 `lqg.ext.show-internal-no` 打开时才有这个键**
 * （CR-20260918-07）—— 关着时键本身不存在，页面按 `has(internalNo)` 决定整行渲不渲染。
 */
export interface SampleDetail extends SampleRow {
  /** 来源单位 id：**只有内部详情给**（`SampleVo`）；外部详情的 VO 里没有这个键 */
  sourceUnitId?: string | number | null
  gender?: string | null
  age?: string | null
  hospitalNo?: string | null
  hasPathology?: string | null
  remark?: string | null
  // 收样段（**只有内部详情会给**；外部详情的 VO 里根本没有这些键，CR-20260918-07）
  receiveDate?: string | null
  isFixed?: string | null
  processTime?: string | null
  hasQcSheet?: string | null
  hasViabilityReport?: string | null
  operatorName?: string | null
  /** 第②段：该样本名下的石蜡包埋卡片（**含外部提交还没核验的送样**，AUTH-EXT-002） */
  embeds?: EmbedRow[]
  /** 第③段：质控文档（AUTH-EXT-003） */
  docs?: unknown[]
}

/**
 * 外部样本详情第②段的石蜡包埋卡片（`ExtEmbedVo`，AUTH-EXT-002）。
 *
 * 键集合就是后端的白名单（`doc/api-contract.md` 第 64 行）：
 * **有**操作人 `operatorName` 与包埋人 `embedBy`（CR-20260918-07 放开），
 * **没有**内部编号 / 备注 / 核验人 / 冻存。（`EmbedCard.vue` 不出现这几个词是硬约束。）
 */
export interface EmbedRow {
  id: string | number
  sampleId?: string | number | null
  submitNo?: string | null
  /** 石蜡块编号：外部提交还没核验的送样为空 → 卡片显示「待核验」/「无效」 */
  paraffinBlockNo?: string | null
  sampleType?: string | null
  organoidSourceType?: string | null
  tissueReceiveTime?: string | null
  tissueProcessTime?: string | null
  agaroseEmbedTime?: string | null
  dehydrateTime?: string | null
  agaroseSendTime?: string | null
  paraffinEmbedTime?: string | null
  sectionTime?: string | null
  /** 已切片（后端由切片时间推） */
  sectioned?: boolean | null
  stainTypes?: string[] | null
  stainOther?: string | null
  markers?: EmbedMarker[] | null
  verifyStatus?: string | null
  invalidReason?: string | null
  submitterName?: string | null
  /** 这条送样是不是我提交的 */
  mine?: boolean | null
  /** 本人提交且待核验 / 无效才可改后重提 */
  editable?: boolean | null
  /** 包埋人 */
  embedBy?: string | null
  /** 操作人 */
  operatorName?: string | null
}

/** 一条 marker 表达（只有名称与表达两个键） */
export interface EmbedMarker {
  markerName?: string | null
  expression?: string | null
}

/** 表单字段（送检段 + 收样段；提交时整份带回去，后端 PUT 收部分字段也能改） */
export interface SampleFormValue {
  sourceUnitName: string
  donorName: string
  gender: string
  age: string
  hospitalNo: string
  tissueType: string
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

/** 空表单（新增时用） */
export function emptyForm(): SampleFormValue {
  return {
    sourceUnitName: '',
    donorName: '',
    gender: '',
    age: '',
    hospitalNo: '',
    tissueType: '',
    hasPathology: '',
    remark: '',
    receiveDate: '',
    internalNo: '',
    isFixed: '',
    processTime: '',
    hasQcSheet: '',
    hasViabilityReport: '',
    operatorName: '',
  }
}

/** 详情 → 表单值（详情没给的键回落空串，绝不 undefined，免得 v-model 变 uncontrolled） */
export function toFormValue(detail: Partial<SampleDetail> | null | undefined): SampleFormValue {
  const base = emptyForm()
  if (!detail) {
    return base
  }
  return {
    sourceUnitName: str(detail.sourceUnitName),
    donorName: str(detail.donorName),
    gender: str(detail.gender),
    age: str(detail.age),
    hospitalNo: str(detail.hospitalNo),
    tissueType: str(detail.tissueType),
    hasPathology: str(detail.hasPathology),
    remark: str(detail.remark),
    receiveDate: str(detail.receiveDate),
    internalNo: str(detail.internalNo),
    isFixed: str(detail.isFixed),
    processTime: str(detail.processTime),
    hasQcSheet: str(detail.hasQcSheet),
    hasViabilityReport: str(detail.hasViabilityReport),
    operatorName: str(detail.operatorName),
  }
}

function str(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

/** 来源单位 id 的一个候选：id 与它对应的单位名（名字对得上才用这个 id） */
export interface UnitIdCandidate {
  id?: string | number | null
  name?: string | null
}

/**
 * 来源单位 id（V01）：组织样本表单只有一个可改的「来源单位」文本格，提交时按**当前名字**回找 id。
 *
 * 名字（去首尾空白）与某个候选的单位名逐字相同，才带那个候选的 id；改成了别的名字 → `null`
 * （只按名字落快照，后端按名字兜底）。两种要避开的坏形态：
 *   - 一律不带 id（V01 的原状）：外部从小程序交的组织样本 `source_unit_id` 为空，
 *     工作台按单位筛选、按单位导出都查不到；
 *   - 无条件带档案里的 id：外部把单位改成了别的名字，后端按 id 取单位名，改的名字被静默丢掉。
 */
export function sourceUnitIdFor(name: string, candidates: UnitIdCandidate[]): string | number | null {
  const target = str(name).trim()
  if (!target) {
    return null
  }
  for (const candidate of candidates) {
    const id = candidate.id
    if (id === null || id === undefined || id === '') {
      continue
    }
    if (str(candidate.name).trim() === target) {
      return id
    }
  }
  return null
}

/** 只有一个「我」的判定入口：后端行上的 `mine`，缺失按 false（不猜成本人） */
export function isMine(row: Pick<SampleRow, 'mine'>): boolean {
  return row.mine === true
}

/** 「新增 / 修改」：更新时间晚于创建时间才算改过（utils/edited.ts，UX 测试 MP-08） */
export function rowActionLabel(row: Pick<SampleRow, 'createTime' | 'updateTime'>): string {
  return wasEdited(row) ? '修改' : '新增'
}

/** 内部「经手人」列：本人显示「我」，否则显示后端给的姓名 */
export function handlerLabel(row: Pick<SampleRow, 'mine' | 'handlerName'>): string {
  if (isMine(row)) {
    return '我'
  }
  return str(row.handlerName) || '—'
}

/** 外部「我 / 同组 某某」：按行上的 `mine` + `submitterName` */
export function ownerLabel(row: Pick<SampleRow, 'mine' | 'submitterName'>): string {
  if (isMine(row)) {
    return '我'
  }
  const name = str(row.submitterName)
  return name ? `同组 ${name}` : '同组'
}

/** 内部列表的第二行摘要：内部编号（没有就送检单号）+ 组织 / 类器官类型 */
export function summaryLabel(row: Partial<SampleRow>): string {
  const kind = str(row.tissueType) || str(row.organoidType)
  const unit = str(row.sourceUnitName)
  return [kind, unit].filter(Boolean).join(' · ')
}

// ── 内部（小程序 · 内部人员）─────────────────────────────────────────────────

/**
 * 内部列表：`sort=recent` = 历史编辑记录取数口；`mine=true` 只在开关打开时带。
 * 分页：`pageNum` 从 1 起（V27：列表触底再取下一页，不再写死 100 条）。
 */
export function fetchIntSampleList(params: {
  sampleKind?: string
  sort?: string
  mine?: boolean
  pageNum?: number
  pageSize?: number
}) {
  return http.get<{ rows: SampleRow[], total: number }>(
    '/mp/int/sample/list',
    {
      sampleKind: params.sampleKind,
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

export function fetchIntSampleDetail(id: string | number) {
  return http.get<SampleDetail>(`/mp/int/sample/${id}`)
}

export function createIntSample(payload: Record<string, unknown>) {
  return http.post<string | number>('/mp/int/sample', payload)
}

export function updateIntSample(payload: Record<string, unknown>) {
  return http.put<void>('/mp/int/sample', payload)
}

// ── 外部（小程序 · 合作单位）────────────────────────────────────────────────

export function fetchExtSampleList(params: {
  sampleKind?: string
  onlyMine?: boolean
  pageNum?: number
  pageSize?: number
}) {
  return http.get<{ rows: SampleRow[], total: number }>(
    '/mp/ext/sample/list',
    {
      sampleKind: params.sampleKind,
      onlyMine: params.onlyMine ? true : undefined,
      pageNum: params.pageNum ?? 1,
      pageSize: params.pageSize ?? PAGE_SIZE,
    },
    { raw: true },
  )
}

export function fetchExtSampleDetail(id: string | number) {
  return http.get<SampleDetail>(`/mp/ext/sample/${id}`)
}

export function createExtSample(payload: Record<string, unknown>) {
  return http.post<string | number>('/mp/ext/sample', payload)
}

export function updateExtSample(id: string | number, payload: Record<string, unknown>) {
  return http.put<void>(`/mp/ext/sample/${id}`, payload)
}
