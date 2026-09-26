// 两个核验页共用的「提交之后」：成功回到来的那一页，失败把后端原话弹框给人看。
//
// ★ 失败用弹框不用 toast：后端的业务拒绝常常是一整句（「内部编号「T-hli01」已存在，请换一个」
//   「所挂样本还未核验有效（当前状态：pending），不能判为有效」），toast 两秒就没了、长句还会被截断；
//   弹框要人点「知道了」，人还停在核验页，改完再提交。
// ★ 成功先轻提示，再回到来的那一页（待核验列表 / 内部管理表格页，它们听 `VERIFIED_EVENT` 重新取数；
//   首页的数在首页再次显示时重取）。深链直接打开、没有上一页时落到「待核验」列表。

/** 待核验列表页（没有上一页时的落点） */
const VERIFY_LIST = '/pages/verify/index'

export function backAfterVerify(message: string): void {
  uni.showToast({ title: message, icon: 'success' })
  setTimeout(() => {
    if (getCurrentPages().length > 1) {
      uni.navigateBack()
      return
    }
    uni.redirectTo({ url: VERIFY_LIST })
  }, 700)
}

export function verifyFailed(e: unknown): void {
  const message = e instanceof Error && e.message ? e.message : '没能保存，请稍后再试'
  uni.showModal({ title: '没能保存', content: message, showCancel: false, confirmText: '知道了' })
}
