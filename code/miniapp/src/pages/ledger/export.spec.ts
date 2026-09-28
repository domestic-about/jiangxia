// 「导出 Excel」查询串纯函数的用例（SYS-EXPORT-001 · Accept 2 第 4 段：`numPassedTests >= 3`）。
//
// ★ 期望值全部写死成**字面量**（不从被测函数反推 —— 否则「改坏实现 → 期望跟着变」）。
// ★ 用例钉的是 ticket §0 口径 2「筛选参数与表格页当前筛选一致：用户筛了什么就导出什么，
//   不筛就是全部」：所以每条都断言**整串**（多一个 `&` 也算不一致）。
import { describe, expect, it } from 'vitest'
import type { LedgerFilters } from '@/api/ledger'
import { isExportSheet, EXPORT_PATH_PREFIX, exportFileName, exportUrl } from './export'

describe('exportUrl(sheet, filters)', () => {
  it('不筛 = 只有路径，查询串一个字符都不带', () => {
    expect(exportUrl('tissue')).toBe('/mp/int/export/tissue')
    expect(exportUrl('tissue', null)).toBe('/mp/int/export/tissue')
    expect(exportUrl('organoid', {})).toBe('/mp/int/export/organoid')
    expect(exportUrl('embed', {})).toBe('/mp/int/export/embed')
    expect(exportUrl('cryo', {})).toBe('/mp/int/export/cryo')
  })

  it('★ 空值不带参数（带了空串 = 后端筛「空串」，行数就不是全部）', () => {
    const empty = { keyword: '', verifyStatus: '', stain: '', cryoView: '' }
    expect(exportUrl('tissue', empty)).toBe('/mp/int/export/tissue')
    expect(exportUrl('organoid', empty)).toBe('/mp/int/export/organoid')
    expect(exportUrl('embed', empty)).toBe('/mp/int/export/embed')
    expect(exportUrl('cryo', empty)).toBe('/mp/int/export/cryo')
  })

  it('样本两张表：keyword + verifyStatus（与 /mp/int/sample/list 同名同序）', () => {
    expect(exportUrl('tissue', { verifyStatus: 'pending' }))
      .toBe('/mp/int/export/tissue?verifyStatus=pending')
    expect(exportUrl('organoid', { keyword: 'T-oco01', verifyStatus: 'valid' }))
      .toBe('/mp/int/export/organoid?keyword=T-oco01&verifyStatus=valid')
  })

  it('中文搜索词要 URL 编码（源单位「A 医院」这种）', () => {
    expect(exportUrl('tissue', { keyword: 'A 医院' }))
      .toBe('/mp/int/export/tissue?keyword=A%20%E5%8C%BB%E9%99%A2')
  })

  it('石蜡包埋：keyword + verifyStatus + stain 三个都带；染色是数组包含语义的值', () => {
    expect(exportUrl('embed', { keyword: 'T-E01', verifyStatus: 'valid', stain: 'IHC' }))
      .toBe('/mp/int/export/embed?keyword=T-E01&verifyStatus=valid&stain=IHC')
    // 样本表**不**带 stain（那张表没有染色列，带了就是空页）
    expect(exportUrl('tissue', { stain: 'HE' })).toBe('/mp/int/export/tissue')
  })

  it('-80 冻存的三个页签：全部不带 / 超期 overdueOnly=true / 液氮 location=ln2', () => {
    expect(exportUrl('cryo', { cryoView: 'overdue' })).toBe('/mp/int/export/cryo?overdueOnly=true')
    expect(exportUrl('cryo', { cryoView: 'ln2' })).toBe('/mp/int/export/cryo?location=ln2')
    // 2026-09-24 加的第四个页签：已取空
    expect(exportUrl('cryo', { cryoView: 'emptied' })).toBe('/mp/int/export/cryo?emptiedOnly=true')
    // 两个页签不会同时生效（一个单选）
    // （`location` 不是表格页的筛选键：故意夹带一个多余键，断它不会被原样拼进查询串 —— 用类型断言绕过多余属性检查）
    expect(exportUrl('cryo', { cryoView: 'overdue', location: 'ln2' } as Partial<LedgerFilters>))
      .toBe('/mp/int/export/cryo?overdueOnly=true')
  })

  it('冻存表不读验证状态 / 搜索框（表格页那一张本来就没有这两个筛选）', () => {
    expect(exportUrl('cryo', { keyword: 'T-hli01', verifyStatus: 'pending' }))
      .toBe('/mp/int/export/cryo')
  })

  it('前缀就一处：四张表都在 /mp/int/export/ 下（契约第 55 行）', () => {
    expect(EXPORT_PATH_PREFIX).toBe('/mp/int/export/')
    for (const sheet of ['tissue', 'organoid', 'embed', 'cryo'] as const) {
      expect(exportUrl(sheet).startsWith(EXPORT_PATH_PREFIX)).toBe(true)
    }
  })

  it('未知工作表不认（页面拿 ?? 的 sheet 串时据此退到第一个已注册表）', () => {
    expect(isExportSheet('tissue')).toBe(true)
    expect(isExportSheet('cryo')).toBe(true)
    expect(isExportSheet('qc')).toBe(false)
    expect(isExportSheet('')).toBe(false)
    expect(isExportSheet(undefined)).toBe(false)
    expect(isExportSheet(1)).toBe(false)
  })
})

describe('exportFileName(sheet, at)', () => {
  it('服务端拿不到名字时的本地兜底：<工作表名>-<yyyyMMddHHmmss>.xlsx', () => {
    const at = new Date(2026, 8, 18, 10, 20, 30) // 2026-09-18 10:20:30（本地时区）
    expect(exportFileName('tissue', at)).toBe('样本记录信息表-20260918102030.xlsx')
    expect(exportFileName('organoid', at)).toBe('类器官收样记录-20260918102030.xlsx')
    expect(exportFileName('embed', at)).toBe('石蜡包埋送样记录-20260918102030.xlsx')
    expect(exportFileName('cryo', at)).toBe('-80冻存-20260918102030.xlsx')
  })
})
