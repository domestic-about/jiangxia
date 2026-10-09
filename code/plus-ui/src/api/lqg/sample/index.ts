import request from '@/utils/request';
import { AxiosPromise } from 'axios';

// ============================================================================
// SAMPLE 域 · 工作台两张样本表（SAMPLE-WEB-001；CR-20260924-10 起拆成「样本记录信息表」
//           「类器官收样记录」两页，接口没拆 —— 两页都调这一组 /lqg/sample/*，页面固定带 sampleKind）
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

/**
 * 切片染色提示（SAMPLE-HINT-001 / UI:admin.sample.list.hint）。
 *
 * ★ <b>读时计算</b>：后端 `SampleHintService` 拿本页样本 id 一条 GROUP BY 查出来再挂到行上，
 *   `t_lqg_sample` 上**没有** `block_count` / `has_section` 这类列（DDL 与 SSOT 逐列相符）。
 * ★ <b>列表每一行都有这个对象</b>，没有包埋记录的行是零值（`0 / false / []`），**不是 null**
 *   —— 渲染时不要写 `hint &&`，直接读（后端已保证）。
 * ★ 计数口径 = 已核验有效、未软删的石蜡块（与 `EmbedChildrenChecker` 同源）：
 *   待核验的外部送样、软删的块都不算；`NONE` 不算一种染色（后端已去掉）。
 */
export interface SampleHintVO {
  /** 已核验有效的石蜡块数 */
  blockCount: number;
  /** 有没有已切片（任一有效石蜡块的切片时间非空） */
  sectioned: boolean;
  /** 做过的染色种类并集（去 NONE、按字典顺序），如 `["HE","IHC"]` */
  stains: string[];
}

/**
 * 「石蜡包埋 / 冻存」关联数（样本两页的关联列；Kevin 2026-09-24 本机验收「四种表之间的关系看着有点乱」）。
 *
 * ★ 与 hint 同一个做法：后端读时算、整页一次查询，列表每一行都有（零值，不是 null）；详情与导出不带。
 * ★ 蜡块数不在这里 —— 沿用 `hint.blockCount`（已核验有效的石蜡块），这里只补两个数；
 *   每个数都对准一张目标页的筛选结果（点过去看到的条数一致）：
 *   - pendingEmbedCount = 石蜡包埋页按这个样本 + 「待核验」筛出来的条数（合作单位送来、还没核验的）；
 *   - cryoBatchCount    = 冻存管理页按这个样本筛出来的批次数（未删即生效，冻存没有核验状态）。
 */
export interface SampleRelationVO {
  pendingEmbedCount: number;
  cryoBatchCount: number;
}

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
  /** 种属（两类都必填；字典 lqg_species 的常用值或手填，CR-20261009-18） */
  species?: string | null;
  donorName?: string | null;
  gender?: string | null;
  age?: string | null;
  hospitalNo?: string | null;
  tissueType?: string | null;
  organoidType?: string | null;
  /** 代数（只有类器官收样记录有，形如 P3；CR-20260924-10） */
  passage?: string | null;
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
  /** ★ 切片染色提示（SAMPLE-HINT-001）：列表每行都有，没有包埋记录也是零值 */
  hint?: SampleHintVO | null;
  /** ★ 石蜡包埋 / 冻存关联数（列表每行都有，零值不是 null；详情不带） */
  relation?: SampleRelationVO | null;
}

/** 列表筛选（字段名与 doc/api-contract.md 第 45 行的参数逐字一致） */
export interface SampleQuery {
  pageNum?: number;
  pageSize?: number;
  sourceUnitId?: string | number | null;
  groupId?: string | number | null;
  /** ★ 两页都显式带（后端不带 = 两类都查） */
  sampleKind?: string | null;
  submitSource?: string | null;
  verifyStatus?: string | null;
  /** 收样日期区间（两端都含）yyyy-MM-dd */
  receiveDateBegin?: string | null;
  receiveDateEnd?: string | null;
  /** 种属（等值；__none__ = 还没填的，CR-20261009-18） */
  species?: string | null;
  tissueType?: string | null;
  /** 类器官类型（模糊；类器官收样记录页用，CR-20260924-10） */
  organoidType?: string | null;
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
  /** 种属（两类都必填；字典 lqg_species 的常用值或手填，CR-20261009-18） */
  species?: string | null;
  donorName?: string | null;
  gender?: string | null;
  age?: string | null;
  hospitalNo?: string | null;
  tissueType?: string | null;
  organoidType?: string | null;
  /** 代数（类器官选填，形如 P3；组织样本后端一律不落） */
  passage?: string | null;
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

/**
 * 送检段（整段替换；FIX V02 / issue #147）。
 *
 * ★ 与后端 `SampleSubmitSegmentBo` 逐键一致：核验抽屉里送检段可编辑，所以「判为有效并保存」/「判为无效」
 *   要把整份送检段随 `PUT /lqg/sample/{id}/verify` 一起带上，后端与核验结论**同一个事务**落库；
 *   规则与 `PUT /lqg/sample`（工作台修改）同一份。不带 `submitSegment` = 送检段不动。
 */
export interface SampleSubmitSegment {
  sourceUnitId?: string | number | null;
  sourceUnitName?: string | null;
  /** 种属（两类都必填；字典 lqg_species 的常用值或手填，CR-20261009-18） */
  species?: string | null;
  donorName?: string | null;
  gender?: string | null;
  age?: string | null;
  hospitalNo?: string | null;
  tissueType?: string | null;
  organoidType?: string | null;
  /** 代数（类器官；核验时实验室能改，CR-20260924-10） */
  passage?: string | null;
  hasPathology?: string | null;
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
  /** 核验抽屉里的送检段（整段；与核验结论同一事务保存，FIX V02） */
  submitSegment?: SampleSubmitSegment | null;
}

/** 列表（分页；待核验置顶，其余按创建时间倒序） */
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

/** 核验 / 改判：判有效要收样日期 + 内部编号；判无效要原因；带 submitSegment 时送检段一并保存 */
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
