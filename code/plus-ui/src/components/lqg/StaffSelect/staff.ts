// 「选人」的纯函数（CR-20261012-22）—— StaffSelect 组件、三个抽屉、三张列表的按人筛选、操作人统计页共用，spec 直接钉。
//
// 口径（与后端 StaffRefRules / PersonKey 同一份）：
//   · 操作人 / 包埋人 / 冻存人从**内部人员名单**里选，库里落「账号 id + 姓名快照」一对；
//   · 历史记录上可能只有手填的姓名（没关联账号），或者关联的人后来被撤销了授权 —— 这两种在下拉里照样看得见是谁，
//     不改就原样留着，但不能再选给别的记录；
//   · 下拉的值一律用「按人筛」的 key：u:<账号 id> / n:<没关联账号的姓名> / none（筛选里的「未填」）。
//     统计页点数字跳到列表时带的也是它。
//
// ★ 账号 id 一律当字符串：真实账号是雪花 id，超出 JS 安全整数。

export interface StaffOption {
  userId: string;
  name: string;
}

export interface StaffChoice {
  /** u:<账号 id> / n:<姓名> */
  value: string;
  label: string;
  /** 不在名单里的现值（撤销了授权的人、没关联账号的手填姓名）：能看见，但只属于这条记录 */
  legacy?: boolean;
}

/** 筛选里「未填」的值（后端 PersonKey.NONE） */
export const PERSON_NONE = 'none';

const USER_PREFIX = 'u:';
const NAME_PREFIX = 'n:';

const text = (value: unknown): string => (typeof value === 'string' ? value.trim() : typeof value === 'number' ? String(value) : '');

export const userKey = (userId: unknown): string => USER_PREFIX + text(userId);

export const nameKey = (name: unknown): string => NAME_PREFIX + text(name);

/** 名单：id 转字符串、姓名去空白、去掉没名字的、同一个账号只留一次 */
export const normalizeStaff = (list: ReadonlyArray<{ userId?: unknown; name?: unknown }> | null | undefined): StaffOption[] => {
  const out: StaffOption[] = [];
  for (const item of list ?? []) {
    const userId = text(item?.userId);
    const name = text(item?.name);
    if (userId && name && !out.some((o) => o.userId === userId)) {
      out.push({ userId, name });
    }
  }
  return out;
};

/** 一栏人名现在的值 → 下拉的值；没填 = null */
export const personValue = (id: unknown, name: unknown): string | null => {
  const userId = text(id);
  if (userId) {
    return userKey(userId);
  }
  const n = text(name);
  return n ? nameKey(n) : null;
};

export interface PersonKeyParts {
  kind: 'user' | 'name' | 'none';
  value: string;
}

/** 拆开一个 key；认不出来 = null */
export const parsePersonKey = (key: unknown): PersonKeyParts | null => {
  const value = text(key);
  if (value === PERSON_NONE) {
    return { kind: 'none', value: '' };
  }
  if (value.startsWith(USER_PREFIX) && /^\d+$/.test(value.slice(USER_PREFIX.length))) {
    return { kind: 'user', value: value.slice(USER_PREFIX.length) };
  }
  if (value.startsWith(NAME_PREFIX) && value.length > NAME_PREFIX.length) {
    return { kind: 'name', value: value.slice(NAME_PREFIX.length) };
  }
  return null;
};

/** 地址栏 / 外部带进来的 key：只认认得出来的形状，别的一律当没带 */
export const personKeyOfQuery = (value: unknown): string | null => {
  const raw = Array.isArray(value) ? value[0] : value;
  return parsePersonKey(raw) ? text(raw) : null;
};

export interface StaffLabels {
  /** 「（未关联账号）」 */
  unlinked: string;
  /** 「（已不在名单）」 */
  gone: string;
}

/**
 * 下拉里的选项：名单里的人；现值不在名单里时排在最后（标明是哪一种），打开下拉还能看见它是谁。
 */
export const staffChoices = (staff: readonly StaffOption[], id: unknown, name: unknown, labels: StaffLabels): StaffChoice[] => {
  const out: StaffChoice[] = staff.map((s) => ({ value: userKey(s.userId), label: s.name }));
  const current = personValue(id, name);
  if (current && !out.some((c) => c.value === current)) {
    const n = text(name);
    const userId = text(id);
    out.push({
      value: current,
      label: userId ? `${n || userId}${labels.gone}` : `${n}${labels.unlinked}`,
      legacy: true
    });
  }
  return out;
};

export interface PickedPerson {
  id: string | null;
  name: string | null;
}

/**
 * 下拉选中的值 → 表单里的那一对（账号 id + 姓名）。
 * `current` 是选之前的那一对：选回不在名单里的现值时，原样还回去（姓名不丢）。
 */
export const pickPerson = (value: unknown, staff: readonly StaffOption[], current: PickedPerson): PickedPerson => {
  const parts = parsePersonKey(value);
  if (!parts || parts.kind === 'none') {
    return { id: null, name: null };
  }
  if (parts.kind === 'name') {
    return { id: null, name: parts.value };
  }
  const hit = staff.find((s) => s.userId === parts.value);
  if (hit) {
    return { id: hit.userId, name: hit.name };
  }
  return text(current.id) === parts.value ? { id: parts.value, name: text(current.name) || null } : { id: parts.value, name: null };
};

/**
 * 筛选下拉的值 → 传给后端的 key。直接输入的文字（不在名单里）当成「没关联账号的姓名」去找 ——
 * 历史手填的名字要能筛出来补关联。
 */
export const filterKey = (value: unknown, staff: readonly StaffOption[]): string | null => {
  const raw = text(value);
  if (!raw) {
    return null;
  }
  if (parsePersonKey(raw)) {
    return raw;
  }
  const hit = staff.find((s) => s.name === raw);
  return hit ? userKey(hit.userId) : nameKey(raw);
};

/**
 * 筛选下拉的选项：名单里的人；现值是一个不在名单里的 key 时也列出来（否则下拉显示成一串 key）。
 * `hintName`：带这个 key 过来的那一页知道的姓名（统计页点已撤销授权的人那一行时带上）。
 */
export const filterChoices = (staff: readonly StaffOption[], current: unknown, labels: StaffLabels, hintName?: unknown): StaffChoice[] => {
  const out: StaffChoice[] = staff.map((s) => ({ value: userKey(s.userId), label: s.name }));
  const parts = parsePersonKey(current);
  const value = text(current);
  if (parts && parts.kind !== 'none' && !out.some((c) => c.value === value)) {
    out.push({
      value,
      label: parts.kind === 'name' ? `${parts.value}${labels.unlinked}` : `${text(hintName) || parts.value}${labels.gone}`,
      legacy: true
    });
  }
  return out;
};

/** 当前登录人在不在名单里；在 → 新增时的默认值 */
export const defaultPerson = (staff: readonly StaffOption[], userId: unknown): PickedPerson => {
  const hit = staff.find((s) => s.userId === text(userId));
  return hit ? { id: hit.userId, name: hit.name } : { id: null, name: null };
};
