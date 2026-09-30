import { describe, expect, it } from 'vitest'
import { dateOrNull, progressText, QC_TABS, SCORE_ROWS, scoreSummary, statusTag, statusText, toScore } from './status'

describe('小程序编辑质控文档 · 纯函数', () => {
  it('三个页签顺序 = 模板顺序，路径段与 docKind 一一对应', () => {
    expect(QC_TABS.map(t => t.type)).toEqual(['sample-qc', 'organoid-qc', 'score'])
    expect(QC_TABS.map(t => t.kind)).toEqual(['sample_qc', 'organoid_qc', 'organoid_score'])
  })

  it('状态：null 是未填写，draft 草稿，published 已完成', () => {
    expect(statusText(null)).toBe('未填写')
    expect(statusText('draft')).toBe('草稿')
    expect(statusText('published')).toBe('已完成')
    expect(statusTag(undefined)).toBe('none')
    expect(statusTag('published')).toBe('published')
  })

  it('进度：填写中写成「n / 3 已完成」', () => {
    expect(progressText('doing', 1)).toBe('1 / 3 已完成')
    expect(progressText('done', 3)).toBe('已全部完成')
    expect(progressText('none', 0)).toBe('未开始')
  })

  it('评分：字典 remark 是字符串也按数字算；任一项没分合计就是 null', () => {
    expect(toScore('8')).toBe(8)
    expect(toScore('')).toBeNull()
    expect(toScore('abc')).toBeNull()
    expect(scoreSummary([8, 5, 3, 2])).toBe(18)
    expect(scoreSummary([8, null, 3, 2])).toBeNull()
    expect(SCORE_ROWS.map(r => r.dictType)).toEqual(['lqg_score_pre_culture', 'lqg_score_culture_days', 'lqg_score_count', 'lqg_score_diameter'])
  })

  it('时间字段：只有 yyyy-MM-dd 才喂给日期面板，手写文字不喂', () => {
    expect(dateOrNull('2026-09-30')).toBe('2026-09-30')
    expect(dateOrNull('约第 5 天')).toBeNull()
    expect(dateOrNull('')).toBeNull()
  })
})
