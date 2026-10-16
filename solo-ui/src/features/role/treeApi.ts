import type { InjectionKey } from 'vue'
import type { SysPermission } from '@/types/api'

/** 三态勾选状态 */
export type CheckState = 'checked' | 'unchecked' | 'indeterminate'

export interface TreeApi {
  /** 读取节点状态 */
  getState: (node: SysPermission) => CheckState
  /** 点击节点复选框 */
  toggle: (node: SysPermission) => void
  /** 展开状态与切换 */
  isExpanded: (id: string) => boolean
  toggleExpand: (id: string) => void
  /** 是否禁用（无授权权限时只读） */
  disabled: boolean
}

export const TREE_API: InjectionKey<TreeApi> = Symbol('PermissionTreeApi')
