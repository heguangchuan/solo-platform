import type { SysServer, ServerPageRequest, ServerRequest, ServerFileItem, ServerFileListRequest, PageResult } from '@/types/api'
import { post, put, del } from '@/utils/request'
import service from '@/utils/request'

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

export function getServerFileListApi(data: ServerFileListRequest) {
  return post<ServerFileItem[]>('/api/server/file/list', data)
}

export function uploadServerFileApi(serverId: string, path: string, file: File) {
  const formData = new FormData()
  formData.append('serverId', serverId)
  formData.append('path', path)
  formData.append('file', file)
  return service.post('/api/server/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function downloadServerFileApi(serverId: string, path: string) {
  return service.post('/api/server/file/download', { serverId, path }, { responseType: 'blob' })
}
