// 种属纯函数（CR-20261009-18）
import { describe, expect, it, vi } from 'vitest'
import { DEFAULT_SPECIES, normalizeSpecies, speciesChoices, speciesOnConfirm, speciesProblem, speciesSheetView } from './species'

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

describe('speciesProblem', () => {
  it('必填；手填最多 50 字', () => {
    expect(speciesProblem('  ')).toBe('请选择种属')
    expect(speciesProblem(undefined)).toBe('请选择种属')
    expect(speciesProblem('人')).toBe('')
    expect(speciesProblem('种'.repeat(50))).toBe('')
    expect(speciesProblem('种'.repeat(51))).toBe('种属不能超过 50 字')
    expect(normalizeSpecies(' 鸡 ')).toBe('鸡')
  })
})

describe('speciesSheetView / speciesOnConfirm（种属面板：一个输入框既搜索又能直接用）', () => {
  const opts = ['人', '鼠兔', '移植猪', '鸡']

  it('没输入：列常用值，当前值打勾；不出「使用」行', () => {
    const v = speciesSheetView(opts, '', '鼠兔')
    expect(v.rows).toEqual([
      { value: '人', selected: false },
      { value: '鼠兔', selected: true },
      { value: '移植猪', selected: false },
      { value: '鸡', selected: false },
    ])
    expect(v.createValue).toBeNull()
  })

  it('当前值是手填的：也列出来（最后一行、打勾），再打开面板看得见它', () => {
    const v = speciesSheetView(opts, '', '食蟹猴')
    expect(v.rows.at(-1)).toEqual({ value: '食蟹猴', selected: true })
  })

  it('输入「猪」：过滤到「移植猪」；不是完全相同 → 顶上出「使用「猪」」', () => {
    const v = speciesSheetView(opts, ' 猪 ', '')
    expect(v.rows.map(r => r.value)).toEqual(['移植猪'])
    expect(v.createValue).toBe('猪')
  })

  it('输入正好是常用值：不出「使用」行（不让人建一个重复的）', () => {
    expect(speciesSheetView(opts, '鸡', '').createValue).toBeNull()
  })

  it('输入字典外的值：没有行可选，只剩「使用「食蟹猴」」', () => {
    const v = speciesSheetView(opts, '食蟹猴', '人')
    expect(v.rows).toEqual([])
    expect(v.createValue).toBe('食蟹猴')
  })

  it('超过 50 字按 50 字截', () => {
    expect(speciesSheetView(opts, '种'.repeat(60), '').createValue).toBe('种'.repeat(50))
  })

  it('键盘「完成」：有「使用」行就用输入；正好等于某一行就用那一行；只剩一行也用它；否则不动', () => {
    expect(speciesOnConfirm(speciesSheetView(opts, '食蟹猴', ''), '食蟹猴')).toBe('食蟹猴')
    expect(speciesOnConfirm(speciesSheetView(opts, '鸡', ''), '鸡')).toBe('鸡')
    expect(speciesOnConfirm(speciesSheetView(opts, '', ''), '')).toBeNull()
    expect(speciesOnConfirm(speciesSheetView(['鼠兔', '鼠'], '鼠', ''), '鼠')).toBe('鼠')
  })
})
