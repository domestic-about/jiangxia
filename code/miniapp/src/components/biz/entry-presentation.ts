import type { EntryKey } from '@/pages/index/entries'

// 首页宫格与「我的 · 内部管理」板块的展示细节（图标字、一句话说明）。
// 口径来自 doc/design-options/gallery.html 的 `#mp-home-final` 帧（**只取内容与排布**，
// 外观按 direction-a 落地规范）。业务判定仍在 src/pages/index/entries.ts。
export interface EntryPresentation {
  /** 图标井里的一个字 */
  mark: string
  title: string
  desc: string
}

export const ENTRY_PRESENTATION: Record<EntryKey, EntryPresentation> = {
  sample: { mark: '样', title: '样本记录信息表', desc: '组织样本送检与收样' },
  organoid: { mark: '类', title: '类器官收样记录', desc: '收到的类器官' },
  embed: { mark: '蜡', title: '石蜡包埋送样记录', desc: '包埋、切片、染色' },
  cryo: { mark: '冻', title: '-80 冻存记录', desc: '冻存批次' },
}

export function presentationOf(key: EntryKey): EntryPresentation {
  return ENTRY_PRESENTATION[key]
}
