// 种属纯函数（CR-20261009-18）：下拉选项、提交值、列表显示。
import { describe, expect, it } from 'vitest';
import { SPECIES_NONE, normalizeSpecies, speciesOptions, speciesText } from './species';

const DICT = [
  { label: '人', value: '人' },
  { label: '鼠兔', value: '鼠兔' },
  { label: '移植猪', value: '移植猪' },
  { label: '鸡', value: '鸡' }
];

describe('speciesOptions', () => {
  it('字典顺序；当前值在字典里就不重复列', () => {
    expect(speciesOptions(DICT, '鼠兔')).toEqual(['人', '鼠兔', '移植猪', '鸡']);
  });

  it('手填的（字典外）当前值排在最后，打开下拉还能看见它', () => {
    expect(speciesOptions(DICT, ' 食蟹猴 ')).toEqual(['人', '鼠兔', '移植猪', '鸡', '食蟹猴']);
  });

  it('筛选的「未填」不当成一个种属；字典空 / 没加载完也不报错', () => {
    expect(speciesOptions(DICT, SPECIES_NONE)).toEqual(['人', '鼠兔', '移植猪', '鸡']);
    expect(speciesOptions(undefined, null)).toEqual([]);
    expect(speciesOptions([{ label: ' ', value: '' }, { label: '人' }, { label: '人' }], null)).toEqual(['人']);
  });
});

describe('normalizeSpecies / speciesText', () => {
  it('提交值去首尾空白，空白 = 没填', () => {
    expect(normalizeSpecies(' 人 ')).toBe('人');
    expect(normalizeSpecies('   ')).toBeNull();
    expect(normalizeSpecies(null)).toBeNull();
    expect(normalizeSpecies(3)).toBeNull();
  });

  it('列表里没填的老记录显示「—」', () => {
    expect(speciesText(null)).toBe('—');
    expect(speciesText('移植猪')).toBe('移植猪');
  });
});
