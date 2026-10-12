// 内部人员名单的取数（CR-20261012-22）：全站共用一份，半分钟内不重复请求。
// 管理员刚在「内部人员授权」里加的人，最迟半分钟、或者重新点开下拉就能选到。
import { listStaffOptions } from '@/api/lqg/staff';
import { StaffOption, normalizeStaff } from './staff';

const TTL = 30_000;

const staff = ref<StaffOption[]>([]);
let loadedAt = 0;
let pending: Promise<StaffOption[]> | null = null;

const load = (force = false): Promise<StaffOption[]> => {
  if (pending) {
    return pending;
  }
  if (!force && loadedAt && Date.now() - loadedAt < TTL) {
    return Promise.resolve(staff.value);
  }
  pending = listStaffOptions()
    .then((res) => {
      staff.value = normalizeStaff(res.data);
      loadedAt = Date.now();
      return staff.value;
    })
    // 拉不到：保留上一次的名单（没有就是空的），下拉里现值照样看得见；下次打开再试
    .catch(() => staff.value)
    .finally(() => {
      pending = null;
    });
  return pending;
};

export const useStaff = () => ({ staff, load });
