import request from '@/utils/request';
import { AxiosPromise } from 'axios';

// ============================================================================
// AUTH 域 · 内部人员授权（AUTH-STAFF-001）
// 后端：code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/.../auth/staff/controller/StaffController.java
// 契约：doc/api-contract.md「AUTH」一节（路径与字段名逐字对齐）
// 权限串：lqg:auth:staff:{list,grant,edit,resetPwd,revoke}，只授给 lqg_admin
// ============================================================================

/** 列表行（姓名、手机号、角色、是否已绑定微信） */
export interface StaffMemberVO {
  userId: string | number;
  name: string;
  phone: string;
  roleKey: string;
  roleName: string;
  wxBound: boolean;
}

/** 只读预检结果（弹窗提示用） */
export interface StaffCheckVO {
  exists: boolean;
  external: boolean;
  name?: string | null;
}

/** 授权入参 */
export interface StaffGrantForm {
  phone: string;
  name: string;
  roleKey: 'lqg_internal' | 'lqg_admin';
  password: string;
}

/** 授权结果：upgraded=true 表示升级了既有账号（没有新建） */
export interface StaffGrantVO {
  userId: string | number;
  upgraded: boolean;
}

/** 列表（带内部角色的账号） */
export function listStaff(): AxiosPromise<StaffMemberVO[]> {
  return request({
    url: '/lqg/auth/staff',
    method: 'get'
  });
}

/** 只读预检：该手机号是否已有（外部）账号 —— 提交前调，不写库 */
export function checkStaff(phone: string): AxiosPromise<StaffCheckVO> {
  return request({
    url: '/lqg/auth/staff/check',
    method: 'get',
    params: { phone }
  });
}

/** 按手机号授权：已有账号 → 原地升级；没有 → 预建内部账号 */
export function grantStaff(data: StaffGrantForm): AxiosPromise<StaffGrantVO> {
  return request({
    url: '/lqg/auth/staff',
    method: 'post',
    data
  });
}

/** 改内部角色 */
export function changeStaffRole(userId: string | number, roleKey: string) {
  return request({
    url: '/lqg/auth/staff/' + userId + '/role',
    method: 'put',
    data: { roleKey }
  });
}

/** 重置工作台密码（后端只对内部账号放行） */
export function resetStaffPwd(userId: string | number, password: string) {
  return request({
    url: '/lqg/auth/staff/' + userId + '/reset-pwd',
    method: 'put',
    data: { password }
  });
}

/** 撤销内部授权（角色改回外部 + 踢下线，不删号） */
export function revokeStaff(userId: string | number) {
  return request({
    url: '/lqg/auth/staff/' + userId,
    method: 'delete'
  });
}
