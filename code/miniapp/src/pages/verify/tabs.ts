import { maskDonorName } from '@/api/ledger'
import type { PendingEmbedRow, PendingSampleRow } from '@/api/verify'
import { ENTRY_SHORT } from '@/pages/index/entries'

// 「待核验」列表页的纯函数（内部人员在小程序里核验）。
//
// 来源：甲方 2026-09-24 测试问题记录表第 20 行 ——「小程序和工作台界面都要能操作」核验，
// Kevin 定这次做（推翻 CR-20260917-05 的「小程序里不做核验」）。冻存取用登记是另一组的事。
//
// 口径：
// 1. 三个页签 = 三种要核验的东西：样本记录（组织样本）/ 类器官收样 / 石蜡包埋送样。
//    `?tab=` 不认识或缺失 → 第一个页签（首页「待核验样本」点进来就是它）。
// 2. 列的是 `verifyStatus=pending` 的全部记录，**不另按内 / 外部筛**：
//    首页的数（`HomeCounterService`）数的就是全部待核验，两边得对得上；
//    待核验的记录本来只可能是合作单位送来的（内部录入直接有效）。
// 3. 点一行 / 表格页点一条待核验记录 → 核验页（`verifyTarget`，表格页 `sheets.ts` 用同一个函数）。
// 4. 每行：送检单号（等宽）、摘要（来源单位 · 关键字段）、提交人、「提交 / 重新提交」、时间。
//    列表按小程序列表规则给供体姓名打码（保留姓），全名在核验页里看。

/** 页签 key（与表格页的工作表 key 同名，方便对照） */
export const VERIFY_TABS = ['tissue', 'organoid', 'embed'] as const
export type VerifyTab = (typeof VERIFY_TABS)[number]

/** 页签短名：与表格页切换条、历史编辑记录同一份（`entries.ts#ENTRY_SHORT`，不另写一套） */
export const VERIFY_TAB_TITLE: Record<VerifyTab, string> = {
  tissue: ENTRY_SHORT.sample,
  organoid: ENTRY_SHORT.organoid,
  embed: ENTRY_SHORT.embed,
}

/** 列表为空时那一句 */
export const VERIFY_EMPTY_TEXT = '没有待核验的记录'

/** `?tab=` → 页签；缺失 / 不认识 → 第一个 */
export function tabOf(raw: unknown): VerifyTab {
  return (VERIFY_TABS as readonly unknown[]).includes(raw) ? raw as VerifyTab : VERIFY_TABS[0]
}

/** 一条记录的核验页（样本两张表共用一页，页面按详情的类别渲染） */
export function verifyTarget(tab: VerifyTab, id: string | number): string {
  return tab === 'embed' ? `/pages/verify/embed?id=${id}` : `/pages/verify/sample?id=${id}`
}

/** 这一行是不是待核验（表格页据此决定点一行进核验页还是只读页） */
export function isPending(row: { verifyStatus?: unknown } | null | undefined): boolean {
  return row?.verifyStatus === 'pending'
}

/** 列表一行的展示数据（交给 `SampleCard`） */
export interface VerifyCard {
  id: string
  /** 送检单号（石蜡包埋送样没有自己的单号，用所挂样本的） */
  code: string
  summary: string
  /** 提交人 */
  owner: string
  /** 「提交」或「重新提交」 */
  action: string
  /** 提交时间，到分钟 */
  date: string
}

function str(value: unknown): string {
  return value === null || value === undefined ? '' : String(value).trim()
}

/** `yyyy-MM-dd HH:mm:ss` → `yyyy-MM-dd HH:mm`（其余形状原样） */
export function minuteOf(value: unknown): string {
  const text = str(value)
  const hit = /^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})/.exec(text)
  return hit ? `${hit[1]} ${hit[2]}` : text
}

/** `yyyy-MM-dd HH:mm:ss` → 毫秒（认不出来 → NaN） */
function msOf(value: string): number {
  return new Date(value.replace(' ', 'T')).getTime()
}

/**
 * 提交时间与动作：待核验的记录只有合作单位自己能改（改完重提），实验室这边的普通保存对待核验一律拒，
 * 所以「最后修改时间比创建时间晚」就是「合作单位改过后重新提交了」—— 时间取最后那一次。
 * ★ 外部新建的那一刻后端就把最后修改时间填成了创建时间（实测两者相同），所以不能只看「有没有」，
 *   要看「晚了没有」（留 2 秒余量；时间认不出来时退回按字面比）。
 */
function submitted(row: { updateTime?: unknown, createTime?: unknown }): { action: string, date: string } {
  const created = str(row.createTime)
  const updated = str(row.updateTime)
  const later = msOf(updated) - msOf(created)
  const resubmitted = !!updated && (Number.isNaN(later) ? updated !== created : later > 2000)
  return resubmitted
    ? { action: '重新提交', date: minuteOf(updated) }
    : { action: '提交', date: minuteOf(created || updated) }
}

/** 样本记录 / 类器官收样的一行 → 卡片 */
export function sampleCardOf(row: PendingSampleRow): VerifyCard {
  const organoid = row.sampleKind === 'organoid'
  const parts = organoid
    ? [str(row.sourceUnitName), str(row.organoidType), str(row.passage)]
    : [str(row.sourceUnitName), str(row.tissueType), maskDonorName(row.donorName)]
  return {
    id: String(row.id),
    code: str(row.submitNo) || '—',
    summary: parts.filter(Boolean).join(' · '),
    owner: str(row.submitterName),
    ...submitted(row),
  }
}

/**
 * 石蜡包埋送样的一行 → 卡片。
 * 所挂样本自己还没核验时在摘要末尾写明（这条送样要等样本核验有效才能判有效）。
 */
export function embedCardOf(row: PendingEmbedRow): VerifyCard {
  const parts = [str(row.sourceUnitName), str(row.sampleType), str(row.organoidSourceType)]
  if (row.sampleVerifyStatus === 'pending') {
    parts.push('样本也待核验')
  }
  return {
    id: String(row.id),
    code: str(row.submitNo) || '—',
    summary: parts.filter(Boolean).join(' · '),
    owner: str(row.handlerName),
    ...submitted(row),
  }
}
