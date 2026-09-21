import type { LoginResult, MpMe } from '@/types/mp'
import { http } from '@/utils/request'

/** 登录入参：对齐 doc/api-contract.md 的 `POST /auth/login` */
export interface LoginPayload {
  xcxCode: string
  phoneCode: string
}

// 小程序登录（grantType=xcx）。
// clientId / tenantId 由请求头与 body 一起带；mock 路径见 src/api/mock-seeds.ts。
export function login(payload: LoginPayload) {
  return http.post<LoginResult>(
    '/auth/login',
    {
      clientId: (import.meta.env.VITE_APP_CLIENT_ID as string) || '',
      grantType: 'xcx',
      tenantId: (import.meta.env.VITE_APP_TENANT_ID as string) || '000000',
      xcxCode: payload.xcxCode,
      phoneCode: payload.phoneCode,
    },
    { redirectOnUnauthorized: false },
  )
}

/** 当前身份：identity / phoneMasked / ext —— 首页与「我的」唯一的数据来源 */
export function fetchMe() {
  return http.get<MpMe>('/mp/me', undefined, { silent: true })
}
