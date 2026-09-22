import request from '@/utils/request';
import { AxiosPromise } from 'axios';

// ============================================================================
// CRYO 域 · 工作台「冻存管理」页（CRYO-WEB-001）
//
// 后端：code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/.../cryo/batch/controller/CryoBatchController.java
//       + .../cryo/batch/service/CryoQueryService.java（读时算剩余 + tabCounts）
//       + .../cryo/flow/controller/CryoFlowController.java（出入库 / 改删登记 / 转液氮）
//       + .../cryo/export/CryoExportService.java（POST /lqg/cryo/batch/export）
// 契约：doc/api-contract.md「CRYO」一节的 /lqg/cryo/* 各行
// 权限串：lqg:cryo:{list,query,add,edit,remove,export,flow}
//         —— 菜单 5410-5417（V202609241210），授给 101（lqg_admin）与 102（lqg_internal）。
//
// ★ 四个「语义最容易做反」的点，前端这一层必须跟着后端一起记：
//   1) remainingQty 是**读时算**的（init_qty + 未删流水 delta 之和），
//      批次表上根本没有这一列 —— 前端不许自己累加 rows 里的流水。
//   2) tabCounts 是**整表口径**，不随筛选收窄；页签数字直接用它，
//      别对当前页 rows 自己数（一翻页就错，accept 2 counterfeit 第一条）。
//   3) overdueDays 未超期时是 **null**（不是 0）：「已超 0 天」只有阈值当天才出现。
//   4) 「暂存 -80」是两个按钮（是 / 否），用 SegButtons，不是 el-switch。
// ============================================================================

/** 位置（与后端 CryoBalanceChecker 的常量一致） */
export const LOCATION_MINUS80 = 'minus80';
export const LOCATION_LN2 = 'ln2';

/** 流水类型 */
export const FLOW_TYPE_TAKE = 'take';
export const FLOW_TYPE_ADD = 'add';
export const FLOW_TYPE_ADJUST = 'adjust';

/** 冻存批次行（GET /lqg/cryo/batch/list 的 rows[]） */
export interface CryoBatchVO {
  id: string | number;
  sampleId: string | number;
  /** 冻存样品名称（手填） */
  cryoName?: string | null;
  /** 代数，形如 P2 */
  passage?: string | null;
  freezeTime?: string | null;
  /** ★ 冻存数量/支 = **初始**支数（不是剩余） */
  initQty?: number | null;
  density?: string | null;
  /** 暂存 -80：Y 是 / N 否 */
  inMinus80?: string | null;
  frozenBy?: string | null;
  toLn2Time?: string | null;
  ln2Location?: string | null;
  remark?: string | null;
  /** ★ 当前剩余（读时算，不落库） */
  remainingQty?: number | null;
  /** minus80 / ln2（读时算） */
  location?: string | null;
  /** 所挂样本的内部编号（读时带出；样本软删时为 null） */
  internalNo?: string | null;
  submitNo?: string | null;
  sourceUnitName?: string | null;
  sampleVerifyStatus?: string | null;
  /** ★ 是否超期（读时算：暂存 -80 且未转液氮且剩余 > 0 且冻存满阈值天数） */
  overdue?: boolean | null;
  /** ★ 已超天数；未超期是 null（不是 0） */
  overdueDays?: number | null;
  createTime?: string | null;
  /** ★ null = 从未修改 */
  updateTime?: string | null;
  createBy?: string | number | null;
  updateBy?: string | number | null;
  handlerName?: string | null;
  updateByName?: string | null;
  mine?: boolean;
}

/** 列表响应（TableDataInfo + 顶层 tabCounts；CRYO-REMIND-001） */
export interface CryoBatchPage {
  code: number;
  msg: string;
  rows: CryoBatchVO[];
  total: number;
  /** ★ 三个数都是整表口径，不随筛选收窄（页签数字取它，不要自己数 rows） */
  tabCounts: { all: number; overdue: number; ln2: number };
}

/** 列表筛选（字段名与 CryoQueryBo / doc/api-contract.md 逐字一致） */
export interface CryoQuery {
  pageNum?: number;
  pageSize?: number;
  /** 内部编号（所挂样本的，等值） */
  internalNo?: string | null;
  /** 冻存样品名称（模糊） */
  cryoName?: string | null;
  /** 所挂样本 id（从样本总表带 sampleId 跳入时用） */
  sampleId?: string | number | null;
  /** minus80 / ln2 */
  location?: string | null;
  /** true = 只看超期（与行上的 overdue、页签数字同一个判据） */
  overdueOnly?: boolean | null;
  freezeTimeBegin?: string | null;
  freezeTimeEnd?: string | null;
}

/** 新增 / 修改批次入参（POST / PUT /lqg/cryo/batch） */
export interface CryoBatchForm {
  id?: string | number | null;
  sampleId?: string | number | null;
  cryoName?: string | null;
  passage?: string | null;
  freezeTime?: string | null;
  /** ★ 初始支数，可改（后端锁批次行后逐笔校验，任一步 < 0 → 400） */
  initQty?: number | null;
  density?: string | null;
  /** Y 是 / N 否 */
  inMinus80?: string | null;
  frozenBy?: string | null;
  toLn2Time?: string | null;
  ln2Location?: string | null;
  remark?: string | null;
}

/** 流水行（GET /lqg/cryo/batch/{id}/flows 的 data[]） */
export interface CryoFlowVO {
  id: string | number;
  batchId: string | number;
  /** take 取走 / add 补入 / adjust 盘点调整 */
  flowType: string;
  /** 带符号变化量（take 恒为负、add 恒为正、adjust 可正可负） */
  delta?: number | null;
  /** minus80 / ln2（登记时的位置，不随后续转移重算） */
  fromLocation?: string | null;
  operatorName?: string | null;
  flowTime?: string | null;
  purpose?: string | null;
  /** ★ 这一笔之后的剩余（读时算，不落库） */
  balanceAfter?: number | null;
  /** ★ 被改过没有 */
  edited?: boolean | null;
  updateByName?: string | null;
  /** 批次当前剩余，只填在时间倒序的第一行上 */
  remainingQty?: number | null;
  createBy?: string | number | null;
  updateBy?: string | number | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/** 登记一笔流水（POST /lqg/cryo/batch/{id}/flow） */
export interface CryoFlowForm {
  flowType: string;
  /** take / add 正整数；adjust 带符号不为 0 */
  qty: number;
  /** adjust 必填（原因留痕） */
  purpose?: string | null;
  operatorName?: string | null;
  flowTime?: string | null;
}

/** 改一笔登记（PUT /lqg/cryo/batch/{id}/flow/{flowId}）—— 类型不可改 */
export interface CryoFlowEditForm {
  qty?: number | null;
  purpose?: string | null;
  operatorName?: string | null;
  flowTime?: string | null;
  /** 只用于「传了且与原来不同 → 400」；改不了它 */
  flowType?: string | null;
}

/** 登记转液氮（PUT /lqg/cryo/batch/{id}/to-ln2） */
export interface CryoToLn2Form {
  toLn2Time: string;
  ln2Location: string;
}

/** 列表（分页；行带 remainingQty / location / overdue / overdueDays，响应带 tabCounts） */
export function listBatches(query: CryoQuery): AxiosPromise<CryoBatchPage> {
  return request({
    url: '/lqg/cryo/batch/list',
    method: 'get',
    params: query
  });
}

/** 详情 */
export function getBatch(id: string | number): AxiosPromise<CryoBatchVO> {
  return request({
    url: '/lqg/cryo/batch/' + id,
    method: 'get'
  });
}

/** 新建批次 */
export function addBatch(data: CryoBatchForm) {
  return request({
    url: '/lqg/cryo/batch',
    method: 'post',
    data
  });
}

/** 修改批次（初始支数可改：后端锁行后逐笔校验，任一步 < 0 → 400 且库里不变） */
export function updateBatch(data: CryoBatchForm) {
  return request({
    url: '/lqg/cryo/batch',
    method: 'put',
    data
  });
}

/** 软删（有未删流水的批次后端会拒） */
export function delBatch(ids: string | number | Array<string | number>) {
  return request({
    url: '/lqg/cryo/batch/' + ids,
    method: 'delete'
  });
}

/**
 * 超期批次清单（GET /lqg/cryo/overdue，已按已超天数倒序）。
 *
 * 页签上的数字**不用**它，用列表响应里的 `tabCounts.overdue`（两者恒等，但少一次请求）。
 */
export function listOverdue(): AxiosPromise<CryoBatchVO[]> {
  return request({
    url: '/lqg/cryo/overdue',
    method: 'get'
  });
}

/**
 * 按当前筛选导出「-80 冻存」xlsx（POST /lqg/cryo/batch/export）。
 *
 * ★ 筛选走 **query 参数**（与后端 controller 的 `CryoQueryBo query` 绑定一致），
 * 不是 JSON body。表头 = 甲方模板 9 列 + 追加「代数」「当前剩余/支」。
 */
export function exportBatches(query: CryoQuery): AxiosPromise<Blob> {
  return request({
    url: '/lqg/cryo/batch/export',
    method: 'post',
    params: query,
    responseType: 'blob'
  });
}

/** 某批次的未删流水（时间倒序；每行带 balanceAfter / edited / updateByName） */
export function listFlows(id: string | number): AxiosPromise<CryoFlowVO[]> {
  return request({
    url: '/lqg/cryo/batch/' + id + '/flows',
    method: 'get'
  });
}

/** 登记一笔流水（取走 / 补入 / 盘点调整） */
export function addFlow(id: string | number, data: CryoFlowForm) {
  return request({
    url: '/lqg/cryo/batch/' + id + '/flow',
    method: 'post',
    data
  });
}

/**
 * 改一笔登记（流水抽屉每行的「修改」）。
 *
 * ★ 与取走 / 补入 / 调整同款弹窗，**类型不可改**（后端传了不同的 flowType → 400）；
 * `qty` 按原类型解释。被拒时后端指出是哪一笔，库里不变。
 */
export function updateFlow(id: string | number, flowId: string | number, data: CryoFlowEditForm) {
  return request({
    url: '/lqg/cryo/batch/' + id + '/flow/' + flowId,
    method: 'put',
    data
  });
}

/**
 * 删一笔登记（流水抽屉每行的「删除」）—— **软删**（追溯不能断）。
 *
 * 删完若让后面某一步剩余为负，后端 400 并指出是哪一笔；被拒时库里不变。
 */
export function deleteFlow(id: string | number, flowId: string | number) {
  return request({
    url: '/lqg/cryo/batch/' + id + '/flow/' + flowId,
    method: 'delete'
  });
}

/** 登记转液氮（保存后批次位置当场变液氮、行上的超期标记当场消失） */
export function toLn2(id: string | number, data: CryoToLn2Form) {
  return request({
    url: '/lqg/cryo/batch/' + id + '/to-ln2',
    method: 'put',
    data
  });
}

// ── 纯展示小工具（页面上配套的文案都走 lqg.cryo.*，这里只出「有没有」） ──

/** 这一行是不是「从没改过」：updateTime 为 null（空串 / undefined 也算） */
export function neverModified(row: Pick<CryoBatchVO, 'updateTime'>): boolean {
  return row?.updateTime === null || row?.updateTime === undefined || row?.updateTime === '';
}

/**
 * 「已超 N 天」徽标的数字：**未超期是 null**，不渲染徽标。
 *
 * ★ 与 `overdue` 布尔同源：只有超期行才可能有天数（阈值当天 = 0）。
 * 别写成 `overdueDays ?? 0` —— 那会给没超期的行显示「已超 0 天」。
 */
export function overdueDaysText(row: Pick<CryoBatchVO, 'overdue' | 'overdueDays'>): string | null {
  if (!row?.overdue) {
    return null;
  }
  return String(row.overdueDays ?? 0);
}

/** «取走 −2» / «补入 +3» / «调整 ±n» —— 时间线上的变化量文案（不用正负号骗人） */
export function deltaText(row: Pick<CryoFlowVO, 'flowType' | 'delta'>): string {
  const delta = row?.delta ?? 0;
  return delta > 0 ? '+' + delta : String(delta);
}

/** 流水类型 → i18n key 后缀（页面里 t('lqg.cryo.flowType.' + key)） */
export function flowTypeKey(flowType?: string | null): string {
  if (flowType === FLOW_TYPE_TAKE) return 'take';
  if (flowType === FLOW_TYPE_ADD) return 'add';
  if (flowType === FLOW_TYPE_ADJUST) return 'adjust';
  return 'unknown';
}

/** 位置 → i18n key 后缀 */
export function locationKey(location?: string | null): string {
  return location === LOCATION_LN2 ? 'ln2' : 'minus80';
}
