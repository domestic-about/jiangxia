// 表格页「切片染色」列的单元测试（SAMPLE-HINT-001 / UI:mp.ledger 最后一列）。
//
// ★ 被测的是 `sheets.ts` 的 `stainHintText` 与 `toTableRows` 的最后一格 —— 与工作台
//   `HintBadges.vue` 同一口径（同一份后端 `hint`）。逐条对着 seed 的五个样本：
//     1001 → {2, true,  ["HE","IHC"]}   → 「石蜡块 2 · 已切片 · HE / IHC」
//     1004 → {1, true,  []}             → 「石蜡块 1 · 已切片」（★ NONE 不算一种染色）
//     1006 → {1, true,  ["OTHER"]}      → 「石蜡块 1 · 已切片 · 其他」
//     1002 → {0, false, []}             → 「—」（名下只有待核验的送样）
//     1008 → {0, false, []}             → 「—」（名下唯一一块是软删）
import { describe, expect, it } from 'vitest'
import type { LedgerRow } from '@/api/ledger'
import { stainHintText, toTableRows } from './sheets'

const row = (hint: Record<string, unknown> | null): LedgerRow => ({
  id: '9000001001',
  sampleKind: 'tissue',
  internalNo: 'T-hli01',
  hint,
})

describe('stainHintText', () => {
  it('两块一块已切片 HE+IHC（seed 1001）', () => {
    expect(stainHintText(row({ blockCount: 2, sectioned: true, stains: ['HE', 'IHC'] })))
      .toBe('石蜡块 2 · 已切片 · HE / IHC')
  })

  it('一个已切片、无染色的块：NONE 不算一种染色（seed 1004）', () => {
    expect(stainHintText(row({ blockCount: 1, sectioned: true, stains: [] }))).toBe('石蜡块 1 · 已切片')
  })

  it('其他染色按缩写显示（seed 1006）', () => {
    expect(stainHintText(row({ blockCount: 1, sectioned: true, stains: ['OTHER'] })))
      .toBe('石蜡块 1 · 已切片 · 其他')
  })

  it('没有可计数的石蜡块一律「—」（seed 1002 待核验 / 1008 软删）', () => {
    expect(stainHintText(row({ blockCount: 0, sectioned: false, stains: [] }))).toBe('—')
    expect(stainHintText(row(null))).toBe('—')
    // 后端保证有 hint，但前端再兜一层：缺键也不许渲染成 undefined
    expect(stainHintText({ id: '1' })).toBe('—')
  })

  it('有块但还没切片：不写「已切片」', () => {
    expect(stainHintText(row({ blockCount: 1, sectioned: false, stains: [] }))).toBe('石蜡块 1')
    expect(stainHintText(row({ blockCount: 3, sectioned: false, stains: ['IF'] })))
      .toBe('石蜡块 3 · IF')
  })

  it('字典外的历史值按原值带出（不吞掉，便于发现问题）', () => {
    expect(stainHintText(row({ blockCount: 1, sectioned: true, stains: ['PAS'] })))
      .toBe('石蜡块 1 · 已切片 · PAS')
  })
})

describe('toTableRows 的最后一列', () => {
  it('两个工作表的最后一列都是「切片染色」的文案', () => {
    for (const kind of ['tissue', 'organoid'] as const) {
      const table = toTableRows([
        { id: '9000001001', sampleKind: kind, hint: { blockCount: 2, sectioned: true, stains: ['HE', 'IHC'] } },
      ])
      expect(table).toHaveLength(1)
      expect(table[0].cells[table[0].cells.length - 1]).toBe('石蜡块 2 · 已切片 · HE / IHC')
    }
  })

  it('不覆盖行上原有的列（cells 长度 = 该表的列数）', () => {
    const table = toTableRows([{ id: '9000001002', sampleKind: 'tissue', hint: { blockCount: 0, sectioned: false, stains: [] } }])
    // tissue = 冻结 1 列（内部编号）+ 其余 14 列（13 个模板列 + 追加的「切片染色」）
    expect(table[0].cells).toHaveLength(14)
    expect(table[0].cells[table[0].cells.length - 1]).toBe('—')
    expect(table[0].frozen).toBe('—')
  })
})
