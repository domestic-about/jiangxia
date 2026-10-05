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
  cryoLedgerSub,
  cryoLedgerTone,
  cryoPayload,
  cryoPlaceText,
  cryoQtyText,
  cryoTabText,
  cryoViewOfTab,
  emptyCryoForm,
  toCryoFormValue,
} from '@/api/cryo'
import {
  canChangeFlow,
  editedText,
  flowBalanceText,
  flowDeltaText,
  flowFromText,
  flowKindText,
  flowOperatorText,
  isEdited,
  mpFlowKindOf,
} from '@/pages/cryo/flow'

/** 一个最小可用的冻存行（其余字段按需覆写） */
function row(patch: Partial<CryoBatchRow> = {}): CryoBatchRow {
  return { id: '9000003001', ...patch }
}

describe('cryoTabText：页签数字只认接口给的 tabCounts', () => {
  it('四个页签都带上整表口径的数字（2026-09-24 加「已取空」）', () => {
    expect(cryoTabText({ all: 7, overdue: 2, ln2: 2, emptied: 1 })).toEqual({
      '': '全部 7',
      overdue: '-80 超期 2',
      ln2: '液氮 2',
      emptied: '已取空 1',
    })
  })

  it('老后端没给 emptied：那一格写「—」，不拿 rows 去数', () => {
    expect(cryoTabText({ all: 7, overdue: 2, ln2: 2 }).emptied).toBe('已取空 —')
  })

  it('拿不到 tabCounts 时退回不带数字的短名（绝不拿 rows 冒充）', () => {
    expect(cryoTabText(null)).toEqual({ '': '全部', overdue: '-80 超期', ln2: '液氮', emptied: '已取空' })
    expect(cryoTabText(undefined).overdue).toBe('-80 超期')
  })
})

describe('cryoViewOfTab：表格页 ?tab= 直达冻存的某个页签', () => {
  it('认 overdue / ln2 / emptied 三个值', () => {
    expect(cryoViewOfTab('overdue')).toBe('overdue')
    expect(cryoViewOfTab('ln2')).toBe('ln2')
    expect(cryoViewOfTab('emptied')).toBe('emptied')
  })

  it('all / 缺省 / 不认识的值一律回到「全部」', () => {
    expect(cryoViewOfTab('all')).toBe('')
    expect(cryoViewOfTab(undefined)).toBe('')
    expect(cryoViewOfTab('')).toBe('')
    expect(cryoViewOfTab('OVERDUE')).toBe('')
    expect(cryoViewOfTab('pending')).toBe('')
  })
})

describe('已取空：冻结格小字、单元格、行底色', () => {
  it('取空的行把「已取空」放在冻结格小字最前（冻结格窄，放句尾会被挤掉）', () => {
    expect(cryoLedgerSub(row({ remainingQty: 0, initQty: 3, emptied: true }))).toBe('已取空 · 初始 3 支')
    expect(cryoLedgerSub(row({ remainingQty: 6, initQty: 8, emptied: false }))).toBe('剩 6 / 初始 8 支')
  })

  it('「当前剩余/支」那一格也写明已取空；没取空照旧只写数字', () => {
    expect(cryoLedgerCell(row({ remainingQty: 0, emptied: true }), 'remainingQty')).toBe('0 · 已取空')
    expect(cryoLedgerCell(row({ remainingQty: 4, emptied: false }), 'remainingQty')).toBe('4')
  })

  it('行底色：超期 > 已取空 > 无；只认后端的 overdue / emptied，不自己拿剩余判', () => {
    expect(cryoLedgerTone(row({ overdue: true }))).toBe('overdue')
    expect(cryoLedgerTone(row({ emptied: true, overdue: false }))).toBe('emptied')
    // 剩余 0 但后端没标 emptied（老后端）→ 不自己推
    expect(cryoLedgerTone(row({ remainingQty: 0 }))).toBe('')
  })
})

describe('cryoPlaceText：批次详情上方「放在哪」', () => {
  it('还在 -80：「-80℃ 暂存 · 冻存 N 天」（天数取后端的 frozenDays）', () => {
    expect(cryoPlaceText(row({ location: 'minus80', inMinus80: 'Y', frozenDays: 20 }))).toBe('-80℃ 暂存 · 冻存 20 天')
    expect(cryoPlaceText(row({ location: 'minus80', inMinus80: 'Y', frozenDays: 0 }))).toBe('-80℃ 暂存 · 冻存 0 天')
    expect(cryoPlaceText(row({ location: 'minus80' }))).toBe('-80℃ 暂存')
  })

  it('已转液氮：「液氮 · 位置 xxx · 转入 yyyy-mm-dd」', () => {
    expect(cryoPlaceText(row({ location: 'ln2', inMinus80: 'Y', ln2Location: '2号罐-3架-B5', toLn2Time: '2026-08-25' })))
      .toBe('液氮 · 位置 2号罐-3架-B5 · 转入 2026-08-25')
  })

  it('冻存当天直接进液氮：没有转入日期，写「直接进液氮」', () => {
    expect(cryoPlaceText(row({ location: 'ln2', inMinus80: 'N', ln2Location: '1号罐-1架-A2', toLn2Time: null })))
      .toBe('液氮 · 位置 1号罐-1架-A2 · 直接进液氮')
  })

  it('位置空着也不渲染成 undefined', () => {
    expect(cryoPlaceText(row({ location: 'ln2', inMinus80: 'Y', toLn2Time: '2026-09-01' }))).toBe('液氮 · 位置 — · 转入 2026-09-01')
  })
})

describe('cryoQtyText / 冻结格小字', () => {
  it('剩 N / 初始 M 支；超期再加「已超 N 天」（天数取后端的 overdueDays）', () => {
    expect(cryoQtyText(row({ remainingQty: 6, initQty: 8 }))).toBe('剩 6 / 初始 8 支')
    expect(cryoQtyText(row({ remainingQty: 6, initQty: 8, overdue: true, overdueDays: 6 }), true))
      .toBe('剩 6 / 初始 8 支 · 已超 6 天')
    // 阈值当天 =「今天到期」（UX 测试 MP-18：原来写「已超 0 天」）；`overdueDays: 0` 也是合法值，不许被当成「没有」（那会变成「已超期」）
    expect(cryoQtyText(row({ remainingQty: 2, initQty: 2, overdue: true, overdueDays: 0 }), true))
      .toBe('剩 2 / 初始 2 支 · 今天到期')
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

  it('已登记转液氮的批次不能把液氮位置清掉', () => {
    const moved = toCryoFormValue({ inMinus80: 'Y', toLn2Time: '2026-09-25', ln2Location: null })
    expect(moved.toLn2Time).toBe('2026-09-25')
    expect(moved.ln2Location).toBe('')
    expect(cryoFormProblem({ ...valid(), toLn2Time: '2026-09-25', ln2Location: '' })).toContain('液氮储存位置')
    expect(cryoFormProblem({ ...valid(), toLn2Time: '2026-09-25', ln2Location: '2号罐' })).toBe('')
  })

  it('转移至液氮时间不得早于冻存时间（同一天可以；与后端同一条）', () => {
    expect(cryoFormProblem({ ...valid(), toLn2Time: '2026-09-01', ln2Location: '2号罐' })).toContain('不能早于冻存时间 2026-09-02')
    expect(cryoFormProblem({ ...valid(), toLn2Time: '2026-09-02', ln2Location: '2号罐' })).toBe('')
  })

  it('★ 不管支数够不够（「会让某一步为负」只有后端能判，前端不写第二份）', () => {
    // 冻存数量改成 1（库里已有取走）在前端不算「填错」，交后端 400
    expect(cryoFormProblem({ ...valid(), initQty: '1' })).toBe('')
  })
})

describe('取用登记行的纯文案', () => {
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

  it('取走那一行写明从哪取（登记那一刻的位置）；别的类型不写', () => {
    expect(flowFromText({ ...take, fromLocation: 'ln2' })).toBe('液氮')
    expect(flowFromText({ ...take, fromLocation: 'minus80' })).toBe('-80℃')
    expect(flowFromText({ ...add, fromLocation: 'ln2' })).toBe('')
  })

  it('小程序里只有取走 / 补入两种能改删；第三种只在工作台改', () => {
    expect(canChangeFlow(take)).toBe(true)
    expect(canChangeFlow(add)).toBe(true)
    expect(canChangeFlow(adjust)).toBe(false)
    expect(mpFlowKindOf(take)).toBe('take')
    expect(mpFlowKindOf(adjust)).toBeNull()
  })

  it('改过的标「已改 · 某某」，没改过不渲染那一段', () => {
    expect(isEdited({ edited: true })).toBe(true)
    expect(editedText({ edited: true, updateByName: '李工' })).toBe('已改 · 李工')
    expect(editedText({ edited: false, updateByName: '李工' })).toBe('')
  })
})

describe('cryoPayload：转移至液氮时间随表单提交（飞书 2026-10-03 小程序行12）', () => {
  it('选了就带上 yyyy-MM-dd', () => {
    const f = { ...emptyCryoForm(), toLn2Time: '2026-10-03' }
    expect(cryoPayload(f).toLn2Time).toBe('2026-10-03')
  })

  it('没选 = null（还没转液氮），不发空串', () => {
    expect(cryoPayload(emptyCryoForm())).toHaveProperty('toLn2Time', null)
  })

  it('修改时原样带回已登记的时间 = 不变', () => {
    const f = toCryoFormValue({ toLn2Time: '2026-09-25 00:00:00', ln2Location: '2号罐' })
    expect(cryoPayload(f).toLn2Time).toBe('2026-09-25')
  })
})
