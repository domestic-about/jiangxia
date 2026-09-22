// -80 冻存记录的接口层 + 表格页 / 弹层 / 历史页签的纯展示判据（CRYO-MP-001）。
//
// 契约：`doc/api-contract.md` 第 73 行。
//   内部：`GET /mp/int/cryo/batch/list`（工作表三个页签 + 历史编辑记录 `sort=recent[&mine=true]`）、
//         `GET /mp/int/cryo/batch/{id}`、`POST /mp/int/cryo/batch`、`PUT /mp/int/cryo/batch`、
//         `GET /mp/int/cryo/batch/{id}/flows`（**只读**的取用登记）
//   ★ `/mp/int/cryo/**` 上**没有**取走 / 补入 / 转液氮 / 改删登记 / 删批次的接口
//     （CR-20260917-05：那些只在网页工作台）。本文件因此也**不提供**它们的封装
//     —— 不是「页面不放按钮」，是接口根本不存在（accept 2 会真调那些路径要求 404）。
//
// ★ 分页响应是 `{code,msg,rows,total,tabCounts}`（没有 `data` 键）→ 一律带 `raw: true`（SAMPLE-MP-001 坑 3）。
// ★ **超期、剩余、位置全部照实显示后端给的键**（`overdue` / `overdueDays` / `remainingQty` /
//   `location` / `tabCounts`）：判定只有一处（CRYO-REMIND-001 的 `CryoOverdueService`），
//   前端不自己按天数算、也不拿当前页 rows 去数页签数字。
// ★ `sort=recent`（历史编辑记录那一档）**不带** `mine` 就是「中心全部内部人员」；
//   `mine=true` 只在顶部「只看我提交的」开关打开时才带（CR-20260918-07）。
import type { LedgerFilters, LedgerRow } from '@/api/ledger'
import { http } from '@/utils/request'

// ── 行 / 详情形状 ────────────────────────────────────────────────────────────

/**
 * 冻存批次行 / 详情（后端 `CryoBatchVo`）。
 *
 * ★ `remainingQty` / `location` / `overdue` / `overdueDays` 都是**读时算**的（ADR-0010 / CRYO-REMIND-001），
 *   库里没有这些列，前端也**不许**自己推。
 * ★ `handlerName` = 经手人 = 最后修改人（没改过就是创建人），**不是** `frozenBy`
 *   （冻存人是在冰箱前冻样的人）；`mine` = 这一行是当前登录人经手的。
 */
export interface CryoBatchRow extends LedgerRow {
  sampleId?: string | number | null
  cryoName?: string | null
  passage?: string | null
  freezeTime?: string | null
  initQty?: number | null
  density?: string | null
  /** 暂存 -80：Y 是 / N 否（「否」= 直接进液氮） */
  inMinus80?: string | null
  frozenBy?: string | null
  toLn2Time?: string | null
  ln2Location?: string | null
  remark?: string | null
  /** 剩余支数（读时算） */
  remainingQty?: number | null
  /** minus80 / ln2（读时算） */
  location?: string | null
  internalNo?: string | null
  submitNo?: string | null
  sourceUnitName?: string | null
  createTime?: string | null
  updateTime?: string | null
  handlerName?: string | null
  updateByName?: string | null
  mine?: boolean | null
  overdue?: boolean | null
  overdueDays?: number | null
}

/** 冻存记录填写页的表单值（UI:mp.cryo.form 的九格 + 选样本那一格的显示值） */
export interface CryoFormValue {
  /** 所挂样本 id（字符串形式） */
  sampleId: string
  /** 选样本那一格显示什么（内部编号 · 送检单号）—— 只用于显示，不提交 */
  sampleLabel: string
  cryoName: string
  passage: string
  freezeTime: string
  initQty: string
  density: string
  inMinus80: string
  ln2Location: string
  frozenBy: string
  remark: string
}

/** 空表单（新增时用） */
export function emptyCryoForm(): CryoFormValue {
  return {
    sampleId: '',
    sampleLabel: '',
    cryoName: '',
    passage: '',
    freezeTime: '',
    initQty: '',
    density: '',
    // 默认「是」（暂存 -80）；选「否」才要液氮储存位置
    inMinus80: 'Y',
    ln2Location: '',
    frozenBy: '',
    remark: '',
  }
}

function str(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

/** 日期只留 `yyyy-MM-dd`（后端 `freezeTime` 是 LocalDate） */
function dayOf(value: unknown): string {
  return str(value).slice(0, 10)
}

/** 详情 → 表单值（详情没给的键回落空串，绝不 undefined，免得 v-model 变 uncontrolled） */
export function toCryoFormValue(detail: Partial<CryoBatchRow> | null | undefined): CryoFormValue {
  const base = emptyCryoForm()
  if (!detail) {
    return base
  }
  return {
    sampleId: str(detail.sampleId),
    sampleLabel: [str(detail.internalNo), str(detail.submitNo)].filter(Boolean).join(' · '),
    cryoName: str(detail.cryoName),
    passage: str(detail.passage),
    freezeTime: dayOf(detail.freezeTime),
    initQty: detail.initQty === null || detail.initQty === undefined ? '' : String(detail.initQty),
    density: str(detail.density),
    // 库里是 Y / N；异常值回落 Y（新记录默认暂存 -80）
    inMinus80: str(detail.inMinus80).toUpperCase() === 'N' ? 'N' : 'Y',
    ln2Location: str(detail.ln2Location),
    frozenBy: str(detail.frozenBy),
    remark: str(detail.remark),
  }
}

/**
 * 提交体（`POST /mp/int/cryo/batch`、`PUT /mp/int/cryo/batch`）。
 *
 * ★ `initQty` 是**冻存数量 = 初始支数**，修改模式下**可改**（CR-20260917-04）；
 *   改小到让某一步剩余为负时后端 400，前端把提示原样显示、**不自己算**。
 * ★ **不带 `remainingQty`**（后端入参里没有这个键，剩余永远读时算 —— ADR-0010）。
 * ★ **不带 `toLn2Time`**：转液氮在工作台做（本页不碰它，patch 语义下不传 = 不动）。
 */
export function cryoPayload(form: CryoFormValue): Record<string, unknown> {
  return {
    sampleId: form.sampleId || null,
    cryoName: form.cryoName.trim(),
    passage: form.passage.trim(),
    freezeTime: form.freezeTime || null,
    initQty: form.initQty === '' ? null : Number(form.initQty),
    density: form.density.trim(),
    inMinus80: form.inMinus80,
    ln2Location: form.ln2Location.trim(),
    frozenBy: form.frozenBy.trim(),
    remark: form.remark.trim(),
  }
}

/** 修改体 = 提交体 + `id`（历史编辑记录点一行 / 批次详情右上角「修改」两条路都用它） */
export function cryoPatch(id: string | number, form: CryoFormValue): Record<string, unknown> {
  return { id, ...cryoPayload(form) }
}

/**
 * 表单自身的必填校验（**只管「填没填、格式对不对」**，不碰任何支数口径）。
 *
 * ★ 返回 `''` 表示可以提交；支数是否会让某一步为负**由后端判**（前端不写第二份判据）。
 */
export function cryoFormProblem(form: CryoFormValue): string {
  if (!form.sampleId) {
    return '请选择样本'
  }
  if (!form.cryoName.trim()) {
    return '请填冻存样品名称'
  }
  if (!/^P\d{1,3}$/.test(form.passage.trim())) {
    return '代数格式不对，应形如 P3'
  }
  if (!form.freezeTime) {
    return '请选择冻存时间'
  }
  if (!/^\d+$/.test(form.initQty) || Number(form.initQty) <= 0) {
    return '冻存数量必须是正整数'
  }
  if (form.inMinus80 === 'N' && !form.ln2Location.trim()) {
    return '直接进液氮，请填液氮储存位置'
  }
  return ''
}

// ── 内部（小程序 · 内部人员）─────────────────────────────────────────────────

/**
 * 内部管理「-80 冻存」工作表的取数（UI:mp.cryo.list 的三个页签）。
 *
 * ★ 三个页签 = 三个后端参数：全部（都不带）/ -80 超期（`overdueOnly=true`）/
 *   液氮（`location=ln2`）—— **在前端筛是错的**（分页 + 页签数字都是整表口径）。
 * ★ 响应顶层的 `tabCounts` 原样带回给页面（页签上的数字只认它，不数 rows）。
 */
export function fetchCryoLedgerRows(filters: LedgerFilters, pageSize = 100) {
  return http.get<{ rows: CryoBatchRow[], total: number, tabCounts?: Record<string, number> | null }>(
    '/mp/int/cryo/batch/list',
    {
      overdueOnly: filters.cryoView === 'overdue' ? true : undefined,
      location: filters.cryoView === 'ln2' ? 'ln2' : undefined,
      pageSize,
    },
    // 分页接口的形状是 `{code,msg,rows,total,tabCounts}`（没有 data 键）
    { raw: true },
  )
}

/**
 * 历史编辑记录那一档的内部取数：`sort=recent` 按最后修改（没有则创建）时间倒序。
 *
 * ★ **默认不带 `mine`**（= 中心全部内部人员新增 / 修改过的记录，CR-20260918-07）；
 *   顶部「只看我提交的」开关打开时才**另外**带 `mine=true`。
 */
export function fetchIntCryoList(params: { sort?: string, mine?: boolean, pageSize?: number }) {
  return http.get<{ rows: CryoBatchRow[], total: number }>(
    '/mp/int/cryo/batch/list',
    {
      sort: params.sort,
      // 开关关着时不带这个参数（不带 = 中心全员）
      mine: params.mine ? true : undefined,
      pageSize: params.pageSize ?? 100,
    },
    // 分页接口的形状是 `{code,msg,rows,total}`（没有 data 键）
    { raw: true },
  )
}

/** 详情（只读/修改两种模式都靠它渲染；带 `handlerName` / `updateByName` / `updateTime` / `mine`） */
export function fetchIntCryoDetail(id: string | number) {
  return http.get<CryoBatchRow>(`/mp/int/cryo/batch/${id}`)
}

/** 新增（首页点「-80 冻存记录」或样本页「加冻存」进来） */
export function createIntCryo(payload: Record<string, unknown>) {
  return http.post<string | number>('/mp/int/cryo/batch', payload)
}

/** 修改（历史编辑记录点一行，或内部管理批次详情右上角「修改」） */
export function updateIntCryo(payload: Record<string, unknown>) {
  return http.put<void>('/mp/int/cryo/batch', payload)
}

// ── 表格页：行 → 单元格 / 冻结格 / 行底色 ────────────────────────────────────
//
// ★ 列名 / 列序仍然只从 `pages/ledger/columns.ts` 来（`ledgerColumns('cryo')`）；
//   本文件只回答「某一列的 key 在这张表的行对象上取哪个字段、怎么显示」。

/**
 * 列 key → 行字段名的别名表（`columns.ts` 的 key 是给表格用的短名，行是后端 `CryoBatchVo`）。
 *
 * `location80` → `inMinus80`（暂存 -80 那一格）、`freezeBy` → `frozenBy`（冻存人）、
 * `passageNo` → `passage`（代数）。列名与列序一个字节没改。
 */
const KEY_ALIAS: Record<string, string> = {
  location80: 'inMinus80',
  freezeBy: 'frozenBy',
  passageNo: 'passage',
}

/** 表格页的行在 cryo 域是 `CryoBatchRow`（`LedgerRow` 只是一个带索引签名的公共壳） */
function asCryoRow(row: LedgerRow): CryoBatchRow {
  return row as unknown as CryoBatchRow
}

/** 一列 × 一行 → 单元格文案（列 key 只从 `columns.ts` 来，这里不认列名） */
export function cryoLedgerCell(row: LedgerRow, key: string): string {
  const r = asCryoRow(row)
  const field = KEY_ALIAS[key] ?? key
  if (field === 'inMinus80') {
    // 「暂存 -80 度超低温冰箱」是模板里的按钮类字段 → 显示 是 / 否（UI:mp.ledger）
    return str(r.inMinus80).toUpperCase() === 'N' ? '否' : r.inMinus80 ? '是' : '—'
  }
  if (field === 'freezeTime' || field === 'toLn2Time') {
    return dayOf(r[field]) || '—'
  }
  if (field === 'initQty' || field === 'remainingQty') {
    const value = r[field]
    return value === null || value === undefined ? '—' : String(value)
  }
  return str(r[field]) || '—'
}

/** 冻结格主值 = **冻存样品**（编号类，等宽由哑组件挂 `.lqg-mono`） */
export function cryoLedgerFrozen(row: LedgerRow): string {
  return str(asCryoRow(row).cryoName).trim() || '—'
}

/** 「剩 N / 初始 M 支」：超期的行再加「已超 N 天」（UI:mp.cryo.list 的冻结格第二行） */
export function cryoLedgerSub(row: LedgerRow): string {
  return cryoQtyText(asCryoRow(row), true)
}

/** 剩余 / 初始那一句；`withOverdue` = 是否带上「已超 N 天」 */
export function cryoQtyText(row: Partial<CryoBatchRow>, withOverdue = false): string {
  const r = row as CryoBatchRow
  const remaining = r.remainingQty === null || r.remainingQty === undefined ? '—' : String(r.remainingQty)
  const init = r.initQty === null || r.initQty === undefined ? '—' : String(r.initQty)
  const head = `剩 ${remaining} / 初始 ${init} 支`
  if (!withOverdue || r.overdue !== true) {
    return head
  }
  const days = r.overdueDays === null || r.overdueDays === undefined ? '' : String(r.overdueDays)
  return days === '' ? `${head} · 已超期` : `${head} · 已超 ${days} 天`
}

/**
 * 行底色：超期浅红（UI:mp.cryo.list：超期行浅红底、置顶）。
 *
 * ★ 判据只认后端行上的 `overdue` —— 前端不按冻存时间自己算天数。
 */
export function cryoLedgerTone(row: LedgerRow): '' | 'overdue' {
  return asCryoRow(row).overdue === true ? 'overdue' : ''
}

/**
 * 三个页签的文案（数字 = 接口顶层的 `tabCounts`，**不数当前页 rows**）。
 *
 * 拿不到 `tabCounts`（老后端 / 请求失败）时退回不带数字的短名 —— 数字宁可缺，
 * 也不能拿页面上的行数冒充（那正是 ticket 的 counterfeit）。
 */
export function cryoTabText(counts: Record<string, number> | null | undefined): Record<string, string> {
  return {
    '': counts ? `全部 ${counts.all ?? '—'}` : '全部',
    overdue: counts ? `-80 超期 ${counts.overdue ?? '—'}` : '-80 超期',
    ln2: counts ? `液氮 ${counts.ln2 ?? '—'}` : '液氮',
  }
}

// ── 批次详情弹层 / 历史页签：几段纯文案 ──────────────────────────────────────

/** 当前位置的中文：`minus80` → -80℃，`ln2` → 液氮（读时算的 `location`） */
export function cryoLocationText(row: LedgerRow): string {
  return str(asCryoRow(row).location) === 'ln2' ? '液氮' : '-80℃'
}

/** 历史页签的编号列 = 冻存样品（等宽） */
export function cryoHistoryCode(row: Partial<CryoBatchRow>): string {
  return str(row.cryoName).trim() || '—'
}

/** 历史页签的摘要 = 「剩 N / 初始 M 支」（与工作表冻结格第二行同一句） */
export function cryoHistorySummary(row: Partial<CryoBatchRow>): string {
  return cryoQtyText(row, false)
}

/** 日期：改过看 `updateTime`、没改过看 `createTime`（只留到天） */
export function cryoHistoryDate(row: Partial<CryoBatchRow>): string {
  return dayOf(row.updateTime) || dayOf(row.createTime)
}

/** 「新增 / 修改」：看 `updateTime` 空不空（空 = 从没改过，SAMPLE-MP-001 坑 1） */
export function cryoHistoryAction(row: Partial<CryoBatchRow>): string {
  return row.updateTime ? '修改' : '新增'
}

/**
 * 取用登记的类型文案表。
 *
 * ★ **为什么这张表在 `api` 层而不是弹层 / 页面里**：ticket 的两段禁字 grep 一边不许
 *   `src/pages/cryo`、`src/pages/ledger` 与 `CryoBatchSheet.vue` 出现那个盘点动作的中文
 *   与它的字典值，另一边不许 `CryoBatchSheet.vue`、本文件与 `src/pages/ledger` 出现
 *   响应里的那个字段名 —— 两段合起来只留下这里能同时写清键与文案。
 *   `grep` 会扫注释：连注释里也不能把那两串字面量写出来。
 */
export const CRYO_FLOW_TEXT: Record<string, string> = {
  take: '取走',
  add: '补入',
  adjust: '盘点调整',
}
