import type { SysServer, ServerPageRequest, ServerRequest, PageResult } from '@/types/api'
import { post, put, del } from '@/utils/request'

export function getServerPageApi(data: ServerPageRequest) {
  return post<PageResult<SysServer>>('/api/server/page', data)
}

export function addServerApi(data: ServerRequest) {
  return post<void>('/api/server', data)
}

export function editServerApi(id: string, data: ServerRequest) {
  return put<void>(`/api/server/${id}`, data)
}

export function deleteServerApi(id: string) {
  return del<void>(`/api/server/${id}`)
}

export function deleteServerBatchApi(ids: string[]) {
  return del<void>('/api/server/batch', { data: ids })
}
