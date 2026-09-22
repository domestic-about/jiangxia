// 文档页签的接口层（DOC-MP-001）。
//
// 契约：`doc/api-contract.md` 的 QC / DOC 一节。
//   内部：`GET /mp/int/doc/list`（全部样本里「已完成且内部版渲染成功」的；行带 internalNo / sourceUnitName）
//   外部：`GET /mp/ext/doc/list`（AUTH-EXT-003；可见样本 ∩ 已完成 ∩ 外部版渲染成功；行带 submitNo / donorNameMasked）
//
// ★ 两个接口的行形状**对齐到同一组键**：{sampleId, title, subtitle, docKind, publishedTime, totalScore?}
//   `title` / `subtitle` 由**后端按身份**给：
//     内部 = 内部编号 / 来源单位；外部 = 送检单号 / 供体姓名（掩码）。
//   → 前端只渲染 title / subtitle，**不自己拼内部编号**（accept 2 的禁字 grep 点的就是这件事）。
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
