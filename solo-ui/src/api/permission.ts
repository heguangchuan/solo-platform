import { get, post, put, del } from '@/utils/request'
import type { SysPermission, PermissionRequest } from '@/types/api'

/** 权限树；type: 1 菜单 / 2 按钮，不传为全量 */
export function getPermissionTreeApi(type?: number) {
  return get<SysPermission[]>('/api/permission/tree', {
    params: type != null ? { type } : undefined,
  })
}

/** 新增权限 */
export function addPermissionApi(data: PermissionRequest) {
  return post<void>('/api/permission', data)
}

/** 编辑权限 */
export function editPermissionApi(id: string, data: PermissionRequest) {
  return put<void>(`/api/permission/${id}`, data)
}

/** 删除权限 */
export function deletePermissionApi(id: string) {
  return del<void>(`/api/permission/${id}`)
}

/** 批量删除 */
export function deletePermissionBatchApi(ids: string[]) {
  return del<void>('/api/permission/batch', { data: ids })
}
