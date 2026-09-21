// AUTH 域 · 单位与组别（外部）—— AUTH-GROUP-001
// 后端：org.dromara.lqg.ext.controller.ExtUnitController / ExtProfileController
// 契约：doc/api-contract.md「AUTH」一节
//   GET /mp/ext/units     → data:[{unitId, unitName, groups:[{groupId, groupName}]}]，只含 active
//   PUT /mp/ext/profile   → {realName, unitId?, groupId?, unitNameInput?, groupNameInput?} → bindStatus=pending
import type { MpExtProfile } from '@/types/mp'
import { http } from '@/utils/request'

/** 对外选择器里的组别（只有 id 与名称 —— 外部看不到人数） */
export interface SelectorGroup {
  groupId: string | number
  groupName: string
}

/** 对外选择器里的单位（只含启用项） */
export interface SelectorUnit {
  unitId: string | number
  unitName: string
  groups: SelectorGroup[]
}

/** 外部档案保存入参：选列表项给两个 id；手动填写给两个 *NameInput（两套互斥） */
export interface ProfileUpdatePayload {
  realName: string
  unitId?: string | number | null
  groupId?: string | number | null
  unitNameInput?: string | null
  groupNameInput?: string | null
}

/** 启用中的单位—组别选择器数据 */
export function fetchUnits() {
  return http.get<SelectorUnit[]>('/mp/ext/units', undefined, { silent: true })
}

/** 保存姓名 + 单位 / 组别；成功后后端把档案置回 pending（要重新核验） */
export function saveProfile(payload: ProfileUpdatePayload) {
  return http.put<MpExtProfile | null>('/mp/ext/profile', payload)
}
