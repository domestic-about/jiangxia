import request from '@/utils/request';
import { AxiosPromise } from 'axios';

// ============================================================================
// AUTH 域 · 来源单位与组别维护 + 外部用户组别核验（AUTH-GROUP-001）
// 后端：code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/.../auth/group/controller/*.java
// 契约：doc/api-contract.md「AUTH」一节
// 权限串：lqg:auth:unit:{list,add,edit,toggle} / lqg:auth:group:{list,add,edit,toggle}
//         lqg:auth:extuser:{list,verify}
//         —— 四张菜单一起授给 101（lqg_admin）与 102（lqg_internal）：核验组别是日常工作。
//
// ★ 单位 / 组别**不物理删**（ticket §2.2）：只有「启用 / 停用」，所以这里没有 remove，
//   只有 toggleUnitStatus / toggleGroupStatus（契约里的 DELETE 本票不做，见完工报告）。
// ============================================================================

// ── 来源单位 ────────────────────────────────────────────────────────────────

/** 单位行（工作台；含停用项） */
export interface SourceUnitVO {
  unitId: string | number;
  unitName: string;
  unitStatus: 'active' | 'pending' | 'disabled';
  remark?: string | null;
  groupCount?: number;
  createTime?: string;
}

export interface SourceUnitForm {
  unitName: string;
  unitStatus?: string;
  remark?: string;
}

/** 单位列表（含停用项 + 每行组别数） */
export function listUnits(): AxiosPromise<SourceUnitVO[]> {
  return request({
    url: '/lqg/auth/unit',
    method: 'get'
  });
}

/** 新增单位 */
export function addUnit(data: SourceUnitForm): AxiosPromise<string | number> {
  return request({
    url: '/lqg/auth/unit',
    method: 'post',
    data
  });
}

/** 改名 / 改备注 */
export function updateUnit(unitId: string | number, data: SourceUnitForm) {
  return request({
    url: '/lqg/auth/unit/' + unitId,
    method: 'put',
    data
  });
}

/** 启用 / 停用（不物理删） */
export function toggleUnitStatus(unitId: string | number, status: 'active' | 'disabled') {
  return request({
    url: '/lqg/auth/unit/' + unitId + '/status',
    method: 'put',
    data: { status }
  });
}

// ── 组别 ────────────────────────────────────────────────────────────────────

/** 组别行（工作台；verifiedCount 是读时 count） */
export interface UnitGroupVO {
  groupId: string | number;
  unitId: string | number;
  unitName?: string;
  groupName: string;
  groupStatus: 'active' | 'pending' | 'disabled';
  remark?: string | null;
  verifiedCount?: number;
  createTime?: string;
}

export interface UnitGroupForm {
  unitId: string | number;
  groupName: string;
  groupStatus?: string;
  remark?: string;
}

/** 某单位下的组别（行上带 readonly 的 verifiedCount） */
export function listGroups(unitId: string | number): AxiosPromise<UnitGroupVO[]> {
  return request({
    url: '/lqg/auth/group',
    method: 'get',
    params: { unitId }
  });
}

/** 新增组别 */
export function addGroup(data: UnitGroupForm): AxiosPromise<string | number> {
  return request({
    url: '/lqg/auth/group',
    method: 'post',
    data
  });
}

/** 改名 / 改备注（不允许改所属单位） */
export function updateGroup(groupId: string | number, data: UnitGroupForm) {
  return request({
    url: '/lqg/auth/group/' + groupId,
    method: 'put',
    data
  });
}

/** 启用 / 停用（不物理删；已绑定的人不受影响） */
export function toggleGroupStatus(groupId: string | number, status: 'active' | 'disabled') {
  return request({
    url: '/lqg/auth/group/' + groupId + '/status',
    method: 'put',
    data: { status }
  });
}

// ── 外部用户与核验 ──────────────────────────────────────────────────────────

/** 外部用户行 */
export interface ExtUserVO {
  userId: string | number;
  name: string;
  phone: string;
  unitId?: string | number | null;
  unitName?: string | null;
  groupId?: string | number | null;
  groupName?: string | null;
  /** 外部自填的原名（自填时后端带上，供核验弹窗显示「新建为 X」） */
  unitNameInput?: string | null;
  groupNameInput?: string | null;
  selfInput?: boolean;
  bindStatus: 'unbound' | 'pending' | 'verified' | 'rejected';
  rejectReason?: string | null;
  sampleCount?: number;
  lastLoginTime?: string | null;
}

/** 核验入参 */
export interface ExtVerifyForm {
  action: 'approve' | 'reject';
  unitId?: string | number | null;
  groupId?: string | number | null;
  createUnit?: boolean;
  createGroup?: boolean;
  reason?: string;
}

/** 外部用户列表（状态 / 单位可筛） */
export function listExtUsers(params?: { bindStatus?: string; unitId?: string | number }): AxiosPromise<ExtUserVO[]> {
  return request({
    url: '/lqg/auth/ext-user/list',
    method: 'get',
    params
  });
}

/** 核验：通过（自填的必须选「新建」或「归并」）或驳回（原因必填） */
export function verifyExtUser(userId: string | number, data: ExtVerifyForm) {
  return request({
    url: '/lqg/auth/ext-user/' + userId + '/verify',
    method: 'put',
    data
  });
}
