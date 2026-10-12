import request from '@/utils/request';
import { AxiosPromise } from 'axios';

// ============================================================================
// 「选人」用的内部人员名单（CR-20261012-22）
//
// 后端：.../auth/staff/controller/StaffDirectoryController.java（GET /lqg/staff/options）
// 鉴权：内部角色闸（每个内部人员都能调，不要权限串）—— 与「内部人员授权」页的 /lqg/auth/staff 不是一回事：
//       那个只有管理员能调、带手机号与角色；这里只有账号 id 与姓名。
//
// ★ userId 一律当**字符串**用：真实账号是雪花 id（19 位），超出 JS 安全整数，后端对这种值按字符串下发；
//   回传时原样带回去即可（后端收 Long，认字符串）。
// ============================================================================

export interface StaffOptionVO {
  userId: string | number;
  name: string;
}

/** 内部人员名单（按姓名排序） */
export const listStaffOptions = (): AxiosPromise<StaffOptionVO[]> => {
  return request({
    url: '/lqg/staff/options',
    method: 'get'
  });
};
