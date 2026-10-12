// 操作人统计（CR-20261012-22）—— 小程序 · 内部人员。
//
// 契约：`GET /mp/int/stat/operator?begin=yyyy-MM-dd&end=yyyy-MM-dd`（两个都可不带；与工作台 `/lqg/stat/operator` 同一份结果）。
// ★ 四张表各一块，块与块之间**不加总**；质控表不在里面。数字每次现算。
// ★ 表名、列名、每行的姓名都是接口给的（工作台与小程序看到的是同一套字）。
import { http } from '@/utils/request'

export interface OperatorStatMetric {
  /** count / embedBy / operator / batches / tubes */
  key: string
  label: string
  unit: string
}

export interface OperatorStatRow {
  /** u:<账号 id> / n:<没关联账号的姓名> / none（这一栏没填） */
  key: string
  userId?: string | number | null
  name: string
  /** 关联了内部人员账号没有 */
  linked: boolean
  /** 是不是当前登录人 */
  me: boolean
  values: Record<string, number>
}

export interface OperatorStatSheet {
  /** tissue / organoid / embed / cryo */
  sheet: string
  title: string
  /** 时间段按这张表的哪一栏算 */
  dateLabel: string
  metrics: OperatorStatMetric[]
  totals: Record<string, number>
  rows: OperatorStatRow[]
}

export interface OperatorStat {
  begin?: string | null
  end?: string | null
  sheets: OperatorStatSheet[]
}

export function fetchOperatorStat(range: { begin?: string | null, end?: string | null }) {
  return http.get<OperatorStat>('/mp/int/stat/operator', {
    begin: range.begin || undefined,
    end: range.end || undefined,
  })
}
