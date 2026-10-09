// 种属纯函数（CR-20261009-18）
import { describe, expect, it, vi } from 'vitest'
import { DEFAULT_SPECIES, isManualSpecies, normalizeSpecies, speciesChoices, speciesProblem } from './species'

vi.mock('@/utils/request', () => ({ http: { get: vi.fn() } }))

describe('speciesChoices', () => {
  it('字典值保序、去空、去重', () => {
    expect(speciesChoices(['人', ' 鼠兔 ', '', '人', '移植猪', '鸡'])).toEqual(['人', '鼠兔', '移植猪', '鸡'])
  })

  it('字典是空的 / 拉不到：用兜底四个，不挡填写', () => {
    expect(speciesChoices([])).toEqual([...DEFAULT_SPECIES])
    expect(speciesChoices(null)).toEqual(['人', '鼠兔', '移植猪', '鸡'])
  })

  it('管理员在字典里加了新的，格子里就有', () => {
    expect(speciesChoices(['人', '鼠兔', '移植猪', '鸡', '食蟹猴'])).toContain('食蟹猴')
  })
})

describe('isManualSpecies / speciesProblem', () => {
  const choices = ['人', '鼠兔', '移植猪', '鸡']

  it('不在常用值里的就是手填的；空不算', () => {
    expect(isManualSpecies('食蟹猴', choices)).toBe(true)
    expect(isManualSpecies(' 人 ', choices)).toBe(false)
    expect(isManualSpecies('', choices)).toBe(false)
  })

  it('必填；手填最多 50 字', () => {
    expect(speciesProblem('  ')).toBe('请选择种属')
    expect(speciesProblem(undefined)).toBe('请选择种属')
    expect(speciesProblem('人')).toBe('')
    expect(speciesProblem('种'.repeat(50))).toBe('')
    expect(speciesProblem('种'.repeat(51))).toBe('种属不能超过 50 字')
    expect(normalizeSpecies(' 鸡 ')).toBe('鸡')
  })
})
