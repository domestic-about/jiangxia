// 线性图标注册表（Kevin 2026-09-24 本机验收：「我的」与首页的单字方块换成一套线性图标）。
//
// 钉住三件事：四张表都配了图标、每个图标都能生成可用的遮罩地址、不认识的名字什么都不画。
import { describe, expect, it } from 'vitest'
import { ENTRY_PRESENTATION } from '@/components/biz/entry-presentation'
import { ENTRY_KEYS } from '@/pages/index/entries'
import { LINE_ICON_NAMES, lineIconUri } from './line-icons'

describe('lineIconUri', () => {
  it('每个图标都是整串编码过的 SVG 遮罩地址（没有原样的 # < > 双引号，小程序行内样式里不会断）', () => {
    expect(LINE_ICON_NAMES.length).toBeGreaterThanOrEqual(12)
    for (const name of LINE_ICON_NAMES) {
      const uri = lineIconUri(name)
      expect(uri.startsWith('data:image/svg+xml,')).toBe(true)
      expect(uri).not.toMatch(/[#<>"]/)
      const svg = decodeURIComponent(uri.slice('data:image/svg+xml,'.length))
      expect(svg).toContain('viewBox=\'0 0 24 24\'')
      expect(svg).toContain('stroke-width=\'1.8\'')
      // 形状里不许写死颜色值（颜色只来自外层文字色）
      expect(svg).not.toMatch(/#[0-9a-f]{3,8}\b|rgb\(/i)
    }
  })

  it('同一个名字取两次是同一串（缓存，不重复编码）', () => {
    expect(lineIconUri('history')).toBe(lineIconUri('history'))
  })

  it('不认识的名字、原型上的名字 → 空串（组件据此不渲染，不画实心方块）', () => {
    expect(lineIconUri('')).toBe('')
    expect(lineIconUri('nope')).toBe('')
    expect(lineIconUri('toString')).toBe('')
    expect(lineIconUri('constructor')).toBe('')
  })
})

describe('四张表的图标', () => {
  it('首页宫格与「我的 · 内部管理」用的四个图标都已注册、互不相同', () => {
    const icons = ENTRY_KEYS.map(key => ENTRY_PRESENTATION[key].icon)
    icons.forEach(icon => expect(lineIconUri(icon)).not.toBe(''))
    expect(new Set(icons).size).toBe(ENTRY_KEYS.length)
  })

  it('「我的」页各行与页头用到的图标都已注册', () => {
    for (const name of ['history', 'unit', 'agreement', 'privacy', 'logout', 'phone', 'chevron', 'clock', 'organoid']) {
      expect(lineIconUri(name)).not.toBe('')
    }
  })
})
