// ============================================================================
// 类器官质量评分表的**纯函数**（QC-WEB-002 / FLOW:F-QC-01.step4）
//
// 口径（三处一致：票面 §0.2、field-ssot.yaml t_lqg_qc_score.total_score、fixture 头注释）：
//   1) 四个变量各选一档；**没选 = null / 空串**，不是「0 分」。
//   2) 0 分是**选了 0 分那一档**（如 `>14d` = 0、`<100` = 0）—— 判「有没有选」只能用
//      `== null`，**不能**用 `if (!item)`（后者会把 0 当成没选，items 与 total 一起错）。
//   3) 任一项没选 → 该项分值 null，**合计也 null**（不是把已选的加起来）。
//   4) 页面上的分值与合计只是**即时反馈**；保存时只提交四个档位，落库分值由后端按
//      `sys_dict_data.remark` 回填（QcScoreDictionary），前端传的分值一个都不收。
//   5) 不出「偏差 / 中等 / 良好」的结论 —— 那句注是文档页脚的固定文字（FIELD 锚原文）。
//
// 分值的唯一来源是字典 remark（`useDict` 的 `remark` 字段）。本文件**不写死任何分值**。
// ============================================================================

/** 四个变量（顺序 = 模板顺序 = 页面与合计的列序；改顺序要同步 fixture） */
export const SCORE_KEYS = ['preCulture', 'cultureDays', 'organoidCount', 'diameter'] as const;

export type ScoreLevelKey = (typeof SCORE_KEYS)[number];

/** 四个档位（`null` / 缺失 / 空串 = 这一项没选） */
export type ScoreLevels = Partial<Record<ScoreLevelKey, string | null | undefined>>;

/**
 * 字典里读出来的「档位 → 分值」（`dictType` 一层的 map）。
 * ★ `remark` 在 `sys_dict_data` 里是 **VARCHAR**，所以这里接受字符串（`'8'`），
 *   与 fixture 里的数字一并按数字解析。
 */
export type ScoreDictScores = Partial<Record<ScoreLevelKey, Record<string, number | string | null | undefined> | null | undefined>>;

export interface ScoreSummary {
  /** 四项各自的分值，顺序 = `SCORE_KEYS`；没选（或字典没配这一档）= null */
  items: (number | null)[];
  /** 合计 = 四项之和；**任一项为 null 就是 null** */
  total: number | null;
}

/** 把字典 remark / fixture 的数字统一成「分值或 null」；空串与 null 都是「没有」。 */
const toScore = (raw: number | string | null | undefined): number | null => {
  if (raw === null || raw === undefined || raw === '') {
    return null;
  }
  const value = typeof raw === 'number' ? raw : Number(String(raw).trim());
  return Number.isFinite(value) ? value : null;
};

/**
 * 即时反馈：按当前四个档位与字典分值算出「各项分值 + 合计」。
 *
 * @param levels     当前选中的四个档位（`null` = 没选）
 * @param dictScores 字典 remark 解析出来的「档位 → 分值」
 */
export function scoreSummary(levels: ScoreLevels | null | undefined, dictScores: ScoreDictScores | null | undefined): ScoreSummary {
  const items = SCORE_KEYS.map((key) => {
    const level = levels?.[key];
    if (level === null || level === undefined || level === '') {
      // ★ 没选 —— 返回 null（不是 0）
      return null;
    }
    return toScore(dictScores?.[key]?.[level]);
  });
  // ★ 0 分档照算：只有 null 才算「没选」，`items.some((i) => i === null)`。
  const total = items.some((item) => item === null) ? null : (items as number[]).reduce((sum, item) => sum + item, 0);
  return { items, total };
}

/** 四个档位的字典类型（页面上 `useDict(...)` 用的就是这四个；顺序同 `SCORE_KEYS`） */
export const SCORE_DICT_TYPES: Record<ScoreLevelKey, string> = {
  preCulture: 'lqg_score_pre_culture',
  cultureDays: 'lqg_score_culture_days',
  organoidCount: 'lqg_score_count',
  diameter: 'lqg_score_diameter'
};

/** 四项里还没选的（给「合计为空」时的一句解释用；不出任何质量等级结论） */
export function missingKeys(levels: ScoreLevels | null | undefined): ScoreLevelKey[] {
  return SCORE_KEYS.filter((key) => {
    const level = levels?.[key];
    return level === null || level === undefined || level === '';
  });
}
