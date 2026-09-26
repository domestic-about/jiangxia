// 冻存取用登记提交前的两条判据（2026-09-24 甲方「支数取空的要提示」+「小程序和工作台界面都能操作」）。
//
// 用例**不在这个文件里**：读需求层的 `doc/verify/fixtures/cryo-take-cases.json`，与工作台
// `code/plus-ui/src/views/lqg/cryo/flow.fixture.spec.ts` 读的是同一份 —— 两端的规则只能一起改。
// 本文件只做「读 fixture → 逐条跑 → 断期望」，外加几条小程序独有的文案 / 按钮判据。
import { describe, expect, it } from 'vitest'
import fixture from '@doc/verify/fixtures/cryo-take-cases.json'
import {
  EMPTY_CONFIRM_TEXT,
  canRegisterLn2,
  flowQtyMessage,
  flowQtyProblem,
  formatDateTime,
  isMpFlowKind,
  ln2Problem,
  needsEmptyConfirm,
  parseQty,
  remainingAfter,
  removeConfirmText,
  takeFromText,
} from './flow'

interface TakeCase {
  name: string
  kind: 'take' | 'add'
  mode: 'new' | 'edit'
  qty: number
  remaining: number
  oldDelta: number
  expect: { qtyProblem: null | 'invalid' | 'overRemaining', remainingAfter: number | null, confirm: boolean }
}

const cases = (fixture as { cases: TakeCase[] }).cases

describe('cryo-take-cases.json（与工作台同一份用例）', () => {
  it('用例不少于 10 条（防止有人删用例迁就实现）', () => {
    expect(cases.length).toBeGreaterThanOrEqual(10)
  })

  for (const c of cases) {
    it(c.name, () => {
      const editing = c.mode === 'edit'
      expect(flowQtyProblem(c.kind, c.qty, c.remaining, editing) || null).toBe(c.expect.qtyProblem)
      expect(remainingAfter(c.kind, c.qty, c.remaining, c.oldDelta)).toBe(c.expect.remainingAfter)
      expect(needsEmptyConfirm(c.kind, c.qty, c.remaining, c.oldDelta)).toBe(c.expect.confirm)
    })
  }
})

describe('小程序独有的几条', () => {
  it('取空确认就是甲方要的那句话', () => {
    expect(EMPTY_CONFIRM_TEXT).toBe('登记后这一批就取空了（剩 0 支），确定吗？')
  })

  it('支数输入框的字符串：空串不是 0（空着提交要被挡）', () => {
    expect(Number.isNaN(parseQty(''))).toBe(true)
    expect(flowQtyProblem('take', parseQty(''), 3)).toBe('invalid')
    expect(parseQty(' 2 ')).toBe(2)
  })

  it('提示文案按类型说', () => {
    expect(flowQtyMessage('invalid', 'take', 3)).toBe('取走支数必须是正整数')
    expect(flowQtyMessage('invalid', 'add', 3)).toBe('补入支数必须是正整数')
    expect(flowQtyMessage('overRemaining', 'take', 3)).toBe('取走支数不能超过当前剩余 3 支')
    expect(flowQtyMessage('', 'take', 3)).toBe('')
  })

  it('小程序只登记取走 / 补入', () => {
    expect(isMpFlowKind('take')).toBe(true)
    expect(isMpFlowKind('add')).toBe(true)
    expect(isMpFlowKind('something-else')).toBe(false)
    expect(isMpFlowKind(undefined)).toBe(false)
  })

  it('取走从哪取按位置自动定（只读）', () => {
    expect(takeFromText('ln2')).toBe('从液氮取走')
    expect(takeFromText('minus80')).toBe('从 -80℃ 取走')
    expect(takeFromText(null)).toBe('从 -80℃ 取走')
  })

  it('「转液氮」只给还在 -80、没转过的批次', () => {
    expect(canRegisterLn2({ inMinus80: 'Y', toLn2Time: null })).toBe(true)
    expect(canRegisterLn2({ inMinus80: 'Y', toLn2Time: '2026-08-25' })).toBe(false)
    expect(canRegisterLn2({ inMinus80: 'N', toLn2Time: null })).toBe(false)
    expect(canRegisterLn2(null)).toBe(false)
  })

  it('转液氮表单：时间必填、不早于冻存时间，位置必填', () => {
    expect(ln2Problem('', '1号罐', '2026-09-04')).toContain('转移至液氮时间')
    expect(ln2Problem('2026-09-01', '1号罐', '2026-09-04')).toContain('不能早于冻存时间 2026-09-04')
    expect(ln2Problem('2026-09-04', ' ', '2026-09-04')).toContain('液氮储存位置')
    expect(ln2Problem('2026-09-24', '2号罐-1架-A1', '2026-09-04')).toBe('')
  })

  it('删之前的确认写明是哪一笔', () => {
    expect(removeConfirmText({ flowType: 'take', delta: -2 })).toBe('删掉这一笔「取走 -2」？删后剩余会重新计算。')
  })

  it('发生时间默认「现在」，格式 yyyy-MM-dd HH:mm:ss', () => {
    expect(formatDateTime(new Date(2026, 8, 24, 9, 5, 7))).toBe('2026-09-24 09:05:07')
  })
})
