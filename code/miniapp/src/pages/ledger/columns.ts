// 内部管理表格页的**列清单**（SAMPLE-MP-002 §2 / UI:mp.ledger / UI:mp.sample.list）。
//
// ★ 列名、列序只有一个来源：甲方四份 xlsx 模板原件第 1 行。
//   期望值抄在 `doc/verify/fixtures/ledger-columns-cases.json` 里（accept 第 2 条拿
//   `doc/verify/xlsx_header.py --print-header` 与它逐字 diff），本文件是**代码侧的那一份**，
//   `columns.fixture.spec.ts` 用 fixture 驱动逐表断「冻结列标签 + 其余列标签深相等」。
//   要改列名只能改甲方原件（再重生成 fixture），**不许改 fixture 迁就这里**。
//
// ★ 规则（fixture 的 `_doc` 逐字）：
//   冻结列 = `frozen`；其余列 = 模板第 1 行去掉冻结列后按原顺序，把 `inserted` 插到各自的 `after` 列后面，
//   再追加 `extra`。`inserted` 是甲方后来要求加、模板原件里没有的列（目前只有类器官收样记录的「代数」，
//   甲方 2026-09-24 第 18 行 / CR-20260924-10），模板列本身一个字不动。
//
// ★ 本文件四张表一次定完（ticket §2）：本张只**注册** tissue / organoid 两个工作表
//   （`sheets.ts`），embed / cryo 的列在这里备好，由 EMBED-MP-001 / CRYO-MP-001 注册。
//
// ★ 页面里不许再手写列清单：`pages/ledger/index.vue` 与 `components/lqg/LedgerTable.vue`
//   的列名只从这里来（accept 第 2 条的禁字 grep 断的就是这件事）。

/** 四张工作表（顺序 = 甲方模板顺序，也是顶部切换条的短名顺序） */
export const SHEET_KEYS = ['tissue', 'organoid', 'embed', 'cryo'] as const
export type SheetKey = (typeof SHEET_KEYS)[number]

/** 一列：`key` 是取数用的行字段名，`label` 是表头文案 */
export interface LedgerColumn {
  key: string
  label: string
}

/** 一张表的列结构：冻结列 + 其余列（顺序即显示顺序） */
export interface LedgerColumns {
  frozen: LedgerColumn
  columns: LedgerColumn[]
}

/**
 * 四张表的列清单。`label` 逐字对 fixture 的 `template` / `extra`，
 * `key` 是该列在行对象上的字段名（行来自 `/mp/int/sample/list` 或各域自己的 list）。
 */
const COLUMNS: Record<SheetKey, LedgerColumns> = {
  // 样本记录信息表（模板 A，14 列 → 冻结「内部编号」后 13 列，末尾追加「切片染色」）
  tissue: {
    frozen: { key: 'internalNo', label: '内部编号' },
    columns: [
      { key: 'sourceUnitName', label: '来源单位' },
      { key: 'donorNameMasked', label: '供体姓名' },
      { key: 'gender', label: '性别' },
      { key: 'age', label: '年龄' },
      { key: 'hospitalNo', label: '住院号' },
      { key: 'tissueType', label: '组织类型' },
      { key: 'receiveDate', label: '收样日期' },
      { key: 'isFixed', label: '有无固定' },
      { key: 'processTime', label: '处理时间' },
      { key: 'hasQcSheet', label: '质控表' },
      { key: 'hasViabilityReport', label: '细胞活率报告' },
      { key: 'operatorName', label: '操作人' },
      { key: 'remark', label: '备注' },
      // 追加列（模板里没有）：内容由 SAMPLE-HINT-001 接，本张留空
      { key: 'stainHint', label: '切片染色' },
    ],
  },
  // 类器官收样记录（模板 B，7 列 → 冻结「内部编号」后 6 列，「类器官类型」后插入「代数」，末尾追加「切片染色」）
  organoid: {
    frozen: { key: 'internalNo', label: '内部编号' },
    columns: [
      { key: 'sourceUnitName', label: '来源单位' },
      { key: 'organoidType', label: '类器官类型' },
      // 插入列（模板里没有，甲方 2026-09-24 第 18 行要加）：fixture 的 organoid.inserted
      { key: 'passage', label: '代数' },
      { key: 'receiveDate', label: '收样日期' },
      { key: 'processTime', label: '处理时间' },
      { key: 'hasViabilityReport', label: '细胞活率报告' },
      { key: 'operatorName', label: '操作人' },
      { key: 'stainHint', label: '切片染色' },
    ],
  },
  // 石蜡包埋送样记录（模板 C，16 列 → 冻结「石蜡块编号」后 15 列，无追加列）
  // 注册票 = EMBED-MP-001（本张只把列定下来，页面里不出现这张工作表）
  embed: {
    frozen: { key: 'paraffinBlockNo', label: '石蜡块编号' },
    columns: [
      { key: 'sampleSubmitNo', label: '样本编号' },
      { key: 'sampleType', label: '样本类型' },
      { key: 'organoidSourceType', label: '类器官来源类型' },
      { key: 'tissueReceiveTime', label: '组织收样时间' },
      { key: 'tissueProcessTime', label: '组织处理时间' },
      { key: 'agaroseEmbedTime', label: '琼脂糖包埋样本时间' },
      { key: 'embedBy', label: '包埋人' },
      { key: 'dehydrateTime', label: '脱水时间' },
      { key: 'agaroseSendTime', label: '琼脂糖包埋样本送样时间' },
      { key: 'paraffinEmbedTime', label: '石蜡包埋时间' },
      { key: 'sectionTime', label: '切片时间' },
      { key: 'stainTypes', label: '染色' },
      { key: 'markers', label: 'mark的表达情况' },
      { key: 'operatorName', label: '操作人' },
      { key: 'remark', label: '备注' },
    ],
  },
  // -80 冻存（模板 D，9 列 → 冻结「冻存样品」后 8 列，末尾追加「代数」「当前剩余/支」）
  // 注册票 = CRYO-MP-001
  cryo: {
    frozen: { key: 'cryoName', label: '冻存样品' },
    columns: [
      { key: 'freezeTime', label: '冻存时间' },
      { key: 'initQty', label: '冻存数量/支' },
      { key: 'density', label: '冻存密度' },
      { key: 'location80', label: '暂存-80度超低温冰箱' },
      { key: 'freezeBy', label: '冻存人' },
      { key: 'toLn2Time', label: '-80度超低温冰箱转移至液氮时间' },
      { key: 'ln2Location', label: '液氮储存位置' },
      { key: 'remark', label: '备注' },
      { key: 'passageNo', label: '代数' },
      { key: 'remainingQty', label: '当前剩余/支' },
    ],
  },
}

/** 这个字符串是不是四张工作表之一 */
export function isSheetKey(raw: unknown): raw is SheetKey {
  return typeof raw === 'string' && (SHEET_KEYS as readonly string[]).includes(raw)
}

/** 一张表的列清单；不认识的 sheet → null（调用方回落到第一个已注册的工作表） */
export function ledgerColumns(sheet: unknown): LedgerColumns | null {
  return isSheetKey(sheet) ? COLUMNS[sheet] : null
}
