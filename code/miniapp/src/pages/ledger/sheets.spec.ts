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
import { ENTRY_KEYS, ENTRY_SHORT, ENTRY_TITLE } from '@/pages/index/entries'
import { VERIFY_TABS, VERIFY_TAB_TITLE } from '@/pages/verify/tabs'
import { sheetOf, sheetsFor, stainHintText, toTableRows } from './sheets'

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
    // tissue = 冻结 1 列（内部编号）+ 其余 15 列（13 个模板列 + 插入的「种属」+ 追加的「切片染色」）
    expect(table[0].cells).toHaveLength(15)
    expect(table[0].cells[table[0].cells.length - 1]).toBe('—')
    expect(table[0].frozen).toBe('—')
  })
})

// 点一行去哪（甲方 2026-09-24 第 20 行：小程序里也能核验）：
// 合作单位送来、待核验的那一条 → 核验页；其余照旧进只读页（mode=view），冻存不受影响。
describe('点一行去哪', () => {
  it('样本两张表：待核验进样本核验页，有效 / 无效进只读页', () => {
    expect(sheetOf('tissue').target({ id: '9000001002', verifyStatus: 'pending', submitSource: 'external' }))
      .toBe('/pages/verify/sample?id=9000001002')
    expect(sheetOf('organoid').target({ id: '9000001011', verifyStatus: 'pending' }))
      .toBe('/pages/verify/sample?id=9000001011')
    expect(sheetOf('tissue').target({ id: '9000001001', verifyStatus: 'valid' }))
      .toBe('/pages/sample/form?id=9000001001&mode=view')
    expect(sheetOf('tissue').target({ id: '9000001003', verifyStatus: 'invalid' }))
      .toBe('/pages/sample/form?id=9000001003&mode=view')
    expect(sheetOf('organoid').target({ id: '9000001009', verifyStatus: 'valid' }))
      .toBe('/pages/organoid/form?id=9000001009&mode=view')
  })

  it('石蜡包埋：待核验的送样进核验页，其余进只读页', () => {
    expect(sheetOf('embed').target({ id: '9000002006', verifyStatus: 'pending' }))
      .toBe('/pages/verify/embed?id=9000002006')
    expect(sheetOf('embed').target({ id: '9000002001', verifyStatus: 'valid' }))
      .toBe('/pages/embed/form?id=9000002001&mode=view')
  })

  it('冻存不受影响：仍是只读（页面在这一档改开批次详情弹层）', () => {
    expect(sheetOf('cryo').target({ id: '9000003001', verifyStatus: 'pending' }))
      .toBe('/pages/cryo/form?id=9000003001&mode=view')
  })
})

// 页签短名只有一份（Kevin 2026-09-24 本机验收「顶部 tab 文字不要换行，可以适当删减一些文字」）：
// 表格页切换条、历史编辑记录、待核验三处都从 `entries.ts#ENTRY_SHORT` 取，这里钉住「同一份」与「够短」。
describe('页签短名', () => {
  /** 工作表 / 核验页签的 key → 入口 key（样本那张表工作表里叫 tissue，入口里叫 sample） */
  const entryKeyOf = (key: string) => (key === 'tissue' ? 'sample' : key) as keyof typeof ENTRY_SHORT

  it('四个短名：样本记录 / 类器官送样 / 石蜡包埋 / -80 冻存（顺序 = 模板顺序）', () => {
    expect(ENTRY_KEYS.map(key => ENTRY_SHORT[key])).toEqual(['样本记录', '类器官送样', '石蜡包埋', '-80 冻存'])
  })

  it('表格页切换条的短名与全称都取自 entries.ts（不另写一份）', () => {
    for (const sheet of sheetsFor()) {
      expect(sheet.short).toBe(ENTRY_SHORT[entryKeyOf(sheet.key)])
      expect(sheet.title).toBe(ENTRY_TITLE[entryKeyOf(sheet.key)])
    }
  })

  it('待核验三个页签与表格页同一组叫法', () => {
    for (const tab of VERIFY_TABS) {
      expect(VERIFY_TAB_TITLE[tab]).toBe(ENTRY_SHORT[entryKeyOf(tab)])
      expect(VERIFY_TAB_TITLE[tab]).toBe(sheetOf(tab).short)
    }
  })

  it('每个短名不超过 5 个字：390 宽下四个页签并排、13px 一行放得下（全称最长 8 个字，必折行）', () => {
    for (const key of ENTRY_KEYS) {
      expect(ENTRY_SHORT[key].replace(/\s/g, '').length).toBeLessThanOrEqual(5)
      expect(ENTRY_SHORT[key].length).toBeLessThan(ENTRY_TITLE[key].length)
    }
  })
})
