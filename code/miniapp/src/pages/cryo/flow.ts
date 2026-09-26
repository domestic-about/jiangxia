// 批次详情弹层里「取用登记」那一段的取数、写入与纯判据（CRYO-MP-001 / UI:mp.cryo.flow）。
//
// ★ 2026-09-24 甲方看设计稿 v3 后要求「小程序和工作台界面都能操作」（G 批 B2）：内部人员在弹层里
//   也能取走、补入、转液氮、改 / 删一笔登记。**写口只有一份**，就是工作台用的
//   `/lqg/cryo/batch/{id}/flow`、`…/flow/{flowId}`、`…/to-ln2`（CRYO-FLOW-001）——
//   小程序内部人员的账号带 `lqg_internal`，那几个权限串本来就授给它；外部账号照旧被拒。
//   `/mp/int/cryo/**` 上**仍然没有**这些写口（不另开一份转发，两份写口迟早规则不一样）。
// ★ 读仍走 `GET /mp/int/cryo/batch/{id}/flows`（时间倒序，每行带操作后剩余与「改过没有」）。
// ★ 小程序只登记**取走、补入**两种（`MP_FLOW_KINDS`）；第三种登记只在工作台做，
//   它的行在弹层里照样显示，但没有「改」「删」（`canChangeFlow`）。
// ★ 支数规则与工作台 `views/lqg/cryo/flow.ts` 是**同一组用例**钉住的
//   （`doc/verify/fixtures/cryo-take-cases.json`，两边的 spec 都读它）：
//   新登记的取走不能超过当前剩余；这一笔让剩余从大于 0 变成恰好 0 时提交前多问一句。
//   后端的逐笔校验不因此放松，被拒时把后端原话显示出来。
//
// ★ 类型文案（三种登记的显示名）放在 `api/cryo.ts` 的 `CRYO_FLOW_TEXT`：ticket 的两段
//   禁字 grep 一边不许本目录出现那一种登记的中文与字典值、一边不许 `api/cryo.ts` 出现响应字段名，
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

/** 这一批的全部取用登记（时间倒序） */
export function fetchCryoFlows(batchId: string | number) {
  return http.get<CryoFlowRecord[]>(`/mp/int/cryo/batch/${batchId}/flows`)
}

// ── 写：取走 / 补入 / 改删一笔 / 转液氮（与工作台同一份写口）──────────────────────
//
// ★ 一律 `silent: true`：被拒时由表单把后端原话显示在表单里（停在当前表单让人改），
//   不靠一闪而过的 toast —— 「会让后面某一步剩余为负」这种话一行 toast 装不下。

/** 小程序里能登记的两种：取走、补入（第三种只在工作台） */
export const MP_FLOW_KINDS = ['take', 'add'] as const
export type MpFlowKind = (typeof MP_FLOW_KINDS)[number]

/** 登记一笔的请求体（`POST /lqg/cryo/batch/{id}/flow`） */
export interface CryoFlowBody {
  flowType: MpFlowKind
  /** 正整数（取走也填正数，落库才变负） */
  qty: number
  purpose: string
  operatorName: string
  /** yyyy-MM-dd HH:mm:ss */
  flowTime: string
}

/** 改一笔的请求体（`PUT …/flow/{flowId}`）：类型原样回传（改不了类型），用途传空串 = 清空 */
export type CryoFlowEditBody = CryoFlowBody

/** 登记转液氮的请求体（`PUT …/to-ln2`） */
export interface CryoLn2Body {
  /** yyyy-MM-dd，不得早于冻存时间 */
  toLn2Time: string
  ln2Location: string
}

/** 登记一笔取走 / 补入 */
export function addCryoFlow(batchId: string | number, body: CryoFlowBody) {
  return http.post<string | number>(`/lqg/cryo/batch/${batchId}/flow`, body, { silent: true })
}

/** 改一笔登记 */
export function updateCryoFlow(batchId: string | number, flowId: string | number, body: CryoFlowEditBody) {
  return http.put<void>(`/lqg/cryo/batch/${batchId}/flow/${flowId}`, body, { silent: true })
}

/** 删一笔登记（软删：追溯不断） */
export function deleteCryoFlow(batchId: string | number, flowId: string | number) {
  return http.delete<void>(`/lqg/cryo/batch/${batchId}/flow/${flowId}`, undefined, { silent: true })
}

/** 登记转液氮（保存后批次位置当场变液氮，不再提醒转液氮） */
export function registerCryoLn2(batchId: string | number, body: CryoLn2Body) {
  return http.put<void>(`/lqg/cryo/batch/${batchId}/to-ln2`, body, { silent: true })
}

// ── 纯判据（与工作台同一组用例：doc/verify/fixtures/cryo-take-cases.json）────────────

/** 是不是小程序能登记 / 改删的那两种 */
export function isMpFlowKind(kind: unknown): kind is MpFlowKind {
  return (MP_FLOW_KINDS as readonly unknown[]).includes(kind)
}

/** 这一笔在小程序里能不能「改」「删」：只有取走、补入（第三种在工作台改） */
export function canChangeFlow(row: Partial<CryoFlowRecord>): boolean {
  return isMpFlowKind(row.flowType)
}

/** 这一笔的登记类型（小程序能改的那两种之一；别的返回 null） */
export function mpFlowKindOf(row: Partial<CryoFlowRecord>): MpFlowKind | null {
  return isMpFlowKind(row.flowType) ? row.flowType : null
}

/** 支数输入框里的字符串 → 数字（空串 / 非数字 → NaN） */
export function parseQty(text: string): number {
  const value = String(text ?? '').trim()
  return value === '' ? Number.NaN : Number(value)
}

/**
 * 支数判据：`''` 合法 / `invalid` 不是正整数 / `overRemaining` 新登记的取走超过当前剩余。
 *
 * ★ 改一笔时（`editing`）不在前端判超取：改的是历史账，「那一刻的余额」只有后端逐笔重算得出来。
 */
export function flowQtyProblem(kind: MpFlowKind, qty: number, remaining: number | null | undefined, editing = false): '' | 'invalid' | 'overRemaining' {
  if (!Number.isInteger(qty) || qty <= 0) {
    return 'invalid'
  }
  if (!editing && kind === 'take' && remaining !== null && remaining !== undefined && qty > remaining) {
    return 'overRemaining'
  }
  return ''
}

/** 支数判据的中文提示（与工作台同一口径） */
export function flowQtyMessage(problem: '' | 'invalid' | 'overRemaining', kind: MpFlowKind, remaining: number | null | undefined): string {
  if (problem === 'invalid') {
    return kind === 'add' ? '补入支数必须是正整数' : '取走支数必须是正整数'
  }
  if (problem === 'overRemaining') {
    return `取走支数不能超过当前剩余 ${remaining ?? 0} 支`
  }
  return ''
}

/**
 * 保存后批次还剩几支（qty 不合法 → null）。
 *
 * @param oldDelta 改一笔时，被改那一笔原来的带符号支数（新登记为 0）
 */
export function remainingAfter(kind: MpFlowKind, qty: number, remaining: number, oldDelta = 0): number | null {
  if (!Number.isInteger(qty) || qty <= 0) {
    return null
  }
  const delta = kind === 'take' ? -qty : qty
  return remaining - oldDelta + delta
}

/**
 * 提交前要不要多问一句「登记后这一批就取空了」：取走、且这一笔让剩余**从大于 0 变成恰好 0**。
 * （批次本来就是 0、改的又不是支数时不问；补入不问。）
 */
export function needsEmptyConfirm(kind: MpFlowKind, qty: number, remaining: number | null | undefined, oldDelta = 0): boolean {
  if (kind !== 'take' || remaining === null || remaining === undefined || remaining <= 0) {
    return false
  }
  return remainingAfter(kind, qty, remaining, oldDelta) === 0
}

/** 取空确认的那句话（两端同一句） */
export const EMPTY_CONFIRM_TEXT = '登记后这一批就取空了（剩 0 支），确定吗？'

/** 这一批还能不能「转液氮」：暂存 -80 且还没登记过转液氮（直接进液氮的、已转过的都没有这个按钮） */
export function canRegisterLn2(batch: { inMinus80?: string | null, toLn2Time?: string | null } | null | undefined): boolean {
  if (!batch) {
    return false
  }
  return str(batch.inMinus80).toUpperCase() !== 'N' && !str(batch.toLn2Time)
}

/** 转液氮表单的判据：时间必填且不早于冻存时间、位置必填（与后端同口径；`''` = 可以提交） */
export function ln2Problem(toLn2Time: string, ln2Location: string, freezeTime?: string | null): string {
  const day = str(toLn2Time).slice(0, 10)
  if (!day) {
    return '请选择转移至液氮时间'
  }
  const freeze = str(freezeTime).slice(0, 10)
  if (freeze && day < freeze) {
    return `转移时间不能早于冻存时间 ${freeze}`
  }
  if (!str(ln2Location).trim()) {
    return '请填液氮储存位置'
  }
  return ''
}

/** 取走从哪取：按批次**当前**位置自动定（只读，不让人选）；改一笔时是登记那一刻的位置 */
export function takeFromText(location?: string | null): string {
  return str(location) === 'ln2' ? '从液氮取走' : '从 -80℃ 取走'
}

/** 这一笔取走是从哪取的（登记那一刻的位置，列表里一行的小字）；不是取走返回空串 */
export function flowFromText(row: Partial<CryoFlowRecord>): string {
  if (row.flowType !== 'take') {
    return ''
  }
  return str(row.fromLocation) === 'ln2' ? '液氮' : '-80℃'
}

/** 删之前那句确认（写明是哪一笔） */
export function removeConfirmText(row: Partial<CryoFlowRecord>): string {
  return `删掉这一笔「${flowKindText(row)} ${flowDeltaText(row)}」？删后剩余会重新计算。`
}

/** 改一笔时支数输入框的初值：库里是带符号的，取走存负数 → 填绝对值 */
export function editableQtyText(row: Partial<CryoFlowRecord>): string {
  const delta = row.delta
  return delta === null || delta === undefined ? '' : String(Math.abs(delta))
}

/** 日期 → `yyyy-MM-dd HH:mm:ss`（发生时间默认「现在」） */
export function formatDateTime(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/** 日期 → `yyyy-MM-dd`（转移时间默认「今天」） */
export function formatDay(date: Date): string {
  return formatDateTime(date).slice(0, 10)
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
