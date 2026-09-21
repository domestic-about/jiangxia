/** 全局通用类型：这里只放跨页面复用的少量类型 */

/** 列表项的三态：加载中 / 空 / 失败（落地规范 §5.11） */
export type LoadState = 'idle' | 'loading' | 'empty' | 'error'

/** 状态徽标可用的修饰类（落地规范 §5.6，颜色只在 components.scss 里给） */
export type TagTone =
  | 'internal'
  | 'external'
  | 'pending'
  | 'valid'
  | 'invalid'
  | 'draft'
  | 'published'
  | 'ln2'
  | 'overdue'
  | 'ocr'
