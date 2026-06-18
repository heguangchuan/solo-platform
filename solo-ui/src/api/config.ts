import type { SysConfig, ConfigPageRequest, ConfigRequest, PageResult } from '@/types/api'
import { post, put, del } from '@/utils/request'
import service from '@/utils/request'

export function getConfigPageApi(data: ConfigPageRequest) {
  return post<PageResult<SysConfig>>('/api/config/page', data)
}

export function exportConfigApi(data: ConfigPageRequest & { ids?: string[] }) {
  return service.post('/api/config/export', data, { responseType: 'blob' })
}

export function addConfigApi(data: ConfigRequest) {
  return post<void>('/api/config', data)
}

export function editConfigApi(id: string, data: ConfigRequest) {
  return put<void>(`/api/config/${id}`, data)
}

export function deleteConfigApi(id: string) {
  return del<void>(`/api/config/${id}`)
}

export function deleteConfigBatchApi(ids: string[]) {
  return del<void>('/api/config/batch', { data: ids })
}
