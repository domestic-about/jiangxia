// ============================================================================
// CRYO 域 · 流水弹窗的纯判据（CRYO-WEB-001）
//
// 抽成纯函数是为了「取走的支数上限 / 调整量不为 0 / 失败文案」这三条能被单测钉住
// （与 EMBED-WEB-001 的 stain.ts 同一个套路：判据只有一处，组件只负责渲染）。
// 文案一律走 i18n key，本文件不产生任何用户可见字符串。
// ============================================================================

import type { CryoFlowVO } from '@/api/lqg/cryo';

/** 三种流水类型 */
export type FlowKind = 'take' | 'add' | 'adjust';

/** 取走 / 补入 / 调整 —— 常量名与后端 CryoFlowService 的 flowType 逐字一致 */
export const FLOW_KINDS: readonly FlowKind[] = ['take', 'add', 'adjust'];

/** 是不是三种已知类型之一 */
export function isFlowKind(value?: string | null): value is FlowKind {
  return value === 'take' || value === 'add' || value === 'adjust';
}

/**
 * 打开「修改」弹窗时，支数输入框里应该填什么。
 *
 * ★ take / add 在库里存的是**带符号** delta（take 恒负、add 恒正），而弹窗收的是
 * **正整数**（后端按原类型解释：take 的 qty 是正数，落库才变负）。填 `delta`
 * （-2）会让「保存」原样发 -2 被后端拒 —— 这里取绝对值。
 * ★ adjust 原样（可正可负），0 也要如实带出来（用户自己改）。
 */
export function editableQtyOf(flow?: Pick<CryoFlowVO, 'flowType' | 'delta'> | null): number {
  if (!flow) {
    return 0;
  }
  const delta = flow.delta ?? 0;
  return isFlowKind(flow.flowType) && flow.flowType !== 'adjust' ? Math.abs(delta) : delta;
}

/**
 * 提交前的支数判据 —— 返回 i18n key 后缀（`lqg.cryo.flow.<key>`），合法返回 null。
 *
 * @param kind      本次操作的类型
 * @param qty       弹窗里填的支数
 * @param remaining 当前剩余（新增时必给；修改时给 null —— 改的是历史账，
 *                  「那一刻的余额」只有后端逐笔重算得出来，前端不许自己算）
 */
export function qtyProblem(kind: FlowKind, qty: number | null | undefined, remaining?: number | null): string | null {
  if (qty === null || qty === undefined || Number.isNaN(qty)) {
    return kind === 'adjust' ? 'qtyAdjustRequired' : kind === 'add' ? 'qtyAddRequired' : 'qtyTakeRequired';
  }
  if (!Number.isInteger(qty)) {
    return kind === 'adjust' ? 'qtyAdjustRequired' : kind === 'add' ? 'qtyAddRequired' : 'qtyTakeRequired';
  }
  if (kind === 'adjust') {
    return qty === 0 ? 'qtyAdjustRequired' : null;
  }
  if (qty <= 0) {
    return kind === 'add' ? 'qtyAddRequired' : 'qtyTakeRequired';
  }
  // ★ 取走的上限 = 当前剩余（ticket §2）；后端仍会硬拦，这里只是别让人白点一次
  if (kind === 'take' && remaining !== null && remaining !== undefined && qty > remaining) {
    return 'qtyOverRemaining';
  }
  return null;
}

/**
 * 盘点调整必填原因（权威 FLOW:F-CRYO-02.step3）；take / add 的用途可空。
 */
export function purposeProblem(kind: FlowKind, purpose?: string | null): string | null {
  if (kind !== 'adjust') {
    return null;
  }
  return (purpose ?? '').trim() ? null : 'purposeRequired';
}

/**
 * 拿失败原因：优先用**后端那句 msg**，拿不到才退回通用话术。
 *
 * 与 EMBED-WEB-001 的 `failText` 同款：`@/utils/request` 的拦截器对 500 / 601 走
 * `Promise.reject(new Error(msg))`（`e.message` 就是后端原话），对 400 是
 * `ElNotification.error(msg)` + `Promise.reject('error')`（原话已经弹过一次）。
 * 两条路都**不许静默** —— 被拒时要让用户看到「后端指出的是哪一笔」。
 */
export function failText(e: unknown, fallback: string): string {
  const msg = e instanceof Error ? e.message : typeof e === 'string' ? e : '';
  return msg && msg !== 'error' ? msg : fallback;
}
