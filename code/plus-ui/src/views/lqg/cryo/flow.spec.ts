import { describe, expect, it } from 'vitest';
import { editableQtyOf, failText, isFlowKind, purposeProblem, qtyProblem } from './flow';

// ============================================================================
// 流水弹窗的纯判据（CRYO-WEB-001）
//
// 为什么要有这组用例：这三条判据在 accept 里都是「假绿重灾区」——
//   * 取走的**上限 = 当前剩余**（超了不许提交；后端仍会硬拦）；
//   * 盘点调整的**支数不为 0、原因必填**（权威 FLOW:F-CRYO-02.step3）；
//   * 打开「修改」时 take / add 的支数输入框要填**绝对值**（库里存的是负 delta，
//     原样填 -2 会让「保存」发一个必然被拒的请求）。
// 与 EMBED-WEB-001 的 stain.fixture.spec.ts 同一个套路：判据只有一处，用例钉住它。
// ============================================================================

describe('isFlowKind', () => {
  it('只认 take / add / adjust', () => {
    expect(isFlowKind('take')).toBe(true);
    expect(isFlowKind('add')).toBe(true);
    expect(isFlowKind('adjust')).toBe(true);
    expect(isFlowKind('delete')).toBe(false);
    expect(isFlowKind(null)).toBe(false);
    expect(isFlowKind(undefined)).toBe(false);
  });
});

describe('editableQtyOf：修改弹窗里支数输入框的初值', () => {
  it('take 存的是负 delta，要填绝对值', () => {
    expect(editableQtyOf({ flowType: 'take', delta: -2 })).toBe(2);
  });

  it('add 存的是正 delta，原样', () => {
    expect(editableQtyOf({ flowType: 'add', delta: 3 })).toBe(3);
  });

  it('adjust 可正可负、原样（0 也如实带出来让人自己改）', () => {
    expect(editableQtyOf({ flowType: 'adjust', delta: -1 })).toBe(-1);
    expect(editableQtyOf({ flowType: 'adjust', delta: 5 })).toBe(5);
    expect(editableQtyOf({ flowType: 'adjust', delta: 0 })).toBe(0);
  });

  it('没有这一笔时是 0，不抛异常', () => {
    expect(editableQtyOf(null)).toBe(0);
    expect(editableQtyOf(undefined)).toBe(0);
  });
});

describe('qtyProblem：支数判据', () => {
  it('take / add 必须是正整数', () => {
    expect(qtyProblem('take', 1, 5)).toBeNull();
    expect(qtyProblem('take', 0, 5)).toBe('qtyTakeRequired');
    expect(qtyProblem('take', -1, 5)).toBe('qtyTakeRequired');
    expect(qtyProblem('take', null, 5)).toBe('qtyTakeRequired');
    expect(qtyProblem('add', 0, 5)).toBe('qtyAddRequired');
    expect(qtyProblem('add', 2, 5)).toBeNull();
  });

  it('★ 取走的上限 = 当前剩余', () => {
    expect(qtyProblem('take', 5, 5)).toBeNull();
    expect(qtyProblem('take', 6, 5)).toBe('qtyOverRemaining');
    // 剩余未知（修改历史账）时不做前端上限判断 —— 那一刻的余额只有后端逐笔算得出来
    expect(qtyProblem('take', 99, null)).toBeNull();
    expect(qtyProblem('take', 99, undefined)).toBeNull();
  });

  it('★ 盘点调整不为 0、可正可负', () => {
    expect(qtyProblem('adjust', 0, null)).toBe('qtyAdjustRequired');
    expect(qtyProblem('adjust', null, null)).toBe('qtyAdjustRequired');
    expect(qtyProblem('adjust', -3, null)).toBeNull();
    expect(qtyProblem('adjust', 4, null)).toBeNull();
  });

  it('小数一律不合法（后端要 Integer）', () => {
    expect(qtyProblem('take', 1.5, 5)).toBe('qtyTakeRequired');
    expect(qtyProblem('adjust', 0.5, null)).toBe('qtyAdjustRequired');
  });
});

describe('purposeProblem：调整必填原因', () => {
  it('只有 adjust 必填', () => {
    expect(purposeProblem('adjust', '')).toBe('purposeRequired');
    expect(purposeProblem('adjust', '   ')).toBe('purposeRequired');
    expect(purposeProblem('adjust', null)).toBe('purposeRequired');
    expect(purposeProblem('adjust', '实盘少 1 支')).toBeNull();
    expect(purposeProblem('take', null)).toBeNull();
    expect(purposeProblem('add', '')).toBeNull();
  });
});

describe('failText：被拒时的文案来源', () => {
  it('优先用后端原话；拿不到才退回通用话术', () => {
    expect(failText(new Error('已取走 2 支，冻存数量不能少于 2'), '兜底')).toBe('已取走 2 支，冻存数量不能少于 2');
    expect(failText(new Error('会让后面的取走变成负数'), '兜底')).toBe('会让后面的取走变成负数');
    // 400 那一档：拦截器已经弹过一次通知，reject 出来的是字面量 'error'
    expect(failText('error', '兜底')).toBe('兜底');
    expect(failText(undefined, '兜底')).toBe('兜底');
    expect(failText(new Error(''), '兜底')).toBe('兜底');
  });
});
