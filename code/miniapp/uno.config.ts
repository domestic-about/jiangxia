import type { Preset } from 'unocss'
import { presetUni } from '@uni-helper/unocss-preset-uni'
import { presetLegacyCompat } from '@unocss/preset-legacy-compat'
import {
  defineConfig,
  presetIcons,
  transformerDirectives,
  transformerVariantGroup,
} from 'unocss'

/**
 * UnoCSS：只用来给 uni-app 的 rpx / 单位与 `@apply` 指令兜底。
 * 业务页面的样式一律走 `src/style/tokens.scss` 的变量与 `.lqg-*` 范式类
 * （落地规范 §8.3：不裸写 view + style 去模拟控件）。
 */
export default defineConfig({
  presets: [
    presetUni({ attributify: false }),
    presetIcons({
      scale: 1.2,
      warn: false,
      extraProperties: {
        'display': 'inline-block',
        'vertical-align': 'middle',
      },
    }),
    presetLegacyCompat({
      commaStyleColorFunction: true,
    }) as Preset,
  ],
  transformers: [
    transformerDirectives(),
    transformerVariantGroup(),
  ],
  shortcuts: [
    { center: 'flex justify-center items-center' },
  ],
})
