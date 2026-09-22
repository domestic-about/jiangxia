// 批次详情弹层里「取用登记」那一段的取数与纯文案（CRYO-MP-001 / UI:mp.cryo.flow）。
//
// ★ **只读**：这一档只有 `GET /mp/int/cryo/batch/{id}/flows`（时间倒序，每行带操作后剩余与
//   「改过没有」）。本文件里没有、也不许有取走 / 补入 / 转液氮 / 改删登记的封装
//   —— `/mp/int/cryo/**` 上根本没有那些端点（CR-20260917-05，accept 2 会真调它们要求 404）。
//
// ★ 类型文案（三种登记的显示名）放在 `api/cryo.ts` 的 `CRYO_FLOW_TEXT`：ticket 的两段
//   禁字 grep 一边不许本目录出现那三个字的中文与字典值、一边不许 `api/cryo.ts` 出现响应字段名，
//   两段合起来只留下那个家（`grep` 会扫注释，连注释里也不能搬过来）。
import { CRYO_FLOW_TEXT } from '@/api/cryo'
import { http } from '@/utils/request'

/** 一行取用登记（后端 `CryoFlowRecordVo` 逐字段同形） */
export interface CryoFlowRecord {
  id: string | number
  batchId?: string | number | null
  /** 登记类型（三种；文案只在 `CRYO_FLOW_TEXT` 里，见那一处的说明） */
  flowType?: string | null
  /** 带符号变化量（取走恒为负、补入恒为正） */
  delta?: number | null
  /** 登记那一刻的位置（minus80 / ln2），不随后续转移重算 */
  fromLocation?: string | null
  operatorName?: string | null
  /** 发生时间 yyyy-MM-dd HH:mm:ss */
  flowTime?: string | null
  purpose?: string | null
  /** 这一笔之后的剩余（读时算） */
  balanceAfter?: number | null
  /** 改过没有 */
  edited?: boolean | null
  updateByName?: string | null
  /** 批次当前剩余（后端只填在最新一笔上） */
  remainingQty?: number | null
  updateTime?: string | null
}

function str(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

/** 这一批的全部取用登记（时间倒序；只读口） */
export function fetchCryoFlows(batchId: string | number) {
  return http.get<CryoFlowRecord[]>(`/mp/int/cryo/batch/${batchId}/flows`)
}

/** 类型文案（三种登记的显示名，表在本文件 import 的那一份里）；字典外的历史值按原值带出，不吞掉 */
export function flowKindText(row: Partial<CryoFlowRecord>): string {
  const kind = str(row.flowType)
  return CRYO_FLOW_TEXT[kind] || kind || '—'
}

/** ±支数（后端给的 `delta` 带符号，取走是负数 —— 照实显示，不在前端翻符号） */
export function flowDeltaText(row: Partial<CryoFlowRecord>): string {
  const delta = row.delta
  if (delta === null || delta === undefined) {
    return '—'
  }
  return delta > 0 ? `+${delta}` : String(delta)
}

/** 发生时间：`MM-DD HH:mm`（列表里不必到秒） */
export function flowTimeText(row: Partial<CryoFlowRecord>): string {
  const text = str(row.flowTime)
  if (text.length < 16) {
    return text || '—'
  }
  return `${text.slice(5, 10)} ${text.slice(11, 16)}`
}

/** 操作后剩余（弹层每行右侧那一格） */
export function flowBalanceText(row: Partial<CryoFlowRecord>): string {
  const balance = row.balanceAfter
  return balance === null || balance === undefined ? '剩 —' : `剩 ${balance}`
}

/** 经手人 */
export function flowOperatorText(row: Partial<CryoFlowRecord>): string {
  return str(row.operatorName) || '—'
}

/** 用途 / 原因 */
export function flowPurposeText(row: Partial<CryoFlowRecord>): string {
  return str(row.purpose) || '—'
}

/** 这一笔改过没有（改过的在行尾标「已改」，gallery `#mp-cryo` 的口径） */
export function isEdited(row: Partial<CryoFlowRecord>): boolean {
  return row.edited === true
}

/** 「已改 · 某某」（没改过回空串，页面不渲染） */
export function editedText(row: Partial<CryoFlowRecord>): string {
  if (!isEdited(row)) {
    return ''
  }
  return `已改 · ${str(row.updateByName) || '—'}`
}
