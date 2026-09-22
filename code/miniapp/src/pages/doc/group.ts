// 文档页签的分组规则（DOC-MP-001 · UI:mp.doc.list 方案 A）。
//
// 纯函数，**不碰 uni / 不碰网络**：接受清单接口的扁平行，产出分组数组。
// 规则（票面 §0 口径复述 2 + accept 1）：
//   1. 组内**固定顺序**：样本质控表 → 类器官质控表 → 类器官质量评分表
//      （不是按完成时间排 —— 甲方要的是「固定顺序 + 按时间找组」）；
//   2. 组间按**组内最新完成时间倒序**（这就是甲方说的「按时间找」）；
//   3. `showMerge`：该组**已完成 ≥ 2 份**才出现合并入口（恰好两份也算）；
//   4. `merged` 行**不是一份文档**（UI:mp.doc.list：组内每份已完成文档一行，最多三份）——
//      它既不进 `docs` / `docKinds`，也不计入 `showMerge` 的份数，更不参与「最新完成时间」。
//      接口若混进 merged 行（外部清单本就会给），这里必须挡住（fixture 第四例就是这个病灶）。
import type { DocListRow } from '@/api/doc'

/** 组内固定顺序（也是「一份文档」的合法取值集合；merged 不在其中） */
export const DOC_KIND_ORDER = ['sample_qc', 'organoid_qc', 'organoid_score'] as const

/** 文档名（后端不再给中文名，前端按 docKind 显示） */
export const DOC_KIND_LABEL: Record<string, string> = {
  sample_qc: '样本质控表',
  organoid_qc: '类器官质控表',
  organoid_score: '类器官质量评分表',
  merged: '质控文档合并件',
}

export function docKindLabel(docKind?: string | null): string {
  if (!docKind) {
    return ''
  }
  return DOC_KIND_LABEL[docKind] ?? docKind
}

/** 一个样本一组 */
export interface DocGroup {
  /** 统一成字符串：接口里小 id 是数字、大 id 是字符串（坑 #5），分组与 key 都要稳 */
  sampleId: string
  /** 组标题：接口按身份给（内部 = 内部编号；外部 = 送检单号） */
  title: string
  /** 组副标题：接口按身份给（内部 = 来源单位；外部 = 供体姓名掩码） */
  subtitle: string
  /** 组内固定顺序的文档（已剔掉 merged 与不认识的种类） */
  docs: DocListRow[]
  /** 与 docs 同序的种类序列（fixture 断言的可观测值） */
  docKinds: string[]
  /** 组内最新完成时间（原始字符串，接口给什么就是什么） */
  latest: string
  /** 该组已完成 ≥ 2 份 → 出「合并预览 / 合并下载」 */
  showMerge: boolean
}

const KIND_RANK: Record<string, number> = DOC_KIND_ORDER.reduce(
  (acc, kind, index) => {
    acc[kind] = index
    return acc
  },
  {} as Record<string, number>,
)

/** `yyyy-MM-dd HH:mm:ss`（接口的固定格式）→ 毫秒；认不出来给 0（排最后） */
function timeValue(text?: string | null): number {
  if (!text) {
    return 0
  }
  const parsed = Date.parse(String(text).replace(' ', 'T'))
  return Number.isNaN(parsed) ? 0 : parsed
}

function text(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

/**
 * 扁平行 → 分组数组（组间最新完成时间倒序；同时间按 sampleId 升序，保证顺序稳定可测）。
 */
export function groupDocs(rows: DocListRow[] | null | undefined): DocGroup[] {
  const buckets = new Map<string, DocListRow[]>()
  for (const row of rows ?? []) {
    const kind = text(row?.docKind)
    // merged（以及任何不认识的种类）不算「一份文档」：既不进组，也不参与合并判断
    if (KIND_RANK[kind] === undefined) {
      continue
    }
    const key = text(row?.sampleId)
    const list = buckets.get(key)
    if (list) {
      list.push(row)
    }
    else {
      buckets.set(key, [row])
    }
  }

  const groups: DocGroup[] = []
  buckets.forEach((list, sampleId) => {
    // 组内固定顺序（不是按完成时间）
    const docs = [...list].sort((a, b) => KIND_RANK[text(a.docKind)] - KIND_RANK[text(b.docKind)])
    let latest = ''
    let latestAt = -1
    for (const doc of docs) {
      const at = timeValue(doc.publishedTime)
      if (at > latestAt) {
        latestAt = at
        latest = text(doc.publishedTime)
      }
    }
    groups.push({
      sampleId,
      title: text(docs[0]?.title),
      subtitle: text(docs[0]?.subtitle),
      docs,
      docKinds: docs.map(doc => text(doc.docKind)),
      latest,
      showMerge: docs.length >= 2,
    })
  })

  // 组间：组内最新完成时间倒序（同时间再按 sampleId 升序，避免顺序抖动）
  groups.sort((a, b) => {
    const diff = timeValue(b.latest) - timeValue(a.latest)
    return diff !== 0 ? diff : a.sampleId.localeCompare(b.sampleId)
  })
  return groups
}
