// 识别预填合并规则的 fixture 驱动用例（OCR-MP-001 · accept 第 1 条）。
//
// 用例与期望**全部**从 `doc/verify/fixtures/prefill-cases.json` 读（`cases`），本文件不写任何
// 期望值 —— fixture 谁改谁生效，spec 藏不住「偷偷放宽」的改动（SAMPLE-MP-001 同一套纪律）。
//
// 三组断言：
//   1. fixture 的结构守卫（5 例、必有 B- 病灶例与 E- 内部字段例）——与 accept 里那段 node
//      断言同源；
//   2. 逐例：`mergeOcrPrefill(form, ocr)` 的 `form` / `marks` 与 fixture 的 expect 逐字相等；
//   3. 跨用例的口径守卫：识别结果里出现**表单里没有的键**（E 例的 internalNo / verifyStatus）
//      时，那些键一个都不许出现在结果对象上（「不夹带内部字段」的机器证据，
//      与 accept 1 counterfeit 第 2 条同向）。
import { describe, expect, it } from 'vitest'
import fixture from '@doc/verify/fixtures/prefill-cases.json'
import type { OcrFields } from './prefill'
import { OCR_FAIL_TEXT, PREFILLABLE_KEYS, mergeOcrPrefill, readOcrResponse, recognizedCount } from './prefill'

interface PrefillCase {
  case: string
  form: Record<string, string>
  ocr: Record<string, unknown>
  expectForm: Record<string, string>
  expectMarks: string[]
}

const cases = fixture.cases as PrefillCase[]

describe('mergeOcrPrefill（fixture 驱动）', () => {
  describe('fixture 自身结构（与 accept 的 node 断言同源）', () => {
    it('5 个用例，且含 B- 病灶例与 E- 内部字段例', () => {
      expect(cases.length).toBe(5)
      expect(cases.some(c => c.case.startsWith('B-'))).toBe(true)
      expect(cases.some(c => c.case.startsWith('E-'))).toBe(true)
    })

    it('每例都有 form / ocr / expectForm / expectMarks 四段', () => {
      cases.forEach((c) => {
        expect(c.form, c.case).toBeTypeOf('object')
        expect(c.ocr, c.case).toBeTypeOf('object')
        expect(c.expectForm, c.case).toBeTypeOf('object')
        expect(c.expectMarks, c.case).toBeTypeOf('object')
      })
    })

    it('prefill.ts 的 PREFILLABLE_KEYS 覆盖每例表单的键（fixture 的 form 键集 ⊆ 清单）', () => {
      const keys = new Set<string>(PREFILLABLE_KEYS)
      cases.forEach((c) => {
        Object.keys(c.form).forEach(key => expect(keys.has(key), `${c.case} · ${key}`).toBe(true))
      })
    })
  })

  describe('逐例：合并后的 form 与 marks', () => {
    cases.forEach((c) => {
      it(`${c.case}`, () => {
        const form = { ...c.form }
        const result = mergeOcrPrefill(form, c.ocr as OcrFields)
        expect(result.form).toEqual(c.expectForm)
        expect(result.marks).toEqual(c.expectMarks)
        // 原地改的是同一个对象（页面持的是这个 ref）；同时**没有多出意外的新键**
        expect(Object.keys(result.form).sort()).toEqual(Object.keys(c.expectForm).sort())
      })
    })
  })

  describe('口径守卫（跨用例的硬约束）', () => {
    it('已手填的项绝不被覆盖 —— B 例里手填的姓名与性别原样保留', () => {
      const b = cases.find(c => c.case.startsWith('B-'))!
      const form = { ...b.form }
      mergeOcrPrefill(form, b.ocr as OcrFields)
      expect(form.donorName).toBe('手填姓名')
      expect(form.gender).toBe('female')
      // 反面对照：把空项填上，证明本函数确实在干活（不是「什么都不做」也能过）
      expect(form.age).toBe('48')
    })

    it('识别结果里表单没有的键（internalNo / verifyStatus）一个都不落进表单', () => {
      const e = cases.find(c => c.case.startsWith('E-'))!
      const form = { ...e.form }
      const result = mergeOcrPrefill(form, e.ocr as OcrFields)
      expect(Object.keys(result.form)).not.toContain('internalNo')
      expect(Object.keys(result.form)).not.toContain('verifyStatus')
      expect(result.marks).not.toContain('internalNo')
      expect(result.marks).not.toContain('verifyStatus')
    })

    it('空串 / null / 只有空白字符的识别值都不填也不标', () => {
      const form = { donorName: '', age: '', hospitalNo: '', tissueType: '  ' }
      const result = mergeOcrPrefill(form, {
        donorName: '',
        age: null,
        hospitalNo: 'ZY0000009',
        tissueType: undefined,
      } as OcrFields)
      expect(result.marks).toEqual(['hospitalNo'])
      expect(form.donorName).toBe('')
      expect(form.age).toBe('')
      expect(form.tissueType).toBe('  ')
    })

    it('只有空白字符的表单项算空（能被预填）', () => {
      const form = { donorName: '   ' }
      const result = mergeOcrPrefill(form, { donorName: '测试供体乙' })
      expect(result.marks).toEqual(['donorName'])
      expect(form.donorName).toBe('测试供体乙')
    })

    it('marks 升序；重复调用不重复填也不重复标（已经填上的值不再是空项）', () => {
      const form = { donorName: '', age: '' }
      const first = mergeOcrPrefill(form, { donorName: '测试供体甲', age: '56' })
      expect(first.marks).toEqual(['age', 'donorName'])
      const second = mergeOcrPrefill(form, { donorName: '另一个人', age: '60' })
      expect(second.marks).toEqual([])
      expect(form.donorName).toBe('测试供体甲')
      expect(form.age).toBe('56')
    })

    it('识别结果为空 / null / 非对象时不炸、不动表单', () => {
      const form = { donorName: '' }
      expect(mergeOcrPrefill(form, null).marks).toEqual([])
      expect(mergeOcrPrefill(form, undefined).marks).toEqual([])
      expect(mergeOcrPrefill(form, {}).marks).toEqual([])
      expect(mergeOcrPrefill(null, { donorName: 'x' }).marks).toEqual([])
      expect(form).toEqual({ donorName: '' })
    })

    it('recognizedCount 数的是「真的填了几项」；失败提示语逐字是权威那一句', () => {
      expect(recognizedCount(['age', 'donorName'])).toBe(2)
      expect(recognizedCount([])).toBe(0)
      expect(OCR_FAIL_TEXT).toBe('没识别出来，请手动填写')
      // 失败提示语里不许出现「系统异常」这类吓人的词（`NoneOcrProvider` 的口径同源）
      expect(OCR_FAIL_TEXT).not.toContain('系统')
    })
  })

  describe('readOcrResponse：响应体收口', () => {
    it('正常体：取出 fields 与 rawLines（数字转字符串）', () => {
      const r = readOcrResponse({
        code: 200,
        msg: '操作成功',
        data: { rawLines: ['姓名：测试供体甲', 42], fields: { donorName: '测试供体甲' } },
      })
      expect(r.ocrFields).toEqual({ donorName: '测试供体甲' })
      expect(r.rawLines).toEqual(['姓名：测试供体甲', '42'])
    })

    it('data / fields / rawLines 缺失或类型不对时给空对象空数组，不抛', () => {
      expect(readOcrResponse(null)).toEqual({ ocrFields: {}, rawLines: [] })
      expect(readOcrResponse({ code: 200 })).toEqual({ ocrFields: {}, rawLines: [] })
      expect(readOcrResponse({ code: 200, data: null })).toEqual({ ocrFields: {}, rawLines: [] })
      expect(readOcrResponse({ code: 200, data: { fields: null, rawLines: 'nope' } }))
        .toEqual({ ocrFields: {}, rawLines: [] })
      expect(readOcrResponse('not-json')).toEqual({ ocrFields: {}, rawLines: [] })
    })

    // ★ 回归：`api/ocr.ts` 的 `uploadFile` 直接 resolve **`body.data`**（不是整个响应体），
    //   而单测原先只喂了整个响应体 —— 于是线上每一次识别都落进「没识别出来」的失败态，
    //   单测却全绿（2026-09-22 端侧实测抓到）。两种入参都必须收。
    it('已经拆过一层的 data（`{rawLines, fields}` 自己）也要收', () => {
      const payload = {
        rawLines: ['A 医院', '姓名：测试供体甲'],
        fields: { donorName: '测试供体甲', sourceUnitName: 'A 医院' },
      }
      expect(readOcrResponse(payload)).toEqual({
        ocrFields: { donorName: '测试供体甲', sourceUnitName: 'A 医院' },
        rawLines: ['A 医院', '姓名：测试供体甲'],
      })
      // 端到端那一步：拆过一层的 payload 直接喂合并，六格必须被填上（不是「没识别出来」）
      const form: Record<string, string> = {
        sourceUnitName: '',
        donorName: '',
        gender: '',
        age: '',
        hospitalNo: '',
        tissueType: '',
      }
      const r = readOcrResponse(payload)
      const merged = mergeOcrPrefill(form, r.ocrFields)
      expect(merged.marks).toEqual(['donorName', 'sourceUnitName'])
      expect(form.donorName).toBe('测试供体甲')
    })

    it('收口后的 fields 直接喂给 mergeOcrPrefill：认不出的键照样被忽略', () => {
      const form = { donorName: '', internalNo: '' }
      const r = readOcrResponse({
        code: 200,
        data: { rawLines: [], fields: { donorName: '测试供体丙', verifyStatus: 'valid', internalNo: 'hli99' } },
      })
      const result = mergeOcrPrefill(form, r.ocrFields)
      // internalNo 在 form 里 —— 它是**表单字段**，所以会被填（这正是「键不在表单里才忽略」的口径）
      expect(result.marks).toEqual(['donorName', 'internalNo'])
      expect(form.donorName).toBe('测试供体丙')
      // verifyStatus 不在 form 里 → 忽略
      expect(Object.keys(form)).not.toContain('verifyStatus')
    })
  })
})
