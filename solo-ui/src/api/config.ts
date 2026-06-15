import type { SysConfig, ConfigPageRequest, ConfigRequest, PageResult } from '@/types/api'
import { post, put, del } from '@/utils/request'
import service from '@/utils/request'

export function getConfigPageApi(data: ConfigPageRequest) {
  return post<PageResult<SysConfig>>('/system/config/page', data)
}

export function exportConfigApi(data: ConfigPageRequest & { ids?: string[] }) {
  return service.post('/system/config/export', data, { responseType: 'blob' })
}

export function addConfigApi(data: ConfigRequest) {
  return post<void>('/system/config', data)
}

export function editConfigApi(id: string, data: ConfigRequest) {
  return put<void>(`/system/config/${id}`, data)
}

export function deleteConfigApi(id: string) {
  return del<void>(`/system/config/${id}`)
}

export function deleteConfigBatchApi(ids: string[]) {
  return del<void>('/system/config/batch', { data: ids })
}
