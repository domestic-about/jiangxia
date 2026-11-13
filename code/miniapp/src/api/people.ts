import { http } from '@/utils/request'

export interface ReviewPerson {
  userId: string | number
  name?: string | null
  phone?: string | null
  unitId?: string | number | null
  groupId?: string | number | null
  unitName?: string | null
  groupName?: string | null
  unitNameInput?: string | null
  groupNameInput?: string | null
  selfInput?: boolean
  bindStatus: string
  rejectReason?: string | null
}
export interface ReviewBody {
  action: 'approve' | 'reject'
  unitId?: string | number
  groupId?: string | number
  createUnit?: boolean
  createGroup?: boolean
  reason?: string
}
// 后端同时校验list/verify权限；UI不靠“内部”身份自行授予审核权。
export const fetchReviewAccess = () => http.get<boolean>('/lqg/auth/ext-user/access', undefined, { silent: true })
export const fetchReviewPeople = (bindStatus: string) => http.get<ReviewPerson[]>('/lqg/auth/ext-user/list', { bindStatus }, { silent: true })
export const reviewPerson = (id: string | number, body: ReviewBody) => http.put<void>(`/lqg/auth/ext-user/${id}/verify`, body, { silent: true })
