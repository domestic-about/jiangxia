// 「待核验」列表页的纯函数：页签解析、点一行去哪、一行怎么摘要。
//
// 行数据按 seed 写（doc/verify/README.md）：1002 / 1007 是两条待核验的组织样本，
// 2006 是挂在 1002 上的待核验石蜡包埋送样（样本自己也还待核验）。
import { describe, expect, it } from 'vitest'
import { VERIFY_TABS, embedCardOf, isPending, minuteOf, sampleCardOf, tabOf, verifyTarget } from './tabs'

describe('tabOf', () => {
  it('三个页签原样认', () => {
    VERIFY_TABS.forEach(tab => expect(tabOf(tab)).toBe(tab))
  })

  it('缺失 / 不认识 → 第一个页签（样本记录）', () => {
    expect(tabOf(undefined)).toBe('tissue')
    expect(tabOf('')).toBe('tissue')
    expect(tabOf('cryo')).toBe('tissue')
    expect(tabOf('EMBED')).toBe('tissue')
  })
})

describe('verifyTarget / isPending', () => {
  it('样本两张表进同一个样本核验页，石蜡包埋进自己的核验页', () => {
    expect(verifyTarget('tissue', '9000001002')).toBe('/pages/verify/sample?id=9000001002')
    expect(verifyTarget('organoid', 9000001011)).toBe('/pages/verify/sample?id=9000001011')
    expect(verifyTarget('embed', '9000002006')).toBe('/pages/verify/embed?id=9000002006')
  })

  it('只有待核验才算（有效、无效、缺失都不算）', () => {
    expect(isPending({ verifyStatus: 'pending' })).toBe(true)
    expect(isPending({ verifyStatus: 'valid' })).toBe(false)
    expect(isPending({ verifyStatus: 'invalid' })).toBe(false)
    expect(isPending({})).toBe(false)
    expect(isPending(null)).toBe(false)
  })
})

describe('一行怎么摘要', () => {
  it('组织样本：送检单号 + 单位 · 组织类型 · 打码的供体姓名 + 提交人 + 提交时间到分钟', () => {
    expect(sampleCardOf({
      id: 9000001002,
      submitNo: 'SJ90000002',
      sampleKind: 'tissue',
      sourceUnitName: 'A 医院',
      tissueType: '胆管组织',
      donorName: '测试供体乙',
      submitterName: '王医生',
      createTime: '2026-09-24 00:00:01',
      updateTime: null,
    })).toEqual({
      id: '9000001002',
      code: 'SJ90000002',
      summary: 'A 医院 · 胆管组织 · 测**',
      owner: '王医生',
      action: '提交',
      date: '2026-09-24 00:00',
    })
  })

  it('合作单位改后重提的：动作写「重新提交」，时间取最后那次', () => {
    const card = sampleCardOf({
      id: 1,
      submitNo: 'SJ1',
      sampleKind: 'tissue',
      createTime: '2026-09-20 08:00:00',
      updateTime: '2026-09-23 17:45:12',
    })
    expect(card.action).toBe('重新提交')
    expect(card.date).toBe('2026-09-23 17:45')
  })

  it('外部刚建的那一刻最后修改时间 = 创建时间（后端插入时一起填）：仍是「提交」', () => {
    const card = sampleCardOf({
      id: 1,
      submitNo: 'SJ1',
      sampleKind: 'tissue',
      createTime: '2026-09-24 10:13:56',
      updateTime: '2026-09-24 10:13:56',
    })
    expect(card.action).toBe('提交')
    expect(card.date).toBe('2026-09-24 10:13')
  })

  it('类器官：单位 · 类器官类型 · 代数（后端还没有代数时就不写）', () => {
    expect(sampleCardOf({ id: 1, sampleKind: 'organoid', sourceUnitName: 'B 大学', organoidType: '肝类器官', passage: 'P3' }).summary)
      .toBe('B 大学 · 肝类器官 · P3')
    expect(sampleCardOf({ id: 1, sampleKind: 'organoid', sourceUnitName: 'B 大学', organoidType: '肝类器官' }).summary)
      .toBe('B 大学 · 肝类器官')
  })

  it('石蜡包埋送样：所挂样本的送检单号；样本也待核验时写明', () => {
    expect(embedCardOf({
      id: 9000002006,
      submitNo: 'SJ90000002',
      sourceUnitName: 'A 医院',
      sampleType: '组织',
      organoidSourceType: null,
      sampleVerifyStatus: 'pending',
      handlerName: '王医生',
      createTime: '2026-09-24 00:00:04',
    })).toEqual({
      id: '9000002006',
      code: 'SJ90000002',
      summary: 'A 医院 · 组织 · 样本也待核验',
      owner: '王医生',
      action: '提交',
      date: '2026-09-24 00:00',
    })
    expect(embedCardOf({ id: 1, sampleType: '组织', sampleVerifyStatus: 'valid' }).summary).toBe('组织')
  })

  it('缺编号显示「—」，时间形状不认识就原样', () => {
    expect(sampleCardOf({ id: 1 }).code).toBe('—')
    expect(minuteOf('2026-09-24')).toBe('2026-09-24')
    expect(minuteOf(null)).toBe('')
  })
})
