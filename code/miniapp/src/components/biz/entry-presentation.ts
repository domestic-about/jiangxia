import type { EntryKey } from '@/pages/index/entries'
import type { LineIconName } from '@/components/ui/line-icons'

// 首页宫格与「我的 · 内部管理」板块的展示细节（图标、一句话说明）。
// 口径来自 doc/design-options/gallery.html 的 `#mp-home-final` 帧（**只取内容与排布**，
// 外观按 direction-a 落地规范）。业务判定仍在 src/pages/index/entries.ts。
// ★ 图标是 `line-icons.ts` 那一套线性图标（Kevin 2026-09-24 本机验收：单字方块换成统一的线性图标）；
//   首页宫格与「我的 · 内部管理」同一张表用同一个图标，只在这里配一次。
export interface EntryPresentation {
  /** 图标井里的线性图标 */
  icon: LineIconName
  title: string
  desc: string
}

export const ENTRY_PRESENTATION: Record<EntryKey, EntryPresentation> = {
  sample: { icon: 'sample', title: '样本记录信息表', desc: '组织样本送检与收样' },
  organoid: { icon: 'organoid', title: '类器官送样记录', desc: '送检的类器官' },
  embed: { icon: 'embed', title: '石蜡包埋送样记录', desc: '包埋、切片、染色' },
  cryo: { icon: 'cryo', title: '-80 冻存记录', desc: '冻存批次' },
}

export function presentationOf(key: EntryKey): EntryPresentation {
  return ENTRY_PRESENTATION[key]
}
