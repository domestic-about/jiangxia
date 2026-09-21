// 调试登录入口的 seed 身份（**只在 dev 构建里存在**，见 pages/login/index.vue）。
//
// 走 AUTH-LOGIN-001 落地的 mock 路径：`xcxCode="mock:<key>"` + `phoneCode="mock:<手机号>"`，
// grantType=xcx。seed 来源 doc/verify/seed/（reseed 后可用）。
//
// ADR-0008：mock 登录只允许在 dev / test 存在；生产构建里这段代码必须被摇掉
// （accept 第 1 条会 grep 生产产物里不能出现 `mock:ext`）。
export interface MockSeed {
  key: string
  /** 调试入口上的按钮文案 */
  label: string
  phone: string
  /** 方便肉眼确认选对了身份 */
  expectIdentity: 'internal' | 'external'
}

export const MOCK_SEEDS: MockSeed[] = [
  { key: 'staff', label: '内部人员 · 李工', phone: '13800000001', expectIdentity: 'internal' },
  { key: 'extA', label: '外部人员 · 王医生（已核验）', phone: '13800000011', expectIdentity: 'external' },
  { key: 'newbie1', label: '外部人员 · 新号（未绑定）', phone: '13800000099', expectIdentity: 'external' },
]

/** mock 登录时构造的两个 code（真实环境下这两个值来自 wx.login 与 getPhoneNumber） */
export function mockCodes(seed: MockSeed): { xcxCode: string, phoneCode: string } {
  return {
    xcxCode: `mock:${seed.key}`,
    phoneCode: `mock:${seed.phone}`,
  }
}
