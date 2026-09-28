import type { Identity, ResolvedIdentity } from '@/types/identity'
import { normalizeIdentity } from '@/types/identity'

// 首页「填写」入口与「我的」区块的纯函数（SYS-MP-001）。
//
// 权威：UI:mp.home / UI:mp.home.entries / UI:mp.me / FLOW:F-MP-01.step1 / FLOW:F-AUTH-01.step4，
// 结构来源 CR-20260917-05（2026-09-17 晚 Kevin 定）。
// 期望值全部在 doc/verify/fixtures/home-entries-cases.json 里，本文件不自带期望表。
//
// 三条硬口径（做反了就是事故）：
// 1. 只认后端 /mp/me 的 identity——缺失、空串、不认识的值 → 入口为空、无跳转目标、
//    「我的」一个区块都不出；**绝不默认当内部**。
// 2. 内部四格、外部三格（没有 -80 冻存记录），顺序 = 甲方模板顺序。
// 3. 点哪格都是进该表的**填写页**新增一条，内部外部一样。

/** 四张表的入口 key，顺序即宫格顺序（甲方的模板顺序） */
export const ENTRY_KEYS = ['sample', 'organoid', 'embed', 'cryo'] as const
export type EntryKey = (typeof ENTRY_KEYS)[number]

/** 外部人员看不到的那张表：-80 冻存记录（CR-20260917-05） */
export const INTERNAL_ONLY_ENTRY: EntryKey = 'cryo'

/** 「我的」页里的区块 key */
export const ME_SECTION_KEYS = ['history', 'unitGroup', 'internalAdmin'] as const
export type MeSectionKey = (typeof ME_SECTION_KEYS)[number]

// 每个入口点进去的页面：一律是该表的填写页（新增一条）。
//
// ★ `?mode=new` **必须由入口自己带**（D2 r1 L2 S0-1）：填写页的 `normalizeMode` 把
//   「缺失 / 不认识的 mode」一律按只读处理（ticket 明文口径，不能改成默认可写），
//   所以「点哪格都是新增一条」（UI:mp.home.entries）只能落在这里：
//   不带 mode 就是 view + 没有 id → 页面直接进「没能加载这条样本」错误态，首页送检整条路断掉。
//   占位页（embed / cryo）不吃 query，多这一个参数不会有事（它们连 onLoad 都没有）。
const ENTRY_FORM_TARGET: Record<EntryKey, string> = {
  sample: '/pages/sample/form?mode=new',
  organoid: '/pages/organoid/form?mode=new',
  embed: '/pages/embed/form?mode=new',
  cryo: '/pages/cryo/form?mode=new',
}

// 首页要渲染哪些入口格。顺序 = ENTRY_KEYS 的模板顺序；
// 外部去掉 cryo；身份未知 → 空数组。
export function entriesFor(identity: unknown): EntryKey[] {
  const resolved = normalizeIdentity(identity)
  if (resolved === null) {
    return []
  }
  if (resolved === 'internal') {
    return [...ENTRY_KEYS]
  }
  return ENTRY_KEYS.filter(key => key !== INTERNAL_ONLY_ENTRY)
}

// 点这个入口去哪。内部外部一样都是该表的填写页；
// 外部点 cryo、身份未知、key 不认识 → null（调用方不渲染成可点项）。
export function entryTarget(identity: unknown, key: string): string | null {
  const allowed = entriesFor(identity)
  if (!allowed.includes(key as EntryKey)) {
    return null
  }
  return ENTRY_FORM_TARGET[key as EntryKey]
}

// 「我的」页里按身份出现的区块，顺序即渲染顺序。
// - 所有人：history（历史编辑记录）
// - 外部：unitGroup（单位与组别，本张只展示 /mp/me 的值，修改页在 AUTH-GROUP-001）
// - 内部：internalAdmin（内部管理板块，外部**不渲染**而不是置灰）
// 身份未知 → 空数组。
export function meSections(identity: unknown): MeSectionKey[] {
  const resolved: ResolvedIdentity = normalizeIdentity(identity)
  if (resolved === null) {
    return []
  }
  if (resolved === 'internal') {
    return ['history', 'internalAdmin']
  }
  return ['history', 'unitGroup']
}

/**
 * 「内部管理」板块底部小字（UI:mp.me）。
 *
 * 甲方 2026-09-24 第 20 行：核验、冻存登记小程序和工作台都要能做（Kevin 定）——
 * 以前那句把人支到网页工作台，现在两边都能做，改成这一句。仍然没有「修改」二字（CR-20260918-07）。
 */
export const INTERNAL_ADMIN_NOTE = '核验、冻存登记在小程序和网页工作台都能做'

/** 入口 key → 该表的全称（宫格与内部管理板块共用） */
export const ENTRY_TITLE: Record<EntryKey, string> = {
  sample: '样本记录信息表',
  organoid: '类器官收样记录',
  embed: '石蜡包埋送样记录',
  cryo: '-80 冻存记录',
}

/**
 * 入口 key → 该表的短名（页签专用：内部管理表格页的切换条、历史编辑记录、待核验三处都从这里取）。
 *
 * Kevin 2026-09-24 本机验收：页签文字不要换行 —— 390 宽下四个全称并排必然折成两行，页签一律用短名；
 * 短名只在这里写一份，别在页面里再写一套。
 */
export const ENTRY_SHORT: Record<EntryKey, string> = {
  sample: '样本记录',
  organoid: '类器官收样',
  embed: '石蜡包埋',
  cryo: '-80 冻存',
}

/** 内部管理板块四个入口点进去的表格页（SAMPLE-MP-002 建页；`sheet` 决定进哪个工作表） */
export function ledgerTarget(key: EntryKey): string {
  return `/pages/ledger/index?sheet=${key}`
}

/** 「我的」里那些非首页入口的固定目标（占位页，内容在各自 ticket） */
export const ME_TARGET = {
  history: '/pages/history/index',
  unitGroup: '/pages/me/unit-group',
  agreement: '/pages/legal/agreement',
  privacy: '/pages/legal/privacy',
} as const

/** 类型守卫：这个字符串是不是合法的身份（供 mock 调试入口用） */
export function isIdentity(raw: unknown): raw is Identity {
  return raw === 'internal' || raw === 'external'
}
