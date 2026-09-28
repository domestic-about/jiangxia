// 核验页表单规则的 fixture 驱动用例（甲方 2026-09-24 第 20 行：小程序里也能核验）。
//
// 用例与期望**全部**从 `doc/verify/fixtures/verify-form-cases.json` 读，本文件不写期望值：
//   sampleFields → 两类各有哪些送检 / 收样字段、两个动作下哪些必填；
//   sampleCases  → 详情 + 用户改动 → 自检结论与请求体（送检段整段替换：没改不带、改了带整段）；
//   embedCases   → 详情 + 用户改动 → 自检结论、请求体（fill 补丁：只带改过的键）、判无效时不会保存的项。
import { describe, expect, it } from 'vitest'
import fixture from '@doc/verify/fixtures/verify-form-cases.json'
import type { EmbedFormValue } from '@/api/embed'
import { toEmbedFormValue } from '@/api/embed'
import type { SampleFieldKey, SampleKind, VerifyAction, VerifySampleForm } from './rules'
import {
  RECEIVE_FIELDS,
  SUBMIT_FIELDS,
  embedDroppedOnInvalid,
  embedProblem,
  embedVerifyBody,
  kindOf,
  sampleProblem,
  sampleRequired,
  sampleVerifyBody,
  toVerifySampleForm,
} from './rules'

interface FieldSpec {
  submit: string[]
  receive: string[]
  requiredValid: string[]
  requiredInvalid: string[]
}

interface SampleCase {
  name: string
  detail: Record<string, unknown>
  me: string
  edits: Partial<VerifySampleForm>
  action: VerifyAction
  reason?: string
  reasonRepeat?: { char: string, times: number }
  expectProblem: string
  expectBody?: Record<string, unknown>
}

interface EmbedCase {
  name: string
  detail: Record<string, unknown>
  sampleValid: boolean
  edits: Partial<EmbedFormValue>
  action: VerifyAction
  reason: string
  expectProblem: string
  expectBody?: Record<string, unknown>
  expectDropped?: string[]
}

const sampleFields = fixture.sampleFields as Record<SampleKind, FieldSpec>
const sampleCases = fixture.sampleCases as unknown as SampleCase[]
const embedCases = fixture.embedCases as unknown as EmbedCase[]

function reasonOf(c: SampleCase): string {
  return c.reasonRepeat ? c.reasonRepeat.char.repeat(c.reasonRepeat.times) : (c.reason ?? '')
}

describe('核验页表单规则（fixture 驱动）', () => {
  describe('样本两类：字段与必填', () => {
    (['tissue', 'organoid'] as SampleKind[]).forEach((kind) => {
      it(`${kind}：送检 / 收样字段与顺序`, () => {
        expect(SUBMIT_FIELDS[kind]).toEqual(sampleFields[kind].submit)
        expect(RECEIVE_FIELDS[kind]).toEqual(sampleFields[kind].receive)
      })

      it(`${kind}：判有效 / 判无效各自的必填`, () => {
        const all: SampleFieldKey[] = [...SUBMIT_FIELDS[kind], ...RECEIVE_FIELDS[kind]]
        expect(all.filter(key => sampleRequired(kind, key, 'valid'))).toEqual(sampleFields[kind].requiredValid)
        expect(all.filter(key => sampleRequired(kind, key, 'invalid'))).toEqual(sampleFields[kind].requiredInvalid)
      })
    })
  })

  describe('样本两类：自检与请求体', () => {
    it('fixture 的 sampleCases 一组不落', () => {
      expect(sampleCases.length).toBeGreaterThan(0)
    })

    sampleCases.forEach((c) => {
      it(c.name, () => {
        const kind = kindOf(c.detail)
        const before = toVerifySampleForm(c.detail, c.me)
        const now: VerifySampleForm = { ...before, ...c.edits }
        const reason = reasonOf(c)
        expect(sampleProblem(kind, c.action, now, reason)).toBe(c.expectProblem)
        if (c.expectBody) {
          expect(sampleVerifyBody(kind, c.action, before, now, reason, 'passage' in c.detail)).toEqual(c.expectBody)
        }
      })
    })
  })

  describe('石蜡包埋送样：自检、请求体、判无效不会保存的项', () => {
    it('fixture 的 embedCases 一组不落', () => {
      expect(embedCases.length).toBeGreaterThan(0)
    })

    embedCases.forEach((c) => {
      it(c.name, () => {
        const before = toEmbedFormValue(c.detail)
        const now: EmbedFormValue = { ...before, ...c.edits }
        expect(embedProblem(c.action, now, c.sampleValid, c.reason)).toBe(c.expectProblem)
        if (c.expectBody) {
          expect(embedVerifyBody(c.action, before, now, c.reason)).toEqual(c.expectBody)
        }
        if (c.expectDropped) {
          expect(embedDroppedOnInvalid(before, now)).toEqual(c.expectDropped)
        }
      })
    })
  })
})
