import { describe, expect, it } from 'vitest'
import { identityLabel, normalizeIdentity } from './identity'

describe('核验状态决定对外身份展示（CR-20261113-31）', () => {
  it.each(['unbound', 'pending', 'rejected', undefined, null, 'VERIFIED', 'unexpected'])(
    '未核验、驳回或异常状态 %s 不展示为合作单位', (status) => {
      expect(identityLabel('external', status)).toBe('外部人员')
    },
  )
  it('核验通过后展示合作单位，技术身份仍为external', () => {
    expect(identityLabel('external', 'verified')).toBe('合作单位')
    expect(normalizeIdentity('external')).toBe('external')
  })
  it('内部身份不受外部核验状态影响', () => {
    for (const status of ['unbound', 'pending', 'verified', 'rejected', null]) {
      expect(identityLabel('internal', status)).toBe('内部人员')
    }
  })
  it('未知身份不因verified而获得标签或内部身份', () => {
    expect(identityLabel(normalizeIdentity('partner'), 'verified')).toBe('')
  })
})
