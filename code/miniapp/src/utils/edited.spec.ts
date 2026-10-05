import { describe, expect, it } from 'vitest'
import { wasEdited } from '@/utils/edited'

describe('wasEdited：新建时后端就填了 updateTime，不能当成「改过」', () => {
  it('从没更新过 = 新增', () => {
    expect(wasEdited({ createTime: '2026-10-05 10:00:00', updateTime: null })).toBe(false)
  })
  it('更新时间与创建时间同一刻（新建时自动填的）= 新增', () => {
    expect(wasEdited({ createTime: '2026-10-05 10:00:00', updateTime: '2026-10-05 10:00:00' })).toBe(false)
    expect(wasEdited({ createTime: '2026-10-05 10:00:00', updateTime: '2026-10-05 10:00:01' })).toBe(false)
  })
  it('晚了 1 秒以上 = 修改', () => {
    expect(wasEdited({ createTime: '2026-10-05 10:00:00', updateTime: '2026-10-05 10:05:00' })).toBe(true)
  })
  it('没给创建时间：有更新时间就算改过（旧判据）', () => {
    expect(wasEdited({ updateTime: '2026-10-05 10:05:00' })).toBe(true)
  })
})
