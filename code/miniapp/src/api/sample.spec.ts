// 来源单位 id 回找（V01）的用例：名字对得上才带 id，改了名字不带旧 id。
import { describe, expect, it } from 'vitest'
import { sourceUnitIdFor } from './sample'

const units = [
  { id: 9000009001, name: 'A 医院' },
  { id: 9000009002, name: 'B 大学' },
]

describe('sourceUnitIdFor', () => {
  it('外部新增：来源单位默认带档案单位名 → 带档案单位的 id', () => {
    expect(sourceUnitIdFor('A 医院', [{ id: 9000009001, name: 'A 医院' }])).toBe(9000009001)
  })

  it('名字前后有空白也算对得上', () => {
    expect(sourceUnitIdFor('  B 大学 ', units)).toBe(9000009002)
  })

  it('改成了列表外的名字 → 不带任何 id（只按名字落快照）', () => {
    expect(sourceUnitIdFor('C 研究所（自填）', units)).toBeNull()
  })

  it('空名字 → null；候选的 id 为空的跳过', () => {
    expect(sourceUnitIdFor('', units)).toBeNull()
    expect(sourceUnitIdFor('A 医院', [{ id: null, name: 'A 医院' }, { id: 9000009001, name: 'A 医院' }])).toBe(9000009001)
  })

  it('候选按顺序取第一个对得上的（详情里的 id 优先于单位列表）', () => {
    expect(sourceUnitIdFor('A 医院', [{ id: 42, name: 'A 医院' }, ...units])).toBe(42)
  })
})
