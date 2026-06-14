import type { SysUser, UserPageRequest, UserRequest, PageResult } from '@/types/api'
import { post, put, del } from '@/utils/request'
import service from '@/utils/request'

/**
 * 获取用户分页列表
 */
export function getUserPageApi(data: UserPageRequest) {
  return post<PageResult<SysUser>>('/system/user/page', data)
}

export function exportUserApi(data: UserPageRequest & { ids?: string[] }) {
  return service.post('/system/user/export', data, { responseType: 'blob' })
}

/**
 * 新增用户
 */
export function addUserApi(data: UserRequest) {
  return post<void>('/system/user', data)
}

/**
 * 编辑用户
 */
export function editUserApi(id: string, data: UserRequest) {
  return put<void>(`/system/user/${id}`, data)
}

export function switchUserStatusApi(id: string, status: number) {
  return put<void>(`/system/user/${id}/status/${status}`)
}

/**
 * 删除用户
 */
export function deleteUserApi(id: string) {
  return del<void>(`/system/user/${id}`)
}

export function deleteUserBatchApi(ids: string[]) {
  return del<void>('/system/user/batch', { data: ids })
}
