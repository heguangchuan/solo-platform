import type { AxiosResponse } from 'axios'
import service, { get, post, put, del } from '@/utils/request'
import type {
  SysRole,
  RolePageRequest,
  RoleExportRequest,
  RoleRequest,
  PageResult,
} from '@/types/api'

/** 角色分页 */
export function getRolePageApi(data: RolePageRequest) {
  return post<PageResult<SysRole>>('/api/role/page', data)
}

/** 全部启用角色（授权选择用） */
export function getRoleListApi() {
  return get<SysRole[]>('/api/role/list')
}

/** 新增角色 */
export function addRoleApi(data: RoleRequest) {
  return post<void>('/api/role', data)
}

/** 编辑角色 */
export function editRoleApi(id: string, data: RoleRequest) {
  return put<void>(`/api/role/${id}`, data)
}

/** 删除角色 */
export function deleteRoleApi(id: string) {
  return del<void>(`/api/role/${id}`)
}

/** 批量删除 */
export function deleteRoleBatchApi(ids: string[]) {
  return del<void>('/api/role/batch', { data: ids })
}

/** 导出角色 Excel（blob），支持按筛选条件或选中 id 导出 */
export function exportRoleApi(data: RoleExportRequest): Promise<AxiosResponse<Blob>> {
  return service.post('/api/role/export', data, { responseType: 'blob' })
}

/** 查询角色已分配的权限 ID */
export function getRoleAssignedPermissionsApi(roleId: string) {
  return get<string[]>(`/api/role/p2r/${roleId}`)
}

/** 给角色分配权限 */
export function assignRolePermissionsApi(roleId: string, permissionIds: string[]) {
  return post<void>('/api/role/p2r', { roleId, permissionIds })
}
