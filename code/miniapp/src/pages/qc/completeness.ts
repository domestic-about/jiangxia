/**
 * 「完成并同步」之前的完整性检查（Kevin 2026-10-06 定：**不硬拦**，列出空着的项，问一句「仍要同步吗」）。
 * 来源：工作台 UX 测试设计决策 1 —— 一份几乎空白的文档也能同步给送检方（界面写着图片 1-3 张，实际 0 张也行）。
 * 两端同一份清单：工作台 views/lqg/qc/editor/completeness.ts ↔ 小程序 pages/qc/completeness.ts（改一处要改另一处）。
 */
type QcDocTypeKey = 'sample-qc' | 'organoid-qc' | 'score'
type Loose = Record<string, unknown> | null | undefined

const blank = (v: unknown) => v === null || v === undefined || String(v).trim() === ''
const noImage = (doc: Loose, slot: string) => {
  const images = (doc?.images ?? {}) as Record<string, unknown[] | undefined>
  return !images[slot] || images[slot]!.length === 0
}

const FIELDS: Record<QcDocTypeKey, Array<[string, (doc: Loose) => boolean]>> = {
  'sample-qc': [
    ['患者编号', d => blank(d?.patientNo)],
    ['取样部位', d => blank(d?.samplingSite)],
    ['取样方式', d => blank(d?.samplingMethod)],
    ['临床诊断 / 既往治疗', d => blank(d?.clinicalDiagnosis)],
    // 摘掉附件时这一栏是 0（api/qc 的约定），也算空
    ['细胞活率测定（附件）', d => blank(d?.viabilityOssId) || String(d?.viabilityOssId) === '0'],
    ['收样原始情况的图片', d => noImage(d, 'orig')],
    ['样本观察情况的图片', d => noImage(d, 'observe')],
    ['样本预处理情况的图片', d => noImage(d, 'pretreat')],
  ],
  'organoid-qc': [
    ['形成类器官时间', d => blank(d?.formedTime)],
    ['生长状态', d => blank(d?.growthState)],
    ['类器官观察的图片', d => noImage(d, 'organoid_observe')],
  ],
  'score': [
    ['培养前样本评分', d => blank(d?.preCultureLevel)],
    ['培养天数', d => blank(d?.cultureDaysLevel)],
    ['类器官数量', d => blank(d?.organoidCountLevel)],
    ['类器官直径', d => blank(d?.diameterLevel)],
  ],
}

/** 这份文档里还空着的项（按模板顺序）；全填了返回空数组 */
export function qcEmptyItems(type: QcDocTypeKey, doc: Loose): string[] {
  return (FIELDS[type] ?? []).filter(([, isEmpty]) => isEmpty(doc)).map(([label]) => label)
}
