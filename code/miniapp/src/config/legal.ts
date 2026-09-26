// 《用户协议》《隐私政策》里的主体名称与联系方式（V06）——**只在这一个文件里改**，两页一起变。
//
// - 主体名称：缺省取合同甲方（doc/commercial/ 开发合同首页的「甲方（委托方）」）。小程序以甲方主体
//   注册、认证，协议与隐私政策里的「我们」就是它；甲方如有另外的正式对外名称，改这里。
// - 联系方式：留空 = 页面上显示醒目的「上线前公布」小标。**提审上线前必须填上真实的电话与邮箱**：
//   隐私政策里「怎么联系我们查询、更正、删除数据」要落到一个真能找到人的渠道。
// - 更新日期：正文有实质改动时一并改。

/** 运营方（协议与隐私政策里的「我们」） */
export const LEGAL_OPERATOR = '湖北江夏实验室类器官研究中心'

/** 小程序的名字（正文里「本小程序」指的就是它） */
export const LEGAL_SERVICE = '类器官送检'

/** 联系方式：留空的项在页面上显示 {@link LEGAL_PENDING_TEXT} */
export const LEGAL_CONTACT: { phone: string, email: string } = {
  phone: '',
  email: '',
}

/** 联系方式还没定时页面上显示的字样（醒目的琥珀色小标） */
export const LEGAL_PENDING_TEXT = '上线前公布'

/** 两份文件的更新日期 */
export const LEGAL_UPDATED = '2026 年 9 月 23 日'

/** 正文的一节：标题 + 若干段（两页的正文各自写在页面文件里） */
export interface LegalSection {
  /** 小节标题，如「一、我们收集哪些信息」 */
  title: string
  /** 一段一条 */
  paras: string[]
}
