// 文档分组规则的 fixture 驱动用例（DOC-MP-001 · accept 第 1 条）。
//
// 用例与期望**全部**从 `doc/verify/fixtures/doc-group-cases.json` 读，
// 本文件不写任何期望值 —— fixture 谁改谁生效，spec 藏不住「偷偷放宽」的改动。
//
// ★ fixture 检查的是**分组规则**的四个可观测值（`shape()` 里那一份）：
//   组内序列 docKinds / 合并入口 showMerge / 最新完成时间 latest / 组标识 sampleId。
//   `groupDocs` 另外还带 `title / subtitle / docs` 供页面渲染（`shape()` 不比较它们）。
import { describe, expect, it } from 'vitest'
import fixture from '@doc/verify/fixtures/doc-group-cases.json'
import type { DocListRow } from '@/api/doc'
import type { DocGroup } from './group'
import { groupDocs } from './group'

interface GroupCase {
  case: string
  rows: DocListRow[]
  expect: unknown[]
}

const cases = fixture.cases as GroupCase[]

/** fixture 的形状 = 分组规则的四个可观测值（不是 groupDocs 的全部字段） */
function shape(groups: DocGroup[]) {
  return groups.map(g => ({
    sampleId: g.sampleId,
    docKinds: g.docKinds,
    showMerge: g.showMerge,
    latest: g.latest,
  }))
}

describe('groupDocs(rows)（fixture 驱动）', () => {
  it('fixture 里有用例（不是空文件）', () => {
    expect(cases.length).toBeGreaterThan(0)
  })

  it('空输入 → 空分组', () => {
    expect(groupDocs([])).toEqual([])
  })

  it('病灶：fixture 里确实有一例混进了 merged 行', () => {
    expect(cases.some(c => c.rows.some(r => r.docKind === 'merged'))).toBe(true)
  })

  cases.forEach((c) => {
    it(c.case, () => {
      expect(shape(groupDocs(c.rows))).toEqual(c.expect)
    })
  })
})
