// 操作人统计的时间段与显示（纯函数，CR-20261012-22）—— 页面只管渲染，spec 直接钉这里。与工作台同一个口径。
//
// 甲方原话：「这个月她包埋了多少个，或者是这周，或者是去年，还有一个总的」「在这之前，或者是之后」。
//   · 本周（周一到周日）/ 本月 / 今年 / 去年 / 全部；
//   · 自定义：开始、结束各自可空 —— 只填结束 = 这一天及之前，只填开始 = 这一天及之后。
import type { OperatorStatRow, OperatorStatSheet } from '@/api/stat'

export type RangePreset = 'week' | 'month' | 'year' | 'lastYear' | 'all' | 'custom'

export const RANGE_PRESETS: Array<{ value: RangePreset, label: string }> = [
  { value: 'week', label: '本周' },
  { value: 'month', label: '本月' },
  { value: 'year', label: '今年' },
  { value: 'lastYear', label: '去年' },
  { value: 'all', label: '全部' },
  { value: 'custom', label: '自定义' },
]

export interface DateRange {
  /** yyyy-MM-dd；'' = 这一头不限 */
  begin: string
  end: string
}

const pad = (n: number) => String(n).padStart(2, '0')

/** 本地日期 → yyyy-MM-dd（不经过 UTC：toISOString 在东八区的凌晨会退一天） */
export function ymd(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 预设时间段（两端都含）；custom 不在这里算（用页面上两个日期的值） */
export function presetRange(preset: RangePreset, today: Date): DateRange {
  const y = today.getFullYear()
  const m = today.getMonth()
  switch (preset) {
    case 'week': {
      // 周一为一周的第一天：周日（getDay() = 0）往回退 6 天
      const offset = (today.getDay() + 6) % 7
      const monday = new Date(y, m, today.getDate() - offset)
      const sunday = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + 6)
      return { begin: ymd(monday), end: ymd(sunday) }
    }
    case 'month':
      return { begin: ymd(new Date(y, m, 1)), end: ymd(new Date(y, m + 1, 0)) }
    case 'year':
      return { begin: `${y}-01-01`, end: `${y}-12-31` }
    case 'lastYear':
      return { begin: `${y - 1}-01-01`, end: `${y - 1}-12-31` }
    default:
      return { begin: '', end: '' }
  }
}

/** 自定义时间段：开始晚于结束 = 填反了 */
export function rangeInverted(range: DateRange): boolean {
  return !!range.begin && !!range.end && range.begin > range.end
}

/** 这段时间用一句话怎么说 */
export function rangeText(range: DateRange): string {
  if (range.begin && range.end) {
    return `${range.begin} 至 ${range.end}`
  }
  if (range.end) {
    return `${range.end} 及之前`
  }
  if (range.begin) {
    return `${range.begin} 及之后`
  }
  return '全部时间'
}

/** 每张表标题下面那句说明（统计口径；与工作台同一组话） */
const SHEET_NOTE: Record<string, string> = {
  tissue: '只算已核验有效的记录',
  organoid: '只算已核验有效的记录',
  embed: '只算已核验有效的石蜡块；没填石蜡包埋时间的只在「全部」里',
  cryo: '按冻存人算；支数 = 冻存数量之和（不是当前剩余）',
}

export function sheetNote(sheet: Pick<OperatorStatSheet, 'sheet' | 'dateLabel'>): string {
  const note = SHEET_NOTE[sheet.sheet]
  return note ? `按${sheet.dateLabel} · ${note}` : `按${sheet.dateLabel}`
}

/** 行上的小标：我 / 未关联账号 / 没有 */
export function rowTag(row: Pick<OperatorStatRow, 'me' | 'linked' | 'key'>): '' | 'me' | 'unlinked' {
  if (row.me) {
    return 'me'
  }
  return !row.linked && row.key !== 'none' ? 'unlinked' : ''
}

/**
 * 小程序上的行序：**当前登录人那一行置顶**（「我做了多少个」是打开这页最想看的），其余保持接口给的顺序
 *（按第一个数从多到少，「未填」在最后）。
 */
export function rowsMeFirst<T extends Pick<OperatorStatRow, 'me'>>(rows: readonly T[]): T[] {
  return [...rows.filter(r => r.me), ...rows.filter(r => !r.me)]
}
