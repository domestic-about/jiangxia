// 类器官收样填写页的接口层与表单值（SAMPLE-MP-002）。
//
// 契约：`doc/api-contract.md` 第 49 / 53 行。
//   内部：`POST /mp/int/sample`（`sampleKind=organoid`，直接 valid）、`PUT /mp/int/sample`（补丁）
//   外部：`POST /mp/ext/organoid`、`PUT /mp/ext/organoid/{id}`（`ExtOrganoidSubmitBo`，→ pending）
//
// ★ 内部 PUT 走 **PUT**（不是再 POST 一次）：POST 会撞内部编号的唯一性
//   （ticket 的 counterfeit 第 3 条）。
// ★ 来源单位：选了列表项要**同时**带 `sourceUnitId` 与 `sourceUnitName`
//   —— 后端按 id 取单位名快照（`SampleService.resolveUnitName`），手填的只有名字。
//   两个都带上，快照那一列永远是名字，不会空（ticket 的 counterfeit 第 2 条）。
import type { SampleDetail } from '@/api/sample'
import { http } from '@/utils/request'

/** 类器官收样表单值（内外部共用一份；外部只渲染其中的三项） */
export interface OrganoidFormValue {
  /** 选中的单位 id（手填单位时为 null） */
  sourceUnitId: string | number | null
  /** 来源单位名称快照（显示 + 提交都要） */
  sourceUnitName: string
  organoidType: string
  receiveDate: string
  internalNo: string
  processTime: string
  hasViabilityReport: string
  operatorName: string
  remark: string
}

/** 空表单（新增时用） */
export function emptyOrganoidForm(): OrganoidFormValue {
  return {
    sourceUnitId: null,
    sourceUnitName: '',
    organoidType: '',
    receiveDate: '',
    internalNo: '',
    processTime: '',
    hasViabilityReport: '',
    operatorName: '',
    remark: '',
  }
}

function str(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

/** 详情 → 表单值（详情没给的键回落空串，绝不 undefined，免得 v-model 变 uncontrolled） */
export function toOrganoidFormValue(detail: Partial<SampleDetail> | null | undefined): OrganoidFormValue {
  const base = emptyOrganoidForm()
  if (!detail) {
    return base
  }
  return {
    sourceUnitId: (detail as { sourceUnitId?: string | number | null }).sourceUnitId ?? null,
    sourceUnitName: str(detail.sourceUnitName),
    organoidType: str(detail.organoidType),
    receiveDate: str(detail.receiveDate),
    internalNo: str(detail.internalNo),
    processTime: str(detail.processTime),
    hasViabilityReport: str(detail.hasViabilityReport),
    operatorName: str(detail.operatorName),
    remark: str(detail.remark),
  }
}

/** 内部提交体（POST 新增 / PUT 修改同一份；PUT 时另加 id） */
export function internalOrganoidPayload(form: OrganoidFormValue): Record<string, unknown> {
  return {
    sampleKind: 'organoid',
    sourceUnitId: form.sourceUnitId,
    sourceUnitName: form.sourceUnitName.trim(),
    organoidType: form.organoidType.trim(),
    receiveDate: form.receiveDate || null,
    internalNo: form.internalNo.trim(),
    processTime: form.processTime ? form.processTime.replace('T', ' ') : null,
    hasViabilityReport: form.hasViabilityReport,
    operatorName: form.operatorName.trim(),
  }
}

/** 外部提交体（只有三项；夹带的内部字段后端一律不落库，这里也一个都不带） */
export function externalOrganoidPayload(form: OrganoidFormValue): Record<string, unknown> {
  return {
    sourceUnitId: form.sourceUnitId,
    sourceUnitName: form.sourceUnitName.trim(),
    organoidType: form.organoidType.trim(),
    remark: form.remark.trim(),
  }
}

// ── 外部（小程序 · 合作单位）────────────────────────────────────────────────

export function createExtOrganoid(payload: Record<string, unknown>) {
  return http.post<string | number>('/mp/ext/organoid', payload)
}

export function updateExtOrganoid(id: string | number, payload: Record<string, unknown>) {
  return http.put<void>(`/mp/ext/organoid/${id}`, payload)
}

/** 类器官类型的联想词：字典接口 `/mp/dict/hints?type=organoid`（拉不到不挡填写） */
export function fetchOrganoidHints() {
  return http.get<string[]>('/mp/dict/hints', { type: 'organoid' }, { silent: true })
}
