// ============================================================================
// 染色切换逻辑的验收单测 · EMBED-WEB-001
//
// 用例**不在这个文件里**：直接读需求层的 `doc/verify/fixtures/stain-toggle-cases.json`
// （EMBED-MP-001 / EMBED-WEB-001 共用，9 条）。实现方自己编用例 = 自证，所以本文件只做
// 「读 fixture → 逐条跑 toggleStain → 断期望」。加一条 fixture 用例，这里自动多跑一条。
//
// 跑法（工作台根目录，即 code/plus-ui）：
//   npm_config_store_dir=<ws>/.pnpm-store pnpm vitest run src/views/lqg/embed/stain.fixture.spec.ts
// ============================================================================

import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { STAIN_NONE, STAIN_ORDER, hasOtherStain, sortStains, stainProblem, toggleStain } from './stain';

/** 需求层 fixture 的绝对路径（从本文件往上五级 = 工作区根） */
const FIXTURE = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../../../../../doc/verify/fixtures/stain-toggle-cases.json'
);

interface StainCase {
  current: string[];
  clicked: string;
  expect: string[];
}

const fixture = JSON.parse(readFileSync(FIXTURE, 'utf8')) as { cases: StainCase[] };
const cases = fixture.cases;

describe('toggleStain · 需求层 fixture（doc/verify/fixtures/stain-toggle-cases.json）', () => {
  it('fixture 至少 9 条（fixture 被删空也会红，不会静默 0 条全绿）', () => {
    expect(cases.length).toBeGreaterThanOrEqual(9);
  });

  cases.forEach((item, index) => {
    it(`第 ${index + 1} 条：${JSON.stringify(item.current)} 点 ${item.clicked} → ${JSON.stringify(item.expect)}`, () => {
      expect(toggleStain(item.current, item.clicked)).toEqual(item.expect);
    });
  });
});

describe('toggleStain · 互斥与顺序的补充判据', () => {
  it('「无染色」与其余四个永远不可能同时出现', () => {
    let picked: string[] = [];
    for (const clicked of [...STAIN_ORDER, ...STAIN_ORDER]) {
      picked = toggleStain(picked, clicked);
      if (picked.includes(STAIN_NONE)) {
        expect(picked).toEqual([STAIN_NONE]);
      }
    }
  });

  it('点「无染色」清掉其余四个（不是只清一个）', () => {
    expect(toggleStain(['HE', 'IF', 'IHC', 'OTHER'], STAIN_NONE)).toEqual([STAIN_NONE]);
  });

  it('点其余任何一个清掉「无染色」', () => {
    expect(toggleStain([STAIN_NONE], 'IHC')).toEqual(['IHC']);
  });

  it('输出永远是固定顺序，与点击顺序无关', () => {
    const picked = ['IHC', 'HE', 'IF'].reduce<string[]>((acc, value) => toggleStain(acc, value), []);
    expect(picked).toEqual(['HE', 'IF', 'IHC']);
    expect(sortStains(['NONE', 'HE'])).toEqual(['HE', 'NONE']);
  });

  it('字典外的值（PAS / 空串）被忽略且不动现有选择', () => {
    expect(toggleStain(['HE'], 'PAS')).toEqual(['HE']);
    expect(toggleStain(['HE'], '')).toEqual(['HE']);
    expect(toggleStain([], 'PAS')).toEqual([]);
  });

  it('current 为空 / 含脏值时不炸（历史数据里可能有字典外的值）', () => {
    expect(toggleStain(null, 'HE')).toEqual(['HE']);
    expect(toggleStain(undefined, 'HE')).toEqual(['HE']);
    expect(toggleStain(['PAS', 'HE'], 'IHC')).toEqual(['HE', 'IHC']);
  });
});

describe('stainProblem · 提交前的自检（后端才是权威，这里只是少跑一趟）', () => {
  it('选了「其他」没写名称 → stainOtherRequired', () => {
    expect(stainProblem(['OTHER'], '')).toBe('stainOtherRequired');
    expect(stainProblem(['OTHER'], null)).toBe('stainOtherRequired');
    expect(stainProblem(['OTHER'], 'Masson')).toBe('');
  });

  it('「无染色」与其余并存 → stainNoneExclusive（正常前端点不出来，防手改）', () => {
    expect(stainProblem(['NONE', 'HE'])).toBe('stainNoneExclusive');
    expect(stainProblem(['NONE'])).toBe('');
    expect(stainProblem([])).toBe('');
  });

  it('hasOtherStain 只看 OTHER', () => {
    expect(hasOtherStain(['HE', 'OTHER'])).toBe(true);
    expect(hasOtherStain(['HE'])).toBe(false);
    expect(hasOtherStain(null)).toBe(false);
  });
});
