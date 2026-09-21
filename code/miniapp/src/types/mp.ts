// `GET /mp/me` 的响应形状，逐字段对齐 doc/api-contract.md AUTH 段。
// 后端实现见 AUTH-LOGIN-001（`MPUserVo` / `ExtProfileVo`）。
export interface MpExtProfile {
  unitId: string | number | null
  unitName: string | null
  groupId: string | number | null
  groupName: string | null
  unitNameInput: string | null
  groupNameInput: string | null
  /** 字典 lqg_verify_status：unbound / pending / verified / rejected */
  bindStatus: string | null
  rejectReason: string | null
}

export interface MpMe {
  userId: string | number
  name: string
  phoneMasked: string
  /** 后端契约里只会是 'internal' | 'external'，但取数时按 unknown 收，由 normalizeIdentity 判定 */
  identity: unknown
  ext: MpExtProfile | null
}

/** `POST /auth/login` 的响应 data（若依：access_token） */
export interface LoginResult {
  access_token: string
  expires_in?: number
  client_id?: string
  [key: string]: unknown
}
