import type { LoginPayload } from '@/api/auth'
import type { MpMe } from '@/types/mp'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchMe, login as loginApi } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/utils/auth'

// 当前用户 store：**只有 /mp/me 的返回值是身份的来源**。
// identity 原样保存（unknown），页面用 `normalizeIdentity` / `entriesFor` / `meSections` 判定，
// 不在 store 里做「兜底成内部」这种危险的默认值。
export const useUserStore = defineStore('lqg-user', () => {
  const token = ref<string>(getToken())
  const me = ref<MpMe | null>(null)
  const loading = ref(false)
  const error = ref('')

  const identity = computed<unknown>(() => me.value?.identity)
  // 首次登录时后端用「wx_手机号」占位当昵称：当成没填（UX 测试 MP-06：首页问候露出完整手机号、
  // 「单位与组别」的姓名框被它占住、看不到「请填写真实姓名」）
  const name = computed<string>(() => {
    const raw = me.value?.name || ''
    return /^wx_\d+$/.test(raw) ? '' : raw
  })
  const phoneMasked = computed<string>(() => me.value?.phoneMasked || '')
  const ext = computed(() => me.value?.ext || null)

  function setTokenValue(value: string) {
    token.value = value
    setToken(value)
  }

  function clear() {
    token.value = ''
    me.value = null
    error.value = ''
    clearToken()
  }

  /** 登录：拿 token → 存 → 拉 /mp/me */
  async function login(payload: LoginPayload): Promise<MpMe> {
    const res = await loginApi(payload)
    if (!res || !res.access_token) {
      throw new Error('登录失败：服务端没有返回 token')
    }
    setTokenValue(res.access_token)
    return loadMe()
  }

  /** 拉当前身份；401 时请求层会清 token 跳登录页 */
  async function loadMe(): Promise<MpMe> {
    loading.value = true
    error.value = ''
    try {
      const data = await fetchMe()
      me.value = data
      return data
    }
    catch (e) {
      error.value = e instanceof Error ? e.message : '没能加载'
      throw e
    }
    finally {
      loading.value = false
    }
  }

  async function logout() {
    clear()
  }

  return {
    token,
    me,
    loading,
    error,
    identity,
    name,
    phoneMasked,
    ext,
    login,
    loadMe,
    logout,
    clear,
  }
})
