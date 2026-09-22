import request from '@/utils/request';
import { AxiosPromise } from 'axios';

// ============================================================================
// SAMPLE 域 · 工作台样本总表（SAMPLE-WEB-001）
//
// 后端：code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/.../sample/controller/*.java
// 契约：doc/api-contract.md「SAMPLE」一节的 /lqg/sample/* 四行
// 权限串：lqg:sample:{list,query,add,edit,remove,verify}
//         —— 菜单 5210-5216（V202609221010），一起授给 101（lqg_admin）与 102（lqg_internal）。
//
// ★ 两个「语义最容易做反」的点，前端这一层必须跟着后端一起记：
//   1) submitSource 是样本行上**提交当时的快照**（submit_source 列），不是提交人现在的角色。
//      所以筛选把它当普通下拉值传上去即可，**前端不许按当前登录人重新推导**。
//   2) updateTime 为 null ⇔ 这一行**从没被改过**（SAMPLE-MP-001 的跨票行为变更）。
//      列表「最后修改」列与抽屉顶部小字都要显式处理 null，别当空字符串渲染成「0」或「Invalid Date」。
// ============================================================================

/** 样本行（GET /lqg/sample/list 的 rows[]） */
export interface SampleVO {
  id: string | number;
  submitNo: string;
  sampleKind: 'tissue' | 'organoid';
  submitSource: 'internal' | 'external';
  submitterId?: string | number | null;
  /** 提交人姓名（外部档案；内部录入的行是 null） */
  submitterName?: string | null;
  /** 提交人的组别（外部档案；内部录入的行是 null） */
  groupId?: string | number | null;
  groupName?: string | null;
  verifyStatus: 'pending' | 'valid' | 'invalid';
  verifyBy?: string | number | null;
  verifyTime?: string | null;
  invalidReason?: string | null;
  sourceUnitId?: string | number | null;
  sourceUnitName?: string | null;
  donorName?: string | null;
  gender?: string | null;
  age?: string | null;
  hospitalNo?: string | null;
  tissueType?: string | null;
  organoidType?: string | null;
  hasPathology?: string | null;
  receiveDate?: string | null;
  internalNo?: string | null;
  isFixed?: string | null;
  processTime?: string | null;
  hasQcSheet?: string | null;
  hasViabilityReport?: string | null;
  operatorName?: string | null;
  remark?: string | null;
  createTime?: string | null;
  /** ★ null = 从未修改（不是「等于创建时间」） */
  updateTime?: string | null;
  /** 最后修改人姓名；没改过时是创建人姓名，配合 updateTime 空不空用 */
  updateByName?: string | null;
  handlerName?: string | null;
  mine?: boolean;
  editable?: boolean;
}

/** 总表筛选（字段名与 doc/api-contract.md 第 45 行的参数逐字一致） */
export interface SampleQuery {
  pageNum?: number;
  pageSize?: number;
  sourceUnitId?: string | number | null;
  groupId?: string | number | null;
  sampleKind?: string | null;
  submitSource?: string | null;
  verifyStatus?: string | null;
  /** 收样日期区间（两端都含）yyyy-MM-dd */
  receiveDateBegin?: string | null;
  receiveDateEnd?: string | null;
  tissueType?: string | null;
  internalNo?: string | null;
  operatorName?: string | null;
  /** 加密列：只支持精确匹配（框旁边标「精确匹配」） */
  donorName?: string | null;
  hospitalNo?: string | null;
}

/** 新增 / 修改入参（POST / PUT /lqg/sample） */
export interface SampleForm {
  id?: string | number | null;
  sampleKind: string;
  sourceUnitId?: string | number | null;
  sourceUnitName?: string | null;
  donorName?: string | null;
  gender?: string | null;
  age?: string | null;
  hospitalNo?: string | null;
  tissueType?: string | null;
  organoidType?: string | null;
  hasPathology?: string | null;
  receiveDate?: string | null;
  internalNo?: string | null;
  isFixed?: string | null;
  processTime?: string | null;
  hasQcSheet?: string | null;
  hasViabilityReport?: string | null;
  operatorName?: string | null;
  remark?: string | null;
}

/** 核验入参（PUT /lqg/sample/{id}/verify） */
export interface SampleVerifyForm {
  action: 'valid' | 'invalid';
  /** action=valid 必填 */
  receiveDate?: string | null;
  /** action=valid 必填、全库唯一 */
  internalNo?: string | null;
  isFixed?: string | null;
  processTime?: string | null;
  hasQcSheet?: string | null;
  hasViabilityReport?: string | null;
  operatorName?: string | null;
  /** action=invalid 必填 */
  reason?: string | null;
}

/** 总表（分页；待核验置顶，其余按创建时间倒序） */
export function listSamples(query: SampleQuery): AxiosPromise<SampleVO[]> {
  return request({
    url: '/lqg/sample/list',
    method: 'get',
    params: query
  });
}

/** 详情（带 updateByName / updateTime / submitterName / groupName） */
export function getSample(id: string | number): AxiosPromise<SampleVO> {
  return request({
    url: '/lqg/sample/' + id,
    method: 'get'
  });
}

/** 内部新增（submit_source='internal'、verify_status='valid'） */
export function addSample(data: SampleForm) {
  return request({
    url: '/lqg/sample',
    method: 'post',
    data
  });
}

/** 内部修改（有效样本随时可改；submitNo / submitSource / submitterId 不可改） */
export function updateSample(data: SampleForm) {
  return request({
    url: '/lqg/sample',
    method: 'put',
    data
  });
}

/** 软删 */
export function delSample(ids: string | number | Array<string | number>) {
  return request({
    url: '/lqg/sample/' + ids,
    method: 'delete'
  });
}

/** 核验 / 改判：判有效要收样日期 + 内部编号；判无效要原因 */
export function verifySample(id: string | number, data: SampleVerifyForm) {
  return request({
    url: '/lqg/sample/' + id + '/verify',
    method: 'put',
    data
  });
}

// ── 纯展示小工具（页面上和它俩配套的文案都走 lqg.sample.*，这里只出「有没有」） ──

/** 这一行是不是「从没改过」：updateTime 为 null（空串 / undefined 也算） */
export function neverModified(row: Pick<SampleVO, 'updateTime'>): boolean {
  return row?.updateTime === null || row?.updateTime === undefined || row?.updateTime === '';
}

/** 三个「有无」按钮字段（Y / N / 未选）的取值是否合法 */
export function isFlagValue(value?: string | null): boolean {
  return value === 'Y' || value === 'N';
}
