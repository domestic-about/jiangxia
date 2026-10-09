// 小程序里「编辑质控文档」（内部人员专用；甲方 2026-09-30 要求：小程序也能编辑，不只是预览和下载）。
//
// ★ 不另开 /mp/int/** 接口：工作台编辑页用的 /lqg/qc/**、/lqg/doc/** 本来就按权限串把门
//   （lqg:qc:query / edit / publish、lqg:doc:render / query），内部角色 lqg_internal 全都有，
//   小程序的内部登录也带着这些权限（核验 /lqg/sample/{id}/verify、冻存 /lqg/cryo/** 已是同一做法）。
//   外部人员没有这些权限串 → 调了也是 403；页面本身也按身份挡，外部连入口都没有。
//
// ★ 上游形状照抄工作台 api/lqg/qc/index.ts 的五条（别改）：
//   1) images 是 map：{"orig":[…],"observe":[…],"pretreat":[…]}；评分表没有图片位；
//   2) 图片 / 附件是独立端点（POST 绑定、DELETE 解绑），不跟「保存」走；
//   3) 附件 fileSize 可选；
//   4) 细胞活率测定附件是表上单独一栏（viabilityOssId / viabilityFileName），摘掉 = viabilityOssId 传 0；
//   5) 保存是补丁语义（null = 不动、空串 = 清空）→ 每次把整份表单都发上去。
import { resolveBaseUrl } from '@/utils/baseUrl'
import { getToken } from '@/utils/auth'
import { http } from '@/utils/request'

export type QcDocType = 'sample-qc' | 'organoid-qc' | 'score'
export type QcDocKind = 'sample_qc' | 'organoid_qc' | 'organoid_score'
export type QcDocStatus = 'draft' | 'published' | null

export interface QcImage {
  id: string | number
  slot: string
  ossId: string | number
  url: string
  previewUrl?: string | null
}

export interface QcAttachment {
  id: string | number
  ossId: string | number
  fileName: string
  fileSize?: number | null
  url: string
}

export interface QcSampleRef {
  id: string | number
  submitNo?: string | null
  sampleKind?: string | null
  internalNo?: string | null
  sourceUnitName?: string | null
  donorName?: string | null
  gender?: string | null
  /** 种属（只读带出，CR-20261009-18） */
  species?: string | null
  receiveDate?: string | null
  processTime?: string | null
  operatorName?: string | null
}

export interface QcSampleDoc {
  patientNo?: string | null
  samplingSite?: string | null
  samplingMethod?: string | null
  clinicalDiagnosis?: string | null
  receiveDesc?: string | null
  viabilityOssId?: string | number | null
  viabilityFileName?: string | null
  origDesc?: string | null
  observeDesc?: string | null
  pretreatDesc?: string | null
  docStatus: string
  publishedTime?: string | null
  images: Record<string, QcImage[]>
  attachments: QcAttachment[]
}

export interface QcOrganoidDoc {
  formedTime?: string | null
  growthState?: string | null
  growthDesc?: string | null
  plannedDrugScreen?: string | null
  feedbackTime?: string | null
  docStatus: string
  publishedTime?: string | null
  images: Record<string, QcImage[]>
  attachments: QcAttachment[]
}

export interface QcScoreDoc {
  preCultureLevel?: string | null
  cultureDaysLevel?: string | null
  organoidCountLevel?: string | null
  diameterLevel?: string | null
  totalScore?: number | null
  docStatus: string
  publishedTime?: string | null
  attachments?: QcAttachment[]
}

export interface QcBundle {
  sample: QcSampleRef
  sampleQc: QcSampleDoc
  organoidQc: QcOrganoidDoc
  score: QcScoreDoc
}

/** 「质控文档」列表一行（与工作台同一个接口 GET /lqg/qc/list） */
export interface QcListRow {
  sampleId: string | number
  internalNo?: string | null
  sampleKind: 'tissue' | 'organoid'
  sourceUnitName?: string | null
  typeName?: string | null
  receiveDate?: string | null
  sampleQcStatus: QcDocStatus
  organoidQcStatus: QcDocStatus
  scoreStatus: QcDocStatus
  publishedCount: number
  progress: 'none' | 'doing' | 'done'
  totalScore?: number | null
  lastUpdateTime?: string | null
}

export interface QcListQuery {
  keyword?: string
  progress?: string
  pageNum: number
  pageSize: number
}

export function fetchQcList(query: QcListQuery) {
  return http.get<{ rows: QcListRow[], total: number }>('/lqg/qc/list', { ...query }, { raw: true })
}

/** 三份文档 + 图片 + 附件 + 样本只读字段（首次打开就地建三份空草稿；样本不是有效 → 400） */
export function fetchQcBundle(sampleId: string | number) {
  return http.get<QcBundle>(`/lqg/qc/${sampleId}`)
}

export function saveQcDoc(sampleId: string | number, docType: QcDocType, body: Record<string, unknown>) {
  return http.put<void>(`/lqg/qc/${sampleId}/${docType}`, body)
}

export function addQcImage(sampleId: string | number, docType: QcDocType, slot: string, ossId: string | number) {
  return http.post<string | number>(`/lqg/qc/${sampleId}/${docType}/image`, { slot, ossId })
}

/** 只解绑，不删 OSS 对象（内部角色没有删 OSS 的权限，也不该删） */
export function removeQcImage(sampleId: string | number, docType: QcDocType, id: string | number) {
  return http.delete<void>(`/lqg/qc/${sampleId}/${docType}/image/${id}`)
}

export function addQcAttachment(sampleId: string | number, docType: QcDocType, body: { ossId: string | number, fileName: string, fileSize?: number }) {
  return http.post<string | number>(`/lqg/qc/${sampleId}/${docType}/attachment`, body)
}

export function removeQcAttachment(sampleId: string | number, docType: QcDocType, id: string | number) {
  return http.delete<void>(`/lqg/qc/${sampleId}/${docType}/attachment/${id}`)
}

/** 完成并同步（草稿 → 已完成；后端异步出内外部版与合并件） */
export function publishQcDoc(sampleId: string | number, docType: QcDocType) {
  return http.post<void>(`/lqg/qc/${sampleId}/${docType}/publish`)
}

/** 撤回（已完成 → 草稿；送检方就看不到了） */
export function unpublishQcDoc(sampleId: string | number, docType: QcDocType) {
  return http.post<void>(`/lqg/qc/${sampleId}/${docType}/unpublish`)
}

export interface QcRenderState {
  status?: string | null
  errorMsg?: string | null
}

export interface QcPages {
  status?: string | null
  errorMsg?: string | null
  pages?: Array<{ pageNo?: number | null, url?: string | null }> | null
}

/** 触发一次内部版渲染（异步：回 pending 时去轮询 pages） */
export function renderQcDoc(sampleId: string | number, docKind: QcDocKind) {
  return http.post<QcRenderState>(`/lqg/doc/${sampleId}/${docKind}/render?audience=internal`, undefined, { silent: true })
}

/** 内部版逐页图（草稿也能看；与工作台编辑页右侧预览同一个接口） */
export function fetchQcPages(sampleId: string | number, docKind: QcDocKind) {
  return http.get<QcPages>(`/lqg/doc/${sampleId}/${docKind}/pages`, { audience: 'internal' }, { silent: true })
}

export interface DictItem {
  dictLabel: string
  dictValue: string
  remark?: string | null
}

/** 字典数据（评分表四个变量的档位与分值；该接口不带权限注解，登录即可取） */
export function fetchDict(dictType: string) {
  return http.get<DictItem[]>(`/system/dict/data/type/${dictType}`, undefined, { silent: true })
}

export interface OssUploaded {
  ossId: string
  url: string
  fileName: string
}

/**
 * 传一个本地文件到对象存储（POST /resource/oss/upload，multipart 字段名 file）。
 * ★ 根地址与鉴权头与 uni.request 同一来源（utils/baseUrl · OCR-MP-001 踩过两处各拼一次的坑）。
 */
export function uploadToOss(filePath: string, fileName?: string): Promise<OssUploaded> {
  const header: Record<string, string> = {
    clientid: (import.meta.env.VITE_APP_CLIENT_ID as string) || '',
  }
  const token = getToken()
  if (token) {
    header.Authorization = `Bearer ${token}`
  }
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${resolveBaseUrl()}/resource/oss/upload`,
      filePath,
      name: 'file',
      header,
      // H5 上 filePath 是 blob: 地址，不带扩展名 —— 把原文件名带上，服务端才知道类型
      ...(fileName ? { formData: { fileName } } : {}),
      success: (res) => {
        let body: { code?: number, msg?: string, data?: OssUploaded } | null = null
        try {
          body = typeof res.data === 'string' ? JSON.parse(res.data) : (res.data as never)
        }
        catch {
          body = null
        }
        if (!body || body.code !== 200 || !body.data) {
          reject(new Error(body?.msg || `上传失败（${res.statusCode}）`))
          return
        }
        resolve(body.data)
      },
      fail: (err) => {
        const msg = String(err?.errMsg || '')
        reject(new Error(msg.includes('domain list') ? '上传域名未在微信后台配置（uploadFile 合法域名）' : (msg || '网络连接失败')))
      },
    })
  })
}
