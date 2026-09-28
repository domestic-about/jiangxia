// ============================================================================
// 核验抽屉补填段（FIX V02b / issue #147）的单测
//
// 钉三件事：
//   1) 前端的两组键与后端 EmbedFillRules 逐字一致（直接读后端源码比对，改一边另一边红）；
//   2) 判为无效只带外部送样填的两项；
//   3) 「抽屉打开以来改过的实验室补填项」算得对 —— 没改的不误报（空行 / 空串 / 空数组），改了的一个不漏。
//
// 跑法（工作台根目录，即 code/plus-ui）：pnpm vitest run src/views/lqg/embed/verifyFill.spec.ts
// ============================================================================

import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { FILL_LAB_KEYS, FILL_SUBMIT_KEYS, fillText, invalidFill, labChanges } from './verifyFill';
import type { EmbedFillForm } from '@/api/lqg/embed';

/** 后端的键分组（从本文件往上五级 = code/） */
const BACKEND_RULES = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../../../../RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/embed/guard/EmbedFillRules.java'
);

const backendList = (name: string): string[] => {
  const src = readFileSync(BACKEND_RULES, 'utf8');
  const m = src.match(new RegExp(`${name}\\s*=\\s*List\\.of\\(([^)]*)\\)`));
  if (!m) {
    throw new Error(`EmbedFillRules.java 里找不到 ${name}`);
  }
  return [...m[1].matchAll(/"([^"]+)"/g)].map((x) => x[1]);
};

/** 待核验外部送样刚打开时抽屉里的补填段（fillPayload 的形状：空 marker 行已被过滤） */
const opened = (): EmbedFillForm => ({
  sampleType: '组织',
  organoidSourceType: null,
  tissueReceiveTime: null,
  tissueProcessTime: null,
  agaroseEmbedTime: null,
  embedBy: null,
  dehydrateTime: null,
  agaroseSendTime: null,
  paraffinEmbedTime: null,
  sectionTime: null,
  stainTypes: [],
  stainOther: null,
  markers: [],
  operatorName: null,
  remark: null
});

describe('补填段的两组键 = 后端 EmbedFillRules', () => {
  it('外部送样两项、实验室补填 13 项，与后端逐字同序', () => {
    expect([...FILL_SUBMIT_KEYS]).toEqual(backendList('SUBMIT_KEYS'));
    expect([...FILL_LAB_KEYS]).toEqual(backendList('LAB_KEYS'));
    expect(FILL_SUBMIT_KEYS.length + FILL_LAB_KEYS.length).toBe(15);
    expect(FILL_LAB_KEYS.some((k) => (FILL_SUBMIT_KEYS as readonly string[]).includes(k))).toBe(false);
  });
});

describe('invalidFill：判为无效只带外部送样填的两项', () => {
  it('其余 13 项一个都不带', () => {
    const now = { ...opened(), sampleType: '组织（更正）', dehydrateTime: '2026-09-19', remark: 'x', stainTypes: ['HE'] };
    expect(invalidFill(now)).toEqual({ sampleType: '组织（更正）', organoidSourceType: null });
  });
});

describe('labChanges：抽屉打开以来改过的实验室补填项', () => {
  it('什么都没改 → 空（空串、空白、null、空数组互相等价，不误报）', () => {
    const now = { ...opened(), remark: '  ', stainOther: '', stainTypes: null, markers: null };
    expect(labChanges(opened(), now)).toEqual([]);
  });

  it('只改了外部送样那两项 → 不算实验室补填', () => {
    expect(labChanges(opened(), { ...opened(), sampleType: '组织（更正）', organoidSourceType: '肝' })).toEqual([]);
  });

  it('改了工序时间、染色、marker、备注 → 按抽屉顺序全部列出，一个不漏', () => {
    const now: EmbedFillForm = {
      ...opened(),
      dehydrateTime: '2026-09-19',
      stainTypes: ['HE'],
      markers: [{ markerName: 'Ki67', expression: 'strong' }],
      remark: '核验时补的备注'
    };
    expect(labChanges(opened(), now)).toEqual(['dehydrateTime', 'stainTypes', 'markers', 'remark']);
  });

  it('清掉原有的值也算改过（带了空值 = 清空，判无效不收）', () => {
    const before = { ...opened(), sectionTime: '2026-09-21', markers: [{ markerName: 'CK19', expression: 'negative' }] };
    expect(labChanges(before, { ...before, sectionTime: null, markers: [] })).toEqual(['sectionTime', 'markers']);
  });

  it('marker 名称只差首尾空白不算改过；表达变了算', () => {
    const before = { ...opened(), markers: [{ markerName: 'Ki67', expression: 'strong' }] };
    expect(labChanges(before, { ...before, markers: [{ markerName: ' Ki67 ', expression: 'strong' }] })).toEqual([]);
    expect(labChanges(before, { ...before, markers: [{ markerName: 'Ki67', expression: 'weak' }] })).toEqual(['markers']);
  });
});

describe('fillText：本次新增的两句文案', () => {
  it('按语言取、替换占位；不认识的语言按中文', () => {
    expect(fillText('zh_CN', 'invalidDropsLab', { fields: '脱水时间、染色' })).toContain('脱水时间、染色');
    expect(fillText('en_US', 'invalidDropsLab', { fields: 'Dehydration time' })).toContain('Dehydration time');
    expect(fillText('ja_JP', 'fillHint')).toBe(fillText('zh_CN', 'fillHint'));
  });
});
