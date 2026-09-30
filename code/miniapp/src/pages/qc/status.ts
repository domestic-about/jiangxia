// 小程序「编辑质控文档」的纯函数（不碰 uni / 网络，fixture 单测直接断）。
import type { QcDocKind, QcDocStatus, QcDocType } from '@/api/qc'

/** 三份表：页签顺序 = 模板顺序；路径段（连字符）与渲染用的 docKind（下划线）一一对应 */
export const QC_TABS: Array<{ type: QcDocType, kind: QcDocKind, label: string, short: string }> = [
  { type: 'sample-qc', kind: 'sample_qc', label: '样本质控表', short: '样本质控' },
  { type: 'organoid-qc', kind: 'organoid_qc', label: '类器官质控表', short: '类器官质控' },
  { type: 'score', kind: 'organoid_score', label: '类器官质量评分表', short: '质量评分' },
]

/** 一份表的状态文字：没打开过（null）= 未填写 */
export function statusText(status: QcDocStatus | string | undefined): string {
  if (status === 'published') {
    return '已完成'
  }
  if (status === 'draft') {
    return '草稿'
  }
  return '未填写'
}

/** 状态小标的样式后缀（lqg-tag--draft / --published；未填写用灰色） */
export function statusTag(status: QcDocStatus | string | undefined): string {
  if (status === 'published') {
    return 'published'
  }
  if (status === 'draft') {
    return 'draft'
  }
  return 'none'
}

export const PROGRESS_FILTERS: Array<{ value: string, label: string }> = [
  { value: '', label: '全部' },
  { value: 'none', label: '未开始' },
  { value: 'doing', label: '填写中' },
  { value: 'done', label: '已全部完成' },
]

/** 进度文字：填写中写成「1 / 3 已完成」，一眼看出还差几份 */
export function progressText(progress: string, publishedCount: number): string {
  if (progress === 'done') {
    return '已全部完成'
  }
  if (progress === 'doing') {
    return `${publishedCount} / 3 已完成`
  }
  return '未开始'
}

/** 评分表四个变量（顺序 = 模板顺序）与字典类型 —— 与工作台 views/lqg/qc/editor/score.ts、lang/lqg/qc-web-002 同一份口径 */
export const SCORE_ROWS: Array<{ key: 'preCulture' | 'cultureDays' | 'organoidCount' | 'diameter', label: string, dictType: string }> = [
  { key: 'preCulture', label: '培养前样本评分', dictType: 'lqg_score_pre_culture' },
  { key: 'cultureDays', label: '培养天数', dictType: 'lqg_score_culture_days' },
  { key: 'organoidCount', label: '类器官数量（药敏实验实际测得）', dictType: 'lqg_score_count' },
  { key: 'diameter', label: '类器官直径', dictType: 'lqg_score_diameter' },
]

/** 字典 remark 是 VARCHAR（'8'）：统一成数字或 null（空串 / null / 非数字都算「没有」） */
export function toScore(raw: unknown): number | null {
  if (raw === null || raw === undefined || raw === '') {
    return null
  }
  const value = typeof raw === 'number' ? raw : Number(String(raw).trim())
  return Number.isFinite(value) ? value : null
}

/** 四项分值与合计：任一项没选（或字典没配分值）合计就是 null —— 不拿半套分数出结论 */
export function scoreSummary(items: Array<number | null>): number | null {
  return items.some(item => item === null) ? null : (items as number[]).reduce((sum, item) => sum + item, 0)
}

/** 形成类器官时间 / 反馈时间：选日期写 yyyy-MM-dd，也允许手写文字 —— 只有像日期的才喂给日期面板 */
export function dateOrNull(text: string | null | undefined): string | null {
  return text && /^\d{4}-\d{2}-\d{2}$/.test(text.trim()) ? text.trim() : null
}
