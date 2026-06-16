import { ref, watch, type Ref } from 'vue'
import type { SysDictData } from '@/types/api'
import { getDictDataByTypeApi } from '@/api/dict'

const dictCache = new Map<string, Promise<SysDictData[]> | SysDictData[]>()

export async function getDictOptions(dictType: string, force = false) {
  if (!dictType) return []

  if (!force) {
    const cached = dictCache.get(dictType)
    if (cached) return cached
  }

  const pending = getDictDataByTypeApi(dictType).then((res) => {
    const options = [...res.data].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    dictCache.set(dictType, options)
    return options
  })
  dictCache.set(dictType, pending)
  return pending
}

export function clearDictCache(dictType?: string) {
  if (dictType) {
    dictCache.delete(dictType)
    return
  }
  dictCache.clear()
}

export function useDict(dictType: Ref<string> | string) {
  const options = ref<SysDictData[]>([])
  const loading = ref(false)

  async function load(force = false) {
    const type = typeof dictType === 'string' ? dictType : dictType.value
    if (!type) {
      options.value = []
      return
    }

    loading.value = true
    try {
      options.value = await getDictOptions(type, force)
    } finally {
      loading.value = false
    }
  }

  function reload() {
    return load(true)
  }

  if (typeof dictType === 'string') {
    load()
  } else {
    watch(dictType, () => load(), { immediate: true })
  }

  return { options, loading, load, reload }
}
