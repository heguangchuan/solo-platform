import { ref } from 'vue'

export interface ConfirmOptions {
  title: string
  description?: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
}

interface ConfirmState extends ConfirmOptions {
  visible: boolean
  resolve?: (value: boolean) => void
}

const state = ref<ConfirmState>({ visible: false, title: '' })

/** 命令式确认弹窗，替代 n-popconfirm */
export const confirm = (options: ConfirmOptions): Promise<boolean> => {
  return new Promise((resolve) => {
    state.value = { ...options, visible: true, resolve }
  })
}

export const useConfirmState = () => {
  const settle = (value: boolean) => {
    state.value.resolve?.(value)
    state.value.visible = false
  }
  return { state, settle }
}
