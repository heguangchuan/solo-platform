import type { SysUser, UserPageRequest, UserRequest, PageResult } from '@/types/api'
import { get, post, put, del } from '@/utils/request'
import service from '@/utils/request'

/**
 * 获取用户分页列表
 */
export function getUserPageApi(data: UserPageRequest) {
  return post<PageResult<SysUser>>('/api/user/page', data)
}

export function exportUserApi(data: UserPageRequest & { ids?: string[] }) {
  return service.post('/api/user/export', data, { responseType: 'blob' })
}

/**
 * 新增用户
 */
export function addUserApi(data: UserRequest) {
  return post<void>('/api/user', data)
}

/**
 * 编辑用户
 */
export function editUserApi(id: string, data: UserRequest) {
  return put<void>(`/api/user/${id}`, data)
}

export function switchUserStatusApi(id: string, status: number) {
  return put<void>(`/api/user/${id}/status/${status}`)
}

/**
 * 删除用户
 */
export function deleteUserApi(id: string) {
  return del<void>(`/api/user/${id}`)
}

export function deleteUserBatchApi(ids: string[]) {
  return del<void>('/api/user/batch', { data: ids })
}

/**
 * 查询用户已分配的角色ID列表
 */
export function getUserAssignedRolesApi(userId: string) {
  return get<string[]>(`/api/user/r2u/${userId}`)
}

/**
 * 给用户分配角色
 */
export function assignUserRolesApi(userId: string, roleIds: string[]) {
  return post<void>('/api/user/r2u', { userId, roleIds })
}
