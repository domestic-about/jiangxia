// 操作人统计：把接口给的「四张表各一块」并成**一张表**（纯函数，CR-20261013-23）—— 页面只管渲染，spec 直接钉这里。
//
// Kevin 2026-10-13：「不需要四个表，只需要一个表就可以了」「顶部筛选加两个：操作人和表类型，默认全部」。
// 甲方问的本来就是「某个人做了多少」→ **一行一个人**，一列是一张表里的一个数（石蜡包埋两列、冻存两列），
// 横着看就是这个人在四张表里各做了多少；竖着看是一张表里每个人做了多少。
//
// ★ 各列各算各的，**不横向相加**（样本记录信息表是从原代开始养的，和别的表里的操作人不是一回事）—— 所以没有「总计」列。
// ★ 接口不变：筛选（操作人、表类型）都在这里对同一份结果做，数字仍然是接口算的那些。
import type { OperatorStatRow, OperatorStatSheet, OperatorStatSheetKey } from '@/api/lqg/stat';
import { PERSON_NONE, parsePersonKey, userKey } from '@/components/lqg/StaffSelect/staff';
import type { StaffOption } from '@/components/lqg/StaffSelect/staff';

/** 一列 = 一张表里的一个数 */
export interface PivotColumn {
  /** `<sheet>.<metric>`，行上 cells 的键 */
  id: string;
  sheet: OperatorStatSheetKey;
  metric: string;
  label: string;
  unit: string;
}

/** 一组列 = 一张表（表头第一层） */
export interface PivotGroup {
  sheet: OperatorStatSheetKey;
  title: string;
  /** 时间段按这张表的哪一栏算 */
  dateLabel: string;
  columns: PivotColumn[];
}

/** 一行 = 一个人 */
export interface PivotRow {
  /** u:<账号 id> / n:<姓名> / none */
  key: string;
  name: string;
  linked: boolean;
  me: boolean;
  /** 列 id → 值（这个人在这一列没有记录 = 0） */
  cells: Record<string, number>;
}

export interface PivotTable {
  groups: PivotGroup[];
  rows: PivotRow[];
  /** 列 id → 表里各行之和 */
  totals: Record<string, number>;
}

export interface PivotFilter {
  /** 只看这个人（行的 key）；空 = 全部 */
  personKey?: string | null;
  /** 只看这张表；空 = 全部 */
  sheet?: string | null;
  /** 选中的人在这段时间里一条记录都没有时，那一行显示的名字 */
  personName?: string | null;
}

/** 行的先后：关联了账号的人（按姓名）→ 没关联账号的手填姓名 → 「未填」 */
const rank = (row: Pick<PivotRow, 'key' | 'linked'>): number => (row.key === PERSON_NONE ? 2 : row.linked ? 0 : 1);

export const pivot = (sheets: readonly OperatorStatSheet[] | null | undefined, filter: PivotFilter = {}): PivotTable => {
  const picked = (sheets ?? []).filter((sheet) => !filter.sheet || sheet.sheet === filter.sheet);
  const groups: PivotGroup[] = picked.map((sheet) => ({
    sheet: sheet.sheet,
    title: sheet.title,
    dateLabel: sheet.dateLabel,
    columns: sheet.metrics.map((metric) => ({
      id: `${sheet.sheet}.${metric.key}`,
      sheet: sheet.sheet,
      metric: metric.key,
      label: metric.label,
      unit: metric.unit
    }))
  }));
  const columnIds = groups.flatMap((group) => group.columns.map((column) => column.id));
  const zero = (): Record<string, number> => Object.fromEntries(columnIds.map((id) => [id, 0]));

  const byKey = new Map<string, PivotRow>();
  for (const sheet of picked) {
    for (const row of sheet.rows ?? []) {
      let target = byKey.get(row.key);
      if (!target) {
        target = { key: row.key, name: row.name, linked: !!row.linked, me: !!row.me, cells: zero() };
        byKey.set(row.key, target);
      }
      for (const metric of sheet.metrics) {
        target.cells[`${sheet.sheet}.${metric.key}`] = Number(row.values?.[metric.key] ?? 0);
      }
    }
  }

  let rows = [...byKey.values()];
  if (filter.personKey) {
    rows = rows.filter((row) => row.key === filter.personKey);
    // 选了一个人、他这段时间一条都没有：给一行 0（「没做」也是答案），不是一张空表
    if (rows.length === 0) {
      rows = [
        {
          key: filter.personKey,
          name: filter.personName || filter.personKey,
          linked: parsePersonKey(filter.personKey)?.kind === 'user',
          me: false,
          cells: zero()
        }
      ];
    }
  }
  rows.sort((a, b) => rank(a) - rank(b) || a.name.localeCompare(b.name, 'zh-Hans-CN') || a.key.localeCompare(b.key));

  const totals = zero();
  for (const row of rows) {
    for (const id of columnIds) {
      totals[id] += row.cells[id] ?? 0;
    }
  }
  return { groups, rows, totals };
};

export interface PersonOption {
  value: string;
  label: string;
}

/**
 * 顶部「操作人」筛选的选项：内部人员名单里的人 → 这段时间的数据里出现过、但不在名单里的
 *（已撤销授权的人、没关联账号的手填姓名，带标注）→「未填」。
 */
export const personOptions = (
  staff: readonly StaffOption[],
  sheets: readonly OperatorStatSheet[] | null | undefined,
  labels: { unlinked: string; gone: string; none: string }
): PersonOption[] => {
  const out: PersonOption[] = staff.map((s) => ({ value: userKey(s.userId), label: s.name }));
  const seen = new Set(out.map((o) => o.value));
  const extra: Array<Pick<OperatorStatRow, 'key' | 'name' | 'linked'>> = [];
  for (const sheet of sheets ?? []) {
    for (const row of sheet.rows ?? []) {
      if (row.key !== PERSON_NONE && !seen.has(row.key)) {
        seen.add(row.key);
        extra.push(row);
      }
    }
  }
  extra
    .sort((a, b) => Number(!a.linked) - Number(!b.linked) || a.name.localeCompare(b.name, 'zh-Hans-CN'))
    // 名单没拉到（空的）时不给人挂「已不在名单」：那是名单的问题，不是这个人的
    .forEach((row) => out.push({ value: row.key, label: `${row.name}${row.linked ? (staff.length ? labels.gone : '') : labels.unlinked}` }));
  out.push({ value: PERSON_NONE, label: labels.none });
  return out;
};
