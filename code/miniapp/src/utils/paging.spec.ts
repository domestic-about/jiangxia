// 触底分页（V27）的用例：total 只认后端、取完的判据、去重、换条件时丢弃晚到的旧响应。
import { describe, expect, it } from 'vitest'
import type { PageResult } from './paging'
import { pagingFooterText, usePagedList } from './paging'

interface Row { id: number }

/** 造一个 total 条的假后端（pageNum 从 1 起） */
function fakeBackend(total: number) {
  const calls: number[] = []
  const fetchPage = async (pageNum: number, pageSize: number): Promise<PageResult<Row>> => {
    calls.push(pageNum)
    const start = (pageNum - 1) * pageSize
    const rows = Array.from({ length: Math.max(0, Math.min(pageSize, total - start)) }, (_, i) => ({ id: start + i + 1 }))
    return { rows, total }
  }
  return { fetchPage, calls }
}

describe('usePagedList', () => {
  it('125 条、每页 20：第一页 20 条，共 125；触底一页页取到 125 条后停止请求', async () => {
    const be = fakeBackend(125)
    const pager = usePagedList<Row>({ fetchPage: be.fetchPage, keyOf: r => String(r.id), pageSize: 20 })
    await pager.reload()
    expect(pager.rows.value.length).toBe(20)
    expect(pager.total.value).toBe(125)
    expect(pager.finished.value).toBe(false)
    for (let i = 0; i < 10; i++) {
      await pager.loadMore()
    }
    expect(pager.rows.value.length).toBe(125)
    expect(pager.rows.value[124].id).toBe(125)
    expect(pager.finished.value).toBe(true)
    expect(be.calls).toEqual([1, 2, 3, 4, 5, 6, 7])
  })

  it('分页边界上重复出现的行只保留一次', async () => {
    const pager = usePagedList<Row>({
      fetchPage: async pageNum => (pageNum === 1
        ? { rows: [{ id: 1 }, { id: 2 }], total: 3 }
        : { rows: [{ id: 2 }, { id: 3 }], total: 3 }),
      keyOf: r => String(r.id),
      pageSize: 2,
    })
    await pager.reload()
    await pager.loadMore()
    expect(pager.rows.value.map(r => r.id)).toEqual([1, 2, 3])
    expect(pager.finished.value).toBe(true)
  })

  it('换条件重取时，上一轮晚到的响应被丢弃', async () => {
    let release: (v: PageResult<Row>) => void = () => {}
    let first = true
    const pager = usePagedList<Row>({
      fetchPage: () => {
        if (first) {
          first = false
          return new Promise<PageResult<Row>>((resolve) => { release = resolve })
        }
        return Promise.resolve({ rows: [{ id: 9 }], total: 1 })
      },
      keyOf: r => String(r.id),
    })
    const stale = pager.reload()
    await pager.reload()
    release({ rows: [{ id: 1 }], total: 50 })
    await stale
    expect(pager.rows.value.map(r => r.id)).toEqual([9])
    expect(pager.total.value).toBe(1)
  })

  it('第一页失败：整块失败态，触底不再请求', async () => {
    let calls = 0
    const pager = usePagedList<Row>({
      fetchPage: async () => {
        calls++
        throw new Error('boom')
      },
      keyOf: r => String(r.id),
    })
    await pager.reload()
    expect(pager.failed.value).toBe(true)
    await pager.loadMore()
    expect(calls).toBe(1)
  })
})

describe('pagingFooterText', () => {
  it('取完写「共 N 条」（N = 后端 total），没取完写已显示几条', () => {
    expect(pagingFooterText({ loadingMore: false, moreFailed: false, finished: true, total: 125, count: 125 })).toBe('共 125 条，已全部显示')
    expect(pagingFooterText({ loadingMore: false, moreFailed: false, finished: false, total: 125, count: 40 })).toBe('已显示 40 / 125 条，上滑加载更多')
    expect(pagingFooterText({ loadingMore: true, moreFailed: false, finished: false, total: 125, count: 40 })).toBe('正在加载…')
    expect(pagingFooterText({ loadingMore: false, moreFailed: true, finished: false, total: 125, count: 40 })).toBe('加载失败，点这里重试')
  })
})
