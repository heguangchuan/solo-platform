import { ref } from 'vue'
import type { SysDictData } from '@/types/api'
import { getDictDataByTypeApi } from '@/api/dict'

/** 进程内字典缓存，避免重复请求 */
const cache = new Map<string, SysDictData[]>()
const inflight = new Map<string, Promise<SysDictData[]>>()

/**
 * 字典 Composable：按 dictType 拉取启用项
 * const { items } = useDict('sys_user_status')
 */
export function useDict(dictType: string) {
  const items = ref<SysDictData[]>(cache.get(dictType) ?? [])

  async function load(force = false) {
    if (!force && cache.has(dictType)) {
      items.value = cache.get(dictType)!
      return items.value
    }
    if (!inflight.has(dictType)) {
      inflight.set(
        dictType,
        getDictDataByTypeApi(dictType)
          .then((res) => {
            const list = res.data.filter((d) => d.status === 1)
            cache.set(dictType, list)
            return list
          })
          .finally(() => inflight.delete(dictType))
      )
    }
    items.value = await inflight.get(dictType)!
    return items.value
  }

  load()

  return { items, refresh: () => load(true) }
}

/** 字典数据变更后清除缓存，下次 useDict 重新拉取 */
export function clearDictCache(dictType?: string) {
  if (dictType) cache.delete(dictType)
  else cache.clear()
}

/** 由 value 取 label（用于表格列展示） */
export function dictLabel(items: SysDictData[], value: string | number) {
  return items.find((i) => i.dictValue === String(value))?.dictLabel ?? value
}
