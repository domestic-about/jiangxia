import request from '@/utils/request';
import { AxiosPromise } from 'axios';

// ============================================================================
// DOC 域 · 文档渲染产物（DOC-PDF-001）
//
// 后端：code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/.../doc/render/controller/DocRenderController.java
//       + .../doc/render/service/DocRenderService.java（docx → pdf → 每页 png 的流水线）
//       + .../doc/pdf/DocPagesService.java（页面图片 + 图片位 + 附件）
// 契约：doc/api-contract.md「QC / DOC」一节的 /lqg/doc/* 三行
// 权限串：lqg:doc:render（触发）/ lqg:doc:query（取状态、签名链接）——菜单 5504/5505（V202609261410）
//
// ★ 三个语义要点：
//   1) 「这份文档的整体状态」= docx 那一行（file_format=docx, page_no=0）的 render_status；
//      流水线任何一步失败（Gotenberg 连不上 / 超时）都是 **failed + errorMsg**，不是 HTTP 500。
//   2) failed 时 pages 一定是空数组（旧产物保留在库里，但不再被当成最新返回）。
//      所以「重新生成」= 再调一次 render（幂等：指纹没变就直接返回 done）。
//   3) 所有 url 都是 **10 分钟短时签名链接**，不要缓存、不要存库、不要拼字符串。
// ============================================================================

export type DocKind = 'sample_qc' | 'organoid_qc' | 'organoid_score' | 'merged';
export type DocAudience = 'internal' | 'external';

/** POST /lqg/doc/{sampleId}/{docKind}/render 的 data */
export interface DocRenderVO {
  sampleId?: string | number;
  docKind?: DocKind;
  audience?: DocAudience;
  /** pending / done / failed */
  status?: string;
  ossId?: string | number | null;
  contentHash?: string | null;
  templateVersion?: string | null;
  /** ★ failed 时的原因（工作台要显示它，并给一个「重新生成」按钮） */
  errorMsg?: string | null;
  renderedTime?: string | null;
  /** true = 这次命中缓存、没有重新渲染 */
  cached?: boolean;
}

/** GET /lqg/doc/{sampleId}/{docKind}/pages 的 data */
export interface DocPagesVO {
  status?: string;
  /** ★ 票面形状之外多加的一个键：failed 时工作台显示原因 */
  errorMsg?: string | null;
  docKind?: DocKind;
  audience?: DocAudience;
  contentHash?: string | null;
  templateVersion?: string | null;
  /** 页面图片（pageNo 从 1 起，仅 done 时非空） */
  pages?: { pageNo: number; url: string }[];
  /** 质控图片位：previewUrl 是预览图，url 是原图（都是短时签名链接） */
  images?: { url: string; previewUrl: string }[];
  attachments?: { fileName: string; fileSize: number; url: string }[];
}

/** GET /lqg/doc/{sampleId}/{docKind}/download 的 data */
export interface DocDownloadVO {
  url: string;
  fileName: string;
}

/** 触发渲染（幂等：指纹没变直接返回 done；失败返回 failed + errorMsg，可直接重试） */
export function renderDoc(sampleId: string | number, docKind: DocKind, audience: DocAudience): AxiosPromise<DocRenderVO> {
  return request({
    url: `/lqg/doc/${sampleId}/${docKind}/render`,
    method: 'post',
    params: { audience }
  });
}

/** 取页面图片 + 图片位 + 附件（failed 时 pages 为空、errorMsg 有原因） */
export function getDocPages(sampleId: string | number, docKind: DocKind, audience: DocAudience): AxiosPromise<DocPagesVO> {
  return request({
    url: `/lqg/doc/${sampleId}/${docKind}/pages`,
    method: 'get',
    params: { audience }
  });
}

/** 取短时签名下载链接（format=docx | pdf） */
export function getDocDownload(
  sampleId: string | number,
  docKind: DocKind,
  format: 'docx' | 'pdf',
  audience: DocAudience
): AxiosPromise<DocDownloadVO> {
  return request({
    url: `/lqg/doc/${sampleId}/${docKind}/download`,
    method: 'get',
    params: { format, audience }
  });
}
