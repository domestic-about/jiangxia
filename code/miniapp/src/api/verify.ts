// 内部人员在小程序里核验 + 首页「待处理」的接口层。
//
// 来源：甲方 2026-09-24 测试问题记录表第 17 / 20 行，Kevin 定这次做：
// 内部首页显示待核验、冻存超期；核验在小程序和网页工作台都能做。
//
// 接口全是现有的（与工作台同一套，后端规则是权威，这里不加新端点）：
//   GET /lqg/home/todo                                     首页待办数（内部角色闸，外部 403；工作台首页同一次现算）
//   GET /mp/int/sample/list?verifyStatus=pending&sampleKind=  待核验的样本记录 / 类器官收样
//   GET /mp/int/embed/list?verifyStatus=pending              待核验的石蜡包埋送样
//   GET /mp/int/sample/{id}、GET /mp/int/embed/{id}          详情（核验页）
//   PUT /lqg/sample/{id}/verify   权限串 lqg:sample:verify（101 / 102 都有）
//   PUT /lqg/embed/{id}/verify    权限串 lqg:embed:verify（同上）
// 小程序登录的内部人员（102 lqg_internal）调这几个都通；外部角色一律 403（实测见 DONE.md）。
//
// ★ 两个核验请求都走 `silent`：后端的业务拒绝（内部编号已存在、石蜡块编号已存在、所挂样本还未核验有效……）
//   要原样、完整地给人看并停在页面上改，toast 两秒就没了、长句还会被截断 —— 页面自己弹框。
// ★ 分页接口的形状是 `{code,msg,rows,total}`（没有 data 键）→ 带 `raw: true`。
import type { EmbedDetail } from '@/api/embed'
import type { SampleDetail } from '@/api/sample'
import { PAGE_SIZE } from '@/utils/paging'
import { http } from '@/utils/request'

/** 样本两张表的一行（`/mp/int/sample/list` 的行） */
export interface PendingSampleRow {
  id: string | number
  submitNo?: string | null
  sampleKind?: string | null
  sourceUnitName?: string | null
  submitterName?: string | null
  donorName?: string | null
  tissueType?: string | null
  organoidType?: string | null
  /** 代数（类器官；后端加列之前没有这个键） */
  passage?: string | null
  createTime?: string | null
  updateTime?: string | null
}

/** 石蜡包埋送样的一行（`/mp/int/embed/list` 的行） */
export interface PendingEmbedRow {
  id: string | number
  submitNo?: string | null
  sourceUnitName?: string | null
  sampleType?: string | null
  organoidSourceType?: string | null
  sampleVerifyStatus?: string | null
  /** 经手人：待核验的送样只有合作单位自己改得动，所以它就是提交人 */
  handlerName?: string | null
  createTime?: string | null
  updateTime?: string | null
}

/** `GET /lqg/home/todo` 的返回（只列本端读的三个；其余键原样留着不读） */
export interface HomeTodo {
  pendingSamples?: number | null
  pendingEmbeds?: number | null
  cryoOverdue?: number | null
  [key: string]: unknown
}

/** 首页待办数（失败不弹 toast：首页那一块自己显示「点一下重试」，不挡下面的填写入口） */
export function fetchHomeTodo() {
  return http.get<HomeTodo>('/lqg/home/todo', undefined, { silent: true })
}

/** 待核验的样本记录 / 类器官收样（一页） */
export function fetchPendingSamples(sampleKind: 'tissue' | 'organoid', pageNum = 1, pageSize = PAGE_SIZE) {
  return http.get<{ rows: PendingSampleRow[], total: number }>(
    '/mp/int/sample/list',
    { sampleKind, verifyStatus: 'pending', pageNum, pageSize },
    { raw: true },
  )
}

/** 待核验的石蜡包埋送样（一页） */
export function fetchPendingEmbeds(pageNum = 1, pageSize = PAGE_SIZE) {
  return http.get<{ rows: PendingEmbedRow[], total: number }>(
    '/mp/int/embed/list',
    { verifyStatus: 'pending', pageNum, pageSize },
    { raw: true },
  )
}

/** 核验页的样本详情（内部详情：带收样段、来源单位 id、提交人） */
export function fetchVerifySample(id: string | number) {
  return http.get<SampleDetail & Record<string, unknown>>(`/mp/int/sample/${id}`)
}

/** 核验页的石蜡包埋详情（带所挂样本的送检单号、内部编号与核验状态） */
export function fetchVerifyEmbed(id: string | number) {
  return http.get<EmbedDetail & Record<string, unknown>>(`/mp/int/embed/${id}`)
}

/** 样本核验（请求体由 `pages/verify/rules.ts#sampleVerifyBody` 拼） */
export function verifySample(id: string | number, body: Record<string, unknown>) {
  return http.put<void>(`/lqg/sample/${id}/verify`, body, { silent: true })
}

/** 石蜡包埋送样核验（请求体由 `pages/verify/rules.ts#embedVerifyBody` 拼） */
export function verifyEmbed(id: string | number, body: Record<string, unknown>) {
  return http.put<void>(`/lqg/embed/${id}/verify`, body, { silent: true })
}

/**
 * 核验成功后广播的事件名：待核验列表页、内部管理表格页听到后重新取数
 * （核验页可能是从这两处任一处进来的，返回时那一页要看到这条记录已经不是待核验）。
 * 首页的数字在首页每次显示时重取，不靠这个事件。
 */
export const VERIFIED_EVENT = 'lqg:verified'
