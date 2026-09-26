import { defineConfig, loadEnv } from 'vite';
import createPlugins from './vite/plugins';
import autoprefixer from 'autoprefixer'; // css自动添加兼容性前缀
import path from 'path';
import { createHash } from 'crypto';

/**
 * 若依公开的那对接口加密密钥（512 位，任何人都有）的 SHA-256。生产构建里出现它们 = 等于没加密。
 * 只存摘要、不把密钥原文再抄一遍进来。
 */
const UPSTREAM_RSA_KEY_SHA256 = [
  'bb4c3674ce2c219924f32c7a7759a6918131e01336ed75919c78ec5ffdf16e1e',
  '31befe1ec70b10bfac38753193ebc84cd655e2a31d049f0591fda54dc2fabe1b'
];

/**
 * V14（独立验收 2026-09-23）：生产构建的接口加密密钥只从环境变量来（.env.production 不再写死）。
 * - 注入的是若依公开的默认密钥 → 直接构建失败；
 * - 没注入 → 照常出产物（票面 accept 里的 build:prod 只验构建与页面），但打醒目警告：这样的产物登录必失败，
 *   不能拿去部署。正式部署走 code/deploy/prod/deploy.sh，它会先核对密钥是否成对再构建。
 * dev / test 构建不查（test 的接口加密本来就关着）。
 */
function checkApiCryptoKeys(mode: string, command: string, env: Record<string, string>) {
  if (command !== 'build' || mode !== 'production' || env.VITE_APP_ENCRYPT !== 'true') {
    return;
  }
  const keys = { VITE_APP_RSA_PUBLIC_KEY: env.VITE_APP_RSA_PUBLIC_KEY, VITE_APP_RSA_PRIVATE_KEY: env.VITE_APP_RSA_PRIVATE_KEY };
  for (const [name, value] of Object.entries(keys)) {
    if (value && UPSTREAM_RSA_KEY_SHA256.includes(createHash('sha256').update(value.trim()).digest('hex'))) {
      throw new Error(`[V14] ${name} 是若依公开的默认密钥，不能用于生产构建：用 code/deploy/prod/gen-secrets.sh 生成新的一对`);
    }
  }
  const missing = Object.entries(keys)
    .filter(([, value]) => !value || !value.trim())
    .map(([name]) => name);
  if (missing.length > 0) {
    console.warn(
      `\n\x1b[33m[V14] 生产构建没有注入接口加密密钥：${missing.join('、')}。\n` +
        '      这份产物的登录请求加密不了（必然失败），只能用来看构建是否通过，不能部署。\n' +
        '      正式部署请走 code/deploy/prod/deploy.sh（从 .env 注入并核对成对）。\x1b[0m\n'
    );
  }
}

export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd());
  checkApiCryptoKeys(mode, command, env);
  return {
    // 部署生产环境和开发环境下的URL。
    // 默认情况下，vite 会假设你的应用是被部署在一个域名的根路径上
    // 例如 https://www.ruoyi.vip/。如果应用被部署在一个子路径上，你就需要用这个选项指定这个子路径。例如，如果你的应用被部署在 https://www.ruoyi.vip/admin/，则设置 baseUrl 为 /admin/。
    base: env.VITE_APP_CONTEXT_PATH,
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src')
      },
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    // https://cn.vitejs.dev/config/#resolve-extensions
    plugins: createPlugins(env, command === 'build'),
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_APP_PORT),
      open: false,
      proxy: {
        [env.VITE_APP_BASE_API]: {
          target: env.VITE_APP_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
          ws: true,
          rewrite: (path) => path.replace(new RegExp('^' + env.VITE_APP_BASE_API), '')
        }
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          // additionalData: '@use "@/assets/styles/variables.module.scss as *";'
          // javascriptEnabled: true
          api: 'modern-compiler'
        }
      },
      postcss: {
        plugins: [
          // 浏览器兼容性
          autoprefixer(),
          {
            postcssPlugin: 'internal:charset-removal',
            AtRule: {
              charset: (atRule) => {
                atRule.remove();
              }
            }
          }
        ]
      }
    },
    // 预编译
    optimizeDeps: {
      include: [
        'vue',
        'vue-router',
        'pinia',
        'axios',
        '@vueuse/core',
        'echarts',
        'vue-i18n',
        '@vueup/vue-quill',
        'image-conversion',
        'element-plus/es/components/**/css'
      ]
    }
  };
});
