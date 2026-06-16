/** 角色管理相关 */
import type { PageRequest } from './common'

export interface RolePageRequest extends PageRequest {
  roleCode?: string
  roleName?: string
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
