/**
 * 底座前端不再做细粒度权限判断：登录后可访问全部静态页面。
 * 保留该入口仅为兼容现有组件，权限码常量仍作为文档参考。
 */
import { computed } from 'vue'
import { PERM, type PermCode } from './permissions'

export function useAccess() {
  function can(_code: PermCode | string): boolean {
    return true
  }

  const isSimulating = computed(() => false)

  return { can, has: can, isSimulating, PERM }
}
