// 种属的纯函数（CR-20261009-18）—— SpeciesSelect 组件与各页的筛选、保存共用，spec 直接钉。
//
// 口径（与后端 SubmitSegmentRules / SampleSpeciesFilter 同一份）：
//   · 常用值来自字典 lqg_species（人 / 鼠兔 / 移植猪 / 鸡）；字典的值与标签都是中文本身，库里存的就是看到的字；
//   · 列表里没有的可以直接手填（「还可以添加其他的」）—— 存的是去首尾空白后的文字，最多 50 字；
//   · 筛选多一个「未填」（值 __none__）：本需求之前录的样本没有种属，按它筛出来补。

/** 筛选里「未填」的值（后端 SampleQueryBo.SPECIES_NONE） */
export const SPECIES_NONE = '__none__';

/** 种属最多几个字（t_lqg_sample.species VARCHAR(50)） */
export const SPECIES_MAX = 50;

export interface SpeciesDictItem {
  label?: string | null;
  value?: string | null;
}

/** 提交前的种属：去首尾空白，空 → null */
export const normalizeSpecies = (value: unknown): string | null => {
  const text = typeof value === 'string' ? value.trim() : '';
  return text ? text : null;
};

/**
 * 下拉里的选项：字典的标签（按字典顺序、去重、去空）；当前值不在字典里（手填的、或字典里后来停用了）时排在最后，
 * 这样打开下拉还能看见它是哪一个。
 */
export const speciesOptions = (dict: readonly SpeciesDictItem[] | null | undefined, current?: string | null): string[] => {
  const out: string[] = [];
  for (const item of dict ?? []) {
    const label = normalizeSpecies(item?.label ?? item?.value);
    if (label && !out.includes(label)) {
      out.push(label);
    }
  }
  const own = normalizeSpecies(current);
  if (own && own !== SPECIES_NONE && !out.includes(own)) {
    out.push(own);
  }
  return out;
};

/** 列表单元格：没填的老记录显示「—」 */
export const speciesText = (value: unknown): string => normalizeSpecies(value) ?? '—';
