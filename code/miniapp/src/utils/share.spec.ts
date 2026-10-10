// 「转发给朋友」的卡片内容（CR-20261010-19）。
//
// 钉的是三件做反了会泄露数据的事：封面固定（不给就是当前页截图）、落地首页不带参数、标题固定。
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { LEGAL_OPERATOR } from '@/config/legal'
import { HOME_PAGE } from '@/router/config'
import { SHARE_COVER, SHARE_TITLE, sharePayload } from './share'

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)))

describe('sharePayload', () => {
  it('★ 恒带固定封面（不给 imageUrl 微信会拿当前页截图当封面，表单上的供体姓名、住院号就发出去了）', () => {
    const p = sharePayload()
    expect(p.imageUrl).toBe(SHARE_COVER)
    expect(p.imageUrl).toBe('/static/share/cover.png')
  })

  it('落地页恒为首页，不带任何参数（不做某条记录的深链）', () => {
    const p = sharePayload()
    expect(p.path).toBe(HOME_PAGE)
    expect(p.path).toBe('/pages/index/index')
    expect(p.path).not.toMatch(/[?=&]/)
  })

  it('标题是固定文案：运营方 + 小程序名', () => {
    expect(sharePayload().title).toBe(`${LEGAL_OPERATOR} · 类器官送检`)
    expect(SHARE_TITLE).toBe('湖北江夏实验室类器官研究中心 · 类器官送检')
  })

  it('每次给的都是同一份内容（与当前页、登录身份无关）', () => {
    expect(sharePayload()).toEqual(sharePayload())
    expect(Object.keys(sharePayload()).sort()).toEqual(['imageUrl', 'path', 'title'])
  })
})

describe('封面图文件', () => {
  it('在 static 里、是 PNG、5:4、不超过 100KB（主包 2MB 上限，封面别占太多）', () => {
    const png = read(`..${SHARE_COVER}`)
    // PNG 文件头
    expect(png.subarray(0, 8).toString('hex')).toBe('89504e470d0a1a0a')
    // IHDR：宽、高各 4 字节（大端），紧跟在 16 字节之后
    const width = png.readUInt32BE(16)
    const height = png.readUInt32BE(20)
    expect(width / height).toBeCloseTo(5 / 4, 2)
    expect(width).toBeGreaterThanOrEqual(500)
    expect(png.length).toBeLessThan(100 * 1024)
  })
})

describe('全站接线', () => {
  const main = read('../main.ts').toString('utf8')

  it('main.ts 用全局混入注册 onShareAppMessage，返回 sharePayload()', () => {
    expect(main).toMatch(/app\.mixin\(\{\s*onShareAppMessage\(\)\s*\{\s*return sharePayload\(\)\s*\},?\s*\}\)/)
  })

  it('不开「分享到朋友圈」', () => {
    expect(main).not.toMatch(/onShareTimeline/)
  })
})
