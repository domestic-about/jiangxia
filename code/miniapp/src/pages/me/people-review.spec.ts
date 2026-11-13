import { describe, expect, it } from 'vitest'
import { emptyReview, reviewBody } from './people-review'
import type { ReviewPerson } from '@/api/people'
const known: ReviewPerson = { userId: '100', bindStatus: 'pending', unitId: 'u1', groupId: 'g1' }
const self: ReviewPerson = { userId: '101', bindStatus: 'pending', selfInput: true, unitNameInput: '医院', groupNameInput: '组' }
describe('单位组别审核显式决策', () => {
  it('已驳回和未绑定人员不能再次审核', () => {
    for (const bindStatus of ['rejected', 'unbound']) expect(() => reviewBody({ ...known, bindStatus }, { action: 'approve', mode: 'keep', reason: '' })).toThrow('重新提交')
  })
  it('已核验人员不能驳回，但可改归组', () => {
    const row = { ...known, bindStatus: 'verified' }
    expect(() => reviewBody(row, { action: 'reject', mode: '', reason: '原因' })).toThrow('只能调整归组')
    expect(reviewBody(row, { action: 'approve', mode: 'merge', unitId: 'u2', groupId: 'g2', reason: '' })).toEqual({ action: 'approve', unitId: 'u2', groupId: 'g2' })
  })
  it('打开表单不能默认通过', () => expect(() => reviewBody(known, emptyReview())).toThrow('请选择通过或驳回'))
  it('驳回必须有原因并去空白', () => {
    expect(() => reviewBody(known, { action: 'reject', mode: '', reason: ' ' })).toThrow('原因')
    expect(reviewBody(known, { action: 'reject', mode: '', reason: '  请核对组别 ' })).toEqual({ action: 'reject', reason: '请核对组别' })
  })
  it('自填不能暗中保留空ID通过', () => expect(() => reviewBody(self, { action: 'approve', mode: 'keep', reason: '' })).toThrow('新建或归并'))
  it('新建必须主动选择，且与归并ID互斥', () => expect(reviewBody(self, { action: 'approve', mode: 'create', unitId: 'old', groupId: 'old', reason: '' })).toEqual({ action: 'approve', createUnit: true, createGroup: true }))
  it('归并必须两个ID，给后端核验所属关系', () => {
    expect(() => reviewBody(self, { action: 'approve', mode: 'merge', unitId: 'u1', reason: '' })).toThrow('组别')
    expect(reviewBody(self, { action: 'approve', mode: 'merge', unitId: 'u1', groupId: 'g1', reason: '' })).toEqual({ action: 'approve', unitId: 'u1', groupId: 'g1' })
  })
  it('已选单位组别的申请也必须明确保留', () => {
    expect(() => reviewBody(known, { action: 'approve', mode: '', reason: '' })).toThrow('有效')
    expect(reviewBody(known, { action: 'approve', mode: 'keep', reason: '' })).toEqual({ action: 'approve' })
  })
})
