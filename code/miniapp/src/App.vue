<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { LOGIN_PAGE } from '@/router/config'
import { hasToken } from '@/utils/auth'

// 冷启动守卫：没 token → 去登录页。
// 有 token 也**不在这里猜身份**——首页与「我的」的身份一律等 /mp/me 返回。
onLaunch(() => {
  if (!hasToken()) {
    uni.reLaunch({ url: LOGIN_PAGE })
  }
})
</script>

<style lang="scss">
/* 全局壳的最小重置：颜色、字体、背景都在 src/style/tokens.scss + components.scss 里 */
page {
  background-color: var(--lqg-bg);
}

view,
text,
scroll-view {
  box-sizing: border-box;
}

/* FieldRow（components/lqg/FieldRow.vue）传给 wot 组件的 custom-*-class。
 * ★ 必须写在全局：wot 组件是 addGlobalClass + styleIsolation:shared，只吃得到全局样式，
 *   FieldRow 自己的 scoped 样式够不到 wot 组件内部的节点。
 * - lqg-fr-nowrap：表单项标题一律单行（飞书 2026-09-30 小程序行21）；标签区宽度由 FieldRow 按字数给足
 * - lqg-fr-ta：多行文本（备注 / 情况描述…）文字靠右、最少约两行高（行19、20：和别的表单项一样值在右） */
.lqg-fr-nowrap {
  white-space: nowrap;
}

/* 长标签收一档字号（FieldRow 的 titleClass；wd-input 的字号在内层 label-inner 上，要一起盖） */
.lqg-fr-sm,
.lqg-fr-sm .wd-input__label-inner {
  font-size: 14px;
}

.lqg-fr-xs,
.lqg-fr-xs .wd-input__label-inner {
  font-size: 13px;
}

.lqg-fr-ta {
  text-align: right;
  min-height: 44px;
}

.lqg-fr-ta--left {
  text-align: left;
}
</style>
