import { normalizeIdentity } from '@/types/identity'

// 首页「待处理」块的纯函数（内部人员小程序首页）。
//
// 来源：甲方 2026-09-24 测试问题记录表第 17 行 ——「内部人员的小程序首页和网页台首页都需要显示
// 待核验、冻存超期这些需要处理的事」，Kevin 定这次一起做（推翻了 CR-20260917-05 的「首页没有数字」）。
// 期望值全部在 `doc/verify/fixtures/home-todo-cases.json` 里，`todo.fixture.spec.ts` 逐例对着断。
//
// 三条口径：
// 1. **只给内部**：身份只认 `/mp/me` 的 identity，外部、缺失、不认识 → 不显示（绝不默认当内部）。
//    外部首页一个字都不变。
// 2. **固定三项、固定顺序**：待核验样本 / 待核验石蜡包埋送样 / -80 超期未转液氮。
//    数为 0 的项弱化显示但**不隐藏**；三项都是 0 → 整块只剩一行「暂无待处理」。
// 3. 数字只来自 `GET /lqg/home/todo`（与工作台首页同一次现算，不在前端数列表）。
//    只读这三个键：`pendingSamples`（组织 + 类器官之和，A 组拆出的 pendingTissue / pendingOrganoid 不读）、
//    `pendingEmbeds`、`cryoOverdue`；外部用户待核验、渲染失败两项是工作台的事，这里不出。

/** 三项的键（顺序即显示顺序），与 `HomeTodoVo` 的字段名逐字一致 */
export const TODO_KEYS = ['pendingSamples', 'pendingEmbeds', 'cryoOverdue'] as const
export type TodoKey = (typeof TODO_KEYS)[number]

/** 三项的文案 */
export const TODO_LABEL: Record<TodoKey, string> = {
  pendingSamples: '待核验样本',
  pendingEmbeds: '待核验石蜡包埋送样',
  cryoOverdue: '-80 超期未转液氮',
}

/**
 * 点一项去哪：
 * - 两个「待核验」→ 待核验列表页（小程序里核验）的对应页签；
 * - 「-80 超期」→ 内部管理表格页冻存表的超期页签（`tab` 参数由冻存那边的表格页认，这里只管带上）。
 */
export const TODO_TARGET: Record<TodoKey, string> = {
  pendingSamples: '/pages/verify/index?tab=tissue',
  pendingEmbeds: '/pages/verify/index?tab=embed',
  cryoOverdue: '/pages/ledger/index?sheet=cryo&tab=overdue',
}

/** 三项都是 0 时整块只剩的那一行 */
export const TODO_EMPTY_TEXT = '暂无待处理'

/** 一项的展示数据（组件只渲染，不再判断） */
export interface TodoItem {
  key: TodoKey
  label: string
  count: number
  /** 数为 0：弱化显示（不隐藏） */
  muted: boolean
  target: string
}

/** 这块要不要出现：只认 `/mp/me` 的 identity，只有内部出 */
export function showTodo(identity: unknown): boolean {
  return normalizeIdentity(identity) === 'internal'
}

/** 取一个数：缺键、null、负数、非数字一律按 0（数字只做展示，不拿它做判断） */
export function todoCount(todo: unknown, key: TodoKey): number {
  if (!todo || typeof todo !== 'object') {
    return 0
  }
  const raw = (todo as Record<string, unknown>)[key]
  const n = typeof raw === 'number' ? raw : typeof raw === 'string' && raw.trim() !== '' ? Number(raw) : Number.NaN
  if (!Number.isFinite(n) || n <= 0) {
    return 0
  }
  return Math.floor(n)
}

/** 固定三项（顺序 = TODO_KEYS），0 的项 muted */
export function todoItems(todo: unknown): TodoItem[] {
  return TODO_KEYS.map((key) => {
    const count = todoCount(todo, key)
    return { key, label: TODO_LABEL[key], count, muted: count === 0, target: TODO_TARGET[key] }
  })
}

/** 三项都是 0 → 整块显示成一行「暂无待处理」 */
export function todoAllClear(todo: unknown): boolean {
  return TODO_KEYS.every(key => todoCount(todo, key) === 0)
}
