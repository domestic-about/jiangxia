/** 登录页路径（唯一出口，请求层 401 与页面跳转都从这里取） */
export const LOGIN_PAGE = '/pages/login/index'

/** 登录后的落地页（首页是 tab 页，只能 reLaunch / switchTab） */
export const HOME_PAGE = '/pages/index/index'

export const TAB_PAGES = ['/pages/index/index', '/pages/doc/index', '/pages/me/index']

/** 是不是 tab 页：跳 tab 页要走 switchTab，否则 navigateTo 会失败 */
export function isTabPage(path: string): boolean {
  return TAB_PAGES.includes(path)
}

/** 统一的页面跳转：tab 页用 switchTab，其余 navigateTo */
export function goPage(path: string): void {
  if (!path) {
    return
  }
  if (isTabPage(path)) {
    uni.switchTab({ url: path })
    return
  }
  uni.navigateTo({
    url: path,
    fail: () => {
      // 页面栈满（最多 10 层）时退化为 redirectTo，别让点击没反应
      uni.redirectTo({ url: path })
    },
  })
}
