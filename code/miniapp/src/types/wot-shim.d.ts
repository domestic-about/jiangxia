// type-check 用的 wot-design-uni 组件替身（**只给 vue-tsc 看**；构建走 vite 的解析，不经过这里）。
//
// ★ 为什么要有它：`import WdCell from 'wot-design-uni/components/wd-cell/wd-cell.vue'` 会把 wot 的
//   组件源码连同 common/util.ts、base64.ts 一起拉进类型检查，报出 wot 自己的类型错误（基线 22 个）。
//   wot 源码不改；tsconfig 的 paths 把 `wot-design-uni/components/*` 指到这里，
//   wot 的源码就不再进 type-check 的范围。代价：模板里 wd-* 的属性与插槽不做类型检查（运行时不受影响）。
declare const component: any
export default component
