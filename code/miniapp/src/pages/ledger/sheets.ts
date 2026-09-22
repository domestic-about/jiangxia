// 内部管理表格页的**工作表注册表**（SAMPLE-MP-002 §2 / UI:mp.ledger）。
//
// 每项 = key、短名、全称、搜索框提示、筛选项、取数函数、冻结格第二行、点行动作。
// 顶部切换条只读这里：**没注册的工作表不显示**；`?sheet=` 传了没注册的键 →
// 落到第一个已注册的工作表（ticket §2）。
//
// ★ 本张注册 `tissue` / `organoid`（SAMPLE-MP-002）与 **`embed`**（EMBED-MP-001）；
//   `cryo` 的列已在 `columns.ts` 里定完，注册票是 CRYO-MP-001。
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
import {
  embedLedgerCell,
  embedLedgerFrozen,
  embedLedgerSub,
  embedLedgerTone,
  fetchEmbedLedgerRows,
} from '@/api/embed'
import type { SheetKey } from './columns'
import { ledgerColumns } from './columns'

/** 一个筛选项：chips 组（空串 = 不筛） */
export interface LedgerFilterSpec {
  key: 'verifyStatus' | 'stain'
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
  /** 搜索框的占位提示（**放在这里**，页面不写死任何一张表的列名 / 字段名） */
  searchPlaceholder: string
  /** 该表的筛选项（搜索框各表共用，见页面） */
  filters: LedgerFilterSpec[]
  /** 取数 */
  fetch(filters: LedgerFilters, pageSize?: number): Promise<{ rows: LedgerRow[], total: number }>
  /** 冻结格第二行小字 */
  frozenSub(row: LedgerRow): string
  /** 行底色 */
  rowTone(row: LedgerRow): '' | 'pending' | 'overdue'
  /** 点一行去哪（**一律只读模式**） */
  target(row: LedgerRow): string
  /**
   * 这张表的行 → 表格矩阵（可选）。
   *
   * 样本两张表走页面默认那条（`toTableRows` 的通用路径，列名只从 `columns.ts` 来）；
   * 石蜡包埋表的行是另一个域的 VO（字段名不同、还有数组列），由 `api/embed.ts` 收口。
   * 列名 / 列序仍然只从 `columns.ts` 来 —— 这里只回答「某一列取哪个字段、怎么显示」。
   */
  toRows?(rows: LedgerRow[]): LedgerTableRow[]
}

/** 样本两张表共用的核验状态筛选 */
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

/**
 * 石蜡包埋表的染色筛选（UI:mp.embed.list：筛选 = 搜索 + 核验状态 + 染色）。
 *
 * 值域 = 字典 `lqg_stain_type`（五个值，与填写页那五个按钮同一份口径）；
 * 后端 `stain` 是**数组包含**语义（`EmbedQueryService.buildWrapper` 的整元素匹配，
 * 不是 `LIKE '%HE%'` —— 否则 OTHER 会被串出来）。
 */
const STAIN_FILTER: LedgerFilterSpec = {
  key: 'stain',
  label: '染色',
  options: [
    { value: '', label: '全部' },
    { value: 'HE', label: 'HE' },
    { value: 'IF', label: 'IF' },
    { value: 'IHC', label: 'IHC' },
    { value: 'OTHER', label: '其他' },
    { value: 'NONE', label: '无染色' },
  ],
}

function sampleSheet(key: 'tissue' | 'organoid', short: string, title: string, form: string, placeholder: string): LedgerSheet {
  return {
    key,
    short,
    title,
    searchPlaceholder: placeholder,
    filters: [VERIFY_FILTER],
    fetch: (filters, pageSize) => fetchSampleLedgerRows(key, filters, pageSize),
    frozenSub: ledgerFrozenSub,
    rowTone: ledgerRowTone,
    // 只读详情：`mode=view`（修改从只读页右上角进，CR-20260918-07）
    target: row => `${form}?id=${row.id}&mode=view`,
  }
}

/**
 * 石蜡包埋送样记录这张表（EMBED-MP-001 / UI:mp.embed.list）。
 *
 * 冻结格 = 石蜡块编号（外部提交还没核验的显示送检单号 +「待核验」，在 `api/embed.ts` 里算）；
 * 第二行小字 = 工序进度小圆点（七个工序时间 + 包埋人，填了几个亮几个）。
 * 表格本身仍然只读：点一行只进只读详情。
 */
const embedSheet: LedgerSheet = {
  key: 'embed',
  short: '石蜡包埋',
  title: '石蜡包埋送样记录',
  searchPlaceholder: '搜石蜡块编号或内部编号',
  filters: [VERIFY_FILTER, STAIN_FILTER],
  fetch: (filters, pageSize) => fetchEmbedLedgerRows(filters, pageSize),
  frozenSub: row => embedLedgerSub(row),
  rowTone: row => embedLedgerTone(row),
  target: row => `/pages/embed/form?id=${row.id}&mode=view`,
  toRows: (rows) => {
    const cols = ledgerColumns('embed')
    if (!cols) {
      return []
    }
    return rows.map(row => ({
      id: String(row.id),
      tone: embedLedgerTone(row),
      frozen: embedLedgerFrozen(row),
      sub: embedLedgerSub(row),
      cells: cols.columns.map(col => embedLedgerCell(row, col.key)),
    }))
  },
}

/** 本张注册的工作表（顺序 = 顶部切换条顺序 = 甲方模板顺序） */
export const LEDGER_SHEETS: LedgerSheet[] = [
  sampleSheet('tissue', '样本记录', '样本记录信息表', '/pages/sample/form', '搜编号或单位'),
  sampleSheet('organoid', '类器官收样', '类器官收样记录', '/pages/organoid/form', '搜编号或单位'),
  embedSheet,
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

/**
 * 取数结果 → 表格矩阵（列名只从 `columns.ts` 来，单元格文案只从 `api/ledger.ts` / `api/embed.ts` 来）。
 *
 * ★ 传了 `sheet` 且该表自带 `toRows`（石蜡包埋表：行是另一个域的 VO）时走它；
 *   不传 / 样本两张表走下面这条通用路径（样本行上的字段名与模板列一一对应）。
 */
export function toTableRows(rows: LedgerRow[], sheet?: LedgerSheet): LedgerTableRow[] {
  if (sheet?.toRows) {
    return sheet.toRows(rows)
  }
  const frozen = ledgerColumns('tissue')
  if (!frozen) {
    return []
  }
  return rows.map((row) => {
    const cols = ledgerColumns(row.sampleKind) ?? frozen
    // ★ 最后一列「切片染色」的文案在本文件里算好再交给 `ledgerCellText`（SAMPLE-HINT-001）：
    //   列名 / 列序仍只从 `columns.ts` 来，`api/ledger.ts` 一个字不用改。
    const rowWithHint: LedgerRow = { ...row, stainHint: stainHintText(row) }
    return {
      id: String(row.id),
      tone: ledgerRowTone(row),
      frozen: ledgerFrozenText(row),
      sub: ledgerFrozenSub(row),
      cells: cols.columns.map(col => ledgerCellText(rowWithHint, col.key)),
    }
  })
}

// ── 切片染色提示（SAMPLE-HINT-001 / UI:mp.ledger 的最后一列「切片染色」）────────────
//
// 数据来自 `/mp/int/sample/list` 行上的 `hint`（后端**读时计算**：一条 GROUP BY 算出本页
// 每行名下「已核验有效、未软删的石蜡块数 / 有没有切片 / 染色并集」）。小程序这一列是**纯文本**
// 单元格（哑组件只渲染字符串），所以把徽标拍成一行小字：「石蜡块 2 · 已切片 · HE / IHC」；
// 没有包埋记录显示「—」（与工作台 `HintBadges.vue` 同一口径）。

/** 行上的 `hint` 形状（与后端 `SampleHintVo` 逐字段同形） */
interface SampleHintLike {
  blockCount?: number | null
  sectioned?: boolean | null
  stains?: string[] | null
}

/** 染色值 → 缩写（值域 = 字典 `lqg_stain_type`；`NONE` 由后端在并集里去掉，到不了这里） */
const STAIN_ABBR: Record<string, string> = { HE: 'HE', IF: 'IF', IHC: 'IHC', OTHER: '其他' }

/**
 * 一行 → 「切片染色」列的文案。
 *
 * 与工作台徽标同一口径：`blockCount === 0`（没有包埋记录、或有记录但都不算块）显示「—」；
 * `sectioned` 才追加「已切片」；染色是并集（后端已去 `NONE`、已按字典序）。
 * 后端保证每行都有 `hint` 对象（零值不是 null）。
 */
export function stainHintText(row: LedgerRow): string {
  const hint = row.hint as SampleHintLike | null | undefined
  const blockCount = hint?.blockCount ?? 0
  if (blockCount <= 0) {
    return '—'
  }
  const parts = [`石蜡块 ${blockCount}`]
  if (hint?.sectioned) {
    parts.push('已切片')
  }
  const stains = (hint?.stains ?? []).map(kind => STAIN_ABBR[kind] ?? kind)
  if (stains.length > 0) {
    parts.push(stains.join(' / '))
  }
  return parts.join(' · ')
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
