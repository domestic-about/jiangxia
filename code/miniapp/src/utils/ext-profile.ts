// 外部档案的核验状态与「单位 · 组别」展示（UI:mp.home / UI:mp.me）。
// 只读 /mp/me 返回的值，不在这里做任何推断；修改页在 AUTH-GROUP-001。
import type { MpExtProfile } from '@/types/mp'
import type { TagTone } from '@/types/ui'

/** 字典 lqg_verify_status 的取值（ext.bindStatus） */
export type BindStatus = 'unbound' | 'pending' | 'verified' | 'rejected'

const STATUS_TEXT: Record<BindStatus, string> = {
  unbound: '未绑定',
  pending: '待核验',
  verified: '已核验',
  rejected: '已驳回',
}

/** bindStatus → 徽标修饰类（落地规范 §5.6：pending 琥珀 / valid 绿 / invalid 红） */
const STATUS_TONE: Record<BindStatus, TagTone> = {
  unbound: 'pending',
  pending: 'pending',
  verified: 'valid',
  rejected: 'invalid',
}

export function normalizeBindStatus(raw: unknown): BindStatus {
  if (raw === 'pending' || raw === 'verified' || raw === 'rejected') {
    return raw
  }
  return 'unbound'
}

export function bindStatusText(raw: unknown): string {
  return STATUS_TEXT[normalizeBindStatus(raw)]
}

export function bindStatusTone(raw: unknown): TagTone {
  return STATUS_TONE[normalizeBindStatus(raw)]
}

/** 单位名称：核验后的正式名称优先，其次是外部填的自由文本 */
export function unitDisplay(ext: MpExtProfile | null): string {
  if (!ext) {
    return ''
  }
  return ext.unitName || ext.unitNameInput || ''
}

/** 组别名称：同上 */
export function groupDisplay(ext: MpExtProfile | null): string {
  if (!ext) {
    return ''
  }
  return ext.groupName || ext.groupNameInput || ''
}

/** 「A 医院 · 肝胆外科组」；都没有时给一句人话，让用户知道去哪填（AUTH-GROUP-001） */
export function unitGroupDisplay(ext: MpExtProfile | null): string {
  const unit = unitDisplay(ext)
  const group = groupDisplay(ext)
  if (unit && group) {
    return `${unit} · ${group}`
  }
  if (unit) {
    return unit
  }
  if (group) {
    return group
  }
  return '还没填单位与组别'
}

/** 首页问候行下面那一行：单位 · 组别 · 核验状态 */
export function unitGroupWithStatus(ext: MpExtProfile | null): string {
  return `${unitGroupDisplay(ext)} · ${bindStatusText(ext?.bindStatus)}`
}

/** 外部档案未绑定时的提示（UI:mp.home：点击进单位与组别页） */
export const UNBOUND_HINT = '补充单位与组别，可与同组同事互看样本'
