<script setup lang="ts">
import { computed } from 'vue'
import { lineIconUri } from './line-icons'

// 线性图标（图形在 `line-icons.ts`，一套笔画一个画布）。
// - 颜色 = 外层元素的文字色（`currentColor`）：放进 `.lqg-well` 就是主色、放进弱文字那一格就是弱色，
//   页面里不给图标单独写颜色。
// - 尺寸按 px 传（落地规范：宫格 22、「我的」行首 18、行尾箭头 16）。
// - 块级、不吃行高：放在 flex 容器里用；要定位 / 上色就包一层自己的 view，别往本组件上挂 class
//  （小程序里挂在自定义组件上的 class 不一定落到内部节点上）。
// - 名字不认识就什么都不画（不画一块实心方块）。
const props = withDefaults(defineProps<{
  name: string
  size?: number
}>(), {
  size: 20,
})

const uri = computed(() => lineIconUri(props.name))
const style = computed(() =>
  `width:${props.size}px;height:${props.size}px;-webkit-mask-image:url("${uri.value}");mask-image:url("${uri.value}");`,
)
</script>

<template>
  <view v-if="uri" class="licon" :style="style" />
</template>

<style lang="scss" scoped>
.licon {
  display: block;
  flex: none;
  background-color: currentColor;
  -webkit-mask-repeat: no-repeat;
  mask-repeat: no-repeat;
  -webkit-mask-position: center;
  mask-position: center;
  -webkit-mask-size: contain;
  mask-size: contain;
}
</style>
