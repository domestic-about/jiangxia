// 表格页列清单的 fixture 驱动用例（SAMPLE-MP-002 · accept 第 2 条）。
//
// 用例与期望**全部**从 `doc/verify/fixtures/ledger-columns-cases.json` 读，本文件不写任何
// 期望值 —— fixture 谁改谁生效，spec 藏不住「偷偷放宽」的改动。fixture 自己又由 accept
// 第 2 条拿甲方四份 xlsx 原件（`doc/verify/xlsx_header.py --print-header`）逐字 diff 一次，
// 所以链条是：**甲方原件 → fixture → spec → columns.ts**，四段里任何一段自己改自己都要红。
//
// 三组断言：
//   1. 逐表：`ledgerColumns(sheet).frozen.label` === fixture 的 `frozen`，
//      `columns.map(label)` 深相等 fixture 的 `expect`（= 模板去掉冻结列 + 插入列 + 追加列）；
//   2. 结构守卫：fixture 的 `expect` 必须等于 `template 去掉 frozen、插入 inserted、再拼 extra`
//      （与 accept 那条 jq 同源；防止有人只改 expect 不改 template）；插入列必须写明来源、
//      锚点列必须是模板列、插入列不许与模板列重名（「代数」只能以插入列的身份出现，不许混进 template）；
//   3. 交叉守卫：冻结列不得在其余列里再出现一次（否则「内部编号」出现两遍）。
import { describe, expect, it } from 'vitest'
import fixture from '@doc/verify/fixtures/ledger-columns-cases.json'
import { SHEET_KEYS, ledgerColumns } from './columns'

/** 甲方后来要求加、模板原件里没有的列：插在 `after` 列后面（CR-20260924-10 起才有） */
interface InsertedColumn {
  label: string
  after: string
  source: string
}

interface SheetCase {
  templateFile: string
  template: string[]
  frozen: string
  /** 没有这个键 = 没有插入列 */
  inserted?: InsertedColumn[]
  extra: string[]
  expect: string[]
}

const sheets = fixture.sheets as Record<string, SheetCase>

/** 模板去掉冻结列 → 插入 inserted（依次插到各自 after 后面）→ 追加 extra */
function composeColumns(s: SheetCase): string[] {
  const out = s.template.filter(label => label !== s.frozen)
  for (const col of s.inserted ?? []) {
    out.splice(out.indexOf(col.after) + 1, 0, col.label)
  }
  return [...out, ...s.extra]
}

describe('ledgerColumns（fixture 驱动）', () => {
  it('fixture 覆盖四张工作表，且每张都有原件与期望', () => {
    expect(Object.keys(sheets).sort()).toEqual([...SHEET_KEYS].sort())
    Object.values(sheets).forEach((s) => {
      expect(s.templateFile).toMatch(/^_input\/templates\/.+\.xlsx$/)
      expect(s.template.length).toBeGreaterThan(0)
      expect(s.expect.length).toBeGreaterThan(0)
    })
  })

  it('每张表的 expect 都等于「模板去掉冻结列 + 插入列 + 追加列」（与 accept 的 jq 同源）', () => {
    Object.entries(sheets).forEach(([key, s]) => {
      expect(composeColumns(s), key).toEqual(s.expect)
    })
  })

  it('插入列：写明来源、锚点是模板列（且不是冻结列）、不与模板列重名', () => {
    Object.entries(sheets).forEach(([key, s]) => {
      for (const col of s.inserted ?? []) {
        expect(col.source, `${key}.${col.label} 缺来源`).toBeTruthy()
        expect(s.template, `${key}.${col.label} 的锚点`).toContain(col.after)
        expect(col.after, `${key}.${col.label} 不能插在冻结列后面`).not.toBe(s.frozen)
        expect(s.template, `${key}.${col.label} 与模板列重名`).not.toContain(col.label)
      }
    })
    // 插入列：类器官收样记录「类器官类型」后的「代数」（甲方 2026-09-24 第 18 行）；
    // 三张表的「种属」（甲方 2026-10-09，CR-20261009-18；冻存那张是追加列）
    expect(sheets.organoid.inserted?.map(c => [c.label, c.after])).toEqual([['种属', '来源单位'], ['代数', '类器官类型']])
    expect(sheets.tissue.inserted?.map(c => [c.label, c.after])).toEqual([['种属', '来源单位']])
    expect(sheets.embed.inserted?.map(c => [c.label, c.after])).toEqual([['种属', '样本编号']])
    expect(sheets.cryo.extra).toContain('种属')
  })

  SHEET_KEYS.forEach((key) => {
    it(`${key}：冻结列标签与其余列标签逐字等于 fixture`, () => {
      const expected = sheets[key]
      const actual = ledgerColumns(key)
      expect(actual).not.toBeNull()
      expect(actual!.frozen.label).toBe(expected.frozen)
      expect(actual!.columns.map(c => c.label)).toEqual(expected.expect)
      // 冻结列不许在其余列里再出现一次（列名唯一性）
      expect(actual!.columns.map(c => c.label)).not.toContain(expected.frozen)
    })
  })

  it('不认识的 sheet → null（页面回落到第一个已注册的工作表）', () => {
    expect(ledgerColumns('nope')).toBeNull()
    expect(ledgerColumns(undefined)).toBeNull()
  })
})
