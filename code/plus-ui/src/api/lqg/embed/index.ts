import request from '@/utils/request';
import { AxiosPromise } from 'axios';

// ============================================================================
// EMBED 域 · 工作台「石蜡包埋」页（EMBED-WEB-001）
//
// 后端：code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/.../embed/controller/EmbedController.java
//       + .../embed/export/EmbedExportService.java（POST /lqg/embed/export）
// 契约：doc/api-contract.md「EMBED / CRYO」一节的 /lqg/embed/* 四行
// 权限串：lqg:embed:{list,query,add,edit,remove,export,verify}
//         —— 菜单 5310-5317（V202609231110），一起授给 101（lqg_admin）与 102（lqg_internal）。
//
// ★ 三个「语义最容易做反」的点，前端这一层必须跟着后端一起记：
//   1) 行里的 internalNo / submitNo / sampleVerifyStatus 都是**所挂样本**的（读时带出），
//      本表只有 sampleId —— 列表的「样本编号」列就是 internalNo。
//   2) updateTime 为 null ⇔ 这一行**从没被改过**（SAMPLE-MP-001 起的跨票语义）。
//      抽屉顶部的「最后修改」小字要显式处理，别渲染成「0」或「Invalid Date」。
//   3) 核验抽屉的「判为有效并保存」在**所挂样本还没核验有效**时要置灰并写明原因
//      （读行里的 sampleVerifyStatus），不是点了再等后端报错（ticket §0 口径复述 4）。
// ============================================================================

/** 染色值（固定顺序，与后端 StainRules.ORDER / 字典 lqg_stain_type 一致） */
export const STAIN_ORDER = ['HE', 'IF', 'IHC', 'OTHER', 'NONE'] as const;

/** marker 表达值（字典 lqg_marker_expr） */
export const MARKER_EXPR_ORDER = ['negative', 'weak', 'strong'] as const;

/** marker 一行（GET /lqg/embed/{id} 的 markers[]） */
export interface EmbedMarkerVO {
  markerName?: string | null;
  expression?: string | null;
  sort?: number | null;
}

/** 石蜡包埋行（GET /lqg/embed/list 的 rows[]） */
export interface EmbedVO {
  id: string | number;
  sampleId: string | number;
  /** ★ 所挂样本的内部编号（模板的「样本编号」列；待核验的外部样本为空） */
  internalNo?: string | null;
  /** ★ 所挂样本的送检单号 */
  submitNo?: string | null;
  /** ★ 所挂样本的核验状态 —— 核验抽屉据此置灰「判为有效」 */
  sampleVerifyStatus?: string | null;
  sourceUnitName?: string | null;
  /** 外部送样在核验前为空 */
  paraffinBlockNo?: string | null;
  sampleType?: string | null;
  organoidSourceType?: string | null;
  tissueReceiveTime?: string | null;
  tissueProcessTime?: string | null;
  agaroseEmbedTime?: string | null;
  embedBy?: string | null;
  dehydrateTime?: string | null;
  agaroseSendTime?: string | null;
  paraffinEmbedTime?: string | null;
  sectionTime?: string | null;
  sectioned?: boolean;
  /** 对外是数组（库里是逗号串），顺序 = 固定顺序 */
  stainTypes?: string[] | null;
  stainOther?: string | null;
  markers?: EmbedMarkerVO[] | null;
  operatorName?: string | null;
  remark?: string | null;
  /** internal / external（提交当时的快照，不按当前角色现算） */
  submitSource?: 'internal' | 'external' | string | null;
  submitterId?: string | number | null;
  verifyStatus?: 'pending' | 'valid' | 'invalid' | string | null;
  verifyBy?: string | number | null;
  verifyTime?: string | null;
  invalidReason?: string | null;
  createTime?: string | null;
  /** ★ null = 从未修改（不是「等于创建时间」） */
  updateTime?: string | null;
  handlerName?: string | null;
  updateByName?: string | null;
  mine?: boolean;
  /** 普通保存能不能改：只有 valid 的记录可改（pending / invalid 只走核验接口） */
  editable?: boolean;
}

/** 列表筛选（字段名与 doc/api-contract.md 第 61 行的参数逐字一致） */
export interface EmbedQuery {
  pageNum?: number;
  pageSize?: number;
  /** 石蜡块编号（模糊） */
  paraffinBlockNo?: string | null;
  /** 内部编号（所挂样本的，等值） */
  internalNo?: string | null;
  /** 所挂样本 id（从样本总表带 sampleId 跳入时用） */
  sampleId?: string | number | null;
  /** 染色（数组包含，精确匹配：HE / IF / IHC / OTHER / NONE 之一） */
  stain?: string | null;
  /** 切片时间区间（两端都含）yyyy-MM-dd */
  sectionTimeBegin?: string | null;
  sectionTimeEnd?: string | null;
  verifyStatus?: string | null;
  /** internal / external */
  submitSource?: string | null;
}

/** 新增 / 修改入参（POST / PUT /lqg/embed） */
export interface EmbedForm {
  id?: string | number | null;
  sampleId?: string | number | null;
  paraffinBlockNo?: string | null;
  sampleType?: string | null;
  organoidSourceType?: string | null;
  tissueReceiveTime?: string | null;
  tissueProcessTime?: string | null;
  agaroseEmbedTime?: string | null;
  embedBy?: string | null;
  dehydrateTime?: string | null;
  agaroseSendTime?: string | null;
  paraffinEmbedTime?: string | null;
  sectionTime?: string | null;
  /** 染色多选；NONE 与其余互斥（唯一入口 toggleStain） */
  stainTypes?: string[] | null;
  stainOther?: string | null;
  /** 传了就整组替换（后端 marker 的语义） */
  markers?: EmbedMarkerVO[] | null;
  operatorName?: string | null;
  remark?: string | null;
}

/** 核验入参（PUT /lqg/embed/{id}/verify） */
export interface EmbedVerifyForm {
  action: 'valid' | 'invalid';
  /** action=valid 必填且全库唯一 */
  paraffinBlockNo?: string | null;
  /** action=invalid 必填 */
  reason?: string | null;
}

/** 列表（分页；待核验置顶，其余按创建时间倒序） */
export function listEmbeds(query: EmbedQuery): AxiosPromise<EmbedVO[]> {
  return request({
    url: '/lqg/embed/list',
    method: 'get',
    params: query
  });
}

/** 详情（带 internalNo / submitNo / sampleVerifyStatus / markers / stainTypes 数组） */
export function getEmbed(id: string | number): AxiosPromise<EmbedVO> {
  return request({
    url: '/lqg/embed/' + id,
    method: 'get'
  });
}

/** 内部新增（石蜡块编号必填、直接 valid） */
export function addEmbed(data: EmbedForm) {
  return request({
    url: '/lqg/embed',
    method: 'post',
    data
  });
}

/** 修改 / 补填（patch 语义；待核验 / 无效的记录后端 400） */
export function updateEmbed(data: EmbedForm) {
  return request({
    url: '/lqg/embed',
    method: 'put',
    data
  });
}

/** 软删 */
export function delEmbed(ids: string | number | Array<string | number>) {
  return request({
    url: '/lqg/embed/' + ids,
    method: 'delete'
  });
}

/** 核验 / 改判：判有效要石蜡块编号（且所挂样本已核验有效）；判无效要原因 */
export function verifyEmbed(id: string | number, data: EmbedVerifyForm) {
  return request({
    url: '/lqg/embed/' + id + '/verify',
    method: 'put',
    data
  });
}

/**
 * 按当前筛选导出「石蜡包埋送样记录」xlsx（POST /lqg/embed/export）。
 *
 * ★ 筛选走 **query 参数**（与后端 controller 的 `EmbedQueryBo query` 绑定一致），
 * 不是 JSON body —— 与 doc/verify/api.sh 的 `POST '/lqg/embed/export?internalNo=…'` 同一形状。
 * 用 responseType: 'blob' 拿文件流，再由页面触发下载。
 */
export function exportEmbeds(query: EmbedQuery): AxiosPromise<Blob> {
  return request({
    url: '/lqg/embed/export',
    method: 'post',
    params: query,
    responseType: 'blob'
  });
}

// ── 纯展示小工具（页面上配套的文案都走 lqg.embed.*，这里只出「有没有」） ──

/** 这一行是不是「从没改过」：updateTime 为 null（空串 / undefined 也算） */
export function neverModified(row: Pick<EmbedVO, 'updateTime'>): boolean {
  return row?.updateTime === null || row?.updateTime === undefined || row?.updateTime === '';
}

/**
 * 所挂样本是否已核验有效 —— 核验抽屉「判为有效并保存」的前置条件（ticket §0 口径复述 4）。
 * 未知（行里没有这个键）时按 false 处理：宁可置灰并说明，也不放一个点了才报错的按钮。
 */
export function sampleVerified(row: Pick<EmbedVO, 'sampleVerifyStatus'>): boolean {
  return row?.sampleVerifyStatus === 'valid';
}

/**
 * 这一行是不是「外部提交、还没生效」的送样（待核验 / 无效）：
 * 打开它 = 核验抽屉，而不是编辑抽屉。
 */
export function isExternalPending(row: Pick<EmbedVO, 'submitSource' | 'verifyStatus'>): boolean {
  const external = row?.submitSource === 'external';
  const status = row?.verifyStatus;
  return external && (status === 'pending' || status === 'invalid');
}

/** 这一行能不能用普通保存改（后端 editable；缺键时按 verifyStatus=valid 兜底） */
export function isEditable(row: Pick<EmbedVO, 'editable' | 'verifyStatus'>): boolean {
  return row?.editable === true || (row?.editable === undefined && row?.verifyStatus === 'valid');
}
