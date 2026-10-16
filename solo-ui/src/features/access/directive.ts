import type { Directive, DirectiveBinding } from 'vue'
import { useAccess } from './useAccess'

/**
 * v-access="'system:user:add'" 无权限时移除该元素
 * 复杂场景优先在脚本中用 useAccess().can()
 */
export const accessDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    const { can } = useAccess()
    const codes = Array.isArray(binding.value) ? binding.value : [binding.value]
    if (!codes.some((c) => can(c))) {
      el.parentNode?.removeChild(el)
    }
  },
}
