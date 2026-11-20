import { ref } from 'vue'

export type ToastType = 'success' | 'error' | 'warning' | 'info'

export interface ToastAction {
  label: string
  onClick: () => void
}

export interface ToastItem {
  id: number
  type: ToastType
  message: string
  action?: ToastAction
}

const toasts = ref<ToastItem[]>([])
let seq = 0

const dismiss = (id: number) => {
  toasts.value = toasts.value.filter((t) => t.id !== id)
}

const push = (type: ToastType, message: string, action?: ToastAction, duration = 3200) => {
  const id = ++seq
  toasts.value.push({ id, type, message, action })
  window.setTimeout(() => dismiss(id), duration)
}

/** 可在组件外（如 axios 拦截器）直接调用的轻量提示 */
export const toast = {
  success: (m: string, action?: ToastAction) => push('success', m, action),
  error: (m: string, action?: ToastAction) => push('error', m, action),
  warning: (m: string, action?: ToastAction) => push('warning', m, action),
  info: (m: string, action?: ToastAction) => push('info', m, action),
  dismiss,
}

export const useToasts = () => {
  return { toasts, dismiss }
}
