// 填写页布局的 fixture 驱动用例（SAMPLE-MP-001 · accept 第 1 条）。
//
// 用例与期望**全部**从 `doc/verify/fixtures/sample-form-cases.json` 读（cases / sendFields /
// receiveFields），本文件不写任何期望值 —— fixture 谁改谁生效，spec 藏不住「偷偷放宽」的改动。
//
// 三组断言：
//   1. fixture 的结构守卫（13 例、收样段 7 个字段、内部只读 5 例、view 两例且都不可改、
//      至少一例 mode 不在 new/edit/view 里）——与 accept 里那段 node 断言同源；
//   2. 每个 case：`formLayout(identity, verifyStatus, mine, mode)` 的 fields / editable / showOcr
//      与 fixture 的 expect 逐字相等；
//   3. 字段词表守卫：所有 case 渲染出来的字段都必须是 sendFields ∪ receiveFields 里的，
//      且**外部任何一例都不含收样段字段**（「不渲染」这条口径的机器证据）。
import { describe, expect, it } from 'vitest'
import fixture from '@doc/verify/fixtures/sample-form-cases.json'
import type { FormFieldKey } from './layout'
import { RECEIVE_FIELDS, SEND_FIELDS, formLayout, normalizeMode, resolveEditable } from './layout'

interface FormCase {
  case: string
  identity: unknown
  verifyStatus: string | null
  mine: boolean
  mode: unknown
  expect: {
    fields: string[]
    editable: boolean
    showOcr: boolean
  }
}

const cases = fixture.cases as FormCase[]
const sendFields = fixture.sendFields as string[]
const receiveFields = fixture.receiveFields as string[]

describe('formLayout（fixture 驱动）', () => {
  describe('fixture 自身结构（与 accept 的 node 断言同源）', () => {
    it('13 个用例、收样段 7 个字段', () => {
      expect(cases.length).toBe(13)
      expect(receiveFields.length).toBe(7)
    })

    it('内部只读 5 例；view 两例且都不可改；至少一例 mode 不认识', () => {
      expect(cases.filter(c => c.identity === 'internal' && !c.expect.editable).length).toBe(5)
      expect(cases.filter(c => c.mode === 'view').length).toBe(2)
      expect(cases.filter(c => c.mode === 'view' && c.expect.editable).length).toBe(0)
      expect(cases.some(c => !['new', 'edit', 'view'].includes(c.mode as string))).toBe(true)
    })

    it('本文件的字段词表与 fixture 的 sendFields / receiveFields 一致', () => {
      expect([...SEND_FIELDS]).toEqual(sendFields)
      expect([...RECEIVE_FIELDS]).toEqual(receiveFields)
    })
  })

  describe('逐例：fields / editable / showOcr', () => {
    cases.forEach((c) => {
      it(`${c.case}（identity=${String(c.identity)} mode=${String(c.mode)} status=${String(c.verifyStatus)} mine=${c.mine}）`, () => {
        const layout = formLayout(c.identity, c.verifyStatus, c.mine, c.mode)
        expect(layout.fields).toEqual(c.expect.fields)
        expect(layout.editable).toBe(c.expect.editable)
        expect(layout.showOcr).toBe(c.expect.showOcr)
      })
    })
  })

  describe('口径守卫（跨用例的硬约束）', () => {
    it('外部任何一例都不渲染收样段字段（不是置灰，是不渲染）', () => {
      const known = new Set<string>([...SEND_FIELDS, ...RECEIVE_FIELDS])
      cases
        .filter(c => c.identity === 'external')
        .forEach((c) => {
          const layout = formLayout(c.identity, c.verifyStatus, c.mine, c.mode)
          receiveFields.forEach((key) => {
            expect(layout.fields).not.toContain(key)
          })
          layout.fields.forEach((key: FormFieldKey) => {
            expect(known.has(key)).toBe(true)
          })
        })
    })

    it('mode 缺失或不认识按只读（绝不按可改）', () => {
      expect(normalizeMode('')).toBe('view')
      expect(normalizeMode(undefined)).toBe('view')
      expect(normalizeMode('EDIT')).toBe('view')
      expect(normalizeMode('new')).toBe('new')
      // 内部 + 有效 + 本人：把 mode 换成不认识的值，就必须从「可改」掉成「只读」
      expect(formLayout('internal', 'valid', true, 'whatever').editable).toBe(false)
      expect(formLayout('internal', 'valid', true, 'edit').editable).toBe(true)
    })

    it('身份缺失时一个字段都不渲染', () => {
      const layout = formLayout('', null, true, 'new')
      expect(layout.fields).toEqual([])
      expect(layout.editable).toBe(false)
      expect(layout.showOcr).toBe(false)
    })

    it('editable 以后端详情为准：详情说不可改就不可改（同组别人的样本）', () => {
      const layout = formLayout('external', 'pending', true, 'edit')
      expect(layout.editable).toBe(true)
      expect(resolveEditable(layout, false)).toBe(false)
      expect(resolveEditable(layout, undefined)).toBe(true)
    })

    it('外部任何字段清单都不含收样段 / 核验人 / 冻存类字段（detail-ext.vue 的禁字 grep 与它同向）', () => {
      // 与 accept 第 1 条那两行 grep 同源的机器断言：`detail-ext.vue` 里连这些**字段名**都不许出现
      // （渲染了再 v-show=false 也会被 grep 抓出来，因为字段名进了外部的包）。
      const forbidden = [
        ...receiveFields,
        'verifyBy',
        'verifyByName',
        'frozenBy',
        'cryoLocation',
        'cryoName',
      ]
      cases
        .filter(c => c.identity === 'external')
        .forEach((c) => {
          const layout = formLayout(c.identity, c.verifyStatus, c.mine, c.mode)
          layout.fields.forEach((key) => {
            expect(forbidden).not.toContain(key)
          })
        })
      // 正面对照：内部那一组**确实**带着收样段（否则上一条在空集合上也成立）
      const internal = formLayout('internal', 'valid', false, 'edit')
      expect(internal.fields).toEqual(expect.arrayContaining([...receiveFields]))
    })

    it('识别条只在新增那一次出现', () => {
      cases.forEach((c) => {
        const layout = formLayout(c.identity, c.verifyStatus, c.mine, c.mode)
        if (layout.showOcr) {
          expect(normalizeMode(c.mode)).toBe('new')
        }
      })
    })
  })
})
