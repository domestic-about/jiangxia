// 「历史编辑记录」页签的数据源注册表（SAMPLE-MP-001 §2 / UI:mp.history）。
//
// 页签清单**不在这里**：它由 `pages/index/entries.ts` 的 `entriesFor(identity)` 给
//（内部四张、外部三张），本文件只登记「哪个页签的数据从哪来、一行怎么摘要、点一行去哪」。
// 本张登记 `sample` 与 `organoid` 两档（SAMPLE-MP-001 / SAMPLE-MP-002）；其余的键查不到数据源 →
// 页面显示空状态（SYS-MP-001 已定的写法：还没注册数据源的页签显示空状态，不另写第二份页签清单）。
//
// ★ 口径（CR-20260918-07，内外部共用同一个「只看我提交的」开关）：
//   外部 = 可见集合（本人 + 同组）→ 开关打开才另带 `onlyMine=true`；行 = 掩码供体 · 类型 ·
//          「我 / 同组 某某」· 状态徽标 · 日期；点行：本人且可改 → 填写页 edit，其余 → 外部详情。
//   内部 = **中心全部内部人员**经手过的（取数口 `sort=recent`，**不带 mine**）→ 开关打开才另带
//          `mine=true` 收窄到本人；行 = 内部编号（没有则送检单号）· 摘要 · 经手人（本人显示「我」）·
//          「新增 / 修改」（看 `updateTime` 空不空）· 日期；点行一律进填写页 edit
//          （外部送来还没核验的由后端挡成只读）。
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
  /** 来源对象：点行时判「本人 + 可改」用（外部那条路） */
  raw: SampleRow
}

export interface HistorySource {
  /** 这一档的空状态文案 */
  emptyText: string
  /** 取一页（`onlyMine` = 顶部开关的值；内部那条路传的是「只看我提交的」） */
  fetch(identity: ResolvedIdentity, onlyMine: boolean): Promise<SampleRow[]>
  /** 一行 → 页面行 */
  toRow(row: SampleRow): HistoryRow
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
  if (isExternal) {
    // 外部行：掩码供体 · 组织类型 / 类器官类型 ·「我 / 同组 某某」· 状态 · 日期
    return {
      id: String(row.id),
      code: String(row.submitNo || ''),
      summary: [String(row.donorNameMasked || ''), summaryLabel(row)].filter(Boolean).join(' · '),
      owner: ownerLabel(row),
      action: '',
      date: latestTime(row),
      status,
      statusText: STATUS_TEXT[status] || '',
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
    raw: row,
  }
}

const sampleSource: HistorySource = {
  emptyText: '你填过的记录会出现在这里',

  async fetch(identity, onlyMine) {
    if (identity === 'internal') {
      // ★ 默认是中心全员（`sort=recent` 不带 mine）；开关打开才**另外**带 mine=true。
      // `sort=recent` 同时是范围口：没人经手过的（外部送来待核验 / 无效、外部自己改过的）不进这张清单。
      const page = await fetchIntSampleList({
        sampleKind: 'tissue',
        sort: 'recent',
        mine: onlyMine,
        pageSize: 100,
      })
      return page.rows ?? []
    }
    const page = await fetchExtSampleList({
      sampleKind: 'tissue',
      onlyMine,
      pageSize: 100,
    })
    return page.rows ?? []
  },

  toRow: toHistoryRow,

  target(identity, row) {
    if (identity === 'internal') {
      // 内部：一律进修改模式（别人录的也能改）；外部送来还没核验的由后端挡成只读
      return `/pages/sample/form?id=${row.id}&mode=edit`
    }
    // 外部：本人 + 可改 → 改后重提；其余 → 样本详情（只读）
    if (isMine(row.raw) && row.raw.editable === true) {
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
  emptyText: '你填过的类器官收样记录会出现在这里',

  async fetch(identity, onlyMine) {
    if (identity === 'internal') {
      const page = await fetchIntSampleList({
        sampleKind: 'organoid',
        sort: 'recent',
        mine: onlyMine,
        pageSize: 100,
      })
      return page.rows ?? []
    }
    const page = await fetchExtSampleList({
      sampleKind: 'organoid',
      onlyMine,
      pageSize: 100,
    })
    return page.rows ?? []
  },

  toRow: toHistoryRow,

  target(identity, row) {
    if (identity === 'internal') {
      return `/pages/organoid/form?id=${row.id}&mode=edit`
    }
    if (isMine(row.raw) && row.raw.editable === true) {
      return `/pages/organoid/form?id=${row.id}&mode=edit`
    }
    return `/pages/organoid/form?id=${row.id}&mode=view`
  },
}

/** 页签 key → 数据源（本张登记样本记录与类器官收样两档） */
export const HISTORY_SOURCES: Partial<Record<EntryKey, HistorySource>> = {
  sample: sampleSource,
  organoid: organoidSource,
}

export function sourceOf(key: EntryKey): HistorySource | null {
  return HISTORY_SOURCES[key] ?? null
}

/** 还没注册数据源的页签的空态文案（SAMPLE-MP-002 / EMBED-MP-001 / CRYO-MP-001 接） */
export const UNREGISTERED_TEXT = '这一档的记录在后续版本开放'
