// 石蜡包埋填写页布局的 fixture 驱动用例（EMBED-MP-001 · accept 第 3 条）。
//
// 用例与期望**全部**从 `doc/verify/fixtures/embed-form-cases.json` 读
// （`cases` / `externalFields` / `internalFields`），本文件不写任何期望值 ——
// fixture 谁改谁生效，spec 藏不住「偷偷放宽」的改动（fixture 一个字节没改）。
//
// 五组断言：
//   1. 结构守卫：9 例、外部 3 项、内部 16 项、`editable=false` 恰 5 例
//      （与 accept 里那段 node 断言同源）；
//   2. 每个 case：`embedLayout(identity, verifyStatus, mine, mode)` 的 fields / editable /
//      showCard 与 fixture 的 expect 逐字相等；
//   3. 词表守卫：所有 case 渲染出来的字段都必须是 externalFields ∪ internalFields 里的；
//   4. 三条病灶守卫：**外部任何一例都不含内部字段**（「不渲染」这条口径的机器证据）、
//      **`mode=view` 的每一例都不可改**、**只有「外部 + 有效」才出包埋卡片**；
//   5. `showEditEntry`（CR-20260918-07 新增返回键，fixture 里没有）：只可能是
//      `mode=view && 内部 && verifyStatus='valid'`。
import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import fixture from '@doc/verify/fixtures/embed-form-cases.json'
import type { EmbedFieldKey } from './layout'
import { INTERNAL_FIELDS, embedLayout } from './layout'

interface EmbedCase {
  case: string
  identity: unknown
  verifyStatus: string | null
  mine: boolean
  mode: unknown
  expect: {
    fields: string[]
    editable: boolean
    showCard: boolean
  }
}

const cases = fixture.cases as EmbedCase[]
const externalFields = fixture.externalFields as string[]
const internalFields = fixture.internalFields as string[]

describe('embedLayout（fixture 驱动）', () => {
  it('fixture 结构：9 例 / 外部 3 项 / 内部 16 项 / 不可改恰 5 例', () => {
    expect(cases.length).toBe(9)
    expect(externalFields.length).toBe(3)
    expect(internalFields.length).toBe(16)
    expect(cases.filter(c => !c.expect.editable).length).toBe(5)
  })

  cases.forEach((c) => {
    it(`${c.case}：fields / editable / showCard 逐字等于 fixture`, () => {
      const layout = embedLayout(c.identity, c.verifyStatus, c.mine, c.mode)
      expect(layout.fields).toEqual(c.expect.fields)
      expect(layout.editable).toBe(c.expect.editable)
      expect(layout.showCard).toBe(c.expect.showCard)
    })
  })

  it('词表守卫：每一例渲染出来的字段都在 external ∪ internal 里', () => {
    const vocabulary = [...internalFields, ...externalFields]
    cases.forEach((c) => {
      embedLayout(c.identity, c.verifyStatus, c.mine, c.mode).fields.forEach((key) => {
        expect(vocabulary, `${c.case} 的 ${key}`).toContain(key as EmbedFieldKey)
      })
    })
  })

  it('外部任何一例都不含内部字段（不是置灰、是根本不进 fields）', () => {
    cases.filter(c => c.identity === 'external').forEach((c) => {
      const fields = embedLayout(c.identity, c.verifyStatus, c.mine, c.mode).fields
      expect(fields).toEqual(externalFields)
      internalFields
        .filter(key => !externalFields.includes(key))
        .forEach((key) => {
          expect(fields, `${c.case} 不该有 ${key}`).not.toContain(key)
        })
    })
  })

  it('mode=view 的每一例都不可改（「修改」是另一个 mode，另算一次）', () => {
    cases.filter(c => c.mode === 'view').forEach((c) => {
      expect(embedLayout(c.identity, c.verifyStatus, c.mine, c.mode).editable, c.case).toBe(false)
    })
  })

  it('包埋卡片只在「外部 + 已核验有效」时出现（内部看全字段，不需要卡片）', () => {
    cases.forEach((c) => {
      const layout = embedLayout(c.identity, c.verifyStatus, c.mine, c.mode)
      if (layout.showCard) {
        expect(c.identity, c.case).toBe('external')
        expect(c.verifyStatus, c.case).toBe('valid')
      }
    })
  })

  it('showEditEntry：只有「只读详情 + 内部 + 这条记录内部可改」才为真（CR-20260918-07）', () => {
    cases.forEach((c) => {
      const layout = embedLayout(c.identity, c.verifyStatus, c.mine, c.mode)
      const expected = c.mode === 'view' && c.identity === 'internal' && c.verifyStatus === 'valid'
      expect(layout.showEditEntry, `${c.case} 的 showEditEntry`).toBe(expected)
    })
    // 反证：外部（哪怕有效）与「内部看外部待核验」都不出「修改」入口
    expect(embedLayout('external', 'valid', true, 'view').showEditEntry).toBe(false)
    expect(embedLayout('internal', 'pending', false, 'edit').showEditEntry).toBe(false)
  })

  it('身份缺失 / 不认识：一个字段都不渲染（不默认当内部）', () => {
    expect(embedLayout('', null, true, 'new').fields).toEqual([])
    expect(embedLayout(undefined, 'valid', true, 'edit').fields).toEqual([])
    expect(embedLayout('nobody', null, true, 'new').showEditEntry).toBe(false)
    // 内部 16 项的词表与 fixture 同源（改一处必须改 fixture —— 那是需求层）
    expect([...INTERNAL_FIELDS]).toEqual(internalFields)
  })

  /**
   * ★ 外部选样本组件**不许出现内部编号**（REQ-AUTH-013：不想让外部知道内部编号）。
   *
   * accept 3 也 grep 一次（打的是 `internalNo` 这个字面量），但那个 grep 打的是一个路径；
   * 组件文件在 `src/components/lqg/`（页面目录下的 `.vue` 会被 uni-pages 当成**页面**注册成
   * 路由 —— 见完工报告 §7 坑 5），所以这里**再钉一道**：按真实路径读文件、断文件里没有那个词。
   * 两道断言同源：改坏任一处都会红。
   */
  it('外部选样本组件里没有「内部编号」那个字段名（连注释也不许有）', () => {
    const file = resolve(dirname(fileURLToPath(import.meta.url)), '../../components/lqg/SamplePickerExt.vue')
    const source = readFileSync(file, 'utf8')
    expect(source).not.toMatch(/internalNo/)
    // 反向对照：它确实读了「本人送检过」的样本列表（不然就是空壳，断言再绿也没意义）
    expect(source).toMatch(/fetchExtMySamples/)
    expect(source).toMatch(/donorNameMasked/)
  })
})
