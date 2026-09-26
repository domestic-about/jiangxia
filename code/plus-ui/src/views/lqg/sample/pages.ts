// ============================================================================
// 工作台两张样本表（CR-20260924-10 · 甲方 2026-09-24 测试问题记录表第 25 行）
//
// 甲方原话：「组织样本和类器官样本同在一张表」，应该是分开的表，导出4个EXCEL表。
// Kevin 拍板：原「样本总表」拆成两个菜单页 ——
//   样本记录信息表（sample_kind = tissue）   /sample            菜单 5210（沿用原路由）
//   类器官收样记录（sample_kind = organoid） /sample-organoid   菜单 5220（V202609281010 新增）
// 两页是同一个列表（index.vue；organoid.vue 只是 `<SampleIndex kind="organoid" />`），类别由页面钉死：
// 筛选里没有「类别」，列按各自模板排。
//
// ★ 本文件是「类别 → 页面」的唯一映射：首页待办卡、最近提交、侧边菜单角标、质控文档页的「返回」
//   都从这里取路径与名字，不各写一份字面量（改路由只改这一处 + 菜单迁移）。
//
// ★ 列清单（sampleColumns）按「模板列 + 插入列 + 追加列」拼：
//   模板列 = 甲方 xlsx 原件第 1 行（期望值在 doc/verify/fixtures/ledger-columns-cases.json，
//   pages.fixture.spec.ts 逐字对它）；「内部编号」是冻结列，挪到最前面当行的主键列（与小程序表格页同一规则）；
//   插入列 = 甲方后来要求加、模板里没有的列（类器官收样记录「类器官类型」后的「代数」，第 18 行）；
//   追加列 = 工作台自己的管理列（送检单号 / 来源 / 核验状态在前，有无病理或备注、提交人、组别、
//   切片染色、最后修改在后；「石蜡包埋 / 冻存」与「操作」两列在页面里单独写，固定在右侧 ——
//   2026-09-24 本机验收：关联记录的数量与入口在「石蜡包埋 / 冻存」一列（relation.ts），「操作」只放对样本本身的动作）。
// ============================================================================

export type SampleKind = 'tissue' | 'organoid';

export const SAMPLE_KINDS: readonly SampleKind[] = ['tissue', 'organoid'] as const;

/** 一张样本表页：路由路径 + 页面名字（i18n 键） */
export interface SamplePage {
  /** 路由路径（= 菜单的 path，顶级菜单 → `/` + path） */
  path: string;
  /** 页面名字（菜单名 / 标题 / 「返回××」里的××） */
  titleKey: string;
}

const PAGES: Record<SampleKind, SamplePage> = {
  tissue: { path: '/sample', titleKey: 'lqg.sample.page.tissue.title' },
  organoid: { path: '/sample-organoid', titleKey: 'lqg.sample.page.organoid.title' }
};

/** 不认识的类别（历史脏值 / 缺失）按组织样本处理 —— 老链接 `/sample` 本来就落在样本记录信息表 */
export const normalizeSampleKind = (kind: unknown): SampleKind => (kind === 'organoid' ? 'organoid' : 'tissue');

/** 这一类样本在工作台的哪一页 */
export const samplePageOf = (kind: unknown): SamplePage => PAGES[normalizeSampleKind(kind)];

/**
 * 侧边菜单角标 / 路由路径 → 这一页的样本类别；不是两张样本表的路径 → null。
 * （路径可能带 query、末尾斜杠，也可能带父级前缀）
 */
export const sampleKindOfPath = (path?: string): SampleKind | null => {
  const raw = (path || '').split('?')[0].replace(/\/+$/, '');
  if (raw.endsWith(PAGES.organoid.path)) {
    return 'organoid';
  }
  if (raw.endsWith(PAGES.tissue.path)) {
    return 'tissue';
  }
  return null;
};

// ── 列清单 ──────────────────────────────────────────────────────────────────

/** 单元格怎么画（index.vue 按它分支；新列先看能不能复用已有的一种） */
export type SampleCell =
  | 'text' // 纯文本，空显示「—」
  | 'mono' // 编号类（内部编号、送检单号），等宽字体
  | 'flag' // 有无按钮：Y / N → 有 / 无
  | 'gender' // 字典 lqg_gender
  | 'submitSource' // 字典 lqg_submit_source（内 / 外部徽标）
  | 'verifyStatus' // 字典 lqg_verify_status
  | 'hint' // 切片染色提示徽标组（HintBadges）
  | 'updateTime'; // 最后修改：null = 从未修改

export interface SampleColumn {
  /** 行上的字段名 */
  key: string;
  /** 表头文案的 i18n 键（lqg.sample.col.*） */
  labelKey: string;
  cell: SampleCell;
  width?: number;
  minWidth?: number;
  align?: 'center';
  /** 超长省略 + 悬停看全文 */
  tooltip?: boolean;
}

const col = (key: string, cell: SampleCell, extra: Partial<SampleColumn> = {}): SampleColumn => ({
  key,
  labelKey: `lqg.sample.col.${key}`,
  cell,
  ...extra
});

/** 冻结列：内部编号（行的主键列，排在最前；它在模板里的位置由这里代表，与小程序表格页同一规则） */
const FROZEN: SampleColumn = col('internalNo', 'mono', { width: 120, tooltip: true });

/** 前置管理列：送检单号、来源（内 / 外部）、核验状态 */
const LEAD: SampleColumn[] = [
  col('submitNo', 'mono', { width: 140, tooltip: true }),
  col('submitSource', 'submitSource', { width: 90, align: 'center' }),
  col('verifyStatus', 'verifyStatus', { width: 100, align: 'center' })
];

/**
 * 模板列（去掉冻结列「内部编号」后的原顺序）+ 插入列。
 * ★ 表头文案逐字 = 甲方 xlsx 原件第 1 行（pages.fixture.spec.ts 对 fixture 断），改文案只能改模板原件。
 */
const TEMPLATE: Record<SampleKind, SampleColumn[]> = {
  // 样本记录信息表（14 列 → 去掉「内部编号」13 列）
  tissue: [
    col('sourceUnitName', 'text', { minWidth: 130, tooltip: true, labelKey: 'lqg.sample.col.sourceUnit' }),
    col('donorName', 'text', { width: 110, tooltip: true }),
    col('gender', 'gender', { width: 80, align: 'center' }),
    col('age', 'text', { width: 80, align: 'center' }),
    col('hospitalNo', 'text', { width: 140, tooltip: true }),
    col('tissueType', 'text', { width: 140, tooltip: true }),
    col('receiveDate', 'text', { width: 115, align: 'center' }),
    col('isFixed', 'flag', { width: 100, align: 'center' }),
    col('processTime', 'text', { width: 165, tooltip: true }),
    col('hasQcSheet', 'flag', { width: 100, align: 'center' }),
    col('hasViabilityReport', 'flag', { width: 125, align: 'center' }),
    col('operatorName', 'text', { width: 100, tooltip: true }),
    col('remark', 'text', { minWidth: 120, tooltip: true })
  ],
  // 类器官收样记录（7 列 → 去掉「内部编号」6 列；「类器官类型」后插入「代数」，甲方 2026-09-24 第 18 行）
  organoid: [
    col('sourceUnitName', 'text', { minWidth: 130, tooltip: true, labelKey: 'lqg.sample.col.sourceUnit' }),
    col('organoidType', 'text', { width: 150, tooltip: true }),
    col('passage', 'mono', { width: 80, align: 'center' }),
    col('receiveDate', 'text', { width: 115, align: 'center' }),
    col('processTime', 'text', { width: 165, tooltip: true }),
    col('hasViabilityReport', 'flag', { width: 125, align: 'center' }),
    col('operatorName', 'text', { width: 100, tooltip: true })
  ]
};

/** 后置管理列：模板里没有、工作台一直有的列（组织样本多一个「有无病理」，类器官多一个「备注」） */
const TAIL: Record<SampleKind, SampleColumn[]> = {
  tissue: [col('hasPathology', 'flag', { width: 105, align: 'center' })],
  organoid: [col('remark', 'text', { minWidth: 120, tooltip: true })]
};

const COMMON_TAIL: SampleColumn[] = [
  col('submitterName', 'text', { width: 110, tooltip: true }),
  col('groupName', 'text', { width: 120, tooltip: true }),
  // 切片染色提示（SAMPLE-HINT-001）：读时计算、不可编辑
  col('hint', 'hint', { width: 200 }),
  col('updateTime', 'updateTime', { width: 170, tooltip: true })
];

/** 模板列 + 插入列（不含冻结列与管理列）—— spec 拿它对 fixture */
export const templateColumns = (kind: SampleKind): SampleColumn[] => TEMPLATE[kind];

/** 冻结列 */
export const frozenColumn = (): SampleColumn => FROZEN;

/** 整张表的列（「石蜡包埋 / 冻存」与「操作」两列不在这里，页面里单独固定在右侧） */
export const sampleColumns = (kind: SampleKind): SampleColumn[] => [
  FROZEN,
  ...LEAD,
  ...TEMPLATE[kind],
  ...TAIL[kind],
  ...COMMON_TAIL
];
