// token 的存取（SYS-MP-001）。
// 只做「存哪儿、怎么取、怎么清」三件事；401 的清 token + 跳登录在 utils/request.ts。
export const TOKEN_KEY = 'lqg_mp_token'

export function getToken(): string {
  try {
    return uni.getStorageSync(TOKEN_KEY) || ''
  }
  catch {
    return ''
  }
}

export function setToken(token: string): void {
  uni.setStorageSync(TOKEN_KEY, token)
}

export function clearToken(): void {
  try {
    uni.removeStorageSync(TOKEN_KEY)
  }
  catch {
    // 存储不可用时静默：清 token 不是关键路径
  }
}

export function hasToken(): boolean {
  return !!getToken()
}
