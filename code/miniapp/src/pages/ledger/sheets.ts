// 内部管理表格页的**工作表注册表**（SAMPLE-MP-002 §2 / UI:mp.ledger）。
//
// 每项 = key、短名、全称、取数函数、筛选项、冻结格第二行、点行动作。
// 顶部切换条只读这里：**没注册的工作表不显示**；`?sheet=` 传了没注册的键 →
// 落到第一个已注册的工作表（ticket §2）。
//
// ★ 本张只注册 `tissue`、`organoid` 两个（数据都在样本主档上）。
//   `embed` / `cryo` 的列已在 `columns.ts` 里定完，注册票分别是 EMBED-MP-001 / CRYO-MP-001。
// ★ 点行动作只有一种：**该表填写页的只读模式**（`mode=view`）。
//   「修改」在只读页右上角、由填写页自己切成修改模式（CR-20260918-07）；
//   表格页这里没有、也不许有「直接进修改模式」那种目标串（accept 第 2 条的两段 grep 断的就是它）。
import type { LedgerFilters, LedgerRow } from '@/api/ledger'
import {
  fetchSampleLedgerRows,
  ledgerCellText,
  ledgerFrozenSub,
  ledgerFrozenText,
  ledgerRowTone,
} from '@/api/ledger'
import type { SheetKey } from './columns'
import { ledgerColumns } from './columns'

/** 一个筛选项：chips 组（空串 = 不筛） */
export interface LedgerFilterSpec {
  key: 'verifyStatus'
  label: string
  options: { value: string, label: string }[]
}

/** 已注册工作表的一行配置 */
export interface LedgerSheet {
  key: SheetKey
  /** 顶部切换条的短名（UI:mp.ledger ①） */
  short: string
  /** 导航栏标题 = 当前表全称 */
  title: string
  /** 该表的筛选项（搜索框两个表共用，见页面） */
  filters: LedgerFilterSpec[]
  /** 取数 */
  fetch(filters: LedgerFilters, pageSize?: number): Promise<{ rows: LedgerRow[], total: number }>
  /** 冻结格第二行小字 */
  frozenSub(row: LedgerRow): string
  /** 行底色 */
  rowTone(row: LedgerRow): '' | 'pending' | 'overdue'
  /** 点一行去哪（**一律只读模式**） */
  target(row: LedgerRow): string
}

/** 两个样本工作表共用的核验状态筛选 */
const VERIFY_FILTER: LedgerFilterSpec = {
  key: 'verifyStatus',
  label: '核验状态',
  options: [
    { value: '', label: '全部' },
    { value: 'pending', label: '待核验' },
    { value: 'valid', label: '有效' },
    { value: 'invalid', label: '无效' },
  ],
}

function sampleSheet(key: 'tissue' | 'organoid', short: string, title: string, form: string): LedgerSheet {
  return {
    key,
    short,
    title,
    filters: [VERIFY_FILTER],
    fetch: (filters, pageSize) => fetchSampleLedgerRows(key, filters, pageSize),
    frozenSub: ledgerFrozenSub,
    rowTone: ledgerRowTone,
    // 只读详情：`mode=view`（修改从只读页右上角进，CR-20260918-07）
    target: row => `${form}?id=${row.id}&mode=view`,
  }
}

/** 本张注册的工作表（顺序 = 顶部切换条顺序） */
export const LEDGER_SHEETS: LedgerSheet[] = [
  sampleSheet('tissue', '样本记录', '样本记录信息表', '/pages/sample/form'),
  sampleSheet('organoid', '类器官收样', '类器官收样记录', '/pages/organoid/form'),
]

/** 切换条要渲染的工作表 */
export function sheetsFor(): LedgerSheet[] {
  return LEDGER_SHEETS
}

/** `?sheet=` → 注册表里的一项；没注册 / 缺失 / 不认识 → 第一个已注册的 */
export function sheetOf(raw: unknown): LedgerSheet {
  const found = LEDGER_SHEETS.find(s => s.key === raw)
  return found ?? LEDGER_SHEETS[0]
}

/** 表头列（冻结列 + 其余列）由 `columns.ts` 给；没注册的表拿不到列就回 null */
export function columnsOf(sheet: LedgerSheet) {
  return ledgerColumns(sheet.key)
}

/**
 * 一行表格数据（**可序列化的纯数据**，给 `LedgerTable.vue` 那个哑组件）。
 *
 * 形状与 `components/lqg/LedgerTable.vue` 里那个不导出的 `LedgerTableRow` 逐字段同形，
 * 靠 TypeScript 的结构化匹配对接 —— 组件因此不 import 任何页面模块。
 */
export interface LedgerTableRow {
  id: string
  tone: string
  frozen: string
  sub: string
  cells: string[]
}

/** 取数结果 → 表格矩阵（列名只从 `columns.ts` 来，单元格文案只从 `api/ledger.ts` 来） */
export function toTableRows(rows: LedgerRow[]): LedgerTableRow[] {
  const frozen = ledgerColumns('tissue')
  if (!frozen) {
    return []
  }
  return rows.map((row) => {
    const cols = ledgerColumns(row.sampleKind) ?? frozen
    return {
      id: String(row.id),
      tone: ledgerRowTone(row),
      frozen: ledgerFrozenText(row),
      sub: ledgerFrozenSub(row),
      cells: cols.columns.map(col => ledgerCellText(row, col.key)),
    }
  })
}

/**
 * 整表宽度（`LedgerTable.vue` 的 `.ledger__fz` / `.ledger__col` 是固定宽 118 / 120）。
 *
 * 显式算出来给组件用，不依赖 `width: max-content`（小程序 WebView 上不稳）；
 * 也避免在 `.vue` 里写乘法（SYS-MP-001 的 accept 3 用 `(^|[ ,{])\*[ ,{]` 扫全域 `.vue`/`.scss`，
 * 一个 `a * b` 就会误伤 —— 它是给「裸 `*` 选择器」写的正则，但对源码文本一样敏感）。
 */
export function ledgerTableWidth(columnCount: number, frozenWidth = 118, columnWidth = 120): string {
  return `${frozenWidth + columnCount * columnWidth}px`
}
