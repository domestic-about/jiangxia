// 表格页列清单的 fixture 驱动用例（SAMPLE-MP-002 · accept 第 2 条）。
//
// 用例与期望**全部**从 `doc/verify/fixtures/ledger-columns-cases.json` 读，本文件不写任何
// 期望值 —— fixture 谁改谁生效，spec 藏不住「偷偷放宽」的改动。fixture 自己又由 accept
// 第 2 条拿甲方四份 xlsx 原件（`doc/verify/xlsx_header.py --print-header`）逐字 diff 一次，
// 所以链条是：**甲方原件 → fixture → spec → columns.ts**，四段里任何一段自己改自己都要红。
//
// 三组断言：
//   1. 逐表：`ledgerColumns(sheet).frozen.label` === fixture 的 `frozen`，
//      `columns.map(label)` 深相等 fixture 的 `expect`（= 模板去掉冻结列 + 追加列）；
//   2. 结构守卫：fixture 的 `expect` 必须等于 `template 去掉 frozen 再拼 extra`
//      （与 accept 那条 jq 同源；防止有人只改 expect 不改 template）；
//   3. 交叉守卫：冻结列不得在其余列里再出现一次（否则「内部编号」出现两遍）。
import { describe, expect, it } from 'vitest'
import fixture from '@doc/verify/fixtures/ledger-columns-cases.json'
import { SHEET_KEYS, ledgerColumns } from './columns'

interface SheetCase {
  templateFile: string
  template: string[]
  frozen: string
  extra: string[]
  expect: string[]
}

const sheets = fixture.sheets as Record<string, SheetCase>

describe('ledgerColumns（fixture 驱动）', () => {
  it('fixture 覆盖四张工作表，且每张都有原件与期望', () => {
    expect(Object.keys(sheets).sort()).toEqual([...SHEET_KEYS].sort())
    Object.values(sheets).forEach((s) => {
      expect(s.templateFile).toMatch(/^_input\/templates\/.+\.xlsx$/)
      expect(s.template.length).toBeGreaterThan(0)
      expect(s.expect.length).toBeGreaterThan(0)
    })
  })

  it('每张表的 expect 都等于「模板去掉冻结列 + 追加列」（与 accept 的 jq 同源）', () => {
    Object.entries(sheets).forEach(([key, s]) => {
      expect([...s.template.filter(label => label !== s.frozen), ...s.extra], key).toEqual(s.expect)
    })
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
