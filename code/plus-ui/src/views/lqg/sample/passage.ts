// ============================================================================
// 类器官收样记录的「代数」（CR-20260924-10：甲方 2026-09-24 测试问题记录表第 18 行）
//
// 纯函数、不依赖任何东西（抽屉用它校验与提交，spec 直接跑）。规则与后端
// `SubmitSegmentRules.normalizePassage / isValidPassage` 一致：选填；填了必须形如 P3
// （`P` + 1~3 位数字，与冻存批次的代数同一规则），去首尾空白、开头的小写 p 转大写。
// 后端同一规则兜底（格式不对 400「代数请填 P 加数字，如 P3」），这里只是先给人话提示。
// ============================================================================

const PASSAGE_PATTERN = /^P\d{1,3}$/;

/** 代数的提交值：去首尾空白、开头的小写 p 转大写；没填 → null（选填） */
export function normalizePassage(value?: string | null): string | null {
  const v = (value ?? '').trim();
  if (!v) {
    return null;
  }
  return v.startsWith('p') ? `P${v.slice(1)}` : v;
}

/** 代数是否合规：没填（选填）或归一化后形如 P3 */
export function isPassageValue(value?: string | null): boolean {
  const v = normalizePassage(value);
  return v === null || PASSAGE_PATTERN.test(v);
}
