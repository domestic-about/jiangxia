// 自定义国际化配置
import { createI18n } from 'vue-i18n';

import { LanguageEnum } from '@/enums/LanguageEnum';
import zh_CN from '@/lang/zh_CN';
import en_US from '@/lang/en_US';

/**
 * 获取当前语言
 * @returns zh-cn|en ...
 */
export const getLanguage = (): LanguageEnum => {
  const language = useStorage<LanguageEnum>('language', LanguageEnum.zh_CN);
  if (language.value) {
    return language.value;
  }
  return LanguageEnum.zh_CN;
};

// ── 业务域 i18n（SYS-WEB-001 立的约定）────────────────────────────────────────
// 每个域在自己的文件里写 src/lang/lqg/<域>.zh_CN.ts / <域>.en_US.ts，default 导出对象；
// 这里按文件名自动合并到 `lqg.<域>.*` 命名空间。新增一个域 = 加两个文件，不用改本文件。
//
// 为什么：src/lang/zh_CN.ts 与 en_US.ts 是**全部域共享**的大文件，并行开发的 ticket 往里加 key
// 必撞车（同一个文件同一段）。业务 key 一律走 lqg.<域>.*。
// 域标签与后端模块 ruoyi-lqg 的子包一致（sys / auth / sample / embed / cryo / qc / doc / ocr / ext）。
type LqgDomainMessages = Record<string, Record<string, unknown>>;

const mergeLqgMessages = (modules: Record<string, unknown>, suffix: string): LqgDomainMessages => {
  const merged: LqgDomainMessages = {};
  for (const [path, mod] of Object.entries(modules)) {
    const domain = path.replace('./lqg/', '').replace(suffix, '');
    merged[domain] = ((mod as { default?: Record<string, unknown> }).default ?? {}) as Record<string, unknown>;
  }
  return merged;
};

const lqgZhCN = mergeLqgMessages(import.meta.glob('./lqg/*.zh_CN.ts', { eager: true }), '.zh_CN.ts');
const lqgEnUS = mergeLqgMessages(import.meta.glob('./lqg/*.en_US.ts', { eager: true }), '.en_US.ts');

const i18n = createI18n({
  globalInjection: true,
  allowComposition: true,
  legacy: false,
  locale: getLanguage(),
  messages: {
    zh_CN: { ...zh_CN, lqg: lqgZhCN },
    en_US: { ...en_US, lqg: lqgEnUS }
  }
});

export default i18n;

export type LanguageType = typeof zh_CN;
