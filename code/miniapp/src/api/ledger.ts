// 内部管理表格页（UI:mp.ledger / UI:mp.sample.list）的取数与单元格格式化（SAMPLE-MP-002）。
//
// 契约：`doc/api-contract.md` 第 49 行 —— 两个样本工作表都走 `GET /mp/int/sample/list`
//（`sampleKind=tissue|organoid` 区分），`keyword`（内部编号 / 来源单位）与 `verifyStatus` 是
// 「内部管理」表格页自己的两个筛选参数；`sort=recent` / `mine=true` 是「历史编辑记录」的取数口，
// **表格页一个都不带**（带 `sort=recent` 会把这页变成「有人经手过的」而不是全表）。
//
// ★ 分页响应是 `{code,msg,rows,total}`（没有 `data` 键）→ 必须带 `raw: true`（SAMPLE-MP-001 坑 3）。
// ★ 冻结列的两行文案、单元格取值、行底色全部在这里收口：`LedgerTable.vue` 只是一个
//   不认识业务字段的哑组件（列名也只从 `columns.ts` 来）。
import { http } from '@/utils/request'

/** 表格页的一行（`/mp/int/sample/list` 的行形状；四张表共用一个 VO） */
export interface LedgerRow {
  id: string | number
  submitNo?: string | null
  sampleKind?: string | null
  submitSource?: string | null
  verifyStatus?: string | null
  /** 冻结列的主值：内部编号；还没有内部编号的待核验外部样本显示送检单号 */
  internalNo?: string | null
  sourceUnitName?: string | null
  /** 内部接口给的是全名（ADR-0006）—— 表格页按列表规则打码后再显示 */
  donorName?: string | null
  donorNameMasked?: string | null
  gender?: string | null
  age?: string | null
  hospitalNo?: string | null
  tissueType?: string | null
  organoidType?: string | null
  receiveDate?: string | null
  isFixed?: string | null
  processTime?: string | null
  hasQcSheet?: string | null
  hasViabilityReport?: string | null
  operatorName?: string | null
  remark?: string | null
  /** 追加列的预留位（SAMPLE-HINT-001 接） */
  stainHint?: string | null
  [key: string]: unknown
}

/** 表格页的两个筛选条件（两个工作表之间切换时**保留**，ticket §2） */
export interface LedgerFilters {
  keyword: string
  verifyStatus: string
}

/** 空筛选 */
export function emptyFilters(): LedgerFilters {
  return { keyword: '', verifyStatus: '' }
}

/**
 * 拉一页表格数据。`sampleKind` 传给后端（**不在前端筛**，ticket 的 counterfeit 第 1 条）；
 * 两个筛选条件为空时不带该参数。
 */
export function fetchSampleLedgerRows(sampleKind: 'tissue' | 'organoid', filters: LedgerFilters, pageSize = 100) {
  return http.get<{ rows: LedgerRow[], total: number }>(
    '/mp/int/sample/list',
    {
      sampleKind,
      keyword: filters.keyword.trim() || undefined,
      verifyStatus: filters.verifyStatus || undefined,
      pageSize,
    },
    // 分页接口的形状是 `{code,msg,rows,total}`（没有 data 键）
    { raw: true },
  )
}

// ── 单元格 / 冻结格 / 行底色：页面与哑组件之间的唯一约定 ─────────────────────

/** 空值统一显示成「—」（表格里留空白会看不出「没有」还是「没加载」） */
function text(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  if (Array.isArray(value)) {
    return value.map(String).filter(Boolean).join('、')
  }
  return String(value)
}

/** 按钮类字段的中文（UI:mp.ledger：按钮类字段显示「有 / 无」「是 / 否」） */
const YN: Record<string, string> = { Y: '有', N: '无', true: '有', false: '无' }
/** 性别按字典值转（`authority/field-ssot.yaml` 的 dicts） */
const GENDER: Record<string, string> = { male: '男', female: '女', unknown: '未知' }

const YN_KEYS = ['isFixed', 'hasQcSheet', 'hasViabilityReport', 'hasPathology']

/**
 * 供体姓名掩码：首字（按 code point 取）+ `**` —— 与后端 `MaskRules.maskDonorName` 同形。
 * 内部接口给的是全名（ADR-0006），但表格页按列表规则打码（UI:mp.ledger / ticket §2）。
 */
export function maskDonorName(value: unknown): string {
  const raw = text(value).trim()
  if (!raw) {
    return ''
  }
  return `${Array.from(raw)[0]}**`
}

/** 一行 × 一列 → 单元格文案。列 `key` 只从 `columns.ts` 来，这里不认列名。 */
export function ledgerCellText(row: LedgerRow, key: string): string {
  if (key === 'donorNameMasked') {
    return maskDonorName(row.donorNameMasked ?? row.donorName) || '—'
  }
  if (YN_KEYS.includes(key)) {
    return YN[text(row[key])] ?? (text(row[key]) || '—')
  }
  if (key === 'gender') {
    return GENDER[text(row[key])] ?? (text(row[key]) || '—')
  }
  return text(row[key]) || '—'
}

/** 冻结格的主值：内部编号优先，没有（待核验的外部样本）就用送检单号 */
export function ledgerFrozenText(row: LedgerRow): string {
  return text(row.internalNo).trim() || text(row.submitNo) || '—'
}

const STATUS_TEXT: Record<string, string> = {
  pending: '待核验',
  valid: '有效',
  invalid: '无效',
}

/** 冻结格第二行小字：核验状态 + 内 / 外部（UI:mp.sample.list） */
export function ledgerFrozenSub(row: LedgerRow): string {
  const status = STATUS_TEXT[text(row.verifyStatus)] ?? ''
  const source = row.submitSource === 'internal' ? '内部' : row.submitSource === 'external' ? '外部' : ''
  const parts = [status, source].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '—'
}

/** 行底色：待核验浅黄（UI:mp.ledger）；超期浅红留给冻存工作表（CRYO-MP-001） */
export function ledgerRowTone(row: LedgerRow): '' | 'pending' | 'overdue' {
  return row.verifyStatus === 'pending' ? 'pending' : ''
}
