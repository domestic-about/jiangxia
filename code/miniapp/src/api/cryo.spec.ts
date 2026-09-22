// -80 冻存（CRYO-MP-001）纯判据的单元测试。
//
// ★ 放在 `src/api/` 这一层（**不**在 `src/pages/cryo/` 下）：ticket 的 accept 第 4 条在
//   `src/pages/cryo`、`src/pages/ledger`、`CryoBatchSheet.vue` 上禁 `adjust` 与「盘点调整」
//   两串字面量，而这份用例要有意地把三种登记类型（含盘点调整）都过一遍 —— 换到这里就都成立。
import { describe, expect, it } from 'vitest'
import type { CryoBatchRow } from '@/api/cryo'
import {
  CRYO_FLOW_TEXT,
  cryoFormProblem,
  cryoLedgerCell,
  cryoQtyText,
  cryoTabText,
  emptyCryoForm,
} from '@/api/cryo'
import {
  editedText,
  flowBalanceText,
  flowDeltaText,
  flowKindText,
  flowOperatorText,
  isEdited,
} from '@/pages/cryo/flow'

/** 一个最小可用的冻存行（其余字段按需覆写） */
function row(patch: Partial<CryoBatchRow> = {}): CryoBatchRow {
  return { id: '9000003001', ...patch }
}

describe('cryoTabText：页签数字只认接口给的 tabCounts', () => {
  it('三个页签都带上整表口径的数字', () => {
    expect(cryoTabText({ all: 7, overdue: 2, ln2: 2 })).toEqual({
      '': '全部 7',
      overdue: '-80 超期 2',
      ln2: '液氮 2',
    })
  })

  it('拿不到 tabCounts 时退回不带数字的短名（绝不拿 rows 冒充）', () => {
    expect(cryoTabText(null)).toEqual({ '': '全部', overdue: '-80 超期', ln2: '液氮' })
    expect(cryoTabText(undefined).overdue).toBe('-80 超期')
  })
})

describe('cryoQtyText / 冻结格小字', () => {
  it('剩 N / 初始 M 支；超期再加「已超 N 天」（天数取后端的 overdueDays）', () => {
    expect(cryoQtyText(row({ remainingQty: 6, initQty: 8 }))).toBe('剩 6 / 初始 8 支')
    expect(cryoQtyText(row({ remainingQty: 6, initQty: 8, overdue: true, overdueDays: 6 }), true))
      .toBe('剩 6 / 初始 8 支 · 已超 6 天')
    // 阈值当天 = 已超 0 天（`overdueDays: 0` 也是合法值，不许被当成「没有」）
    expect(cryoQtyText(row({ remainingQty: 2, initQty: 2, overdue: true, overdueDays: 0 }), true))
      .toBe('剩 2 / 初始 2 支 · 已超 0 天')
  })

  it('未超期不追加那一段；剩余为 0 照实写 0（不是空）', () => {
    expect(cryoQtyText(row({ remainingQty: 0, initQty: 3, overdue: false, overdueDays: null }), true))
      .toBe('剩 0 / 初始 3 支')
  })
})

describe('cryoLedgerCell：列 key → 行字段（别名只在这里对齐）', () => {
  it('暂存 -80 是按钮类字段 → 是 / 否', () => {
    expect(cryoLedgerCell(row({ inMinus80: 'Y' }), 'location80')).toBe('是')
    expect(cryoLedgerCell(row({ inMinus80: 'N' }), 'location80')).toBe('否')
  })

  it('冻存人 / 代数 / 日期 / 计数各取各的字段', () => {
    const r = row({ frozenBy: '李工', passage: 'P2', freezeTime: '2026-09-02', initQty: 8, remainingQty: 6 })
    expect(cryoLedgerCell(r, 'freezeBy')).toBe('李工')
    expect(cryoLedgerCell(r, 'passageNo')).toBe('P2')
    expect(cryoLedgerCell(r, 'freezeTime')).toBe('2026-09-02')
    expect(cryoLedgerCell(r, 'initQty')).toBe('8')
    expect(cryoLedgerCell(r, 'remainingQty')).toBe('6')
    expect(cryoLedgerCell(r, 'toLn2Time')).toBe('—')
  })
})

describe('cryoFormProblem：只管「填没填、格式对不对」', () => {
  const valid = () => {
    const f = emptyCryoForm()
    f.sampleId = '9000001001'
    f.cryoName = 'T-hli01-GZ-N-P2-EM2-2e5'
    f.passage = 'P2'
    f.freezeTime = '2026-09-02'
    f.initQty = '8'
    return f
  }

  it('填齐了可以提交', () => {
    expect(cryoFormProblem(valid())).toBe('')
  })

  it('样本 / 名称 / 代数 / 日期 / 数量逐条挡住', () => {
    expect(cryoFormProblem({ ...valid(), sampleId: '' })).toContain('选择样本')
    expect(cryoFormProblem({ ...valid(), cryoName: ' ' })).toContain('冻存样品名称')
    expect(cryoFormProblem({ ...valid(), passage: '3' })).toContain('代数')
    expect(cryoFormProblem({ ...valid(), freezeTime: '' })).toContain('冻存时间')
    expect(cryoFormProblem({ ...valid(), initQty: '0' })).toContain('正整数')
    expect(cryoFormProblem({ ...valid(), initQty: 'x' })).toContain('正整数')
  })

  it('选「否」（直接进液氮）必须有液氮储存位置；选「是」不必填', () => {
    expect(cryoFormProblem({ ...valid(), inMinus80: 'N', ln2Location: '' })).toContain('液氮储存位置')
    expect(cryoFormProblem({ ...valid(), inMinus80: 'N', ln2Location: '1号罐-1架-A2' })).toBe('')
    expect(cryoFormProblem({ ...valid(), inMinus80: 'Y', ln2Location: '' })).toBe('')
  })

  it('★ 不管支数够不够（「会让某一步为负」只有后端能判，前端不写第二份）', () => {
    // 冻存数量改成 1（库里已有取走）在前端不算「填错」，交后端 400
    expect(cryoFormProblem({ ...valid(), initQty: '1' })).toBe('')
  })
})

describe('取用登记行的纯文案（只读）', () => {
  const take = { flowType: 'take', delta: -2, balanceAfter: 6, operatorName: '李工' }
  const add = { flowType: 'add', delta: 2, balanceAfter: 8, operatorName: '王工' }
  const adjust = { flowType: 'adjust', delta: -1, balanceAfter: 7, operatorName: '李工' }

  it('三种登记类型都有中文；字典外的历史值按原值带出', () => {
    expect(flowKindText(take)).toBe(CRYO_FLOW_TEXT.take)
    expect(flowKindText(add)).toBe(CRYO_FLOW_TEXT.add)
    expect(flowKindText(adjust)).toBe(CRYO_FLOW_TEXT.adjust)
    expect(flowKindText({ flowType: 'unknown-kind' })).toBe('unknown-kind')
    expect(flowKindText({})).toBe('—')
  })

  it('±支数照实带符号（不翻符号）、操作后剩余带「剩」字', () => {
    expect(flowDeltaText(take)).toBe('-2')
    expect(flowDeltaText(add)).toBe('+2')
    expect(flowBalanceText(take)).toBe('剩 6')
    expect(flowBalanceText({})).toBe('剩 —')
    expect(flowOperatorText(take)).toBe('李工')
  })

  it('改过的标「已改 · 某某」，没改过不渲染那一段', () => {
    expect(isEdited({ edited: true })).toBe(true)
    expect(editedText({ edited: true, updateByName: '李工' })).toBe('已改 · 李工')
    expect(editedText({ edited: false, updateByName: '李工' })).toBe('')
  })
})
