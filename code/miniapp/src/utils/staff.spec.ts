// 选人的纯函数（CR-20261012-22）：面板显示什么、默认值、提交值。
import { describe, expect, it } from 'vitest'
import { defaultPerson, normalizeStaff, personIdOf, personOf, staffSheetView } from './staff'

const STAFF = normalizeStaff([
  { userId: 9000000101, name: '李工' },
  { userId: '1970000000000000001', name: ' 刘佳 ' },
  { userId: 9000000101, name: '重复行' },
  { userId: 5, name: '  ' },
  null,
])

describe('normalizeStaff', () => {
  it('账号 id 转字符串（雪花 id 超出安全整数）、姓名去空白、去重、去掉没名字的', () => {
    expect(STAFF).toEqual([
      { userId: '9000000101', name: '李工' },
      { userId: '1970000000000000001', name: '刘佳' },
    ])
  })
})

describe('staffSheetView', () => {
  it('不输入：名单里的人都列出来，现在选的那一个打勾', () => {
    const view = staffSheetView(STAFF, '', personOf(9000000101, '李工'))
    expect(view.rows.map(r => [r.label, r.selected])).toEqual([['李工', true], ['刘佳', false]])
    expect(view.noStaff).toBe(false)
    expect(view.noMatch).toBe(false)
  })

  it('输入了字：按「包含」过滤；一个都没对上要说出来（不是空白一片）', () => {
    expect(staffSheetView(STAFF, '刘', personOf('', '')).rows.map(r => r.label)).toEqual(['刘佳'])
    const none = staffSheetView(STAFF, '袁琴琴', personOf('', ''))
    expect(none.rows).toEqual([])
    expect(none.noMatch).toBe(true)
  })

  it('★ 历史手填的姓名（没关联账号）：排在最后、带标注、打勾 —— 再打开时看得见它是谁', () => {
    const view = staffSheetView(STAFF, '', personOf('', '袁琴琴'))
    expect(view.rows[2]).toEqual({
      key: 'current',
      person: { id: '', name: '袁琴琴' },
      label: '袁琴琴（未关联账号）',
      selected: true,
      legacy: true,
    })
    expect(view.rows.slice(0, 2).every(r => !r.selected)).toBe(true)
  })

  it('已撤销授权的人：看得见、带标注', () => {
    const view = staffSheetView(STAFF, '', personOf('77', '离职的人'))
    expect(view.rows[2].label).toBe('离职的人（已不在名单）')
    expect(view.rows[2].legacy).toBe(true)
  })

  it('名单是空的（还没加人 / 没拉到）：说得出来，现值照样看得见', () => {
    const view = staffSheetView([], '', personOf('', '李工'))
    expect(view.noStaff).toBe(true)
    expect(view.rows.map(r => r.label)).toEqual(['李工（未关联账号）'])
  })
})

describe('defaultPerson', () => {
  it('当前登录人在名单里：账号 id + 名单里的姓名', () => {
    expect(defaultPerson(STAFF, 9000000101, '李工（昵称）')).toEqual({ id: '9000000101', name: '李工' })
  })

  it('★ 名单没拉到 / 不在名单里：只带姓名（后端按姓名再对一次，对不上只存文字），不挡新增', () => {
    expect(defaultPerson([], 9000000101, '李工')).toEqual({ id: '', name: '李工' })
    expect(defaultPerson(STAFF, 1, '')).toEqual({ id: '', name: '' })
  })
})

describe('personIdOf', () => {
  it('没有账号 = null（不是空串：后端收的是数字）', () => {
    expect(personIdOf('')).toBeNull()
    expect(personIdOf('1970000000000000001')).toBe('1970000000000000001')
    expect(personIdOf(9000000101)).toBe('9000000101')
  })
})
