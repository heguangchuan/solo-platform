import type { App, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

function hasPermission(value: string | string[], permissions: string[]) {
  if (Array.isArray(value)) {
    return value.some((item) => permissions.includes(item))
  }
  return permissions.includes(value)
}

export function setupPermissionDirective(app: App) {
  app.directive('permission', {
    mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
      const userStore = useUserStore()
      if (!hasPermission(binding.value, userStore.permissions)) {
        el.parentNode?.removeChild(el)
      }
    },
  })
}
