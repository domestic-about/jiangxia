// ============================================================================
// 染色切换逻辑（纯函数）· EMBED-WEB-001
//
// 规则（FLOW:F-EMBED-01.step3 / ticket §0 口径复述 3；验收用例见
// doc/verify/fixtures/stain-toggle-cases.json，EMBED-WEB-001 / EMBED-MP-001 共用）：
//   · 染色是五个按钮：HE / IF / IHC / OTHER / NONE，多选；
//   · 「无染色 NONE」与其余四个**互斥** —— 点 NONE 清掉其余，点其余任何一个清掉 NONE；
//   · 再点一次**已选中**的按钮 = 取消它；
//   · 字典外的值（比如 PAS）**原样忽略**：不抛错也不改现有选择。
//   · 输出一律按 HE, IF, IHC, OTHER, NONE 的**固定顺序**（后端 StainRules.ORDER 同序），
//     不按点击顺序 —— 后端落库也会再排一次，但前端先排好，界面上的选中态才稳定。
//
// ★ 为什么单独放在 .ts 而不是写在组件里：它是本票唯一有 fixture 的判据
//   （`stain.fixture.spec.ts` 直接读那份 JSON 跑），组件里没法单测。
// ============================================================================

/** 五个染色 value，固定顺序（与后端 StainRules.ORDER、字典 lqg_stain_type 一致） */
export const STAIN_ORDER: string[] = ['HE', 'IF', 'IHC', 'OTHER', 'NONE'];

/** 与其余四个互斥的那个值 */
export const STAIN_NONE = 'NONE';

/** 必须写具体名称的那个值 */
export const STAIN_OTHER = 'OTHER';

/**
 * 一次点击后的新选中数组。
 *
 * @param current 当前选中的染色 value 数组（可空 / 可含重复 / 可含字典外值）
 * @param clicked 本次点击的 value
 * @returns 新的选中数组（固定顺序；点已选中项 = 去掉它；字典外的值 = 原样返回 current 的规范化副本）
 */
export function toggleStain(current: readonly string[] | null | undefined, clicked: string): string[] {
  const picked = new Set<string>((current ?? []).filter((value) => STAIN_ORDER.includes(value)));
  const target = typeof clicked === 'string' ? clicked.trim() : '';
  // 字典外的值一律忽略：保留现有选择（fixture 最后一条：current ['HE'] + 点 'PAS' → ['HE']）
  if (!STAIN_ORDER.includes(target)) {
    return sortStains(picked);
  }
  if (picked.has(target)) {
    // 再点一次已选中 = 取消
    picked.delete(target);
  } else {
    picked.add(target);
    if (target === STAIN_NONE) {
      // 点「无染色」清掉其余四个
      for (const value of STAIN_ORDER) {
        if (value !== STAIN_NONE) {
          picked.delete(value);
        }
      }
    } else {
      // 点其余任何一个清掉「无染色」
      picked.delete(STAIN_NONE);
    }
  }
  return sortStains(picked);
}

/** 去重后按固定顺序输出 */
export function sortStains(values: Iterable<string>): string[] {
  const picked = new Set(values);
  return STAIN_ORDER.filter((value) => picked.has(value));
}

/** 这组染色里有没有 OTHER（有 → 必须写具体名称，参照后端 StainRules.hasOther） */
export function hasOtherStain(values: readonly string[] | null | undefined): boolean {
  return (values ?? []).includes(STAIN_OTHER);
}

/**
 * 提交前的自检：与后端 `StainRules.normalize` 同一组判据的前半段（值合法 + 互斥），
 * 但**只报错不规范化** —— 后端才是权威，这里只是让用户少跑一趟。
 *
 * @returns 错误文案的 i18n key 后缀（'' = 没毛病）
 */
export function stainProblem(values: readonly string[] | null | undefined, stainOther?: string | null): string {
  const picked = (values ?? []).filter((value) => STAIN_ORDER.includes(value));
  if (picked.includes(STAIN_NONE) && picked.length > 1) {
    return 'stainNoneExclusive';
  }
  if (picked.includes(STAIN_OTHER) && !(stainOther ?? '').trim()) {
    return 'stainOtherRequired';
  }
  return '';
}
