// 「选人」的纯函数与取数（CR-20261012-22）—— 小程序填写页 / 核验页 / 选人面板共用，spec 直接钉。
//
// 口径（与后端 StaffRefRules、工作台 StaffSelect 同一份）：
//   · 操作人 / 包埋人 / 冻存人从**内部人员名单**里选（`/mp/int/staff/options`），提交「账号 id + 姓名」一对；
//     甲方要按人统计工作量 —— 手填的名字「刘佳」「刘 佳」会被算成两个人，所以不再让手填；
//   · 名单里没有的人选不了：请管理员在工作台「人员与单位 → 内部人员授权」里先加上；
//   · 历史记录上手填的姓名（没关联账号）、已撤销授权的人：面板里看得见（带标注），不改就原样留着。
//
// ★ 账号 id 一律当字符串：真实账号是雪花 id，超出 JS 安全整数（后端对这种值按字符串下发）。
import { http } from '@/utils/request'

export interface StaffOption {
  userId: string
  name: string
}

/** 表单里的一栏人名：账号 id + 姓名；没填 = 两个空串 */
export interface PersonValue {
  id: string
  name: string
}

export const EMPTY_PERSON: PersonValue = { id: '', name: '' }

function text(value: unknown): string {
  return typeof value === 'string' ? value.trim() : typeof value === 'number' ? String(value) : ''
}

/** 名单：id 转字符串、姓名去空白、去掉没名字的、同一个账号只留一次 */
export function normalizeStaff(list: readonly unknown[] | null | undefined): StaffOption[] {
  const out: StaffOption[] = []
  for (const item of list ?? []) {
    const row = (item ?? {}) as { userId?: unknown, name?: unknown }
    const userId = text(row.userId)
    const name = text(row.name)
    if (userId && name && !out.some(o => o.userId === userId)) {
      out.push({ userId, name })
    }
  }
  return out
}

/** 详情 → 表单里的一栏人名 */
export function personOf(id: unknown, name: unknown): PersonValue {
  return { id: text(id), name: text(name) }
}

/** 面板里的一行 */
export interface StaffRow {
  /** 行的唯一键（名单里的人 = 账号 id；不在名单里的现值 = `current`） */
  key: string
  person: PersonValue
  /** 显示的文字（不在名单里的现值带标注） */
  label: string
  selected: boolean
  /** 不在名单里的现值：只属于这条记录，看得见、不能选给别的记录 */
  legacy: boolean
}

export interface StaffSheetView {
  rows: StaffRow[]
  /** 名单是空的（还没加人 / 没拉到） */
  noStaff: boolean
  /** 输入了字但一个都没对上 */
  noMatch: boolean
}

/**
 * 选人面板显示什么：名单里的人（输入了字就按「包含」过滤）；现值不在名单里时排在最后并标注。
 *
 * @param keyword 搜索框里的字
 * @param current 现在的值
 */
export function staffSheetView(staff: readonly StaffOption[], keyword: unknown, current: PersonValue): StaffSheetView {
  const kw = text(keyword).toLowerCase()
  const cur = personOf(current?.id, current?.name)
  const rows: StaffRow[] = staff
    .filter(s => !kw || s.name.toLowerCase().includes(kw))
    .map(s => ({
      key: s.userId,
      person: { id: s.userId, name: s.name },
      label: s.name,
      selected: cur.id === s.userId,
      legacy: false,
    }))
  const inStaff = !!cur.id && staff.some(s => s.userId === cur.id)
  if ((cur.id || cur.name) && !inStaff) {
    const label = cur.id ? `${cur.name || cur.id}（已不在名单）` : `${cur.name}（未关联账号）`
    if (!kw || label.toLowerCase().includes(kw)) {
      rows.push({ key: 'current', person: cur, label, selected: true, legacy: true })
    }
  }
  return { rows, noStaff: staff.length === 0, noMatch: rows.length === 0 && !!kw }
}

/**
 * 新增时的默认值 = 当前登录人。在名单里 → 账号 id + 名单里的姓名；
 * 名单没拉到 / 不在名单里 → 只带姓名（后端按姓名再对一次账号，对不上就只存文字）。
 */
export function defaultPerson(staff: readonly StaffOption[], userId: unknown, fallbackName: unknown): PersonValue {
  const hit = staff.find(s => s.userId === text(userId))
  return hit ? { id: hit.userId, name: hit.name } : { id: '', name: text(fallbackName) }
}

/** 提交时的账号 id：没有 = null（不是空串：后端收的是数字） */
export function personIdOf(id: unknown): string | null {
  return text(id) || null
}

const TTL = 30_000
let cache: StaffOption[] = []
let loadedAt = 0
let pending: Promise<StaffOption[]> | null = null

/** 拉内部人员名单（半分钟内不重复请求；拉不到 → 上一次的名单，没有就是空的，不挡填写） */
export function fetchStaffOptions(force = false): Promise<StaffOption[]> {
  if (pending) {
    return pending
  }
  if (!force && loadedAt && Date.now() - loadedAt < TTL) {
    return Promise.resolve(cache)
  }
  pending = http.get<unknown[]>('/mp/int/staff/options', {}, { silent: true })
    .then((list) => {
      cache = normalizeStaff(list)
      loadedAt = Date.now()
      return cache
    })
    .catch(() => cache)
    .finally(() => {
      pending = null
    })
  return pending
}
