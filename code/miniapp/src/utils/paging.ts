// 列表的触底分页（V27）：台账、历史编辑记录、选择样本弹层共用这一份。
//
// ★ 以前每个列表都写死 `pageSize=100` 只取第一页，表格页却写「共 N 条」（N 是后端 total）
//   —— 第 101 条以后永远看不到。现在一律「先取一页，滑到底再取下一页」：
//   - 「共 N 条」只认后端的 `total`（整表口径），不数已经加载了几行；
//   - 取完的判据：已加载行数 ≥ total，或者这一页不满（后端 total 不可靠时也不会无限请求）；
//   - 换筛选 / 换页签时 `reload()`：旧请求晚回来直接丢掉（按请求序号），不会把上一张表的行拼进来；
//   - 追加时按行 id 去重（分页边界上恰好有新数据插进来时，同一行不会出现两次）。
// ★ 纯逻辑、不碰 uni.* —— 页面自己在 onReachBottom / scroll-view 的 scrolltolower 里调 `loadMore()`。
import { ref, shallowRef } from 'vue'

/** 每页条数：一屏半左右，触底再取 */
export const PAGE_SIZE = 20

/** 分页接口的形状（`{code,msg,rows,total}` 里的两个键） */
export interface PageResult<T> {
  rows?: T[] | null
  total?: number | null
}

export interface PagedListOptions<T> {
  /** 取第 pageNum 页（从 1 起） */
  fetchPage: (pageNum: number, pageSize: number) => Promise<PageResult<T>>
  /** 行的唯一键（去重用） */
  keyOf: (row: T) => string
  pageSize?: number
  /** 每取回一页的回调（第一页时 `first=true`）：页面借它拿响应里的其它字段（如 tabCounts） */
  onPage?: (page: PageResult<T>, first: boolean) => void
}

export function usePagedList<T>(options: PagedListOptions<T>) {
  const pageSize = options.pageSize ?? PAGE_SIZE
  // shallowRef：每次都整体换掉数组（不在原数组上 push），不需要深层响应，也不让泛型被 UnwrapRef 搅乱
  const rows = shallowRef<T[]>([])
  const total = ref(0)
  /** 第一页加载中（整块骨架屏） */
  const loading = ref(false)
  /** 第一页失败（整块失败态） */
  const failed = ref(false)
  /** 后续页加载中（列表底部一行「正在加载…」） */
  const loadingMore = ref(false)
  /** 后续页失败（列表底部一行「加载失败，点这里重试」） */
  const moreFailed = ref(false)
  /** 已经取完 */
  const finished = ref(false)

  let pageNum = 0
  let seq = 0

  function append(page: PageResult<T>, replace: boolean) {
    const incoming = page.rows ?? []
    if (replace) {
      const seen = new Set<string>()
      rows.value = incoming.filter((row) => {
        const key = options.keyOf(row)
        if (seen.has(key)) {
          return false
        }
        seen.add(key)
        return true
      })
    }
    else {
      const seen = new Set(rows.value.map(options.keyOf))
      rows.value = rows.value.concat(incoming.filter(row => !seen.has(options.keyOf(row))))
    }
    total.value = typeof page.total === 'number' ? page.total : rows.value.length
    finished.value = incoming.length < pageSize || rows.value.length >= total.value
  }

  /** 从第一页重新取（换筛选、换页签、下拉刷新） */
  async function reload() {
    const mine = ++seq
    pageNum = 0
    loading.value = true
    failed.value = false
    loadingMore.value = false
    moreFailed.value = false
    finished.value = false
    try {
      const page = await options.fetchPage(1, pageSize)
      if (mine !== seq) {
        return
      }
      pageNum = 1
      append(page, true)
      options.onPage?.(page, true)
    }
    catch {
      if (mine !== seq) {
        return
      }
      failed.value = true
      rows.value = []
      total.value = 0
      finished.value = true
    }
    finally {
      if (mine === seq) {
        loading.value = false
      }
    }
  }

  /** 触底：取下一页（正在取、已取完、第一页还没成功时什么都不做） */
  async function loadMore() {
    if (loading.value || loadingMore.value || finished.value || failed.value || pageNum === 0) {
      return
    }
    const mine = seq
    loadingMore.value = true
    moreFailed.value = false
    try {
      const page = await options.fetchPage(pageNum + 1, pageSize)
      if (mine !== seq) {
        return
      }
      pageNum += 1
      append(page, false)
      options.onPage?.(page, false)
    }
    catch {
      if (mine === seq) {
        moreFailed.value = true
      }
    }
    finally {
      if (mine === seq) {
        loadingMore.value = false
      }
    }
  }

  /** 一次把剩下的页都取完（选择样本弹层要在全部候选里搜时用；有上限，防止失控） */
  async function loadAll(maxPages = 50) {
    for (let i = 0; i < maxPages && !finished.value && !failed.value && !moreFailed.value; i++) {
      await loadMore()
    }
  }

  /** 清空（身份不对、页签没有数据源时） */
  function clear() {
    seq++
    pageNum = 0
    rows.value = []
    total.value = 0
    loading.value = false
    failed.value = false
    loadingMore.value = false
    moreFailed.value = false
    finished.value = true
  }

  return { rows, total, loading, failed, loadingMore, moreFailed, finished, reload, loadMore, loadAll, clear }
}

/** 列表底部那一行的文案（页面与弹层共用口径） */
export function pagingFooterText(state: {
  loadingMore: boolean
  moreFailed: boolean
  finished: boolean
  total: number
  count: number
}): string {
  if (state.loadingMore) {
    return '正在加载…'
  }
  if (state.moreFailed) {
    return '加载失败，点这里重试'
  }
  if (state.finished) {
    return `共 ${state.total} 条，已全部显示`
  }
  return `已显示 ${state.count} / ${state.total} 条，上滑加载更多`
}
