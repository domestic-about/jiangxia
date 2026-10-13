// 操作人统计并成一张表（CR-20261013-23）：一行一个人、一列是一张表里的一个数；操作人 / 表类型两个筛选。
import { describe, expect, it } from 'vitest';
import type { OperatorStatSheet } from '@/api/lqg/stat';
import { personOptions, pivot } from './pivot';

const row = (key: string, name: string, values: Record<string, number>, extra: Partial<{ linked: boolean; me: boolean }> = {}) => ({
  key,
  name,
  linked: extra.linked ?? key.startsWith('u:'),
  me: extra.me ?? false,
  values
});

// 测试站 2026-10-13 的那一屏（今年）：李工 5 / 6 / 包埋 1 操作 3 / 9 批 41 支；石蜡包埋另有「未填」包埋 3 操作 1
const SHEETS: OperatorStatSheet[] = [
  { sheet: 'tissue', title: '样本记录信息表', dateLabel: '收样日期', metrics: [{ key: 'count', label: '操作人', unit: '条' }], totals: { count: 6 }, rows: [row('u:101', '李工', { count: 5 }, { me: true }), row('n:袁琴琴', '袁琴琴', { count: 1 })] },
  { sheet: 'organoid', title: '类器官送样记录', dateLabel: '收样日期', metrics: [{ key: 'count', label: '操作人', unit: '条' }], totals: { count: 6 }, rows: [row('u:101', '李工', { count: 6 }, { me: true })] },
  {
    sheet: 'embed',
    title: '石蜡包埋送样记录',
    dateLabel: '石蜡包埋时间',
    metrics: [
      { key: 'embedBy', label: '包埋人', unit: '块' },
      { key: 'operator', label: '操作人', unit: '块' }
    ],
    totals: { embedBy: 4, operator: 4 },
    rows: [row('u:101', '李工', { embedBy: 1, operator: 3 }, { me: true }), row('none', '未填', { embedBy: 3, operator: 1 }, { linked: false })]
  },
  {
    sheet: 'cryo',
    title: '-80 冻存记录',
    dateLabel: '冻存时间',
    metrics: [
      { key: 'batches', label: '批次', unit: '批' },
      { key: 'tubes', label: '支数', unit: '支' }
    ],
    totals: { batches: 10, tubes: 46 },
    rows: [row('u:101', '李工', { batches: 9, tubes: 41 }, { me: true }), row('u:102', '刘佳', { batches: 1, tubes: 5 })]
  }
];

describe('pivot（不筛）', () => {
  const table = pivot(SHEETS);

  it('列 = 四张表按序、每张表里的每个数一列（共 6 列），表头第一层是表名', () => {
    expect(table.groups.map((g) => g.title)).toEqual(['样本记录信息表', '类器官送样记录', '石蜡包埋送样记录', '-80 冻存记录']);
    expect(table.groups.flatMap((g) => g.columns.map((c) => c.id))).toEqual([
      'tissue.count',
      'organoid.count',
      'embed.embedBy',
      'embed.operator',
      'cryo.batches',
      'cryo.tubes'
    ]);
  });

  it('★ 一行一个人：同一个人在四张表里的数并到一行，没有记录的那一列是 0', () => {
    const li = table.rows.find((r) => r.key === 'u:101');
    expect(li?.cells).toEqual({ 'tissue.count': 5, 'organoid.count': 6, 'embed.embedBy': 1, 'embed.operator': 3, 'cryo.batches': 9, 'cryo.tubes': 41 });
    expect(li?.me).toBe(true);
    const liu = table.rows.find((r) => r.key === 'u:102');
    expect(liu?.cells).toEqual({ 'tissue.count': 0, 'organoid.count': 0, 'embed.embedBy': 0, 'embed.operator': 0, 'cryo.batches': 1, 'cryo.tubes': 5 });
  });

  it('行序：关联了账号的人（按姓名）→ 没关联账号的手填姓名 →「未填」永远在最后', () => {
    expect(table.rows.map((r) => r.name)).toEqual(['李工', '刘佳', '袁琴琴', '未填']);
  });

  it('合计 = 各行之和，与接口给的每张表合计一致；没有横向相加的列', () => {
    expect(table.totals).toEqual({ 'tissue.count': 6, 'organoid.count': 6, 'embed.embedBy': 4, 'embed.operator': 4, 'cryo.batches': 10, 'cryo.tubes': 46 });
    expect(Object.keys(table.rows[0].cells)).toHaveLength(6);
  });
});

describe('pivot（筛选）', () => {
  it('表类型：只留那张表的列；这张表里没有记录的人不出现', () => {
    const table = pivot(SHEETS, { sheet: 'cryo' });
    expect(table.groups.map((g) => g.sheet)).toEqual(['cryo']);
    expect(table.rows.map((r) => [r.name, r.cells])).toEqual([
      ['李工', { 'cryo.batches': 9, 'cryo.tubes': 41 }],
      ['刘佳', { 'cryo.batches': 1, 'cryo.tubes': 5 }]
    ]);
  });

  it('操作人：只留那一行，合计就是他自己的数', () => {
    const table = pivot(SHEETS, { personKey: 'none' });
    expect(table.rows.map((r) => r.name)).toEqual(['未填']);
    expect(table.totals['embed.embedBy']).toBe(3);
    expect(table.totals['tissue.count']).toBe(0);
  });

  it('★ 选的人这段时间一条都没有：给一行 0（「没做」也是答案），不是一张空表', () => {
    const table = pivot(SHEETS, { personKey: 'u:103', personName: '王五' });
    expect(table.rows).toHaveLength(1);
    expect(table.rows[0]).toMatchObject({ key: 'u:103', name: '王五', linked: true, me: false });
    expect(new Set(Object.values(table.rows[0].cells))).toEqual(new Set([0]));
  });

  it('两个一起筛：某个人在某张表里的数', () => {
    const table = pivot(SHEETS, { personKey: 'u:101', sheet: 'embed' });
    expect(table.rows.map((r) => r.cells)).toEqual([{ 'embed.embedBy': 1, 'embed.operator': 3 }]);
  });

  it('接口还没回来（null）：空表，不报错', () => {
    expect(pivot(null)).toEqual({ groups: [], rows: [], totals: {} });
  });
});

describe('personOptions（顶部「操作人」筛选）', () => {
  const STAFF = [
    { userId: '101', name: '李工' },
    { userId: '103', name: '王五' }
  ];
  const LABELS = { unlinked: '（未关联账号）', gone: '（已不在名单）', none: '未填' };

  it('名单里的人（这段时间没记录的也在）→ 数据里出现、但不在名单里的（带标注）→「未填」', () => {
    expect(personOptions(STAFF, SHEETS, LABELS)).toEqual([
      { value: 'u:101', label: '李工' },
      { value: 'u:103', label: '王五' },
      { value: 'u:102', label: '刘佳（已不在名单）' },
      { value: 'n:袁琴琴', label: '袁琴琴（未关联账号）' },
      { value: 'none', label: '未填' }
    ]);
  });

  it('名单没拉到：数据里的人照样选得到，也不误标「已不在名单」', () => {
    const options = personOptions([], SHEETS, LABELS);
    expect(options.map((o) => o.value)).toEqual(['u:101', 'u:102', 'n:袁琴琴', 'none']);
    // 名单是空的时候不给人挂「已不在名单」
    expect(options[0].label).toBe('李工');
  });
});
