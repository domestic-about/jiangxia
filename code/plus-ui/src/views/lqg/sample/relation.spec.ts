// ============================================================================
// 四张表之间的来回跳转（Kevin 2026-09-24 本机验收）· relation.ts 的纯函数
//
// 数据按 seed 取：1001 T-hli01（A 医院，2 块有效石蜡块、2 批冻存）、1002（待核验，名下 1 条待核验送样）、
// 1005 T-hga03（有效，名下什么都没有）、1009 T-oco01（类器官）。
//
// 跑法（code/plus-ui）：pnpm vitest run src/views/lqg/sample/relation.spec.ts
// ============================================================================

import { describe, expect, it } from 'vitest';
import {
  CRYO_PATH,
  EMBED_PATH,
  SUBMIT_SOURCES,
  VERIFY_STATUSES,
  cryoOfSample,
  embedOfSample,
  flagOfQuery,
  oneOfQuery,
  queryWithout,
  relationOf,
  routeKeyOf,
  sampleIdOfQuery,
  sampleOf,
  sampleScopeLabel
} from './relation';

const ALL = { embedList: true, embedAdd: true, cryoList: true, cryoAdd: true };

describe('去向：样本 → 石蜡包埋 / 冻存，带 sampleId（不是内部编号）', () => {
  it('路径是菜单 path（/embed、/cryo），不是 /lqg/embed', () => {
    expect(EMBED_PATH).toBe('/embed');
    expect(CRYO_PATH).toBe('/cryo');
    expect(embedOfSample('9000001001')).toEqual({ path: '/embed', query: { sampleId: '9000001001' } });
    expect(cryoOfSample(9000001004)).toEqual({ path: '/cryo', query: { sampleId: '9000001004' } });
  });

  it('「待核验 N」带上核验状态；数量为 0 的「新增」带 add=1', () => {
    expect(embedOfSample('1', { pending: true }).query).toEqual({ sampleId: '1', verifyStatus: 'pending' });
    expect(embedOfSample('1', { add: true }).query).toEqual({ sampleId: '1', add: '1' });
    expect(cryoOfSample('1', { add: true }).query).toEqual({ sampleId: '1', add: '1' });
  });
});

describe('回来：石蜡包埋 / 冻存的一行 → 它的样本所在那一页', () => {
  it('组织样本回样本记录信息表，类器官回类器官收样记录；不认识的类别按组织样本', () => {
    expect(sampleOf('tissue', '9000001001')).toEqual({ path: '/sample', query: { sampleId: '9000001001' } });
    expect(sampleOf('organoid', 9000001009)).toEqual({ path: '/sample-organoid', query: { sampleId: '9000001009' } });
    expect(sampleOf(null, '1').path).toBe('/sample');
  });
});

describe('路由 query 只认合法值', () => {
  it('样本 id 只认纯数字（雪花 id 按字符串走，不丢精度）', () => {
    expect(sampleIdOfQuery('9000001001')).toBe('9000001001');
    expect(sampleIdOfQuery(' 12 ')).toBe('12');
    expect(sampleIdOfQuery(['1928374650192837465', 'x'])).toBe('1928374650192837465');
    for (const bad of [undefined, null, '', 'abc', '1;drop', '1.5', {}, '123456789012345678901']) {
      expect(sampleIdOfQuery(bad), String(bad)).toBeNull();
    }
  });

  it('核验状态 / 来源只认字典里的几个值；开关只认 true / 1', () => {
    expect(oneOfQuery('pending', VERIFY_STATUSES)).toBe('pending');
    expect(oneOfQuery('bogus', VERIFY_STATUSES)).toBeNull();
    expect(oneOfQuery('external', SUBMIT_SOURCES)).toBe('external');
    expect(oneOfQuery(undefined, SUBMIT_SOURCES)).toBeNull();
    expect(flagOfQuery('true')).toBe(true);
    expect(flagOfQuery('1')).toBe(true);
    expect(flagOfQuery('false')).toBe(false);
    expect(flagOfQuery(undefined)).toBe(false);
  });

  it('地址筛选的指纹只看本页认的键：别的键（add、无关参数）变了不算变', () => {
    const keys = ['sampleId', 'verifyStatus'];
    expect(routeKeyOf({ sampleId: '1', add: '1' }, keys)).toBe(routeKeyOf({ sampleId: '1' }, keys));
    expect(routeKeyOf({ sampleId: '1' }, keys)).not.toBe(routeKeyOf({ sampleId: '2' }, keys));
    expect(routeKeyOf({ sampleId: ['1'] }, keys)).toBe(routeKeyOf({ sampleId: '1' }, keys));
    expect(routeKeyOf({}, keys)).toBe(routeKeyOf({ other: 'x' }, keys));
  });

  it('「看全部」只清 sampleId，别的筛选原样留着', () => {
    expect(queryWithout({ sampleId: '1', verifyStatus: 'pending', add: '1' }, ['sampleId', 'add'])).toEqual({ verifyStatus: 'pending' });
    expect(queryWithout({}, ['sampleId'])).toEqual({});
  });
});

describe('提示条上的样本名', () => {
  it('内部编号优先，括号里带来源单位', () => {
    expect(sampleScopeLabel({ internalNo: 'T-hli01', submitNo: 'SJ90000001', sourceUnitName: 'A 医院' }, '样本 1')).toBe('T-hli01（A 医院）');
  });

  it('待核验的外部样本还没有内部编号 → 送检单号；没有单位就只写编号', () => {
    expect(sampleScopeLabel({ internalNo: null, submitNo: 'SJ90000002', sourceUnitName: 'A 医院' }, '样本 2')).toBe('SJ90000002（A 医院）');
    expect(sampleScopeLabel({ internalNo: 'T-hli05', sourceUnitName: '  ' }, 'x')).toBe('T-hli05');
  });

  it('样本没加载到（已删 / 还在加载）→ 调用方给的兜底', () => {
    expect(sampleScopeLabel(null, '样本 9000001010')).toBe('样本 9000001010');
    expect(sampleScopeLabel({ internalNo: '', submitNo: '' }, '样本 1')).toBe('样本 1');
  });
});

describe('样本行的「石蜡包埋 / 冻存」一列', () => {
  it('1001：蜡块 2、冻存 2 批都可点，带 sampleId；没有待核验', () => {
    const cell = relationOf(
      { id: '9000001001', verifyStatus: 'valid', hint: { blockCount: 2 }, relation: { pendingEmbedCount: 0, cryoBatchCount: 2 } },
      ALL
    );
    expect(cell.blocks).toEqual({ count: 2, to: embedOfSample('9000001001'), addTo: null });
    expect(cell.cryo).toEqual({ count: 2, to: cryoOfSample('9000001001'), addTo: null });
    expect(cell.pending).toEqual({ count: 0, to: null, addTo: null });
  });

  it('1005：有效样本名下什么都没有 → 数量「—」、给「新增」入口（到了直接开新增抽屉）', () => {
    const cell = relationOf(
      { id: '9000001005', verifyStatus: 'valid', hint: { blockCount: 0 }, relation: { pendingEmbedCount: 0, cryoBatchCount: 0 } },
      ALL
    );
    expect(cell.blocks).toEqual({ count: 0, to: null, addTo: embedOfSample('9000001005', { add: true }) });
    expect(cell.cryo).toEqual({ count: 0, to: null, addTo: cryoOfSample('9000001005', { add: true }) });
  });

  it('1002：待核验样本 —— 合作单位送来的 1 条待核验送样单独标、可点（带待核验筛选）；不能挂新记录，不给「新增」', () => {
    const cell = relationOf(
      { id: '9000001002', verifyStatus: 'pending', hint: { blockCount: 0 }, relation: { pendingEmbedCount: 1, cryoBatchCount: 0 } },
      ALL
    );
    expect(cell.pending).toEqual({ count: 1, to: embedOfSample('9000001002', { pending: true }), addTo: null });
    expect(cell.blocks.addTo).toBeNull();
    expect(cell.cryo.addTo).toBeNull();
  });

  it('没有列表权限不可点、没有新增权限不给「新增」', () => {
    const row = { id: '1', verifyStatus: 'valid', hint: { blockCount: 3 }, relation: { pendingEmbedCount: 1, cryoBatchCount: 0 } };
    const cell = relationOf(row, { embedList: false, embedAdd: true, cryoList: true, cryoAdd: false });
    expect(cell.blocks.to).toBeNull();
    expect(cell.pending.to).toBeNull();
    expect(cell.cryo.addTo).toBeNull();
    expect(cell.blocks.count).toBe(3);
  });

  it('hint / relation 缺失或脏值都按 0 处理（不渲染 NaN / undefined）', () => {
    const cell = relationOf({ id: '1', verifyStatus: 'invalid', hint: null, relation: { pendingEmbedCount: null, cryoBatchCount: -1 } }, ALL);
    expect([cell.blocks.count, cell.pending.count, cell.cryo.count]).toEqual([0, 0, 0]);
    expect(cell.blocks.addTo).toBeNull();
  });
});
