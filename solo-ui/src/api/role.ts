import type { SysRole, RolePageRequest, RoleRequest, PageResult } from '@/types/api'
import { get, post, put, del } from '@/utils/request'
import service from '@/utils/request'

/**
 * 获取角色分页列表
 */
export function getRolePageApi(data: RolePageRequest) {
  return post<PageResult<SysRole>>('/api/role/page', data)
}

/**
 * 获取全部启用角色（用于授权选择）
 */
export function getRoleListApi() {
  return get<SysRole[]>('/api/role/list')
}

export function exportRoleApi(data: RolePageRequest & { ids?: string[] }) {
  return service.post('/api/role/export', data, { responseType: 'blob' })
}

/**
 * 新增角色
 */
export function addRoleApi(data: RoleRequest) {
  return post<void>('/api/role', data)
}

/**
 * 编辑角色
 */
export function editRoleApi(id: string, data: RoleRequest) {
  return put<void>(`/api/role/${id}`, data)
}

/**
 * 删除角色
 */
export function deleteRoleApi(id: string) {
  return del<void>(`/api/role/${id}`)
}

export function deleteRoleBatchApi(ids: string[]) {
  return del<void>('/api/role/batch', { data: ids })
}

/**
 * 查询角色已分配的权限ID列表
 */
export function getRoleAssignedPermissionsApi(roleId: string) {
  return get<string[]>(`/api/role/p2r/${roleId}`)
}

/**
 * 给角色分配权限
 */
export function assignRolePermissionsApi(roleId: string, permissionIds: string[]) {
  return post<void>('/api/role/p2r', { roleId, permissionIds })
}
