import type { FunctionalComponent } from 'vue'

export type TablerIcon = FunctionalComponent

let cache: Record<string, TablerIcon> | null = null
let pending: Promise<Record<string, TablerIcon>> | null = null

/**
 * 异步加载全量 Tabler 图标（按需触发，首次调用后缓存）。
 * 仅用于权限管理的图标选择器/图标列，避免打进主包。
 * key 为 PascalCase 导出名，如 IconUsers。
 */
export function loadAllIcons(): Promise<Record<string, TablerIcon>> {
  if (cache) return Promise.resolve(cache)
  if (pending) return pending
  pending = import('@tabler/icons-vue').then((mod) => {
    cache = mod.icons as unknown as Record<string, TablerIcon>
    return cache
  })
  return pending
}

/** 按名称取单个图标组件，未加载返回 undefined */
export function findIcon(name: string): TablerIcon | undefined {
  return cache?.[name]
}
