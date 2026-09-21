/// <reference types="vite/client" />
/// <reference types="miniprogram-api-typings" />
/// <reference types="@uni-helper/vite-plugin-uni-pages" />

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_APP_PORT: string
  readonly VITE_UNI_APPID: string
  readonly VITE_WX_APPID: string
  readonly VITE_APP_PUBLIC_BASE: string
  readonly VITE_SERVER_BASEURL: string
  readonly VITE_APP_PROXY_ENABLE: string
  readonly VITE_APP_PROXY_PREFIX: string
  readonly VITE_APP_CLIENT_ID: string
  readonly VITE_APP_TENANT_ID: string
  readonly VITE_DELETE_CONSOLE: string
  /** 调试登录入口开关：只有 .env.development 里有（ADR-0008） */
  readonly VITE_MOCK_LOGIN?: string
  readonly DEV: boolean
  readonly PROD: boolean
  readonly MODE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare const __VITE_APP_PROXY__: string
