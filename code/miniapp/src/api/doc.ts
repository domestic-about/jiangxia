// 文档页签的接口层（DOC-MP-001）。
//
// 契约：`doc/api-contract.md` 的 QC / DOC 一节。
//   内部：`GET /mp/int/doc/list`（全部样本里「已完成且内部版渲染成功」的；行带 internalNo / sourceUnitName）
//   外部：`GET /mp/ext/doc/list`（AUTH-EXT-003；可见样本 ∩ 已完成 ∩ 外部版渲染成功；行带 submitNo / donorNameMasked）
//
// ★★ 两个接口的组标题键**不是同一组**（实测，契约第 86/87 行只把外部那 6 键写死了）：
//     内部 `/mp/int/doc/list`：`title`（内部编号）/ `subtitle`（来源单位）；
//     外部 `/mp/ext/doc/list`：`submitNo`（送检单号）/ `donorNameMasked`（供体姓名掩码）
//      —— `ExtDocVo` 的字段集合被 AUTH-EXT-003 的形状契约钉成**恰好 6 键**，加不了 title/subtitle。
//   → 两个接口的**内容**都是「后端按身份给、前端不自己拼」，只是键名不同；
//     归一化（`DocListRow` 上的 `title/subtitle ?? submitNo/donorNameMasked`）放在 `group.ts` 一处，
//     前端**绝不**自己算内部编号或掩码（accept 2 的禁字 grep 点的就是这件事）。
// ★ 分页响应是 `{code,msg,rows,total}`（没有 `data` 键）→ 一律带 `raw: true`（SAMPLE-MP-001 坑 3）。
// ★ `docKind` 是**下划线**那四个（sample_qc / organoid_qc / organoid_score / merged）——
//   不是质控草稿侧的连字符三个（DOC-PUBLISH-001 连坑两次）。
// ★ 时间范围两种写法都收：`yyyy-MM-dd`（止端按当天 23:59:59 收口）或 `yyyy-MM-dd HH:mm:ss`。
import { http } from '@/utils/request'

/** 清单行（内部 / 外部两个接口共用这一组键） */
export interface DocListRow {
  sampleId?: string | number | null
  /** 组标题：后端按身份给（内部 = 内部编号；外部 = 送检单号） */
  title?: string | null
  /** 组副标题：后端按身份给（内部 = 来源单位；外部 = 供体姓名掩码） */
  subtitle?: string | null
  docKind?: string | null
  publishedTime?: string | null
  /** 评分表合计分：**只在评分表那一行上有**（别的行连键都不出） */
  totalScore?: number | null
  /** 外部接口的组标题：送检单号（无 title 时用它） */
  submitNo?: string | null
  /** 外部接口的组副标题：供体姓名掩码（无 subtitle 时用它） */
  donorNameMasked?: string | null
  /** 内部接口才有（前端不读，只用 title / subtitle） */
  internalNo?: string | null
  sourceUnitName?: string | null
}

export interface DocListQuery {
  sampleId?: string | number
  docKind?: string
  publishedBegin?: string
  publishedEnd?: string
  pageSize?: number
}

/** 内部清单：全部样本里已完成且内部版渲染成功的文档 */
export function fetchIntDocList(params: DocListQuery = {}) {
  return http.get<{ rows: DocListRow[], total: number }>(
    '/mp/int/doc/list',
    {
      sampleId: params.sampleId,
      docKind: params.docKind,
      publishedBegin: params.publishedBegin,
      publishedEnd: params.publishedEnd,
      pageSize: params.pageSize ?? 100,
    },
    { raw: true },
  )
}

/** 外部清单：可见集合内已完成且**外部版**渲染成功的文档（AUTH-EXT-003） */
export function fetchExtDocList(params: DocListQuery = {}) {
  return http.get<{ rows: DocListRow[], total: number }>(
    '/mp/ext/doc/list',
    {
      sampleId: params.sampleId,
      docKind: params.docKind,
      publishedBegin: params.publishedBegin,
      publishedEnd: params.publishedEnd,
      pageSize: params.pageSize ?? 100,
    },
    { raw: true },
  )
}

/**
 * 一个样本的文档（外部样本详情第三段「质控文档」，CR-20260917-04 的切换条也是这个口）。
 *
 * ★ 用**清单**接口的 `sampleId` 筛选，不要逐个调预览接口判存在：
 *   清单的「不可见样本」给的是空列表（不泄露），预览给的是 404（与「没这份文档」不可区分）。
 */
export async function fetchSampleDocs(sampleId: string | number, identity: 'internal' | 'external' | null) {
  const fetcher = identity === 'internal' ? fetchIntDocList : fetchExtDocList
  const res = await fetcher({ sampleId, pageSize: 100 })
  return (res.rows ?? []) as DocListRow[]
}
