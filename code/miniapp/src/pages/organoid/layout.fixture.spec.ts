// 类器官收样填写页布局的 fixture 驱动用例（SAMPLE-MP-002 · accept 第 3 条）。
//
// 用例与期望**全部**从 `doc/verify/fixtures/organoid-form-cases.json` 读
// （`cases` / `externalFields` / `internalFields`），本文件不写任何期望值 ——
// fixture 谁改谁生效，spec 藏不住「偷偷放宽」的改动。
//
// 五组断言：
//   1. 结构守卫：9 例、外部 4 项、内部 8 项（各含 1 个插入字段「代数」）、`editable=false` 恰 5 例
//      （accept 里那段 node 断言原是 3 / 7，CR-20260924-10 之后同步改成 4 / 8）；
//   5. 插入字段守卫：fixture 的 `inserted`（甲方后加、模板里没有的字段）在内外部字段表里都紧跟它的 `after`，
//      去掉插入字段后内部仍是模板 B 的 7 列、外部仍是 CR-20260917-05 定的三项 —— 插入不许顺手改动别的字段；
//   2. 每个 case：`organoidLayout(identity, verifyStatus, mine, mode)` 的 fields / editable
//      与 fixture 的 expect 逐字相等；
//   3. 词表守卫：所有 case 渲染出来的字段都必须是 externalFields ∪ internalFields 里的；
//   4. 两条病灶守卫：**外部任何一例都不含收样段字段**（「不渲染」这条口径的机器证据）、
//      **`mode=view` 的每一例都不可改**（只读页不能直接可写，CR-20260918-07）。
import { describe, expect, it } from 'vitest'
import fixture from '@doc/verify/fixtures/organoid-form-cases.json'
import type { OrganoidFieldKey } from './layout'
import { RECEIVE_FIELDS, organoidLayout } from './layout'

interface OrganoidCase {
  case: string
  identity: unknown
  verifyStatus: string | null
  mine: boolean
  mode: unknown
  expect: {
    fields: string[]
    editable: boolean
  }
}

interface InsertedField {
  field: string
  label: string
  after: string
  source: string
}

const cases = fixture.cases as OrganoidCase[]
const externalFields = fixture.externalFields as string[]
const internalFields = fixture.internalFields as string[]
const inserted = fixture.inserted as InsertedField[]

describe('organoidLayout（fixture 驱动）', () => {
  it('fixture 结构：9 例 / 外部 4 项 / 内部 8 项 / 不可改恰 5 例', () => {
    expect(cases.length).toBe(9)
    expect(externalFields.length).toBe(4)
    expect(internalFields.length).toBe(8)
    expect(cases.filter(c => !c.expect.editable).length).toBe(5)
  })

  it('插入字段（代数）在内外部都紧跟 after；去掉插入字段后仍是原来的三项 / 模板 B 七列', () => {
    expect(inserted.map(i => [i.field, i.label, i.after])).toEqual([['passage', '代数', 'organoidType']])
    inserted.forEach((i) => {
      expect(i.source, `${i.field} 要写明来源`).toMatch(/2026-09-24/)
      for (const list of [externalFields, internalFields]) {
        expect(list.indexOf(i.field), i.field).toBe(list.indexOf(i.after) + 1)
      }
    })
    const insertedKeys = inserted.map(i => i.field)
    expect(externalFields.filter(k => !insertedKeys.includes(k))).toEqual(['sourceUnitName', 'organoidType', 'remark'])
    expect(internalFields.filter(k => !insertedKeys.includes(k))).toEqual([
      'sourceUnitName', 'organoidType', 'receiveDate', 'internalNo', 'processTime', 'hasViabilityReport', 'operatorName',
    ])
  })

  cases.forEach((c) => {
    it(`${c.case}：fields 与 editable 逐字等于 fixture`, () => {
      const layout = organoidLayout(c.identity, c.verifyStatus, c.mine, c.mode)
      expect(layout.fields).toEqual(c.expect.fields)
      expect(layout.editable).toBe(c.expect.editable)
    })
  })

  it('词表守卫：每一例渲染出来的字段都在 external ∪ internal 里', () => {
    const vocabulary = [...internalFields, ...externalFields]
    cases.forEach((c) => {
      organoidLayout(c.identity, c.verifyStatus, c.mine, c.mode).fields.forEach((key) => {
        expect(vocabulary, `${c.case} 的 ${key}`).toContain(key as OrganoidFieldKey)
      })
    })
  })

  it('外部任何一例都不含收样段字段（不是置灰、是根本不进 fields）', () => {
    cases.filter(c => c.identity === 'external').forEach((c) => {
      const fields = organoidLayout(c.identity, c.verifyStatus, c.mine, c.mode).fields
      RECEIVE_FIELDS.forEach((key) => {
        expect(fields, `${c.case} 不该有 ${key}`).not.toContain(key)
      })
    })
  })

  it('mode=view 的每一例都不可改（「修改」是另一个 mode，另算一次）', () => {
    cases.filter(c => c.mode === 'view').forEach((c) => {
      expect(organoidLayout(c.identity, c.verifyStatus, c.mine, c.mode).editable, c.case).toBe(false)
    })
  })
})
