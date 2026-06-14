import type { SysPermission, PermissionRequest } from '@/types/api'
import { post, put, del, get } from '@/utils/request'

/**
 * 获取权限树
 */
export function getPermissionTreeApi() {
  return get<SysPermission[]>('/system/permission/tree')
}

/**
 * 新增权限
 */
export function addPermissionApi(data: PermissionRequest) {
  return post<void>('/system/permission', data)
}

/**
 * 编辑权限
 */
export function editPermissionApi(id: number, data: PermissionRequest) {
  return put<void>(`/system/permission/${id}`, data)
}

/**
 * 删除权限
 */
export function deletePermissionApi(id: number) {
  return del<void>(`/system/permission/${id}`)
}
