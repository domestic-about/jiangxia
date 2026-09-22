// ============================================================================
// 评分即时反馈的验收单测 · QC-WEB-002
//
// 用例**不在这个文件里**：直接读需求层的 `doc/verify/fixtures/score-cases.json`
// （6 条：含 0 分档的第 2/6 例、没选全的第 4 例、全没选的第 5 例）。实现方自己编用例
// = 自证，所以本文件只做「读 fixture → 逐条跑 scoreSummary → 断 items 与 total」。
// 加一条 fixture 用例，这里自动多跑一条。
//
// ★ 两个最容易做反的点（fixture 专治）：
//   · 0 分档是「选了 0 分」——`items` 里必须是 0，不是 null；合计要把它算进去；
//   · 没选全（任一项 null）→ 合计必须是 null，不许把已选的加起来。
//
// 跑法（工作台根目录，即 code/plus-ui）：
//   npm_config_store_dir=<ws>/.pnpm-store pnpm vitest run src/views/lqg/qc/editor/score.fixture.spec.ts
// ============================================================================

import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { SCORE_KEYS, missingKeys, scoreSummary, type ScoreDictScores, type ScoreLevels } from './score';

/** 需求层 fixture 的绝对路径（从本文件往上七级 = 工作区根） */
const FIXTURE = resolve(dirname(fileURLToPath(import.meta.url)), '../../../../../../../doc/verify/fixtures/score-cases.json');

interface ScoreCase {
  levels: ScoreLevels;
  expect: { items: (number | null)[]; total: number | null };
}

const fixture = JSON.parse(readFileSync(FIXTURE, 'utf8')) as {
  dictScores: ScoreDictScores;
  cases: ScoreCase[];
};
const cases = fixture.cases;

describe('scoreSummary · 需求层 fixture（doc/verify/fixtures/score-cases.json）', () => {
  it('fixture 至少 6 条、且含「0 分档」与「合计为空」两类（fixture 被掏空也会红）', () => {
    expect(cases.length).toBeGreaterThanOrEqual(6);
    expect(cases.some((c) => c.expect.items.includes(0))).toBe(true);
    expect(cases.some((c) => c.expect.total === null)).toBe(true);
  });

  cases.forEach((item, index) => {
    it(`第 ${index + 1} 条：${JSON.stringify(item.levels)} → items=${JSON.stringify(item.expect.items)} total=${JSON.stringify(item.expect.total)}`, () => {
      const result = scoreSummary(item.levels, fixture.dictScores);
      expect(result.items).toEqual(item.expect.items);
      expect(result.total).toEqual(item.expect.total);
    });
  });
});

describe('scoreSummary · 0 分档 / 没选 的补充判据', () => {
  it('0 分档是「选了 0 分」：items 里是 0、合计照算（不是 null）', () => {
    const { items, total } = scoreSummary({ preCulture: 'gt80', cultureDays: 'gt14', organoidCount: 'lt100', diameter: 'gt100' }, fixture.dictScores);
    expect(items[1]).toBe(0);
    expect(items[2]).toBe(0);
    expect(items).toEqual([20, 0, 0, 30]);
    expect(total).toBe(50);
  });

  it('没选全 → 合计为 null，已选各项分值照给（不把 null 当 0 加进去）', () => {
    const { items, total } = scoreSummary({ preCulture: 'gt80', cultureDays: 'gt14', organoidCount: null, diameter: 'gt100' }, fixture.dictScores);
    expect(items).toEqual([20, 0, null, 30]);
    expect(total).toBeNull();
  });

  it('空串 / 缺键 与 null 同义（都是「没选」）；全没选时四项都 null', () => {
    expect(scoreSummary({ preCulture: '', cultureDays: undefined }, fixture.dictScores)).toEqual({
      items: [null, null, null, null],
      total: null
    });
    expect(scoreSummary(null, null)).toEqual({ items: [null, null, null, null], total: null });
    expect(missingKeys({ preCulture: 'gt80', cultureDays: '' })).toEqual(['cultureDays', 'organoidCount', 'diameter']);
  });

  it('字典 remark 是字符串（sys_dict_data.remark 是 VARCHAR）也照算；字典没配这一档 → 该项与合计为 null', () => {
    const stringScores: ScoreDictScores = {
      preCulture: { gt80: '20' },
      cultureDays: { gt14: '0' }
    };
    expect(scoreSummary({ preCulture: 'gt80', cultureDays: 'gt14' }, stringScores)).toEqual({
      items: [20, 0, null, null],
      total: null
    });
    // 档位不在字典里（键缺失）→ null，不炸、不当 0
    expect(scoreSummary({ preCulture: 'gt999' }, fixture.dictScores).items[0]).toBeNull();
    // 字典 remark 不是数字（配置错）→ 前端只显示为空，权威判定在后端（后端会 500）
    expect(scoreSummary({ preCulture: 'gt80' }, { preCulture: { gt80: '不是数字' } }).items[0]).toBeNull();
  });

  it('列序恒为 SCORE_KEYS（preCulture / cultureDays / organoidCount / diameter），与入参键序无关', () => {
    expect(SCORE_KEYS).toEqual(['preCulture', 'cultureDays', 'organoidCount', 'diameter']);
    const shuffled = scoreSummary({ diameter: 'gt100', organoidCount: '1500to4000', cultureDays: 'le14', preCulture: 'gt80' }, fixture.dictScores);
    expect(shuffled.items).toEqual([20, 10, 25, 30]);
    expect(shuffled.total).toBe(85);
  });
});
