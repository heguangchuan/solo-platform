import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'

export interface TagItem {
  /** 路由完整路径（含 query），用作主键 */
  fullPath: string
  /** route.name，对接 keep-alive include */
  name: string
  /** 显示标题 */
  title: string
  /** 图标（来自 route.meta.icon） */
  icon?: string
  /** 是否固定（不可关闭，例如"首页"） */
  affix?: boolean
}

const STORAGE_KEY = 'app-tags'
/** 永远固定在第一位、不可关闭 */
const HOME_TAG: TagItem = {
  fullPath: '/dashboard',
  name: 'Dashboard',
  title: '首页',
  icon: 'Odometer',
  affix: true,
}

function loadFromStorage(): TagItem[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw) as TagItem[]
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function saveToStorage(tags: TagItem[]) {
  try {
    // affix 的 home 不需要存（每次都从常量恢复）；只存其余
    const data = tags.filter((t) => !t.affix)
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(data))
  } catch {
    // ignore quota
  }
}

export const useTagsStore = defineStore('tags', () => {
  /** 当前打开的所有 Tag，第一位永远是 HOME_TAG */
  const tags = ref<TagItem[]>([HOME_TAG, ...loadFromStorage()])
  /** 当前激活的 fullPath */
  const activeTag = ref<string>('/dashboard')

  /** 给 keep-alive 的 include 用（必须是 component name 数组） */
  const cachedNames = computed(() => tags.value.map((t) => t.name).filter(Boolean))

  function persist() {
    saveToStorage(tags.value)
  }

  function isMatch(a: TagItem, fullPath: string) {
    return a.fullPath === fullPath
  }

  /** 路由进入时调用：不存在则推入，存在则激活 */
  function open(route: RouteLocationNormalized) {
    if (route.meta?.hidden && route.name !== 'Profile') {
      // 不在侧边栏的隐藏页（如 404 / 403 / login）不进 Tags
      // 个人中心 hidden 但允许加入
      return
    }
    if (!route.name) return

    activeTag.value = route.fullPath
    const exists = tags.value.find((t) => isMatch(t, route.fullPath))
    if (exists) return

    tags.value.push({
      fullPath: route.fullPath,
      name: String(route.name),
      title: (route.meta?.title as string) || String(route.name),
      icon: route.meta?.icon as string | undefined,
    })
    persist()
  }

  /**
   * 关闭某 Tag。
   * 返回值表示"是否关掉了当前激活页"，调用方需据此跳转到相邻 Tag。
   */
  function close(fullPath: string): { closedActive: boolean; next?: TagItem } {
    const idx = tags.value.findIndex((t) => t.fullPath === fullPath)
    if (idx < 0) return { closedActive: false }
    const target = tags.value[idx]
    if (target.affix) return { closedActive: false }

    const wasActive = activeTag.value === fullPath
    tags.value.splice(idx, 1)
    persist()

    if (wasActive) {
      // 优先取右边邻居，没有就取左边
      const next = tags.value[idx] || tags.value[idx - 1] || HOME_TAG
      activeTag.value = next.fullPath
      return { closedActive: true, next }
    }
    return { closedActive: false }
  }

  /** 关闭其它（保留当前 + affix） */
  function closeOthers(fullPath: string): TagItem[] {
    const kept = tags.value.filter((t) => t.affix || t.fullPath === fullPath)
    const removed = tags.value.filter((t) => !kept.includes(t))
    tags.value = kept
    activeTag.value = fullPath
    persist()
    return removed
  }

  /** 关闭左侧（保留 affix + 当前及右侧） */
  function closeLeft(fullPath: string): TagItem[] {
    const idx = tags.value.findIndex((t) => t.fullPath === fullPath)
    if (idx <= 0) return []
    const removed = tags.value.slice(0, idx).filter((t) => !t.affix)
    tags.value = [
      ...tags.value.slice(0, idx).filter((t) => t.affix),
      ...tags.value.slice(idx),
    ]
    persist()
    return removed
  }

  /** 关闭右侧 */
  function closeRight(fullPath: string): TagItem[] {
    const idx = tags.value.findIndex((t) => t.fullPath === fullPath)
    if (idx < 0 || idx === tags.value.length - 1) return []
    const removed = tags.value.slice(idx + 1).filter((t) => !t.affix)
    tags.value = tags.value.slice(0, idx + 1)
    persist()
    return removed
  }

  /** 关闭全部（仅保留 affix） */
  function closeAll(): TagItem[] {
    const removed = tags.value.filter((t) => !t.affix)
    tags.value = tags.value.filter((t) => t.affix)
    activeTag.value = HOME_TAG.fullPath
    persist()
    return removed
  }

  /** 退出登录时清空 */
  function reset() {
    tags.value = [HOME_TAG]
    activeTag.value = HOME_TAG.fullPath
    sessionStorage.removeItem(STORAGE_KEY)
  }

  return {
    tags,
    activeTag,
    cachedNames,
    open,
    close,
    closeOthers,
    closeLeft,
    closeRight,
    closeAll,
    reset,
  }
})
