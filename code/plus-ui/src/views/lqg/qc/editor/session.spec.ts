import { describe, expect, it } from 'vitest';
import { decideSampleSwitch, sampleIdOf } from './session';
import { URL_FRESH_MS, urlsMayBeStale } from '../components/freshUrls';

// ============================================================================
// 质控编辑页的两条纯判据（H 批 H2 组 · Kevin 本机验收「网页工作台」第 3 行、第 8 行第 2 点）
//
//   1) 换样本：地址里的 sampleId 变了 → 重新打开；有没保存的改动、又没在离开时确认过 → 先问一句；
//      同一个样本 → 什么都不动（keep-alive 留着的改动接着编辑）。
//   2) 签名地址保鲜：10 分钟的签名链接，取回来超过 5 分钟就重取；刚取的还打不开 → 不重取（真坏了）。
// ============================================================================

describe('sampleIdOf：地址里的样本', () => {
  it('取 sampleId 原样（长 id 不走 Number，避免精度丢失）', () => {
    expect(sampleIdOf({ sampleId: '2103006300488052738' })).toBe('2103006300488052738');
  });

  it('没带 / 空白 → 空串（页面给引导，不是上一次的样本）', () => {
    expect(sampleIdOf({})).toBe('');
    expect(sampleIdOf({ sampleId: '  ' })).toBe('');
    expect(sampleIdOf({ sampleId: null })).toBe('');
  });

  it('重复参数取第一个', () => {
    expect(sampleIdOf({ sampleId: ['9000001001', '9000001009'] })).toBe('9000001001');
  });
});

describe('decideSampleSwitch：换样本怎么办', () => {
  it('还是这个样本 → stay（不管有没有改动）', () => {
    expect(decideSampleSwitch('9000001001', '9000001001', false, false)).toBe('stay');
    expect(decideSampleSwitch('9000001001', '9000001001', true, false)).toBe('stay');
  });

  it('换了样本、没有改动 → 直接打开新的（原来的坏法：一直显示第一次进的那个）', () => {
    expect(decideSampleSwitch('9000001001', '9000001009', false, false)).toBe('open');
  });

  it('换了样本、有没保存的改动 → 先问一句', () => {
    expect(decideSampleSwitch('9000001001', '9000001009', true, false)).toBe('confirm');
  });

  it('改动在离开编辑页时已经确认过丢掉 → 直接换，不问第二遍', () => {
    expect(decideSampleSwitch('9000001001', '9000001009', true, true)).toBe('open');
  });

  it('从有样本到没带样本 → 打开引导页（有改动同样先问）', () => {
    expect(decideSampleSwitch('9000001001', '', false, false)).toBe('open');
    expect(decideSampleSwitch('9000001001', '', true, false)).toBe('confirm');
  });
});

describe('urlsMayBeStale：签名地址该不该重取', () => {
  const t0 = 1_758_000_000_000;

  it('刚取回来 → 不重取（刚取的也打不开是真坏了，避免失败 → 重取 → 失败的死循环）', () => {
    expect(urlsMayBeStale(t0, t0)).toBe(false);
    expect(urlsMayBeStale(t0, t0 + URL_FRESH_MS - 1)).toBe(false);
  });

  it('取回来满 5 分钟 → 重取（签名 10 分钟过期，留足余量）', () => {
    expect(URL_FRESH_MS).toBe(5 * 60 * 1000);
    expect(urlsMayBeStale(t0, t0 + URL_FRESH_MS)).toBe(true);
    expect(urlsMayBeStale(t0, t0 + 3 * 60 * 60 * 1000)).toBe(true);
  });

  it('还没取过（0）→ 没有可重取的', () => {
    expect(urlsMayBeStale(0, t0)).toBe(false);
  });
});
