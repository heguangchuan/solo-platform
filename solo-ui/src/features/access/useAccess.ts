/**
 * 权限统一入口：替代散落的 v-hasPermi 字符串指令
 * 用法：const { can } = useAccess(); can(PERM.USER_ADD)
 */
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { PERM, type PermCode } from './permissions'

export function useAccess() {
  const auth = useAuthStore()

  /** 是否拥有权限；支持通配前缀匹配（如拥有 system:user 视为拥有其下按钮） */
  function can(code: PermCode | string): boolean {
    const set = auth.activePermissions
    if (set.has(code)) return true
    // 逐级向上找通配：system:user:add -> system:user -> system
    const parts = code.split(':')
    for (let i = parts.length - 1; i > 0; i--) {
      if (set.has(parts.slice(0, i).join(':'))) return true
    }
    return false
  }

  /** 是否处于角色模拟视角 */
  const isSimulating = computed(() => auth.simulatedPermissions != null)

  return { can, has: can, isSimulating, PERM }
}
