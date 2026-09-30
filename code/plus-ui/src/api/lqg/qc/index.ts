import request from '@/utils/request';
import { AxiosPromise } from 'axios';

// ============================================================================
// QC 域 · 三份质控文档的读写（QC-WEB-001 建立，QC-WEB-002 续用）
//
// 后端：code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/.../qc/controller/QcDocController.java
//       （QC-MODEL-001 实现；本票只消费，不改后端）
// 契约：doc/api-contract.md「QC / DOC」一节的 /lqg/qc/** 行
// 权限串：lqg:qc:{query,edit,publish}——菜单 5501-5503（V202609261300），101 / 102 都有。
//
// ★ 五个「最容易做反」的上游形状（前两位实现者专门写下来的，别改）：
//   1) `images` 是 **map 不是数组**：`{"orig":[…],"observe":[…],"pretreat":[…]}`，
//      每个位都在、没图是 `[]`；评分表的 `images` 恒为 `{}`（它没有图片位）。
//   2) 图片位排序 `PUT …/image/sort` 的请求体是 `{ids:[该位**全部** id]}`（issue #210）
//      —— 不是 {slot, ids}、也不是只传被移动的那一张。
//   3) 附件的 `fileSize` **可选**（issue #209）：契约没带、`sys_oss.ext1` 也没记录时是 0，
//      前端别把 0 当「空文件」拒绝，也别为了凑它去额外查一次。
//   4) 细胞活率测定附件挂在表上单独一栏（`viabilityOssId` / `viabilityFileName`），
//      **不在** `attachments` 里；摘掉它的约定是 `viabilityOssId = 0`（issue #212），
//      不是 null（null = 不动）。
//   5) 保存是**补丁语义**：`null` = 不动，空串 = 清空。
//      → 前端每次保存都要把「当前这一份表单的完整值」发上去，不要只发改动过的键，
//        否则「把某个文本框清空」会被后端当成「不动」而静默丢失。
//
// ★ 图片的 `url` 是原图（点开看的就是它），`previewUrl` 是进 Word 的预览图
//   （TIFF 等格式由后端另存 JPEG；无需转换时两者相等）。
// ============================================================================

/** 路径里的 docType（连字符） */
export type QcDocType = 'sample-qc' | 'organoid-qc' | 'score';

/** 渲染 / 页面图接口用的 docKind（下划线），与 `document/...` 的字典值一致 */
export type QcDocKind = 'sample_qc' | 'organoid_qc' | 'organoid_score';

/** 图片位 slot：样本质控表 orig / observe / pretreat；类器官质控表只有 organoid_observe */
export type QcImageSlot = 'orig' | 'observe' | 'pretreat' | 'organoid_observe';

/** 一个图片位里的一张图（DocImageVo） */
export interface DocImageVO {
  id: string | number;
  slot: QcImageSlot | string;
  ossId: string | number;
  previewOssId?: string | number | null;
  sort?: number | null;
  /** 原图 URL（短时签名链接） */
  url: string;
  /** 预览图 URL（TIFF 等格式的 JPEG 预览；无需转换时等于 url） */
  previewUrl: string;
}

/** 一条通用附件（DocAttachmentVo） */
export interface DocAttachmentVO {
  id: string | number;
  ossId: string | number;
  fileName: string;
  /** ★ 可选：可能是 0 / null（issue #209），不要当「空文件」拒绝 */
  fileSize?: number | null;
  sort?: number | null;
  url: string;
}

/** 从样本主档带出的只读字段（QcSampleRefVo）——页头摘要条，不进表单 */
export interface QcSampleRefVO {
  id: string | number;
  submitNo?: string | null;
  sampleKind?: string | null;
  internalNo?: string | null;
  sourceUnitName?: string | null;
  donorName?: string | null;
  gender?: string | null;
  receiveDate?: string | null;
  processTime?: string | null;
  operatorName?: string | null;
}

/** 样本质控表（QcSampleDocVo） */
export interface QcSampleDocVO {
  id: string | number;
  sampleId: string | number;
  patientNo?: string | null;
  samplingSite?: string | null;
  samplingMethod?: string | null;
  clinicalDiagnosis?: string | null;
  receiveDesc?: string | null;
  /** 细胞活率测定附件（单独一栏，不在 attachments 里） */
  viabilityOssId?: string | number | null;
  viabilityFileName?: string | null;
  origDesc?: string | null;
  observeDesc?: string | null;
  pretreatDesc?: string | null;
  docStatus: 'draft' | 'published' | string;
  publishedTime?: string | null;
  publishedBy?: string | number | null;
  images: Record<string, DocImageVO[]>;
  attachments: DocAttachmentVO[];
}

/** 类器官质控表（QcOrganoidDocVo；本票只占位展示状态，编辑在 QC-WEB-002） */
export interface QcOrganoidDocVO {
  id: string | number;
  sampleId: string | number;
  formedTime?: string | null;
  growthState?: string | null;
  growthDesc?: string | null;
  plannedDrugScreen?: string | null;
  feedbackTime?: string | null;
  docStatus: 'draft' | 'published' | string;
  publishedTime?: string | null;
  images: Record<string, DocImageVO[]>;
  attachments: DocAttachmentVO[];
}

/** 类器官质量评分表（QcScoreDocVo；分值由后端按字典回填） */
export interface QcScoreDocVO {
  id: string | number;
  sampleId: string | number;
  preCultureLevel?: string | null;
  cultureDaysLevel?: string | null;
  organoidCountLevel?: string | null;
  diameterLevel?: string | null;
  preCultureScore?: number | null;
  cultureDaysScore?: number | null;
  organoidCountScore?: number | null;
  diameterScore?: number | null;
  totalScore?: number | null;
  docStatus: 'draft' | 'published' | string;
  publishedTime?: string | null;
  images?: Record<string, DocImageVO[]>;
  attachments?: DocAttachmentVO[];
}

/** `GET /lqg/qc/{sampleId}` 的 data（三份文档一定都在，首次访问就地建三份空草稿） */
export interface QcDocBundleVO {
  sample: QcSampleRefVO;
  sampleQc: QcSampleDocVO;
  organoidQc: QcOrganoidDocVO;
  score: QcScoreDocVO;
}

/** `PUT …/sample-qc` 的请求体（补丁语义；见文件头第 5 条） */
export interface QcSampleSaveBO {
  patientNo?: string | null;
  samplingSite?: string | null;
  samplingMethod?: string | null;
  clinicalDiagnosis?: string | null;
  receiveDesc?: string | null;
  /** null = 不动；**0 = 摘掉**（issue #212） */
  viabilityOssId?: string | number | null;
  viabilityFileName?: string | null;
  origDesc?: string | null;
  observeDesc?: string | null;
  pretreatDesc?: string | null;
}

/** 三份文档 + 图片 + 附件 + 样本只读字段（首次访问建三份空草稿；样本不是 valid → 400） */
export function getQcBundle(sampleId: string | number): AxiosPromise<QcDocBundleVO> {
  return request({
    url: `/lqg/qc/${sampleId}`,
    method: 'get'
  });
}

/** 保存样本质控表（整份表单一起发，补丁语义） */
export function saveSampleQc(sampleId: string | number, data: QcSampleSaveBO): AxiosPromise<void> {
  return request({
    url: `/lqg/qc/${sampleId}/sample-qc`,
    method: 'put',
    data
  });
}

/** 保存类器官质控表（QC-WEB-002 用；本票不调用，先按契约留好形状） */
export function saveOrganoidQc(sampleId: string | number, data: Record<string, any>): AxiosPromise<void> {
  return request({
    url: `/lqg/qc/${sampleId}/organoid-qc`,
    method: 'put',
    data
  });
}

/** 保存评分表（只收四个 level，分值后端回填；QC-WEB-002 用） */
export function saveScore(sampleId: string | number, data: Record<string, any>): AxiosPromise<void> {
  return request({
    url: `/lqg/qc/${sampleId}/score`,
    method: 'put',
    data
  });
}

/** 往图片位加一张图（先走 `/resource/oss/upload` 拿 ossId，再调这里绑定） */
export function addDocImage(
  sampleId: string | number,
  docType: QcDocType,
  data: { slot: string; ossId: string | number }
): AxiosPromise<string | number> {
  return request({
    url: `/lqg/qc/${sampleId}/${docType}/image`,
    method: 'post',
    data
  });
}

/** 软删一张图（★ 只解绑，不删 OSS 对象 —— 101/102 没有 `system:oss:remove`） */
export function removeDocImage(sampleId: string | number, docType: QcDocType, id: string | number): AxiosPromise<void> {
  return request({
    url: `/lqg/qc/${sampleId}/${docType}/image/${id}`,
    method: 'delete'
  });
}

/**
 * 重排一个图片位内的顺序。
 * ★ 请求体是 `{ids:[该位全部 id]}`（issue #210）——按目标顺序给全量，缺一张后端就 400。
 */
export function sortDocImages(sampleId: string | number, docType: QcDocType, ids: (string | number)[]): AxiosPromise<void> {
  return request({
    url: `/lqg/qc/${sampleId}/${docType}/image/sort`,
    method: 'put',
    data: { ids }
  });
}

/** 挂一个通用附件（fileSize 可选，≤ 50MB 时后端才校验） */
export function addDocAttachment(
  sampleId: string | number,
  docType: QcDocType,
  data: { ossId: string | number; fileName: string; fileSize?: number }
): AxiosPromise<string | number> {
  return request({
    url: `/lqg/qc/${sampleId}/${docType}/attachment`,
    method: 'post',
    data
  });
}

/** 软删一个通用附件（同样只解绑，不删 OSS 对象） */
export function removeDocAttachment(sampleId: string | number, docType: QcDocType, id: string | number): AxiosPromise<void> {
  return request({
    url: `/lqg/qc/${sampleId}/${docType}/attachment/${id}`,
    method: 'delete'
  });
}

// ── 文件上传（框架自带 POST /resource/oss/upload，返回 {ossId, url, fileName}）────
// ★ 101 / 102 有 `system:oss:upload`，**没有** `system:oss:remove`：
//   所以「删除」一律只调 /lqg/qc/** 的解绑接口，绝不去删 OSS 对象（会 403）。
// ★ upload URL 用 VITE_APP_BASE_API + 路径（与 components/FileUpload 同一写法），
//   让 dev 代理把请求转到后端；headers 用 globalHeaders()（token + clientid）。
export const OSS_UPLOAD_URL = import.meta.env.VITE_APP_BASE_API + '/resource/oss/upload';

/** `POST /resource/oss/upload` 的 data */
export interface OssUploadVO {
  ossId: string;
  url: string;
  fileName: string;
}

// ── 「质控文档」板块列表（CR-20260930-11，飞书「网页工作台」第 17 行）──────────────
// GET /lqg/qc/list：已核验有效的样本 + 三份质控表各自的状态；权限 lqg:qc:query（与编辑页同一串）。

/** 一份质控表的状态：null = 还没打开过；draft = 草稿；published = 已完成并同步 */
export type QcDocStatus = 'draft' | 'published' | null;

/** 列表筛选 */
export interface QcDocListQuery extends PageQuery {
  /** 内部编号 / 来源单位 / 送检单号，模糊匹配 */
  keyword?: string;
  /** tissue（样本记录信息表）/ organoid（类器官送样记录） */
  sampleKind?: string;
  /** none（未开始）/ doing（填写中）/ done（三份都已完成） */
  progress?: string;
  /** 收样日期 yyyy-MM-dd（含当天） */
  receiveBegin?: string;
  receiveEnd?: string;
}

/** 列表一行 = 一个样本 */
export interface QcDocListVO {
  sampleId: string | number;
  internalNo?: string;
  sampleKind: 'tissue' | 'organoid';
  sourceUnitName?: string;
  typeName?: string;
  receiveDate?: string;
  sampleQcStatus: QcDocStatus;
  organoidQcStatus: QcDocStatus;
  scoreStatus: QcDocStatus;
  publishedCount: number;
  progress: 'none' | 'doing' | 'done';
  totalScore?: number;
  lastUpdateTime?: string;
}

export function listQcDocs(query: QcDocListQuery): AxiosPromise<QcDocListVO[]> {
  return request({
    url: '/lqg/qc/list',
    method: 'get',
    params: query
  });
}
