import type { SysRole, RolePageRequest, RoleRequest, PageResult } from '@/types/api'
import { post, put, del } from '@/utils/request'

/**
 * 获取角色分页列表
 */
export function getRolePageApi(data: RolePageRequest) {
  return post<PageResult<SysRole>>('/system/role/page', data)
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
export function editRoleApi(id: number, data: RoleRequest) {
  return put<void>(`/system/role/${id}`, data)
}

/**
 * 删除角色
 */
export function deleteRoleApi(id: number) {
  return del<void>(`/system/role/${id}`)
}

export function deleteRoleBatchApi(ids: number[]) {
  return del<void>('/system/role/batch', { data: ids })
}
