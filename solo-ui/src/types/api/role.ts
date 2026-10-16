/** 角色管理相关 */
import type { PageRequest } from './common'

export interface RolePageRequest extends PageRequest {
  roleCode?: string
  roleName?: string
}

/** 角色导出参数（支持按选中 id 导出） */
export interface RoleExportRequest extends RolePageRequest {
  ids?: string[]
}

export interface RoleRequest {
  roleCode: string
  roleName: string
  description: string
  status: number
}

export interface SysRole {
  id: string
  roleCode: string
  roleName: string
  description: string
  status: number
  createTime: string
  updateTime: string
}
