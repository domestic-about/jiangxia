// 操作人统计的时间段与跳转（纯函数，CR-20261012-22）—— 页面只管渲染，spec 直接钉这里。
//
// 甲方原话：「这个月她包埋了多少个，或者是这周，或者是去年，还有一个总的」「在这之前，或者是之后」。
//   · 本周（周一到周日）/ 本月 / 今年 / 去年 / 全部；
//   · 自定义：开始、结束各自可空 —— 只填结束 = 这一天及之前，只填开始 = 这一天及之后。
import { samplePageOf } from '@/views/lqg/sample/pages';
import { CRYO_PATH, EMBED_PATH } from '@/views/lqg/sample/relation';
import type { OperatorStatSheetKey } from '@/api/lqg/stat';

export type RangePreset = 'week' | 'month' | 'year' | 'lastYear' | 'all' | 'custom';

export const RANGE_PRESETS: RangePreset[] = ['week', 'month', 'year', 'lastYear', 'all', 'custom'];

export interface DateRange {
  begin: string | null;
  end: string | null;
}

const pad = (n: number) => String(n).padStart(2, '0');

/** 本地日期 → yyyy-MM-dd（不经过 UTC：toISOString 在东八区的凌晨会退一天） */
export const ymd = (date: Date): string => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

/** 预设时间段（两端都含）；custom 不在这里算（用页面上两个日期框的值） */
export const presetRange = (preset: RangePreset, today: Date): DateRange => {
  const y = today.getFullYear();
  const m = today.getMonth();
  switch (preset) {
    case 'week': {
      // 周一为一周的第一天：周日（getDay() = 0）往回退 6 天
      const offset = (today.getDay() + 6) % 7;
      const monday = new Date(y, m, today.getDate() - offset);
      const sunday = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + 6);
      return { begin: ymd(monday), end: ymd(sunday) };
    }
    case 'month':
      return { begin: ymd(new Date(y, m, 1)), end: ymd(new Date(y, m + 1, 0)) };
    case 'year':
      return { begin: `${y}-01-01`, end: `${y}-12-31` };
    case 'lastYear':
      return { begin: `${y - 1}-01-01`, end: `${y - 1}-12-31` };
    default:
      return { begin: null, end: null };
  }
};

/** 自定义时间段：开始晚于结束 = 填反了 */
export const rangeInverted = (range: DateRange): boolean => !!range.begin && !!range.end && range.begin > range.end;

/**
 * 跳到列表时带的时间段：列表上的日期框是「区间」，必须两头都有。只填了一头的，另一头补一个够远的日期
 * （与「不限」等价），这样列表筛出来的条数与统计的数字仍是同一个范围；两头都没填 = 不带。
 */
export const linkRange = (range: DateRange): { timeBegin?: string; timeEnd?: string } => {
  if (!range.begin && !range.end) {
    return {};
  }
  return { timeBegin: range.begin ?? '2000-01-01', timeEnd: range.end ?? '2099-12-31' };
};

export interface StatLink {
  path: string;
  query: Record<string, string>;
}

/**
 * 统计表里一个数字 → 它对应的列表（带「这个人 + 这段时间」，与统计同一个口径）。
 *
 * @param sheet  哪张表
 * @param metric 哪一列：石蜡包埋的「包埋人」列按包埋人筛，「操作人」列按操作人筛；冻存两列都按冻存人筛
 * @param key    行上的 key（u:<账号 id> / n:<姓名> / none）
 * @param name   行上的姓名（那个人已不在名单里时，列表的筛选框里显示用）
 */
export const statLink = (sheet: OperatorStatSheetKey, metric: string, key: string, name: string, range: DateRange): StatLink => {
  const query: Record<string, string> = { ...linkRange(range), personName: name };
  if (sheet === 'tissue' || sheet === 'organoid') {
    return { path: samplePageOf(sheet).path, query: { ...query, operatorKey: key, verifyStatus: 'valid' } };
  }
  if (sheet === 'embed') {
    return { path: EMBED_PATH, query: { ...query, [metric === 'embedBy' ? 'embedByKey' : 'operatorKey']: key, verifyStatus: 'valid' } };
  }
  return { path: CRYO_PATH, query: { ...query, frozenByKey: key } };
};
