import type { SysRole, RolePageRequest, RoleRequest, PageResult } from '@/types/api'
import { get, post, put, del } from '@/utils/request'
import service from '@/utils/request'

/**
 * 获取角色分页列表
 */
export function getRolePageApi(data: RolePageRequest) {
  return post<PageResult<SysRole>>('/system/role/page', data)
}

/**
 * 获取全部启用角色（用于授权选择）
 */
export function getRoleListApi() {
  return get<SysRole[]>('/system/role/list')
}

export function exportRoleApi(data: RolePageRequest & { ids?: string[] }) {
  return service.post('/system/role/export', data, { responseType: 'blob' })
}

/**
 * 新增角色
 */
export function addRoleApi(data: RoleRequest) {
  return post<void>('/system/role', data)
}

/**
 * 编辑角色
 */
export function editRoleApi(id: string, data: RoleRequest) {
  return put<void>(`/system/role/${id}`, data)
}

/**
 * 删除角色
 */
export function deleteRoleApi(id: string) {
  return del<void>(`/system/role/${id}`)
}

export function deleteRoleBatchApi(ids: string[]) {
  return del<void>('/system/role/batch', { data: ids })
}

/**
 * 查询角色已分配的权限ID列表
 */
export function getRoleAssignedPermissionsApi(roleId: string) {
  return get<string[]>(`/system/role/p2r/${roleId}`)
}

/**
 * 给角色分配权限
 */
export function assignRolePermissionsApi(roleId: string, permissionIds: string[]) {
  return post<void>('/system/role/p2r', { roleId, permissionIds })
}
