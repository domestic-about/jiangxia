// Vitest 配置（SYS-MP-001）。
//
// 与 vite.config.ts 分离：单测只需要 `@vitejs/plugin-vue` 跑 SFC 与别名，
// 不需要 uni-app / unocss / unplugin-* 那一串只服务编译产物的插件
// （那些插件在测试环境里会要求 uni 全局）。
//
// 跑法：pnpm vitest run src/pages/index/entries.fixture.spec.ts
import path from 'node:path'
import process from 'node:process'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

const root = process.cwd()

export default defineConfig({
  plugins: [vue()],
  // 与 vite.config.ts 同名的构建期常量：单测里一律按「生产」口径（测试身份入口关着）
  define: {
    __LQG_MOCK_LOGIN__: 'false',
  },
  test: {
    environment: 'node',
    globals: true,
    include: ['src/**/*.{test,spec}.{ts,vue}'],
    exclude: ['node_modules', 'dist', 'dist/**'],
  },
  resolve: {
    alias: {
      '@': path.join(root, './src'),
      // 需求层的验收 fixture 在仓库根的 doc/verify/fixtures/ 下，不在小程序工程里
      '@doc': path.resolve(root, '../../doc'),
    },
  },
})
