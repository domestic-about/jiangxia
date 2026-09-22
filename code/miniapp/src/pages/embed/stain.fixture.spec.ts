// 染色切换逻辑的验收单测 · EMBED-MP-001（accept 第 1 条那一段 vitest 跑的就是本文件）。
//
// 用例**不在这个文件里**：直接读需求层的 `doc/verify/fixtures/stain-toggle-cases.json`
// （EMBED-WEB-001 / EMBED-MP-001 共用，9 条）。实现方自己编用例 = 自证，所以本文件只做
// 「读 fixture → 逐条跑 toggleStain → 断期望」。加一条 fixture 用例，这里自动多跑一条。
//
// ★ 与工作台 `code/plus-ui/src/views/lqg/embed/stain.ts` 的对偶关系：两份实现跑**同一份**
//   fixture —— 「小程序与工作台共用同一份染色口径」这句话因此有机器证据（ticket §1）。
import { describe, expect, it } from 'vitest'
import fixture from '@doc/verify/fixtures/stain-toggle-cases.json'
import { STAIN_NONE, STAIN_ORDER, hasOtherStain, sortStains, stainProblem, toggleStain } from './stain'

interface StainCase {
  current: string[]
  clicked: string
  expect: string[]
}

const cases = fixture.cases as StainCase[]

describe('toggleStain · 需求层 fixture（doc/verify/fixtures/stain-toggle-cases.json）', () => {
  it('fixture 至少 9 条（fixture 被删空也会红，不会静默 0 条全绿）', () => {
    expect(cases.length).toBeGreaterThanOrEqual(9)
  })

  cases.forEach((item, index) => {
    it(`第 ${index + 1} 条：${JSON.stringify(item.current)} 点 ${item.clicked} → ${JSON.stringify(item.expect)}`, () => {
      expect(toggleStain(item.current, item.clicked)).toEqual(item.expect)
    })
  })
})

describe('toggleStain · 互斥与顺序的补充判据', () => {
  it('「无染色」与其余四个永远不可能同时出现', () => {
    let picked: string[] = []
    for (const clicked of [...STAIN_ORDER, ...STAIN_ORDER]) {
      picked = toggleStain(picked, clicked)
      if (picked.includes(STAIN_NONE)) {
        expect(picked).toEqual([STAIN_NONE])
      }
    }
  })

  it('点「无染色」清掉其余四个（不是只清一个）', () => {
    expect(toggleStain(['HE', 'IF', 'IHC', 'OTHER'], STAIN_NONE)).toEqual([STAIN_NONE])
  })

  it('点其余任何一个清掉「无染色」', () => {
    expect(toggleStain([STAIN_NONE], 'IHC')).toEqual(['IHC'])
  })

  it('输出永远是固定顺序，与点击顺序无关', () => {
    const picked = ['IHC', 'HE', 'IF'].reduce<string[]>((acc, value) => toggleStain(acc, value), [])
    expect(picked).toEqual(['HE', 'IF', 'IHC'])
    expect(sortStains(['NONE', 'HE'])).toEqual(['HE', 'NONE'])
  })

  it('字典外的值（PAS / 空串）被忽略且不动现有选择', () => {
    expect(toggleStain(['HE'], 'PAS')).toEqual(['HE'])
    expect(toggleStain(['HE'], '')).toEqual(['HE'])
    expect(toggleStain([], 'PAS')).toEqual([])
  })

  it('current 为空 / 含脏值时不炸（历史数据里可能有字典外的值）', () => {
    expect(toggleStain(null, 'HE')).toEqual(['HE'])
    expect(toggleStain(undefined, 'HE')).toEqual(['HE'])
    expect(toggleStain(['PAS', 'HE'], 'IHC')).toEqual(['HE', 'IHC'])
  })
})

describe('stainProblem · 提交前的自检（后端才是权威，这里只是少跑一趟）', () => {
  it('选了「其他」没写名称 → 报错（后端 StainRules 同口径）', () => {
    expect(stainProblem(['OTHER'], '')).not.toBe('')
    expect(stainProblem(['OTHER'], null)).not.toBe('')
    expect(stainProblem(['OTHER'], 'Masson')).toBe('')
  })

  it('「无染色」与其余并存 → 报错（正常前端点不出来，防手改）', () => {
    expect(stainProblem(['NONE', 'HE'])).not.toBe('')
    expect(stainProblem(['NONE'])).toBe('')
    expect(stainProblem([])).toBe('')
  })

  it('hasOtherStain 只看 OTHER', () => {
    expect(hasOtherStain(['HE', 'OTHER'])).toBe(true)
    expect(hasOtherStain(['HE'])).toBe(false)
    expect(hasOtherStain(null)).toBe(false)
  })
})
