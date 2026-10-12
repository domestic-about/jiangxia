import request from '@/utils/request';
import { AxiosPromise } from 'axios';

// ============================================================================
// 操作人统计（CR-20261012-22）
//
// 后端：.../stat/controller/OperatorStatController.java（GET /lqg/stat/operator）
// 鉴权：内部角色闸，全员可见。数字每次请求现算。
//
// ★ 四张表各一块，**块与块之间不加总**；质控表不在里面。
// ★ 行上的 key（u:<账号 id> / n:<姓名> / none）就是三张列表「按人筛」的参数值 ——
//   点数字跳过去时原样带上，列表筛出来的条数与这里的数字是同一个口径。
// ============================================================================

export type OperatorStatSheetKey = 'tissue' | 'organoid' | 'embed' | 'cryo';

export interface OperatorStatMetric {
  /** count / embedBy / operator / batches / tubes */
  key: string;
  label: string;
  unit: string;
}

export interface OperatorStatRow {
  /** 按人筛的参数值：u:<账号 id> / n:<姓名> / none */
  key: string;
  userId?: string | number | null;
  name: string;
  /** 关联了内部人员账号没有 */
  linked: boolean;
  /** 是不是当前登录人 */
  me: boolean;
  values: Record<string, number>;
}

export interface OperatorStatSheet {
  sheet: OperatorStatSheetKey;
  title: string;
  /** 时间段按这张表的哪一栏算 */
  dateLabel: string;
  metrics: OperatorStatMetric[];
  totals: Record<string, number>;
  rows: OperatorStatRow[];
}

export interface OperatorStatVO {
  begin?: string | null;
  end?: string | null;
  sheets: OperatorStatSheet[];
}

/** 操作人统计；begin / end 都可不带（不带 = 不限） */
export const getOperatorStat = (params: { begin?: string | null; end?: string | null }): AxiosPromise<OperatorStatVO> => {
  return request({
    url: '/lqg/stat/operator',
    method: 'get',
    params: { begin: params.begin || undefined, end: params.end || undefined }
  });
};
