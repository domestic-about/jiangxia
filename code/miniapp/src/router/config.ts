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

/**
 * 表单提交成功后的去处（UX 测试 MP-01 / MP-02：提交后返回，回到的还是填满的表单，再点一次就重复建一条）：
 *   · 上一页就是目标页（从历史编辑记录点一行进来改的）→ 直接退回去，目标页 onShow 自己刷新；
 *   · 否则把当前表单页**替换**成目标页（redirectTo），表单不留在页面栈里，返回键回不到它。
 */
export function finishTo(path: string): void {
  if (!path) {
    return
  }
  if (isTabPage(path)) {
    uni.switchTab({ url: path })
    return
  }
  const pages = getCurrentPages()
  const prev = pages.length > 1 ? pages[pages.length - 2] : null
  if (prev && prev.route === path.split('?')[0].replace(/^\//, '')) {
    uni.navigateBack()
    return
  }
  uni.redirectTo({ url: path })
}
