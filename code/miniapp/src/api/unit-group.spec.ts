// 来源单位选择面板的候选（V01，与后端口径一致）：内部全部启用单位；外部只有本人绑定的那一个。
import { describe, expect, it } from 'vitest'
import type { MpExtProfile } from '@/types/mp'
import { boundUnitOf, unitOptionsFor } from './unit-group'

const units = [
  { unitId: 9000009001, unitName: 'A 医院', groups: [] },
  { unitId: 9000009002, unitName: 'B 大学', groups: [] },
]

function ext(over: Partial<MpExtProfile>): MpExtProfile {
  return { unitId: null, unitName: null, groupId: null, groupName: null, unitNameInput: null, groupNameInput: null, bindStatus: 'unbound', rejectReason: null, ...over }
}

describe('boundUnitOf', () => {
  it('已核验 / 待核验且有单位 id → 绑定的单位', () => {
    expect(boundUnitOf(ext({ unitId: 9000009001, unitName: 'A 医院', bindStatus: 'verified' }))?.unitId).toBe(9000009001)
    expect(boundUnitOf(ext({ unitId: 9000009001, unitName: 'A 医院', bindStatus: 'pending' }))?.unitName).toBe('A 医院')
  })
  it('自填单位名、未绑定、被驳回 → 没有', () => {
    expect(boundUnitOf(ext({ unitNameInput: 'C 研究所', bindStatus: 'pending' }))).toBeNull()
    expect(boundUnitOf(ext({ bindStatus: 'unbound' }))).toBeNull()
    expect(boundUnitOf(ext({ unitId: 9000009001, unitName: 'A 医院', bindStatus: 'rejected' }))).toBeNull()
    expect(boundUnitOf(null)).toBeNull()
  })
})

describe('unitOptionsFor', () => {
  const extA = ext({ unitId: 9000009001, unitName: 'A 医院', bindStatus: 'verified' })
  it('内部：全部启用单位', () => {
    expect(unitOptionsFor('internal', units, null).map(u => u.unitName)).toEqual(['A 医院', 'B 大学'])
  })
  it('外部：只有本人绑定的单位（列全部会让人选到别的单位被后端 400）', () => {
    expect(unitOptionsFor('external', units, extA).map(u => u.unitName)).toEqual(['A 医院'])
    expect(unitOptionsFor('external', units, ext({ unitNameInput: 'C 研究所', bindStatus: 'pending' }))).toEqual([])
  })
  it('身份不明：一个都不列', () => {
    expect(unitOptionsFor(undefined, units, extA)).toEqual([])
  })
})
