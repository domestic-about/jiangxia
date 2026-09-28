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

/**
 * 本人档案里**绑定**的单位（V01 与后端同口径）：档案里有 `unitId` 且核验状态是待核验或已核验。
 * 自填单位（只有 `unitNameInput`）、未绑定、被驳回的都没有可用的单位 id。
 */
export function boundUnitOf(ext: MpExtProfile | null | undefined): SelectorUnit | null {
  if (!ext || ext.unitId === null || ext.unitId === undefined || ext.unitId === '' || !ext.unitName) {
    return null
  }
  if (ext.bindStatus !== 'pending' && ext.bindStatus !== 'verified') {
    return null
  }
  return { unitId: ext.unitId, unitName: ext.unitName, groups: [] }
}

/**
 * 填写页「来源单位」选择面板里列哪些单位（组织样本、类器官两张表共用）：
 *   · 内部：全部启用单位（内部路径**只认 id**，后端不按名字回找，所以要能从列表里选）；
 *   · 外部：**只有本人绑定的那一个**（后端只收本人绑定单位的 id，列全部单位会让人选到别的单位被 400）；
 *   · 身份不明：一个都不列。
 * 两种身份都另有「列表里没有，手动填写」（只落单位名，不带 id）。
 */
export function unitOptionsFor(
  identity: unknown,
  units: SelectorUnit[],
  ext: MpExtProfile | null | undefined,
): SelectorUnit[] {
  if (identity === 'internal') {
    return units
  }
  if (identity === 'external') {
    const bound = boundUnitOf(ext)
    return bound ? [bound] : []
  }
  return []
}
