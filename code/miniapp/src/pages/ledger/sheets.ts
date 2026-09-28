// 内部管理表格页的**工作表注册表**（SAMPLE-MP-002 §2 / UI:mp.ledger）。
//
// 每项 = key、短名、全称、搜索框提示、筛选项、取数函数、冻结格第二行、点行动作。
// 顶部切换条只读这里：**没注册的工作表不显示**；`?sheet=` 传了没注册的键 →
// 落到第一个已注册的工作表（ticket §2）。
//
// ★ 本张注册 `tissue` / `organoid`（SAMPLE-MP-002）、**`embed`**（EMBED-MP-001）
//   与 **`cryo`**（CRYO-MP-001）；四张表在 `columns.ts` 里都已定完列。
// ★ 点行动作三种：该表填写页的**只读模式**（`mode=view`）；（冻存）**只读的批次详情弹层**；
//   **合作单位送来、待核验的那一条 → 核验页**（甲方 2026-09-24 第 20 行：小程序里也能核验，
//   去向与「待核验」列表页同一个函数 `verifyTarget`）。
//   「修改」在只读页 / 弹层右上角、由那一处自己切成修改模式（CR-20260918-07）；
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
import {
  cryoLedgerCell,
  cryoLedgerFrozen,
  cryoLedgerSub,
  cryoLedgerTone,
  cryoTabText,
  fetchCryoLedgerRows,
} from '@/api/cryo'
import { ENTRY_SHORT, ENTRY_TITLE } from '@/pages/index/entries'
import { isPending, verifyTarget } from '@/pages/verify/tabs'
import type { SheetKey } from './columns'
import { ledgerColumns } from './columns'

/** 筛选条件的键（每张表用其中一部分；`cryoView` 是冻存那三个页签专有的） */
export type LedgerFilterKey = 'verifyStatus' | 'stain' | 'cryoView'

/** 一个筛选项：chips 组（空串 = 不筛） */
export interface LedgerFilterSpec {
  key: LedgerFilterKey
  label: string
  options: { value: string, label: string }[]
}

/** 已注册工作表的一行配置 */
export interface LedgerSheet {
  key: SheetKey
  /** 顶部切换条的短名（UI:mp.ledger ①；取 `entries.ts#ENTRY_SHORT`，历史编辑记录、待核验的页签用同一份） */
  short: string
  /** 导航栏标题 = 当前表全称（取 `entries.ts#ENTRY_TITLE`） */
  title: string
  /** 搜索框的占位提示（**放在这里**，页面不写死任何一张表的列名 / 字段名）；空串 = 这张表没有搜索框 */
  searchPlaceholder: string
  /** 该表的筛选项（搜索框各表共用，见页面） */
  filters: LedgerFilterSpec[]
  /** 取一页（`pageNum` 从 1 起，V27 触底分页）；冻存那张会另外把响应顶层的 `tabCounts` 带回来（页签数字只认它） */
  fetch(filters: LedgerFilters, pageNum: number, pageSize: number): Promise<{
    rows: LedgerRow[]
    total: number
    tabCounts?: Record<string, number> | null
  }>
  /** 冻结格第二行小字 */
  frozenSub(row: LedgerRow): string
  /** 行底色（`emptied` = 冻存那张已取空的行，冻结格小字醒目） */
  rowTone(row: LedgerRow): '' | 'pending' | 'overdue' | 'emptied'
  /** 点一行去哪：只读模式；待核验的那一条去核验页 */
  target(row: LedgerRow): string
  /**
   * 这张表的行 → 表格矩阵（可选）。
   *
   * 样本两张表走页面默认那条（`toTableRows` 的通用路径，列名只从 `columns.ts` 来）；
   * 石蜡包埋 / 冻存两张表的行是另一个域的 VO（字段名不同、还有数组列），由各自的 `api/*.ts` 收口。
   * 列名 / 列序仍然只从 `columns.ts` 来 —— 这里只回答「某一列取哪个字段、怎么显示」。
   */
  toRows?(rows: LedgerRow[]): LedgerTableRow[]
  /**
   * 筛选项文案的动态覆盖（可选）：`接口给的 tabCounts → { 选项值: 文案 }`。
   *
   * ★ 冻存那张用它把**页签数字**拼上去（数字来自响应顶层 `tabCounts`，
   *   **不是**当前页 rows 的长度）—— ticket 的 counterfeit 点名「前端按 rows 自己数」。
   */
  chipsText?(tabCounts: Record<string, number> | null | undefined): Record<string, string>
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
    fetch: (filters, pageNum, pageSize) => fetchSampleLedgerRows(key, filters, pageNum, pageSize),
    frozenSub: ledgerFrozenSub,
    rowTone: ledgerRowTone,
    // 只读详情：`mode=view`（修改从只读页右上角进，CR-20260918-07）；待核验的进核验页
    target: row => (isPending(row) ? verifyTarget(key, row.id) : `${form}?id=${row.id}&mode=view`),
  }
}

/**
 * 石蜡包埋送样记录这张表（EMBED-MP-001 / UI:mp.embed.list）。
 *
 * 冻结格 = 石蜡块编号（外部提交还没核验的显示送检单号 +「待核验」，在 `api/embed.ts` 里算）；
 * 第二行小字 = 工序进度小圆点（七个工序时间 + 包埋人，填了几个亮几个）。
 * 表格本身仍然只读：点一行进只读详情；外部送来还没核验的那一条进核验页。
 */
const embedSheet: LedgerSheet = {
  key: 'embed',
  short: ENTRY_SHORT.embed,
  title: ENTRY_TITLE.embed,
  searchPlaceholder: '搜石蜡块编号或内部编号',
  filters: [VERIFY_FILTER, STAIN_FILTER],
  fetch: (filters, pageNum, pageSize) => fetchEmbedLedgerRows(filters, pageNum, pageSize),
  frozenSub: row => embedLedgerSub(row),
  rowTone: row => embedLedgerTone(row),
  target: row => (isPending(row) ? verifyTarget('embed', row.id) : `/pages/embed/form?id=${row.id}&mode=view`),
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

/**
 * -80 冻存这张表（CRYO-MP-001 / UI:mp.cryo.list）。
 *
 * ★ 筛选行**只有四个页签**（没有搜索框）：全部 / -80 超期（`overdueOnly=true`）/
 *   液氮（`location=ln2`）/ 已取空（`emptiedOnly=true`，2026-09-24 甲方「支数取空的要提示」）
 *   —— 四个值走后端参数，前端不自己筛。表格页 `?sheet=cryo&tab=overdue|ln2|emptied|all` 直达其中一档。
 * ★ 页签上的数字来自响应顶层的 `tabCounts`（`chipsText` 把文案补全），**不数当前页 rows**：
 *   切到「-80 超期」只剩 2 行时，数字仍然是 `全部 7 / -80 超期 2 / 液氮 2`（整表口径）。
 * ★ 冻结格 = 冻存样品；第二行小字 =「剩 N / 初始 M 支」（超期再加「已超 N 天」）。
 * ★ **点一行打开的是只读的批次详情弹层**（`CryoBatchSheet`，在页面里挂），不是填写页：
 *   要改这条记录走弹层右上角「修改」→ 填写页的修改模式（CR-20260918-07）。
 *   这里的 `target` 仍然只给「只读」这条路（`mode=view`），页面在冻存这一档改走弹层。
 */
const CRYO_VIEW_FILTER: LedgerFilterSpec = {
  key: 'cryoView',
  label: '视图',
  options: [
    { value: '', label: '全部' },
    { value: 'overdue', label: '-80 超期' },
    { value: 'ln2', label: '液氮' },
    { value: 'emptied', label: '已取空' },
  ],
}

const cryoSheet: LedgerSheet = {
  key: 'cryo',
  short: ENTRY_SHORT.cryo,
  title: ENTRY_TITLE.cryo,
  // 空串 = 这张表没有搜索框（UI:mp.cryo.list 的筛选行只有三个页签）
  searchPlaceholder: '',
  filters: [CRYO_VIEW_FILTER],
  fetch: (filters, pageNum, pageSize) => fetchCryoLedgerRows(filters, pageNum, pageSize),
  frozenSub: row => cryoLedgerSub(row),
  rowTone: row => cryoLedgerTone(row),
  target: row => `/pages/cryo/form?id=${row.id}&mode=view`,
  chipsText: counts => cryoTabText(counts),
  toRows: (rows) => {
    const cols = ledgerColumns('cryo')
    if (!cols) {
      return []
    }
    return rows.map(row => ({
      id: String(row.id),
      tone: cryoLedgerTone(row),
      frozen: cryoLedgerFrozen(row),
      sub: cryoLedgerSub(row),
      cells: cols.columns.map(col => cryoLedgerCell(row, col.key)),
    }))
  },
}

/** 本张注册的工作表（顺序 = 顶部切换条顺序 = 甲方模板顺序） */
export const LEDGER_SHEETS: LedgerSheet[] = [
  // 工作表 key 是 tissue，对应入口 key sample（同一张「样本记录信息表」）
  sampleSheet('tissue', ENTRY_SHORT.sample, ENTRY_TITLE.sample, '/pages/sample/form', '搜编号或单位'),
  sampleSheet('organoid', ENTRY_SHORT.organoid, ENTRY_TITLE.organoid, '/pages/organoid/form', '搜编号或单位'),
  embedSheet,
  cryoSheet,
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
