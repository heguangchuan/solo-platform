import type { SysPermission, PermissionRequest } from '@/types/api'
import { post, put, del, get } from '@/utils/request'
import service from '@/utils/request'

/**
 * 获取权限树
 * @param type 可选的权限类型过滤（1菜单 / 2按钮），不传则返回全量
 */
export function getPermissionTreeApi(type?: number) {
  return get<SysPermission[]>('/api/permission/tree', { params: type != null ? { type } : undefined })
}

export function exportPermissionApi(data: { ids?: string[] }) {
  return service.post('/api/permission/export', data, { responseType: 'blob' })
}

/**
 * 新增权限
 */
export function addPermissionApi(data: PermissionRequest) {
  return post<void>('/api/permission', data)
}

/**
 * 编辑权限
 */
export function editPermissionApi(id: string, data: PermissionRequest) {
  return put<void>(`/api/permission/${id}`, data)
}

/**
 * 删除权限
 */
export function deletePermissionApi(id: string) {
  return del<void>(`/api/permission/${id}`)
}

export function deletePermissionBatchApi(ids: string[]) {
  return del<void>('/api/permission/batch', { data: ids })
}
