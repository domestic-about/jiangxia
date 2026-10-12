// 操作人统计的时间段与显示（CR-20261012-22）。
import { describe, expect, it } from 'vitest'
import { presetRange, rangeInverted, rangeText, rowTag, rowsMeFirst, sheetNote, ymd } from './range'

describe('presetRange', () => {
  // 2026-10-12 是周一；2026-10-18 是周日
  it('本周 = 周一到周日（周日那天也还在这一周里）', () => {
    for (const day of [12, 14, 18]) {
      expect(presetRange('week', new Date(2026, 9, day))).toEqual({ begin: '2026-10-12', end: '2026-10-18' })
    }
  })

  it('本周跨年也对', () => {
    expect(presetRange('week', new Date(2026, 11, 31))).toEqual({ begin: '2026-12-28', end: '2027-01-03' })
  })

  it('本月到当月最后一天（闰年二月）；今年 / 去年 / 全部', () => {
    const today = new Date(2026, 9, 12)
    expect(presetRange('month', today)).toEqual({ begin: '2026-10-01', end: '2026-10-31' })
    expect(presetRange('month', new Date(2028, 1, 10))).toEqual({ begin: '2028-02-01', end: '2028-02-29' })
    expect(presetRange('year', today)).toEqual({ begin: '2026-01-01', end: '2026-12-31' })
    expect(presetRange('lastYear', today)).toEqual({ begin: '2025-01-01', end: '2025-12-31' })
    expect(presetRange('all', today)).toEqual({ begin: '', end: '' })
  })

  it('日期按本地算，不经过 UTC（凌晨不会退一天）', () => {
    expect(ymd(new Date(2026, 0, 1, 0, 30))).toBe('2026-01-01')
  })
})

describe('rangeInverted / rangeText', () => {
  it('只在两头都填、且开始晚于结束时算填反', () => {
    expect(rangeInverted({ begin: '2026-10-12', end: '2026-10-01' })).toBe(true)
    expect(rangeInverted({ begin: '2026-10-01', end: '2026-10-01' })).toBe(false)
    expect(rangeInverted({ begin: '', end: '2026-10-01' })).toBe(false)
  })

  it('★ 只填一头：这一天及之前 / 及之后（甲方「在这之前，或者是之后」）', () => {
    expect(rangeText({ begin: '2026-10-01', end: '2026-10-31' })).toBe('2026-10-01 至 2026-10-31')
    expect(rangeText({ begin: '', end: '2026-09-30' })).toBe('2026-09-30 及之前')
    expect(rangeText({ begin: '2026-10-01', end: '' })).toBe('2026-10-01 及之后')
    expect(rangeText({ begin: '', end: '' })).toBe('全部时间')
  })
})

describe('行的显示', () => {
  it('小标：当前登录人 = 我；没关联账号的手填姓名 = 未关联账号；「未填」那一行、别人都没有小标', () => {
    expect(rowTag({ me: true, linked: true, key: 'u:1' })).toBe('me')
    expect(rowTag({ me: false, linked: false, key: 'n:袁琴琴' })).toBe('unlinked')
    expect(rowTag({ me: false, linked: false, key: 'none' })).toBe('')
    expect(rowTag({ me: false, linked: true, key: 'u:2' })).toBe('')
  })

  it('★ 当前登录人那一行置顶，其余保持接口给的顺序', () => {
    const rows = [{ name: '刘佳', me: false }, { name: '李工', me: true }, { name: '未填', me: false }]
    expect(rowsMeFirst(rows).map(r => r.name)).toEqual(['李工', '刘佳', '未填'])
  })

  it('每张表标题下说清按哪个时间、算哪些记录', () => {
    expect(sheetNote({ sheet: 'cryo', dateLabel: '冻存时间' })).toBe('按冻存时间 · 按冻存人算；支数 = 冻存数量之和（不是当前剩余）')
    expect(sheetNote({ sheet: 'x', dateLabel: '某时间' })).toBe('按某时间')
  })
})
