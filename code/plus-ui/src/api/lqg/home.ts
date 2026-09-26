import request from '@/utils/request';
import { AxiosPromise } from 'axios';

// ============================================================================
// SYS 域 · 工作台首页（SYS-HOME-001 · UI:admin.home）
//
// 后端：code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/.../sys/home/controller/HomeController.java
//       + .../sys/home/service/HomeCounterService.java（五个数的唯一来源）
// 契约：doc/api-contract.md 第 88 行的 `GET /lqg/home/todo`、`GET /lqg/home/recent`
// 权限：★ 登录即可调（没有权限串、没有菜单）。首页是登录后落地的第一屏。
//
// ★ 四个语义要点（做反了 accept 2 就红）：
//   1) 待办数**读时计算**：没有缓存、没有计数字段。所以每次进来都重新拉一次即可，
//      前端不要自己叠加、不要「拉过一次就一直用旧数字」。
//   2) **为 0 不隐藏**：卡片显示 0 并变灰；侧边菜单角标在 0 时**不显示**（角标 0 是个红点，很吵）。
//      两者别写反 —— accept 2 第 3 段专门盯这个。
//   3) 首页卡片与侧边菜单角标**共用一次请求**的结果（store/modules/lqgTodo.ts），
//      页面自己不许再调一次本文件里的接口（两个请求的数字可能不一样，老师会怀疑系统）。
//   4) cryoOverdue 与「冻存管理」超期页签、超期清单**同源**（后端转发 CRYO-REMIND-001 的唯一判定），
//      前端不要拿冰冻列表自己数。
// ============================================================================

/**
 * GET /lqg/home/todo 的 data —— ★ 恰好七个键（后端契约就是这七个，别自己加）。
 *
 * CR-20260924-10（甲方 2026-09-24 第 25 行）：工作台样本总表拆成「样本记录信息表」「类器官收样记录」两页，
 * 首页「待核验样本」一张卡也拆成两张 → 加了 pendingTissue / pendingOrganoid；
 * pendingSamples 保留（= 两者之和，小程序首页用它），工作台不再单独显示它。
 */
export interface HomeTodoVO {
  /** 待核验样本（组织与类器官都算）= pendingTissue + pendingOrganoid */
  pendingSamples: number;
  /** 待核验的样本记录信息表（组织样本）—— 一张卡 + 「样本记录信息表」菜单角标 */
  pendingTissue: number;
  /** 待核验的类器官收样记录 —— 一张卡 + 「类器官收样记录」菜单角标 */
  pendingOrganoid: number;
  /** 待核验石蜡包埋送样 */
  pendingEmbeds: number;
  /** -80 超期批次数（与超期清单同源） */
  cryoOverdue: number;
  /** 待核验的外部用户档案数 */
  pendingExtUsers: number;
  /** 文档渲染异常数：渲染失败 + 内部版缺图（按「样本 + 文档种类 + 受众」组数，不是行数） */
  renderFailed: number;
}

/**
 * GET /lqg/home/render-issues 的一行 —— 「文档渲染失败」卡片点开的清单（独立验收 V29）。
 * 与卡片上的 renderFailed 同一个口径：一行 = 一份文档的一个版本（内部版 / 外部版）。
 */
export interface HomeRenderIssueVO {
  sampleId: string | number;
  internalNo?: string | null;
  submitNo?: string | null;
  sourceUnitName?: string | null;
  /** sample_qc / organoid_qc / organoid_score / merged */
  docKind: 'sample_qc' | 'organoid_qc' | 'organoid_score' | 'merged';
  /** internal / external */
  audience: 'internal' | 'external';
  /** failed = 渲染失败（含外部版缺图）；missing_images = 内部版照出但缺图 */
  issue: 'failed' | 'missing_images';
  errorMsg?: string | null;
  missingImageCount?: number;
  missingImages?: string | null;
  time?: string | null;
}

/** GET /lqg/home/recent 的一行 */
export interface HomeRecentVO {
  /** 提交时间 */
  submitTime?: string;
  /** 送检单号 */
  submitNo?: string;
  /** 样本类别 tissue / organoid：点一行按它进「样本记录信息表」或「类器官收样记录」（CR-20260924-10） */
  sampleKind?: string;
  /** 来源单位名称 */
  sourceUnitName?: string;
  /** internal / external */
  submitSource?: string;
  /** pending / valid / invalid */
  verifyStatus?: string;
}

/** 待办数（卡片与侧边菜单角标同源；每次调用现算） */
export function getHomeTodo(): AxiosPromise<HomeTodoVO> {
  return request({
    url: '/lqg/home/todo',
    method: 'get'
  });
}

/** 最近提交 10 条（送检时间倒序；不含软删的样本） */
export function getHomeRecent(): AxiosPromise<HomeRecentVO[]> {
  return request({
    url: '/lqg/home/recent',
    method: 'get'
  });
}

/** 渲染失败与缺图清单（「文档渲染失败」卡片点开；与卡片数字同一个口径） */
export function getRenderIssues(): AxiosPromise<HomeRenderIssueVO[]> {
  return request({
    url: '/lqg/home/render-issues',
    method: 'get'
  });
}
