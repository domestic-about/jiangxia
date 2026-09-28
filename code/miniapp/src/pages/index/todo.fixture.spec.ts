// 首页「待处理」块的 fixture 驱动用例（甲方 2026-09-24 第 17 行）。
//
// 用例与期望**全部**从 `doc/verify/fixtures/home-todo-cases.json` 读（showCases / itemCases /
// labels / targets / emptyText），本文件不写任何期望值。
import { describe, expect, it } from 'vitest'
import fixture from '@doc/verify/fixtures/home-todo-cases.json'
import { TODO_EMPTY_TEXT, TODO_KEYS, TODO_TARGET, showTodo, todoAllClear, todoItems } from './todo'

interface ShowCase {
  identity: unknown
  expect: boolean
}

interface ItemCase {
  name: string
  todo: unknown
  expect: { counts: number[], muted: boolean[], allClear: boolean }
}

const showCases = fixture.showCases as ShowCase[]
const itemCases = fixture.itemCases as ItemCase[]

describe('首页「待处理」（fixture 驱动）', () => {
  describe('showTodo(identity)：只给内部', () => {
    it('fixture 的 showCases 一组不落', () => {
      expect(showCases.length).toBeGreaterThan(0)
    })

    showCases.forEach((c) => {
      it(`identity=${JSON.stringify(c.identity)} → ${c.expect}`, () => {
        expect(showTodo(c.identity)).toBe(c.expect)
      })
    })
  })

  describe('todoItems / todoAllClear：固定三项、0 弱化不隐藏、全 0 一行', () => {
    it('fixture 的 itemCases 一组不落', () => {
      expect(itemCases.length).toBeGreaterThan(0)
    })

    itemCases.forEach((c) => {
      it(c.name, () => {
        const items = todoItems(c.todo)
        expect(items.map(i => i.count)).toEqual(c.expect.counts)
        expect(items.map(i => i.muted)).toEqual(c.expect.muted)
        expect(todoAllClear(c.todo)).toBe(c.expect.allClear)
        // 三项的顺序与文案不随数字变
        expect(items.map(i => i.label)).toEqual(fixture.labels)
      })
    })
  })

  describe('点击去向与空态文案', () => {
    it('三项各去哪', () => {
      expect(TODO_TARGET).toEqual(fixture.targets)
      expect(todoItems({}).map(i => i.target)).toEqual(TODO_KEYS.map(k => (fixture.targets as Record<string, string>)[k]))
    })

    it('全 0 那一行的字', () => {
      expect(TODO_EMPTY_TEXT).toBe(fixture.emptyText)
    })
  })
})
