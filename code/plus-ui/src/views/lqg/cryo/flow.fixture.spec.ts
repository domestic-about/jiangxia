import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { needsEmptyConfirm, qtyProblem, remainingAfter } from './flow';
import type { FlowKind } from './flow';

// ============================================================================
// 冻存取用登记提交前的两条判据 · 2026-09-24 甲方「支数取空的要提示」（G 批 B2）
//
// 用例**不在这个文件里**：读需求层的 `doc/verify/fixtures/cryo-take-cases.json`，与小程序
// `code/miniapp/src/pages/cryo/flow.fixture.spec.ts` 读的是同一份 —— 两端规则只能一起改。
//   * qtyProblem：新登记的取走不能超过当前剩余；改一笔时不在前端判超取（传 remaining = null）；
//   * remainingAfter / needsEmptyConfirm：取走让剩余从大于 0 变成恰好 0 → 提交前多问一句。
//
// 跑法（工作台根目录，即 code/plus-ui）：npx vitest run src/views/lqg/cryo/flow.fixture.spec.ts
// ============================================================================

/** 需求层 fixture 的绝对路径（从本文件往上六级 = 工作区根） */
const FIXTURE = resolve(dirname(fileURLToPath(import.meta.url)), '../../../../../../doc/verify/fixtures/cryo-take-cases.json');

interface TakeCase {
  name: string;
  kind: FlowKind;
  mode: 'new' | 'edit';
  qty: number;
  remaining: number;
  oldDelta: number;
  expect: { qtyProblem: null | 'invalid' | 'overRemaining'; remainingAfter: number | null; confirm: boolean };
}

const cases = (JSON.parse(readFileSync(FIXTURE, 'utf8')) as { cases: TakeCase[] }).cases;

/** 工作台的 qtyProblem 回的是 i18n key 后缀 → 归一成 fixture 的三个值 */
function normalize(key: string | null): null | 'invalid' | 'overRemaining' {
  if (key === null) return null;
  return key === 'qtyOverRemaining' ? 'overRemaining' : 'invalid';
}

describe('cryo-take-cases.json（与小程序同一份用例）', () => {
  it('用例不少于 10 条（防止有人删用例迁就实现）', () => {
    expect(cases.length).toBeGreaterThanOrEqual(10);
  });

  for (const c of cases) {
    it(c.name, () => {
      // 改一笔时工作台弹窗传 remaining = null（「那一刻的余额」只有后端算得出来）
      const remainingForCheck = c.mode === 'edit' ? null : c.remaining;
      expect(normalize(qtyProblem(c.kind, c.qty, remainingForCheck))).toBe(c.expect.qtyProblem);
      expect(remainingAfter(c.kind, c.qty, c.remaining, c.oldDelta)).toBe(c.expect.remainingAfter);
      expect(needsEmptyConfirm(c.kind, c.qty, c.remaining, c.oldDelta)).toBe(c.expect.confirm);
    });
  }

  it('盘点调整从不触发取空确认（它有自己的原因必填）', () => {
    expect(needsEmptyConfirm('adjust', -2, 2)).toBe(false);
    expect(remainingAfter('adjust', -2, 2)).toBe(0);
  });
});
