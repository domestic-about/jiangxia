// 选人的纯函数（CR-20261012-22）：下拉选项、选中后落的那一对值、按人筛的 key。
import { describe, expect, it } from 'vitest';
import {
  PERSON_NONE,
  defaultPerson,
  filterChoices,
  filterKey,
  normalizeStaff,
  parsePersonKey,
  personKeyOfQuery,
  personValue,
  pickPerson,
  staffChoices
} from './staff';

const STAFF = normalizeStaff([
  { userId: 9000000101, name: '李工' },
  { userId: '1970000000000000001', name: ' 刘佳 ' },
  { userId: 9000000101, name: '李工（重复行）' },
  { userId: 3, name: '  ' }
]);
const LABELS = { unlinked: '（未关联账号）', gone: '（已不在名单）' };

describe('normalizeStaff', () => {
  it('账号 id 一律转成字符串（雪花 id 超出安全整数）、姓名去空白、去重、去掉没名字的', () => {
    expect(STAFF).toEqual([
      { userId: '9000000101', name: '李工' },
      { userId: '1970000000000000001', name: '刘佳' }
    ]);
  });
});

describe('personValue / parsePersonKey', () => {
  it('有账号用账号，没有就用姓名，都没有是 null', () => {
    expect(personValue(9000000101, '李工')).toBe('u:9000000101');
    expect(personValue(null, ' 袁琴琴 ')).toBe('n:袁琴琴');
    expect(personValue(null, '  ')).toBeNull();
  });

  it('只认三种形状；认不出来的是 null（地址栏里乱写的不当筛选用）', () => {
    expect(parsePersonKey('u:12')).toEqual({ kind: 'user', value: '12' });
    expect(parsePersonKey('n:袁琴琴')).toEqual({ kind: 'name', value: '袁琴琴' });
    expect(parsePersonKey(PERSON_NONE)).toEqual({ kind: 'none', value: '' });
    for (const bad of ['12', 'u:abc', 'n:', 'x:1', '', null, undefined]) {
      expect(parsePersonKey(bad)).toBeNull();
    }
    expect(personKeyOfQuery(['u:12', 'u:13'])).toBe('u:12');
    expect(personKeyOfQuery('u:1 or 1=1')).toBeNull();
  });
});

describe('staffChoices（表单下拉）', () => {
  it('现值在名单里：就是名单', () => {
    expect(staffChoices(STAFF, '9000000101', '李工', LABELS).map((c) => c.label)).toEqual(['李工', '刘佳']);
  });

  it('★ 历史手填的姓名（没关联账号）排在最后并标注，打开下拉还能看见它是谁', () => {
    const choices = staffChoices(STAFF, null, '袁琴琴', LABELS);
    expect(choices[choices.length - 1]).toEqual({ value: 'n:袁琴琴', label: '袁琴琴（未关联账号）', legacy: true });
  });

  it('已撤销授权的人：看得见、带标注', () => {
    const choices = staffChoices(STAFF, '77', '离职的人', LABELS);
    expect(choices[choices.length - 1]).toEqual({ value: 'u:77', label: '离职的人（已不在名单）', legacy: true });
  });
});

describe('pickPerson（选中后落的一对值）', () => {
  const none = { id: null, name: null };

  it('选名单里的人：id 与姓名一起落', () => {
    expect(pickPerson('u:1970000000000000001', STAFF, none)).toEqual({ id: '1970000000000000001', name: '刘佳' });
  });

  it('清空：两个一起清', () => {
    expect(pickPerson(undefined, STAFF, { id: '9000000101', name: '李工' })).toEqual(none);
    expect(pickPerson('', STAFF, { id: '9000000101', name: '李工' })).toEqual(none);
  });

  it('★ 选回不在名单里的现值：原样还回去，姓名不丢', () => {
    expect(pickPerson('u:77', STAFF, { id: '77', name: '离职的人' })).toEqual({ id: '77', name: '离职的人' });
    expect(pickPerson('n:袁琴琴', STAFF, { id: null, name: '袁琴琴' })).toEqual({ id: null, name: '袁琴琴' });
  });
});

describe('filterKey / filterChoices（列表筛选）', () => {
  it('选人 = 账号 key；「未填」= none；清空 = 不筛', () => {
    expect(filterKey('u:9000000101', STAFF)).toBe('u:9000000101');
    expect(filterKey(PERSON_NONE, STAFF)).toBe('none');
    expect(filterKey('', STAFF)).toBeNull();
    expect(filterKey(undefined, STAFF)).toBeNull();
  });

  it('★ 直接输入的名字：是名单里的人就按账号筛；不是 → 找没关联账号的手填记录', () => {
    expect(filterKey(' 刘佳 ', STAFF)).toBe('u:1970000000000000001');
    expect(filterKey('袁琴琴', STAFF)).toBe('n:袁琴琴');
  });

  it('现值不在名单里（统计页点过来的）：也列出来，不显示成一串 key', () => {
    expect(filterChoices(STAFF, 'n:袁琴琴', LABELS).pop()).toEqual({ value: 'n:袁琴琴', label: '袁琴琴（未关联账号）', legacy: true });
    expect(filterChoices(STAFF, 'u:77', LABELS, '离职的人').pop()).toEqual({ value: 'u:77', label: '离职的人（已不在名单）', legacy: true });
    expect(filterChoices(STAFF, 'none', LABELS)).toHaveLength(2);
  });
});

describe('defaultPerson', () => {
  it('当前登录人在名单里 → 新增时默认是他；不在（上游超管）→ 空着', () => {
    expect(defaultPerson(STAFF, 9000000101)).toEqual({ id: '9000000101', name: '李工' });
    expect(defaultPerson(STAFF, 1)).toEqual({ id: null, name: null });
  });
});
