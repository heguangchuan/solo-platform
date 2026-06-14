import type { SysPermission, PermissionRequest } from '@/types/api'
import { post, put, del, get } from '@/utils/request'
import service from '@/utils/request'

/**
 * 获取权限树
 */
export function getPermissionTreeApi() {
  return get<SysPermission[]>('/system/permission/tree')
}

export function exportPermissionApi(data: { ids?: string[] }) {
  return service.post('/system/permission/export', data, { responseType: 'blob' })
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
export function editPermissionApi(id: string, data: PermissionRequest) {
  return put<void>(`/system/permission/${id}`, data)
}

/**
 * 删除权限
 */
export function deletePermissionApi(id: string) {
  return del<void>(`/system/permission/${id}`)
}

export function deletePermissionBatchApi(ids: string[]) {
  return del<void>('/system/permission/batch', { data: ids })
}
