// 「历史编辑记录」页签的数据源注册表（SAMPLE-MP-001 §2 / UI:mp.history）。
//
// 页签清单**不在这里**：它由 `pages/index/entries.ts` 的 `entriesFor(identity)` 给
//（内部四张、外部三张），本文件只登记「哪个页签的数据从哪来、一行怎么摘要、点一行去哪」。
// 本文件登记 `sample` / `organoid`（SAMPLE-MP-001 / SAMPLE-MP-002）与 `embed`（EMBED-MP-001）；
// 其余的键查不到数据源 → 页面显示空状态（SYS-MP-001 已定的写法：还没注册数据源的页签显示空状态，
// 不另写第二份页签清单）。
//
// ★ 口径（CR-20260918-07，内外部共用同一个「只看我提交的」开关）：
//   外部 = 可见集合（本人 + 同组）→ 开关打开才另带 `onlyMine=true`；行 = 掩码供体 · 类型 ·
//          「我 / 同组 某某」· 状态徽标 · 日期；点行：本人且可改 → 填写页 edit，其余 → 外部详情。
//   内部 = **中心全部内部人员**经手过的（取数口 `sort=recent`，**不带 mine**）→ 开关打开才另带
//          `mine=true` 收窄到本人；行 = 内部编号 / 石蜡块编号（没有则送检单号）· 摘要 ·
//          经手人（本人显示「我」）·「新增 / 修改」（看 `updateTime` 空不空）· 日期；
//          点行一律进填写页 edit（外部送来还没核验的由后端挡成只读）。
import type { EmbedDetail } from '@/api/embed'
import {
  embedHistoryAction,
  embedHistoryCode,
  embedHistoryDate,
  embedHistorySummary,
  fetchExtEmbedList,
  fetchIntEmbedList,
} from '@/api/embed'
import type { CryoBatchRow } from '@/api/cryo'
import {
  cryoHistoryAction,
  cryoHistoryCode,
  cryoHistoryDate,
  cryoHistorySummary,
  fetchIntCryoList,
} from '@/api/cryo'
import type { SampleRow } from '@/api/sample'
import {
  fetchExtSampleList,
  fetchIntSampleList,
  handlerLabel,
  isMine,
  ownerLabel,
  rowActionLabel,
  summaryLabel,
} from '@/api/sample'
import type { EntryKey } from '@/pages/index/entries'
import type { ResolvedIdentity } from '@/types/identity'
import type { PageResult } from '@/utils/paging'

/** 一行背后的原始对象（每个域一个 VO：样本行 / 石蜡包埋行 / 冻存批次行） */
export type HistoryRaw = SampleRow | EmbedDetail | CryoBatchRow

/** 一行在页面上的样子（页面只认这几个字符串，不认识业务字段） */
export interface HistoryRow {
  id: string
  code: string
  summary: string
  owner: string
  action: string
  date: string
  status: string
  statusText: string
  /**
   * 判无效的原因（D2 r1 L2 S1-2）。
   *
   * ★ 权威 FLOW:F-SAMPLE-01.step5 的动作是「在『我的 → 历史编辑记录』**看到无效及原因** →
   *   修改送检段 → 重新提交」。只给状态不给原因时，外部只能「盲改」。
   *   只有 `verifyStatus === 'invalid'` 且有值时页面才渲染这一行。
   */
  reason: string
  /** 来源对象：点行时判「本人 + 可改」用（外部那条路） */
  raw: HistoryRaw
}

export interface HistorySource {
  /** 这一档的空状态文案 */
  emptyText: string
  /**
   * 取第 `pageNum` 页（从 1 起；`onlyMine` = 顶部开关的值；内部那条路传的是「只看我提交的」）。
   * ★ V27：以前写死 `pageSize=100` 只取第一页，第 101 条以后永远看不到；现在页面触底再取下一页，
   *   `total` 原样带回（页面底部的「共 N 条」只认它）。
   */
  fetch(identity: ResolvedIdentity, onlyMine: boolean, pageNum: number, pageSize: number): Promise<PageResult<HistoryRaw>>
  /** 一行 → 页面行 */
  toRow(row: HistoryRaw): HistoryRow
  /** 点这一行去哪（返回页面路径） */
  target(identity: ResolvedIdentity, row: HistoryRow): string
}

const STATUS_TEXT: Record<string, string> = {
  pending: '待核验',
  valid: '有效',
  invalid: '无效',
}

/** 日期：只留 `yyyy-MM-dd`（列表里不必到秒），空值给空串 */
function dayOf(value: unknown): string {
  const text = value === null || value === undefined ? '' : String(value)
  return text.slice(0, 10)
}

/** 最新一次改动的时间：改过看 `updateTime`，没改过看 `createTime` */
function latestTime(row: SampleRow): string {
  return dayOf(row.updateTime) || dayOf(row.createTime)
}

/** 编号类：内部编号优先，没有（待核验的外部样本）就用送检单号 */
function codeOf(row: SampleRow): string {
  return String(row.internalNo || '').trim() || String(row.submitNo || '')
}

/**
 * 一行 → 页面行（两档共用）。
 *
 * ★ 怎么分辨这一行是内部行还是外部行：内部接口的行带 `handlerName`，外部接口的行带
 *   `donorNameMasked`（两个 VO 的形状差异，见 `doc/api-contract.md` 第 49-51 行）。
 * ★ 内部行的「新增 / 修改」看 `updateTime` 空不空（SAMPLE-MP-001 坑 1：没改过的行
 *   `updateTime` 是 null），`updateByName` 只回答「谁」。
 */
function toHistoryRow(row: SampleRow): HistoryRow {
  const status = String(row.verifyStatus || '')
  const isExternal = row.donorNameMasked !== undefined || row.handlerName === undefined
  // ★ 无效原因只在「真的无效」时带出来（D2 r1 L2 S1-2）：状态不是 invalid 的话，
  //   行上纵使有脏值也不显示，免得给已改判有效的行挂一条旧原因。
  const reason = status === 'invalid' ? String(row.invalidReason || '') : ''
  if (isExternal) {
    // 外部行：掩码供体 · 组织类型 / 类器官类型 ·「我 / 同组 某某」· 状态 · 日期（+ 无效原因）
    return {
      id: String(row.id),
      code: String(row.submitNo || ''),
      summary: [String(row.donorNameMasked || ''), summaryLabel(row)].filter(Boolean).join(' · '),
      owner: ownerLabel(row),
      action: '',
      date: latestTime(row),
      status,
      statusText: STATUS_TEXT[status] || '',
      reason,
      raw: row,
    }
  }
  // 内部行：内部编号（没有则送检单号）· 摘要 · 经手人（本人显示「我」）· 新增 / 修改 · 日期
  return {
    id: String(row.id),
    code: codeOf(row),
    summary: summaryLabel(row),
    owner: handlerLabel(row),
    action: rowActionLabel(row),
    date: latestTime(row),
    status,
    statusText: STATUS_TEXT[status] || '',
    reason,
    raw: row,
  }
}

const sampleSource: HistorySource = {
  emptyText: '你填过的记录会出现在这里',

  fetch(identity, onlyMine, pageNum, pageSize) {
    if (identity === 'internal') {
      // ★ 默认是中心全员（`sort=recent` 不带 mine）；开关打开才**另外**带 mine=true。
      // `sort=recent` 同时是范围口：没人经手过的（外部送来待核验 / 无效、外部自己改过的）不进这张清单。
      return fetchIntSampleList({
        sampleKind: 'tissue',
        sort: 'recent',
        mine: onlyMine,
        pageNum,
        pageSize,
      })
    }
    return fetchExtSampleList({
      sampleKind: 'tissue',
      onlyMine,
      pageNum,
      pageSize,
    })
  },

  toRow: toHistoryRow,

  target(identity, row) {
    if (identity === 'internal') {
      // 内部：一律进修改模式（别人录的也能改）；外部送来还没核验的由后端挡成只读
      return `/pages/sample/form?id=${row.id}&mode=edit`
    }
    // 外部：本人 + 可改 → 改后重提；其余 → 样本详情（只读）
    const raw = row.raw as SampleRow
    if (isMine(raw) && raw.editable === true) {
      return `/pages/sample/form?id=${row.id}&mode=edit`
    }
    return `/pages/sample/detail-ext?id=${row.id}`
  },
}

// 类器官收样记录这一档（SAMPLE-MP-002）。
//
// ★ 取数口与样本那一档**同一个形状**：外部 `sampleKind=organoid` + `onlyMine`（只列可见集合：
//   本人 + 同组已核验同事）；内部 `sort=recent` + 开关打开才带 `mine=true`
//   —— 内部默认是**中心全部内部人员**经手过的（CR-20260918-07 覆盖 ticket 正文里那句
//   「内部 `mine=true`」，与样本那一档保持一致）。
// ★ 点行：外部本人且可改 → 类器官填写页 edit，其余 → 类器官填写页 view（这一档没有单独的外部详情页）；
//   内部 → edit（外部送来还没核验的由纯函数算成只读，页面上连「修改」都不出现）。
const organoidSource: HistorySource = {
  emptyText: '你填过的类器官送样记录会出现在这里',

  fetch(identity, onlyMine, pageNum, pageSize) {
    if (identity === 'internal') {
      return fetchIntSampleList({
        sampleKind: 'organoid',
        sort: 'recent',
        mine: onlyMine,
        pageNum,
        pageSize,
      })
    }
    return fetchExtSampleList({
      sampleKind: 'organoid',
      onlyMine,
      pageNum,
      pageSize,
    })
  },

  toRow: toHistoryRow,

  target(identity, row) {
    if (identity === 'internal') {
      return `/pages/organoid/form?id=${row.id}&mode=edit`
    }
    const raw = row.raw as SampleRow
    if (isMine(raw) && raw.editable === true) {
      return `/pages/organoid/form?id=${row.id}&mode=edit`
    }
    return `/pages/organoid/form?id=${row.id}&mode=view`
  },
}

// 石蜡包埋送样记录这一档（EMBED-MP-001）。
//
// ★ 内部取数口 = `GET /mp/int/embed/list?sort=recent`（**不带 mine**）：默认是
//   「中心全部内部人员新增或修改过的石蜡包埋记录」（CR-20260918-07，甲方原话
//   「我们内部人员也有多个哦，江夏实验室所有的工作人员」）；顶部「只看我提交的」开关
//   （默认关）打开才**另外**带 `mine=true`。
//   `sort=recent` 只管排序（按最后修改、没有则创建时间倒序），**不拿 mine 兼当排序**。
// ★ 外部取数口 = `GET /mp/ext/embed/list?onlyMine=`（外部接口本来就按最近倒序，不另收 sort）。
// ★ 行 = 石蜡块编号（还没有编号的外部送样显示送检单号 +「待核验 / 无效」）· 样本类型 ·
//   经手人（本人显示「我」）·「新增 / 修改」· 日期。
// ★ 点行：内部 → 修改模式（别人录的也能改，CR-20260918-07；外部送来还没核验的由后端挡成只读）；
//   外部本人且可改 → 改后重提，其余 → 只读。
function toEmbedHistoryRow(row: EmbedDetail): HistoryRow {
  const status = String(row.verifyStatus || '')
  return {
    id: String(row.id),
    code: embedHistoryCode(row),
    summary: embedHistorySummary(row),
    owner: handlerLabel(row),
    action: embedHistoryAction(row),
    date: embedHistoryDate(row),
    status,
    statusText: STATUS_TEXT[status] || '',
    reason: status === 'invalid' ? String(row.invalidReason || '') : '',
    raw: row,
  }
}

const embedSource: HistorySource = {
  emptyText: '你填过的石蜡包埋送样记录会出现在这里',

  fetch(identity, onlyMine, pageNum, pageSize) {
    if (identity === 'internal') {
      // ★ 默认中心全员（`sort=recent` **不带** mine）；开关打开才另外带 mine=true。
      return fetchIntEmbedList({
        sort: 'recent',
        mine: onlyMine,
        pageNum,
        pageSize,
      })
    }
    return fetchExtEmbedList({ onlyMine, pageNum, pageSize })
  },

  toRow: toEmbedHistoryRow,

  target(identity, row) {
    if (identity === 'internal') {
      return `/pages/embed/form?id=${row.id}&mode=edit`
    }
    const raw = row.raw as EmbedDetail
    if (raw.mine === true && raw.editable === true) {
      return `/pages/embed/form?id=${row.id}&mode=edit`
    }
    return `/pages/embed/form?id=${row.id}&mode=view`
  },
}

// -80 冻存记录这一档（CRYO-MP-001）。
//
// ★ 内部取数口 = `GET /mp/int/cryo/batch/list?sort=recent`（**不带 mine**）：默认是
//   「中心全部内部人员新增或修改过的冻存记录」（CR-20260918-07，甲方原话
//   「我们内部人员也有多个哦，江夏实验室所有的工作人员」）；顶部「只看我提交的」开关
//   （默认关）打开才**另外**带 `mine=true`。`sort=recent` 只管排序
//   （按最后修改、没有则创建时间倒序），**不拿 mine 兼当排序**。
// ★ 行 = 冻存样品（等宽）·「剩 N / 初始 M 支」· 经手人（本人显示「我」）·「新增 / 修改」· 日期。
// ★ 点行一律进**修改模式**（别人录的也能改，CR-20260918-07）；
//   这一档外部没有页签（`entriesFor` 已管住），接口也 403。
function toCryoHistoryRow(row: CryoBatchRow): HistoryRow {
  return {
    id: String(row.id),
    code: cryoHistoryCode(row),
    summary: cryoHistorySummary(row),
    owner: handlerLabel(row),
    action: cryoHistoryAction(row),
    date: cryoHistoryDate(row),
    status: '',
    statusText: '',
    reason: '',
    raw: row,
  }
}

const cryoSource: HistorySource = {
  emptyText: '你填过的冻存记录会出现在这里',

  fetch(_identity, onlyMine, pageNum, pageSize) {
    // ★ 默认中心全员（`sort=recent` **不带** mine）；开关打开才另外带 mine=true。
    return fetchIntCryoList({ sort: 'recent', mine: onlyMine, pageNum, pageSize })
  },

  toRow: toCryoHistoryRow,

  target(_identity, row) {
    // 内部：一律进修改模式（谁录的都能改）；冻存这一档没有只读详情页
    return `/pages/cryo/form?id=${row.id}&mode=edit`
  },
}

/** 页签 key → 数据源（样本记录 / 类器官收样 / 石蜡包埋 / -80 冻存四档） */
export const HISTORY_SOURCES: Partial<Record<EntryKey, HistorySource>> = {
  sample: sampleSource,
  organoid: organoidSource,
  embed: embedSource,
  cryo: cryoSource,
}

export function sourceOf(key: EntryKey): HistorySource | null {
  return HISTORY_SOURCES[key] ?? null
}

/** 查不到数据源的页签的空态文案（四档都已注册；只在页签键不认识时兜底） */
export const UNREGISTERED_TEXT = '这一档暂时没有可看的记录'
