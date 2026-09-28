// 首页入口与「我的」区块的 fixture 驱动用例（SYS-MP-001 · accept 第 2 条）。
//
// 用例与期望**全部**从 `doc/verify/fixtures/home-entries-cases.json` 读
// （cases / targetCases / meCases 三组），本文件不写任何期望值——
// 这样 fixture 谁改谁生效，spec 藏不住「偷偷放宽」的改动。
import { describe, expect, it } from 'vitest'
import fixture from '@doc/verify/fixtures/home-entries-cases.json'
import { entriesFor, entryTarget, meSections } from './entries'

interface EntriesCase {
  identity: unknown
  expect: string[]
}

interface TargetCase {
  identity: unknown
  key: string
  expect: string | null
}

interface MeCase {
  identity: unknown
  expect: string[]
}

const cases = fixture.cases as EntriesCase[]
const targetCases = fixture.targetCases as TargetCase[]
const meCases = fixture.meCases as MeCase[]

describe('entriesFor / entryTarget / meSections（fixture 驱动）', () => {
  describe('entriesFor(identity) → 首页入口 key 数组', () => {
    it('fixture 的 cases 一组不落', () => {
      expect(cases.length).toBeGreaterThan(0)
    })

    cases.forEach((c) => {
      it(`identity=${JSON.stringify(c.identity)} → ${JSON.stringify(c.expect)}`, () => {
        expect(entriesFor(c.identity)).toEqual(c.expect)
      })
    })
  })

  describe('entryTarget(identity, key) → 点这个入口去哪', () => {
    it('fixture 的 targetCases 一组不落', () => {
      expect(targetCases.length).toBeGreaterThan(0)
    })

    targetCases.forEach((c) => {
      it(`identity=${JSON.stringify(c.identity)} key=${c.key} → ${JSON.stringify(c.expect)}`, () => {
        expect(entryTarget(c.identity, c.key)).toBe(c.expect)
      })
    })
  })

  describe('meSections(identity) → 「我的」页按身份出现的区块', () => {
    it('fixture 的 meCases 一组不落', () => {
      expect(meCases.length).toBeGreaterThan(0)
    })

    meCases.forEach((c) => {
      it(`identity=${JSON.stringify(c.identity)} → ${JSON.stringify(c.expect)}`, () => {
        expect(meSections(c.identity)).toEqual(c.expect)
      })
    })
  })
})
