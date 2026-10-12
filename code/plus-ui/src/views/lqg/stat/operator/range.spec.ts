// 操作人统计的时间段与跳转（CR-20261012-22）。
import { describe, expect, it } from 'vitest';
import { linkRange, presetRange, rangeInverted, statLink, ymd } from './range';

describe('presetRange', () => {
  // 2026-10-12 是周一；2026-10-18 是周日
  it('本周 = 周一到周日（周日那天也还在这一周里）', () => {
    expect(presetRange('week', new Date(2026, 9, 12))).toEqual({ begin: '2026-10-12', end: '2026-10-18' });
    expect(presetRange('week', new Date(2026, 9, 14))).toEqual({ begin: '2026-10-12', end: '2026-10-18' });
    expect(presetRange('week', new Date(2026, 9, 18))).toEqual({ begin: '2026-10-12', end: '2026-10-18' });
  });

  it('本周跨月、跨年也对', () => {
    expect(presetRange('week', new Date(2026, 11, 31))).toEqual({ begin: '2026-12-28', end: '2027-01-03' });
  });

  it('本月：到当月最后一天（二月、闰年）', () => {
    expect(presetRange('month', new Date(2026, 9, 12))).toEqual({ begin: '2026-10-01', end: '2026-10-31' });
    expect(presetRange('month', new Date(2028, 1, 10))).toEqual({ begin: '2028-02-01', end: '2028-02-29' });
  });

  it('今年 / 去年 / 全部', () => {
    const today = new Date(2026, 9, 12);
    expect(presetRange('year', today)).toEqual({ begin: '2026-01-01', end: '2026-12-31' });
    expect(presetRange('lastYear', today)).toEqual({ begin: '2025-01-01', end: '2025-12-31' });
    expect(presetRange('all', today)).toEqual({ begin: null, end: null });
  });

  it('日期按本地算，不经过 UTC（凌晨不会退一天）', () => {
    expect(ymd(new Date(2026, 0, 1, 0, 30))).toBe('2026-01-01');
  });
});

describe('rangeInverted', () => {
  it('只在两头都填、且开始晚于结束时算填反', () => {
    expect(rangeInverted({ begin: '2026-10-12', end: '2026-10-01' })).toBe(true);
    expect(rangeInverted({ begin: '2026-10-01', end: '2026-10-01' })).toBe(false);
    expect(rangeInverted({ begin: null, end: '2026-10-01' })).toBe(false);
  });
});

describe('statLink', () => {
  const month = { begin: '2026-10-01', end: '2026-10-31' };

  it('样本两张表：各回各的页，带操作人 + 收样日期段 + 已核验有效', () => {
    expect(statLink('tissue', 'count', 'u:9000000101', '李工', month)).toEqual({
      path: '/sample',
      query: { timeBegin: '2026-10-01', timeEnd: '2026-10-31', personName: '李工', operatorKey: 'u:9000000101', verifyStatus: 'valid' }
    });
    expect(statLink('organoid', 'count', 'none', '未填', month).path).toBe('/sample-organoid');
  });

  it('★ 石蜡包埋：「包埋人」那一列按包埋人筛，「操作人」那一列按操作人筛（两列不是一回事）', () => {
    expect(statLink('embed', 'embedBy', 'n:袁琴琴', '袁琴琴', month).query).toMatchObject({ embedByKey: 'n:袁琴琴', verifyStatus: 'valid' });
    expect(statLink('embed', 'embedBy', 'n:袁琴琴', '袁琴琴', month).query.operatorKey).toBeUndefined();
    expect(statLink('embed', 'operator', 'u:1', '李工', month).query).toMatchObject({ operatorKey: 'u:1' });
    expect(statLink('embed', 'operator', 'u:1', '李工', month).path).toBe('/embed');
  });

  it('冻存：批次、支数两列都按冻存人筛', () => {
    expect(statLink('cryo', 'tubes', 'u:1', '李工', month)).toEqual({
      path: '/cryo',
      query: { timeBegin: '2026-10-01', timeEnd: '2026-10-31', personName: '李工', frozenByKey: 'u:1' }
    });
  });

  it('★ 全部 = 不带时间段；只填了一头的补一个够远的日期（列表的日期框要两头都有）', () => {
    expect(linkRange({ begin: null, end: null })).toEqual({});
    expect(linkRange({ begin: null, end: '2026-09-30' })).toEqual({ timeBegin: '2000-01-01', timeEnd: '2026-09-30' });
    expect(linkRange({ begin: '2026-10-01', end: null })).toEqual({ timeBegin: '2026-10-01', timeEnd: '2099-12-31' });
  });
});
