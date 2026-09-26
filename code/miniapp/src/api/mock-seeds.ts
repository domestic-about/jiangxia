// 测试身份登录入口的 seed 身份（**只在开了 mock 登录的构建里存在**，见 pages/login/index.vue）。
//
// 走 AUTH-LOGIN-001 落地的 mock 路径：`xcxCode="mock:<key>"` + `phoneCode="mock:<手机号>"`，
// grantType=xcx；后端把 key 换成 openid `mock-openid-<key>`，与 doc/verify/seed/ 里的绑定逐字对应
// （reseed 后可用）。身份与手机号以 doc/verify/README.md 的「身份」表为准。
//
// ADR-0008：mock 登录只允许在 dev / test 存在。**生产构建里这份清单必须一个字都不剩**：
//   开关是 vite.config.ts 里 define 的构建期常量 `__LQG_MOCK_LOGIN__`（判据只写在那一处：
//   development / test 两种 mode 且 VITE_MOCK_LOGIN=1）。它在每个模块里被原样替换成字面量 ——
//   生产构建里是 false，下面每个三元的「开」分支（清单、面板文案、拼 mock 串的代码）都成了死代码，
//   登录页也用同一个常量判断，打包时整段摇掉，连这个模块都不会进包。
//   （核对：生产包里 grep 不到任何 seed 手机号、身份代号、`mock:` 前缀与面板文案。）

export interface MockSeed {
  key: string
  /** 入口上的按钮文案 */
  label: string
  phone: string
  /** 方便肉眼确认选对了身份 */
  expectIdentity: 'internal' | 'external'
}

/**
 * 入口面板的文案（G24：中性措辞 —— 本地开发构建与测试环境体验版都有这个入口，正式版没有）。
 * 关着时是 null：文案不进模板静态结构，生产包的 wxml 里也搜不到。
 */
export const MOCK_PANEL: { title: string, desc: string } | null = __LQG_MOCK_LOGIN__
  ? {
      title: '测试身份登录',
      desc: '选一个预置的测试身份直接进入，正式版没有这个入口',
    }
  : null

/** seed 身份（顺序：内部 → 外部 A–F → 未绑定新号） */
export const MOCK_SEEDS: MockSeed[] = __LQG_MOCK_LOGIN__
  ? [
      { key: 'staff', label: '内部人员 · 李工', phone: '13800000001', expectIdentity: 'internal' },
      { key: 'extA', label: '外部人员 · 王医生（已核验）', phone: '13800000011', expectIdentity: 'external' },
      { key: 'extB', label: '外部人员 · 陈医生（与王医生同组）', phone: '13800000012', expectIdentity: 'external' },
      { key: 'extC', label: '外部人员 · 赵医生（同单位异组）', phone: '13800000013', expectIdentity: 'external' },
      { key: 'extD', label: '外部人员 · 孙老师（B 大学）', phone: '13800000014', expectIdentity: 'external' },
      { key: 'extE', label: '外部人员 · 周医生（同组待核验）', phone: '13800000015', expectIdentity: 'external' },
      { key: 'extF', label: '外部人员 · 吴同学（自填单位待核验）', phone: '13800000016', expectIdentity: 'external' },
      { key: 'newbie1', label: '外部人员 · 新号（未绑定）', phone: '13800000099', expectIdentity: 'external' },
    ]
  : []

/** mock 登录时构造的两个 code（真实环境下这两个值来自 wx.login 与 getPhoneNumber）；关着时返回 null */
export function mockCodes(seed: MockSeed): { xcxCode: string, phoneCode: string } | null {
  if (!__LQG_MOCK_LOGIN__) {
    return null
  }
  return {
    xcxCode: `mock:${seed.key}`,
    phoneCode: `mock:${seed.phone}`,
  }
}
