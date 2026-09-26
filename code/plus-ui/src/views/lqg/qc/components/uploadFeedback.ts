// ============================================================================
// 质控页上传失败的提示口径（图片位 ImageSlotUploader 与附件 AttachmentList 共用，独立验收「上传提示」）
//
// 为什么要有它：el-upload 自己发的上传请求（不走 axios 拦截器）失败时，旧写法一律只说「上传失败」——
//   超过容器层的大小上限时，服务端回的是**空响应的 400**（V05 活体证实），界面上什么原因都看不出来。
//   这里按 HTTP 状态把原因说清楚：超限 / 登录过期 / 网络断了 / 服务端拒绝（多半是超限）/ 服务端报错。
//
// 前端口径保持：附件单个 ≤ 50 MB、图片单张 ≤ 20 MB（后端上限由配置组调到 50 MB）。
// ============================================================================

type Translate = (key: string, ...args: any[]) => string;

interface UploadAjaxErrorLike {
  status?: number;
  message?: string;
}

/** el-upload `on-error` 的第一个参数（UploadAjaxError）→ 给人看的一句话 */
export function uploadErrorMessage(t: Translate, error: unknown, fileName: string | undefined, maxMb: number): string {
  const err = (error || {}) as UploadAjaxErrorLike;
  const status = Number(err.status || 0);
  const name = fileName || '';
  if (status === 413) {
    return t('lqg.qc.integrity.uploadTooLargeServer', { name, max: maxMb });
  }
  if (status === 401) {
    return t('lqg.qc.integrity.uploadExpired');
  }
  if (status === 0) {
    return t('lqg.qc.integrity.uploadNetwork', { name });
  }
  if (status === 400) {
    // 容器层拒绝 multipart（最常见：超过服务器的单文件 / 单请求上限）—— 响应体是空的，原因只能按口径说
    return t('lqg.qc.integrity.uploadRejected', { name, status, max: maxMb });
  }
  const detail = serverMessage(err.message);
  if (detail) {
    return t('lqg.qc.integrity.uploadServerError', { name, msg: detail });
  }
  // 5xx 且没有响应体：多半是服务端在收大文件的半路断开了连接（开发代理 / 网关会把它报成 500 / 502）
  return t('lqg.qc.integrity.uploadBroken', { name, status, max: maxMb });
}

/** 上传接口回了 200 但业务码不是 200（例如文件类型不允许）→ 带上文件名 */
export function uploadBizErrorMessage(t: Translate, res: { msg?: string } | undefined, fileName: string | undefined): string {
  return t('lqg.qc.integrity.uploadServerError', { name: fileName || '', msg: res?.msg || t('lqg.qc.uploadFailed') });
}

/** UploadAjaxError.message 可能是后端的 JSON（{code,msg}），也可能是「fail to post … 500」 */
function serverMessage(message?: string): string {
  if (!message) return '';
  try {
    const parsed = JSON.parse(message);
    if (parsed && typeof parsed.msg === 'string') return parsed.msg;
  } catch {
    // 不是 JSON：原样用（但去掉 element-plus 自己拼的前缀）
  }
  return message.startsWith('fail to ') ? '' : message.slice(0, 200);
}
