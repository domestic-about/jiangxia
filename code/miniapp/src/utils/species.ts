// 种属的纯函数（CR-20261009-18）—— 小程序填写页 / 核验页 / 种属面板共用，spec 直接钉。
//
// 口径（与后端 SubmitSegmentRules、工作台 SpeciesSelect 同一份）：
//   · 常用值来自字典 lqg_species（`/mp/dict/hints?type=species`）；拉不到就用内置的四个，不挡填写；
//   · 列表里没有的可以手填（「还可以添加其他的」），存去首尾空白后的文字，最多 50 字；
//   · 两类样本都必填。
import { http } from '@/utils/request'

/** 种属最多几个字（t_lqg_sample.species VARCHAR(50)） */
export const SPECIES_MAX = 50

/** 字典拉不到时的兜底（= 迁移 V202610091000 里 seed 的四个，顺序同字典） */
export const DEFAULT_SPECIES = ['人', '鼠兔', '移植猪', '鸡'] as const

/** 面板里的格子：字典值（去空、去重、保序）；字典是空的就用兜底四个。手填的值不进格子（在手填框里） */
export function speciesChoices(dict: readonly unknown[] | null | undefined): string[] {
  const out: string[] = []
  for (const item of dict ?? []) {
    const text = typeof item === 'string' ? item.trim() : ''
    if (text && !out.includes(text)) {
      out.push(text)
    }
  }
  return out.length > 0 ? out : [...DEFAULT_SPECIES]
}


/** 提交前 / 比较用：去首尾空白 */
export function normalizeSpecies(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

/** 提交前自检：'' = 没问题 */
export function speciesProblem(value: unknown): string {
  const text = normalizeSpecies(value)
  if (!text) {
    return '请选择种属'
  }
  if (Array.from(text).length > SPECIES_MAX) {
    return `种属不能超过 ${SPECIES_MAX} 字`
  }
  return ''
}

/** 拉字典里的常用值（拉不到 → 兜底四个，不挡填写） */
export async function fetchSpeciesOptions(): Promise<string[]> {
  try {
    const list = await http.get<string[]>('/mp/dict/hints', { type: 'species' }, { silent: true })
    return speciesChoices(list)
  }
  catch {
    return [...DEFAULT_SPECIES]
  }
}

/** 面板里的一行：值 + 是不是当前选中的 */
export interface SpeciesRow {
  value: string
  selected: boolean
}

/** 面板的显示状态：过滤后的行 + 「使用「…」」那一行要用的值（null = 不出这一行） */
export interface SpeciesSheetView {
  rows: SpeciesRow[]
  createValue: string | null
}

/**
 * 种属面板显示什么（一个输入框既搜索、又能直接用输入的字 —— 不分「选」与「手填」两种模式）：
 *   · 行 = 常用值 +（当前值是手填的就把它也列上，排最后，打勾）；输入了字就按「包含」过滤；
 *   · 输入的字（去首尾空白）不等于任何一行 → 顶上出「使用「…」」，点它就用这几个字；
 *   · 输入超过 50 字按 50 字截（与后端上限一致）。
 */
export function speciesSheetView(options: readonly string[], keyword: string, current: string): SpeciesSheetView {
  const own = normalizeSpecies(current)
  const all = [...options]
  if (own && !all.includes(own)) {
    all.push(own)
  }
  const kw = Array.from(normalizeSpecies(keyword)).slice(0, SPECIES_MAX).join('')
  const rows = all
    .filter(value => !kw || value.includes(kw))
    .map(value => ({ value, selected: value === own }))
  const createValue = kw && !all.includes(kw) ? kw : null
  return { rows, createValue }
}

/**
 * 键盘上点「完成」时用哪个值：有「使用「…」」就用输入的字；输入的字正好是某一行就用那一行；
 * 过滤后只剩一行也用它；其余（没输入 / 还剩好几行）→ null，面板不动。
 */
export function speciesOnConfirm(view: SpeciesSheetView, keyword: string): string | null {
  if (view.createValue) {
    return view.createValue
  }
  const kw = normalizeSpecies(keyword)
  if (!kw) {
    return null
  }
  const exact = view.rows.find(r => r.value === kw)
  if (exact) {
    return exact.value
  }
  return view.rows.length === 1 ? view.rows[0].value : null
}
