import { reactive, ref } from 'vue'

interface PageResult<T> {
  total: number
  rows: T[]
}

interface UseResourceOptions<Q, T> {
  /** 请求函数：入参为查询条件，返回分页结果 */
  fetcher: (query: Q) => Promise<{ data: PageResult<T> }>
  /** 初始查询条件 */
  initial: Q
  /** 每页条数，默认 50（工作区大列表，弱化分页感） */
  pageSize?: number
}

/**
 * 资源列表统一逻辑：查询条件 / 加载 / 累加（无限滚动）/ 重置
 * 团队只需提供 fetcher，不必每页重复写 loading 与分页
 */
export function useResource<Q extends { page?: number; pageSize?: number }, T>(
  options: UseResourceOptions<Q, T>
) {
  const size = options.pageSize ?? 50
  const query = reactive({ ...options.initial }) as Q
  const items = ref<T[]>([]) as { value: T[] }
  const total = ref(0)
  const loading = ref(false)
  const finished = ref(false)

  async function load(reset = false) {
    if (loading.value) return
    // 非重置场景：已全部加载完毕则不再请求
    // （避免 IntersectionObserver observe 时的初始回调在首屏未撑满时重复触发）
    if (!reset && finished.value) return
    if (reset) {
      query.page = 1
      items.value = []
      finished.value = false
    }
    loading.value = true
    try {
      query.pageSize = size
      const res = await options.fetcher({ ...query })
      items.value = reset ? res.data.rows : [...items.value, ...res.data.rows]
      total.value = res.data.total
      if (items.value.length >= res.data.total || res.data.rows.length === 0) {
        finished.value = true
      } else {
        query.page = (query.page ?? 1) + 1
      }
    } finally {
      loading.value = false
    }
  }

  /** 应用新筛选条件（从头加载） */
  function search() {
    return load(true)
  }

  /** 从已加载列表中移除（删除后本地同步，不必整页刷新） */
  function removeWhere(predicate: (item: T) => boolean) {
    items.value = items.value.filter((i) => !predicate(i))
    total.value = Math.max(0, total.value - 1)
  }

  return { query, items, total, loading, finished, load, search, removeWhere }
}
