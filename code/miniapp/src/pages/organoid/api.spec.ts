// 内部提交体「类目身份不可越类改」的契约测试（issue #105 · D2 r3 返工）。
//
// 病灶：类器官表单的**修改模式**把 `sampleKind:'organoid'` 当可改字段发出去
//（`updateIntSample({id, sampleKind:'organoid', …})`），而后端 PUT 对 valid 行
//「谁录的都能改」—— 一条**组织样本**点错行进来保存后会被静默改判成类器官
//（两页签 / 两套必填集跟着错）。
//
// 修法两头都收：
//   前端 —— 修改模式用 `internalOrganoidPatch`（**不带** sampleKind）；`internalOrganoidPayload`
//          只给新增用。
//   后端 —— `PUT /mp/int/sample` 的 sampleKind 与库里不一致 → 400
//          （契约第 49 行；`MpSampleContractTest.putRejectsASampleKindThatDiffersFromTheStoredRow`）。
//
// `sample/form.vue` 里 `payload()` 是页面内函数、不进单测模块图，故用读源码的形态断言
// （与 accept 里那些「禁字 grep」同一个套路：钉的是「修改模式不发类目身份」这条口径）。
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { emptyOrganoidForm, externalOrganoidPayload, internalOrganoidPatch, internalOrganoidPayload } from './api'
import { PASSAGE_HINT, normalizePassage, passageProblem } from './layout'

function filled() {
  const f = emptyOrganoidForm()
  f.sourceUnitId = 9000009002
  f.sourceUnitName = 'B 大学'
  f.organoidType = '结直肠类器官'
  f.passage = 'p3'
  f.receiveDate = '2026-09-17'
  f.internalNo = 'T-oco01'
  f.processTime = '2026-09-18 10:00:00'
  f.hasViabilityReport = 'Y'
  f.operatorName = '李工'
  f.remark = '备注'
  return f
}

describe('类器官内部提交体', () => {
  it('新增（POST）带 sampleKind=organoid —— 类目由入口定下', () => {
    const body = internalOrganoidPayload(filled())
    expect(body.sampleKind).toBe('organoid')
    // 别的字段一个都不能少（合并进 patch 之后是 10 个键：原来 9 个 + 代数）
    expect(Object.keys(body).sort()).toEqual([
      'hasViabilityReport', 'internalNo', 'operatorName', 'organoidType', 'passage',
      'processTime', 'receiveDate', 'sampleKind', 'sourceUnitId', 'sourceUnitName',
    ])
  })

  it('★ 修改（PUT）不带 sampleKind —— 不许把类目身份当可改字段发出去', () => {
    const body = internalOrganoidPatch(filled())
    expect(body).not.toHaveProperty('sampleKind')
    expect(Object.keys(body).sort()).toEqual([
      'hasViabilityReport', 'internalNo', 'operatorName', 'organoidType', 'passage',
      'processTime', 'receiveDate', 'sourceUnitId', 'sourceUnitName',
    ])
    // 数值 / 空值口径不变：选了单位 id 就带 id，处理时间 T → 空格
    expect(body.sourceUnitId).toBe(9000009002)
    expect(body.processTime).toBe('2026-09-18 10:00:00')
  })

  it('修改模式的调用点用的是 patch（不是 payload）', () => {
    const src = readFileSync(fileURLToPath(new URL('./form.vue', import.meta.url)), 'utf8')
    expect(src).toMatch(/updateIntSample\(\{ id: organoidId\.value, \.\.\.internalOrganoidPatch\(form\.value\) \}\)/)
    expect(src).not.toMatch(/updateIntSample\(\{ id: organoidId\.value, \.\.\.internalOrganoidPayload/)
  })

  it('★ 样本记录信息表的修改模式同样不发 sampleKind（只在 mode=new 时带）', () => {
    const src = readFileSync(fileURLToPath(new URL('../sample/form.vue', import.meta.url)), 'utf8')
    // 老的写法：无条件塞进 body 字面量
    expect(src).not.toMatch(/const body: Record<string, unknown> = \{\s*sampleKind: 'tissue',/)
    // 新写法：只有 mode==='new' 才补
    expect(src).toMatch(/if \(mode\.value === 'new'\) \{\s*body\.sampleKind = 'tissue'\s*\}/)
  })
})

// 代数（CR-20260924-10：甲方 2026-09-24 第 18 行，内外部类器官收样记录都加这一项）
describe('类器官收样记录的代数', () => {
  it('内外部提交体都带代数，小写 p 转大写、去空白', () => {
    const f = filled()
    f.passage = ' p12 '
    expect(internalOrganoidPatch(f).passage).toBe('P12')
    expect(externalOrganoidPayload(f).passage).toBe('P12')
    // 外部四项：来源单位（id + 名称）、类器官类型、代数、备注 —— 收样段一个都不带
    expect(Object.keys(externalOrganoidPayload(f)).sort()).toEqual([
      'organoidType', 'passage', 'remark', 'sourceUnitId', 'sourceUnitName',
    ])
  })

  it('清空代数也要发这个键（空串）：补丁语义里「没带 = 不改」，不发就清不掉', () => {
    const f = filled()
    f.passage = '   '
    expect(internalOrganoidPatch(f)).toHaveProperty('passage', '')
  })

  it('格式：选填；填了必须形如 P3（与冻存批次的代数同一规则），提示是人话', () => {
    for (const ok of ['', '  ', 'P3', 'p3', ' P12 ', 'P999']) {
      expect(passageProblem(ok), ok).toBe('')
    }
    for (const bad of ['3', '第3代', 'P1234', 'P', 'P3a', 'p 3']) {
      expect(passageProblem(bad), bad).toBe(PASSAGE_HINT)
    }
    expect(PASSAGE_HINT).toBe('代数请填 P 加数字，如 P3')
    expect(normalizePassage(null)).toBe('')
  })
})
