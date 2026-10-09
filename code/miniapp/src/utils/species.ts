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

/** 当前值是不是手填的（有值、且不在常用值里） */
export function isManualSpecies(value: string | null | undefined, choices: readonly string[]): boolean {
  const text = String(value ?? '').trim()
  return !!text && !choices.includes(text)
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
