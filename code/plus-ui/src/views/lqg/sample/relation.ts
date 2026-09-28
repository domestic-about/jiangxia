// ============================================================================
// 工作台四张表之间的来回跳转（Kevin 2026-09-24 本机验收「四种表之间的关系目前看着有点乱」）
//
// 业务关系：一个样本（样本记录信息表的组织样本 / 类器官收样记录）名下可以有多个石蜡块
// （石蜡包埋送样记录）和多个冻存批次（-80 冻存记录）。所以：
//   · 样本两页：「操作」列只放对这一条样本本身的动作（编辑 / 核验、质控文档、删除）；
//     它名下的石蜡块、冻存批次单独一列「石蜡包埋 / 冻存」，写数量、数字可点（本文件 relationOf）；
//   · 点过去：石蜡包埋 / 冻存管理页带着 sampleId 打开，顶部提示条写明「只看××的记录 · 共 N 条」，
//     「看全部」清掉这个筛选，「新增」默认挂这个样本（本文件 sampleScopeLabel / sampleIdOfQuery）；
//   · 点回来：那两页每行的样本编号是链接，按样本类别回到它所在的那一页并打开它（本文件 sampleOf）。
//
// ★ 路径只在这里写一次（石蜡包埋 = 菜单 5310 的 path、冻存 = 5410 的 path；样本两页取 pages.ts），
//   页面与徽标组件都从这里拿，不各写一份字面量。
// ★ 纯函数、不依赖 Vue / 请求（relation.spec.ts 直接跑）。
// ============================================================================

import { samplePageOf } from './pages';

/** 石蜡包埋页（菜单 5310 的 path 'embed'，顶级 = /embed；不是 /lqg/embed） */
export const EMBED_PATH = '/embed';

/** 冻存管理页（菜单 5410 的 path 'cryo'，顶级 = /cryo） */
export const CRYO_PATH = '/cryo';

/** router.push 能直接吃的目标（query 一律是字符串） */
export interface RouteTarget {
  path: string;
  query: Record<string, string>;
}

type Id = string | number;

/** 核验状态 / 提交来源的合法值（路由 query 只认这几个，别把任意串塞进查询参数） */
export const VERIFY_STATUSES = ['pending', 'valid', 'invalid'] as const;
export const SUBMIT_SOURCES = ['internal', 'external'] as const;

/**
 * 某个样本的石蜡包埋记录。
 *
 * ★ 跳转参数是 **sampleId**（后端 `EmbedQueryBo.sampleId` 的既有筛选），不是内部编号 ——
 *   待核验样本还没有内部编号，用编号跳会筛出空页（SAMPLE-WEB-001 立的口径）。
 * @param opts.pending 只看合作单位送来、还待核验的（= 关联列「待核验 N」）
 * @param opts.add     到了就打开「新增」抽屉，所挂样本已预选好（关联列数量为 0 时的「新增」入口）
 */
export const embedOfSample = (sampleId: Id, opts: { pending?: boolean; add?: boolean } = {}): RouteTarget => {
  const query: Record<string, string> = { sampleId: String(sampleId) };
  if (opts.pending) {
    query.verifyStatus = 'pending';
  }
  if (opts.add) {
    query.add = '1';
  }
  return { path: EMBED_PATH, query };
};

/** 某个样本的冻存批次（同样用 sampleId；`add` 同上） */
export const cryoOfSample = (sampleId: Id, opts: { add?: boolean } = {}): RouteTarget => {
  const query: Record<string, string> = { sampleId: String(sampleId) };
  if (opts.add) {
    query.add = '1';
  }
  return { path: CRYO_PATH, query };
};

/**
 * 从石蜡包埋 / 冻存的一行回到它的样本：组织样本回「样本记录信息表」、类器官回「类器官收样记录」，
 * 到了那一页直接打开这条样本的抽屉（样本页读 `?sampleId=`）。
 */
export const sampleOf = (kind: unknown, sampleId: Id): RouteTarget => ({
  path: samplePageOf(kind).path,
  query: { sampleId: String(sampleId) }
});

/** 路由 query 里的样本 id：只认纯数字（雪花 id 超过 JS 安全整数，按字符串带着走） */
export const sampleIdOfQuery = (value: unknown): string | null => {
  const raw = Array.isArray(value) ? value[0] : value;
  if (typeof raw !== 'string' && typeof raw !== 'number') {
    return null;
  }
  const text = String(raw).trim();
  return /^\d{1,20}$/.test(text) ? text : null;
};

/** 路由 query 里的枚举值：只认给定的几个（别把任意串塞进查询参数） */
export const oneOfQuery = <T extends string>(value: unknown, allowed: readonly T[]): T | null => {
  const raw = Array.isArray(value) ? value[0] : value;
  return typeof raw === 'string' && (allowed as readonly string[]).includes(raw) ? (raw as T) : null;
};

/** 路由 query 里的开关（'true' / '1' 算开） */
export const flagOfQuery = (value: unknown): boolean => {
  const raw = Array.isArray(value) ? value[0] : value;
  return raw === 'true' || raw === '1';
};

/**
 * 地址里「本页认的那几个筛选」的指纹（原样取值，不解析）：石蜡包埋 / 冻存两页被 keep-alive 缓存，
 * 靠它判断「再次进来时地址里的筛选变没变」—— 变了才按地址重新套筛选，没变就保留页面上的手动筛选。
 */
export const routeKeyOf = (query: Record<string, unknown>, keys: string[]): string =>
  JSON.stringify(
    keys.map((key) => {
      const raw = Array.isArray(query?.[key]) ? (query[key] as unknown[])[0] : query?.[key];
      return raw === undefined || raw === null ? null : String(raw);
    })
  );

/** 去掉 query 里的某几个键（「看全部」清 sampleId、打开过抽屉后清 add 用），其余原样保留 */
export const queryWithout = (query: Record<string, unknown>, keys: string[]): Record<string, string> => {
  const out: Record<string, string> = {};
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (keys.includes(key) || value === undefined || value === null) {
      return;
    }
    const raw = Array.isArray(value) ? value[0] : value;
    if (raw !== undefined && raw !== null) {
      out[key] = String(raw);
    }
  });
  return out;
};

/** 提示条 / 抽屉里「这是哪个样本」要用到的样本字段 */
export interface ScopeSample {
  internalNo?: string | null;
  submitNo?: string | null;
  sourceUnitName?: string | null;
}

/**
 * 提示条上的样本名：内部编号优先（待核验的外部样本还没有内部编号 → 送检单号），括号里带来源单位，
 * 例如「T-hli01（A 医院）」「SJ90000002（A 医院）」。样本没加载到时退回 `fallback`（调用方给「样本 <id>」）。
 */
export const sampleScopeLabel = (sample: ScopeSample | null | undefined, fallback: string): string => {
  const no = (sample?.internalNo || '').trim() || (sample?.submitNo || '').trim();
  if (!no) {
    return fallback;
  }
  const unit = (sample?.sourceUnitName || '').trim();
  return unit ? `${no}（${unit}）` : no;
};

// ── 样本两页的「石蜡包埋 / 冻存」一列 ─────────────────────────────────────────

/** 样本行上关联列要用到的字段（SampleVO 的子集，spec 好造数据） */
export interface RelationRow {
  id: Id;
  verifyStatus?: string | null;
  hint?: { blockCount?: number | null } | null;
  relation?: { pendingEmbedCount?: number | null; cryoBatchCount?: number | null } | null;
}

/** 当前登录人能看 / 能新增哪两张表（页面用 checkPermi 算好传进来） */
export interface RelationPerms {
  embedList: boolean;
  embedAdd: boolean;
  cryoList: boolean;
  cryoAdd: boolean;
}

/** 关联列里的一项：数量、点数字去哪（null = 不可点）、数量为 0 时的「新增」去哪（null = 不给） */
export interface RelationItem {
  count: number;
  to: RouteTarget | null;
  addTo: RouteTarget | null;
}

export interface RelationCell {
  /** 蜡块：已核验有效的石蜡块（= hint.blockCount，与切片染色提示同一个数） */
  blocks: RelationItem;
  /** 合作单位送来、还待核验的石蜡包埋送样（还不是一块，单独标出来） */
  pending: RelationItem;
  /** 冻存批次 */
  cryo: RelationItem;
}

const count = (value?: number | null): number => (typeof value === 'number' && value > 0 ? value : 0);

/**
 * 关联列一格怎么画。
 *
 * ★ 数量有就可点（带 sampleId 跳过去）；为 0 显示「—」，样本已核验有效、且有新增权限时再给一个「新增」
 *   （跳过去直接打开新增抽屉、样本已选好）—— 待核验 / 无效的样本不能挂石蜡块或冻存批次，不给入口。
 * ★ 没有列表权限的一律不可点（点了也是 403）。
 */
export const relationOf = (row: RelationRow, perms: RelationPerms): RelationCell => {
  const valid = row.verifyStatus === 'valid';
  const blocks = count(row.hint?.blockCount);
  const pending = count(row.relation?.pendingEmbedCount);
  const cryo = count(row.relation?.cryoBatchCount);
  return {
    blocks: {
      count: blocks,
      to: blocks > 0 && perms.embedList ? embedOfSample(row.id) : null,
      addTo: blocks === 0 && valid && perms.embedList && perms.embedAdd ? embedOfSample(row.id, { add: true }) : null
    },
    pending: {
      count: pending,
      to: pending > 0 && perms.embedList ? embedOfSample(row.id, { pending: true }) : null,
      addTo: null
    },
    cryo: {
      count: cryo,
      to: cryo > 0 && perms.cryoList ? cryoOfSample(row.id) : null,
      addTo: cryo === 0 && valid && perms.cryoList && perms.cryoAdd ? cryoOfSample(row.id, { add: true }) : null
    }
  };
};
