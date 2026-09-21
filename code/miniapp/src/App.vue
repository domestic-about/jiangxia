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
</style>
