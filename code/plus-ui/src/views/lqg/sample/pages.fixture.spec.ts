// ============================================================================
// 工作台两张样本表的列清单与路由映射 · CR-20260924-10
//
// 列的期望值**不在这个文件里**：直接读需求层的 `doc/verify/fixtures/ledger-columns-cases.json`
// （小程序表格页、导出表头对账共用那一份；模板列由 accept 拿甲方 xlsx 原件逐字 diff）。
// 本文件断：工作台「模板列 + 插入列」那一段的表头文案（zh_CN）逐字 = fixture 的
// 「模板去掉冻结列 → 插入 inserted」；冻结列（内部编号）排最前；fixture 的追加列（切片染色）也在。
//
// 跑法（工作台根目录，即 code/plus-ui）：
//   pnpm vitest run src/views/lqg/sample/pages.fixture.spec.ts
// ============================================================================

import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import zh from '@/lang/lqg/sample.zh_CN';
import {
  SAMPLE_KINDS,
  frozenColumn,
  normalizeSampleKind,
  sampleColumns,
  sampleKindOfPath,
  samplePageOf,
  templateColumns
} from './pages';
import { isPassageValue, normalizePassage } from './passage';

/** 需求层 fixture 的绝对路径（从本文件往上五级 = 工作区根） */
const FIXTURE = resolve(dirname(fileURLToPath(import.meta.url)), '../../../../../../doc/verify/fixtures/ledger-columns-cases.json');

interface InsertedColumn {
  label: string;
  after: string;
  source: string;
}

interface SheetCase {
  template: string[];
  frozen: string;
  inserted?: InsertedColumn[];
  extra: string[];
}

const fixture = JSON.parse(readFileSync(FIXTURE, 'utf8')) as { sheets: Record<string, SheetCase> };

/** `lqg.sample.col.xxx` → zh_CN 文案 */
const zhLabel = (key: string): string => {
  const path = key.replace(/^lqg\.sample\./, '').split('.');
  let node: any = zh;
  for (const part of path) {
    node = node?.[part];
  }
  if (typeof node !== 'string') {
    throw new Error(`i18n 缺键：${key}`);
  }
  return node;
};

/** fixture 规则：模板去掉冻结列 → 把 inserted 插到各自 after 后面（追加列另断） */
const templateWithInserted = (s: SheetCase): string[] => {
  const out = s.template.filter((label) => label !== s.frozen);
  for (const col of s.inserted ?? []) {
    out.splice(out.indexOf(col.after) + 1, 0, col.label);
  }
  return out;
};

describe('两张样本表的列（fixture 驱动）', () => {
  SAMPLE_KINDS.forEach((kind) => {
    const sheet = fixture.sheets[kind];

    it(`${kind}：模板列 + 插入列的表头逐字等于 fixture（甲方原件第 1 行 + 甲方后加的列）`, () => {
      expect(templateColumns(kind).map((c) => zhLabel(c.labelKey))).toEqual(templateWithInserted(sheet));
    });

    it(`${kind}：冻结列「${sheet.frozen}」排第一；fixture 的追加列都在；列不重复`, () => {
      const labels = sampleColumns(kind).map((c) => zhLabel(c.labelKey));
      expect(labels[0]).toBe(sheet.frozen);
      expect(zhLabel(frozenColumn().labelKey)).toBe(sheet.frozen);
      sheet.extra.forEach((label) => expect(labels).toContain(label));
      expect(new Set(labels).size).toBe(labels.length);
      // 类别由页面钉死：表里不再有「类别」一列
      expect(labels).not.toContain(zh.col.sampleKind);
    });
  });

  it('代数只出现在类器官收样记录，且紧跟「类器官类型」（甲方 2026-09-24 第 18 行）', () => {
    const organoid = templateColumns('organoid').map((c) => c.key);
    expect(organoid.indexOf('passage')).toBe(organoid.indexOf('organoidType') + 1);
    expect(sampleColumns('tissue').map((c) => c.key)).not.toContain('passage');
  });
});

describe('类别 → 页面（首页卡片 / 最近提交 / 菜单角标 / 质控页返回共用）', () => {
  it('两页的路径与菜单迁移 V202609281010 一致', () => {
    expect(samplePageOf('tissue').path).toBe('/sample');
    expect(samplePageOf('organoid').path).toBe('/sample-organoid');
    expect(zhLabel(samplePageOf('tissue').titleKey)).toBe('样本记录信息表');
    expect(zhLabel(samplePageOf('organoid').titleKey)).toBe('类器官送样记录');
  });

  it('不认识的类别按组织样本（老链接 /sample 本来就落在样本记录信息表）', () => {
    expect(normalizeSampleKind(undefined)).toBe('tissue');
    expect(normalizeSampleKind('bogus')).toBe('tissue');
    expect(samplePageOf(null).path).toBe('/sample');
  });

  it('菜单路径 → 类别（角标按页分开）；别的页面不是样本表', () => {
    expect(sampleKindOfPath('/sample')).toBe('tissue');
    expect(sampleKindOfPath('/sample/')).toBe('tissue');
    expect(sampleKindOfPath('/sample?verifyStatus=pending')).toBe('tissue');
    expect(sampleKindOfPath('/sample-organoid')).toBe('organoid');
    expect(sampleKindOfPath('/embed')).toBeNull();
    expect(sampleKindOfPath('/cryo')).toBeNull();
    expect(sampleKindOfPath(undefined)).toBeNull();
  });
});

describe('代数格式（与后端 SubmitSegmentRules 同一规则）', () => {
  it('选填；填了必须形如 P3，小写 p 转大写', () => {
    for (const ok of [null, undefined, '', '  ', 'P3', 'p3', ' P12 ', 'P999']) {
      expect(isPassageValue(ok), String(ok)).toBe(true);
    }
    for (const bad of ['3', '第3代', 'P1234', 'P', 'P3a']) {
      expect(isPassageValue(bad), bad).toBe(false);
    }
    expect(normalizePassage(' p4 ')).toBe('P4');
    expect(normalizePassage('   ')).toBeNull();
  });
});
