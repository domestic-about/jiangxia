// 线性图标（方向 A 落地规范 §5.2：图标井里放「线性图标」）—— 一套笔画、一个画布，全站共用。
//
// ★ 为什么不用 wd-icon：wot 的字体图标在小程序里从 at.alicdn.com 在线拉字体（只有 H5 / App 用包内 ttf），
//   内网或弱网下整套图标会变成方块；也没有试管、类器官、冻存这类业务图形。
// ★ 为什么不用 <image src="*.svg">：图片的颜色画死在文件里，跟不了 tokens.scss 的变量。
//   这里的做法是**遮罩**：SVG 只当形状（遮罩只看不透明度），颜色由 `LineIcon.vue` 的
//   `background-color: currentColor` 给 —— 图标永远是它所在那一行的文字色，零色值字面量。
// ★ 画法统一：24×24 画布、1.8 描边、圆头圆角、不填充。新增图标照这个画布画，别混别的风格。
//   `stroke='currentColor'` 只是让笔画不透明（遮罩里它是什么颜色都无所谓），不是颜色值。

/** 每个图标 = 24×24 画布里的若干笔画（SVG 片段） */
const ICON_BODY = {
  /** 样本记录信息表：试管 + 液面 */
  sample: '<path d=\'M7 3h10\'/><path d=\'M8 3v13.5a4 4 0 0 0 8 0V3\'/><path d=\'M8 11h8\'/>',
  /** 类器官收样记录：一团细胞 */
  organoid: '<circle cx=\'12\' cy=\'12\' r=\'8.5\'/><circle cx=\'9.6\' cy=\'10.2\' r=\'2.2\'/><circle cx=\'14.4\' cy=\'10.2\' r=\'2.2\'/><circle cx=\'12\' cy=\'14.4\' r=\'2.2\'/>',
  /** 石蜡包埋送样记录：蜡块（立方体） */
  embed: '<path d=\'M12 3 3.8 7.4v9.2L12 21l8.2-4.4V7.4L12 3Z\'/><path d=\'M3.8 7.4 12 11.8l8.2-4.4\'/><path d=\'M12 11.8V21\'/>',
  /** -80 冻存记录：雪花 */
  cryo: '<path d=\'M12 2.5v19M20.23 7.25 3.77 16.75M20.23 16.75 3.77 7.25\'/><path d=\'M14.4 3.4 12 5.8 9.6 3.4M20.65 9.78l-3.28-.88.88-3.28M18.25 18.38l-.88-3.28 3.28-.88M9.6 20.6l2.4-2.4 2.4 2.4M3.35 14.22l3.28.88-.88 3.28M5.75 5.62l.88 3.28-3.28.88\'/>',
  /** 历史编辑记录：逆时针的钟 */
  history: '<path d=\'M3.5 12A8.5 8.5 0 1 0 6 6l-2.5 2.5\'/><path d=\'M3.5 4v4.5H8\'/><path d=\'M12 7.5V12l3 1.8\'/>',
  /** 单位与组别 / 所属单位：楼 */
  unit: '<path d=\'M4.5 20.5V5.5a1.5 1.5 0 0 1 1.5-1.5h7a1.5 1.5 0 0 1 1.5 1.5v15\'/><path d=\'M14.5 9.5H18a1.5 1.5 0 0 1 1.5 1.5v9.5\'/><path d=\'M3 20.5h18\'/><path d=\'M8 8h3M8 11.5h3M8 15h3\'/>',
  /** 用户协议：文件 */
  agreement: '<path d=\'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5Z\'/><path d=\'M14 3v5h5\'/><path d=\'M9 13h6M9 16.5h4\'/>',
  /** 隐私政策：盾牌 + 勾 */
  privacy: '<path d=\'M12 3 5 5.8v5.7c0 4.2 2.9 7.8 7 9 4.1-1.2 7-4.8 7-9V5.8L12 3Z\'/><path d=\'m9 11.8 2.1 2.1 3.9-3.9\'/>',
  /** 退出登录：门 + 向外的箭头 */
  logout: '<path d=\'M10 20.5H6a1.5 1.5 0 0 1-1.5-1.5V5A1.5 1.5 0 0 1 6 3.5h4\'/><path d=\'m15.5 16.5 4.5-4.5-4.5-4.5\'/><path d=\'M20 12H9.5\'/>',
  /** 手机号 */
  phone: '<rect x=\'6.5\' y=\'2.5\' width=\'11\' height=\'19\' rx=\'2.5\'/><path d=\'M10.5 18h3\'/>',
  /** 时钟（首页底部说明前那个） */
  clock: '<circle cx=\'12\' cy=\'12\' r=\'8.5\'/><path d=\'M12 7.5V12l3 1.8\'/>',
  /** 行尾的右箭头 */
  chevron: '<path d=\'m9.5 5.5 6.5 6.5-6.5 6.5\'/>',
} as const

export type LineIconName = keyof typeof ICON_BODY

/** 全部图标名（单测与图标一览用） */
export const LINE_ICON_NAMES = Object.keys(ICON_BODY) as LineIconName[]

function svgOf(name: LineIconName): string {
  return `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'>${ICON_BODY[name]}</svg>`
}

const cache = new Map<string, string>()

/**
 * 图标 → 遮罩用的 data URI（整串 URL 编码：小程序 wxss / 行内样式里 `#`、`<` 这些原样写会断）。
 * 不认识的名字给空串（调用方据此不渲染，别渲染一块实心方块）。
 */
export function lineIconUri(name: string): string {
  // 用 hasOwn 而不是 in：'toString' 这类原型上的名字不能当成图标
  if (!Object.prototype.hasOwnProperty.call(ICON_BODY, name)) {
    return ''
  }
  let uri = cache.get(name)
  if (!uri) {
    uri = `data:image/svg+xml,${encodeURIComponent(svgOf(name as LineIconName))}`
    cache.set(name, uri)
  }
  return uri
}
