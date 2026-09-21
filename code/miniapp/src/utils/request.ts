// 统一请求层（SYS-MP-001 §2）。
//
// 契约（doc/api-contract.md 通用段）：
// - 请求头一律带 `Authorization: Bearer <token>` + `clientid: <clientId>`（缺 clientid → token 无效）
// - 响应体是 `{code, msg, data}`，HTTP 状态码几乎恒为 200，**成败看 code**
// - 业务码 401 → 清 token 跳登录页；其余非 200 → toast `msg`
//
// 小程序端 `uni.request` 的 statusCode 与业务 code 都可能表达 401，两条都要处理。
import { LOGIN_PAGE } from '@/router/config'
import { TOKEN_KEY, clearToken, getToken } from '@/utils/auth'

interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

export interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  /** query 参数（会自动丢掉 undefined / null / 空串） */
  params?: Record<string, unknown>
  data?: unknown
  header?: Record<string, string>
  /** 是否在 401 时自动跳登录页（登录接口自身传 false） */
  redirectOnUnauthorized?: boolean
  /** 是否静默：true 时非 200 不弹 toast，由调用方自己处理 */
  silent?: boolean
}

/** 是否正在跳登录页，避免并发请求弹多次 */
let redirecting = false

// 取出真正的请求根地址。
// - 小程序 / App：直连 `VITE_SERVER_BASEURL`（真机调试时它是 LAN IP，见栈包 gotchas §6.6）
// - H5：走 vite dev server 代理前缀，绕开浏览器跨域
function resolveBaseUrl(): string {
  const direct = (import.meta.env.VITE_SERVER_BASEURL as string) || ''
  // #ifdef H5
  if (import.meta.env.DEV && import.meta.env.VITE_APP_PROXY_ENABLE === 'true') {
    return (import.meta.env.VITE_APP_PROXY_PREFIX as string) || ''
  }
  // #endif
  return direct
}

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) {
    return ''
  }
  const pairs: string[] = []
  Object.keys(params).forEach((key) => {
    const value = params[key]
    if (value === undefined || value === null || value === '') {
      return
    }
    pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
  })
  return pairs.length > 0 ? `?${pairs.join('&')}` : ''
}

/** 401 的统一处理：清 token + 跳登录页（带防抖，避免并发请求连着跳） */
export function handleUnauthorized(): void {
  clearToken()
  if (redirecting) {
    return
  }
  redirecting = true
  const pages = getCurrentPages()
  const current = pages.length > 0 ? `/${pages[pages.length - 1].route}` : ''
  if (current === LOGIN_PAGE) {
    redirecting = false
    return
  }
  uni.reLaunch({
    url: LOGIN_PAGE,
    complete: () => {
      redirecting = false
    },
  })
}

// 发一个请求。成功时 resolve 业务 `data`；失败时 reject 一个 Error，
// 并把 toast / 跳登录页这两件通用事做完。
export function request<T = unknown>(options: RequestOptions): Promise<T> {
  const {
    url,
    method = 'GET',
    params,
    data,
    header,
    redirectOnUnauthorized = true,
    silent = false,
  } = options

  const finalUrl = `${resolveBaseUrl()}${url}${buildQuery(params)}`
  const token = getToken()

  const finalHeader: Record<string, string> = {
    'clientid': (import.meta.env.VITE_APP_CLIENT_ID as string) || '',
    'Content-Type': 'application/json;charset=utf-8',
    ...header,
  }
  if (token) {
    finalHeader.Authorization = `Bearer ${token}`
  }

  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: finalUrl,
      method,
      data: data as any,
      header: finalHeader,
      success: (res) => {
        const statusCode = res.statusCode
        const body = res.data as ApiResponse<T> | undefined

        if (statusCode === 401 || (body && body.code === 401)) {
          if (redirectOnUnauthorized) {
            handleUnauthorized()
          }
          reject(new Error((body && body.msg) || '登录状态已失效，请重新登录'))
          return
        }
        if (statusCode !== 200) {
          const msg = (body && body.msg) || `请求失败（${statusCode}）`
          if (!silent) {
            uni.showToast({ title: msg, icon: 'none' })
          }
          reject(new Error(msg))
          return
        }
        if (!body) {
          reject(new Error('响应体为空'))
          return
        }
        if (body.code !== 200) {
          const msg = body.msg || '请求失败'
          if (!silent) {
            uni.showToast({ title: msg, icon: 'none' })
          }
          reject(new Error(msg))
          return
        }
        resolve(body.data)
      },
      fail: (err) => {
        const msg = '网络连接失败，请检查网络'
        if (!silent) {
          uni.showToast({ title: msg, icon: 'none' })
        }
        reject(new Error(err.errMsg || msg))
      },
    })
  })
}

export const http = {
  get: <T = unknown>(url: string, params?: Record<string, unknown>, options: Partial<RequestOptions> = {}) =>
    request<T>({ url, method: 'GET', params, ...options }),
  post: <T = unknown>(url: string, data?: unknown, options: Partial<RequestOptions> = {}) =>
    request<T>({ url, method: 'POST', data, ...options }),
  put: <T = unknown>(url: string, data?: unknown, options: Partial<RequestOptions> = {}) =>
    request<T>({ url, method: 'PUT', data, ...options }),
  delete: <T = unknown>(url: string, data?: unknown, options: Partial<RequestOptions> = {}) =>
    request<T>({ url, method: 'DELETE', data, ...options }),
}

export { TOKEN_KEY }
