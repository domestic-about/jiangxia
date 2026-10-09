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
import { normalizePassage } from './layout'

/** 类器官收样表单值（内外部共用一份；外部只渲染其中的四项） */
export interface OrganoidFormValue {
  /** 选中的单位 id（手填单位时为 null） */
  sourceUnitId: string | number | null
  /** 来源单位名称快照（显示 + 提交都要） */
  sourceUnitName: string
  /** 种属（CR-20261009-18：甲方 2026-10-09 要加的一项；必填，字典常用值或手填） */
  species: string
  organoidType: string
  /** 代数（CR-20260924-10：甲方 2026-09-24 第 18 行要加的一项；选填，形如 P3） */
  passage: string
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
    species: '',
    organoidType: '',
    passage: '',
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
    species: str(detail.species),
    organoidType: str(detail.organoidType),
    passage: str(detail.passage),
    receiveDate: str(detail.receiveDate),
    internalNo: str(detail.internalNo),
    processTime: str(detail.processTime),
    hasViabilityReport: str(detail.hasViabilityReport),
    operatorName: str(detail.operatorName),
    remark: str(detail.remark),
  }
}

/**
 * 内部**新增**提交体（`POST /mp/int/sample`）。
 *
 * ★ `sampleKind` 只在新增时带：它是这条记录的**类目身份**
 * （`FIELD:t_lqg_sample.sample_kind`，`tissue` 组织样本 / `organoid` 类器官收样记录），
 * 由入口决定、创建时写死，不是修改路径的字段（issue #105）。
 * 修改走 {@link internalOrganoidPatch}（不带 `sampleKind`）—— 否则一条**组织样本**被
 * 类器官表单改了别的字段，保存后会被静默改判成类器官。
 */
export function internalOrganoidPayload(form: OrganoidFormValue): Record<string, unknown> {
  return {
    sampleKind: 'organoid',
    ...internalOrganoidPatch(form),
  }
}

/**
 * 内部**修改**（`PUT /mp/int/sample`）的补丁主体 —— 与新增同一份字段，**去掉 `sampleKind`**。
 *
 * ★ 为什么单独一个函数、而不是给 {@link internalOrganoidPayload} 加个布尔开关：
 *   「修改模式不许发类目身份」是**口径**（issue #105），加开关时谁忘了传就又会静默改判；
 *   两个入口各一个具名函数，调用点一眼看得出走的是哪条口径。
 *   后端 `PUT /mp/int/sample` 同口径兜底：`sampleKind` 与库里不一致 → 400（两头都收）。
 */
export function internalOrganoidPatch(form: OrganoidFormValue): Record<string, unknown> {
  return {
    sourceUnitId: form.sourceUnitId,
    sourceUnitName: form.sourceUnitName.trim(),
    species: form.species.trim(),
    organoidType: form.organoidType.trim(),
    // 代数选填：清空也要发空串（补丁语义「带了空值 = 清空」），不能省略这个键
    passage: normalizePassage(form.passage),
    receiveDate: form.receiveDate || null,
    internalNo: form.internalNo.trim(),
    processTime: form.processTime ? form.processTime.replace('T', ' ') : null,
    hasViabilityReport: form.hasViabilityReport,
    operatorName: form.operatorName.trim(),
  }
}

/** 外部提交体（只有五项；夹带的内部字段后端一律不落库，这里也一个都不带） */
export function externalOrganoidPayload(form: OrganoidFormValue): Record<string, unknown> {
  return {
    sourceUnitId: form.sourceUnitId,
    sourceUnitName: form.sourceUnitName.trim(),
    species: form.species.trim(),
    organoidType: form.organoidType.trim(),
    passage: normalizePassage(form.passage),
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
