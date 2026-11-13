import type { ReviewBody, ReviewPerson } from '@/api/people'

export interface ReviewChoice {
  action: '' | 'approve' | 'reject'
  mode: '' | 'keep' | 'create' | 'merge'
  unitId?: string | number
  groupId?: string | number
  reason: string
}
export const selfInputOf = (row: ReviewPerson) => !!(row.selfInput || row.unitNameInput || row.groupNameInput)
export const emptyReview = (): ReviewChoice => ({ action: '', mode: '', reason: '' })
export function reviewBody(row: ReviewPerson, choice: ReviewChoice): ReviewBody {
  if (!['pending', 'verified'].includes(row.bindStatus)) throw new Error('需由申请人修改资料并重新提交后再审核')
  if (choice.action === 'reject' && row.bindStatus !== 'pending') throw new Error('已核验人员只能调整归组')
  if (!choice.action) throw new Error('请选择通过或驳回')
  if (choice.action === 'reject') {
    if (!choice.reason.trim()) throw new Error('请填写驳回原因')
    if (choice.reason.trim().length > 200) throw new Error('驳回原因最多200字')
    return { action: 'reject', reason: choice.reason.trim() }
  }
  if (choice.mode === 'create' && selfInputOf(row)) return { action: 'approve', createUnit: true, createGroup: true }
  if (choice.mode === 'merge') {
    if (!choice.unitId || !choice.groupId) throw new Error('请选择单位和该单位下的组别')
    return { action: 'approve', unitId: choice.unitId, groupId: choice.groupId }
  }
  if (choice.mode === 'keep' && !selfInputOf(row) && row.unitId && row.groupId) return { action: 'approve' }
  throw new Error(selfInputOf(row) ? '请选择新建或归并到已有单位与组别' : '请选择有效的单位和组别')
}
