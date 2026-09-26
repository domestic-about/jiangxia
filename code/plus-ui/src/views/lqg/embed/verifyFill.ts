// ============================================================================
// 核验抽屉的补填段（纯函数）· FIX V02b / issue #147
//
// 病灶：核验抽屉里工序时间、包埋人、染色、marker、操作人、备注、样本类型都显示成可填，
// 点「判为有效并保存」请求体却只有 {action, paraffinBlockNo}，填的内容被静默丢弃。
// 修法：PUT /lqg/embed/{id}/verify 带上 `fill`（补丁语义同 PUT /lqg/embed），后端与核验结论同一事务保存。
//
// 两组键（与后端 EmbedFillRules 逐字一致；口径 UI:mp.embed.form、FLOW:F-EMBED-01.step6 / step7）：
//   · FILL_SUBMIT_KEYS：外部送样填的两项 —— 判为有效、判为无效都随核验一起保存；
//   · FILL_LAB_KEYS：实验室补填的 13 项（「实验室核验有效后填」）—— 只随「判为有效并保存」保存；
//     判为无效不收（后端带了就 400），所以抽屉在判无效前把「改过却不会保存」的几项明说出来，不静默丢。
//
// ★ 为什么单独放在 .ts：组件里没法单测（verifyFill.spec.ts 直接跑这几个函数）。
// ============================================================================

import type { EmbedFillForm } from '@/api/lqg/embed';

/** 外部送样填的两项：判为有效、判为无效都随核验一起保存 */
export const FILL_SUBMIT_KEYS = ['sampleType', 'organoidSourceType'] as const;

/** 实验室补填的 13 项：只随「判为有效并保存」一起保存 */
export const FILL_LAB_KEYS = [
  'tissueReceiveTime',
  'tissueProcessTime',
  'agaroseEmbedTime',
  'embedBy',
  'dehydrateTime',
  'agaroseSendTime',
  'paraffinEmbedTime',
  'sectionTime',
  'stainTypes',
  'stainOther',
  'markers',
  'operatorName',
  'remark'
] as const;

export type FillSubmitKey = (typeof FILL_SUBMIT_KEYS)[number];
export type FillLabKey = (typeof FILL_LAB_KEYS)[number];

/** 比较用的规范形：空串 / undefined = null、文本去首尾空白、marker 只看名称与表达 */
const canonical = (key: FillLabKey, value: unknown): unknown => {
  if (value === undefined || value === null || value === '') {
    return key === 'stainTypes' || key === 'markers' ? [] : null;
  }
  if (key === 'markers') {
    return (value as Array<{ markerName?: string | null; expression?: string | null }>).map((m) => ({
      markerName: (m?.markerName ?? '').trim() || null,
      expression: m?.expression || null
    }));
  }
  if (typeof value === 'string') {
    return value.trim() || null;
  }
  return value;
};

/**
 * 实验室补填的 13 项里，抽屉打开以来**改过**的那几项（按抽屉里的顺序）。
 *
 * @param before 抽屉打开时的补填段快照
 * @param now    现在的补填段
 */
export function labChanges(before: EmbedFillForm, now: EmbedFillForm): FillLabKey[] {
  return FILL_LAB_KEYS.filter((key) => JSON.stringify(canonical(key, before?.[key])) !== JSON.stringify(canonical(key, now?.[key])));
}

/** 判为无效时随原因一起保存的补填段：只有外部送样填的两项 */
export function invalidFill(now: EmbedFillForm): EmbedFillForm {
  return { sampleType: now?.sampleType ?? null, organoidSourceType: now?.organoidSourceType ?? null };
}

/**
 * 本次修复新增的两句界面文案。
 *
 * ★ 按 SYS-WEB-001 的约定它们应放进 `src/lang/lqg/embed.zh_CN.ts` / `embed.en_US.ts` 的 `drawer` 段
 *   （建议键名 `drawer.fillHint` / `drawer.invalidDropsLab`）；本次修复的文件归属只到
 *   `views/lqg/embed/**` 与 api 文件，先放在这里按当前语言取，由协调方挪过去后删掉本段即可。
 */
export const VERIFY_FILL_TEXT = {
  zh_CN: {
    fillHint:
      '工序时间、包埋人、染色、marker、操作人、备注可在这里一并补填，随「判为有效并保存」同一次保存；判为无效只保存原因和样本类型、类器官来源类型。',
    invalidDropsLab: '判为无效不会保存你在抽屉里补填的：{fields}（这些在核验有效后才补填）。要保存它们，请取消后点「判为有效并保存」。'
  },
  en_US: {
    fillHint:
      'Process times, embedded by, stain, markers, operator and remark can be filled here and are saved together with "Approve and save"; "Reject" only saves the reason plus the sample type / organoid source type.',
    invalidDropsLab:
      '"Reject" will not save what you filled in the drawer: {fields} (these are filled after approval). To keep them, cancel and use "Approve and save".'
  }
} as const;

export type VerifyFillTextKey = keyof (typeof VERIFY_FILL_TEXT)['zh_CN'];

/** 按当前语言取本次新增的文案（不认识的语言按中文） */
export function fillText(locale: string, key: VerifyFillTextKey, params: Record<string, string> = {}): string {
  const dict = locale === 'en_US' ? VERIFY_FILL_TEXT.en_US : VERIFY_FILL_TEXT.zh_CN;
  return dict[key].replace(/\{(\w+)\}/g, (_, name: string) => params[name] ?? '');
}
