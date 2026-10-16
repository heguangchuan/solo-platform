import type { AxiosResponse } from 'axios'
import service, { get, post, put, del } from '@/utils/request'
import type {
  SysUser,
  UserPageRequest,
  UserExportRequest,
  UserRequest,
  PageResult,
  UserStatusCount,
} from '@/types/api'

/** 用户分页 */
export function getUserPageApi(data: UserPageRequest) {
  return post<PageResult<SysUser>>('/api/user/page', data)
}

/** 新增用户 */
export function addUserApi(data: UserRequest) {
  return post<void>('/api/user', data)
}

/** 编辑用户 */
export function editUserApi(id: string, data: UserRequest) {
  return put<void>(`/api/user/${id}`, data)
}

/** 切换用户状态 */
export function switchUserStatusApi(id: string, status: number) {
  return put<void>(`/api/user/${id}/status/${status}`)
}

/** 删除用户 */
export function deleteUserApi(id: string) {
  return del<void>(`/api/user/${id}`)
}

/** 批量删除用户 */
export function deleteUserBatchApi(ids: string[]) {
  return del<void>('/api/user/batch', { data: ids })
}

/** 查询用户已分配的角色 ID */
export function getUserAssignedRolesApi(userId: string) {
  return get<string[]>(`/api/user/r2u/${userId}`)
}

/** 给用户分配角色 */
export function assignUserRolesApi(userId: string, roleIds: string[]) {
  return post<void>('/api/user/r2u', { userId, roleIds })
}

/** 导出用户 Excel（blob），支持按筛选条件或选中 id 导出 */
export function exportUserApi(data: UserExportRequest): Promise<AxiosResponse<Blob>> {
  return service.post('/api/user/export', data, { responseType: 'blob' })
}

/** 按状态统计用户数（全局口径，与查询条件无关） */
export function getUserStatusCountApi() {
  return get<UserStatusCount[]>('/api/user/status-count')
}
