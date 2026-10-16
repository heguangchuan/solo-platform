/** 权限管理相关 */

export interface PermissionRequest {
  permissionName: string
  permissionCode: string
  url: string
  type: number
  parentId: string | number
  sort: number
  icon: string
  status: number
}

export interface SysPermission {
  id: string
  permissionName: string
  permissionCode: string
  url: string
  type: number
  parentId: string | number
  sort: number
  icon: string
  status: number
  children?: SysPermission[]
}
