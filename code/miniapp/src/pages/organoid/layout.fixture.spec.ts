// 类器官收样填写页布局的 fixture 驱动用例（SAMPLE-MP-002 · accept 第 3 条）。
//
// 用例与期望**全部**从 `doc/verify/fixtures/organoid-form-cases.json` 读
// （`cases` / `externalFields` / `internalFields`），本文件不写任何期望值 ——
// fixture 谁改谁生效，spec 藏不住「偷偷放宽」的改动。
//
// 四组断言：
//   1. 结构守卫：9 例、外部 3 项、内部 7 项、`editable=false` 恰 5 例
//      （与 accept 里那段 node 断言同源）；
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

const cases = fixture.cases as OrganoidCase[]
const externalFields = fixture.externalFields as string[]
const internalFields = fixture.internalFields as string[]

describe('organoidLayout（fixture 驱动）', () => {
  it('fixture 结构：9 例 / 外部 3 项 / 内部 7 项 / 不可改恰 5 例', () => {
    expect(cases.length).toBe(9)
    expect(externalFields.length).toBe(3)
    expect(internalFields.length).toBe(7)
    expect(cases.filter(c => !c.expect.editable).length).toBe(5)
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
